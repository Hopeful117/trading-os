# ADR-073 - cTrader Authentication and Explicit External Account Binding

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-10-23 |
| **Decision Makers** | Project Owner, Trading OS Architecture |
| **Related ADRs** | ADR-024, ADR-030, ADR-041, ADR-043, ADR-071, ADR-072 |
| **Related Stories** | Story 0146, Story 0147 |

---

## 1. Context

Story 0146 established the cTrader provider registration and opaque routing
reference, but explicitly excluded OAuth, token lifecycle, transport, account
discovery and external-account binding. The current `BrokerConnection` model
supports Kraken API-key validation and an optional `externalAccountId`, but it
does not distinguish provider recognition, OAuth authentication, account
selection, account binding, synchronization or execution readiness.

cTrader Open API uses a browser authorization flow followed by an HTTP token
exchange and a separate Open API application/account authorization sequence.
The authorization scope can be `accounts` (view-only) or `trading` (full
access). The first implementation must use view-only authorization and must
not create an execution path.

---

## 2. Decision

The Broker Service SHALL own the cTrader authentication and account-binding
boundary. Trading Core SHALL receive only provider-neutral connection and
binding state and SHALL not receive OAuth codes, access tokens, refresh tokens,
client secrets, cTrader DTOs, or transport/session details.

The lifecycle SHALL remain explicitly staged:

```text
provider registered
    -> authorization initiated
    -> callback correlated and exchanged
    -> cTrader application authenticated
    -> authorized external accounts discovered
    -> one account explicitly selected
    -> selected external account bound
    -> authentication/binding state available
```

Authentication does not select an account automatically. A binding command must
be owner-scoped, provider-scoped and based on the authoritative cTrader account
identifier. Rebinding to a different external account is a controlled explicit
transition and is not an implicit consequence of reauthorization.

The existing Broker Service secret-management ports remain the persistence
boundary. cTrader OAuth material is stored as an encrypted provider-specific
secret payload behind an opaque `CredentialReference`; it is not added to
Trading Core. Application identity and client secret are deployment
configuration/secret concerns, not user business identity.

Authorization attempts are server-correlated and one-time consumable. The
correlation record binds the authenticated owner, local `BrokerAccount`, cTrader
provider registration and expiry. Callback requests cannot choose a local
account by trusting a browser-supplied UUID.

The implementation SHALL use a narrow cTrader adapter boundary and SHALL not
introduce a cTrader SDK unless a later approved decision proves that the SDK is
required. Provider-specific protocol and mapping types remain inside Broker
Service.

---

## 3. cTrader Contract Constraints

The implementation is based on the official cTrader Open API documentation
consulted on 2026-10-23:

- https://help.ctrader.com/open-api/account-authentication
- https://help.ctrader.com/open-api/messages
- https://help.ctrader.com/open-api/proxies-endpoints

Documented facts:

- the authorization code is short-lived, documented as one minute;
- the access token is documented as approximately 30 days;
- the refresh token has no documented expiry and token refresh returns new
  access/refresh values while invalidating the old values;
- authorization uses a registered `client_id`, configured `redirect_uri` and
  `scope` of `accounts` or `trading`;
- `ProtoOAApplicationAuthReq` authenticates the application connection;
- `ProtoOAGetAccountListByAccessTokenReq` discovers accounts authorized for the
  access token and exposes the cTrader account identifiers and permission scope;
- `ProtoOAAccountAuthReq` authorizes the selected cTrader account session;
- live and demo Open API endpoints are separated and require separate
  connections when both environments are used;
- token invalidation and account disconnect events can terminate account access.

Trading OS decisions derived from those facts:

- request `accounts` scope for this Story;
- never persist an authorization code;
- store tokens only through Broker Service secret management;
- require explicit selection when zero or multiple accounts are discovered;
- preserve the previously bound external identity when authentication expires or
  is revoked;
- treat transport/session usability as separate from durable binding;
- keep all execution capabilities absent and fail closed.

---

## 4. Security Invariants

- State/correlation is generated server-side, stored with an expiry and consumed
  exactly once.
- Missing, invalid, expired, replayed, cross-owner or provider-mismatched
  callbacks fail without token exchange or binding.
- Authorization codes are exchanged immediately and never logged or persisted.
- Access tokens, refresh tokens and client secrets are never returned through
  public APIs, frontend storage, logs, metrics or Trading Core contracts.
- Account discovery results are readable only by the authenticated owner of the
  local `BrokerAccount` and its pending authorization attempt.
- A selected external cTrader account ID is never used as a local
  `BrokerAccount.id` or `Account.accountId`.
- cTrader authorization with `accounts` scope cannot enable order, cancel,
  amend, close or reconciliation capabilities.
- No automatic account selection or destructive rebinding occurs.

---

## 5. State and Readiness

The exact enum names remain an implementation detail, but the model SHALL
distinguish at least:

```text
provider recognized
authorization required/in progress
authenticated
account selection required
external account bound
authentication expired/revoked
transport unavailable
account facts synchronized
Risk ready
execution ready
```

`authenticated + bound` may coexist with `Risk ready = false` and
`execution ready = false`. A disconnect or token invalidation must not erase the
durable external identity, but it must make provider usability and facts
availability fail closed until revalidated.

---

## 6. Scope Boundary

This ADR authorizes only the foundation required to authenticate cTrader and
explicitly bind one authorized external trading account. It does not authorize:

- account balance, equity, margin, positions, orders, deals or ledger
  synchronization;
- RiskSnapshot production or Risk formula changes;
- market-data integration;
- any cTrader order, amend, cancel, close, SL/TP or execution capability;
- FTMO Challenge rules or Challenge lifecycle changes;
- S1 or AI work;
- automatic account selection;
- real-provider validation without human credentials and explicit procedure.

---

## 7. Consequences

Positive:

- cTrader authentication remains isolated in Broker Service;
- OAuth success cannot be confused with account binding or synchronization;
- view-only authorization makes execution impossible by provider permission;
- restart, expiry, revocation and transport disconnect can be represented without
  losing durable identity;
- the next read-only synchronization Story receives a deterministic bound
  account without inheriting credential or callback security concerns.

Trade-offs:

- a durable authorization-attempt/correlation mechanism is required;
- provider-specific OAuth token material requires a structured encrypted secret
  payload;
- cTrader transport must be implemented or isolated sufficiently to prove
  application auth, account discovery and selected-account auth;
- real cTrader validation remains an explicit human-controlled gate.

---

## 8. Required Follow-Up

Story 0147 must obtain Repository Analysis approval and then produce an
implementation plan. Any change to the secret-storage boundary, external
identity authority, execution capability boundary or scope model requires a new
ADR or an explicit amendment to this ADR.
