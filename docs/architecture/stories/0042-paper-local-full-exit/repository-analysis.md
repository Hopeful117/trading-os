# Repository Analysis - Story 0042

## Repository State

| Field | Value |
|---|---|
| Branch | `main` |
| HEAD | `9843e4d4a50719cd31b0a1d6e4448c5c434e0616` |
| Baseline | Story 0041 merged |
| Target module | `trading-core` |
| Implementation performed | No |
| New ADR required | No |

The worktree was clean at investigation start. This analysis creates only the
Story 0042 artifacts.

## Governing Decisions

- ADR-042: Trading Core owns PAPER position, lifecycle, settlement, and realized
  PnL; Broker Service remains authoritative for LIVE.
- ADR-042: product action is conceptually `ClosePosition`; PAPER delegates to an
  explicit `EXIT` execution intent and local settlement.
- ADR-042: first milestone is `FULL_CLOSE_ONLY`, with no partial close, reversal,
  hedging, provider FIFO, or automatic exits.
- ADR-043: `Account.accountId` is financial identity and linked
  `BrokerAccount.id` is routing identity. They remain distinct.
- Story 0041: a PAPER position is a persisted OPEN `Trade`, normally addressed
  by its local `tradeId`; no broker position reference is fabricated.

## Current Evidence

### Existing Entry Pipeline

The common execution pipeline is:

```text
T1 revalidation -> validation -> idempotency -> attempt -> submission
  -> response/fill -> finalization -> PAPER settlement
```

Relevant code:

- `execution/domain/aggregate/ExecutionIntent.java`
- `execution/domain/model/ExecutionParameters.java`
- `execution/application/service/ExecuteTradeService.java`
- `execution/application/pipeline/ExecutionFinalizationStep.java`
- `execution/infrastructure/adapter/SimulatedExecutionAdapter.java`
- `execution/application/service/PaperSettlementService.java`

`ExecutionIntent` currently requires a `TradePlanReference` and
`RiskApprovalReference`, and its parameters contain instrument, side, order type,
quantity, and limit price only. There is no persisted `ExecutionPurpose` or
local exit target. Side alone is insufficient because `SELL` can mean either a
short entry or a long reduction.

The simulated adapter already selects executable prices from Market Data: BUY
at ask and SELL at bid. It returns an acknowledged synthetic order and fill.
PAPER reconciliation is not a provider authority and must not be used to prove
local settlement.

### Existing PAPER Settlement

`PaperSettlementService` loads the canonical linked `BrokerAccount`, resolves
the financial `Account`, validates owner/provider consistency, then mutates
balances and local trades in one Spring transaction.

Current behavior is entry-oriented:

- BUY deducts quote currency and adds base quantity.
- SELL deducts base quantity and adds quote currency.
- fees are deducted from equity; the current simulator supplies no meaningful
  fee model beyond the fill value.
- same-symbol/same-side OPEN trades are weighted-average aggregated.
- an opposite-side fill creates or aggregates the opposite OPEN trade; it does
  not close the existing trade.

`Trade` already has `tradeId`, side (`TradeType`), entry price, quantity,
`exitPrice`, `pnl`, `openedAt`, `closedAt`, and `TradeStatus`. There is no
explicit settlement lineage, exit execution ID, or optimistic version field on
`Account` or `Trade`. `ExecutionIntentEntity` does have a JPA version and a
unique idempotency key.

## Accounting Semantics From Repository Reality

### Current Implemented Semantics

`Account` does not define balance or equity behavior itself. Its fields are
persisted mutable values. The observable meanings are supplied by callers:

| Question | Current behavior and evidence |
|---|---|
| `Account.balance` | There is no scalar `Account.balance` field. `Account.balances` is a collection of per-asset `AccountBalance.amount` values. `AccountServiceImpl.getTotalBalance()` sums raw amounts across all assets, without conversion; `getAvailableBalance()` selects one named asset. |
| `Account.equity` | A persisted scalar initialized to PAPER initial capital by `BrokerAccountService.createPaperAccount()`. `PaperSettlementService` subtracts fill fees only. It does not mark open positions to market or add/remove entry notional. The dashboard has a separate calculated view, `balance + unrealizedPnl`, but that is not the persisted PAPER settlement invariant. |
| Asset balances | Holdings/available quantities by asset, not a normalized reporting-currency valuation. The PAPER settlement path creates or updates the named quote/base asset rows. |
| PAPER BUY entry | `PaperSettlementService.updateBalances()`: deduct `quote notional + fee`; add `base quantity`. `updatePosition()` creates or aggregates an OPEN BUY Trade. `recalculateEquity()` subtracts the fee only. |
| PAPER SELL entry | Deduct `base quantity`; add `quote notional - fee`. `updatePosition()` creates or aggregates an OPEN SELL Trade. Equity again changes only by `-fee`. This is an entry operation in the current code, even when it consumes base holdings. |
| Fees | `BrokerResponseProcessingStep` currently creates every simulated fill with `BigDecimal.ZERO` fee. `PaperSettlementService` deducts the fee from quote/base cash according to the side and separately subtracts it from persisted equity. No non-zero simulator fee model exists. |
| `Trade.pnl` | Entry settlement leaves it null. The legacy `TradingServiceImpl.closeTrade()` computes gross direction-aware PnL through `TradingCalculatorService.calculatePnL()` and stores it, but this method does not implement the execution-path PAPER balance settlement. |
| Open equity | `AccountEquityService` calculates a dashboard value as scalar balance plus unrealized PnL when given those inputs. `PaperSettlementService` does not update persisted equity for unrealized PnL. |

`TradingCalculatorServiceImpl.calculatePnL()` is the authoritative existing
direction formula:

```text
BUY/LONG:  (exitPrice - entryPrice) * quantity
SELL/SHORT: (entryPrice - exitPrice) * quantity
```

The existing `TradingServiceImplTest` proves both directions and proves that a
legacy full close adds gross PnL to `Account.equity`. The
`PaperExecutionVerticalRegressionTest` proves the execution-path distinction:
BUY entry at 50,100 with quantity 0.1 changes USD from 10,000 to 4,990, adds
0.1 BTC, and leaves equity at 10,000; SELL entry at 2,995 with quantity 1 and
one ETH starting balance changes USD from 10,000 to 12,995, ETH to zero, and
leaves equity at 12,995. Both simulated fees are asserted to be zero.

`AccountServiceImpl.getTotalBalance()` must not be used as a financial USD
balance for the round-trip invariant: summing USD and BTC/ETH quantities is not
a currency conversion. For this Story, “balance” means the relevant per-asset
holding rows, with quote-currency net proceeds called out explicitly.

### Required Story 0042 Semantics

The execution-path PAPER close must preserve the implemented per-asset model
and add only the missing reducing-exit mutation:

1. Capture the target Trade's persisted entry basis and full quantity.
2. Execute the opposite side and record the actual simulated fill price and
   fee from `BrokerOrder.Fill`.
3. Reverse the entry asset movements for the full quantity, using the exit
   cash flow:
   - LONG close/SELL: deduct base quantity and add quote notional minus exit
     fee.
   - SHORT close/BUY: add base quantity and deduct quote notional plus exit
     fee.
4. Set `Trade.exitPrice`, `Trade.closedAt`, `Trade.pnl`, and `CLOSED`.
5. Update persisted equity by gross realized PnL minus the exit fee. Entry fees
   have already reduced equity at entry, so they must not be subtracted again.
6. Commit Account balances, equity, and Trade state atomically.

The resulting round-trip invariant is:

```text
final quote holding = initial quote holding
                     + gross realized PnL
                     - entry fee
                     - exit fee

final base holding = initial base holding

final persisted equity = initial persisted equity
                         + gross realized PnL
                         - entry fee
                         - exit fee
```

This assumes no unrelated account mutation and uses the actual fees in each
fill. With the current simulator, both fees are zero. The base holding returns
to its pre-entry value for both LONG and SHORT. The Trade's `pnl` stores gross
realized PnL, matching the existing calculator and legacy close tests; fees are
account-level settlement effects because the current `Trade` model has no fee
field.

For an initial USD 10,000 account with no base holding, a LONG 2 @ 100 entry
and SELL 2 @ 110 exit produce USD 10,020, base 0, equity 10,020, and Trade PnL
20 with zero fees. A SHORT 2 @ 100 requires initial base 2; the entry produces
USD 10,200 and base 0, and a BUY 2 @ 90 exit produces USD 10,020 and base 2,
equity 10,020, and Trade PnL 20. Loss cases use the same formulas and reduce
the quote holding and equity.

The legacy `TradingServiceImpl.closeTrade()` is evidence for PnL direction and
equity addition only. It is not the implementation boundary for this Story
because it accepts a client-supplied exit price, does not resolve PAPER mode,
does not mutate balances, and does not use the simulated fill.

### LIVE Close Must Remain Separate

`PositionCloseService` resolves an opaque broker reference, creates a
`PositionCloseCommand`, calls `BrokerPositionClosePort`, and supports provider
reconciliation. Its fields and lifecycle assume broker identity, mutation scope,
external order state, and provider convergence. It must not be reused for PAPER
just to share a command name.

### Risk and Market Data

The current T1 service constructs an entry `ProposedTrade` from a TradePlan and
required-margin facts. ADR-042 explicitly defines a reducing exit as not being a
new entry and says the entry TradePlan/T0 approval must not be required for the
first PAPER close. The exit still requires deterministic authorization and must
fail closed for invalid ownership, target state, quantity, or execution context.

Market Data provides bid, ask, last price, tradability, timestamp, and explicit
status. For a full close, the simulated execution side is the opposite of the
target Trade side. Therefore the fill side determines the executable mark:

- close LONG with SELL at bid;
- close SHORT with BUY at ask.

No last-price fallback or new freshness policy should be introduced by this
Story. The existing simulator's quote validation and limit handling should be
reused or made explicit for the exit path.

## Required Invariants

- Authenticate and authorize the caller against `Account.accountId` and the
  target local `Trade`.
- Resolve the linked `BrokerAccount` and require `ExecutionMode.PAPER` before
  local exit processing.
- Reload the target and require `TradeStatus.OPEN`.
- Derive side and full current quantity server-side.
- Derive execution side as the opposite side; reject caller-supplied mismatch.
- Reject requested quantity other than the complete current quantity in V1.
- Never create an opposite OPEN Trade as a consequence of an EXIT.
- Apply close state, exit price, close time, realized PnL, and balance/equity
  changes atomically, or roll back the complete settlement.
- Make repeated requests with the same idempotency key return the original
  outcome without a second settlement.
- Prevent two concurrent closes from settling the same Trade twice.
- Do not use Broker Service or provider reconciliation for PAPER.
- Keep LIVE `PositionCloseCommand` behavior unchanged.

## Accounting Answers

```text
WHAT_IS_ACCOUNT_BALANCE = No scalar field exists. Account.balances stores raw
                          per-asset amounts; a requested asset amount is the
                          meaningful available balance. Cross-asset summation
                          is legacy convenience, not valuation.
WHAT_IS_ACCOUNT_EQUITY = Persisted scalar Account.equity. In the PAPER
                         execution path it starts at initial capital, decreases
                         by recorded fees, and is not marked to market while a
                         Trade is OPEN.
WHAT_DO_ASSET_BALANCES_REPRESENT = Raw holdings/available amounts per asset.
HOW_ENTRY_CHANGES_BALANCES = BUY: quote -(notional + fee), base +quantity.
                             SELL: base -quantity, quote +(notional - fee).
HOW_ENTRY_CHANGES_EQUITY = -fee only in PaperSettlementService; current
                           simulated entry fee is zero.
HOW_FEES_CHANGE_BALANCES = Included in the quote/base cash movement according
                           to order side.
HOW_FEES_CHANGE_EQUITY = Subtracted once from persisted equity per fill.
```

## Accounting Model To Specify

The Story must make the following deterministic examples pass, using the fill
price and fee from the recorded `BrokerOrder.Fill`:

| Position | Exit | Cash effect | Realized PnL |
|---|---|---|---|
| LONG 2 @ 100 | SELL 2 @ 110 | add quote proceeds, remove base 2 | `(110 - 100) * 2 - fees` |
| LONG 2 @ 100 | SELL 2 @ 90 | add quote proceeds, remove base 2 | `(90 - 100) * 2 - fees` |
| SHORT 2 @ 100 | BUY 2 @ 90 | remove quote cost, add base 2 | `(100 - 90) * 2 - fees` |
| SHORT 2 @ 100 | BUY 2 @ 110 | remove quote cost, add base 2 | `(100 - 110) * 2 - fees` |

The implementation must state whether `Account.equity` is updated by realized
PnL, fees, or both, and must preserve the existing entry accounting model. No
unrelated dashboard or complete portfolio-ledger redesign belongs here.

## Decision

`NEXT = IMPLEMENTATION`

Story 0042 can be implemented safely as a focused PAPER full-close vertical
slice if it introduces an explicit exit meaning and target, while reusing the
common order/fill/audit pipeline where semantics match. A new universal
Position aggregate or a second provider-style close command would expand scope
and conflict with ADR-042.

The accounting invariant is now sufficiently specified from repository
behavior. Implementation may be authorized by the human engineer. This
refinement itself did not authorize or perform implementation.

## Validation Expectations

- Entry then full PAPER close survives repository reload with the Trade CLOSED.
- LONG and SHORT realized PnL and balance effects are deterministic.
- `exitPrice`, `closedAt`, and realized `pnl` are persisted.
- Closed trades no longer appear in Story 0041 OPEN position queries.
- Caller cannot close another user's account or trade.
- Missing, closed, wrong-mode, stale, or invalid targets fail without mutation.
- Duplicate idempotency requests do not create a second order, fill, or ledger
  mutation.
- Concurrent close attempts result in one winner and one explicit conflict or
  already-closed outcome.
- PAPER close makes no Broker Service mutation or reconciliation call.
- LIVE close regression tests remain green.
- Relevant Trading Core tests and `git diff --check` pass.
