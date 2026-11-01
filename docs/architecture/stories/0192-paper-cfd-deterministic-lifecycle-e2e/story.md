# Story 0192 - PAPER CFD Deterministic Lifecycle E2E

## Metadata

**ID:** `0192`
**Title:** PAPER CFD Deterministic Lifecycle E2E
**Status:** Preparatory Evidence Complete - Blocked by ADR-051/ADR-083
**Related ADRs:** `ADR-082`, `ADR-083`
**Predecessor Stories:** `0181`, `0182`, `0184`, `0190`, `0191`

## Goal

Prove the complete deterministic PAPER CFD decision and accounting lifecycle
from authoritative T0 risk approval through human authorization, independent T1
revalidation, local PAPER execution, CFD settlement, mark-to-market valuation,
full close, realized PnL, and exactly-once replay behavior.

## Current Decision

This Story does not authorize `CASH + CFD`. The repository-wide compatibility
policy remains fail-closed, and the CFD settlement service rejects incompatible
accounts directly. This Story now records preparatory CFD accounting and risk
evidence only; the positive settlement lifecycle remains deferred until the
ADR-083 PAPER margin authority and a separately approved Phase 8 authorization
Story are complete.

## Scope

- Add focused PostgreSQL/Testcontainers integration proof for the production
  PAPER CFD execution path.
- Prove T0 risk approval is persisted before human authorization and execution.
- Prove T1 uses fresh execution-time valuation and blocks execution when T1
  rejects.
- Prove successful PAPER CFD entry creates one open signed exposure and applies
  fees without broker/provider mutation.
- Prove post-entry MTM updates equity without changing realized balance.
- Prove full close realizes deterministic PnL, applies the exit fee, and leaves
  no open exposure.
- Prove repeated execution/settlement evidence is idempotent and creates no
  duplicate economic effect.
- Preserve compatibility rejection and all LIVE/provider authority boundaries.

## Out Of Scope

- Enabling `CASH + CFD`, `INVENTORY + CFD`, or any settlement compatibility.
- Changing Risk Domain, Broker Service, cTrader behavior, or LIVE authority.
- Adding migrations or changing persisted financial contracts.
- Provider mutations, real broker orders, or automatic retry of unsafe writes.
- Frontend work, new execution architecture, or production behavior unrelated
  to the missing lifecycle proof.

## Acceptance Criteria

- A PostgreSQL integration test proves T0 and T1 remain independently persisted
  and auditable for a CFD plan.
- A controlled fresh-T1 rejection proves no broker submission, fill, settlement,
  trade, exposure, balance, or provider mutation occurs.
- The real application path rejects `CASH + CFD` before the CFD settlement seam
  is selected.
- Direct calls to the CFD settlement service also reject incompatible accounts
  before mutation.
- `SettlementCompatibilityPolicy` continues to reject `CASH + CFD` globally.
- Positive CFD settlement, MTM, close, fees, and replay remain deferred until
  ADR-083 implementation and a separately approved Phase 8 authorization Story.
- Risk Domain, Broker Service, LIVE/provider authority, and cTrader safety tests
  remain unchanged and passing.

## Safety Invariants

```text
PAPER_EXECUTION_AUTHORITY = TRADING_CORE_LOCAL_SIMULATION
PAPER_CFD_SETTLEMENT = LOCAL_ACCOUNTING_ONLY
T0_REQUIRED = true
HUMAN_AUTHORIZATION_REQUIRED = true
T1_REQUIRED = true
T1_REJECTION_PREVENTS_EXECUTION = true
CASH_PLUS_CFD = REJECTED
INVENTORY_PLUS_CFD = REJECTED
UNKNOWN_PLUS_CFD = REJECTED
LIVE_AUTHORITY_CHANGED = NO
BROKER_SERVICE_CHANGED = NO
RISK_DOMAIN_CHANGED = NO
CTRADER_PROVIDER_MUTATION = false
EXACTLY_ONCE_ECONOMIC_EFFECT = true
```
