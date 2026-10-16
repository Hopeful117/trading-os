# Implementation Plan: First ML Baseline Experiment

## Scope

Implement the approved Story 0109B scope as an offline extension of the
existing `research_dataset` package:

- load only bundles that pass the Story 0109A validation boundary;
- expose a validated immutable research-dataset representation;
- select the fixed BTC/USD and `PT4H` experiment population;
- calculate explicit readiness and block metrics when readiness fails;
- implement chronological 70/30 split with semantic 4-hour purge;
- implement train-only mixed-type preprocessing;
- evaluate zero and train-mean baselines plus fixed-alpha Ridge;
- emit reproducible manifest, metrics, and test-only prediction artifacts;
- attempt the real local dataset and report its factual readiness result.

Out of scope:

- ML models other than Ridge and an optional LinearRegression diagnostic;
- hyperparameter search, feature selection, walk-forward evaluation, or
  production model persistence;
- online inference, APIs, services, model registry, LLM, RAG, or trading
  recommendations;
- historical backfill, synthetic data, data repair, or new feature semantics;
- Java contract changes unless a concrete 0109A interoperability defect is
  found.

## Readiness Policy

The V1 readiness policy will be encoded before the real experiment is run and
recorded in `experiment-manifest.json` when execution is possible.

The following are hard V1 gates, selected from the Story's operational target
recommendations:

- strict observed coverage of at least 90 continuous days;
- at least 5,000 eligible samples;
- at least 500 approximate non-overlapping 4-hour windows;
- at least 100 approximate non-overlapping 4-hour test windows;
- no gap larger than the canonical 15-minute capture cadence in selected
  strict-observed cutoffs;
- valid chronological train/test split with sufficient rows remaining after
  purge.

The gates are operational readiness criteria for this first experiment, not
universal statistical laws. The result must report each measured value and the
failed criteria. No threshold may be lowered after inspecting results.

With the current runtime facts, execution is expected to produce
`BLOCKED_BY_DATA_READINESS` with zero eligible samples and no `PT4H` outcomes.

## Package Design

Extend the existing package under `research/ml/src/research_dataset` without a
second reader.

### Validated dataset boundary

Add a small immutable representation, for example `ResearchDataset`, containing
the validated manifest metadata and validated sample rows needed by the
experiment. Add a loader that:

1. calls `validate_bundle`;
2. fails closed on diagnostics;
3. reads the already validated manifest and JSONL into typed research rows;
4. preserves Decimal values and explicit feature status until the ML boundary;
5. does not expose the result as domain authority or mutate the source files.

The loader must use the existing schema constants and validation behavior rather
than reimplementing a parallel contract. Any second parsing step must be a
projection of the same validated input, not an alternative acceptance path.

### Experiment definition and readiness

Add typed experiment configuration and result structures containing:

- experiment definition version;
- market identity and display label for BTC/USD;
- explicit validated market UUID because the 0109A bundle has UUIDs but no
  market symbols;
- `PT4H` horizon;
- `forwardReturn` target;
- readiness policy and measured facts;
- split and purge policy;
- preprocessing and model configuration;
- conclusion state.

Implement readiness before split, preprocessing, model fitting, or metric
calculation. Return structured `BLOCKED_BY_DATA_READINESS` results and a
controlled non-zero CLI exit without producing misleading metric/prediction
artifacts.

### Population and temporal split

Filter in this order and report counts after each step:

1. all validated rows;
2. configured market;
3. `PT4H` horizon;
4. `ELIGIBLE` status;
5. `AVAILABLE` outcome;
6. `feature-projection-v1`.

Sort by `evaluationCutoff`, then `marketId`, `horizon`, and `sampleId` for
deterministic behavior. Compute the 70/30 boundary using the ordered rows.

The test start is the first test row's `evaluationCutoff`. Retain only train
rows whose future outcome interval does not overlap that start:

```text
evaluationCutoff + PT4H <= testStart
```

The purge count and final train/test identities must be recorded. No random
split, shuffle, arbitrary row-count purge, or separate embargo is introduced in
V1.

Implement the approximate non-overlapping-window diagnostic by greedily walking
chronological cutoffs and counting a new window only when it begins at least
4 hours after the last counted window. Document that this is a diagnostic, not
an effective sample-size estimator.

### Feature matrix

Derive columns from the validated feature schema, never from arbitrary row
metadata. Build a pandas representation only after validation and keep the
source Decimal/availability semantics in the validated objects.

For each feature:

- Numeric: Decimal to float64 at the ML boundary, missing values as `NaN`,
  missingness indicator, `SimpleImputer(strategy="median", add_indicator=True)`
  fitted on train, then `StandardScaler` fitted on train.
- Categorical: missing/unavailable values represented by a dedicated technical
  missing sentinel distinct from real categories, then train-fitted
  `OneHotEncoder(handle_unknown="ignore", ...)`.
- Boolean: present true/false converted to 1/0 at the ML boundary; missing
  values remain missing and receive an explicit indicator rather than becoming
  false.

Use a `ColumnTransformer` containing per-type pipelines and a model `Pipeline`
with a final `Ridge(alpha=1.0)` estimator. Use the current scikit-learn API,
including `OneHotEncoder(handle_unknown="ignore")` and the supported sparse
output parameter. Record transformed feature names for coefficient diagnostics.

No outcome, target, market identity, timestamp, horizon, sample identity,
fingerprint, provenance, or creation metadata may enter `X`.

### Baselines and metrics

Evaluate all predictors on exactly the same protected test rows:

- ZERO: a vector of exact `0.0` predictions;
- TRAIN_MEAN: the mean of `y_train` only;
- Ridge: fitted on purged train rows through the complete pipeline.

Implement a metrics module with deterministic JSON-safe output for:

- MAE;
- RMSE;
- R2;
- Pearson correlation;
- Spearman correlation;
- directional accuracy as a diagnostic only;
- absolute and percentage MAE improvement against both baselines.

Represent undefined correlations, R2, and zero-denominator improvements as
structured unavailable values with reasons. Never coerce them to zero.

Classify conclusions conservatively:

- `BLOCKED_BY_DATA_READINESS` before fitting;
- `INCONCLUSIVE` when data executes but the evidence is insufficient or
  degenerate;
- `NO_MEASURABLE_IMPROVEMENT` when Ridge does not beat the zero baseline;
- `PRELIMINARY_IMPROVEMENT` only when Ridge beats the zero baseline and the
  secondary diagnostics do not expose an obvious degeneracy. This is not a
  profitability or production claim.

### Experiment artifacts and identity

Add deterministic serializers for:

- `experiment-manifest.json`: definition/version, dataset fingerprint, feature
  and outcome versions, market, horizon, target, temporal boundaries, purge,
  preprocessing, baselines, model parameters, dependency versions, counts,
  readiness, metrics references, and conclusion;
- `metrics.json`: population filtering, split/purge counts, metric values,
  undefined reasons, improvements, diagnostics, and conclusion;
- `predictions.jsonl`: test-only sample ID, cutoff, actual forward return, zero,
  train-mean, and Ridge predictions.

Use a canonical sorted material list joined by the existing unit separator for
an experiment fingerprint. Include only semantic configuration and boundaries;
exclude creation time, absolute paths, random UUIDs, and machine-specific data.
Do not emit metrics or predictions when readiness blocks execution; emit a
readiness/manifest artifact sufficient to explain the blocked result.

### CLI

Extend `research_dataset.__main__` with:

```text
python -m research_dataset experiment --manifest <path> --samples <path>
    --market BTC/USD --market-id <validated-market-uuid> --horizon PT4H
    --output-dir <path>
```

The command validates the bundle before loading experiment rows. It returns
zero for a completed valid experiment, a distinct controlled non-zero result for
readiness blocking, and non-zero failure for invalid input or implementation
errors. Existing `validate` and `diagnose` behavior must remain unchanged.

## Dependency Changes

Update the existing `research/ml/pyproject.toml`; do not create a second
environment or package. Add the minimal runtime dependencies with reproducible
constraints:

- `numpy==1.26.4`;
- `pandas==2.2.3`;
- `scikit-learn==1.7.1`.

The exact versions will be verified against Python `>=3.11` during validation.
No plotting dependency is required for the first implementation; plots remain
deferred until a real readiness-passing run exists.

## Tests

Add clearly marked synthetic test fixtures only for code verification.

### Temporal and readiness tests

- deterministic chronological ordering independent of input order;
- 70/30 boundary behavior;
- exact 4-hour purge boundary, including equality at `T0 + H == testStart`;
- overlapping training windows removed;
- insufficient sample and post-purge rejection;
- all readiness thresholds and factual failure reasons;
- approximate non-overlap diagnostic.

### Preprocessing and leakage tests

- numeric train-only median and scaler state;
- categorical train-only categories and unknown test category handling;
- boolean present/missing behavior;
- semantic missingness not converted to zero/false/category neutrality;
- test-only mutations do not change fitted train preprocessing;
- all metadata and outcome fields excluded from `X`;
- target is exactly `forwardReturn`.

### Baseline, metrics, and reproducibility tests

- zero predictions are exactly zero;
- train mean uses only train targets;
- known MAE/RMSE/R2/correlation values;
- undefined and constant-target cases;
- deterministic split, predictions, metrics, and semantic experiment fingerprint;
- artifact schema and test-only prediction rows;
- invalid 0109A bundle blocks before preprocessing/model construction.

Run the existing 0109A tests unchanged.

## Validation Sequence

1. Install the declared Python dependencies in the existing `research/ml`
   project environment or an isolated validation environment.
2. Run Python tests through the repository-equivalent command.
3. Run the existing 0109A validation and CLI tests.
4. Run `experiment` against the synthetic test fixture only through tests.
5. Attempt the real local bundle path and record the actual readiness result;
   do not substitute the fixture.
6. Run affected Java tests only if Java code changed.
7. Run `git diff --check` and inspect the complete diff.
8. Review specifically for random split, test-fitted preprocessing, target or
   metadata leakage, incorrect purge, missingness collapse, and unsupported
   versions.

## Documentation Reconciliation

Update the 0109B Story-local documentation with the actual CLI commands,
dependency versions, readiness policy, artifact schema, runtime result, and
limitations. Update `research/ml/README.md` only for commands and package
behavior introduced by this Story. Do not add an ADR unless the architecture
boundary changes.

## Approval Gate

This plan requires explicit human approval before implementation begins.
