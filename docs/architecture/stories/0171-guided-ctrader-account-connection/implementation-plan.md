# Story 0171 - Implementation Plan

## Status

`IMPLEMENTATION PLAN - HUMAN APPROVAL REQUIRED`

This plan is based on the approved Repository Analysis, `ADR-073`, `ADR-074`,
`ADR-077`, and the current repository state. It does not authorize
implementation until explicitly approved.

## Outcome

Replace the current manual cTrader connection procedure with a guided Accounts
workflow:

```text
Accounts UI
    -> create/reuse local cTrader BrokerAccount
    -> start Broker Service OAuth
    -> provider callback
    -> poll safe authorization state
    -> explicitly select discovered account
    -> bind external account
    -> provision Trading Core account
    -> optionally/explicitly authorize Market Data
```

The Broker Service and Market Data authorization stages remain separate. The
frontend orchestrates them but never receives or transfers token material.

## Preconditions

Implementation may begin only after:

- explicit human approval of this Implementation Plan;
- current branch and dirty worktree are revalidated;
- the existing unrelated changes are preserved;
- the exact current broker-account creation/provisioning contracts are verified;
- no real cTrader credentials are added to code, tests or artifacts.

## 1. Backend Contract Audit and Minimal Safe Extensions

Audit the existing cTrader authentication, broker-account provisioning and
gateway routes before changing contracts.

Reuse the existing endpoints when their semantics are sufficient:

- create/reuse local broker account;
- start cTrader authorization;
- read authorization attempt status;
- bind one discovered external account;
- provision Trading Core account;
- start and complete the separate Market Data authorization.

Add only missing provider-neutral workflow metadata or endpoints. Any new
contract must expose only:

- local account ID;
- authorization attempt ID;
- sanitized lifecycle state;
- discovered external account identifiers and safe display metadata;
- expiry and actionable safe messages.

Never expose authorization codes, access tokens, refresh tokens, client secrets,
raw cTrader payloads or provider transport details.

Likely backend areas, subject to audit:

- `broker-service/.../ctrader/authentication/`
- `broker-service/.../connection/`
- `trading-core/.../brokeraccount/`
- `market-data/.../ctrader/authentication/`
- `gateway/.../GatewayRouteConfig.java`

## 2. Broker OAuth Workflow Completion

Implement a web-compatible flow around the existing server-correlated attempt:

1. The authenticated UI creates or selects the local cTrader BrokerAccount.
2. The UI calls Broker Service initiation and receives an authorization URL and
   attempt ID.
3. The UI opens the provider authorization URL in a controlled popup or new
   window without placing secrets in browser storage.
4. The callback consumes the server-side state exactly once.
5. The UI polls the owner-scoped attempt status using the non-secret attempt ID.
6. `ACCOUNT_SELECTION_REQUIRED` displays discovered accounts.
7. The UI submits the selected external account ID to the existing binding
   endpoint.

Callback completion must be safe on reload. Replaying the callback must not
exchange the code twice, rotate state unexpectedly or create a second binding.
The implementation should prefer a safe completion page/window-close signal or
an equivalent frontend return path over displaying raw JSON in the provider
popup, while preserving the existing server-side correlation.

## 3. Account Provisioning Continuation

After a successful cTrader binding:

- refresh the broker-account state;
- show that authentication and binding are complete;
- offer or perform the existing Trading Core provisioning action according to
  the current backend contract;
- make repeated provisioning idempotent;
- distinguish provider connection success from Trading Core account creation
  failure.

The UI must not report Risk readiness, market-data readiness or execution
readiness merely because binding succeeded.

## 4. Separate Market Data Authorization Stage

Add a guided Market Data step after Broker binding/provisioning, using the
existing Market Data authorization boundary.

The step must:

- call the Market Data authorization start endpoint through an authenticated
  gateway route where applicable;
- open its provider authorization separately;
- display completion, expiry, denial and unavailable states;
- never pass Broker Service tokens to Market Data;
- never claim that Broker binding automatically authorizes Market Data;
- expose whether Market Data is required for the current user action or may be
  deferred.

If the existing Market Data configuration is service/account scoped rather than
user/account scoped, preserve that behavior and make the UI state explicit
instead of inventing a new account association model in this Story.

## 5. Frontend Accounts Workflow

Extend the Accounts page without changing the existing Kraken workflow:

- provider selector or provider-specific connection action for cTrader;
- cTrader connection step indicator;
- progress states for authorization, discovery, selection, binding,
  provisioning and Market Data;
- explicit account-selection list;
- retry, cancel and expired-attempt handling;
- safe feedback for denied authorization, provider outage, invalid state and
  unavailable credentials;
- refresh-safe state restoration using non-secret attempt/account IDs only.

Likely frontend areas:

- `trading-os-web/src/app/core/services/broker-account.service.ts`
- new or extended provider-neutral cTrader connection models/services;
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.ts`;
- `accounts.html` and associated styles/tests.

Do not put cTrader protocol DTOs or tokens in frontend models.

## 6. Gateway and API Boundary Verification

Verify that the gateway forwards only the intended routes:

- authenticated Broker authorization initiation/status/binding routes;
- unauthenticated provider callback route with server-side state validation;
- Market Data authorization start/callback routes according to its existing
  service boundary.

Do not make internal token, secret or discovery endpoints public. Add route
tests where the current gateway test conventions support them.

## 7. Failure, Idempotency and Security Rules

Cover these cases explicitly:

- callback denial;
- missing, invalid, expired or replayed state;
- callback reload after successful exchange;
- zero discovered accounts;
- multiple discovered accounts;
- selected account no longer authorized;
- repeated binding of the same account;
- attempted binding of a different account;
- provisioning repeated after success;
- Market Data authorization denied or unavailable;
- service restart between initiation and callback where current persistence
  supports it.

All failures remain fail-closed and provider-neutral at public boundaries.

## 8. Tests and Validation

Backend:

- cTrader initiation, callback and status contract tests;
- explicit account-selection and binding tests;
- callback replay/idempotency tests;
- broker-account provisioning idempotency tests;
- Market Data authorization lifecycle tests;
- gateway route/security tests.

Frontend:

- provider selection and cTrader start-flow tests;
- polling and terminal-state tests;
- explicit account selection tests;
- callback reload/replay-safe UI tests;
- provisioning continuation and failure feedback tests;
- Market Data separate-stage tests;
- existing Kraken regression tests.

Runtime:

- manually validate one cTrader account connection with human-provided
  credentials;
- verify discovered-account selection and binding;
- verify Trading Core provisioning;
- verify the separate Market Data step;
- verify no order is submitted and no token is visible in browser/API/logs.

Expected commands will be selected after the implementation audit, then
reported explicitly. At minimum, run affected Maven tests, Angular tests/build,
and `git diff --check`.

## 9. Documentation Reconciliation

Update only the canonical documentation required by the final behavior:

- Story implementation report;
- relevant cTrader connection/setup instructions if they exist;
- ADR amendment only if implementation requires changing the separate-token
  boundary or automatic account-selection semantics.

Do not amend ADRs for a frontend orchestration change that preserves their
boundaries.

## Human Approval Gate

This Implementation Plan is complete and requires explicit human approval
before implementation may begin.
