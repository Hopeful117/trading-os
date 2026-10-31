# Story 0184 - ADR-082 Phase 7 CFD Risk Integration

## Metadata

**ID:** `0184`
**Title:** ADR-082 Phase 7 CFD Risk Integration
**Status:** Implemented Locally - Awaiting Human Review
**Related ADR:** `ADR-082`
**Related ADRs:** `ADR-028`, `ADR-043`, `ADR-055`, `ADR-078`, `ADR-081`
**Related Stories:** `0051`, `0086`, `0101`, `0179`, `0180`, `0182`, `0183`

## Goal

Integrate complete provider-neutral CFD financial facts into the existing
deterministic Risk evaluation at T0 and T1. CFD notional, stop-loss monetary
risk, current position exposure, equity, required margin, and currency
conversion must use the validated financial contract and authoritative facts
without adding CFD or provider branches to the Risk Domain.

## Scope

- Assemble CFD-aware proposed-trade facts in Trading Core before Risk evaluation.
- Reuse `CfdFinancialCalculator` for CFD notional and stop-loss calculations.
- Resolve complete provider-neutral CFD contracts from the existing contract
  boundary and fail closed when the contract is missing or incomplete.
- Assemble CFD-aware current position market value and stop-loss risk for PAPER
  and LIVE facts using the correct mode authority.
- Preserve LIVE provider-authoritative account equity and current position facts
  from the Phase 6 V2 snapshot/reconciliation boundary.
- Preserve PAPER local authority and use read-only Market Data valuation facts;
  PAPER Risk facts must not call Broker Service.
- Use `RequiredMarginPort` for proposed-order margin only, with explicit
  currency normalization and freshness/provenance validation.
- Require directed FX conversion facts whenever CFD quote currency differs from
  the Risk reporting currency.
- Execute the same independent fact assembly for T0 and T1, with the existing
  T0/T1 freshness policies.
- Keep the Risk Domain provider- and product-agnostic.
- Add focused regression coverage for positive/negative quantities, multiplier,
  stop risk, cross-currency conversion, margin authority, PAPER/LIVE authority,
  incomplete facts, and T0/T1 parity.

## Out of Scope

- Changes to Risk Domain formulas, rule identifiers, or provider branching.
- Changes to `SettlementCompatibilityPolicy`; `CASH + CFD` and `INVENTORY + CFD`
  remain rejected.
- CFD settlement, realized PnL, cash mutation, equity mutation, or execution
  enablement.
- LIVE settlement or a second LIVE position ledger.
- Deriving required margin from notional, leverage, or current used margin.
- Broker orders, provider mutations, automatic retries, or execution changes.
- New database migrations or historical backfills.
- Provider-specific types, cTrader names, or payloads crossing into the
  provider-neutral financial boundary.

## Authority Invariants

```text
RISK_DOMAIN_PROVIDER_NEUTRAL = PRESERVED
PAPER_LOCAL_AUTHORITY = PRESERVED
LIVE_PROVIDER_AUTHORITY = PRESERVED
REQUIRED_MARGIN_FACT = AUTHORITATIVE_ONLY
CFD_MISSING_FACTS = CONTEXT_UNAVAILABLE
CASH_PLUS_CFD = STILL_BLOCKED
INVENTORY_PLUS_CFD = STILL_BLOCKED
```

## Acceptance Criteria

- [ ] A CFD proposed trade uses the validated contract multiplier and directed
      quote-to-reporting conversion for notional.
- [ ] A CFD proposed trade uses deterministic signed-quantity stop-loss risk and
      fails closed when the contract, stop, mark, or conversion fact is missing.
- [ ] A CFD current position uses the validated contract multiplier for market
      value and protected stop-loss risk; the generic `abs(quantity) * price`
      shortcut is not used for CFD positions.
- [ ] PAPER risk facts use only Trading Core state plus Market Data valuation
      facts and make zero Broker Service financial-fact calls.
- [ ] LIVE risk facts use provider-authoritative Phase 6 account/position facts;
      incomplete, stale, unreconciled, or missing contract facts fail closed.
- [ ] Proposed-order required margin comes only from `RequiredMarginPort` (or an
      explicitly equivalent mode-correct authoritative fact), is normalized with
      an explicit directed conversion when needed, and is never inferred from
      leverage, notional, or current used margin.
- [ ] T0 and T1 independently assemble fresh CFD facts under their existing
      decision-boundary policies; T1 does not silently reuse a T0 result.
- [ ] No CFD or provider-specific branch is added to the Risk Domain.
- [ ] Existing SPOT and `LINEAR_PERPETUAL` risk behavior remains unchanged.
- [ ] `CASH + CFD`, `INVENTORY + CFD`, and `UNKNOWN + CFD` remain rejected.
- [ ] Focused Trading Core tests, affected module tests, and `git diff --check`
      pass.

## Validation

- Focused `CfdFinancialCalculator`, `CfdValuationService`, Risk facts, T0, and
  T1 tests.
- PAPER no-call/no-mutation tests.
- LIVE provider-authority, reconciliation, freshness, contract, and margin
  separation tests.
- Existing SPOT and `LINEAR_PERPETUAL` risk regression tests.
- Full Trading Core and Risk Domain tests, plus `git diff --check`.
- Independent review focused on authority inversion, currency direction,
  multiplier use, margin semantics, fail-closed behavior, and T0/T1 parity.
