# Story 0097 - Implementation Report

## Status

`IMPLEMENTATION COMPLETE - RUNTIME VALIDATION COMPLETE`

## Implemented

- Added Kraken Futures Charts REST support for bounded `LINEAR_PERPETUAL` OHLC
  history.
- Added Futures OHLC DTOs, interval mapping, timestamp preservation, close-state
  mapping, and provider-neutral `OhlcEvent` conversion.
- Routed Futures ticker, order-book, and trade subscriptions to the Futures
  WebSocket endpoint using the persisted provider symbol.
- Rejected Futures OHLC WebSocket subscriptions explicitly because Kraken does
  not provide that feed on the supported Futures WebSocket API.
- Preserved existing Spot REST and WebSocket routing.
- Preserved canonical market identity and isolated Futures provider symbols at
  the adapter boundary.
- Corrected ticker event timestamp mapping for provider events.
- Added Futures order-book parsing for Kraken object-shaped levels
  (`{price, qty}`) while preserving Spot array-shaped parsing.
- Added Futures trade identifier fallback from Kraken `uid` when `trade_id` is
  absent.
- Added focused mapper, routing, persistence, synchronization, and integration
  tests.

## Validation Executed

- `mvn -q verify` in `market-data`: passed.
- `git diff --check`: passed.
- `docker compose build market-data`: passed.
- `docker compose up -d market-data`: passed.
- `mvn -q -Dtest=KrakenDataProviderDispatchTest,KrakenTradeMapperTest test`:
  passed.
- Market Data container started successfully, connected to PostgreSQL, applied
  migrations, registered with Eureka, and completed market synchronization.
- `GET /api/v1/markets`: returned `200`.
- Runtime market catalogue contained 204 persisted `LINEAR_PERPETUAL` markets
  and 1461 Spot markets.
- `GET /api/v1/markets/9422dae2-f169-474d-9ade-5307b6d2eef3/ohlc?interval=ONE_MINUTE&limit=5`:
  returned `200` with live Kraken Futures BTC/USD candles, provider-neutral
  identity, source timestamps, volume, and `synthetic=false`.
- The invalid unauthenticated Market Facts probe returned `403`; the internal
  endpoint is JWT-protected as designed.
- Authenticated Market Facts runtime probe returned `200` with readiness
  `AVAILABLE`; activity was `INSUFFICIENT_DATA` because Kraken Futures Charts
  does not provide `vwap` and the deterministic activity calculation therefore
  has no quote notional.
- Futures ticker delivery was validated through the direct Market Data and
  Gateway WebSocket paths.
- Authenticated Gateway subscription requests for Futures `ORDER_BOOK` depth 10
  and `TRADES` returned `204`.
- Authenticated Gateway WebSocket connections delivered live Futures order-book
  snapshots and recent-trades snapshots for BTC/USD.
- Order-book snapshots contained coherent bids/asks, best bid/ask, spread,
  volumes, imbalance, and source timestamps.
- Recent-trades snapshots contained stable identifiers from Kraken `uid`, side,
  price, quantity, notional, and source timestamps.
- Duplicate subscriptions, partial unsubscribe, final unsubscribe, and
  resubscription after cleanup were validated live.
- Final unsubscribe stopped publication; a subsequent subscription reconnected
  and resumed both streams.
- No Kraken parsing, invalid Futures trade, or pre-snapshot order-book errors
  appeared in the runtime logs during the final validation.
- Review follow-up: verified against the live Kraken Charts payload that candle
  timestamps are epoch milliseconds; corrected the DTO documentation and kept
  the millisecond conversion.
- Review follow-up: Futures requests now reject markets without a persisted
  `providerSymbol` instead of falling back to the canonical symbol.
- Review follow-up: order-book depth and trade registrations now maintain
  independent counters so partial unsubscribe preserves remaining consumers.
- Review follow-up tests cover missing Futures provider symbols and duplicate
  order-book/trade subscriptions with partial unsubscribe.
- Review follow-up: WebSocket subscriptions are replayed after reconnection and
  remote unsubscribe is sent only for the last matching consumer.
- Review follow-up test covers active subscription replay after reconnection.

## Remaining Validation

- Human code review remains pending.

## Scope Confirmation

No Challenge, Risk, settlement, leverage, margin, liquidation, funding, or live
derivative execution behavior was changed.

No commit or push was performed.
