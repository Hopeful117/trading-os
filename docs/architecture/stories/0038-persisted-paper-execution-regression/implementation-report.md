# Implementation Report - Story 0038

## Status

Implemented and ready for human review. No commit, push, or merge was made.

## Changes

- Added `PaperExecutionPersistenceIntegrationTest` using Spring Boot, H2,
  Flyway, real Trading Core repositories, and mocked external boundaries.
- Added `BrokerFillEntity` and `JpaBrokerFillRepository`.
- Added Flyway migration `V11__persist_execution_broker_fills.sql`.
- Updated broker-order persistence and mapping to save and reload fills.
- Enabled cascading persistence and orphan removal for Account trades.

## Verified Behavior

The regression executes a PAPER BUY and reloads the final state from the
persistence layer. It verifies:

- `ExecutionStatus.COMPLETED`;
- PAPER mode and expected owner;
- no live `BrokerExecutionClient` submission;
- a FILLED broker order with one fill at ask price `50100`;
- one persisted broker fill;
- USD/BTC balances, equity, and peak equity;
- one open BTC/USD Trade associated with the expected Account.

## Deliberate Coverage Boundary

The new integration test covers the persisted BUY path. SELL bid pricing and
exact-once settlement invocation are covered by the existing isolated
`PaperExecutionVerticalRegressionTest`, but are not independently re-proven by
this persisted regression. No execution test was found that proves rollback or
absence of partial persistence after a failing transaction.

The integration test also replaces the execution-time risk revalidation service
with a mock so that this Story isolates persistence and external boundaries.
The ordering and blocking behavior of the T1 gate remain covered by
`ExecutionPipelineOrderRegressionTest`.

## Evidence Matrix

| Claim | Evidence | Level |
|---|---|---|
| PAPER mode and ownership survive reload | `PaperExecutionPersistenceIntegrationTest.executesPaperOrderThroughSpringBeansAndPersistsFinalState` | Proven by this Story |
| BUY uses ask price and fill price survives reload | Same integration test | Proven by this Story |
| SELL uses bid price | `PaperExecutionVerticalRegressionTest.paperExecutionRoutesToSimulationFillsSettlesAndFinalizesForBuyAndSell` | Existing isolated coverage only |
| Settlement is invoked exactly once | Same vertical regression, `verify(settlement, times(1))` | Existing isolated coverage only |
| Risk gate precedes submission | `ExecutionPipelineOrderRegressionTest.t1RunsBeforeBrokerSubmission` and rejection/unavailable variants | Existing isolated coverage only |
| Rollback/no partial persistence after failure | No matching execution regression found | Not independently proven |

## Files Owned by This Story

- `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/adapter/JpaBrokerOrderAdapter.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/mapper/BrokerOrderMapper.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/persistence/BrokerFillEntity.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/persistence/JpaBrokerFillRepository.java`
- `trading-core/src/main/resources/db/migration/common/V11__persist_execution_broker_fills.sql`
- `trading-core/src/main/java/com/hope/trading/trading_core/model/Account.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/execution/PaperExecutionPersistenceIntegrationTest.java`

Unrelated pre-existing worktree changes remain outside this Story.
