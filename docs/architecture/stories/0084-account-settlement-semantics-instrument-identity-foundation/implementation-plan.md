# Implementation Plan - Story 0084

## Status

`IMPLEMENTED - HUMAN APPROVAL NOT INFERRED`

## Implementation Boundary

Implement the provider-neutral settlement and product identity foundation only.
Do not implement derivative settlement or change Risk, Challenge, Broker Service,
frontend, or universal Position responsibilities.

## Planned Changes

1. Add `SettlementModel` and persist it on Trading Core Accounts.
2. Add `InstrumentProductType` to Market Data entities, DTOs, mappings, and
   synchronization.
3. Replace market uniqueness with provider, symbol, and product type.
4. Add the deterministic compatibility policy.
5. Enforce compatibility in the production PAPER settlement bean before balance
   or trade mutation.
6. Preserve the existing spot-only lookup while exposing an explicit
   product-aware Market Data service lookup.
7. Add additive migrations with conservative historical classification.
8. Add unit, integration, persistence, and migration tests.

## Invariants

- Existing PAPER accounts migrate to `INVENTORY`.
- Other historical accounts migrate to `UNKNOWN`.
- Existing markets migrate to `SPOT`.
- Missing or ambiguous product identity fails closed.
- CASH accounts remain representable but cannot use inventory settlement.
- No provider-specific or execution-mode-specific derivative semantics are
  introduced.

## Validation Plan

- `mvn -q verify` in `trading-core`.
- `mvn -q verify` in `market-data`.
- Focused settlement and migration tests.
- JaCoCo checks from both module builds.
- `git diff --check`.

## Deferred Work

- Cash-settled derivative entry and exit.
- Derivative P&L and mark-to-market.
- Funding, leverage, margin, liquidation, and generic Challenge compatibility.
