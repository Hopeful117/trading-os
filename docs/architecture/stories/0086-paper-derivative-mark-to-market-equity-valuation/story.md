# Story 0086 - PAPER Derivative Mark-to-Market and Equity Valuation

## Metadata

**ID:** `0086`

**Status:** Approved for implementation

**Related ADRs:** `ADR-028`, `ADR-042`, `ADR-051`, `ADR-052`

## Goal

Provide deterministic, provider-neutral mark-to-market equity for PAPER CASH
linear perpetual accounts without double-counting realized or unrealized P&L.

## Scope

- Persist an explicit realized PAPER CASH baseline and valuation freshness.
- Migrate existing Story 0085 PAPER CASH accounts from persisted equity.
- Recompute equity from realized balance plus all OPEN derivative unrealized P&L.
- Refresh valuation before PAPER CASH Risk monitoring.
- Recompute locally after settlement without an unnecessary market-data call.
- Preserve generic Risk, Challenge, BALANCE progression, LIVE, and INVENTORY
  responsibilities.
- Add focused accounting, valuation, freshness, migration, Risk, Challenge,
  and concurrency coverage.

## Out of Scope

Continuous scheduling, streaming valuation, funding, leverage, margin,
liquidation, partial close, hedge mode, inverse perpetuals, historical equity
curves, frontend changes, and provider-specific rules.

## Invariants

```text
realizedBalance = initial realized capital + realized P&L - fees
equity = realizedBalance + aggregate current OPEN-trade P&L
```

Freshness is explicit. Stale or unavailable valuation cannot produce an
approved PAPER CASH Risk evaluation.

## Validation

Run focused Trading Core tests, `mvn -q verify`, and `git diff --check`.
