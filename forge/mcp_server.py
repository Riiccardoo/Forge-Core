"""Official MCP Python SDK server for Forge."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Any

try:
    from mcp.server import MCPServer
except ImportError as exc:  # pragma: no cover - exercised by CLI startup checks
    raise RuntimeError("MCP support is not installed. Run: pip install -e '.[mcp]'") from exc

from forge.core.service import Forge


TOOL_NAMES = (
    "forge_status",
    "forge_search",
    "forge_get_experience",
    "forge_build_context",
    "forge_detect_novelty",
    "forge_create_experience",
    "forge_update_experience",
    "forge_register_failure",
    "forge_add_solution_version",
    "forge_create_pattern",
    "forge_get_related",
    "forge_validate_memory",
    "forge_rebuild_index",
    "forge_project_context",
    "forge_update_project",
    "forge_propose_update",
)


def _root() -> Path:
    return Path(os.environ.get("FORGE_ROOT", Path.cwd())).resolve()


def _forge() -> Forge:
    return Forge(_root())


mcp = MCPServer("Forge", instructions="Search Forge before solving; verify compatibility and consider failures before reuse.")


@mcp.tool()
def forge_status() -> dict[str, Any]:
    """Report Forge initialization, paths, index state and memory counts."""
    return _forge().status()


@mcp.tool()
def forge_search(query: str, limit: int = 10) -> list[dict[str, Any]]:
    """Search experiences, transferable patterns and pertinent failures."""
    return _forge().search(query, limit)


@mcp.tool()
def forge_get_experience(experience_id: str) -> dict[str, Any]:
    """Read one complete validated Experience Capsule by stable ID."""
    return _forge().get_experience(experience_id)


@mcp.tool()
def forge_build_context(task: str, project: str | None = None, output: str = "markdown") -> Any:
    """Build a concise agent context bundle in markdown or JSON."""
    return _forge().build_context(task, project, output)


@mcp.tool()
def forge_detect_novelty(problem: dict[str, Any]) -> dict[str, Any]:
    """Classify whether knowledge is new, an update, a solution, failure or pattern."""
    return _forge().novelty(problem)


@mcp.tool()
def forge_create_experience(experience: dict[str, Any]) -> dict[str, Any]:
    """Create a validated Experience Capsule without inventing missing facts."""
    return _forge().create_experience(experience)


@mcp.tool()
def forge_update_experience(experience_id: str, changes: dict[str, Any]) -> dict[str, Any]:
    """Update mutable fields of an existing Experience Capsule."""
    return _forge().update_experience(experience_id, changes)


@mcp.tool()
def forge_register_failure(experience_id: str, failure: dict[str, Any]) -> dict[str, Any]:
    """Record a failed approach and connect it to its experience."""
    return _forge().register_failure(experience_id, failure)


@mcp.tool()
def forge_add_solution_version(experience_id: str, solution: dict[str, Any]) -> dict[str, Any]:
    """Append a solution version while preserving prior solution lineage."""
    return _forge().add_solution_version(experience_id, solution)


@mcp.tool()
def forge_create_pattern(pattern: dict[str, Any]) -> dict[str, Any]:
    """Create a technology-independent pattern backed by evidence."""
    return _forge().create_pattern(pattern)


@mcp.tool()
def forge_get_related(experience_id: str, limit: int = 5) -> list[dict[str, Any]]:
    """Find structurally related experiences, including cross-technology analogies."""
    return _forge().get_related(experience_id, limit)


@mcp.tool()
def forge_validate_memory() -> dict[str, Any]:
    """Validate canonical files, identifiers and cross-references."""
    return _forge().validate()


@mcp.tool()
def forge_rebuild_index() -> dict[str, int]:
    """Rebuild the disposable FTS5 index entirely from canonical files."""
    return _forge().rebuild_index()


@mcp.tool()
def forge_project_context(project: str) -> dict[str, Any]:
    """Read isolated project knowledge without promoting it to general memory."""
    return _forge().project_context(project)


@mcp.tool()
def forge_update_project(project: str, knowledge: dict[str, Any]) -> dict[str, Any]:
    """Create or update project-scoped knowledge separately from general memory."""
    return _forge().update_project(project, knowledge)


@mcp.tool()
def forge_propose_update(proposal: dict[str, Any]) -> dict[str, Any]:
    """Save a code-evolution proposal; this never changes Forge code or Git state."""
    return _forge().propose_update(proposal)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Forge MCP server")
    parser.add_argument("--check", action="store_true", help="Import server and report registered tool contract")
    args = parser.parse_args(argv)
    if args.check:
        print(json.dumps({"server": "Forge", "tools": list(TOOL_NAMES)}, indent=2))
        return 0
    mcp.run(transport="stdio")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
