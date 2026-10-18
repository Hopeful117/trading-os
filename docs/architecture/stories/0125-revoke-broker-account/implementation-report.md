# Implementation Report - Story 0125

## Status

Implementation completed. Human code review and commit remain pending.

## Implemented Outcome

The Accounts page now exposes the existing logical broker-account revocation
command. The flow is:

```text
explicit confirmation
    -> DELETE /api/v1/broker-accounts/{accountId}
    -> refresh broker-account list
    -> hide revoked accounts from the active presentation
```

The existing backend command and its responsibility boundaries were preserved.
No physical account deletion, credential-model change, provider change, or new
API endpoint was introduced. A backend edge case was corrected: a locally
created broker account with no Broker Service connection is now revoked locally
when Broker Service returns `404`, because there is no credential to revoke.

## Changed Files

- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.ts`
  - added revoke busy state and safe feedback state;
  - added confirmation, duplicate-request protection, revoke command, refresh,
    and safe error handling.
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.html`
  - added accessible revoke action for active broker accounts;
  - added progress state and status feedback;
  - excluded `REVOKED` accounts from the active presentation.
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.spec.ts`
  - added confirmation, success, failure, duplicate-click, and revoked-account
    coverage.
- `broker-service/src/test/java/com/hope/trading/broker_service/connection/application/BrokerCredentialConnectionServiceTest.java`
  - added coverage for credential revoke and Trading Core lifecycle callback
    behavior, including failure cases.
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/api/BrokerAccountController.java`
  - handles a missing Broker Service connection by completing the local logical
    revoke for an account that was never connected.
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/domain/BrokerAccount.java`
  - permits `CREATED -> REVOKED` for accounts with no broker credentials.
- Trading Core controller and lifecycle regression tests cover the new edge case.

The following pre-existing changes were not modified by this implementation:

- Kraken fee-schedule implementation and tests;
- Story `0121` artifacts;
- Story `0123` artifacts.

## Validation

Passed:

- Broker Service focused revoke tests.
- Broker Service full Maven suite: `238` tests passed.
- Trading Core targeted revoke, fallback, ownership, and lifecycle tests passed.
- Angular Accounts focused suite: `23` tests passed.
- Angular full suite: `51` test files and `454` tests passed.
- Angular production build passed.
- Angular coverage run: `82.86%` statements, `76.75%` branches,
  `81.14%` functions, `83.26%` lines.
- `git diff --check` passed.

Sonar validation was not completed successfully:

- the global quality scan exceeded the execution window during Market Data
  validation;
- the targeted Broker Service analysis failed in the Sonar Compute Engine;
- the targeted Web analysis completed tests and coverage, but Sonar rejected
  the scan because the required organization/authorization configuration was
  unavailable in the environment.

These Sonar outcomes do not invalidate the passing application tests, but a
Quality Gate result is not available for this implementation.

## Scope Review

- logical revocation only;
- ownership and credential behavior preserved;
- no credentials or provider payloads exposed in UI feedback;
- no changes to positions, executions, history, or broker-side orders;
- no commit, push, merge, or branch integration performed.

## Documentation Outcome

No ADR or runtime documentation change is required because the implementation
uses the existing API contract and preserves the documented responsibility
boundaries.

## Remaining Human Actions

1. Review the complete diff in IntelliJ.
2. Review the Sonar limitations or rerun the scans with valid project
   configuration.
3. Approve the implementation and create the human commit.
