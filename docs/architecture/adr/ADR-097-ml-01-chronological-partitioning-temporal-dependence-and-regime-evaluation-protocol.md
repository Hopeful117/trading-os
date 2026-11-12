# ADR-097 - ML-01 Chronological Partitioning, Temporal Dependence and Regime Evaluation Protocol

| | |
|---|---|
| **Status** | **ACCEPTED - human scientific approval recorded** |
| **Date** | 2026-11-12 |
| **Decision Type** | Offline ML-01.2/ML-01.3 scientific evaluation protocol |
| **Related ADRs** | ADR-092, ADR-093, ADR-094, ADR-095, ADR-096 |
| **Related Stories** | Story 0228, Story 0229 |

## 1. Context and Evidence

ML-01 is an offline exploratory experiment for deterministic OHLC regime
discovery on EUR/USD and GBP/USD. It is not a trading-signal, profitability,
execution or risk-control decision.

The prepared source is the 12-month Dukascopy BID M1 dataset for
`[2025-10-01T00:00:00Z, 2026-10-01T00:00:00Z)`:

- Acquisition dataset fingerprint:
  `c212d83c0050374400af8e8c3d9961a2bbcf0f9169fd2c149c0513fc3002f198`.
- EUR/USD observations: `371,614`.
- GBP/USD observations: `371,266`.
- Research timeframe: H1.
- Primary profile: `ML01_DUKASCOPY_EXPLORATORY_V1`.
- Comparison profiles: 60/60, 59/60 and 55/60.

The latest validation report is:

`docs/architecture/reports/ml-01.1-dukascopy-prepared-dataset-scientific-validation-2026-11-12.md`

Its primary technical populations are:

| Instrument | Feature available | Joint available | Joint eligible |
|---|---:|---:|---:|
| EUR/USD | 3,530 | 2,145 | 0 |
| GBP/USD | 2,927 | 1,630 | 0 |

The 59/60 comparison has 154 EUR/USD and 14 GBP/USD joint-available anchors.
The strict 60/60 population has zero joint-available anchors. The 55/60
population is heavily exposed to partial H1 windows and remains a measurement-
sensitivity concern.

The prepared population contains overlapping 33-H1 feature lookbacks and
24-observation future-outcome windows. The latest report identifies 48 EUR/USD
joint-available segments and 39 GBP/USD segments, with a longest segment of 64
anchors for both instruments. Raw anchor counts therefore must not be treated
as independent sample counts.

## 2. Decision Problem

Under what explicit and reproducible conditions may ML-01.2 train exploratory
KMeans models and ML-01.3 evaluate discovered regimes without temporal leakage,
unjustified independence assumptions or retrospective optimization on held-out
data?

This ADR proposes the protocol boundary. It does not approve any partition,
purge, embargo, minimum effective sample size, KMeans execution or model.

## 3. Decision Drivers

- Preserve ADR-093's Java-owned four-feature and H+4/H+12/H+24 contracts.
- Preserve ADR-094 fail-closed continuity and unknown-gap semantics.
- Preserve ADR-096 provider-specific 60/60, 59/60 and 55/60 distinctions.
- Use actual dependency timestamps rather than silently substituting elapsed
  wall-clock durations.
- Keep the final test period untouched until the frozen final evaluation.
- Separate availability, scientific eligibility, model selection and outcome
  evaluation.
- Quantify temporal dependence instead of assuming independent anchors.
- Keep all candidate parameters versioned and fingerprinted.
- Stop before ML-01.2 until the required human scientific gates are approved.

## 4. Proposed Partition Strategy

### 4.1 Common UTC boundaries

The first experiment SHOULD use common UTC calendar boundaries for both
instruments. The same cutoff timestamps make instrument comparisons auditable
and prevent choosing a favorable instrument-specific boundary.

Partition percentages refer to elapsed research time, not eligible row counts.
Rows are assigned by their H1 cutoff timestamp. Empty or rejected H1 buckets
remain in the chronological source timeline and never become eligible rows.

The primary candidate scenario is:

```text
70% chronological train
15% chronological validation
15% untouched chronological test
```

The `60/20/20` scenario remains a predeclared sensitivity scenario. It must not
be selected because it produces more favorable held-out outcomes.

For the current dataset, the candidate boundaries are approximately:

| Scenario | Train end | Validation end | Test end |
|---|---|---|---|
| 70/15/15 | 2026-06-13T12:00:00Z | 2026-08-07T06:00:00Z | 2026-10-01T00:00:00Z |
| 60/20/20 | 2026-05-08T00:00:00Z | 2026-07-20T00:00:00Z | 2026-10-01T00:00:00Z |

These dates are diagnostic examples, not approved configuration. Boundaries
must be frozen in a versioned experiment definition before any model-selection
run.

### 4.2 Instrument strategy

The primary experiment SHOULD preserve ADR-093's one shared KMeans model across
EUR/USD and GBP/USD, using only the four common feature values and never the
instrument identity as a model feature. Results must nevertheless be reported
globally and separately per instrument.

This is the simplest protocol consistent with ADR-093 and tests whether a
shared feature-space regime description is plausible. Instrument-specific
models and train-on-one/evaluate-on-the-other transfer experiments are
sensitivity studies only and require separate pre-registration. Pooling must
not be justified solely by increasing the raw anchor count.

## 5. Temporal Dependency Model

Each row must expose a dependency footprint containing:

1. `featureLookbackStart`: the timestamp of the oldest H1 observation used by
   the 33-valid-H1 feature calculation;
2. `cutoff`: the evaluated H1 candle cutoff;
3. `futureOpenTimes`: every actual H1 observation used for each future horizon;
4. `futureEnd`: the latest timestamp used by the relevant outcome;
5. the complete source and row fingerprints.

The feature interval is the actual set of 33 valid H1 observations, not a
promise that 33 elapsed clock hours were available. The future interval is the
actual sequence of H+4, H+12 or H+24 valid H1 observations. Missing or rejected
H1 buckets are not silently converted into valid elapsed-time observations.

The complete dependency footprint for a row is the union of its feature
lookback interval and the future-outcome interval. It must be persisted in the
research bundle and included in the row/configuration fingerprint.

### 5.1 Dependence categories

- **Future-label leakage:** an outcome observation or any future-derived value
  is used in feature fitting, scaling, KMeans fitting, cluster-count selection
  or model selection.
- **Feature-history overlap:** neighboring rows reuse historical H1 observations
  in their lookbacks. This is dependence, but it is legitimate market history
  available at the later cutoff and is not automatically leakage.
- **Shared future observations:** neighboring rows reuse future outcome candles.
  This is dependence for uncertainty estimation and becomes leakage when the
  reused outcome crosses a protected partition boundary.
- **Source overlap:** the same source M1/H1 observations are reused across rows.
  It must be reported and must not be described as independent replication.

## 6. Purge and Embargo

### 6.1 Dependency-aware purge

Purge MUST operate on actual dependency timestamps, not only fixed durations.
For a boundary `B`:

- A training row is removed if its future dependency end is at or after `B`.
- A validation row is removed if its future dependency end is at or after the
  test boundary.
- A test row is never used for model selection or fitting, regardless of its
  outcome availability.
- A row is removed if its feature dependency is not available under the frozen
  source timeline or crosses an unknown continuity gap.

Feature history before a partition boundary may be used to compute a later
validation/test row because it was available at that row's cutoff. Such reuse
must be reported as temporal dependence. It must not be treated as a reason to
discard all historical context unless a separate independence sensitivity is
being evaluated.

### 6.2 Candidate embargo

The initial diagnostic SHOULD compare:

- dependency-aware purge with no additional embargo;
- dependency-aware purge plus a 24-hour candidate embargo after train and
  validation boundaries.

The 24-hour value is a diagnostic aligned with the maximum H+24 horizon, not an
approved constant. A 33-hour embargo must not be imposed merely because the
feature lookback has 33 observations; historical feature context is not future
label leakage.

Every purge and embargo exclusion must include its reason, dependency
timestamps, configuration version and configuration fingerprint. A validation
must prove that no retained training row has future dependency at or beyond the
next protected boundary.

### 6.3 Current diagnostic evidence

The existing candidate 24-hour purge/embargo diagnostics produced:

| Instrument | Scenario | Train | Validation | Test |
|---|---|---:|---:|---:|
| EUR/USD | 70/15/15 | 1,495 | 308 | 327 |
| GBP/USD | 70/15/15 | 1,099 | 222 | 294 |
| EUR/USD | 60/20/20 | 1,273 | 340 | 515 |
| GBP/USD | 60/20/20 | 919 | 291 | 420 |

These counts are feasibility evidence only. They do not approve the scenario,
the embargo or minimum sample sizes.

## 7. Temporal Dependence and Effective Sample Size

Every report MUST contain all of the following counts:

- raw joint-available anchors;
- anchors after dependency-aware purge and embargo;
- contiguous segment count and segment length distribution;
- non-overlapping anchor sensitivity;
- feature-window overlap counts;
- future-window overlap counts;
- instrument and calendar-month distribution;
- effective-sample limitations.

The first sensitivity analysis SHOULD use actual dependency footprints to select
non-overlapping anchors rather than a hard-coded elapsed-hour stride. A
conservative anchor is retained only when its complete dependency footprint does
not overlap the previous retained footprint. This is a sensitivity population,
not the default population.

Segment-level summaries MUST be reported because 2,145 and 1,630 raw anchors
are not independent observations. Temporal thinning may be used to show
robustness, but it must not be used to manufacture a minimum effective sample
size.

Block bootstrap or another block-resampling method MAY be evaluated for
uncertainty only after block construction is predeclared. The block unit,
minimum block length, resampling count and confidence interval method require
human scientific approval. No statistically validated effective sample size is
claimed by this ADR.

## 8. ML-01.2 Training Protocol

The canonical feature order remains:

1. `ohlc.normalized_atr`;
2. `ohlc.realized_volatility`;
3. `ohlc.volatility_compression_ratio`;
4. `ohlc.directional_efficiency`.

The following protocol is proposed:

- reject rows missing, non-finite or invalid in any required feature;
- fit `StandardScaler` on training rows only;
- apply the frozen training scaler to validation and test rows;
- fit candidate KMeans models only on training features;
- evaluate candidate `k` values `2, 3, 4, 5, 6`;
- predeclare deterministic seeds, initialization count, convergence settings,
  dependency versions and feature schema version;
- use training and validation diagnostics for model selection only;
- never use test features or outcomes to choose `k`, a seed, a scaler, a
  transformation or any model parameter;
- fingerprint the input rows, partition definition, scaler parameters, KMeans
  configuration, seed set and resulting artifacts;
- run the selected configuration once against the untouched test partition only
  after the model-selection record is frozen.

No winning `k`, seed, scaler setting or stability threshold is selected by this
ADR. The implementation must fail closed when the frozen protocol is incomplete.

## 9. Model Selection and Regime Stability

Silhouette score alone is insufficient because it can reward geometric
separation without demonstrating temporal persistence, robustness to seeds,
instrument consistency or meaningful forward behavior.

Training/validation diagnostics SHOULD include:

- separation and compactness metrics;
- cluster population counts and imbalance;
- centroid profiles in original feature units;
- repeated-seed assignment stability;
- stability across chronological blocks;
- temporal persistence and transition summaries;
- EUR/USD and GBP/USD cluster composition;
- sensitivity to 60/60, 59/60 and 55/60 source quality;
- sensitivity to dependency-aware thinning.

Candidate acceptance thresholds, balance rules and minimum stability criteria
must be proposed with rationale and approved before execution. They must not be
chosen after viewing held-out outcomes.

## 10. ML-01.3 Held-Out Evaluation

ML-01.3 MUST use the frozen model and untouched test partition. It may evaluate
H+4, H+12 and H+24 outcomes already computed by the deterministic Java boundary:

- future realized volatility;
- future directional efficiency;
- absolute future net return.

The evaluation SHOULD report, globally and by instrument:

- outcome distributions by cluster;
- sample counts and imbalance by cluster/horizon;
- descriptive location and dispersion statistics;
- uncertainty intervals using a predeclared dependence-aware method;
- temporal-block and quality-stratum sensitivity;
- multiple-comparison handling for cluster/horizon/instrument comparisons.

Descriptive cluster differences are not predictive usefulness, and predictive
usefulness is not profitability. No result may be described as a trading edge,
validated signal, calibrated probability or execution authorization.

Test outcomes MUST NOT be used to choose the model, `k`, seeds, scaling,
admissibility profile, partition, purge, embargo or acceptance threshold.

## 11. Partial-H1 Measurement Sensitivity

The experiment MUST compare the frozen 60/60, 59/60 and 55/60 populations where
joint availability exists. Since 60/60 currently has zero joint-available
anchors, the comparison must report this structural absence rather than invent
a substitute population.

For each population, report:

- feature distributions;
- cluster assignment sensitivity after ML-01.2 is separately authorized;
- cluster stability by H1 quality and missing-minute exposure;
- outcome distributions by quality exposure;
- temporal and calendar-month distribution of partial candles;
- EUR/USD and GBP/USD differences;
- overlap and effective-sample limitations.

Differences are descriptive evidence of measurement sensitivity. They are not
causal proof of measurement bias and must not be used to adjust the threshold
after inspecting held-out outcomes.

## 12. Instrument Strategy and Alternatives

### 12.1 Primary pooled model

The first implementation SHOULD use one pooled KMeans model, as required by
ADR-093, with instrument identity excluded from features and retained only for
diagnostics. This avoids silently changing the approved ML-01 objective while
testing whether the four-feature space supports a shared regime description.

### 12.2 Independent models

Independent EUR/USD and GBP/USD models may reveal instrument-specific geometry,
but they reduce comparability and increase model-selection multiplicity. They
are a sensitivity experiment requiring pre-registration, not the primary path.

### 12.3 Transfer evaluation

Training on one instrument and evaluating on the other tests transferability but
is not a substitute for the approved pooled experiment. It requires separate
sample-feasibility and correlation analysis.

### 12.4 Partition alternatives

| Alternative | Advantage | Risk / consequence |
|---|---|---|
| 70/15/15 fixed UTC | Larger training population; simple audit trail | Smaller validation/test populations and dependence concerns |
| 60/20/20 fixed UTC | More validation/test evidence | Less training data; may reduce stable cluster discovery |
| Rolling-origin | Tests temporal robustness | More complex, more comparisons and repeated model-selection choices |
| Row-count splits | Equalizes available rows | Breaks common time semantics and can select favorable periods |

The first protocol favors fixed common UTC boundaries, with rolling-origin and
row-count alternatives deferred to separate approved sensitivity work.

### 12.5 Purge and sampling alternatives

| Alternative | Advantage | Risk / consequence |
|---|---|---|
| Actual dependency purge | Directly proves leakage prevention | Requires complete per-row dependency provenance |
| Fixed 24-hour purge | Simple diagnostic baseline | Can under- or over-purge around irregular gaps |
| Fixed 33-hour purge | Conservative feature-window proxy | Confuses historical feature context with future leakage |
| Anchor thinning | Reduces overlap dependence | Discards scarce data and may bias temporal coverage |
| Block summaries/bootstrap | Represents dependence | Requires predeclared block and uncertainty methodology |

The protocol favors actual dependency purge and reports fixed-duration rules only
as sensitivity diagnostics.

## 13. Scientific Acceptance Gates

### Gate A - Dataset integrity

Required:

- source manifest identity and checksums validated;
- derived bundle fingerprints stable;
- deterministic row ordering and UTC bounds verified;
- no synthetic observations or cross-provider contamination;
- final post-fix real-data byte-for-byte repeat completed.

Current status: source and derived integrity are supported by evidence, but the
final post-fix real-data byte comparison remains open.

### Gate B - Partition protocol

Required:

- common UTC boundaries frozen;
- dependency-aware purge and embargo rules approved;
- leakage proof executed;
- test holdout and model-selection boundaries immutable.

Current status: unresolved. Existing 70/15/15 and 60/20/20 counts are diagnostic.

### Gate C - Sample feasibility

Required:

- temporal dependence and segment concentration assessed;
- thinning/block sensitivity assessed;
- minimum effective-sample requirements approved;
- instrument-specific feasibility accepted.

Current status: unresolved. Raw joint-available anchors are not independent
sample evidence.

### Gate D - Model execution authorization

ML-01.2 may begin only after Gates A, B and C are closed by explicit human
scientific approval and the implementation Story is approved. This ADR does not
authorize KMeans, scaling or model fitting.

### Gate E - Held-out evaluation authorization

ML-01.3 may begin only after the model-selection record is frozen, the test
partition remains untouched, and the held-out evaluation protocol is approved.
This ADR does not authorize held-out outcome evaluation execution.

## 14. Consequences

### Positive

- Leakage prevention is defined against actual dependency footprints.
- Technical availability cannot be confused with scientific eligibility.
- Common UTC boundaries preserve instrument comparability.
- Overlap and effective-sample uncertainty become explicit deliverables.
- ML-01.2 and ML-01.3 remain independently approval-gated.

### Negative

- Scarce joint-available observations may be reduced by valid purge and
  dependence diagnostics.
- Complete provenance and dependency checks increase bundle size and processing
  time.
- A pooled model may obscure instrument-specific behavior.
- No immediate KMeans result is produced by this decision.

## 15. Open Scientific Questions

1. Should 55/60 be accepted for exploratory modeling despite its measured
   partial-window exposure?
2. Should 70/15/15 or 60/20/20 be the frozen first partition?
3. Is a 24-hour candidate embargo sufficient once actual dependency intervals
   are inspected, or is another predeclared rule justified?
4. What dependence-aware uncertainty method and block definition are acceptable?
5. What minimum effective sample is scientifically sufficient per instrument,
   partition and cluster?
6. Should the first model remain pooled, or should an independent-model
   sensitivity be approved?
7. What non-outcome-based stability and balance thresholds should govern model
   selection?
8. Is the final post-fix real-data byte reproducibility comparison complete?

## 16. Follow-up Implementation Scope

After this ADR is approved, a dedicated Engineering Story MAY implement only:

- frozen partition configuration and manifest versioning;
- dependency-aware purge/embargo validation;
- temporal dependence, thinning and block diagnostics;
- train-only scaling and KMeans execution for predeclared `k=2..6` and seeds;
- model-selection and stability artifacts;
- untouched-test outcome evaluation under ML-01.3;
- reproducibility and leakage regression tests.

The follow-up Story must not modify production trading, broker execution, risk
controls, ADR-093 formulas, ADR-094 continuity semantics or ADR-096 admissibility
parameters without a separate decision.

## 17. Approval Status

This ADR is **ACCEPTED**. Human scientific approval was recorded on
2026-11-12 in the engineering workflow. The accepted decision authorizes
implementation of the protocol described in Section 16 after the required
follow-up Story is approved.

This ADR does not approve any specific diagnostic partition, embargo duration,
minimum effective sample size, KMeans result or held-out outcome. It does not
authorize commit, push or merge.
