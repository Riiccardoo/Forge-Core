# EXP-000001: Hytale Adaptive Surface: persistent geometry with selected-block materials

## Problem

One smoothing block must form slopes/corners, retain its shape/material across world saves, and copy the exact block a player right-clicked without rebuilding a large client atlas during play or preventing world startup.

## Context

User-confirmed 0.9.4 on Hytale 0.6.8. Initial 0.9.3 creative search exposed internal helpers and new donor material Rock_Calcite_Brick was absent from the active pack. Runtime pack replacement was blocked by the Windows ZIP lock. 0.9.4 corrected both and retains clicked-target behavior.

## Current Solution

Adaptive Surface 0.9.4 keeps one searchable item, loads selected baked and newly learned HD materials through one enabled per-world ZIP, and publishes staged changes only after the Hytale server exits on Windows.

## Lessons

Transfer static-versus-active asset separation, validation of save-level pack enablement, and event-target versus clicked-target distinction only after checking the target engine API. Hytale-specific model/state IDs remain project knowledge.

## Failures

- FAIL-000001
- FAIL-000002
- FAIL-000003
- FAIL-000004
- FAIL-000019
- FAIL-000035

## Transferable Patterns

- None recorded

## Related Experiences

- None recorded
