from __future__ import annotations

import json


def test_novelty_near_identical_updates_existing(forge_app, world_experience):
    result = forge_app.novelty(
        {
            "text": "Programmatic world modification in Hytale: place generated objects into a persistent game world",
            "problem_class": "programmatic world modification",
            "capabilities_required": ["world_modification", "spatial_placement", "object_creation", "persistence"],
            "technologies": ["Hytale"],
            "tags": ["procedural", "placement", "world"],
        }
    )
    assert result["decision"] == "UPDATE_EXISTING"
    assert result["similar_experiences"][0] == world_experience["id"]


def test_novelty_new_problem_creates_experience(forge_app, world_experience):
    result = forge_app.novelty("Prove a new theorem about category-theoretic adjunctions")
    assert result["decision"] == "NEW_EXPERIENCE"


def test_novelty_failed_approach_becomes_failure(forge_app, world_experience):
    result = forge_app.novelty(
        {
            "text": "Hytale object placement in the world",
            "failed_attempt": "Place before chunk load",
            "event_type": "failure",
        }
    )
    assert result["decision"] == "NEW_FAILURE"


def test_novelty_solution_preserves_lineage(forge_app, world_experience):
    result = forge_app.novelty(
        {
            "text": "Programmatic world modification in Hytale",
            "capabilities_required": world_experience["capabilities_required"],
            "technologies": ["Hytale"],
            "proposed_solution": "Use a validated placement queue",
        }
    )
    assert result["decision"] == "NEW_SOLUTION_VERSION"


def test_novelty_can_explicitly_skip_routine_work(forge_app, world_experience):
    result = forge_app.novelty({"text": "Routine rerun with no new findings", "event_type": "no_new_learning"})
    assert result["decision"] == "NO_MEMORY_UPDATE"


def test_context_contains_solution_failure_pattern_and_project(forge_app, world_experience):
    forge_app.add_solution_version(
        world_experience["id"],
        {"description": "Validated placement queue", "status": "preferred", "test_results": ["integration passed"]},
    )
    forge_app.register_failure(
        world_experience["id"],
        {"attempt": "place before load", "reason_failed": "chunk unavailable", "workaround": "wait for ready"},
    )
    forge_app.create_pattern(
        {
            "name": "Readiness before mutation",
            "description": "Check target readiness before spatial placement.",
            "conditions": ["asynchronous target initialization"],
        }
    )
    forge_app.update_project("Demo", {"summary": "Project-only context"})
    forge_app.rebuild_index()
    bundle = forge_app.build_context("spatial placement in an asynchronously loaded world", "Demo")
    assert bundle["experiences"][0]["preferred_solution"]["description"] == "Validated placement queue"
    assert bundle["failures_to_avoid"]
    assert bundle["patterns"]
    assert bundle["project_context"]["summary"] == "Project-only context"
    markdown = forge_app.build_context("spatial placement", "Demo", "markdown")
    assert "# Forge Context Bundle" in markdown
    assert "Failures to avoid" in markdown


def test_context_respects_small_budget(forge_app, world_experience):
    forge_app.config.context_token_budget = 80
    bundle = forge_app.context_builder.build("programmatic Hytale world modification")
    assert bundle.estimated_tokens <= 80
    assert bundle.truncated is True


def test_context_summarizes_large_project_without_eating_budget(forge_app, world_experience):
    forge_app.update_project(
        "Demo",
        {
            "summary": "Current project status " * 100,
            "decisions": ["Old detail " * 300 for _ in range(40)],
            "historical_versions": ["prior release " * 1000 for _ in range(20)],
        },
    )
    forge_app.rebuild_index()
    bundle = forge_app.context_builder.build("programmatic Hytale world modification", "Demo")
    assert bundle.estimated_tokens <= forge_app.config.context_token_budget
    assert len(json.dumps(bundle.model_dump(mode="json"), indent=2, ensure_ascii=False)) <= 4 * forge_app.config.context_token_budget
    assert len(forge_app.context_builder.to_markdown(bundle)) <= 4 * forge_app.config.context_token_budget
    assert bundle.project_context["source_path"] == "memory/projects/demo/project.json"
    assert bundle.project_context["details_omitted"] is True
    assert "historical_versions" not in bundle.project_context
    assert bundle.experiences[0]["id"] == world_experience["id"]


def test_context_includes_linked_failure_and_latest_version(forge_app, world_experience):
    preferred = forge_app.add_solution_version(
        world_experience["id"], {"description": "Validated queue", "status": "preferred"}
    )
    forge_app.add_solution_version(
        world_experience["id"],
        {"id": preferred["id"], "description": "New unvalidated queue", "status": "experimental"},
    )
    failure = forge_app.register_failure(
        world_experience["id"],
        {"attempt": "Use a blue button", "reason_failed": "It obscures the result", "workaround": "Use a plain button"},
    )
    forge_app.rebuild_index()
    bundle = forge_app.build_context("programmatic Hytale world modification")
    assert bundle["experiences"][0]["preferred_solution"]["description"] == "Validated queue"
    assert bundle["experiences"][0]["latest_solution"]["status"] == "experimental"
    assert failure["id"] in [item["id"] for item in bundle["failures_to_avoid"]]
