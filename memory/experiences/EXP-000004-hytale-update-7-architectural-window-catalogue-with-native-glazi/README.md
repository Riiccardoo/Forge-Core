# EXP-000004: Hytale Update 7 architectural-window catalogue with native glazing and material-isolated masonry

## Problem

Correct pane rotation and displacement in the 20 generated window variants after migration to Hytale 0.7.0, add the missing donor-textured wall around non-rectangular openings without merging the frame into the wall, and make all 21 inventory items recognizable.

## Context

User screenshots showed particle glazing rotated or displaced from a Gothic bifora and missing masonry around the frame. The 0.4.1 generator confirmed that each opening had only a narrow adaptive reveal and that every pane was a separately transformed particle. Released 0.7.0 assets contain native transparent window faces and matching RequiresAlphaBlending block definitions.

## Current Solution

AdaptiveWindow 0.4.3 keeps the full placed architecture separate from a lightweight item representation: each of the 21 items uses a shared double-sided quad and a 128x128 texture under the schema-approved Items root, while its explicit inventory Icon remains under Icons.

## Lessons

The isolation of donor-textured wall UVs from fixed frame/glazing UVs applies to other generated architectural assets. Native transparent-face support remains version-specific and must be revalidated against each released Hytale asset schema and client.

## Failures

- FAIL-000020
- FAIL-000021
- FAIL-000025

## Transferable Patterns

- None recorded

## Related Experiences

- EXP-000003
- EXP-000001
