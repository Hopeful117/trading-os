# Story 0042 - PAPER Local Full Exit

## Metadata

**ID:** `0042`

**Status:** IMPLEMENTED - REVIEWED - ACCEPTED

**Baseline:** `main` at `9843e4d` (Story 0041 merged)

**Related ADRs:** ADR-042, ADR-043

## Goal

Allow a human trader to fully close one persisted local PAPER position through
the normal execution and audit model, producing durable CLOSED state and
realized PnL without routing the action through Broker Service.

## Scope

- Add an explicit semantic distinction between `ENTRY` and `EXIT` execution.
- Expose one mode-neutral `ClosePosition` application action for the selected
  account position.
- Resolve and authorize the local PAPER `Trade` server-side.
- Derive the opposite execution side and the complete current quantity.
- Simulate the exit using the existing Market Data and order/fill path.
- Settle the fill atomically against the local Account and target Trade.
- Persist exit price, close timestamp, realized PnL, and CLOSED status.
- Preserve idempotency, audit, and explicit failure outcomes.
- Keep LIVE close routed through `PositionCloseCommand` and Broker Service.
- Update the frontend only as needed to invoke the neutral close action and show
  local PAPER outcome semantics.

## Out Of Scope

- Partial close or quantity selection.
- Exposure reversal, hedging, or opposite-side netting.
- Provider FIFO or LIVE close redesign.
- Automatic stop-loss/take-profit execution.
- Pending/partial simulated orders.
- New fee, slippage, margin, liquidation, or complete ledger model.
- Universal persistent `Position` aggregate.
- Dashboard balance/equity redesign.
- Complete PAPER Risk Facts implementation.

## Acceptance Criteria

- [x] A human-authorized request can fully close an OPEN PAPER `Trade` by its
      stable local identifier.
- [x] Account ownership and canonical Account-to-BrokerAccount relation are
      validated before mutation.
- [x] The target must be PAPER and OPEN; missing, closed, or wrong-mode targets
      are rejected without financial mutation.
- [x] The server derives side and quantity from the reloaded Trade.
- [x] LONG closes use SELL and SHORT closes use BUY.
- [x] Quantity is exactly the current target quantity; partial close and excess
      quantity are rejected.
- [x] An EXIT cannot create or aggregate an opposite OPEN Trade.
- [x] A successful close persists CLOSED status, exit price, close timestamp,
      and deterministic realized PnL.
- [x] LONG and SHORT profit and loss accounting follows the formulas documented
      in `repository-analysis.md`.
- [x] Account balances and equity are updated atomically with Trade state.
- [x] Duplicate idempotency keys return the original result and do not settle
      twice.
- [x] Concurrent close attempts cannot settle the same target twice.
- [x] Local settlement failure rolls back or produces an explicit recoverable
      state; it is never reported as a successful closed position.
- [x] PAPER close does not call Broker Service mutation or reconciliation APIs.
- [x] LIVE close behavior remains unchanged and provider-authoritative.
- [x] Story 0041 position query excludes the closed PAPER Trade after reload.
- [x] UI does not send a PAPER local ID as `brokerPositionReference`.

## Accounting Contract

The current execution-path contract is per-asset balances plus a persisted
equity scalar. There is no scalar `Account.balance`, and raw amounts from
different assets must not be summed as USD valuation. PAPER entry behavior is
already established by `PaperSettlementService`:

```text
BUY  entry: quote -= notional + fee; base += quantity; equity -= fee
SELL entry: base  -= quantity; quote += notional - fee; equity -= fee
```

The exit must use the actual simulated `BrokerOrder.Fill` price and fee and
reverse the position movement without creating an opposite Trade:

```text
LONG  exit/SELL: base -= quantity; quote += exitNotional - exitFee
SHORT exit/BUY:  base += quantity; quote -= exitNotional + exitFee
```

`Trade.pnl` is gross direction-aware realized PnL:

```text
LONG:  (exitPrice - entryPrice) * quantity
SHORT: (entryPrice - exitPrice) * quantity
```

Because the current `Trade` model has no fee field, fees remain Account-level
effects. Entry fees have already reduced equity and must not be charged again
at exit. The complete round-trip invariant is:

```text
final quote = initial quote + gross PnL - entry fee - exit fee
final base  = initial base
final equity = initial equity + gross PnL - entry fee - exit fee
```

The current simulator records zero fees, so a LONG 2 @ 100 closed at 110 from
an initial USD 10,000/no-base account ends at USD 10,020, base 0, equity
10,020, and Trade PnL 20. A SHORT 2 @ 100 requires initial base 2 and closed
at 90 ends with the original base 2, USD 10,020, equity 10,020, and PnL 20.
Losses reduce quote holdings and equity by the same direction-aware formula.

The legacy `TradingServiceImpl.closeTrade()` is not the Story boundary: it
updates equity and Trade PnL but does not mutate asset balances or use a
simulated fill. Story 0042 must extend the execution settlement path instead.

## Failure Semantics

- Unauthorized account/Trade: typed authorization failure, no mutation.
- Account-to-BrokerAccount mismatch or wrong execution mode: reject, no
  provider call and no local mutation.
- Target missing or already closed: explicit not-found/already-closed outcome.
- Concurrent target claim: explicit conflict or already-processing outcome.
- Invalid market quote: reject as unavailable/invalid; no financial settlement.
- Simulated order rejection: persist normal rejected execution outcome; Trade
  remains OPEN.
- Unexpected local settlement failure: transaction rollback or recoverable local
  state, never definitive success.
- Repeated request with an existing idempotency key: return the persisted
  original outcome without replaying settlement.

## Architectural Constraints

The implementation may generalize `ExecutionIntent` or introduce an
exit-specific authorization, but it must preserve common order/fill/audit
concepts without forcing entry TradePlan/T1 prerequisites onto a reducing exit.
The LIVE `PositionCloseCommand` remains broker-specific. No new ADR is required
unless implementation proposes a new authority boundary, universal Position
aggregate, or materially different accounting model.

## Test Intent

- Unit-test exit target resolution, side derivation, quantity rules, and PnL.
- Test LONG and SHORT profit/loss, fees, precision, and balance effects.
- Test entry followed by close across repository reload.
- Test ownership, canonical relation, mode, missing target, and closed target.
- Test idempotent replay and concurrent close claim.
- Test rejected/invalid/unavailable simulated exit without mutation.
- Test zero Broker Service calls for PAPER and compatibility for LIVE.
- Test Story 0041 query no longer returns the closed Trade.
- Test frontend uses neutral local-target semantics.

## Definition Of Done

- [x] Repository Analysis approved.
- [x] Implementation Plan approved.
- [x] Implementation and tests completed within this scope.
- [x] Relevant Trading Core validation passes.
- [x] Human diff review and code review completed.

Implementation is present in the worktree. Dedicated persistence round-trip,
concurrent race, rollback, idempotency replay, and post-reload position-query
proof now exist in `PaperExitAcceptanceIntegrationTest`, including verification
that the LIVE broker execution client is untouched. Story 0042 is accepted after
human architectural, implementation, code, and acceptance-evidence review.
