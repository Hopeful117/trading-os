# Story 0085 - Implementation Report

## Result

Implemented cash-settled derivative PAPER entry and exit settlement for
`CASH + LINEAR_PERPETUAL` while preserving the existing `INVENTORY + SPOT`
settlement path.

## Repository State

- Starting branch: `main`
- Starting HEAD: `968b6cbdd7180fc7ecd781e082aaa8deb106854c`
- Starting worktree: clean
- No commit, push, merge, reset, or checkout was performed.
- DevLog context was requested but unavailable because the provider quota was
  exhausted.

## Implemented

- Removed the unconditional CASH rejection from `PaperSettlementService`.
- Preserved fail-closed Account/Product compatibility enforcement.
- Added the cash derivative entry branch for BUY and SELL directions.
- Cash derivative entry creates the existing OPEN `Trade`, applies the opening
  fee to equity, and leaves balances unchanged.
- Cash derivative exit reuses existing full-exit validation and P&L calculation,
  applies gross P&L less closing fee to equity, and leaves balances unchanged.
- Preserved inventory spot balance mutation, trade lifecycle, persistence,
  idempotency, transaction, and Challenge reevaluation behavior.
- Added long, short, fee, balance-invariance, repeated-exit, and failure tests.
- Added a vertical execution regression proving a cash derivative PAPER entry
  routes through simulation, finalization, and settlement without changing
  balances.
- Added concurrent cash derivative exit coverage with non-zero closing fees,
  one-winner persistence, single P&L/fee realization, and balance invariance.
- Added controller coverage for optimistic-lock close conflicts returning HTTP
  409.
- Updated the former CASH unsupported test to assert the compatibility failure.

## Files Changed

- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/PaperSettlementService.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/execution/PaperSettlementExitTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/execution/PaperCashDerivativeSettlementTest.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/positionclose/api/PositionCloseController.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/positionclose/PaperExitAcceptanceIntegrationTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/positionclose/api/PositionCloseControllerTest.java`

## Validation

- Focused tests:
  `mvn -q -Dtest=PaperSettlementExitTest,PaperCashDerivativeSettlementTest test`
  passed.
- Full `trading-core` verification: `mvn -q verify` passed.
- `git diff --check`: passed.

## Deferred

- Mark-to-market and continuous derivative equity valuation remain Story 0086.
- Funding, leverage, margin, liquidation, partial exits, and Position remain
  out of scope.
