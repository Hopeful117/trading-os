# Implementation Report - Story 0147

## Result

Implemented the Broker Service foundation for cTrader authorization, authorized
account discovery and explicit external-account binding. Trading Core remains
cTrader-agnostic and no cTrader execution capability was registered.

The implementation is locally complete for deterministic behavior. Real cTrader
authorization remains required and was not executed because no real credentials
were provided.

## Repository Baseline

| Item | Value |
|---|---|
| Branch | `story/0147-ctrader-authentication-and-authenticated-account-binding` |
| Base commit | `009df93080bc11507e53dc3ee05d9a45fc074b06` |
| Base branch | `main` |
| Worktree boundary | Pre-existing untracked `docs/architecture/reports/*` files were not modified |

## ADR Decision

ADR-073 defines Broker Service ownership of cTrader OAuth, token material,
transport authentication, account discovery and explicit binding. It preserves
the distinction between authentication, binding, synchronization, Risk and
execution readiness.

## cTrader Auth Contract

The implementation follows the official contract documented in the Story
analysis:

- authorization URL uses configured client ID, redirect URI and `accounts` scope;
- authorization code is exchanged through `/apps/token`;
- cTrader JSON Open API transport performs application authorization;
- authorized account IDs are discovered through the account-list message;
- the selected account is authorized through the account-auth message;
- demo/live endpoint is configuration, not business identity.

## Authentication Architecture

Broker Service now contains:

- owner-scoped persisted authorization attempts;
- SHA-256 state correlation with expiry and one-time consumption;
- cTrader HTTP token exchange;
- minimal JSON WebSocket transport for application auth, account discovery and
  selected-account authorization;
- sanitized authorization/discovery/binding API responses.
- the OAuth callback is explicitly reachable without a user JWT; state validation
  remains the callback's trust boundary.

The adapter does not expose provider protocol types through Trading Core.

## Credential Ownership

The cTrader access/refresh token pair is serialized behind the existing opaque
`CredentialReference` and persisted through the existing encrypted secret
ports. The cTrader application client secret remains Broker Service deployment
configuration. Authorization codes remain request-scoped and are never
persisted.

No token, client secret, authorization code or raw provider payload is returned
by the API or written to logs.

## Callback Security

Callbacks require a server-generated state value. The persisted state hash binds
the attempt to the owner, local BrokerAccount, provider registration, environment
and expiry. Missing, invalid, expired, replayed, cross-owner and provider-
mismatched callbacks fail closed. Failed callback outcomes are committed as
failed attempts rather than being lost to transaction rollback.

## Account Discovery and Explicit Binding

Discovery returns the cTrader authoritative account identifiers and keeps zero
or multiple account results in an explicit selection state. No first-account
selection is performed.

Binding validates the authenticated owner, local BrokerAccount, cTrader provider
registration, authorization attempt and selected external account. The selected
ID is stored on the Broker Service `BrokerConnection` as `externalAccountId`,
separate from `Account.accountId`, `BrokerAccount.id`, `BrokerConnection.id` and
the provider registration reference.

Same-account binding is idempotent. A different external account cannot silently
replace an existing binding.

## Connection State and Readiness

The existing technical `BrokerConnectionStatus` is intentionally not changed to
`CONNECTED` by cTrader OAuth or account binding. cTrader authentication stores a
credential reference while the existing connection remains unavailable to the
provider resolver. This prevents authentication or binding from implying
synchronization or execution readiness.

Transport disconnect and token expiry therefore preserve the durable external
identity while failing closed for current provider use. Full refresh-token
automation is not exposed as a separate endpoint; expiry requires a new
owner-correlated authorization flow.

## Persistence and Migration

Migration `V7__ctrader_authentication_and_account_binding.sql` adds the durable
authorization-attempt table. It does not rewrite historical Kraken identities.
The existing encrypted `broker_secret` table is reused for cTrader token
material.

## Execution Boundary

No cTrader order, amend, cancel, close, reconciliation or account-fact
synchronization message was implemented. The cTrader adapter exposes only the
three authentication/discovery message families required by this Story.

## Kraken Regression

Existing Broker Service provider, credential, ownership, capability, execution,
revocation and persistence tests remain green. No cTrader fallback was added to
Kraken resolution.

## Validation

Passed:

```text
broker-service: mvn -q -DskipTests compile
broker-service: mvn -q test -Dtest=BrokerApiSecurityIntegrationTest,CTraderAuthenticationServiceTest,CTraderAuthorizationAttemptTest,CTraderTokenCodecTest
broker-service: mvn -q test
broker-service: mvn -q clean verify
git diff --check
```

The full Broker Service verification applied Flyway V7 to PostgreSQL
Testcontainers and validated the JPA model against H2. Mockito emitted its
existing dynamic-agent warning; it did not fail the build.

## Real Provider Validation Status

```text
LOCAL_DETERMINISTIC_TEST = PASS
REAL_PROVIDER_VALIDATION = REQUIRED
```

No cTrader application secret, user token, live account, demo account or order
was used. Human validation must configure a cTrader Open API application with
`accounts` scope, execute one owner-scoped authorization and explicit account
selection, verify sanitized `BOUND` evidence, then revoke and clean up the test
material. Orders and synchronization must remain impossible.

## Residual Gaps

- Real cTrader authorization and account discovery remain unvalidated.
- Refresh-token automation is intentionally deferred to reauthorization rather
  than silently changing account identity.
- Full account synchronization, authoritative facts, Risk readiness and
  execution remain out of scope.
- The cTrader WebSocket numeric payload constants must be confirmed against the
  provider environment during controlled real-provider validation.
- Existing 0146 documentation still contains stale pre-merge workflow statuses;
  this Story did not rewrite historical investigation records.

## Next Story

`Story 0148 - cTrader READ-ONLY Account Synchronization`

## Files Changed

- `broker-service/src/main/java/com/hope/trading/broker_service/connection/domain/BrokerConnection.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/security/BrokerSecurityConfiguration.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/`
- `broker-service/src/main/resources/application.properties`
- `broker-service/src/main/resources/db/migration/V7__ctrader_authentication_and_account_binding.sql`
- `broker-service/src/test/java/com/hope/trading/broker_service/ctrader/`
- `broker-service/src/test/java/com/hope/trading/broker_service/broker/BrokerApiSecurityIntegrationTest.java`
- `docs/architecture/adr/ADR-073-ctrader-authentication-and-account-binding.md`
- `docs/architecture/stories/0147-ctrader-authentication-and-authenticated-account-binding/`

## Vault Outcome

- Vault consulted: no.
- Outcome: no vault action.
- Rationale: the Story concerns repository-owned provider authentication and
  security contracts already governed by repository ADRs.

## Documentation Outcome

Updated the Story implementation report and added ADR-073. No FTMO Challenge,
Market Data, S1 or AI documentation was changed.

## Final Gate

========================================
CTRADER AUTH + ACCOUNT BINDING GATE
========================================

REPOSITORY BASELINE:
009df93080bc11507e53dc3ee05d9a45fc074b06

STORY:
0147 - cTrader Authentication and Authenticated Account Binding

ADR REQUIRED:
YES

ADR:
ADR-073 - cTrader Authentication and Explicit External Account Binding

CTRADER PROVIDER FOUNDATION:
READY

AUTHENTICATION CONTRACT:
IMPLEMENTED

AUTH STATE CORRELATION:
READY

CREDENTIAL ISOLATION:
READY

ACCOUNT DISCOVERY:
READY

EXPLICIT ACCOUNT SELECTION:
READY

EXTERNAL ACCOUNT BINDING:
READY

OWNER ISOLATION:
READY

REAUTHENTICATION:
PARTIAL

RESTART SEMANTICS:
PARTIAL

TRADING CORE CTRADER COUPLING:
NO

BROKER SERVICE FTMO COUPLING:
NO

KRAKEN REGRESSION:
PASS

REAL CTRADER AUTH VALIDATED:
NO

CTRADER ACCOUNT SYNCHRONIZED:
NO

RISK READY FROM CTRADER:
NO

CTRADER EXECUTION ENABLED:
NO

S1 REQUIRED:
NO

AI REQUIRED:
NO

IMPLEMENTATION STATUS:
PARTIAL

NEXT ENGINEERING STORY:
0148 - cTrader READ-ONLY Account Synchronization

NEXT HUMAN ACTION:
Perform controlled demo cTrader authorization and explicit account-binding validation with `accounts` scope, without placing orders.
