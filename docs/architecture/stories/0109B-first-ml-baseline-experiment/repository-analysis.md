# Repository Analysis - Story 0109B

Date: 2026-10-16 · Branch: `main` · HEAD: `d226ed7bb1d3c1b3cf8bb0cb4e2f237e5681deea`

## Workflow and Authority

- Story 0109B is now present at `story.md`; this analysis is the first
  repository-analysis artifact for the Story.
- The repository is clean except for the new Story 0109B artifact set.
- `HEAD` and `origin/main` resolve to the merge of Story 0109A / PR #119.
- `git fetch origin` was attempted but failed because the configured SSH
  security-key operation was refused. No Git authentication or remote state was
  changed.
- DevLog context was requested but unavailable because the provider reported
  exhausted quota.
- Current repository state and runtime data are authoritative.

## Architectural Authority

ADR-057 through ADR-062 establish the required boundary:

- Historical evidence is valid only when its `knownAt` and evidence policy make
  it available at the evaluation cutoff.
- `STRICT_OBSERVED` replay never falls back to provider history or current
  responses.
- Objective future outcomes are immutable facts, not ML labels or strategy
  results.
- `feature-projection-v1` is derived only from deterministic T0 artifacts and
  must not contain outcome or future evidence.
- Outcome selection is explicit by complete immutable identity and fingerprint;
  no latest-row or database-order selection is allowed.
- Java owns factual dataset construction and Python owns later preprocessing,
  splitting, and model experimentation.
- No new ADR is required for an offline, read-only statistical experiment unless
  implementation introduces a new evidence mode, model authority, deployed
  service, or persisted prediction contract.

## Story 0109A Implementation

The merged 0109A implementation is under `research/ml` and provides:

- `pyproject.toml` with the package name `trading-os-research-dataset`;
- `research_dataset.__main__` with `validate` and `diagnose` commands;
- `research_dataset.validator.validate_bundle` with strict manifest and JSONL
  validation;
- Decimal parsing and validation before any ML conversion;
- explicit feature missingness, provenance, outcome, lineage, checksums, and
  SHA-256 fingerprint validation;
- market and cutoff population checks;
- Java-compatible sample ID verification;
- duplicate semantic identity rejection;
- a Java-generated golden bundle under
  `research/ml/tests/fixtures/java-generated/`;
- standard-library-only runtime dependencies at present.

0109A does not currently expose a typed validated dataset object or loader. The
validator returns `ValidationResult` containing diagnostics and summary data;
the 0109B implementation must introduce the smallest reusable validated-data
representation without parsing the bundle through a second independent reader.
The intended boundary is one loader that invokes the existing validation path,
then exposes immutable validated rows for preprocessing.

The current package has no pandas, scikit-learn, or Pydantic dependency. The
working Python environment has NumPy `1.26.4`, but pandas, scikit-learn, and
Pydantic are unavailable. Dependency versions must therefore be declared by the
project rather than inferred from the local environment.

## Canonical Dataset Contract

The input remains the 0109A bundle:

```text
dataset-manifest.json
samples.jsonl
```

Only rows satisfying all of the following may enter the experiment population:

- market is the configured BTC/USD market identity;
- horizon is `PT4H`;
- sample `status` is `ELIGIBLE`;
- outcome `status` is `AVAILABLE`;
- feature projection is `feature-projection-v1`;
- outcome definition and dataset versions are supported;
- the bundle passes 0109A validation.

The model matrix must be built only from validated FeatureSnapshot feature
values and semantic availability statuses. The following must remain outside
`X`: market and sample IDs, evaluation cutoffs, horizons, dataset and lineage
fingerprints, feature snapshot metadata, outcome fields, outcome status,
creation timestamps, and all other metadata.

## Current Runtime Data

The running local PostgreSQL services were inspected without changing data.
Current facts are:

| Fact | Value |
|---|---:|
| Persisted FeatureSnapshots | 0 |
| Persisted outcomes | 7 |
| Available outcomes | 6 |
| Outcome horizon | `PT1H` only |
| Historical OHLC rows | 598 |
| Markets with historical OHLC | 1 |
| Historical OHLC rows with `knownAt <= closeTime` | 0 |

The current runtime therefore has no eligible FeatureSnapshot population and no
`PT4H` outcome population. It cannot truthfully produce an ML metric for the
0109B question. The expected runtime result is:

```text
frameworkReady = true
dataReady = false
conclusion = BLOCKED_BY_DATA_READINESS
```

The implementation must report this result with factual counts and must not
duplicate rows, reinterpret backfilled candles, fabricate FeatureSnapshots,
interpolate returns, weaken `STRICT_OBSERVED`, or substitute the golden fixture
for the runtime experiment.

## Proposed Experiment Architecture

The smallest coherent implementation is an extension of `research_dataset`:

1. Load and validate the existing Java bundle through the 0109A validation
   boundary.
2. Expose immutable validated rows as a `ResearchDataset` representation.
3. Apply explicit market, horizon, eligibility, projection, and available
   outcome filters while recording removal counts.
4. Sort by `evaluationCutoff` with deterministic secondary keys.
5. Calculate strict temporal coverage and approximate non-overlapping 4-hour
   windows.
6. Evaluate readiness before constructing a model or calculating metrics.
7. Split chronologically at the 70/30 boundary and purge training rows whose
   `[evaluationCutoff, evaluationCutoff + PT4H]` window overlaps the test start.
8. Build train-fitted numeric, categorical, and boolean preprocessing inside a
   scikit-learn `Pipeline` and `ColumnTransformer`.
9. Evaluate zero and train-mean baselines plus fixed-alpha Ridge on the exact
   same protected test rows.
10. Emit separate experiment manifest, metrics, and test-only prediction
    artifacts only when readiness permits execution.

The loader, split policy, readiness policy, preprocessing, metrics, and
artifact serialization should be separately testable. A complex walk-forward
engine, model registry, online endpoint, or broad model catalog is not justified
for V1.

## Readiness Policy Requiring Explicit Plan Definition

The Story gives operational recommendations of approximately 90 days strict
capture, 5,000 eligible samples, 500 non-overlapping 4-hour windows, and 100
non-overlapping test windows. It explicitly says these are not universal laws.

The Implementation Plan must define which are hard V1 gates and which are
diagnostic warnings. It must not lower them after inspecting the result. At a
minimum, a readiness failure must occur for zero eligible rows, no `PT4H`
outcomes, insufficient rows for a chronological split, or insufficient rows
remaining after purge. Any chosen count/duration thresholds must be documented
before experiment execution and included in the experiment manifest.

## Leakage and Temporal Risks

The principal implementation risks are:

- loading JSONL independently of the 0109A validator;
- including outcome or lineage fields in `X`;
- fitting imputation, scaling, or category encoders on test/all data;
- using random split or file order;
- purging an arbitrary row count rather than temporal overlap;
- calculating the train-mean baseline from the complete population;
- converting missingness into zero, false, or a synthetic category;
- reporting undefined metrics as zero;
- treating a tiny synthetic fixture or positive result as runtime evidence;
- allowing unsupported dataset or feature versions through the experiment path.

The test plan must include explicit leakage regression tests that alter test-only
values and verify train-fitted preprocessing is unchanged.

## Required Artifacts

When data readiness passes, the experiment should produce:

- `experiment-manifest.json` describing the exact experiment definition,
  versions, boundaries, purge, preprocessing, baselines, model, dependencies,
  counts, and semantic identity;
- `metrics.json` containing counts, baseline/model metrics, improvements,
  undefined-statistic reasons, diagnostics, and conservative conclusion;
- `predictions.jsonl` containing test-only sample identity, cutoff, actual
  forward return, zero prediction, train-mean prediction, and Ridge prediction;
- optional plots and coefficient diagnostics only when execution is ready.

Creation timestamps, absolute paths, random UUIDs, and other operational values
must not participate in semantic experiment identity. No production model or
online inference artifact is required.

## Validation Plan

Required implementation validation:

- Python tests for validated loading, population filtering, chronological
  ordering, 70/30 split, temporal purge, small-population rejection, and
  deterministic repeatability;
- train-only preprocessing tests for numeric imputation/scaling, categorical
  unknown handling, boolean conversion, and all supported missingness states;
- leakage regression tests for test-only mutations and excluded metadata;
- baseline tests proving zero predictions and train-only mean behavior;
- metric tests for MAE, RMSE, R2, Pearson, Spearman, and undefined cases;
- artifact reproducibility tests;
- the existing 0109A Python checks and CLI validation;
- an actual local runtime attempt reporting `BLOCKED_BY_DATA_READINESS`;
- `git diff --check`;
- affected Java tests only if the Java contract changes.

No Java change is currently required. If the implementation reveals a need to
change the Java dataset contract, the change must remain within the approved
Story scope and affected Java tests must be run.

## Scope Assessment

The repository architecture supports the Story as an offline extension of the
existing research package. No new ADR is currently required. The implementation
must stop and request architectural guidance if it requires a deployed Python
service, live data access, a new evidence mode, prediction persistence into a
trading domain, model authority, broker/risk/execution interaction, or a
production model registry.

## Analysis Outcome

The Story is implementable after the explicit readiness policy and experiment
artifact contract are fixed in the Implementation Plan. Current runtime data is
not sufficient for a truthful experiment execution; the first real run must
therefore be expected to return `BLOCKED_BY_DATA_READINESS`.

The next permitted workflow stage is the Implementation Plan, after explicit
human approval of this Repository Analysis.
