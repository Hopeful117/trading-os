# Story 0096 - Implementation Plan

## Plan Status

`PROPOSED - AWAITING HUMAN APPROVAL`

The Repository Analysis was explicitly approved. This plan is limited to
removing the observed absence of Kraken `LINEAR_PERPETUAL` market data and
proving the next manual TradePlan stage. It does not change Challenge, Risk, or
execution semantics.

## Design

### 1. Preserve the generic Market identity contract

Add a provider-neutral `providerSymbol` field to the Market Data `Market`
entity and its persistence model.

The identity remains:

```text
provider + canonical symbol + productType
```

Examples:

```text
KRAKEN + BTC/USD + SPOT              -> providerSymbol XXBTZUSD or existing Spot identity
KRAKEN + BTC/USD + LINEAR_PERPETUAL  -> providerSymbol PF_XBTUSD
```

The public `symbol` remains the canonical Trading OS instrument symbol used by
Trading Core. The provider symbol is used only by the provider adapter for
requests. Existing Spot rows receive a provider symbol during synchronization,
using their current provider request identity without changing their product
type or public behavior.

Add the required Flyway migration and update the Market Data persistence,
domain, DTO mapping, and fixtures. Do not add a Kraken-specific column or
table.

### 2. Add official Kraken Futures provider DTOs and clients

Extend the Kraken adapter with provider-specific DTOs for:

- `GET /derivatives/api/v3/instruments`;
- `GET /derivatives/api/v3/instruments/status`;
- `GET /derivatives/api/v3/tickers`.

Use a separate configured Futures base URL, defaulting to the official
production host when the runtime configuration provides no override. Preserve
the existing Spot base URL and `KrakenHttpClient` behavior.

Keep all Kraken field names, contract types, tags, status-list semantics, and
provider symbol handling inside the Kraken package.

### 3. Map Spot and perpetual products separately

Keep the current Spot AssetPairs mapper and classify its output as `SPOT`.

Add a Futures mapper that:

- accepts only `flexible_futures` instruments;
- excludes expired instruments;
- requires a matching ticker with `tag=perpetual` for runtime price support;
- maps the provider pair/base/quote to a canonical symbol;
- stores the provider contract code as `providerSymbol`;
- maps tick size and available quantity constraints;
- never maps `futures_inverse` or dated futures to
  `LINEAR_PERPETUAL`.

Canonical symbol mapping will normalize Kraken pair separators and provider
asset aliases at the adapter boundary. The initial verified example is
`PF_XBTUSD` plus instrument pair `BTC:USD` to canonical `BTC/USD`.

### 4. Map current tradability from instrument status

Build a status lookup from the Futures `/instruments/status` response.

For each supported perpetual:

- status-list presence means currently tradable and maps to the existing open
  and tradable state;
- absence maps to a closed/unavailable state;
- no provider-specific status leaks outside the Kraken adapter.

The implementation must not use the `/instruments` `tradeable` field alone,
because Kraken documents that field as historical availability.

### 5. Aggregate synchronization without collisions

Extend `KrakenMarketData.getMarkets()` to combine the existing Spot results and
the supported Futures perpetual results.

The existing `MarketSynchronizationImpl` remains the generic merge authority.
Its product-aware lookup must preserve both Spot and perpetual rows across:

- initial synchronization;
- repeated synchronization;
- state changes;
- partial provider result changes.

No delete-and-reinsert synchronization behavior will be introduced.

### 6. Route fresh tickers by product identity

Update `KrakenProviderSymbolResolver` and `KrakenMarketData.acquireCurrentSnapshot`
to route by `Market.productType`:

- `SPOT` uses the existing Spot `/0/public/Ticker` endpoint and Spot provider
  symbol;
- `LINEAR_PERPETUAL` uses Futures `/derivatives/api/v3/tickers` and the
  persisted Futures provider symbol.

Map Futures `bid`, `ask`, `last`, and `lastTime` into the existing generic
`TickerEvent`. Use the existing `MarketPriceSnapshotService` freshness checks;
do not create a derivative-specific snapshot type.

If the Futures API returns no current ticker for a persisted market, return an
empty acquisition result and let the existing unavailable/stale behavior apply.

### 7. Preserve public and Trading Core contracts

Expose the existing `MarketResponse` with canonical `symbol`, provider,
`productType`, MarketState, and constraints. Do not add a Kraken-only endpoint.

If `providerSymbol` is not intended for public consumers, omit it from the
public response while retaining it in the provider-neutral persistence model.

Verify that Trading Core continues to consume:

- canonical symbol for manual TradePlan context;
- `productType=LINEAR_PERPETUAL` for CASH compatibility;
- market ID for fresh snapshot lookup.

Only make minimal DTO propagation changes if compilation or contract tests prove
they are required.

### 8. ADR decision

Before implementation is finalized, determine whether the generic
`providerSymbol` identity field and multi-product provider routing exceed the
repository ADR threshold.

If yes, create a new ADR before or with implementation documentation. The ADR
must describe the durable provider-neutral rule, not Kraken implementation
details. If no, record the evidence-based decision in the Implementation Report.

No existing ADR will be modified.

## Expected Production Files

Likely Market Data production changes:

- `market-data/src/main/java/com/hope/trading/market_data/model/Market.java`;
- `market-data/src/main/java/com/hope/trading/market_data/dto/MarketResponse.java`
  only if the public contract requires the field;
- `market-data/src/main/java/com/hope/trading/market_data/kraken/brokerClient/`
  Futures client and provider routing;
- `market-data/src/main/java/com/hope/trading/market_data/kraken/dto/`
  Futures response DTOs;
- `market-data/src/main/java/com/hope/trading/market_data/kraken/helper/`
  Futures mappers and symbol resolution;
- `market-data/src/main/java/com/hope/trading/market_data/service/`
  only where generic synchronization or projection needs the field;
- `market-data/src/main/resources/db/migration/` for the additive Market
  provider-symbol migration;
- `market-data/src/main/resources/application.properties` for the Futures base
  URL configuration.

Trading Core changes are not planned unless a contract test demonstrates a
minimal required propagation correction.

## Expected Tests

Add or update focused tests for:

### Provider mapping

- Spot AssetPair maps to `SPOT`.
- `flexible_futures` with a perpetual ticker maps to
  `LINEAR_PERPETUAL`.
- `futures_inverse` does not map to `LINEAR_PERPETUAL`.
- dated future symbols/tags do not map to `LINEAR_PERPETUAL`.
- canonical `BTC/USD` and provider `PF_XBTUSD` are retained separately.
- current status presence maps to tradable/open state.
- status absence maps to unavailable/closed state.

### Identity and persistence

- Spot and perpetual rows with the same canonical symbol coexist.
- provider/product identity is stable across repeated synchronization.
- provider symbol updates do not change the canonical identity or market ID.
- Flyway migration works on a fresh database and existing Spot rows remain
  readable.

### Ticker routing

- Spot markets call the Spot ticker source.
- Perpetual markets call the Futures ticker source with `PF_XBTUSD`.
- Futures ticker data maps to a generic `TickerEvent` with source timestamp.
- No cross-product ticker leakage occurs.

### Synchronization

- Initial mixed Spot/perpetual synchronization.
- Repeated mixed synchronization without duplicates.
- State updates preserve both product families.
- Missing or non-perpetual Futures ticker data does not create a false fresh
  price.

Retain the existing Market Data tests and persistence identity tests.

## Runtime Acceptance

After focused validation:

1. Run Market Data against the real public Kraken Spot and Futures endpoints.
2. Synchronize the market catalogue.
3. Call authenticated `GET /api/v1/markets` through Gateway.
4. Record actual Spot and `LINEAR_PERPETUAL` counts.
5. Select one current tradable perpetual, expected initially to be
   `PF_XBTUSD` / canonical `BTC/USD` if still available.
6. Retrieve a fresh market price snapshot and record provider, canonical symbol,
   product type, state, bid, ask, reference price, and timestamp.
7. Create one manual TradePlan through the supported public API using that
   market.
8. Stop before Risk, T1, execution, or further E2E progression.

Do not claim live acceptance if only fixtures or mocked provider responses were
used.

## Validation Commands

Run focused tests first from `market-data`:

```bash
mvn -q -Dtest=KrakenMarketDataTest,MarketSynchronizationImplTest,MarketProductIdentityPersistenceTest,MarketPriceSnapshotServiceTest test
```

Then run the full module validation:

```bash
mvn -q verify
```

Run relevant Trading Core contract tests only if its DTO or source code changes.
Also run:

```bash
git diff --check
```

Review the complete diff and verify that no Challenge, Risk, execution, or
unrelated provider code changed.

## Explicit Exclusions

- no Challenge definition or compatibility changes;
- no Risk or T1 changes;
- no live Kraken derivative order placement;
- no leverage, margin, liquidation, funding, or fee model;
- no reclassification of Spot markets;
- no Kraken-specific public endpoint;
- no Story 0097 implementation;
- no commit, push, merge, or branch integration.

## Approval Required

Human approval is required before implementation begins.
