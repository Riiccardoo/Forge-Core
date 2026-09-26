from __future__ import annotations

import json

import pytest
from pydantic import ValidationError

from forge.core.lineage import compare_solution_versions
from forge.core.models import Experience


def test_create_experience_writes_json_and_readme(forge_app):
    value = forge_app.create_experience(
        {"title": "Atomic file replacement", "problem_description": "Safely replace a memory file."}
    )
    assert value["id"] == "EXP-000001"
    path = forge_app.repository.find_experience_path(value["id"])
    assert path.name == "capsule.json"
    assert (path.parent / "README.md").exists()
    assert json.loads(path.read_text(encoding="utf-8"))["title"] == "Atomic file replacement"


def test_schema_rejects_invalid_confidence():
    with pytest.raises(ValidationError):
        Experience(id="EXP-000001", title="Bad", confidence=2.0)


def test_update_preserves_id_and_created_at(forge_app, world_experience):
    before = forge_app.get_experience(world_experience["id"])
    after = forge_app.update_experience(world_experience["id"], {"summary": "Updated summary"})
    assert after["id"] == before["id"]
    assert after["created_at"] == before["created_at"]
    assert after["summary"] == "Updated summary"


def test_solution_lineage_and_balanced_comparison(forge_app, world_experience):
    first = forge_app.add_solution_version(
        world_experience["id"],
        {
            "description": "Place actors directly",
            "status": "working",
            "metrics": {"reliability": "medium", "performance": "high"},
        },
    )
    second = forge_app.add_solution_version(
        world_experience["id"],
        {
            "id": first["id"],
            "description": "Validate coordinates, then place actors",
            "status": "preferred",
            "metrics": {"reliability": "high", "implementation_complexity": "medium"},
        },
    )
    capsule = forge_app.repository.get_experience(world_experience["id"])
    assert [item.version for item in capsule.solution_versions] == [1, 2]
    assert second["supersedes"].endswith("v001")
    assert capsule.current_solution.endswith("v002")
    comparison = compare_solution_versions(capsule.solution_versions)
    assert len(comparison) == 2
    assert all("dimensions" in item and "caveat" in item for item in comparison)


def test_new_solution_families_receive_distinct_global_ids(forge_app, world_experience):
    first = forge_app.add_solution_version(world_experience["id"], {"description": "Approach A"})
    second = forge_app.add_solution_version(world_experience["id"], {"description": "Approach B"})
    assert first["id"] == "SOL-000001"
    assert second["id"] == "SOL-000002"


def test_failure_memory_links_global_and_experience(forge_app, world_experience):
    failure = forge_app.register_failure(
        world_experience["id"],
        {
            "attempt": "Write objects before the world chunk is loaded",
            "reason_failed": "The engine rejected placement in an unloaded chunk",
            "error_signature": "CHUNK_NOT_LOADED",
            "conditions": ["asynchronous world loading"],
            "workaround": "Wait for chunk-ready and retry once",
        },
    )
    assert failure["id"] == "FAIL-000001"
    capsule = forge_app.get_experience(world_experience["id"])
    assert failure["id"] in capsule["known_failures"]
    assert forge_app.repository.get_failure(failure["id"]).error_signature == "CHUNK_NOT_LOADED"


def test_pattern_and_project_memory_are_separate(forge_app, world_experience):
    pattern = forge_app.create_pattern(
        {
            "name": "Validate before spatial mutation",
            "description": "Validate coordinates and target readiness before changing spatial state.",
            "problem_classes": ["world modification"],
            "applicable_domains": ["game worlds", "robotics"],
            "related_experiences": [world_experience["id"]],
        }
    )
    project = forge_app.update_project(
        "Zytale", {"summary": "Project-specific facts", "constraints": ["private map format"]}
    )
    assert pattern["id"] == "PAT-000001"
    assert project["id"] == "zytale"
    assert "private map format" not in forge_app.get_experience(world_experience["id"])["constraints"]


def test_evolution_proposal_does_not_modify_code(forge_app):
    proposal = forge_app.propose_update(
        {
            "problem": "Slow rebuilds at very large scale",
            "component": "index",
            "proposed_change": "Add incremental indexing",
            "expected_benefit": "Lower rebuild latency",
            "risks": ["stale entries"],
            "required_tests": ["full rebuild equivalence"],
        }
    )
    assert proposal["id"] == "EVP-000001"
    assert proposal["status"] == "proposed"
