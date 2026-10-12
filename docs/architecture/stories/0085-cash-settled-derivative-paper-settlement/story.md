# Story 0085 - Cash-Settled Derivative PAPER Settlement

## Metadata

**ID:** `0085`
**Status:** IMPLEMENTED - AWAITING HUMAN REVIEW
**Related ADR:** `ADR-051`

## Goal

Implement cash-settled derivative PAPER entry and exit settlement for
`CASH + LINEAR_PERPETUAL` while preserving existing `INVENTORY + SPOT`
behavior.

## Scope

- Support PAPER derivative ENTRY for long and short directions.
- Deduct opening fees from account equity without changing asset balances.
- Persist the existing `Trade` aggregate as the open derivative position.
- Support full derivative EXIT using the existing execution pipeline.
- Realize gross P&L minus closing fee in account equity.
- Preserve existing trade, full-exit, transaction, idempotency, and Challenge
  reevaluation behavior.
- Reject unsupported Account/Product combinations before financial mutation.
- Add focused settlement and regression coverage.
- Add complete Story workflow artifacts.

## Out of Scope

- Mark-to-market or continuous equity valuation.
- Funding, leverage, margin, liquidation, or maintenance margin.
- Partial exits, scaling, or a Position aggregate.
- Real-broker derivative execution.
- Frontend, Market Intelligence, Risk, Prop, or provider-specific changes.
- Story 0086 behavior.

## Acceptance Criteria

- `CASH + LINEAR_PERPETUAL` PAPER entry supports BUY and SELL.
- Entry changes only settlement-currency equity by the opening fee and creates
  an OPEN `Trade`.
- Entry does not change underlying or settlement balances.
- Exit supports long and short trades using the existing target-trade contract.
- Exit realizes `gross P&L - closing fee` in equity and closes the trade.
- Existing `INVENTORY + SPOT` behavior remains green.
- Unsupported and ambiguous combinations fail closed before mutation.
- Repeated exit cannot realize P&L or fees twice.
- Failed settlement leaves balances, equity, and trades unchanged.
- Challenge reevaluation remains post-settlement and best-effort.
- No Story 0086 behavior is introduced.

## Decisions

Settlement behavior is selected by the existing Account settlement model and
Market Data product identity. `PaperSettlementService` remains the application
boundary and reuses the existing `Trade` lifecycle and P&L calculator. CASH
derivative settlement skips inventory balance mutation; it does not add a
margin or valuation engine.

## Related and Deferred Stories

- Story 0084: Account Settlement Semantics and Instrument Identity Foundation.
- Story 0086: PAPER Derivative Mark-to-Market and Equity Valuation.
- Story 0087: Generic Challenge Account Compatibility and Prop Configuration.
