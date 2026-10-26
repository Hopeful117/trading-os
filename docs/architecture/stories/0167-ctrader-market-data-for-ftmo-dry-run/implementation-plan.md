# Implementation Plan

## Overview

Story 0167 will add cTrader Market Data support without turning Broker Service
into a cTrader Market Data runtime.

The implementation follows accepted ADR-077:

```text
shared cTrader protocol primitives
shared secret-crypto primitives
        /                 \
       /                   \
 Broker Service          Market Data
trading session         market-data session
private account         market feeds
```

Broker Service keeps its existing trading/account authentication and private
account authority. Market Data owns its own read-only cTrader Market Data
authorization, token lifecycle, session, subscriptions, canonical mapping,
freshness, MarketState, and provenance.

No service-to-service dependency between Broker Service and Market Data will be
introduced. The shared cTrader library is a stateless protocol-only module. A
separate stateless `secret-crypto` module may provide cryptographic primitives
to both services; token storage, token lifecycle, and credential ownership stay
inside each consuming service.

Implementation is not authorized until this plan is explicitly approved.

## Governing Decisions

- ADR-077 is accepted with `REVISE_ADR_077_SHARED_TECHNICAL_LIBRARY`.
- ADR-073 has a narrow clarification: Broker Service owns trading cTrader
  authentication and account binding, while Market Data may own separate
  read-only Market Data authorization.
- ADR-006, ADR-011, ADR-019, and ADR-020 keep Market Data acquisition,
  normalization, Market State, and subscriptions in `market-data`.
- FTMO remains a challenge/business context; cTrader remains a technical
  provider/platform.
- `execution provider != analytical Market Data provider` remains supported.
- Risk, TradePlan, execution, Market Thesis, Tradeability policies,
  `FeatureProjectionV1`, and S1 remain unchanged.

## Dependency Direction

The dependency graph will be:

```text
ctrader-openapi-technical
        ^                    ^
        |                    |
broker-service          market-data

secret-crypto
        ^                    ^
        |                    |
 broker-service          market-data
```

There must be no dependency in either direction between `broker-service` and
`market-data` for this Story.

The shared module must not depend on Spring, JPA, service domain classes,
Market Data models, Broker Service models, or Trading Core models.

The `secret-crypto` module must also remain framework-independent and stateless.
It may expose AES-GCM/key-provider primitives only. It must not contain token
models, repositories, OAuth flows, service configuration, or credential
lifecycle behavior.

## Shared Technical Library

### Module placement

Create a new standalone Maven jar at:

```text
ctrader-openapi-technical/
```

The repository has independent Maven services rather than a root Maven reactor,
so the module will have its own `pom.xml` and be installed or built before the
two consuming services. The exact group/version must follow the existing Maven
coordinates.

### Package placement

Use a provider-technical package such as:

```text
com.hope.trading.ctrader.openapi.protocol
com.hope.trading.ctrader.openapi.transport
com.hope.trading.ctrader.openapi.correlation
com.hope.trading.ctrader.openapi.codec
```

Allowed contents:

- official cTrader payload type constants;
- wire envelope and request/response correlation primitives;
- official protocol message encoders/decoders;
- protobuf or JSON serialization primitives according to the verified current
  Open API transport contract;
- application-auth message construction;
- account-auth message construction without account-binding persistence;
- cTrader relative-price decoding helpers;
- low-level provider error parsing;
- immutable technical transport records.

Forbidden contents:

- Spring beans with service ownership;
- OAuth controllers or callback workflows;
- token repositories or secret storage;
- account-binding aggregates;
- cTrader sessions or reconnect loops;
- subscription registries;
- canonical `Market`, `TickerEvent`, or `OhlcEvent` models;
- Market Data normalization;
- broker/account business logic;
- event-bus behavior.

The library must remain usable by two independent service-owned adapters. It must
not retain sockets, tokens, subscriptions, scheduled tasks, or mutable global
state.

### Reuse of existing Broker Service code

Refactor the low-level parts of:

```text
broker-service/.../ctrader/authentication/JsonWebSocketCTraderOpenApiClient.java
```

only where they are protocol primitives. Keep the following in Broker Service:

- `CTraderAuthenticationService`;
- authorization attempt persistence;
- encrypted trading token storage;
- trading account binding;
- private account snapshot capability;
- trading session lifecycle;
- broker-specific account mapping.

The existing Broker Service behavior must remain unchanged from its public
contract perspective.

## Market Data Authorization and Token Lifecycle

### Ownership

Market Data will own a separate read-only cTrader Market Data authorization.
This is an authorization to access market facts through a configured cTrader
account context, not a Trading Core account binding.

Market Data will not read Broker Service secret storage and will not copy a
Broker Service access or refresh token.

### Configuration

Add a Market Data-owned configuration boundary for:

- cTrader application client ID;
- cTrader application client secret reference;
- redirect URI;
- authorization endpoint;
- token endpoint;
- WebSocket endpoint;
- demo/live environment;
- configured cTrader Market Data account ID;
- encrypted token-store key reference;
- authorization state TTL;
- refresh and connection timeouts.

The client secret and encryption key must come from deployment secrets. They must
not be committed, logged, returned by APIs, or stored in plaintext configuration.

### Authorization flow

Implement a bounded internal/operator flow in Market Data without frontend work:

```text
authorization initiation
    -> cTrader consent callback
    -> authorization-code exchange
    -> account discovery
    -> explicit configured source-account validation
    -> encrypted Market Data token persistence
    -> Market Data session authorization
```

The flow must:

- use the read-only `accounts` scope;
- hash and expire server-generated state;
- consume callbacks once;
- reject environment/account mismatches;
- never persist authorization codes;
- require explicit account identity matching;
- expose only safe status and failure codes.

The endpoint surface must be internal/operator-protected and must not create a
user-facing frontend feature.

### Token storage

Add a Market Data-owned encrypted token store using the service's secure
configuration boundary. The store must support:

- encrypted access token;
- encrypted refresh token;
- expiry timestamp;
- provider environment;
- configured external account ID;
- credential version;
- updated timestamp;
- revocation/invalid state.

The token store must not be placed in the shared library. Refresh must rotate
both access and refresh values atomically because cTrader invalidates the old
refresh token when a new one is issued.

### Shared cryptographic primitives

Create a separate standalone Maven jar at:

```text
secret-crypto/
```

This module may extract the generic AES-GCM and environment-key primitives from
Broker Service. It must not be part of `ctrader-openapi-technical` and must not
own token storage or credential lifecycle. Broker Service and Market Data keep
separate repositories, master-key configuration, token models, and rotation
policies while using the common cryptographic primitives.

## Market Data Session Lifecycle

Create a Market Data-owned cTrader session adapter in:

```text
market-data/src/main/java/com/hope/trading/market_data/ctrader/
```

The session lifecycle is:

```text
DISCONNECTED
    -> CONNECTING
    -> APPLICATION_AUTHENTICATING
    -> ACCOUNT_AUTHENTICATING
    -> READY
    -> SUBSCRIBING
    -> STREAMING
    -> STALE / REAUTHENTICATION_REQUIRED / UNAVAILABLE
    -> RECONNECTING
    -> RESUBSCRIBING
```

Market Data owns:

- socket/session instance;
- connection state;
- request correlation use;
- active desired subscriptions;
- provider subscription mapping;
- reconnect backoff;
- resubscription after successful reauthentication;
- stale transition after provider silence;
- explicit shutdown behavior.

The session must not be shared with Broker Service.

## Provider Registration

Extend the existing Market Data provider routing:

- add `CTRADER` as a technical `MarketProvider` value;
- do not use `FTMO` as the cTrader adapter identity;
- register `CTraderMarketData implements MarketDataProvider`;
- register `CTraderDataStreamProvider implements MarketDataStreamProvider`;
- preserve Kraken registration and behavior unchanged.

The provider adapter will use the configured Market Data source binding rather
than a Trading Core `Account` or `BrokerAccount` relationship.

## Canonical Instrument Mapping

Market Data owns the cTrader instrument catalogue and mapping.

The mapping flow is:

```text
cTrader light symbol discovery
    -> full symbol metadata
    -> explicit canonical mapping
    -> Market(provider=CTRADER, providerSymbol=opaque cTrader identity)
```

Normalize and preserve, where provided:

- cTrader symbol ID;
- cTrader symbol name;
- canonical symbol;
- product type;
- asset class;
- base/quote or equivalent semantics;
- digits and pip position;
- tick size where authoritative;
- min/max/step volume;
- price and quantity precision;
- schedule/trading availability;
- source account/environment provenance.

Unknown or ambiguous mappings fail closed. Name similarity must not create an
automatic mapping to an underlying future or reference market.

Because cTrader symbol IDs are server/environment-specific, the provider ID and
source environment must remain in provenance. Multiple account-specific
catalogues remain out of scope for V1.

## Historical Trendbars

Implement cTrader historical acquisition through the existing provider-neutral
Market Data path.

Use the verified official operations:

- `ProtoOASymbolByIdReq` / response for full metadata;
- `ProtoOAGetTrendbarsReq` / response for history.

The provider adapter must:

- map required canonical `OhlcInterval` values to cTrader periods;
- reject unsupported interval mappings explicitly;
- use bounded `fromTimestamp`, `toTimestamp`, and `count`;
- paginate where `hasMore` is returned;
- normalize cTrader relative prices using the symbol price semantics;
- sort by open time;
- detect duplicate and conflicting bars;
- preserve provider symbol ID, source time, fetch time, and provenance;
- never fill missing history from current provider state.

Extend the provider port only if required to pass a cutoff/range explicitly,
preferably with a backward-compatible default method. The cutoff-aware path must
be used by strict historical Market Data reads.

## Live Bid/Ask

Use:

- `ProtoOASubscribeSpotsReq` / response;
- `ProtoOASpotEvent`.

The adapter must:

- preserve bid and ask independently;
- decode provider price scaling using symbol metadata;
- reject or mark incomplete events when one side is absent;
- preserve provider timestamp and received timestamp;
- attach canonical instrument and provider provenance;
- publish through the existing Market Data ticker publisher;
- transition the quote to stale/unavailable after the configured silence
  boundary.

The existing `TickerEvent` contract currently has less provenance than
`OhlcEvent`. Before implementation, verify whether it can be extended compatibly
with provider symbol, source ID, and fetched timestamp. If required, add these
fields with compatibility constructors and update Kraken mappers/tests without
changing Kraken semantics.

## Live Trendbars and Closed-Bar Semantics

Use the official cTrader sequence:

```text
spot subscription
    -> live trendbar subscription
    -> provider spot/trendbar events
```

The adapter must distinguish:

```text
FORMING
CLOSED
```

A cTrader bar is eligible for deterministic closed-bar analysis only when:

- the provider event represents the closed trendbar according to the verified
  cTrader contract;
- the bar's effective close boundary is at or before the analysis cutoff;
- the normalized OHLC values are complete;
- the provider/source timestamps are valid;
- the event is not synthetic.

The implementation must not infer closed status merely because an event was
received after the expected time. Boundary tests must cover exact interval
cutoffs, delayed events, reconnect replay, duplicate events, and out-of-order
events.

## Freshness and MarketState

Reuse existing Market Data freshness and status contracts:

- `AVAILABLE`;
- `STALE`;
- `UNAVAILABLE`;
- `UNSUPPORTED`;
- `INSUFFICIENT_DATA` where applicable.

Market Data owns the translation of cTrader source conditions into:

- `MarketState`;
- `TradingStatus`;
- `MarketAvailability`;
- closure reason;
- last provider update.

`OPEN` may be asserted only when authoritative provider facts support immediate
execution availability. A schedule alone must not be presented as complete
tradability proof if cTrader exposes a more specific availability state.

## Subscription Lifecycle

Follow the existing `MarketDataStreamProvider` and Kraken lifecycle patterns,
without copying Kraken DTOs or provider assumptions.

Market Data owns one registry for:

- market;
- stream type;
- interval where applicable;
- source account/environment;
- reference count or equivalent demand ownership.

The registry must:

- avoid duplicate provider subscriptions;
- subscribe only when demand exists;
- unsubscribe when demand reaches zero;
- replay active subscriptions after reconnect;
- avoid unbounded inbound/outbound queues;
- preserve unrelated subscriptions during partial failures;
- classify intentional disconnect separately from provider failure.

Broker Service has no registry for these subscriptions.

## Failure Handling

Normalize failures at the Market Data adapter boundary:

- unavailable;
- stale;
- unsupported;
- invalid request;
- provider error;
- authentication error;
- reauthentication required;
- connection timeout;
- source account mismatch.

Rules:

- no retry of invalid requests;
- bounded retry for safe read operations and reconnect;
- no stale quote remains current after provider silence;
- token refresh is serialized per Market Data credential;
- failed refresh transitions to reauthentication required;
- reconnect must reauthenticate before resubscription;
- partial symbol/history failures do not fabricate facts;
- raw provider errors are not exposed to Market Intelligence or frontend.

## Security Boundaries

- Market Data client secrets and token encryption keys are deployment secrets.
- Access and refresh tokens are encrypted at rest in Market Data's own store.
- Tokens are never stored in the shared library.
- Tokens are never sent to Broker Service or Trading Core.
- Tokens, authorization codes, and client secrets are never logged.
- Internal/operator authorization endpoints are protected.
- The configured cTrader account ID is treated as authorization context, not a
  user account identity.
- No cTrader execution scope is requested by Story 0167.
- No execution operation is registered in Market Data.

## Files to Create

### Shared technical module

- `ctrader-openapi-technical/pom.xml`.
- `ctrader-openapi-technical/src/main/java/com/hope/trading/ctrader/openapi/...`.
- Focused protocol, codec, correlation, and numeric-decoding tests.

### Shared security primitives

- `secret-crypto/pom.xml`.
- `secret-crypto/src/main/java/com/hope/trading/security/crypto/...`.
- Focused encryption, decryption, key-version, invalid-key, and tamper tests.

### Market Data

- cTrader configuration properties;
- Market Data authorization state and token-store ports/adapters;
- encrypted Market Data token persistence and migration;
- cTrader Market Data session/lifecycle adapter;
- cTrader Market Data provider and stream provider;
- cTrader symbol, metadata, trendbar, spot, and session mappers;
- operator/internal authorization endpoints if required by existing security
  conventions;
- cTrader-specific focused tests and fixtures.

## Files to Modify

- `market-data/pom.xml`: depend on the shared technical jar.
- `broker-service/pom.xml`: depend on the shared technical jar and refactor only
  protocol primitives out of the existing cTrader client.
- `broker-service/pom.xml` and `market-data/pom.xml`: depend on `secret-crypto`.
- Broker Service secret adapters: use the extracted cryptographic primitives
  without changing the existing public secret-store behavior.
- `MarketProvider`: add `CTRADER` as a technical provider identity.
- `MarketDataProvider`: add only the cutoff/range capability required by cTrader
  historical reads, preserving existing implementations through defaults where
  possible.
- `Market`, `MarketConstraints`, `MarketState`, `TickerEvent`, or related
  canonical contracts only when compatibility inspection proves the existing
  contract cannot preserve required metadata/provenance.
- Existing Kraken mappers/tests only for compatibility constructors or shared
  provenance fields; no Kraken behavior change.
- Relevant Market Data configuration and migration files.
- ADR-073 implementation references only if the accepted clarification requires
  a documentation link update.

No `market-intelligence`, `trading-core`, frontend, Risk, TradePlan, or execution
implementation changes are planned.

## Documentation Reconciliation

Update only canonical documentation affected by implementation:

- Story 0167 implementation report;
- ADR-077 references if module/package names differ from this plan;
- ADR-073 cross-reference if the narrow clarification is implemented;
- Market Data operational configuration documentation for the cTrader source,
  secret requirements, supported intervals, and runtime validation status.

Do not claim live Spotware validation without approved credentials and evidence.

## Test Plan

### Shared technical library

- wire envelope encoding/decoding;
- request correlation;
- application/account auth primitives;
- protocol error classification;
- relative price decoding;
- malformed and incomplete payloads;
- no mutable session or token state in the library.

### Market Data authorization

- state generation and one-time callback consumption;
- expired/replayed/cross-environment callback rejection;
- explicit source-account matching;
- encrypted token persistence;
- atomic refresh-token rotation;
- refresh expiry and reauthentication state;
- no credential leakage in response/log/error paths.

### Instrument mapping

- unique mapping;
- unknown symbol;
- ambiguous symbol;
- provider/server-specific symbol ID;
- metadata and precision normalization;
- volume constraints;
- schedule and tradability state;
- canonical identity preservation.

### Historical trendbars

- interval mapping;
- price scaling;
- pagination and `hasMore`;
- deterministic ordering;
- duplicates and conflicts;
- gaps;
- cutoff exclusion;
- forming/current bar exclusion;
- unavailable and provider errors;
- provenance.

### Live quotes and bars

- bid-only and ask-only events;
- complete bid/ask event;
- timestamp and received-time handling;
- stale transition after silence;
- forming versus closed bars;
- exact cutoff boundary;
- duplicate and out-of-order events;
- reconnect and resubscribe;
- subscription reference counting;
- partial subscription failure.

### Regression and compatibility

- existing Kraken unit and integration tests;
- existing Market Data application tests;
- canonical Market Data publisher and persistence tests;
- Market Intelligence compatibility tests using normalized cTrader OHLC;
- no provider-specific type crossing the Market Data boundary;
- no changes to Risk, TradePlan, execution, Market Thesis, or S1 semantics.

## Validation Commands

These are planned validation commands, not completed results:

```bash
cd /home/ludo/workspace/trading-os/ctrader-openapi-technical && mvn test && mvn verify
cd /home/ludo/workspace/trading-os/broker-service && mvn test && mvn verify
cd /home/ludo/workspace/trading-os/market-data && mvn test && mvn verify
cd /home/ludo/workspace/trading-os/market-intelligence && mvn test && mvn verify
cd /home/ludo/workspace/trading-os && git diff --check
```

If SonarQube is configured for the affected modules, include the applicable
quality gate and new-code measures in the Implementation Report.

## Spotware Runtime Validation Strategy

Separate implementation validation from live runtime validation.

### Without live credentials

- official contract fixtures;
- deterministic JSON/protobuf wire fixtures;
- recorded symbol metadata fixtures;
- recorded historical trendbar fixtures;
- recorded spot and closed-trendbar events;
- deterministic reconnect and token-expiry simulations.

### With approved credentials

Validate against the configured cTrader demo/live environment only through an
explicit human-controlled procedure:

- Market Data OAuth with `accounts` scope;
- explicit source-account validation;
- symbol catalogue retrieval;
- one supported instrument metadata read;
- bounded historical trendbar read;
- live bid/ask subscription;
- live closed-bar subscription;
- disconnect/reconnect/resubscribe;
- token expiry/refresh behavior;
- stale/unavailable transition;
- no order or mutation request.

The report must distinguish:

```text
IMPLEMENTATION_COMPLETE
LIVE_CTRADER_RUNTIME_VALIDATED
```

## Risks and Stop Conditions

- If the shared library requires tokens, sessions, subscriptions, reconnect
  policy, canonical mapping, or normalization, stop and request architecture
  review.
- If Market Data cannot obtain a secure service-owned authorization without
  reading Broker Service secrets, stop and request architecture review.
- If cTrader requires a different account ownership model than this plan,
  stop and request architecture review.
- If a cTrader operation cannot provide cutoff-safe or closed-bar semantics,
  expose it as unsupported/unavailable rather than infer data.
- If adding canonical provenance requires changing Market Intelligence semantics,
  stop; Story 0167 is not authorized to redesign that boundary.
- If a new inter-service event bus, shared runtime, or frontend workflow becomes
  necessary, stop and request architecture review.

## Validation Checklist

- [x] ADR-077 accepted and referenced.
- [x] ADR-073 narrow clarification applied without broader scope expansion.
- [x] Shared cTrader technical library remains stateless and protocol-only.
- [x] Shared `secret-crypto` module remains stateless and contains no credentials,
  tokens, repositories, or lifecycle behavior.
- [x] No Broker Service to Market Data service dependency is introduced.
- [x] Market Data owns cTrader transport and subscriptions.
- [x] Market Data owns separate authorization and token lifecycle.
- [x] Broker Service trading/account behavior remains unchanged.
- [x] Canonical mapping is explicit and ambiguity-safe for implemented facts.
- [x] Historical OHLC is cutoff-safe and deterministic for normalized closed bars.
- [x] Bid/ask remain separate and provenance-bearing.
- [x] Forming and closed bars remain distinct.
- [x] Reconnect/resubscribe is bounded and tested; provider-silence recovery still
  requires authenticated runtime validation.
- [x] Kraken regression passes.
- [x] Security and token-leakage tests pass.
- [x] Planned Maven validation passes for implemented modules; runtime validation
  remains pending.
- [x] Runtime validation status is truthful.
- [x] Documentation reconciliation is complete.
- [x] `git diff --check` passes.

The following acceptance areas remain partial rather than complete: deployment
bootstrap does not implement OAuth account discovery, scope validation, or source
binding; the current canonical persisted constraints model cannot represent
cTrader maximum/step volume facts, so those facts remain unsupported; and
authoritative session/tradability plus reconnect recovery require credentialed
runtime validation.

## Recommendation

The plan was approved before implementation. The security clarification above
is part of the approved execution direction: use shared cryptographic
primitives, but keep credential storage and lifecycle service-owned. No
service-to-service credential access is permitted.
