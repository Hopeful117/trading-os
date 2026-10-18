# Repository Analysis - Story 0125

## Status

Repository Analysis completed. Implementation is not authorized until this
analysis and the subsequent Implementation Plan receive explicit human
approval.

## Sources and Authority

- The current repository is authoritative for source code, tests, Git state, and
  current implementation behavior.
- `ADR-042` governs the distinction between LIVE broker authority and PAPER
  Trading Core authority.
- `ADR-043` governs the independent `Account` financial identity and
  `BrokerAccount` execution/routing identity.
- Story `0039` establishes the durable account identity and ownership boundary;
  this Story must not physically delete the broker-account identity or linked
  financial history.
- DevLog was queried and returned `429 insufficient_quota`; no conclusion in
  this analysis relies on unavailable DevLog context.
- Obsidian was not consulted because the repository and accepted ADRs provide
  the required authoritative context.

## Git State

- Branch: `main`.
- HEAD and `origin/main`: merge commit `aa523a88` for Story `0124`.
- Pre-existing modified files were preserved:
  - `broker-service/src/main/java/com/hope/trading/broker_service/broker/infrastructure/provider/kraken/capability/KrakenFeeScheduleCapability.java`
  - `broker-service/src/test/java/com/hope/trading/broker_service/broker/KrakenFeeScheduleCapabilityTest.java`
- Pre-existing untracked Story artifacts were preserved:
  - Story `0121` directory;
  - Story `0123` directory.
- Story `0125` currently contains only its Story and this Repository Analysis.
- No application code, migration, or database data was modified during this
  analysis.

## Current End-to-End Flow

The existing revoke path is:

```text
Angular Accounts page
    -> BrokerAccountService.revoke(accountId)
    -> DELETE /api/v1/broker-accounts/{accountId}
    -> Trading Core BrokerAccountController
    -> BrokerConnectionCommandClient
    -> DELETE /api/v1/broker-accounts/{accountId}/credentials
    -> Broker Service BrokerCredentialConnectionService.revoke
    -> secret revocation
    -> Broker Service callback with REVOKED
    -> Trading Core status callback
    -> BrokerAccount.markRevoked
```

The backend command already exists. `BrokerAccountController.revoke` delegates
to Broker Service and returns `204 No Content`. Broker Service validates the
caller and account ownership, revokes the active secret when present, marks its
connection revoked, and calls Trading Core with `REVOKED`. The Trading Core
callback eventually clears the local credential reference through
`BrokerAccount.markRevoked`.

The Angular service already exposes the DELETE call and has a unit test for its
HTTP method and URL. The Accounts page template does not currently render a
revoke action, and the Accounts component does not currently call the method.

## Domain and Ownership Findings

### BrokerAccount Identity

`BrokerAccount` is an execution/routing identity with its own UUID, owner,
provider, execution mode, connection status, credential reference, and
timestamps. It is not the same identity as the financial `Account`.

The domain already models `REVOKED` as terminal:

- `CONNECTED` and `DISCONNECTED` can transition to `REVOKED`;
- `BrokerAccount.markRevoked` clears `credentialReference`;
- `REVOKED` has no outgoing lifecycle transitions.

This supports logical revocation and preservation of the row instead of
physical deletion.

### Ownership

Trading Core resolves the request principal and delegates the command to Broker
Service with the authenticated authorization header. Broker Service calls its
Trading Core ownership lookup and resolves the local connection by
`brokerAccountId` and owner. Existing ownership tests cover the service-level
boundary, but the complete revoke path is not covered end to end.

### Financial State

The Story must not delete or reassign a linked financial `Account`, PAPER
trades, positions, executions, or historical records. ADR-042 also means that
revoking a LIVE broker credential does not automatically close provider-owned
positions. The UI and API should communicate revocation, not exposure closure.

## Current Gaps

### Frontend Gap

`trading-os-web/src/app/features/accounts/pages/accounts/accounts.html` renders
broker-account metadata but has no revoke control. `accounts.ts` has no revoke
state, confirmation flow, error feedback, or post-success reload.

The existing component uses imperative action methods with signals and
Observables, so the smallest consistent implementation is an explicit action
method with a busy guard, confirmation, service call, feedback, and list
refresh. It should not introduce a new state-management abstraction.

### Backend Test Gap

`BrokerAccountControllerTest.revokeReturns204` verifies only that Trading Core
delegates the DELETE command to its Feign client. It does not verify the
Broker Service callback or a successful local `REVOKED` transition.

`BrokerCredentialConnectionServiceTest` currently covers credential validation
and rotation, but not revoke success, absent credentials, ownership failure,
secret-revocation failure, callback failure, or partial failure semantics.

`BrokerAccountServiceOwnershipTest` and domain lifecycle tests cover ownership
and `markRevoked` behavior separately. These tests should be reused and
extended only where necessary; no new abstraction is justified by the current
scope.

### Frontend Test Gap

`BrokerAccountService` already has a focused DELETE test. `Accounts` tests cover
initial loading, creation, synchronization, and feedback, but the test double
does not expose `revoke`, and no component test covers confirmation, success,
failure, busy-state protection, or refresh after revocation.

## Distributed Failure Boundary

Credential revocation and Trading Core lifecycle notification are separate
service operations. There is no distributed transaction across Broker Service
and Trading Core.

The current successful ordering is safe for the normal path:

1. validate ownership;
2. revoke the secret;
3. mark the Broker Service connection revoked;
4. callback Trading Core with `REVOKED`.

If the callback fails after secret revocation, the system can temporarily have
credentials revoked remotely while the local Trading Core status is stale. The
Story should make this failure visible and testable, but it must not invent a
new reconciliation subsystem or claim atomicity that the architecture does not
provide.

The UI should treat a non-success HTTP response as failure and reload only
after a successful response. It must not display credentials, provider error
payloads, or internal callback details.

## Recommended Implementation Boundary

The likely implementation surface is:

- `trading-os-web/.../features/accounts/pages/accounts/accounts.ts` for revoke
  state, confirmation, feedback, and reload;
- `trading-os-web/.../features/accounts/pages/accounts/accounts.html` for the
  explicit action and accessible confirmation affordance;
- the Accounts component test and its broker-account service test double;
- `BrokerCredentialConnectionServiceTest` for the existing backend revoke
  success/failure contract if the approved plan requires backend coverage;
- `BrokerAccountControllerTest` only for any response/error behavior that is
  changed by implementation.

No database migration, physical deletion, Broker provider change, Gateway
route change, Trading Core domain redesign, or new API endpoint is currently
justified. The existing gateway exposure and DELETE contract should be reused.

## Implementation Risks and Decisions for the Plan

The Implementation Plan must make these details explicit:

- whether confirmation uses the browser `confirm` primitive or an existing
  project dialog pattern;
- whether revoked accounts remain visible with a `REVOKED` status or are
  filtered from the active list after refresh;
- the exact user-facing message for a callback/partial failure;
- whether the action is available for PAPER, LIVE, disconnected, and already
  revoked accounts;
- whether repeated revoke calls are treated as a deterministic conflict or
  disabled by the terminal lifecycle state.

The plan must preserve the existing API contract unless repository evidence
requires an explicit error-contract change.

## Validation Baseline

Applicable validation should include:

- focused Trading Core broker-account tests;
- focused Broker Service connection revoke tests;
- Angular broker-account service and Accounts component tests;
- the independent module test suites for any changed modules;
- `git diff --check`.

No implementation validation has been run for Story `0125` because no
implementation has been authorized or performed.

## Conclusion

The repository already contains the secure backend primitive and client service
needed for logical broker-account revocation. The missing product capability is
the Accounts-page action and its user-facing lifecycle. The main engineering
risk is distributed partial failure between secret revocation and the Trading
Core status callback, which must be represented honestly rather than solved by
physical deletion or an invented transaction boundary.

Implementation can proceed with a small frontend-focused change and targeted
backend contract tests after this Repository Analysis and the resulting
Implementation Plan receive explicit human approval.
