"""Explainable structured similarity with cross-technology capability matching."""

from __future__ import annotations

from dataclasses import dataclass
import re
import unicodedata
from typing import Iterable, Mapping, Protocol

from forge.config import DEFAULT_WEIGHTS
from forge.core.models import Experience, ProblemSpec


TOKEN_RE = re.compile(r"[a-z0-9][a-z0-9_+#.-]*")
STOP_WORDS = {
    "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "is",
    "it", "of", "on", "or", "the", "to", "with", "un", "una", "il", "lo", "la",
    "i", "gli", "le", "di", "da", "in", "con", "su", "per", "tra", "fra", "e",
}

CAPABILITY_HINTS: dict[str, tuple[str, ...]] = {
    "world_modification": ("world", "map", "terrain", "mondo", "mappa"),
    "spatial_placement": ("placement", "place", "position", "spawn", "layout", "posizion"),
    "object_creation": ("building", "object", "entity", "actor", "prefab", "edific", "oggett"),
    "procedural_generation": ("procedural", "programmatic", "generate", "generation", "procedur", "generat"),
    "persistence": ("persist", "save", "storage", "durable", "salvat"),
    "coordinate_management": ("coordinate", "transform", "vector", "grid", "coordinat"),
    "validation": ("validate", "validation", "test", "verify", "validaz", "verific"),
    "data_transformation": ("transform", "convert", "mapping", "conversion", "trasform", "convert"),
    "concurrency_control": ("concurrent", "parallel", "race", "lock", "concorren", "parallel"),
    "error_recovery": ("retry", "recover", "fallback", "rollback", "recuper", "errore"),
}

TECHNOLOGY_HINTS = {
    "unreal engine": ("unreal", "ue4", "ue5"),
    "hytale": ("hytale",),
    "unity": ("unity",),
    "python": ("python",),
    "javascript": ("javascript", "js", "typescript", "ts"),
    "sqlite": ("sqlite", "fts5"),
}


class SemanticBackend(Protocol):
    """Optional future semantic scorer; Forge v0.1 does not require one."""

    def score(self, query: str, document: str) -> float:
        """Return a normalized score between zero and one."""


@dataclass(frozen=True, slots=True)
class SimilarityBreakdown:
    score: float
    components: dict[str, float]
    capability_overlap: tuple[str, ...]
    technology_overlap: tuple[str, ...]
    analogy: bool


def normalize_text(value: str) -> str:
    ascii_value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode("ascii")
    return ascii_value.lower().replace("-", "_")


def tokenize(value: str) -> set[str]:
    return {token for token in TOKEN_RE.findall(normalize_text(value)) if token not in STOP_WORDS and len(token) > 1}


def normalize_terms(values: Iterable[str]) -> set[str]:
    terms: set[str] = set()
    for value in values:
        normalized = normalize_text(str(value)).strip().replace(" ", "_")
        if normalized:
            terms.add(normalized)
            terms.update(tokenize(str(value)))
    return terms


def infer_capabilities(text: str) -> set[str]:
    normalized = normalize_text(text)
    inferred: set[str] = set()
    for capability, hints in CAPABILITY_HINTS.items():
        if any(hint in normalized for hint in hints):
            inferred.add(capability)
    return inferred


def infer_technologies(text: str) -> set[str]:
    normalized = normalize_text(text)
    return {
        technology.replace(" ", "_")
        for technology, hints in TECHNOLOGY_HINTS.items()
        if any(re.search(rf"\b{re.escape(hint)}\b", normalized) for hint in hints)
    }


def problem_spec(value: str | Mapping[str, object] | ProblemSpec) -> ProblemSpec:
    if isinstance(value, ProblemSpec):
        spec = value
    elif isinstance(value, str):
        spec = ProblemSpec(text=value)
    else:
        raw = dict(value)
        if "text" not in raw:
            raw["text"] = str(raw.get("problem_description") or raw.get("problem") or raw.get("summary") or "")
        spec = ProblemSpec.model_validate(raw)
    inferred_caps = infer_capabilities(spec.text)
    inferred_tech = infer_technologies(spec.text)
    return spec.model_copy(
        update={
            "capabilities_required": sorted(set(spec.capabilities_required) | inferred_caps),
            "technologies": sorted(set(spec.technologies) | inferred_tech),
        }
    )


def jaccard(left: set[str], right: set[str]) -> float:
    if not left or not right:
        return 0.0
    return len(left & right) / len(left | right)


def containment(left: set[str], right: set[str]) -> float:
    if not left or not right:
        return 0.0
    return len(left & right) / min(len(left), len(right))


def _field_score(query: Iterable[str], candidate: Iterable[str]) -> float:
    left = normalize_terms(query)
    right = normalize_terms(candidate)
    return 0.55 * jaccard(left, right) + 0.45 * containment(left, right)


def experience_similarity(
    query: ProblemSpec,
    experience: Experience,
    weights: Mapping[str, float] | None = None,
) -> SimilarityBreakdown:
    selected = dict(DEFAULT_WEIGHTS)
    if weights:
        selected.update(weights)
    query_caps = normalize_terms(query.capabilities_required) | infer_capabilities(query.text)
    experience_caps = normalize_terms(experience.capabilities_required)
    query_tech = normalize_terms(query.technologies) | infer_technologies(query.text)
    experience_tech = normalize_terms([*experience.technologies, *experience.engines, *experience.languages])
    candidate_text = " ".join(
        [experience.title, experience.summary, experience.problem_description, experience.problem_class, experience.transferability]
    )
    components = {
        "text": 0.55 * jaccard(tokenize(query.text), tokenize(candidate_text))
        + 0.45 * containment(tokenize(query.text), tokenize(candidate_text)),
        "problem_class": _field_score([query.problem_class] if query.problem_class else tokenize(query.text), [experience.problem_class]),
        "domains": _field_score(query.domains or tokenize(query.text), experience.domains),
        "capabilities_required": 0.55 * jaccard(query_caps, experience_caps)
        + 0.45 * containment(query_caps, experience_caps),
        "inputs": _field_score(query.inputs, experience.inputs),
        "outputs": _field_score(query.outputs, experience.outputs),
        "constraints": _field_score(query.constraints, experience.constraints),
        "technologies": 0.55 * jaccard(query_tech, experience_tech) + 0.45 * containment(query_tech, experience_tech),
        "tools": _field_score(query.tools, experience.tools),
        "tags": _field_score(query.tags or tokenize(query.text), experience.tags),
    }
    available = {
        "text": bool(query.text.strip()),
        "problem_class": bool(query.problem_class.strip()),
        "domains": bool(query.domains),
        "capabilities_required": bool(query_caps),
        "inputs": bool(query.inputs),
        "outputs": bool(query.outputs),
        "constraints": bool(query.constraints),
        "technologies": bool(query_tech),
        "tools": bool(query.tools),
        "tags": bool(query.tags),
    }
    active_weight = sum(weight for key, weight in selected.items() if available.get(key, False))
    raw = sum(selected.get(key, 0.0) * value for key, value in components.items())
    # Preserve meaningful partial matches: absent optional fields should not dilute
    # a capability-rich query all the way to zero.
    normalized = raw / active_weight if active_weight else 0.0
    coverage = min(1.0, active_weight / 0.55)
    score = normalized * (0.78 + 0.22 * coverage)
    capability_overlap = tuple(sorted(query_caps & experience_caps))
    technology_overlap = tuple(sorted(query_tech & experience_tech))
    analogy = bool(capability_overlap) and not technology_overlap and components["capabilities_required"] >= 0.35
    if analogy:
        score = min(1.0, score + 0.08)
    return SimilarityBreakdown(
        score=round(max(0.0, min(score, 1.0)), 6),
        components={key: round(value, 6) for key, value in components.items()},
        capability_overlap=capability_overlap,
        technology_overlap=technology_overlap,
        analogy=analogy,
    )
