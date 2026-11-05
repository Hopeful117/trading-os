# Story 0204 - Crypto Intelligence Pipeline Validation and AI Readiness

## Metadata

**ID:** `0204`
**Title:** Crypto Intelligence Pipeline Validation and AI Readiness
**Status:** Implementation Plan pending human approval
**Priority:** P0 - Trading Intelligence
**Related Stories:** `0203`
**Related ADRs:** `ADR-020`, `ADR-021`, `ADR-034`, `ADR-056`, `ADR-076`

## Goal

Validate and harden the existing deterministic crypto intelligence pipeline so
that a future AI Engine can consume a coherent, traceable and reproducible
Market Thesis without requiring a deterministic-pipeline rewrite.

The pipeline must work from public Kraken market data, without cTrader,
private Kraken credentials or trading execution.

## Scope

- Validate an explicit, bounded crypto scan scope using canonical Kraken market
  identifiers, without inventing or mixing Spot and Futures identities.
- Verify OHLC availability, timeframe coverage, chronology, gaps, incomplete
  candles, freshness, provenance, normalization version and evaluation cutoff.
- Validate the existing Trend Context H4/H1/M15, Market Structure, Momentum and
  Tradeability capabilities without changing their business thresholds.
- Validate the existing `ProductionIntelligencePipeline` and
  `MarketThesisComposer` through Market Thesis and opportunity/no-signal paths.
- Preserve and expose deterministic negative outcomes such as `NO_TRADE`,
  `WATCH`, `UNKNOWN`, stale evidence and missing evidence.
- Add only the minimum persistence or projection required to retain negative
  analytical outcomes when existing models and boundaries support it.
- Verify that a structured deterministic context can be serialized for a future
  AI Engine without exposing secrets, credentials or unnecessary account data.
- Reuse existing Angular scan, opportunity and Market Thesis projections.
- Add provider-free tests for data quality, deterministic analysis, thesis
  composition, negative outcomes, scanner scope and Angular projections.

## Out of Scope

- Any cTrader call or modification to cTrader protections.
- Any private Kraken call, credential use, order, mutation or execution.
- Changes to T0/T1, Risk Domain, Execution, Broker Service or FTMO policies.
- A passive scanner.
- A new strategy or changes to strategy thresholds.
- A real LLM, AI Engine runtime, Python/FastAPI service or recommendation logic.
- A new microservice or framework.
- Backtesting, feature-store infrastructure or broad Market Intelligence
  refactoring.
- A UI redesign.

## Constraints

- Preserve all existing work from Stories 0198-0203.
- Use only canonical market identifiers found in the persisted Kraken catalogue.
- Keep Spot and Futures provider/product identity explicit.
- Treat missing, stale, incomplete and contradictory data explicitly; never
  manufacture a candidate from insufficient evidence.
- Preserve Market Thesis as analytical evidence, not Risk or execution authority.
- Keep deterministic capabilities pure and versioned.
- Keep AI behind `AiEnginePort` and prevent direct access to source services.
- Do not commit, push, merge or modify unrelated work automatically.

## Acceptance Criteria

- A bounded crypto scan can be triggered without Broker Service, cTrader or
  private Kraken APIs.
- The selected markets and product types use identifiers actually present in
  the Kraken catalogue.
- Required H4/H1/M15 evidence is identifiable by market, interval, timestamps,
  cutoff, provenance and normalization/rule versions.
- OHLC chronology, gaps, incomplete candles, freshness and duplicate/conflict
  behavior are validated without silently normalizing invalid evidence into a
  valid setup.
- Trend Context, Market Structure, Momentum and Tradeability outputs remain
  distinct, versioned and traceable.
- Market Thesis preserves market identity, cutoff, validity, supporting,
  contradicting and missing evidence, reasons, confirmation/invalidation
  conditions and provenance.
- Equal authoritative inputs and versions produce equal deterministic thesis
  results.
- No-signal and degraded results remain explainable and are not presented as
  valid opportunities.
- A future AI context projection can be serialized from deterministic evidence
  without secrets, credentials or unrestricted service access.
- Angular distinguishes market facts, deterministic evidence, contradictions,
  missing data, thesis state and human decisions; it does not present ranking
  as a calibrated probability or deterministic analysis as AI output.
- Automated validation uses no real broker or provider credentials and no
  external mutation.
- No T0/T1, Risk, Execution, Broker Service or cTrader protection behavior is
  changed.

## Validation Expectations

- Targeted Market Data tests.
- Targeted Market Intelligence tests for capabilities, pipeline, thesis,
  persistence and negative outcomes.
- Scanner tests for bounded multi-market scope and provider isolation.
- Angular tests for thesis and negative-state projections.
- Market Intelligence and Angular checks required by the repository.
- `git diff --check`.
- Any public Kraken runtime validation must be separately approved first with a
  fixed endpoint, market, timeframe and call budget. It is not part of ordinary
  implementation validation.

## Next Story

Story 0205 - AI Engine Runtime Foundation: integrate a real provider-neutral,
structured, shadow-mode AI Engine behind the existing `AiEnginePort`.
