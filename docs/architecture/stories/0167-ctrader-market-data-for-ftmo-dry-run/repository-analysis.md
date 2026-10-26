# Repository Analysis

## Story Understanding

Story 0167 requests the minimum provider-neutral, read-only cTrader Market Data
capability required for a future FTMO/cTrader PAPER dry-run. The implementation
must replace the current Kraken-oriented analytical source for configured
cTrader execution instruments without exposing cTrader DTOs, credentials,
transport details, or FTMO challenge semantics outside the provider boundary.

The Story explicitly excludes LIVE execution, FTMO challenge rules, Risk and
TradePlan changes, Market Thesis redesign, Tradeability threshold tuning, AI,
reference-market data, DOM/order flow, and frontend redesign.

## Repository Baseline

| Item | Current value |
|---|---|
| Branch | `main` |
| `HEAD` | `8e7483a1a2c46698804c7e66309e160855124c71` |
| `origin/main` | `8e7483a1a2c46698804c7e66309e160855124c71` |
| Working tree | clean before Story 0167 artifact creation |
| Story artifact | `docs/architecture/stories/0167-ctrader-market-data-for-ftmo-dry-run/story.md` |
| Story status | `Draft`, human-approved for Repository Analysis |
| Story 0166 | merged and confirmed in current `main` |
| DevLog | Story Agent attempted; unavailable due upstream rate limit / `LLM_PROVIDER_ERROR` |

The only current worktree addition is the Story 0167 artifact and this analysis
artifact. No implementation files have been changed.

## Architecture Authority

The relevant accepted decisions establish these boundaries:

- ADR-006 assigns market catalog, metadata, constraints, state, realtime
  streams, provider acquisition, and normalization to Market Data Service.
- ADR-020 assigns Market Data acquisition/normalization to Market Data Service
  and keeps Market Intelligence as an orchestration and interpretation layer.
- ADR-030 assigns provider authentication, transport, mapping, errors, and
  communication resilience to Broker Service and prohibits provider DTOs from
  leaving that boundary.
- ADR-053 defines `provider + canonical symbol + productType` identity and the
  generic `providerSymbol` field for adapter-only request identity.
- ADR-071 separates FTMO as the challenge/business provider from cTrader as the
  execution platform and assigns cTrader technical authority to Broker Service.
- ADR-073 assigns cTrader OAuth, token storage, account binding, and cTrader
  protocol types to Broker Service.
- ADR-074 assigns cTrader read-only account synchronization and provider
  transport to Broker Service, while explicitly excluding Market Data
  integration from Story 0148.
- ADR-077 has now been proposed to define the bounded internal cTrader Market
  Data transport boundary while preserving canonical Market Data ownership.

The Story is compatible with the existing Market Data authority, but it exposes
a missing cross-service boundary: Market Data needs cTrader authenticated
access while Broker Service owns the token and current cTrader transport.

## Affected Modules

- `market-data`: primary implementation surface for provider registration,
  catalog synchronization, canonical Market records, historical OHLC, live
  quotes, bar events, freshness, session state, and subscriptions.
- `broker-service`: existing cTrader authentication, secret access, account
  binding, and WebSocket transport. It may require a new provider-neutral
  technical boundary only if reuse cannot be achieved without leaking secrets
  or duplicating lifecycle ownership.
- `market-intelligence`: compatibility verification only. It already consumes
  provider-neutral Market Data contracts and must not gain cTrader types.
- `trading-core`: compatibility verification only. No Risk, TradePlan,
  execution, or FTMO business changes are in scope.

No frontend module or new microservice is authorized by this Story.

## Existing Market Data Architecture

### Provider ports and routing

`MarketDataProvider` currently defines the provider-neutral synchronous port:

- `getMarkets()`;
- `getName()`;
- `acquireCurrentSnapshot(Market)`;
- `findOhlcHistory(Market, OhlcInterval, int)`;
- optional `MarketHistorySnapshot` enrichment.

`MarketDataStreamProvider` defines the provider-neutral streaming lifecycle:

- `connect()`;
- `disconnect()`;
- `subscribe(List<Market>, MarketStreamRequest)`;
- `unsubscribe(List<Market>, MarketStreamRequest)`.

`MarketHistoryService`, `MarketPriceSnapshotService`, and subscription services
route providers through `MarketProvider` and provider implementations. Kraken is
the only registered `MarketDataProvider` implementation. `MarketProvider`
contains `FTMO`, but does not contain `CTRADER`, and FTMO must not be used as a
technical platform identity.

### Canonical market model

`Market` already contains:

- generated canonical market ID;
- provider;
- canonical symbol;
- provider symbol;
- base and quote assets;
- product type;
- embedded `MarketConstraints`;
- embedded `MarketState`.

This is compatible with ADR-053. `MarketConstraints` currently supports
minimum order size, minimum cost, tick size, quantity precision, and price
precision. cTrader-specific volume step, maximum volume, schedule, and other
constraints may require a bounded generic model extension if they are needed by
the canonical Market Data contract. Such an extension must remain provider
neutral and must not become Risk policy.

`MarketState` already represents trading status, a `tradable` flag, closure
reason, and last update. ADR-006 defines OPEN as immediately executable. The
current `MarketStateBuilder` has only a simple available/unavailable initial
mapping; authoritative cTrader session state will need a more precise
normalization path.

### Historical OHLC and freshness

`OhlcEvent` already preserves:

- market ID;
- provider;
- canonical symbol;
- interval;
- open and close times;
- OHLC values;
- optional volume, VWAP, and trade count;
- closed flag;
- occurrence time;
- synthetic flag;
- source ID;
- fetched time;
- provider symbol.

`OhlcInterval` currently supports 1m, 5m, 15m, 30m, 1h, 4h, 12h, 1d, and 1w.
The existing Market Intelligence Trend Context, Structure, and Momentum
contracts must determine which of these are required; the adapter must not
invent an arbitrary FTMO timeframe set.

`MarketFactsService`, `HistoricalOhlcHistoryService`, `MarketDataReadiness`,
`MarketFactStatus`, and `OhlcHistoryNormalizer` already provide cutoff-aware
closed-candle filtering, duplicate/conflict detection, gap handling, freshness
classification, and explicit `AVAILABLE`, `INSUFFICIENT_DATA`, `STALE`,
`UNAVAILABLE`, and `UNSUPPORTED` states.

The primary compatibility risk is that cTrader trendbars must be mapped into
these existing closed-bar and cutoff-safe semantics without treating a current
or forming trendbar as closed.

### Live quote and stream contracts

`TickerEvent` already preserves bid, ask, last, volume, occurrence time, market
ID, provider, and canonical symbol. It is suitable for cTrader bid/ask without
collapsing the spread.

The existing Kraken stream implementation demonstrates WebSocket connection,
subscription, event routing, and termination diagnostics. Its provider DTOs and
transport are isolated below `market-data/.../kraken` and must not be copied
into a cTrader-specific public contract.

### Existing APIs

The canonical APIs already include:

- `GET /api/v1/markets`;
- `GET /api/v1/markets/{marketId}`;
- `POST /api/v1/markets/synchronize`;
- `POST/DELETE /api/v1/markets/{marketId}/subscriptions`;
- `GET /api/v1/markets/{marketId}/ohlc`;
- internal cutoff-aware historical OHLC and Market Facts endpoints.

No cTrader-specific public endpoint is required by the current architecture.

## Existing Kraken Implementation

`KrakenMarketData` is the only `MarketDataProvider` implementation. It currently
handles:

- Spot market discovery;
- Kraken Futures perpetual discovery;
- provider-symbol resolution;
- current ticker acquisition;
- Spot and Futures historical OHLC;
- interval mapping;
- closed-bar marking for historical responses;
- normalization and missing-interval handling.

Kraken WebSocket classes implement live stream lifecycle and provider event
mapping. Existing tests cover Kraken market data, Futures routing, OHLC mappers,
stream lifecycle, termination, and application contracts.

The required regression invariant is:

```text
ADDING CTRADER MUST NOT CHANGE KRAKEN BEHAVIOR
```

## Existing cTrader Infrastructure

### Authentication and account binding

Broker Service owns cTrader authentication through:

- `CTraderAuthenticationService`;
- `CTraderAuthenticationController`;
- encrypted secret references;
- `CTraderToken` and `CTraderTokenCodec`;
- explicit external account binding;
- `BrokerConnection` provider/environment/external-account identity.

The existing OAuth flow requests the `accounts` scope. Tokens are persisted only
through Broker Service secret management. Public responses expose authorization
status and account IDs, not token material.

### cTrader provider and transport

`CTraderBrokerProvider` currently registers only the
`CTraderAuthoritativeSnapshotCapability`.

`CTraderOpenApiClient` currently exposes:

- authorized account discovery;
- account authorization;
- read-only account observation.

`JsonWebSocketCTraderOpenApiClient` contains a private, one-shot `Session` that:

- opens a WebSocket;
- sends JSON-encoded payload types;
- correlates synchronous request/response messages by `clientMsgId`;
- closes the socket after each operation.

It currently implements application/account authentication and read-only account
snapshot messages, including trader, assets, symbols, reconcile, PnL, and deals.
It does not expose Market Data operations, persistent subscriptions, reconnect,
resubscribe, or shared connection lifecycle.

### Ownership gap

`market-data` has no code dependency on Broker Service and no provider-neutral
credential/token client for cTrader. Broker Service has no Market Data capability
in its current provider contract.

Therefore the following options exist:

1. Market Data duplicates cTrader token/transport access, which violates ADR-030,
   ADR-073, and the Story security boundary.
2. Broker Service exposes a new provider-neutral Market Data capability or
   streaming API, which risks moving Market Data authority into Broker Service
   and requires a deliberate cross-service architecture decision.
3. A shared cTrader transport/runtime is extracted or introduced, which is a
   new cross-service stateful runtime decision.
4. Market Data owns a cTrader adapter while Broker Service supplies an opaque,
   securely resolved provider session through an approved technical boundary.

The repository does not currently establish which of options 2-4 is approved.
The current private one-shot Session cannot be reused directly for the required
live Market Data lifecycle.

## Official cTrader Contracts Verified

The official cTrader Open API documentation was consulted through the current
cTrader Open API documentation source.

Relevant contracts identified for implementation planning are:

- `ProtoOAApplicationAuthReq` / `ProtoOAApplicationAuthRes` for application
  authorization;
- `ProtoOAAccountAuthReq` / `ProtoOAAccountAuthRes` for account authorization;
- `ProtoOASymbolsListReq` / response and `ProtoOASymbolByIdReq` /
  `ProtoOASymbolByIdRes` for symbol discovery and full symbol metadata;
- `ProtoOAGetTrendbarsReq` / `ProtoOAGetTrendbarsRes` for historical trendbars;
- `ProtoOASubscribeSpotsReq` / `ProtoOASubscribeSpotsRes` and
  `ProtoOASpotEvent` for live bid/ask;
- `ProtoOASubscribeLiveTrendbarReq` and the corresponding live trendbar events
  for current bar updates. The documentation requires spot subscription before
  live trendbar subscription.

The documented cTrader symbol model provides, where available:

- symbol ID and symbol name;
- price digits and pip position;
- minimum, maximum, and step volume;
- trading schedule intervals;
- short-selling and trading constraints;
- stop and take-profit distances;
- commission and related instrument metadata.

The documented spot event bid and ask fields are optional and use provider price
scaling based on symbol digits. A quote event with only one side must not be
treated as a complete spread observation.

The implementation plan must record the exact payload names, numeric scaling,
event fields, timestamp semantics, and supported trendbar periods used by code.

## Market Intelligence Compatibility

Market Intelligence consumes provider-neutral `Market`, `OhlcEvent`, snapshot,
and historical evidence contracts. Trend Context, Market Structure, Momentum,
and Market Thesis should use the same path for cTrader and Kraken.

No Market Intelligence code change is currently justified by the analysis. A
compatibility test may be required after the Market Data implementation proves
that cTrader events carry the required canonical identity, cutoff, closed-bar,
freshness, and provenance fields.

Tradeability is a separate concern. This Story only supplies the normalized
facts required later for spread, freshness, session, and range evidence. It must
not change Story 0166 policies or thresholds.

## Relevant Tests and Validation

Existing Market Data tests cover:

- `KrakenMarketData`;
- Kraken Futures OHLC and ticker routing;
- mapper normalization;
- historical OHLC normalization and persistence;
- market facts and readiness;
- stream subscription lifecycle;
- WebSocket handling;
- termination and reconnect-related diagnostics;
- controller and security contracts.

The affected module uses Maven, Java 21, Spring Boot 4, WebFlux/WebSocket,
JPA/Flyway, and an 80% JaCoCo bundle threshold in `market-data/pom.xml`.

No tests were executed during this repository analysis. Implementation-specific
validation must be defined in the approved Implementation Plan.

## Constraints

- Market Data remains authoritative for normalized market facts, provider
  symbols, market state, freshness, and subscriptions.
- Broker Service remains authoritative for cTrader trading credentials,
  authentication, account binding, private account transport, and provider
  trading errors.
- Market Data remains authoritative for cTrader Market Data authorization,
  Market Data transport, market subscriptions, normalized facts, freshness, and
  provider market-data errors.
- A narrow shared cTrader technical library may contain protocol primitives only;
  it must not own sessions, tokens, subscriptions, or domain facts.
- No OAuth token, refresh token, client secret, cTrader DTO, or raw provider
  error may cross a public provider-neutral contract.
- FTMO must remain a challenge/business context, not a Market Data provider
  identity.
- cTrader execution-market data must not be represented as CME futures or
  underlying-market data.
- `FeatureProjectionV1`, S1 semantics, Risk, TradePlan, execution, and human
  authorization remain unchanged.
- Kraken behavior must remain unchanged.
- No fake production fixture adapter is acceptable.
- Runtime validation must distinguish implementation completion from live
  cTrader credential/runtime validation.

## Architecture Challenge Findings

The initial ADR-077 proposal was challenged because it assigned cTrader Market
Data transport, reconnect, provider subscriptions, and historical/live requests
to Broker Service. Repository evidence supports the challenge:

- ADR-006 assigns market catalogue, metadata, state, and realtime streams to
  Market Data Service.
- ADR-011 assigns dynamic subscription lifecycle and central duplicate
  suppression to Market Data Service.
- ADR-019 explicitly identifies bid/ask, OHLC, freshness, WebSocket streams,
  and market state as Market Data responsibilities and prohibits a direct
  `Market Data Service -> Broker Service` dependency.
- ADR-020 assigns market data acquisition and normalization to Market Data
  Service.
- The Kraken implementation confirms that Market Data owns its provider REST and
  WebSocket adapters independently from Broker Service's private trading
  integration.

The cTrader account ID requirement is an authorization/routing requirement of
the Open API, not evidence that Market Data facts are account-owned business
data. Historical trendbars, symbol metadata, spot subscriptions, and live
trendbar subscriptions require `ctidTraderAccountId`, but Market Data can use a
configured, service-specific read-only source binding.

The recommended architecture is therefore:

```text
shared cTrader protocol primitives
        /                 \
       /                   \
Broker Service          Market Data
trading session         market-data session
private account         market feeds
```

Broker Service and Market Data must use independent service-owned sessions and
credential boundaries. Token material must not be copied between services.

## Architecture Options

### Option A - Broker Service owns cTrader Market Data transport

- implementation complexity: `MEDIUM`;
- runtime complexity: `HIGH`;
- cross-service coupling: `HIGH`;
- failure coupling: `HIGH`;
- security complexity: `MEDIUM`;
- duplicate infrastructure: `LOW`;
- subscription ownership clarity: `LOW`;
- future provider extensibility: `MEDIUM`.

Rejected because it conflicts with ADR-006, ADR-011, ADR-019 and ADR-020 and
creates dual Market Data subscription ownership.

### Option B - Market Data owns cTrader Market Data transport

- implementation complexity: `MEDIUM`;
- runtime complexity: `MEDIUM`;
- cross-service coupling: `LOW`;
- failure coupling: `LOW`;
- security complexity: `MEDIUM`;
- duplicate infrastructure: `MEDIUM`;
- subscription ownership clarity: `HIGH`;
- future provider extensibility: `HIGH`.

This preserves the product boundary but may duplicate low-level protocol code.

### Option C - Shared cTrader technical library with independent sessions

- implementation complexity: `MEDIUM`;
- runtime complexity: `MEDIUM`;
- cross-service coupling: `LOW`;
- failure coupling: `LOW`;
- security complexity: `MEDIUM`;
- duplicate infrastructure: `LOW`;
- subscription ownership clarity: `HIGH`;
- future provider extensibility: `HIGH`.

Recommended. The library remains stateless and limited to protocol primitives;
each service owns its own session, token boundary, lifecycle, and responsibility.

### Option D - Separate cTrader Integration Service

- implementation complexity: `HIGH`;
- runtime complexity: `HIGH`;
- cross-service coupling: `HIGH`;
- failure coupling: `MEDIUM`;
- security complexity: `HIGH`;
- duplicate infrastructure: `LOW`;
- subscription ownership clarity: `MEDIUM`;
- future provider extensibility: `HIGH`.

Rejected for V1 because it creates a new service and central provider authority
without repository evidence that the bounded two-service design is insufficient.

## Responsibility Matrix

| Responsibility | Owner |
|---|---|
| Trading OAuth consent | Broker Service |
| Market Data OAuth consent | Market Data Service |
| Trading token storage | Broker Service |
| Market Data token storage | Market Data Service |
| Trading token refresh | Broker Service |
| Market Data token refresh | Market Data Service |
| Trading application auth | Broker Service |
| Market Data application auth | Market Data Service |
| Trading account auth/binding | Broker Service |
| Market Data account authorization/source binding | Market Data Service |
| Private account stream | Broker Service |
| Order execution | Broker Service |
| Market symbol discovery | Market Data Service |
| Historical trendbars | Market Data Service |
| Live spot subscriptions | Market Data Service |
| Live trendbars | Market Data Service |
| Market subscription registry | Market Data Service |
| Market reconnect/resubscribe | Market Data Service |
| Canonical market mapping | Market Data Service |
| Market freshness | Market Data Service |
| Market state | Market Data Service |

## Risks

- **Credential boundary risk:** Market Data requires its own secure read-only
  authorization path; it must not access Broker Service secret storage or copy
  trading tokens.
- **Shared library coupling:** the technical library must remain stateless and
  protocol-only, otherwise it can become a hidden central cTrader runtime.
- **Stateful transport risk:** the existing cTrader client is a one-shot request
  session and does not provide persistent subscription, reconnect, or
  resubscription semantics.
- **Symbol mapping risk:** cTrader symbol IDs are environment/server-specific;
  name similarity is not sufficient to establish canonical identity.
- **Price scaling risk:** spot and trendbar prices require cTrader symbol-digit
  normalization.
- **Closed-bar risk:** live trendbars and historical response boundaries must not
  introduce a forming bar into deterministic analysis.
- **Session completeness risk:** cTrader schedule metadata may not by itself
  prove immediate order executability for every account/instrument state.
- **Volume semantics risk:** cTrader trendbar volume must not be treated as
  centralized exchange volume without explicit provider evidence.
- **Runtime validation risk:** Spotware activation, credentials, environment,
  and exact FTMO account access may prevent live validation.

## Open Questions

- Which existing secure configuration/secret pattern should Market Data use for
  its read-only cTrader source credential?
- Does ADR-073 require a narrow amendment to distinguish trading authentication
  from Market Data authorization?
- Which exact protocol primitives belong in the shared technical library, and
  which lifecycle code must remain service-local?
- Which cTrader account/environment is authoritative for public market-data
  discovery and subscriptions?
- Are cTrader symbol catalogs account-scoped, and how should the canonical
  Market catalog handle different account/server symbol IDs?
- Which current Trend Context, Structure, and Momentum call paths determine the
  required cTrader intervals?
- Which cTrader timestamp fields are authoritative for trendbar close eligibility
  and live spot freshness?
- What exact cTrader session/tradability facts can be mapped to the existing
  `MarketState` without unsupported inference?

## Architecture Decision Gate

```text
ADR_REQUIRED = YES
ADR-077 = ACCEPTED
ARCHITECTURAL_APPROVAL = COMPLETED
```

ADR-077 has been revised after the architecture challenge and accepted. It now
requires Market Data-owned cTrader transport and subscriptions with a narrow
shared technical library and independent service sessions.

ADR-073 has received the authorized narrow clarification distinguishing trading
authentication/account binding from Market Data authorization.

## Recommendation

Implementation Planning is now unblocked. Implementation remains blocked until
the current Implementation Plan is explicitly approved.

The existing Market Data and cTrader foundations are reusable. The revised
proposal preserves the repository's stable service boundary, isolates failures,
and supports execution-provider/data-provider independence. It requires a
service-specific read-only Market Data authorization path and a small shared
technical library.

## Implementation Readiness

```text
REPOSITORY_ANALYSIS = COMPLETE
ADR-077 = ACCEPTED
IMPLEMENTATION_PLAN = COMPLETE_PENDING_APPROVAL
IMPLEMENTATION = NOT_AUTHORIZED
```

No implementation files were changed. No implementation tests were executed.

Human approval is required for the Implementation Plan before implementation can
begin.

Repository Analysis completed.

STOP - awaiting human approval of the Story 0167 Implementation Plan.
