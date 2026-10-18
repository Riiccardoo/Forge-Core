# EXP-000013: Consolidate Hytale asset packs across saved worlds while preserving placed block identifiers

## Problem

The main map showed the obsolete BlackridgeMonasticDetails name, while Tall Wheat was a separate global mod. The user wanted a single correctly named Buildings and Furnishment mod, both legacy packages removed, and existing blocks kept compatible.

## Context

The architecture 1.1.0 packs have 80 resources that are already byte-identical to the vegetation 1.6.1 library. Tall Wheat adds seven resources and an empty JavaPlugin entry point. Six saved worlds reference Tall Wheat, three have world-local Blackridge packs, and one test world has both disabled.

## Current Solution

Build one Hytale pack under the desired manifest name, copy missing legacy assets without renaming any item/block IDs, migrate all affected world config keys with backup and rollback, and remove the retired packages only after verifying coverage.

## Lessons

Byte-level asset coverage and config-key migration are reusable; Hytale 0.7.2 pack loading was checked here. Existing placed-block rendering still needs a client check.

## Failures

- None recorded

## Transferable Patterns

- None recorded

## Related Experiences

- EXP-000012
- EXP-000004
