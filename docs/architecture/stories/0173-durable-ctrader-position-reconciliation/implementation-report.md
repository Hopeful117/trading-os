# Story 0173 - Implementation Report

## Status

`IMPLEMENTED_LOCALLY - AWAITING REVIEW`

The durable cTrader position reconciliation changes are implemented locally.
No cTrader order, position mutation, or broker write operation was performed.

## Implemented

- Added stable provider-neutral broker position identity to the cTrader and
  Trading Core snapshot contracts.
- Added cTrader position/deal correlation using `positionId` and
  `closePositionDetail`.
- Reconciled Trading Core open trades by broker position reference instead of
  deleting and recreating all open trades.
- Added durable close-deal references, closed quantity/notional, original
  quantity, realized PnL, exit price, and close timestamp fields.
- Added partial-close, full-close, idempotency, and no-resurrection handling.
- Added Flyway migration `V43__durable_broker_position_reconciliation.sql`.
- Added Flyway migration `V44__durable_broker_position_reconciliation_baseline.sql`
  to distinguish imported historical close evidence from applied close facts.
- Preserved the existing legacy synchronization path and Kraken compatibility.

## Validation

Passed:

- `mvn test` in `broker-service` (`308` tests, no failures or errors)
- `mvn -Dtest=PostgresFreshInstallMigrationTest test` in `trading-core`
  (`5` tests, no failures or errors)
- `mvn -q test` in `trading-core`
- `git diff --check`

The test suites emitted existing framework, database, and migration warnings.
No broker write path was invoked.

## Review State

Human code review, commit, push, pull request creation, and merge remain
pending. Pre-existing Dockerfile and runtime/network artifacts were preserved.

## Accepted Risk

Existing open trades created before V43 without a broker position reference are
not matched heuristically to provider positions. On upgrade, the first
authoritative synchronization may therefore create a second local trade for the
same provider position. This risk is accepted for this Story; future migration
work may provide an explicit operator-assisted reconciliation path.
