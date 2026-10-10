# Implementation Plan

## Overview

Implement the Story entirely in `trading-os-web` by reusing the existing
authenticated TradePlan, Risk, and execution contracts.

The implementation has two independent frontend changes:

1. Make Opportunity Detail offer direct TradePlan preparation when authoritative
   account context is available, while retaining the Decision Workspace as an
   explicit secondary path and account-selection fallback.
2. Replace the proposal-page `Accept Plan` action with a chained
   `Accept and Evaluate Risk` action that evaluates the version returned by the
   acceptance command. Keep the accepted-state Risk action for recovery and
   retries, and keep `Execute Trade` separate.

No new API, persistence model, dependency, or architectural boundary is
required by the approved Repository Analysis.

---

## Planned Changes

### 1. Direct Opportunity Preparation

**Component:** `OpportunityDetail`

* Inject the existing `TradePlanService` and router/navigation dependencies.
* Add a command state for direct preparation, including loading and retryable
  error handling, without changing the loaded Opportunity contract.
* When the Opportunity is active and a resolved account ID exists, invoke
  `createFromOpportunity(opportunityId, accountId, idempotencyKey)`.
* Navigate to the existing versioned TradePlan route using the returned plan ID
  and version.
* Disable duplicate preparation commands while the request is in flight.
* Do not create a plan when the Opportunity is not active or account context is
  absent.

**Reason:** Remove the redundant Workspace navigation and `Prepare from
opportunity` click from the normal account-scoped path.

**Constraints:** The account ID is only a command input. Trading Core remains
the authority for ownership, eligibility, profile, and Opportunity validity.

### 2. Opportunity Detail Actions and Fallback

**Component:** `opportunity-details.html`

* Make the direct preparation action the primary CTA when account context is
  present.
* Keep a secondary `Open Decision Workspace` action carrying the existing
  `opportunityId`, `marketId`, and `accountId` query parameters.
* When no account context exists, keep the account-first Workspace route as the
  primary actionable fallback and explain why direct preparation is unavailable.
* Render preparation failures honestly and provide a retry without navigating
  to a misleading plan state.

**Reason:** Preserve live-context review and direct-entry behavior while making
the shorter path discoverable.

**Constraints:** Do not remove the Decision Workspace or the compatibility
`/trade-planning/prepare/:opportunityId` route.

### 3. Chained Acceptance and Risk Evaluation

**Component:** `PlanPage`

* Add a dedicated command stream for the combined proposal action.
* Call `TradePlanService.decide(plan.id, plan.version, 'ACCEPT')` first.
* Pass the returned accepted plan object to Risk evaluation, including its
  returned version and trading account ID.
* Generate the Risk idempotency key only for the Risk command.
* Map approved and approved-with-warning results to the existing
  `executionReady` state and rejected results to the existing `riskDecision`
  state.
* If acceptance fails, expose a retryable acceptance-and-Risk action using the
  original proposal version.
* If acceptance succeeds but Risk fails, retain the accepted plan in the error
  state and allow the existing Risk-only retry path.
* Keep `REJECT` independent.
* Keep the accepted-state `Evaluate Risk` action for reload recovery and manual
  retry.
* Keep `Execute Trade`, authorized-execution resume, Risk revalidation,
  reconciliation, and broker recovery streams unchanged.
* Extend command-in-flight protection across the entire chained operation.

**Reason:** Remove the redundant second click without treating deterministic Risk
as a human approval or bypassing the persisted lifecycle.

**Constraints:** Risk must receive the accepted version, and execution remains a
separate explicit human action.

### 4. Plan Page Presentation

**Component:** `plan-page.html`

* Rename the proposal action to `Accept and Evaluate Risk`.
* Preserve the existing `Reject` action.
* Preserve the accepted-state `Evaluate Risk` button for recovery.
* Preserve the existing Risk result and separate `Execute Trade` presentation.
* Keep stage-specific busy and error states understandable to the trader.

### 5. Regression Tests

**Components:** Opportunity Detail and Plan Page test suites

* Update existing Opportunity Detail link expectations for the new primary and
  secondary actions.
* Add direct preparation success, navigation, idempotency-key, loading,
  failure, retry, and duplicate-click tests.
* Add missing-account and account-context fallback tests.
* Add combined acceptance/Risk tests proving call order and that Risk receives
  the accepted plan version rather than the proposal version.
* Add acceptance failure and Risk failure/retry tests.
* Add duplicate-trigger protection tests for the complete chained command.
* Preserve and rerun existing execution recovery, Risk rejection, and accepted
  plan tests.

No backend test or production change is planned unless implementation discovers
that an existing contract cannot support the behavior safely. Such a discovery
must stop implementation and request a scope decision.

---

## Files to Modify

* `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.ts`
  - direct preparation command state, service call, navigation, and retry logic.
* `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.html`
  - primary direct-preparation CTA, secondary Workspace action, fallback, and
    error/loading presentation.
* `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.spec.ts`
  - direct preparation, fallback, secondary navigation, failure, retry, and
    duplicate-command coverage.
* `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.ts`
  - chained acceptance/Risk command stream, accepted-version propagation, and
    stage-specific retry handling.
* `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.html`
  - combined acceptance/Risk action label and test selectors.
* `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.spec.ts`
  - chained lifecycle, call-order, failure/retry, and duplicate-trigger tests.

Potentially affected only if test helpers require adjustment:

* `trading-os-web/src/app/core/services/trade-plan.service.spec.ts`

No production changes are planned for Trading Core, Market Intelligence, Risk
Domain, Gateway, or Execution services.

---

## Files to Create

None.

---

## Dependencies

* Existing `TradePlanService.createFromOpportunity`, `decide`, and
  `evaluateRisk` methods.
* Existing Angular router and RxJS conventions used by the affected components.
* Existing authenticated Gateway routes and Trading Core contracts.
* Existing Opportunity account context fields and query-parameter fallback.
* No new external dependency is required.

Implementation ordering:

1. Update Opportunity Detail command and action behavior.
2. Update Opportunity Detail tests.
3. Add the Plan Page chained command and presentation.
4. Update Plan Page tests, including accepted-version assertions.
5. Run focused tests, then the repository frontend quality gates.

---

## Test Plan

### Opportunity Detail

* Active Opportunity with account context invokes direct preparation once.
* Direct preparation sends the account ID and an idempotency key through the
  existing service.
* Successful preparation navigates to the returned plan ID/version route.
* Direct preparation does not evaluate Risk or invoke execution services.
* The Workspace secondary action preserves Opportunity, market, and account
  query parameters.
* Account-less direct entry remains actionable through the Workspace fallback.
* Preparation failures render a retryable state and retry only the preparation
  command.
* Repeated clicks while preparing do not issue duplicate requests.

### Plan Page

* The proposal renders `Accept and Evaluate Risk` and `Reject`.
* Acceptance is called with the proposal ID/version and `ACCEPT`.
* Risk is called only after acceptance succeeds.
* Risk receives the accepted response's plan ID, version, and account ID.
* Approved Risk results render the existing execution-ready state.
* Rejected Risk results render reasons and no execution action.
* Acceptance failure exposes acceptance retry without invoking Risk.
* Risk failure after acceptance retains the accepted plan and exposes Risk-only
  retry behavior.
* The command-in-flight guard prevents a second chained request.
* The accepted-state standalone Risk action still works after reload/recovery.
* Existing execution authorization and recovery tests remain green.

### Validation Commands

Execute after implementation:

```text
cd trading-os-web && npm run test:ci
cd trading-os-web && npm run build
cd trading-os-web && npm run format:check
git diff --check
```

If repository scripts use a different formatting command, use the existing
project-defined equivalent and record the exact command in the Implementation
Report.

Expected success conditions:

* focused and complete Angular tests pass;
* production build succeeds;
* formatting validation passes;
* `git diff --check` is clean;
* no backend module requires changes;
* authenticated PAPER walkthrough confirms the direct and Workspace paths and
  separate execution authorization.

---

## Risks

* **Loss of live-context review on the primary path.** The direct CTA could
  encourage preparation without the Workspace. Mitigation: retain a visible
  secondary Workspace action and preserve the existing plan review before
  acceptance.
* **Risk evaluated against the wrong version.** Calling Risk with the original
  proposal version would violate the lifecycle contract. Mitigation: chain from
  the accepted response object and assert the version in tests.
* **Ambiguous failure stage.** A combined command could hide whether acceptance
  or Risk failed. Mitigation: use distinct retry actions and retain the accepted
  plan when the Risk stage fails.
* **Duplicate commands.** Rapid clicks could submit multiple acceptance or Risk
  commands. Mitigation: guard the whole chain with the existing in-flight flag
  and cover it with a test.
* **Account context misuse.** URL or Opportunity account data could be treated
  as authorization. Mitigation: keep backend ownership validation authoritative
  and preserve the account-less fallback.

No blocking risk or unresolved architecture conflict remains.

---

## Validation Checklist

* [ ] Opportunity Detail can directly prepare an active Opportunity when account
      context exists.
* [ ] Direct preparation uses the existing authenticated API and idempotency
      header.
* [ ] Direct preparation navigates to the existing versioned plan route.
* [ ] Decision Workspace remains visible as the secondary review path.
* [ ] Missing account context falls back to account-first Workspace selection.
* [ ] Proposal review remains available before acceptance.
* [ ] `Accept and Evaluate Risk` records acceptance explicitly.
* [ ] Risk receives the returned accepted plan version.
* [ ] Acceptance and Risk failure states provide correct retry actions.
* [ ] Duplicate clicks do not issue concurrent commands.
* [ ] `Execute Trade` remains separate and unchanged in responsibility.
* [ ] Compatibility preparation route remains functional.
* [ ] Focused tests cover all new transitions and failure paths.
* [ ] Complete Angular tests pass.
* [ ] Angular production build passes.
* [ ] Formatting and `git diff --check` pass.
* [ ] Authenticated PAPER walkthrough passes.
* [ ] No backend, API, persistence, or unrelated module changes are introduced.

---

## Recommendation

Ready for implementation

This is a technical recommendation only. It does not approve the plan or
authorize implementation.

---

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
