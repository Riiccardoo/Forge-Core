from __future__ import annotations


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
