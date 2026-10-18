# Implementation Plan - Story 0128

## Production Change

Add the existing `ohlc-range-analysis` capability to the strict passive
execution plan. The existing `market-structure-analysis` producer remains an
artifact dependency of Trend Context and is not duplicated in the explicit
plan list.

## Test Change

Update the strict passive strategy contract test to require both deterministic
capabilities needed by the feature projection boundary:

- `trend-context-analysis`
- `ohlc-range-analysis`

Existing projection tests already cover deterministic PRESENT OHLC values,
explicit missingness, and Structure/Trend projection. Strict cutoff validation
rejects future evidence while allowing valid evidence ending before the
execution cutoff.

## Versioning

- Keep `feature-projection-v1`; its formulas and artifact contract do not change.
- Keep the existing OHLC capability version `1.1.0`; only plan selection changes.

## Runtime Boundary

Do not restart or modify `market-data`. Do not regenerate historical snapshots.
Deploy and validate a new strict passive execution with captured OHLC evidence;
never rewrite existing snapshots.
