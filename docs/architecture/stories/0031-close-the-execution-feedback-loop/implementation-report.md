# Story 0031 — Implementation Report

## Baseline

```
ROOT = /home/ludo/Bureau/workspace/trading-os
BRANCH = main
HEAD_BEFORE = 14fd7721133c950313972d4c47be1125c470f98f
WORKTREE_BEFORE = CLEAN
```

## Story

```
STORY = 0031-close-the-execution-feedback-loop
STATUS = Completed
```

## Trading Core Changes

```
DTO_PROJECTION = ExecutionDto enriched with 6 new fields:
  brokerExternalOrderId, brokerOrderStatus, filledQuantity,
  averageFillPrice, totalFees, failureReason.

FILL_AGGREGATION = Computed deterministically on backend:
  filledQuantity = Σ fill.quantity
  averageFillPrice = Σ(fill.price × fill.quantity) / Σ(fill.quantity)
  totalFees = Σ fill.fee
  Uses BigDecimal with RoundingMode.HALF_UP, scale 12.

FAILURE_MAPPING = Deterministic switch on resultCode:
  ACKNOWLEDGED → null (success)
  REJECTED → "Order rejected by broker"
  TIMEOUT → "Submission timed out"
  OUTCOME_UNKNOWN → "Submission outcome uncertain"
  other → "Execution failed"

RECONCILIATION = New user-scoped endpoint:
  POST /executions/{id}/reconcile
  - Authentication required
  - Ownership verified via requireOwned()
  - Only SUBMISSION_OUTCOME_UNKNOWN, RECONCILIATION_IN_PROGRESS,
    RECOVERY_BLOCKED states eligible
  - Reuses existing RecoverExecutionService pipeline

OWNERSHIP = All endpoints (get, execute, retry, cancel, reconcile)
  verify ownership via requireOwned() before action.
```

## Gateway

```
CHANGED = NO (existing wildcard /api/v1/executions/** covers /reconcile)
USER_SCOPED_RECONCILE_REACHABLE = YES
GLOBAL_RECOVERY_EXPOSED = NO
```

## Angular

```
ENRICHED_DISPLAY = Execution result shows:
  - Human-readable status labels (not raw enums)
  - Broker order reference and status
  - Fill summary (quantity, average price, fees) when available
  - Failure reason when applicable
  - UNKNOWN/RECOVERY_BLOCKED explanations

POLLING = Short-lived polling for non-terminal states:
  - Starts when status is pollable after execute()
  - 2s interval for first 30s, then 5s
  - Max 5 minutes total
  - Stops on terminal status, FAILED, or component destroy

FAILED_HANDLING = Shows failureReason text + Retry button

UNKNOWN_HANDLING = Shows safety explanation + "Check broker status" button.
  No Retry button (prevents blind resubmission).

RECONCILIATION = "Check broker status" button calls POST /executions/{id}/reconcile
  and resumes polling with returned state.

RETRY = "Retry" button calls POST /executions/{id}/retry
  and resumes polling with returned state.
```

## Semantic Verification

```
COMPLETED_EQUALS_FULLY_FILLED = NO
  COMPLETED means "Broker accepted the execution request".
  BrokerOrder.FILLED means order fully filled. These are separate.

UNKNOWN_EQUALS_FAILED = NO
  UNKNOWN means "submission outcome cannot be established".
  FAILED means "broker explicitly rejected". Different semantics.

UNKNOWN_BLIND_RETRY_ALLOWED = NO
  UNKNOWN shows "Check broker status" (reconcile), not Retry.

FRONTEND_AUTHORITATIVE = NO
  Frontend polls backend GET /executions/{id} for state.
  Backend remains authoritative for all execution state.
```

## Tests

```
MODULE              | COMMAND                                          | PASSED | FAILED
Trading Core        | mvn test                                         | 258    | 0
Broker Service      | mvn test                                         | 105    | 0
Gateway             | mvn test                                         | 20     | 0
Angular             | npm run test:ci                                  | 242    | 0
Angular Build       | npx ng build                                     | OK     | 0
```

## Regression Review

```
risk_approval_still_required = YES (evaluateRisk step unchanged)
human_click_still_required = YES (execute button unchanged)
execution_idempotency_unchanged = YES (idempotency key flow unchanged)
broker_ownership_enforcement_intact = YES (requireOwned on all endpoints)
unknown_cannot_blind_retry = YES (UNKNOWN shows reconcile, not retry)
failed_retry_uses_backend_rules = YES (RetryExecutionService unchanged)
kraken_details_do_not_leak = YES (only externalOrderId/status exposed)
global_recovery_not_exposed = YES (POST /executions/recovery not routed)
```

## Files Changed

```
trading-core/src/main/java/.../execution/api/ExecutionController.java
trading-core/src/main/java/.../execution/api/dto/ExecutionDto.java
trading-core/src/main/java/.../execution/application/service/RecoverExecutionService.java
trading-core/src/main/java/.../execution/infrastructure/configuration/ExecutionConfiguration.java
trading-core/src/test/java/.../execution/ExecutionPipelineTest.java
trading-core/src/test/java/.../execution/api/dto/ExecutionDtoTest.java
trading-os-web/src/app/core/models/execution.model.ts
trading-os-web/src/app/core/services/execution.service.ts
trading-os-web/src/app/features/trade-planning/plan-page/plan-page.html
trading-os-web/src/app/features/trade-planning/plan-page/plan-page.ts
docs/architecture/stories/0031-close-the-execution-feedback-loop/story.md
```

## Remaining Gaps

- Execution history page (intentionally out of scope)
- Cancel execution UI (intentionally out of scope)
- WebSocket execution events (intentionally out of scope)
- Scheduled automatic recovery (intentionally out of scope)
- ExecutionDto could include instrument/side/quantity from parameters (future UX improvement)

## Git

```
HEAD_AFTER = (not committed)
STAGED = NO
COMMITS_CREATED = 0
PUSH_PERFORMED = NO
```

## Implementation Result

```
STORY_0031_COMPLETE
```
