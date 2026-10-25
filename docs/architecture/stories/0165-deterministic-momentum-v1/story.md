# Story 0165 - Deterministic Momentum V1 for Market Thesis

## Metadata

**ID:** `0165`

**Title:** Deterministic Momentum V1 for Market Thesis

**Status:** Draft

## Goal

Add one deterministic, provider-neutral Momentum capability that helps Market
Thesis determine whether a structurally valid move is continuing, weakening,
accelerating, or losing directional participation.

The capability must improve disciplined `TRADE` / `WATCH` / `NO_TRADE`
decision support without becoming a prediction, confidence score, Risk rule,
or execution authorization.

## Context

Story 0164 materially improved Market Thesis structural geometry by adding:

- structural and protected levels;
- structural invalidation references;
- a next structural objective;
- opposing structural obstacles;
- cutoff, freshness, and provenance preservation.

The current repository already provides provider-neutral closed OHLC evidence,
Trend Context, Market Structure, multi-timeframe alignment, phase, ATR, and
structural invalidation semantics.

The remaining high-value gap is that Market Thesis can identify direction and
structure but cannot independently determine whether the current move is
strengthening or deteriorating. Its current confirmation condition is primarily
`TREND_DIRECTIONAL`, so a fresh directional structure can become `CANDIDATE`
without explicit continuation or participation evidence.

ADR-076 establishes Market Thesis as the deterministic composition authority
and explicitly keeps missing Momentum evidence visible rather than inferring it
from Trend or Structure.

## Problem

Market Thesis cannot currently answer:

> Is the current structurally valid move continuing with sufficient directional
> participation, or is it weakening and therefore only `WATCH` or `NO_TRADE`?

The repository contains OHLC inputs sufficient for a bounded deterministic
Momentum capability, but no Momentum model is implemented. Consequently:

- confirmation is too generic;
- weakening before structural invalidation is not explicit;
- an attractive structural scenario can remain a `CANDIDATE` without
  independent move-quality evidence;
- contradictory price movement cannot reliably prevent weak candidate
  presentation.

## Trader Question

> Is current price movement confirming the structural scenario, weakening it,
> or providing insufficient evidence to promote the Thesis beyond `WATCH`?

## Scope

- Add one deterministic provider-neutral Momentum V1 capability in
  `market-intelligence`.
- Consume normalized closed OHLC evidence only.
- Respect an explicit evaluation cutoff and exclude future, open, invalid, or
  otherwise ineligible candles.
- Define deterministic evidence for the current move's directional persistence,
  normalized slope, and acceleration or deceleration relative to recent OHLC
  history.
- Preserve source market/instrument identity, timeframe, observed range,
  cutoff, freshness, capability version, rule version, input fingerprint, and
  result fingerprint.
- Represent unavailable, stale, insufficient, and not-applicable Momentum
  evidence explicitly.
- Compose Momentum into Market Thesis as supporting, contradicting, or missing
  evidence according to an explicit deterministic policy.
- Expose structured Momentum confirmation and deterioration conditions that can
  explain `WATCH`, `CANDIDATE`, and `NO_TRADE` outcomes.
- Prevent a structurally attractive Thesis from being promoted solely by
  direction when the approved Momentum composition policy requires current
  continuation evidence.
- Preserve immutable Thesis versions and deterministic replay.

## Out of Scope

- Adding Volume, tick-volume, broker-volume, exchange-volume, or order-flow
  semantics.
- Adding a new Regime or Range engine, compression classification, or expansion
  classification.
- Changing existing Trend Context direction, structure, phase, alignment,
  invalidation, EMA, or ATR semantics.
- Replacing or duplicating Market Structure.
- Adding a universal confidence, strength, confluence, probability, or quality
  score.
- Adding RSI, MACD, VWAP, ADX, or an indicator collection by default.
- Adding divergence semantics unless they can be defined and validated as part
  of the approved bounded Momentum contract.
- Adding News, economic-event, Session, Spread, Liquidity, or Order Book
  behavior.
- Adding cTrader Market Data, reference-market data, or provider-specific
  contracts.
- Changing `FeatureProjectionV1` or S1 semantics.
- Changing StrategyMatch, TradingOpportunity, Opportunity ranking, TradePlan,
  Risk, position sizing, execution, reconciliation, or broker behavior.
- Making Market Thesis account-scoped, challenge-scoped, or a Risk authority.
- Automatically creating, ranking, approving, or authorizing a trade.
- Adding AI, LLM interpretation, or AI-generated canonical evidence.
- Adding a frontend screen or frontend-side analytical calculation.
- Adding a new microservice.

## Acceptance Criteria

- [ ] Momentum V1 is deterministic for identical normalized OHLC inputs,
      profile/policy versions, cutoff, and evidence fingerprints.
- [ ] Only closed, valid, cutoff-eligible OHLC candles participate in Momentum
      calculations; no look-ahead is possible.
- [ ] Momentum evidence distinguishes at minimum directional persistence,
      normalized slope, and acceleration/deceleration or their explicit
      unavailable states.
- [ ] The capability does not create or change Trend direction, Market
      Structure, protected levels, invalidation, or Risk authority.
- [ ] Momentum is evaluated as evidence relative to an existing directional
      Thesis and cannot create a scenario when structural direction is absent.
- [ ] Momentum can produce explicit supporting evidence when movement confirms
      the scenario, contradicting evidence when movement materially weakens or
      opposes it, and missing evidence when the required input is unavailable.
- [ ] Contradiction severity is explicit and no contradiction is hidden by a
      numeric score or averaged confidence value.
- [ ] Market Thesis confirmation conditions identify what Momentum event or
      predicate is pending or satisfied.
- [ ] Market Thesis state policy prevents a fresh `CANDIDATE` from being based
      only on directional Trend evidence when required Momentum evidence is
      unavailable, stale, or materially contradictory.
- [ ] A Momentum contradiction can keep a Thesis at `WATCH` or produce
      `NO_TRADE` according to explicit deterministic policy without bypassing
      structural invalidation semantics.
- [ ] Missing or unavailable Momentum remains distinguishable from negative
      market evidence.
- [ ] Momentum evidence preserves market/instrument, timeframe, observed-at,
      cutoff, freshness, provider-neutral source reference, capability version,
      rule/policy version, and fingerprints.
- [ ] The same authoritative inputs produce identical Momentum evidence,
      Thesis content, state, reasons, and fingerprints.
- [ ] Thesis re-evaluation remains immutable and creates a new version or
      lineage rather than mutating historical evidence.
- [ ] The implementation does not change `FeatureProjectionV1`, S1, Risk,
      execution, broker, StrategyMatch, TradingOpportunity, or TradePlan
      semantics.
- [ ] Focused tests cover bullish and bearish symmetry, persistence,
      slope, acceleration/deceleration, insufficient history, stale evidence,
      cutoff isolation, contradictory movement, missing Momentum, deterministic
      replay, and Thesis state transitions.
- [ ] Affected Market Intelligence tests, formatting checks, and `git diff
      --check` pass.

## Constraints

- ADR-076 is the governing architectural decision.
- Market Data remains authoritative for normalized OHLC facts and freshness.
- Market Intelligence owns deterministic Momentum analysis and Thesis
  composition.
- Trend Context and Market Structure remain authoritative for direction,
  structure, phase, alignment, levels, and invalidation.
- Momentum must remain provider-neutral and must not expose cTrader payloads or
  identifiers.
- OHLC is the sufficient V1 data dependency; cTrader Market Data is not a
  prerequisite.
- Volume must not be inferred, required, or smuggled into Momentum semantics.
- Risk remains the deterministic financial authorization boundary.
- Market Thesis remains decision support, not prediction, probability, Risk,
  TradePlan, execution, or human authorization.
- Missing, stale, invalid, insufficient, and not-implemented evidence must
  remain explicit and must not be silently converted into support or
  contradiction.
- Thresholds and classifications must be deterministic, versioned, evidence-
  linked, and validated without introducing a universal score.
- `FeatureProjectionV1` and S1 semantics are frozen for this Story.
- No commit, push, merge, pull request, branch deletion, or destructive Git
  operation is performed automatically.

## Relevant ADRs

- `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md`
- `docs/architecture/adr/ADR-020.md`
- `docs/architecture/adr/ADR-025.md`
- `docs/architecture/adr/ADR-038.md`

## Relevant Stories

- `0063` - Deterministic Trend Context Engine.
- `0064` - Trend Context Observation Integration.
- `0107B` - Strict Historical Market Intelligence Replay.
- `0128` - OHLC Temporal Feature Availability.
- `0163` - Deterministic Market Thesis Composition.
- `0164` - Structural Levels and Objectives for Market Thesis.

## Relevant Modules

- `market-intelligence`
- `market-data` (existing provider-neutral OHLC contract compatibility only)

## Architecture Decision Gate

ADR-076 is sufficient if this Story adds one provider-neutral deterministic
Momentum capability and composes its evidence into the existing Market Thesis
authority without changing ownership boundaries.

A new ADR is required if implementation introduces a new analytical authority,
changes Market Structure or Trend Context ownership, introduces provider-
specific semantics into the domain, changes executable planning or Risk, or
requires reference-market mapping.

## Validation

- Focused Market Intelligence domain tests for Momentum calculation and
  evidence polarity.
- Mirrored bullish and bearish fixtures.
- Tests for persistence, slope normalization, acceleration/deceleration, and
  insufficient history.
- No-look-ahead tests using appended post-cutoff candles.
- Freshness, provenance, input fingerprint, and deterministic replay tests.
- Tests proving Momentum cannot create direction without Trend/Structure.
- Tests proving Momentum contradiction prevents unjustified candidate
  promotion according to the approved Thesis policy.
- Tests for `WATCH`, `CANDIDATE`, and `NO_TRADE` reasons and confirmation
  conditions.
- Tests proving no Volume, cTrader, AI, Risk, execution, FeatureProjectionV1,
  or S1 boundary is called or changed.
- Affected Market Intelligence Maven tests.
- Architecture tests where applicable.
- `git diff --check`.
- No cTrader runtime validation is required because V1 uses existing
  provider-neutral OHLC contracts.

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
