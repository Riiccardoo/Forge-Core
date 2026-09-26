param(
    [string]$Python = "python"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $ProjectRoot
try {
    & $Python -m venv .venv
    & .\.venv\Scripts\python.exe -m pip install --upgrade pip
    & .\.venv\Scripts\python.exe -m pip install -e ".[mcp,dev]"
    & .\.venv\Scripts\forge.exe init
    & .\.venv\Scripts\forge.exe doctor
}
finally {
    Pop-Location
}

