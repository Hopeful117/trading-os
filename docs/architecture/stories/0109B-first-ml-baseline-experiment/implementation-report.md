# Implementation Report - Story 0109B

Date: 2026-10-16 · Branch: `main`

## Implementation Summary

Implemented the first offline, reproducible ML experiment boundary on top of
the validated Story 0109A research dataset contract.

## Delivered

- Added `ValidatedResearchDataset` and a loader that validates through the
  existing 0109A validator before projecting rows for research use.
- Added explicit BTC/USD and `PT4H` experiment filtering with factual removal
  counts.
- Added a V1 readiness policy requiring 90 days of strict observed coverage,
  5,000 eligible samples, 500 approximate non-overlapping 4-hour windows, 100
  approximate non-overlapping test windows, and no gap larger than the
  canonical 15-minute capture cadence.
- Added chronological ordering, 70/30 holdout selection, and semantic 4-hour
  horizon purge.
- Added train-only mixed-type preprocessing with pandas,
  `ColumnTransformer`, median imputation, missingness indicators,
  `StandardScaler`, and unknown-safe categorical encoding.
- Added ZERO and TRAIN_MEAN baselines and fixed `Ridge(alpha=1.0)` regression.
- Added MAE, RMSE, R2, Pearson, Spearman, directional diagnostics, baseline
  improvement reporting, and explicit undefined-statistic handling.
- Added deterministic `experiment-manifest.json`, `metrics.json`, and
  test-only `predictions.jsonl` output for readiness-passing experiments.
- Added `BLOCKED_BY_DATA_READINESS` CLI behavior and preserved existing
  `validate`/`diagnose` commands.
- Required an explicit validated market UUID when the `BTC/USD` label is used;
  0109A bundles contain market IDs but no symbols, so the experiment never
  guesses an arbitrary sole market.
- Added synthetic test-only experiment fixtures and leakage-oriented regression
  coverage without presenting them as runtime evidence.
- Added package dependencies constrained to NumPy 1.26.4, pandas 2.2.3, and
  scikit-learn 1.7.1.

## Runtime Result

The local runtime was inspected directly:

- FeatureSnapshots: 0;
- observed outcomes: 7;
- available outcomes: 6;
- available horizon: `PT1H` only;
- historical OHLC rows: 598;
- rows with `knownAt <= closeTime`: 0.

The actual experiment is therefore not data-ready for BTC/USD `PT4H`. The
runtime database has no valid materialized bundle because there are no
FeatureSnapshots. The fixture CLI invocation, using the same explicit market
selection contract, returned exit code `2` and:

```text
BLOCKED_BY_DATA_READINESS
```

No runtime metrics or predictions were claimed. Synthetic fixtures were used
only for automated code verification. The runtime facts above are the real
readiness evidence; no fixture result is presented as runtime evidence.

No runtime 0109A bundle is present in the repository or under the expected local
export path. A direct CLI attempt was made with:

```text
research-dataset experiment \
  --manifest /home/ludo/workspace/trading-os/research/ml/runtime/0109A/dataset-manifest.json \
  --samples /home/ludo/workspace/trading-os/research/ml/runtime/0109A/samples.jsonl \
  --market BTC/USD --horizon PT4H \
  --market-id 11111111-1111-1111-1111-111111111111 \
  --output-dir /tmp/opencode/0109b-real-bundle-attempt
```

The attempt failed before model execution with the validator diagnostic
`manifest.invalid_json: cannot read JSON file`; the path does not exist. The
only repository bundle is the explicitly test-only
`research/ml/tests/fixtures/java-generated` fixture.

The corresponding factual readiness counts are `validatedRows=0`,
`marketRows=0`, `horizonRows=0`, `eligibleRows=0`,
`availableOutcomeRows=0`, `experimentRows=0`,
`approxNonOverlapping4hWindows=0`, `approxTest4hWindows=0`,
`trainCandidates=0`, `purgedSamples=0`, `trainSamples=0`, and
`testSamples=0`.

## Documentation Reconciliation

Documentation update: completed.

- Added the complete Story 0109B artifact set.
- Added `implementation-plan.md` and this implementation report.
- Updated `research/ml/README.md` with the experiment command, readiness gate,
  artifact behavior, and non-profitability interpretation.
- No Java, public API, Gateway, frontend, deployment, or trading-domain
  documentation required updating.

## Validation Executed

- `research/ml/.venv/bin/python -m unittest discover -s research/ml/tests`:
  35 tests passed.
- `PYTHONPATH=research/ml/src python3 -m unittest discover -s research/ml/tests`:
  35 tests passed, with five dependency-specific tests skipped because the
  system interpreter does not have pandas/scikit-learn installed.
- Fixture experiment CLI: correctly returned `BLOCKED_BY_DATA_READINESS` with
  exit code `2`.
- `git diff --check`: passed.

The Java module was not modified by this Story, so no Java tests were required.

## Scope and Deviations

No material scope deviation was identified. No synthetic runtime data,
historical backfill, online inference, production model persistence, trading
recommendation, Risk, Broker, Execution, Gateway, or frontend behavior was
introduced.

Plots were deferred because the real dataset fails readiness; metrics and
predictions remain the higher-value artifacts for the first executable run.

DevLog context was unavailable because the configured provider reported
exhausted quota. Current repository and runtime state remain authoritative.

## Vault Outcome

- Vault consulted: no.
- Outcome: no vault action.
- Rationale: this Story concerns repository-local offline experimentation and
  existing ADR-governed data contracts; no curated transverse note was needed.
- No Obsidian vault content was modified.

## Approval Boundary

This report records implementation evidence. It does not grant Code Review
approval or authorize commit, push, merge, or pull-request creation.
