from __future__ import annotations


def test_rebuild_index_and_lexical_retrieval(forge_app, world_experience):
    counts = forge_app.rebuild_index()
    assert counts["experience"] == 1
    hits = forge_app.search("persistent Hytale world")
    assert hits[0]["id"] == world_experience["id"]


def test_cross_technology_analogy(forge_app, world_experience):
    hits = forge_app.search("Procedural building placement in Unreal Engine")
    assert hits
    hit = next(item for item in hits if item["id"] == world_experience["id"])
    assert hit["analogy"] is True
    assert "spatial_placement" in hit["capability_overlap"]
    assert "object_creation" in hit["capability_overlap"]
    assert "compatibility verification" in hit["compatibility_note"]


def test_related_excludes_source(forge_app, world_experience):
    other = forge_app.create_experience(
        {
            "title": "Place warehouse robots on a grid",
            "problem_description": "Coordinate-managed spatial placement",
            "capabilities_required": ["spatial_placement", "coordinate_management"],
            "technologies": ["ROS"],
        }
    )
    related = forge_app.get_related(world_experience["id"])
    assert related[0]["id"] == other["id"]
    assert all(item["id"] != world_experience["id"] for item in related)


def test_failure_retrieval(forge_app, world_experience):
    failure = forge_app.register_failure(
        world_experience["id"],
        {
            "attempt": "place buildings before chunk load",
            "reason_failed": "chunk unavailable",
            "error_signature": "CHUNK_NOT_LOADED",
            "workaround": "wait for chunk-ready",
        },
    )
    hits = forge_app.search("building placement chunk not loaded")
    assert any(item["id"] == failure["id"] for item in hits)

