# EXP-000007: Continuous localized Hytale fog with air-masked client volume integration

## Problem

Fog must fill selected air space continuously, remain visible at distance, avoid solid/fluid blocks, animate smoothly without moving its bounds, offer different material structures and independent transparency, and avoid repeated asset rebuilds or excessive CPU/GPU cost.

## Context

Project Zytale / AtmosphereTool. User accepted an additional client component and then approved preview.2 visually. On 2026-10-15 the user requested an installable ZIP, a transparency slider and Forge/GitHub records. Approval of the native renderer does not imply support by Hytale or marketplace approval.

## Current Solution

AtmosphereTool 2.0.0: integrate continuous style-specific density along scene rays in an authored OpenGL client component, clipped to selected bounds, air clearance and scene depth; publish local occupancy atomically from a bounded server worker and update material parameters without rebuilding/uploading unchanged masks.

## Lessons

Separate spatial occupancy, visual material and transport lifecycle. Cache slow-changing spatial data while updating small parameters, and test rendering in the actual client path. Reusing preloading principles does not make a different renderer equivalent, nor does packaging a native binary create a supported platform loader.

## Failures

- FAIL-000024

## Transferable Patterns

- None recorded

## Related Experiences

- EXP-000001
- EXP-000005
- EXP-000006
