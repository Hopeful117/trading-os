# Story 0109B - First ML Baseline Experiment

## Status

Proposed - pending repository analysis and implementation-plan approval.

## Goal

Implement the first reproducible Machine Learning research experiment in
Trading OS.

The experiment must answer one falsifiable question without presuming a
positive result:

> Does `feature-projection-v1` contain measurable out-of-sample information
> about BTC/USD 4-hour forward return compared with a naive baseline?

This Story is the first Story authorized to train an ML model. It does not
claim that ML works, establish profitability, or create trading authority.

## Research Question

### Hypotheses

`H0`: `feature-projection-v1` does not improve prediction of BTC/USD 4-hour
forward return relative to the naive baseline on chronological out-of-sample
data.

`H1`: `feature-projection-v1` provides measurable chronological out-of-sample
improvement for BTC/USD 4-hour forward return relative to the naive baseline.

The target, market, and horizon must not be changed after seeing results.

## Scope

### Dataset boundary

Reuse the validated offline Python package and contract created by Story 0109A.
The pipeline remains:

```text
Java dataset
    -> 0109A validation
    -> validated research dataset
    -> 0109B preprocessing
    -> chronological experiment
    -> research artifacts
```

Do not create a second dataset reader or bypass 0109A validation. Pandas may be
used only after the bundle has passed validation.

### Experiment definition

- Market: BTC/USD.
- Horizon: `PT4H`.
- Target: `ObservedMarketOutcome.forwardReturn`.
- Feature projection: `feature-projection-v1`.
- Eligible rows: `ELIGIBLE` samples with `AVAILABLE` outcomes only.
- Ordering: chronological by `evaluationCutoff`, with deterministic secondary
  ordering.
- Split: approximately 70% oldest observations for train and 30% newest for
  test.
- Purge: remove train observations whose future outcome window overlaps the
  test start. For test start `T`, remove rows where `T0 + H > T`.
- Embargo: no separate V1 embargo unless implementation analysis identifies a
  specific need beyond horizon purge; document the decision.
- Primary baseline: predict zero.
- Secondary baseline: predict the train-only mean target.
- Primary model: scikit-learn `Ridge`, fixed documented `alpha=1.0` unless a
  repository-specific reason requires another fixed value.
- Primary metric: MAE.
- Secondary metrics where mathematically defined: RMSE, R2, Pearson
  correlation, Spearman correlation, and directional accuracy as a diagnostic
  only.

### Preprocessing

Build preprocessing dynamically from the validated feature schema and keep it
inside a scikit-learn `Pipeline`, preferably using a `ColumnTransformer`:

- Numeric values: validated Decimal values converted to `float64` only at the
  ML boundary, train-fitted median imputation where necessary, and train-fitted
  `StandardScaler`.
- Categorical values: train-fitted
  `OneHotEncoder(handle_unknown="ignore")`; no arbitrary ordinal encoding.
- Boolean values: present `false`/`true` converted to `0`/`1` only at the ML
  boundary; missing values remain distinct from false.
- Missingness: preserve semantic availability states and never silently replace
  missing values with zero. Add missingness indicators where appropriate.

Only canonical FeatureSnapshot features may enter `X`. Exclude market IDs,
timestamps, sample IDs, dataset and lineage fingerprints, horizon, outcome
fields, outcome status, creation timestamps, and all other metadata.

### Readiness gate

Separate framework readiness from research-data readiness. The framework may be
implemented when current data is insufficient, but model metrics must not be
calculated unless hard readiness requirements pass.

The implementation must report factual readiness information, including:

- eligible sample count;
- requested market and horizon;
- strict observed temporal coverage;
- available 4-hour outcomes;
- approximate non-overlapping 4-hour windows;
- train candidates, purged samples, final train samples, and test samples.

Operational recommendations are approximately 90 days of continuous strict
capture, 5,000 eligible samples, 500 non-overlapping 4-hour windows, and 100
non-overlapping test windows. These are readiness recommendations, not
universal statistical laws, and must not be weakened merely to obtain metrics.

If requirements fail, return an explicit `NOT_READY` result and block model
execution. A valid result may therefore be:

```text
Experiment framework: VALID
Experiment execution: BLOCKED_BY_DATA_READINESS
```

No samples, outcomes, or evidence may be fabricated, duplicated,
interpolated, backfilled, or synthetically augmented.

## Experiment Results

Introduce explicit conclusion semantics such as:

- `BLOCKED_BY_DATA_READINESS`;
- `INCONCLUSIVE`;
- `NO_MEASURABLE_IMPROVEMENT`;
- `PRELIMINARY_IMPROVEMENT`.

Never introduce `PROFITABLE`, `TRADING_READY`, or `PRODUCTION_READY` conclusions.

Ridge improvement over the primary baseline is necessary but not sufficient for
a positive preliminary conclusion. Interpret sample size, effective
independence, temporal stability, residual behavior, and secondary metrics.
Undefined statistics must remain explicitly undefined or unavailable with a
reason; they must not be converted to zero.

Report an approximate count of non-overlapping 4-hour windows based on actual
temporal coverage. This is a diagnostic, not a perfect effective sample-size
estimator.

## Artifacts

The experiment must produce separate artifacts when execution is ready:

- `experiment-manifest.json`: versioned experiment definition, dataset and
  feature/outcome versions, market, horizon, target, boundaries, purge policy,
  preprocessing, baselines, model parameters, dependency versions, counts,
  metrics, and conclusion.
- `metrics.json`: population counts, split and purge counts, baseline/model
  metrics, improvements, diagnostics, and conclusion.
- `predictions.jsonl`: test-only sample identity, cutoff, actual forward return,
  zero prediction, train-mean prediction, and Ridge prediction.
- Optional small plots only when readiness passes: target distribution,
  actual-versus-predicted over time, residual distribution, prediction scatter,
  and coefficient magnitude.
- Optional coefficient diagnostics with post-transform feature names and an
  explicit warning that coefficient magnitude is not causal importance.

Create a deterministic semantic experiment fingerprint when this can be done
without weak identity semantics. It may include dataset fingerprint, experiment
version, market, horizon, target, boundaries, purge, preprocessing, model, and
parameters. It must exclude timestamps, filesystem paths, random UUIDs, and
other operational metadata.

## CLI

Extend the existing research package rather than creating a second package.
The experiment command should conceptually support:

```bash
python -m research_dataset experiment \
  --manifest dataset-manifest.json \
  --samples samples.jsonl \
  --market BTC/USD \
  --horizon PT4H \
  --output-dir research-output/
```

The command must validate the bundle first, fail closed on invalid or
unsupported data, and return a clear non-zero status when readiness blocks
execution. A readiness command may be added only if it does not duplicate
diagnostics unnecessarily.

## Constraints

- Java remains authoritative for historical facts, deterministic features,
  outcomes, eligibility, and dataset identity.
- Python remains offline and read-only relative to trading.
- No network, live market data, Broker, Execution, Risk mutation, TradePlan,
  Challenge, or recommendation API access.
- No random train/test split, test-set preprocessing, target leakage, or
  outcome metadata in `X`.
- No feature selection, hyperparameter search, walk-forward engine, or extra
  model family in V1.
- No synthetic data, historical backfill mode, new indicators, external news,
  funding, order book, volume, or raw price features unless already present in
  the canonical feature projection.
- Ridge is the primary model. LinearRegression is optional only as a small
  secondary diagnostic.
- Do not add RandomForest, XGBoost, LightGBM, CatBoost, boosting, neural
  networks, PyTorch, TensorFlow, LSTM, Transformers, LLM, or RAG.
- Do not persist a production model, serve online inference, create a FastAPI
  endpoint, or publish results into the trading domain.
- Do not create a new ADR unless implementation reveals a genuinely new
  architectural authority, evidence mode, deployed service, or model authority.

## Tests

Add test-only synthetic fixtures clearly marked as synthetic. They must never be
reported as real Trading OS research evidence.

Required test coverage includes:

- chronological ordering and deterministic 70/30 split;
- temporal 4-hour purge and no overlapping train window into test;
- rejection of too-small populations and insufficient post-purge samples;
- train-only numeric imputation and scaling;
- train-only categorical encoding and unknown test categories;
- boolean handling and all missingness states;
- test-only changes not affecting fitted preprocessing;
- exact zero baseline and train-only mean baseline;
- MAE, RMSE, R2, Pearson, Spearman, and undefined-statistic handling;
- reproducible split, predictions, metrics, and semantic experiment identity;
- explicit leakage regression checks for target, outcome fields, metadata, and
  preprocessing.

After unit tests, attempt the experiment against the actual local dataset.
Report the real dataset fingerprint, readiness facts, and blocked or executed
result. Never substitute the synthetic fixture for runtime reporting.

## Documentation

Document the research question, hypotheses, data requirements, market, horizon,
target, split, purge, preprocessing, baselines, Ridge configuration, metrics,
readiness gate, artifacts, runtime result, and limitations.

Preserve the distinction:

```text
predictive improvement != tradable edge
tradable edge != profitability
```

## Acceptance Criteria

1. The existing 0109A validated dataset boundary is reused without a second
   reader or validation bypass.
2. The experiment selects only the explicit BTC/USD, `PT4H`,
   `feature-projection-v1`, eligible, available-outcome population.
3. Samples are chronologically ordered and evaluated with a 70/30 holdout plus
   a semantic 4-hour horizon purge.
4. All preprocessing is train-fitted inside a reproducible pipeline.
5. Zero and train-mean baselines use the exact same protected test population
   as Ridge.
6. Ridge regression uses the exact forward-return target and no model tuning or
   target-derived labels.
7. Invalid data, unsupported contracts, leakage, insufficient samples, or an
   insufficient post-purge split fail closed before metric calculation.
8. Readiness and conclusion states are explicit and conservative.
9. Experiment manifest, metrics, and test-only predictions are deterministic
   and preserve dataset identity and configuration.
10. Unit tests cover temporal split/purge, train-only preprocessing, baselines,
    metrics, leakage, and reproducibility.
11. A real-data run is attempted and reports the factual readiness result; a
    valid `BLOCKED_BY_DATA_READINESS` result is acceptable.
12. No online inference, production model persistence, trading authority,
    Broker, Risk, Execution, or frontend capability is introduced.

## Validation Requirements

- Run the existing Python test suite and any formatter, linter, or type checker
  established by Story 0109A.
- Run the experiment against the actual local bundle after tests.
- Run affected Java tests only if the Java research contract changes.
- Run `git diff --check`.
- Review specifically for random splits, test-fitted preprocessing, target or
  metadata leakage, incorrect purge boundaries, missingness collapse, and
  unsupported version acceptance.

## Follow-Up

Future Stories may introduce walk-forward validation, additional models,
hyperparameter selection, or production model concerns only after this first
experiment establishes a sufficient truthful dataset and defines their own
contracts.
