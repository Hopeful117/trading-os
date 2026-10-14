# Story

## Metadata

**ID:** `0096`

**Title:** Kraken LINEAR_PERPETUAL Market Data Integration

**Status:** IMPLEMENTATION PLAN COMPLETE - AWAITING HUMAN APPROVAL

**Source:** Real PAPER Challenge E2E blocker observed after Stories 0094 and
0095.

---

## Goal

Extend the Market Data provider boundary so Trading OS can discover and consume
Kraken linear perpetual derivative markets while preserving the existing
provider-neutral market domain and existing Kraken Spot support.

The immediate goal is to unblock the next stage of the real PAPER Challenge
workflow: selecting a tradable `LINEAR_PERPETUAL` market and creating a manual
TradePlan. Risk, execution, and the remainder of the E2E workflow remain
outside this Story.

---

## Context

The real E2E run on commit `a134f891b77e0768d1fca1c561581eed31ed9677`
successfully completed Gateway routing, authentication, PAPER account creation,
Challenge activation, and Challenge monitoring from Stories 0094 and 0095.

Authenticated `GET /api/v1/markets` then returned 1460 Kraken markets, all
`SPOT`, with zero `LINEAR_PERPETUAL` instruments. Market Data PostgreSQL
contained the same product distribution.

The Challenge requires:

```text
SettlementModel.CASH
InstrumentProductType.LINEAR_PERPETUAL
```

The current adapter calls Kraken Spot `/0/public/AssetPairs`, and
`KrakenMarketMapper` assigns `InstrumentProductType.SPOT` to every result.
There is no runtime Kraken derivatives market source.

---

## Provider Investigation Gate

The implementation must use the official Kraken Futures APIs and must not guess

Official sources:

- Futures introduction:
  `https://docs.kraken.com/exchange/guides/futures/introduction`
- Instruments API:
  `https://futures.kraken.com/derivatives/api/v3/instruments`
- Instrument status API:
  `https://futures.kraken.com/derivatives/api/v3/instruments/status`
- Tickers API:
  `https://futures.kraken.com/derivatives/api/v3/tickers`
- Official API reference:
  `https://docs.kraken.com/api/docs/futures-api/trading/get-instruments`

The provider investigation established:

- Kraken Futures is a separate public API family from Spot.
- Instruments use provider symbols such as `PF_XBTUSD`.
- `type: flexible_futures` identifies the perpetual product family.
- Ticker entries identify live perpetual data with `tag: perpetual`.
- `futures_inverse` and dated future tags must not map to
  `LINEAR_PERPETUAL`.
- `/instruments` exposes contract metadata including pair, base, quote,
  tickSize, contractSize, and expiration information.
- Kraken documents `tradeable=true` on `/instruments` as historical market
  availability; current tradability must use `/instruments/status`.
- Public market discovery and ticker data do not require authentication.
- Production and demo APIs use the same endpoint structure; demo uses
  `https://demo-futures.kraken.com`.
- Futures REST rate limiting is cost-based; synchronization must remain bounded
  and avoid per-instrument discovery requests.

Observed public response examples:

```json
{
  "symbol": "PF_XBTUSD",
  "type": "flexible_futures",
  "pair": "BTC:USD",
  "base": "BTC",
  "quote": "USD",
  "tickSize": 1,
  "contractSize": 1,
  "tradeable": true,
  "isExpired": false
}
```

```json
{
  "symbol": "PF_XBTUSD",
  "last": 83369,
  "lastTime": "2026-10-13T22:03:13.205770134Z",
  "tag": "perpetual",
  "pair": "XBT:USD",
  "bid": 83367,
  "ask": 83368
}
```

---

## Canonical Product Mapping

Only the following provider combination may map to the existing canonical type:

```text
Kraken flexible_futures
+ non-expired instrument
+ perpetual ticker semantics
+ current presence in instrument status
        -> InstrumentProductType.LINEAR_PERPETUAL
```

The canonical example is:

```text
provider symbol: PF_XBTUSD
canonical symbol: BTC/USD
provider: KRAKEN
productType: LINEAR_PERPETUAL
```

The provider contract code must remain an adapter concern. A generic
`providerSymbol` representation is preferable to exposing Kraken-specific
contract codes as Trading Core instrument symbols.

The existing product identity remains:

```text
provider + canonical symbol + productType
```

Therefore Spot and perpetual markets with the same canonical pair remain
distinct.

---

## Scope

- Add official Kraken Futures instrument discovery.
- Add official Kraken Futures current instrument status discovery.
- Add official Kraken Futures ticker acquisition.
- Preserve the existing Spot AssetPairs source and mapping.
- Map only semantically valid perpetual contracts to
  `LINEAR_PERPETUAL`.
- Map current provider status into the existing provider-neutral `MarketState`
  and tradability model.
- Preserve canonical symbol identity while retaining the provider symbol for
  adapter requests.
- Persist Spot and perpetual markets without identity collision.
- Route ticker acquisition by product identity.
- Reuse the existing `TickerEvent` and Market Price Snapshot architecture.
- Expose both product families through `GET /api/v1/markets`.
- Prove at least one fresh tradable perpetual price through the runtime path.
- Prove manual TradePlan creation for a compatible perpetual market.
- Add deterministic provider mapping, identity, synchronization, and ticker
  routing tests.
- Document the provider investigation, mapping, alternatives, and validation.

---

## Out of Scope

- Challenge definition or Challenge admission changes.
- Risk policy, Risk rules, Risk hierarchy, or T1 changes.
- Execution implementation for Kraken derivatives.
- Live futures trading.
- Leverage, margin, liquidation, funding, or provider-perfect fee modeling.
- Reclassifying Spot pairs as derivatives.
- A Kraken-specific public API endpoint.
- A Kraken-specific Market table.
- Changes to the existing PAPER Challenge workflow beyond exposing its required
  market identity.
- Story 0097 or any follow-up E2E implementation.

---

## Acceptance Criteria

- [ ] The actual Kraken derivative API family, endpoints, response semantics,
      authentication, rate limits, and demo/production differences are
      documented from authoritative sources.
- [ ] Only semantically valid perpetual contracts map to
      `LINEAR_PERPETUAL`.
- [ ] Existing Kraken Spot discovery remains operational.
- [ ] Spot and perpetual identities coexist without persistence or lookup
      collisions.
- [ ] Current derivative tradability is mapped from provider status, not only
      historical instrument metadata.
- [ ] A perpetual market provides fresh bid, ask, last/reference price, and
      source timestamp through the existing snapshot architecture.
- [ ] Product identity survives persistence and public API projection.
- [ ] Product-aware ticker lookup routes Spot and perpetual markets to the
      correct provider source.
- [ ] `GET /api/v1/markets` returns real Kraken Spot and perpetual markets
      during runtime acceptance.
- [ ] At least one returned perpetual is currently tradable, unless the
      provider has no tradable perpetual at runtime and the provider evidence is
      documented.
- [ ] A manual TradePlan can be created for a compatible perpetual market
      through the supported public application boundary.
- [ ] No Challenge or Risk rule is weakened.
- [ ] No live derivative execution, leverage, margin, liquidation, or funding
      model is introduced.

---

## Relevant Decisions

- ADR-051 - Account Settlement Semantics and Instrument Product Identity.
- ADR-052 - PAPER Cash Realized State and Equity Valuation Authority.
- ADR-053 - Provider-Neutral Market Identity and Multi-Product Adapters.
- Existing Market Data product identity migration and persistence tests.

ADR-053 records the durable generic rule for provider symbol versus canonical
symbol identity and multi-product provider adapter routing.

---

## Validation

- Focused Kraken DTO and mapper tests.
- Perpetual versus dated/inverse contract tests.
- Current status mapping tests.
- Product identity coexistence persistence tests.
- Repeated synchronization tests.
- Product-aware ticker routing tests.
- Existing Market Data module test suite and `mvn verify`.
- Relevant Trading Core contract tests if a public DTO is changed.
- `git diff --check`.
- Live public Kraken runtime validation through Market Data and Gateway.
- Manual TradePlan creation for a selected perpetual market.

Do not continue to Risk or execution as part of Story 0096 acceptance.

---

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed.
- [ ] Relevant validation executed.
- [ ] Live provider runtime acceptance completed.
- [ ] Manual TradePlan unblock acceptance completed.
- [ ] Documentation reconciled.
- [ ] Code Review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.
