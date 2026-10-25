# Story 0166 - Deterministic Market Tradeability Evidence for Market Thesis

## Metadata

**ID:** `0166`
**Title:** Deterministic Market Tradeability Evidence for Market Thesis
**Status:** Draft

## Goal

Add one deterministic, provider-neutral tradeability capability that helps
Market Thesis distinguish a directionally coherent scenario from a scenario
that is currently unsuitable for consideration because execution conditions,
market quality, or time-sensitive market context are unsafe or insufficient.

The capability must improve `CANDIDATE` / `WATCH` / `NO_TRADE` decision support
without becoming a Risk rule, TradePlan authority, execution authorization, or
human decision.

## Context

Stories 0163 through 0165 established deterministic Market Thesis composition
from Trend Context, Market Structure, structural objectives, and OHLC Momentum.
The current composer can therefore identify direction, structural geometry, and
continuation evidence.

The repository also contains provider-neutral capabilities and context for:

- normalized market snapshots and spread analysis;
- order-book and liquidity measurements;
- historical OHLC range analysis;
- temporally bounded News and economic-event context.

These inputs are not currently authoritative for Market Thesis state. As a
result, a Thesis can become `CANDIDATE` while the current spread is unsuitable,
liquidity evidence is stale or insufficient, a market-quality condition is
abnormal, or a relevant time-sensitive event is not safely understood.

## Problem

Market Thesis cannot currently answer:

> Is this structurally and directionally coherent scenario currently tradable
> enough to remain a `CANDIDATE`, or should it remain `WATCH` or become
> `NO_TRADE` because market conditions are unsafe or unknown?

This creates a false-candidate risk. Direction and Momentum are not sufficient
to establish current tradeability. Missing or contradictory market-condition
evidence must remain explicit rather than being silently treated as support or
rejection.

## Trader Question

> Are current execution conditions and time-sensitive market facts compatible
> with considering this Thesis now, or is the scenario blocked, weakened, or
> not yet evaluable?

## Scope

- Add one deterministic provider-neutral Tradeability V1 capability in
  `market-intelligence`.
- Consume existing normalized provider-neutral inputs only: market snapshot,
  spread, available order-book/liquidity measurements, historical OHLC range
  context, and temporally valid News/economic-event context.
- Define versioned deterministic predicates for:
  - quote and source freshness;
  - spread or transaction-cost suitability when a configured threshold exists;
  - liquidity/order-book evidence availability and deterioration;
  - abnormal or insufficient market-range context when the required OHLC input
    exists;
  - relevant scheduled or recently released events within the evaluated
    cutoff, without interpreting their economic meaning beyond the available
    normalized event facts.
- Preserve market/instrument identity, observed-at timestamps, evaluation
  cutoff, freshness, source references, capability and policy versions, input
  fingerprints, and result fingerprints.
- Represent `AVAILABLE`, `UNAVAILABLE`, `STALE`, `UNKNOWN`,
  `NOT_APPLICABLE`, and `NOT_IMPLEMENTED` states explicitly where applicable.
- Compose Tradeability into Market Thesis as supporting, weakening,
  blocking, or missing evidence according to an explicit deterministic policy.
- Expose structured Tradeability confirmation and blocking conditions that
  explain `WATCH`, `CANDIDATE`, and `NO_TRADE` outcomes.
- Prevent a fresh `CANDIDATE` when required current tradeability evidence is
  unavailable, stale, unknown, or blocking under the approved policy.
- Preserve immutable Thesis versions and deterministic replay.

## Out of Scope

- Changing Trend Context, Market Structure, Momentum, phase, regime, or
  alignment semantics.
- Creating a new spread, liquidity, order-book, volatility, range, News, or
  economic-calendar provider when an existing provider-neutral contract can be
  reused.
- Inferring liquidity, volatility, event impact, or execution quality from
  unsupported fields or from AI output.
- Predicting event direction or price response.
- Adding a confidence, quality, confluence, ranking, probability, or universal
  score.
- Creating executable Entry, Stop, Target, slippage, fee, or position-sizing
  parameters.
- Changing StrategyMatch, TradingOpportunity, TradePlan, Risk, execution,
  reconciliation, broker, or human-authorization semantics.
- Making Market Thesis account-scoped, challenge-scoped, or broker-scoped.
- Adding cTrader-specific identifiers, payloads, credentials, or provider
  behavior to the Thesis contract.
- Automatically creating, ranking, approving, or authorizing a trade.
- Adding AI, LLM interpretation, or AI-generated canonical evidence.
- Adding a frontend screen or frontend-side analytical calculation.
- Adding a new microservice.

## Acceptance Criteria

- [ ] Tradeability V1 is deterministic for identical normalized inputs,
      policy/version configuration, cutoff, and evidence fingerprints.
- [ ] Only cutoff-eligible, temporally valid, provider-neutral facts
      participate in the assessment; future facts cannot affect the result.
- [ ] Spread and quote freshness are represented as typed evidence with
      explicit threshold and policy provenance where configured.
- [ ] Liquidity/order-book evidence distinguishes available, stale,
      insufficient, unavailable, and not-applicable states without fabricating
      market depth.
- [ ] OHLC range context is used only when its source interval, cutoff, and
      freshness are compatible with the assessment; no unsupported volatility
      conclusion is inferred.
- [ ] News and economic-event context preserves temporal facts and does not
      infer event impact or price direction beyond normalized source data.
- [ ] Supporting, weakening, blocking, and missing Tradeability evidence are
      distinct typed contributions with source, timestamp, cutoff, freshness,
      policy, and fingerprint provenance.
- [ ] Required unavailable, stale, or unknown Tradeability evidence cannot
      silently produce a fresh `CANDIDATE`.
- [ ] A blocking Tradeability condition cannot produce `CANDIDATE`.
- [ ] A weakening but non-blocking Tradeability condition can keep a Thesis at
      `WATCH` according to an explicit deterministic state policy.
- [ ] Missing Tradeability evidence is not converted into negative market
      evidence, positive market evidence, or a universal score.
- [ ] Market Thesis exposes structured Tradeability confirmation and blocking
      references explaining the resulting state and reasons.
- [ ] The capability does not alter Risk, TradePlan, execution, broker,
      StrategyMatch, TradingOpportunity, or human-authorization authority.
- [ ] Identical authoritative inputs produce identical Tradeability evidence,
      Thesis content, state, reasons, and fingerprints.
- [ ] Re-evaluation creates a new immutable Thesis version or lineage and never
      mutates historical evidence.
- [ ] Focused tests cover fresh suitable conditions, excessive spread, stale
      quotes, unavailable and insufficient liquidity, abnormal or unavailable
      range context, future News facts, relevant event context, contradictory
      conditions, missing required inputs, deterministic replay, and Thesis
      state transitions.
- [ ] Affected Market Intelligence tests, formatting checks, and `git diff
      --check` pass.

## Constraints

- ADR-076 is the governing architectural decision.
- Market Data remains authoritative for normalized market facts and freshness.
- News Service remains authoritative for normalized News and economic-event
  facts and their temporal metadata.
- Market Intelligence owns deterministic Tradeability analysis and Thesis
  composition, but not Risk or execution authorization.
- Trend Context, Market Structure, and Momentum remain authoritative for their
  existing typed semantics.
- Missing, stale, unavailable, invalid, insufficient, and not-implemented
  evidence must remain distinguishable and must not be silently converted into
  support or contradiction.
- Provider-neutral contracts must not expose provider payloads, credentials, or
  cTrader identifiers.
- `FeatureProjectionV1` and S1 semantics remain frozen.
- No commit, push, merge, pull request, branch deletion, or destructive Git
  operation is performed automatically.

## Relevant ADRs

- `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md`
- `docs/architecture/adr/ADR-020.md`
- `docs/architecture/adr/ADR-025.md`
- `docs/architecture/adr/ADR-027.md`
- `docs/architecture/adr/ADR-028.md`
- `docs/architecture/adr/ADR-034.md`
- `docs/architecture/adr/ADR-038.md`

## Relevant Stories

- `0163` - Deterministic Market Thesis Composition.
- `0164` - Structural Levels and Objectives.
- `0165` - Deterministic Momentum V1.
- `0102` - User-Facing News and Economic Context.
- `0112A` - Durable Order Book Evidence Acquisition.
- `0113` - Realistic Execution Transaction Cost Research.
- `0159` - Surface Evidence-Led Opportunity Decisions.
- `0160` - Deterministic Trade Scenario Projection.

## Relevant Modules

- `market-intelligence`
- `market-data` (existing provider-neutral facts and freshness)
- `news-service` (existing normalized News/economic-event facts)
- `trading-core` (provenance compatibility only, if required)
- `risk-domain` (no semantic changes; compatibility verification only)

## Architecture Decision Gate

ADR-076 is sufficient if this Story reuses existing provider-neutral market,
order-book, OHLC, and News authority and adds only deterministic Tradeability
composition to Market Thesis. A new ADR is required if implementation changes
the authority of Market Data or News Service, introduces a competing market
quality engine, changes executable cost or target semantics, or changes Risk,
TradePlan, execution, or challenge behavior.

## Validation

- Focused Market Intelligence domain tests for Tradeability evidence and Thesis
  state predicates.
- Tests for fresh, stale, future, unavailable, insufficient, invalid, and
  not-applicable source facts.
- Tests for spread threshold and quote freshness behavior.
- Tests for order-book/liquidity evidence provenance and missingness.
- Tests for compatible and incompatible OHLC range context.
- Tests for News/economic-event cutoff isolation and temporal metadata.
- Tests for supporting, weakening, blocking, and missing evidence polarity.
- Tests proving no Risk, TradePlan, StrategyMatch, Opportunity, execution,
  broker, or human-authorization boundary is called or changed.
- Deterministic replay, fingerprint, immutable persistence, and version-lineage
  tests.
- Affected Market Intelligence and integration-contract Maven tests,
  formatting checks, and `git diff --check`.
- Runtime validation only when an authenticated deterministic Thesis surface
  exists; no cTrader runtime validation is required for this Story.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed.
- [ ] Documentation reconciliation completed.
- [ ] Relevant validation executed.
- [ ] Diff reviewed in IntelliJ.
- [ ] Independent Code Review approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.
