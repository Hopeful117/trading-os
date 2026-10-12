# Story 0086 - Repository Analysis

## Baseline

The repository baseline is `main` at `d783e3652d3fa50de0f2bcdf307a69fe359e5d82`.
The worktree was clean. Story 0085 is merged and currently mutates
`Account.equity` directly for PAPER CASH entry fees and exit P&L.

## Relevant Existing Contracts

- `Account` owns persisted balances, equity, peak equity, and PAPER trades.
- `Trade.currentPrice` is initialized at entry and is not a freshness signal.
- Market Data exposes `MarketPriceSnapshotDto` with bid, ask, occurred-at, and
  `FRESH`, `STALE`, `UNAVAILABLE`, or `UNKNOWN_MARKET` status.
- Dashboard position valuation already uses direction-aware bid/ask; Story
  0086 selects a provider-neutral midpoint for account equity and documents the
  distinction.
- `ModeAwareRiskFactsProvider` is the application boundary where PAPER facts
  are assembled before the deterministic Risk engine.
- `ChallengeReevaluationService` remains downstream of Risk and requires no
  market lookup or derivative formula.

## Accounting Decision

Add `Account.realizedBalance` and valuation metadata. PAPER CASH settlement
updates only the realized baseline for fees and realized P&L, then recomputes
equity from that baseline and known open-trade marks. Valuation refreshes all
open CASH trades from fresh midpoint snapshots and persists their prices and
the resulting equity atomically under optimistic locking.

Existing Story 0085 PAPER CASH accounts are migrated with
`realizedBalance = equity`, because no prior mark-to-market behavior existed.
Other account classes are not migrated to this semantic.

## Risk and Freshness

PAPER CASH Risk facts refresh valuation before facts are built. Any market
catalog ambiguity, unsupported product, missing price, stale snapshot, or
Market Data failure prevents a complete Risk snapshot. Persisted stale equity
is never presented as fresh.

## Concurrency

Valuation uses the Account optimistic version during persistence. A concurrent
settlement changes the version and causes the valuation write to fail rather
than overwrite the newer realized state.

## Legacy Boundaries

`TradingServiceImpl` and broker synchronization remain unchanged for unrelated
legacy and LIVE paths. They are not used as PAPER CASH valuation authorities.
