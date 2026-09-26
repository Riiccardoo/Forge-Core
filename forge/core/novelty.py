"""Data-based novelty classification."""

from __future__ import annotations

from typing import Mapping

from forge.config import ForgeConfig
from forge.core.models import NoveltyResult, ProblemSpec
from forge.core.retrieval import RetrievalEngine
from forge.core.similarity import problem_spec


def detect_novelty(
    problem: str | Mapping[str, object] | ProblemSpec,
    retrieval: RetrievalEngine,
    config: ForgeConfig,
) -> NoveltyResult:
    spec = problem_spec(problem)
    event = (spec.event_type or "").strip().lower()
    matches = retrieval.search(spec, limit=5, kinds={"experience"})
    identifiers = [item.id for item in matches]
    scores = {item.id: item.score for item in matches}
    top = matches[0] if matches else None

    if event in {"failure", "failed_attempt", "new_failure"} or spec.failed_attempt:
        if top and top.score >= config.novelty_threshold * 0.7:
            return NoveltyResult(
                decision="NEW_FAILURE",
                similar_experiences=identifiers,
                similarity_scores=scores,
                reasoning_summary=f"A failed approach was supplied and {top.id} is the closest known experience ({top.score:.2f}); record the failure there.",
            )
        return NoveltyResult(
            decision="NEW_EXPERIENCE",
            similar_experiences=identifiers,
            similarity_scores=scores,
            reasoning_summary="A failed approach was supplied, but no sufficiently related experience exists to own it.",
        )
    if event in {"pattern", "new_pattern"}:
        return NoveltyResult(
            decision="NEW_PATTERN",
            similar_experiences=identifiers,
            similarity_scores=scores,
            reasoning_summary="The input explicitly describes a transferable pattern; preserve it separately from a technology-specific solution.",
        )
    if event in {"project", "project_update", "project_only"}:
        return NoveltyResult(
            decision="PROJECT_ONLY_UPDATE",
            similar_experiences=identifiers,
            similarity_scores=scores,
            reasoning_summary="The input is explicitly project-scoped and should not be promoted to general technical knowledge.",
        )
    if top is None or top.score < config.novelty_threshold:
        observed = f" Best known match is {top.id} at {top.score:.2f}." if top else ""
        return NoveltyResult(
            decision="NEW_EXPERIENCE",
            similar_experiences=identifiers,
            similarity_scores=scores,
            reasoning_summary=f"No existing experience reaches the configured similarity threshold ({config.novelty_threshold:.2f}).{observed}",
        )
    if spec.proposed_solution or event in {"solution", "solution_version", "new_solution"}:
        return NoveltyResult(
            decision="NEW_SOLUTION_VERSION",
            similar_experiences=identifiers,
            similarity_scores=scores,
            reasoning_summary=f"The problem matches {top.id} ({top.score:.2f}) and introduces a solution candidate; preserve it as lineage rather than overwriting history.",
        )
    return NoveltyResult(
        decision="UPDATE_EXISTING",
        similar_experiences=identifiers,
        similarity_scores=scores,
        reasoning_summary=f"The closest experience {top.id} scores {top.score:.2f}, above the configured threshold ({config.novelty_threshold:.2f}).",
    )

