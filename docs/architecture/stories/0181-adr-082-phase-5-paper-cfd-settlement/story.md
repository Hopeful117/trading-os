# Story 0181 - ADR-082 Phase 5 PAPER CFD Cash Settlement and MTM Equity

## Metadata

**ID:** `0181`
**Title:** ADR-082 Phase 5 PAPER CFD Cash Settlement and MTM Equity
**Status:** Draft
**Related ADR:** `ADR-082`
**Related ADRs:** `ADR-042`, `ADR-043`, `ADR-051`, `ADR-078`
**Related Stories:** `0178`, `0179`, `0180`

---

## Goal

Implement the first truthful local PAPER accounting lifecycle for a CFD:
persisted signed exposure, read-only mark-to-market equity, and exactly-once
full-exit realized PnL and cash accounting.

This Story implements ADR-082 Phase 5 only. It does not authorize global CFD
execution compatibility.

## Scope

- Delegate CFD PAPER settlement to a dedicated component rather than extending
  the legacy SPOT or LINEAR_PERPETUAL branch with another product condition.
- Persist positive LONG and negative SHORT CFD exposures using the existing
  persisted provider-neutral contract projection.
- Keep CFD entry and exit out of base-asset inventory mutation and out of full
  notional cash debits.
- Reuse `CfdValuationService`, `MarketValuationPort`,
  `ReadOnlyMarketValuationPort`, and `CfdFinancialCalculator` for deterministic
  mark-to-market projection.
- Project equity as cash balance plus complete unrealized CFD PnL minus only
  supported known unsettled costs.
- Support full CFD exit only, with direction and quantity validation.
- Apply realized PnL and supported costs atomically and exactly once using the
  existing execution provenance/idempotency boundary where sufficient.
- Prove restart-safe reconstruction from persisted cash and exposure state.
- Preserve LIVE provider authority and reject LIVE use of the PAPER component.
- Keep `INVENTORY + CFD` and `CASH + CFD` rejected by
  `SettlementCompatibilityPolicy`.

## Out of Scope

- Changing `SettlementCompatibilityPolicy` or enabling `CASH + CFD`.
- Partial CFD reductions, reversals, pending orders, or liquidation.
- LIVE settlement, broker reconciliation, provider balance or position
  authority.
- Margin inference, leverage-derived collateral, financing, commission, swap,
  spread, or slippage modeling without an existing deterministic persistence
  contract.
- Risk Domain formula or authorization changes.
- Broker orders, broker close requests, cTrader execution, or provider
  mutations.
- Historical CFD backfill or reinterpretation of SPOT/
  `LINEAR_PERPETUAL` records.

## Acceptance Criteria

- A dedicated PAPER CFD accounting boundary supports LONG and SHORT entry with
  canonical signed exposure and complete persisted contract metadata.
- CFD entry does not mutate base-asset inventory or debit full notional cash.
- Duplicate entry settlement is idempotent for the same execution fill.
- PAPER CFD MTM reconstructs persisted exposure after persistence-context
  clearing and computes deterministic long/short PnL through the Phase 4
  valuation boundary and calculator.
- Missing, stale, incomplete, invalid, or wrongly directed valuation facts fail
  closed.
- Equity explicitly follows `cash + unrealized PnL - supported unsettled known
  costs`.
- Full LONG and SHORT exits validate target, direction, quantity, and reversal
  prevention; realize PnL once; close the exposure; and leave no inventory.
- Replay of the same full-exit fill cannot apply realized PnL twice.
- Settlement mutation is atomic across exposure, cash/equity, and settlement
  provenance.
- Existing SPOT and LINEAR_PERPETUAL settlement behavior remains unchanged.
- `INVENTORY + CFD` and `CASH + CFD` remain rejected globally.
- `CTRADER_SAFETY_EXECUTION_ENABLED=false` remains unchanged and all broker
  order/close/provider mutation counts remain zero.

## Validation

- Focused CFD entry, MTM, equity, restart, full-exit, idempotency, atomicity,
  compatibility, and authority tests.
- Regression tests for existing PAPER SPOT and LINEAR_PERPETUAL settlement,
  `CfdFinancialCalculator`, persisted CFD projection, `CfdValuationService`,
  read-only valuation, provenance, and settlement compatibility.
- `trading-core/./mvnw -q test` and relevant Market Data tests when touched.
- `git diff --check` and complete diff review.
- No authenticated broker execution or provider mutation.

## Constraints

- ADR-082 is authoritative for CFD financial semantics.
- Trading Core owns PAPER cash, fills, exposure, and realized state.
- Market Data supplies current marks and FX facts only through provider-neutral
  contracts.
- Missing financial facts fail closed; no unknown cost or margin is defaulted.
- No implementation may enable `CASH + CFD` compatibility.
