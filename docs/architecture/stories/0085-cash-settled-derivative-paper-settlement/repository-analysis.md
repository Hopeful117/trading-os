# Repository Analysis - Story 0085

## Status

`RECORDED AFTER IMPLEMENTATION - HUMAN APPROVAL NOT INFERRED`

## Baseline

- Repository: Trading OS
- Starting branch: `main`
- Starting HEAD: `968b6cbdd7180fc7ecd781e082aaa8deb106854c`
- Starting worktree: clean
- Affected module: `trading-core`
- DevLog: unavailable because the provider quota was exhausted

## Relevant Architecture

- Trading Core owns PAPER execution orchestration, Accounts, Trades, and
  settlement mutation.
- Market Data owns canonical product identity.
- Account settlement semantics are represented by `SettlementModel`.
- `SettlementCompatibilityPolicy` allows `INVENTORY + SPOT` and
  `CASH + LINEAR_PERPETUAL` only.
- `PaperSettlementService` is invoked by execution finalization after a filled
  PAPER order and before final execution persistence.

## Current State

- Existing PAPER settlement mutates inventory balances for spot BUY and SELL.
- Existing trade creation, averaging, full-exit validation, P&L calculation,
  equity updates, persistence, and Challenge reevaluation are already present.
- Story 0084 rejects CASH accounts because derivative settlement was deferred.
- `Trade` already represents direction, entry price, quantity, exit price, P&L,
  lifecycle state, and timestamps.

## Required Change

- Remove the CASH guard only for the compatible linear perpetual product.
- Keep compatibility and identity checks before mutation.
- Add a cash derivative branch that records the trade and fees in equity while
  leaving balances unchanged.
- Reuse existing exit target, side, quantity, P&L, lifecycle, persistence, and
  reevaluation behavior.

## Boundaries

- No new Position aggregate.
- No balance-backed collateral or borrowing for derivatives.
- No mark-to-market, funding, leverage, liquidation, or partial close.
- No changes to Broker Service, Market Data, Risk, Challenge ownership, or the
  frontend.

## Validation Needs

- Long and short entry/exit unit coverage.
- Fee and equity coverage.
- Balance invariance coverage.
- Unsupported product/account and failure invariance coverage.
- Repeated-exit/idempotency coverage.
- Existing inventory settlement regression coverage.
