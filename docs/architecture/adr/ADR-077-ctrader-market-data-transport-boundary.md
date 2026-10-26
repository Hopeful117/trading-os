# ADR-077 - cTrader Market Data Transport Boundary

| | |
|---|---|
| **Status** | Accepted - human architecture review completed |
| **Date** | 2026-10-25 |
| **Decision Makers** | Project Owner, Trading OS Architecture |
| **Decision Type** | cTrader service ownership and shared protocol infrastructure |
| **Related Stories** | Story 0167 - cTrader Market Data for FTMO Dry-Run |
| **Related ADRs** | ADR-006, ADR-011, ADR-019, ADR-020, ADR-030, ADR-053, ADR-071, ADR-073, ADR-074 |

---

## 1. Context

Trading OS has two distinct provider connection patterns for Kraken:

```text
Broker Service
    -> authenticated private broker API
    -> accounts, orders, positions, execution and reconciliation

Market Data Service
    -> public REST/WebSocket market APIs
    -> market catalogue, ticker, OHLC and market streams
```

The two services use separate provider adapters because Kraken public Market Data
does not require the authenticated broker account session.

cTrader is different at the protocol level. The current cTrader Open API
operations used by Story 0167 require an authorized cTrader account context:

```text
application authentication
    -> account authentication
    -> ctidTraderAccountId
    -> symbol discovery / metadata
    -> historical trendbars and spot subscriptions
```

The account identifier is required by the Open API to route and authorize the
request. It does not by itself make the resulting market facts account-owned
business data.

The current cTrader implementation is in Broker Service and owns OAuth,
encrypted token storage, account binding, and a private request-oriented
WebSocket session. It does not establish that Broker Service should own Market
Data transport or Market Data subscription lifecycle.

The repository establishes stronger product boundaries:

- ADR-006 makes Market Data Service the authoritative source of market-related
  information, including catalogue, metadata, market state, and realtime streams.
- ADR-011 makes Market Data Service responsible for dynamic subscription
  lifecycle management and central duplicate suppression.
- ADR-019 states that Market Data Service owns market facts, bid/ask, OHLC,
  freshness, WebSocket streams, and does not depend on Broker Service.
- ADR-020 assigns market data acquisition and normalization to Market Data
  Service, while Broker Service owns broker connectivity and account operations.
- ADR-030 isolates broker-provider concerns inside Broker Service but does not
  make Broker Service the universal owner of all provider market feeds.

Story 0167 requires:

- cTrader instrument discovery and metadata;
- historical trendbars;
- live bid/ask;
- live closed-bar updates;
- provider freshness and provenance;
- session/tradability facts where authoritative;
- reconnect and resubscription;
- no LIVE execution and no Risk changes.

## 2. Problem

The first proposal for ADR-077 assigned cTrader Market Data transport and
subscription lifecycle to Broker Service because cTrader requires an account
authorization token. That conflated separate concerns:

```text
authentication material
!=
market-data ownership
```

It would make Broker Service a cTrader integration runtime and introduce dual
subscription ownership between Broker Service and Market Data Service. That is
in tension with ADR-006, ADR-011, ADR-019, and ADR-020.

The architecture must support both of these future cases:

```text
execution provider != analytical Market Data provider

Market Data consumed without execution through the same provider
```

## 3. Decision

Trading OS SHALL assign ownership by stable domain responsibility rather than by
the location of credentials:

```text
Broker Service
    -> trading/account connectivity
    -> private account authority
    -> orders, positions, deals and execution

Market Data Service
    -> market connectivity
    -> market catalogue and instrument identity
    -> historical and realtime Market Data
    -> canonical normalization
    -> freshness, Market State and subscriptions
```

For cTrader specifically:

- Market Data Service SHALL own the cTrader Market Data transport session.
- Market Data Service SHALL own the Market Data subscription registry,
  reconnect, and resubscription lifecycle.
- Market Data Service SHALL own cTrader market symbol discovery and canonical
  instrument mapping.
- Market Data Service SHALL own normalization into canonical Market Data facts.
- Broker Service SHALL own trading-account OAuth consent, trading token storage,
  trading account binding, private account facts, orders, positions, deals,
  and execution connectivity.
- Broker Service SHALL not become the cTrader Market Data transport runtime.

A narrow shared cTrader technical library SHALL be used only for protocol-level
primitives that are safe to share between the two services:

- Open API message encoding/decoding primitives;
- protobuf serialization support;
- application-authentication request construction;
- token-aware connection bootstrap primitives;
- request correlation primitives;
- provider protocol constants and low-level transport error parsing.

The shared library SHALL NOT contain:

- domain services;
- account or Market Data ownership;
- token persistence;
- OAuth workflow orchestration;
- subscription registries;
- reconnect policy;
- provider event distribution;
- a generic cTrader runtime;
- a platform event bus;
- canonical Market Data models;
- execution or Risk behavior.

Each service SHALL create and own its own bounded cTrader session and lifecycle
for its own responsibility.

## 4. Authentication Responsibility Split

Authentication is decomposed into separate responsibilities:

| Responsibility | Owner |
|---|---|
| Trading OAuth consent | Broker Service |
| Market Data OAuth consent | Market Data Service |
| Trading token storage | Broker Service secret boundary |
| Market Data token storage | Market Data Service secret boundary |
| Trading token refresh | Broker Service |
| Market Data token refresh | Market Data Service |
| Trading application authentication | Broker Service session |
| Market Data application authentication | Market Data Service session |
| Trading account authentication | Broker Service |
| Market Data account authorization | Market Data Service |
| Trading account binding | Broker Service |
| Market Data source binding | Market Data Service configuration |

These are separate service-specific authorizations. Market Data does not read
Broker Service secrets and Broker Service does not operate Market Data sessions.

The two services may use the same cTrader Open API application registration and
the same user/cTID account, but they must use service-appropriate scopes and
credential references. Token material must not be copied across services.

The existing Broker Service cTrader authentication flow remains valid for
trading connectivity. Story 0167 adds a separate read-only Market Data
authorization flow or deployment-managed Market Data credential source. It does
not silently reuse Broker Service's token store or account-binding workflow.

## 5. cTrader Account Dependency

Official cTrader Open API contracts require `ctidTraderAccountId` for the Story
0167 operations:

- `ProtoOAGetTrendbarsReq` requires the account ID and symbol ID;
- `ProtoOASymbolByIdReq` requires the account ID;
- `ProtoOASubscribeSpotsReq` requires the account ID and symbol ID;
- `ProtoOASubscribeLiveTrendbarReq` requires the account ID, period, and symbol
  ID;
- live trendbar subscriptions require spot-event subscription first.

The account ID is an authorization and routing context for cTrader's provider
API. It is not a Trading OS account ownership claim for the resulting market
facts.

For Story 0167 V1:

- Market Data uses one explicitly configured cTrader Market Data source binding.
- The binding identifies the cTrader account/environment needed for authorized
  access to the market catalogue and feeds.
- The binding is not a Trading Core `Account` or `BrokerAccount` ownership
  relationship.
- No automatic cTrader account selection is permitted.
- Multiple account-specific Market Data catalogues are out of scope.
- Account/environment mismatch fails closed.

This preserves the distinction:

```text
cTrader trading account binding
    -> Broker Service

cTrader account-authorized Market Data access
    -> Market Data Service
```

## 6. Symbol Mapping Ownership

The architecture distinguishes two mappings:

```text
account/provider trading identity
    -> Broker Service

market/provider instrument identity
    -> Market Data Service
```

Broker Service owns the identity of the bound trading account and any private
account facts.

Market Data owns:

- cTrader symbol discovery for the configured Market Data source;
- provider symbol ID and provider symbol name;
- canonical symbol and product type mapping;
- price/volume precision and constraints;
- provider-specific instrument provenance.

cTrader symbol IDs are server/environment-specific. Similar names are not enough
to establish equivalence. A cTrader symbol must map explicitly to the canonical
Trading OS identity. A cTrader CFD must not be silently mapped to an underlying
future or reference-market contract.

Canonical identity remains:

```text
provider + canonical symbol + productType
```

The provider symbol ID remains Market Data adapter metadata.

## 7. Subscription Ownership

Market Data Service SHALL own the complete Market Data subscription lifecycle:

- consumer demand registry;
- reference counting or equivalent duplicate suppression;
- provider subscription creation;
- provider unsubscribe;
- connection lifecycle;
- reconnect;
- resubscribe;
- stale/unavailable transition;
- publication of canonical `TickerEvent` and `OhlcEvent` values.

Broker Service SHALL have no Market Data subscription registry.

Broker Service may have an independent private account stream lifecycle for
account, order, position, or deal facts. That stream is not a Market Data
subscription and must not be used as the Market Data transport.

This avoids the dual-registry design from the previous ADR-077 proposal.

## 8. Market Data Responsibilities

Market Data Service SHALL own:

- cTrader Market Data configuration and source binding;
- Market Data authorization for the configured source;
- cTrader Market Data connection;
- symbol discovery and metadata retrieval;
- historical trendbar requests;
- live spot subscriptions;
- live trendbar subscriptions;
- reconnect and resubscribe;
- canonical instrument mapping;
- bid/ask and OHLC normalization;
- forming versus closed-bar semantics;
- cutoff and no-lookahead semantics;
- freshness and unavailable states;
- `MarketState` and provider-tradability mapping;
- provenance and publication through existing Market Data contracts.

Market Intelligence consumes the resulting provider-neutral facts only.

## 9. Broker Service Responsibilities

Broker Service SHALL own:

- trading OAuth consent;
- trading token storage and refresh;
- cTrader trading-account binding;
- application/account authentication for the trading session;
- private account snapshots;
- private account streams;
- orders, positions and deals;
- execution and reconciliation capabilities;
- provider-specific trading errors and transport for those capabilities.

Broker Service SHALL not own cTrader Market Data catalogue, Market Data
subscriptions, Market Data reconnect, or canonical Market Data normalization.

## 10. Failure Isolation

The two service sessions are intentionally independent:

| Failure | Expected effect |
|---|---|
| Broker Service crash | Trading/account operations unavailable; Market Data session may continue |
| Market Data crash | Market facts unavailable; Broker private account connectivity may continue |
| cTrader Market Data disconnect | Market Data marks affected facts stale/unavailable and reconnects independently |
| cTrader private account stream disconnect | Broker Service handles private account degradation independently |
| Trading OAuth token expiry | Broker Service trading session requires refresh/reauthentication; Market Data unaffected |
| Market Data token expiry | Market Data session refreshes or becomes unavailable; Broker Service unaffected |
| Market subscription failure | Affected Market Data subscription fails explicitly; unrelated broker operations continue |
| Shared library defect | Both services may be affected at build/runtime; the library remains stateless and bounded to reduce coupling |

No architecture can eliminate provider-wide outages, but this design avoids
unnecessarily coupling trading/account service health to analytical Market Data
service health.

## 11. Shared Library Boundary

The shared library is a technical module, not a service and not a domain module.

Allowed content:

- cTrader protocol message definitions or generated protobuf bindings;
- serialization/deserialization helpers;
- connection bootstrap primitives;
- application-auth message construction;
- request correlation identifiers;
- low-level protocol error parsing;
- numeric protocol decoding helpers where provider-specific.

Disallowed content:

- Spring service beans with business ownership;
- secret persistence;
- OAuth callback controllers;
- token repositories;
- account-binding aggregates;
- Market Data subscriptions;
- reconnect schedules;
- canonical `Market`, `TickerEvent`, or `OhlcEvent` models;
- event bus or central connection manager.

Each service wraps the library in its own adapter and supplies its own:

- credentials or credential references;
- account authorization;
- session lifecycle;
- subscription policy;
- persistence;
- resilience policy;
- observability context.

## 12. Architecture Options

### Option A - Broker Service owns cTrader Market Data transport

```text
cTrader -> Broker Service -> internal Market Data transport -> Market Data
```

Assessment:

- implementation complexity: `MEDIUM`;
- runtime complexity: `HIGH`;
- cross-service coupling: `HIGH`;
- failure coupling: `HIGH`;
- security complexity: `MEDIUM`;
- duplicate infrastructure: `LOW`;
- subscription ownership clarity: `LOW`;
- future provider extensibility: `MEDIUM`.

Rejected because it conflicts with ADR-006, ADR-011, ADR-019 and ADR-020 and
makes Broker Service a universal cTrader provider runtime. It also creates dual
subscription ownership.

### Option B - Market Data owns cTrader Market Data transport

```text
                 cTrader
               /         \
              /           \
 Broker Service          Market Data
 private/trading         market feeds
```

Assessment:

- implementation complexity: `MEDIUM`;
- runtime complexity: `MEDIUM`;
- cross-service coupling: `LOW`;
- failure coupling: `LOW`;
- security complexity: `MEDIUM`;
- duplicate infrastructure: `MEDIUM`;
- subscription ownership clarity: `HIGH`;
- future provider extensibility: `HIGH`.

This preserves the intended service boundary. The main cost is a separate
Market Data authorization/token path and potentially duplicated low-level
protocol code.

### Option C - Shared cTrader technical library with independent sessions

```text
shared cTrader technical primitives
        /                 \
       /                   \
Broker Service          Market Data
private session         market-data session
```

Assessment:

- implementation complexity: `MEDIUM`;
- runtime complexity: `MEDIUM`;
- cross-service coupling: `LOW`;
- failure coupling: `LOW`;
- security complexity: `MEDIUM`;
- duplicate infrastructure: `LOW`;
- subscription ownership clarity: `HIGH`;
- future provider extensibility: `HIGH`.

Recommended. It preserves Option B's ownership while reducing duplication of
protocol-level infrastructure. The shared library remains stateless and does
not own tokens, sessions, subscriptions, or domain facts.

### Option D - Separate cTrader Integration Service

```text
cTrader Integration Service
        -> Broker facts
        -> Market facts
```

Assessment:

- implementation complexity: `HIGH`;
- runtime complexity: `HIGH`;
- cross-service coupling: `HIGH`;
- failure coupling: `MEDIUM`;
- security complexity: `HIGH`;
- duplicate infrastructure: `LOW`;
- subscription ownership clarity: `MEDIUM`;
- future provider extensibility: `HIGH`.

Rejected for V1. It introduces another service and central provider authority
without evidence that the current bounded two-service design is insufficient.

## 13. Product Architecture Tests

### Consume cTrader Market Data without cTrader execution

The recommended architecture remains valid. Market Data can authorize and
consume cTrader market facts independently while Broker Service is unused or
connected to another execution provider.

### Execute through cTrader but analyze another provider

The recommended architecture remains valid. Trading Core can bind execution to
cTrader while Market Intelligence receives Market Data from an explicitly
configured different provider. Provider identity and canonical market identity
remain separate.

### Multiple brokers and Market Data providers

The architecture scales by adding independent provider adapters:

```text
Broker Service
    -> Kraken / cTrader / other execution providers

Market Data Service
    -> Kraken / cTrader / reference providers / other data providers
```

The cTrader pairing used by the FTMO dry-run is a configuration choice, not a
permanent equality between broker provider and Market Data provider.

## 14. Security Invariants

- Access tokens, refresh tokens, client secrets, and provider credentials remain
  inside the service that owns the corresponding authorization.
- Market Data does not read Broker Service secret storage.
- Broker Service does not read Market Data secret storage.
- Service-specific credentials are encrypted using the existing secure
  configuration/storage pattern for that service.
- Account identifiers are used only as authorization/routing context where
  required by cTrader and are not promoted into Market Data business ownership.
- Provider DTOs and raw protocol errors remain inside the relevant provider
  adapter or shared technical library boundary.
- No token or credential is returned through Market Data, Trading Core, frontend,
  logs, metrics, or Market Intelligence contracts.
- No cTrader execution capability is introduced by this ADR.

## 15. Preserved Invariants

This revised ADR preserves the following Story 0167 invariants:

- FTMO is not cTrader.
- Provider DTOs remain inside provider adapters.
- Credentials never leak.
- Market Intelligence remains provider-neutral.
- Bid and ask remain separate.
- Forming bars are not closed bars.
- cTrader DOM is out of Story 0167.
- Reference-market data remains later.
- Risk is unchanged.
- TradePlan is unchanged.
- Market Thesis authority is unchanged.
- `FeatureProjectionV1` is unchanged.
- S1 semantics are unchanged.
- No LIVE order execution is added.

## 16. Consequences

### Positive

- Market Data remains the stable owner of market observation and streaming.
- Broker Service remains focused on trading/account connectivity.
- Market Data can be consumed without cTrader execution.
- cTrader execution can be paired with another analytical provider.
- Broker and Market Data failures are isolated.
- The shared technical library reduces protocol duplication without creating a
  central cTrader runtime.
- Kraken's existing independent service architecture remains unchanged.

### Trade-offs

- Market Data needs its own cTrader authorization and secure token boundary.
- The same cTrader account may require separate service-specific authorization
  material.
- The shared library becomes a build-time dependency of two services.
- cTrader application/account authentication primitives must be maintained in
  two service-owned session lifecycles.
- Multi-account Market Data support remains a later decision.

## 17. Scope Boundary

This ADR authorizes the architecture required for Story 0167:

- Market Data-owned cTrader Market Data transport;
- Market Data-owned cTrader Market Data authorization for the configured source;
- Broker Service-owned cTrader trading/account connectivity;
- a narrow shared cTrader technical library;
- independent service sessions and failure domains;
- canonical Market Data normalization and subscription ownership in Market Data;
- read-only Market Data operations.

It does not authorize:

- cTrader order placement, amendment, cancellation, close, or execution;
- FTMO Challenge rules or lifecycle;
- Risk, TradePlan, or human authorization changes;
- Market Thesis redesign;
- Tradeability policy or threshold changes;
- reference-market or futures data;
- DOM or advanced order flow;
- AI or S1 changes;
- a generic event bus or central provider runtime;
- frontend redesign;
- automatic cTrader account selection;
- provider-specific fields in Market Intelligence.

## 18. Required Follow-Up

After human approval of this revised ADR:

1. Amend ADR-073 if its statement that Broker Service owns the complete cTrader
   authentication boundary is understood to include Market Data authorization.
2. Define the shared technical library module and its exact allowed contents.
3. Define the Market Data cTrader authorization/source-binding contract without
   coupling it to Trading Core account ownership.
4. Produce the Story 0167 Implementation Plan covering symbol mapping, cTrader
   operations, session lifecycle, freshness, provenance, tests, and controlled
   runtime validation.
5. Do not begin implementation until the Implementation Plan is explicitly
   approved.

## 19. Approval Gate

```text
ADR-077_STATUS = ACCEPTED
CURRENT_ADR_VERDICT = REVISE_ADR_077_SHARED_TECHNICAL_LIBRARY
HUMAN_ARCHITECTURE_APPROVAL = COMPLETED
STORY_0167_IMPLEMENTATION = BLOCKED_UNTIL_APPROVED_PLAN
```
