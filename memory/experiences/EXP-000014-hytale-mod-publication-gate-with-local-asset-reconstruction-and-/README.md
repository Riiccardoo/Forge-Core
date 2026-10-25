# EXP-000014: Hytale mod publication gate with local asset reconstruction and explicit evidence limits

## Problem

Produce a reviewable public Hytale mod artifact from working source without redistributing unlicensed native/third-party assets, dropping previously saved material IDs, confusing technical checks with client appearance, or mixing test files with the download.

## Context

Adaptive Surface 1.0.0 was prepared on Windows for Hytale 0.7.4 from user-accepted rc.19. The user reported the rc.19 feature worked. The exact 1.0 client visual check and a real historical-world client migration remained with the author; no public upload or browser download was executed.

## Current Solution

For Adaptive Surface 1.0.0, treat the public JAR, migration utility and author handoff as separate artifacts. Reconstruct material pixels locally from the installed Hytale assets and verified old inputs. Fail closed and retain an existing pack if required data is missing. Audit every distributed file and material, then stage only original/authorized content with a checksum manifest and explicit evidence gates.

## Lessons

Reuse the sequence of provenance, clean-install, failure-retention, migration-copy, packaging and evidence gates by analogy on another Hytale mod. Recheck current Hytale APIs, asset rights, saved-data format and target game version; Surface-specific code and numeric results do not transfer directly.

## Failures

- FAIL-000071

## Transferable Patterns

- None recorded

## Related Experiences

- EXP-000001
- EXP-000013
