# ADR-096 - Proposed Dukascopy Exploratory H1 Admissibility and Continuity Policy

| | |
|---|---|
| **Status** | **ACCEPTED - human scientific and architecture approval recorded** |
| **Date** | 2026-11-12 |
| **Decision Type** | Provider-scoped offline exploratory ML-01 historical admissibility |
| **Related ADRs** | ADR-092, ADR-093, ADR-094, ADR-095 |
| **Related Story** | Story 0228 - Dukascopy Historical Forex Acquisition And Dataset Assembly |

## 1. Context

ML-01 investigates unsupervised FX market-regime discovery from four
deterministic OHLC features:

- `ohlc.normalized_atr`;
- `ohlc.realized_volatility`;
- `ohlc.volatility_compression_ratio`;
- `ohlc.directional_efficiency`.

ADR-093 requires a 33-valid-H1 feature lookback, future observation horizons
H+4, H+12 and H+24, chronological evaluation, no future-data leakage and
human approval before ML-01.2. ADR-094 requires fail-closed historical
continuity and authoritative evidence before a gap is classified as a verified
closure. ADR-095 is an accepted, separate exploratory protocol for HistData;
its fixed UTC-05 session convention is not a Dukascopy contract and is not
reused by this ADR.

Story 0228 acquired and assembled a reproducible 12-month Dukascopy M1 BID
dataset while deliberately keeping provider acquisition separate from
scientific eligibility. The acquisition contract and current Java reader do
not establish a Dukascopy-specific H1 admissibility policy.

This ADR prepares such a provider-scoped research policy. It does not change
accepted ADRs, authorize implementation, authorize dataset publication as
scientifically valid, or authorize ML-01.2.

## 2. Problem Statement

The Dukascopy dataset is internally reproducible at the normalized M1 import
boundary, but strict full-H1 continuity does not provide enough joint
feature/outcome observations for ML-01. The research question is whether a
bounded exploratory H1 population may include partial H1 candles without
silently treating them as complete production market data or bridging unknown
historical gaps.

The policy must distinguish:

1. observed H1 candle quality;
2. continuity between H1 observations;
3. feature availability;
4. future-outcome availability;
5. verified closures and assumed session transitions;
6. diagnostic feasibility counts and actual ML-01 eligible rows.

## 3. Experimental Evidence

The evidence comes from the completed read-only ML-01.1 Dukascopy experiments:

- `docs/architecture/reports/ml-01.1-dukascopy-12-month-dataset-readiness-2026-11-12.md`;
- `docs/architecture/reports/ml-01.1-dukascopy-gap-continuity-sensitivity-2026-11-12.md`.

The source window is `[2025-10-01T00:00:00Z,
2026-10-01T00:00:00Z)`, with dataset fingerprint
`c212d83c0050374400af8e8c3d9961a2bbcf0f9169fd2c149c0513fc3002f198` and
manifest SHA-256
`4542e9e2ab7114edf8ca9514c146905fba8390a0753f26ba4cca47285b752041`.

| Metric | EUR/USD | GBP/USD |
|---|---:|---:|
| M1 observations | 371,614 | 371,266 |
| Fully observed H1 | 5,588 | 5,551 |
| Partially observed H1 | 638 | 675 |
| Strict 33-H1 feature anchors | 93 | 24 |
| Strict joint feature/outcome anchors | 0 | 0 |

The strict 33-H1 anchors have all four Java features available, but the strict
H+24 count is zero for both instruments. The strict joint count is therefore
zero.

The sensitivity experiment produced these diagnostic 24-H1 window counts
without assumed session transitions:

| H1 completeness | EUR/USD | GBP/USD |
|---|---:|---:|
| 60/60 | 0 | 0 |
| 59/60 | 154 | 14 |
| 55/60 | 2,145 | 1,630 |

These are not verified ML-01 feature/outcome eligible populations. They are
structural or Java-feature diagnostic counts. They do not include approved
chronological partitions, purge, embargo, minimum effective-sample decisions,
or a verified Dukascopy outcome bundle.

The sensitivity report also found:

- missing minutes are predominantly clustered;
- incomplete H1 buckets are concentrated on Saturday and Sunday but also occur
  on weekdays;
- candidate weekend-shaped gaps number 9 for EUR/USD and 5 for GBP/USD;
- those candidate gaps are heuristic patterns, not verified closure evidence;
- bounded assumed session transitions increase sequence and window counts;
- 59/60 and 55/60 one- and two-transition scenarios are not equivalent to
  strict continuity and are not interchangeable with the strict population;
- partial candles showed lower descriptive mean high-low ranges and absolute
  open-close movement than full candles in the inspected population.

The descriptive price differences establish a credible measurement-risk
hypothesis, not causal bias proof or a safe-imputation result.

## 4. Decision Drivers

The proposed policy is driven by:

- preservation of ADR-094 fail-closed continuity semantics;
- deterministic reconstruction from observed UTC M1 records;
- no interpolation, forward-fill, backfill or synthetic candles;
- explicit preservation of partial-candle quality;
- separation of provider acquisition from research admissibility;
- reproducibility through policy, source and row fingerprints;
- no use of future outcomes in admissibility selection;
- explicit scientific validation of partial-H1 effects before ML-01.2;
- no reuse of HistData's fixed UTC-05 session assumption;
- human approval before implementation or model execution.

## 5. Candidate Policies

| Candidate | Rule | Diagnostic 24-H1 windows | Scientific position |
|---|---|---:|---|
| Strict baseline | 60/60, exact continuity | 0 EUR/USD; 0 GBP/USD | Strongest measurement integrity; currently insufficient for the intended joint horizon |
| Conservative partial | 59/60, first and last minute required, exact continuity | 154 EUR/USD; 14 GBP/USD | Smaller measurement-risk exposure; still materially sparse and not validated |
| Primary exploratory candidate | 55/60, first and last minute required, exact continuity | 2,145 EUR/USD; 1,630 GBP/USD | Feasible for bounded exploratory validation, but not scientifically approved by this ADR |

The higher 55/60 population is not by itself a justification for selecting
55/60. It is proposed as the primary candidate because it supports a
meaningful sensitivity and validation experiment while retaining endpoint
requirements and strict unknown-gap rejection. Its scientific acceptability
remains unresolved until the validation requirements in Section 11 are met.

## 6. Proposed Decision

This ADR proposes the provider-scoped profile:

```text
Policy ID: ML01_DUKASCOPY_EXPLORATORY_V1
Status: APPROVED POLICY DIRECTION; IMPLEMENTATION PENDING
Provider: Dukascopy
Source resolution: M1
Research resolution: H1
Price side: BID
Minimum observed M1 per H1: 55/60
First minute required: YES
Last minute required: YES
Missing-minute interpolation: FORBIDDEN
Synthetic candles: FORBIDDEN
Conflicting duplicates: REJECTED
Invalid OHLC: REJECTED
Unknown continuity gaps: REJECTED
Assumed session transitions: NOT PERMITTED
Usage: OFFLINE EXPLORATORY ML-01 RESEARCH ONLY
```

The proposed profile does not approve 55/60 as scientifically safe. It defines
the candidate policy to be implemented and validated only after human approval.
Until approval, the profile is documentation only and no Dukascopy rows are
eligible under it.

The strict 60/60 population remains the integrity baseline. The 59/60
population remains a conservative comparison baseline. Any future experiment
must report all three populations using frozen parameters before inspecting
held-out outcomes.

## 7. H1 Admissibility Semantics

### 7.1 Deterministic UTC reconstruction

The source records are interpreted as UTC timestamps. Each record belongs to
the half-open UTC bucket:

```text
[floorToUtcHour(timestamp), floorToUtcHour(timestamp) + 1 hour)
```

The H1 candle is constructed only from valid observed M1 records in that
bucket:

```text
open  = open of the earliest observed minute
high  = maximum observed high
low   = minimum observed low
close = close of the latest observed minute
```

The reconstruction must not insert a record for an absent minute. The H1
timestamp and end timestamp remain the UTC bucket bounds, while quality
metadata records the actual observed minutes.

### 7.2 M1 validity and uniqueness

An M1 record is valid only when its timestamp is UTC minute-aligned, falls
inside the acquisition bounds, has positive OHLC values, non-negative volume,
and satisfies:

```text
high >= max(open, close)
low  <= min(open, close)
low  <= high
```

An exact duplicate timestamp with identical OHLCV values may be deterministically
collapsed to one unique observation. The duplicate count must be retained in
quality metadata. An exact duplicate is not an additional minute.

A conflicting duplicate timestamp is not resolved by choosing a winner. All
conflicting values affect the bucket's integrity decision and the bucket is
rejected. The conflict count and source provenance must be retained.

Malformed rows, invalid timestamps, invalid OHLCV and chronology anomalies
that affect a bucket are rejected. They must not be converted into missing
minutes or verified closures.

### 7.3 Boundary and missing-minute metadata

For every H1 bucket, the research representation must retain:

- unique valid M1 count;
- observed minute positions;
- missing-minute positions;
- missing-minute count;
- first-minute presence;
- last-minute presence;
- maximum consecutive missing-minute run;
- exact duplicate count;
- conflicting duplicate indicator and count;
- invalid or malformed-row indicators;
- chronology warnings;
- original strict H1 quality state;
- derived exploratory quality state;
- rejection reason when rejected.

The proposed 55/60 profile requires both boundary minutes. A bucket with 55
or more unique valid minutes but a missing first or last minute is not
`PARTIAL_EXPLORATORY` under this profile.

### 7.4 Consecutive missing-minute runs

The maximum consecutive missing-minute run must be observed, recorded and
reported. No additional numerical rejection threshold is proposed by this ADR.

The experiments demonstrate that maximum runs vary by quality class, but do not
provide a validated error tolerance mapping from a run length to feature error.
Selecting a run threshold now would invent a scientific acceptance criterion.
Future validation may retain, stratify or reject run lengths only after
predeclared full-vs-partial comparisons and feature-error analysis.

## 8. Historical Continuity Semantics

This policy preserves ADR-094. It does not alter strict Market Data
classifications or convert a recurring calendar pattern into a closure.

The following concepts remain distinct:

1. `CONTIGUOUS`: adjacent eligible H1 observations have the exact expected
   one-hour relationship.
2. `UNEXPLAINED_GAP`: one or more expected H1 intervals are absent or rejected
   without applicable verified closure evidence.
3. `VERIFIED_CLOSURE`: a bounded interval supported by versioned authoritative
   evidence under ADR-094.
4. `UNKNOWN_CLOSURE_EVIDENCE`: closure evidence is absent, stale, contradictory
   or unable to decide.
5. `ASSUMED_SESSION_TRANSITION`: a research heuristic, not a verified closure.

For `ML01_DUKASCOPY_EXPLORATORY_V1`:

- exact adjacency is required between every H1 observation in a continuity
  dependent window;
- unknown or unexplained gaps reject the affected window;
- a completely missing H1 interval cannot be crossed merely because adjacent
  candles look like a weekend reopening;
- no retrospective weekend normalization is performed;
- no assumed session transition is permitted;
- verified closure evidence retains its ADR-094 meaning and may only be used if
  a future, explicit consumer contract authorizes crossing it;
- no verified Dukascopy closure evidence is currently available in this
  experiment, so no closure-crossing population is established here;
- actual elapsed timestamps and reopening price jumps must be preserved;
- valid observation-count horizons must not be substituted with elapsed-time
  arithmetic or synthetic candles.

A 55/60 H1 bucket may be an observed partial H1 candle under this research
profile. That fact does not authorize crossing a completely missing H1 bucket.
Partial candle admissibility and inter-candle continuity are separate gates.

## 9. Quality Stratification

The following conceptual quality states are proposed as provider-scoped
research metadata. They reuse the semantic names established by ADR-095 but do
not alter the accepted HistData policy:

### `FULL_OBSERVED`

Exactly 60 unique valid M1 observations are present, no integrity rejection
affects the bucket, and all required provenance is available.

### `PARTIAL_EXPLORATORY`

The bucket contains 55-59 unique valid M1 observations, both boundary minutes
are present, no conflicting duplicate or invalid observation affects the
bucket, and all missingness metadata is available. The bucket is usable only
inside the explicitly approved Dukascopy exploratory research contract.

### `REJECTED`

A known integrity or admissibility rule fails, including fewer than 55 unique
valid minutes, a missing boundary minute, conflicting duplicates, invalid OHLC,
malformed data or a known chronology violation.

### `UNKNOWN_QUALITY`

The evidence is insufficient to make a deterministic quality decision. This is
fail-closed and cannot enter the exploratory population.

These are not production Market Data states. The provider-specific research
metadata must carry the policy ID and version, and must remain distinguishable
from strict H1 evidence. A partial exploratory candle must never be silently
serialized or exposed as a fully observed production candle.

Quality metadata must survive H1 reconstruction, feature computation,
outcome construction, partitioning and research export. Results must be
reported separately by quality stratum and instrument.

## 10. Feature And Outcome Eligibility

For each candidate evaluation H1 candle, eligibility is evaluated independently
for each requested future horizon.

### Feature eligibility

A feature window requires 33 valid H1 observations, including the evaluation
candle according to the existing deterministic Java semantics. Every H1
dependency must:

- satisfy the selected Dukascopy admissibility profile;
- have a known quality state other than `UNKNOWN_QUALITY` or `REJECTED`;
- satisfy all continuity transitions in the window;
- avoid unknown or unexplained gaps;
- contain no synthetic or interpolated observation;
- use the unchanged Java feature formulas;
- produce actual availability for all four required features.

The existing feature names, formulas, lookbacks and `FeatureProjectionV2`
version remain unchanged. Python or an external research tool must not
recompute canonical feature values from raw Dukascopy data or substitute a
different projection.

### Outcome eligibility

For H+4, H+12 and H+24, all required future H1 observations must independently
satisfy the selected admissibility and continuity rules. Future outcomes must
be computed from actual future OHLC observations for:

- future realized volatility;
- future directional efficiency;
- absolute future net return.

An unavailable or invalid future outcome excludes that horizon. A feature row
is not an ML-01 eligible row merely because its features are structurally
available.

### Joint eligibility

An ML-01 joint row requires:

1. all four features available;
2. the selected 33-observation feature lookback valid;
3. the selected H+4, H+12 and H+24 outcomes available;
4. all dependency observations admissible;
5. all required continuity checks passed;
6. no unknown gap crossed;
7. no synthetic observation;
8. chronological partition, purge and embargo rules passed;
9. the row fingerprint and research configuration fingerprint valid.

The sensitivity experiment's 24-H1 counts do not establish this joint
population. They must not be copied into a dataset manifest, readiness result
or ML-01.2 approval.

## 11. Scientific Bias Risks And Required Validation

Partial H1 acceptance creates a credible measurement-risk hypothesis:

- missing interior minutes may hide the true high or low;
- observed range may be understated;
- nonrandom missingness may concentrate around provider session boundaries;
- missing endpoints may alter open, close and close-to-close transitions;
- ATR and normalized ATR may be distorted;
- realized volatility may be distorted by incomplete close paths;
- compression ratios may shift because current or baseline ATR is measured from
  partial candles;
- directional efficiency may change when path movement is incompletely
  observed;
- future outcome availability and missingness may be correlated with candle
  quality;
- clustering may identify provider coverage artifacts instead of market regimes;
- regime distributions and forward associations may differ by quality stratum.

Before ML-01.2, an implementation or scientific validation Story must compare,
with frozen policy parameters and without using held-out outcomes for policy
selection:

- strict 60/60 versus 59/60 versus 55/60 populations;
- full-only versus full-plus-partial populations;
- `FULL_OBSERVED` versus `PARTIAL_EXPLORATORY` rows;
- boundary-complete partial rows by missing-minute count and maximum run;
- EUR/USD versus GBP/USD;
- contiguous rows versus any separately approved continuity strata;
- feature distributions and feature availability;
- future outcome distributions and availability;
- chronological partition support after purging and embargo;
- overlap, unique H1 units, sessions or trading days, and non-overlapping row
  counts.

The validation must report effect sizes, uncertainty or an explicit unavailable
reason. It must not invent numerical acceptance thresholds. Retaining,
restricting or rejecting partial H1 observations requires explicit scientific
approval based on predeclared evidence, not on the largest resulting sample.

## 12. Session-Transition Decision

The proposed initial Dukascopy policy permits **no assumed session
transitions**.

The sensitivity experiment tested bounded weekend-shaped heuristics. They
increased diagnostic sequence lengths and feature-window counts, but no
authoritative Dukascopy closure evidence supported them. The candidate shape
must therefore remain an `UNEXPLAINED_GAP` or unknown transition under strict
provenance and reject continuity-dependent windows in this first policy.

Deferred alternatives are:

- provider-authoritative historical closure evidence compatible with ADR-094;
- a separately versioned Dukascopy assumed-session exploratory stratum;
- a future independent session-continuity policy with its own scientific
  validation and approval.

The HistData fixed UTC-05 weekend convention in ADR-095 must not be reused for
Dukascopy. Conservatism reduces the available sample, but prevents a calendar
heuristic from being mistaken for historical evidence.

## 13. Research Reproducibility Contract

Every Dukascopy ML-01 research bundle must record:

- provider and provider instrument identity;
- canonical instrument and market identity;
- price side;
- source dataset fingerprint;
- source manifest checksum and per-file checksums;
- source timeframe and research timeframe;
- UTC timestamp semantics and bounds;
- H1 admissibility policy ID and version;
- continuity policy ID and version;
- H1 quality stratum;
- unique valid M1 count;
- missing-minute count and positions;
- maximum consecutive missing-minute run;
- first-minute and last-minute status;
- duplicate, conflict, invalid and chronology indicators;
- original strict quality and continuity classifications;
- feature projection schema and version;
- feature availability for each required feature;
- outcome definition and availability for H+4, H+12 and H+24;
- rejection reasons and continuity transition details;
- actual elapsed lookback and outcome durations;
- partition, purge and embargo configuration;
- research configuration fingerprint;
- generated-at timestamp and runtime/dependency versions.

Changing any policy, continuity rule, source fingerprint, feature version,
outcome definition or partition configuration produces a new immutable research
bundle. Prior Dukascopy and HistData reports must not be overwritten or
reinterpreted as if they used the new policy.

## 14. Alternatives Considered

### Strict 60/60 only

This is the strongest measurement-integrity baseline and remains mandatory as
a comparison population. It currently produces no diagnostic 24-H1 window and
no strict joint feature/outcome rows for this dataset.

### Conservative 59/60

This reduces the number of missing minutes tolerated and produced 154 EUR/USD
and 14 GBP/USD diagnostic 24-H1 windows without session assumptions. It remains
a required sensitivity baseline, but the resulting population is still sparse
and its scientific adequacy is not established.

### 55/60 without boundary requirements

Rejected as a candidate because missing first or last minutes can alter H1 open
or close and affect close-dependent features and outcomes.

### 55/60 with interpolation or synthetic candles

Rejected. It would create unobserved prices and compromise provenance,
continuity and deterministic scientific interpretation.

### 55/60 with assumed weekend transitions

Deferred. The sensitivity experiment shows sample-size benefits, but the
heuristic is not authoritative Dukascopy closure evidence and would introduce
an additional continuity assumption before the first provider-specific policy
is validated.

### Reusing ADR-095's HistData session convention

Rejected. ADR-095's UTC-05 fixed-offset convention is provider- and
source-specific to HistData. Dukascopy records are already UTC and Story 0228
explicitly excludes importing the HistData session policy.

## 15. Consequences

### Positive

- Dukascopy acquisition remains independent from scientific admissibility.
- ADR-094's fail-closed continuity and verified-closure meanings remain intact.
- Partial H1 observations can be studied without being promoted to production
  Market Data evidence.
- Strict, conservative and exploratory populations can be compared
  reproducibly.
- Unknown gaps and session assumptions remain visible rather than silently
  normalized.
- Feature and outcome eligibility remain separate from structural window counts.
- The policy is limited to offline exploratory ML-01 research.

### Costs and risks

- The 55/60 population may be biased and may distort all four features.
- Strict no-session-crossing continuity leaves sample scarcity unresolved.
- Additional provenance, stratification and validation are required.
- Large row counts may overstate information because rolling windows overlap.
- The policy cannot be used until the threshold and safeguards receive human
  scientific approval.
- A future implementation requires a Dukascopy-specific adapter or equivalent
  research capability; the current ML-01 preparer is HistData-specific.

## 16. Explicit Non-Goals

This ADR does not:

- modify ADR-092, ADR-093, ADR-094 or ADR-095;
- change accepted HistData policies;
- change Market Data production quality or continuity policies;
- implement a Dukascopy H1 aggregator or research exporter;
- approve production `FeatureSnapshot` values;
- authorize ML model training or KMeans;
- authorize ML-01.2 or ML-01.3;
- authorize live trading, risk decisions, execution or automation;
- infer or publish Dukascopy verified closure evidence;
- merge Dukascopy with HistData or another provider;
- create a generic quality-policy framework or session-calendar service;
- create an Engineering Story automatically.

## 17. Open Questions

The following remain unresolved and require human approval or further evidence:

1. Is 55/60 scientifically acceptable, or should 59/60 remain the maximum
   exploratory tolerance?
2. Are first and last-minute requirements sufficient protection for endpoint
   semantics?
3. Can a defensible maximum missing-minute-run rule be established without
   inventing an error tolerance?
4. Should partial H1 rows be retained for features, outcomes, both, or only a
   separately reported exploratory stratum?
5. What evidence is required to compare partial-derived feature values with a
   full-observation reference population?
6. What minimum effective samples, non-overlapping counts and per-instrument
   support are required before ML-01.2?
7. What partition, purge and embargo parameters are scientifically approved?
8. Must the initial Dukascopy policy remain `CONTIGUOUS_ONLY` permanently for
   ML-01, or may a separately versioned session stratum be considered later?
9. What authoritative historical closure source, if any, can support Dukascopy
   closure evidence under ADR-094?
10. What criteria are required before partial-H1 or mixed-quality rows may be
    included in a shared KMeans population?

## 18. Approval Record

Human approval has been explicitly recorded for the policy direction and
parameters documented by this ADR:

- the proposed 55/60 completeness candidate, subject to the validation gates;
- first-minute and last-minute requirements;
- recording, but not thresholding, the maximum missing-minute run;
- the four quality strata and their provider-specific research metadata;
- strict continuity without assumed session transitions;
- partial-H1 scientific bias safeguards;
- the evidence required before ML-01.2;
- the distinction between diagnostic 24-H1 counts and joint eligible rows.

Approval of this ADR would authorize only the documented research-policy
direction. It would not authorize implementation, ML-01.2, KMeans, production
exports or operational use.

## 19. Follow-up Engineering Story Scope

If this ADR is approved, a separate human-created Engineering Story should be
scoped to:

1. Implement the Dukascopy-specific research H1 reconstruction and admissibility
   evaluator without modifying production Market Data quality semantics.
2. Preserve exact duplicate collapse, conflict rejection, invalid-row rejection,
   boundary state, missing positions, maximum missing run and quality metadata.
3. Implement `CONTIGUOUS_ONLY` continuity with no assumed session transitions.
4. Reuse the unchanged Java feature and outcome definitions where their
   contracts are compatible.
5. Recalculate actual feature, outcome and joint eligibility counts rather than
   copying diagnostic sensitivity counts.
6. Produce immutable row, policy, source and configuration fingerprints.
7. Validate full-only versus partial-inclusive populations and all quality and
   instrument strata before any ML-01.2 request.
8. Keep outputs offline, research-only and outside canonical production
   `FeatureSnapshot` and live trading paths.

The follow-up Story must separately define partition, purge, embargo,
effective-sample and scientific acceptance criteria. It must not authorize
KMeans merely because the evaluator completes.

## 20. Approval Status

**ACCEPTED. Human scientific and architecture approval recorded.**

Acceptance authorizes the documented research-policy direction only. It does
not authorize implementation, model training, KMeans execution, commit, push or
pull request. A separate approved Engineering Story remains required before
implementation, and ML-01.2 remains approval-gated by the validation requirements
in this ADR and ADR-093.
