# Implementation Plan - Story 0041

This plan implements observation and valuation only. It does not implement
PAPER EXIT, realized PnL, close-time balance mutation, or Story 0042 behavior.

## Step 1 - Neutral Read Contract

**PURPOSE**

Make the existing position projection usable by LIVE broker facts and PAPER
local Trade facts without naming local state as provider authority.

**LIKELY COMPONENTS**

- `dashboard/integration/BrokerPositionFact`
- `dashboard/service/PositionQueryService`
- `dashboard/model/OpenPositionDashboardView`
- neutral fact type or explicit broker/local adapters

**INVARIANTS**

- PAPER ID is persisted `Trade.tradeId`.
- No PAPER provider position ID or mutation scope is fabricated.
- Existing LIVE fields remain compatible unless additive status/source metadata
  is required.

**TESTS**

- LIVE mapping regression.
- PAPER Trade mapping with distinct Account and BrokerAccount IDs.

**LEARNING MODE**

PAIR

## Step 2 - Canonical Mode Routing

**PURPOSE**

Route the existing account positions endpoint by canonical relation and
`BrokerAccount.executionMode`.

**LIKELY COMPONENTS**

- `PositionController`
- `PositionQueryService` or small source selector
- `AccountService` / `AccountRepository`
- `BrokerAccountRepository`

**INVARIANTS**

- Path `accountId` is financial `Account.accountId`.
- `Account.brokerAccountId` resolves routing `BrokerAccount.id`.
- Owner/relation checks precede external calls.
- PAPER makes zero Broker Service account/position calls.

**TESTS**

- LIVE routes to Broker Service.
- PAPER routes locally.
- Distinct IDs, missing relation, unknown mode, and cross-user access.

**LEARNING MODE**

PAIR

## Step 3 - Local PAPER Snapshot

**PURPOSE**

Load and map authoritative local OPEN Trades before the remote Market Data
call.

**LIKELY COMPONENTS**

- `Account`, `Trade`, `AccountRepository`
- local position read adapter/service

**INVARIANTS**

- Only OPEN Trades are returned.
- CLOSED Trades are excluded.
- The query does not mutate persisted financial state.
- Opposite-side positions are not merged.

**TESTS**

- Empty, one, multiple, and invalid local positions.
- Reload after persisted PAPER entry.
- No writes during query.

**LEARNING MODE**

LEARN

## Step 4 - Market Data And Mark Selection

**PURPOSE**

Use the existing catalogue and batch snapshot boundary for valuation.

**LIKELY COMPONENTS**

- `MarketDataClient`
- `MarketResponse`
- `MarketPriceSnapshotDto`
- `MarketPriceSnapshotStatus`

**INVARIANTS**

- No direct Kraken/Broker Service valuation call.
- Symbol mapping is exact or deterministic and unambiguous.
- FRESH plus valid timestamp and positive executable-side quote are required.
- BUY uses bid; SELL uses ask.
- `lastPrice` is not a fallback.
- No new freshness threshold is added.
- Quote currency must equal Account base currency.

**TESTS**

- Batch request, long/short bid-ask selection.
- Fresh, stale, unavailable, unknown market, missing quote.
- Ambiguous symbol and unsupported quote currency.

**LEARNING MODE**

LEARN

## Step 5 - Deterministic Derived View

**PURPOSE**

Calculate supported valuation and preserve explicit unavailable states.

**LIKELY COMPONENTS**

- `PositionValuationService`
- `TradingCalculatorService`
- position response DTO

**INVARIANTS**

- Delegate signed PnL to `TradingCalculatorService.calculatePnL()`.
- Preserve BigDecimal precision and existing percentage rounding.
- Missing mark yields null PnL/percentage and explicit status.
- Persisted `Trade.currentPrice`/`pnl` never override Market Data.

**TESTS**

- Long profit/loss, short profit/loss, precision, and unavailable valuation.
- Existing LIVE valuation regression.

**LEARNING MODE**

LEARN

## Step 6 - API And Minimum UI Safety

**PURPOSE**

Keep `/api/v1/accounts/{accountId}/positions` as the mode-neutral product path
and prevent PAPER positions from invoking LIVE close controls.

**LIKELY COMPONENTS**

- `PositionController` with `ResponseEntity`
- `OpenPositionDashboardView`
- `trading-os-web` position model/service/page/tests

**INVARIANTS**

- No `/paper/positions` parallel endpoint.
- PAPER has no provider mutation reference or Kraken FIFO messaging.
- Angular remains Observable/async-pipe based.
- Dashboard aggregation remains deferred.

**TESTS**

- Controller JSON contract.
- PAPER endpoint without Broker Service.
- LIVE compatibility.
- Frontend PAPER rendering and disabled LIVE-only action.

**LEARNING MODE**

DELEGATE after Steps 1-5 are approved.

## Step 7 - Full Validation

**PURPOSE**

Prove mode isolation, non-mutating reads, and LIVE compatibility.

**LIKELY COMPONENTS**

- Trading Core position/controller/integration tests.
- Angular position tests and build if UI adaptation is included.

**INVARIANTS**

- PAPER Broker Service account/position calls remain zero.
- Query does not mutate Account or Trade state.
- No Story 0042 state-changing behavior is introduced.

**TESTS**

- Full Story 0041 matrix.
- Relevant Trading Core tests.
- Angular tests/build.
- `git diff --check`.

**LEARNING MODE**

PAIR for authority failures; DELEGATE for repetitive fixture repair.
