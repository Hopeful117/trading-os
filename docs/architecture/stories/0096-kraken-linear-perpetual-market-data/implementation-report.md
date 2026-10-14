# Story 0096 Implementation Report

## Result

Implemented Kraken `LINEAR_PERPETUAL` market discovery and product-aware ticker
acquisition while preserving existing Spot behavior and the provider-neutral
Market Data boundary. Challenge, Risk, and derivative execution semantics were
not changed.

## Changes

- Added Kraken Futures clients and DTOs for instruments, current instrument
  status, and tickers.
- Added mapping for non-expired `flexible_futures` instruments with
  `perpetual` ticker semantics to `LINEAR_PERPETUAL`.
- Excluded inverse and dated futures from the perpetual mapping.
- Mapped current tradability from the Futures instrument-status response.
- Added generic `providerSymbol` persistence and the V4 migration, while
  retaining canonical `symbol` for public and Trading Core contracts.
- Aggregated Spot and perpetual markets without changing the existing product
  identity of `provider + symbol + productType`.
- Routed Spot and perpetual snapshot acquisition to their respective Kraken
  APIs.
- Added ADR-053 for provider-neutral identity and multi-product adapters.
- Added focused mapper, ticker, persistence, migration, synchronization, and
  routing tests.
- Addressed independent review findings by isolating Futures discovery failures
  from Spot discovery, validating Futures response status, synchronizing
  `providerSymbol` updates, and rejecting incomplete Futures snapshots.

## Validation

- `market-data`: `mvn verify` passed with exit code 0.
- Market Data test suite: 161 tests, 0 failures, 0 errors, 0 skipped.
- `git diff --check` passed.
- Root-level `mvn verify` was not applicable because the repository has no root
  `pom.xml`; the module-level Market Data validation was executed instead.
- DevLog Story Agent was unavailable during finalization because its provider
  reported exhausted quota. Repository sources and the approved Story context
  were used.

## Runtime Acceptance

Live public Kraken runtime validation completed through Market Data and Gateway:

- Market catalogue: 1664 markets.
- Spot markets: 1460.
- `LINEAR_PERPETUAL` markets: 204.
- Selected market: canonical `BTC/USD`, provider symbol `PF_XBTUSD`.
- Selected market state: `OPEN` and currently tradable.
- Fresh snapshot obtained with bid, ask, last/reference price, and provider
  source timestamp.
- PostgreSQL persistence contained both Spot and perpetual products without
  identity collision.
- Manual TradePlan creation through the supported public boundary returned
  HTTP `201` for the selected perpetual market.

## Scope Boundary

- No Challenge admission or definition change was made.
- No Risk rule or deterministic risk behavior was changed.
- No leverage, margin, liquidation, funding, or live derivative execution
  support was introduced.
- The Challenge/Risk/execution E2E was intentionally not continued because it
  is outside Story 0096 scope.

## Remaining Review Items

- Story status and acceptance checkboxes still require human approval and
  reconciliation.
- Code Review and Engineering Report remain pending.
- Changes remain uncommitted for human review.
