# Story 0148 - Harden cTrader Read-Only Synchronization Safety

## Metadata

**ID:** `0148`
**Title:** Harden cTrader Read-Only Synchronization Safety
**Status:** Review

> This canonical Story was created at the user's request because the repository
> and accepted ADRs referenced Story 0148 but its Story artifact was missing.
> The scope below is reconciled from ADR-073, ADR-074, commit `7d5e37ab`, the
> current implementation, and the validation evidence.

## Goal

Provide a fail-closed, read-only cTrader synchronization boundary that protects
the provider and the trading workflow from repeated or concurrent snapshot
requests while keeping the human trader in control.

## Context

Trading Core consumes provider-neutral account snapshots assembled by Broker
Service. cTrader account synchronization is a bounded provider operation and
must not become an execution path or an uncontrolled polling loop.

The accepted architecture assigns cTrader authentication, provider transport,
provider mapping, safety controls, and read-only synchronization to Broker
Service. Trading Core remains responsible for deterministic dashboard assembly,
and the web application presents provider state without treating it as an
execution authorization.

Relevant decisions are defined by ADR-073 and ADR-074.

## Problem

Repeated dashboard refreshes can reach cTrader while a snapshot cooldown or
concurrent synchronization lease is active. Returning a generic unavailable
response causes clients to retry at the normal cadence and can create a request
storm. A provider deferral must be machine-readable, propagated through Trading
Core, and represented explicitly in the frontend.

## Scope

- Enforce cTrader snapshot cooldown and concurrent synchronization protection.
- Enforce a bounded global cTrader request budget and execution-disabled safety
  defaults.
- Preserve provider-neutral snapshot and dashboard boundaries.
- Propagate snapshot deferral as the machine-readable
  `BROKER_SNAPSHOT_DEFERRED` contract.
- Make Dashboard and Decision Workspace polling use a shared bounded cadence.
- Apply a longer bounded backoff to deferred snapshots.
- Preserve the last valid snapshot only when it is explicitly marked deferred
  or stale.
- Clear stale/deferred state after a successful refresh.
- Cover normal polling cadence, deferred backoff, recovery, cancellation,
  account isolation, stale rendering, and HTTP contract propagation with tests.

## Out of Scope

- cTrader order, amend, cancel, close, SL/TP, or reconciliation execution.
- Sandbox or live provider validation requiring external credentials.
- FTMO rules, risk formula changes, or autonomous trading decisions.
- Market Data authorization or market-data synchronization.
- Changes to Kraken behavior.
- Commit, push, merge, or branch cleanup by the coding agent.

## Acceptance Criteria

- [x] cTrader snapshot requests are protected by a persisted cooldown and
      concurrent synchronization lease.
- [x] The cTrader execution capability remains disabled by default and the
      safety guard remains fail-closed.
- [x] Broker Service exposes snapshot deferral as HTTP `409` with
      `code=BROKER_SNAPSHOT_DEFERRED` without classifying unrelated safety or
      authentication failures as deferrals.
- [x] Trading Core recognizes the machine-readable broker contract and exposes
      the same provider-neutral dashboard error contract.
- [x] Dashboard and Decision Workspace use bounded polling and do not retry a
      deferred snapshot at the normal five-second cadence.
- [x] A deferred or stale snapshot is visibly represented and is never shown as
      current authoritative data without an explicit state indicator.
- [x] A successful refresh clears stale/deferred state and replaces the prior
      snapshot.
- [x] Targeted frontend, Broker Service, and Trading Core tests pass.
- [x] The production frontend build and relevant backend builds pass.
- [x] Runtime validation confirms the `409` contract, deferred UI state, and
      approximately sixty-second retry cadence without cTrader order writes.
- [ ] Sandbox cTrader validation is completed.
- [ ] Human review, commit, and integration are completed.

## Constraints

- Preserve Broker Service, Trading Core, and Risk Domain responsibility
  boundaries.
- Keep cTrader DTOs, credentials, transport details, and provider errors inside
  Broker Service.
- Keep risk and execution decisions deterministic and human-authorized.
- Never treat a deferred or stale provider snapshot as fresh authority.
- Never expose credentials or tokens through logs, frontend state, or public
  contracts.
- Keep execution disabled during this read-only validation scope.

## Relevant ADRs

- `docs/architecture/adr/ADR-073-ctrader-authentication-and-account-binding.md`
- `docs/architecture/adr/ADR-074-ctrader-authoritative-read-only-synchronization.md`

## Relevant Modules

- `broker-service`
- `trading-core`
- `trading-os-web`

## Validation

- Targeted Broker Service Maven tests for safety and error classification.
- Targeted Trading Core Maven tests for snapshot propagation and HTTP response
  serialization.
- Targeted Angular tests for polling, stale/deferred state, recovery, and
  account switching.
- Angular production build.
- Docker image builds for Broker Service, Trading Core, and Trading OS Web.
- Local runtime validation through Gateway for Dashboard and Decision Workspace.
- Confirm no cTrader execution or order-write operation is triggered.
- Sandbox cTrader validation remains pending because the configured environment
  is live and no separate demo binding or demo credentials are available.

## Definition of Done

- [x] Repository context reconciled with ADR-073 and ADR-074.
- [x] Implementation completed.
- [x] Relevant targeted validation executed.
- [x] Independent code review completed with no material findings.
- [x] Runtime evidence captured for the local deployed flow.
- [ ] Sandbox cTrader validation completed or explicitly waived by the owner.
- [ ] Diff reviewed and committed by the human engineer.
- [ ] Engineering closure report completed.
