# Story 0162 - Position Intelligence View

## Metadata

**ID:** `0162`
**Title:** Position Intelligence View
**Status:** Draft

## Goal

Give the trader a read-only, on-demand view of an open position that combines
authoritative current position facts, explicit decision lineage, comparable
entry/current information, and deterministic Stop/Target consequences without
producing an exit verdict or changing execution state.

## Context

Trading Core currently exposes open positions for PAPER and LIVE accounts, with
different authorities for each mode. TradePlan, ExecutionIntent, TradeOutcome,
Opportunity, Risk, Market Data, and Challenge facts already exist but remain
distributed across services and screens.

Story 0160 provides a deterministic read-only TradePlan scenario projection that
can be reused through an adapter for the current position context. Story 0161
preserves TradePlan and Opportunity decision continuity but does not cover
position monitoring.

The repository investigation identified that price, P/L, exposure, position
risk, Stop, Target, freshness, and basic TradePlan lineage are partially
available. A complete auditable market-context snapshot at entry is not yet
available, so Trend, Structure, Volatility, and thesis invalidation comparisons
must remain unavailable rather than inferred.

## Problem

The trader can see an open position and its basic financial state, but cannot
reliably answer from one bounded surface:

- which TradePlan and Opportunity produced the position;
- what the current position facts are and how fresh they are;
- what changed since entry using only comparable facts;
- what the current Stop/Target consequences are;
- which information is unknown, unavailable, stale, or not applicable.

The current model does not support deterministic thesis invalidation because
TradePlan invalidation and management rules are text-based and entry market
context is not persisted as a complete typed snapshot.

## Scope

- Add a Trading Core-owned, read-only, on-demand Position Intelligence view for
  an authorized open position.
- Support both PAPER and LIVE positions using their existing authorities:
  Trading Core for PAPER and Broker Service facts for LIVE.
- Expose current instrument, direction, quantity, entry price, current price,
  P/L, exposure, Stop, Target, duration, position risk, account risk, and
  freshness where authoritative facts are available.
- Expose explicit lineage to TradeOutcome, ExecutionIntent, TradePlan, and
  Opportunity only when canonical identifiers exist.
- Present original TradePlan thesis, invalidation conditions, and management
  rules as historical context without interpreting text as executable rules.
- Compare entry and current values for price, P/L, distances, and other facts
  only when both sides are semantically comparable.
- Expose truthful `UNCHANGED`, `UNKNOWN`, `UNAVAILABLE`, `STALE`, and
  `NOT_APPLICABLE` states.
- Reuse the deterministic scenario and Challenge projection semantics from
  Story 0160 through an explicit position-context adapter where applicable.
- Provide read-only frontend presentation for one position, without frontend
  financial calculations.
- Validate the capability first with PAPER data and authenticated ownership
  boundaries.

## Out of Scope

- Machine-evaluable thesis invalidation or thesis health scoring.
- Trend, Market Structure, or Volatility entry-vs-current comparison when the
  required entry snapshot is absent.
- Automatic Hold, Exit, Reduce, or Close recommendations.
- Automatic position management of any kind.
- Stop Loss or Take Profit modification.
- Full or partial position close execution.
- Alerts, notifications, monitoring events, or continuous passive monitoring.
- Persisted Position Intelligence snapshots, monitoring history, or replay.
- New Position, Trade, TradePlan, Opportunity, Risk, Account, or Challenge
  aggregate authorities.
- Changing the immutable TradePlan lifecycle or mutating the original thesis.
- Adding a global `Active Position` Cockpit state.
- Scanner ranking, Opportunity semantics, or new Market Intelligence
  calculations.
- AI interpretation, recommendation, or natural-language verdict generation.
- Provider-specific payloads or cTrader implementation.
- New ADR unless implementation requires a new authority, persistent
  cross-domain aggregate, or changed lifecycle semantics.

## Acceptance Criteria

- [ ] An authenticated user can request Position Intelligence for an open
      position belonging to the selected account.
- [ ] PAPER positions use Trading Core as their position authority and LIVE
      positions use Broker Service facts without leaking provider-specific DTOs.
- [ ] The response exposes current position facts, account risk facts, source
      timestamps, calculation timestamp, and freshness/status for each relevant
      source.
- [ ] Missing, stale, unavailable, and not-applicable facts are represented
      explicitly and are never converted into false unchanged or safe states.
- [ ] TradeOutcome, ExecutionIntent, TradePlan, and Opportunity lineage is
      shown only when an explicit canonical identifier exists.
- [ ] The system never infers lineage from instrument, direction, price,
      quantity, or timestamps.
- [ ] Original thesis, invalidation conditions, and management rules are shown
      as historical TradePlan context and are not treated as executable rules.
- [ ] Entry-versus-current comparison is available for price, P/L, Stop/Target
      distances, and supported risk facts when required inputs are valid.
- [ ] Trend, Structure, Volatility, and thesis comparisons report unavailable
      or unknown status when comparable entry evidence does not exist.
- [ ] Stop, Target, and Challenge consequences reuse deterministic Risk Domain
      semantics and Story 0160 projection behavior through an explicit adapter.
- [ ] Projection and viewing do not mutate Position, Trade, TradePlan, Risk,
      Account, Challenge, ExecutionIntent, ExecutionAttempt, or Broker state.
- [ ] The frontend renders the view reactively and performs no financial or
      risk calculations.
- [ ] The frontend distinguishes current facts from original TradePlan context
      and does not present a thesis-validity or exit recommendation.
- [ ] Ownership, account isolation, unknown position, missing lineage, stale
      market data, unavailable broker data, missing protections, and absent
      Challenge cases have negative or degraded-state coverage.
- [ ] Focused backend/frontend tests, affected module tests, Angular build,
      formatting, and `git diff --check` pass where applicable.
- [ ] Authenticated PAPER runtime validation demonstrates fresh, stale,
      unavailable, and incomplete-lineage states without mutation.

## Constraints

- Trading Core remains responsible for deterministic position orchestration and
  read composition.
- Broker Service remains responsible for LIVE broker facts and provider
  translation.
- Market Data remains responsible for current public market facts and freshness.
- Risk Domain remains authoritative for deterministic risk and scenario
  calculations.
- Market Intelligence remains authoritative for TradePlan and Opportunity
  context, but does not decide position management.
- TradePlan remains immutable after execution.
- The view is on-demand, recomputable, and read-only.
- No financial or risk calculation is introduced in Angular.
- No provider-specific broker field may leak into broker-neutral contracts.
- Preserve explicit human authority over all execution and position changes.
- Do not commit, push, merge, or create a pull request automatically.

## Relevant ADRs

- `docs/architecture/adr/ADR-019.md`
- `docs/architecture/adr/ADR-028.md`
- `docs/architecture/adr/ADR-029.md`
- `docs/architecture/adr/ADR-031.md`
- `docs/architecture/adr/ADR-042.md`
- `docs/architecture/adr/ADR-048.md`
- `docs/architecture/adr/ADR-050.md`

## Relevant Stories

- `0041` - Paper Local Position Query and Valuation.
- `0032` - Dedicated Open Positions Monitoring.
- `0155` - Authoritative Executed Trading Evidence.
- `0160` - Deterministic Trade Scenario Projection.
- `0161` - Trader Cockpit to TradePlan Decision Continuity.

## Relevant Modules

- `trading-core`
- `broker-service`
- `market-data`
- `market-intelligence`
- `risk-domain`
- `trading-os-web`

## Validation

- Focused Trading Core position query, ownership, lineage, projection adapter,
  freshness, and no-mutation tests.
- Risk Domain and Story 0160 compatibility tests for current, Stop, Target, and
  Challenge consequences.
- Broker Service contract tests confirming provider-neutral LIVE facts.
- Market Intelligence contract tests for TradePlan and Opportunity lineage when
  those contracts change.
- Angular unit tests for reactive loading, ownership errors, degraded states,
  unavailable facts, and no frontend financial calculations.
- Affected Maven test suites, Angular tests, production build, formatting, and
  `git diff --check`.
- Authenticated PAPER runtime validation with fresh, stale, unavailable,
  missing-protection, missing-lineage, and no-Challenge cases.
- Narrow/mobile layout validation for the position detail surface.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed.
- [ ] Relevant validation executed.
- [ ] Diff reviewed in IntelliJ.
- [ ] Code Review approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.
