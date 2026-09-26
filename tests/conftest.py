from __future__ import annotations

from pathlib import Path

import pytest

from forge.core.service import Forge


@pytest.fixture()
def forge_app(tmp_path: Path) -> Forge:
    config_dir = tmp_path / "config"
    config_dir.mkdir()
    source = Path(__file__).parents[1] / "config" / "default.toml"
    (config_dir / "default.toml").write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
    app = Forge(tmp_path)
    app.initialize()
    return app


@pytest.fixture()
def world_experience(forge_app: Forge) -> dict:
    return forge_app.create_experience(
        {
            "title": "Programmatic world modification in Hytale",
            "summary": "Place generated objects into a persistent game world.",
            "problem_class": "programmatic world modification",
            "problem_description": "Modify a Hytale world and place objects by coordinate.",
            "domains": ["game worlds", "procedural systems"],
            "technologies": ["Hytale"],
            "capabilities_required": [
                "world_modification",
                "spatial_placement",
                "object_creation",
                "persistence",
            ],
            "inputs": ["procedural rules", "coordinates"],
            "outputs": ["placed world objects"],
            "constraints": ["persistent world state"],
            "transferability": "Placement rules and coordinate validation transfer; engine APIs do not.",
            "confidence": 0.8,
            "validation_status": "validated",
            "tags": ["procedural", "placement", "world"],
        }
    )

