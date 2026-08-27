# Implementation Plan — Story 0030

## Design

```
[Step 1] ADR-032 status → Accepted
[Step 2] ValidateAndCreateService: remove step 4, fix tests
[Step 3] Broker Service: add ownerId to ExecutionRequest, enforce ownership
[Step 4] Trading Core: propagate ownerId to BrokerService via Feign
[Step 5] Gateway: add execution route
[Step 6] Angular: ExecutionService + PlanPage execution states + Execute button
[Step 7] Test margin adapter for sandbox proof
[Step 8] Full targeted regression
```

## Steps

### Step 1 — Accept ADR-032

**Objective:** Formalize the existing ADR-032 decision as Accepted.

**Current behavior:** `docs/architecture/adr/ADR-032.md` line 3 reads `**Status:** Proposed`.

**Required change:** Update line 3 to `**Status:** Accepted`.

**Affected modules:** None (documentation only).

**Files:**
- `docs/architecture/adr/ADR-032.md:3`

**Tests:** None.

**Acceptance Criteria:** Definition of Done item "ADR-032 has been accepted".

**Dependencies:** None.

**Risks:** None.

---

### Step 2 — Fix Account-ID Bug

**Objective:** Remove the invalid TradingAccount-vs-BrokerAccount comparison and fix the test that encodes the wrong behavior.

**Current behavior:** `ValidateAndCreateService.java:74` compares `evaluation.accountId()` (TradingAccount UUID) with `command.brokerAccountId()` (BrokerAccount UUID). Always fails. `ValidateAndCreateServiceTest` sets both IDs to the same value to hide the bug.

**Required change:**

1. **`ValidateAndCreateService.java`:** Remove lines 74-77 (the step 4 check). Step 10 (`evaluation.accountId().equals(plan.tradingAccountId())`) already correctly validates TradingAccount consistency. Step 11 (`brokerAccounts.findByIdAndOwnerId(brokerAccountId, initiatorId)`) already correctly validates BrokerAccount ownership.

2. **`ValidateAndCreateServiceTest.java`:** 
   - Add a new `UUID tradingAccountId = UUID.randomUUID()` field distinct from `brokerAccountId`.
   - In `storedEvaluation()`: set `accountId` to `tradingAccountId` (not `brokerAccountId`).
   - In `marketPlan()`: set `tradingAccountId` to `tradingAccountId` (not `brokerAccountId`).
   - Add a regression test proving that execution succeeds when `tradingAccountId != brokerAccountId` but all ownership/consistency rules are satisfied.
   - Remove the "evaluation account mismatch" test (step 4 no longer exists). The existing step 10 test (trading account mismatch) already covers the correct validation.

**Affected modules:** `trading-core`

**Files:**
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/ValidateAndCreateService.java:74-77`
- `trading-core/src/test/java/com/hope/trading/trading_core/execution/application/service/ValidateAndCreateServiceTest.java`

**Domain/security constraints:** TradingAccount and BrokerAccount must remain distinct identities. Step 10 validates the correct relationship (evaluation.accountId == plan.tradingAccountId). Step 11 validates BrokerAccount ownership.

**Tests:**
- Modified: `ValidateAndCreateServiceTest` — distinct IDs, regression test for account-ID mismatch
- Verify existing step 10 test still passes (trading account mismatch)

**Acceptance Criteria:** AC2 (distinct IDs, invalid comparison removed), AC3 (test corrected with distinct IDs).

**Dependencies:** None.

**Validation commands:**
```bash
cd trading-core && mvn test -pl . -Dtest=ValidateAndCreateServiceTest
```

**Risks:** Low. Removing a buggy check that always fails. Correct checks at steps 10-11 remain.

---

### Step 3 — Broker Service Ownership Enforcement (Correction A Applied)

**Objective:** Add defense-in-depth ownership verification to Broker Service execution operations using the authenticated JWT principal.

**Correction A:** Do NOT add `ownerId` to HTTP request DTOs. Instead, extract `userId` from `BrokerPrincipal` (derived from the JWT already propagated by Feign's `Authorization` header). The HTTP caller must not be the authority for user identity.

**Verified JWT propagation path:**
```
Trading Core (JWT in SecurityContext)
  → FeignAuthorizationConfiguration (propagates Authorization header)
  → BrokerJwtAuthenticationFilter (parses JWT, creates BrokerPrincipal)
  → BrokerPrincipal.userId() (from JWT subject claim)
```

**Current behavior:** `ExecuteOrderService`, `CancelOrderService`, `ReconcileExecutionService` resolve provider by `brokerAccountId` only. No owner verification.

**Required change:**

1. **`BrokerOperationServices.ExecuteOrderService`:** Inject `BrokerConnectionRepository`. Add `UUID ownerId` parameter. Add ownership check before resolving provider:
   ```java
   public ExecutionResult execute(ExecutionRequest r, UUID ownerId) {
       requireOwnership(r.brokerAccountId(), ownerId);
       return metrics.record("execution", () ->
           require(providers.resolve(r.brokerAccountId()), ExecutionCapability.class).execute(r));
   }
   ```

2. **`BrokerOperationServices.CancelOrderService`:** Add `UUID ownerId` parameter and ownership check.

3. **`BrokerOperationServices.ReconcileExecutionService`:** Add `UUID ownerId` parameter and ownership check.

4. **New shared helper in `BrokerOperationServices`:**
   ```java
   private static void requireOwnership(BrokerConnectionRepository connections,
           UUID brokerAccountId, UUID ownerId) {
       if (connections.findByBrokerAccountIdAndOwnerId(brokerAccountId, ownerId).isEmpty())
           throw new BrokerAuthorizationException("Broker account is not accessible");
   }
   ```

5. **`ExecutionController` (Broker Service):** Add `@AuthenticationPrincipal BrokerPrincipal principal` to `execute()`, `reconcile()`, and `cancel()` methods. Extract `principal.userId()` and pass to services. No `ownerId` in request body.

6. **New helper method in `BrokerOperationServices`:**
   ```java
   private void requireOwnership(UUID brokerAccountId, UUID ownerId) {
       if (connections.findByBrokerAccountIdAndOwnerId(brokerAccountId, ownerId).isEmpty())
           throw new BrokerAuthorizationException("Broker account is not accessible");
   }
   ```

7. **`ExecutionController` (Broker Service):** Add `@AuthenticationPrincipal BrokerPrincipal principal` to `execute()`, `reconcile()`, and `cancel()` methods. Extract `principal.userId()` and pass to services.

**Affected modules:** `broker-service`

**Files:**
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/domain/model/BrokerModels.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/api/dto/BrokerApiDtos.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/application/service/BrokerOperationServices.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/api/controller/ExecutionController.java`

**Domain/security constraints:** Defense in depth — even though Trading Core validates ownership, Broker Service must also validate. The `brokerAccountId` alone is never sufficient authorization.

**Tests:**
- New: `BrokerServiceOwnershipTest` — prove ExecuteOrderService throws `BrokerAuthorizationException` when ownerId doesn't match
- New: Cancel and reconcile ownership tests
- Modified: `BrokerApiContractTest` — update contract for new `ownerId` field

**Acceptance Criteria:** AC4 (user must own TradePlan and BrokerAccount), AC5 (Broker Service verifies ownership).

**Dependencies:** None (can be done in parallel with Step 2).

**Validation commands:**
```bash
cd broker-service && mvn test
```

**Risks:** Low. Follows existing `GetRiskSnapshotService` pattern. `BrokerConnectionRepository.findByBrokerAccountIdAndOwnerId` already exists.

---

### Step 4 — Actor Identity Propagation (No HTTP DTO Change Required)

**Objective:** Verify that the existing JWT propagation mechanism provides the authenticated identity Broker Service needs for ownership verification.

**Correction A applied:** No `ownerId` is added to `BrokerRequest` or `BrokerExecutionClient`. The `Authorization` header is already propagated by `FeignAuthorizationConfiguration`. Broker Service's `BrokerJwtAuthenticationFilter` extracts `userId` from the JWT into `BrokerPrincipal`. The `ExecutionController` uses `@AuthenticationPrincipal BrokerPrincipal principal` to obtain the trusted identity.

**No changes required in Trading Core for actor propagation.** The existing Feign interceptor already propagates the `Authorization` header. The `ExecutionController` in Broker Service extracts identity from `BrokerPrincipal`.

**Verification:** Add a focused test proving the JWT propagation path works for execution calls.

**Affected modules:** `broker-service` (test only)

**Tests:**
- New: `BrokerExecutionAuthenticationTest` — prove that Broker Service execution controller extracts userId from JWT and passes to ownership check

**Acceptance Criteria:** AC4 (ownership), AC5 (defense in depth), AC8 (explicit auth required).

**Dependencies:** Step 3 (Broker Service ownership enforcement).

**Validation commands:**
```bash
cd broker-service && mvn test
```

**Risks:** None. Existing mechanism, no changes to HTTP contracts.

---

### Step 5 — Gateway Execution Route

**Objective:** Expose Trading Core execution API to Angular through Gateway.

**Current behavior:** No `/api/v1/executions/**` route in Gateway route table.

**Required change:**

1. **`GatewayRouteConfig.java`:** Add execution route.
   ```java
   route("executions", "/api/v1/executions/**", "lb://trading-core")
   ```

**Affected modules:** `gateway`

**Files:**
- `gateway/src/main/java/com/hope/trading/gateway/config/GatewayRouteConfig.java`

**Tests:**
- New: `GatewayExecutionRouteTest` — verify execution route exists and forwards to trading-core

**Acceptance Criteria:** DoD item "Required Trading Core execution route is reachable through Gateway".

**Dependencies:** None.

**Validation commands:**
```bash
cd gateway && mvn test
```

**Risks:** None. Follows existing convention.

---

### Step 6 — Angular Execution UI

**Objective:** Add explicit post-risk Execute action and execution result display to Angular.

**Current behavior:** PlanPage terminates at `riskDecision` state. No execution service, no execute button, no execution result display.

**Required change:**

1. **`execution.service.ts` (new):** Angular service with `validate()` and `execute()` methods.
   ```typescript
   validate(request: ValidateExecutionRequest): Observable<ExecutionDto>
   execute(executionId: string): Observable<ExecutionDto>
   getExecution(executionId: string): Observable<ExecutionDto>
   ```

2. **`execution.model.ts` (new):** TypeScript interfaces for `ValidateExecutionRequest`, `ExecutionDto`, `ExecutionStatus`.

3. **`plan-page.ts` — new view states:**
   - `executionReady` — risk approved, Execute button visible
   - `executionSubmitting` — execution in progress, button disabled
   - `executionResult` — execution completed/failed/unknown, result displayed

4. **`plan-page.html` — execution UI:**
   - In `riskDecision` view: add "Execute trade" button (only when `decision.approved === true`)
   - Optional lightweight confirmation dialog summarizing: instrument, side, quantity, order type, Broker Account
   - `executionResult` view: display status with appropriate messaging
   - UNKNOWN state: "Order submission status is uncertain. Reconciliation is required." with no blind retry

5. **`plan-page.spec.ts` — tests:**
   - Execute hidden without valid approval
   - Execute available after valid approval
   - Click triggers execution workflow
   - Double-click protection (submitting state)
   - Success state rendered
   - Rejection rendered
   - Unknown outcome rendered distinctly

6. **`trade-plan.service.ts` — add execution API methods** or keep in separate `ExecutionService`.

**Affected modules:** `trading-os-web`

**Files:**
- `trading-os-web/src/app/core/services/execution.service.ts` (new)
- `trading-os-web/src/app/core/models/execution.model.ts` (new)
- `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.ts`
- `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.html`
- `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.scss`
- `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.spec.ts`

**Domain/security constraints:**
- Execute action only visible when `decision.approved === true`
- Execution parameters are immutable (read from TradePlan, not editable)
- Double-click protection via submitting state
- UNKNOWN outcome displayed distinctly from FAILED
- No blind retry when UNKNOWN

**Tests:**
- New: `execution.service.spec.ts` — validate/execute API calls
- Modified: `plan-page.spec.ts` — execution states, button visibility, result display

**Acceptance Criteria:** AC1 (user can Execute), AC6 (acceptance alone doesn't submit), AC7 (risk alone doesn't submit), AC8 (explicit post-risk action), AC9 (immutable parameters), AC12 (user sees result).

**Dependencies:** Steps 2-5 (backend must be working).

**Validation commands:**
```bash
cd trading-os-web && npx ng test --watch=false
cd trading-os-web && npx ng build --configuration production
```

**Risks:** Medium. Frontend integration must match backend API contract exactly.

---

### Step 7 — Test Margin Adapter for Sandbox Proof (Correction B Applied)

**Objective:** Provide controlled test adapters for margin and execution so risk evaluation and execution pipeline succeed without contacting Kraken.

**Correction B:** Do NOT depend on a live Kraken endpoint. Use deterministic test/mock adapters.

**Required change:**

1. **`TestMarginAdapter.java` (new, in trading-core test sources):** Implements `RequiredMarginPort`, returns a valid `Fact` with configurable margin amount.
   ```java
   @Component
   @Profile("sandbox")
   public class TestMarginAdapter implements RequiredMarginPort {
       @Override
       public Optional<Fact> resolve(Request request) {
           return Optional.of(new Fact(
               new BigDecimal("100.00"), "USD",
               "test-margin", 1L, Instant.now()));
       }
   }
   ```

2. **Controlled ExecutionCapability (new, in broker-service test sources):** A test-only fake that implements `ExecutionCapability` and `ReconciliationCapability`. Returns deterministic `Acknowledged`, `Rejected`, or `Unknown` responses without contacting Kraken.

3. **Broker Service test configuration:** Register the fake execution capability for test profile. Wire it into `BrokerProviderRegistry` for tests.

**Affected modules:** `trading-core` (test), `broker-service` (test)

**Files:**
- `trading-core/src/test/java/com/hope/trading/trading_core/risk/infrastructure/client/TestMarginAdapter.java` (new)
- `broker-service/src/test/java/com/hope/trading/broker_service/broker/infrastructure/fake/FakeExecutionCapability.java` (new)
- `broker-service/src/test/java/com/hope/trading/broker_service/broker/infrastructure/fake/FakeReconciliationCapability.java` (new)
- `broker-service/src/test/resources/application-test.properties` (update)

**Tests:** The margin adapter itself is the test infrastructure.

**Acceptance Criteria:** AC13 (controlled sandbox/mock environment).

**Dependencies:** None.

**Risks:** Low. Test-only component. Does not affect production.

---

### Step 8 — Full Targeted Regression

**Objective:** Run all affected test suites and verify no regressions.

**Commands:**
```bash
cd trading-core && mvn test
cd broker-service && mvn test
cd gateway && mvn test
cd trading-os-web && npx ng test --watch=false
cd trading-os-web && npx ng build --configuration production
```

**Verification checklist:**
- [ ] All Trading Core tests pass
- [ ] All Broker Service tests pass
- [ ] All Gateway tests pass
- [ ] All Angular tests pass
- [ ] Angular production build succeeds
- [ ] Account-ID regression test passes (distinct IDs)
- [ ] Broker Service ownership tests pass
- [ ] Gateway execution route test passes
- [ ] Execution UI tests pass
- [ ] Architecture tests still pass (no infrastructure imports in domain)

---

## AC Traceability

| AC | Step(s) | Validation |
|---|---|---|
| AC1: User can Execute | Step 6 | Angular test: Execute button visible after approval, click triggers execution |
| AC2: Distinct account IDs | Step 2 | ValidateAndCreateServiceTest: distinct UUIDs, regression test |
| AC3: Test corrected | Step 2 | ValidateAndCreateServiceTest: tradingAccountId != brokerAccountId |
| AC4: Ownership required | Steps 3, 4 | Broker Service ownership test, Trading Core requireOwned |
| AC5: Broker Service ownership | Step 3 | BrokerServiceOwnershipTest: throws on unauthorized |
| AC6: Acceptance ≠ execution | Step 6 | Angular test: no broker order on acceptance |
| AC7: Risk approval ≠ execution | Step 6 | Angular test: no broker order on risk approval |
| AC8: Explicit post-risk action | Step 6 | Angular test: Execute only after approval |
| AC9: Immutable parameters | Step 6 | Angular: execution uses TradePlan data, no user input |
| AC10: Idempotency intact | Steps 2-4 | Existing idempotency tests pass unchanged |
| AC11: UNKNOWN safety | Step 6 | Angular test: UNKNOWN displayed distinctly, no blind retry |
| AC12: User sees result | Step 6 | Angular test: result states rendered |
| AC13: Controlled environment | Step 7 | TestMarginAdapter, sandbox config |
| AC14: No live execution | Step 7 | KRAKEN_BASE_URL sandbox config |
| AC15: No risk bypass | All steps | No force-execute, no skip-risk added |

## Definition of Done Traceability

| DoD Item | Step |
|---|---|
| ADR-032 accepted | Step 1 |
| Account identity bug fixed | Step 2 |
| Distinct-ID regression | Step 2 |
| Broker Service ownership enforced | Step 3 |
| Gateway route reachable | Step 5 |
| Angular Execute action | Step 6 |
| No automatic execution | Step 6 (AC6, AC7, AC8) |
| Immutable entryIntent | Step 6 (AC9) + existing Step 2 (deriveParameters unchanged) |
| Idempotency intact | Steps 2-4 (no changes to idempotency logic) |
| UNKNOWN outcome safe | Step 6 (AC11) |
| Controlled E2E proof | Step 7 |
| Frontend renders result | Step 6 (AC12) |
| Automated tests pass | Step 8 |
| No uncontrolled live-money | Step 7 |
| Risk freshness gap documented | Implementation Report (not this plan) |
