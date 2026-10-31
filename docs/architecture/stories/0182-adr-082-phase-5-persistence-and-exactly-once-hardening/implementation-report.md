# Implementation Report - Story 0182

## Result

Implementation completed without changing the CFD accounting service or
compatibility policy. One production migration was required after the
PostgreSQL integration fixture exposed an existing schema defect: persisted
short CFD quantities were rejected by `trades_open_quantity_positive`.

## Changes

- Added `PaperCfdSettlementPersistenceHardeningIntegrationTest` with six
  PostgreSQL Testcontainers tests covering:
  - long and short CFD MTM after flush/clear/reload;
  - directed USD-to-EUR conversion after reload;
  - closed exposure exclusion from unrealized MTM;
  - replay after commit and reload using production provenance persistence;
  - concurrent replay in independent transactions and persistence contexts;
  - rollback atomicity across trade, account, and provenance.
- Added `V48__allow_signed_open_cfd_exposure.sql`.
- Updated fresh-install migration expectations from version 47 to 48.
- Exercised the durable `(broker_order_id, fill_id)` provenance uniqueness
  constraint directly in the PostgreSQL integration test.

## Acceptance Matrix

| Criterion | Result | Evidence |
|---|---|---|
| Long CFD reload MTM `+1 USD` | PASS | `reconstructsLongAndShortMtmAfterFlushClearAndReload` |
| Short CFD reload MTM `+1 USD` | PASS | Same test |
| Cross-currency reload conversion | PASS | `reconstructsCrossCurrencyMtmAfterReloadUsingDirectedConversion` |
| Closed exposure remains closed and excluded from MTM | PASS | `keepsClosedExposureClosedAndExcludedAfterReload` |
| Replay has one economic effect | PASS | `replaysCommittedExitOnceWithProductionProvenanceAfterReload` |
| Concurrent replay has one economic effect | PASS | `concurrentReplayProducesOneEconomicEffect` |
| Durable settlement identity uniqueness | PASS | Duplicate provenance insert rejected by PostgreSQL |
| Rollback leaves no partial state | PASS | `settlementRollbackLeavesNoPartialEconomicState` |
| Compatibility boundaries unchanged | PASS | No compatibility production code changed; existing regression tests passed |
| PAPER produces no broker mutation | PASS | Existing PAPER boundary and settlement regression coverage |
| Full Trading Core suite | PASS | `./mvnw -q test` passed with a longer execution window |
| `git diff --check` | PASS | Executed after implementation |

## Validation

- `./mvnw -q -Dtest=PaperCfdSettlementPersistenceHardeningIntegrationTest test`: PASS, 6 tests.
- `./mvnw -q -Dtest=PaperCfdSettlementPersistenceHardeningIntegrationTest,PostgresFreshInstallMigrationTest test`: PASS, 12 tests total.
- `./mvnw -q -DskipTests test-compile`: PASS.
- Relevant CFD, settlement, valuation, compatibility, persistence, and
  execution regression tests: PASS as executed during implementation.
- `./mvnw -q test`: PASS after rerunning with a longer execution window.

## Scope and Safety

- No LIVE CFD behavior was added.
- No CASH + CFD compatibility change was added.
- No Risk Domain or Phase 6 work was added.
- No broker/provider mutation path is used by the new tests.
- No commit, push, merge, or pull request action was performed.

## Compatibility Boundary Clarification

The PostgreSQL settlement fixture uses the existing PAPER cash ledger required
by `PaperCfdSettlementService` to exercise CFD persistence and realized-PnL
atomicity. This is an internal accounting test seam, not an authorization of
global `CASH + CFD` execution compatibility. The integration test explicitly
asserts that `SettlementCompatibilityPolicy` continues to reject `CASH + CFD`.

The migration test explicitly asserts the V48 constraint definition and the
removal of the superseded positive-quantity constraint.

## Remaining Validation

Human review and commit remain required.
