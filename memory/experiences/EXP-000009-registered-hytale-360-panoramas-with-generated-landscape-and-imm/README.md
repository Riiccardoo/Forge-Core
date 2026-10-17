# EXP-000009: Registered Hytale 360 panoramas with generated landscape and immutable protected pixels

## Problem

Generate coherent 360-degree landscape proposals around an existing playable map while preserving the observed buildings, crops, paths and orientation, and distinguish rendered proposals from actual world geometry.

## Context

Zytale, Windows, Hytale 0.7.2 revision d769dc3d57881261a62858e1394f0e4de9a21eca. Three elevated coordinate viewpoints, night captures plus supplemental fullbright inspection. User approved the first distant landscape and later the three near-terrain simulations.

## Current Solution

Extend the registered panoramas into unused near terrain using explicit allow masks, protected holes and inside-only feathering; restore the original base everywhere else. Deliver the three viewpoints with comparison and display-only brightness control.

## Lessons

Direct reuse of ray projection, inside-only feathered masks, lossless source preservation and evidence separation for similar panoramas. Analogy to multi-view concept validation in other engines; native camera conventions, world APIs and playable bounds require fresh verification.

## Failures

- FAIL-000029

## Transferable Patterns

- None recorded

## Related Experiences

- EXP-000002
