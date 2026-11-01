# Implementation Plan - Story 0190

## Objective

Correct the PAPER T1 required-margin authority flow with the smallest change that
reuses the existing valuation and freshness abstractions.

## Implementation

1. Extend `RequiredMarginPort.Request` with optional resolved valuation and
   freshness context while preserving existing constructors.
2. Pass the existing T0 valuation snapshot with `FreshnessRequirement.t0()`.
3. Pass the existing T1 valuation snapshot with `FreshnessRequirement.t1()`.
4. For PAPER T1, use the valuation Fact `sourcePrice` rather than the TradePlan
   entry price.
5. Require valuation context for PAPER margin resolution and reject mismatched
   freshness metadata, missing price, or stale facts.
6. Remove duplicate Market Data acquisition from `PaperRequiredMarginClient`.
7. Preserve local policy, exposure, contract, FX, LIVE, Risk Domain, settlement,
   human authorization, and compatibility behavior.

## Validation

- Add direct local PAPER T0/T1 price-change and fail-closed tests.
- Capture the application T1 request and verify fresh price and `t1()` context.
- Run focused Trading Core tests, full Trading Core tests, verify, Risk Domain
  verification, migration checks, and `git diff --check`.

## Explicit Non-Changes

- No `SettlementCompatibilityPolicy` change.
- No V49 change or new migration.
- No Risk Domain or settlement change.
- No provider preview, leverage inference, used-margin substitution, broker
  mutation, or cTrader safety change.
