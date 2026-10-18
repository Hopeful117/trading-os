# Story 0127 - Broker Account Cross-Service Reconciliation After Partial Failure

## Metadata

- **ID:** `0127`
- **Title:** Broker Account Cross-Service Reconciliation After Partial Failure
- **Status:** Draft
- **Related Stories:** `0125`, `0126`
- **Related ADRs:** `ADR-024`, `ADR-042`, `ADR-043`

## Goal

Ensure that a successful destructive broker-credential revocation eventually
converges Trading Core and Broker Service to the terminal `REVOKED` state when
the cross-service lifecycle callback fails, times out, or its response is lost.

The mechanism must preserve the irreversible security direction:

```text
credential revocation
    -> Broker Service REVOKED
    -> Trading Core REVOKED
```

It must never restore a revoked credential or transition a revoked account back
to `CONNECTED`.

## Context

Story 0125 introduced the user-facing revoke command and explicitly retained
the distributed callback failure as a residual risk. Story 0126 introduced the
state-aware recovery workflow and preserved `REVOKED` as terminal, but did not
introduce cross-service reconciliation.

The current revoke flow performs a synchronous Broker Service callback to
Trading Core. The current Broker Service implementation uses a local database
transaction for secret and connection state. A callback response can still be
lost after Trading Core commits, leaving Trading Core `REVOKED` while Broker
Service retains its previous connected state after local rollback. The reverse
divergence is also possible when local destructive state is committed before a
callback in a future or external secret-store configuration.

The investigation concluded that best-effort handling is insufficient because
divergence can survive process restart and can affect live execution eligibility.
The minimum required guarantee is controlled durable eventual consistency.

## Problem

The current implementation has no durable lifecycle-notification state, no
restart-safe callback retry, and no idempotent terminal callback handling.

Consequently:

- a callback can be accepted by Trading Core while Broker Service treats the
  request as failed;
- retrying the original revoke can fail on a duplicate terminal callback;
- a process restart loses any in-memory opportunity to retry;
- operators have no durable pending-reconciliation record;
- stale cross-service state can influence broker-account workflows and live
  execution eligibility.

## Scope

- Persist the minimum domain state required to identify a pending terminal
  revocation notification.
- Make the Broker Service destructive local transition durable before callback
  delivery is considered complete.
- Attempt the existing Trading Core `REVOKED` callback after the local commit.
- Retry pending callback delivery after process restart using bounded scheduling
  and existing service infrastructure.
- Make the Trading Core `REVOKED` callback idempotent when the account is
  already `REVOKED` and the incoming credential reference is null.
- Reject callback payloads or commands that would attach credentials or move a
  `REVOKED` account to a usable lifecycle state.
- Persist safe delivery status, retry timing, attempt count, and failure
  diagnostics sufficient for operations.
- Preserve ownership checks, credential isolation, terminal lifecycle rules,
  and existing public API boundaries.
- Add focused tests for commit ordering, callback failure, lost response,
  restart-safe retry, duplicate delivery, duplicate revoke, and no credential
  resurrection.

## Out Of Scope

- Kafka or another message broker.
- A generic transactional outbox framework.
- A Saga framework or distributed transaction coordinator.
- A platform-wide reconciliation framework.
- Bidirectional last-writer-wins synchronization.
- Restoration, reconnection, rotation, or resurrection of a `REVOKED` account.
- Changes to Market Data, Market Intelligence, economic research, News, fees,
  funding, risk models, or execution strategy.
- Redesign of the Angular Accounts experience.
- Multi-broker expansion or provider-specific changes.
- Physical deletion of broker accounts, credentials, or historical records.

## Invariants

- `REVOKED` is terminal in Trading Core and Broker Service.
- A physically revoked or absent credential dominates cached lifecycle state.
- Trading Core never stores credential material.
- Broker Service remains the authority for technical connection and secret
  state.
- Trading Core remains the authority for broker-account identity, ownership,
  and business lifecycle projection.
- Duplicate terminal callback delivery is safe.
- Callback retry never restores credentials.
- Exhausted delivery remains durably visible and is not reported as confirmed
  synchronization.
- User-facing responses do not expose credentials, provider payloads, or
  internal exception details.
- Existing ownership and authorization boundaries remain enforced.

## Acceptance Criteria

- [ ] Given a valid revoke request, Broker Service persists the credential
      revocation, terminal connection state, and pending Trading Core
      notification target in a local durable transaction.
- [ ] Given a callback failure before delivery succeeds, the pending terminal
      notification remains durable after the request fails.
- [ ] Given a Broker Service restart after the local revoke commit, the pending
      notification is discovered and retried without user intervention.
- [ ] Given a callback response is lost after Trading Core commits `REVOKED`, a
      retry converges Broker Service and Trading Core without revoking or
      writing a new credential.
- [ ] Given Trading Core receives a duplicate `REVOKED` callback with a null
      credential reference, it accepts the callback idempotently.
- [ ] Given a callback attempts to move a `REVOKED` account to a usable state or
      attach a credential reference, the request is rejected deterministically.
- [ ] Given a duplicate revoke request, the result is deterministic and cannot
      resurrect or replace a revoked credential.
- [ ] Given callback delivery repeatedly fails, the system retains durable
      pending/failed state, bounded retry metadata, and safe operator-visible
      diagnostics.
- [ ] Given stale Trading Core state, no reconciliation path restores a
      credential or transitions `REVOKED` to `CONNECTED`.
- [ ] Existing successful revoke, ownership, credential-failure, recovery, and
      terminal-state behavior remains unchanged outside the reconciliation
      correction.
- [ ] Focused Broker Service and Trading Core tests pass, including the
      post-commit failure and duplicate-delivery scenarios.
- [ ] No Market Data service, acquisition process, database, credential, or
      unrelated runtime state is modified during implementation validation.

## Constraints

- Use the smallest domain-specific durable mechanism; do not introduce Kafka,
  a generic outbox, Saga coordination, or distributed ACID transactions.
- Preserve ADR-024 ownership and credential-isolation rules.
- Preserve ADR-042 and ADR-043 account identity and execution-mode boundaries.
- Do not use timestamps or last-writer-wins as authority for destructive state.
- Do not make callback success depend on Angular availability.
- Do not treat an HTTP success response as proof that both services committed
  unless the durable notification state confirms delivery.
- Do not expose secrets in persistence diagnostics, logs, metrics, or errors.
- Do not modify the ongoing Market Data research evidence window.
- Do not commit, push, merge, reset, stash, or discard unrelated work.

## Expected Implementation Boundary

The implementation should remain bounded to:

- Broker Service lifecycle persistence and callback delivery state.
- Broker Service revoke transaction and post-commit delivery/retry behavior.
- Trading Core terminal callback idempotency and validation.
- Focused Broker Service and Trading Core tests.
- Necessary schema migration and operational configuration for the bounded
  retry mechanism.

No new public user workflow is required by this Story.

## Validation

- Focused Broker Service revoke and reconciliation tests.
- Focused Trading Core callback and terminal lifecycle tests.
- Persistence tests proving pending state survives transaction failure and
  process restart simulation.
- Duplicate callback and duplicate revoke tests.
- Tests proving no credential write, rotation, or resurrection occurs after
  `REVOKED`.
- Relevant Broker Service and Trading Core module suites.
- `git diff --check`.
- Static review confirming no Market Data or unrelated service changes.
- Safe runtime checks only; no real credential mutation, revoke, reconnect,
  trade, or Market Data restart.

## Definition Of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed.
- [ ] Relevant validation executed.
- [ ] Documentation reconciled.
- [ ] Code Review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.
