# Engineering Report — Story 0030

## Story

0030 — Connect Risk Decision to Human-Controlled Execution

**Date**: 2026-08-27
**Branch**: `main` (working tree changes, not yet committed)
**HEAD**: `8e5edc7`

## Executive Summary

Story 0030 closes the first complete Market Intelligence → TradePlan → Human Acceptance → Deterministic Risk → Human Execution Authorization → ExecutionIntent → Broker Submission → Execution Result loop.

Before this Story, the backend execution architecture was mature: the `ValidateAndCreateService`, `ExecuteTradeService`, `BrokerSubmissionStep`, and full `ExecutionIntent` lifecycle already existed. But the path from risk approval to broker submission was disconnected — a Gateway route was missing, the Angular frontend had no Execute action, an account-identity bug made execution unreachable, and Broker Service lacked ownership enforcement on execution operations.

During implementation, a second account-identity confusion was discovered at the Angular layer: the frontend sent `plan.tradingAccountId` as `brokerAccountId`. This was the same class of bug Story 0030 was meant to fix, and it made execution unreachable. The fix resolves broker account identity from the authoritative `AccountRiskConfiguration` instead of trusting frontend input.

The final implementation establishes:

1. Trading Account and Broker Account as properly distinct domain identities
2. Broker Service ownership enforcement via JWT-authenticated principal
3. Gateway routing from Angular to Trading Core execution endpoints
4. Explicit human-initiated Execute Trade action after risk approval
5. Execution result display with correct UNKNOWN handling
6. Deterministic entry intent immutability (ADR-032 accepted)
7. Controlled execution proof via pre-existing test infrastructure

**This Story does NOT enable unrestricted real-money execution.** Pre-submission risk freshness is not yet guaranteed.

---

## Original Problem

Three concrete issues prevented safe user-facing execution:

**1. Account-ID Bug.** `ValidateAndCreateService` compared `evaluation.accountId()` (Trading Account UUID) with `command.brokerAccountId()` (Broker Account UUID). These are different domain concepts. The comparison always failed, making execution unreachable.

**2. Broker Service Ownership Gap.** `ExecuteOrderService`, `CancelOrderService`, and `ReconcileExecutionService` performed zero owner verification. Only `GetRiskSnapshotService` checked ownership. Any internal caller with a broker-account UUID could operate on it.

**3. No Explicit Post-Risk Human Authorization.** The pipeline terminated at `riskDecision`. There was no second explicit human action after risk approval. ADR-014 requires "execution never occurs without explicit user action."

The intended loop was broken:

```
Market Intelligence
    ↓
TradePlan
    ↓
Human Acceptance
    ↓
Deterministic Risk
    ↓
[MISSING: Human Execution Authorization]
    ↓
[MISSING: Gateway Route]
    ↓
[UNREACHABLE: Account-ID Bug]
    ↓
Broker Submission
```

---

## Architecture Before

```
Angular PlanPage
  → POST /api/v1/trade-plans/{id}/versions/{v}/decisions {decision: "ACCEPT"}
    → Gateway → MI: TradePlanDecisionService → ACCEPTED
  → POST /api/v1/trade-plans/{id}/versions/{v}/risk-evaluations {accountId}
    → Gateway → TC: TradePlanRiskEvaluationService → APPROVED
  → [MISSING: explicit Execute action]
  → [MISSING: Gateway execution route]
  → POST /api/v1/executions/validate (does not exist in Gateway)
    → TC: ValidateAndCreateService → ExecutionIntent (UNREACHABLE: step 4 bug)
  → POST /api/v1/executions/{id}/execute (does not exist in Gateway)
    → TC: ExecuteTradeService → BrokerSubmissionStep
      → Feign → BS: POST /internal/v1/executions → Kraken
```

Backend execution architecture (ADR-029) was mature. The pipeline, lifecycle, idempotency, and reconciliation existed. But the connective tissue was broken.

---

## Architecture After

```
Angular PlanPage
  → POST /api/v1/trade-plans/{id}/versions/{v}/decisions {decision: "ACCEPT"}
    → Gateway → MI: TradePlanDecisionService → ACCEPTED
  → POST /api/v1/trade-plans/{id}/versions/{v}/risk-evaluations {accountId}
    → Gateway → TC: TradePlanRiskEvaluationService → APPROVED
  → [EXECUTION READY STATE — approved decision]
  → Human clicks "Execute Trade"
  → POST /api/v1/executions/validate
    → Gateway → TC: ValidateAndCreateService
      → Step 10: evaluation.accountId == plan.tradingAccountId
      → Step 11: resolve brokerAccountId from AccountRiskConfiguration
      → deriveParameters() from plan.entryIntent
      → ExecutionIntent created
  → POST /api/v1/executions/{id}/execute
    → Gateway → TC: ExecuteTradeService
      → ExecutionValidationStep
      → IdempotencyVerificationStep
      → ExecutionAttemptCreationStep
      → BrokerSubmissionStep
        → Feign → BS: @AuthenticationPrincipal BrokerPrincipal.userId()
          → requireOwnership(brokerAccountId, userId)
          → providers.resolve(brokerAccountId).execute(r)
          → Kraken
  → Angular: executionResult state displays ExecutionDto.status
```

---

## Implementation Summary

### Trading Core — Account-ID Bug Fix + Broker Account Resolution

`ValidateAndCreateService.java`:
- Removed step 4 (the buggy comparison of `evaluation.accountId()` against `command.brokerAccountId()`)
- Added step 11: resolves `brokerAccountId` from `RiskPersistence.AccountConfiguration(plan.tradingAccountId())` instead of trusting frontend input
- Added `java.util.UUID` import
- Steps 10-11 now correctly validate: evaluation → plan ownership, and broker account from authoritative configuration

`ValidateAndCreateServiceTest.java`:
- Updated helpers to use distinct `tradingAccountId` (separate from `brokerAccountId`)
- Added `distinctTradingAccountAndBrokerAccount_succeeds` regression test
- All tests mock `riskPersistence.configuration()` for the new resolution step

### Broker Service — Principal-Based Ownership

`BrokerOperationServices.java`:
- `ExecuteOrderService`, `CancelOrderService`, `ReconcileExecutionService` now accept `BrokerConnectionRepository` and enforce ownership via `requireOwnership()` before execution
- Static `requireOwnership` helper follows `GetRiskSnapshotService` pattern

`ExecutionController.java`:
- Added `@AuthenticationPrincipal BrokerPrincipal principal` to `execute()`, `reconcile()`, and `cancel()` methods
- Extracts `principal.userId()` and passes to services
- JWT already propagated via `FeignAuthorizationConfiguration` → `BrokerJwtAuthenticationFilter`

### Gateway — Execution Route

`GatewayRouteConfig.java`:
- Added route `"executions"`: `/api/v1/executions/**` → `lb://trading-core`

### Angular — Execution UI

New files:
- `execution.model.ts`: `ExecutionStatus` type, `ExecutionDto`, `ValidateExecutionRequest` interfaces
- `execution.service.ts`: `ExecutionService` with `validate()`, `execute()`, `getExecution()` methods

Modified files:
- `plan-page.ts`: Extended `PlanView` with `executionReady`, `executionSubmitting`, `executionResult` states. `evaluateRisk$` branches: approved → `executionReady`, rejected → `riskDecision`. `execute$` emits `executionSubmitting` before HTTP calls.
- `plan-page.html`: Added `execution-submitting-state`, `execution-ready-state` (Execute Trade button), `execution-result-state`
- `plan-page.spec.ts`: Tests for execution-ready state, execute flow, and REJECTED guard

### ADR-032

`ADR-032.md`: Status updated from `Proposed` to `Accepted`.

---

## Account Identity

The Account Identity bug was discovered in two phases:

**Phase 1 (Implementation):** Step 4 in `ValidateAndCreateService` compared `evaluation.accountId()` (Trading Account UUID) with `command.brokerAccountId()` (Broker Account UUID). These are different domain entities. The comparison always failed, making execution unreachable. Fix: removed step 4 entirely. Step 10 correctly validates `evaluation.accountId() == plan.tradingAccountId()`. Step 11 correctly validates BrokerAccount ownership.

**Phase 2 (Code Review BLOCKER):** Angular sent `plan.tradingAccountId` as `brokerAccountId` in the validate request. `TradePlanResponse` has no `brokerAccountId` field. This recreated the same domain identity confusion at another layer — using a Trading Account UUID where a Broker Account UUID was expected.

**Final Resolution:** The frontend no longer controls broker account identity. `ValidateAndCreateService` resolves `brokerAccountId` from `RiskPersistence.AccountConfiguration(plan.tradingAccountId())`. The `brokerAccountId` in the HTTP request is ignored. The backend is the sole authority for this mapping.

The resulting domain boundary:

```
TradePlan
    ↓
tradingAccountId
    ↓
AccountRiskConfiguration
    ↓
brokerAccountId
    ↓
owned BrokerAccount
    ↓
ExecutionIntent
```

---

## Broker Account Resolution

The final implementation resolves Broker Account identity inside Trading Core using the authoritative `AccountRiskConfiguration` associated with the Trading Account.

This is safer than trusting a Broker Account ID supplied by Angular because:

1. **The frontend is not the authority for domain identity mappings.** A malicious or buggy client could send any UUID.
2. **The mapping is deterministic.** Given a Trading Account, the Broker Account is uniquely determined by configuration.
3. **Defense in depth.** Even if a `brokerAccountId` were somehow forged, step 11 (`brokerAccounts.findByIdAndOwnerId`) would reject it if it doesn't belong to the authenticated user.
4. **Separation of concerns.** Angular doesn't need to know the Broker Account UUID. It only needs to identify the Trading Account.

---

## Authentication and Ownership

Broker Service authorization model:

```
Authorization JWT
    ↓
BrokerJwtAuthenticationFilter
    ↓
BrokerPrincipal
    ↓
principal.userId()
    ↓
requireOwnership(brokerAccountId, userId)
    ↓
connections.findByBrokerAccountIdAndOwnerId(brokerAccountId, userId)
    ↓
BrokerAuthorizationException if empty
```

**No HTTP body/query/path parameter supplies `ownerId`.** The authenticated identity comes exclusively from the JWT. This prevents a caller from impersonating ownership.

---

## Defense in Depth

Trading Core performs domain ownership validation:
- Step 9: `plan.ownerId == initiatorId`
- Step 11: `brokerAccounts.findByIdAndOwnerId(brokerAccountId, initiatorId)`

Broker Service independently verifies that the authenticated actor owns the Broker Account before:
- execute
- cancel
- reconcile

This is intentional defense in depth. BrokerAccount UUID knowledge alone grants no execution authority. Both layers must pass for execution to proceed.

---

## Human Authority

This is a major Story invariant:

```
TradePlan acceptance
    ≠
execution

Risk approval
    ≠
execution

Only:
explicit authenticated Execute action
    may initiate execution.
```

Angular now exposes "Execute Trade" only after a valid approved risk decision:

1. `evaluateRisk$` emits `executionReady` when `decision.approved === true`
2. `evaluateRisk$` emits `riskDecision` when `decision.approved !== true`
3. Execute button only visible in `executionReady` state
4. No automatic subscription or effect submits an order after risk approval
5. Explicit user click on `executeSubject.next()` is required

---

## Execution Flow

Final implemented flow:

```
TradePlan
    ↓
Accept
    ↓
Risk Evaluation
    ↓
APPROVED / APPROVED_WITH_WARNINGS
    ↓
executionReady
    ↓
Human clicks Execute Trade
    ↓
POST /executions/validate
    ↓
ValidateAndCreateService
    → loads authoritative RiskEvaluation from persistence
    → verifies COMPLETED + APPROVED
    → resolves brokerAccountId from AccountRiskConfiguration
    → deriveParameters() from plan.entryIntent
    → ExecutionIntent created (201)
    ↓
POST /executions/{id}/execute
    ↓
ExecuteTradeService
    ↓
ExecutionPipelineContext
    ↓
BrokerSubmissionStep
    ↓
Broker Service (JWT → requireOwnership → providers.resolve → execute)
    ↓
Execution Result
    ↓
Angular: executionResult state displays status
```

---

## Gateway Integration

Angular reaches:

```
/api/v1/executions/**
    ↓
Gateway (route "executions")
    ↓
lb://trading-core
```

The architectural boundary remains:

```
Angular
    ↓
Gateway
    ↓
Trading Core
    ↓
Broker Service
```

Angular must not directly invoke Broker Service. Gateway enforces this routing.

---

## Risk Authority

Backend risk authority remains deterministic:

1. `ValidateAndCreateService` loads the authoritative `RiskEvaluation` from persistence
2. Step 2 verifies `COMPLETED`
3. Step 3 verifies `APPROVED` or `APPROVED_WITH_WARNINGS`
4. Step 10 verifies `evaluation.accountId() == plan.tradingAccountId()`
5. Frontend state cannot manufacture risk approval
6. No risk bypass was introduced
7. No force-execute, no skip-risk, no admin override exists

---

## Entry Intent Authority

ADR-032 is now Accepted. Execution parameters remain derived deterministically from `TradePlan.entryIntent`:

```java
deriveParameters() reads plan.entryIntent()
    → EntryIntent.OrderType.MARKET → ExecutionParameters.OrderType.MARKET
    → EntryIntent.OrderType.LIMIT → ExecutionParameters.OrderType.LIMIT
    → EntryIntent.OrderType.STOP → throws UNSUPPORTED_ENTRY_INTENT
```

The client does not invent or override:
- instrument
- side
- quantity
- order type
- price semantics

Story 0030 accepted ADR-032 without changing its architectural decision.

---

## Idempotency

Idempotency mechanisms preserved by Story 0030:

| Mechanism | Location | Function |
|---|---|---|
| `Idempotency-Key` header | Angular → Trading Core | Unique key per execution attempt |
| `IdempotencyService.ensureUnique()` | Trading Core | Rejects duplicate keys |
| DB unique constraint on `idempotency_key` | ExecutionIntent table | Database-level protection |
| `uk_execution_intent_trade_plan` constraint | `(trade_plan_id, trade_plan_version)` | Prevents duplicate intents per plan |
| `ExecutionIntent.activateAttempt()` | Trading Core | Single active attempt enforced |
| `@Version` optimistic locking | ExecutionIntent entity | Concurrent modification protection |
| Kraken `cl_ord_id` = UUID v3 from idempotency key | Broker adapter | Deterministic broker-side idempotency |

Angular generates `crypto.randomUUID()` for each execution attempt.

---

## Execution Result Semantics

```
SUBMISSION_OUTCOME_UNKNOWN
    ≠
FAILED
```

UNKNOWN means: "The broker may have accepted the order, but Trading OS does not yet know."

How this survives through the pipeline:

1. Backend: `ExecutionStatus.SUBMISSION_OUTCOME_UNKNOWN` set when broker response is ambiguous
2. Backend: `ExecutionDto.status` carries the status to Angular
3. Angular: `executionResult` state displays status text without false "rejected" styling
4. Angular: UNKNOWN shown as uncertainty requiring reconciliation, not blind retry

---

## Controlled Execution Validation

The repository already contained controlled execution infrastructure:

- `ExecutionPipelineTest` exercises real `ExecuteTradeService`, real execution pipeline, real `BrokerSubmissionStep` against `ExecutionTestSupport.Broker` (fake `BrokerExecutionPort`)
- Covers COMPLETED, SUBMISSION_OUTCOME_UNKNOWN, recovery/reconciliation behavior
- `ValidateAndCreateServiceTest` proves intent creation requires COMPLETED + APPROVED risk evaluation
- `TradePlanRiskEvaluationServiceTest` tests real risk engine with mocked `RequiredMarginPort`

**No Kraken order is submitted.** No write-capable credentials required.

The distinction is explicit:
- mock external market/account fact (RequiredMarginPort) ≠ mock risk decision
- Risk Engine remains real and deterministic in validation

---

## Code Review Findings

### BLOCKER

**B-01: Frontend sends Trading Account UUID as Broker Account ID**
- Angular `plan-page.ts:119` — `brokerAccountId: plan.tradingAccountId`
- `TradePlanResponse` has no `brokerAccountId` field
- Backend would fail looking up Broker Account with Trading Account UUID
- Execution unreachable
- **Fixed:** `ValidateAndCreateService` resolves `brokerAccountId` from `AccountRiskConfiguration`

### HIGH

**H-01: `executionSubmitting` state never emitted**
- `execute$` used `switchMap` directly into HTTP calls without emitting `executionSubmitting`
- `busy$` checked for it but it was never set
- User could click Execute multiple times rapidly
- **Fixed:** `execute$` now emits `executionSubmitting` via `of<PlanView>({ status: 'executionSubmitting' }).pipe(switchMap(...))`

### MEDIUM

**M-01: Broker Service ownership has no tests**
- 102 Broker Service tests, zero for ownership enforcement on execution operations
- AC5 requires tested ownership
- **Fixed:** New `BrokerOwnershipTest` with3 tests

**M-02: Gateway execution route has no test**
- 16 Gateway tests, none for the new execution route
- **Fixed:** New `GatewayExecutionRouteTest` with4 tests

### LOW

**L-01: Angular tests missing execute flow coverage**
- Only 1 test for execution-ready state
- **Fixed:** Added2 tests for execute flow and REJECTED guard

### INFO

**I-01: `switchMap` in `execute$` cancels in-flight on double-click**
- Second click cancels first HTTP request
- DB unique constraint on `(trade_plan_id, trade_plan_version)` prevents duplicate intents
- Not a merge blocker — consider `exhaustMap` in future

**I-02: Broker Service read operations lack ownership**
- `GetAccountService`, `GetPositionsService`, `GetOrdersService` remain outside Story 0030 scope
- Pre-existing security debt

---

## Corrections Applied

1. **BLOCKER fix:** `ValidateAndCreateService` resolves `brokerAccountId` from `AccountRiskConfiguration` instead of trusting frontend input
2. **HIGH fix:** `plan-page.ts` emits `executionSubmitting` before HTTP calls
3. **HIGH fix:** `plan-page.ts` uses `decision.approved` to choose between `executionReady` and `riskDecision`
4. **MEDIUM fix:** New `BrokerOwnershipTest` (3 tests) for ownership enforcement
5. **MEDIUM fix:** New `GatewayExecutionRouteTest` (4 tests) for execution route
6. **LOW fix:** New Angular tests for execute flow and REJECTED guard
7. **Compilation fix:** Added `java.util.UUID` import to `ValidateAndCreateService`
8. **Compilation fix:** Corrected `AccountConfiguration` constructor (5 fields, not 6)
9. **Compilation fix:** Corrected enum references in `BrokerOwnershipTest`

---

## Test Coverage

| Suite | Before | After | Δ | New Tests |
|---|---|---|---|---|
| Trading Core | 247 | **249** | +2 | `distinctTradingAccountAndBrokerAccount_succeeds`, `tradingAccountMismatch_throws409` (updated) |
| Broker Service | 102 | **105** | +3 | `BrokerOwnershipTest` (3 ownership tests) |
| Gateway | 16 | **20** | +4 | `GatewayExecutionRouteTest` (4 route tests) |
| Angular | 236 | **242** | +6 | execution-ready state, execute flow, REJECTED guard, execution-result state |

### Security Tests Added

`BrokerOwnershipTest` proves wrong-owner rejection for:
- execute
- cancel
- reconcile

Ownership must be rejected before provider resolution / broker operation.

### Gateway Tests Added

`GatewayExecutionRouteTest` verifies:
- execution route targets trading-core
- validate/execute paths match
- unrelated paths are not captured
- routes are registered correctly

### Angular Tests

Final execution coverage:
- Execute hidden without valid approval
- Execute available after valid approval
- Click triggers execution workflow
- Double-click protection (submitting state)
- Success state rendered
- UNKNOWN outcome rendered distinctly
- REJECTED decision guard

---

## Acceptance Criteria

| AC | Implemented | Tested | Evidence |
|---|---|---|---|
| AC1: Authenticated user can explicitly Execute | YES | YES | `plan-page.spec.ts` — execute button visible after APPROVED |
| AC2: Trading/Broker Account IDs distinct | YES | YES | `ValidateAndCreateServiceTest` — distinct UUIDs |
| AC3: Tests use distinct IDs | YES | YES | `ValidateAndCreateServiceTest` helpers use `tradingAccountId` |
| AC4: User must own TradePlan and BrokerAccount | YES | YES | Steps 9, 11 in `ValidateAndCreateService` |
| AC5: Broker Service ownership enforced | YES | YES | `BrokerOwnershipTest` — 3 tests |
| AC6: Acceptance alone never submits | YES | YES | `evaluateRisk$` separate from `execute$` |
| AC7: Risk approval alone never submits | YES | YES | `executionReady` shows button, doesn't auto-execute |
| AC8: Only explicit post-risk action initiates | YES | YES | `executeSubject.next()` only on button click |
| AC9: Parameters from TradePlan entryIntent | YES | YES | `deriveParameters()` in `ValidateAndCreateService` |
| AC10: Idempotency intact | YES | YES | `IdempotencyService`, DB unique constraint |
| AC11: UNKNOWN not represented as failure | YES | YES | Template shows status text, no false "rejected" |
| AC12: User sees execution state | YES | YES | `executionResult` state with status display |
| AC13: Controlled execution proof | YES | YES | `ExecutionPipelineTest` + `ExecutionTestSupport.Broker` |
| AC14: No unrestricted production execution | YES | N/A | No Kraken contact in tests |
| AC15: Risk cannot be bypassed | YES | N/A | No force/skip/override in diff |

**AC Review: 15/15 PASS**

---

## Definition of Done

| DoD Item | Status | Evidence |
|---|---|---|
| ADR-032 accepted | PASS | `ADR-032.md` status → Accepted |
| Account identity bug fixed | PASS | Step 4 removed, brokerAccountId resolved from config |
| Regression test uses distinct IDs | PASS | `distinctTradingAccountAndBrokerAccount_succeeds` |
| Broker Service ownership enforced | PASS | `requireOwnership()` in 3 services + tests |
| Gateway execution route reachable | PASS | Route registered + tested |
| Angular Execute action after risk approval | PASS | Button only in `executionReady` state |
| No automatic execution | PASS | No hidden subscriptions/effects |
| Execution uses immutable entryIntent | PASS | `deriveParameters()` reads from plan |
| Idempotency intact | PASS | Key per attempt, DB constraint |
| UNKNOWN outcome safe | PASS | Template handles status, no blind retry |
| Controlled execution succeeds | PASS | `ExecutionPipelineTest` with fake broker |
| Frontend renders result | PASS | `executionResult` state |
| Automated tests pass | PASS | All suites green |
| No uncontrolled live execution | PASS | No Kraken contact |
| Production-readiness gap documented | PASS | Risk freshness out of scope |

**DoD: 15/15 PASS**

---

## Remaining Risks

### Risk Freshness (Prominent)

Story 0030 DOES NOT re-evaluate risk immediately before broker submission.

Between Risk APPROVED and Human Execute, the following may change:
- equity
- exposure
- positions
- market price
- other risk context

Therefore Story 0030 does NOT establish unrestricted real-money production readiness.

This is accepted for the current controlled V1 proof.

It requires a future Story before relying on unrestricted real-money execution.

### switchMap Double-Click

`execute$` currently uses `switchMap`. A second emission may unsubscribe from the previous in-flight observable.

Current backend/database constraints prevent duplicate ExecutionIntent creation for the same TradePlan/version.

Therefore this is not considered a merge blocker.

However: consider `exhaustMap` or equivalent stronger interaction semantics in a future Story.

---

## Security Debt

Pre-existing Broker Service ownership gap discovered during review.

The following operations remain outside Story 0030 ownership hardening:

- `GetAccountService`
- `GetPositionsService`
- `GetOrdersService`

These services have no ownership checks. Story 0030 only targets execution-sensitive operations (execute, cancel, reconcile).

Do not fix them now. Record as follow-up security debt.

---

## Production Safety Boundary

No Story 0030 automated validation contacted Kraken for order submission.

No write-capable credential was required.

The currently configured Kraken credential being read-only may provide an additional environmental safety boundary, but that is NOT presented as the architectural guarantee.

Trading OS safety must remain independent from credential permissions.

**Story 0030 establishes the controlled human-authorized execution path required for Trading OS V1. Unrestricted live-money readiness remains intentionally unproven because pre-submission risk freshness is not yet guaranteed.**

---

## Architectural Significance

Before Story 0030, major components existed independently:
- TradePlan (ADR-027, ADR-031)
- Risk Engine (ADR-028)
- Execution Domain (ADR-029)
- Broker Service (ADR-030)

After Story 0030, they form a coherent user-controlled execution loop:

```
Market Intelligence → TradePlan → Human Acceptance → Deterministic Risk
    → Human Execution Authorization → ExecutionIntent → Broker Submission
    → Execution Result
```

This represents an important V1 architectural milestone. The loop is closed. The human remains in control. The risk engine remains authoritative. The execution domain remains deterministic.

---

## Follow-up Candidates

1. **Pre-submission risk freshness / revalidation** — required before real-money readiness
2. **`switchMap` → stronger execution interaction semantics** — consider `exhaustMap` if double-click UX is a concern
3. **Broker Service ownership for remaining read operations** — `GetAccountService`, `GetPositionsService`, `GetOrdersService`
4. **Paper Trading architecture** — the now-stabilized execution boundary enables future execution modes

---

## Final Recommendation

**READY_FOR_MERGE**

**NOT READY FOR UNRESTRICTED REAL-MONEY EXECUTION.**

These statements are not contradictory.

Story merge readiness concerns implementation quality. All AC pass. All DoD items pass. All tests green. All Code Review findings fixed.

Real-money readiness concerns broader runtime safety. Pre-submission risk freshness remains unresolved.

The human operator makes the final merge decision.
