# EXP-000008: Hytale 0.7.2 scoped terrain editing through ECS sections with native restart and selective region merge

## Problem

Legacy WorldChunk block and height APIs no longer compile on the installed release. A native editing and inspection path must load complete sections safely, preserve multipart roots/fillers and unrelated saved entities, and install only authorized terrain records.

## Context

Blackridge grain fields on a freshly backed-up user-expanded Working Copy. Same enabled mods as live, no players, frozen isolated test server; 10 irregular plots and one asset-only dark wheat variant. Client UI control was not used. Later landscape work used explicit authorization to modify the installed Working Copy, with a fresh backup, closed client world and temporary native bridge. Native second-pass validation covered 107058190 changed cells; the subsequent 608-distance revision is tracked separately in project memory.

## Current Solution

Rebuild a localized Hytale 0.7.2 hill as continuous supported terrain, replant native trees on the final heightfield, remove unsupported natural components, then retrofit the slope with variants resolved by the installed Adaptive Surface Topology while leaving full terrain and trees unchanged.

## Lessons

Loading lifecycle, explicit removal allowlists and exact-record preservation are reusable principles. API names, bounds, asset IDs, serialization schema and metrics are specific to this observed 0.7.2 installation.

## Failures

- FAIL-000026
- FAIL-000027
- FAIL-000028
- FAIL-000030
- FAIL-000031
- FAIL-000032
- FAIL-000033
- FAIL-000064

## Transferable Patterns

- None recorded

## Related Experiences

- EXP-000002
