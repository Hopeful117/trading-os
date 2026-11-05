# Story 0198 - cTrader FTMO End-to-End Integration Validation

## Metadata

**ID:** `0198`
**Title:** cTrader FTMO End-to-End Integration Validation
**Status:** Repository Analysis approved - awaiting implementation plan approval
**Priority:** P0 - first-trade readiness
**Date:** 2026-11-04
**Related ADRs:** `ADR-074`, `ADR-084`, `ADR-086`
**Related Stories:** `0193`, `0194`, `0195`, `0196`, `0197`

## Goal

Validate the existing cTrader integration end to end against the already
configured FTMO Free Trial account, without sending a real order, changing
financial admissibility, or bypassing T0/T1.

The Story must establish observable evidence for real read-only connectivity,
broker data propagation, account synchronization, user-facing diagnostics,
deterministic risk behavior, simulated execution and reconciliation, and real
order preparation.

## Mandatory Account Boundary

Use only the existing configured and previously validated FTMO Free Trial
cTrader account. Before any real read-only call, resolve the normal Trading OS
connection, identify the durable local broker account, external cTrader account
ID and environment, verify permissions and connection status, and confirm the
provider response identity.

Do not create an account, create a new OAuth connection, change credentials,
use Kraken or PAPER, substitute another cTrader account, or expose secrets.

An account ID found only in a fixture, test, log or historical note is not proof
of the target account identity.

If the target account cannot be identified with certainty, all real provider
calls stop and the report records the blocker.

## Scope

- Phase 0: repository, Story, ADR, configuration and working-tree preflight.
- Phase A: read-only authentication, authorization, identity, environment,
  permissions and connection validation for the existing account.
- Phase B: account, balance, currency, permissions, symbols, metadata,
  positions, orders, PnL, deals and synchronization validation.
- Phase C: Trade Intent creation through the existing Angular/API path without
  order submission.
- Phase D: T0/T1 and Risk Engine behavior with real available facts, preserving
  expected fail-closed equity behavior.
- Phase E: isolated simulated broker execution through the real validation and
  reconciliation services, including ambiguous outcomes and idempotency.
- Phase F: real-account order preparation checks without `ProtoOANewOrderReq`.
- Phase G: Angular account, diagnostics, intent and execution-state validation.
- Phase H: evidence matrix distinguishing `PASS`, `FAIL`, `BLOCKED` and
  `NOT_TESTED`.
- Local, testable fixes for defects directly observed during validation when
  they preserve existing contracts and safety boundaries.

## Out of Scope

- Any real order, position mutation, cancellation, amendment, close or SL/TP
  operation.
- Enabling `CTRADER_SAFETY_EXECUTION_ENABLED`.
- Bypassing T0, T1, human authorization, risk admission or reconciliation.
- Making experimental or unavailable equity admissible.
- Reopening Stories 0193-0197 or changing their contracts and decisions.
- New OAuth credentials, accounts, providers, microservices or private FTMO
  APIs.
- New ADRs about equity or financial semantics.
- Broad UI refactoring or unrelated technical-debt cleanup.

## Safety Invariants

```text
TARGET_ACCOUNT = EXISTING_FTMO_FREE_TRIAL_ONLY
REAL_PROVIDER_MUTATIONS = 0
REAL_ORDER_SUBMISSION = NOT_TESTED
CTRADER_SAFETY_EXECUTION_ENABLED = false
EQUITY_ADMISSION = UNCHANGED
T0_T1_BYPASS = false
AMBIGUOUS_BROKER_OUTCOME = UNKNOWN_AND_RECONCILE
SECRETS_IN_REPORTS_OR_LOGS = false
```

## Acceptance Criteria

- [ ] The exact existing FTMO Free Trial account, external account ID,
      environment, local broker account and permissions are proven without
      exposing credentials; otherwise real validation is explicitly blocked.
- [ ] Read-only cTrader authentication, authorization and account identity
      validation produce per-check `PASS`, `FAIL` or `NOT_TESTED` evidence.
- [ ] Account, symbol, position, order, PnL, deal and synchronization facts are
      classified as available, missing, stale, incoherent or unsupported without
      invented values.
- [ ] The complete transformation path from cTrader adapter through Broker
      Service, Trading Core and Angular is tested where the existing interfaces
      support it.
- [ ] Trade Intent creation is tested without submitting a real order.
- [ ] T0 and T1 preserve independent invariant diagnostics while remaining
      fail-closed for mandatory missing or inadmissible evidence.
- [ ] The existing equity inadmissibility behavior is observed and unchanged.
- [ ] Simulated execution uses the real validation/orchestration boundaries and
      covers accepted, rejected, timeout, unknown, idempotency and
      reconciliation outcomes without fabricated approval.
- [ ] Real-account order preparation is checked read-only; no
      `ProtoOANewOrderReq` is sent.
- [ ] Angular displays account state, connection status, diagnostics and
      execution blocking states through existing components.
- [ ] The final E2E matrix separates real provider validation, simulation,
      local tests, blocked checks and untested real order submission.
- [ ] Any correction is local, testable, documented and does not change policy,
      risk, execution authorization, equity provenance or provider boundaries.

## Required Report

The implementation report must contain:

- Existing Account.
- Real cTrader Connectivity.
- End-to-End Integration.
- Simulated Execution.
- Angular Validation.
- Discovered Defects.
- Remaining Blockers separated into connectivity, functional, financial,
  security and environment limitations.
- First-trade readiness: `READY_FOR_CONTROLLED_EXECUTION`, `PARTIALLY_READY`
  or `NOT_READY`.
- One prioritized next action.

## Constraints

- Use secure application mechanisms for existing credentials and tokens.
- Never print, persist or commit secrets.
- Do not claim real E2E success from fixtures, mocks or unit tests.
- Preserve all unrelated working-tree changes.
- No commit, push or merge without explicit human authorization.
