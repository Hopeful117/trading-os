# Repository Analysis - Story 0046

## Baseline

```text
STORY = 0046-trade-plan-frontend-state-and-error-resilience
BRANCH = story/0046-trade-plan-frontend-state-and-error-resilience
BASE = c5615b3 (main)
WORKTREE = pre-existing modifications and four untracked Story directories
```

The existing modifications are outside this Story:

```text
.env.example
.idea/compiler.xml
README.md
docker-compose.yml
docs/architecture/stories/0046-*/story.md
docs/architecture/stories/0047-*/story.md
docs/architecture/stories/0048-*/story.md
docs/architecture/stories/0049-*/story.md
```

They must not be reverted or included accidentally in the implementation.

## Story Boundary

Story 0046 is a frontend reliability Story for the existing opportunity to
Trade Plan, risk decision, and human execution journey.

The frontend expresses user intent and renders backend truth. Trading Core,
Market Intelligence, Risk Domain, and the execution domain remain authoritative
for lifecycle, sizing, risk, ownership, idempotency, and execution state.

No new aggregate, endpoint, risk rule, execution transition, provider
integration, or state-management dependency is justified by the current Story.

## Current Flow

The current Angular routes are:

```text
/opportunities
/opportunities/:opportunityId
/trade-planning/prepare/:opportunityId
/trade-planning/plans/:planId/versions/:version
```

The existing services call the Gateway contracts for:

```text
POST /v1/trade-plans/opportunities/{opportunityId}/trade-plans
GET  /v1/trade-plans/{planId}/versions/{version}
POST /v1/trade-plans/{planId}/versions/{version}/decisions
POST /v1/trade-plans/{planId}/versions/{version}/risk-evaluations
POST /v1/executions/validate
POST /v1/executions/{id}/execute
GET  /v1/executions/{id}
POST /v1/executions/{id}/retry
POST /v1/executions/{id}/retry-t1
POST /v1/executions/{id}/reconcile
```

The existing backend validation resolves the authoritative broker account from
the selected trading account. The frontend must continue to submit the
trading-account identity already carried by the Trade Plan and must not attempt
to resolve broker identities itself.

## Verified Gaps

### 1. Creation failure can leave the page stuck

`trading-os-web/src/app/features/trade-planning/prepare-plan-page/prepare-plan-page.ts`
sets the view to `creating`, then swallows creation errors with
`catchError(() => of(void 0))`. There is no error state, error message, or
retry action after a failed request.

### 2. Busy state is defined but not applied

`prepare-plan-page.ts` and `plan-page.ts` both expose a `busy$` observable.
The Trade Plan template does not bind it to the decision, risk, or execution
buttons. The current `switchMap` streams cancel a previous inner observable
when another subject event arrives, but this does not provide safe user-facing
single-flight behavior or prevent duplicate HTTP requests from being started.

### 3. Decision and evaluation transitional states are not emitted

`PlanView` declares `deciding` and `evaluatingRisk`, but the accept, reject, and
risk-evaluation streams map directly to their response or to `error`. The
corresponding loading states therefore cannot represent the actual in-flight
operation.

### 4. Persisted plan statuses are collapsed

`PlanPage.toViewForPlan()` explicitly maps only `PROPOSED`, `ACCEPTED`,
`REJECTED`, and `DRAFT`. Its default branch returns the `accepted` view.

The backend Trade Plan lifecycle contains `RISK_VALIDATED`, `READY_TO_EXECUTE`,
and `EXECUTED`. A reload of one of these states therefore renders an incorrect
available action, most notably an `Evaluate Risk` action for a plan that has
already progressed beyond that state.

### 5. Error semantics are flattened

The relevant page streams convert all request errors into `{ status: 'error' }`.
This loses the distinction between not found, expired, forbidden, stale
version, invalid command, risk refusal, and temporary unavailability.

The current public services do not expose a shared frontend error model or
mapping helper for these responses. Any mapping must remain presentation-only;
backend status and business codes remain authoritative.

### 6. Execution polling failure loses useful state

`PlanPage.pollOrResult()` polls the authoritative execution endpoint, but an
HTTP error propagates to the outer generic error state. The last known execution
state is not retained for a transient read failure, and the trader is not told
whether the execution remains unresolved.

This must be addressed only within the Story's safe UI behavior. It must not
turn an uncertain execution into a failure or trigger an automatic retry.

## Existing Tests

The frontend currently has focused tests for the nominal preparation and plan
flows, but the relevant coverage is incomplete:

* `prepare-plan-page.spec.ts` checks active gating and the disabled action when
  no account is selected, but not creation failure, retry, or double trigger.
* `plan-page.spec.ts` checks nominal proposal, acceptance, risk, and execution
  rendering, but not in-flight button protection, backend error mapping,
  reloads for all persisted statuses, or duplicate actions.
* Service tests cover HTTP construction but do not establish a shared error
  presentation contract.

The current frontend baseline was verified before implementation:

```text
npm run test:ci = 299 tests passed
npm run build   = successful, with existing bundle/style budget warnings
```

## Candidate Files

Primary frontend files:

```text
trading-os-web/src/app/features/trade-planning/prepare-plan-page/prepare-plan-page.ts
trading-os-web/src/app/features/trade-planning/prepare-plan-page/prepare-plan-page.html
trading-os-web/src/app/features/trade-planning/prepare-plan-page/prepare-plan-page.spec.ts
trading-os-web/src/app/features/trade-planning/plan-page/plan-page.ts
trading-os-web/src/app/features/trade-planning/plan-page/plan-page.html
trading-os-web/src/app/features/trade-planning/plan-page/plan-page.spec.ts
trading-os-web/src/app/core/models/trade-plan.model.ts
trading-os-web/src/app/core/models/execution.model.ts
trading-os-web/src/app/core/services/trade-plan.service.ts
trading-os-web/src/app/core/services/execution.service.ts
```

Potentially affected supporting files:

```text
trading-os-web/src/app/core/models/opportunity.model.ts
trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.ts
```

No supporting file should be changed unless the implementation demonstrates a
direct requirement from the acceptance criteria.

## Proposed Implementation Shape

The smallest coherent implementation is:

1. Extend the existing discriminated view models with explicit recoverable
   error context and action-in-progress states.
2. Make create and command flows single-flight at the UI boundary, while
   retaining existing idempotency headers and backend semantics.
3. Add a small presentation-level error mapper for known HTTP status/business
   code combinations, with a safe fallback for unknown failures.
4. Map every persisted Trade Plan status explicitly and expose only actions
   valid for that state.
5. Preserve the last known execution view when a polling read fails
   transiently, while showing a non-authoritative degraded message.
6. Add focused tests before broad refactoring or introducing reusable
   abstractions.

This is an implementation direction, not an approved implementation plan. A
separate Implementation Plan is required only if the accepted Story cannot be
implemented safely from these facts and existing conventions.

## Risks and Questions

* The exact backend error codes are not consistent across every public
  controller. Before implementation, inspect the affected response fixtures
  and use only codes verified in the current contracts.
* The frontend's `TradePlanResponse.status` is currently typed as `string`.
  Narrowing it must not reject forward-compatible backend statuses without an
  explicit fallback state.
* The existing execution polling semantics include uncertain states. Any
  polling adjustment must preserve the no-blind-retry invariant from ADR-029
  and Story 0031.

No architectural decision or product decision is currently required. If the
implementation requires a new backend error contract or a new persisted plan
read capability, work must stop and the contract decision must be recorded
before proceeding.

## Validation Plan

* Add focused Vitest coverage for creation failure/retry, command single-flight,
  all persisted plan statuses, error mapping, and transient execution read
  failure.
* Run `npm run test:ci`.
* Run `npm run build` and record any budget warnings without expanding scope.
* Run `git diff --check`.
* Review the complete diff and confirm that the pre-existing worktree changes
  remain untouched.

## Workflow Gate

```text
REPOSITORY_ANALYSIS = completed
IMPLEMENTATION_PLAN = not required yet
HUMAN_APPROVAL = required before implementation
```
