# Story 0226 - ML-01 Exploratory H1 Admissibility Evaluator

## Metadata

**ID:** `0226`
**Title:** ML-01 Exploratory H1 Admissibility Evaluator
**Status:** Implementation complete - human validation pending
**Priority:** P1 - Research Trustworthiness
**Related ADRs:** `ADR-092`, `ADR-093`, `ADR-094`, `ADR-095`
**Related Stories:** `Story 0225` - Historical Continuity and ML-01 Verified Closure Crossing

## Goal

Implement a deterministic, research-only Market Intelligence evaluator for the
`ML01_HISTDATA_EXPLORATORY_V1` profile. The evaluator determines whether an H1
observation qualifies for the exploratory ML-01 population while preserving the
strict Market Data evidence, quality state, continuity classification and source
provenance that produced it.

This Story is limited to H1 completeness and quality admissibility. It does not
construct session-transition windows, future horizons or an exploratory dataset.

## Context

ADR-095 accepts a separate exploratory population because strict September 2026
HistData continuity does not provide enough complete H1 observations for ML-01.
The exploratory profile permits 55-59 valid M1 observations only when both
boundary minutes are present and no disqualifying quality event exists. It does
not claim that partial candles are scientifically equivalent to fully observed
candles.

Market Data remains authoritative for production historical evidence. For the
offline HistData profile, Market Intelligence owns the local source parser and
aggregation contract; its `MI_LOCAL_HISTDATA_*` quality and continuity facts are
research-only derived facts and are never promoted to production Market Data
authority. Market Intelligence owns the derived research admissibility decision.
The evaluator must not become a second parser for the same source.

## Scope

- Add a named, versioned `ML01_HISTDATA_EXPLORATORY_V1` evaluator in Market
  Intelligence.
- Evaluate deterministic H1 quality using the existing Market Data and HistData
  quality/provenance contracts.
- Return exactly these research quality states:
  `FULL_OBSERVED`, `PARTIAL_EXPLORATORY`, `REJECTED` and `UNKNOWN_QUALITY`.
- Apply the 55/60 minimum, mandatory first and last M1 minutes, conflict and
  validity rejection, malformed-input rejection and chronology-anomaly
  rejection from ADR-095.
- Record unique valid M1 count, missing-minute positions, boundary presence,
  maximum consecutive missing-minute run and all applicable rejection reasons.
- Expose stable rejection reason codes for at least insufficient coverage,
  missing first minute, missing last minute, conflicting duplicate, invalid OHLC,
  malformed observation, chronology anomaly and unknown quality/provenance.
- Preserve the original strict H1 quality state and strict continuity
  classifications without mutation.
- Expose the smallest explicit contract required by the subsequent continuity
  evaluator.
- Construct the bounded research-only ML-01 H1 dataset and export bundle from
  the evaluator decisions, including outcomes, partitions and provenance.
- Define the dedicated research feature schema and export contract; canonical
  `FeatureSnapshot` evidence remains forbidden.
- Add deterministic unit tests and focused integration tests where the existing
  module boundaries require them.

## Out Of Scope

- Session-transition evaluation or weekend-session classification.
- `CONTIGUOUS_ONLY` and `ASSUMED_SESSION_TRANSITION` window construction.
- Combined feature-plus-outcome transition counting.
- H+4, H+12 or H+24 eligibility.
- Session-transition evaluation and continuity-stratum window construction.
- Changes to ADR-092, ADR-094 or Story 0225.
- Changes to feature formulas, outcome formulas or canonical `FeatureSnapshot`
  evidence.
- A generic quality-policy framework, session-calendar service or new parser.
- New services, databases, event brokers, providers, MCP permissions or
  production APIs.
- Java or Python ML execution, KMeans, model training or operational use.

## Architectural Placement And Dependencies

The evaluator belongs in the Market Intelligence HistData research preparation
boundary and must reuse existing contracts where possible, including:

- `HistDataOfflinePipeline.ImportResult` and
  `HistDataOfflinePipeline.AggregatedCandle` for the existing deterministic
  import and aggregation evidence;
- `HistDataOfflinePipeline` source timezone and source-quality semantics;
- Market Data `HistoricalOhlcEvidence`, `HistoricalOhlcContinuityReport` and
  `HistoricalOhlcGapAssessment` where the consumer boundary receives those
  contracts;
- existing Market Intelligence research preparation and provenance types.

The implementation must not duplicate authoritative Market Data classification
logic. If the current contracts do not expose sufficient M1-level evidence, the
implementation must document and implement only the smallest required interface
extension. It must not silently introduce a parallel parser or a second OHLC
aggregation source.

## Research Profile

```text
Profile: ML01_HISTDATA_EXPLORATORY_V1
Minimum valid unique M1 observations: 55/60
First M1 minute required: YES
Last M1 minute required: YES
Conflicting duplicates: REJECT
Invalid OHLC: REJECT
Malformed observations: REJECT
Unresolved chronology anomalies: REJECT
Missing-minute positions: RECORD
Maximum consecutive missing-minute run: RECORD
Additional missing-run rejection threshold: NONE
Interpolation, forward fill, backfill and synthetic candles: FORBIDDEN
```

The profile is offline and exploratory only. A `PARTIAL_EXPLORATORY` result is
not a trusted complete H1 observation and must not be exposed as canonical
production evidence.

## Acceptance Criteria

- [ ] **AC-01 - Fully observed candle:** Given 60 unique valid M1 observations
      with no quality conflicts, the evaluator returns `FULL_OBSERVED`.
- [ ] **AC-02 - Qualified partial candle:** Given 55-59 unique valid M1
      observations, with first and last minutes present and no disqualifying
      anomalies, the evaluator returns `PARTIAL_EXPLORATORY`.
- [ ] **AC-03 - Insufficient coverage:** Given fewer than 55 unique valid M1
      observations, the evaluator returns `REJECTED`.
- [ ] **AC-04 - Missing first minute:** Given otherwise sufficient coverage but
      no first M1 minute, the evaluator returns `REJECTED`.
- [ ] **AC-05 - Missing last minute:** Given otherwise sufficient coverage but
      no last M1 minute, the evaluator returns `REJECTED`.
- [ ] **AC-06 - Conflicting duplicates:** Given conflicting observations for the
      same M1 timestamp, the evaluator returns `REJECTED`.
- [ ] **AC-07 - Invalid OHLC:** Given an invalid OHLC observation affecting the
      H1 bucket, the evaluator returns `REJECTED`.
- [ ] **AC-08 - Unknown quality:** Given insufficient provenance to evaluate a
      required rule deterministically, the evaluator returns `UNKNOWN_QUALITY`
      and excludes the candle.
- [ ] **AC-09 - No synthetic repair:** The evaluator never interpolates,
      forward-fills, backfills or synthesizes observations.
- [ ] **AC-10 - Strict evidence preserved:** The evaluator does not modify strict
      Market Data quality states or continuity classifications.
- [ ] **AC-11 - Deterministic reproducibility:** The same source evidence and
      policy version produce the same decision, rejection reasons and quality
      metadata.
- [ ] **AC-12 - Research isolation:** Exploratory decisions are not exposed as
      trusted production H1 completeness or canonical `FeatureSnapshot`
      evidence.
- [ ] **AC-13 - Versioned policy:** Every decision identifies
      `ML01_HISTDATA_EXPLORATORY_V1` and its policy version.
- [ ] **AC-14 - Complete provenance:** Every decision records source identity,
      source fingerprint when available, strict state, counts, boundary flags,
      missing positions, maximum missing run and rejection reasons.
- [ ] **AC-15 - Fail closed:** Null, malformed, contradictory or otherwise
      insufficient evidence never becomes an exploratory eligible result.
- [ ] **AC-16 - Population recalculation:** Eligibility is calculated from the
      final evaluator decisions and is not copied from prior 55/60 diagnostics.

## Testing Expectations

Use the existing Java test framework and module conventions. Tests must include:

- parameterized coverage cases for 54, 55, 59 and 60 valid minutes;
- missing first-minute and missing last-minute cases;
- exact duplicate and conflicting duplicate cases;
- invalid OHLC cases;
- malformed input evidence cases;
- chronology anomaly cases;
- unknown or incomplete provenance cases;
- missing-minute position and maximum consecutive-run calculations;
- deterministic repeated-evaluation and policy-version tests;
- preservation tests proving strict Market Data state and continuity values are
  unchanged;
- research-isolation tests proving partial results cannot become canonical
  `FeatureSnapshot` evidence;
- focused integration tests at the existing Market Intelligence/Market Data
  contract boundary when required by the implementation.

## Session-Transition Boundary

The following responsibilities are explicitly deferred to a subsequent Story:

- HistData weekend-session transition evaluation;
- `CONTIGUOUS_ONLY` and `ASSUMED_SESSION_TRANSITION` window construction;
- combined feature-plus-outcome transition counting;
- independent H+4, H+12 and H+24 eligibility;

The Story now includes bounded exploratory dataset construction, export and
fingerprinting. Session-transition evaluation and continuity-stratum window
construction remain deferred to the subsequent continuity Story.

The next Story should consume this evaluator's immutable quality decision and
provenance rather than reimplementing H1 admissibility.

## Future Engineering Stories

The following Stories are identified but are not created by this task:

1. **ML-01 Exploratory Session Continuity**
   - Consume this evaluator and the bounded research dataset, then implement
     the ADR-095 session convention and combined-window transition counting.
2. **ML-01.1 Exploratory Scientific Readiness Validation**
   - Validate formulas, population counts, chronological partitions, purge,
     embargo, effective samples and quality-stratum stability without running
     KMeans.

## Validation

- Focused Market Intelligence evaluator and HistData preparation tests.
- Relevant Market Data contract tests when an interface extension is required.
- Deterministic repeated-evaluation and provenance/fingerprint tests.
- Regression tests for unchanged strict Market Data classifications.
- Existing affected-module tests where the evaluator integrates with current
  preparation code.
- `git diff --check`.
- No dataset generation, provider download, MCP execution, model training or
  live-provider mutation.

## Definition Of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Evaluator implemented in Market Intelligence.
- [ ] All acceptance criteria pass.
- [ ] Required provenance is preserved and policy-versioned.
- [ ] Strict Market Data classifications remain unchanged.
- [ ] Determinism is demonstrated by tests.
- [ ] Research-only boundaries are enforced.
- [ ] Relevant tests and documentation checks pass.
- [ ] Implementation Report documents exact changes and limitations.
- [ ] The report does not claim ML-01.1 dataset readiness from this Story alone.
- [ ] Code Review completed.
- [ ] Human commit created separately.

## Constraints

- Preserve ADR-092 Java-controlled research and MCP boundaries.
- Preserve ADR-093 feature, outcome, leakage and non-operational semantics.
- Preserve ADR-094 strict historical continuity and verified-closure authority.
- Preserve ADR-095's accepted exploratory quality profile.
- Preserve Market Data ownership of raw evidence and authoritative quality
  classifications.
- Keep exploratory decisions derived, explicit, deterministic and fail closed.
- Do not modify pre-existing user changes or runtime artifacts.
- Do not commit, push or merge automatically.
