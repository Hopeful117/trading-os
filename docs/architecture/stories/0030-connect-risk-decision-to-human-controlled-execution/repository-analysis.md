# Repository Analysis — Story 0030

## Story

0030 — Connect Risk Decision to Human-Controlled Execution

## Repository State

| Field | Value |
|---|---|
| Branch | `main` |
| HEAD | `8e5edc7` |
| Working tree | Clean (untracked `docs/` only) |
| Fetch | Unavailable (SSH key) |

## DevLog Context

- **Availability:** Online
- **Freshness:** PARTIALLY_FRESH (context 21b1d52, HEAD 8e5edc7)
- **Usefulness:** LOW for Story 0030 — evidence biased toward Story 0029 (opportunity setup snapshot). No execution-domain source code surfaced. All Story 0030 evidence gathered through direct repository inspection.

## Governing ADRs

| ADR | Status | Story Impact | Implementation Alignment |
|---|---|---|---|
| ADR-001 | Accepted | Human authority: user is final decision maker | Aligned — execution requires explicit user action |
| ADR-014 | Accepted | Pipeline terminates with human validation | Aligned — but no post-risk Execute action exists yet |
| ADR-029 | Accepted | ExecutionIntent lifecycle, idempotency, reconciliation | Aligned — full pipeline exists |
| ADR-030 | Accepted | Broker Service = technical adapter, no business logic | Aligned — but ownership enforcement missing |
| ADR-031 | Accepted | TradePlanningContext vs RiskContext separation | Aligned — no changes needed |
| ADR-032 | **Proposed** | Entry intent immutability | **Implementation aligned** — `deriveParameters()` reads `plan.entryIntent()` without invention. ADR should be accepted as-is. |

## Current End-to-End Flow

```
Angular PlanPage
  → POST /api/v1/trade-plans/{id}/versions/{v}/decisions {decision: "ACCEPT"}
    → Gateway → MI: TradePlanDecisionService → ACCEPTED
  → POST /api/v1/trade-plans/{id}/versions/{v}/risk-evaluations {accountId}
    → Gateway → TC: TradePlanRiskEvaluationService → APPROVED
  → [MISSING: explicit Execute action]
  → [MISSING: Gateway execution route]
  → POST /api/v1/executions/validate (does not exist in Gateway)
    → TC: ValidateAndCreateService → ExecutionIntent
  → POST /api/v1/executions/{id}/execute (does not exist in Gateway)
    → TC: ExecuteTradeService → BrokerSubmissionStep
      → Feign → BS: POST /internal/v1/executions → Kraken
```

**Current pipeline status:** Backend execution architecture is mature. The missing pieces are: Gateway route, Angular UI, account-ID bug fix, Broker Service ownership, and actor propagation.

## Trading Core Execution

### ExecutionController endpoints (all require authentication via `requireOwned()`)

| Method | Path | Auth | Ownership |
|---|---|---|---|
| POST | `/executions/validate` | `principal(authentication).getUserId()` | Step 9: plan.ownerId == initiatorId; Step 11: brokerAccounts.findByIdAndOwnerId |
| POST | `/executions/{id}/execute` | `requireOwned(id, authentication)` | query.findOwned(id, userId) |
| GET | `/executions/{id}` | `requireOwned(id, authentication)` | query.findOwned(id, userId) |
| GET | `/executions` | `requireOwned` (list by userId) | findOwned(userId) |
| POST | `/executions/{id}/retry` | `requireOwned(id, authentication)` | query.findOwned(id, userId) |
| POST | `/executions/{id}/cancel` | `requireOwned(id, authentication)` | query.findOwned(id, userId) |

**Assessment:** Trading Core execution endpoints are SAFE.

### ValidateAndCreateService — 14 validation steps

1. Load RiskEvaluation by ID → 404 if absent
2. Status == COMPLETED → 422 if not
3. Decision == APPROVED or APPROVED_WITH_WARNINGS → 422 if not
4. **BUG:** `evaluation.accountId()` == `command.brokerAccountId()` → 409 on mismatch
5. Load TradePlan by ID+version → 404 if absent
6. Plan.status == ACCEPTED → 422 if not
7. Plan.version == evaluation.version → 409 if mismatch
8. Plan.id == evaluation.tradePlanId → 409 if mismatch
9. Plan.ownerId == command.initiatorId → 403 if mismatch
10. evaluation.accountId == plan.tradingAccountId → 409 if mismatch
11. BrokerAccount belongs to initiator → 403 if not
12. deriveParameters() from plan.entryIntent → ExecutionParameters
13. Create ExecutionIntent aggregate
14. Transition to VALIDATED

**Step 4 is the bug.** It compares TradingAccount UUID with BrokerAccount UUID. Steps 10-11 correctly handle account consistency.

### ExecuteTradeService pipeline

ExecutionValidationStep → IdempotencyVerificationStep → ExecutionAttemptCreationStep → BrokerSubmissionStep → BrokerResponseProcessingStep → ExecutionFinalizationStep

### ExecutionIntent lifecycle

```
CREATED → VALIDATED → SUBMISSION_IN_PROGRESS → COMPLETED | FAILED | SUBMISSION_OUTCOME_UNKNOWN
                                                          ↓
                                         RECONCILIATION_IN_PROGRESS → COMPLETED | FAILED | RECOVERY_BLOCKED
                                                          ↓
                                         SUBMISSION_OUTCOME_UNKNOWN → RECONCILIATION_IN_PROGRESS
```

## Account Identity Bug

**Location:** `ValidateAndCreateService.java:74`

```java
if (!evaluation.accountId().equals(command.brokerAccountId())) {
```

- `evaluation.accountId()` = Trading Account UUID (from `RiskPersistence.StoredEvaluation.accountId`, populated from `RiskEvaluationEntity.account_id`)
- `command.brokerAccountId()` = Broker Account UUID (from `ValidateAndCreateRequest.brokerAccountId`)

**These are different domain entities.** The comparison is always false in normal operation, making execution unreachable.

**Fix:** Remove step 4 entirely. Step 10 already correctly validates `evaluation.accountId() == plan.tradingAccountId()`. Step 11 already correctly validates BrokerAccount ownership.

**Test impact:** `ValidateAndCreateServiceTest` deliberately sets `storedEvaluation.accountId = brokerAccountId` and `plan.tradingAccountId = brokerAccountId` to pass the buggy check. Must be corrected to use distinct UUIDs.

## Broker Service Ownership

### Current state

| Service | Ownership Check | Pattern |
|---|---|---|
| GetRiskSnapshotService | YES | `connections.findByBrokerAccountIdAndOwnerId(id, ownerId)` |
| ExecuteOrderService | **NO** | `providers.resolve(brokerAccountId)` only |
| CancelOrderService | **NO** | `providers.resolve(id)` only |
| ReconcileExecutionService | **NO** | `providers.resolve(brokerAccountId)` only |
| GetAccountService | **NO** | `providers.resolve(id)` only |
| GetPositionsService | **NO** | `providers.resolve(id)` only |
| GetOrdersService | **NO** | `providers.resolve(id)` only |

### Required pattern (from GetRiskSnapshotService)

1. Controller extracts `ownerId` from `@AuthenticationPrincipal BrokerPrincipal`
2. Service validates `connections.findByBrokerAccountIdAndOwnerId(id, ownerId).isEmpty()`
3. Only proceeds if broker account belongs to authenticated user

### ExecutionRequest model (BrokerService)

```java
record ExecutionRequest(UUID executionIntentId, UUID executionAttemptId,
    String idempotencyKey, UUID brokerAccountId, String instrument,
    Side side, OrderType orderType, BigDecimal quantity, BigDecimal limitPrice)
```

**No `ownerId` field exists.** Correction A: Do NOT add `ownerId` to the HTTP DTO. Instead, use the authenticated `BrokerPrincipal.userId()` derived from the JWT.

## Actor Propagation

### Current state

- **Gateway → Trading Core:** JWT principal propagated via Spring Security context. `ExecutionController` extracts `principal(authentication).getUserId()`.
- **Trading Core → Broker Service:** Feign interceptor propagates `Authorization` header only. No `X-Actor-Id` or `ownerId` in `BrokerRequest`.
- **Broker Service:** `ExecutionController` has no `@AuthenticationPrincipal`. No userId extraction. `brokerAccountId` from request body is trusted directly.

### Correction A: JWT-based identity (verified)

The JWT is already propagated: Trading Core's `FeignAuthorizationConfiguration` copies the `Authorization` header to all Feign requests. Broker Service's `BrokerJwtAuthenticationFilter` parses the JWT and creates `BrokerPrincipal(userId, username, role)` in the SecurityContext.

**Mechanism:** Add `@AuthenticationPrincipal BrokerPrincipal principal` to Broker Service `ExecutionController` methods. Extract `principal.userId()` and pass to application services. Services validate ownership using `connections.findByBrokerAccountIdAndOwnerId(brokerAccountId, ownerId)`.

No `ownerId` in HTTP request body. No new headers. No new authentication infrastructure.

## Gateway

### Current routes

No `/api/v1/executions/**` route exists. The route table covers: authentication, accounts, broker-credential-commands, broker-accounts, trade-plan-risk-evaluations, opportunities, markets, market-intelligence.

Discovery locator is enabled (`spring.cloud.gateway.discovery.locator.enabled=true`), which could auto-generate routes for `trading-core` including execution endpoints. But explicit routes take priority.

### Required change

Add one explicit route:
```java
route("executions", "/api/v1/executions/**", "lb://trading-core")
```

This follows the existing convention (each domain owns its prefix).

### Discovery locator concern

Discovery locator may generate a `/trading-core/internal/v1/**` route that bypasses the explicit route table. This is an existing architectural concern outside Story 0030 scope. Broker Service ownership enforcement provides defense-in-depth.

## Angular

### Current TradePlan page states

```
loading → error
loading → proposal (PROPOSED/DRAFT: accept/reject buttons)
loading → accepted (ACCEPTED: evaluate risk button)
loading → rejected (REJECTED: plan info)
loading → evaluatingRisk → riskDecision (risk results: approved/rejected/warnings)
```

**No execution states exist.** The pipeline terminates at `riskDecision`.

### Required additions

1. `ExecutionService` — new Angular service with `validate()` and `execute()` methods
2. New view states on PlanPage: `executionReady`, `executionSubmitting`, `executionResult`
3. Execute button in `riskDecision` view (only when `decision.approved === true`)
4. Execution result display (completed/rejected/unknown)
5. Optional lightweight confirmation before execution

### Test conventions

- Fake data factories (`fakePlan()`, `fakeRiskDecision()`)
- `configureMocks()` with `TestBed.configureTestingModule`
- `data-testid` selectors
- `HttpTestingController` for service tests

## Execution API

### Existing endpoints (Trading Core)

| Endpoint | Method | Request | Response |
|---|---|---|---|
| `/executions/validate` | POST | `ValidateAndCreateRequest` + `Idempotency-Key` header | `ExecutionDto` (201) |
| `/executions/{id}/execute` | POST | path variable | `ExecutionDto` (200) |
| `/executions/{id}` | GET | path variable | `ExecutionDto` (200) |

### Angular integration

Angular must call these through Gateway. The `ExecutionDto` response contains: `id, tradePlanId, tradePlanVersion, riskEvaluationId, idempotencyKey, brokerAccountId, status, createdAt, updatedAt, expiresAt, version`.

The `status` field is the `ExecutionStatus` enum: `CREATED, VALIDATED, SUBMISSION_IN_PROGRESS, SUBMISSION_OUTCOME_UNKNOWN, RECONCILIATION_IN_PROGRESS, COMPLETED, FAILED, RECOVERY_BLOCKED, CANCELLED, EXPIRED`.

## Entry Intent

`ValidateAndCreateService.deriveParameters()` reads `plan.entryIntent()` and maps:
- `EntryIntent.OrderType.MARKET` → `ExecutionParameters.OrderType.MARKET`
- `EntryIntent.OrderType.LIMIT` → `ExecutionParameters.OrderType.LIMIT`
- `EntryIntent.OrderType.STOP` → throws `UNSUPPORTED_ENTRY_INTENT`

No caller input is used. ADR-032 is fully aligned.

## Idempotency

- DB unique constraint on `idempotency_key`
- `IdempotencyService.ensureUnique()` checks before creation
- `IdempotencyService.verifyIdentity()` checks before re-execution
- Single active attempt enforced by `ExecutionIntent.activateAttempt()`
- Optimistic locking via `@Version`
- Kraken `cl_ord_id` = UUID v3 from idempotency key (deterministic)

**Frontend:** The `Idempotency-Key` header must be provided. Angular should generate a UUID for each execution attempt.

## Execution Result Semantics

| Backend Status | Angular Display |
|---|---|
| `COMPLETED` | "Order submitted and confirmed." |
| `FAILED` | "Order rejected: {reason}." |
| `SUBMISSION_OUTCOME_UNKNOWN` | "Order submission status is uncertain. Reconciliation is required." |
| `RECONCILIATION_IN_PROGRESS` | "Reconciliation in progress..." |
| `RECOVERY_BLOCKED` | "Recovery blocked. Manual intervention required." |

**UNKNOWN must not be flattened into FAILED.**

## Sandbox / Mock Strategy

- `KRAKEN_BASE_URL` env var controls API target (configurable)
- No sandbox profile exists in code
- Tests use `https://example.invalid`
- No mock `ExecutionCapability` exists in broker-service

**Recommended:** Use Kraken sandbox by setting `KRAKEN_BASE_URL=https://api-demo.kraken.com` (or equivalent sandbox URL). If sandbox unavailable, create a test-only `ExecutionCapability` stub in broker-service tests.

**Margin blocker:** `UnavailableRequiredMarginClient` returns `Optional.empty()`, causing risk evaluation to throw `REQUIRED_MARGIN_UNAVAILABLE`. For sandbox proof, a test margin adapter returning a valid `Fact` is required.

## Live-Trading Safety

`KRAKEN_BASE_URL` is the only control. If unset or pointing to production, execution goes live. The environment variable approach is fail-closed (missing var = connection failure = no execution).

**No accidental live execution risk** as long as the sandbox env var is set correctly for the controlled proof.

## Margin Constraint

`RequiredMarginPort` defaults to `UnavailableRequiredMarginClient` (fail-closed stub). Risk evaluation throws `REQUIRED_MARGIN_UNAVAILABLE` when margin cannot be resolved.

**For sandbox proof:** A test/mock margin adapter returning a valid `Fact` with appropriate values is needed. This does NOT bypass risk — it provides the margin fact that deterministic risk rules consume.

**Classification:** TEST_CONFIGURATION_REQUIRED

## Test Inventory

### Existing tests to modify

| Test | File | Change |
|---|---|---|
| ValidateAndCreateServiceTest | `trading-core/.../ValidateAndCreateServiceTest.java` | Use distinct TradingAccount/BrokerAccount UUIDs; add regression test for account-ID bug |
| ExecutionPipelineTest | `trading-core/.../ExecutionPipelineTest.java` | No changes expected |
| BrokerExecutionContractTest | `trading-core/.../BrokerExecutionContractTest.java` | Update if BrokerRequest changes (add ownerId) |

### New tests to create

| Test | Module | Purpose |
|---|---|---|
| Broker Service ownership test | broker-service | Prove ExecuteOrderService rejects unauthorized brokerAccountId |
| Gateway execution route test | gateway | Prove execution route forwards to trading-core |
| Angular execution service test | trading-os-web | Prove validate/execute API calls |
| Angular plan-page execution test | trading-os-web | Prove Execute button visibility, result display |

### Tests to verify unchanged

| Test | Module |
|---|---|
| ExecutionDomainTest | trading-core |
| ExecutionArchitectureTest | trading-core |
| RetryExecutionServiceTest | trading-core |
| BrokerApiContractTest | broker-service |

## Required Changes

1. Remove ValidateAndCreateService step 4 (account-ID bug)
2. Add `ownerId` field to BrokerService `ExecutionRequest` and `BrokerRequest`
3. Add ownership validation to `ExecuteOrderService`, `CancelOrderService`, `ReconcileExecutionService`
4. Add `@AuthenticationPrincipal` to BrokerService `ExecutionController`
5. Add `ownerId` propagation in Trading Core Feign client/adapter
6. Add Gateway execution route
7. Create Angular `ExecutionService`
8. Add execution states and UI to Angular `PlanPage`
9. Create test margin adapter for sandbox proof
10. Update tests with distinct account IDs

## Reusable Components

- `GetRiskSnapshotService` ownership pattern (Broker Service)
- `BrokerConnectionRepository.findByBrokerAccountIdAndOwnerId()` (already exists)
- `BrokerJwtAuthenticationFilter` + `BrokerPrincipal` (already extracts userId)
- `ExecutionController.requireOwned()` pattern (Trading Core)
- `IdempotencyKey`, `IdempotencyService` (no changes needed)
- `ExecutionLifecycleService` (no changes needed)
- `BrokerSubmissionStep` (no changes needed)
- Existing Angular test conventions (fake factories, configureMocks, data-testid)

## Risks

1. **Margin blocker:** Risk evaluation fails without margin adapter. Must provide test adapter.
2. **Kraken sandbox availability:** If sandbox endpoint is unreachable, E2E proof requires mock.
3. **Discovery locator:** May expose internal Broker Service routes. Ownership enforcement provides defense-in-depth.
4. **Feign header propagation:** Adding `ownerId` to `BrokerRequest` changes the Feign contract. Must update both sides atomically.

## Blockers

NONE. All evidence supports a clean implementation path.

## Recommendation

**READY_FOR_IMPLEMENTATION_PLAN.** The execution backend is mature. The fixes are bounded and well-understood. No architectural redesign is required. The margin test adapter is the only configuration prerequisite.
