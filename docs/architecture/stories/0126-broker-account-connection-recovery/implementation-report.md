# Implementation Report - Story 0126

## Result

Implemented the approved broker-account connection recovery workflow without
changing broker-account identity, database schema, Gateway routes, provider
adapters, or Market Data.

## Implemented Behavior

- Existing LIVE accounts can be connected, reconnected, or updated from the
  Accounts page through the existing `POST /credentials` command.
- The Broker Service writes a new secret only when no active credential
  reference exists.
- Existing active credentials are replaced through `SecretRotator` only after
  provider validation succeeds.
- Invalid replacement credentials preserve the active secret reference in both
  Broker Service and the Trading Core callback.
- `REVOKED` connections reject connect, rotate, validate, and disconnect before
  provider or secret operations, with an HTTP `409 CONFLICT` response.
- Connected accounts can be explicitly disconnected without revocation.
- The Accounts UI exposes state-aware recovery, update, disconnect, and revoke
  actions, while hiding terminal revoked accounts.
- Recovery and disconnect use busy-state guards, safe feedback, canonical list
  refresh, and sensitive-field clearing.
- Optional passphrase input is now visible in the LIVE credential form.

## Changed Files

### Broker Service

- `broker-service/src/main/java/com/hope/trading/broker_service/connection/application/BrokerCredentialConnectionService.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/connection/application/BrokerConnectionRevokedException.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/connection/api/BrokerCredentialExceptionHandler.java`
- `broker-service/src/test/java/com/hope/trading/broker_service/connection/application/BrokerCredentialConnectionServiceTest.java`

### Angular

- `trading-os-web/src/app/core/services/broker-account.service.ts`
- `trading-os-web/src/app/core/services/broker-account.service.spec.ts`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.ts`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.html`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.scss`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.spec.ts`

### Story Documentation

- `story.md`
- `repository-analysis.md`
- `implementation-plan.md`
- `implementation-report.md`

The pre-existing `docs/architecture/reports/v1-functional-audit.md` was not
modified.

## Independent Review Follow-Up

The independent review identified three issues, all corrected:

- A revoked Trading Core account without a local Broker Service connection is
  rejected before local connection creation, provider validation, or secret
  mutation.
- Recovery, disconnect, and revoke now share a lifecycle busy guard, preventing
  conflicting commands while any lifecycle operation is pending.
- Selecting another account cannot replace an active recovery form and discard
  entered credentials.

Regression coverage was added for the revoked remote-only account, conflicting
lifecycle operations, recovery selection, and failed-recovery field clearing.

## Validation

Passed:

```text
broker-service: mvn -B test             242 tests, 0 failures
trading-core:  mvn -B test             789 tests, 0 failures
trading-os-web: npm run test:ci        463 tests, 0 failures
trading-os-web: npm run build          successful
git diff --check                        successful
```

The focused post-fix suites also passed with 11 Broker Service tests and 40
Angular tests.

The Angular production build reports existing bundle and stylesheet budget
warnings; they do not fail the build.

The Trading Core suite emitted pre-existing integration/scheduled-task warning
logs during successful tests, including unavailable local PostgreSQL and a
scheduled null-clock warning. No Story 0126 test failed because of them.

## Runtime Boundary

No real Kraken credentials were submitted. No production broker lifecycle was
mutated. No Market Data service was restarted, recreated, or modified.

Callback failure remains a known distributed partial-failure risk from Story
0125. This implementation does not claim cross-service atomicity and does not
introduce a reconciliation subsystem.

## Git Boundary

No commit, push, merge, reset, stash, or cleanup operation was performed.
Changes remain in the working tree for human review.
