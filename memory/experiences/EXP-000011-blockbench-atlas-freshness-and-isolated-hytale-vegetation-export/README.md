# EXP-000011: Blockbench atlas freshness and isolated Hytale vegetation export validation

## Problem

The PNG on disk changed but the current and newly created Blockbench projects could still display the previous procedural foliage after fromPath. Exporting before asynchronous image loading also risked stale or incomplete embedded source textures.

## Context

Six original vegetation assets in the existing Zytale asset pack. Production world must remain untouched. User rejected the first conifer's visual fidelity and later explicitly took ownership of client visual review.

## Current Solution

Author through the installed native Blockbench codec with freshly loaded atlas bytes, preserve editable texture embeddings, and validate pack loading and persistence in a guarded isolated world.

## Lessons

Applicable to editor pipelines that cache image paths and embed asynchronously loaded images. Exact fromDataURL and texture APIs are specific to inspected Blockbench 5.2.2; retest other versions.

## Failures

- FAIL-000036

## Transferable Patterns

- None recorded

## Related Experiences

- EXP-000002
- EXP-000003
