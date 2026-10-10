# Repository Analysis - Story 0082

## Scope Reviewed

The current repository implementation was reviewed for the Opportunity Detail,
Decision Workspace, TradePlan page, TradePlan service, execution service, and
Trading Core TradePlan/Risk controllers.

DevLog context was requested but the bounded Story Agent request timed out. The
analysis therefore uses the current repository, accepted ADRs, and existing
Stories as authoritative sources.

## Current Journey

The current account-scoped path is:

```text
Opportunities
  -> select account for the scan
  -> open Opportunity Detail
  -> Open Decision Workspace
  -> Prepare from opportunity
  -> review proposed TradePlan
  -> Accept Plan
  -> Evaluate Risk
  -> Execute Trade
```

The scan account is propagated through the Opportunity navigation. The
Opportunity Detail view resolves `opportunity.accountId` first and falls back
to the `accountId` query parameter. It also carries `opportunityId` and
`marketId` into the Decision Workspace.

The Decision Workspace restores account and market context only after resolving
the account context and eligibility. Its `Prepare from opportunity` action calls
the existing `TradePlanService.createFromOpportunity` method and navigates to
the existing versioned TradePlan route.

The compatibility `/trade-planning/prepare/:opportunityId` route remains a
separate account-selection preparation flow. It is not the current primary path
from Opportunity Detail, but its behavior and tests must not be broken.

## Authoritative Contracts

### Opportunity TradePlan Creation

`TradePlanService.createFromOpportunity` calls:

```text
POST /api/v1/trade-plans/opportunities/{opportunityId}/trade-plans
```

with `accountId` in the body and an `Idempotency-Key` header.

`OpportunityTradePlanController` obtains the actor from the authenticated
principal. `OpportunityTradePlanOrchestrationService` validates account
existence, account ownership, effective planning profile currency, and delegates
Opportunity-origin plan generation to Market Intelligence.

Therefore a direct frontend action may reuse the existing contract. It must not
treat the account query parameter or Opportunity response as an authorization
authority; the backend remains authoritative.

### TradePlan Decision

`TradePlanService.decide` calls:

```text
POST /api/v1/trade-plans/{planId}/versions/{version}/decisions
```

The controller accepts `ACCEPT` or `REJECT`, derives actor identity from the
authenticated principal, and returns the resulting plan transport. The
returned plan is the authoritative accepted version for the next step.

### Risk Evaluation

`TradePlanService.evaluateRisk` calls:

```text
POST /api/v1/trade-plans/{planId}/versions/{version}/risk-evaluations
```

with the account and a new idempotency key. Trading Core validates ownership,
loads the accepted TradePlan version, gathers the current Risk context, and
persists the deterministic decision.

The chained UI action must therefore call Risk with the `version` from the
successful `decide(..., 'ACCEPT')` response, not the proposal version captured
before acceptance.

### Execution

The current `Execute Trade` action first validates and creates or reuses the
execution intent, then submits it. This is the explicit human execution
boundary and must remain a separate action after an approved Risk decision.

The existing execution state machine also handles authorized-execution resume,
Risk revalidation, unknown broker outcomes, reconciliation, and retries. Story
0082 must not alter those branches.

## Existing Implementation Findings

### Direct Opportunity Preparation

`OpportunityDetail` currently renders a single anchor labelled `Open Decision
Workspace`. It already has the resolved `accountId` available in its loaded
view, but it does not inject `TradePlanService` or perform creation.

The smallest coherent change is to make the active Opportunity CTA stateful:

* when a valid account context is present, invoke the existing
  `createFromOpportunity` contract and navigate to the versioned plan route;
* retain an explicit secondary link to the Decision Workspace with the existing
  Opportunity, market, and account query parameters;
* when no account context is present, retain the account-first Workspace path
  instead of guessing an account.

The backend will reject stale, nonexistent, or unauthorized account IDs. The
frontend must render that failure honestly and must not create a plan or execute
anything after a failed preparation command.

The existing `PreparePlanPage` remains a compatibility fallback and should not
be removed or silently repurposed as part of this Story.

### Chained Acceptance and Risk

`PlanPage` currently has independent RxJS command streams:

* `acceptSubject` records the decision and renders the accepted state;
* `evaluateRiskSubject` evaluates the currently displayed plan version;
* `executeSubject` performs execution-intent validation and broker submission.

The proposed combined action should be implemented as a new command stream or
equivalent composition that:

1. records `ACCEPT` using the currently displayed proposal version;
2. receives the accepted plan response;
3. invokes Risk using the accepted response's plan ID, version, and account;
4. renders the existing `executionReady` or `riskDecision` states;
5. renders a retryable error tied to the correct stage when either command
   fails.

The existing separate accepted-state Risk action should remain available for
compatibility, reload recovery, and retry after an acceptance-only outcome.
The `REJECT` action remains independent.

Risk evaluation already uses an idempotency key. The frontend command-in-flight
guard must cover the complete acceptance/Risk chain so one user interaction
cannot issue duplicate commands concurrently.

## Architectural Reconciliation

The implementation is consistent with:

* ADR-001: the trader remains the final decision maker;
* ADR-014: the pipeline still terminates in human validation;
* ADR-027 and ADR-031: TradePlan and Risk responsibilities remain separate;
* ADR-043 and ADR-044: account ownership and actor identity remain server-side;
* ADR-047: Opportunity and Manual TradePlan origins continue to share the
  normalized downstream lifecycle without direct broker commands.

No responsibility boundary changes, new persistence model, new API authority,
or ADR are required by the current evidence.

## Affected Files and Tests

Expected frontend surfaces:

* `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.ts`
* `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.html`
* `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.spec.ts`
* `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.ts`
* `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.html`
* `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.spec.ts`

Potentially affected shared service tests:

* `trading-os-web/src/app/core/services/trade-plan.service.spec.ts`

Backend changes are not currently indicated. If implementation discovers that
the existing contracts cannot support the chained transition or direct
preparation safely, it must stop and request approval before introducing an
additive backend contract.

## Risks

* Direct preparation can remove the live-context review from the primary path.
  The secondary Workspace action must remain visible and understandable.
* A chained acceptance/Risk action can obscure which stage failed. The UI must
  preserve stage-specific error and retry semantics.
* Calling Risk with the proposal version would create a stale-version defect.
  The accepted response version must be used explicitly and covered by tests.
* The existing compatibility preparation route and direct-entry account fallback
  can regress if Opportunity Detail assumes every Opportunity has an account.
* The frontend cannot validate ownership itself; it must preserve backend error
  handling and not infer authority from URL state.

## Validation Evidence Required

* Focused Opportunity Detail tests for direct preparation, secondary Workspace
  navigation, missing account, command failure, and duplicate-click protection.
* Focused PlanPage tests proving `decide` precedes `evaluateRisk` and that Risk
  receives the returned accepted version.
* Failure and retry tests for both acceptance and Risk stages.
* Existing execution state tests remain green.
* Angular test suite, production build, formatting verification, and
  `git diff --check`.
* Authenticated PAPER walkthrough covering both direct preparation and the
  optional Decision Workspace path, followed by separate execution
  authorization.

## Analysis Conclusion

The Story is implementable without an architectural decision or backend API
change based on current repository evidence. The likely implementation is
limited to `trading-os-web`, reusing existing authenticated contracts and
preserving the current backend lifecycle.

This Repository Analysis is complete and requires explicit human approval
before an Implementation Plan is produced.
