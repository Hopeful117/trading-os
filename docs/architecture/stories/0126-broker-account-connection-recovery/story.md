# Story 0126 - Broker Account Connection Recovery Workflow

## Metadata

- **ID:** `0126`
- **Title:** Broker Account Connection Recovery Workflow
- **Status:** Implementation completed; independent review findings fixed; human commit pending
- **Related Stories:** `0125`, `0037`, `0039`
- **Related ADRs:** `ADR-024`, `ADR-042`, `ADR-043`

## Problem

Trading OS already supports broker credential validation, connection,
disconnection, credential rotation, and logical revoke. The Accounts page does
not expose a complete recovery path for an existing broker account that is
disconnected or has a recoverable authentication problem. The current LIVE
form creates a new `BrokerAccount` before submitting credentials, so using it to
recover an existing account can create an unnecessary second identity.

## Goal

Allow a user to explicitly disconnect and recover an existing recoverable LIVE
Broker Account through the Accounts page by submitting new credentials through
the existing credential command path. The workflow must refresh from canonical
backend state after each successful operation.

## Invariants

- `REVOKED` is terminal and cannot be reconnected, un-revoked, or rotated.
- Trading Core owns the business Broker Account and never stores broker secrets.
- Broker Service owns validation, credential references, and secret persistence.
- Credentials are write-only from the product perspective and are never returned
  to Angular, logged, persisted in browser storage, or placed in query strings.
- Ownership is enforced by the backend for every recovery operation.
- Invalid replacement credentials must not revoke or destroy the previous active
  secret.
- Angular reflects canonical backend state rather than inventing status locally.
- Partial cross-service failures remain explicit and are not reported as fully
  successful recovery.

## Scope

- Add explicit state-aware disconnect and recovery/update-credentials actions to
  the Angular Accounts page.
- Reuse the existing `POST /api/v1/broker-accounts/{accountId}/credentials`
  command for reconnecting an existing account where its semantics are valid.
- Correct the Broker Service credential path so a recoverable account with an
  active secret uses safe rotation rather than creating an orphaned active
  secret.
- Reject credential connection or rotation attempts for `REVOKED` connections
  deterministically.
- Preserve the existing revoke semantics and explicit confirmation.
- Add focused domain, application, controller/service, and Angular behavior
  tests.
- Add Story documentation and implementation evidence.

## Out Of Scope

- Un-revoking or replacing a revoked Broker Account identity.
- A new broker-account model or new distributed reconciliation subsystem.
- Kafka, an outbox, a Saga framework, or a distributed transaction coordinator.
- Provider changes, multi-broker support, trading execution, risk, Market Data,
  News, XOOMAR, Marketaux, funding, fees, research, ML, or LLM work.
- Automatic reconnect without an explicit user action.
- Physical deletion or recreation behind a revoked identity.

## Acceptance Criteria

- [x] The Accounts page exposes actions according to canonical connection state.
- [x] A recoverable existing account opens an explicit credential submission flow
      without creating another Broker Account.
- [x] Valid credentials transition the existing account through validation to
      `CONNECTED` and refresh the canonical list.
- [x] Invalid credentials produce safe user feedback and do not destroy the
      previous active secret or create an orphaned active secret.
- [x] Disconnect remains recoverable and is presented as distinct from revoke.
- [x] `REVOKED` accounts expose no disconnect, reconnect, or rotate action, and
      backend commands reject illegal recovery attempts deterministically.
- [x] Credential fields are cleared after submission and cancellation and are
      never returned or persisted.
- [x] Duplicate network actions are prevented while a command is pending.
- [x] Ownership checks remain enforced for disconnect, recovery, and rotation.
- [x] Partial callback failures are surfaced as unconfirmed/unsuccessful rather
      than reported as fully connected.
- [x] Existing creation, synchronization, revoke, fee schedule, and ownership
      behavior remains unchanged.

## Validation

- Focused Broker Service connection and secret-rotation tests.
- Focused Trading Core lifecycle and ownership tests.
- Angular Broker Account service and Accounts component tests.
- Full Broker Service, Trading Core, and Angular suites as appropriate.
- `git diff --check`.
- Safe runtime checks only; no real credential mutation, revoke, disconnect,
  rotation, trade, or Market Data restart.

## Definition Of Done

- [x] Repository Analysis approved.
- [x] Implementation Plan approved.
- [x] Implementation completed.
- [x] Documentation reconciled.
- [x] Relevant validation executed.
- [x] Code Review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.
