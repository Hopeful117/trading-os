# Discovery Report — Story 0034 Execution-Time Risk Revalidation

## Repository State

```
ROOT = /home/ludo/Bureau/workspace/trading-os
BRANCH = main
HEAD = ad7a0e4825d4bec5020cff42df63cbff716cde12
WORKTREE = clean
```

## ADR-041 Verification Summary

ADR-041 created at `docs/architecture/adr/ADR-041.md` with D1–D10 formalized from human-designed decisions. Status: **Proposed (READY_FOR_HUMAN_REVIEW)**.

| Decision | Repository Status | Evidence |
|----------|-------------------|----------|
| D1 Human Authority | IMPLEMENTED | Story 0030: explicit Execute button, no auto-execution |
| D2 Financial Boundary | PARTIALLY_IMPLEMENTED | Standard execution ✓; Position close ✗ (@Transactional spans broker call) |
| D3 T1 Revalidation | NOT_IMPLEMENTED | Critical gap — Story 0034 target |
| D4 Idempotency | IMPLEMENTED | Idempotency-Key header, DB constraints, deterministic cl_ord_id |
| D5 Acknowledgement ≠ Fill | IMPLEMENTED | ExecutionStatus, ExecutionResult, BrokerOrder separation |
| D6 Unknown → Reconcile | IMPLEMENTED | SUBMISSION_OUTCOME_UNKNOWN, RecoveryPipeline, ReconcileExecutionService |
| D7 Identity Separation | IMPLEMENTED | ExecutionIntentId, ExecutionAttemptId, IdempotencyKey, cl_ord_id/txid |
| D8 No Live Tests | IMPLEMENTED | ExecutionPipelineTest with fake BrokerExecutionPort |
| D9 Provider Neutral | IMPLEMENTED | Capability architecture, BrokerExecutionPort boundary |
| D10 Audit Provenance | PARTIALLY_IMPLEMENTED | T0 complete (StoredEvaluation); T1 pending |

---

## Current Risk → Execution Flow (Actual)

```
POST /executions/validate
    ↓
ValidateAndCreateService
    → loads T0 StoredEvaluation (COMPLETED + APPROVED)
    → loads TradePlan (ACCEPTED)
    → verifies ownership, version, account match
    → resolves brokerAccountId from AccountRiskConfiguration
    → deriveParameters() from plan.entryIntent
    → creates ExecutionIntent (VALIDATED)
    ↓
POST /executions/{id}/execute
    ↓
ExecuteTradeService.execute()
    → ExecutionPipelineContext
    → ExecutionValidationStep (intent state, expiration, Risk approval)
    → IdempotencyVerificationStep
    → ExecutionAttemptCreationStep
    → BrokerSubmissionStep (BROKER CALL HERE)
    → BrokerResponseProcessingStep
    → ExecutionFinalizationStep
    → events.publish()
```

---

## Broker Execution Capabilities (Current)

| Capability | Interface | Kraken Implementation |
|------------|-----------|----------------------|
| Execution | `ExecutionCapability.execute()` | `KrakenCapabilities.execute()` → AddOrder with cl_ord_id |
| Reconciliation | `ReconciliationCapability.reconcile()` | Queries OpenOrders/ClosedOrders by cl_ord_id |
| Position Mgmt | `PositionManagementCapability` (resolveTarget, executeClose, reconcile) | `KrakenPositionManagementCapability` — reduce_only=true, FIFO |
| Position Read | `PositionCapability.positions()` | OpenPositions → PositionSnapshot with brokerPositionReference=txid |

---

## Safety Gap Analysis (vs. ADR-041)

| Gap | Severity | Description |
|-----|----------|-------------|
| T1 Revalidation Missing (D3) | **CRITICAL** | No fresh risk evaluation before broker submission |
| Position Close @Transactional (D2) | **HIGH** | `PositionCloseService.close()` and `reconcile()` span broker call in Spring transaction |
| PostgreSQL Partial Index Unverified | **MEDIUM** | Story 0033 concurrency guard only tested on H2 |

---

## Story 0034 Proposed Boundary

**Minimal Coherent Scope:** Pre-Submission Risk Revalidation (T1)

```
TradePlan accepted
    ↓
T0 Risk Evaluation (APPROVED)
    ↓
Human clicks Execute
    ↓
ValidateAndCreateService → ExecutionIntent (VALIDATED)
    ↓
*** T1 Revalidation ***
    ↓ resolve authoritative current account risk profile (D12)
    ↓ load fresh broker/market facts
    ↓ run RiskEngine with resolved policy + fresh facts
    ↓
if T1 APPROVED → ExecuteTradeService pipeline
if T1 REJECTED → ExecutionIntent terminal failure state (D13), NO broker call
```

---

## Reusable Existing Components

| Component | Location | Reuse For |
|-----------|----------|-----------|
| `RiskEngine` | `risk-domain/engine/` | Deterministic T1 evaluation |
| `TradePlanRiskEvaluationService` fact loading | `trading-core/risk/application/` | Fresh context assembly |
| `BrokerRiskFactsPort` / `MarketValuationPort` / `RequiredMarginPort` | `trading-core/risk/application/port/` | Fresh facts acquisition |
| `RiskPersistence` | `trading-core/risk/infrastructure/persistence/` | Resolve authoritative current account profile (D12), persist T1 |
| `ExecutionIntentRepositoryPort` | `trading-core/execution/domain/repository/` | Link T1 to intent |
| `ExecutionStatus` | `trading-core/execution/domain/valueobject/` | Add terminal risk-rejection state (D13) |

---

## Required New Components (Conceptual)

| Component | Responsibility |
|-----------|----------------|
| `ExecutionTimeRiskRevalidationService` | Called before `ExecuteTradeService`; resolves authoritative current account profile (D12), loads fresh facts, runs RiskEngine, persists T1, returns decision |
| `ExecutionTimeRiskEvaluation` (persistence) | T1 evaluation record: decision, metrics, violations, timestamp, policy identity, linkage to ExecutionIntent |
| `ExecutionStatus` terminal risk-rejection state | Terminal state for T1 deterministic rejection (D13) |
| T1 integration in `ExecuteTradeService` or new pre-pipeline step | Gate broker submission on T1 result |

---

## D11 Analysis — Server-Side T1 Invariant

**Status:** `HUMAN_APPROVED`

**Approved Decision:**
> Execution-time risk revalidation is a mandatory server-side invariant of the human-authorized execution command. It cannot be delegated to the frontend. T1 occurs after the Core has deterministically validated the execution request and resolved the authoritative execution context, but before durable broker-command preparation/submission. The T1 → financial-command interval should be minimized.

**Semantics:**
- T1 is mandatory server-side gate in Trading Core.
- `ExecutionIntent` may precede T1 (created by `ValidateAndCreateService` at validate time).
- Broker submission before T1 APPROVED is **forbidden**.
- Frontend does not own or enforce the T1 gate.
- T1 runs synchronously in the execute request (or as immediate pre-step) to minimize T1→broker window.

**Repository Analysis:**
- `ValidateAndCreateService` already resolves authoritative context (brokerAccountId from AccountRiskConfiguration, parameters from TradePlan.entryIntent).
- `ExecuteTradeService` is the natural gate — called via `POST /executions/{id}/execute`.
- Angular `ExecutionService.execute()` calls this endpoint.
- **No frontend delegation risk** — T1 runs in Trading Core before any broker call.
- **Interval minimization** — T1 should run synchronously in the execute request (or as immediate pre-step) to minimize T1→broker window.

**Implementation Impact:**
- `ExecuteTradeService` has no transaction boundary (good for D2). Adding T1 must not introduce `@Transactional` spanning broker call.
- T1 loads fresh facts (broker/market) and runs `RiskEngine` before `BrokerSubmissionStep`.
- T1 result persisted before any broker command is prepared.

```
D11_STATUS = HUMAN_APPROVED
D11_IMPLEMENTATION = T1 as mandatory server-side pre-execution step in Trading Core, synchronous in execute request
```

---

## D12–D15 Decision Packet

### D12 — Execution-Time Authoritative Risk Policy

**Status:** `HUMAN_APPROVED`

**Question:** Should T1 use the exact rule-set/profile version used at T0, the currently effective risk policy, or another semantic?

---

**Approved Decision:**

T0 and T1 are separate temporal safety decisions.

```
T0:
Was the TradePlan acceptable according to
the authoritative policy and facts applicable at T0?

T1:
Is this human-authorized financial command still safe
according to the authoritative policy and facts
applicable NOW, at execution time?
```

---

**T0 Policy Semantics:**

T0 remains permanently associated with the immutable risk profile version that was authoritative when the TradePlan was evaluated. Never reinterpret or mutate the historical T0 evaluation. The T0 profile/version remains part of historical provenance.

```
T0
├── authoritative policy at T0
├── facts at T0
├── deterministic evaluation
└── immutable provenance
```

---

**T1 Policy Semantics:**

At T1, Trading Core MUST independently resolve the authoritative risk profile assignment applicable to the account at execution time.

```
T1 =
    authoritative current account risk policy
    +
    fresh authoritative execution-time facts
```

NOT:

```
T1 =
    historical T0 policy
    +
    fresh facts
```

unless the current authoritative policy happens to still be the same profile/version.

---

**Critical Invariant:**

Historical approval MUST NOT silently override a stricter risk policy that has legitimately become authoritative before execution.

Example:

```
T0
profile v3
max risk = 2%
trade risk = 1.5%
→ APPROVED

later:
account authoritative profile becomes v4
max risk = 1%

T1
profile v4
trade risk = 1.5%
→ REJECTED

NO FINANCIAL COMMAND
```

This is intentional. T1 is an execution-time safety gate, not merely a freshness check performed against historical policy.

---

**More-Permissive Policy Scenario:**

```
T0 v3 = APPROVED

later:
v4 becomes legitimately authoritative
and is more permissive

T1 v4 = APPROVED
```

Execution MAY continue because BOTH safety gates approved:

```
ExecutionAllowed =
    HumanAuthorized
    AND T0_APPROVED
    AND T1_APPROVED
```

However:

```
T0_REJECTED
    +
T1 would approve under newer policy
```

MUST NOT resurrect the rejected TradePlan. T0 approval remains a mandatory prerequisite. Therefore: T1 cannot rescue T0 rejection.

---

**Authoritative T1 Policy Resolution:**

"Current policy" does NOT mean:

- latest database row
- caller-selected profile
- frontend-selected profile
- AI-selected profile
- profile/version supplied by execution request

It means: the authoritative account risk profile assignment resolved server-side by Trading Core according to the existing Risk Domain / account risk configuration semantics.

No caller may choose the T1 policy.

```
CALLER_SELECTS_T1_POLICY = NO
FRONTEND_SELECTS_T1_POLICY = NO
AI_SELECTS_T1_POLICY = NO
TRADING_CORE_RESOLVES_T1_POLICY = YES
```

---

**Temporal Resolution Point:**

At the beginning of T1 context construction/evaluation, Trading Core resolves the authoritative account risk profile assignment. That resolved immutable profile version becomes the policy snapshot for that T1 evaluation.

```
Human Execute
    ↓
ExecutionIntent
    ↓
T1 begins
    ↓
resolve authoritative account profile
    ↓
freeze profile/version for this T1 evaluation
    ↓
load/build fresh authoritative facts
    ↓
RiskEngine
    ↓
T1 decision
```

Do NOT continuously re-resolve the profile during the same T1 evaluation. If policy changes after the T1 policy snapshot but before broker submission, that is a separate temporal/race consideration. D11 already requires minimizing the T1 → financial-command interval.

---

**Policy Drift is Auditable Information:**

A difference between T0 and T1 profile versions is NOT inherently an error. It is meaningful provenance.

```
T0
profile = profile-X:v3
decision = APPROVED

T1
profile = profile-X:v4
decision = REJECTED
```

The system must eventually be able to reconstruct:

- T0 profile identity
- T0 policy/rule versions
- T0 facts/provenance
- T0 decision
- T1 profile identity
- T1 policy/rule versions
- T1 facts/provenance
- T1 decision
- whether policy identity changed

The architectural requirement is reconstructability, not a specific schema field.

---

**Determinism Clarification:**

Determinism does NOT require T0 and T1 to use the same policy. These are different deterministic evaluations.

```
Evaluation(
    policySnapshot,
    factsSnapshot
)
→ deterministic result
```

Therefore:

```
(policy v3 + facts F0)
```

and:

```
(policy v4 + facts F1)
```

are different inputs. Both evaluations can independently satisfy:

```
same inputs → same result
```

Do NOT claim that changing policy between T0 and T1 violates deterministic evaluation.

---

**Fail-Closed Policy Resolution:**

If Trading Core cannot resolve the authoritative T1 risk policy:

```
NO T1 APPROVAL
        ↓
NO FINANCIAL COMMAND
```

Examples may include: missing assignment; referenced profile version missing; corrupted configuration; incomplete profile; policy resolution infrastructure failure.

This does NOT mean broker `UNKNOWN`. No financial side effect has occurred yet. Exact D14 failure-state semantics remain OPEN.

---

**Repository Evidence:**

- `RiskProfileEntity` is `@Immutable` with composite key `(UUID id, String semanticVersion)` — profiles are versioned and never mutated.
- `AccountRiskProfileAssignmentEntity` links account to current profile version.
- `RiskPersistence.assignedProfile()` loads current assignment then loads profile by composite key.
- `StoredEvaluation` persists T0 profile identity in provenance.
- `EffectiveRiskRuleSet` carries `policyVersions` map for audit.

---

**Implementation Impact:**

- `RiskPersistence.assignedProfile()` already resolves the current authoritative profile. T1 reuses this existing method.
- No new persistence method needed for policy resolution (unlike the rejected Option A which would have required `profileByIdAndVersion`).
- T1 provenance must store the resolved profile identity for drift reconstructability.

---

**Rejected Alternatives:**

- **Option A (T0 Profile Version):** REJECTED_BY_HUMAN. Prevents legitimate policy updates from affecting pending executions. Violates the principle that T1 is an execution-time safety gate checking current reality.
- **Option C (Hybrid — load both, enforce current):** REJECTED_BY_HUMAN. Adds unnecessary complexity. If current policy is authoritative, loading T0 version is redundant.

```
D12_STATUS = HUMAN_APPROVED
D12_SELECTED = EXECUTION_TIME_AUTHORITATIVE_POLICY
D12_T0_POLICY = HISTORICAL_IMMUTABLE_POLICY
D12_T1_POLICY = CURRENT_AUTHORITATIVE_ACCOUNT_POLICY
T0_APPROVAL_REQUIRED = YES
T1_APPROVAL_REQUIRED = YES
T1_POLICY_RESOLUTION = SERVER_SIDE_TRADING_CORE
CALLER_SELECTS_T1_POLICY = NO
POLICY_DRIFT_ALLOWED = YES
POLICY_DRIFT_AUDITABLE = YES
T1_POLICY_RESOLUTION_FAILURE = FAIL_CLOSED
```

---

### D13 — Deterministic T1 Risk Rejection State

**Status:** `HUMAN_APPROVED`

**Question:** When the human explicitly requests execution, an ExecutionIntent already exists, and T1 then returns a deterministic REJECTED decision, what durable execution-domain state should represent that outcome?

---

**Approved Decision:**

When:
- `ExecutionIntent` already exists
- Human explicitly requests execution
- T1 executes successfully
- T1 decision = REJECTED

Then the `ExecutionIntent` MUST transition to a dedicated terminal execution-domain state:

```
RISK_REVALIDATION_REJECTED
```

Conceptually:

```
ExecutionIntent VALIDATED
        ↓
Human Execute
        ↓
T1 deterministic evaluation
        ↓
REJECTED
        ↓
ExecutionIntent RISK_REVALIDATION_REJECTED
        ↓
TERMINAL
        ↓
NO BROKER COMMAND
```

---

**Semantic Distinction:**

This is a deterministic business/safety rejection. It is NOT:

- broker rejection
- infrastructure failure
- risk engine unavailable
- timeout
- stale-facts failure
- broker UNKNOWN
- execution attempt

The system did not fail to evaluate risk. The trade was rejected by a successful deterministic risk decision.

```
RiskEngine completed successfully
        ↓
decision = REJECTED
```

D14 will separately govern situations where T1 cannot produce a trustworthy decision.

---

**Status Name:**

```
RISK_REVALIDATION_REJECTED
```

Do NOT use `RISK_REVALIDATION_FAILED` for this D13 case.

---

**Terminal Semantics:**

`RISK_REVALIDATION_REJECTED` is terminal for that `ExecutionIntent`.

```
SAME_INTENT_RETRY_AFTER_T1_REJECTION = FORBIDDEN
```

A later change in market price, equity, exposure, positions, margin, account risk policy, or other risk-relevant facts MUST NOT make the same rejected `ExecutionIntent` executable again.

Do NOT allow:

```
RISK_REVALIDATION_REJECTED → VALIDATED
```

or any equivalent resurrection.

---

**Human Authorization Semantics:**

D4 remains authoritative: one human authorization corresponds to one logical execution authorization.

```
Human Execute
    ↓
T1 REJECTED
```

consumes/terminates that logical execution authorization.

If the user wants to attempt execution again later, a new execution authorization cycle is required:

```
new ExecutionIntent
+
fresh human Execute authorization
+
fresh T1
```

Do NOT overstate D13 as necessarily requiring a new TradePlan or new T0 evaluation unless existing repository invariants already require them. The exact reuse validity of the existing TradePlan/T0 remains governed by existing execution/risk rules. D13 only decides that the SAME `ExecutionIntent` cannot be retried after deterministic T1 rejection.

---

**No ExecutionAttempt:**

A T1 rejection occurs before any broker submission attempt.

```
ExecutionAttempt created for T1 rejection = NO
```

Do not model T1 rejection as an `ExecutionAttempt`. Preserve `ExecutionAttempt = financial/broker submission attempt semantics`.

---

**Audit Semantics:**

The durable lifecycle must allow reconstruction of:

```
T0 approval
    ↓
ExecutionIntent
    ↓
human Execute authorization
    ↓
T1 evaluation
    ↓
T1 REJECTED
    ↓
ExecutionIntent RISK_REVALIDATION_REJECTED
```

The T1 evaluation details/provenance will be finalized under D15.

---

**Event Semantics:**

Following existing `ExecutionEvent` naming conventions:
- Lifecycle events: `ExecutionIntent{Created|Validated|Cancelled}`
- D13 natural event name: `ExecutionIntentRiskRejected` (or `ExecutionRiskRevalidationRejected`)

To be finalized during implementation. Minimum design consequence: one new `ExecutionEvent` type representing this transition.

---

**Repository Evidence:**

- `ExecutionIntent` terminal states: `COMPLETED`, `CANCELLED`, `EXPIRED`
- `FAILED` → `VALIDATED` (retry enabled, for broker rejection)
- `ExecutionAttempt` represents broker submission attempt (T1 rejection occurs BEFORE any broker call)
- `ExecutionValidationService` checks `riskApproval != null` but does NOT re-verify against persistence
- D11: `ExecutionIntent` exists ≠ broker execution authorized

---

**Rejected Alternatives:**

- **Option A (Leave intent VALIDATED):** REJECTED_BY_HUMAN. Leaves intent in executable state, bypasses T1 gate.
- **Option C (Generic FAILED):** REJECTED_BY_HUMAN. Conflates with broker rejection, retryable on same authorization inappropriate for deterministic risk rejection.
- **Option D (ExecutionAttempt):** REJECTED_BY_HUMAN. Violates `ExecutionAttempt` semantic meaning; no broker submission occurred.

```
D13_STATUS = HUMAN_APPROVED
D13_SELECTED = TERMINAL_RISK_REJECTION_STATE
D13_STATUS_NAME = RISK_REVALIDATION_REJECTED

D13_T1_RESULT = DETERMINISTIC_REJECTION
D13_TERMINAL = YES

D13_SAME_INTENT_RETRY = FORBIDDEN
D13_NEW_EXECUTION_AUTHORIZATION_REQUIRED = YES

D13_EXECUTION_ATTEMPT_CREATED = NO
D13_BROKER_COMMAND_ALLOWED = NO
```

---

### D14 — T1 Unavailable / Timeout / Stale Facts

**Question:** When T1 cannot produce a trustworthy deterministic risk decision because required execution-time context or infrastructure is unavailable, stale, inconsistent, or times out — before any broker financial command has been submitted — what execution-domain semantics and retry behavior should apply?

---

**Fundamental Invariant:**

```
NO TRUSTWORTHY T1 APPROVAL
        ↓
NO FINANCIAL COMMAND
```

Fail closed. Explicitly:

```
BROKER_SUBMISSION_ALLOWED = NO
BROKER_UNKNOWN = NO
```

ADR-041 D6 financial-side-effect UNKNOWN/reconciliation semantics MUST NOT be reused for a pre-command T1 failure. No broker command has been submitted, so there is nothing to reconcile.

---

**Repository Evidence:**

**ExecutionStatus vocabulary (10 values):**
- Terminal: `COMPLETED`, `CANCELLED`, `EXPIRED`
- Non-terminal retryable: `FAILED` (→ `VALIDATED`), `VALIDATED`
- Non-terminal non-retryable: `RECOVERY_BLOCKED`
- Active: `SUBMISSION_IN_PROGRESS`, `SUBMISSION_OUTCOME_UNKNOWN`, `RECONCILIATION_IN_PROGRESS`

**T0 ContextUnavailable pattern:**
- Private inner exception in `TradePlanRiskEvaluationService`
- Caught → produces `CONTEXT_UNAVAILABLE` response (status string, not a `RiskDecision`)
- 20+ specific failure codes: `ACCOUNT_RISK_CONFIGURATION_MISSING`, `BROKER_RISK_FACTS_INCOMPLETE`, `REQUIRED_MARGIN_UNAVAILABLE`, `EFFECTIVE_RISK_PROFILE_INVALID`, etc.

**Port unavailability patterns:**
- `BrokerRiskFactsPort`: throws on hard failure, `complete` flag + `unavailabilityReasons` for partial
- `MarketValuationPort`: `complete` flag, fact-level `status` field (`"AVAILABLE"`), `maxObservationAge`
- `RequiredMarginPort`: `Optional.empty()` on unavailability

**Freshness semantics:**
- Market Intelligence: extensive (`FreshnessPolicy`, `staleTolerance`, `FRESH/STALE/EXPIRED`)
- Risk evaluation: basic temporal validation (`observedAt` within risk day)
- Execution domain: **ABSENT** — no freshness requirements

**Timeout:**
- `ExecutionAttempt.timeout()` exists but is NOT wired into lifecycle
- T1 timeout is conceptually different from broker submission timeout

**Error handling:**
- `ExecutionExceptionHandler` has no handler for risk-domain exceptions
- `RiskEvaluationException` carries HTTP status but would fall through to 500 in execution context

**Retry:** Only `FAILED` and `VALIDATED` are retryable via `RetryExecutionService`.

---

**Failure Taxonomy:**

| Code | Nature | T1 Decision? | Broker Command? | Example |
|------|--------|-------------|-----------------|---------|
| `DETERMINISTIC_RISK_REJECTION` | Successful risk evaluation | Yes (REJECTED) | Never | Risk rules exceeded (D13) |
| `CONTEXT_UNAVAILABLE` | Infrastructure failure | No | No | Broker down, market data service down |
| `CONTEXT_STALE` | Data quality | No | No | Facts exist but too old |
| `POLICY_RESOLUTION_FAILURE` | Configuration error | No | No | Missing profile, incomplete rules |
| `RISK_EVALUATION_TECHNICAL_FAILURE` | Engine failure | No | No | Context construction error, engine exception |
| `TIMEOUT` | Infrastructure timeout | No | No | Risk evaluation exceeded time budget |

D13 governs `DETERMINISTIC_RISK_REJECTION`. D14 governs all others.

---

## Axis 1 — Durable Semantic State

### Option A — Dedicated non-terminal `RISK_REVALIDATION_UNAVAILABLE`

New `ExecutionStatus` value. Non-terminal. Intent remains in this state until explicitly retried or cancelled.

**State transition:**
```
VALIDATED → RISK_REVALIDATION_UNAVAILABLE (on T1 unavailable/timeout/stale/config)
```

**Retry path:**
```
RISK_REVALIDATION_UNAVAILABLE → VALIDATED (via explicit human retry)
```

**Safety implications:** Non-terminal allows retry, which is correct for transient failures. But also allows retry for permanent configuration failures (futile but not unsafe). Cancel always available.

**Audit implications:** Clear distinction from `RISK_REVALIDATION_REJECTED` (D13). Reason code stored in event/provenance. Audit query: "T1 could not evaluate" vs "T1 evaluated and rejected."

**Human-authority implications:** Retry requires explicit human action (`POST /{id}/retry`). No automatic retry. Human re-authorizes by clicking retry.

**Retry implications:** Same intent retryable. Each retry creates new `ExecutionAttempt`, runs fresh T1. Transient failures may succeed on retry. Permanent failures will fail again.

**Complexity:** One new enum value. One new transition. One new event type. `RetryExecutionService` updated to accept this status.

---

### Option B — Generic `FAILED` with structured reason

Use existing `FAILED` status. Persist T1 unavailability reason/provenance separately.

**Safety implications:** `FAILED` is retryable (`FAILED → VALIDATED`). Same retry behavior as Option A. But `FAILED` currently means "broker rejected order" — conflation with T1 unavailability is semantically misleading.

**Audit implications:** Audit query "why did this intent fail?" returns `FAILED` for both broker rejection and T1 unavailability. Requires additional lookup to distinguish. Loses the semantic distinction between "broker said no" and "risk engine couldn't evaluate."

**Human-authority implications:** Same as Option A — retry requires explicit human action.

**Retry implications:** Same as Option A. `RetryExecutionService` already accepts `FAILED`.

**Complexity:** No new status. But semantic conflation. Existing `FAILED` event semantics (`ExecutionAttemptFailed`) don't fit T1 (no attempt was made).

---

### Option C — Dedicated terminal `RISK_REVALIDATION_UNAVAILABLE`

New `ExecutionStatus` value. Terminal. Same intent cannot be retried.

**Safety implications:** Strongest safety. Prevents any retry on same authorization. But consumes execution authorization for transient infrastructure failures. User must create entirely new intent.

**Audit implications:** Clean terminal state. But audit shows "T1 unavailable" as terminal, which may be confusing for transient failures that could have succeeded on retry.

**Human-authority implications:** Most conservative. Every retry requires new `POST /executions/validate` + new Execute click. Strongest D4 alignment but potentially frustrating UX.

**Retry implications:** NOT retryable on same intent. User must create new intent. Correct for permanent configuration failures. Overly restrictive for transient broker downtime.

**Complexity:** One new enum value. Terminal. No retry path needed.

---

### Option D — No intent transition; persist T1 failure separately

Leave `ExecutionIntent` in `VALIDATED`. Persist T1 failure in a separate record. Return error to caller.

**Safety implications:** `VALIDATED` is the state from which `ExecuteTradeService` proceeds to broker submission. Leaving intent in `VALIDATED` after T1 failure falsely represents it as executable. The next `POST /{id}/execute` call would re-run T1 (if T1 gate is in the pipeline). But if T1 gate is NOT in the retry path, broker submission could proceed without T1. **RISKY** — depends on implementation.

**Audit implications:** No terminal marker on intent. T1 failure only visible in separate record. Intent audit trail incomplete.

**Human-authority implications:** Intent remains executable. Human can retry by calling execute again. But no explicit "retry" action — just re-executing.

**Retry implications:** Implicitly retryable (intent stays VALIDATED). But no explicit retry UX. May confuse users.

**Complexity:** Minimal. But semantic correctness depends on T1 being in the execute pipeline path.

---

### Option E — Another repository-consistent state

No evidence from repository analysis justifies another option beyond A–D.

---

## Axis 2 — Retry Models

### R1 — Terminal / New Authorization

```
Human Execute
    ↓
T1 unavailable
    ↓
intent terminal
    ↓
user must create new execution authorization later
```

**Analysis:**
- Strongest human-authority semantics
- No stale authorization reuse
- Simplest lifecycle
- Cost: transient infrastructure problem consumes execution authorization
- Potentially frustrating UX for transient broker downtime

**Compatible with:** Option C (terminal state)

---

### R2 — Same Intent, Explicit Human Retry

```
Human Execute
    ↓
T1 unavailable
    ↓
intent blocked (non-terminal)
    ↓
later user explicitly clicks Retry Execute
    ↓
fresh T1
```

**Analysis:**
- Requires explicit human action — no automatic retry
- Second click constitutes fresh human authorization while preserving same logical `ExecutionIntent`
- Compatible with D4: human explicitly re-authorizes by clicking retry
- Transient failures may succeed on retry
- Permanent failures will fail again (user learns to cancel)
- Cost: slightly weaker D4 alignment than R1 (same intent, not new intent)

**Compatible with:** Option A (non-terminal state)

**Important:** This MUST require explicit human action. No automatic broker submission. No automatic execution after infrastructure recovers.

---

### R3 — Automatic T1 Retries Inside Bounded Execute Request

```
one human Execute
    ↓
small bounded technical retry
    ↓
still same immediate command attempt
```

**Analysis:**
- Could be acceptable for extremely short transient acquisition failures
- Risks: facts changing during retries, authorization aging, T1→broker interval, hidden repeated infrastructure activity
- Similar to HTTP retry semantics — user doesn't know about them
- For very short timeouts (e.g., single retry after 100ms), may be acceptable
- For longer failures, dangerous

**Compatible with:** Option A or D (non-terminal states)

**Recommendation:** Do NOT approve as a general mechanism. May be acceptable for specific extremely short transient failures during implementation discovery. Requires explicit human approval if wired.

---

### R4 — Background Retry

```
human Execute
    ↓
T1 unavailable
    ↓
later system retries automatically
    ↓
broker submission
```

**Analysis:**
- Incompatible with D1 (human authority), D4 (one authorization = one execution), D11 (T1 is mandatory gate)
- Background retry after time passes means stale human authorization
- Market conditions change during wait
- No explicit human action connected to eventual submission

**Verdict:** REJECTED — incompatible with D1/D4/D11.

---

## Freshness Findings

```
FACT_FRESHNESS_SEMANTICS = PARTIAL
```

**Existing support:**
- Market Intelligence: extensive (`FreshnessPolicy`, `staleTolerance`, `FRESH/STALE/EXPIRED`, configurable `stale-after:30s`)
- Risk evaluation: basic (`observedAt` within risk day, `fact.observedAt().isAfter(asOf)`)
- Broker facts: `observedAt` timestamp, `complete` flag, `unavailabilityReasons`
- Market valuation: `capturedAt`, `maxObservationAge`, fact-level `status`

**Missing:**
- Execution domain: no freshness requirements
- T1: no staleness threshold for broker/market facts
- No configurable `max-age` for T1 fact freshness

**Implication:** T1 freshness policy must be designed during implementation. D14 should not invent arbitrary thresholds. The `CONTEXT_STALE` reason code provides the semantic hook; the actual threshold is an implementation concern.

---

**D14 Failure Sub-Categories:**

| Category | Examples | Transient? | Same-Intent Retry? |
|----------|----------|-----------|-------------------|
| Infrastructure unavailable | Broker down, market service down | Likely yes | Yes (R2) |
| Fact stale | Broker snapshot too old, market data expired | Depends | Yes (R2) — fresh facts on retry |
| Policy resolution failure | Missing assignment, incomplete profile | No | No — configuration error |
| Technical failure | RiskEngine exception, context construction error | Depends | Case-by-case |
| Timeout | T1 evaluation exceeded budget | Likely yes | Yes (R2) |

---

**HUMAN_APPROVED:** **Option A — Non-terminal `RISK_REVALIDATION_UNAVAILABLE` + R2 Explicit Human Retry**

**Why:**
1. **Semantic precision:** Clearly distinguishes from `RISK_REVALIDATION_REJECTED` (D13) and `FAILED` (broker rejection)
2. **Retry flexibility:** Non-terminal allows explicit human retry for transient failures
3. **Cancel always available:** User can always cancel if they don't want to retry
4. **Reason codes:** Structured reason codes distinguish transient from permanent failures
5. **Consistency:** Follows existing pattern of `SUBMISSION_OUTCOME_UNKNOWN` (non-terminal, reason-coded)

**Recommended status name:** `RISK_REVALIDATION_UNAVAILABLE`

**Recommended reason-code model:**

```
RISK_REVALIDATION_UNAVAILABLE
├── reasonCode: String (CONTEXT_UNAVAILABLE | CONTEXT_STALE | POLICY_RESOLUTION_FAILURE
│                      | RISK_EVALUATION_TECHNICAL_FAILURE | TIMEOUT)
├── retryable: boolean (derived from reason code)
└── occurredAt: Instant
```

Reason code taxonomy:
- `CONTEXT_UNAVAILABLE` — broker/market infrastructure unavailable (retryable: yes)
- `CONTEXT_STALE` — facts present but too old (retryable: yes — fresh facts on retry)
- `POLICY_RESOLUTION_FAILURE` — missing profile, incomplete config (retryable: no)
- `RISK_EVALUATION_TECHNICAL_FAILURE` — engine exception (retryable: depends)
- `TIMEOUT` — evaluation exceeded time budget (retryable: yes)

**Recommended retry semantics:**
- Same intent: retryable via explicit human action (`POST /{id}/retry`)
- New intent: always possible (user creates new validation + execute)
- Automatic retry: NOT allowed
- Bounded immediate retry: potentially acceptable for specific short transient failures (requires implementation discovery + human approval)

**Why (trade-offs):**
- Safety: fail-closed preserved. No broker command without trustworthy T1.
- UX: transient failures don't consume execution authorization. User can retry.
- Audit: clear distinction between "couldn't evaluate" and "evaluated and rejected"
- D4: explicit human retry constitutes fresh authorization for same logical intent
- Complexity: one new status, reason codes, retry path. Minimal.

```
D14_STATUS = HUMAN_APPROVED
D14_SELECTED_STATE = RISK_REVALIDATION_UNAVAILABLE
D14_TERMINAL = NO

D14_SAME_INTENT_RETRY = YES
D14_EXPLICIT_HUMAN_RETRY_REQUIRED = YES
D14_FRESH_T1_ON_RETRY = YES

D14_BACKGROUND_RETRY = FORBIDDEN
D14_BOUNDED_IMMEDIATE_RETRY = FORBIDDEN_FOR_STORY_0034

D14_BROKER_UNKNOWN = NO
D14_RECONCILIATION = NO

D14_RETRY_MODEL = R2_EXPLICIT_HUMAN_RETRY
D14_R3_APPROVED = NO
D14_R4_APPROVED = NO
```

---

### D15 — T1 Audit Persistence

**Question:** What durable model should represent T1 risk evaluation attempts and their complete audit provenance while keeping T0 and T1 semantically distinct, queryable, reconstructable, and consistent with the existing Risk Domain and Execution Domain?

---

## Repository Evidence

**T0 Persistence Model (5 tables):**

| Table | Key Fields | Purpose |
|-------|-----------|---------|
| `risk_evaluation` | `id` (UUID PK), `actor_id`, `idempotency_key`, `trade_plan_id/version`, `account_id`, `status`, `decision`, `context_snapshot_version`, `result_payload` (TEXT), `response_payload` (TEXT) | Core T0 evaluation record |
| `risk_component_snapshot` | `version` (BIGINT auto PK), `evaluation_id`, `component_type` (ACCOUNT/PORTFOLIO/MARKET/RULE_SET), `source_version`, `captured_at`, `payload` (TEXT) | T0 provenance components (4 per evaluation) |
| `risk_context_snapshot` | `version` (BIGINT auto PK), `evaluation_id` (UNIQUE), `captured_at`, `payload` (TEXT) | T0 full evaluation context |
| `risk_day_baseline` | `version` (BIGINT auto PK), `account_id`, `risk_day`, `amount`, `payload` (TEXT) | Daily baseline (shared) |
| `risk_acknowledgment_outbox` | `evaluation_id` (UUID PK), `trade_plan_id/version`, `decision`, `status` | Acknowledgment delivery (shared) |

**T0 Provenance Assembly (from `TradePlanRiskEvaluationService`):**
```java
persistence.component(evaluationId, "ACCOUNT", "broker:" + sourceVersion, observedAt, accountPayload);
persistence.component(evaluationId, "PORTFOLIO", "broker:" + sourceVersion, observedAt, portfolioPayload);
persistence.component(evaluationId, "MARKET", "market-data:" + sourceVersion, capturedAt, marketPayload);
persistence.component(evaluationId, "RULE_SET", profileId + ":" + profileVersion, requestedAt, ruleSetPayload);
persistence.context(evaluationId, requestedAt, fullContextPayload);
```

**T0 StoredEvaluation record:**
```java
record StoredEvaluation(UUID id, UUID tradePlanId, long tradePlanVersion,
                        UUID accountId, String status, String decision,
                        Response response) { }
```
Note: `StoredEvaluation` is a read-side projection. It does NOT contain full provenance. Full provenance is in `risk_component_snapshot` + `risk_context_snapshot`.

**Execution Intent:**
```java
// ExecutionIntentEntity
riskEvaluationId  // UUID, NOT NULL, UPDATABLE=false → T0 evaluation ID
riskDecision      // String, NOT NULL, UPDATABLE=false → T0 decision
riskApprovedAt    // Instant, NOT NULL, UPDATABLE=false → T0 approval timestamp
```

**RiskApprovalReference (value object on ExecutionIntent):**
```java
record RiskApprovalReference(UUID evaluationId, Decision decision, Instant approvedAt)
// Decision enum: APPROVED, APPROVED_WITH_WARNINGS
```

**Execution Attempt:**
```java
// ExecutionAttemptEntity
id, intentId, attemptNumber, status, brokerCorrelationId, resultCode, timestamps
// No T1 reference currently
```

**Execution Events:**
- Immutable append-only: `ExecutionEventEntity` with `intent_id`, `event_type`, `occurred_at`, `payload` (TEXT, max 2000)
- 20+ event types in sealed interface

**RiskValidationResult (domain model):**
```java
record RiskValidationResult(
    EvaluationStatus evaluationStatus,    // COMPLETED, INCOMPLETE, FAILED
    Optional<RiskDecision> decision,      // APPROVED, APPROVED_WITH_WARNINGS, REJECTED
    List<RiskRuleResult> ruleResults,
    List<RiskRuleResult> violations,
    List<RiskRuleResult> warnings,
    RiskMetrics globalMetrics,
    ValidationMode evaluationMode,
    Duration evaluationDuration,
    Instant evaluatedAt,
    TraceMetadata trace
)
```

**TraceMetadata (domain model):**
```java
record TraceMetadata(
    UUID evaluationId, UUID correlationId, String engineVersion,
    Map<String, String> policyVersions,    // {"policy-id": "policy-version"}
    Map<String, String> ruleVersions,      // {"rule-id": "rule-version"}
    ContextMetadata context
)
```

**ContextMetadata (domain model):**
```java
record ContextMetadata(
    UUID accountId, long accountVersion, UUID portfolioId, long portfolioVersion,
    long marketVersion, long ruleSetVersion,
    Instant accountCapturedAt, Instant portfolioCapturedAt,
    Instant marketCapturedAt, Instant ruleSetCapturedAt
)
```

**EffectiveRiskRuleSet (domain model):**
```java
record EffectiveRiskRuleSet(List<RuleConfiguration> rules, Map<String, String> policyVersions)
```

**Ownership Boundaries:**
- Risk Domain owns: `RiskEngine`, `RiskValidationResult`, `TraceMetadata`, `EffectiveRiskRuleSet`, `RuleConfiguration`
- Risk Domain infrastructure owns: `RiskPersistence` (T0 tables, profile resolution, component snapshots)
- Execution Domain owns: `ExecutionIntent`, `ExecutionAttempt`, `ExecutionEvent`, `ExecutionStatus`
- Trading Core application orchestrates both

---

## Domain Finding

```
T0_AND_T1_DOMAIN_RELATION = SAME_RISK_EVALUATION_CONCEPT_DIFFERENT_LIFECYCLE_PURPOSE
```

T0 and T1 are the same domain concept (deterministic risk evaluation producing a `RiskValidationResult`) occurring at different lifecycle stages for different purposes:

- **T0**: Preparation-time evaluation answering "Is this TradePlan acceptable?" → gates execution authorization
- **T1**: Execution-time evaluation answering "Is this human-authorized financial command still safe NOW?" → gates broker submission

Both use the same `RiskEngine`, same domain types (`RiskValidationResult`, `RiskDecision`, `TraceMetadata`), same deterministic semantics.

**Key differences:**

| Dimension | T0 | T1 |
|-----------|----|----|
| Trigger | Trade plan analysis | Human Execute click |
| Subject | Trade plan | Execution intent (human-authorized command) |
| Policy | Authoritative at T0 | Authoritative at T1 (independently resolved) |
| Facts | T0 fact snapshot | Fresh execution-time facts |
| Linkage | `trade_plan_id/version` | `execution_intent_id` + optional `t0_evaluation_id` |
| Purpose | Gate execution authorization | Gate broker submission |
| Attempts | Typically one per idempotency key | Multiple per execution intent (D14) |
| Unavailable | `CONTEXT_UNAVAILABLE` status | `RISK_REVALIDATION_UNAVAILABLE` (D14) |
| Rejection | Risk decision → no ExecutionIntent created | Risk decision → terminal intent state (D13) |

---

## Required Invariants

```
D15_I1: T0 and T1 must remain separately queryable and distinguishable
D15_I2: Each T1 attempt must be immutable after completion (append-only)
D15_I3: Multiple T1 attempts per ExecutionIntent must be supported (D14)
D15_I4: T1 APPROVED must be durably persisted BEFORE broker submission may begin
D15_I5: Policy drift between T0 and T1 must be reconstructable from persisted identities
D15_I6: Full provenance (facts, policy, rules, context) must be persistable for each T1 attempt
D15_I7: The exact T1 approval that authorized broker submission must be reconstructable
D15_I8: T1 UNAVAILABLE (D14) must be durable attempts, not only logs/errors
D15_I9: T1 REJECTED (D13) must be durable attempts with full provenance
D15_I10: ExecutionIntent lifecycle state must reflect T1 outcome without storing T1 details
```

---

## Option A — Dedicated T1 Evaluation Model/Table

**Concept:**

```text
risk_evaluation          // T0 (existing)
risk_evaluation_t1       // T1 (new, dedicated)
```

T1 evaluation table mirrors T0 structure with execution-specific fields.

**Domain semantics:** Clear separation. T0 and T1 are distinct persistence artifacts with distinct tables, distinct query paths, distinct lifecycle.

**Auditability:** Each T1 attempt is a separate row. Full provenance via `risk_component_snapshot` + `risk_context_snapshot` (reusing existing proven pattern).

**Multiple T1 attempts:** Natural. Each attempt = new row in `risk_evaluation_t1`.

**T0/T1 distinction:** Explicit via separate tables. No ambiguity.

**Ownership:** T1 table lives in Risk Domain infrastructure (alongside T0). Execution Domain references T1 by ID.

**Atomicity:**
```
persist T1 row + component snapshots + context snapshot
    ↓
update ExecutionIntent status
    ↓
if APPROVED: create ExecutionAttempt with t1EvaluationId
    ↓ (outside transaction)
broker submission
```
T1 durable BEFORE broker call. Compliant with D15_I4.

**Queryability:**
- `findByExecutionIntentId(intentId)` → all T1 attempts for an intent
- `findById(t1EvaluationId)` → specific T1 evaluation
- T0 and T1 join via `execution_intent.risk_evaluation_id` (T0) + `risk_evaluation_t1.execution_intent_id` (T1)

**Implementation scope:** MODERATE
- New table + Flyway migration
- New JPA entity
- New persistence methods on `RiskPersistence`
- New `ExecutionTimeRiskRevalidationService`
- Modifications to `ExecutionIntent` (status, transition map)
- Modifications to execution flow
- Test coverage

**Long-term evolution:** Later generalization possible — extract common `RiskEvaluationRecord` base, make T0/T1 specializations. But this is a future concern, not Story 0034 scope.

---

## Option B — Generalized Common Evaluation Model

**Concept:**

```text
risk_evaluation
    id
    phase = T0 | T1          // discriminator
    trade_plan_id (nullable)  // T0 only
    execution_intent_id (nullable)  // T1 only
    t0_evaluation_id (nullable)  // T1 only
    ...
```

**Domain semantics:** Architecturally cleaner — one canonical risk evaluation model. But requires nullable/conditional fields.

**Auditability:** Same as Option A. Full provenance via component snapshots.

**Multiple T1 attempts:** Same as Option A. Each attempt = new row.

**T0/T1 distinction:** Via `phase` discriminator column. Less explicit than separate tables but queryable.

**Ownership:** Single table owned by Risk Domain. Execution Domain references by ID.

**Atomicity:** Same as Option A.

**Queryability:**
- `findByExecutionIntentId(intentId)` → T1 attempts
- `findByPhase(phase)` → all T0 or all T1
- Requires COALESCE or conditional logic for phase-specific fields

**Implementation scope:** LARGE
- Migration of existing T0 data (risky in Story 0034)
- Nullable columns for phase-specific fields
- Updated persistence methods
- Updated `TradePlanRiskEvaluationService` (T0 path)
- Updated validation logic
- More test surface

**Long-term evolution:** Cleaner long-term. But migration complexity and Story 0034 scope pressure make this inappropriate for V1.

**Verdict:** Architecturally attractive but pragmatically too broad for Story 0034. Deferred to future generalization story.

---

## Option C — Common Immutable Core + Distinct T0/T1 Wrappers

**Concept:**

```text
RiskEvaluationSnapshot        // shared deterministic core
    evaluationId
    evaluationStatus
    decision
    ruleResults
    violations
    warnings
    globalMetrics
    evaluationMode
    evaluationDuration
    evaluatedAt
    trace (TraceMetadata)

T0StoredEvaluation            // T0-specific
    tradePlanId, tradePlanVersion, accountId
    idempotencyKey, actorId
    provenance (component snapshots)

T1ExecutionEvaluation         // T1-specific
    executionIntentId
    t0EvaluationId
    provenance (component snapshots)
```

**Domain semantics:** Clean separation of shared evaluation core from phase-specific identity/linkage.

**Auditability:** Same as Option A.

**Multiple T1 attempts:** Same as Option A.

**T0/T1 distinction:** Explicit via distinct wrapper types.

**Ownership:** Shared core in Risk Domain. Wrappers in respective domains.

**Atomicity:** Same as Option A.

**Queryability:** Same as Option A, but requires composition of core + wrapper for full view.

**Implementation scope:** MODERATE (similar to Option A)
- Requires both wrapper types and persistence for each
- More code than Option A for similar functionality

**Long-term evolution:** Better separation of concerns. But adds abstraction layer that may not be justified by current usage patterns.

**Verdict:** Clean but adds unnecessary abstraction for Story 0034. The repository already uses flat records (e.g., `StoredEvaluation`). Introducing wrapper composition is a larger design change.

---

## Option D — ExecutionEvent-Based Audit

**Concept:**

```text
ExecutionEvent.ExecutionTimeRiskRevalidated
    intentId, evaluationId, decision, evaluatedAt, policyVersions, ruleVersions
```

T1 primarily persisted as execution events.

**Domain semantics:** Execution events are already the audit trail mechanism. T1 fits naturally as an event type.

**Auditability:** Events are append-only, immutable, indexed by intent_id. Good for timeline queries.

**Multiple T1 attempts:** Natural. Each attempt = new event.

**T0/T1 distinction:** T0 is in Risk Domain tables. T1 is in Execution Events. Explicit separation.

**Ownership:** Execution events owned by Execution Domain. T1 evaluation semantics from Risk Domain.

**Atomicity:** Event persisted with intent status update. But event payload limited to 2000 chars — cannot store full provenance.

**Queryability:** Timeline query works. But detailed provenance lookup requires joining with separate tables.

**Implementation scope:** SMALL
- New event type
- Event payload contains decision + summary
- Full provenance still needs component snapshots

**Long-term evolution:** Events are supplementary. D10 requires queryable persisted evaluation with full provenance. Events alone are insufficient.

**Verdict:** Insufficient alone. Events are a supplementary audit mechanism, not the primary persistence. Full provenance (D10, D15_I6) requires dedicated persistence. Combine with Option A if desired.

---

## Option E — Store T1 Inside ExecutionIntent

**Concept:**

```text
ExecutionIntent
    t1EvaluationId        // latest T1 evaluation
    t1Decision            // latest T1 decision
    t1EvaluatedAt         // latest T1 timestamp
```

Or JSON history collection on the intent.

**Domain semantics:** T1 is tightly coupled to execution lifecycle. Storing on intent is natural.

**Auditability:** T1 details on intent. But intent is mutable aggregate — history overwrites.

**Multiple T1 attempts:** Problematic. Mutable fields overwrite previous attempts. JSON history adds complexity.

**T0/T1 distinction:** T0 in Risk Domain, T1 on ExecutionIntent. Explicit.

**Ownership:** T1 data on ExecutionIntent (Execution Domain). T1 evaluation semantics from Risk Domain.

**Atomicity:** Same as Option A. But intent is already a mutable aggregate with optimistic locking.

**Queryability:** Direct lookup via intent. But no independent T1 query path. Cannot query "all T1 evaluations for account X" without scanning intents.

**Implementation scope:** SMALL
- Add columns to ExecutionIntent
- Modify aggregate to carry T1 data
- Modify repository mapping

**Long-term evolution:** Anti-pattern for audit data. Mutable aggregate carrying audit history violates single-responsibility. Aggregate grows unbounded with retry history.

**Verdict:** Anti-pattern. ExecutionIntent is a lifecycle aggregate, not an audit store. Multiple attempts and full provenance make this impractical.

---

## Comparison Matrix

| Criterion | Option A (Dedicated) | Option B (Generalized) | Option C (Core+Wrappers) | Option D (Events) | Option E (Intent) |
|-----------|---------------------|----------------------|-------------------------|-------------------|-------------------|
| Semantic clarity | HIGH | HIGH | HIGH | MEDIUM | LOW |
| Audit completeness | HIGH | HIGH | HIGH | LOW | LOW |
| Multiple T1 attempts | HIGH | HIGH | HIGH | HIGH | LOW |
| Policy provenance | HIGH | HIGH | HIGH | LOW | MEDIUM |
| Exact broker-authorization linkage | HIGH | HIGH | HIGH | MEDIUM | MEDIUM |
| Domain ownership | CLEAR | CLEAR | CLEAR | MIXED | MIXED |
| Implementation complexity | MODERATE | LARGE | MODERATE | SMALL | SMALL |
| Migration risk | LOW | HIGH | LOW | LOW | LOW |
| Future extensibility | GOOD | EXCELLENT | GOOD | LIMITED | LIMITED |

---

## Recommended Option

**Option A — Dedicated T1 Evaluation Model/Table**

**Why:**
1. **Semantic clarity:** T0 and T1 are distinct persistence artifacts. No ambiguity.
2. **Audit completeness:** Full provenance via existing component snapshot pattern.
3. **Multiple attempts:** Natural. Each attempt = new row. Append-only.
4. **Policy provenance:** `policyVersions` in `TraceMetadata` enables drift reconstruction.
5. **Broker-authorization linkage:** `ExecutionAttempt` can reference `t1EvaluationId`.
6. **Ownership:** T1 table in Risk Domain infrastructure. Execution Domain references by ID.
7. **Implementation scope:** MODERATE — follows proven T0 patterns.
8. **Migration risk:** LOW — no migration of existing data.
9. **Future extensibility:** Later generalization possible (Option B) when stable.

---

## Recommended Durable T1 Identity

```text
T1_EVALUATION_ID = UUID (primary key)
```

Uniquely identifies one T1 attempt. Generated fresh for each attempt.

---

## Recommended Linkage Model

```
T1 → ExecutionIntent:    risk_evaluation_t1.execution_intent_id (FK)
T1 → T0:                 risk_evaluation_t1.t0_evaluation_id (nullable FK)
ExecutionAttempt → T1:   execution_attempt.t1_evaluation_id (nullable FK, set when T1 APPROVED)
ExecutionIntent → T0:    execution_intent.risk_evaluation_id (existing, unchanged)
```

**Audit chain:**
```
ExecutionIntent
    ↓ risk_evaluation_id → T0 evaluation
    ↓ execution_intent_id → all T1 evaluations
    ↓
T1 evaluation APPROVED
    ↓ t1_evaluation_id → ExecutionAttempt
    ↓
ExecutionAttempt
    ↓ broker submission
    ↓ broker outcome
```

**Which exact T1 approval authorized broker submission?**
- `ExecutionAttempt.t1_evaluation_id` references the specific T1 evaluation.
- If multiple T1 attempts exist, only the APPROVED one has an ExecutionAttempt referencing it.
- Query: `SELECT * FROM execution_attempt WHERE t1_evaluation_id = ?`

---

## Recommended Immutability Model

```text
T1_ATTEMPT_IMMUTABLE = YES
```

Each T1 evaluation row is immutable after creation:
- `risk_evaluation_t1` is INSERT-only (no UPDATE after initial persist)
- `risk_component_snapshot` rows for T1 are INSERT-only (already @Immutable)
- `risk_context_snapshot` row for T1 is INSERT-only (already @Immutable)
- `ExecutionEvent` rows are already INSERT-only

An unavailable attempt must not later mutate into approved. Instead:
```
T1 attempt #1 = UNAVAILABLE (immutable row)
T1 attempt #2 = APPROVED (new immutable row)
```

---

## Recommended Transaction Invariant

```text
T1_APPROVED_DURABLE_BEFORE_BROKER_SUBMISSION = YES
```

Required ordering:
```
1. Persist T1 evaluation + component snapshots + context snapshot  (in transaction)
2. Update ExecutionIntent status                                   (in same transaction)
3. Create ExecutionAttempt with t1EvaluationId (if APPROVED)       (in same transaction)
4. Commit transaction
5. [outside transaction] Broker submission via BrokerExecutionPort
```

If crash occurs between steps 4 and 5:
- T1 is durable
- ExecutionAttempt exists with status CREATED
- Intent reflects T1 outcome
- Reconciliation/retry can discover orphaned attempt

---

## Recommended Unavailable-Attempt Persistence

```text
T1_UNAVAILABLE_PERSISTED_AS_DURABLE_ATTEMPT = YES
```

T1 UNAVAILABLE (D14) is a durable attempt with:
- `status = 'UNAVAILABLE'`
- `decision = null`
- `unavailable_reason_code = 'CONTEXT_UNAVAILABLE' | 'CONTEXT_STALE' | ...`
- Full provenance (what facts were attempted, what failed)

This satisfies:
- D15_I3 (multiple attempts per intent)
- D15_I8 (UNAVAILABLE is durable attempt)
- D14 (structured reason codes)

---

## Recommended Query Model

Minimum lookup paths:

```sql
-- All T1 attempts for an execution intent
SELECT * FROM risk_evaluation_t1 WHERE execution_intent_id = ? ORDER BY evaluated_at;

-- Specific T1 evaluation
SELECT * FROM risk_evaluation_t1 WHERE id = ?;

-- Approving T1 evaluation for broker submission
SELECT t1.* FROM risk_evaluation_t1 t1
  JOIN execution_attempt ea ON ea.t1_evaluation_id = t1.id
  WHERE ea.id = ?;

-- Compare T0 and T1 policy versions
SELECT t0.response_payload, t1.response_payload
  FROM risk_evaluation t0
  JOIN execution_intent ei ON ei.risk_evaluation_id = t0.id
  JOIN risk_evaluation_t1 t1 ON t1.execution_intent_id = ei.id
  WHERE ei.id = ?;

-- T1 attempts by account (for account-level audit)
SELECT * FROM risk_evaluation_t1 WHERE account_id = ? ORDER BY evaluated_at;
```

---

## Scope Assessment

```text
STORY_0034_IMPLEMENTATION_SCOPE = MODERATE
```

**Components to create:**
- `risk_evaluation_t1` table + Flyway migration
- `RiskEvaluationT1Entity` (JPA entity)
- `RiskPersistence` methods: `t1Evaluation()`, `findT1ByExecutionIntentId()`, `findT1ById()`
- `ExecutionTimeRiskRevalidationService` (new service)
- `ExecutionStatus` additions: `RISK_REVALIDATION_REJECTED` (D13), `RISK_REVALIDATION_UNAVAILABLE` (D14)
- `ExecutionIntent` transition map updates
- `ExecutionEvent` additions: `ExecutionIntentRiskRejected`, `ExecutionIntentRiskUnavailable`
- `RetryExecutionService` updates for `RISK_REVALIDATION_UNAVAILABLE`
- T1 integration point in execution flow (before `ExecuteTradeService`)
- Angular T1 loading state

**Estimated complexity:** Comparable to T0 persistence implementation. Same patterns, same provenance model. No hidden redesign.

**Does NOT require:**
- Migration of existing T0 data
- Changes to `TradePlanRiskEvaluationService`
- Changes to Risk Domain engine
- Changes to Risk Domain domain types
- Changes to broker service
- Generalization of risk evaluation model

---

## Migration Path

V1 (Story 0034): Dedicated T1 table, separate from T0.

V2 (Future story): Generalize risk evaluation persistence:
- Extract `RiskEvaluationRecord` base with common fields
- T0 and T1 become specializations
- Single table with `phase` discriminator or inheritance
- Requires data migration and broader scope

The V1 design preserves a clean migration path:
- T1 table structure mirrors T0 → easy to generalize later
- Component snapshot pattern is shared → already aligned
- No T0 changes → no migration risk

---

```
STORY_0034_D15 = HUMAN_APPROVED

D15_PERSISTENCE_MODEL = DEDICATED_T1_EVALUATION
D15_T1_ATTEMPTS = APPEND_ONLY_IMMUTABLE
D15_MULTIPLE_ATTEMPTS_PER_INTENT = YES

D15_T1_LINKS_EXECUTION_INTENT = YES
D15_T1_LINKS_T0_EVALUATION = YES

D15_EXECUTION_ATTEMPT_LINKS_APPROVING_T1 = YES

D15_POLICY_PROVENANCE_REQUIRED = YES
D15_UNAVAILABLE_ATTEMPTS_PERSISTED = YES

D15_T1_APPROVAL_DURABLE_BEFORE_BROKER_SUBMISSION = YES
D15_COMPLETED_T1_ATTEMPT_IMMUTABLE = YES

D15_T0_T1_DOMAIN_RELATION = SAME_RISK_EVALUATION_CONCEPT_DIFFERENT_LIFECYCLE_PURPOSE
D15_V1_PERSISTENCE_DECISION = YES
D15_GENERALIZATION_DEFERRED = YES
```

---

## Transaction-Boundary Audit

### Standard Execution (`ExecuteTradeService`)

**Status:** `COMPLIANT` with ADR-041 D2

**Evidence:**
- `ExecuteTradeService.execute()` — NO `@Transactional`
- `ExecutionController.execute()` — NO `@Transactional`
- Pipeline steps: `ExecutionValidationStep` → `IdempotencyVerificationStep` → `ExecutionAttemptCreationStep` → `BrokerSubmissionStep` → `BrokerResponseProcessingStep` → `ExecutionFinalizationStep`
- `BrokerSubmissionStep.execute()`: saves attempt/intent, calls `broker.submit()`, returns. No surrounding transaction.
- State persisted before and after broker call via repository `save()` calls, each in own transaction (Spring Data default).

### Position Close (`PositionCloseService`)

**Status:** `NON_COMPLIANT` with ADR-041 D2

**Evidence:**
- `PositionCloseService.close()` — **HAS `@Transactional`** (line 26)
- Entire flow: `resolveTarget()` → create command → `save()` → `transitionToSubmitted()` → `save()` → `executeClose()` (BROKER CALL) → `mapBrokerResult()` → `save()` — all in one Spring transaction.
- `PositionCloseService.reconcile()` — **HAS `@Transactional`** (line 61)

**Accepted Technical Debt:** Story 0033 Engineering Report acknowledges: "The Full Exposure Close orchestration executes inside a single `@Transactional` boundary... A database rollback does NOT imply the broker command was not executed."

**Recommendation:** Separate remediation Story (not Story 0034 scope).

### Reconciliation (`RecoverExecutionService`)

**Status:** `COMPLIANT` with ADR-041 D2

**Evidence:**
- `RecoverExecutionService.recoverOne()` — NO `@Transactional`
- Pipeline: `inspection` → `strategy` → `reconciliation` (broker call) → `finalization` → `events.publish()` → `intents.save()`
- Broker call in `BrokerReconciliationStep.execute()` outside transaction.

---

## PostgreSQL Concurrency Verification

**Story 0033** uses PostgreSQL partial unique index for active-scope concurrency guard:

```sql
CREATE UNIQUE INDEX uq_active_command_per_scope
ON position_close_command (broker_account_id, resolved_mutation_scope)
WHERE status IN ('CREATED', 'SUBMITTED', 'ACKNOWLEDGED', 'UNKNOWN');
```

**Current Test Coverage:**
- H2 test profile: Full unique index (stricter but safe) — **TESTED**
- PostgreSQL partial unique index: **NOT TESTED**

**Production Invariant Unverified:**
1. One ACTIVE command succeeds
2. Concurrent equivalent ACTIVE command fails
3. Different scope succeeds
4. Terminal command allows future ACTIVE for same scope
5. Concurrent transactions cannot create two ACTIVE commands for same scope

**Recommendation:** Dedicated PostgreSQL/Testcontainers verification task before real-money use. Separate from Story 0034.

---

## Scope Verification

```
PRODUCTION_CODE_MODIFIED = NO
FINANCIAL_COMMAND_EXECUTED = NO
IMPLEMENTATION_STARTED = NO
COMMIT_CREATED = NO
PUSH_PERFORMED = NO
```

---

## Final Gate

```
ADR_041 = ACCEPTED
D11 = HUMAN_APPROVED
D12 = HUMAN_APPROVED (EXECUTION_TIME_AUTHORITATIVE_POLICY)
D13 = HUMAN_APPROVED (RISK_REVALIDATION_REJECTED — terminal)
D14 = HUMAN_APPROVED (RISK_REVALIDATION_UNAVAILABLE — non-terminal, R2 retry)
D15 = HUMAN_APPROVED (DEDICATED_T1_EVALUATION — append-only immutable)
STORY_0034_DESIGN = ACCEPTED
IMPLEMENTATION_AUTHORIZED = NO
```

---

## Summary

The repository implements the human-controlled execution path (Story 0030) and position close (Story 0033) with strong safety invariants (D1, D4–D10). The critical production-readiness gap is **D3 — Execution-Time Risk Revalidation (T1)**.

Story 0034 addresses this gap with minimal scope: a fresh deterministic T1 risk evaluation using the authoritative current account risk policy (D12), gating broker submission, with full audit persistence.

All design decisions (D11–D15) are human-approved. ADR-041 is accepted. Story 0034 design is complete. Two technical debts (Position Close transaction boundary, PostgreSQL partial index) are documented but outside Story 0034 scope.