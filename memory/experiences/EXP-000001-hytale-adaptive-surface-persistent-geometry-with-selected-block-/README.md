# EXP-000001: Hytale Adaptive Surface: persistent geometry with selected-block materials

## Problem

One smoothing block must form slopes/corners, retain its shape/material across world saves, and copy the exact block a player right-clicked without rebuilding a large client atlas during play or preventing world startup.

## Context

Adaptive Surface 0.12.4 is installed on Hytale 0.7.2, Windows, Java 25. The client yaw-minus-PI correction follows 0.12.3 transition isolation. The user reported on 2026-10-20 that the latest work succeeded and everything works. This is user acceptance of the installed result, separate from the existing technical tests; no instrumented framebuffer or long-duration performance measurement was supplied.

## Current Solution

Adaptive Surface 0.12.4 compensates the client body's yaw-minus-PI convention for BlockEntity transition overlays, while retaining 0.12.3 cube-transition isolation.

## Lessons

Transfer static-versus-active asset separation, validation of save-level pack enablement, and event-target versus clicked-target distinction only after checking the target engine API. Hytale-specific model/state IDs remain project knowledge.

## Failures

- FAIL-000001
- FAIL-000002
- FAIL-000003
- FAIL-000004
- FAIL-000019
- FAIL-000035
- FAIL-000042
- FAIL-000043
- FAIL-000044
- FAIL-000045
- FAIL-000046
- FAIL-000047
- FAIL-000048
- FAIL-000049
- FAIL-000050
- FAIL-000051
- FAIL-000052
- FAIL-000053
- FAIL-000065
- FAIL-000066
- FAIL-000067
- FAIL-000068

## Transferable Patterns

- None recorded

## Related Experiences

- None recorded
