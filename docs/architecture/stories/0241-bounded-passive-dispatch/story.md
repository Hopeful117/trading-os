# Story 0241 - Add Bounded Recurring Passive Dispatch

## Metadata

**ID:** `0241`

**Title:** Add Bounded Recurring Passive Dispatch

**Status:** Proposed - implementation not authorized

**Priority:** P1 - Trading Intelligence

**Epic:** `0238`

**Related ADRs:** `ADR-021`, `ADR-033`, `ADR-087`, `ADR-100`, `ADR-102`

**Predecessor:** `0240`

## Goal

Schedule due passive work within explicit resource and provider budgets, using
durable claims and reconciliation without unbounded fan-out.

## Product Value

The platform maintains market awareness predictably and safely across provider
outages, restarts and slow analysis.

## Existing Components Reused

- Existing Spring scheduling conventions.
- `ActiveScanDispatchClaimService` patterns.
- `LocalAnalysisExecutionDispatcher` boundary.
- Existing provider request budget/rate-limit policies.
- `PassiveAnalysisStrategy` and `AnalysisExecutionService`.

## Scope

- Add a five-minute-or-larger scheduler check configurable by deployment.
- Select only due markets with no compatible in-flight work.
- Enforce maximum markets per run and passive concurrency.
- Dispatch the initial two-market demonstration at concurrency one.
- Add lease expiry and safe claim recovery.
- Reconcile child execution outcomes into the run projection.
- Record provider unavailable, closed-market and insufficient-evidence outcomes.
- Apply bounded retry budgets and defer excess work to a later cadence.
- Execute through a system-owned internal scheduler authority, not a user or
  agent identity; configuration enablement remains operator-controlled.

## Out of Scope

- Distributed scheduler or leader-election service.
- Notifications.
- Automatic ActiveScan creation.
- Account synchronization or broker writes.
- AI invocation.
- Historical backfill.

## Resource Policy

The scheduler must not run a full analysis every tick. Per-market due time,
freshness, active in-flight work, provider budget and configured batch limit are
all required before dispatch.

## Acceptance Criteria

- [ ] Scheduler creates no more than the configured number of markets per run.
- [ ] A scheduler wake-up with no due market creates no analysis execution.
- [ ] A market is due only when its cadence, evidence, market-state, in-flight,
      provider-budget and retry-budget checks all pass.
- [ ] Concurrent passive executions never exceed the configured limit.
- [ ] Repeated scheduler ticks do not duplicate in-flight logical work.
- [ ] Lease expiry allows safe recovery after process interruption.
- [ ] Retryable failures persist attempt count and backoff and cannot retry
      indefinitely; permanent failures are not immediately re-dispatched.
- [ ] Provider failures are bounded and do not block unrelated markets.
- [ ] Closed or unavailable markets receive explicit non-positive outcomes.
- [ ] Deterministic due-time ordering prevents a due market from being starved.
- [ ] A run reaches a truthful terminal or partial state after reconciliation.
- [ ] Scheduler is disabled unless explicitly enabled by configuration.
- [ ] Metrics expose selected, skipped, dispatched, retry, duplicate,
      insufficient-evidence and provider-budget outcomes.
- [ ] Tests cover budget, concurrency, retry, lease and restart behavior.

## Dependencies

Depends on Stories 0239 and 0240. Blocks operational awareness projection in
Story 0242.

## Migration and Safety

No external scheduler or deployment topology change is introduced. Default
configuration must leave the scheduler disabled until operational validation is
approved. No broker or account mutation is possible through this path.

## Validation

- Scheduler unit tests with deterministic clock.
- Concurrency and lease integration tests.
- Provider-budget and unavailable-provider tests.
- Controlled fixture run with AI disabled and no broker calls.
