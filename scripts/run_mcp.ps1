$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$env:FORGE_ROOT = $ProjectRoot
& "$ProjectRoot\.venv\Scripts\python.exe" -m forge.mcp_server

