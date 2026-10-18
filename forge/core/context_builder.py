"""Build concise, agent-ready context bundles."""

from __future__ import annotations

import json
from typing import Any

from forge.config import ForgeConfig
from forge.core.models import ContextBundle
from forge.core.repository import FileMemoryRepository, RepositoryError
from forge.core.retrieval import RetrievalEngine
from forge.core.similarity import tokenize


class ContextBuilder:
    def __init__(self, config: ForgeConfig, repository: FileMemoryRepository, retrieval: RetrievalEngine):
        self.config = config
        self.repository = repository
        self.retrieval = retrieval

    def build(self, task: str, project: str | None = None) -> ContextBundle:
        limit = self.config.context_result_limit
        experience_hits = self.retrieval.search(task, limit=limit, kinds={"experience"})
        pattern_hits = self.retrieval.search(task, limit=max(2, limit // 2), kinds={"pattern"})
        failure_hits = self.retrieval.search(task, limit=max(3, limit), kinds={"failure"})

        experiences: list[dict[str, Any]] = []
        known_failures_by_experience: dict[str, list[str]] = {}
        analogy_notes: list[str] = []
        for hit in experience_hits:
            value = self.repository.get_experience(hit.id)
            known_failures_by_experience[value.id] = value.known_failures
            preferred = next(
                (
                    item
                    for item in value.solution_versions
                    if f"{item.id}:v{item.version:03d}" == value.current_solution
                ),
                None,
            )
            latest = max(value.solution_versions, key=lambda item: item.created_at, default=None)
            experiences.append(
                {
                    "id": value.id,
                    "title": value.title,
                    "score": hit.score,
                    "problem_class": value.problem_class,
                    "capabilities": value.capabilities_required,
                    "technologies": [*value.technologies, *value.engines, *value.languages][:6],
                    "preferred_solution": self._solution_summary(preferred),
                    "latest_solution": self._solution_summary(latest) if latest != preferred else None,
                    "transferability": self._shorten(value.transferability, 240),
                    "validation_status": value.validation_status,
                    "confidence": value.confidence,
                    "source_path": str(self.repository.find_experience_path(value.id).relative_to(self.config.root)),
                }
            )
            if hit.analogy:
                analogy_notes.append(f"{value.id}: {hit.compatibility_note}")

        patterns = []
        for hit in pattern_hits:
            value = self.repository.get_pattern(hit.id)
            patterns.append(
                {
                    "id": value.id,
                    "name": value.name,
                    "score": hit.score,
                    "description": value.description,
                    "conditions": value.conditions,
                    "tradeoffs": value.tradeoffs,
                    "confidence": value.confidence,
                    "status": value.status,
                }
            )

        # A relevant experience can carry a crucial failure even when the task
        # wording does not lexically match that failure's title or description.
        hit_scores = {hit.id: hit.score for hit in failure_hits}
        linked_ids = []
        for item in experiences[:2]:
            known = known_failures_by_experience[item["id"]]
            ranked = sorted(
                enumerate(known),
                key=lambda pair: (-hit_scores.get(pair[1], 0), -pair[0]),
            )
            linked_ids.extend(failure_id for _, failure_id in ranked[:2])
        ordered_ids = list(dict.fromkeys([*linked_ids, *(hit.id for hit in failure_hits)]))
        experience_scores = {item["id"]: item["score"] for item in experiences}
        failures = []
        for failure_id in ordered_ids:
            value = self.repository.get_failure(failure_id)
            failures.append(
                {
                    "id": value.id,
                    "experience_id": value.experience_id,
                    "score": hit_scores.get(failure_id, experience_scores.get(value.experience_id, 0) * 0.85),
                    "attempt": self._shorten(value.attempt, 180),
                    "reason_failed": self._shorten(value.reason_failed, 220),
                    "workaround": self._shorten(value.workaround, 180),
                    "resolved_by": value.resolved_by,
                    "source_path": f"memory/failures/{value.id}.json",
                }
            )

        project_context = None
        if project:
            try:
                project_context = self._project_summary(
                    self.repository.get_project(project).model_dump(mode="json"), task
                )
            except RepositoryError:
                project_context = {"name": project, "status": "No project memory found"}

        bundle = ContextBundle(
            task=task,
            project=project,
            experiences=experiences,
            patterns=patterns,
            failures_to_avoid=failures,
            project_context=project_context,
            analogy_notes=analogy_notes,
        )
        self._fit_budget(bundle)
        return bundle

    def _fit_budget(self, bundle: ContextBundle) -> None:
        budget = self.config.context_token_budget
        estimate = self._estimate(bundle)
        while estimate > budget:
            bundle.truncated = True
            if len(bundle.experiences) > 1:
                removed = bundle.experiences.pop()
                bundle.analogy_notes = [
                    note for note in bundle.analogy_notes if not note.startswith(f"{removed['id']}:")
                ]
            elif len(bundle.failures_to_avoid) > 2:
                bundle.failures_to_avoid.pop()
            elif len(bundle.patterns) > 1:
                bundle.patterns.pop()
            elif bundle.project_context and any(
                bundle.project_context.get(field)
                for field in ("decisions", "constraints", "technologies", "experience_refs", "summary")
            ):
                for field in ("decisions", "constraints", "technologies", "experience_refs", "summary"):
                    if bundle.project_context.get(field):
                        bundle.project_context.pop(field)
                        break
            elif len(bundle.failures_to_avoid) > 1:
                bundle.failures_to_avoid.pop()
            elif bundle.patterns:
                bundle.patterns.pop()
            elif bundle.project_context:
                bundle.project_context = None
            elif bundle.failures_to_avoid:
                bundle.failures_to_avoid.pop()
            elif bundle.experiences:
                removed = bundle.experiences.pop()
                bundle.analogy_notes = [
                    note for note in bundle.analogy_notes if not note.startswith(f"{removed['id']}:")
                ]
            elif bundle.analogy_notes:
                bundle.analogy_notes.pop()
            elif bundle.task:
                bundle.task = bundle.task[: max(0, len(bundle.task) - 40)]
            else:
                break
            estimate = self._estimate(bundle)
        bundle.estimated_tokens = estimate

    @staticmethod
    def _shorten(value: str, limit: int) -> str:
        return value if len(value) <= limit else value[: limit - 1].rstrip() + "…"

    @classmethod
    def _solution_summary(cls, solution: Any) -> dict[str, Any] | None:
        if solution is None:
            return None
        return {
            "id": f"{solution.id}:v{solution.version:03d}",
            "description": cls._shorten(solution.description, 300),
            "status": solution.status,
            "test_results": [cls._shorten(item, 140) for item in solution.test_results[:2]],
        }

    @classmethod
    def _project_summary(cls, project: dict[str, Any], task: str) -> dict[str, Any]:
        task_terms = tokenize(task)

        def relevant(values: list[str], count: int) -> list[str]:
            ranked = sorted(
                enumerate(values),
                key=lambda pair: (-len(task_terms & tokenize(pair[1])), pair[0]),
            )
            return [cls._shorten(value, 180) for _, value in ranked[:count]]

        return {
            "id": project["id"],
            "name": project["name"],
            "summary": cls._shorten(project.get("summary", ""), 400),
            "technologies": relevant(project.get("technologies", []), 3),
            "constraints": relevant(project.get("constraints", []), 3),
            "decisions": relevant(project.get("decisions", []), 2),
            "experience_refs": project.get("experience_refs", [])[:5],
            "source_path": f"memory/projects/{project['id']}/project.json",
            "details_omitted": True,
        }

    @classmethod
    def _estimate(cls, bundle: ContextBundle) -> int:
        markdown_size = len(cls.to_markdown(bundle))
        json_size = len(json.dumps(bundle.model_dump(mode="json"), indent=2, ensure_ascii=False))
        return max(1, (max(markdown_size, json_size) + 3) // 4)

    @staticmethod
    def to_markdown(bundle: ContextBundle) -> str:
        lines = ["# Forge Context Bundle", "", f"**Task:** {bundle.task}"]
        if bundle.project:
            lines.extend([f"**Project:** {bundle.project}", ""])
        lines.extend(["", "## Relevant experiences", ""])
        if not bundle.experiences:
            lines.append("No relevant experience found.")
        for item in bundle.experiences:
            lines.append(f"### {item['id']} — {item['title']} (score {item['score']:.2f})")
            lines.append(f"- Capabilities: {', '.join(item['capabilities']) or 'not recorded'}")
            lines.append(f"- Technologies: {', '.join(item['technologies']) or 'technology-independent'}")
            lines.append(f"- Validation: {item['validation_status']} (confidence {item['confidence']:.2f})")
            if item["preferred_solution"]:
                solution = item["preferred_solution"]
                lines.append(f"- Preferred solution {solution['id']} ({solution['status']}): {solution['description']}")
            if item["latest_solution"]:
                solution = item["latest_solution"]
                lines.append(f"- Latest solution {solution['id']} ({solution['status']}): {solution['description']}")
            if item["transferability"]:
                lines.append(f"- Transferability: {item['transferability']}")
            lines.append(f"- Details: {item['source_path']}")
            lines.append("")
        lines.extend(["## Failures to avoid", ""])
        if not bundle.failures_to_avoid:
            lines.append("No pertinent failure recorded.")
        for item in bundle.failures_to_avoid:
            lines.append(f"- **{item['id']}**: {item['attempt']} — {item['reason_failed']}")
            if item["workaround"]:
                lines.append(f"  Workaround: {item['workaround']}")
            lines.append(f"  Details: {item['source_path']}")
        lines.extend(["", "## Transferable patterns", ""])
        if not bundle.patterns:
            lines.append("No pertinent pattern recorded.")
        for item in bundle.patterns:
            lines.append(f"- **{item['id']} — {item['name']}**: {item['description']}")
        if bundle.analogy_notes:
            lines.extend(["", "## Cross-technology cautions", ""])
            lines.extend(f"- {note}" for note in bundle.analogy_notes)
        if bundle.project_context:
            lines.extend(["", "## Project context (summary; open source only if needed)", "", "```json", json.dumps(bundle.project_context, ensure_ascii=False), "```"])
        lines.extend(["", f"_Estimated tokens: {bundle.estimated_tokens}; truncated: {str(bundle.truncated).lower()}_"])
        return "\n".join(lines) + "\n"

