# PAT-000001: Validate each asset consumer contract separately

When one authored asset has inventory, held-item and placed-world consumers, validate the size, path, model and rendering contract of each consumer independently before bulk production or release. A server load or a correct PNG does not prove client acceptance.

## Applies when

- One source design is exported to more than one runtime consumer
- Consumers have different native schema, size or renderer constraints

## Trade-offs

- Requires a small client-facing pilot in addition to static/server checks
- Exact dimensions and paths remain engine- and version-specific

## Evidence

- memory/failures/FAIL-000021.json: Placed structural atlas was rejected as an item texture
- memory/failures/FAIL-000025.json: Preview-sized inventory icon was clipped
- memory/failures/FAIL-000041.json: Vegetation repeated both consumer-contract mistakes
