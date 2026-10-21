# Implementation Plan

## Overview

Implement Story 0139 as a measured sequence rather than as an unrestricted
performance refactor.

The first change is a frontend-only orchestration improvement: readiness and
integrity requests will start concurrently while the page continues to render
readiness as soon as it is available and keeps integrity independently optional.
The existing public and internal API contracts remain unchanged.

After the baseline and Step 1 measurements, backend changes are conditional:

- Step 2 may bound or parallelize independent historical OHLC lookups only if
  their latency is material and explicit provider-load limits remain intact.
- Step 3 may introduce targeted persistence projections only if query plans,
  row volume, deserialization cost, and semantic equivalence justify them.
- Step 4 is a design/decision gate. A shared backend projection, durable
  precomputation, or new cache authority requires an ADR before implementation.

No implementation may change S1 thresholds, evidence semantics, fail-closed
behavior, remote budgets, authentication, or service ownership.

---

## Planned Changes

### Step 0 - Establish the baseline

**Components:** `trading-os-web`, `market-intelligence`, controlled runtime
environment.

1. Capture cold-cache and warm-cache page loads.
2. Record time to first useful readiness content and time to complete readiness
   plus integrity content.
3. Record request ordering, request count, request duration, status, and payload
   size for readiness and integrity.
4. Record backend readiness/integrity duration and, where available, database
   query timing, row counts, serialized payload volume, remote OHLC/replay/fee
   timing, and cache hit/miss behavior.
5. Record failure behavior for integrity failure, readiness failure, and remote
   dependency unavailability.
6. Store the result in a Story-local baseline artifact and use the same scenario
   for every subsequent comparison.

**Constraint:** measurements must be observational and must not weaken
authentication, budgets, timeout behavior, or evidence controls.

### Step 1 - Concurrent frontend requests

**Components:** `ResearchOverview`, `ResearchService`, Research Overview tests.

1. Refactor the per-refresh RxJS orchestration so readiness and integrity
   subscriptions are created from the same refresh event and start concurrently.
2. Preserve progressive rendering: readiness must be able to produce the usable
   view before integrity completes.
3. Preserve integrity isolation: an integrity error must keep readiness visible
   and project integrity as unknown.
4. Preserve readiness failure behavior: a readiness error must retain the
   retryable error state.
5. Preserve one request per endpoint per refresh and avoid duplicate subscriptions
   or repeated network calls.
6. Do not use an orchestration that waits for both responses before emitting the
   first readiness state. A simple all-at-once join is not sufficient unless it
   is combined with an explicitly progressive state model.
7. Re-run the baseline and retain the change only if it reduces critical-path
   latency without semantic or request-count regressions.

**Constraint:** no DTO, endpoint, Gateway, or backend ownership change.

### Step 2 - Bounded backend historical lookups

**Components:** `ResearchReadinessService`,
`ResearchLifecycleProperties`, historical Market Data adapter configuration,
readiness service tests.

1. Use baseline evidence to determine whether sequential per-market OHLC lookup
   is a material contributor to readiness latency.
2. If justified, execute independent OHLC lookups with an explicit bounded
   executor or equivalent existing concurrency mechanism.
3. Add or reuse configuration for maximum concurrency and timeout only where the
   repository's existing configuration patterns support it.
4. Keep failure isolation: one market-data failure must not make unrelated market
   evidence appear available.
5. Keep provider-load controls explicit and bounded; do not add unsafe retries,
   unbounded fan-out, or hidden fallback values.
6. Keep replay and historical-fee budgets and concurrency semantics unchanged.
7. Add tests for success, partial failure, timeout, concurrency bound, and
   unchanged readiness values for equivalent inputs.
8. Re-run the baseline and document provider-load and failure trade-offs.

**Constraint:** Market Data remains authoritative for historical OHLC evidence;
Market Intelligence remains the owner of readiness composition.

### Step 3 - Targeted persistence projections

**Components:** `FeatureSnapshotRepository`, `ObservedMarketOutcomeRepository`,
`JpaFeatureSnapshotRepository`, `JpaObservedMarketOutcomeRepository`, the
corresponding Spring Data interfaces, `ResearchReadinessService`,
`ResearchIntegrityService`, and focused persistence/application tests.

1. Profile broad reads and identify the exact fields needed by readiness and
   integrity calculations.
2. Inspect query plans, existing indexes, row counts, payload sizes, and domain
   deserialization cost before changing query shapes.
3. Introduce only targeted read methods that preserve the current semantic
   ownership, projection version, evidence policy, market scope, and cutoff
   boundaries.
4. Prefer count/group/latest projections where they can be proven equivalent to
   the existing domain calculation; keep any non-trivial semantic composition in
   Market Intelligence.
5. Avoid schema changes and migrations unless profiling proves an existing index
   is insufficient and the change is explicitly approved as part of the plan.
6. Add equivalence tests comparing the old and optimized calculations over the
   same authoritative fixtures, including empty, partial, stale, future-dated,
   and duplicate-outcome cases.
7. Re-run the baseline and reject changes that trade latency for semantic drift,
   excessive database load, or loss of fail-closed behavior.

**Constraint:** no new persistence owner, derived truth table, or unsupported
research semantics.

### Step 4 - Shared projection or precomputation decision

**Components:** architecture decision and, only after approval, the selected
Market Intelligence projection/cache components.

1. Use the Step 1-3 measurements to decide whether duplicated reads and cold
   recomputation remain materially costly.
2. If not material, defer Step 4 and keep the separate cached projections.
3. If material, prepare an ADR covering ownership, contract shape, freshness,
   invalidation, recovery, partial failure, stale-state representation, and
   migration/rollback behavior.
4. Do not implement a combined endpoint, materialized view, durable cache, or new
   cache technology until the ADR is accepted and the implementation plan is
   updated if the decision changes scope.
5. If approved, implement the smallest projection that preserves independent
   readiness/integrity availability and explicit freshness metadata.
6. Validate cold start, warm read, invalidation, restart, partial computation,
   stale data, and dependency failure behavior.

**Constraint:** TTL alone cannot establish business validity. A shared projection
must not turn integrity failure into readiness failure or silently serve stale
readiness as current.

---

## Files to Modify

- `trading-os-web/src/app/features/research/research-overview.ts`: concurrent
  per-refresh request orchestration with progressive state emission.
- `trading-os-web/src/app/features/research/research-overview.spec.ts`: request
  ordering, concurrency, progressive rendering, refresh, and failure tests.
- `trading-os-web/src/app/core/services/research.service.ts`: modify only if a
  service-level composition helper is required; endpoint methods remain stable.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ResearchReadinessService.java`:
  only if Step 2 or Step 3 measurements justify backend changes.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ResearchIntegrityService.java`:
  only if Step 3 changes its read path without changing its semantics.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/config/ResearchLifecycleProperties.java`:
  only if a bounded OHLC concurrency/timeout setting is required and consistent
  with existing configuration conventions.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/FeatureSnapshotRepository.java`:
  only for justified targeted projections.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/ObservedMarketOutcomeRepository.java`:
  only for justified targeted projections.
- Corresponding JPA repository implementations and Spring Data interfaces only
  when Step 3 is accepted.
- Relevant Gateway files only if measurement reveals a real route/filter issue;
  no Gateway change is expected for Step 1.

---

## Files to Create

- `docs/architecture/stories/0139-research-page-load-performance/performance-baseline.md`:
  reproducible cold/warm baseline and before/after measurements, if the
  implementation workflow requires a persistent measurement artifact.
- An ADR file only if Step 4 is justified and its architectural decision is
  approved. No ADR is created by this plan in advance.

---

## Dependencies

- Existing Angular/RxJS and HTTP testing infrastructure.
- Existing Market Intelligence configuration, caches, repositories, and remote
  client ports.
- Existing Gateway route and authentication behavior.
- Controlled runtime with representative research data for measurements.
- Database query-plan and row-volume visibility for Step 3.
- Human approval of this plan before any source implementation.
- A separate approved ADR before any Step 4 architectural implementation.

No new external dependency is required for Steps 0-3. A new cache technology,
database, service, or messaging dependency is prohibited unless separately
approved through architecture governance.

Ordering dependencies:

```text
baseline
  -> Step 1
  -> Step 1 measurement
  -> Step 2 evaluation/conditional implementation
  -> Step 2 measurement
  -> Step 3 evaluation/conditional implementation
  -> Step 3 measurement
  -> Step 4 decision and ADR if justified
```

---

## Test Plan

### Frontend

- Update `research-overview.spec.ts` to prove readiness and integrity requests
  are both scheduled from one refresh without requiring readiness completion
  first.
- Prove readiness renders while integrity is pending.
- Prove integrity failure preserves readiness and displays the existing unknown
  state.
- Prove readiness failure remains retryable.
- Prove refresh sends exactly one request per endpoint.
- Run the existing Angular CI test command and production build.

### Market Intelligence

- Run existing readiness and integrity service/controller tests before and after
  any backend step.
- For Step 2, add bounded concurrency, timeout, partial failure, and equivalent
  result tests for historical OHLC lookup behavior.
- For Step 3, add repository/application equivalence tests for empty, bounded,
  partial, stale, future-dated, and duplicate data cases.
- For Step 4, add cache/projection freshness, invalidation, restart, partial
  failure, and independent availability tests only after its ADR is accepted.

### Runtime and repository validation

- Capture cold-cache and warm-cache browser/network measurements.
- Compare request count, critical-path latency, payload size, database volume,
  remote-call volume, cache behavior, and failure behavior.
- Run affected Maven tests and applicable integration tests.
- Run Angular tests and production build.
- Run formatting checks and `git diff --check`.
- Inspect the complete diff and verify that no unrelated pre-existing files are
  changed.

No test is claimed to pass before implementation and execution.

---

## Risks

- **Progressive rendering regression:** a naïve all-at-once RxJS join could make
  the page wait for integrity. Mitigation: use a state model that subscribes to
  both branches concurrently and emits readiness independently.
- **Duplicate requests:** multiple subscriptions or refresh interactions could
  multiply calls. Mitigation: retain the shared per-refresh stream and assert
  request counts in Angular tests.
- **Market Data provider load:** parallel OHLC calls could reduce local latency
  while increasing downstream load. Mitigation: implement Step 2 only after
  measurement and use explicit bounded concurrency and timeout controls.
- **Semantic drift from SQL projections:** count/group queries may omit domain
  conditions or evidence identity. Mitigation: preserve filters and add fixture
  equivalence tests before replacing broad reads.
- **Stale shared projection:** precomputation could serve outdated readiness.
  Mitigation: require explicit freshness, invalidation, recovery, and stale-state
  semantics in an ADR before Step 4.
- **Cross-projection coupling:** a combined backend computation could make
  integrity failure delay or fail readiness. Mitigation: preserve independent
  availability or defer the combined projection.
- **Insufficient baseline data:** without representative cold/warm measurements,
  later steps could optimize the wrong bottleneck. Mitigation: treat baseline
  capture as a prerequisite and record unknown metrics rather than inventing them.

---

## Validation Checklist

- [ ] Baseline captures cold and warm behavior with request, latency, payload,
      database, remote-call, cache, and failure measurements.
- [ ] Step 1 starts readiness and integrity concurrently.
- [ ] Readiness renders before integrity completion.
- [ ] Integrity failure does not hide or alter readiness.
- [ ] Readiness failure remains retryable.
- [ ] Refresh does not duplicate endpoint requests.
- [ ] S1 thresholds, blockers, evidence semantics, and response contracts remain
      unchanged.
- [ ] Step 2 is implemented only if measured and uses bounded concurrency,
      timeout, and provider-load controls.
- [ ] Replay and fee budgets remain unchanged.
- [ ] Step 3 is implemented only after query-plan and semantic-equivalence
      evidence.
- [ ] No unsupported persistence owner, cache authority, or business validity
      rule is introduced.
- [ ] Step 4 has an accepted ADR before any architectural implementation.
- [ ] Angular tests and production build pass.
- [ ] Affected Market Intelligence tests and applicable integration validation
      pass.
- [ ] Formatting, `git diff --check`, and complete diff review pass.
- [ ] Only Story-scoped files are changed; pre-existing user changes remain
      untouched.

---

## Recommendation

**Ready for implementation**

This is a technical recommendation only. It does not approve the plan or
authorize source changes.

---

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
