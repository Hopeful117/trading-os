# Story 0040 - Mode-Aware Neutral Risk Facts for Canonical T0/T1 Evaluation

## Metadata

**ID:** `0040`

**Status:** Implemented - Ready for Code Review

**Baseline:** `main` at `2f2dbdc` (Story 0039 merged)

## Goal

Build one neutral Trading Core Risk Facts boundary that resolves the canonical
financial `Account`, resolves its linked `BrokerAccount` and `ExecutionMode`,
selects authoritative LIVE or PAPER fact sources, and constructs coherent Risk
Domain snapshots for T0 and T1.

T0 and T1 must use the same financial Account and exact persisted RiskProfile
assignment while preserving deterministic fail-closed behavior.

## Context

ADR-042 establishes:

```text
LIVE_POSITION_AUTHORITY = BROKER
PAPER_POSITION_AUTHORITY = TRADING_CORE
```

ADR-043 establishes:

```text
Account.accountId = financial identity
BrokerAccount.id = execution/routing identity
Account.brokerAccountId = canonical relation
```

Story 0039 persisted the canonical relation and explicit PAPER profile
assignment. The current risk services do not consume that relation as the
authoritative routing boundary. T0 uses `Command.accountId` but obtains its
BrokerAccount through `AccountRiskConfiguration`. T1 incorrectly treats
`ExecutionIntent.brokerAccountId` as an Account ID. Both paths currently use
`BrokerRiskFactsPort`, including PAPER.

The Risk Domain itself is already fact-source agnostic. The missing boundary is
in Trading Core application fact acquisition and context construction.

## Problem

Current behavior creates three correctness gaps:

- T0 and T1 are not guaranteed to resolve the same financial Account.
- T1 can compare or persist `BrokerAccount.id` as `Account.accountId`.
- PAPER risk evaluation depends on external Broker Service facts even though
  Trading Core owns PAPER balances and positions.

The current T1 is an execution-time, pre-submission gate. There is no separate
post-settlement T1 validation path in the repository.

## Scope

- Resolve T0 from the financial Account ID and its canonical relation.
- Resolve T1 from `TradePlan.tradingAccountId`, then verify that the canonical
  relation matches `ExecutionIntent.brokerAccountId`.
- Resolve `ExecutionMode` from the linked BrokerAccount.
- Introduce a neutral application Risk Facts provider/router boundary.
- Preserve Broker Service/provider authority for LIVE.
- Build PAPER account and position facts from Trading Core local state.
- Use Market Data only for current prices and valuation/conversion inputs.
- Reuse exact assigned RiskProfile ID and semantic version.
- Propagate completeness, timestamps, freshness metadata, and provenance.
- Fail closed when required facts or configuration are unavailable or
  inconsistent.
- Persist the financial Account ID in T1 evaluation records.
- Prove distinct Account and BrokerAccount IDs through integration tests.

## Out of Scope

- New Risk Domain rules or changes to RiskEngine interpretation.
- New PAPER fee, slippage, pending-order, or execution semantics.
- New PAPER margin semantics unless separately approved.
- PAPER exit, close, position-query, or realized-PnL redesign.
- Universal Position aggregate.
- Broker Service or Market Data Service redesign.
- Frontend changes.
- RiskProfile authoring, default policy creation, or inheritance.
- Distributed transactions.
- Full idempotency redesign.
- Post-execution T1 redesign.

## Acceptance Criteria

- [ ] T0 resolves `Command.accountId` as `Account.accountId`.
- [ ] T0 resolves the canonical linked BrokerAccount without owner/provider identity inference.
- [ ] T1 resolves the financial Account through `TradePlan.tradingAccountId`.
- [ ] T1 fails closed when the TradePlan Account does not resolve to the intent BrokerAccount.
- [ ] T0 and T1 work with independently generated Account and BrokerAccount IDs.
- [ ] T0 and T1 persist and use the same financial Account identity.
- [ ] T0 and T1 obtain ExecutionMode from the linked BrokerAccount.
- [ ] T0 and T1 use the exact assigned RiskProfile ID and semantic version.
- [ ] LIVE balances and provider-owned positions come from Broker Service/provider facts.
- [ ] LIVE local PAPER state is never used as authoritative risk state.
- [ ] PAPER balances and positions come from Trading Core local financial and execution state.
- [ ] PAPER risk evaluation makes no Broker Service financial or position-facts call.
- [ ] Market Data supplies current prices and conversion/valuation inputs only.
- [ ] Missing or incomplete Market Data fails closed without authorization.
- [ ] Missing required margin fails closed without substituting zero or defaults.
- [ ] Incomplete or inconsistent PAPER state fails closed.
- [ ] The Risk Domain receives only neutral facts, policy, and proposed-trade data.
- [ ] T1 persists the financial Account ID and never substitutes BrokerAccount.id.
- [ ] Existing LIVE authority, idempotency, provenance, and execution lifecycle behavior remain preserved.

## Failure Semantics

Required missing or inconsistent inputs must remain distinguishable where
practical and must prevent authorization:

```text
missing Account or canonical relation -> mapping/domain failure
missing configuration/profile        -> configuration unavailable
invalid/incomplete profile           -> configuration invalid
missing Market Data                  -> valuation unavailable
missing required margin              -> risk dependency unavailable
inconsistent PAPER state             -> context invalid
```

No missing fact may become zero, empty exposure, unlimited risk, or an approved
evaluation.

## Test Intent

### Unit Tests

- Map LIVE and PAPER source snapshots into neutral Risk Facts.
- Route source selection from ExecutionMode.
- Propagate exact RiskProfile identity and provenance.
- Reject missing, incomplete, stale, or inconsistent facts.
- Preserve Account ID and BrokerAccount ID distinction.

### Application Tests

- T0 uses the canonical relation.
- T1 uses `TradePlan.tradingAccountId`.
- T1 rejects relation mismatch with `intent.brokerAccountId`.
- T1 persists financial Account ID.
- PAPER makes zero BrokerRiskFactsPort calls.
- LIVE preserves BrokerRiskFactsPort/provider calls.
- Missing relation, profile, Market Data, margin, or local PAPER facts fails closed.

### Repository Tests

- Canonical relation and profile assignment survive reload.
- AccountRiskConfiguration remains consistent with the canonical relation.
- Distinct Account and BrokerAccount identifiers resolve correctly.

### Integration Tests

- PAPER T0 uses local Account, AccountBalance, Trade, and execution state.
- PAPER T1 uses the same canonical Account and local facts.
- LIVE T0 and T1 preserve Broker Service/provider facts.
- Distinct-ID execution succeeds without UUID aliasing.
- PAPER makes no Broker Service financial/position-facts call.
- Market Data completeness, freshness metadata, and provenance are preserved.

The current repository has no post-settlement T1 path. Tests must not imply one
unless a separate approved design is introduced.

## Relevant ADRs and Stories

- `docs/architecture/adr/ADR-042.md` - position authority by execution mode
- `docs/architecture/adr/ADR-043.md` - account identity and mode-aware Risk Facts
- `docs/architecture/stories/0039-persisted-account-identity-explicit-paper-provisioning/story.md` - canonical identity and explicit profile assignment

## Architectural Decision

No new ADR is required. ADR-042 and ADR-043 already authorize a neutral,
mode-aware application boundary above the Risk Domain and existing adapters.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved if required.
- [x] Mode-aware neutral Risk Facts boundary implemented.
- [x] T0/T1 canonical identity correction implemented.
- [x] LIVE and PAPER source authority tests pass.
- [x] Complete Trading Core validation passes.
- [ ] Human diff review completed.
- [ ] Code Review approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.
