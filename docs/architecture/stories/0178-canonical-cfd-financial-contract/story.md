# Story 0178 - Canonical CFD Financial Contract

## Metadata

**ID:** `0178`
**Title:** Canonical CFD Financial Contract
**Status:** Implemented Locally - Awaiting Human Review
**Related ADR:** `ADR-082`
**Related ADRs:** `ADR-081`, `ADR-051`, `ADR-052`
**Related Stories:** `0084`, `0085`, `0177`

---

## Goal

Define the validated provider-neutral financial contract required to represent
CFD instrument metadata before later notional, P&L, risk, valuation or
settlement calculations are introduced.

The contract must preserve canonical quantity semantics, precise constraints and
valuation provenance while keeping cTrader-specific facts inside the Market Data
adapter boundary.

---

## Context

ADR-082 defines CFD accounting and valuation semantics but requires a separate
canonical financial contract before calculations or settlement are implemented.

ADR-081 requires explicit cTrader product classification and prohibits inferring
product identity from provider symbols or account environment labels.

The existing cTrader Market Data adapter already normalizes provider volume
constraints. Trading Core requires a provider-neutral representation of those
normalized facts.

---

## Problem

The repository did not have a validated provider-neutral contract containing the
instrument identity, product type, currencies, quantity unit, contract
multiplier, precision, quantity constraints and provenance required by ADR-082.

The existing cTrader volume metadata also derived quantity precision from the
step alone, which could underrepresent the precision required by the minimum or
maximum quantity.

---

## Scope

- Define an immutable provider-neutral `InstrumentFinancialContract`.
- Validate required identity, product type, currencies, quantity unit,
  multiplier, precision, quantity constraints and provenance.
- Map normalized Market Data CFD facts into the canonical contract.
- Require the contract multiplier as an authoritative mapper input; do not infer
  it from cTrader symbols, account environments or textual identities.
- Normalize cTrader centi-unit volume constraints into canonical underlying
  units.
- Derive cTrader quantity precision from the normalized minimum, maximum and
  increment constraints.
- Preserve source compatibility for the existing five-argument
  `MarketConstraints` constructor while failing closed when the new quantity
  increment is unavailable.
- Add deterministic unit and regression tests.

---

## Out of Scope

- CFD notional, P&L, stop-risk or FX conversion calculators.
- Mark-to-market valuation or valuation snapshot persistence.
- PAPER or LIVE CFD settlement behavior.
- Margin, leverage, financing, commission or liquidation semantics.
- Database migrations or historical-data changes.
- Execution, broker order placement, position mutation or reconciliation.
- Changes to Risk Domain formulas or settlement compatibility policy.
- Provider-authenticated cTrader execution or sandbox validation.
- Frontend, AI, strategy or trade-plan changes.

---

## Acceptance Criteria

- [x] An immutable provider-neutral CFD financial contract exists in Trading
      Core.
- [x] Missing, invalid or contradictory contract metadata fails closed.
- [x] The contract requires an explicit positive contract multiplier and never
      infers one from provider symbols or account environment.
- [x] EUR/USD CFD normalized facts map to `EUR/USD`, `CFD`, `EUR`, `USD`,
      `UNDERLYING_UNITS`, validated quantity constraints and provenance.
- [x] cTrader centi-unit volume constraints are converted to canonical
      underlying units inside the cTrader adapter.
- [x] Quantity precision accounts for minimum, maximum and increment values.
- [x] Unclassified cTrader symbols remain rejected rather than defaulting to
      SPOT.
- [x] Existing settlement compatibility remains unchanged and rejects
      unsupported `CASH + CFD` and `INVENTORY + CFD` combinations.
- [x] No broker, account, position, execution or settlement mutation is
      performed.
- [x] Focused Trading Core and Market Data tests pass.
- [x] No database migration or unrelated module behavior is changed.

---

## Constraints

- Preserve the service responsibility boundaries defined by the repository
  architecture.
- Keep provider-specific cTrader types and conversion semantics in the Market
  Data adapter.
- Treat missing authoritative multiplier or provenance as unavailable metadata.
- Use exact decimal arithmetic for all financial and quantity values.
- Keep risk, settlement and execution decisions deterministic.
- Do not silently change existing account/product compatibility behavior.
- Do not perform broker or provider mutations during validation.
- Do not commit, push or merge automatically without human authorization.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-082-cfd-financial-model-and-mark-to-market-accounting.md`
- `docs/architecture/adr/ADR-081-ctrader-instrument-product-classification.md`
- `docs/architecture/adr/ADR-051-cash-settled-derivative-paper-settlement.md`
- `docs/architecture/adr/ADR-052-paper-account-equity-valuation.md`

---

## Relevant Modules

- `trading-core`
- `market-data`

---

## Validation

- Run the focused `InstrumentFinancialContractTest`,
  `InstrumentFinancialContractMapperTest` and
  `SettlementCompatibilityPolicyTest` Trading Core tests.
- Run the focused `CTraderMarketDataTest` and
  `MarketProductIdentityPersistenceTest` Market Data tests.
- Run `git diff --check`.
- Review the complete implementation diff.
- Do not perform authenticated cTrader execution or broker mutation.

The complete Trading Core and Market Data suites may exceed the local bounded
command timeout; a timeout is reported as inconclusive rather than as a pass.

---

## Definition of Done

- [x] Implementation completed locally.
- [x] Relevant focused validation executed.
- [x] Independent code review completed and review findings addressed.
- [ ] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [ ] Engineering Report completed
- [ ] Human commit created
- [ ] Pull request validated and merged
