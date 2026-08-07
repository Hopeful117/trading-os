# Engineering Report

## Story

Story 0004 — Validate Authorized Trade Plans before Execution.

## Final Status

Complete.

Human validation of an authorized Trade Plan is now an explicit Execution Domain lifecycle transition (`CREATED → VALIDATED`) using authoritative data loaded from persistence. No execution parameters, risk references, or account data are accepted from the caller — all data is loaded from persistence and verified. The resulting Execution Intent is traceable, idempotent, and derived exclusively from authoritative Trade Plan and Risk Evaluation data.

The Code Review is approved with 5 corrections applied. No commit, push, merge, reset, or discard operation was performed.

## Delivered Architecture

```text
Authenticated user submits validation request
    -> trade plan ID, evaluation ID, broker account ID, expiry
    -> ValidateAndCreateService loads authoritative RiskEvaluation
    -> verifies status=COMPLETED, decision=APPROVED/APPROVED_WITH_WARNINGS
    -> loads exact Trade Plan version via TradePlanRiskPort
    -> verifies state=ACCEPTED, version correspondence, identity match
    -> verifies account ownership (user -> broker account -> trading account)
    -> derives ExecutionParameters from EntryIntent (never from caller)
    -> creates ExecutionIntent (idempotent) via CreateExecutionIntentService
    -> transitions lifecycle CREATED -> VALIDATED via ExecutionLifecycleService
    -> returns ExecutionIntent (no broker order placed)
```

The security gap is closed: `ExecutionController.create()` previously accepted ALL data from the caller. Now only identifiers and expiry are accepted — execution parameters are derived from authoritative data.

## Architecture Decisions

### ADR-032 — Represent Trade Plan Entry Intent Explicitly

Entry intent is part of the immutable Trade Plan. The Execution Domain translates it into `ExecutionParameters` without inventing values.

`EntryIntent` is placed in `shared.domain.model` to preserve the architecture boundary: `risk/` must not import from `execution/`.

```java
public record EntryIntent(OrderType orderType, BigDecimal price) {
    public enum OrderType { MARKET, LIMIT, STOP }
}
```

- `MARKET` → `ExecutionParameters.OrderType.MARKET`
- `LIMIT` → `ExecutionParameters.OrderType.LIMIT` (requires non-null price)
- `STOP` → rejected with `UNSUPPORTED_ENTRY_INTENT` (not yet supported)

### Lifecycle Transition

Human validation is a lifecycle transition, not a new domain concept:

```
ExecutionIntent States: CREATED → VALIDATED → ACKNOWLEDGED → SUBMITTED → ...
```

The transition is irreversible. No mutation of validation decisions — they are read-only.

## Files

### Created (7)

| File | Purpose |
|------|---------|
| `shared/domain/model/EntryIntent.java` | Trade Plan entry intent: order type + price |
| `execution/domain/exception/ExecutionValidationException.java` | Validation failure with code + status |
| `execution/application/command/ValidateAndCreateCommand.java` | Input: IDs, version, idempotency, expiry |
| `execution/application/service/ValidateAndCreateService.java` | 14-step validation + idempotent creation |
| `execution/api/dto/ValidateAndCreateRequest.java` | REST request DTO |
| `execution/application/service/ValidateAndCreateServiceTest.java` | 9 unit tests |
| `docs/.../engineering-report.md` | This report |

### Modified (9)

| File | Change |
|------|--------|
| `TradePlanRiskPort.java` | `Snapshot.entryIntent` replaces `entryPrice` |
| `MarketIntelligenceRiskClient.java` | Added `toEntryIntent()` mapping |
| `RiskPersistence.java` | Added `evaluationById(UUID)`, extended `StoredEvaluation` with `status`/`decision` |
| `TradePlanRiskEvaluationService.java` | `plan.entryPrice()` → `plan.entryIntent().price()` |
| `TradePlanRiskEvaluationServiceTest.java` | Updated helper to use `EntryIntent` |
| `ExecutionController.java` | Added `POST /executions/validate` |
| `ExecutionExceptionHandler.java` | Handler for `ExecutionValidationException` |
| `ExecutionConfiguration.java` | Registered `ValidateAndCreateService` bean |
| `MockMaker` | Changed to `mock-maker-inline` (required for `final class` mocking) |

### Removed (1)

| File | Reason |
|------|--------|
| `CreateExecutionRequest.java` | Orphaned — old caller-driven DTO |

## Code Review Corrections

All 5 corrections from the Code Review have been applied:

| # | Correction | Applied |
|---|-----------|---------|
| 1 | Remove unused `TradeDirection` enum from `EntryIntent` | ✅ Removed |
| 2 | Remove `now` from `ValidateAndCreateCommand` | ✅ Removed — service uses injected `Clock` |
| 3 | Extract `ValidationException` to `execution.domain.exception` | ✅ Extracted as `ExecutionValidationException` |
| 4 | Replace FQCN with import in `ExecutionController` | ✅ Import added |
| 5 | Re-check `mock-maker-inline` requirement | ✅ Required — `CreateExecutionIntentService` and `ExecutionLifecycleService` are both `final class` |

## API Endpoint

```
POST /executions/validate
```

**Request:** `{ tradePlanId, tradePlanVersion, evaluationId, brokerAccountId, expiresAt }`
**Headers:** `Idempotency-Key: string`
**Response:** `201 Created` with `ExecutionDto`
**Errors:** `403`, `404`, `409`, `422` with `ProblemDetail` body containing machine-readable `code`

## ValidateAndCreateService — 14-Step Validation

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

## Validation Summary

```bash
mvn test
# Tests run: 83, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS
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

| Test Suite | Tests | Status |
|-----------|-------|--------|
| ExecutionIntentTest | 3 | ✅ |
| ExecutionLifecycleServiceTest | 1 | ✅ |
| TradePlanRiskEvaluationServiceTest | 18 | ✅ |
| RiskEvaluationArchitectureTest | 1 | ✅ |
| ExecutionArchitectureTest | 1 | ✅ |
| BrokerExecutionContractTest | 2 | ✅ |
| ExecutionPipelineTest | 3 | ✅ |
| ExecutionDomainTest | 3 | ✅ |
| All other suites | 49 | ✅ |
| **Total** | **83** | **✅** |

## Artifact History

- `story.md` — authoritative Story
- `repository-analysis.md` — repository and architecture analysis
- `implementation-plan.md` — approved implementation plan
- `implementation-report.md` — implementation record with corrections
- `code-review.md` — approved Code Review with all corrections applied
- `engineering-report.md` — this final consolidated report

## Final Recommendation

Story 0004 is technically complete and approved through the Engineering Story workflow. All acceptance criteria are satisfied. The worktree should remain uncommitted until the engineer has completed any desired final IDE inspection and chooses the repository's normal commit and delivery process.
