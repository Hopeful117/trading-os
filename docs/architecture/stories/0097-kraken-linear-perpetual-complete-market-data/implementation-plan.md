# Story 0097 - Implementation Plan

## Plan Status

`APPROVED`

This plan is limited to completing provider-neutral Market Data support for
Kraken `LINEAR_PERPETUAL` markets. It does not change Challenge, Risk,
settlement, or derivative execution semantics.

## Design

### 1. Verify the Kraken Futures contracts first

Before coding, record authoritative evidence for the Futures REST and WebSocket
APIs. Confirm endpoint URLs, response wrappers, provider symbols, intervals,
channel names, authentication, limits, timestamps, and reconnect behavior.

If the official APIs do not provide a generic stream or interval required by the
application, represent that capability as explicitly unsupported and document
the limitation.

### 2. Add Futures historical OHLC acquisition

Extend the Kraken adapter with provider-specific DTOs and a client method for the
official Futures historical candle endpoint.

The implementation must:

- route only `LINEAR_PERPETUAL` markets to the Futures client;
- use the persisted `providerSymbol` rather than the canonical symbol;
- map provider candles into the existing `OhlcEvent` contract;
- preserve source timestamps, interval, close state, volume, VWAP, and fetched
  time where the provider supplies them;
- enforce the existing bounded history limits;
- avoid silently substituting Spot history;
- return an explicit unavailable or unsupported result when data is absent.

Reuse `MarketHistoryService` and `OhlcHistoryNormalizer`. Do not introduce a
provider-specific public history endpoint.

### 3. Enable Market Facts for Futures

Once Futures OHLC is available, run the existing `MarketFactsService` path without
special Kraken logic in Market Facts.

Verify that:

- completed and observed Futures candles satisfy readiness requirements;
- normalization does not falsely claim provider completeness;
- stale, empty, malformed, and unsupported results remain distinguishable;
- repeated bounded requests preserve deterministic results;
- current runtime errors are replaced by valid facts or explicit non-available
  statuses.

If Market Facts currently assumes a Spot-only invariant, make the smallest
provider-neutral correction required and test it at the service boundary.

### 4. Introduce product-aware stream identity

Review `MarketSubscriptionServiceImpl`, `KrakenDataStreamProvider`, and event
publication state. Subscription and state keys must distinguish at least:

```text
provider + canonical symbol + productType
```

Prefer the persisted market ID where the existing generic contract allows it.
Do not use canonical symbol alone when Spot and Futures coexist.

### 5. Route Spot and Futures WebSockets separately

Extend configuration with an explicit Futures WebSocket URL if required by the
official contract. Keep the existing Spot URL and behavior unchanged.

For each requested stream:

- partition markets by product family;
- connect or reuse the correct provider stream;
- send the correct provider symbol and channel syntax;
- map provider messages into the existing generic event contracts;
- publish only to the matching market identity;
- preserve unsubscribe, reconnect, and state cleanup behavior.

Provider-specific DTOs and protocol fields remain inside the Kraken adapter.

### 6. Handle unsupported capabilities explicitly

Do not force Futures into a Spot channel that has different semantics. If a
Futures stream type is unavailable or incompatible, return a structured
unsupported result through the existing service boundary and expose a useful
diagnostic without leaking provider payloads.

### 7. Preserve existing contracts

Do not add a Kraken-specific public controller or expose provider symbols through
public Trading Core contracts. Existing public market identity remains canonical
symbol plus product type and market ID.

If a generic contract must change to preserve stream identity, stop and document
the architectural decision before implementation proceeds.

## Expected Production Files

Likely changes are limited to:

- `market-data/src/main/java/com/hope/trading/market_data/kraken/brokerClient/`;
- `market-data/src/main/java/com/hope/trading/market_data/kraken/dto/`;
- `market-data/src/main/java/com/hope/trading/market_data/kraken/helper/`;
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/`;
- `market-data/src/main/java/com/hope/trading/market_data/service/` only for
  generic identity or unsupported-capability handling;
- `market-data/src/main/java/com/hope/trading/market_data/brokerClient/` only
  if the existing provider-neutral port needs a minimal, approved extension;
- `market-data/src/main/resources/application.properties` for explicit Futures
  endpoint configuration.

Trading Core and Market Intelligence changes are not planned unless contract
validation proves a minimal propagation change is required.

## Expected Tests

### Futures REST and OHLC

- official response DTO deserialization;
- interval and provider-symbol mapping;
- completed versus in-progress candle mapping;
- volume/VWAP and timestamp handling;
- bounded limits and malformed responses;
- Spot/Futures endpoint isolation;
- unavailable and unsupported response behavior.

### Market Facts

- available Futures readiness;
- insufficient and stale Futures history;
- unavailable provider response;
- deterministic repeated fact evaluation;
- no false fresh fact after an acquisition failure.

### WebSocket Routing

- Spot and Futures endpoint selection;
- provider-symbol subscription payloads;
- ticker, OHLC, order-book, and recent-trade mapping;
- identical canonical symbols remain isolated;
- repeated subscriptions and reference counting;
- unsubscribe and reconnect cleanup;
- malformed or unsupported Futures messages do not corrupt state.

Retain the complete existing Market Data test suite.

## Runtime Acceptance

After focused tests and module validation:

1. Run Market Data against official Kraken Spot and Futures endpoints.
2. Synchronize and select one current tradable `LINEAR_PERPETUAL` market.
3. Retrieve bounded historical OHLC through the existing API boundary.
4. Retrieve Market Facts for the same market and record readiness and evidence.
5. Retrieve a current Futures snapshot and verify timestamp freshness.
6. Subscribe to every supported Futures stream type and record normalized events.
7. Verify Spot and Futures streams remain isolated for a shared canonical symbol.
8. Unsubscribe, reconnect, and repeat the subscription validation.
9. Stop before Risk, Challenge progression, or derivative execution.

Do not claim runtime acceptance from fixtures or mocked provider responses alone.

## Validation Commands

Run focused tests from `market-data`, then the full module validation:

```bash
mvn -q -Dtest=KrakenMarketDataTest,KrakenFuturesTickerMapperTest,KrakenDataProviderDispatchTest,MarketFactsServiceTest,MarketSubscriptionServiceImplTest test
mvn -q verify
git diff --check
```

Adjust the focused class list to the actual tests added during implementation.
Run relevant Market Intelligence contract tests only if its contract changes.

Review the complete diff and verify that no Challenge, Risk, settlement, or
execution behavior changed.

## Explicit Exclusions

- no derivative order placement or cancellation;
- no leverage, margin, liquidation, funding, or fee model;
- no Challenge or Risk rule changes;
- no broad persistence redesign;
- no provider-specific public API;
- no support for inverse or dated Futures.
