# Implementation Plan - Story 0125

## Status

Implementation Plan completed. Implementation is not authorized until this plan
receives explicit human approval.

## Planned Outcome

Expose the existing logical broker-account revocation command in the Angular
Accounts page, preserve the existing backend contract, and add focused tests
for the user flow and the distributed revoke callback.

Revocation remains a logical lifecycle operation:

```text
user confirmation
    -> DELETE Trading Core endpoint
    -> Broker Service secret revoke
    -> Trading Core REVOKED callback
    -> refresh active broker-account list
```

No physical database deletion, migration, new endpoint, or broker-provider
change is planned.

## Implementation Steps

### 1. Add Accounts-page revoke state and command

Modify:

- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.ts`

Add the smallest state needed by the existing component pattern:

- a signal holding the currently revoking broker-account ID, or `null`;
- a dedicated revoke feedback signal using the existing success/error feedback
  shape;
- a `revokeBrokerAccount(account)` method.

The method will:

1. return immediately when another revoke is in progress;
2. skip already `REVOKED` accounts;
3. ask for explicit confirmation with `window.confirm`;
4. set the revoking ID and clear previous revoke feedback;
5. call the existing `BrokerAccountService.revoke(account.id)`;
6. on success, clear the busy state, show safe success feedback, and reload the
   broker-account list;
7. on error, clear the busy state and show a safe message stating that the
   revocation could not be confirmed;
8. never display provider payloads, credentials, or internal exception details.

The success path will reload broker accounts only. It will not delete or reload
financial accounts, positions, executions, or history.

### 2. Add the UI action

Modify:

- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.html`

For each non-revoked broker account:

- render an accessible button with a stable test ID;
- include the account display name in the confirmation text;
- disable the button while that account is being revoked;
- show a progress label while the request is active.

Render revoke feedback near the broker-account list with the existing
success/error CSS classes and `role="status"`. Revoked accounts will not expose
an active revoke action and will be omitted from the active broker-account
presentation after the list refresh.

### 3. Preserve and verify the backend command

Do not change the production backend path unless focused tests expose a concrete
contract defect. The existing path already:

- validates the authenticated owner in Broker Service;
- revokes the active secret when present;
- marks the Broker Service connection revoked;
- calls Trading Core with `REVOKED`;
- clears the Trading Core credential reference through `markRevoked`.

Add or extend focused tests to cover the existing contract:

- successful revoke with an active credential invokes the secret revoker and
  sends the `REVOKED` callback;
- revoke without an active credential still sends the lifecycle callback;
- ownership or connection lookup failure prevents secret mutation;
- secret revocation failure prevents a successful callback;
- callback failure is surfaced as an unsuccessful operation and is not reported
  as a confirmed local revoke.

No distributed transaction or new reconciliation subsystem will be introduced.

### 4. Extend Angular tests

Modify:

- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.spec.ts`

Update the broker-account service test double with `revoke`, then cover:

- canceling confirmation does not call the service;
- confirmation calls DELETE through the service;
- success shows safe feedback and reloads broker accounts;
- failure shows error feedback and clears the busy state;
- duplicate clicks do not issue concurrent revoke requests;
- revoked accounts do not render an active revoke action.

Use a test spy for `window.confirm`; do not require browser interaction or
introduce a new dialog dependency.

## Files Expected To Change

- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.ts`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.html`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.spec.ts`
- `broker-service/src/test/java/com/hope/trading/broker_service/connection/application/BrokerCredentialConnectionServiceTest.java`

The Angular `BrokerAccountService` implementation and its existing DELETE test
are expected to remain unchanged.

## Validation Plan

Run the following after implementation:

1. Focused Broker Service revoke tests.
2. Focused Angular Accounts component tests.
3. Existing Angular broker-account service tests.
4. Relevant full module test/build commands according to the repository's
   frontend and Broker Service configuration.
5. `git diff --check`.

Review the complete diff and confirm that pre-existing Broker Service changes,
Story `0121`, and Story `0123` remain untouched.

## Risks and Mitigations

- The operation spans two services. The UI will only remove the account from
  the active presentation after a successful HTTP response; failures will show
  an unconfirmed-revocation message.
- `REVOKED` is terminal in the current domain. The UI will not offer an
  accidental reconnect or un-revoke action.
- LIVE positions remain broker-authoritative. The revoke action will not close
  positions or claim that exposure was removed.
- Browser confirmation is intentionally minimal and dependency-free; the plan
  does not introduce a modal framework for one destructive action.

## Documentation Outcome

The Story and its Repository Analysis already document the logical-revocation
semantics. No additional architecture or runtime documentation is expected
unless implementation changes the existing API contract.

## Approval Boundary

No production or test code has been modified for this plan. Implementation may
begin only after explicit human approval of this Implementation Plan.
