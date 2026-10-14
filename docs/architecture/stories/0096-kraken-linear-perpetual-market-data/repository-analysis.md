# Repository Analysis

## Status

`COMPLETE - AWAITING HUMAN APPROVAL`

## Baseline

- Repository: Trading OS.
- Branch: `main`.
- HEAD investigated: `a134f891b77e0768d1fca1c561581eed31ed9677`.
- Stories 0094 and 0095 are merged in the investigated commit.
- Existing untracked Story 0093 E2E reports were preserved.
- No production or test code was changed during this analysis.
- DevLog applicability was checked. The Story Agent was unavailable because
  the connected provider reported exhausted quota. Repository and official
  Kraken sources were used instead.

## Observed Blocker

The real PAPER Challenge E2E run reached authenticated Market Data discovery
after successfully activating a CASH Kraken Prop Advanced 50k Challenge.

```text
GET /api/v1/markets
```

returned 1460 Kraken markets, all `SPOT`, and zero `LINEAR_PERPETUAL` markets.
Market Data PostgreSQL independently contained only `SPOT` rows.

The current path is:

```text
KrakenMarketData.getMarkets()
    -> KrakenHttpClient.getAssetPairs()
    -> Spot /0/public/AssetPairs
    -> KrakenMarketMapper
    -> InstrumentProductType.SPOT
```

The Challenge correctly requires `CASH + LINEAR_PERPETUAL`. No Challenge,
Risk, scheduler, or execution defect was found.

## Official Provider Evidence

The official Kraken Futures API is separate from the Spot API:

- Instruments: `GET /derivatives/api/v3/instruments`.
- Current status: `GET /derivatives/api/v3/instruments/status`.
- Tickers: `GET /derivatives/api/v3/tickers`.
- Production base URL: `https://futures.kraken.com`.
- Demo base URL: `https://demo-futures.kraken.com`.
- Public discovery and ticker requests do not require authentication.

The live public response observed during analysis contained:

- 226 instruments;
- 214 `flexible_futures` instruments;
- 12 `futures_inverse` instruments;
- 208 ticker entries tagged `perpetual`;
- dated ticker tags including `week`, `month`, `quarter`, and `semiannual`.

Representative perpetual data:

```text
instrument symbol: PF_XBTUSD
instrument type: flexible_futures
instrument pair: BTC:USD
instrument base: BTC
instrument quote: USD
instrument tickSize: 1
instrument contractSize: 1
ticker tag: perpetual
ticker pair: XBT:USD
bid: 83367
ask: 83368
last: 83369
lastTime: 2026-10-13T22:03:13.205770134Z
```

The provider's `tradeable` field on `/instruments` is not sufficient for
current Market Data tradability: Kraken documents it as historical availability
and states that it is always true for instruments returned by that endpoint.
The `/instruments/status` response provides the current tradeable symbol list.

## Current Repository Architecture

### Market Domain

`market-data` already contains:

- provider-neutral `Market`;
- `InstrumentProductType.SPOT` and `LINEAR_PERPETUAL`;
- `MarketState` with `TradingStatus`, `tradable`, closure reason, and timestamp;
- `MarketConstraints` with minimum size, minimum cost, tick size, and precision;
- database uniqueness on `provider + symbol + product_type`;
- public `MarketResponse` with provider, symbol, product type, state, and
  constraints.

Persistence coexistence for the same provider and symbol across product types
is already covered by `MarketProductIdentityPersistenceTest`.

### Provider Boundary

`MarketDataProvider` is already one provider-neutral port per provider:

- `getMarkets()`;
- `acquireCurrentSnapshot(Market)`;
- historical OHLC operations.

The existing `KrakenMarketData` implementation can retain the single provider
port while routing internally by product type. No Kraken-specific type needs to
leak into Trading Core or generic Market Data services.

### Synchronization

`MarketSynchronizationImpl` already merges using:

```text
provider + symbol + productType
```

It updates constraints and state in place and does not delete other product
families. Focused tests are required to prove Spot and perpetual rows coexist
across repeated synchronization.

### Snapshot Architecture

`MarketPriceSnapshotService` selects the provider by `MarketProvider`, then
passes the full `Market` to `acquireCurrentSnapshot`. It enforces fresh bid and
ask data using the existing `TickerEvent` and stale-after policy.

The provider adapter can therefore route Spot and derivative tickers without a
new derivative-specific snapshot model.

### Current Identity Gap

`Market.symbol` is currently both the public/canonical instrument symbol and the
provider request symbol. Kraken derivatives expose provider codes such as
`PF_XBTUSD`, while the canonical Trading OS pair should be `BTC/USD`.

Trading Core uses the canonical Market symbol for Challenge admission, manual
TradePlan context, simulated execution, and valuation lookup. Exposing
`PF_XBTUSD` as the canonical symbol would leak provider semantics and could
break the existing identity contract.

The implementation plan should therefore evaluate adding a generic
`providerSymbol` field to `Market` and its persistence model, while keeping the
existing canonical `symbol` contract. This is not a Kraken-specific domain
field.

## Selected Semantic Mapping

The supported mapping is:

```text
Kraken instrument type flexible_futures
+ non-expired
+ ticker tag perpetual
+ current presence in /instruments/status
    -> provider KRAKEN
    -> canonical symbol BTC/USD
    -> product type LINEAR_PERPETUAL
```

The implementation must exclude:

- `futures_inverse` instruments;
- fixed-maturity/fixed-date contracts;
- ticker entries whose tag is not `perpetual`;
- instruments absent from current status when determining tradability.

Spot remains sourced from `/0/public/AssetPairs` and remains mapped only to
`SPOT`.

## Affected Areas

Expected implementation areas:

- Kraken Futures DTOs and Feign client methods;
- Kraken provider mapping and provider-symbol resolution;
- generic Market provider-symbol persistence if approved in the plan;
- Kraken synchronization aggregation;
- product-aware current ticker routing;
- Market Data tests and possibly Trading Core DTO contract tests;
- Story 0096 documentation;
- an ADR if the generic identity/routing rule meets the repository threshold.

No Challenge, Risk, settlement, valuation, or execution business semantics
should change.

## Required Test Coverage

- Spot AssetPair maps to `SPOT`.
- `flexible_futures` plus perpetual ticker maps to `LINEAR_PERPETUAL`.
- Inverse and dated futures do not map to `LINEAR_PERPETUAL`.
- Current status maps to `OPEN`/tradable only for currently tradeable symbols.
- Similar Spot and perpetual canonical symbols coexist in persistence.
- Repeated synchronization does not duplicate or overwrite product families.
- Spot ticker requests use the Spot endpoint.
- Perpetual ticker requests use the Futures endpoint and provider symbol.
- Derivative ticker `bid`, `ask`, `last`, and `lastTime` become a fresh generic
  `TickerEvent`.
- Existing Market Data tests remain passing.

## Runtime Validation Boundary

After implementation, runtime validation must:

1. start Market Data with the real public Kraken endpoints;
2. synchronize Spot and Futures markets;
3. retrieve `GET /api/v1/markets` through Gateway;
4. record actual Spot and perpetual counts;
5. select one currently tradable perpetual;
6. retrieve a fresh snapshot with provider, canonical symbol, product type,
   state, bid, ask, reference price, and timestamp;
7. create a manual TradePlan through the supported public boundary;
8. stop before Risk and execution.

## Architecture Gate

An ADR is likely appropriate if the implementation introduces a durable generic
rule for either:

- provider symbol versus canonical symbol storage; or
- multi-product provider adapter and product-aware ticker routing.

No ADR is created by this analysis. The decision belongs in the approved
Implementation Plan before implementation begins.

## Workflow State

Repository Analysis is complete and awaits explicit human approval before an
Implementation Plan is prepared.
