# Implementation Report - Story 0042

## BASELINE

```text
BRANCH = main
HEAD = 9843e4d4a50719cd31b0a1d6e4448c5c434e0616
ORIGIN_MAIN = 9843e4d4a50719cd31b0a1d6e4448c5c434e0616
WORKTREE_STATUS = Story 0042 artifacts were pre-existing; implementation changes are now uncommitted
STORY_0041_PRESENT = yes
STORY_0042_DOCUMENTATION_PRESENT = yes
ADR_042_PRESENT = yes
ADR_043_PRESENT = yes
```

## IMPLEMENTED_SCOPE

- Added explicit `ExecutionPurpose.ENTRY` and `ExecutionPurpose.EXIT`.
- Added a persisted optional local `targetTradeId` to execution intents.
- Added PAPER local close dispatch while preserving the existing LIVE close
  command and Broker Service path.
- Added server-side Account/BrokerAccount/Trade validation and server-derived
  opposite side and full quantity.
- Reused the simulated execution, order, fill, finalization, and event path.
- Added local exit settlement and realized PnL using the existing calculator.
- Added Account/Trade optimistic locking and a migration for durable races.
- Added focused LONG/SHORT settlement and fee tests.
- Updated the minimum positions UI to send `tradeId` for local PAPER close and
  retain broker-only FIFO/reconciliation messaging.

## EXECUTION_PURPOSE_DESIGN

`ExecutionIntent` now carries `ENTRY` or `EXIT`. Entry intents retain the
existing TradePlan/risk references. Exit intents carry no entry TradePlan or T1
reference and instead carry a required local target Trade ID.

## EXIT_TARGET_DESIGN

`PaperExitService` reloads the financial Account, validates ownership and the
canonical linked BrokerAccount, then reloads an OPEN Trade from the Account's
local collection. It derives the opposite side and current full quantity. The
frontend cannot supply side, quantity, price, or PnL.

## MODE_DISPATCH

`PositionCloseController` uses `tradeId` for the PAPER request shape and routes
it to `PaperExitService`. Existing requests containing only
`brokerPositionReference` continue through `PositionCloseService` for LIVE.

## CONCURRENCY_MECHANISM

```text
CONCURRENCY_MECHANISM = JPA optimistic locking on Account and Trade via @Version
WHY_THIS_MECHANISM = Existing execution entities already use @Version; the
                     smallest durable extension protects the aggregate that
                     carries the financial mutation.
TARGET_CLAIM_POINT = PaperSettlementService, immediately before changing the
                      OPEN target Trade and Account balances.
TRANSACTION_BOUNDARY = PaperExitService and ExecuteTradeService are transactional;
                        PAPER settlement participates in that transaction.
LOSING_REQUEST_BEHAVIOR = OptimisticLockException rolls back the losing
                           execution transaction; the caller does not receive a
                           successful settlement.
```

The mechanism is durable and not an in-memory lock. The acceptance integration
test invokes two independent close requests concurrently and proves that only
one EXIT intent/order is committed and only one financial settlement succeeds.

## IDEMPOTENCY_MECHANISM

The existing unique execution-intent idempotency key is reused. PAPER replay
returns the existing intent only when purpose, initiator, BrokerAccount, and
target Trade also match. A mismatched reuse is rejected as a conflict.

## TRANSACTION_BOUNDARY

The PAPER close application call creates the EXIT intent, runs the simulated
execution pipeline, settles the Account/Trade, and finalizes execution within
the Spring transaction. Settlement failure therefore prevents a successful
financial commit.

## MARKET_DATA_EXECUTION

The existing `SimulatedExecutionAdapter` remains authoritative for the
synthetic executable fill: SELL consumes bid and BUY consumes ask, with no last
price fallback. Existing quote validation and LIMIT behavior are unchanged.

## PAPER_SETTLEMENT

`PaperSettlementService` branches on explicit purpose. ENTRY behavior is
unchanged. EXIT validates the target and full fill quantity, updates cash asset
rows, computes gross PnL with `TradingCalculatorService`, closes the Trade, and
updates equity by gross PnL minus exit fee.

## LONG_ACCOUNTING

LONG EXIT is SELL: base decreases by quantity and USD increases by exit
notional minus fee. The focused test verifies a 2 @ 100 position closed at 110
with a 0.5 fee produces USD 10,019.5, base zero, equity 10,019.5, and gross
Trade PnL 20.

## SHORT_ACCOUNTING

SHORT EXIT is BUY: base increases by quantity and USD decreases by exit
notional plus fee. The focused test starts with existing base inventory and
verifies SELL 2 @ 100 followed by BUY 2 @ 90 restores base 2 and produces USD
and equity 10,020 with gross PnL 20. No borrowing or negative inventory is
introduced.

## FEE_ACCOUNTING

Fees are consumed from the recorded Fill. `Trade.pnl` remains gross because the
legacy Trade model has no fee field. Entry fees are applied by entry settlement;
exit fees are applied once by exit settlement. The simulator still generates
zero fees.

## TRADE_LIFECYCLE

Successful EXIT persists `exitPrice`, `closedAt`, gross `pnl`, and `CLOSED`.
Closed targets and full-quantity mismatches are rejected. No opposite OPEN
Trade is created.

## LIVE_ISOLATION

LIVE requests continue to use `PositionCloseService`, `PositionCloseCommand`,
`BrokerPositionClosePort`, and provider reconciliation. The new PAPER service
does not call the LIVE close port.

## STORY_0041_INTEGRATION

Story 0041's local position query already filters for OPEN Trades. The
acceptance integration test now proves that a persisted CLOSED transition
removes the successful target from the query after repository reload.

## FRONTEND_CHANGES

PAPER positions now use the same close control but send `{ tradeId }`; LIVE
positions send `{ brokerPositionReference }`. FIFO and reconciliation controls
remain broker-only.

## TESTS

- `PaperSettlementExitTest`: passed, 2 tests.
- `PaperExitAcceptanceIntegrationTest`: passed, 4 tests covering persistence
  round-trip, idempotency, concurrency, rollback, and broker isolation.
- `mvn -q test`: passed.
- `npm run check`: passed, 294 tests and production build.
- Build warnings remain for existing bundle/style budgets.
- `git diff --check`: passed.

## REGRESSIONS

Existing PAPER entry, LIVE close, persistence, mapper, security, dashboard, and
position-close tests passed in the Trading Core suite. Existing frontend tests
were updated only for the now-actionable local PAPER close behavior.

## KNOWN_LIMITATIONS

- The close response is adapted to the existing position-close response shape.

This is non-blocking future refinement. It was not changed as part of Story
0042.

## FINAL_ACCEPTANCE

```text
HUMAN_REVIEW = APPROVED
ARCHITECTURE_REVIEW = APPROVED
IMPLEMENTATION_REVIEW = APPROVED
ACCEPTANCE_EVIDENCE = APPROVED
CODE_REVIEW = APPROVED
BLOCKING_FINDINGS = NONE
STORY_ACCEPTANCE = APPROVED
```

Accepted validation evidence:

- `PaperSettlementExitTest` = PASS.
- `PaperExitAcceptanceIntegrationTest` = PASS, 4 tests.
- `mvn -q test` = PASS.
- `npm run check` = PASS, 294 frontend tests and production build.
- `git diff --check` = PASS.

## OUT_OF_SCOPE

Partial close, reversal, hedging, borrowing, margin, new fees/slippage,
universal Position, ledger/dashboard redesign, complete PAPER Risk Facts, and
LIVE close redesign remain out of scope.

## WORKTREE_STATE

All changes remain uncommitted in the worktree for human review. No commit,
push, merge, or PR was performed.
