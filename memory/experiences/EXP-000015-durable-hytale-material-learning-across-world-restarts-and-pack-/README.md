# EXP-000015: Durable Hytale material learning across world restarts and pack recovery

## Problem

A mod acknowledges a material discovery during gameplay, but its generated asset pack is registered only at the next world start. A delayed or failed background build can lose the request or leave the player in a false reopen loop.

## Context

Adaptive Surface 1.0.0 on Hytale 0.7.4 lost both native and biome-tinted requests when the background worker was delayed six seconds and the server shut down normally. Pack recovery also emitted the saved/reopen message while learning was suspended. 1.0.1 was delivered with source and publication files separate; the user then reported it works. The specific original second-PC failure mechanism was not isolated from its logs.

## Current Solution

Adaptive Surface 1.0.1 writes the material request journal during selection, processes required tint inputs before an orderly shutdown, reconstructs the pack from persisted data at startup, and distinguishes preparing, prepared and blocked states. Unresolvable fresh donors remain visible in pendingInputs while saved P/Q identities still fail closed.

## Lessons

For any game mod that activates discovered assets only at next startup, persist the request at the moment of acknowledgement and test real selection, orderly shutdown and first restart under a delayed worker. Treat missing inputs as pending and preserve already saved asset IDs. RocksDb reader details and pixel budgets are Hytale-version-specific.

## Failures

- FAIL-000074

## Transferable Patterns

- None recorded

## Related Experiences

- EXP-000001
- EXP-000014
- EXP-000004
