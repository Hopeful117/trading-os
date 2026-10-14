# Story 0097: Complete Kraken LINEAR_PERPETUAL Market Data

**Status:** IMPLEMENTATION IN PROGRESS - RUNTIME VALIDATION PARTIAL
**Size:** Large
**Risk:** High
**Predecessor:** Story 0096 - Kraken LINEAR_PERPETUAL Market Data Integration
**Follow-up:** Runtime validation of the complete PAPER decision workflow

## Goal

Complete the Kraken `LINEAR_PERPETUAL` market-data path so existing Trading OS
consumers can use Futures markets for both historical and realtime market data,
not only for the current REST ticker snapshot implemented by Story 0096.

The result must provide provider-neutral OHLC and realtime stream behavior for
supported Futures markets while preserving existing Spot behavior, product
identity, deterministic Market Facts, and the existing Market Data service
boundaries.

## Context

Story 0096 added Kraken Futures instrument discovery, current status discovery,
Futures ticker acquisition, product-aware persistence, and current price
snapshot routing. Its scope intentionally stopped at a fresh bid/ask/last
snapshot and manual TradePlan creation.

The current runtime shows a remaining gap:

- `KrakenMarketData.findOhlcHistorySnapshot` rejects every non-Spot market;
- `MarketFactsService` therefore fails repeatedly for Futures markets;
- the Kraken WebSocket adapter still connects to the configured Spot WebSocket,
  extracts canonical symbols, and does not route subscriptions by product
  identity or provider symbol;
- existing realtime consumers cannot reliably receive Futures ticker, OHLC,
  order-book, or trade streams through the provider-neutral subscription path.

The repository logs have observed repeated `Market facts acquisition failed`
warnings with `IllegalArgumentException` for persisted Futures market IDs.

## Problem

Futures markets are visible and can provide a current REST snapshot, but they
are not fully usable by existing Market Data consumers. Market Facts cannot
obtain historical candles, and realtime subscriptions can send a Spot protocol
request for a Futures market. This produces incomplete or misleading data in
market analysis and user-facing market views.

## Scope

### In Scope

- Investigate and document the official Kraken Futures historical and WebSocket
  APIs before implementation.
- Add product-aware Futures OHLC history acquisition through the existing
  broker-neutral `MarketDataProvider` and normalization path.
- Preserve source timestamps, close-state semantics, interval mapping, bounded
  limits, and raw-versus-normalized OHLC behavior.
- Make `MarketFactsService` work for supported `LINEAR_PERPETUAL` markets using
  the same deterministic facts and readiness semantics as Spot markets.
- Add product-aware realtime routing for supported provider-neutral stream types
  used by the repository: ticker, OHLC, order book, and recent trades.
- Route Futures subscriptions to the correct Kraken Futures WebSocket endpoint,
  provider symbol, channel syntax, and payload mappers.
- Preserve Spot WebSocket behavior without cross-product symbol or event
  leakage.
- Preserve `provider + canonical symbol + productType` identity and the stored
  provider symbol.
- Add focused unit, mapper, routing, integration, and runtime validation tests.
- Add observability for unsupported, unavailable, stale, and malformed Futures
  data without logging credentials or sensitive payloads.

### Out of Scope

- Kraken derivative order placement, cancellation, or live execution.
- Leverage, margin, liquidation, funding, contract settlement, or fee-model
  changes.
- Challenge admission, Challenge definition, Risk rules, or T1 behavior.
- A Kraken-specific public API or provider-specific public DTO.
- A durable OHLC history store unless an approved design proves it is required
  for the existing bounded Market Facts path.
- New Market Intelligence ranking, strategy, opportunity, or trade-plan logic.
- Reclassification of Spot instruments or support for inverse or dated Futures.
- Broad refactoring of the existing Market Data WebSocket architecture unrelated
  to product-aware routing.

## Product Boundary

Only persisted markets with `productType=LINEAR_PERPETUAL` and a valid Kraken
Futures provider symbol are routed to the Futures adapter. The canonical Trading
OS symbol remains provider-neutral. Kraken contract codes remain inside the
adapter and persistence field intended for provider symbols.

The implementation must not silently fall back from a Futures request to a Spot
endpoint or from a Futures event to a Spot market. Missing or unsupported data
must produce an explicit provider-neutral unavailable or unsupported outcome.

## Acceptance Criteria

- [ ] Official Kraken Futures historical and WebSocket endpoints, payload
      semantics, intervals, symbols, authentication, rate limits, and demo/
      production differences are documented from authoritative sources.
- [ ] A supported `LINEAR_PERPETUAL` market returns bounded historical OHLC
      events through the existing Market Data API.
- [ ] Futures OHLC events preserve canonical market identity, provider source,
      source timestamp, interval, volume/VWAP where available, and close state.
- [ ] Futures OHLC acquisition does not use the Spot endpoint or Spot symbol
      translation.
- [ ] `MarketFactsService` produces deterministic facts for a supported Futures
      market when the required OHLC evidence is available.
- [ ] Missing, stale, malformed, or unsupported Futures history produces an
      explicit non-available status and does not become a false fresh fact.
- [ ] Futures ticker, OHLC, order-book, and recent-trade subscriptions route to
      the correct Futures WebSocket endpoint and provider symbol where the
      provider supports the stream.
- [ ] Spot subscriptions continue to route to the existing Spot WebSocket
      behavior.
- [ ] Futures and Spot events cannot cross-publish through the same canonical
      symbol.
- [ ] Unsubscribe, reconnect, state reset, and repeated subscription behavior
      remain correct for both product families.
- [ ] Provider-specific payloads and symbols do not leak through public or
      broker-neutral contracts.
- [ ] Existing Spot Market Data tests and runtime behavior remain operational.
- [ ] At least one live tradable perpetual passes historical OHLC, Market Facts,
      current snapshot, and supported realtime validation through the runtime
      path.
- [ ] No Challenge, Risk, or execution rule is weakened or changed.
- [ ] No live derivative execution, leverage, margin, liquidation, or funding
      model is introduced.

## Relevant Decisions

- ADR-051 - Account Settlement Semantics and Instrument Product Identity.
- ADR-052 - PAPER Cash Realized State and Equity Valuation Authority.
- ADR-053 - Provider-Neutral Market Identity and Multi-Product Adapters.
- Story 0069 - Market Facts Foundation.
- Story 0096 - Kraken LINEAR_PERPETUAL Market Data Integration.

If product-aware WebSocket routing changes a durable generic responsibility
boundary, the implementation must stop for an ADR decision rather than silently
introducing one.

## Validation

- Official provider documentation review and recorded endpoint evidence.
- Focused Futures DTO and mapper tests.
- Futures interval and OHLC normalization tests.
- Product-aware REST and WebSocket routing tests.
- Event isolation tests for identical canonical Spot and Futures symbols.
- Market Facts service and controller tests for available, stale, unavailable,
  insufficient, and unsupported data.
- Reconnect, unsubscribe, duplicate subscription, and state reset tests.
- Full `market-data` module validation with `mvn verify`.
- Relevant Market Intelligence contract tests if its consumer behavior changes.
- `git diff --check`.
- Live public Kraken runtime validation through Market Data and Gateway.
- Manual verification of a Futures market in the user-facing market workflow.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed.
- [ ] Documentation reconciled.
- [ ] Relevant validation executed.
- [ ] Live Futures historical and realtime acceptance completed.
- [ ] Code Review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.
