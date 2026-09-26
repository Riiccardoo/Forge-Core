"""Configuration loading for Forge."""

from __future__ import annotations

from dataclasses import dataclass, field
import logging
from pathlib import Path
import tomllib
from typing import Any


DEFAULT_WEIGHTS: dict[str, float] = {
    "text": 0.18,
    "problem_class": 0.12,
    "domains": 0.10,
    "capabilities_required": 0.25,
    "inputs": 0.06,
    "outputs": 0.06,
    "constraints": 0.08,
    "technologies": 0.04,
    "tools": 0.03,
    "tags": 0.08,
}


@dataclass(slots=True)
class ForgeConfig:
    """Resolved runtime configuration.

    Relative paths are resolved against ``root`` rather than the process working
    directory, so MCP hosts may start Forge from any directory.
    """

    root: Path
    memory_path: Path
    index_path: Path
    similarity_weights: dict[str, float] = field(default_factory=lambda: dict(DEFAULT_WEIGHTS))
    novelty_threshold: float = 0.56
    strong_match_threshold: float = 0.76
    context_result_limit: int = 5
    context_token_budget: int = 1800
    read_only: bool = False
    logging_level: str = "INFO"

    @classmethod
    def load(cls, root: Path | str | None = None, config_path: Path | str | None = None) -> "ForgeConfig":
        resolved_root = Path(root or Path.cwd()).resolve()
        path = Path(config_path).resolve() if config_path else resolved_root / "config" / "default.toml"
        data: dict[str, Any] = {}
        if path.exists():
            with path.open("rb") as handle:
                data = tomllib.load(handle)
        paths = data.get("paths", {})
        memory = Path(paths.get("memory_path", "memory"))
        index = Path(paths.get("index_path", ".forge/index.sqlite3"))
        if not memory.is_absolute():
            memory = resolved_root / memory
        if not index.is_absolute():
            index = resolved_root / index
        novelty = data.get("novelty", {})
        context = data.get("context", {})
        runtime = data.get("runtime", {})
        weights = dict(DEFAULT_WEIGHTS)
        weights.update({k: float(v) for k, v in data.get("similarity_weights", {}).items()})
        return cls(
            root=resolved_root,
            memory_path=memory.resolve(),
            index_path=index.resolve(),
            similarity_weights=weights,
            novelty_threshold=float(novelty.get("threshold", 0.56)),
            strong_match_threshold=float(novelty.get("strong_match_threshold", 0.76)),
            context_result_limit=int(context.get("result_limit", 5)),
            context_token_budget=int(context.get("token_budget", 1800)),
            read_only=bool(runtime.get("read_only", False)),
            logging_level=str(runtime.get("logging", "INFO")),
        )

    def configure_logging(self) -> None:
        logging.basicConfig(
            level=getattr(logging, self.logging_level.upper(), logging.INFO),
            format="%(asctime)s %(levelname)s %(name)s: %(message)s",
        )

