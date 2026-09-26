"""Disposable SQLite FTS5 index rebuilt from canonical memory files."""

from __future__ import annotations

import json
import os
from pathlib import Path
import sqlite3
from typing import Any

from forge.config import ForgeConfig
from forge.core.repository import FileMemoryRepository
from forge.core.similarity import tokenize


class IndexError(RuntimeError):
    """Raised when the local search index cannot be used."""


class SearchIndex:
    def __init__(self, config: ForgeConfig, repository: FileMemoryRepository):
        self.config = config
        self.repository = repository

    def rebuild(self) -> dict[str, int]:
        if self.config.read_only:
            raise IndexError("Cannot rebuild the index in read-only mode")
        target = self.config.index_path
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_suffix(target.suffix + f".{os.getpid()}.tmp")
        temporary.unlink(missing_ok=True)
        counts = {"experience": 0, "pattern": 0, "failure": 0}
        try:
            connection = sqlite3.connect(temporary)
            try:
                connection.executescript(
                    """
                    PRAGMA journal_mode=DELETE;
                    CREATE TABLE documents (
                        rowid INTEGER PRIMARY KEY,
                        kind TEXT NOT NULL,
                        identifier TEXT NOT NULL UNIQUE,
                        title TEXT NOT NULL,
                        body TEXT NOT NULL,
                        path TEXT NOT NULL
                    );
                    CREATE VIRTUAL TABLE documents_fts USING fts5(
                        identifier UNINDEXED,
                        title,
                        body,
                        tokenize='unicode61 remove_diacritics 2'
                    );
                    CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
                    """
                )
                documents: list[tuple[str, str, str, str, str]] = []
                for item in self.repository.list_experiences():
                    path = self.repository.find_experience_path(item.id)
                    body = " ".join(
                        [
                            item.summary,
                            item.problem_class,
                            item.problem_description,
                            item.context,
                            item.transferability,
                            *item.domains,
                            *item.technologies,
                            *item.engines,
                            *item.languages,
                            *item.tools,
                            *item.capabilities_required,
                            *item.inputs,
                            *item.outputs,
                            *item.constraints,
                            *item.tags,
                        ]
                    )
                    documents.append(("experience", item.id, item.title, body, str(path)))
                    counts["experience"] += 1
                for item in self.repository.list_patterns():
                    path = next(self.config.memory_path.glob(f"patterns/{item.id}-*/pattern.json"))
                    body = " ".join(
                        [item.description, *item.problem_classes, *item.applicable_domains, *item.conditions, *item.tradeoffs, *item.tags]
                    )
                    documents.append(("pattern", item.id, item.name, body, str(path)))
                    counts["pattern"] += 1
                for item in self.repository.list_failures():
                    path = self.config.memory_path / "failures" / f"{item.id}.json"
                    body = " ".join(
                        [item.attempt, item.reason_failed, str(item.environment), item.error_signature, *item.conditions, item.workaround]
                    )
                    documents.append(("failure", item.id, item.attempt, body, str(path)))
                    counts["failure"] += 1
                for kind, identifier, title, body, path in documents:
                    cursor = connection.execute(
                        "INSERT INTO documents(kind, identifier, title, body, path) VALUES (?, ?, ?, ?, ?)",
                        (kind, identifier, title, body, path),
                    )
                    connection.execute(
                        "INSERT INTO documents_fts(rowid, identifier, title, body) VALUES (?, ?, ?, ?)",
                        (cursor.lastrowid, identifier, title, body),
                    )
                connection.execute("INSERT INTO metadata(key, value) VALUES ('counts', ?)", (json.dumps(counts),))
                connection.commit()
            finally:
                connection.close()
            os.replace(temporary, target)
        finally:
            temporary.unlink(missing_ok=True)
        return counts

    def ensure(self) -> None:
        if not self.config.index_path.exists():
            self.rebuild()

    def search(self, query: str, limit: int = 20) -> dict[str, float]:
        self.ensure()
        terms = sorted(tokenize(query))
        if not terms:
            return {}
        expression = " OR ".join(f'"{term.replace(chr(34), "")}"' for term in terms[:24])
        try:
            connection = sqlite3.connect(f"file:{self.config.index_path.as_posix()}?mode=ro", uri=True)
            try:
                rows = connection.execute(
                    """
                    SELECT identifier
                    FROM documents_fts
                    WHERE documents_fts MATCH ?
                    ORDER BY bm25(documents_fts, 0.0, 5.0, 1.0)
                    LIMIT ?
                    """,
                    (expression, limit),
                ).fetchall()
            finally:
                connection.close()
        except sqlite3.Error as exc:
            raise IndexError(f"FTS5 search failed: {exc}") from exc
        return {row[0]: round(1.0 / (1.0 + rank * 0.25), 6) for rank, row in enumerate(rows)}

