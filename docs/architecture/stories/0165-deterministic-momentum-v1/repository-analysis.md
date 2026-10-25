# Repository Analysis

## Story Understanding

Story 0165 requests one bounded deterministic Momentum V1 capability for
Market Thesis.

The requested behavior is to derive provider-neutral move-quality evidence from
closed OHLC data, including directional persistence, normalized slope, and
acceleration/deceleration or explicit unavailable states. The result must be
versioned, cutoff-safe, fresh, provenance-bearing, deterministic, and composed
into Market Thesis as supporting, contradicting, or missing evidence.

The engineering objective is to answer whether a structurally valid move is
continuing or weakening. This should improve the explanation and gating of
`WATCH`, `CANDIDATE`, and `NO_TRADE` without creating a prediction, confidence
score, Risk decision, TradePlan, or execution command.

Included scope:

- one deterministic Momentum capability in `market-intelligence`;
- normalized closed OHLC as the V1 data dependency;
- cutoff, freshness, provenance, capability/rule/policy versions, and
  fingerprints;
- explicit supporting, contradicting, and missing Momentum evidence;
- structured confirmation and deterioration conditions in Market Thesis;
- deterministic Thesis state integration;
- focused tests and affected-module validation.

Explicit exclusions:

- Volume, tick-volume, broker-volume, exchange-volume, and order flow;
- a new Regime or Range engine;
- changes to Trend Context or Market Structure semantics;
- News, Session, Spread, Liquidity, Order Book, or cTrader work;
- `FeatureProjectionV1`, S1, AI, frontend, Risk, execution, TradePlan,
  StrategyMatch, and TradingOpportunity changes;
- a universal score, probability, confidence, or automatic trade decision.

## Repository Summary

Trading OS is a multi-service platform in which `market-intelligence` owns
deterministic analysis orchestration, observations, Market Thesis, opportunity
context, and trade-plan preparation. `market-data` owns normalized market facts
and freshness. Risk and execution remain separate authorities.

The current Market Thesis path is:

```text
market-data OHLC / Trend Context
    -> deterministic capabilities
    -> Observation
    -> MarketThesisComposer
    -> persisted Market Thesis
```

The repository is currently on clean `main` at:

```text
63c1cf6c9f9a39de3d9c9fa69488802ad295545c
```

`HEAD` and `origin/main` are aligned. Story 0164 is merged and its structural
level/objective behavior is present in the current source.

## Affected Modules

### `market-intelligence`

This is the owning module. It contains:

- deterministic capability contracts and execution;
- normalized historical OHLC context;
- Trend Context and Market Structure analysis;
- Observation consolidation;
- Market Thesis composition, persistence, and web projection;
- the relevant unit, integration, architecture, and persistence tests.

### `market-data`

This module is involved only through the existing provider-neutral OHLC and
freshness contracts consumed by Market Intelligence. Provider-specific market
payloads must remain outside the Momentum domain contract.

No other service is required by the current Story boundary.

## Existing Implementation

### OHLC input and temporal validation

`HistoricalOhlcContext` contains a market ID, interval, and immutable list of
`OhlcPoint` values. `OhlcPoint` already carries open/close timestamps, OHLC
prices, and an optional volume field.

`OhlcTemporalCalculator` validates positive OHLC values, candle geometry,
ordering, and optional evaluation cutoff. It already exposes the repository's
pattern for deterministic temporal calculation, insufficient data, and invalid
input handling. It currently calculates first-to-last price change, simple
return, range, and observed time bounds. It does not implement Momentum.

`HistoricalOhlcContextContributor` and its evidence-source collaborators load
provider-neutral historical OHLC and preserve freshness/provenance metadata.

### Trend Context and Market Structure

`TrendContextAnalysisCapability` consumes role-scoped history and existing
Market Structure artifacts. It produces a versioned
`TrendContextCapabilityContent` containing `TrendContextAssessment`.

The assessment already contains:

- direction;
- regime and phase;
- BIAS/SETUP/TRIGGER timeframe assessments;
- alignment;
- findings, contradictions, exclusions, and invalidations;
- EMA and ATR evidence;
- input cutoff and fingerprints.

`TrendContextEngine` is the authority for these semantics. Story 0165 must not
change them or create a second structure/trend engine.

### Existing Observation flow

`ObservationBuilder` consolidates complete capability results into durable,
versioned observations. `OhlcTrendObservationRule` currently uses the
first-to-last OHLC price change and `TrendContextObservationRule` preserves the
Trend Context assessment payload.

The production pipeline builds the observation and then calls
`MarketThesisComposer`. The current production observation rule does not yet
compose Momentum evidence.

### Current Market Thesis behavior

`MarketThesisComposer` currently:

- extracts the Trend Context assessment from the observation payload;
- copies observation evidence;
- maps Trend findings to supporting evidence;
- maps Trend contradictions and fresh invalidations to contradicting evidence;
- maps exclusions, stale roles, and unavailable inputs to missing evidence;
- invokes `StructuralLevelObjectiveComposer` for Story 0164 levels,
  invalidation, objective, and obstacles;
- records Momentum, Volume, and advanced Liquidity as
  `NOT_IMPLEMENTED` missing evidence;
- creates a generic `TREND_DIRECTIONAL` confirmation condition;
- derives state primarily from scenario direction, blocking contradictions, and
  required evidence availability.

The current state policy can therefore produce `CANDIDATE` for fresh
directional Trend evidence without independent Momentum evidence. This is the
behavior Story 0165 is intended to address.

`MarketThesis`, `MarketThesisEvidence`, `MarketThesisCondition`, and
`MarketThesisScenario` already support the required structural concepts:

- `SUPPORTING`, `CONTRADICTING`, and `MISSING` evidence roles;
- explicit contradiction severity;
- `AVAILABLE`, `UNAVAILABLE`, `STALE`, `UNKNOWN`, `NOT_APPLICABLE`, and
  `NOT_IMPLEMENTED` availability;
- structured confirmation and invalidation conditions;
- immutable identity, lineage, source cutoff, validity, and fingerprints.

### Persistence and API

`JpaMarketThesisRepository` serializes the scenario, evidence, and reasons into
the existing Thesis payload while persisting identity, state, cutoff, validity,
and composer metadata in the Thesis entity. The current persistence model is
generic enough to carry additional typed evidence fields without an identified
new database authority, but JSON compatibility and round-trip behavior must be
preserved.

`MarketThesisResponse` and the read-only query boundary already expose the
Thesis state, scenario, evidence, reasons, and provenance to consumers.

### Existing tests

Relevant coverage includes:

- `OhlcTemporalCalculatorTest`;
- `OhlcRangeAnalysisCapabilityTest`;
- `HistoricalOhlcContextContributorTest`;
- `HistoricalOhlcEvidenceSourceTest`;
- `OhlcEvidencePolicySelectionTest`;
- `TrendContextEngineTest`;
- `TrendContextCanonicalScenarioTest`;
- `TrendContextAnalysisCapabilityTest`;
- `TrendContextObservationIntegrationTest`;
- `MarketThesisComposerTest`;
- `MarketThesisDomainTest`;
- `MarketThesisJpaPersistenceTest`;
- `MarketThesisLevelSerializationTest`.

Existing tests cover temporal OHLC validity, Trend Context semantics, current
Thesis state behavior, missing Momentum representation, deterministic replay,
and Thesis persistence. They do not cover Momentum calculation or Momentum-led
Thesis state transitions.

Existing behavior that must remain unchanged:

- Trend Context direction, structure, phase, alignment, EMA, ATR, and
  invalidation semantics;
- structural levels, objectives, and obstacles from Story 0164;
- generic Market Thesis persistence and immutable lineage;
- Risk, TradePlan, execution, broker, StrategyMatch, Opportunity,
  FeatureProjectionV1, and S1 boundaries.

## Relevant Documentation

The analysis used:

- repository `AGENTS.md`;
- repository `README.md`;
- `docs/architecture/stories/0165-deterministic-momentum-v1/story.md`;
- `docs/architecture/stories/0163-market-thesis-composition/story.md`;
- `docs/architecture/stories/0164-structural-levels-and-objectives/story.md`;
- `docs/architecture/stories/0063-deterministic-trend-context-engine/story.md`;
- `docs/architecture/stories/0063-deterministic-trend-context-engine/implementation-plan.md`;
- `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md`;
- `docs/investigations/deterministic-trend-context-domain-design-2026-09-23.md`;
- `docs/investigations/conservative-trend-swing-trader-v1-2026-09-23.md`;
- the Engineering Story Repository Analysis workflow instructions.

DevLog was queried for Story 0165 context, but the service returned an
`LLM_PROVIDER_ERROR`; no DevLog evidence was used. The local curated Obsidian
vault was not consulted because the repository Story, ADR, and current source
provide the required project-specific context.

## Constraints

- Market Intelligence owns Momentum calculation and Market Thesis composition.
- Market Data remains authoritative for normalized OHLC facts and freshness.
- Trend Context remains authoritative for direction, phase, alignment, EMA, ATR,
  and its existing contradictions/invalidation.
- Market Structure remains authoritative for swings, relations, breaks,
  protected levels, and structural validity.
- Momentum must be independent of Trend direction and must not create direction
  or structure.
- Volume must not be used implicitly through the existing `OhlcPoint.volume`
  field because its semantic ownership is not established for this Story.
- All calculations must be deterministic, cutoff-safe, versioned, and
  provenance-bearing.
- Missing or stale Momentum cannot silently become supporting evidence.
- Contradictions remain typed and must not become a universal score.
- Market Thesis remains market-scoped, strategy-neutral, and non-authorizing.
- No cTrader or reference-market dependency is required for the current boundary.
- `FeatureProjectionV1` and S1 are frozen.
- No Risk, execution, broker, TradePlan, StrategyMatch, Opportunity, frontend,
  News, Session, Spread, Liquidity, or Order Book behavior may change.
- Existing persisted Thesis JSON and historical Thesis meaning must remain
  readable and immutable.

## Risks

### Unspecified Momentum policy parameters

The repository does not currently define the Momentum lookback, normalization
baseline, thresholds, or exact classification vocabulary. These are Story-scoped
policy decisions and must be made explicit in the Implementation Plan rather
than inferred from Trend Context or generic trading conventions.

### Candidate-state regression

Changing `CANDIDATE` eligibility can alter downstream human investigation and
pipeline observations. The state transition must remain deterministic, explain
its reasons, and preserve the distinction between analytical candidacy and Risk
authorization.

### False independence

Momentum could accidentally restate Trend direction or EMA slope. The domain
contract must preserve independent temporal price behavior and must not derive
Momentum solely from existing Trend labels.

### Cutoff and look-ahead errors

Momentum uses a sequence of candles, so future candles or incomplete candles
could contaminate historical Thesis results. Existing cutoff and temporal
validation patterns must be preserved.

### Persistence compatibility

Market Thesis payloads are persisted as JSON and reconstructed through the
existing repository. New evidence or conditions must round-trip without
breaking historical records or deterministic fingerprints.

### Over-scoping the capability

Acceleration/deceleration can expand into Volume, Regime, divergence, or
indicator semantics. Those concepts are explicitly outside this Story and
must not enter the implementation through shared convenience code.

### Data semantic leakage

The optional `volume` field on `OhlcPoint` and future cTrader data could be
mistaken for a universal participation measure. Story 0165 requires OHLC-only
provider-neutral semantics and must not expose provider-specific meaning.

## Open Questions

None blocking Repository Analysis. The exact Momentum formula, parameter
values, classification thresholds, and required-versus-optional promotion
policy are not existing repository facts; they are implementation-policy
decisions that must be made explicit and approved in the Implementation Plan.

## Recommendation

Ready for planning

The repository is sufficiently understood, ownership is clear, the existing
OHLC contract is available, Market Thesis already has the required evidence and
condition model, and no blocking ADR conflict or cTrader dependency exists.

This is a technical recommendation only. It does not approve the Repository
Analysis or authorize Implementation Planning.

## Implementation Readiness

The current repository provides the technical prerequisites for planning:

- provider-neutral OHLC points and historical context;
- deterministic temporal validation and cutoff handling;
- capability/artifact metadata and provenance patterns;
- Market Thesis supporting, contradicting, and missing evidence;
- structured confirmation conditions;
- immutable Thesis persistence and fingerprints;
- focused tests for the affected boundaries.

No missing service, database, provider adapter, or architectural authority is
required before planning. The remaining decisions are bounded Momentum policy
decisions and must be captured in the approved Implementation Plan. No
implementation code has been added by this analysis.

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
