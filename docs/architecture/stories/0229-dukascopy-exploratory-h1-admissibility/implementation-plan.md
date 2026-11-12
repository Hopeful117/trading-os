# Story 0229 - Implementation Plan

## Planning State

- Story: `0229`
- ADR: `ADR-096`
- Repository Analysis: approved by the human before this plan
- Implementation Plan: approved by the human in the current engineering session
- Implementation authorized: **Yes, within this plan**
- ML-01.2/KMeans authorized: **No**

This plan converts the approved Repository Analysis into an implementation
sequence. The human approved this plan in the current engineering session.
Implementation remains limited to this plan.

## Implementation Objective

Add a Dukascopy-specific, offline Market Intelligence preparation path for
`ML01_DUKASCOPY_EXPLORATORY_V1` that:

1. consumes the existing normalized UTC M1 reader output;
2. reconstructs deterministic H1 candles from observed M1 records;
3. preserves bucket-level integrity and strict continuity evidence;
4. classifies H1 quality using the accepted 55/60 candidate and boundary rules;
5. evaluates only exact `CONTIGUOUS_ONLY` sequences;
6. reuses unchanged Java feature formulas;
7. computes ADR-093-compatible feature, per-horizon and joint eligibility;
8. writes a reproducible, research-only bundle with explicit readiness blockers;
9. leaves HistData policy, production Market Data evidence and ML-01.2
   authorization unchanged.

## Design Decisions For Implementation

### Provider isolation

Do not reuse `Ml01HistDataPreparation` as the Dukascopy entry point. It embeds
HistData filenames, source timezone, normalization version, policy IDs and the
accepted HistData session convention. Create a Dukascopy-specific preparation
boundary in the existing `application.research.histdata` package only because
that package currently owns the offline historical preparation code; name all
new policy and output constants explicitly for Dukascopy.

Do not modify accepted HistData behavior except for a narrowly scoped pure
outcome extraction described below, and only with regression evidence proving
byte/semantic compatibility for existing HistData fixtures.

### Source evidence preservation

Extend `DukascopyOfflinePipeline` only as required to preserve evidence needed
by the H1 research boundary:

- timestamp-associated malformed/invalid/chronology events where the timestamp
  can be determined;
- original-order chronology anomalies;
- per-import quality-event metadata needed to associate source events with an
  H1 bucket;
- existing exact/conflicting duplicate counts and quarantined timestamps.

If a malformed row cannot be assigned deterministically to a timestamp or H1
bucket, retain it as a source-level integrity event and fail the affected
research readiness path closed. Never guess its bucket from line position or
repair the source.

### Dukascopy H1 evidence

Add immutable Dukascopy-specific value contracts rather than coupling the
existing HistData records to UTC Dukascopy semantics:

- `DukascopyH1Bucket` for UTC bucket bounds, OHLCV, strict bucket state and
  source identity;
- `DukascopyH1QualityEvidence` for unique count, observed/missing positions,
  first/last presence, maximum run and source quality events;
- `DukascopyH1ResearchQualityState` with
  `FULL_OBSERVED`, `PARTIAL_EXPLORATORY`, `REJECTED`, `UNKNOWN_QUALITY`;
- `Ml01DukascopyH1RejectionReason` for stable provider-scoped reasons;
- `Ml01DukascopyH1AdmissibilityDecision` for policy version, strict state,
  derived state, provenance and rejection metadata.

The contracts must be immutable records with constructor validation and stable
ordering for lists used in fingerprints. The existing HistData quality state,
reason enum and decision record remain unchanged.

### UTC H1 aggregation

Add an aggregation operation at the Dukascopy offline research boundary:

1. group assembled observations by instrument and UTC hour;
2. use half-open hour bounds `[hour, hour + 1h)`;
3. compute OHLC from actual observed M1 records only;
4. retain volume semantics without using volume as an ML-01 feature;
5. detect exact minute positions and missing positions across all 60 slots;
6. associate timestamped source quality events and duplicate quarantine with
   the bucket;
7. preserve empty buckets in the UTC grid so continuity breaks are explicit;
8. classify source/strict bucket state before applying research admissibility.

Cross-file overlaps for one instrument must be a research integrity blocker,
even though the current reader continues to report them rather than throwing.
The source fingerprint and overlap details must remain in the diagnostics.

### Admissibility evaluation

Add `Ml01DukascopyH1AdmissibilityProfile` with the exact ADR-096 parameters:

- `ML01_DUKASCOPY_EXPLORATORY_V1`;
- minimum 55 unique valid M1 records;
- expected 60 records;
- first and last minute required;
- conflicts, invalid data, malformed data and chronology anomalies rejected;
- no additional maximum-run rejection threshold.

Evaluate in fail-closed order:

1. contradictory or insufficient evidence -> `UNKNOWN_QUALITY`;
2. known integrity failure -> `REJECTED`;
3. count or boundary failure -> `REJECTED`;
4. exactly 60 valid records -> `FULL_OBSERVED`;
5. 55-59 valid records with all rules satisfied ->
   `PARTIAL_EXPLORATORY`.

Record all applicable rejection reasons in deterministic order. A partial
candle remains partial in every downstream row and manifest.

### Strict continuity

Add a Dukascopy-specific continuity evaluator for eligible H1 candidates:

- exact previous-close/next-open equality at one-hour cadence -> `CONTIGUOUS`;
- skipped, empty, rejected or unknown H1 buckets -> `UNEXPLAINED_GAP` or
  `UNKNOWN_QUALITY` according to the preserved source evidence;
- no candidate weekend shape or elapsed duration can become a session
  transition;
- no verified closure evidence is created by this Story;
- the original strict classification and actual timestamps remain in the output.

Build feature and future windows from eligible candidates only, but retain the
intervening rejected/empty bucket evidence in the transition provenance. A
window crossing any non-contiguous transition is unavailable.

### Feature calculation

Adapt the accepted Dukascopy H1 sequence into `OhlcPoint` and
`HistoricalOhlcContext`. Call the existing `VolatilityCalculator` without
changing ATR period, baseline, formulas, timestamp checks or
`FeatureProjectionV2` feature names.

For each evaluation candle:

- require 33 admissible and exactly contiguous H1 observations;
- calculate the four features through the existing Java calculator;
- record `AVAILABLE` versus unavailable status and the calculator reason;
- mark whether the feature lookback contains partial H1 candles;
- never write a canonical production `FeatureSnapshot`.

The research feature schema and fingerprint must include the Dukascopy source,
policy, continuity and H1 dependency fingerprints.

### Future outcomes

Use the existing ML-01 definitions for future realized volatility, future
directional efficiency and absolute future net return. To avoid a second
provider-specific formula implementation, extract the existing pure calculation
currently embedded in `Ml01HistDataPreparation` into a small deterministic ML-01
outcome component only if the plan's implementation review confirms that the
extraction preserves the existing `ml01-future-ohlc-v1` output.

The extraction must:

- preserve the current formula and definition version;
- preserve current HistData fixture results;
- accept ordered H1 OHLC values and a valid observation horizon;
- reject incomplete, non-contiguous or gapped future evidence;
- return explicit unavailable reasons rather than zeros.

Do not substitute `MarketOutcomeCalculator`'s current high/low/range contract
for the three ADR-093 ML-01 outcomes unless a separate approved decision changes
the outcome definition.

### Eligibility and bundle

Add a Dukascopy-specific preparation service/class that consumes the H1 quality
and continuity decisions and emits the same conceptual offline artifacts as the
existing research path, with provider-specific metadata:

- `observations.jsonl`;
- `dataset-manifest.json`;
- `quality-report.json`;
- `partition-manifest.json`;
- `readiness.json`;
- `source-metadata.json`.

The output must report separately:

- H1 quality states by instrument;
- strict 60/60, 59/60 and 55/60 diagnostics;
- feature-only availability;
- H+4, H+12 and H+24 availability;
- joint all-feature/all-outcome availability;
- rejection reasons and continuity transitions;
- partial-row and full-row counts;
- overlapping and non-overlapping dependency diagnostics.

The bundle must use a Dukascopy source profile and policy identity. It must not
claim `PIPELINE_VALIDATED` or scientific readiness when partition, purge,
embargo or minimum effective-sample parameters are absent. It must report
`REQUIRES_SCIENTIFIC_APPROVAL` or the existing equivalent readiness state and
set model-training/KMeans flags to false.

`HistoricalDatasetExportApplicationService` and MCP contracts remain outside
this Story unless implementation reveals that the offline bundle cannot be
validated without a minimal, approved contract extension. No Gateway or MCP
surface should be added.

## Implementation Sequence

### Phase 1 - Extend source evidence safely

Files expected to change:

- `market-intelligence/.../DukascopyOfflinePipeline.java`
- `market-intelligence/.../DukascopyOfflinePipelineTest.java`

Tasks:

- preserve timestamp-associated source quality and chronology evidence;
- keep exact duplicate behavior and conflict quarantine unchanged;
- keep source and assembly fingerprints deterministic;
- add tests for malformed/invalid/chronology evidence and cross-file blockers.

### Phase 2 - Add Dukascopy H1 and quality contracts

Files expected to add under the existing historical research package:

- `DukascopyH1Bucket.java`;
- `DukascopyH1QualityEvidence.java`;
- `Ml01DukascopyH1AdmissibilityProfile.java`;
- `DukascopyH1ResearchQualityState.java`;
- `Ml01DukascopyH1RejectionReason.java`;
- `Ml01DukascopyH1AdmissibilityDecision.java`;
- `Ml01DukascopyH1AdmissibilityEvaluator.java`;
- a focused `DukascopyH1...Test.java` suite.

Tasks:

- implement UTC hour aggregation from parsed M1 observations;
- preserve all bucket-level quality metadata;
- apply the 55/60 policy and retain comparison profiles for diagnostics;
- ensure unknown evidence is never eligible.

### Phase 3 - Add strict continuity and ML-01 preparation

Files expected to add or change:

- `Ml01DukascopyContinuityEvaluator.java`;
- `Ml01DukascopyPreparation.java`;
- a Dukascopy preparation integration test;
- the smallest pure ML-01 outcome extraction, if required by Phase 3.

Tasks:

- create `CONTIGUOUS_ONLY` sequences;
- reject unknown/missing transitions;
- calculate feature, per-horizon and joint eligibility;
- preserve quality and transition provenance;
- generate and validate deterministic research bundle artifacts;
- retain explicit scientific partition blockers.

### Phase 4 - Regression and quality validation

Tasks:

- run focused Dukascopy and ML-01 tests;
- run existing HistData/admissibility/session tests to prove no regression;
- run the affected `market-intelligence` Maven validation;
- run repository quality validation applicable to the modified Java module;
- run `git diff --check`;
- inspect the full diff for provider-policy leakage, production evidence writes
  and unauthorized files.

## Expected File Scope

Expected implementation scope is limited to:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata/`
  for Dukascopy offline research classes and any narrowly justified pure ML-01
  outcome extraction;
- corresponding `market-intelligence/src/test/java/.../histdata/` tests;
- no production Market Data service contract unless a material dependency is
  discovered and separately approved;
- no Gateway, MCP, broker, frontend, Python ML or database changes.

The plan does not authorize modifying untracked reports, ADR-096, Story 0229 or
other unrelated worktree artifacts during implementation.

## Validation Requirements

Required targeted tests:

- UTC bucket boundaries and month boundaries;
- 54, 55, 59 and 60 valid minutes;
- missing first/last minute;
- exact/conflicting duplicates;
- malformed, invalid and chronology cases;
- empty and partial H1 buckets;
- maximum missing-run recording;
- exact continuity and missing-H1 rejection;
- feature availability and partial-quality provenance;
- independent H+4/H+12/H+24 outcomes;
- joint eligibility rejection;
- quality/instrument stratification;
- deterministic fingerprints and repeated evaluation;
- HistData regression and no session-policy leakage.

Applicable broader validation:

```bash
(cd market-intelligence && ../trading-core/mvnw test)
(cd market-intelligence && ../trading-core/mvnw verify)
git diff --check
```

If the full module validation is blocked by infrastructure or unrelated
integration startup, the failure must be reported with the exact command and
reason; it must not be represented as passing.

No provider download, ML training, KMeans, MCP operation or broker operation is
part of validation.

## Risks And Mitigations

| Risk | Mitigation |
|---|---|
| HistData policy leaks into Dukascopy | Provider-specific profile, contracts and preparation entry point; regression metadata assertions |
| Bucket quality inferred from OHLC | Preserve and test M1 positions and timestamped source events before aggregation |
| Partial H1 bias hidden by larger sample | Separate full/partial strata and emit comparative diagnostics; no numerical threshold invented |
| Unknown gap crossed accidentally | Continuity evaluator fails closed and tests empty/rejected intervening buckets |
| Outcome definitions drift | Extract only with exact regression tests and preserve `ml01-future-ohlc-v1` |
| Diagnostic counts mistaken for eligible rows | Separate manifest fields and explicit readiness blockers |
| Production evidence contamination | No canonical FeatureSnapshot writes; research-only evidence policy assertions |
| Cross-file duplicate or overlap ambiguity | Research preparation rejects overlaps and preserves source diagnostics |
| Scientific parameters invented | Partition/purge/embargo/sample-size values remain explicit approval blockers |

## Non-Goals During Implementation

- no assumed Dukascopy session transition;
- no closure evidence creation;
- no accepted ADR modification;
- no HistData policy change;
- no provider merge;
- no generic framework;
- no ML model or KMeans execution;
- no operational readiness claim;
- no commit, push, merge or pull request creation.

## Approval Gate

This Implementation Plan is approved for implementation within this session.
Implementation may proceed only within this plan. A material change
to the selected contract boundary, outcome definition, source-policy semantics
or file scope requires renewed approval.
