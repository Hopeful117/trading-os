# Story 0062 - Establish the Trend Context Input Contract

## Metadata

**ID:** `0062`

**Title:** Establish the Trend Context Input Contract

**Status:** IMPLEMENTED - VALIDATION COMPLETE; HUMAN CODE REVIEW PENDING

**Size:** MEDIUM

**Implementation Risk:** HIGH

## Goal

Provide a dedicated, immutable `TrendContextAssessmentInput` contract that can
represent the role-based OHLC evidence required by the accepted deterministic
Trend Context V1 design.

The contract must preserve enough source and temporal information for two
independent deterministic implementations to receive equivalent inputs without
expanding generic `HistoricalOhlcContext` into a Trend Context-specific model.

## Context

Market Data currently exposes `OhlcEvent` with market/provider identity,
interval, OHLCV, `closed`, and `occurredAt`. `OhlcHistoryNormalizer` inserts
flat synthetic candles for missing intervals, but the normalized result does not
currently expose synthetic status explicitly. Market Intelligence currently
maps historical data into a single-interval `HistoricalOhlcContext` and the
active contributor requests only 200 fifteen-minute candles.

The accepted Trend Context design requires `BIAS`, `SETUP`, and optional
`TRIGGER` role series, closed-candle semantics, real/synthetic provenance,
cut-off, gaps, freshness, source references, profile/version identity, and a
deterministic input fingerprint.

## Problem

The current generic history payload cannot safely distinguish real from
synthetic candles or preserve all candle-level provenance required for
confirmed structural analysis. A pure engine cannot safely calculate pivots,
breaks, or freshness from an input that has already discarded those semantics.

## Scope

### Included

- Define the dedicated `TrendContextAssessmentInput` and immutable role-series
  value objects in the Market Intelligence domain boundary.
- Represent BIAS, SETUP, and optional TRIGGER roles and their configured
  intervals.
- Preserve candle OHLCV, closure, synthetic status, source/provider identity,
  source occurrence time, fetch time, open/close times, and source identity.
- Preserve missing intervals/gap findings rather than silently treating them as
  market behavior.
- Define assessment cut-off, freshness metadata, profile ID/version, rule
  version, and deterministic input fingerprint fields.
- Map Market Data historical responses into the dedicated input without moving
  market fact ownership into Market Intelligence.
- Make synthetic status explicit at the normalized boundary. The implementation
  may extend the normalized Market Data contract or introduce an equivalent
  explicit normalization result, but it must not infer synthetic status from
  price values.
- Validate ordering, duplicates, OHLC constraints, future candles, role
  interval compatibility, and required role presence.
- Define the provisional `CONSERVATIVE_SWING_V1` profile values from the accepted
  domain design without optimizing or changing them.
- Produce a stable fingerprint for identical accepted inputs and versions.

### Required validation outcomes

- malformed OHLC, impossible timestamps, future candles, or conflicting
  duplicates are invalid;
- open candles are excluded from deterministic calculations;
- synthetic candles are excluded from deterministic calculations but remain
  visible as provenance/gap findings;
- missing required roles and insufficient history are explicit findings;
- optional TRIGGER absence is representable without manufacturing evidence.

## Out of Scope

- Swing, structure, break, transition, EMA, ATR, pullback, extension, or
  multi-timeframe calculations.
- `IntelligenceObservation` persistence.
- Strategy evaluation or opportunity creation.
- Risk, TradePlan, execution, ML, agents, news, or UI changes.
- Rich range classification, momentum, strength, clustered structural areas, or
  probabilistic ranking.
- Changing provider APIs or moving normalization ownership to Market
  Intelligence.

## Architectural Constraints

- `docs/architecture/adr/ADR-048.md` is authoritative for source/evidence
  authority, provenance, immutability, and temporal integrity.
- The accepted Trend Context design is authoritative for the dedicated input
  shape, role semantics, synthetic-candle policy, and profile hypotheses.
- Market Data remains authoritative for normalized market facts, source metadata,
  market state, and interval semantics.
- The input must not depend on Spring, repositories, HTTP clients, brokers,
  account state, Risk, ML, LLMs, or execution.
- Do not extend generic `HistoricalOhlcContext` merely to hide Trend Context
  semantics in an untyped payload.
- Existing unrelated worktree changes must be preserved.

## Acceptance Criteria

- [x] A dedicated immutable input contract represents BIAS, SETUP, and optional
      TRIGGER role series with their configured intervals.
- [x] Each input candle preserves closure, synthetic status, source/provider
      identity, source occurrence time, fetch time, and candle timestamps.
- [x] The input preserves gap findings and does not encode synthetic candles as
      ordinary market candles.
- [x] The input records market identity, assessment/cut-off time, profile
      identity/version, rule version, freshness metadata, and fingerprint.
- [x] Invalid OHLC, impossible timestamps, future candles, conflicting
      duplicates, invalid profiles, and missing required roles fail explicitly.
- [x] Open and synthetic candles cannot enter the calculation-ready series.
- [x] Optional TRIGGER absence is distinguishable from an invalid required role.
- [x] Identical accepted inputs and versions produce the same fingerprint.
- [x] Mapping tests prove Market Data source metadata is not silently discarded.
- [x] Existing generic OHLC context and Market Data history behavior remain
      compatible unless an explicit metadata extension is required for this
      contract.
- [x] No deterministic trading conclusion, opportunity, Risk result, or
      execution command is produced by this Story.
- [x] Focused Market Data and Market Intelligence tests pass.
- [x] `git diff --check` passes.

## Test Requirements

- Unit tests for candle and role validation.
- Mapping tests for provider/source/closure/synthetic/gap metadata.
- Duplicate, ordering, cut-off, invalid OHLC, and insufficient-history tests.
- Fingerprint determinism and changed-input differentiation tests.
- Regression tests for existing `OhlcHistoryNormalizer` and historical OHLC
  consumers.

## Observability and Provenance Requirements

- Record accepted and excluded candle counts by role.
- Record source IDs, provider, source/fetch timestamps, cut-off, profile/version,
  rule version, and fingerprint.
- Never log credentials or sensitive account data.
- Do not duplicate full provider payloads when immutable source references are
  sufficient.

## Dependencies

- Existing Market Data `OhlcEvent`, `OhlcInterval`, `MarketHistoryService`, and
  normalization contracts.
- Existing Market Intelligence `MarketDataClient`, `OhlcResponse`, and context
  adapter.
- Accepted ADR-048 and the deterministic Trend Context domain design.

## Likely Files/Components Affected

- `market-data/.../model/OhlcEvent.java` or an equivalent normalized history
  contract.
- `market-data/.../service/OhlcHistoryNormalizer.java` and its tests.
- `market-intelligence/.../domain/` dedicated Trend Context input types.
- `market-intelligence/.../adapter/marketdata/MarketDataClient.java` and
  response mapping.
- `market-intelligence/.../adapter/marketdata/HistoricalOhlcContextContributor.java`
  or its replacement adapter.
- Focused Market Data and Market Intelligence tests.

## Risks

- The existing normalizer currently creates synthetic candles without explicit
  identity, so source-contract changes may cross a module boundary.
- Required 4h/1h/15m history may exceed the current single-interval 200-candle
  request and must not be silently truncated.
- A compatibility mapping that loses closure or synthetic metadata would make
  later deterministic conclusions unsafe.

## Definition of Done

- [x] Story scope approved by the human engineer.
- [x] Repository Analysis approved.
- [x] Implementation Plan approved when required.
- [x] Input contract and source mapping implemented within scope.
- [x] Required unit/integration/regression tests pass.
- [x] Provenance and fingerprint behavior is documented by tests.
- [ ] Human code review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.
