# Implementation Plan — Story 0033

## Design

```
[Phase A]  Position Identity Fix (capability-path brokerPositionReference)
[Phase B]  Broker Service PositionManagementCapability
[Phase C]  Database Migration + PositionCloseCommand Entity
[Phase D]  Trading Core Position Close Service + Feign Client
[Phase E]  Trading Core REST API (close + reconcile endpoints)
[Phase F]  Broker Service Internal API (resolve-target, execute-close, reconcile-close)
[Phase G]  Kraken Position Management Adapter
[Phase H]  Angular Full Exposure Close UX
[Phase I]  Concurrency + Crash-Window Tests
[Phase J]  Regression + Quality Pipeline
[Phase K]  Story Artifacts (implementation-report.md, engineering-report.md, code-review.md)
```

## Critical Sequence (ADR-040)

Every close command MUST follow this exact sequence:

```
resolve (NO financial mutation)
  → atomically reserve (scope locked, partial unique index)
    → final provider revalidation (fresh state, same scope — no reinterpretation)
      → financial mutation (provider-specific close)
        → persist outcome (ACKNOWLEDGED | REJECTED | UNKNOWN)
          → evidence-based reconciliation/convergence (explicit user action only)
```

**Invariants enforced at every phase:**

| Invariant | Enforcement Point |
|---|---|
| No financial mutation before successful reservation | Phase D: scope reservation (CREATED) before Phase F: execute call |
| No blind retry | UNKNOWN → reconciliation only; no automatic retry |
| No automatic replacement/cancellation | Partial unique index rejects duplicate active commands |
| ACKNOWLEDGED ≠ CLOSED | ACKNOWLEDGED is ACTIVE; CLOSED requires explicit reconciliation |
| UNKNOWN remains financially uncertain | Reconciliation required; no inference from timeout |
| Reconciliation never submits another close | Reconciliation queries provider state only |
| Frontend quantity/side non-authoritative | Server derives from authoritative reload |
| Kraken txid is read identity only | Resolved to pair/exposure scope server-side |
| resolvedMutationScope opaque outside Broker | Angular treats as opaque correlation key |
| Trading Core remains Kraken-neutral | No reduce_only, FIFO, cl_ord_id in Trading Core |
| No persistent Position aggregate | Positions remain broker-authoritative |
| No out-of-scope expansion | No partial close, SL/TP, cTrader, FTMO, autonomous exits |

---

## Phase A — Position Identity Fix (Foundation)

### Objective

Fix capability-based `PositionSnapshot` to preserve the Kraken txid as `brokerPositionReference`.

### Existing Components to Reuse

- `BrokerModels.PositionSnapshot` — add field
- `KrakenCapabilities.positions()` — pass txid (currently drops it at line 30)
- `BrokerDashboardMapper.toPositionFact()` — already passes `brokerPositionId`, no change

### Files to Modify

| File | Change |
|---|---|
| `broker-service/.../domain/model/BrokerModels.java` | Add `String brokerPositionReference` to `PositionSnapshot` |
| `broker-service/.../infrastructure/provider/kraken/capability/KrakenCapabilities.java` | Pass txid as `brokerPositionReference` in `positions()` |

### Files to Create

| File | Purpose |
|---|---|
| `broker-service/.../KrakenCapabilitiesPositionReferenceTest.java` | Verify txid propagation |

### Contracts

No API contract changes. Internal record extension only.

### Invariants

- Legacy path (KrakenMapper → Angular) must continue working unchanged
- `PositionSnapshot.brokerPositionReference` = Kraken OpenPositions map key (txid string)
- `BrokerPositionFact.positionId` continues to be set from `PositionSnapshot.brokerPositionId`

### Tests Required Before Proceeding

1. Unit test: `KrakenCapabilities.positions()` returns `PositionSnapshot` with `brokerPositionReference` populated
2. Regression: existing position read path (legacy) unchanged
3. Contract: `PositionSnapshot` serialization includes new field

### Dependencies

None. Foundation for all subsequent phases.

---

## Phase B — Broker Service PositionManagementCapability

### Objective

Add `PositionManagementCapability` interface and Kraken adapter with `resolveTarget`, `executeClose`, and `reconcile` operations.

### Existing Components to Reuse

- `BrokerCapabilities` interface pattern (static inner interfaces)
- `BrokerModels` record/sealed-interface pattern
- `KrakenBrokerProvider.capability()` routing
- `BrokerOperationServices` inner-service pattern

### Files to Modify

| File | Change |
|---|---|
| `broker-service/.../domain/capability/BrokerCapabilities.java` | Add `PositionManagementCapability` interface |
| `broker-service/.../domain/model/BrokerModels.java` | Add `ResolveTargetRequest`, `ResolvedPositionCloseTarget`, `ExecuteCloseRequest`, `CloseResult`, `ReconcileCloseRequest`, `ReconciliationCloseResult` |
| `broker-service/.../infrastructure/provider/kraken/KrakenBrokerProvider.java` | Register `PositionManagementCapability` |
| `broker-service/.../application/service/BrokerOperationServices.java` | Add `ResolveTargetService`, `ExecuteCloseService`, `ReconcileCloseService` |

### Files to Create

| File | Purpose |
|---|---|
| `broker-service/.../infrastructure/provider/kraken/capability/KrakenPositionManagementCapability.java` | Kraken adapter (Phase G detail) |
| `broker-service/.../api/controller/PositionManagementController.java` | Internal REST endpoints |
| `broker-service/.../api/dto/PositionCloseApiDtos.java` | Request/response DTOs |

### Contracts

#### PositionManagementCapability Interface

```java
public interface PositionManagementCapability {
    ResolvedPositionCloseTarget resolveTarget(ResolveTargetRequest request);
    CloseResult executeClose(ExecuteCloseRequest request);
    ReconciliationCloseResult reconcile(ReconcileCloseRequest request);
}
```

#### Domain Models

```java
public record ResolveTargetRequest(UUID brokerAccountId, String brokerPositionReference) {}
public record ResolvedPositionCloseTarget(UUID brokerAccountId, String resolvedMutationScope) {}
public record ExecuteCloseRequest(UUID brokerAccountId, String resolvedMutationScope, String idempotencyKey) {}
public sealed interface CloseResult permits Acknowledged, Rejected, Unknown {}
public record Acknowledged(String externalOrderId, String correlationId) implements CloseResult {}
public record Rejected(String externalOrderId, String reasonCode) implements CloseResult {}
public record Unknown(String reasonCode) implements CloseResult {}
public record ReconcileCloseRequest(UUID brokerAccountId, String resolvedMutationScope, String idempotencyKey) {}
public sealed interface ReconciliationCloseResult permits ExposureConfirmedAbsent, CommandConfirmedNotExecuted, Inconclusive {}
public record ExposureConfirmedAbsent() implements ReconciliationCloseResult {}
public record CommandConfirmedNotExecuted() implements ReconciliationCloseResult {}
public record Inconclusive() implements ReconciliationCloseResult {}
```

#### Internal REST Endpoints

```
POST /internal/v1/positions/resolve-target
POST /internal/v1/positions/execute-close
POST /internal/v1/positions/reconcile-close
```

### Invariants

- `resolveTarget` performs zero financial mutation
- `resolveTarget` validates `brokerPositionReference` against fresh broker state
- `executeClose` requires reserved scope (does not re-derive from brokerPositionReference)
- `executeClose` performs final provider revalidation immediately before mutation
- Final revalidation must not reinterpret scope into different exposure scope
- `reconcile` queries provider state only — never places orders
- Trading Core must not see `reduce_only`, `cl_ord_id`, `FIFO`, Kraken-specific types

### Tests Required Before Proceeding

1. `resolveTarget` returns `ResolvedPositionCloseTarget` with valid `resolvedMutationScope`
2. `resolveTarget` with missing position → appropriate error (no mutation)
3. `executeClose` with acknowledged result → `CloseResult.Acknowledged`
4. `executeClose` with rejected result → `CloseResult.Rejected`
5. `executeClose` with unknown/timeout → `CloseResult.Unknown`
6. `reconcile` returns `ExposureConfirmedAbsent` when positions empty for scope
7. `reconcile` returns `CommandConfirmedNotExecuted` when exposure still exists
8. No Kraken-specific types leak into Trading Core boundary

### Dependencies

Phase A (PositionSnapshot must have `brokerPositionReference`).

---

## Phase C — Database Migration + PositionCloseCommand Entity

### Objective

Create `position_close_command` table and domain model with 7-state lifecycle.

### Existing Components to Reuse

- `ExecutionIntentEntity` JPA pattern (UUID PK, `@Version`, public fields)
- Flyway V1-V7 migration conventions
- PostgreSQL partial index pattern from V3

### Files to Create

| File | Purpose |
|---|---|
| `trading-core/src/main/resources/db/migration/common/V8__position_close_command.sql` | Table definition |
| `trading-core/src/main/resources/db/migration/postgresql/V8__position_close_command_partial_index.sql` | PostgreSQL partial unique index |
| `trading-core/.../positionclose/domain/model/PositionCloseCommand.java` | Domain model |
| `trading-core/.../positionclose/domain/model/PositionCloseStatus.java` | 7-state enum |
| `trading-core/.../positionclose/domain/model/ReconciliationCloseResult.java` | Reconciliation result enum |
| `trading-core/.../positionclose/domain/service/PositionCloseLifecycleService.java` | State machine |
| `trading-core/.../positionclose/domain/repository/PositionCloseCommandRepositoryPort.java` | Repository port |
| `trading-core/.../positionclose/infrastructure/persistence/PositionCloseCommandEntity.java` | JPA entity |
| `trading-core/.../positionclose/infrastructure/persistence/JpaPositionCloseCommandRepository.java` | Spring Data repository |

### Migration Schema

```sql
-- common/V8__position_close_command.sql
CREATE TABLE position_close_command (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id                UUID NOT NULL,
    broker_account_id         UUID NOT NULL,
    broker_position_reference VARCHAR(255) NOT NULL,
    resolved_mutation_scope   VARCHAR(255) NOT NULL,
    idempotency_key           VARCHAR(160) NOT NULL,
    status                    VARCHAR(32) NOT NULL,
    reconciliation_result     VARCHAR(48),
    external_order_id         VARCHAR(255),
    failure_reason            VARCHAR(500),
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    version                   BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE position_close_command
    ADD CONSTRAINT uk_position_close_idempotency UNIQUE (idempotency_key);

CREATE INDEX idx_position_close_account ON position_close_command (account_id);
CREATE INDEX idx_position_close_broker_account ON position_close_command (broker_account_id);
```

```sql
-- postgresql/V8__position_close_command_partial_index.sql
CREATE UNIQUE INDEX uq_active_command_per_scope
    ON position_close_command (broker_account_id, resolved_mutation_scope)
    WHERE status IN ('CREATED', 'SUBMITTED', 'ACKNOWLEDGED', 'UNKNOWN');
```

### PositionCloseStatus Enum

```java
public enum PositionCloseStatus {
    CREATED,         // ACTIVE — scope reserved, not yet submitted
    SUBMITTED,       // ACTIVE — sent to Broker Service
    ACKNOWLEDGED,    // ACTIVE — broker accepted (NOT exposure confirmed closed)
    REJECTED,        // TERMINAL — broker rejected
    UNKNOWN,         // ACTIVE — outcome uncertain
    CLOSED,          // TERMINAL — exposure confirmed absent by reconciliation
    NOT_SUBMITTED    // TERMINAL — exposure absent at resolution, no mutation
}
```

### Lifecycle State Machine

```
CREATED → SUBMITTED → ACKNOWLEDGED | REJECTED | UNKNOWN
                                     ↓              ↓
                                  CLOSED ←── reconciliation
                                     ↑
NOT_SUBMITTED (terminal: exposure absent at resolution)
```

### Invariants

- ACTIVE states: CREATED, SUBMITTED, ACKNOWLEDGED, UNKNOWN (included in partial unique index)
- TERMINAL states: REJECTED, CLOSED, NOT_SUBMITTED (excluded from partial unique index)
- ACKNOWLEDGED is ACTIVE — does NOT mean exposure confirmed closed
- CLOSED only via explicit reconciliation with `EXPOSURE_CONFIRMED_ABSENT`
- `reconciliation_result` is a separate dimension, not a lifecycle state
- Optimistic locking via `@Version`

### Tests Required Before Proceeding

1. Migration runs cleanly on H2 and PostgreSQL
2. `PositionCloseCommandEntity` persists and retrieves correctly
3. Unique constraint on `idempotency_key` enforced
4. Partial unique index enforced (PostgreSQL) / full unique index enforced (H2)
5. `PositionCloseLifecycleService` validates all state transitions
6. Invalid transitions rejected

### Dependencies

None. Can run in parallel with Phases A-B.

---

## Phase D — Trading Core Position Close Service + Feign Client

### Objective

Application service orchestrating close flow: scope reservation → broker execution → outcome persistence.

### Existing Components to Reuse

- `ValidateAndCreateService` validation-chain pattern
- `ExecutionIntentEntity` idempotency-check pattern
- `BrokerApiClient` Feign client pattern
- `RecoverExecutionService` pipeline concept

### Files to Create

| File | Purpose |
|---|---|
| `trading-core/.../positionclose/application/service/PositionCloseService.java` | Main orchestration service |
| `trading-core/.../positionclose/application/command/PositionCloseCommand.java` | Command object |
| `trading-core/.../positionclose/application/port/BrokerPositionClosePort.java` | Port interface |
| `trading-core/.../positionclose/infrastructure/adapter/BrokerPositionCloseAdapter.java` | Adapter implementing port |
| `trading-core/.../positionclose/infrastructure/adapter/BrokerPositionCloseClient.java` | Feign client |

### Feign Client Contract

```java
@FeignClient(name="broker-service", contextId="position-close-client",
             url="${broker-service.base-url:}")
public interface BrokerPositionCloseClient {
    @PostMapping("/internal/v1/positions/resolve-target")
    ResolvedTargetResponse resolveTarget(@RequestBody ResolveTargetRequest request);

    @PostMapping("/internal/v1/positions/execute-close")
    BrokerCloseResponse executeClose(@RequestBody ExecuteCloseRequest request);

    @PostMapping("/internal/v1/positions/reconcile-close")
    BrokerReconcileResponse reconcileClose(@RequestBody ReconcileCloseRequest request);
}
```

### Orchestration Flow

```
PositionCloseService.close(accountId, brokerPositionReference, idempotencyKey):
  1. authenticate + ownership validation
  2. idempotency check (findByIdempotencyKey → return existing if found)
  3. call Broker Service resolveTarget → ResolvedPositionCloseTarget
  4. atomically persist PositionCloseCommandEntity (CREATED) with resolvedMutationScope
     → partial unique index may reject → 409 Conflict
  5. call Broker Service executeClose(resolvedMutationScope, idempotencyKey)
     → ACKNOWLEDGED | REJECTED | UNKNOWN
  6. update command status with broker result
  7. return ClosePositionResponse
```

### Invariants

- Step 3 (resolveTarget) performs zero financial mutation
- Step 4 (reservation) must succeed before step 5 (execution)
- If step 4 fails (unique index conflict) → return 409, zero broker mutation
- If step 5 fails → command remains SUBMITTED or transitions to UNKNOWN
- `idempotencyKey` checked in step 2 prevents duplicate commands
- Different `idempotencyKey` for same scope → blocked by partial unique index (step 4)

### Tests Required Before Proceeding

1. Happy path: resolve → reserve → execute → ACKNOWLEDGED
2. Position absent at resolution → NOT_SUBMITTED (no broker call)
3. Scope reservation conflict → 409, execute call count = 0
4. Idempotency: same key → returns existing result
5. Idempotency bypass prevention: different key + same scope → blocked
6. Broker rejects → REJECTED status persisted
7. Broker timeout → UNKNOWN status persisted
8. Ownership validation: non-owner rejected
9. Concurrent requests: at most one reservation succeeds

### Dependencies

Phase B (PositionManagementCapability), Phase C (entity + migration).

---

## Phase E — Trading Core REST API

### Objective

Expose close and reconcile endpoints for Angular.

### Existing Components to Reuse

- `PositionController` at `/api/v1/accounts/{accountId}/positions`
- `ExecutionController` REST pattern (ResponseEntity, @AuthenticationPrincipal)
- `@RequestHeader("Idempotency-Key")` pattern from `ExecutionController`

### Files to Create

| File | Purpose |
|---|---|
| `trading-core/.../positionclose/api/PositionCloseController.java` | REST controller |
| `trading-core/.../positionclose/api/dto/PositionCloseRequest.java` | Request DTO |
| `trading-core/.../positionclose/api/dto/PositionCloseResponse.java` | Response DTO |

### External API Contracts

#### POST /api/v1/accounts/{accountId}/positions/close

```
Headers: Authorization: Bearer <JWT>, Idempotency-Key: <uuid>
Body: { "brokerPositionReference": "string" }
Response: 202 Accepted
Response body: ClosePositionResponse
Errors: 401, 403, 404 (position not found), 409 (conflict), 503 (broker unavailable)
```

#### POST /api/v1/accounts/{accountId}/positions/close/{commandId}/reconcile

```
Headers: Authorization: Bearer <JWT>
Eligible statuses: ACKNOWLEDGED, UNKNOWN
Response: 200 OK
Response body: ClosePositionResponse (updated)
Errors: 401, 403, 404 (command not found), 409 (command not reconcilable)
```

#### ClosePositionResponse

```java
public record ClosePositionResponse(
    String commandId,
    String status,
    String externalOrderId,
    String failureReason,
    String resolvedMutationScope,
    String reconciliationResult
) {}
```

### Invariants

- Authentication and ownership validated before processing
- `brokerPositionReference` is NOT in request body — it is the body field
- `Idempotency-Key` is a header only, not in body
- `resolvedMutationScope` returned as opaque string
- `reconciliationResult` returned as separate field

### Tests Required Before Proceeding

1. Auth required (401 without token)
2. Ownership enforced (403 for wrong account)
3. Valid close request → 202 Accepted
4. Reconcile with ACKNOWLEDGED command → 200 OK
5. Reconcile with non-eligible status → 409
6. Missing command → 404
7. Response includes all fields

### Dependencies

Phase D (PositionCloseService).

---

## Phase F — Broker Service Internal API

### Objective

Expose internal REST endpoints for Trading Core Feign calls.

### Existing Components to Reuse

- `ExecutionController` internal REST pattern
- `BrokerPrincipal` authentication
- `BrokerApiDtos` request/response pattern

### Files to Create

| File | Purpose |
|---|---|
| `broker-service/.../api/controller/PositionManagementController.java` | Internal REST controller |
| `broker-service/.../api/dto/PositionCloseApiDtos.java` | DTOs |

### Internal API Contracts

```
POST /internal/v1/positions/resolve-target
  Body: { brokerAccountId, brokerPositionReference }
  Response: { brokerAccountId, resolvedMutationScope }

POST /internal/v1/positions/execute-close
  Body: { brokerAccountId, resolvedMutationScope, idempotencyKey }
  Response: { status, externalOrderId, correlationId, reasonCode }

POST /internal/v1/positions/reconcile-close
  Body: { brokerAccountId, resolvedMutationScope, idempotencyKey }
  Response: { reconciliationResult }
```

### Invariants

- Internal endpoints require `BrokerPrincipal` authentication
- `resolveTarget` zero financial mutation
- `executeClose` requires reserved scope
- `reconcile` queries provider only

### Tests Required Before Proceeding

1. All three endpoints accessible via internal path
2. BrokerPrincipal authentication enforced
3. resolveTarget returns ResolvedPositionCloseTarget
4. executeClose returns CloseResult
5. reconcile returns ReconciliationCloseResult

### Dependencies

Phase B (PositionManagementCapability).

---

## Phase G — Kraken Position Management Adapter

### Objective

Implement Kraken-specific close using opposite-side market order with `reduce_only=true`.

### Existing Components to Reuse

- `KrakenCapabilities` capability-implementation pattern
- `KrakenRestProviderClient.privatePost()` for API calls
- `KrakenMapper` for parsing responses
- `KrakenResilientClient` for resilience

### Files to Create

| File | Purpose |
|---|---|
| `broker-service/.../infrastructure/provider/kraken/capability/KrakenPositionManagementCapability.java` | Kraken adapter |

### Kraken Close Contract

```java
// resolveTarget:
// 1. Reload open positions from Kraken
// 2. Find position matching brokerPositionReference (txid)
// 3. Return resolvedMutationScope = "{brokerAccountId}:{pair}:{exposure_direction}"
//    (opaque to Trading Core — internal Broker Service convention)

// executeClose:
// 1. Final reload of open positions (revalidation)
// 2. Verify scope semantics still safe (same pair, exposure exists)
// 3. Derive current aggregate signed quantity for pair
// 4. Determine opposite side (BUY→sell, SELL→buy)
// 5. Call AddOrder with:
//    - pair = instrument
//    - type = opposite side
//    - ordertype = market
//    - volume = absolute aggregate quantity for pair
//    - reduce_only = true
//    - cl_ord_id = derived from idempotency key
// 6. Map response to Acknowledged / Rejected / Unknown

// reconcile:
// 1. Query ClosedOrders for matching cl_ord_id
// 2. Refresh OpenPositions for the pair
// 3. If order confirmed executed or positions empty → ExposureConfirmedAbsent
// 4. If order not found + positions still exist → CommandConfirmedNotExecuted
// 5. Otherwise → Inconclusive
```

### Invariants

- `reduce_only=true` guarantees no exposure reversal at provider level
- Oversized volume → provider auto-resizes/cancels residual
- No position → provider rejects (REJECTED)
- FIFO: Kraken settles oldest positions first for pair
- `cl_ord_id` derived from idempotency key (same pattern as `KrakenCapabilities.clientOrderId()`)
- `cl_ord_id` and `txid` are distinct — never conflated
- Final revalidation does not reinterpret scope into different exposure

### Tests Required Before Proceeding

1. resolveTarget: txid found → valid resolvedMutationScope
2. resolveTarget: txid not found → error
3. executeClose: AddOrder called with reduce_only=true
4. executeClose: opposite side derived correctly
5. executeClose: volume = aggregate pair quantity
6. executeClose: provider rejects → REJECTED
7. executeClose: timeout → UNKNOWN
8. reconcile: order confirmed + positions empty → ExposureConfirmedAbsent
9. reconcile: order not found + positions exist → CommandConfirmedNotExecuted
10. reconcile: ambiguous → Inconclusive
11. No exposure reversal possible (reduce_only invariant)

### Dependencies

Phase B (PositionManagementCapability interface + BrokerModels).

---

## Phase H — Angular Full Exposure Close UX

### Objective

Add close exposure confirmation, command state display, and reconcile button to `/positions` page.

### Existing Components to Reuse

- `positions.ts` reactive polling (timer + switchMap)
- `position.service.ts` service pattern
- `execution.service.ts` close/reconcile method pattern
- `dashboard-summary.model.ts` `OpenPositionDashboardView` model
- `positions.html` card grid layout
- `positions.scss` dark theme

### Files to Modify

| File | Change |
|---|---|
| `trading-os-web/.../core/services/position.service.ts` | Add `closePosition()`, `reconcileClose()`, `getCloseCommand()` |
| `trading-os-web/.../features/positions/pages/positions/positions.ts` | Add close command handling, confirmation state, reconcile logic |
| `trading-os-web/.../features/positions/pages/positions/positions.html` | Add close button, confirmation inline, command state badge, reconcile button |
| `trading-os-web/.../features/positions/pages/positions/positions.scss` | Add close/command state styles |
| `trading-os-web/.../features/positions/pages/positions/positions.spec.ts` | Add close-related tests |

### Files to Create

| File | Purpose |
|---|---|
| `trading-os-web/.../core/models/position-close.model.ts` | Close command models |

### Angular Models

```typescript
export type PositionCloseStatus =
  | 'CREATED' | 'SUBMITTED' | 'ACKNOWLEDGED' | 'REJECTED'
  | 'UNKNOWN' | 'CLOSED' | 'NOT_SUBMITTED';

export type ReconciliationResult =
  | 'EXPOSURE_CONFIRMED_ABSENT'
  | 'COMMAND_CONFIRMED_NOT_EXECUTED'
  | 'RECONCILIATION_INCONCLUSIVE'
  | null;

export interface PositionCloseResponse {
  commandId: string;
  status: PositionCloseStatus;
  externalOrderId: string | null;
  failureReason: string | null;
  resolvedMutationScope: string;
  reconciliationResult: ReconciliationResult;
}
```

### Angular Service Methods

```typescript
closePosition(accountId: string, brokerPositionReference: string, idempotencyKey: string): Observable<PositionCloseResponse>
reconcileClose(accountId: string, commandId: string): Observable<PositionCloseResponse>
```

### UX Flow

```
/positions page
  ↓
User clicks "Close Exposure" on position card
  ↓
Inline confirmation:
  "Close all current [SYMBOL] [Long/Short] exposure completely?"
  "Kraken settles multiple open positions for this pair using FIFO."
  "This action cannot be undone."
  [Cancel] [Confirm Full Exposure Close]
  ↓
POST /api/v1/accounts/{accountId}/positions/close
  ↓
Position card shows command state badge:
  CREATED → SUBMITTED → ACKNOWLEDGED | REJECTED | UNKNOWN
  ↓
ACKNOWLEDGED:
  - card remains visible
  - Reconcile button available
  - position polling continues
  ↓
UNKNOWN:
  - card remains
  - "Outcome uncertain" badge
  - Reconcile button available
  ↓
Reconcile clicked:
  POST /api/v1/accounts/{accountId}/positions/close/{commandId}/reconcile
  ↓
CLOSED: position card removed by polling after broker confirms absence
```

### Per-Scope Command Reflection

If several Kraken cards resolve to the same exposure scope: starting close from one card causes all affected controls to reflect the same in-flight operation. No same-scope card offers an independent close while another is active.

### Invariants

- Confirmation required before close submission
- `resolvedMutationScope` treated as opaque by Angular
- ACKNOWLEDGED does not remove position card
- UNKNOWN does not offer blind retry
- Reconcile button only visible for ACKNOWLEDGED and UNKNOWN
- Position removed only by polling after authoritative broker state change
- Duplicate click while command active does not double-submit
- FIFO disclosure in confirmation when applicable

### Tests Required Before Proceeding

1. Close Exposure action visible for open position
2. Confirmation shown with FIFO disclosure
3. Cancel sends nothing
4. Confirm calls service once
5. Duplicate click blocked while command active
6. ACKNOWLEDGED state displayed with Reconcile button
7. UNKNOWN state displayed with Reconcile button
8. Reconcile calls reconciliation endpoint
9. CLOSED position removed by polling
10. Same-scope cards reflect same command state
11. Other position cards remain usable

### Dependencies

Phase E (backend API available).

---

## Phase I — Concurrency + Crash-Window Tests

### Objective

Prove PostgreSQL concurrency guard works; validate crash-window behavior.

### Concurrency Test Strategy

#### H2 Unit Test (Application-Level)

```
Test: Two concurrent requests with different idempotency keys, same resolved scope
Setup: Insert PositionCloseCommandEntity with scope="scope-A", status=CREATED
Action: Attempt second insert with same scope, different idempotency key
Expect: DataIntegrityViolationException (unique constraint)
Verify: Only one command exists for scope
```

#### PostgreSQL Integration Test

```
Test: Concurrent scope reservation with real database
Setup: @SpringBootTest + @ActiveProfiles("test") + explicit TransactionTemplate
Action: Two threads, each persisting command for same scope, different idempotency keys
Coordination: CountDownLatch for concurrent start
Expect: Exactly one succeeds, other fails with constraint violation
Verify: Broker execute call count = 1
```

#### Partial Unique Index Test (PostgreSQL Only)

```
Test: Terminal state does not block new command for same scope
Setup: Insert command with scope="scope-B", status=CLOSED
Action: Insert new command with same scope, status=CREATED
Expect: Succeeds (partial index only blocks ACTIVE states)
```

### Crash-Window Test Matrix

| Scenario | Persisted State | Financial Risk | Recovery Path | Blind Retry Safe |
|---|---|---|---|---|
| A. Before reservation | Nothing | None | User retries | YES |
| B. After reservation, before submission | CREATED | None | User re-initiates | NO |
| C. During submission | SUBMITTED | Uncertain | Reconcile | NO |
| D. Response lost | SUBMITTED/UNKNOWN | Position may be closing | Reconcile | NO |
| E. ACK before persist | SUBMITTED | Broker accepted | Reconcile → CLOSED | NO |

### Tests Required Before Proceeding

1. H2 concurrent scope reservation: at most one succeeds
2. PostgreSQL concurrent scope reservation: at most one succeeds
3. Partial index: terminal state allows new command
4. Partial index: active state blocks duplicate
5. Crash A: no command, user can retry
6. Crash B: transaction rollback prevents orphaned CREATED (no command blocks)
7. Crash C/D/E: reconciliation resolves uncertain state

### Dependencies

Phase C (entity + migration), Phase D (service).

---

## Phase J — Regression + Quality Pipeline

### Objective

Validate no regressions and pass quality pipeline.

### Validation Commands

| Scope | Command |
|---|---|
| Broker Service | `mvn test` in `broker-service/` |
| Trading Core | `mvn test` in `trading-core/` |
| Gateway | `mvn test` in `gateway/` |
| Angular tests | `npm run test:ci` in `trading-os-web/` |
| Angular build | `npm run build` in `trading-os-web/` |
| Angular quality | `npm run check` in `trading-os-web/` |

### Regression Checklist

- [ ] Story 0030 execution flow preserved
- [ ] Story 0031 execution feedback/reconciliation preserved
- [ ] Story 0032 position monitoring preserved
- [ ] Dashboard position projection unchanged
- [ ] Kraken account reads unchanged
- [ ] Market data unchanged
- [ ] Trade planning unchanged
- [ ] No out-of-scope functionality introduced

### Dependencies

All previous phases.

---

## Phase K — Story Artifacts

### Objective

Complete story documentation lifecycle.

### Files to Create

| File | Content |
|---|---|
| `implementation-report.md` | Evidence of implementation: files changed, tests executed, issues found |
| `engineering-report.md` | Code review findings, architectural alignment, quality assessment |
| `code-review.md` | Detailed diff review, security, performance, maintainability |

### Dependencies

Phase J (implementation complete and validated).

---

## Phase Summary

| Phase | Objective | Depends On | Can Parallel |
|---|---|---|---|
| A | Position identity fix | None | B, C |
| B | PositionManagementCapability | A | C, D |
| C | Migration + entity | None | A, B |
| D | Close service + Feign | B, C | E |
| E | REST API | D | F |
| F | Internal Broker API | B | E |
| G | Kraken adapter | B | D, E, F |
| H | Angular UX | E | I |
| I | Concurrency tests | C, D | H |
| J | Regression | All | K |
| K | Story artifacts | J | — |

**Critical path:** A → B → C → D → E → H → J → K

**Parallel opportunities:**
- A ∥ C (identity fix ∥ migration)
- B ∥ C (capability ∥ migration)
- F ∥ D ∥ G (internal API ∥ service ∥ adapter)
- H ∥ I (Angular ∥ concurrency tests)
