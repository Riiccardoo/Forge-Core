# PAT-000002: Separate technical validation from visual acceptance

For visual deliverables, establish technical validity and user-visible quality as distinct gates. Test one representative result in the real renderer or viewer, request feedback before multiplying variants, and preserve the accepted baseline during later changes.

## Applies when

- The result is judged by appearance or design fidelity
- Static, structural or server tests cannot reproduce the user's final view

## Trade-offs

- A pilot and feedback step can delay batch production
- User approval applies to the tested version and view, not all future environments

## Evidence

- memory/failures/FAIL-000016.json: Transparent glass passed technical checks but failed visual acceptance
- memory/failures/FAIL-000017.json: Structurally validated house was rejected for design quality
- memory/failures/FAIL-000024.json: Discrete fog carriers failed visual continuity despite functional integration
- memory/projects/zytale/references/landscape-360-2026-10-16.md: Approved baseline and three-view visual acceptance were preserved
