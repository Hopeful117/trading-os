# Story 0030 — Connect Risk Decision to Human-Controlled Execution

## Goal

After accepting a Trade Plan and receiving deterministic Risk Engine approval, the authenticated user can explicitly authorize execution, causing Trading OS to create and execute an ExecutionIntent against the correct owned Broker Account, and then display the execution outcome without ambiguity.

This is the first closed Market Intelligence → TradePlan → Human Acceptance → Deterministic Risk → Human Execution Authorization → ExecutionIntent → Broker Submission → Execution Result loop.

Risk approval MUST NOT automatically submit an order. Execution requires a SECOND explicit human action.

---

## Context

Two investigations have established the next V1 vertical slice:

1. `docs/investigations/v1-trading-intelligence-reality-check.md` — concluded GO_FOR_NEXT_STORY, recommended "Connect Risk Decision to Human-Controlled Execution"
2. `docs/investigations/execution-readiness.md` — concluded GO_FOR_EXECUTION_STORY

The backend execution architecture is already mature (ADR-029). The pipeline exists:

```
TradePlan (ACCEPTED)
    ↓
RiskPlanRiskEvaluationService
    ↓
RiskValidationResult (APPROVED)
    ↓
POST /executions/validate (ValidateAndCreateService)
    ↓
ExecutionIntent
    ↓
POST /executions/{id}/execute (ExecuteTradeService)
    ↓
ExecutionPipelineContext
    ↓
BrokerSubmissionStep → Broker Service → Kraken
    ↓
Execution result
```

This Story connects and secures the existing pipeline. It does NOT replace it.

---

## Problem

Three concrete issues prevent safe user-facing execution:

**1. Account-ID Bug (CONFIRMED_BUG):** `ValidateAndCreateService` compares `evaluation.accountId()` (Trading Account UUID) with `command.brokerAccountId()` (Broker Account UUID). These are different domain concepts. The comparison always fails, making execution unreachable.

**2. Broker Service Ownership (UNSAFE):** `ExecuteOrderService`, `CancelOrderService`, and `ReconcileExecutionService` perform zero owner verification. Only `GetRiskSnapshotService` checks ownership. Any internal caller with a broker-account UUID can operate on it.

**3. No Explicit Post-Risk Human Authorization:** The current flow is: user accepts plan → risk evaluates → execution can be created. There is no second explicit human action after risk approval. ADR-014 requires "execution never occurs without explicit user action." The product should add an explicit "Execute" action after risk approval.

Additionally, Gateway does not expose the execution API to Angular, and ADR-032 (Entry Intent Representation) remains Proposed despite its implementation already aligning.

---

## Scope

### In scope

- Fix ValidateAndCreateService step 4 account-ID mismatch (bug fix)
- Remove invalid TradingAccount-vs-BrokerAccount comparison
- Add Broker Service ownership enforcement for execution-sensitive operations
- Propagate actor identity from Trading Core to Broker Service for ownership verification
- Add Gateway execution route for Angular
- Add explicit post-risk "Execute" action in Angular TradePlan page
- Add execution result display in Angular (completed/rejected/unknown)
- Prove one controlled Kraken sandbox or mock execution end-to-end
- Accept ADR-032 as-is (no amendment needed)
- Test coverage for account identity, ownership, human authority, idempotency, broker outcomes

### Out of scope

- Production real-money trading enablement
- Risk revalidation immediately before submission
- New margin provider implementation
- Position monitoring
- Portfolio management
- Passive scanner
- New active-scanner strategies
- AI Engine / AI recommendations
- News Service
- Historical market datasets / quant research / backtesting
- Paper-trading architecture beyond controlled execution proof
- Account/BrokerAccount domain redesign
- Multi-broker expansion
- Advanced order management
- Full reconciliation UI
- Order modification
- Stop-loss/take-profit management (unless already part of current entryIntent)
- Broad Gateway redesign
- Broad security refactor

---

## Acceptance Criteria

* [ ] AC1: Authenticated user who owns an accepted TradePlan with a completed APPROVED risk evaluation can explicitly choose Execute, causing Trading OS to create a valid ExecutionIntent.
* [ ] AC2: Trading Account ID and Broker Account ID remain distinct domain identities; the invalid direct comparison is removed.
* [ ] AC3: Existing test that uses the same UUID for RiskEvaluation account, TradePlan trading account, and BrokerAccount is corrected to use distinct IDs.
* [ ] AC4: Authenticated user must own both the TradePlan and target BrokerAccount.
* [ ] AC5: Broker Service independently verifies ownership for execution-sensitive operations (execute, cancel, reconcile).
* [ ] AC6: TradePlan acceptance alone never submits a broker order.
* [ ] AC7: Risk approval alone never submits a broker order.
* [ ] AC8: Only explicit post-risk human authorization can initiate execution.
* [ ] AC9: Execution parameters derive exclusively from the accepted TradePlan entryIntent (no invention, no caller override).
* [ ] AC10: Duplicate requests cannot produce duplicate broker orders (idempotency intact).
* [ ] AC11: UNKNOWN broker outcome is not represented as failure and cannot be blindly retried.
* [ ] AC12: User can see the resulting execution state (completed, rejected, unknown).
* [ ] AC13: First end-to-end proof runs only against a controlled sandbox/mock environment.
* [ ] AC14: Story does not enable unrestricted production real-money execution.
* [ ] AC15: Risk cannot be bypassed (no force execute, no skip risk, no admin bypass).
* [ ] Relevant tests pass.
* [ ] No unrelated behavior is changed.

---

## Constraints

* Preserve existing execution architecture (ADR-029 pipeline).
* Respect ADR-001 (human authority), ADR-014 (decision pipeline), ADR-029 (execution domain), ADR-030 (broker isolation), ADR-031 (trade planning context), ADR-032 (entry intent).
* Keep risk and execution authorization deterministic.
* Trading Core owns authoritative validation and ExecutionIntent creation.
* Human validation cannot override the Risk Domain decision.
* RiskEvaluation data must never be trusted from client input.
* Execution parameters must originate from the authoritative TradePlan.
* A modified TradePlan version requires a new RiskEvaluation.
* BrokerAccount identifiers must be validated against the authenticated user.
* Trading Core must not access or persist broker credentials.
* Risk Domain never accesses Broker Service directly.
* Broker Service remains a provider of facts and execution capabilities only.
* Defense in depth: Broker Service must enforce ownership even if Trading Core already validated.
* Angular must never call Broker Service directly.
* No broker order may result solely from TradePlan acceptance or Risk Engine approval.
* Execution is a financial state-changing operation; authentication and ownership are mandatory.
* Credentials never reach Angular; credentials never appear in logs.
* Production execution fails closed unless explicitly enabled.
* No withdrawal permission is required.
* Do not introduce unrelated dependencies.
* Do not commit, push, or merge automatically.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-001.md` — Trading OS Vision (human authority)
* `docs/architecture/adr/ADR-014.md` — Trading Decision Pipeline (layered pipeline, human validation)
* `docs/architecture/adr/ADR-029.md` — Execution Domain Architecture (lifecycle, idempotency, reconciliation)
* `docs/architecture/adr/ADR-030.md` — Broker Service Architecture (broker isolation, no business logic)
* `docs/architecture/adr/ADR-031.md` — Trade Planning Context (financial authority assembly)
* `docs/architecture/adr/ADR-032.md` — Entry Intent Representation (PROPOSED → should be ACCEPTED)

ADR-032 status: **Proposed**. Required action: **Accept as-is**. Implementation already aligns: `ValidateAndCreateService.deriveParameters()` reads `plan.entryIntent()` and derives `ExecutionParameters` without invention. No amendment needed.

---

## Relevant Modules

* `trading-core` — ValidateAndCreateService bug fix, execution endpoints, ownership checks
* `broker-service` — ownership enforcement on execution operations
* `gateway` — execution route exposure
* `trading-os-web` — Angular execution action and result display
* `risk-domain` — no changes (existing risk evaluation preserved)

---

## Security Requirements

* Execution is a financial state-changing operation.
* Authentication is mandatory on all execution endpoints.
* Ownership is mandatory: actor must own TradePlan and BrokerAccount.
* BrokerAccountId alone is never trusted as authorization.
* Credentials never reach Angular.
* Credentials never appear in logs.
* Broker Service performs defense-in-depth ownership verification.
* No withdrawal permission is required.
* Production execution fails closed unless explicitly enabled.

---

## Human Authority Boundary

This is a hard requirement.

TradePlan acceptance means: "I accept this proposed trade and want deterministic risk evaluation."

Risk approval means: "This trade satisfies the deterministic risk constraints represented by this evaluation."

Neither means "Execute this trade."

Execution requires a SECOND explicit human action: the user clicks "Execute trade" after risk approval.

Canonical semantics:

```
TradePlan accepted
    ↓
Risk evaluation
    ↓
APPROVED
    ↓
nothing happens automatically
    ↓
user explicitly clicks Execute
    ↓
execution begins
```

No broker order may result solely from TradePlan acceptance or Risk Engine approval. There MUST be an authenticated explicit execution command.

---

## Execution Parameter Immutability

The frontend execution action must NOT allow the user to silently change instrument, side, quantity, order type, or limit price after risk evaluation. Those values come from the validated TradePlan entryIntent.

If the user wants different execution parameters, the existing plan/risk workflow must be restarted according to domain rules. Execution must not mutate the accepted TradePlan.

---

## Execution API Flow

The frontend uses existing backend semantics:

```
POST /executions/validate
    ↓
ExecutionIntent created/validated
    ↓
POST /executions/{id}/execute
    ↓
execution result
```

Do not merge these into a new shortcut endpoint. Preserve ADR-029 lifecycle boundaries.

---

## Execution Result Semantics

The UI must distinguish:

* **ACKNOWLEDGED / COMPLETED** — "Order submitted and confirmed."
* **REJECTED / FAILED** — "Order rejected: {reason}."
* **SUBMISSION_OUTCOME_UNKNOWN** — "Order submission status is uncertain. Reconciliation is required."

Do not flatten UNKNOWN into FAILED. UNKNOWN means "The broker may have accepted the order, but Trading OS does not yet know." Do not offer blind retry while backend state requires reconciliation.

---

## Sandbox Boundary

First execution proof must use Kraken sandbox/demo or a controlled mock broker adapter. Production/live execution must remain disabled unless explicitly configured.

If environment configuration is ambiguous, execution must NOT default to live trading.

Do NOT assume sandbox behavior. Verify current repository configuration. If sandbox cannot be used, use a mock/test ExecutionCapability. Do NOT place a real-money order merely to satisfy acceptance criteria.

---

## Production Safety Boundary

This Story proves human-controlled end-to-end execution in a controlled environment.

Before real-money production execution, a future Story must address risk freshness/revalidation or formally establish another safe policy. This Story does NOT enable unrestricted production real-money execution.

Risk revalidation before submission is NOT currently implemented. Between Risk APPROVED and Execute, equity, exposure, daily drawdown, margin, and market price may change. This is acceptable ONLY for the first controlled sandbox proof.

---

## Definition of Done

* [ ] ADR-032 has been accepted according to repository workflow
* [ ] Account identity bug is fixed
* [ ] Regression test uses distinct Trading Account / Broker Account IDs
* [ ] Broker Service ownership is enforced on execution-sensitive operations
* [ ] Required Trading Core execution route is reachable through Gateway
* [ ] Angular exposes explicit Execute action only after valid risk approval
* [ ] No automatic execution exists
* [ ] Execution uses immutable TradePlan entryIntent
* [ ] Idempotency remains intact
* [ ] UNKNOWN outcome remains safe
* [ ] Controlled sandbox/mock execution succeeds end-to-end
* [ ] Frontend renders the result
* [ ] Automated tests pass
* [ ] No uncontrolled live-money execution is enabled
* [ ] Implementation report documents remaining production-readiness gap around risk freshness

---

## Validation

Expected validation:

* Targeted Maven tests for `trading-core` (account identity, ownership, idempotency, execution lifecycle)
* Targeted Maven tests for `broker-service` (ownership enforcement)
* Targeted Maven tests for affected Gateway behavior
* Angular tests (execution action, result display, state management)
* Angular production build
* Architecture validation against ADR-001, ADR-014, ADR-029, ADR-030, ADR-031, ADR-032
* End-to-end validation from accepted TradePlan to execution result in sandbox/mock
* Manual review in IntelliJ
* Verification that no automatic execution occurs
* Verification that no broker order is placed without explicit human action
