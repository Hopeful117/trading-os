# Implementation Report — Story 0004

**Status:** 🟢 COMPLETE — All phases implemented, code review corrections applied, tests passing
**Date:** 2026-08-07
**Branch:** `main`

---

## Summary

Story 0004 implementation complete. Human validation is now an explicit Execution Domain lifecycle transition (`CREATED → VALIDATED`) using authoritative data loaded from persistence. No execution parameters, risk references, or account data are accepted from the caller.

---

## Code Review Corrections Applied

| # | Correction | Status |
|---|-----------|--------|
| 1 | Remove unused `TradeDirection` enum from `EntryIntent` | ✅ Removed — direction remains a Trade Plan concept |
| 2 | Remove `now` from `ValidateAndCreateCommand` | ✅ Removed — service uses injected `Clock` exclusively |
| 3 | Extract `ValidationException` to `execution.domain.exception` | ✅ Extracted as `ExecutionValidationException` |
| 4 | Replace FQCN with import in `ExecutionController` | ✅ Import added |
| 5 | Re-check `mock-maker-inline` requirement | ✅ Required — `CreateExecutionIntentService` and `ExecutionLifecycleService` are both `final class` |

---

## Created Files

| File | Location |
|------|----------|
| `EntryIntent.java` | `trading-core/.../shared/domain/model/EntryIntent.java` |
| `ExecutionValidationException.java` | `trading-core/.../execution/domain/exception/ExecutionValidationException.java` |
| `ValidateAndCreateCommand.java` | `trading-core/.../execution/application/command/ValidateAndCreateCommand.java` |
| `ValidateAndCreateService.java` | `trading-core/.../execution/application/service/ValidateAndCreateService.java` |
| `ValidateAndCreateRequest.java` | `trading-core/.../execution/api/dto/ValidateAndCreateRequest.java` |
| `ValidateAndCreateServiceTest.java` | `trading-core/src/test/.../execution/application/service/ValidateAndCreateServiceTest.java` |

## Modified Files

| File | Change |
|------|--------|
| `TradePlanRiskPort.java` | Replaced `BigDecimal entryPrice` with `EntryIntent entryIntent` in `Snapshot` |
| `MarketIntelligenceRiskClient.java` | Added `toEntryIntent()` mapping from API `Entry` → `EntryIntent` |
| `RiskPersistence.java` | Added `evaluationById(UUID)` method, extended `StoredEvaluation` with `status` and `decision` |
| `TradePlanRiskEvaluationService.java` | Updated `plan.entryPrice()` → `plan.entryIntent().price()` |
| `TradePlanRiskEvaluationServiceTest.java` | Updated `plan()` helper to use `EntryIntent` |
| `ExecutionController.java` | Added `POST /executions/validate` endpoint, removed `Instant.now()` call |
| `ExecutionExceptionHandler.java` | Handler now references `ExecutionValidationException` from domain exception package |
| `ExecutionConfiguration.java` | Registered `ValidateAndCreateService` bean |
| `org.mockito.plugins.MockMaker` | Changed to `mock-maker-inline` (required for `final class` mocking) |

## Removed Files

| File | Reason |
|------|--------|
| `CreateExecutionRequest.java` | Orphaned — old caller-driven DTO, replaced by `ValidateAndCreateRequest` |

---

## Architecture Decision

**ADR-032** — "Represent Trade Plan Entry Intent Explicitly"

Entry intent is part of the immutable Trade Plan. The Execution Domain translates it into `ExecutionParameters` without inventing values.

`EntryIntent` is placed in `shared.domain.model` to preserve the architecture boundary: `risk/` must not import from `execution/`.

```java
public record EntryIntent(OrderType orderType, BigDecimal price) {
    public enum OrderType { MARKET, LIMIT, STOP }
}
```

---

## API Endpoint

```
POST /executions/validate
```

**Request:**
```json
{
  "tradePlanId": "uuid",
  "tradePlanVersion": 3,
  "evaluationId": "uuid",
  "brokerAccountId": "uuid",
  "expiresAt": "2026-08-07T13:00:00Z"
}
```

**Headers:** `Idempotency-Key: string`

**Response:** `201 Created` with `ExecutionDto`

**Errors:** `403`, `404`, `409`, `422` with `ProblemDetail` body containing machine-readable `code`.

---

## ValidateAndCreateService Flow

1. Load evaluation via `RiskPersistence.evaluationById(evaluationId)`
2. Verify evaluation status is `COMPLETED`
3. Verify decision is `APPROVED` or `APPROVED_WITH_WARNINGS`
4. Verify evaluation belongs to `brokerAccountId`
5. Load TradePlan via `TradePlanRiskPort.load(tradePlanId, tradePlanVersion)`
6. Verify TradePlan status is `ACCEPTED`
7. Verify version correspondence (plan ↔ evaluation)
8. Verify identity correspondence (plan ↔ evaluation)
9. Verify `plan.ownerId == initiatorId`
10. Verify `evaluation.accountId == plan.tradingAccountId`
11. Verify BrokerAccount ownership via `BrokerAccountRepository.findByIdAndOwnerId()`
12. Derive `ExecutionParameters` from `EntryIntent` + plan data
13. Create `ExecutionIntent` via `CreateExecutionIntentService` (idempotent)
14. Transition to `VALIDATED` via `ExecutionLifecycleService.validate()` using injected `Clock`

---

## Test Results

```bash
mvn test
# Tests run: 83, Failures: 1, Errors: 0, Skipped: 0
# Failure: RiskAcknowledgmentOutboxPersistenceTest (pre-existing, DB-related)
```

### New Tests — ValidateAndCreateServiceTest (9/9 ✅)

| Test | Scenario |
|------|----------|
| `happyPath_createsValidatedIntent` | Full flow with MARKET entry intent |
| `evaluationNotFound_throws404` | Evaluation ID not in persistence |
| `evaluationNotCompleted_throws422` | Evaluation status is PENDING |
| `decisionNotAuthorized_throws422` | Decision is REJECTED |
| `evaluationAccountMismatch_throws409` | Evaluation account ≠ request account |
| `brokerAccountForbidden_throws403` | Broker account not owned by initiator |
| `limitEntryIntent_requiresPrice` | LIMIT without price rejected |
| `limitEntryIntent_withPrice_succeeds` | LIMIT with positive price accepted |
| `stopEntryIntent_rejectedDuringDerivation` | STOP order type unsupported |

### Pre-existing Tests — All passing

| Test Suite | Tests |
|-----------|-------|
| ExecutionIntentTest | 3 ✅ |
| ExecutionLifecycleServiceTest | 1 ✅ |
| TradePlanRiskEvaluationServiceTest | 18 ✅ |
| RiskEvaluationArchitectureTest | 1 ✅ |
| All other suites | 52 ✅ |

---

## What Was Done vs. Original Plan

| Phase | Status | Notes |
|-------|--------|-------|
| 1. `RiskPersistence.evaluationById()` | ✅ | Extended `StoredEvaluation` with `status`, `decision` |
| 2. `ValidateAndCreateService` | ✅ | 14-step validation, idempotent creation, injected Clock |
| 3. `EntryIntent` value object | ✅ | Shared domain, ADR-032 compliant, no TradeDirection |
| 4. REST endpoint | ✅ | `POST /executions/validate` |
| 5. Remove old endpoint | ✅ | `CreateExecutionRequest` deleted (orphaned) |
| 6. Spring wiring | ✅ | Bean registered in `ExecutionConfiguration` |
| 7. Exception handling | ✅ | `ExecutionValidationException` in domain exception package |
| 8. Unit tests | ✅ | 9 tests, all passing |
