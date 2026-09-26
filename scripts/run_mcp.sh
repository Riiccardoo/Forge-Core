#!/usr/bin/env sh
set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
export FORGE_ROOT="$PROJECT_ROOT"
exec "$PROJECT_ROOT/.venv/bin/python" -m forge.mcp_server

