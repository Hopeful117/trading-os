# Engineering Report - Story 0038

## Summary

Story 0038 closes the documentation and regression-coverage gap around the
persisted PAPER execution path. The test proves that the normal Trading Core
execution pipeline can complete a PAPER BUY and that its order, fill, account,
and trade state survives repository reload.

## Validation

| Check | Result |
|---|---|
| Focused execution tests | Passed |
| Trading Core complete suite | 517 tests passed |
| `./mvnw -B verify` | Passed |
| JaCoCo checks | Passed |
| `git diff --check` | Passed |

## Findings Resolved

- Broker-order fills were not persisted or rehydrated; a dedicated persistence
  model and migration now close that gap.
- Settlement-created trades were not reliably propagated through the Account
  JPA graph; cascade and orphan-removal configuration now preserves the
  existing aggregate persistence behavior.

## Evidence Qualification

- The persisted Spring-backed regression proves the successful PAPER BUY final
  state after repository reload.
- SELL bid pricing is proven by
  `PaperExecutionVerticalRegressionTest.paperExecutionRoutesToSimulationFillsSettlesAndFinalizesForBuyAndSell`,
  which uses in-memory fixtures rather than persisted Spring infrastructure.
- Exact-once settlement is also asserted by that isolated regression with
  `verify(settlement, times(1))`; it is not an assertion of the persisted test.
- T1-before-submission is proven by
  `ExecutionPipelineOrderRegressionTest.t1RunsBeforeBrokerSubmission` and its
  rejection/unavailable variants. The persisted test mocks the T1 service.
- No execution test was found that proves rollback or absence of partial
  persistence after a failing transaction.

## Remaining Review Items

- Human review must confirm the staged file set and migration naming/order.
- SELL bid pricing, settlement exact-once, and rollback behavior are not
  independently asserted by the new integration test.
- No human commit has been created.

`ENGINEERING_STATUS = READY_FOR_HUMAN_REVIEW`
