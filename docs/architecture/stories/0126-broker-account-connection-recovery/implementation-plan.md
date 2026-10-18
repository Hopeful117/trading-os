# Implementation Plan - Story 0126

## Status

Implementation Plan completed. Production implementation is not authorized
until this plan receives explicit human approval.

## Planned Outcome

Complete the existing broker connection lifecycle with the smallest coherent
change:

```text
existing BrokerAccount
    -> explicit Accounts action
    -> existing POST /credentials command
    -> provider validation
    -> initial secret write or safe secret rotation
    -> Trading Core pending/connected callbacks
    -> canonical broker-account refresh
```

Disconnect remains a recoverable technical operation. Revoke remains a
terminal logical/security operation. No new public endpoint, broker-account
identity, migration, provider integration, or reconciliation subsystem is
planned.

## Decisions

### 1. Recovery API

Add an Angular `connectExisting(accountId, credentials)` method that calls the
existing:

```text
POST /api/v1/broker-accounts/{accountId}/credentials
```

The existing-account flow will use this command for `CREATED`, disconnected,
invalid-credential, insufficient-permission, temporary-unavailability,
reauthentication-required, and connected/update-credentials states.

The existing `PUT /credentials` method remains available and tested as the
explicit active-reference rotation contract, but the new Accounts recovery
workflow will use `POST /credentials` so the backend can safely select initial
write versus replacement based on the actual active secret.

### 2. Broker Service secret selection

In `BrokerCredentialConnectionService.connect`:

- validate the owned Trading Core account and local connection first;
- reject `REVOKED` before setting pending, validating provider credentials, or
  mutating secrets;
- send the existing `PENDING_VALIDATION` callback before provider validation;
- preserve the existing no-write behavior for invalid credentials;
- use `SecretWriter.write` only when no active credential reference exists;
- use `SecretRotator.rotate` when an active credential reference exists;
- update the connection with the returned opaque reference and callback
  `CONNECTED` only after successful validation and secret persistence.

This preserves old-secret safety for disconnected/recoverable accounts and
prevents orphaned active secrets.

### 3. Terminal-state error contract

Add a focused Broker Service application exception for a revoked connection and
map it to `409 CONFLICT` through `BrokerCredentialExceptionHandler`.

Apply the terminal guard to:

- `connect`;
- `rotate`;
- `disconnect`.

The service will not expose whether a credential exists on a revoked account,
and no provider validation will run after the guard.

If `rotate` is invoked without an active credential reference, preserve the
existing conflict semantics through `SecretRevokedException` rather than
allowing a null-reference failure.

### 4. Trading Core

No production Trading Core API or domain redesign is planned. Existing
`ensurePending` callback behavior already supports:

```text
DISCONNECTED -> PENDING_VALIDATION -> CONNECTED
```

Add only focused regression tests if implementation exposes a missing lifecycle
case. Existing `REVOKED` terminal transition rules remain unchanged.

### 5. Angular interaction

Reuse the current Accounts reactive form and component style rather than adding
a modal dependency or a second broker-account model.

Add component state for:

- selected recovery account;
- recovery/reconnect busy state;
- disconnect busy account ID;
- recovery and disconnect feedback.

When an existing account enters recovery mode:

- display the selected account identity and current status;
- hide or disable create-only fields such as display name, execution mode, and
  initial capital;
- display empty API key, API secret, and optional passphrase fields;
- submit only credential material to `connectExisting`;
- allow cancellation without a network request;
- clear all sensitive controls on cancel, success, and failure.

The existing create flow remains unchanged for new LIVE and PAPER accounts.

### 6. State-aware actions

The broker list will expose actions as follows:

| State | Recovery/update | Disconnect | Revoke |
|---|---|---|---|
| `CREATED` | `Connecter` | No | Yes |
| `CONNECTED` | `Mettre à jour les credentials` | `Déconnecter` | Yes |
| `DISCONNECTED` | `Reconnecter` | No | Yes |
| `INVALID_CREDENTIALS` | `Mettre à jour les credentials` | No | Yes |
| `INSUFFICIENT_PERMISSIONS` | `Mettre à jour les credentials` | No | Yes |
| `TEMPORARILY_UNAVAILABLE` | `Réessayer la connexion` | No | Yes |
| `REAUTHENTICATION_REQUIRED` | `Réauthentifier` | No | Yes |
| `PENDING_VALIDATION` | No duplicate action | No | No normal action |
| `REVOKED` | No | No | No |

The exact rendered labels may remain French and follow existing UI language.
The backend remains authoritative regardless of visibility.

Disconnect will require explicit confirmation explaining that the broker
account remains registered and can be recovered. Revoke will retain the current
stronger confirmation and terminal wording.

### 7. Canonical refresh

After successful existing-account connection, rotation, or disconnect:

- clear busy state;
- clear sensitive form fields;
- clear or close the action panel;
- call `loadBrokerAccounts()`;
- use the refreshed account list as the only displayed status source.

No optimistic status mutation will be added.

## Production Files Expected To Change

### Broker Service

- `broker-service/src/main/java/com/hope/trading/broker_service/connection/application/BrokerCredentialConnectionService.java`
- new focused terminal-state exception in the connection application package;
- `broker-service/src/main/java/com/hope/trading/broker_service/connection/api/BrokerCredentialExceptionHandler.java`

### Trading Core

- No production file expected unless a focused test proves an existing callback
  transition gap.

### Angular

- `trading-os-web/src/app/core/services/broker-account.service.ts`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.ts`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.html`
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.scss` only
  for bounded action/recovery-panel styling.

### Documentation

- Story 0126 artifacts:
  - `story.md`;
  - `repository-analysis.md`;
  - this `implementation-plan.md`;
  - `implementation-report.md` after implementation.

No ADR, Gateway route, Market Data, provider, or runtime configuration change
is expected.

## Tests To Add Or Update

### Broker Service

Extend `BrokerCredentialConnectionServiceTest` to cover:

- valid existing connection with active reference uses `SecretRotator`, not
  `SecretWriter`;
- valid initial connection without active reference uses `SecretWriter`;
- invalid replacement credentials do not call either writer or rotator;
- revoked connection rejects connect before provider validation or secret
  mutation;
- revoked connection rejects rotate and disconnect;
- callback failure is not returned as successful recovery;
- ownership failure prevents validation and secret mutation.

Add handler/controller coverage for the deterministic `409 CONFLICT` response
if the project test pattern requires it.

### Trading Core

Run existing broker-account lifecycle, ownership, controller, and callback tests.
Add only a regression test if the current callback path fails to preserve
`DISCONNECTED -> PENDING_VALIDATION -> CONNECTED`.

### Angular

Extend `BrokerAccountService` tests for `connectExisting` and retain existing
`rotate`, `disconnect`, and `revoke` coverage.

Extend `Accounts` behavior tests for:

- action visibility by status;
- recovery uses the existing account ID and does not call `createAndConnect`;
- recovery cancellation does not send credentials;
- successful recovery refreshes the canonical list;
- failed recovery shows safe feedback and clears sensitive fields;
- duplicate recovery submission is prevented;
- disconnect confirmation, busy state, success refresh, and failure feedback;
- revoked accounts expose no recovery, disconnect, or revoke action;
- credential fields are empty after cancellation and success.

## Validation Plan

Run focused checks first:

```bash
cd broker-service && mvn -B -Dtest=BrokerCredentialConnectionServiceTest test
```

```bash
cd ../trading-core && mvn -B -Dtest=BrokerAccountLifecycleRegressionTest,BrokerAccountServiceOwnershipTest,BrokerAccountControllerTest test
```

```bash
cd ../trading-os-web && npm run test:ci -- --include='**/accounts.spec.ts' --include='**/broker-account.service.spec.ts'
```

Then run affected module validation:

```bash
cd broker-service && mvn -B test
```

```bash
cd ../trading-core && mvn -B test
```

```bash
cd ../trading-os-web && npm run check
```

Finally run:

```bash
git diff --check
```

No runtime lifecycle mutation against the real Kraken account will be
performed. No Market Data service restart, container recreation, order-book
operation, research mutation, or credential mutation outside automated tests is
permitted.

## Risks And Mitigations

- **Existing active secret:** select `SecretRotator` rather than `SecretWriter`.
- **Invalid replacement:** validate before secret persistence and preserve the
  current active reference.
- **Revoked account:** reject before provider or secret operations and expose no
  recovery action.
- **Callback failure:** return an unsuccessful/unconfirmed result; do not add a
  reconciliation subsystem or claim atomicity.
- **Double submission:** use component busy signals and backend credential rate
  limiting.
- **Credential leakage:** use password inputs, no prefill/storage/query string,
  and clear controls after every terminal form outcome.
- **Unrelated work:** limit changes to Broker Service connection semantics and
  Angular Accounts workflow; preserve the pre-existing audit report.

## Documentation And Approval Boundary

The implementation changes user-visible lifecycle behavior, so the Story-local
implementation report must document the final workflow, tests, runtime
validation limitations, and Market Data protection explicitly.

No production or test code has been modified for this plan. Implementation may
begin only after explicit human approval of this Implementation Plan.
