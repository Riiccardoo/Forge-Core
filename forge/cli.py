"""Dependency-light command line interface for Forge."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any

from pydantic import ValidationError

from forge.core.repository import RepositoryError
from forge.core.service import Forge


def _json(value: Any) -> str:
    return json.dumps(value, indent=2, ensure_ascii=False, default=str)


def _load_payload(value: str | None, file: str | None) -> dict[str, Any]:
    if bool(value) == bool(file):
        raise ValueError("provide exactly one of --data or --file")
    raw = value if value is not None else Path(str(file)).read_text(encoding="utf-8")
    parsed = json.loads(raw)
    if not isinstance(parsed, dict):
        raise ValueError("payload must be a JSON object")
    return parsed


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="forge", description="Technical experience memory")
    parser.add_argument("--root", default=".", help="Forge repository root (default: current directory)")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("init", help="Create the empty memory layout and search index")
    sub.add_parser("doctor", help="Check configuration, storage and FTS5")
    sub.add_parser("status", help="Show memory counts and paths")

    search = sub.add_parser("search", help="Search experiences, failures and patterns")
    search.add_argument("query")
    search.add_argument("--limit", type=int, default=10)

    context = sub.add_parser("context", help="Build a concise context bundle")
    context.add_argument("task")
    context.add_argument("--project")
    context.add_argument("--json", action="store_true", dest="as_json")

    novelty = sub.add_parser("novelty", help="Classify new knowledge")
    novelty.add_argument("problem")
    novelty.add_argument("--event-type")
    novelty.add_argument("--solution")
    novelty.add_argument("--failed-attempt")

    show = sub.add_parser("show", help="Show an experience capsule")
    show.add_argument("experience_id")

    sub.add_parser("validate", help="Validate all canonical memory files and references")
    sub.add_parser("rebuild-index", help="Rebuild the disposable SQLite FTS5 index")

    export = sub.add_parser("export-context", help="Write a context bundle to Markdown or JSON")
    export.add_argument("task")
    export.add_argument("--project")
    export.add_argument("--format", choices=("markdown", "json"), default="markdown")
    export.add_argument("--output", required=True)

    for name, help_text in (
        ("create-experience", "Create a validated experience from a JSON object"),
        ("create-pattern", "Create a validated pattern from a JSON object"),
        ("propose-update", "Create a non-executing evolution proposal"),
    ):
        command = sub.add_parser(name, help=help_text)
        command.add_argument("--data")
        command.add_argument("--file")

    update = sub.add_parser("update-experience", help="Merge fields into an experience")
    update.add_argument("experience_id")
    update.add_argument("--data")
    update.add_argument("--file")

    solution = sub.add_parser("add-solution", help="Add an immutable solution version")
    solution.add_argument("experience_id")
    solution.add_argument("--data")
    solution.add_argument("--file")

    failure = sub.add_parser("register-failure", help="Record a failed approach")
    failure.add_argument("experience_id")
    failure.add_argument("--data")
    failure.add_argument("--file")

    project = sub.add_parser("update-project", help="Create or update isolated project memory")
    project.add_argument("project")
    project.add_argument("--data")
    project.add_argument("--file")
    return parser


def execute(args: argparse.Namespace) -> Any:
    forge = Forge(Path(args.root))
    command = args.command
    if command == "init":
        return forge.initialize()
    if command == "doctor":
        return forge.doctor()
    if command == "status":
        return forge.status()
    if command == "search":
        return forge.search(args.query, args.limit)
    if command == "context":
        return forge.build_context(args.task, args.project, "json" if args.as_json else "markdown")
    if command == "novelty":
        payload = {
            "text": args.problem,
            "event_type": args.event_type,
            "proposed_solution": args.solution,
            "failed_attempt": args.failed_attempt,
        }
        return forge.novelty(payload)
    if command == "show":
        return forge.get_experience(args.experience_id)
    if command == "validate":
        return forge.validate()
    if command == "rebuild-index":
        return forge.rebuild_index()
    if command == "export-context":
        content = forge.build_context(args.task, args.project, args.format)
        path = Path(args.output).resolve()
        path.parent.mkdir(parents=True, exist_ok=True)
        if args.format == "json":
            path.write_text(_json(content) + "\n", encoding="utf-8", newline="\n")
        else:
            path.write_text(str(content), encoding="utf-8", newline="\n")
        return {"written": str(path), "format": args.format}
    payload = _load_payload(args.data, args.file)
    if command == "create-experience":
        return forge.create_experience(payload)
    if command == "update-experience":
        return forge.update_experience(args.experience_id, payload)
    if command == "add-solution":
        return forge.add_solution_version(args.experience_id, payload)
    if command == "register-failure":
        return forge.register_failure(args.experience_id, payload)
    if command == "create-pattern":
        return forge.create_pattern(payload)
    if command == "propose-update":
        return forge.propose_update(payload)
    if command == "update-project":
        return forge.update_project(args.project, payload)
    raise ValueError(f"Unknown command: {command}")


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        result = execute(args)
    except (RepositoryError, ValidationError, ValueError, OSError) as exc:
        print(f"Forge error: {exc}", file=sys.stderr)
        return 2
    if isinstance(result, str):
        print(result, end="" if result.endswith("\n") else "\n")
    else:
        print(_json(result))
    if args.command == "doctor" and not result.get("healthy", False):
        return 1
    if args.command == "validate" and not result.get("valid", False):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
