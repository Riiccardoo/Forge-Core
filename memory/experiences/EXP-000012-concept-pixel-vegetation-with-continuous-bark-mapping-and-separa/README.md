# EXP-000012: Concept-pixel vegetation with continuous bark mapping and separate native wind foliage

## Problem

The user requested a separate trial using actual concept pixels, then rejected the vanilla Fir geometry but liked its wind effect. Applying a photographed bark panel to each short trunk segment repeated the photographed trunk silhouette/background and caused discontinuities. Applying block-wide Wind to a monolithic tree would not isolate the foliage.

## Context

User reports invisible 1.6 icons and requests grass grid slightly skewed relative to the block. Reuse existing FAIL-000025 native 64px icon rule and FAIL-000021 item-texture isolation. Same EXP-000012 asset lineage; installed only in backed-up QA world. On 2026-10-18 the user approved the 1.6.1 appearance before requesting unified mod packaging.

## Current Solution

Correct native inventory texture contracts separately from placed models and apply an explicit 22.5-degree rigid yaw to native grass grids.

## Lessons

Source-coordinate RGB checks and continuous UV phase generalize. Wind, Material Empty, node limits and filler-cell behavior are verified only for the installed Hytale 0.7.2 build; retest other releases.

## Failures

- FAIL-000037
- FAIL-000038
- FAIL-000039
- FAIL-000040
- FAIL-000041
- FAIL-000054
- FAIL-000055
- FAIL-000056
- FAIL-000057
- FAIL-000058
- FAIL-000059
- FAIL-000060
- FAIL-000061
- FAIL-000062

## Transferable Patterns

- None recorded

## Related Experiences

- EXP-000011
- EXP-000002
