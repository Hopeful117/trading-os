# Implementation Plan — Story 0031

## Design

```
[Step 1] Enrich ExecutionDto with broker order details, fills, failure reason
[Step 2] Add recoverOne() to RecoverExecutionService
[Step 3] Add user-scoped POST /executions/{id}/reconcile endpoint
[Step 4] Update ExecutionController to pass enriched data to DTO
[Step 5] Update Angular ExecutionDto model
[Step 6] Add retry/reconcile methods to Angular ExecutionService
[Step 7] Add polling, status labels, state handling to PlanPage
[Step 8] Update execution result template with enriched display
[Step 9] Write backend tests
[Step 10] Write frontend tests
[Step 11] Full regression
```

## Steps

### Step 1 — Enrich ExecutionDto

**Objective:** Add broker order details, fill aggregation, and failure reason to the ExecutionDto.

**Current behavior:** ExecutionDto is a flat record with 11 fields. No broker order, fill, or failure information is exposed.

**Required change:**

1. **`ExecutionDto.java`:** Add 6 new fields:
   - `String brokerExternalOrderId`
   - `String brokerOrderStatus`
   - `BigDecimal filledQuantity`
   - `BigDecimal averageFillPrice`
   - `BigDecimal totalFees`
   - `String failureReason`

2. **Add new factory method:** `ExecutionDto.from(ExecutionIntent, Optional<BrokerOrder>, Optional<ExecutionAttempt>)`

3. **Fill aggregation:**
   - `filledQuantity = Σ fill.quantity`
   - `averageFillPrice = Σ(fill.price × fill.quantity) / Σ(fill.quantity)` (BigDecimal, HALF_UP, scale 12)
   - `totalFees = Σ fill.fee`

4. **Failure mapping:** Deterministic switch on `resultCode`:
   - `ACKNOWLEDGED` → null
   - `REJECTED` → "Order rejected by broker"
   - `TIMEOUT` → "Submission timed out"
   - `OUTCOME_UNKNOWN` → "Submission outcome uncertain"
   - other → "Execution failed"

5. **Keep old factory method:** `ExecutionDto.from(ExecutionIntent)` returns null for new fields (backward compatibility).

**Files:** `trading-core/.../execution/api/dto/ExecutionDto.java`

**Tests:** Update `ExecutionDtoTest` with 7 new test cases.

---

### Step 2 — Add recoverOne to RecoverExecutionService

**Objective:** Add a per-execution reconciliation method.

**Current behavior:** `RecoverExecutionService.recoverAll()` processes ALL recoverable executions globally. No per-execution method exists.

**Required change:**

1. **`RecoverExecutionService.java`:** Add `recoverOne(ExecutionIntentId id)`:
   - Load intent by ID
   - Verify status is recoverable (SUBMISSION_OUTCOME_UNKNOWN, RECONCILIATION_IN_PROGRESS, RECOVERY_BLOCKED)
   - Run existing recovery pipeline steps
   - Save and return intent

2. **`ExecutionConfiguration.java`:** Pass `ExecutionIntentRepositoryPort` to RecoverExecutionService constructor.

**Files:**
- `trading-core/.../execution/application/service/RecoverExecutionService.java`
- `trading-core/.../execution/infrastructure/configuration/ExecutionConfiguration.java`

**Tests:** Add `recoverOneReconcilesSingleExecution` and `recoverOneRejectsNonRecoverableState` to `ExecutionPipelineTest`.

---

### Step 3 — Add User-Scoped Reconcile Endpoint

**Objective:** Expose per-execution reconciliation through a safe, user-scoped endpoint.

**Current behavior:** `POST /executions/recovery` is global (no auth, processes all users). Not routed through Gateway.

**Required change:**

1. **`ExecutionController.java`:** Add `POST /executions/{id}/reconcile`:
   - Authentication required
   - Ownership verified via `requireOwned()`
   - Calls `recovery.recoverOne(intentId)`
   - Returns enriched `ExecutionDto`

**Files:** `trading-core/.../execution/api/ExecutionController.java`

**Tests:** Verify ownership enforcement, valid state transition, DTO returned.

---

### Step 4 — Update ExecutionController DTO Assembly

**Objective:** Pass broker order and attempt data to the enriched DTO factory method.

**Current behavior:** Controller calls `ExecutionDto.from(intent)` — flat projection only.

**Required change:**

1. **`ExecutionController.java`:** Add private `toEnrichedDto(intent)` method:
   - Query `BrokerOrderRepositoryPort.findByIntentId(intentId)`
   - Query `ExecutionAttemptRepositoryPort.findLatestByIntentId(intentId)`
   - Call `ExecutionDto.from(intent, brokerOrder, latestAttempt)`

2. **Inject repositories:** Add `BrokerOrderRepositoryPort` and `ExecutionAttemptRepositoryPort` to constructor.

3. **Update all endpoints:** Use `toEnrichedDto()` instead of `ExecutionDto.from(intent)`.

**Files:** `trading-core/.../execution/api/ExecutionController.java`

**Tests:** Existing tests pass. DTO projection tests verify enrichment.

---

### Step 5 — Update Angular ExecutionDto Model

**Objective:** Add enriched fields to the Angular ExecutionDto interface.

**Current behavior:** Angular `ExecutionDto` has 11 fields matching the old backend DTO.

**Required change:**

1. **`execution.model.ts`:** Add 6 new optional fields:
   - `brokerExternalOrderId: string | null`
   - `brokerOrderStatus: string | null`
   - `filledQuantity: number | null`
   - `averageFillPrice: number | null`
   - `totalFees: number | null`
   - `failureReason: string | null`

2. **Add helper functions:** `isTerminal(status)`, `shouldPoll(status)`

3. **Add status sets:** `TERMINAL_STATUSES`, `POLLABLE_STATUSES`

**Files:** `trading-os-web/.../core/models/execution.model.ts`

**Tests:** TypeScript compilation.

---

### Step 6 — Add Retry/Reconcile to ExecutionService

**Objective:** Add retry and reconcile API methods.

**Current behavior:** `ExecutionService` has `validate()`, `execute()`, `getExecution()`. No retry or reconcile.

**Required change:**

1. **`execution.service.ts`:** Add two methods:
   - `retry(executionId)` → POST `/executions/{id}/retry`
   - `reconcile(executionId)` → POST `/executions/{id}/reconcile`

**Files:** `trading-os-web/.../core/services/execution.service.ts`

**Tests:** Existing service tests pass.

---

### Step 7 — Add Polling and State Handling to PlanPage

**Objective:** Add short-lived polling for non-terminal execution states.

**Current behavior:** After `execute()` returns, the view is frozen. No polling.

**Required change:**

1. **`plan-page.ts`:**
   - Add `retrySubject` and `reconcileSubject`
   - Add `pollOrResult()` method: starts timer-based polling for non-terminal states
   - Add `retry()` and `reconcile()` methods
   - Add `statusLabel()` and `brokerOrderLabel()` for human-readable display
   - Add `OnDestroy` lifecycle to stop polling

2. **Polling policy:**
   - 2s interval for first 30s
   - 5s interval after 30s
   - Max 5 minutes total
   - Stops on terminal status, FAILED, or component destroy

**Files:** `trading-os-web/.../features/trade-planning/plan-page/plan-page.ts`

**Tests:** Add polling behavior tests.

---

### Step 8 — Update Execution Result Template

**Objective:** Display enriched execution data with proper state handling.

**Current behavior:** Simple status badge, ID, timestamps, back link.

**Required change:**

1. **`plan-page.html`:** Add new `executionPolling` state case and update `executionResult`:
   - Human-readable status labels
   - Broker order reference and status
   - Fill summary (quantity, average price, fees)
   - Failure reason
   - UNKNOWN explanation + "Check broker status" button
   - RECOVERY_BLOCKED explanation + "Check broker status" button
   - FAILED failure reason + "Retry" button

**Files:** `trading-os-web/.../features/trade-planning/plan-page/plan-page.html`

**Tests:** Update plan-page spec tests.

---

### Step 9 — Backend Tests

**Objective:** Verify DTO projection, reconciliation, and regression.

**New tests:**
- `ExecutionDtoTest`: 7 new test cases for enriched projection
- `ExecutionPipelineTest`: 2 new tests for recoverOne

**Regression:** All existing Trading Core tests pass (258 total).

---

### Step 10 — Frontend Tests

**Objective:** Verify polling, state handling, and enriched display.

**New tests:**
- Polling starts for non-terminal states
- Polling stops on terminal state
- Polling stops on component destroy
- FAILED shows failure reason and retry button
- UNKNOWN shows explanation and reconcile button
- UNKNOWN does not show retry button
- Fill summary rendered when present
- No fill section when absent

**Regression:** All existing Angular tests pass (242 total).

---

### Step 11 — Full Regression

**Modules:**
- Trading Core: 258 tests
- Broker Service: 105 tests
- Gateway: 20 tests
- Angular: 242 tests
- Angular production build

**Expected:** All pass. No regressions.
