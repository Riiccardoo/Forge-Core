"""Stable application façade shared by CLI and MCP."""

from __future__ import annotations

from pathlib import Path
from typing import Any, Mapping

from forge.config import ForgeConfig
from forge.core.context_builder import ContextBuilder
from forge.core.index import SearchIndex
from forge.core.novelty import detect_novelty
from forge.core.repository import FileMemoryRepository
from forge.core.retrieval import RetrievalEngine
from forge.core.validation import validate_memory


class Forge:
    def __init__(self, root: Path | str | None = None, config_path: Path | str | None = None):
        self.config = ForgeConfig.load(root, config_path)
        self.repository = FileMemoryRepository(self.config)
        self.index = SearchIndex(self.config, self.repository)
        self.retrieval = RetrievalEngine(self.config, self.repository, self.index)
        self.context_builder = ContextBuilder(self.config, self.repository, self.retrieval)

    def initialize(self) -> dict[str, Any]:
        self.repository.initialize()
        self.index.rebuild()
        return self.repository.status()

    def status(self) -> dict[str, Any]:
        return self.repository.status()

    def doctor(self) -> dict[str, Any]:
        checks: dict[str, Any] = {
            "python": "3.12+",
            "memory_initialized": self.config.memory_path.is_dir(),
            "config_found": (self.config.root / "config" / "default.toml").exists(),
            "sqlite_fts5": False,
            "index_readable": False,
        }
        import sqlite3

        try:
            connection = sqlite3.connect(":memory:")
            connection.execute("CREATE VIRTUAL TABLE probe USING fts5(content)")
            connection.close()
            checks["sqlite_fts5"] = True
        except sqlite3.Error as exc:
            checks["sqlite_error"] = str(exc)
        if checks["memory_initialized"] and checks["sqlite_fts5"]:
            try:
                self.index.ensure()
                self.index.search("forge-health-probe", limit=1)
                checks["index_readable"] = True
            except Exception as exc:  # report diagnostics rather than hiding them
                checks["index_error"] = str(exc)
        checks["healthy"] = all(
            checks[key] for key in ("memory_initialized", "config_found", "sqlite_fts5", "index_readable")
        )
        return checks

    def rebuild_index(self) -> dict[str, int]:
        return self.index.rebuild()

    def create_experience(self, payload: dict[str, Any]) -> dict[str, Any]:
        value = self.repository.create_experience(payload)
        self.index.rebuild()
        return value.model_dump(mode="json")

    def update_experience(self, experience_id: str, changes: dict[str, Any]) -> dict[str, Any]:
        value = self.repository.update_experience(experience_id, changes)
        self.index.rebuild()
        return value.model_dump(mode="json")

    def add_solution_version(self, experience_id: str, payload: dict[str, Any]) -> dict[str, Any]:
        value = self.repository.add_solution_version(experience_id, payload)
        self.index.rebuild()
        return value.model_dump(mode="json")

    def register_failure(self, experience_id: str, payload: dict[str, Any]) -> dict[str, Any]:
        value = self.repository.register_failure(experience_id, payload)
        self.index.rebuild()
        return value.model_dump(mode="json")

    def create_pattern(self, payload: dict[str, Any]) -> dict[str, Any]:
        value = self.repository.create_pattern(payload)
        self.index.rebuild()
        return value.model_dump(mode="json")

    def propose_update(self, payload: dict[str, Any]) -> dict[str, Any]:
        return self.repository.create_evolution_proposal(payload).model_dump(mode="json")

    def search(self, query: str | Mapping[str, object], limit: int = 10) -> list[dict[str, Any]]:
        return [item.model_dump(mode="json") for item in self.retrieval.search(query, limit=limit)]

    def get_experience(self, experience_id: str) -> dict[str, Any]:
        return self.repository.get_experience(experience_id).model_dump(mode="json")

    def get_related(self, experience_id: str, limit: int = 5) -> list[dict[str, Any]]:
        return [item.model_dump(mode="json") for item in self.retrieval.related(experience_id, limit)]

    def novelty(self, problem: str | Mapping[str, object]) -> dict[str, Any]:
        return detect_novelty(problem, self.retrieval, self.config).model_dump(mode="json")

    def build_context(self, task: str, project: str | None = None, output: str = "json") -> Any:
        value = self.context_builder.build(task, project)
        if output == "markdown":
            return self.context_builder.to_markdown(value)
        return value.model_dump(mode="json")

    def project_context(self, project: str) -> dict[str, Any]:
        return self.repository.get_project(project).model_dump(mode="json")

    def update_project(self, project: str, payload: dict[str, Any]) -> dict[str, Any]:
        return self.repository.upsert_project(project, payload).model_dump(mode="json")

    def validate(self) -> dict[str, Any]:
        return validate_memory(self.repository)
