# PAT-000003: Enforce protected regions with deterministic composition

When generated output may alter areas that must remain unchanged, keep the approved source as the base and merge only explicitly permitted regions or state transitions. Verify protected areas after composition, rather than relying on instructions to the generator alone.

## Applies when

- Some source regions or state must remain unchanged
- A generator or bulk transform can rewrite more than the permitted scope

## Trade-offs

- Requires explicit masks or desired-state rules and comparison checks
- Different media need different composition and boundary validation

## Evidence

- memory/failures/FAIL-000029.json: Generated panorama changed pixels outside the allowed terrain
- memory/failures/FAIL-000028.json: Terrain operation ordering erased newly placed soil
- memory/projects/zytale/references/landscape-360-2026-10-16/near-validation.json: Protected pixels verified after final composition
