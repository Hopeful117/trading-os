# Repository Analysis - Story 0049

## Baseline

```text
STORY = 0049-trade-history-and-plan-continuity
BRANCH = main
BASE = f1fba6b
WORKTREE = pre-existing modifications and untracked Story 0049 artifacts
```

Pre-existing changes must not be reverted or included accidentally. They include
IDE metadata, Dockerfiles, Gateway configuration, Market Intelligence client
configuration, frontend shell styling, screenshots, and other Story artifacts.

## Story Boundary

Story 0049 is a frontend history and navigation Story. Trading Core remains the
authority for owned executions and execution lifecycle state. The frontend may
render and link authoritative responses, but must not derive execution success,
risk, fill, performance, or reconciliation truth.

No new trading command, retry behavior, recovery operation, analytics metric, or
execution lifecycle transition is justified by the current Story.

## Current Contracts

The authenticated Gateway already routes `/api/v1/executions/**` to Trading
Core. Trading Core exposes:

```text
GET /api/v1/executions
GET /api/v1/executions/{id}
```

The list endpoint uses `QueryExecutionService.findOwned(principal.userId)` and
returns `ExecutionSummaryDto` containing:

```text
id, tradePlanId, status, updatedAt
```

The detail endpoint applies the same ownership check and returns the richer
`ExecutionDto`, including Trade Plan id/version, risk evaluation id, broker
account id, timestamps, broker status, fills, fees, and failure reason.

The frontend already has `ExecutionService.getExecution()` and safe execution
status sets used by `PlanPage`, but it does not expose the owned list endpoint.
The existing Trade Plan service can load a specific plan by id/version and the
plan response contains `tradingAccountId`, instrument, direction, and execution
parameters. The positions service supports account-scoped position reads.

## Verified Gaps

### 1. No authenticated history route or view

`app.routes.ts` has no execution-history route. The existing trader routes are
protected by `authGuard`, so a new history route must use the same guard.

### 2. The sidebar contains a dead Analytics link

`sidebar.html` links to `/analytics`, but no matching route exists. Story 0049
must either provide an explicitly limited authenticated history/continuity route
at that path or remove/defer the link. It must not introduce analytics metrics
without a separate product decision.

### 3. The list response is intentionally minimal

`ExecutionSummaryDto` has no account id, Trade Plan version, instrument, broker
reference, or fill summary. The frontend can use the execution id to load the
owned detail response, then use the Trade Plan id/version to load plan context.
Rows without a Trade Plan relationship must remain safely non-navigable rather
than guessing an account or instrument.

### 4. Execution status semantics already distinguish uncertainty

`ExecutionStatus` includes `SUBMISSION_OUTCOME_UNKNOWN`,
`RECONCILIATION_IN_PROGRESS`, `RECOVERY_BLOCKED`, and
`RISK_REVALIDATION_UNAVAILABLE`. The history view must render these as
uncertain or pending states and must not offer blind retry or label them as
successes or failures.

### 5. Existing navigation targets are available only conditionally

The Trade Plan route requires both `planId` and `version`. The positions route
accepts an `accountId` query parameter. The list summary does not provide those
values completely, so continuity links should be built from the enriched detail
and plan responses, with disabled or absent actions when the relationship is
missing.

## Implementation Boundary

The smallest coherent implementation is frontend-only unless validation proves
that an existing contract cannot support the required display:

* Add an authenticated execution-history/continuity page and route.
* Add an owned-list method to `ExecutionService`.
* Load detail and available Trade Plan context without exposing provider data.
* Render loading, empty, error, status, and uncertain-outcome states explicitly.
* Link to the Trade Plan and account-scoped positions only when authoritative
  identifiers are available.
* Remove or replace the dead `/analytics` sidebar link with a truthful history
  entry; do not create an analytics product surface.
* Add focused Angular tests for ownership-scoped requests, rendering states,
  status semantics, conditional navigation, and authenticated routing.

No backend change is currently required. A backend contract change would need
separate approval if the detail-per-row approach is insufficient for the
expected history volume or product UX.

## Risks and Questions

* Loading one detail and plan request per list row may be acceptable for the
  current bounded list but should not silently become an unbounded request fan
  out. Prefer a bounded list presentation or lazy enrichment.
* A Trade Plan may be unavailable, stale, or intentionally absent. The UI must
  preserve the execution record and explain that continuity is unavailable.
* `ExecutionSummaryDto` does not expose `createdAt`; the list should use
  `updatedAt` unless the detail response is loaded. Do not invent timestamps.
* The current status model includes `FAILED` in the frontend, while backend
  statuses and broker outcomes must remain the source of truth. Mapping must be
  verified against current DTOs and tests.

## Validation Plan

* Add focused Angular service tests for `GET /v1/executions` and detail loading.
* Add component tests for loading, empty, error, owned data, uncertain states,
  status labels, and conditional Trade Plan/positions navigation.
* Add route tests proving the new trader-facing route is behind `authGuard`.
* Run `npm run test:ci` and `npm run build`.
* Run `git diff --check` and inspect the complete diff.
* Preserve all pre-existing worktree changes outside Story 0049.

## Workflow Gate

```text
REPOSITORY_ANALYSIS = completed
IMPLEMENTATION_PLAN = not required yet
HUMAN_APPROVAL = required before implementation
```
