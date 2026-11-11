# Story 0226 - Implementation Plan

## Executive Summary

Add a small, deterministic Market Intelligence evaluator for the accepted
`ML01_HISTDATA_EXPLORATORY_V1` profile. The evaluator will consume already parsed
HistData M1/H1 evidence, return an explicit research admissibility decision with
bucket-level provenance, and preserve the original strict Market Data state and
continuity classifications.

This plan does not implement session-transition construction, ML-01.1 readiness
or KMeans. It includes the bounded research-only dataset/export contract needed
to publish evaluator decisions and future outcomes. It does not add a parser,
provider, database, service, API or MCP capability.

## Objectives

- Represent the accepted V1 profile and immutable policy version.
- Expose bucket-level M1 quality evidence without reparsing source files.
- Evaluate H1 quality into `FULL_OBSERVED`, `PARTIAL_EXPLORATORY`, `REJECTED`
  or `UNKNOWN_QUALITY`.
- Return stable rejection reason codes and complete missingness metadata.
- Preserve strict H1 quality states and continuity classifications as input
  provenance, never as mutable research output.
- Keep exploratory decisions outside canonical `FeatureSnapshot` evidence.
- Prove deterministic behavior with focused Java tests.

## Architecture Impact

### Market Intelligence

The implementation remains in:

`market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata`

Planned domain/application types, subject to existing package conventions:

- `Ml01ExploratoryH1AdmissibilityProfile`: immutable accepted profile constants,
  including `VERSION`, minimum count `55`, expected count `60`, mandatory
  boundaries and forbidden repair modes;
- `Ml01H1ResearchQualityState`: the four ADR-095 research states;
- `Ml01H1RejectionReason`: stable reason codes for insufficient coverage,
  missing boundaries, conflicts, invalid OHLC, malformed observations,
  chronology anomalies and unknown quality/provenance;
- `H1BucketQualityEvidence`: the smallest immutable bucket-level evidence object
  derived from existing parsed `ImportResult` data, including valid count,
  missing positions, boundary flags, maximum missing run, quality indicators and
  original strict aggregation state;
- `Ml01H1AdmissibilityDecision`: immutable decision contract containing profile
  version, research state, reason codes, missingness metadata, source identity or
  fingerprint when available, original strict state and continuity provenance;
- `Ml01H1AdmissibilityEvaluator`: deterministic evaluator with no filesystem,
  clock, network, persistence or provider dependency.

Names may be adjusted to match repository conventions, but the contract and
responsibilities must remain unchanged.

### Existing HistData pipeline boundary

Extend the existing `HistDataOfflinePipeline` aggregation boundary only as
needed to derive `H1BucketQualityEvidence` from already parsed observations and
quality events. The preferred shape is a deterministic helper or value-object
projection that filters `ImportResult.observations()` by the existing H1 bucket
interval and combines the existing invalid/quarantine/event metadata.

The extension must not:

- read or parse the source file a second time;
- infer quality from OHLC values after M1 evidence has been discarded;
- alter the existing strict `AggregationState`;
- create synthetic M1 or H1 observations;
- change current callers' continuity behavior.

Adding fields directly to `AggregatedCandle` is permitted only if the existing
constructor and serialization compatibility impact is smaller than a separate
evidence projection. The plan must preserve existing test fixtures and callers
or update them mechanically without changing semantics.

### Market Data

No Market Data production contract change is planned initially. Existing
`HistoricalOhlcEvidence`, `HistoricalOhlcContinuityReport` and
`HistoricalOhlcGapAssessment` values remain authoritative inputs where they are
available at the consumer boundary. If implementation proves that a required
strict state or continuity value cannot cross the current boundary, stop and
report the smallest required interface extension rather than introducing a
parallel classification model.

## Decision Algorithm

For each H1 bucket, the evaluator performs the following deterministic steps:

1. Validate that the profile and bucket evidence are present and internally
   coherent. Missing required evidence produces `UNKNOWN_QUALITY`.
2. Preserve the original strict aggregation state and continuity classifications
   in the decision before applying exploratory rules.
3. Record unique valid M1 count, expected count, missing positions, first-minute
   presence, last-minute presence and maximum consecutive missing run.
4. Apply known integrity failures in deterministic precedence order:
   malformed evidence, conflicting duplicates, invalid OHLC, chronology anomaly,
   missing first minute, missing last minute, then insufficient valid coverage.
5. Return `REJECTED` with one or more stable reason codes when a known rule
   fails.
6. Return `FULL_OBSERVED` when exactly 60 valid unique minutes pass all quality
   rules.
7. Return `PARTIAL_EXPLORATORY` when 55-59 valid unique minutes pass all quality
   rules and both boundary minutes are present.
8. Return `UNKNOWN_QUALITY` when the evidence cannot deterministically establish
   a required rule; unknown decisions are never eligible.
9. Produce no repaired, interpolated, filled or synthetic observation.

The exact reason precedence must be covered by tests and must be stable for
identical input. Multiple applicable reasons may be retained in deterministic
order, while the primary reason must be deterministic.

## Provenance Contract

Every `Ml01H1AdmissibilityDecision` must retain, when available:

- profile name and version;
- source/provider identity and source file or source identifier;
- source fingerprint or normalization version;
- H1 open and close timestamps;
- original strict H1 aggregation state;
- original strict continuity classifications;
- expected and unique valid M1 counts;
- missing-minute positions;
- first-minute and last-minute presence;
- maximum consecutive missing-minute run;
- malformed, invalid and duplicate/conflict indicators;
- chronology anomaly indicator;
- research quality state;
- primary and complete rejection reason codes.

The decision is derived research metadata. It must not be serialized into or
passed as a trusted production `FeatureSnapshot` state.

## Implementation Steps

### 1. Add policy and result contracts

- Define the accepted profile version and immutable constants.
- Define the four research states and stable rejection reason codes.
- Define the immutable bucket evidence and decision records with constructor
  validation and defensive copies.
- Ensure `UNKNOWN_QUALITY` cannot be represented as eligible.

### 2. Expose bucket-level quality evidence

- Add the smallest projection at the existing HistData pipeline boundary.
- Derive missing positions and consecutive runs from parsed M1 timestamps,
  expected H1 minute boundaries and existing quality events.
- Associate invalid and conflicting timestamps with their H1 bucket.
- Preserve source chronology warnings and original `AggregationState`.
- Add no second parser and no synthetic data path.

### 3. Implement the evaluator

- Implement pure evaluation over the profile and bucket evidence.
- Keep decision ordering and reason codes deterministic.
- Preserve strict states/classifications in the returned decision.
- Keep evaluator output independent from feature calculation and persistence.

### 4. Integrate only at the research boundary

- Wire the evaluator into the existing HistData research preparation boundary
  only where required to expose exploratory admissibility.
- Do not make partial results eligible in the existing strict feature or
  `FeatureSnapshot` path.
- Do not add session-transition or future-horizon logic.
- Preserve existing strict preparation behavior and current Story 0225 closure
  evidence behavior.

### 5. Add tests and validate

- Add focused unit tests for the decision contract and algorithm.
- Add pipeline tests for bucket metadata projection.
- Add integration/regression tests proving strict output is unchanged and
  exploratory decisions remain isolated.
- Run the focused and affected-module validation listed below.

## Test Plan

### Evaluator unit tests

- 54 valid minutes -> `REJECTED` / insufficient coverage.
- 55 valid minutes -> `PARTIAL_EXPLORATORY` when boundaries are present.
- 59 valid minutes -> `PARTIAL_EXPLORATORY` when boundaries are present.
- 60 valid minutes -> `FULL_OBSERVED`.
- Missing first minute -> `REJECTED`.
- Missing last minute -> `REJECTED`.
- Conflicting duplicate -> `REJECTED`.
- Invalid OHLC -> `REJECTED`.
- Malformed source evidence -> `REJECTED`.
- Chronology anomaly -> `REJECTED`.
- Missing or insufficient provenance -> `UNKNOWN_QUALITY`.
- Missing positions and maximum consecutive run are exact and deterministic.
- Repeated evaluation produces identical decision and metadata.
- Policy version is present and changes are visible in provenance.
- No repair or synthetic observation is produced.

### Integration and regression tests

- Existing `HistDataOfflinePipeline` behavior remains unchanged for strict
  parsing and aggregation.
- Existing `Ml01HistDataPreparation` strict feature/outcome behavior remains
  unchanged.
- Original strict H1 state and continuity classifications remain unchanged after
  exploratory evaluation.
- `PARTIAL_EXPLORATORY` and `UNKNOWN_QUALITY` cannot enter canonical
  `FeatureSnapshot` evidence.
- Existing exact/conflicting duplicate, invalid-row and chronology tests remain
  passing.

## Validation Plan

Run from `market-intelligence`:

```bash
mvn -q -Dtest=HistDataOfflinePipelineTest,Ml01HistDataPreparationTest test
mvn -q test
mvn -q verify
```

Run Market Data tests only if its contract is extended. Always run:

```bash
git diff --check
```

No dataset generation, external download, MCP execution, KMeans execution,
model training, broker operation or production database mutation is permitted.

## Risks And Mitigations

- **Lossy bucket metadata:** derive metadata from parsed M1 evidence before it is
  discarded; never infer missingness from final OHLC.
- **Strict/exploratory leakage:** keep decision types separate and add regression
  tests around `FeatureSnapshot` production.
- **Classification drift:** preserve original strict state/classifications as
  immutable fields and do not call the exploratory state a Market Data state.
- **Story 0225 interference:** limit changes to the HistData admissibility
  boundary and preserve all existing closure-evidence code and user changes.
- **Non-deterministic diagnostics:** avoid clocks, unordered reason iteration and
  filesystem reads in the evaluator; sort positions and reason codes.

## Expected Deliverables

- Accepted V1 profile and evaluator/result contracts in Market Intelligence.
- Bucket-level quality evidence projection using existing parsed M1 data.
- Deterministic evaluator implementation and tests.
- Regression evidence that strict Market Data and canonical feature behavior is
  unchanged.
- Implementation Report documenting exact changes, validation and limitations.

The Story must not claim ML-01.1 dataset readiness. Session continuity,
combined-window construction, future horizons and scientific readiness remain
subsequent Stories.
