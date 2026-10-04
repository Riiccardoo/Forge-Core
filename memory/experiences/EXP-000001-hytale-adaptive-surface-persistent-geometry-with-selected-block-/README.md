# EXP-000001: Hytale Adaptive Surface: persistent geometry with selected-block materials

## Problem

One smoothing block must form slopes/corners, retain its shape/material across world saves, and copy the exact block a player right-clicked without rebuilding a large client atlas during play or preventing world startup.

## Context

Zytale Adaptive Surface 0.9.3 on Hytale 0.6.8. The sole inventory item is Adaptive Surface (Blocks → Soils). Java plugin and generated asset pack run in tandem. Earlier broad HD asset loading caused client texture failures; the later world ZIP was initially disabled by save config. Legacy atlas resizing also produced diagonal alpha artifacts. The final click-selection implementation was positively reported by the user but not instrumented with client click traces.

## Current Solution

0.9.3 keeps the full baked catalogue dormant in the JAR and builds a small active pack from each saved world. It enables the pack in save config, keeps fallback UVs native, and selects the clicked material from placement interaction data.

## Lessons

Transfer static-versus-active asset separation, validation of save-level pack enablement, and event-target versus clicked-target distinction only after checking the target engine API. Hytale-specific model/state IDs remain project knowledge.

## Failures

- FAIL-000001
- FAIL-000002
- FAIL-000003
- FAIL-000004

## Transferable Patterns

- None recorded

## Related Experiences

- None recorded
