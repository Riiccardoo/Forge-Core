"""Whole-memory validation."""

from __future__ import annotations

from typing import Any

from forge.core.repository import FileMemoryRepository, RepositoryError


def validate_memory(repository: FileMemoryRepository) -> dict[str, Any]:
    errors: list[str] = []
    warnings: list[str] = []
    experiences = []
    patterns = []
    failures = []
    projects = []
    for label, loader, target in (
        ("experiences", repository.list_experiences, experiences),
        ("patterns", repository.list_patterns, patterns),
        ("failures", repository.list_failures, failures),
        ("projects", repository.list_projects, projects),
    ):
        try:
            target.extend(loader())
        except RepositoryError as exc:
            errors.append(f"{label}: {exc}")

    experience_ids = {item.id for item in experiences}
    pattern_ids = {item.id for item in patterns}
    solution_refs = {
        f"{version.id}:v{version.version:03d}"
        for experience in experiences
        for version in experience.solution_versions
    }
    for experience in experiences:
        for related in experience.related_experiences:
            if related not in experience_ids:
                warnings.append(f"{experience.id} references missing experience {related}")
        for pattern in experience.patterns:
            if pattern not in pattern_ids:
                warnings.append(f"{experience.id} references missing pattern {pattern}")
        if experience.current_solution and experience.current_solution not in solution_refs:
            errors.append(f"{experience.id} current_solution does not exist: {experience.current_solution}")
    for failure in failures:
        if failure.experience_id not in experience_ids:
            errors.append(f"{failure.id} references missing experience {failure.experience_id}")
        if failure.resolved_by and failure.resolved_by not in solution_refs:
            warnings.append(f"{failure.id} resolved_by is not present: {failure.resolved_by}")
    for pattern in patterns:
        for experience_id in pattern.related_experiences:
            if experience_id not in experience_ids:
                warnings.append(f"{pattern.id} references missing experience {experience_id}")
    return {
        "valid": not errors,
        "errors": errors,
        "warnings": warnings,
        "counts": {
            "experiences": len(experiences),
            "patterns": len(patterns),
            "failures": len(failures),
            "projects": len(projects),
        },
    }
