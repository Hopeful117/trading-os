# Repository Analysis - Story 0126

## Status

Repository Analysis completed. Production implementation is not authorized
until this analysis and the subsequent Implementation Plan receive explicit
human approval.

## Sources And Authority

- The current repository is authoritative for current code, tests, Git state,
  and runtime configuration.
- `docs/architecture/reports/v1-functional-audit.md` identifies the missing
  Accounts recovery workflow and the existing distributed revoke residual risk.
- Story 0125 defines revoke as terminal and explicitly excludes reconnecting or
  un-revoking an account.
- ADR-024 defines write-only credentials, validation-before-activation, secret
  rotation ports, and the connection lifecycle.
- ADR-042 and ADR-043 preserve the distinction between broker-routing identity
  and financial-account identity.
- DevLog applicability was attempted and returned HTTP 429 because the
  configured provider had exhausted quota. No conclusion here depends on that
  unavailable context.
- Obsidian was not consulted. Current repository code, Stories, and ADRs are
  sufficient for this analysis.

## Git State

- Branch: `main`.
- HEAD: `c4081ac9e66e04604a6ac808e15b7dccc7a1550b`.
- `origin/main`: `c4081ac9e66e04604a6ac808e15b7dccc7a1550b` after the safe fetch.
- Ahead/behind: 0/0.
- Tracked modifications: none.
- Staged modifications: none.
- Pre-existing untracked file: `docs/architecture/reports/v1-functional-audit.md`.
  It does not overlap Story 0126 implementation files and is preserved.
- No application source, database, runtime process, credentials, or Market Data
  state was modified during this analysis.

## Current Ownership And API Boundary

### Trading Core

Trading Core owns `BrokerAccount`, its owner, business connection status,
opaque credential reference metadata, and lifecycle transition validation. It
does not receive credential material.

Relevant code:

- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/domain/BrokerAccount.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/application/BrokerAccountService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/api/BrokerAccountController.java`

### Broker Service

Broker Service owns `BrokerConnection`, provider validation, credential
references, encrypted secret persistence, rotation, revocation, and technical
callbacks to Trading Core.

Relevant code:

- `broker-service/src/main/java/com/hope/trading/broker_service/connection/domain/BrokerConnection.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/connection/application/BrokerCredentialConnectionService.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/connection/api/BrokerCredentialController.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/secret/adapter/persistence/EncryptedDatabaseSecretStore.java`

### Angular

Angular uses the Gateway-backed public routes through
`BrokerAccountService`. The Accounts page currently supports new account
creation, listing, synchronization, and revoke. The service already exposes
`rotate` and `disconnect`, but the page does not call either operation.

Relevant code:

- `trading-os-web/src/app/core/services/broker-account.service.ts`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.ts`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.html`

## Existing API Surface

The existing Gateway routes are sufficient for the intended workflow:

- `POST /api/v1/broker-accounts` creates the business identity.
- `POST /api/v1/broker-accounts/{id}/credentials` validates credentials and
  connects the account.
- `PUT /api/v1/broker-accounts/{id}/credentials` validates and rotates an
  existing active credential.
- `POST /api/v1/broker-accounts/{id}/disconnect` performs a recoverable
  technical disconnect.
- `DELETE /api/v1/broker-accounts/{id}` performs terminal logical revoke.
- `GET /api/v1/broker-accounts` returns canonical business state without
  credentials.

No new public endpoint is justified at analysis time. The missing frontend
operation is an existing-account variant of the already available `POST
/credentials` command, not a new protocol.

## Actual Lifecycle State Machine

The following table combines the current Trading Core domain transitions with
the Broker Service command behavior and callback path.

| Current state | Connect existing account | Disconnect | Rotate credentials | Reconnect | Revoke | Terminal? |
|---|---|---|---|---|---|---|
| `CREATED` | `POST /credentials`; validates then connects; writes initial secret | Rejected by Trading Core domain | Requires Broker Service connection and active reference; not valid for initial state | Same as connect | Allowed locally for never-connected account through Trading Core fallback | No |
| `PENDING_VALIDATION` | Validation command can continue the in-flight lifecycle; not a user recovery state | Rejected by Trading Core domain | Not a valid stable recovery state | Same validation path | Rejected by the current Trading Core lifecycle callback | No |
| `CONNECTED` | `POST /credentials` currently attempts `SecretWriter.write`; an active secret makes this unsafe/blocked; `PUT /credentials` rotates safely | Allowed; retains active credential reference | Validates then `SecretRotator.rotate`; callback returns `CONNECTED` | Existing API semantics are connect/replace, not a separate reconnect command | Allowed and terminal | No |
| `INVALID_CREDENTIALS` | `POST /credentials` validates again; initial-invalid accounts have no active secret and can use initial write | Not allowed by current Trading Core transition table | Safe only if an active reference exists; current path assumes one | Same as connect existing | Allowed | No |
| `INSUFFICIENT_PERMISSIONS` | `POST /credentials` revalidates | Not allowed by current Trading Core transition table | Safe only if an active reference exists | Same as connect existing | Allowed | No |
| `TEMPORARILY_UNAVAILABLE` | `POST /credentials` revalidates | Not allowed by current Trading Core transition table | Valid only if an active reference exists and provider is available | Same as connect existing | Allowed | No |
| `REAUTHENTICATION_REQUIRED` | `POST /credentials` revalidates | Not allowed by current Trading Core transition table | Valid only if an active reference exists | Same as connect existing | Allowed | No |
| `DISCONNECTED` | `POST /credentials` sends `PENDING_VALIDATION` then validates; current `SecretWriter.write` conflicts when the old active secret is retained | Idempotent technical operation is not a normal user action; existing endpoint delegates and domain may reject | `PUT /credentials` safely rotates the active secret; Trading Core's `CONNECTED` callback uses `ensurePending`, so `DISCONNECTED -> PENDING_VALIDATION -> CONNECTED` is accepted | Same as connect existing | Allowed and terminal | No |
| `REVOKED` | Must be rejected; no recovery | Must be rejected | Must be rejected | Must be rejected | Already terminal; existing revoke semantics apply only where allowed | Yes |

### Minimal Missing Backend Semantics

The existing endpoints can support recovery, but two safety corrections are
needed before exposing them:

1. Existing-account `POST /credentials` must use `SecretRotator` when an active
   credential reference exists. Otherwise reconnecting a disconnected or
   recoverable account either conflicts with the active secret or risks an
   orphaned active secret if the storage contract changes.
2. Broker Service must reject `REVOKED` connections before validation or secret
   mutation with the established deterministic error contract. The current
   `connect` and `rotate` service methods do not explicitly guard this state;
   they rely on the later Trading Core callback to fail.

The `DISCONNECTED -> PENDING_VALIDATION -> CONNECTED` lifecycle already exists
in Trading Core. The existing `connect` command sends the pending callback
before provider validation, so it is the smallest safe recovery path once its
secret-write choice is corrected. The `PUT /credentials` rotation path already
uses `ensurePending` during the Trading Core `CONNECTED` callback and remains a
valid safe replacement command for accounts known to have an active reference.

## Credential Replacement Semantics

Current `SecretRotator.rotate` behavior is:

1. validate replacement credentials before persistence;
2. require the current active reference to match the active database secret;
3. persist the new secret as `PENDING`;
4. revoke the old secret and link it to the new reference;
5. activate the new secret;
6. return the new opaque reference;
7. update `BrokerConnection` and callback Trading Core.

The operation is transactional inside Broker Service. Invalid provider
credentials do not call the rotator and preserve the current active reference.
Storage or callback exceptions are not reported as a successful connected
state. A callback failure remains the known cross-service partial-failure risk:
the operation may need reconciliation, but Story 0126 must not introduce a
generic reconciliation subsystem.

## Angular Gap

The current Accounts page has one credential form tied to
`createAndConnect`, which always creates a new LIVE Broker Account first. The
broker list only renders status and revoke. There is no existing-account
selection, credential recovery panel, disconnect action, or state-aware action
visibility.

The smallest UI design is to retain the existing reactive form and add a
recoverable-account mode:

- select one existing non-revoked LIVE account for recovery;
- show a credential form without prefilled secrets;
- submit through an existing-account service method targeting `POST
  /credentials`;
- clear sensitive controls on cancel, success, and failure;
- refresh `list()` after successful connect, rotate, or disconnect;
- show disconnect as recoverable and revoke as terminal/destructive;
- disable actions while the relevant request is pending.

No new state-management abstraction or modal dependency is required by current
repository conventions.

## Partial Failure Boundary

The normal recovery sequence is:

```text
explicit Angular action
    -> Broker Service validates credentials
    -> secret write or safe rotation
    -> BrokerConnection CONNECTED
    -> Trading Core CONNECTED callback
    -> Angular GET canonical broker-account list
```

If Broker Service local persistence succeeds but the Trading Core callback
fails, the user-facing request must fail or be reported as unconfirmed. The UI
must not show a locally invented `CONNECTED` state. The existing residual
reconciliation gap from Story 0125 remains separate and is not expanded here.

## Backend Validation And Ownership

- Broker Service credential commands validate the user-owned Trading Core
  account through `findOwned` and resolve the local `BrokerConnection` by
  account and owner.
- Angular visibility is not authorization; illegal transitions must be rejected
  server-side.
- `REVOKED` must fail before provider validation and secret mutation.
- The credential DTOs are write-only and the connection response contains only
  status, opaque metadata, permissions, and timestamps.

## Proposed Implementation Boundary

Expected production/test files, subject to plan approval:

- Broker Service connection domain/application/exception handler and focused
  connection tests for safe reconnect and terminal revoke rejection.
- Trading Core lifecycle tests only if a transition callback correction is
  required; no domain redesign is currently justified.
- Angular `BrokerAccountService`, Accounts component/template/styles, and focused
  service/component tests.
- Story 0126 documentation artifacts.

No changes are proposed to `market-data`, Market Intelligence evidence,
research lifecycle, Gateway routes, database migrations, or provider adapters.

## Required Implementation Plan Decisions

The next plan must specify:

1. whether existing-account recovery uses `POST /credentials` exclusively or
   also targets `PUT /credentials` for selected states;
2. the exact exception and HTTP status for `REVOKED` recovery attempts;
3. whether failed recovery from `DISCONNECTED` preserves `DISCONNECTED` or
   records a recoverable validation-failure status while retaining the old
   secret;
4. how the Angular form selects an existing account without retaining secrets;
5. the exact refresh and error behavior for disconnect, recovery, and revoke;
6. the callback-failure tests and the explicit non-goal of reconciliation;
7. the exact commands for Angular and affected Maven validation.

## Validation Baseline

Before implementation, the current relevant suites were not modified or run
as part of this analysis. The approved plan should run focused tests first, then
the affected full suites:

- `cd broker-service && mvn -B test`;
- `cd trading-core && mvn -B test`;
- the canonical Angular test command from `trading-os-web/package.json`;
- Angular production build;
- `git diff --check`.

No runtime lifecycle validation against the real Kraken account is safe or
required. No Market Data restart or mutation is permitted.

## Conclusion

Story 0126 is implementable without a new endpoint or architecture redesign,
but it is not a frontend-only change. The backend must safely distinguish an
initial secret write from replacement of an existing active secret and must
reject `REVOKED` before mutation. The frontend then needs a state-aware,
explicit existing-account recovery workflow using canonical refresh.

Implementation should begin only after this Repository Analysis and the next
Implementation Plan receive explicit human approval.
