"""Solution lineage inspection and balanced metric comparison."""

from __future__ import annotations

from typing import Any

from forge.core.models import SolutionVersion


_LEVELS = {"low": 0.25, "medium": 0.5, "high": 0.75, "very high": 1.0}


def _numeric(value: Any, *, inverse: bool = False) -> float | None:
    if value is None:
        return None
    if isinstance(value, (int, float)):
        score = max(0.0, min(float(value), 1.0))
    else:
        score = _LEVELS.get(str(value).strip().lower())
    if score is None:
        return None
    return 1.0 - score if inverse else score


def compare_solution_versions(versions: list[SolutionVersion]) -> list[dict[str, Any]]:
    """Return a balanced comparison without declaring a universal winner."""

    rows: list[dict[str, Any]] = []
    for value in versions:
        measures = {
            "reliability": _numeric(value.metrics.reliability),
            "maintainability": _numeric(value.metrics.maintainability),
            "performance": _numeric(value.metrics.performance),
            "compatibility": _numeric(value.metrics.compatibility),
            "implementation_simplicity": _numeric(value.metrics.implementation_complexity, inverse=True),
            "cost_efficiency": _numeric(value.metrics.execution_cost, inverse=True),
        }
        known = [item for item in measures.values() if item is not None]
        rows.append(
            {
                "solution": f"{value.id}:v{value.version:03d}",
                "status": value.status,
                "dimensions": measures,
                "balanced_score": round(sum(known) / len(known), 3) if known else None,
                "caveat": "Compare dimensions against current constraints; this score is not an automatic selection.",
            }
        )
    return rows

