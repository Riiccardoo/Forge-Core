"""Build concise, agent-ready context bundles."""

from __future__ import annotations

import json
from typing import Any

from forge.config import ForgeConfig
from forge.core.models import ContextBundle
from forge.core.repository import FileMemoryRepository, RepositoryError
from forge.core.retrieval import RetrievalEngine


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
        analogy_notes: list[str] = []
        for hit in experience_hits:
            value = self.repository.get_experience(hit.id)
            preferred = next(
                (
                    item
                    for item in value.solution_versions
                    if f"{item.id}:v{item.version:03d}" == value.current_solution
                ),
                None,
            )
            experiences.append(
                {
                    "id": value.id,
                    "title": value.title,
                    "score": hit.score,
                    "problem_class": value.problem_class,
                    "capabilities": value.capabilities_required,
                    "technologies": [*value.technologies, *value.engines, *value.languages],
                    "preferred_solution": preferred.model_dump(mode="json") if preferred else None,
                    "transferability": value.transferability,
                    "validation_status": value.validation_status,
                    "confidence": value.confidence,
                    "source_refs": [item.model_dump(mode="json") for item in value.source_refs],
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

        failures = []
        for hit in failure_hits:
            value = self.repository.get_failure(hit.id)
            failures.append(
                {
                    "id": value.id,
                    "experience_id": value.experience_id,
                    "score": hit.score,
                    "attempt": value.attempt,
                    "reason_failed": value.reason_failed,
                    "error_signature": value.error_signature,
                    "conditions": value.conditions,
                    "workaround": value.workaround,
                    "resolved_by": value.resolved_by,
                }
            )

        project_context = None
        if project:
            try:
                project_context = self.repository.get_project(project).model_dump(mode="json")
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
        serialized = bundle.model_dump_json(exclude={"estimated_tokens", "truncated"})
        estimate = max(1, len(serialized) // 4)
        while estimate > budget and (bundle.failures_to_avoid or bundle.patterns or bundle.experiences):
            bundle.truncated = True
            collections = [bundle.experiences, bundle.failures_to_avoid, bundle.patterns]
            largest = max(collections, key=lambda value: len(json.dumps(value, default=str)))
            if largest:
                largest.pop()
            serialized = bundle.model_dump_json(exclude={"estimated_tokens", "truncated"})
            estimate = max(1, len(serialized) // 4)
        bundle.estimated_tokens = estimate

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
                lines.append(f"- Preferred solution: {item['preferred_solution']['description']}")
            if item["transferability"]:
                lines.append(f"- Transferability: {item['transferability']}")
            lines.append("")
        lines.extend(["## Failures to avoid", ""])
        if not bundle.failures_to_avoid:
            lines.append("No pertinent failure recorded.")
        for item in bundle.failures_to_avoid:
            lines.append(f"- **{item['id']}**: {item['attempt']} — {item['reason_failed']}")
            if item["workaround"]:
                lines.append(f"  Workaround: {item['workaround']}")
        lines.extend(["", "## Transferable patterns", ""])
        if not bundle.patterns:
            lines.append("No pertinent pattern recorded.")
        for item in bundle.patterns:
            lines.append(f"- **{item['id']} — {item['name']}**: {item['description']}")
        if bundle.analogy_notes:
            lines.extend(["", "## Cross-technology cautions", ""])
            lines.extend(f"- {note}" for note in bundle.analogy_notes)
        if bundle.project_context:
            lines.extend(["", "## Project context", "", "```json", json.dumps(bundle.project_context, indent=2), "```"])
        lines.extend(["", f"_Estimated tokens: {bundle.estimated_tokens}; truncated: {str(bundle.truncated).lower()}_"])
        return "\n".join(lines) + "\n"

