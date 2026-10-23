# Story 0147 - Implementation Plan

## Status

`IMPLEMENTED_LOCALLY - REAL_PROVIDER_VALIDATION_REQUIRED`

This plan is based on the approved Repository Analysis, the proposed ADR-073,
the accepted ADR-024/071/072 boundaries and the cTrader Open API contract
documented in the Story analysis. This plan does not authorize implementation.

## Outcome

Implement the smallest Broker Service-only cTrader authentication and explicit
external-account binding foundation:

```text
owner-owned BrokerAccount
    -> correlated authorization attempt
    -> cTrader OAuth token material
    -> authorized account discovery
    -> explicit account selection
    -> deterministic external account binding
```

The result must preserve:

```text
provider recognized != authenticated
authenticated != bound
bound != synchronized
bound != Risk ready
bound != execution ready
```

The implementation must request cTrader `accounts` scope only. No execution
capability or synchronization capability is authorized by this plan.

## Preconditions

Implementation may begin only after:

- human approval of this Implementation Plan;
- ADR-073 is accepted or explicitly approved as the governing architecture
  decision for this Story;
- current branch and worktree are revalidated;
- no unrelated untracked report is modified or staged;
- no real cTrader credential is available to the implementation or tests.

## 1. Provider-Neutral Authentication Contracts

Define narrow provider-neutral application ports in Broker Service for:

- authorization initiation;
- authorization callback completion;
- authorized-account discovery;
- explicit external-account binding;
- token refresh/revalidation;
- safe lifecycle/status reporting.

The contracts must carry only owner-scoped local identifiers, opaque provider
references, provider-neutral lifecycle states and sanitized account metadata.
They must never expose cTrader OAuth DTOs, tokens, authorization codes, client
secrets or transport messages outside the cTrader adapter.

Expected affected areas, subject to exact code audit:

- `broker-service/.../connection/api/`
- `broker-service/.../connection/application/`
- `broker-service/.../connection/domain/`
- `broker-service/.../broker/domain/capability/`
- provider-neutral internal service contracts

Do not add cTrader types to Trading Core. If Trading Core must receive a state
update, use the existing provider-neutral connection update boundary and only
the minimum state required by business workflow.

## 2. Authorization Attempt Correlation

Add a durable or otherwise restart-safe owner-scoped authorization-attempt
record using the repository persistence conventions. It must contain only
non-secret correlation metadata:

- attempt identity;
- owner identity;
- local `BrokerAccount`/`BrokerConnection` identity;
- opaque cTrader provider registration reference;
- hashed or opaque state value;
- configured redirect/environment binding;
- creation and expiry timestamps;
- consumed/completed outcome;
- safe failure classification.

Rules:

- generate state server-side;
- never trust a browser-supplied local account UUID as authorization;
- validate authenticated owner and provider before creating the attempt;
- consume state atomically before accepting a callback;
- reject missing, invalid, expired, replayed, cross-owner and provider-mismatched
  callbacks;
- never persist the authorization code;
- avoid revealing whether another owner's attempt exists.

Use a migration only if the current schema conventions require persistence for
restart-safe correlation. Document the exact reason and avoid modifying
historical Kraken identities.

## 3. cTrader Application Configuration and Secret Handling

Classify and place credentials as follows:

| Material | Classification | Boundary |
|---|---|---|
| cTrader `client_id` | provider application configuration | Broker Service configuration |
| cTrader `client_secret` | deployment secret | Broker Service secret/configuration boundary |
| authorization code | transient auth state | callback request only, never persisted |
| access token | persisted provider identity secret | encrypted Broker Service secret store |
| refresh token | persisted provider identity secret | encrypted Broker Service secret store |
| cTrader external account ID | business/provider identity | BrokerConnection binding metadata |
| permission scope/environment | provider binding metadata | Broker Service connection metadata |

Reuse `SecretWriter`, `SecretReader`, `SecretRotator`, `SecretRevoker` and
`CredentialReference`. Introduce a versioned provider-specific encrypted token
payload behind the existing opaque reference rather than creating a second
secret store. Do not reuse the Kraken API-key serializer unless its contract is
generalized safely and tests prove no credential leakage.

Token payload handling must:

- use encrypted storage;
- support access-token expiry metadata without putting token values in logs;
- rotate access/refresh values atomically because cTrader invalidates old values
  after refresh;
- revoke/disable the stored material on provider revocation;
- redact all diagnostics and API responses.

## 4. cTrader Adapter and Protocol Boundary

Implement cTrader-specific code under the existing Broker Service provider
infrastructure conventions, not in Trading Core. The adapter must isolate:

- authorization URL construction;
- HTTP token exchange and refresh;
- Open API application authentication;
- account-list discovery;
- selected-account authorization;
- provider error mapping;
- demo/live endpoint selection;
- transport disconnect and token invalidation events.

Use direct protocol integration unless implementation audit proves a maintained
SDK is required. Do not add an SDK by default. If a library becomes necessary,
record its version, maintenance status, compatibility, license and boundary
leakage in the Implementation Report and stop for an ADR amendment if the
choice changes the architecture.

The adapter may use the minimum Open API messages required to authenticate and
discover/bind an account:

```text
ProtoOAApplicationAuthReq
ProtoOAGetAccountListByAccessTokenReq
ProtoOAAccountAuthReq
```

It must not implement or register:

```text
ProtoOANewOrderReq
ProtoOACancelOrderReq
ProtoOAAmendOrderReq
ProtoOAAmendPositionSLTPReq
ProtoOAClosePositionReq
```

No account snapshot, position, order, deal, ledger or market-data synchronization
is part of this plan.

## 5. Lifecycle and Binding State

Extend or refactor the existing `BrokerConnectionStatus` only where necessary.
Do not mechanically add the exact states from the Story. The resulting model
must distinguish:

- authorization required/in progress;
- authentication valid/expired/revoked;
- account selection required;
- external account bound/unbound;
- transport currently usable/unavailable;
- synchronized facts unavailable;
- execution unavailable.

Prefer separate provider-neutral binding/auth metadata over overloading
`CONNECTED` to mean every stage. Preserve the existing durable external account
identity on transport disconnect, expiry or revocation. Do not silently replace
it when reauthorization returns a different account list.

The explicit binding command must:

- require the authenticated owner;
- require the local BrokerAccount and connection to match;
- require the selected account to be present in the same authorized discovery
  result or a freshly validated equivalent;
- validate provider and environment;
- be idempotent for the same external account;
- reject a different external account unless an explicit controlled rebinding
  operation is defined and authorized;
- never enable synchronization, Risk or execution.

## 6. API and Ownership Security

Expose only owner-scoped, sanitized endpoints/contracts for:

1. start cTrader authorization;
2. receive the provider callback;
3. read pending discovery results;
4. explicitly bind one discovered account;
5. request reauthentication or safe refresh/revalidation.

All user-facing endpoints must use the existing authenticated owner boundary.
Internal callbacks must retain service authentication. Public responses must
not expose client secrets, authorization codes, tokens, raw provider payloads or
another owner's account list.

Error mapping must distinguish at least:

- denied authorization;
- invalid/expired authorization code;
- invalid state or replay;
- token acquisition/refresh failure;
- provider outage/transport disconnect;
- no eligible account;
- multiple accounts requiring selection;
- account no longer authorized;
- owner/provider mismatch;
- unsupported provider capability.

Use safe messages and structured observability with local correlation ID,
owner-scoped local account ID and non-secret provider/account references only.

## 7. Restart, Reauthentication and Disconnect Semantics

Persist enough metadata to prove after restart that:

- the local binding belongs to the same owner and provider;
- the external account identity has not been silently replaced;
- tokens remain protected and are not reported as usable without validation;
- expired/revoked authentication requires reauthentication;
- execution remains unavailable regardless of persisted auth history.

When refresh or provider validation returns a different account set, preserve the
previous binding and require explicit validation before any change. A transport
disconnect must not delete the durable external account identity, but must make
current provider usability and facts availability fail closed.

## 8. Kraken Regression and Boundary Protection

Preserve and run existing Kraken coverage for:

- provider registration and resolution;
- BrokerAccount/BrokerConnection ownership;
- credential validation and rotation;
- synchronization;
- capability resolution;
- execution routing and unknown-result semantics.

Add architecture tests/searches proving:

- no FTMO classes or rules appear in Broker Service cTrader code;
- no cTrader OAuth/transport/DTO/token types appear in Trading Core;
- no execution capability is registered for cTrader;
- no cTrader path falls back to Kraken;
- no secret or callback code is logged or serialized into public responses.

## 9. Focused Tests

Add deterministic tests with synthetic identifiers and token material:

- authorization URL and `accounts` scope;
- owner/provider/account correlation;
- valid callback and one-time state consumption;
- missing, invalid, expired and replayed state;
- cross-owner callback and provider mismatch;
- authorization denial and token exchange failure;
- encrypted token write/rotate/revoke and redaction;
- discovery with zero, one and multiple accounts;
- explicit account selection and same-account idempotency;
- different-account rebinding rejection;
- token expiry, refresh rotation and revocation;
- transport disconnect and restart semantics;
- no execution capability / fail-closed execution request;
- Kraken regression tests;
- API security and service-authentication tests.

Tests must assert domain/security outcomes, persisted identity and state
transitions, not merely mock invocation counts.

## 10. Validation Commands

Before implementation completion, run the affected repository checks:

```bash
cd broker-service
mvn test
```

```bash
cd trading-core
mvn test
```

If frontend contracts change:

```bash
cd trading-os-web
npm run check
```

Repository checks:

```bash
git diff --check
```

Run migration/repository integration tests if a persistence migration is added.
Run the repository quality workflow/SonarQube checks when applicable. Report
executed, passed, failed, blocked and non-applicable checks separately.

## 11. Real Provider Validation Procedure

Deterministic local tests cannot prove real cTrader authorization. The
Implementation Report must record:

```text
LOCAL_DETERMINISTIC_TEST = PASS
REAL_PROVIDER_VALIDATION = REQUIRED
```

If a human later authorizes real validation, the procedure must require:

1. a cTrader Open API application registered by the human;
2. configured redirect URI and client ID;
3. client secret supplied only through the Broker Service secret/configuration
   mechanism;
4. a demo or explicitly selected environment with `accounts` scope;
5. one owner-scoped authorization and explicit account selection;
6. evidence of authenticated/bound state without any token or raw payload;
7. proof that no synchronization, Risk readiness or execution capability is
   enabled;
8. token revocation/cleanup and removal of test material.

No order, amendment, cancellation, close, position mutation or live challenge is
permitted.

## 12. Documentation Reconciliation

Update only the canonical Story/ADR/implementation documentation required by
the actual implementation. Preserve historical reports and do not rewrite them
to claim knowledge they did not contain.

The Implementation Report must include the documentation outcome, validation
matrix, residual gaps, real-provider status and the final gate requested by the
Story. A Code Review Report and Engineering Report are produced only after
implementation and validation.

## 13. Stop Conditions

Stop and request a new decision if:

- secret handling requires a second persistence system;
- OAuth material must cross into Trading Core;
- callback ownership cannot be proven server-side;
- cTrader requires `trading` scope to perform the requested authentication/binding;
- account binding cannot remain distinct from synchronization or execution;
- a provider SDK becomes architecturally necessary without approved decision;
- a persistence migration would rewrite ambiguous historical Kraken data;
- the only way to validate cTrader is to register an execution capability;
- the implementation would modify FTMO Challenge semantics or Market Data;
- real credentials are required before deterministic tests can be completed.

## Deliverables

- cTrader authentication initiation/callback foundation;
- protected token storage using existing Broker Service secret management;
- owner-scoped account discovery and explicit external-account binding;
- fail-closed lifecycle and execution capability behavior;
- focused tests and Kraken regression evidence;
- updated Story implementation report;
- code review report and engineering report after subsequent workflow stages;
- human-controlled real-provider validation procedure/status.

## Next Workflow Gate

Human approval of this Implementation Plan is required before implementation
may begin. Approval authorizes the implementation stage only; it does not
authorize real-provider credentials, live trading or execution.
