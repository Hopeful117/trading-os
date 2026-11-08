# Implementation Plan - Story 0221

## Objective

Make the existing offline dataset diagnostic output measure data readiness
without inventing runtime coverage, changing the canonical Java bundle format,
or adding another validator. The output must be deterministic, useful for a
human readiness review, and explicit when a dimension cannot be inferred from
the bundle contract.

## Approved Scope Boundary

### In scope

- Extend `research/ml/src/research_dataset/validator.py` summary generation.
- Preserve the existing `validate`, `diagnose`, `experiment` and `benchmark`
  commands and their exit-code behavior.
- Add deterministic quality dimensions derived only from validated bundle
  manifest and rows.
- Add focused Python tests for complete, sparse, gapped, multi-market,
  multi-horizon, missing-outcome and insufficient-population cases.
- Update `research/ml/README.md` with the quality report contract.
- Produce the Story-local historical data readiness report after validation,
  clearly labeling fixture evidence and the runtime blocker.
- Update `implementation-report.md` with validation, documentation outcome,
  vault outcome and final verdict.

### Out of scope

- Java domain, persistence, Flyway migrations or public API changes.
- New manifest fields unless implementation proves an existing field is
  insufficient and the change remains backward compatible.
- Runtime database queries, credentials, provider synchronization or backfill.
- New ML models, training, inference, notebooks or statistical claims.
- Changes to temporal eligibility, fingerprints, outcome identity or export
  selection semantics.

## Quality Report Contract

Extend the existing `ValidationResult.summary` returned by `diagnose` with a
stable `quality` object. Keep the current top-level summary fields for
compatibility. The quality object will contain:

- `validationStatus`: `VALID` or `INVALID`;
- `observationCount`: JSONL row count;
- `eligibleCount`, `excludedCount`, `outcomeStatuses`, `featureMissingness`;
- `markets`, `horizons`, and deterministic ordered identity counts;
- `coverage`: earliest/latest evaluation cutoff and covered duration;
- `gaps`: adjacent cutoff count and largest observed gap, with no claim that a
  gap is a physical candle gap when the population cadence does not define one;
- `expectedCadence`: explicit `NOT_DETERMINABLE` when only
  `ONE_PER_FEATURE_SNAPSHOT` is declared;
- `duplicateChecks`: semantic identity, sample ID and sample fingerprint
  counts already detected by the validator;
- `featureCompleteness`: present/missingness counts and rows with disallowed
  required missingness;
- `outcomeCompleteness`: available, pending, incomplete, unavailable, missing
  and mature eligible counts;
- `provenance`: feature provenance validity, known-at violations and historical
  reconstruction lineage coverage;
- `effectiveSampleSize`: a bounded diagnostic based on non-overlapping cutoff
  windows only when a positive horizon is available, explicitly labeled as an
  approximation and not statistical independence;
- `datasetFingerprint` and `samplesSha256`.

If the bundle does not contain enough metadata to calculate a dimension, emit a
stable status such as `NOT_DETERMINABLE` and a reason instead of guessing.
Quality calculations must not change `ValidationResult.valid` or replace the
existing fail-closed diagnostics.

## Implementation Details

1. Refactor the existing `_summary` calculation into small local helpers only
   where necessary; do not introduce a parallel validation pipeline.
2. Parse all timestamps through the existing timezone-aware `_parse_time` path.
3. Use sorted rows and sorted identity sets for deterministic ordering.
4. Count quality diagnostics from existing diagnostic codes rather than
   duplicating validation rules.
5. Derive temporal coverage from evaluation cutoffs. Treat an adjacent cutoff
   difference as an observed gap, not proof of missing provider candles.
6. Derive approximate non-overlapping windows from each declared horizon using
   the same conservative chronological semantics already used by the Python
   experiment; do not call it effective statistical sample size.
7. Preserve `knownAt <= evaluationCutoff`, exact outcome selection and strict
   evidence validation unchanged.
8. Keep the machine-readable report in `diagnose` JSON. The human-readable
   readiness report will be repository documentation, not a generated runtime
   artifact or a fixture masquerading as production data.

## Tests

### Existing regression tests

- All current `research/ml` tests.
- Committed Java-generated fixture remains valid.
- Existing invalid schema, checksum, fingerprint, provenance, timestamp,
  missingness and duplicate tests remain unchanged in behavior.

### New quality tests

- Deterministic quality output for repeated validation of the same fixture.
- Single-sample fixture reports one observation and no determinable physical
  cadence.
- Multiple cutoffs report ordered coverage and largest observed gap.
- Multiple markets and horizons report stable sorted identity dimensions.
- Missing, pending, incomplete and unavailable outcomes are counted without
  promoting excluded samples.
- Feature missingness and provenance counts are reported consistently.
- Duplicate identities remain invalid and are reflected in diagnostics.
- Empty and insufficient populations report readiness limitations without
  producing model metrics.
- Future `knownAt` evidence remains invalid and cannot be hidden by quality
  reporting.

## Documentation Reconciliation

- Update `research/ml/README.md` only for the new `diagnose` quality output.
- Add `historical-data-readiness-report.md` under this Story directory with the
  measured fixture evidence, runtime `401` limitation, quality result, ML
  readiness matrix and recommended next data-access step.
- Add `implementation-report.md` after implementation with the final verdict.
- Record `Documentation update` and `Vault Outcome` explicitly.
- No ADR is required because the plan preserves the existing dataset contract
  and introduces no new architectural boundary.

## Validation Commands

```text
cd research/ml && .venv/bin/pytest
PYTHONPATH=research/ml/src research/ml/.venv/bin/python -m research_dataset diagnose research/ml/tests/fixtures/java-generated
git diff --check
```

If available after the Python changes, run the affected Java Market Intelligence
tests to confirm the export contract remains compatible. Do not run controlled
historical reconstruction or access runtime databases without new explicit
authorization and credentials.

## Stop Conditions

- The quality report requires an incompatible manifest/schema change.
- A requested metric requires inferring provider retention or physical candle
  cadence that the bundle does not encode.
- Runtime access requires bypassing authentication or inspecting secrets.
- Existing tests reveal that temporal validation or outcome identity semantics
  would need to be weakened.
- A real populated bundle becomes available and requires product or architecture
  decisions beyond this additive diagnostic scope.

## Approval Gate

Implementation must not begin until this Implementation Plan is explicitly
approved.
