# EXP-000006: Hytale client rejects generated Atmosphere volume animations

## Problem

Runtime-generated fog blockyanim files pass server parsing and replication tests but trigger client NullReferenceException and parse warnings after world join.

## Context

The error became visible after removing the earlier WorldLoadProgress disconnect. The affected-world log reached InGame and then reported failures under VFX/Atmosphere/Volume.

## Current Solution

No solution has been recorded yet.

## Lessons

Generated Hytale animation assets require a real client parsing gate; server-side parsing and replication alone are insufficient evidence.

## Failures

- FAIL-000023

## Transferable Patterns

- None recorded

## Related Experiences

- EXP-000005
