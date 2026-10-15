# Story 0106 - Implementation Plan

## Plan Status

This plan records the minimal implementation boundary used for Story 0106.
Implementation and validation are complete; human Story review remains pending.

## Implementation Boundary

Extend the existing Market Intelligence domain and capabilities without adding
a new service, persistence model, provider integration or parallel analyzer
framework.

## Design

- Add typed status and result records for quote-quality and OHLC temporal
  calculations.
- Keep mathematical calculations pure and provider-neutral.
- Reuse the existing `SpreadAnalysisCapability` and
  `OhlcRangeAnalysisCapability` artifact boundaries.
- Preserve existing metric names and artifact compatibility while adding the
  new measurements.
- Use `IntelligenceAnalysisRequest.evaluationCutoff()` as the Trend Context
  temporal boundary when supplied.
- Filter Trend Context candle open, close, occurrence and fetch timestamps at
  the contributor boundary.
- Preserve existing Market Structure and Trend Context implementations rather
  than creating duplicate engines.

## Validation Plan

- Add unit tests for valid, insufficient and invalid quote calculations.
- Add unit tests for OHLC range, price change, simple return and invalid input.
- Add capability regression assertions for the new measurements.
- Add a Trend Context regression test containing future evidence.
- Run `mvn -q test` and `mvn -q verify` from `market-intelligence`.
- Run `git diff --check` and inspect unrelated worktree changes.

## Explicit Non-Goals

- No Opportunity, TradePlan, Risk or execution behavior.
- No ML, LLM, feature vector or feature-store work.
- No order-flow or news semantic analysis.
- No schema migration or cross-service contract change.
