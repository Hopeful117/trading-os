# Story 0147 - Repository Analysis

## Status

`WAITING_FOR_ANALYSIS_APPROVAL`

This artifact records the current repository analysis. It does not authorize
implementation and does not grant approval for the proposed ADR.

## Repository Baseline

| Item | Current value |
|---|---|
| Branch | `main` |
| HEAD | `009df93080bc11507e53dc3ee05d9a45fc074b06` |
| origin/main | `009df93080bc11507e53dc3ee05d9a45fc074b06` |
| Working tree | Nine pre-existing untracked files under `docs/architecture/reports/`; no tracked modifications at analysis start |
| Latest merged Story | Story 0146, PR #164, merge commit `009df93080bc11507e53dc3ee05d9a45fc074b06` |
| Latest accepted ADR | ADR-072; ADR-073 is proposed by this task |
| Next Story number | `0147`; no `0147` artifact existed before this analysis |
| DevLog | Applicability check attempted; `devlog_devlog_story_agent` timed out, so analysis continues from repository evidence |
| Vault | Not consulted; current repository ADRs and Story artifacts are the authoritative sources for this task |

## Merged Foundation Verification

### Story 0145

The merged implementation preserves the explicit
`Account.brokerAccountId -> BrokerAccount.id` route and v2 broker snapshot
contract. Trading Core rejects missing, cross-owner, provider-mismatched and
external-identity-mismatched mappings. Broker Service owns provider capability
resolution. The implementation report records focused Broker Service and
Trading Core tests and no cTrader-specific implementation.

### Story 0146

The merged code contains:

- `BrokerProviderRegistrationCatalog` with broker-owned opaque UUID references
  for Kraken and cTrader;
- cTrader provider identity in Broker Service only;
- provider-neutral Trading Core connection contracts;
- V34 migration for the deterministic existing Kraken reference conversion;
- capability-less cTrader resolution that returns unavailable behavior rather
  than falling back to Kraken;
- preserved local `BrokerAccount.id`, `BrokerConnection.brokerAccountId` and
  optional `externalAccountId` as distinct identities;
- migration, ownership, provider registry and routing regression tests.

The current 0146 Story and plan artifacts still describe their historical
pre-merge workflow state (`Proposed` and `IMPLEMENTATION_IN_PROGRESS`) and the
0146 directory has no implementation report or engineering report. This is a
documentation reconciliation gap in the merged foundation. It does not change
the verified physical code on `main`; a later documentation-only reconciliation
should preserve the historical decision and record the merged validation rather
than rewrite prior investigation reports.

## Current Broker Service Model

`BrokerConnection` currently persists:

- local `brokerAccountId`, owner and immutable provider;
- one `activeCredentialReference` UUID;
- technical `BrokerConnectionStatus`;
- detected permissions and validation timestamp;
- optional `externalAccountId` and an API key hint.

The current status values are `CREATED`, `PENDING_VALIDATION`, `CONNECTED`,
`INVALID_CREDENTIALS`, `INSUFFICIENT_PERMISSIONS`,
`TEMPORARILY_UNAVAILABLE`, `REAUTHENTICATION_REQUIRED`, `DISCONNECTED` and
`REVOKED`. They model the existing API-key validation lifecycle but cannot
cleanly distinguish authenticated application state from explicit external
account binding or synchronized facts.

`BrokerCredentialConnectionService` currently accepts generic
`CredentialMaterial` shaped around API key, API secret and passphrase. It
resolves provider validators by provider identity, writes encrypted material
through `SecretWriter`/`SecretRotator`, and propagates provider-neutral status
updates to Trading Core. A cTrader OAuth token bundle cannot safely be treated
as an API-key credential without an explicit provider-neutral secret payload
decision.

`CredentialReference.toString()` is deliberately opaque. `BrokerSecretEntity`
stores encrypted ciphertext and metadata in Broker Service. This existing
secret boundary can support an opaque cTrader token payload without creating a
second secret store, but the payload format and metadata lifecycle require a
new implementation decision.

`BrokerConnectionResponse` currently exposes provider, technical status,
external account ID, permission names, API key hint and validation timestamp,
but no authorization-attempt, account-discovery or binding state. The API
surface must not expose OAuth material.

## Current Provider Boundary

`BrokerProviderRegistrationCatalog` resolves the opaque cTrader registration
reference to `BrokerProviderId.CTRADER`. The provider registry remains
capability-oriented. There is no cTrader provider adapter, authentication
capability, transport client or execution capability on `main`.

This is the correct starting point: cTrader recognition is present, but
recognition is not authentication and must not be made to appear authenticated
by registering a fake capability.

## Current Trading Core Boundary

Trading Core owns the local `BrokerAccount`, owner relation, business connection
status, `CredentialReference`, optional external account identity and the
canonical `Account.brokerAccountId` relation. It has no cTrader transport or
OAuth types. It must remain that way. Any new cTrader state crossing the
service boundary must be provider-neutral and limited to lifecycle/binding
facts required by business workflow.

## Documented cTrader Contract

Official documentation was consulted on 2026-10-23:

- [App and account authentication](https://help.ctrader.com/open-api/account-authentication)
- [Protobuf messages](https://help.ctrader.com/open-api/messages)
- [Proxies and endpoints](https://help.ctrader.com/open-api/proxies-endpoints)

Documented contract:

- cTrader uses OAuth 2.0-style browser authorization;
- authorization uses registered `client_id`, configured `redirect_uri` and
  `scope` `accounts` or `trading`;
- `accounts` is view-only and cannot perform trading operations;
- authorization code lifetime is documented as one minute;
- access-token lifetime is documented as 2,628,000 seconds, approximately 30
  days;
- refresh tokens have no documented expiry, and refresh returns new access and
  refresh tokens while invalidating the old values;
- the token exchange uses `https://openapi.ctrader.com/apps/token`;
- application authentication uses `ProtoOAApplicationAuthReq` with client ID
  and client secret;
- account discovery uses `ProtoOAGetAccountListByAccessTokenReq` and returns
  cTrader account identifiers plus permission scope;
- selected-account session authorization uses `ProtoOAAccountAuthReq` with the
  authoritative `ctidTraderAccountId` and access token;
- live and demo endpoints are separate (`live.ctraderapi.com` and
  `demo.ctraderapi.com`), so environment is part of provider connection
  configuration;
- token invalidation and account disconnect events can invalidate account
  sessions.

The provider documentation does not provide Trading OS callback `state`, owner
correlation, replay protection or local binding semantics. Those are Trading OS
security decisions, not generic assumptions about cTrader.

## Authentication and Transport Separation

The cTrader contract separates:

1. browser authorization and HTTP token exchange;
2. application authentication on the Open API transport;
3. account discovery from the access token;
4. selected-account session authorization.

The first implementation therefore needs a minimal transport boundary to prove
account discovery and selected-account authorization, but it must not implement
market/account synchronization or any order-capable message. TCP/WebSocket and
Protobuf/JSON are provider transport choices inside Broker Service. The current
repository has no cTrader SDK dependency; introducing one is not justified by
the documentation alone and would require an explicit compatibility/leakage
assessment.

## Identity Model

The following identities must remain distinct:

| Identity | Owner |
|---|---|
| `Account.accountId` | Trading Core financial account |
| `BrokerAccount.id` | Trading Core execution-routing account |
| `BrokerConnection.id` | Broker Service technical connection |
| opaque provider registration reference | Broker Service provider selection |
| cTrader `ctidTraderAccountId` | cTrader external trading-account identity |
| FTMO ChallengeInstance identity | Trading Core challenge/business domain |

The existing `BrokerConnection.externalAccountId` can represent the selected
external identity only if binding state and authentication validity are modeled
separately. It must not be overwritten merely because a new authorization
returns a different account list.

## Required Security Design

The current model has no authorization-attempt entity. The implementation will
need an owner-scoped, provider-scoped and expiring correlation record containing
only non-secret metadata and a hash/opaque representation of state. It must be
one-time consumable and must validate the authenticated owner and local account
server-side before token exchange or binding.

The authorization code must remain request-scoped and be exchanged immediately.
Access and refresh tokens must be serialized into encrypted Broker Service
secret storage behind `CredentialReference`. No token, code or client secret may
cross into Trading Core, frontend local storage, logs or telemetry.

The selected external account must be explicitly submitted after discovery and
validated against the authorized discovery result for the same owner and
authorization attempt. Zero accounts, multiple accounts, expired authorization,
revocation, stale state and provider mismatch must have deterministic
provider-neutral outcomes.

## Required Architecture Decision

ADR-073 is required because this Story crosses the explicit ADR-072 boundary:

- it adds a new durable authentication lifecycle;
- it defines how OAuth material uses the existing secret ports;
- it introduces callback correlation and replay protection;
- it decides whether existing `externalAccountId` is sufficient for binding or
  requires provider-neutral binding metadata;
- it separates transport session authorization from durable authentication;
- it must guarantee no execution capability is exposed while using cTrader
  authorization.

ADR-073 is proposed, not accepted. Its decision is therefore not yet an
implementation authorization.

## Proposed Scope for Planning

After analysis approval, the Implementation Plan should decide and validate:

1. provider-neutral authorization-attempt persistence and expiry;
2. cTrader-specific token payload encryption through existing secret ports;
3. application configuration versus secret ownership for client identity/secret;
4. minimal direct protocol transport boundary versus a separately justified SDK;
5. provider-neutral API contracts for initiation, callback, discovery and binding;
6. lifecycle state mapping without conflating authentication, binding,
   synchronization or execution readiness;
7. restart, expiry, revocation, disconnect and idempotency behavior;
8. focused deterministic tests and the human real-provider validation procedure.

## Risks

- Reusing API-key credential DTOs could leak or mishandle OAuth token material.
- Persisting raw callback state or authorization codes would enable replay or
  cross-owner binding.
- Treating the first discovered account as canonical would create silent wrong-
  account binding.
- Storing cTrader account authorization as `CONNECTED` could falsely imply
  synchronization or execution readiness.
- Adding a generic cTrader capability before an `accounts`-scope adapter exists
  could make execution appear available.
- A provider SDK could leak cTrader types across the Broker Service boundary or
  add an unverified transport dependency.

## Validation Expectations

The future Implementation Plan must include:

- focused authorization initiation/callback/security tests;
- token persistence/redaction tests using synthetic secrets only;
- account discovery zero/one/multiple tests;
- explicit binding, ownership, provider mismatch and idempotency tests;
- expiry, revocation, disconnect and restart tests;
- cTrader capability-less execution-failure tests;
- Kraken regression tests;
- provider-boundary architecture/search tests;
- `git diff --check`, affected Maven tests and frontend checks if API models
  change;
- explicit `REAL_PROVIDER_VALIDATION = REQUIRED` unless a human later runs the
  documented controlled procedure with view-only credentials.

## Current Gate

Repository Analysis is complete. Human approval is required before producing
the Implementation Plan. Approval will not authorize implementation; it only
advances the workflow to planning.
