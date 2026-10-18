# Story 0125 - Revoke Broker Account

## Metadata

**ID:** `0125`
**Title:** Revoke Broker Account
**Status:** Draft
**Related Stories:** `0037`, `0039`
**Related ADRs:** `ADR-042`, `ADR-043`

## Goal

Allow a user to remove a broker account from the Accounts experience through a
safe end-to-end revocation flow.

Revocation means that the account can no longer be used for broker access or
new trading workflows while its durable identity and historical relationships
remain available for audit and referential integrity.

## Context

The repository already contains partial support:

- the Angular `BrokerAccountService` exposes `revoke(accountId)`;
- Trading Core exposes `DELETE /api/v1/broker-accounts/{accountId}`;
- Broker Service can revoke the stored broker credentials.

The current endpoint delegates credential revocation to Broker Service. Broker
Service revokes the secret and calls back Trading Core with `REVOKED` on the
successful path. The Accounts page does not currently expose a user action to
invoke the operation, and the end-to-end callback/failure behavior is not
covered as one user-facing flow.

## Problem

The existing capability is not exposed in the Accounts page. Without a tested
user-facing flow, the user cannot deliberately revoke an account and refresh
the local account view. A failure after credential revocation but before the
Trading Core callback is also a distributed partial-failure case that must not
be hidden.

## Scope

- Add an explicit revoke action for each broker account in the Accounts page.
- Require a clear user confirmation before revocation.
- Verify and preserve the existing end-to-end command: ownership validation,
  credential revocation, and the Broker Service callback marking the local
  `BrokerAccount` as `REVOKED`.
- Preserve the existing broker-account identity and historical data; do not
  physically delete the row.
- Remove or refresh the revoked account from the active Accounts presentation.
- Present deterministic success and error feedback to the user.
- Add focused Trading Core, Broker Service contract, Angular service, and
  Accounts-page tests for the flow and ownership boundary.

## Out of Scope

- Physical deletion of `BrokerAccount`, `Account`, positions, executions, or
  trading history.
- Closing positions, cancelling orders, or changing broker-side positions as a
  consequence of revocation.
- Automatic deletion or reassignment of linked financial accounts.
- Changes to credential encryption, provider integrations, or authentication.
- Reconnecting or un-revoking an account.
- Changes to unrelated account provisioning or synchronization behavior.

## Acceptance Criteria

- [ ] Given an owned broker account displayed in the Accounts page, when the
      user chooses revoke and confirms, then the UI sends
      `DELETE /api/v1/broker-accounts/{accountId}`.
- [ ] Given a revoke request for an account owned by another user, then the
      request is rejected without changing either account or credentials.
- [ ] Given a valid owned account, then Broker Service credential revocation is
      requested and its successful callback marks the local Trading Core
      account `REVOKED`.
- [ ] Given credential revocation fails, then the local account is not reported
      as successfully revoked and the UI shows an error state.
- [ ] If the broker credential is revoked but the local lifecycle transition
      fails, then the operation reports a partial failure for reconciliation and
      does not present the account as fully revoked without local confirmation.
- [ ] Given successful revocation, then the revoked account is not presented as
      an active broker account after refresh.
- [ ] Given a revoked account, then its durable identity and historical
      references remain available and no physical account row is deleted.
- [ ] The UI requires explicit confirmation and does not expose credentials or
      sensitive provider payloads in messages or logs.
- [ ] Existing disconnect, provisioning, synchronization, and authorization
      behavior remains unchanged.
- [ ] Focused backend and frontend tests pass, including success, failure,
      ownership, confirmation, and refresh behavior.

## Constraints

- Preserve the account identity and ownership boundaries from ADR-042 and
  ADR-043.
- Keep Broker Service responsible for broker credentials and Trading Core
  responsible for the user-owned broker-account lifecycle.
- Keep the operation idempotent for an already revoked account where existing
  domain transition rules permit it; otherwise return a deterministic conflict.
- Do not physically delete data required by audit, reconciliation, or foreign
  key relationships.
- Do not commit, push, merge, or modify unrelated pre-existing work.

## Relevant Files

- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/api/BrokerAccountController.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/application/BrokerAccountService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/application/BrokerConnectionCommandClient.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/connection/api/BrokerCredentialController.java`
- `trading-os-web/src/app/core/services/broker-account.service.ts`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.ts`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.html`

## Validation

- Targeted Trading Core tests for ownership, local state transition, and
  credential-command failure.
- Targeted Broker Service contract or controller tests for credential revoke.
- Angular service and Accounts-page tests for DELETE, confirmation, feedback,
  and list refresh.
- Relevant independent module test suites.
- `git diff --check`.

## Definition of Done

- [ ] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [ ] Implementation completed
- [ ] Relevant validation executed
- [ ] Diff reviewed in IntelliJ
- [ ] Code Review approved
- [ ] Engineering Report completed
- [ ] Human commit created
