# Story 0084 - Account Settlement Semantics and Instrument Identity Foundation

## Metadata

**ID:** `0084`
**Status:** IMPLEMENTED - AWAITING HUMAN REVIEW
**Related ADR:** `ADR-051`

## Goal

Make account financial settlement semantics and instrument product identity
explicit without implementing derivative settlement.

## Scope

- Add provider-neutral Account settlement semantics.
- Add SPOT and LINEAR_PERPETUAL product identity to Market Data contracts.
- Add deterministic Account/Product compatibility policy.
- Preserve current PAPER inventory behavior.
- Allow explicit CASH PAPER account creation while rejecting it from the
  unsupported legacy inventory settlement path.
- Migrate existing PAPER and LIVE accounts conservatively.
- Preserve ADR-028, ADR-042, Stories 0078 and 0079, and BrokerAccount's
  technical responsibility.

## Out of Scope

- Cash-settled derivative entry or exit settlement.
- Derivative P&L realization.
- Mark-to-market or continuous equity valuation.
- Funding, leverage, liquidation, margin engines, or accounting ledgers.
- Challenge compatibility enforcement or Prop configuration.
- Universal Position aggregate.
- Frontend changes.

## Decisions

`Account` owns `SettlementModel`; Market Data owns `InstrumentProductType`.
Provider and execution mode do not select settlement semantics. Account/Product
compatibility is deterministic and fail-closed.

Existing PAPER accounts retain `INVENTORY`. Ambiguous historical LIVE accounts
use `UNKNOWN` and cannot be treated as derivative-compatible by the policy.

`AccountType` alone was rejected because it cannot identify two products sharing
the same symbol. `InstrumentProductType` alone was rejected because it cannot
define the meaning of Account balances and equity.

## Acceptance Criteria

- `INVENTORY`, `CASH`, and transitional `UNKNOWN` Account settlement semantics
  are explicit.
- Market identity includes `SPOT` or `LINEAR_PERPETUAL` product type.
- Existing markets migrate to `SPOT`.
- Existing PAPER accounts migrate to `INVENTORY`.
- Existing non-PAPER accounts migrate to `UNKNOWN`.
- `INVENTORY + SPOT` and `CASH + LINEAR_PERPETUAL` are allowed.
- Unsupported and UNKNOWN combinations are rejected.
- CASH PAPER account creation is representable but current settlement rejects it
  before financial mutation.
- Existing PAPER inventory settlement tests remain green.
- No provider, execution-mode, Challenge, or Risk-specific settlement branch is
  introduced.
- No derivative settlement or mark-to-market behavior is implemented.

## Related and Deferred Stories

- Story 0085: Cash-Settled Derivative PAPER Settlement
- Story 0086: PAPER Derivative Mark-to-Market and Equity Valuation
- Story 0087: Generic Challenge Account Compatibility and Prop Configuration
