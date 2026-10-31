# Story 0183 - ADR-082 Phase 6 LIVE CFD Authoritative Fact Mapping and Reconciliation

## Metadata

**ID:** `0183`
**Title:** ADR-082 Phase 6 LIVE CFD Authoritative Fact Mapping and Reconciliation
**Status:** Draft
**Related ADR:** `ADR-082`
**Related Stories:** `0148`, `0150`, `0173`, `0182`

## Goal

Expose one provider-neutral, read-only boundary for complete LIVE CFD account
facts and provider positions, then classify those observations against existing
local provider-position linkage without making Trading Core the financial
authority.

## Scope

- Reuse `BrokerAccountSnapshotV2` for provider-neutral account and position facts.
- Map cTrader account equity, used margin, free margin, valuation asset, and
  timestamps from provider fields without local financial recomputation.
- Preserve canonical signed quantity and provider position identity.
- Validate canonical CFD product and financial contract metadata before CFD
  reconciliation.
- Add explicit `MATCHED`, `PROVIDER_ONLY`, `LOCAL_ONLY`, `MISMATCH`, and
  `UNAVAILABLE` reconciliation semantics.
- Reuse existing durable broker-position linkage and read-only synchronization.
- Prove LIVE/PAPER authority separation, completeness/freshness fail-closed
  behavior, and required-margin separation.

## Out of Scope

- LIVE settlement, local LIVE cash or realized-PnL mutation, or a second LIVE
  position ledger.
- CFD risk integration or Risk Domain changes.
- Proposed-order margin calculation changes.
- Cost schedule or financing implementation.
- Compatibility policy changes; `CASH + CFD` and `INVENTORY + CFD` remain
  rejected.
- Provider mutations, execution, close, cancel, or order modification.

## Authority Invariants

```text
LIVE_PROVIDER_AUTHORITY = PRESERVED
PAPER_LOCAL_AUTHORITY = PRESERVED
LIVE_SETTLEMENT = NOT_IMPLEMENTED
RISK_INTEGRATION = NOT_IMPLEMENTED
CASH_PLUS_CFD = STILL_BLOCKED
```

## Acceptance Criteria

- [ ] Complete LIVE account facts remain provider-authoritative through Broker
      Service and Trading Core mapping.
- [ ] Missing account facts, stale/incomplete observations, missing CFD
      classification, and missing contracts fail closed.
- [ ] LONG and SHORT signed canonical quantities and provider references survive
      mapping and reconciliation.
- [ ] Existing provider-position linkage is used for reconciliation; no symbol-only
      identity replaces it.
- [ ] Reconciliation distinguishes all five required outcomes.
- [ ] Incomplete or stale absence is never interpreted as a confirmed close.
- [ ] Current used margin is not substituted for proposed-order required margin,
      and no notional/leverage fallback is introduced.
- [ ] PAPER valuation and settlement remain local and do not call Broker Service
      for financial truth.
- [ ] No provider mutation or local LIVE settlement occurs.
- [ ] Relevant Trading Core and Broker Service tests plus `git diff --check` pass.

## Validation

- Focused provider mapping and cTrader read-only observation tests.
- Focused Trading Core reconciliation and mode-authority tests.
- Full Trading Core tests and Broker Service tests when production Broker
  Service mapping changes.
- Independent review focused on authority inversion, identity, completeness,
  signed quantity, margin semantics, and mutation safety.
