# Story 0225 - Repository Analysis

## Purpose

Assess the current repository state before planning implementation of the
accepted historical continuity and ML-01 verified-closure policy.

## Repository Overview

Trading OS separates Market Data historical evidence and continuity primitives
from Market Intelligence feature, outcome and research-dataset preparation. The
current worktree already contains unrelated, pre-existing ML-01 and dataset
changes. They are not part of this analysis or an implementation change for
Story 0225.

## Current Implementation

### Market Data

- `HistoricalOhlcEvidence` is the provider-neutral persisted observation
  contract and already carries market identity, interval, timestamps, OHLC
  values, provider/source identity, capture timestamps, `knownAt`, origin,
  normalization version and an observation fingerprint.
- `HistoricalOhlcContinuityValidator` builds a regular expected-time grid and
  classifies absent observations through a caller-supplied function. Its
  classifications are `EXPECTED_MARKET_CLOSURE`, `MISSING_DATA`,
  `INVALID_CANDLE`, `DUPLICATE` and `UNCERTAIN`.
- `cTraderForexWeekendAvailability` is a shape-based weekend heuristic. It has
  no closure interval, source version, historical `knownAt`, applicability
  result or evidence fingerprint, so it cannot be promoted to verified closure
  evidence.
- `HistoricalOhlcContinuityReport` counts classifications and has a fail-closed
  `readyFor` predicate, but it does not distinguish base transition assessment
  from consumer acceptance and does not carry closure provenance.
- `HistoricalOhlcRangeCalculator` also creates an elapsed-time expected grid and
  marks absent intervals as ordinary gaps. It accepts only `CAPTURED` evidence
  and selects one candidate per open timestamp rather than representing
  conflicting candidates as a first-class continuity result.

### Market Intelligence

- `Ml01HistDataPreparation` aggregates the HistData M1 files into complete H1
  candles, constructs a contiguous historical tail, computes the four ML-01
  features and computes H+4/H+12/H+24 outcomes.
- The feature path uses `VolatilityCalculator`, which rejects any temporal gap
  between ordered candles and requires 33 valid observations.
- The ML-01 outcome path currently requires exact `previous.closeTime()` to
  `next.openTime()` adjacency and reports
  `UNEXPLAINED_GAP_OR_UNVERIFIED_CLOSURE` when that fails.
- ML-01 quality output currently records
  `verifiedClosureEvidence=false` and
  `unexplainedGapsFailClosed=true`, but it has no transition classifications,
  closure-evidence provenance or continuity-policy fingerprint.
- Existing ML-01 tests cover feature/outcome preparation, source conflict
  quarantine, incomplete aggregation and partition gating. They do not cover a
  verified closure crossing or consumer rejection of unknown evidence.

## Relevant Components

- `market-data/.../model/HistoricalOhlcEvidence.java`
- `market-data/.../model/HistoricalOhlcGapClassification.java`
- `market-data/.../model/HistoricalOhlcGapAssessment.java`
- `market-data/.../model/HistoricalOhlcContinuityReport.java`
- `market-data/.../service/HistoricalOhlcContinuityValidator.java`
- `market-data/.../service/HistoricalOhlcRangeCalculator.java`
- `market-data/src/test/.../HistoricalOhlcContinuityValidatorTest.java`
- `market-intelligence/.../application/research/histdata/Ml01HistDataPreparation.java`
- `market-intelligence/.../domain/volatility/VolatilityCalculator.java`
- `market-intelligence/.../domain/outcome/MarketOutcomeCalculator.java`
- `market-intelligence/src/test/.../Ml01HistDataPreparationTest.java`
- `market-intelligence/src/test/.../HistDataOfflinePipelineTest.java`

## Architecture Alignment

The current ownership boundaries align with ADR-094:

- Market Data is the correct owner for observations, closure evidence and base
  continuity assessment.
- Market Intelligence is the correct owner for ML-01 feature/outcome acceptance
  and derived artifact provenance.
- Live `MarketState` and provider trading status must not be reused as
  historical closure evidence.
- Python remains a validated-bundle consumer and must not become canonical
  historical truth.

No new service, generic policy engine, MCP permission or broker capability is
required by the current repository shape.

## Dependencies And Risks

- No approved historical closure evidence source is currently represented in the
  repository. The implementation can safely add the provider-neutral contract,
  deterministic synthetic behavior and fail-closed diagnostics, but a real-data
  closure-crossing validation remains blocked until source authority, scope and
  output limits are approved.
- Closure evidence must be bounded by the historical knowledge boundary. Evidence
  discovered after an evaluation cutoff cannot silently make a prospective row
  eligible.
- The existing validator hardcodes cTrader as the only valid provider. A Story
  implementation must avoid turning that provider-specific rule into a new
  universal closure assumption while preserving existing provider validation.
- The current ML-01 preparation constructs H1 candles directly from local CSV
  files. Introducing closure crossing requires a deterministic transition policy
  at the H1 consumer boundary; silently removing adjacency checks would violate
  ADR-094.
- Existing source conflicts, chronological inversions and incomplete intervals
  remain independent fail-closed blockers and must remain visible.
- Generated artifacts use fingerprints and `Instant.now()` in some manifests.
  Continuity evidence and policy inputs must be included in the relevant
  deterministic fingerprint material without rewriting unrelated artifact
  timestamp behavior.

## Recommended Implementation Shape

The smallest coherent implementation should:

1. Add provider-neutral closure-evidence and continuity-policy value objects in
   Market Data, reusing existing report and fingerprint structures where that
   remains compatible.
2. Make the base validator produce explicit transition semantics and preserve
   closure provenance, while retaining a fail-closed result for unknown or
   contradictory evidence.
3. Add a Market Intelligence consumer policy for ML-01 that counts valid H1
   observations across only `VERIFIED_CLOSURE` transitions and rejects all other
   non-contiguous transitions.
4. Preserve reopening gaps in existing ATR, realized-volatility and directional
   efficiency calculations by passing accepted candles without compressing
   timestamps or altering formulas.
5. Add synthetic tests for every ADR-094 classification and ML-01 lookback and
   horizon behavior. Keep real-data validation diagnostic-only until closure
   authority is approved.

This is a planning direction, not an approved implementation plan. The exact
public constructors, compatibility strategy and artifact schema changes require
the next workflow stage.

## Validation Surface

- Market Data unit tests for evidence scope, closure applicability,
  classification, provenance and fingerprints.
- Market Intelligence unit tests for 33-candle feature lookbacks and H+4/H+12/H+24
  valid-candle horizons across accepted closures.
- Regression tests proving contiguous-input feature and outcome values are
  unchanged.
- Existing `market-data` and `market-intelligence` test suites.
- `git diff --check`.
- No broker, Gateway, MCP, database mutation, model training or live-provider
  validation is required for the synthetic implementation stage.

## Open Questions

- Which versioned source is authoritative for closure evidence for the first real
  canonical market and venue?
- Should closure evidence be persisted immediately, or should this Story first
  support an injected/read-only evidence contract for bounded validation?
- Which exact existing fingerprint records should be versioned when continuity
  policy metadata is added to ML-01 artifacts?

## Recommendation

Repository Analysis is complete and supports proceeding to an Implementation
Plan after explicit human approval. Implementation must not begin before that
approval and the subsequent Implementation Plan approval.

## References

- `docs/architecture/stories/0225-historical-continuity-and-ml01-closure-crossing/story.md`
- `docs/architecture/adr/ADR-093-proposed-ml-01-chronological-experimental-protocol.md`
- `docs/architecture/adr/ADR-094-market-data-continuity-and-verified-closure-evidence-policy.md`
