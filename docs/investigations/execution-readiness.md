# Investigation — Execution Readiness

## Status

COMPLETE

## Executive Summary

The Trading OS execution domain is architecturally mature. ADR-029 defines a rigorous ExecutionIntent lifecycle with idempotency, reconciliation, and audit. The backend pipeline exists: ValidateAndCreateService → ExecutionIntent → ExecuteTradeService → BrokerSubmissionStep → Broker Service → Kraken. However, three concrete issues must be resolved before execution can be safely exposed as a user-facing action:

1. **Account-ID mismatch (CONFIRMED_BUG):** ValidateAndCreateService compares `evaluation.accountId()` (Trading Account ID) with `command.brokerAccountId()` (Broker Account ID). These are different domain concepts. The comparison will always fail in normal operation, making execution unreachable.

2. **Broker Service ownership enforcement (UNSAFE):** ExecuteOrderService, CancelOrderService, and ReconcileExecutionService perform no owner verification. Only GetRiskSnapshotService checks ownership. Any internal caller that knows a broker-account UUID can operate on it.

3. **No explicit post-risk human authorization (NOT_DEFINED):** The current flow is: user accepts plan → risk evaluates → execution can be created. There is no second explicit human action after risk approval. ADR-029 and ADR-014 require human authority, but the current implementation treats plan acceptance + risk approval as sufficient authorization. The product should add an explicit "Execute" action after risk approval.

All three issues are small, coherent fixes that belong in a single Execution Story. The existing execution backend is otherwise safe: unknown outcomes require reconciliation before retry, idempotency is enforced, and the lifecycle state machine prevents invalid transitions.

## Scope

A. Trading Account vs Broker Account identity
B. Ownership / actor authorization across execution
C. TradePlan / risk / execution state semantics
D. Human authorization boundary
E. ADR governance
F. Prerequisites for the next Execution Story

## DevLog Context

- **Availability:** Online
- **Freshness:** PARTIALLY_FRESH (context 8e5edc7, baseline 21b1d52)
- **Usefulness:** LOW for this investigation — DevLog evidence contained zero references to execution domain classes, ADR-029/030/031/032, ownership enforcement, or account identity. All evidence came from direct repository inspection.

## Relevant ADRs

### ADR-001 — Trading OS Vision (Accepted)

**Decision:** The user is always the final decision maker. Deterministic rules always have priority over AI. The user validates and executes trades.

**Governs execution:** Yes — foundational human authority principle.

### ADR-014 — Trading Decision Pipeline (Accepted)

**Decision:** Layered pipeline terminating in human validation before broker execution. "Execution never occurs without explicit user action."

**Governs execution:** Yes — defines the complete pipeline from market data through human validation to broker execution.

### ADR-029 — Execution Domain Architecture (Accepted)

**Decision:** Execution begins only after Risk approval. ExecutionIntent is the security boundary. Idempotency is a business responsibility. Unknown outcomes require reconciliation before retry. Recovery never assumes an order was not created.

**Governs execution:** Yes — primary execution architecture ADR.

### ADR-030 — Broker Service Architecture (Accepted)

**Decision:** Broker Service is a synchronous technical adapter. No business logic. No execution state persistence. Unsafe operations must never be retried automatically. Business resilience belongs to ADR-029's Execution Domain.

**Governs execution:** Yes — defines the transport layer and broker abstraction.

### ADR-031 — Trade Planning Context (Accepted)

**Decision:** TradePlanningContext is a planning context only. Financial authorization belongs to Risk Domain. An account without an explicit effective Trade Planning Profile cannot request a Trade Plan.

**Governs execution:** Indirectly — ensures financial authority is assembled by Trading Core.

### ADR-032 — Entry Intent Representation (Proposed)

**Decision:** A Trade Plan MUST explicitly contain its planned entry intent. The Execution Domain translates entry intent into ExecutionParameters. Must NOT invent execution parameters. Human validation does not create or modify entry intent.

**Governs execution:** Yes — critical correctness ADR. Status: PROPOSED despite implementation alignment.

**Recommendation:** ADR-032 should be ACCEPTED. The implementation already aligns: `ValidateAndCreateService.deriveParameters()` reads `plan.entryIntent()` and derives `ExecutionParameters` from it without invention. No amendment needed.

## Current Execution Flow

```
TradePlan (status=ACCEPTED)
    ↓
RiskPlanRiskEvaluationService.evaluate()
    ↓
RiskValidationResult (decision=APPROVED)
    ↓
RiskAcknowledgmentDeliveryService → MI (outbox pattern)
    ↓
[USER ACTION MISSING: explicit "Execute" after risk approval]
    ↓
POST /executions/validate (ValidateAndCreateCommand)
    ↓
ValidateAndCreateService.validateAndCreate()
    ↓ ExecutionIntent (status=CREATED → VALIDATED)
POST /executions/{id}/execute
    ↓
ExecuteTradeService.execute()
    ↓ ExecutionPipelineContext
    ├─ ExecutionValidationStep
    ├─ IdempotencyVerificationStep
    ├─ ExecutionAttemptCreationStep
    ├─ BrokerSubmissionStep
    │   ↓
    │   BrokerExecutionPort.submit()
    │   ↓
    │   Broker Service → Kraken
    ├─ BrokerResponseProcessingStep
    └─ ExecutionFinalizationStep
    ↓
ExecutionIntent (COMPLETED | FAILED | SUBMISSION_OUTCOME_UNKNOWN)
```

## Account Domain

### Account (Legacy)

- **Identifier:** `accountId` (UUID, auto-generated)
- **Owner:** `user` (ManyToOne → User)
- **Service:** Trading Core
- **Persistence:** `accounts` table
- **Purpose:** Local trading account with balances, equity, peak equity, optional Rules, local trades
- **Lifecycle:** Created during sync or manually
- **Relationships:** Has `trades` (OneToMany), `rules` (ManyToOne), `balances` (OneToMany)

### BrokerAccount (New)

- **Identifier:** `id` (UUID, explicit)
- **Owner:** `ownerId` (UUID, not FK — no JPA relationship to User)
- **Service:** Trading Core
- **Persistence:** `broker_account` table
- **Purpose:** Broker reference with connection state, credential reference, provider
- **Lifecycle:** Created → PENDING_VALIDATION → CONNECTED → (various states) → REVOKED
- **Relationships:** None to Account. No formal mapping exists.

### Cardinality

- **Can one Account have multiple BrokerAccounts?** No evidence of explicit mapping. The legacy Account has a `broker` string field. The new BrokerAccount has no reference to Account. The risk configuration table (`account_risk_configuration`) maps Trading Account → Broker Account, but this is a risk-layer concept, not a domain relationship.

- **Can one BrokerAccount serve multiple Accounts?** No evidence. BrokerAccount.ownerId is unique per user, but no Account-level constraint exists.

- **Is there currently any explicit mapping?** Only through `account_risk_configuration` (risk layer) and the legacy `Account.broker` string field.

## Account Identity Mismatch

**Classification: CONFIRMED_BUG**

**Location:** `ValidateAndCreateService.java:73-77`

```java
// Step 4: Verify evaluation belongs to the correct account
if (!evaluation.accountId().equals(command.brokerAccountId())) {
    throw new ExecutionValidationException("EVALUATION_ACCOUNT_MISMATCH",
            "Risk Evaluation does not match the specified account", 409);
}
```

**What each ID represents:**
- `evaluation.accountId()` = Trading Account ID (UUID from `Account.accountId`)
- `command.brokerAccountId()` = Broker Account ID (UUID from `BrokerAccount.id`)

**Why they are being compared:** The code assumes these are the same concept. They are not.

**Is the comparison semantically invalid?** Yes. The RiskEvaluation is created against a Trading Account (the `accountId` in `RiskEvaluationModels.Command`). The execution targets a Broker Account. These are different entities with different lifecycles.

**Does this make execution unreachable?** Yes. In normal operation, `evaluation.accountId()` (Trading Account UUID) will never equal `command.brokerAccountId()` (Broker Account UUID). The check will always throw `EVALUATION_ACCOUNT_MISMATCH`.

**Do tests encode the wrong behavior?** Yes. `ValidateAndCreateServiceTest.java:194-213` uses the same UUID for evaluation account, TradePlan trading account, and broker account — hiding the mismatch.

**What the intended validation probably was:** Verify that the RiskEvaluation was created for the same Trading Account that the TradePlan references. This is already done correctly in step 10: `evaluation.accountId().equals(plan.tradingAccountId())`.

**Correct conceptual relationship:**
```
TradePlan
    ↓ references
Trading Account (plan.tradingAccountId())
    ↓ mapped via
account_risk_configuration
    ↓ references
Broker Account (configuration.brokerAccountId())
```

**Fix:** Remove step 4 (it's redundant with step 10). The Broker Account ownership is already verified in step 11. The missing piece is verifying that the Broker Account is the one mapped to the Trading Account in the risk configuration — but this is implicitly guaranteed by the risk evaluation flow (which loads the broker snapshot through the configuration's brokerAccountId).

**Estimate:** Small fix within the Execution Story. Not a separate prerequisite.

## Actor Identity

### JWT Subject

- JWT subject = `User.userId` (UUID)
- Generated by `JwtServiceImpl.generateToken()` with `subject(userDto.getUserId().toString())`
- Extracted by `JwtServiceImpl.extractUserId()` via `Claims.getSubject()`

### Gateway Propagation

- `JwtAuthenticationFilter` builds `UserAuthenticationDto` with `userId`, `username`, `email`, `role`
- Sets as `Authentication.principal` in reactive security context
- `AuthenticatedActorHeaderFilter` propagates `X-Actor-Id` header — but ONLY for `/api/v1/intelligence/**` routes
- For Trading Core routes, the JWT principal travels as the Spring Security `Authentication` object

### Trading Core

- `JwtAuthenticationFilter` extracts principal from security context
- Controllers receive `Authentication` parameter, cast principal to `UserDto`
- `principal(authentication).getUserId()` returns the authenticated user's UUID

### Broker Service

- Broker Service endpoints are at `/internal/v1/**`
- No JWT validation on Broker Service internal endpoints
- Broker Service trusts that only Trading Core can call it
- **No actor identity is propagated to Broker Service for execution operations**

## Trading Core Ownership

| Endpoint | Actor | Ownership Check | Result |
|---|---|---|---|
| `POST /executions/validate` | `initiatorId` from JWT | Step 9: `plan.ownerId().equals(command.initiatorId())` | SAFE |
| `POST /executions/validate` | `initiatorId` from JWT | Step 11: `brokerAccounts.findByIdAndOwnerId(brokerAccountId, initiatorId)` | SAFE |
| `POST /executions/{id}/execute` | JWT principal | `requireOwned(id, authentication)` → `query.findOwned(id, userId)` | SAFE |
| `GET /executions/{id}` | JWT principal | `requireOwned(id, authentication)` | SAFE |
| `GET /executions` | JWT principal | `query.findOwned(userId)` | SAFE |
| `POST /executions/{id}/retry` | JWT principal | `requireOwned(id, authentication)` | SAFE |
| `POST /executions/{id}/cancel` | JWT principal | `requireOwned(id, authentication)` | SAFE |

**Assessment:** Trading Core execution endpoints are SAFE. All endpoints verify actor ownership through `requireOwned()` or explicit ownership checks in `ValidateAndCreateService`.

**Can User A execute or inspect User B's execution resources?** No. All endpoints use `requireOwned()` which queries by both intent ID and owner userId.

## Broker Service Ownership

| Endpoint | Authenticated? | Actor Propagated? | Ownership Verified? | Safe to Expose? |
|---|---|---|---|---|
| `POST /internal/v1/executions` (execute) | NO | NO | NO | MISSING_OWNERSHIP |
| `POST /internal/v1/executions/reconcile` | NO | NO | NO | MISSING_OWNERSHIP |
| `POST /internal/v1/executions/{id}/cancel` | NO | NO | NO | MISSING_OWNERSHIP |
| `GET /internal/v1/accounts/{id}` | NO | NO | NO | MISSING_OWNERSHIP |
| `GET /internal/v1/positions/{id}` | NO | NO | NO | MISSING_OWNERSHIP |
| `GET /internal/v1/orders/{id}` | NO | NO | NO | MISSING_OWNERSHIP |
| `GET /internal/v1/risk-snapshot` | NO | YES (ownerId param) | YES | SAFE |

**Assessment:** Broker Service execution endpoints are **UNSAFE**. Only `GetRiskSnapshotService` enforces ownership. All other services resolve the provider solely by broker-account UUID without verifying the caller's identity or ownership.

**Risk mitigation:** Broker Service is intended as an internal service. If the Docker network is the only access path, the risk is limited. However, Gateway's discovery-locator may expose these endpoints.

**Defense in depth recommendation:** Add ownership verification to Broker Service execution endpoints as part of the Execution Story. This is a small addition (check ownerId against a header or parameter) and prevents privilege escalation if network boundaries are breached.

## Gateway Exposure

**Classification: EXPOSED_BUT_AUTHENTICATED**

Gateway has `spring.cloud.gateway.discovery.locator.enabled=true`. This can generate service-name-prefixed routes (e.g., `/broker-service/internal/v1/executions`) in addition to the explicit route table.

Gateway requires authentication for all non-public endpoints (`anyExchange().authenticated()`). So discovery-located routes would still require a valid JWT.

**However:** Broker Service internal endpoints have no JWT validation. If a discovery-located route reaches Broker Service, any authenticated user could call execution endpoints. The Gateway authentication filter runs, but Broker Service does not verify the caller's identity.

**Assessment:** The combination of Gateway discovery-locator + Broker Service missing ownership = potential privilege escalation. This should be addressed before execution is exposed.

**Recommendation:** Either disable discovery-locator for Broker Service routes, or add JWT/ownership validation to Broker Service execution endpoints. The latter is preferred as defense in depth.

## TradePlan State Machine

The TradePlan state machine is simple. States are stored as strings in the MI persistence layer:

```
DRAFT → PROPOSED → ACCEPTED → [RISK_VALIDATED — via acknowledgment]
```

**Current relevant states:**
- `PROPOSED` — Plan created by MI, pending human decision
- `ACCEPTED` — Human accepts the proposal
- Risk evaluation requires `ACCEPTED` status
- Execution requires `ACCEPTED` status

**Key finding:** There is NO state mismatch. The plan stays in `ACCEPTED` after risk evaluation. The RiskAcknowledgment is delivered to MI via outbox, but the plan state itself does not change to `RISK_VALIDATED`. ValidateAndCreateService checks for `"ACCEPTED"`, which is the correct current state.

The Reality Check's concern about RISK_VALIDATED vs ACCEPTED was based on an incorrect assumption. The actual code is consistent.

## Risk Evaluation State

Risk evaluation produces:
- `evaluation.status()` = `"COMPLETED"` | `"CONTEXT_UNAVAILABLE"`
- `evaluation.decision()` = `"APPROVED"` | `"APPROVED_WITH_WARNINGS"` | `"REJECTED"` | `null`

The evaluation is persisted with:
- evaluationId, actorId, idempotencyKey, tradePlanId, tradePlanVersion, accountId
- status, decision, contextVersion
- full result and response

**Risk does NOT transition TradePlan state.** It persists an acknowledgment outbox entry that MI consumes asynchronously.

## Execution Preconditions

ValidateAndCreateService requires:
1. RiskEvaluation found by ID
2. Evaluation status = COMPLETED
3. Evaluation decision = APPROVED or APPROVED_WITH_WARNINGS
4. ~~Evaluation accountId == command.brokerAccountId~~ **(BUG — should be removed)**
5. TradePlan found by ID and version
6. TradePlan status = ACCEPTED
7. TradePlan version == evaluation version
8. TradePlan ID == evaluation tradePlanId
9. TradePlan ownerId == command.initiatorId
10. Evaluation accountId == plan.tradingAccountId
11. BrokerAccount belongs to initiator

**Can the normal product flow reach these preconditions?** Almost. The flow is:
1. User accepts plan → plan status = ACCEPTED ✓
2. Risk evaluates → evaluation COMPLETED + APPROVED ✓
3. User calls POST /executions/validate → **BLOCKED by step 4 bug**

After fixing step 4, the flow would work.

## State Mismatch

**Classification: NO_ISSUE**

The Reality Check reported a mismatch between RISK_VALIDATED and ACCEPTED. This is incorrect. The plan stays in ACCEPTED after risk evaluation. ValidateAndCreateService checks for ACCEPTED. The states are consistent.

## Plan Acceptance Means

**"I agree this is the trade I want to consider."**

Evidence:
- UI wording: "Accept" / "Reject" on the plan page
- Controller behavior: `TradePlanDecisionController.decide()` transitions plan to ACCEPTED
- Domain: `accept()` method on the plan aggregate
- ADR-014: "Human Validation is mandatory — the user always validates opening trades"

Plan acceptance does NOT mean "I authorize broker execution." It means "I accept this proposed plan and want it evaluated by the risk engine."

## Post-Risk Human Authorization

**Classification: REQUIRED**

Current behavior: Plan acceptance + risk approval = execution can proceed. No second action.

ADR-014 states: "Execution never occurs without explicit user action."
ADR-001 states: "The user is always the final decision maker."

The current implementation technically satisfies this through the `POST /executions/validate` call (which requires authentication). But there is no product UI action between "risk result displayed" and "execution created."

**Recommendation:** The Execution Story should add an explicit "Execute" button on the plan page after risk approval. This button triggers `POST /executions/validate` + `POST /executions/{id}/execute`. The user must click it. Risk approval alone does not trigger execution.

**Risk revalidation:** Currently NOT done. The system relies on the risk snapshot from evaluation time. For a first controlled sandbox execution, this is acceptable. For production, risk revalidation before submission should be considered.

## Idempotency

**Classification: STRONG**

- ExecutionIntent has a stable, immutable `idempotencyKey`
- ValidateAndCreateService checks for existing evaluation with same idempotency key
- BrokerSubmissionStep uses the idempotency key to derive a stable `cl_ord_id` for Kraken
- Kraken supports client order ID for idempotency
- Only one active ExecutionAttempt is allowed at a time (enforced by `activateAttempt()`)
- Optimistic locking on ExecutionIntent version

**Can one accepted/risk-approved TradePlan create multiple broker orders?** No. The idempotency key is bound to the ValidateAndCreateCommand. A duplicate request with the same key returns the existing evaluation. A different key would require a different evaluation, which requires a different risk request.

## Unknown Outcome / Reconciliation

**Classification: SAFE**

Flow:
1. Broker submission → response unknown
2. ExecutionIntent transitions to `SUBMISSION_OUTCOME_UNKNOWN`
3. Retry is BLOCKED: `RetryExecutionService` throws "Reconciliation is required before retry"
4. Recovery pipeline: discovery → inspection → strategy → reconciliation → finalization
5. Reconciliation calls Broker Service with same idempotency key
6. Broker Service searches open + closed orders for matching `cl_ord_id`
7. Results: `ReconciledOrder` (found), `ConfirmedAbsent` (not found), `Inconsistent` (ambiguous)
8. Finalization: Completed (filled), Failed (rejected/cancelled), Validated (absent → retry), RecoveryBlocked (inconsistent)

**What prevents blind retry?** `RetryExecutionService` explicitly checks for `SUBMISSION_OUTCOME_UNKNOWN` and `RECONCILIATION_IN_PROGRESS` statuses and throws before any broker call.

## Risk Freshness / Revalidation

**Classification: NOT currently revalidated**

The execution flow uses the risk evaluation snapshot from evaluation time. It does NOT revalidate risk immediately before broker submission.

**Risk:** Account equity, open exposure, daily drawdown, or market price may have changed between evaluation and execution.

**For first sandbox execution:** Acceptable. The evaluation is recent (same user session), and sandbox orders are low-risk.

**For production:** Should be addressed in a future Story.

## Market Price / Margin Constraints

- TradePlan contains `entryIntent` with price semantics (MARKET or LIMIT)
- ExecutionParameters derive from entryIntent: orderType, price, instrument, side, quantity
- LIMIT orders include limitPrice; MARKET orders do not
- No slippage handling, no quantity recalculation, no stale plan detection
- For first sandbox execution with MARKET order: acceptable
- For LIMIT orders: price staleness is a concern but not a blocker for sandbox

## Margin Provider Gap

**Classification: NON_BLOCKING FOR FIRST EXECUTION**

The `RequiredMarginPort` defaults to `UnavailableRequiredMarginClient`. This blocks risk evaluation (throws `REQUIRED_MARGIN_UNAVAILABLE`).

However: if risk evaluation can be completed (meaning margin was resolved somehow, e.g., through a mock or test adapter), execution itself does not re-check margin. The margin fact is captured in the risk snapshot and used for risk rules, not for execution validation.

**For sandbox:** A test/mock margin adapter is sufficient to unblock risk evaluation. Execution does not depend on real-time margin.

## Broker Permissions

From repository documentation and code:
- Kraken API endpoints used: Balance, OpenPositions, OpenOrders, ClosedOrders, AddOrder, CancelOrder
- AddOrder requires `trade` permission on Kraken API key
- CancelOrder requires `trade` permission
- Balance/OpenPositions/OpenOrders require `read` permission
- No withdrawal endpoints are called

**Minimum required permissions:** `read` + `trade`
**Should NOT require:** withdrawal permissions

## Safe Execution Environment

Available strategies from repository:
1. **Kraken demo/sandbox:** Kraken offers a sandbox environment. The code uses environment-configurable API endpoints (`KrakenApiClient` reads from properties).
2. **Mock broker adapter:** Could be implemented as a test-only `ExecutionCapability` that records requests without external calls.
3. **Very small controlled live order:** Possible but not recommended for first proof.

**Recommendation:** Use Kraken sandbox for the first controlled execution proof. If sandbox is unavailable, implement a mock adapter for CI/manual testing.

## Frontend Execution Boundary

Current plan-page state after risk:
- Risk approved → shows approval with reasons/warnings, metrics, footer "Back to opportunities"
- Risk rejected → shows rejection with reasons, footer "Back to opportunities"
- Risk unavailable → shows unavailability explanation

**Minimal future UX:**
```
Risk approved
--------------
✓ Maximum risk: 0.8% (limit: 1.0%)
✓ Exposure: 2.1% (limit: 5.0%)
✓ Daily drawdown: 0.3% (limit: 2.0%)

[ Execute trade ]  [ Abandon plan ]
```

**Execution result UX:** After clicking Execute:
- ACKNOWLEDGED → "Order submitted. Waiting for fill..."
- REJECTED → "Order rejected: {reason}"
- UNKNOWN → "Order submitted. Status uncertain. Reconciling..."

**Critical:** UNKNOWN must NOT be displayed as FAILED. The order may have succeeded.

## Failure Safety

| Failure Case | Backend Behavior | Assessment |
|---|---|---|
| Provider unavailable | Returns UNKNOWN, blocks retry | SAFE |
| Timeout | Returns UNKNOWN, blocks retry | SAFE |
| Unknown broker outcome | SUBMISSION_OUTCOME_UNKNOWN, requires reconciliation | SAFE |
| Rejected order | REJECTED, FAILED status | SAFE |
| Risk no longer valid | Not revalidated (snapshot used) | ACCEPTABLE for sandbox |
| Ownership failure | Trading Core: 403. Broker Service: no check | PARTIAL (Broker Service gap) |
| Duplicate request | Idempotency key prevents duplicate intent | SAFE |
| Stale TradePlan | Version check prevents stale execution | SAFE |
| Invalid account mapping | Step 11 verifies BrokerAccount ownership | SAFE |

## Transaction Boundaries

Broker submission occurs OUTSIDE database transactions:
1. BrokerSubmissionStep: persists attempt + intent, then calls `broker.submit()`
2. Response is processed after the external call
3. This is correct: external calls should not be inside DB transactions

The risk evaluation occurs inside a transaction (`TransactionTemplate`).

## Outbox / Events

- Risk acknowledgment uses an outbox pattern (`risk_acknowledgment_outbox` table)
- `RiskAcknowledgmentDeliveryService` polls and delivers acknowledgments to MI
- Execution events are published via `ExecutionEventPublisher` (in-memory, not outbox)
- Broker submission is synchronous: `broker.submit()` is called directly, result is processed immediately

## Human Authority ADR Decision

**Classification: ADR_032_ACCEPTANCE_REQUIRED**

ADR-032 (Proposed) should be ACCEPTED. Its implementation already aligns:
- `ValidateAndCreateService.deriveParameters()` reads `plan.entryIntent()` and derives `ExecutionParameters`
- No execution parameters are invented
- Entry intent is immutable in the Trade Plan

The canonical semantics should be:
1. User accepts TradePlan → plan status = ACCEPTED
2. Deterministic risk evaluates → APPROVED/REJECTED
3. Risk approval permits execution but does not trigger it
4. User explicitly clicks "Execute" → execution intent created
5. System submits order

ADR-014 already governs this: "Execution never occurs without explicit user action." ADR-032's acceptance formalizes the entry intent immutability.

## Account Identity Decision

**What exact identity should execution validate?**

```
1. Actor owns TradePlan (plan.ownerId == initiatorId) ✓ (step 9)
2. Actor owns BrokerAccount (brokerAccounts.findByIdAndOwnerId) ✓ (step 11)
3. RiskEvaluation matches TradePlan (evaluation.tradePlanId == plan.id) ✓ (step 8)
4. RiskEvaluation matches TradingAccount (evaluation.accountId == plan.tradingAccountId) ✓ (step 10)
5. BrokerAccount is the one mapped in risk configuration ✓ (implicit: risk evaluation loaded broker snapshot through configuration.brokerAccountId)
```

The missing piece (step 4 bug) is that the code also compares `evaluation.accountId()` with `command.brokerAccountId()`, which is wrong. This should be removed.

## Ownership Decision

**At which boundaries must ownership be enforced?**

1. **Gateway authentication:** JWT validation on all non-public endpoints ✓
2. **Trading Core aggregate ownership:** `requireOwned()` on all execution endpoints ✓
3. **Broker Service broker-account ownership:** MISSING on execution endpoints

Defense in depth is appropriate. Relying solely on Trading Core ownership is insufficient if Broker Service endpoints are reachable through discovery-locator.

## Story Boundary

**Classification: ONE_EXECUTION_STORY**

The following can safely contain:
- Fix ValidateAndCreateService step 4 (small code change)
- Add Broker Service ownership enforcement (small addition to BrokerOperationServices)
- Add explicit post-risk "Execute" action in plan-page Angular component
- Add execution result display in plan-page
- Add Gateway execution route
- One controlled Kraken sandbox proof
- Test coverage

**Rationale:** All three fixes are small, coherent, and directly related to making execution safe and user-facing. Splitting them would create artificial boundaries without independent value.

## ADR Decision

**Classification: ACCEPT_ADR_032**

ADR-032 should be accepted as-is. No amendment needed. The implementation already aligns with its decisions.

## Production / Real-Money Safety

- Kraken sandbox should be used for first proof
- Execution should be gated by environment configuration (sandbox vs production)
- The Execution Story should NOT enable uncontrolled real-money trading
- Broker credentials with trade permissions should be explicitly validated in sandbox first

## Security Severity

| Issue | Severity | Location |
|---|---|---|
| ValidateAndCreateService step 4 account-ID mismatch | HIGH | `ValidateAndCreateService.java:73-77` |
| Broker Service missing ownership on execution endpoints | HIGH | `BrokerOperationServices.ExecuteOrderService`, `CancelOrderService`, `ReconcileExecutionService` |
| Gateway discovery-locator may expose internal Broker Service routes | MEDIUM | `gateway/application.properties:10-11` |
| Broker Service internal endpoints have no JWT validation | MEDIUM | `broker-service/broker/api/controller/ExecutionController.java` |
| TradePlan test uses same UUID for all accounts | LOW | `ValidateAndCreateServiceTest.java:194-213` |

## Test Strategy for Future Story

Minimum test matrix:

| Test | Expected |
|---|---|
| Owner creates execution intent | SUCCESS |
| Non-owner cannot create execution intent | 403 |
| Wrong BrokerAccount cannot be used | 403 |
| Wrong Trading Account cannot be used | 409 |
| Risk rejected → cannot execute | 422 |
| Risk unavailable → cannot execute | 422 |
| Risk approved → execution may be authorized | SUCCESS |
| Approval alone → no broker order | N/A (UI action required) |
| Duplicate execute → no duplicate broker order | Idempotent |
| Broker acknowledged | COMPLETED |
| Broker rejected | FAILED |
| Broker unknown | SUBMISSION_OUTCOME_UNKNOWN |
| Unknown reconciliation | SAFE path |
| Stale/invalid state | 422 |
| Gateway authentication | 401 without token |
| Frontend state mapping | Correct UX for each state |

## Risks

1. **Sandbox availability:** If Kraken sandbox is not available, first proof requires mock adapter
2. **Credential permissions:** Existing Kraken credentials may not have trade permissions
3. **Risk revalidation gap:** Stale risk snapshot between evaluation and execution (acceptable for sandbox)
4. **Strategy legitimacy:** All opportunity scores = 100; only legacy fixture strategy is active

## Open Questions

1. Do existing Kraken credentials have trade permissions, or must new sandbox credentials be created?
2. Is Kraken sandbox accessible from the current deployment environment?
3. Should the Gateway discovery-locator be disabled for Broker Service, or should Broker Service add JWT validation?
4. Is the plan-page "Execute" action sufficient for human authorization, or should there be a separate confirmation step?

## Recommendation

**GO_FOR_EXECUTION_STORY**

The evidence supports creating a single Execution Story that:
1. Fixes the ValidateAndCreateService account-ID mismatch (small bug fix)
2. Adds Broker Service ownership enforcement (small security addition)
3. Adds explicit post-risk "Execute" action in Angular (UI addition)
4. Adds Gateway execution route (routing addition)
5. Proves one controlled Kraken sandbox execution
6. Covers the test matrix above

All fixes are coherent, small, and directly enable the first end-to-end trading decision journey.

---

*Investigation artifact created: 2026-08-27*
*Repository: main @ 8e5edc7*
*DevLog: partially fresh, LOW usefulness for execution investigation*
