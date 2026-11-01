# Story 0190 - PAPER CFD T1 Authoritative Margin Revalidation

## Metadata

**ID:** `0190`
**Title:** PAPER CFD T1 Authoritative Margin Revalidation
**Status:** Implemented Locally - Awaiting Human Review
**Related ADRs:** `ADR-082`, `ADR-083`
**Predecessor Story:** `0189`

## Goal

Ensure PAPER CFD required margin at T1 is calculated from independently acquired
execution-time valuation facts, especially the fresh T1 price, without reusing T0
freshness semantics or a stale TradePlan/request price.

## Scope

- Make the valuation snapshot and freshness requirement explicit on
  `RequiredMarginPort.Request`.
- Pass T0 and T1 valuation context from their existing orchestration boundaries.
- Require valuation context for PAPER margin resolution and remove the PAPER
  margin adapter's duplicate Market Data acquisition.
- Calculate PAPER margin from the authoritative valuation fact `sourcePrice`.
- Make PAPER T1 request price come from the fresh T1 valuation fact.
- Add behavioral and fail-closed T1 tests.

## Explicit Non-Changes

- `CASH + CFD` remains rejected.
- ADR-083 policy semantics and V49 persistence are unchanged.
- LIVE provider-authoritative margin routing is unchanged.
- Risk Domain, settlement, compatibility policy, and database migrations are
  unchanged.
- No broker orders, closes, provider mutations, or cTrader safety changes.

## Acceptance Criteria

- PAPER T0 carries a T0 valuation snapshot and `FreshnessRequirement.t0()`.
- PAPER T1 carries a fresh valuation snapshot and `FreshnessRequirement.t1()`.
- PAPER margin uses the valuation fact price rather than a carried request price.
- T0 and T1 price changes produce different required-margin amounts.
- Missing or stale T1 price fails closed.
- Mismatched valuation freshness metadata fails closed.
- Human authorization and execution-time T1 gates remain mandatory.
