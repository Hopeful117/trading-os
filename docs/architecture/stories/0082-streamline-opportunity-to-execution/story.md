# Story 0082 - Streamline Opportunity Trade Preparation and Risk Review

## Metadata

**ID:** `0082`
**Title:** Streamline Opportunity Trade Preparation and Risk Review
**Status:** Completed

---

## Goal

Reduce the number of redundant user actions between selecting an active
Opportunity and reaching an execution-ready Risk decision, while preserving the
existing TradePlan, deterministic Risk, human validation, and execution
authorization boundaries.

The primary Opportunity journey should become:

```text
Opportunity
  -> prepare TradePlan
  -> review and accept/evaluate Risk
  -> explicitly execute
```

The Decision Workspace remains available when the trader wants to inspect live
market context before preparing the plan.

---

## Context

Stories `0062` and `0081` converged Opportunity decisions into the account-scoped
Decision Workspace and preserved the selected account and market context during
navigation.

The current downstream lifecycle is already shared and authoritative:

```text
Opportunity
  -> OPPORTUNITY TradePlan
  -> human ACCEPT/REJECT
  -> deterministic Risk evaluation
  -> explicit execution authorization
  -> ExecutionIntent and execution
```

The current primary path still requires the trader to open the Decision
Workspace and then click `Prepare from opportunity`, even when the Opportunity
already carries the account and market context needed to create the TradePlan.
After reviewing the proposed plan, the trader must separately click `Accept
Plan` and `Evaluate Risk`, although Risk evaluation is deterministic and does
not require a second human decision.

Relevant existing implementation surfaces include:

* `trading-os-web/src/app/features/opportunities/opportunity-details`
* `trading-os-web/src/app/features/decision-workspace`
* `trading-os-web/src/app/features/trade-planning/plan-page`
* `trading-os-web/src/app/core/services/trade-plan.service.ts`
* `trading-os-web/src/app/core/services/execution.service.ts`
* Trading Core Opportunity TradePlan orchestration and Risk controllers

---

## Problem

The active Opportunity journey currently contains two avoidable transitions:

```text
Opportunity Detail
  -> Open Decision Workspace
  -> Prepare from opportunity
```

The user must also perform two consecutive lifecycle actions before execution:

```text
Accept Plan
  -> Evaluate Risk
```

These actions do not represent two independent user decisions. The user must
explicitly accept the proposal, but deterministic Risk evaluation can follow
that acceptance automatically using the accepted TradePlan version.

The final `Execute Trade` action must remain separate because it creates the
execution intent boundary and represents explicit human authorization to submit
the trade.

---

## Scope

* Make the Opportunity row the direct primary action when valid account context
  is available.
* Reuse the latest authenticated TradePlan linked to the Opportunity, regardless
  of its current lifecycle status, and navigate to its existing TradePlan page.
* Create a TradePlan only when no linked plan exists for the authenticated actor
  and account.
* Preserve the Decision Workspace as an explicit secondary path for reviewing
  live market context before preparation.
* Provide a single user action that records explicit TradePlan acceptance and
  then evaluates deterministic Risk against the accepted plan version.
* Preserve the existing rejected-plan path and the ability to retry Risk
  evaluation independently when the chained operation cannot complete.
* Preserve the separate `Execute Trade` action and all existing execution
  validation, idempotency, revalidation, reconciliation, and recovery behavior.
* Preserve truthful fallback behavior when an Opportunity has no valid account
  context; the trader must use the existing account-selection flow rather than
  receiving an implicit or guessed account.
* Add focused Angular and, if contracts require it, backend regression tests for
  the streamlined transitions and failure states.

---

## Out of Scope

* Removing the Decision Workspace or its live market-data context.
* Automatically accepting a TradePlan without an explicit user action.
* Automatically creating an ExecutionIntent or submitting an order after Risk
  approval.
* Combining Risk approval and execution authorization into one action.
* Changing Risk rules, sizing, market eligibility, account ownership, or broker
  behavior.
* Creating a second TradePlan or execution pipeline.
* Changing Opportunity scoring, ranking, expiration, provenance, or strategy
  semantics.
* Removing the compatibility `/trade-planning/prepare/:opportunityId` route.
* Removing or merging the `OPPORTUNITY` and `MANUAL` TradePlan origins.
* Introducing a new state-management framework or unrelated navigation changes.
* LIVE broker execution validation beyond the existing execution lifecycle.

---

## Acceptance Criteria

* [ ] From an active Opportunity with valid authoritative account context, the
      row click creates or reuses an `OPPORTUNITY` TradePlan without requiring
      the trader to open the Decision Workspace first.
* [ ] When a linked TradePlan already exists, its latest version is reused and
      opened regardless of whether it is proposed, accepted, rejected, expired,
      risk-validated, ready to execute, or executed.
* [ ] Successful direct preparation or reuse navigates to the existing
      TradePlan page and preserves the existing authenticated ownership and
      provenance checks.
* [ ] Direct preparation never creates an ExecutionIntent, evaluates Risk, or
      submits an order as a side effect.
* [ ] The Decision Workspace remains available as a clearly discoverable
      secondary action for traders who want to inspect live market context.
* [ ] When account context is missing, stale, invalid, or not owned by the
      authenticated actor, the direct action does not guess an account and the
      existing account-selection fallback remains actionable.
* [ ] The proposed TradePlan remains visible for review before the user can
      accept it.
* [ ] A single explicit `Accept and Evaluate Risk` action records acceptance
      and evaluates Risk using the newly accepted TradePlan version.
* [ ] The chained acceptance/Risk action cannot be double-triggered and
      preserves retryable state when acceptance or Risk evaluation fails.
* [ ] Risk decisions, warnings, refusal reasons, and rejected outcomes remain
      rendered from the authoritative persisted Risk response.
* [ ] `Execute Trade` remains a separate explicit action available only after
      an approved Risk decision and continues to create or resume execution
      through the existing execution contract.
* [ ] Existing authorized-execution recovery, broker-outcome-unknown,
      reconciliation, retry, and Risk revalidation states remain unchanged.
* [ ] Angular tests cover direct Opportunity preparation, missing-account
      fallback, workspace secondary navigation, chained acceptance/Risk
      transitions, failure/retry behavior, and double-trigger protection.
* [ ] Existing Opportunity-origin TradePlan, Risk, execution, and route
      regression tests remain green.
* [ ] Applicable Maven tests, Angular `npm run test:ci`, Angular production
      build, Prettier verification, and `git diff --check` pass.
* [ ] No unrelated behavior or pre-existing user changes are modified.

---

## Constraints

* Preserve ADR-001, ADR-014, ADR-027, ADR-031, ADR-043, ADR-044, and ADR-047.
* Trading Core remains authoritative for account ownership, TradePlan
  orchestration, Risk orchestration, and execution-intent creation.
* Risk remains deterministic, fail-closed, and independent of frontend
  convenience actions.
* The frontend expresses explicit user intent and renders authoritative server
  responses; it must not compute Risk, sizing, eligibility, or authorization.
* Human acceptance of the TradePlan remains explicit even when Risk evaluation
  follows in the same UI action.
* Human execution authorization remains a separate explicit action.
* Preserve Angular standalone components, typed contracts, Observables, and
  async-pipe conventions.
* Preserve idempotency and optimistic version checks across TradePlan decisions
  and execution commands.
* Do not introduce provider-specific concepts into frontend or TradePlan
  contracts.
* Do not commit, push, merge, or discard unrelated changes automatically.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-001.md` - Trading OS Vision and Human Authority
* `docs/architecture/adr/ADR-014.md` - Trading Decision Pipeline
* `docs/architecture/adr/ADR-027.md` - Trade Planning Model
* `docs/architecture/adr/ADR-031.md` - Trade Planning and Risk Context Responsibilities
* `docs/architecture/adr/ADR-043.md` - Account and BrokerAccount Identity
* `docs/architecture/adr/ADR-044.md` - Inter-Service Trust and Actor Propagation
* `docs/architecture/adr/ADR-047.md` - Manual Trade Plans as a First-Class Trade Plan Origin

---

## Relevant Stories

* `0023` - Decide a proposed Trade Plan from an Opportunity
* `0030` - Connect Risk Decision to Human-Controlled Execution
* `0046` - Trade Plan Frontend State and Error Resilience
* `0047` - Execution to Position Frontend Continuity
* `0052` - PAPER Execution via Broker Contract
* `0062` - Converge Opportunity Decisions into the Decision Workspace
* `0081` - Preserve Opportunity Decision Context

---

## Relevant Modules

* `trading-os-web`
* `trading-core` only if the existing authenticated contracts cannot support
  the streamlined transitions without an additive, backward-compatible change
* `market-intelligence` only if existing Opportunity-origin preparation
  contracts require clarification; no Opportunity semantics change is expected

---

## Validation

* Focused Angular tests for Opportunity Detail, Decision Workspace, TradePlan
  page, routing, and TradePlan service interactions.
* Regression tests proving direct preparation preserves account ownership,
  Opportunity provenance, and no-execution side effects.
* Regression tests proving acceptance uses the returned accepted version before
  Risk evaluation.
* Failure-path tests for acceptance failure, Risk failure, retry, stale plan
  versions, and duplicate commands.
* Trading Core Opportunity TradePlan, Risk, and execution contract tests if
  backend code changes.
* Angular `npm run test:ci`.
* Angular production `npm run build`.
* Prettier verification and `git diff --check`.
* Authenticated PAPER walkthrough covering:
  * active Opportunity with preserved account context;
  * direct preparation to the TradePlan page;
  * explicit acceptance followed by Risk evaluation;
  * separate execution authorization;
  * Decision Workspace secondary path;
  * missing-account fallback.

---

## Definition of Done

* [ ] Repository Analysis approved
* [ ] Implementation Plan approved when required
* [x] Implementation completed
* [x] Relevant validation executed
* [x] Authenticated runtime walkthrough completed
* [x] Documentation reconciliation completed
* [ ] Diff reviewed in IntelliJ
* [ ] Code Review approved
* [ ] Engineering Report completed
* [ ] Human commit created

## Closure Note

Story `0082` is closed. The direct Opportunity-to-TradePlan journey and the
TradePlan acceptance/Risk transition are implemented and validated.

The PAPER validation also established a separate design limitation: a user can
prepare and accept a TradePlan before deterministic Risk evaluates the current
portfolio and rejects it for `DAILY_DRAWDOWN` or `MAX_EXPOSURE`. The Risk
calculation is correct for the captured facts; preventing this avoidable journey
is tracked by Story `0083` and is outside the scope of this Story.
