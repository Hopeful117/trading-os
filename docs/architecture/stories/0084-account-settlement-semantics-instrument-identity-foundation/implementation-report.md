# Story 0084 - Implementation Report

## Result

Implemented the provider-neutral settlement and product identity foundation.
Derivative settlement and mark-to-market remain deferred.

## Repository State

- Starting branch: `main`
- Starting HEAD: `ab915ab81dd57fb4968f511fb5f5f7c3625812ef`
- Starting worktree: clean
- No commit, push, merge, reset, or checkout was performed.
- DevLog context was requested but unavailable because the provider quota was
  exhausted; repository and accepted ADR evidence were used.

## Implemented

- Added `SettlementModel` with `INVENTORY`, `CASH`, and `UNKNOWN`.
- Added Trading Core `InstrumentProductType` and compatibility policy.
- Added persisted `Account.settlementModel`.
- Added `settlementModel` to the account DTO and PAPER account creation request.
- Added a fail-closed guard preventing CASH or UNKNOWN accounts from using the
  current inventory settlement implementation.
- Enforced Account/Product compatibility in the production PAPER settlement bean
  using the Market Data product identity, rejecting missing or ambiguous product
  identity before financial mutation.
- Added Market Data product identity with `SPOT` and `LINEAR_PERPETUAL`.
- Changed Market Data uniqueness to provider, symbol, and product type.
- Added an explicit product-aware Market Data service lookup.
- Added additive migrations for account settlement model and market product type.
- Added focused compatibility and CASH settlement guard tests.
- Added production-path regression coverage for unsupported perpetual settlement
  and persistence coverage for duplicate symbols across product types.
- Added migration tests executing both scripts against legacy-style H2 fixtures,
  covering PAPER/LIVE account classification and product-scoped market identity.
- Preserved existing `Trade` and PAPER inventory settlement behavior.

## Migration Semantics

- Historical PAPER accounts linked to PAPER BrokerAccounts become `INVENTORY`.
- Other historical accounts become `UNKNOWN`.
- The account column defaults to `INVENTORY` for legacy SQL writers that omit the
  new field; the migration classification runs immediately afterward.
- Existing markets become `SPOT`.
- UNKNOWN is intentionally fail-closed for future derivative settlement.

## Validation

Executed `mvn -q verify` successfully in both Trading Core and Market Data.
Focused settlement compatibility and CASH settlement guard tests also pass.
`git diff --check` passes.
JaCoCo verification passes in both modules.

## Documentation Reconciliation

Added the missing Story workflow artifacts: Repository Analysis,
Implementation Plan, Code Review, and Engineering Report. No unrelated
documentation was changed.

## Vault Outcome

- Vault consulted: no.
- Outcome: no vault action.
- Rationale: repository-local Story and ADR evidence was sufficient; no curated
  transverse knowledge was needed.
- No `vault-outcome.json` or proposal artifact was generated because no vault
  candidate or enrichment action was identified.

## Deferred

- Story 0085: cash-settled derivative settlement.
- Story 0086: mark-to-market and equity valuation.
- Story 0087: Challenge compatibility and generic Prop configuration.
