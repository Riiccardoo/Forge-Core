"""Git-friendly file repository for Forge memory."""

from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import time
from typing import Any, Iterator, TypeVar

from pydantic import BaseModel

from forge.config import ForgeConfig
from forge.core.models import (
    EvolutionProposal,
    Experience,
    Failure,
    Pattern,
    ProjectMemory,
    SolutionVersion,
    utc_now,
)


T = TypeVar("T", bound=BaseModel)


class RepositoryError(RuntimeError):
    """Raised when memory cannot be read or safely changed."""


def slugify(value: str) -> str:
    normalized = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    return normalized[:64] or "untitled"


class FileMemoryRepository:
    """Store canonical knowledge in JSON and Markdown files.

    The SQLite index is deliberately not managed here: it is a disposable read
    optimization built from this repository.
    """

    def __init__(self, config: ForgeConfig):
        self.config = config
        self.memory_path = config.memory_path

    def initialize(self) -> None:
        if self.config.read_only:
            raise RepositoryError("Forge is configured read-only")
        directories = [
            "experiences",
            "patterns",
            "projects",
            "failures",
            "benchmarks",
            "evolution/proposals",
        ]
        for relative in directories:
            (self.memory_path / relative).mkdir(parents=True, exist_ok=True)
        self.config.index_path.parent.mkdir(parents=True, exist_ok=True)

    def status(self) -> dict[str, Any]:
        return {
            "memory_path": str(self.memory_path),
            "index_path": str(self.config.index_path),
            "initialized": self.memory_path.is_dir(),
            "experiences": len(self.list_experiences()),
            "patterns": len(self.list_patterns()),
            "failures": len(self.list_failures()),
            "projects": len(self.list_projects()),
            "evolution_proposals": len(self.list_evolution_proposals()),
            "index_exists": self.config.index_path.exists(),
            "read_only": self.config.read_only,
        }

    @contextmanager
    def _id_lock(self) -> Iterator[None]:
        lock_path = self.memory_path / ".id.lock"
        lock_path.parent.mkdir(parents=True, exist_ok=True)
        deadline = time.monotonic() + 5.0
        descriptor: int | None = None
        while descriptor is None:
            try:
                descriptor = os.open(lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            except FileExistsError:
                if time.monotonic() >= deadline:
                    raise RepositoryError("Timed out waiting for the Forge ID lock")
                time.sleep(0.05)
        try:
            os.write(descriptor, str(os.getpid()).encode("ascii"))
            os.close(descriptor)
            descriptor = None
            yield
        finally:
            if descriptor is not None:
                os.close(descriptor)
            lock_path.unlink(missing_ok=True)

    def _next_id(self, prefix: str) -> str:
        pattern = re.compile(rf"^{re.escape(prefix)}-(\d{{6}})$")
        maximum = 0
        for path in self.memory_path.rglob("*.json") if self.memory_path.exists() else []:
            match = pattern.match(path.stem)
            if match:
                maximum = max(maximum, int(match.group(1)))
                continue
            try:
                value = json.loads(path.read_text(encoding="utf-8")).get("id", "")
            except (OSError, json.JSONDecodeError, AttributeError):
                continue
            match = pattern.match(value)
            if match:
                maximum = max(maximum, int(match.group(1)))
        return f"{prefix}-{maximum + 1:06d}"

    @staticmethod
    def _read_model(path: Path, model: type[T]) -> T:
        try:
            return model.model_validate_json(path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as exc:
            raise RepositoryError(f"Cannot read {path}: {exc}") from exc

    @staticmethod
    def _write_model(path: Path, value: BaseModel) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_suffix(path.suffix + f".{os.getpid()}.tmp")
        data = value.model_dump_json(indent=2, exclude_none=False) + "\n"
        try:
            temporary.write_text(data, encoding="utf-8", newline="\n")
            os.replace(temporary, path)
        finally:
            temporary.unlink(missing_ok=True)

    def create_experience(self, payload: dict[str, Any]) -> Experience:
        self._assert_writable()
        with self._id_lock():
            identifier = self._next_id("EXP")
            now = utc_now()
            experience = Experience.model_validate(
                {**payload, "id": identifier, "created_at": now, "updated_at": now}
            )
            directory = self.memory_path / "experiences" / f"{identifier}-{slugify(experience.title)}"
            if directory.exists():
                raise RepositoryError(f"Experience directory already exists: {directory}")
            self._write_model(directory / "capsule.json", experience)
            (directory / "solutions").mkdir(exist_ok=True)
            (directory / "failures").mkdir(exist_ok=True)
            (directory / "evidence").mkdir(exist_ok=True)
            self._write_experience_readme(directory, experience)
        return experience

    def update_experience(self, experience_id: str, changes: dict[str, Any]) -> Experience:
        self._assert_writable()
        path = self.find_experience_path(experience_id)
        existing = self._read_model(path, Experience)
        forbidden = {"id", "created_at"}
        if forbidden.intersection(changes):
            raise RepositoryError("Experience id and created_at are immutable")
        merged = existing.model_dump(mode="python")
        merged.update(changes)
        merged["updated_at"] = utc_now()
        updated = Experience.model_validate(merged)
        self._write_model(path, updated)
        self._write_experience_readme(path.parent, updated)
        return updated

    def get_experience(self, experience_id: str) -> Experience:
        return self._read_model(self.find_experience_path(experience_id), Experience)

    def find_experience_path(self, experience_id: str) -> Path:
        matches = sorted((self.memory_path / "experiences").glob(f"{experience_id}-*/capsule.json"))
        if not matches:
            raise RepositoryError(f"Unknown experience: {experience_id}")
        if len(matches) > 1:
            raise RepositoryError(f"Duplicate experience id: {experience_id}")
        return matches[0]

    def list_experiences(self) -> list[Experience]:
        base = self.memory_path / "experiences"
        return [self._read_model(path, Experience) for path in sorted(base.glob("EXP-*/capsule.json"))] if base.exists() else []

    def add_solution_version(self, experience_id: str, payload: dict[str, Any]) -> SolutionVersion:
        self._assert_writable()
        with self._id_lock():
            experience = self.get_experience(experience_id)
            solution_id = str(payload.get("id") or self._next_id("SOL"))
            versions = [item.version for item in experience.solution_versions if item.id == solution_id]
            version_number = int(payload.get("version") or (max(versions, default=0) + 1))
            supersedes = payload.get("supersedes")
            if supersedes is None and versions:
                supersedes = f"{solution_id}:v{max(versions):03d}"
            solution = SolutionVersion.model_validate(
                {**payload, "id": solution_id, "version": version_number, "supersedes": supersedes}
            )
            if any(item.id == solution.id and item.version == solution.version for item in experience.solution_versions):
                raise RepositoryError(f"Duplicate solution version {solution.id}:v{solution.version:03d}")
            if solution.status == "preferred":
                experience.solution_versions = [
                    item.model_copy(update={"status": "working"}) if item.status == "preferred" else item
                    for item in experience.solution_versions
                ]
                experience.current_solution = f"{solution.id}:v{solution.version:03d}"
            experience.solution_versions.append(solution)
            if experience.current_solution is None:
                experience.current_solution = f"{solution.id}:v{solution.version:03d}"
            experience.updated_at = utc_now()
            capsule_path = self.find_experience_path(experience_id)
            self._write_model(capsule_path, experience)
            self._write_model(
                capsule_path.parent / "solutions" / f"{solution.id}-v{solution.version:03d}.json",
                solution,
            )
            self._write_experience_readme(capsule_path.parent, experience)
        return solution

    def register_failure(self, experience_id: str, payload: dict[str, Any]) -> Failure:
        self._assert_writable()
        with self._id_lock():
            experience = self.get_experience(experience_id)
            identifier = self._next_id("FAIL")
            failure = Failure.model_validate({**payload, "id": identifier, "experience_id": experience_id})
            self._write_model(self.memory_path / "failures" / f"{identifier}.json", failure)
            self._write_model(
                self.find_experience_path(experience_id).parent / "failures" / f"{identifier}.json",
                failure,
            )
            if identifier not in experience.known_failures:
                experience.known_failures.append(identifier)
            experience.updated_at = utc_now()
            capsule_path = self.find_experience_path(experience_id)
            self._write_model(capsule_path, experience)
            self._write_experience_readme(capsule_path.parent, experience)
        return failure

    def get_failure(self, failure_id: str) -> Failure:
        path = self.memory_path / "failures" / f"{failure_id}.json"
        if not path.exists():
            raise RepositoryError(f"Unknown failure: {failure_id}")
        return self._read_model(path, Failure)

    def list_failures(self) -> list[Failure]:
        base = self.memory_path / "failures"
        return [self._read_model(path, Failure) for path in sorted(base.glob("FAIL-*.json"))] if base.exists() else []

    def create_pattern(self, payload: dict[str, Any]) -> Pattern:
        self._assert_writable()
        with self._id_lock():
            identifier = self._next_id("PAT")
            now = utc_now()
            pattern = Pattern.model_validate({**payload, "id": identifier, "created_at": now, "updated_at": now})
            directory = self.memory_path / "patterns" / f"{identifier}-{slugify(pattern.name)}"
            self._write_model(directory / "pattern.json", pattern)
            self._write_pattern_readme(directory, pattern)
        return pattern

    def get_pattern(self, pattern_id: str) -> Pattern:
        matches = sorted((self.memory_path / "patterns").glob(f"{pattern_id}-*/pattern.json"))
        if len(matches) != 1:
            raise RepositoryError(f"Unknown or duplicate pattern: {pattern_id}")
        return self._read_model(matches[0], Pattern)

    def list_patterns(self) -> list[Pattern]:
        base = self.memory_path / "patterns"
        return [self._read_model(path, Pattern) for path in sorted(base.glob("PAT-*/pattern.json"))] if base.exists() else []

    def get_project(self, project: str) -> ProjectMemory:
        candidates = [
            self.memory_path / "projects" / slugify(project) / "project.json",
            self.memory_path / "projects" / project / "project.json",
        ]
        for path in candidates:
            if path.exists():
                return self._read_model(path, ProjectMemory)
        raise RepositoryError(f"Unknown project: {project}")

    def upsert_project(self, project: str, payload: dict[str, Any]) -> ProjectMemory:
        self._assert_writable()
        directory = self.memory_path / "projects" / slugify(project)
        path = directory / "project.json"
        existing: dict[str, Any] = {}
        if path.exists():
            existing = self._read_model(path, ProjectMemory).model_dump(mode="python")
        existing.update(payload)
        existing.update({"id": slugify(project), "name": payload.get("name", project), "updated_at": utc_now()})
        value = ProjectMemory.model_validate(existing)
        self._write_model(path, value)
        for name in ("decisions", "architecture", "constraints", "references"):
            (directory / name).mkdir(parents=True, exist_ok=True)
        return value

    def list_projects(self) -> list[ProjectMemory]:
        base = self.memory_path / "projects"
        return [self._read_model(path, ProjectMemory) for path in sorted(base.glob("*/project.json"))] if base.exists() else []

    def create_evolution_proposal(self, payload: dict[str, Any]) -> EvolutionProposal:
        self._assert_writable()
        with self._id_lock():
            identifier = self._next_id("EVP")
            proposal = EvolutionProposal.model_validate({**payload, "id": identifier})
            self._write_model(
                self.memory_path / "evolution" / "proposals" / f"{identifier}.json",
                proposal,
            )
        return proposal

    def list_evolution_proposals(self) -> list[EvolutionProposal]:
        base = self.memory_path / "evolution" / "proposals"
        return [self._read_model(path, EvolutionProposal) for path in sorted(base.glob("EVP-*.json"))] if base.exists() else []

    def _assert_writable(self) -> None:
        if self.config.read_only:
            raise RepositoryError("Forge is configured read-only")
        if not self.memory_path.exists():
            self.initialize()

    @staticmethod
    def _write_experience_readme(directory: Path, value: Experience) -> None:
        preferred = next(
            (item for item in value.solution_versions if f"{item.id}:v{item.version:03d}" == value.current_solution),
            None,
        )
        solution = preferred.description if preferred else "No solution has been recorded yet."
        lessons = value.transferability or "No transferable lesson recorded yet."
        failures = "\n".join(f"- {item}" for item in value.known_failures) or "- None recorded"
        patterns = "\n".join(f"- {item}" for item in value.patterns) or "- None recorded"
        related = "\n".join(f"- {item}" for item in value.related_experiences) or "- None recorded"
        content = f"""# {value.id}: {value.title}

## Problem

{value.problem_description or value.summary or "Not yet described."}

## Context

{value.context or "No additional context recorded."}

## Current Solution

{solution}

## Lessons

{lessons}

## Failures

{failures}

## Transferable Patterns

{patterns}

## Related Experiences

{related}
"""
        (directory / "README.md").write_text(content, encoding="utf-8", newline="\n")

    @staticmethod
    def _write_pattern_readme(directory: Path, value: Pattern) -> None:
        content = f"""# {value.id}: {value.name}

{value.description}

## Applies when

{chr(10).join(f"- {item}" for item in value.conditions) or "- Conditions not yet recorded"}

## Trade-offs

{chr(10).join(f"- {item}" for item in value.tradeoffs) or "- None recorded"}

## Evidence

{chr(10).join(f"- {item.reference}: {item.description}" for item in value.evidence) or "- None recorded"}
"""
        (directory / "README.md").write_text(content, encoding="utf-8", newline="\n")
