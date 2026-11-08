# Code Review - Story 0221

## Result

The independent review initially returned **NEEDS CHANGES**. All reported
findings were addressed in the follow-up revision.

## Review Scope

- `research_dataset.validator` quality summary calculations;
- preservation of existing validation diagnostics and exit behavior;
- deterministic ordering and fingerprint reporting;
- explicit handling of unavailable cadence, freshness and expected counts;
- tests and README documentation;
- consistency with ADR-057, ADR-060, ADR-061 and ADR-062.

## Findings And Resolutions

- The controlled Java export was not executed. Reports now state that the
  fixture contract was validated but controlled export execution remains
  unverified.
- Outcome status counts were mixed with diagnostic occurrences. The quality
  report now exposes separate status and violation counters.
- Early invalid-bundle returns omitted `summary.quality`. They now emit a
  partial quality summary with `validationStatus: INVALID`.
- Tests now cover early invalid paths and distinguish outcome statuses from
  provenance violations.

## Residual Risks

- Runtime historical data remains unverified because read-only endpoints require
  authorization and returned HTTP `401`.
- `summary.quality` reports observed cutoff intervals but intentionally cannot
  infer physical provider gaps or stale observations without bundle-level
  cadence/freshness metadata.
- Approximate non-overlapping windows are diagnostic only and are not an
  effective statistical sample-size claim.
- The root-level Python test command is not a valid cross-project command in
  the current environment; the targeted `research/ml` suite is the applicable
  validation and passed.

## Validation Evidence

- `cd research/ml && .venv/bin/pytest`: passed after the follow-up changes.
- Fixture `diagnose`: valid, deterministic quality report emitted.
- `git diff --check`: passed.
