from __future__ import annotations

import json
from pathlib import Path

from forge.cli import main


def test_cli_init_status_doctor_search_and_context(tmp_path: Path, capsys):
    config = tmp_path / "config"
    config.mkdir()
    source = Path(__file__).parents[1] / "config" / "default.toml"
    (config / "default.toml").write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
    assert main(["--root", str(tmp_path), "init"]) == 0
    capsys.readouterr()
    assert main(["--root", str(tmp_path), "doctor"]) == 0
    doctor = json.loads(capsys.readouterr().out)
    assert doctor["healthy"] is True
    payload = json.dumps({"title": "CLI capsule", "problem_description": "Created through CLI"})
    assert main(["--root", str(tmp_path), "create-experience", "--data", payload]) == 0
    capsys.readouterr()
    assert main(["--root", str(tmp_path), "search", "CLI capsule"]) == 0
    assert "EXP-000001" in capsys.readouterr().out
    assert main(["--root", str(tmp_path), "context", "CLI capsule"]) == 0
    assert "Forge Context Bundle" in capsys.readouterr().out


def test_mcp_server_contract_imports():
    import asyncio
    import pytest

    pytest.importorskip("mcp")
    from mcp import Client
    from forge.mcp_server import TOOL_NAMES, mcp

    assert mcp is not None
    assert len(TOOL_NAMES) == 16
    assert "forge_detect_novelty" in TOOL_NAMES
    assert "forge_propose_update" in TOOL_NAMES

    async def probe() -> list[str]:
        async with Client(mcp) as client:
            result = await client.list_tools()
            return [tool.name for tool in result.tools]

    registered = asyncio.run(probe())
    assert sorted(registered) == sorted(TOOL_NAMES)
