# Repository Analysis - Story 0097

## Analysis Status

`APPROVED`

## Scope Assessment

Story 0096 successfully introduced `LINEAR_PERPETUAL` discovery and current
Futures ticker snapshots. The remaining issue is not a display-only or isolated
mapper defect. It affects two existing Market Data capabilities:

1. historical OHLC acquisition used by Market Facts;
2. realtime stream routing used by subscriptions and WebSocket consumers.

The work should be tracked as a separate Story rather than expanding Story 0096,
whose acceptance criteria were intentionally limited to current bid/ask/last
data and manual TradePlan creation.

## Repository Evidence

### Current Futures REST Support

`market-data/src/main/java/com/hope/trading/market_data/kraken/brokerClient/KrakenMarketData.java`
currently:

- routes `LINEAR_PERPETUAL` current snapshots to
  `KrakenFuturesHttpClient.getTickers()`;
- validates bid, ask, last, and source timestamp;
- rejects every non-Spot OHLC request with
  `Kraken OHLC history supports Spot markets only`.

`KrakenFuturesHttpClient` currently exposes only:

- Futures instruments;
- Futures instrument status;
- Futures tickers.

No Futures historical candle endpoint is currently represented in the adapter.

### Market Facts Impact

`MarketFactsService` calls the provider-neutral OHLC history path. Runtime logs
show repeated `IllegalArgumentException` failures for persisted Futures market
IDs when Market Facts requests an hourly history.

This confirms that the remaining defect is observable in the current runtime and
blocks existing analytical consumers, not only a future capability.

### Current WebSocket Support

`KrakenDataStreamProvider` currently:

- connects through the single configured `kraken.websocket` URL;
- extracts `Market.symbol`, not `Market.providerSymbol`;
- stores subscriptions keyed primarily by canonical symbol;
- uses the existing Kraken Spot DTOs and mappers for ticker, OHLC, order book,
  and trade messages.

There is no product-aware endpoint selection, Futures provider-symbol routing,
or Futures payload mapping in this component. Spot and perpetual markets can
therefore collide when their canonical symbols are equal.

### Existing Generic Boundaries

The repository already provides reusable boundaries:

- `MarketDataProvider` for current snapshots and historical OHLC;
- `MarketHistoryService` for bounded OHLC requests and normalization;
- `OhlcEvent`, `TickerEvent`, order-book, and recent-trade domain contracts;
- `MarketFactsService` for deterministic readiness and activity facts;
- `MarketSubscriptionServiceImpl` and `MarketDataWebSocketHandler` for generic
  subscription lifecycle and event publication;
- Kraken-specific DTOs and mappers under the Kraken adapter package.

The preferred implementation should extend these boundaries rather than expose
Kraken-specific APIs or create a second Market Data service.

## Responsibility Boundary

- Kraken adapters own Futures endpoint selection, provider symbols, payloads,
  protocol channels, and provider error translation.
- `market-data` owns normalized OHLC, ticker, order-book, trade, freshness, and
  subscription state contracts.
- `MarketFactsService` owns deterministic facts and readiness, not provider
  interpretation.
- Market Intelligence remains the consumer of Market Facts and analytical
  context; it must not gain Kraken-specific logic.
- Trading Core, Risk, Challenge, and Broker Service remain outside this Story.

## Main Risks

- Kraken Spot and Futures use separate REST and WebSocket API families.
- Futures payload shapes and channel semantics may differ from Spot even when
  the normalized domain event is identical.
- A canonical symbol such as `BTC/USD` can identify both Spot and perpetual
  products, so stream state and event routing must include product identity or
  market ID.
- The provider may not support every generic stream type for every Futures
  market; unsupported capabilities must be explicit rather than silently mapped.
- Futures OHLC intervals may not match the existing `OhlcInterval` values one to
  one and require an approved mapping or explicit unsupported result.
- Runtime validation depends on public provider availability and rate limits.

## Required Investigation Before Implementation

Before implementation, verify from official Kraken documentation:

- Futures historical candle endpoint and response format;
- Futures WebSocket endpoint and authentication requirements;
- ticker, OHLC, order-book, and trade channel names and subscription payloads;
- provider symbol requirements;
- snapshot/update semantics and reconnect behavior;
- interval availability and timestamp precision;
- production versus demo behavior and rate limits.

No provider endpoint or payload should be guessed from the Spot API.

## Architectural Assessment

The Story can likely be implemented inside the existing Market Data and Market
Facts boundaries. However, product-aware WebSocket endpoint and state routing may
cross the current generic adapter assumptions. If that requires a durable change
to subscription ownership, stream identity, or public contracts, an ADR is
required before implementation continues.

## DevLog and Context Limitations

The DevLog applicability check for project `trading-os` was attempted but the
Story Agent provider returned an exhausted-quota error. The current repository,
Story 0096, ADR-053, and runtime logs were therefore used as authoritative
current-state evidence.
