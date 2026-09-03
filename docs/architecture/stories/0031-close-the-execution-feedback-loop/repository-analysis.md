# Repository Analysis — Story 0031

## Story

0031 — Close the Execution Feedback Loop

## Repository State

| Field | Value |
|---|---|
| Branch | `main` |
| HEAD | `14fd772` |
| Working tree | Clean |

## Governing ADRs

| ADR | Status | Story Impact | Implementation Alignment |
|---|---|---|---|
| ADR-001 | Accepted | Human authority: user is final decision maker | Aligned — polling/retry/reconcile are read or user-initiated |
| ADR-014 | Accepted | Pipeline terminates with human validation | Aligned — no automatic execution introduced |
| ADR-029 | Accepted | ExecutionIntent lifecycle, idempotency, reconciliation | Aligned — reuses existing lifecycle and recovery pipeline |

## Current End-to-End Flow (Post Story 0030)

```
Angular PlanPage
  → POST /api/v1/executions/validate
    → Gateway → TC: ValidateAndCreateService → ExecutionIntent(VALIDATED)
  → POST /api/v1/executions/{id}/execute
    → Gateway → TC: ExecuteTradeService → BrokerSubmissionStep
      → Feign → BS: POST /internal/v1/executions → Kraken
  → ExecutionDto returned to frontend
  → [STORY 0031: status refresh, enriched display, retry, reconcile]
```

**Story 0030 established the execution path. Story 0031 closes the feedback loop.**

## Trading Core Execution

### ExecutionController endpoints

| Method | Path | Auth | Ownership |
|---|---|---|---|
| POST | `/executions/validate` | `principal(authentication).getUserId()` | Plan + BrokerAccount ownership |
| POST | `/executions/{id}/execute` | `requireOwned(id, authentication)` | query.findOwned(id, userId) |
| GET | `/executions/{id}` | `requireOwned(id, authentication)` | query.findOwned(id, userId) |
| GET | `/executions` | `requireOwned` (list by userId) | findOwned(userId) |
| POST | `/executions/{id}/retry` | `requireOwned(id, authentication)` | query.findOwned(id, userId) |
| POST | `/executions/{id}/cancel` | `requireOwned(id, authentication)` | query.findOwned(id, userId) |
| POST | `/executions/recovery` | **None** | **Global — all users** |

**Assessment:** All user-facing endpoints are safe. The `/recovery` endpoint is global and must NOT be exposed through Gateway.

### ExecutionDto (Current)

```java
record ExecutionDto(
    UUID id, UUID tradePlanId, long tradePlanVersion, UUID riskEvaluationId,
    String idempotencyKey, UUID brokerAccountId, ExecutionStatus status,
    Instant createdAt, Instant updatedAt, Instant expiresAt, long version
)
```

**Missing:** broker order details, fills, failure reason. This is the primary gap Story 0031 addresses.

### ExecutionIntent Lifecycle

```
CREATED → VALIDATED → SUBMISSION_IN_PROGRESS → COMPLETED | FAILED | SUBMISSION_OUTCOME_UNKNOWN
                                                           ↓
                                          RECONCILIATION_IN_PROGRESS → COMPLETED | FAILED | RECOVERY_BLOCKED
                                                           ↓
                                          SUBMISSION_OUTCOME_UNKNOWN → RECONCILIATION_IN_PROGRESS
```

Terminal states: COMPLETED, CANCELLED, EXPIRED.

### RecoverExecutionService

- `recoverAll()` — processes ALL recoverable executions globally
- No `recoverOne()` method exists — this is a gap Story 0031 addresses

## Gateway

### Current routes

The existing wildcard route `/api/v1/executions/**` already covers any new sub-path under executions. No Gateway change needed for the new `/reconcile` endpoint.

### Global recovery endpoint

`POST /executions/recovery` is NOT routed through Gateway. This must remain internal.

## Angular

### Current execution states

```
executionSubmitting → executionResult
```

**Missing states:** executionPolling (for non-terminal refresh).

### Current execution result display

- Status badge (raw enum text)
- Execution ID
- Created/Updated timestamps
- Back link

**Missing:** broker order details, fills, failure reason, retry action, reconcile action, human-readable labels.

### ExecutionService

- `validate()` ✓
- `execute()` ✓
- `getExecution()` ✓ (exists but unused after execution)
- `retry()` ✗ (missing)
- `reconcile()` ✗ (missing)

## Test Inventory

### Existing tests to preserve

| Test | Module | Status |
|---|---|---|
| ExecutionDtoTest | trading-core | Must update for enriched DTO |
| ExecutionPipelineTest | trading-core | Must add recoverOne tests |
| ExecutionDomainTest | trading-core | No changes expected |
| RetryExecutionServiceTest | trading-core | No changes expected |
| ExecutionArchitectureTest | trading-core | No changes expected |
| GatewayDownstreamRoutingIntegrationTest | gateway | No changes expected |
| PlanPage spec tests | trading-os-web | Must update for new states |

### New tests to create

| Test | Module | Purpose |
|---|---|---|
| Enriched DTO projection tests | trading-core | Verify broker order/fill/failure mapping |
| recoverOne tests | trading-core | Verify per-execution reconciliation |
| Polling behavior tests | trading-os-web | Verify poll start/stop/interval |
| UNKNOWN/FAILED state tests | trading-os-web | Verify safe handling |

## Required Changes

1. Enrich ExecutionDto with broker order details, fills, failure reason
2. Add fill aggregation computation (filledQuantity, averageFillPrice, totalFees)
3. Add failure reason projection with deterministic mapping
4. Add `recoverOne()` to RecoverExecutionService
5. Add user-scoped `POST /executions/{id}/reconcile` endpoint
6. Update ExecutionController to pass broker order + attempt to DTO
7. Update Angular ExecutionDto model
8. Add polling to PlanPage for non-terminal states
9. Add human-readable status labels
10. Handle FAILED/UNKNOWN/RECOVERY_BLOCKED states
11. Add retry and reconcile actions
12. Display fill summary when available

## Risks

1. **Polling overhead:** Short-lived polling adds HTTP calls. Mitigated by 2s→5s cadence and 5min max.
2. **Reconcile endpoint safety:** Must verify ownership. Mitigated by requireOwned() pattern.
3. **Fill aggregation precision:** BigDecimal with HALF_UP rounding. No floating-point.

## Blockers

NONE. All evidence supports a clean implementation path.

## Recommendation

**READY_FOR_IMPLEMENTATION.** The backend has all required state persisted. The gap is primarily DTO enrichment and frontend UX. No new domain concepts required.
