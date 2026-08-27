# Implementation Report — Story 0030

**Date**: 2026-08-27
**Branch**: `main` (working tree changes, not yet committed)

## Summary

Connected risk decision to human-controlled execution. Fixed the Account-ID comparison bug, resolved broker account from `AccountRiskConfiguration` instead of trusting frontend input, added principal-based broker ownership enforcement, routed execution through Gateway, and exposed the execution flow in the Angular PlanPage with a human-initiated Execute Trade button.

## Changes

### Trading Core — Account-ID Bug Fix + Broker Account Resolution
- `ValidateAndCreateService.java`: Removed step 4 (comparison of `evaluation.accountId()` against `command.brokerAccountId()`). These are different domain identities. Added step 11: resolves `brokerAccountId` from `RiskPersistence.AccountConfiguration(plan.tradingAccountId())` instead of trusting frontend input. Added `java.util.UUID` import.
- `ValidateAndCreateServiceTest.java`: Updated helpers to use distinct `tradingAccountId`. Added `distinctTradingAccountAndBrokerAccount_succeeds` regression test. All tests mock `riskPersistence.configuration()` for the new resolution step.

### Broker Service — Principal-Based Ownership
- `BrokerOperationServices.java`: `ExecuteOrderService`, `CancelOrderService`, `ReconcileExecutionService` now accept `BrokerConnectionRepository` and enforce ownership via `requireOwnership()` before execution.
- `ExecutionController.java`: Added `@AuthenticationPrincipal BrokerPrincipal principal` to `execute()`, `reconcile()`, and `cancel()` methods.

### Gateway — Execution Route
- `GatewayRouteConfig.java`: Added route `"executions"`: `/api/v1/executions/**` → `lb://trading-core`.

### Angular — Execution UI
- `execution.model.ts` (new): `ExecutionStatus` type, `ExecutionDto`, `ValidateExecutionRequest` interfaces.
- `execution.service.ts` (new): `ExecutionService` with `validate()`, `execute()`, `getExecution()` methods.
- `plan-page.ts`: Extended `PlanView` with `executionReady`, `executionSubmitting`, `executionResult` states. `evaluateRisk$` now branches: approved → `executionReady`, rejected → `riskDecision`. `execute$` emits `executionSubmitting` before HTTP calls.
- `plan-page.html`: Added `execution-submitting-state`, `execution-ready-state` (with Execute Trade button), `execution-result-state`.
- `plan-page.spec.ts`: Added `ExecutionService` mock. Tests for execution-ready state, execute flow, and REJECTED guard.

### ADR-032
- `ADR-032.md`: Status updated from `Proposed` to `Accepted`.

### Code Review Fixes
- Resolved BLOCKER: broker account now resolved from `AccountRiskConfiguration` instead of frontend input
- Fixed HIGH: `executionSubmitting` state now emitted before HTTP calls
- Fixed HIGH: `evaluateRisk$` branches on `decision.approved` for correct state routing
- Added `BrokerOwnershipTest` (3 tests) for ownership enforcement
- Added `GatewayExecutionRouteTest` (4 tests) for execution route
- Added Angular tests for execute flow and REJECTED guard

## Quality Gates

| Suite | Before | After | Δ |
|---|---|---|---|
| Trading Core | 247 | **249** | +2 |
| Broker Service | 102 | **105** | +3 |
| Gateway | 16 | **20** | +4 |
| Market Data | 92 | **92** | — |
| Angular | 236 | **242** | +6 |

- [x] `mvn test` (Trading Core): 249/249
- [x] `mvn test` (Broker Service): 105/105
- [x] `mvn test` (Gateway): 20/20
- [x] `mvn test` (Market Data): 92/92
- [x] `npm test` (Angular): 242/242
- [x] `ng build`: success (pre-existing budget warning)
- [x] `git diff --check`: clean

## Files Changed (9 modified, 4 new)

```
broker-service/.../controller/ExecutionController.java         | +8 -1
broker-service/.../service/BrokerOperationServices.java        | +10 -3
broker-service/.../broker/BrokerOwnershipTest.java             | NEW
docs/architecture/adr/ADR-032.md                               | +1 -1
gateway/.../config/GatewayRouteConfig.java                     | +4
gateway/.../GatewayExecutionRouteTest.java                     | NEW
trading-core/.../service/ValidateAndCreateService.java         | +22 -6
trading-core/.../service/ValidateAndCreateServiceTest.java     | +65 -15
trading-os-web/.../plan-page/plan-page.html                    | +119
trading-os-web/.../plan-page/plan-page.spec.ts                 | +42 -3
trading-os-web/.../plan-page/plan-page.ts                      | +65 -15
trading-os-web/.../models/execution.model.ts                   | NEW
trading-os-web/.../services/execution.service.ts               | NEW
```

## Scope Compliance

Implemented all 6 in-scope items from Story 0030:
1. Account-ID comparison bug fix
2. Broker Service ownership enforcement (principal-based)
3. Gateway execution route
4. Angular execution UI
5. ADR-032 acceptance
6. Documentation artifacts

Controlled execution proof (Correction B) — already satisfied by pre-existing tests:
- `ExecutionPipelineTest` exercises the full pipeline with `ExecutionTestSupport.Broker` (fake `BrokerExecutionPort`).
- `ValidateAndCreateServiceTest` proves intent creation requires COMPLETED + APPROVED risk evaluation.
- `TradePlanRiskEvaluationServiceTest` tests the real risk engine with mocked `RequiredMarginPort`.
- No Kraken contact. No write-capable credentials required.

Deferred 2 out-of-scope items as agreed:
- Configurable risk profiles per trading account
- New `TriggerType` and `RiskLimitType` enum values
