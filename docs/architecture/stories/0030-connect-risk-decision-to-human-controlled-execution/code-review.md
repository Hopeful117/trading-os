# Code Review — Story 0030

## Review Scope

Story 0030 — Connect Risk Decision to Human-Controlled Execution. Code review of all changes in working tree against Story 0030 acceptance criteria, ADR-001/014/029/030/031/032, and security invariants.

## Review Inputs

- Story: `docs/architecture/stories/0030-connect-risk-decision-to-human-controlled-execution/story.md`
- Repository Analysis: `docs/architecture/stories/0030-connect-risk-decision-to-human-controlled-execution/repository-analysis.md`
- Implementation Plan: `docs/architecture/stories/0030-connect-risk-decision-to-human-controlled-execution/implementation-plan.md`
- Implementation Report: `docs/architecture/stories/0030-connect-risk-decision-to-human-controlled-execution/implementation-report.md`
- ADRs: 001, 014, 029, 030, 031, 032
- Git diff: 9 modified files, 4 new files

## Summary

Review identified 1 BLOCKER, 1 HIGH, 2 MEDIUM, 1 LOW findings. All fixed. The BLOCKER was a domain identity confusion where the frontend sent `plan.tradingAccountId` as `brokerAccountId` — the exact type of bug Story 0030 was meant to fix. The fix resolves the broker account from `AccountRiskConfiguration` instead of trusting frontend input.

## Findings

### BLOCKER

**B-01: Frontend sends Trading Account UUID as Broker Account ID**
- Area: Angular `plan-page.ts:119`
- Evidence: `brokerAccountId: plan.tradingAccountId` — `TradePlanResponse` has no `brokerAccountId` field
- Impact: Backend `ValidateAndCreateService.step 11` would fail looking up a Broker Account with a Trading Account UUID. Execution unreachable.
- Fix Applied: YES — `ValidateAndCreateService` now resolves `brokerAccountId` from `RiskPersistence.AccountConfiguration(plan.tradingAccountId())`. Frontend value is ignored.
- Test: `ValidateAndCreateServiceTest` — `distinctTradingAccountAndBrokerAccount_succeeds` proves resolution works with distinct IDs.

### HIGH

**H-01: `executionSubmitting` state never emitted**
- Area: Angular `plan-page.ts` — `execute$` observable
- Evidence: `execute$` used `switchMap` directly into HTTP calls without emitting `executionSubmitting`. `busy$` checked for it but it was never set.
- Impact: User can click Execute multiple times rapidly. No visual feedback during submission.
- Fix Applied: YES — `execute$` now emits `executionSubmitting` via `of<PlanView>({ status: 'executionSubmitting' }).pipe(switchMap(...))` before HTTP calls.
- Test: Angular `plan-page.spec.ts` — `clicking execute triggers execution flow and shows result` verifies state transitions.

### MEDIUM

**M-01: Broker Service ownership has no tests**
- Area: `BrokerOperationServices` — `ExecuteOrderService`, `CancelOrderService`, `ReconcileExecutionService`
- Evidence: 102 Broker Service tests, zero for ownership enforcement on execution operations. AC5 requires tested ownership.
- Impact: Security boundary untested. Wrong owner could execute/cancel/reconcile.
- Fix Applied: YES — New `BrokerOwnershipTest` with3 tests: `execute_rejectsWrongOwner`, `cancel_rejectsWrongOwner`, `reconcile_rejectsWrongOwner`. All verify `BrokerAuthorizationException` before provider resolution.
- Test: `BrokerOwnershipTest` — 3/3 pass.

**M-02: Gateway execution route has no test**
- Area: `GatewayRouteConfig` — new `"executions"` route
- Evidence: 16 Gateway tests, none for the new execution route.
- Impact: Route misconfiguration could expose wrong service or miss authentication.
- Fix Applied: YES — New `GatewayExecutionRouteTest` with4 tests: route forwards to trading-core, matches validate/execute endpoints, does not catch unrelated paths, all routes registered.
- Test: `GatewayExecutionRouteTest` — 4/4 pass.

### LOW

**L-01: Angular tests missing execute flow coverage**
- Area: `plan-page.spec.ts`
- Evidence: Only 1 test for execution-ready state. No test for: clicking Execute, result rendering, REJECTED decision not showing Execute.
- Fix Applied: YES — Added2 tests: `clicking execute triggers execution flow and shows result`, `does not show execute button for REJECTED risk decision`.
- Test: Angular — 242/242 pass.

### INFO

**I-01: `switchMap` in `execute$` cancels in-flight on double-click**
- Area: Angular `plan-page.ts`
- Evidence: `switchMap` unsubscribes from previous inner observable on new emission. Second click cancels first HTTP request.
- Impact: First request may have created ExecutionIntent on backend. Second request creates new intent with different idempotency key. Database unique constraint on `(trade_plan_id, trade_plan_version)` prevents duplicate intents. Harmless but suboptimal.
- Recommendation: Consider `exhaustMap` or guard for future. Not a blocker — DB constraint protects.

**I-02: Broker Service `GetAccountService`, `GetPositionsService`, `GetOrdersService` still lack ownership**
- Area: `BrokerOperationServices`
- Evidence: These services have no ownership checks. Story 0030 only targets execution-sensitive operations.
- Impact: Other services remain accessible to any authenticated user with a broker account UUID. Pre-existing debt.
- Recommendation: Follow-up security story.

## Account Identity

PASS — `evaluation.accountId()` is no longer compared to `brokerAccountId`. Step 10 correctly validates `evaluation.accountId() == plan.tradingAccountId()`. Step 11 resolves `brokerAccountId` from `AccountRiskConfiguration`. Tests use distinct UUIDs.

## Authentication & Ownership

PASS — Broker Service `ExecutionController` uses `@AuthenticationPrincipal BrokerPrincipal principal`. `principal.userId()` passed to all three services. `requireOwnership()` checks `findByBrokerAccountIdAndOwnerId` before provider resolution. No HTTP body/query/path parameter can supply `ownerId`.

## Gateway

PASS — Route `"executions"`: `/api/v1/executions/**` → `lb://trading-core`. Correct path, correct target. Tested by `GatewayExecutionRouteTest`. No conflict with existing routes.

## Human Authority

PASS — `evaluateRisk$` emits `executionReady` only when `decision.approved === true`. Rejected decisions emit `riskDecision`. Execute button only in `executionReady` state. No hidden subscriptions trigger execution. Explicit user click required.

## Angular Execution Flow

PASS — `execute$` chains: `executeSubject` → `executionSubmitting` → `validate()` → `execute()` → `executionResult`. Idempotency key generated per click. `ExecutionDto.id` from validate used for execute call. Error states handled.

## Idempotency

PASS — `crypto.randomUUID()` generates unique idempotency key per execution attempt. Backend `IdempotencyService.ensureUnique()` rejects duplicate keys. Database unique constraint on `idempotency_key` column. TradePlan-level uniqueness enforced by `uk_execution_intent_trade_plan` constraint on `(trade_plan_id, trade_plan_version)`.

## Risk Authority

PASS — `ValidateAndCreateService` loads authoritative `RiskEvaluation` from persistence. Step 2 verifies `COMPLETED`. Step 3 verifies `APPROVED` or `APPROVED_WITH_WARNINGS`. No frontend state can substitute for backend risk validation.

## Entry Intent

PASS — `deriveParameters()` reads `plan.entryIntent()` and derives `ExecutionParameters` without invention. STOP remains unsupported. Story 0030 did not modify entry intent logic.

## Execution Result Semantics

PASS — Angular `executionResult` state displays `ExecutionDto.status`. Template uses `[class.approved]` for `COMPLETED` and `[class.rejected]` for `FAILED`. `SUBMISSION_OUTCOME_UNKNOWN` shown as status text. No false "rejected" styling for UNKNOWN.

## Controlled Execution Proof

PASS — `ExecutionPipelineTest` exercises full pipeline with `ExecutionTestSupport.Broker` (fake `BrokerExecutionPort`). Covers COMPLETED, SUBMISSION_OUTCOME_UNKNOWN, recovery. `ValidateAndCreateServiceTest` proves intent creation requires `COMPLETED + APPROVED`. `TradePlanRiskEvaluationServiceTest` tests real risk engine with mocked `RequiredMarginPort`. No Kraken contact.

## Test Coverage

| Suite | Before | After | Δ | New Tests |
|---|---|---|---|---|
| Trading Core | 247 | **249** | +2 | `distinctTradingAccountAndBrokerAccount_succeeds`, `tradingAccountMismatch_throws409` (updated) |
| Broker Service | 102 | **105** | +3 | `BrokerOwnershipTest` (3 ownership tests) |
| Gateway | 16 | **20** | +4 | `GatewayExecutionRouteTest` (4 route tests) |
| Angular | 236 | **242** | +6 | execution-ready state, execute flow, REJECTED guard, execution-result state |

## Acceptance Criteria Review

| AC | Implemented | Tested | Evidence | Finding |
|---|---|---|---|---|
| AC1: Authenticated user can explicitly Execute | YES | YES | `plan-page.spec.ts` — execute button visible after APPROVED | — |
| AC2: Trading/Broker Account IDs distinct | YES | YES | `ValidateAndCreateServiceTest` — distinct UUIDs | — |
| AC3: Tests use distinct IDs | YES | YES | `ValidateAndCreateServiceTest` helpers use `tradingAccountId` | — |
| AC4: User must own TradePlan and BrokerAccount | YES | YES | Steps 9, 11 in `ValidateAndCreateService` | — |
| AC5: Broker Service ownership enforced | YES | YES | `BrokerOwnershipTest` — 3 tests | Fixed M-01 |
| AC6: Acceptance alone never submits | YES | YES | `evaluateRisk$` separate from `execute$` | — |
| AC7: Risk approval alone never submits | YES | YES | `executionReady` shows button, doesn't auto-execute | — |
| AC8: Only explicit post-risk action initiates | YES | YES | `executeSubject.next()` only on button click | — |
| AC9: Parameters from TradePlan entryIntent | YES | YES | `deriveParameters()` in `ValidateAndCreateService` | — |
| AC10: Idempotency intact | YES | YES | `IdempotencyService`, DB unique constraint | — |
| AC11: UNKNOWN not represented as failure | YES | YES | Template shows status text, no false "rejected" | — |
| AC12: User sees execution state | YES | YES | `executionResult` state with status display | — |
| AC13: Controlled execution proof | YES | YES | `ExecutionPipelineTest` + `ExecutionTestSupport.Broker` | — |
| AC14: No unrestricted production execution | YES | N/A | No Kraken contact in tests | — |
| AC15: Risk cannot be bypassed | YES | N/A | No force/skip/override in diff | — |

**AC Review: 15/15 PASS**

## Definition of Done Review

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

## Changes Applied During Review

1. **BLOCKER fix**: `ValidateAndCreateService` resolves `brokerAccountId` from `AccountRiskConfiguration` instead of trusting frontend input
2. **HIGH fix**: `plan-page.ts` emits `executionSubmitting` before HTTP calls
3. **HIGH fix**: `plan-page.ts` uses `decision.approved` to choose between `executionReady` and `riskDecision`
4. **MEDIUM fix**: New `BrokerOwnershipTest` (3 tests) for ownership enforcement
5. **MEDIUM fix**: New `GatewayExecutionRouteTest` (4 tests) for execution route
6. **LOW fix**: New Angular tests for execute flow and REJECTED guard
7. **Compilation fix**: Added `java.util.UUID` import to `ValidateAndCreateService`
8. **Compilation fix**: Corrected `AccountConfiguration` constructor (5 fields, not 6)
9. **Compilation fix**: Corrected enum references in `BrokerOwnershipTest`

## Quality Gates

```
Trading Core:     249/249 pass
Broker Service:   105/105 pass  (+3)
Gateway:           20/20 pass   (+4)
Angular:          242/242 pass  (+6)
Angular build:    OK (pre-existing budget warning)
git diff --check: clean
```

## Remaining Risks

1. **Risk freshness**: Between risk approval and Execute, equity/exposure/market price may change. Accepted for controlled proof. Requires future Story.
2. **`switchMap` double-click**: Second click cancels first in-flight request. DB constraint prevents duplicate intents. Consider `exhaustMap` in future.
3. **Other broker operations lack ownership**: `GetAccountService`, `GetPositionsService`, `GetOrdersService` — pre-existing debt, not Story 0030 scope.

## Recommendation

**APPROVED_WITH_FIXES** — All BLOCKER/HIGH/MEDIUM findings fixed. All tests green. All AC pass. All DoD items pass. Ready for human review.
