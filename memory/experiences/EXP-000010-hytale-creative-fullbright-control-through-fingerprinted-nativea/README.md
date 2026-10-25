# EXP-000010: Hytale Creative Fullbright control through fingerprinted NativeAOT tail dispatch

## Problem

User requested Global Fullbright based on the Creative tools feature and explicitly canceled a separate server lighting-command slider. Screen brightness or weather changes would not implement the requested function.

## Context

Zytale / AtmosphereTool. User authorized the existing client component and automatic game-folder updates after closing Hytale. Fog rendering was already approved and must be preserved.

## Current Solution

Add one 0..100 Creative Fullbright slider and use the existing native Creative minimum-light setter through a fingerprinted leaf dispatch at managed world-update call sites; retain original fog rendering.

## Lessons

The separation of native feature semantics, exact build checks, coalesced expensive operations and owned restoration is reusable. The dispatch mechanism is experimental and requires independent ABI/GC/live-runtime validation on every target; offsets and compatibility cannot be generalized.

## Failures

- FAIL-000034
- FAIL-000069

## Transferable Patterns

- None recorded

## Related Experiences

- EXP-000007
