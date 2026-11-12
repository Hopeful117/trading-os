# Story 0230 - Implementation Plan

## Plan Status

**Status:** Approved - implementation in progress
**Story:** `0230`
**ADR:** `ADR-097`
**Repository Analysis:** Human-approved for planning on 2026-11-12

This plan is implementation guidance. It does not authorize code changes,
KMeans execution, held-out evaluation, dataset generation, commit, push or merge
until the current Implementation Plan is explicitly approved.

## Implementation Decision

The implementation will use a single directional boundary:

```text
Dukascopy M1/H1 evidence and ML-01 rows
        ↓ Java Market Intelligence
dependency footprints, partition/purge/embargo, fingerprints
        ↓ immutable versioned research bundle
research/ml Python package
        ↓
train-only scaling, KMeans candidates, validation selection,
stability diagnostics and frozen-test descriptive evaluation
```

### Java authority

Java remains authoritative for:

- source and bundle identity;
- Dukascopy quality and continuity semantics;
- feature and outcome values;
- actual feature and future dependency timestamps;
- common UTC partition assignment;
- dependency-aware purge and candidate embargo exclusions;
- temporal-dependence and effective-sample diagnostics;
- row, configuration and bundle fingerprints;
- readiness and fail-closed decisions.

### Python responsibility

The isolated `research/ml` package consumes only the frozen Java bundle and is
responsible for:

- validating the dedicated ML-01 Dukascopy bundle schema;
- fitting `StandardScaler` on the Java-declared training rows only;
- fitting configured pooled KMeans candidates on scaled training features;
- calculating training/validation structural and stability diagnostics;
- recording the selection-freeze artifact;
- applying the frozen scaler/model to the untouched test rows;
- producing descriptive H+4/H+12/H+24 cluster-conditioned outcomes and
  predeclared uncertainty diagnostics.

Python must not reparse provider data, recompute canonical Java features or
reassign partitions, purge rows or embargo rows. Any mismatch between Java
metadata and Python validation must fail closed.

## Phase 1 - Define Immutable Configuration Contracts

### 1.1 Java configuration

Add a small immutable configuration contract in the existing research package,
for example `Ml01DukascopyExperimentConfiguration`, containing:

- experiment identifier and version;
- source dataset fingerprint and accepted bundle/profile identifiers;
- common UTC research bounds;
- named partition scenario;
- train, validation and test end-exclusive boundaries;
- purge policy identifier;
- embargo duration and identifier;
- feature schema and ordered feature names;
- outcome definition and horizons;
- quality profiles under comparison;
- model family and candidate `k` values;
- deterministic seed and initialization configuration;
- dependency/runtime versions;
- block and uncertainty method identifiers;
- model-selection and stability thresholds.

The constructor must reject incomplete or contradictory configurations. No
scientific parameter may receive a hidden default. A configuration that lacks
approved threshold/block/uncertainty values may be serialized for readiness
diagnostics but must block model execution.

Provide named candidate factories for:

- `COMMON_UTC_70_15_15`;
- `COMMON_UTC_60_20_20`.

These factories identify diagnostic scenarios only. They must not select a
scenario based on test outcomes.

### 1.2 Configuration fingerprint

Fingerprint canonical ordered material containing every configuration field,
including null/blocked state representation, runtime versions and threshold
values. The fingerprint must be present in every Java and Python artifact.

## Phase 2 - Extend the Java Research Row Contract

### 2.1 Dependency footprint

Extend the Dukascopy preparation row/outcome representation so every horizon
contains:

- feature lookback start;
- feature cutoff;
- complete feature dependency timestamps or the immutable H1 dependency IDs;
- all future observation open timestamps;
- explicit future dependency end;
- source, feature and outcome fingerprints.

The future end must be derived from the actual last H1 outcome observation, not
from an elapsed-time approximation. Existing outcome values and definitions must
remain unchanged.

### 2.2 Row fingerprint compatibility

Include the complete dependency footprint and its horizon in row fingerprint
material. Update the Dukascopy bundle version when the serialized contract
changes. Existing Story 0229 generated artifacts must not be silently rewritten;
the new version must make incompatibility visible.

### 2.3 Bundle schema

Extend the Dukascopy manifest and observations with an explicit versioned
research schema, including:

- experiment/contract version;
- source and preparation fingerprints;
- quality profile and continuity policy;
- feature/outcome schema versions;
- dependency-footprint contract version;
- partition scenario and boundaries;
- purge and embargo policy;
- row partition and exclusion reason;
- dependence diagnostics;
- readiness and execution authorization state.

Keep output research-only and separate from canonical `FeatureSnapshot` data.

## Phase 3 - Implement Java Partition, Purge And Dependence Boundary

### 3.0 Mandatory three-way split

Every authorized experiment run MUST use three distinct chronological
populations:

```text
TRAIN -> fit the scaler and KMeans candidates
VALIDATION -> select k, seed and stability configuration
TEST -> evaluate once after selection freeze
```

A train/test-only split is invalid and must fail closed. The implementation must
not use row counts to create these populations. It must use common UTC cutoff
boundaries for both instruments, preserve the full timeline including empty or
rejected buckets, and record raw, purged and embargoed counts for each of the
three populations.

After purge and embargo, `TRAIN`, `VALIDATION` and `TEST` must remain explicitly
distinguishable. If a required population is empty or lacks the configured
minimum feasibility evidence, the run may produce diagnostics but must not fit a
model, select a candidate or evaluate held-out outcomes.

### 3.1 Partition assignment

Implement a Dukascopy-specific partitioning component or a demonstrably
provider-independent pure component. Do not modify `Ml01HistDataPreparation` to
accept Dukascopy semantics.

Assign rows by common UTC `cutoff`:

```text
cutoff < trainEndExclusive       -> TRAIN
cutoff < validationEndExclusive  -> VALIDATION
cutoff < testEndExclusive        -> TEST
otherwise                        -> OUT_OF_RANGE
```

Chronological boundary inclusivity/exclusivity must be tested exactly.

### 3.2 Dependency-aware purge

For each protected boundary:

- purge a train row when any selected outcome future end is at or after the
  train/validation boundary;
- purge a validation row when any selected outcome future end is at or after the
  validation/test boundary;
- never use test rows for fitting or selection;
- reject rows whose feature or outcome dependency crosses an unknown/rejected
  continuity gap;
- preserve ordinary overlap within a partition and report it as dependence.

Each exclusion must include a stable reason code, boundary, affected horizon,
dependency timestamps, configuration version and configuration fingerprint.

### 3.3 Candidate embargo

Materialize separate runs/manifests for:

- dependency purge with `NONE` additional embargo;
- dependency purge with the 24-hour candidate embargo.

Do not combine the two counts. The 24-hour value remains a diagnostic
configuration, not a universal scientific constant.

### 3.4 Dependence diagnostics

Compute and serialize:

- raw and retained joint-available anchors;
- exclusion counts by reason and horizon;
- contiguous segment IDs and length distribution;
- feature-window overlap counts;
- future-window overlap counts;
- source/H1 observation reuse counts where available;
- actual-dependency non-overlapping-anchor sensitivity;
- instrument and UTC calendar-month distributions;
- explicit effective-sample limitation state.

Do not calculate or claim a statistically validated effective sample size unless
the configured approved method and thresholds are present.

## Phase 4 - Add Java Bundle Validation

Add a Dukascopy-specific validator for the new bundle contract. It must verify:

- source dataset and manifest identity;
- supported schema and policy versions;
- ordered feature names and outcome horizons;
- row count and row ordering;
- row fingerprints and bundle fingerprint;
- dependency timestamps and future-end consistency;
- partition boundaries and exclusion reasons;
- purge proof for every retained train/validation row;
- no test row appears in training or selection populations;
- readiness state matches configured approval gates.

The validator must fail closed on unknown fields that can affect scientific
semantics, invalid timestamps, non-finite values, missing provenance,
contradictory partition metadata or fingerprint mismatch.

## Phase 5 - Extend The Python Research Consumer

### 5.1 Dedicated loader

Add a dedicated loader/validator for the Java Dukascopy ML-01 bundle. Do not
change the existing `research-dataset-bundle-v1` contract or the BTC/USD Ridge
baseline behavior.

The loader must expose immutable rows and manifest metadata for:

- four numeric features in the declared order;
- instrument identity for diagnostics only;
- quality profile/stratum;
- partition and purge metadata;
- H+4/H+12/H+24 outcomes;
- dependency and fingerprint metadata.

### 5.2 Train-only scaling

Fit one `StandardScaler` from the selected Java-declared training rows only.
Persist means, scales, feature order, input row fingerprints and scaler
fingerprint. Apply the frozen scaler to validation and test rows without fitting
or imputing from those populations.

Reject missing/non-finite required features unless the frozen configuration
explicitly declares an approved handling rule. Do not introduce implicit
imputation.

### 5.3 KMeans candidates

Implement pooled KMeans candidates for `k=2..6` using only the four scaled
features. Instrument identity, outcomes, quality labels and timestamps are
diagnostic metadata and must not be model inputs.

Record deterministic seeds, initialization count, convergence settings,
scikit-learn version, input row fingerprints, centroids and assignments.

### 5.4 Model selection and stability

Calculate configured training/validation diagnostics:

- separation and compactness;
- cluster sizes and balance;
- original-unit centroid profiles;
- repeated-seed assignment stability;
- chronological-block stability;
- temporal persistence and transitions;
- global and per-instrument composition;
- quality-profile and dependency-thinning sensitivity.

Selection must consume only training/validation diagnostics. If selection
thresholds or block configuration are absent, emit a blocked readiness artifact
and do not select a model.

### 5.5 Selection freeze and held-out evaluation

Write an immutable selection-freeze artifact containing the selected scenario,
scaler fingerprint, model fingerprint, candidate diagnostics, selection criteria,
input fingerprints and timestamp-independent configuration material.

Only after the freeze artifact validates may the workflow score the untouched
test rows. Held-out output must be descriptive, global and per instrument, for
H+4/H+12/H+24, with cluster counts, distributions, quality strata, temporal
blocks, uncertainty method and multiple-comparison metadata.

The test path must reject any attempt to mutate or append model-selection
artifacts after evaluation begins.

## Phase 6 - Tests

### Java tests

Add focused tests for:

- configuration completeness, contradiction and fingerprinting;
- dependency footprint and future-end serialization;
- exact 70/15/15 and 60/20/20 UTC boundary assignment;
- future-boundary purge and no-purge within-partition overlap;
- no-embargo versus 24-hour embargo separation;
- unknown/rejected dependency fail-closed behavior;
- segment/overlap/non-overlapping-anchor diagnostics;
- row, bundle and manifest validation;
- test-isolation metadata and selection-freeze prerequisites;
- repeated deterministic output;
- unchanged HistData contract regression coverage.

### Python tests

Add virtual-environment tests for:

- dedicated Dukascopy bundle loading and schema rejection;
- train-only scaler state under mutated validation/test values;
- KMeans candidate seed determinism and feature-schema isolation;
- selection using only train/validation data;
- selection-freeze validation;
- untouched-test enforcement;
- quality-profile/instrument diagnostics;
- deterministic model/evaluation artifacts;
- blocked state when thresholds, uncertainty or block configuration is absent.

Use synthetic fixtures for unit tests. Do not download or process the full
Dukascopy dataset in the unit test suite.

## Phase 7 - Validation And Controlled Execution

Run targeted validation first:

```bash
(cd market-intelligence && ../trading-core/mvnw -q -Dtest=\
DukascopyH1ResearchPipelineTest test)
```

Run the full affected Java module:

```bash
(cd market-intelligence && ../trading-core/mvnw test)
```

When Python files change, use the project-local environment:

```bash
research/ml/.venv/bin/python -m pytest research/ml/tests
```

If the research environment is absent or dependencies cannot be imported, install
the declared project dependencies before reporting validation unavailable.

Run:

```bash
```

Full real-data execution is a separate controlled step. It must first verify
the final post-fix reproducibility comparison, then verify the ADR-097 Gate A-C
conditions and explicit scientific readiness. If any gate is unresolved, it
must emit blocked diagnostics and not fit KMeans or evaluate the held-out test.

## Acceptance-Criteria Mapping

| Story criteria | Planned implementation evidence |
|---|---|
| AC-01 to AC-03 | Versioned Java config, row dependency footprint and bundle validator |
| AC-04 to AC-09 | Java UTC partition/purge/embargo component and dependence report |
| AC-10 to AC-14 | Python scaler, KMeans candidate and validation/stability artifacts |
| AC-15 to AC-17 | Selection-freeze artifact and guarded held-out evaluation |
| AC-18 to AC-19 | Quality-profile diagnostics and deterministic rerun tests |
| AC-20 to AC-22 | Research-only output contract, fail-closed validation and limitations report |

## Files And Artifacts

Expected implementation areas, subject to final repository validation:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata/Ml01DukascopyPreparation.java`
- new Dukascopy ML-01 configuration/partition/dependence/validation classes in
  the same research package only where existing contracts cannot be reused;
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata/Ml01DukascopyExperimentRunner.java`
- focused Java tests under the existing HistData research test package;
- new dedicated Python modules under `research/ml/src/research_dataset/`;
- new Python tests under `research/ml/tests/`;
- generated full-data artifacts only under ignored local research storage.

No accepted ADR, production API, broker service, Gateway route, MCP permission,
database schema or frontend surface should change.

## Risks And Stop Conditions

Stop and request human guidance if:

- the Java/Python handoff requires a new service or public API;
- ADR-097 does not provide enough authority for a required threshold or
  uncertainty method;
- exact ADR-093 outcome semantics cannot be preserved;
- the existing bundle cannot expose complete dependency timestamps without
  changing Story 0229 semantics;
- a test or model-selection path can observe test outcomes before freeze;
- final source/bundle reproducibility remains inconsistent;
- unrelated worktree changes would be modified or overwritten.

## Definition Of Done For The Plan

- [ ] Human approval of this Implementation Plan recorded.
- [ ] Implementation follows the Java-authority/Python-consumer boundary.
- [ ] All Story acceptance criteria are covered by implementation evidence.
- [ ] Targeted and affected-module tests pass.
- [ ] Python tests pass through the project virtual environment when applicable.
- [ ] Full-data execution is either gated and blocked with evidence or completed
      under the approved scientific conditions.
- [ ] Implementation Report and documentation reconciliation are produced.
- [ ] Code Review is completed separately.
- [ ] No automatic Git integration occurs.
