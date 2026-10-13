# EXP-000004: Hytale Update 7 architectural-window catalogue with native glazing and material-isolated masonry

## Problem

Correct pane rotation and displacement in the 20 generated window variants after migration to Hytale 0.7.0, add the missing donor-textured wall around non-rectangular openings without merging the frame into the wall, and make all 21 inventory items recognizable.

## Context

User screenshots showed particle glazing rotated or displaced from a Gothic bifora and missing masonry around the frame. The 0.4.1 generator confirmed that each opening had only a narrow adaptive reveal and that every pane was a separately transformed particle. Released 0.7.0 assets contain native transparent window faces and matching RequiresAlphaBlending block definitions.

## Current Solution

AdaptiveWindow 0.4.2 for released Hytale 0.7.0: place glazing as native transparent quads inside each structural model; generate donor-textured masonry as separate model pieces outside the aperture union; keep every architectural frame on fixed UVs and give all 21 items explicit icons and dimension-prefixed English names.

## Lessons

The isolation of donor-textured wall UVs from fixed frame/glazing UVs applies to other generated architectural assets. Native transparent-face support remains version-specific and must be revalidated against each released Hytale asset schema and client.

## Failures

- FAIL-000020

## Transferable Patterns

- None recorded

## Related Experiences

- EXP-000003
- EXP-000001
