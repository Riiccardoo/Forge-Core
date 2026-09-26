"""Hybrid retrieval over lexical index and structured memory fields."""

from __future__ import annotations

from pathlib import Path
from typing import Mapping

from forge.config import ForgeConfig
from forge.core.index import SearchIndex
from forge.core.models import ProblemSpec, SearchHit
from forge.core.repository import FileMemoryRepository
from forge.core.similarity import experience_similarity, infer_capabilities, jaccard, problem_spec, tokenize


class RetrievalEngine:
    def __init__(self, config: ForgeConfig, repository: FileMemoryRepository, index: SearchIndex | None = None):
        self.config = config
        self.repository = repository
        self.index = index or SearchIndex(config, repository)

    def search(
        self,
        query: str | Mapping[str, object] | ProblemSpec,
        *,
        limit: int = 10,
        kinds: set[str] | None = None,
    ) -> list[SearchHit]:
        spec = problem_spec(query)
        lexical = self.index.search(spec.text, limit=max(limit * 4, 20))
        hits: list[SearchHit] = []
        if kinds is None or "experience" in kinds:
            for item in self.repository.list_experiences():
                breakdown = experience_similarity(spec, item, self.config.similarity_weights)
                lexical_score = lexical.get(item.id, 0.0)
                combined = 0.72 * breakdown.score + 0.28 * lexical_score
                if breakdown.analogy:
                    combined = min(1.0, combined + 0.06)
                if combined <= 0:
                    continue
                path = self.repository.find_experience_path(item.id)
                note = ""
                if breakdown.analogy:
                    note = "Transferable capabilities match, but technology-specific APIs/code require compatibility verification."
                hits.append(
                    SearchHit(
                        id=item.id,
                        kind="experience",
                        title=item.title,
                        score=round(combined, 6),
                        lexical_score=lexical_score,
                        structured_score=breakdown.score,
                        capability_overlap=list(breakdown.capability_overlap),
                        technology_overlap=list(breakdown.technology_overlap),
                        analogy=breakdown.analogy,
                        compatibility_note=note,
                        path=self._relative(path),
                    )
                )
        query_tokens = tokenize(spec.text)
        query_caps = set(spec.capabilities_required) | infer_capabilities(spec.text)
        if kinds is None or "pattern" in kinds:
            for item in self.repository.list_patterns():
                body = " ".join([item.name, item.description, *item.problem_classes, *item.applicable_domains, *item.conditions, *item.tags])
                structural = max(jaccard(query_tokens, tokenize(body)), jaccard(query_caps, tokenize(body)))
                lexical_score = lexical.get(item.id, 0.0)
                score = 0.60 * structural + 0.40 * lexical_score
                if score > 0:
                    path = next(self.config.memory_path.glob(f"patterns/{item.id}-*/pattern.json"))
                    hits.append(
                        SearchHit(
                            id=item.id,
                            kind="pattern",
                            title=item.name,
                            score=round(score, 6),
                            lexical_score=lexical_score,
                            structured_score=structural,
                            path=self._relative(path),
                        )
                    )
        if kinds is None or "failure" in kinds:
            for item in self.repository.list_failures():
                body = " ".join([item.attempt, item.reason_failed, item.error_signature, *item.conditions, item.workaround])
                structural = max(jaccard(query_tokens, tokenize(body)), jaccard(query_caps, infer_capabilities(body)))
                lexical_score = lexical.get(item.id, 0.0)
                score = 0.58 * structural + 0.42 * lexical_score
                if score > 0:
                    path = self.config.memory_path / "failures" / f"{item.id}.json"
                    hits.append(
                        SearchHit(
                            id=item.id,
                            kind="failure",
                            title=item.attempt,
                            score=round(score, 6),
                            lexical_score=lexical_score,
                            structured_score=structural,
                            path=self._relative(path),
                        )
                    )
        kind_priority = {"failure": 0, "experience": 1, "pattern": 2}
        hits.sort(key=lambda item: (-item.score, kind_priority[item.kind], item.id))
        return hits[:limit]

    def related(self, experience_id: str, limit: int = 5) -> list[SearchHit]:
        item = self.repository.get_experience(experience_id)
        spec = ProblemSpec(
            text=" ".join([item.title, item.problem_description, item.summary]),
            problem_class=item.problem_class,
            domains=item.domains,
            capabilities_required=item.capabilities_required,
            inputs=item.inputs,
            outputs=item.outputs,
            constraints=item.constraints,
            technologies=[*item.technologies, *item.engines, *item.languages],
            tools=item.tools,
            tags=item.tags,
        )
        return [hit for hit in self.search(spec, limit=limit + 1, kinds={"experience"}) if hit.id != experience_id][:limit]

    def _relative(self, path: Path) -> str:
        try:
            return str(path.relative_to(self.config.root))
        except ValueError:
            return str(path)
