# Story 0168 - Thesis-Aware Opportunity Decision Gate

## Metadata

**ID:** `0168`
**Title:** Thesis-Aware Opportunity Decision Gate
**Status:** Draft - Implementation Plan Awaiting Approval

## Goal

Make the deterministic Market Thesis visible and authoritative in the existing
Opportunity investigation workflow so a trader cannot prepare a TradePlan
without understanding the exact Thesis that produced the Opportunity.

This Story improves trade selection, NO TRADE selection, invalidation clarity,
objective clarity, contradiction visibility, missing-evidence visibility, and
decision discipline. It does not add new analysis.

## Decision Invariant

```text
Market Evidence
    -> Deterministic Analyses
    -> Market Thesis
    -> Opportunity
    -> Human Investigation
    -> TRADE / NO TRADE
    -> TradePlan
    -> Risk
    -> Explicit Human Authorization
```

The following boundaries remain unchanged:

```text
Market Thesis != trading decision
Opportunity != authorization
CANDIDATE != TRADE
TradePlan != Risk approval
Risk approval != execution authorization
```

## Context

Market Thesis is already a persisted, immutable, versioned Market Intelligence
concept. It contains state, structured evidence, contradictions, missing
evidence, confirmation conditions, invalidation conditions, structural levels,
objective context, freshness, and provenance.

TradingOpportunity currently exposes only generic setup and strategy-match
provenance. It does not preserve the exact Market Thesis identity used to
produce the Opportunity. The DecisionWorkspace therefore cannot present the
authoritative Thesis before TradePlan preparation, and the backend preparation
path does not enforce Thesis state.

The current cockpit NO TRADE state is presentation-local. There is no durable
human decision record for rejecting an Opportunity without creating a
TradePlan.

## Scope

- Preserve exact `MarketThesis` id/version lineage on every actionable
  `TradingOpportunity`.
- Expose the exact persisted Thesis through the Opportunity decision contract.
- Present Thesis state, scenario, confirmations, invalidations, objective,
  obstacles, supporting evidence, contradictions, missing evidence, freshness,
  and provenance in `DecisionWorkspace`.
- Enforce the preparation gate in the backend:
  - `NO_TRADE` -> preparation blocked;
  - `WATCH` -> preparation blocked;
  - `CANDIDATE` -> preparation allowed.
- Fail closed when Thesis lineage is missing, stale, expired, incompatible, or
  unavailable.
- Require the exact Opportunity version during Opportunity-based preparation.
- Preserve exact Thesis lineage in TradePlan rationale and response contracts.
- Add a bounded durable human `NO_TRADE` decision record without creating a
  TradePlan.
- Preserve account ownership, Risk, TradePlan, execution, and human
  authorization boundaries.
- Add focused backend and Angular tests and perform one bounded manual runtime
  validation when the cTrader environment is available.

## Out Of Scope

- New Trend, Structure, Momentum, Tradeability, Volume, Order Flow, DOM, or
  cTrader analytical logic.
- AI, LLM recommendations, probability, confidence, expected value, or a new
  universal score.
- Changes to deterministic Risk semantics, sizing, challenge rules, Stop,
  Target, execution, or live trading.
- Replacing Market Thesis with a competing analytical model.
- Re-querying latest Thesis data for an old Opportunity.
- Discretionary overrides for `WATCH`.
- Automatically converting Thesis invalidation into an executable Stop.
- Automatically converting structural objective into an executable Target.
- A separate primary Market Thesis application page.

## State Semantics

### NO_TRADE

Trading OS currently finds no responsible actionable setup. The Opportunity
cannot prepare a TradePlan. The trader may record an explicit durable NO TRADE
decision.

### WATCH

A coherent scenario may exist, but required confirmation or evidence is
incomplete. TradePlan preparation is blocked for the initial FTMO dry-run.

### CANDIDATE

Deterministic evidence is sufficient for human investigation. TradePlan
preparation is allowed, but this state does not authorize the trade or bypass
Risk.

## Durable NO TRADE Decision

Trading Core owns the authenticated, account-scoped human workflow. Add a
bounded `OpportunityDecision` record containing:

- actor id;
- account id;
- Opportunity id/version;
- exact Market Thesis id/version;
- decision `NO_TRADE`;
- optional rationale;
- decision timestamp.

This record is not a Market Thesis authority, Risk authority, TradePlan, or
execution authorization.

## Acceptance Criteria

- [ ] Every actionable Opportunity exposes the exact persisted Market Thesis
      id/version used for its creation.
- [ ] Historical Opportunity versions never silently resolve to a newer Thesis.
- [ ] The Opportunity contract exposes structured Thesis state and content.
- [ ] `NO_TRADE`, `WATCH`, and `CANDIDATE` are visibly distinct in the cockpit.
- [ ] Supporting evidence is exposed with source, timeframe, polarity, state,
      freshness, and provenance where available.
- [ ] Contradicting evidence is first-class and preserves severity,
      including `WEAKENING` and `BLOCKING`.
- [ ] Missing evidence preserves `UNAVAILABLE`, `STALE`, `UNKNOWN`,
      `NOT_APPLICABLE`, and `NOT_IMPLEMENTED` semantics where applicable.
- [ ] Confirmation and invalidation conditions are shown as structured data.
- [ ] Structural objective and obstacles are shown separately from executable
      Target and Stop values.
- [ ] Thesis freshness, evaluation time, source cutoff, validity, and useful
      provenance are visible.
- [ ] Backend preparation rejects `NO_TRADE`.
- [ ] Backend preparation rejects `WATCH`.
- [ ] Backend preparation accepts `CANDIDATE`, subject to existing ownership,
      freshness, planning, and Risk preflight checks.
- [ ] Missing, stale, expired, or incompatible Thesis lineage fails closed.
- [ ] TradePlan provenance contains exact Thesis id/version.
- [ ] NO TRADE can be durably recorded without a TradePlan.
- [ ] NO TRADE records preserve actor, account, Opportunity, Thesis, timestamp,
      and optional rationale.
- [ ] Risk semantics and explicit human authorization remain unchanged.
- [ ] Angular tests cover all Thesis sections, all gate states, and context
      changes.
- [ ] Backend tests cover lineage, gates, stale Thesis, ownership, idempotency,
      and durable NO TRADE.
- [ ] A bounded manual cTrader-connected journey is performed when available;
      a successful NO TRADE or WATCH outcome is valid.

## Relevant ADRs

- `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md`
- `docs/architecture/adr/ADR-034.md`
- `docs/architecture/adr/ADR-048.md`
- `docs/architecture/adr/ADR-031.md`
- `docs/architecture/adr/ADR-028.md`

## Relevant Stories

- `0159` - Surface Evidence-Led Opportunity Decisions in the Trader Cockpit.
- `0160` - Deterministic Trade Scenario Projection.
- `0161` - Trader Cockpit to TradePlan Decision Continuity.
- `0163` - Deterministic Market Thesis Composition.
- `0164` - Structural Levels and Objectives for Market Thesis.
- `0156` - Persist Human Trade Plan Decision Provenance.

## Relevant Modules

- `market-intelligence`
- `trading-core`
- `trading-os-web`

## Validation

- Focused Market Intelligence Thesis, Opportunity, and TradePlan tests.
- Focused Trading Core preparation-gate and OpportunityDecision tests.
- Angular DecisionWorkspace and service tests.
- Angular production build.
- Affected Maven module tests.
- `git diff --check`.
- Authenticated desktop and narrow-width cockpit validation where available.
- Bounded cTrader-connected paper journey without live execution.

## Workflow Status

- [x] Repository Analysis completed.
- [x] Repository Analysis approved by human.
- [x] Durable NO TRADE decision recommendation approved by human.
- [x] Implementation Plan prepared.
- [x] Implementation Plan approved.
- [x] Implementation completed.
- [x] Documentation reconciliation completed.
- [x] Code Review completed.
- [ ] Human commit and integration completed.
