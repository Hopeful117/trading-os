# Implementation Report - Story 0221

## Outcome

Extended the existing Python research dataset diagnostic output with a
deterministic quality report. No Java dataset contract, persistence schema,
runtime service, provider adapter or ML model was added.

## Changes

- Added `summary.quality` to the existing `research_dataset diagnose` output.
- Reported observed cutoff coverage, adjacent intervals and largest observed
  gap without inferring provider candle continuity.
- Reported explicit `NOT_DETERMINABLE` cadence when the population only declares
  `ONE_PER_FEATURE_SNAPSHOT`.
- Reported duplicate diagnostics, feature missingness, outcome completeness,
  provenance violations, historical reconstruction lineage and fingerprints.
- Reported approximate non-overlapping windows by horizon with an explicit
  warning that they are not statistically independent sample counts.
- Preserved all existing validation diagnostics and command exit behavior.
- Added deterministic, invalid-bundle and multi-horizon quality tests.
- Documented the diagnostic contract in `research/ml/README.md`.
- Added `historical-data-readiness-report.md` with the final runtime verdict.

## Validation

- `cd research/ml && .venv/bin/pytest`: `55 passed`.
- `PYTHONPATH=research/ml/src research/ml/.venv/bin/python -m research_dataset diagnose research/ml/tests/fixtures/java-generated`: valid fixture and deterministic quality report.
- `git diff --check`: passed.
- Runtime read-only inspection: blocked by HTTP `401`; no bypass attempted.

The initial root-level pytest invocation was not an applicable validation command:
it collected `ai-engine` and `mcp-adapter` tests outside the target virtual
environment and failed during collection because their dependencies were not
installed in that interpreter. The targeted Research ML suite passed.

## Documentation Reconciliation

- Updated `research/ml/README.md` because the public offline diagnostic output
  now contains a documented quality report.
- Added the Story-local historical data readiness report required by Story 0221.
- No ADR update was required: the existing bundle contract, temporal boundary,
  provenance and provider-neutral ownership remain unchanged.

## Vault Outcome

- Vault consulted: no.
- Outcome: no vault action.
- Rationale: the analysis and implementation depend on repository-local dataset
  contracts, current tests and runtime authorization evidence; no transverse
  curated note was required.
- No vault write or proposal artifact was created.

## Runtime Limitation

The final verdict is:

```text
RUNTIME_DATA_NOT_VERIFIED
```

The committed fixture proves the export/validator boundary only. It does not
prove actual historical population, retention, continuity or ML readiness.

## Git Boundary

No commit, push, merge, deployment, database mutation, provider backfill or ML
training was performed.
