# Story 0139 - Improve Research Page Load Performance

## Metadata

**ID:** `0139`  
**Title:** Improve Research Page Load Performance  
**Status:** Implemented locally - pending human review  
**Related Stories:** `0133`, `0131`  
**Related Reports:**

- `docs/architecture/reports/s1-replay-fee-zero-coverage-root-cause-investigation.md`
- `docs/architecture/reports/v1-functional-audit.md`

This Story defines a measured four-step performance improvement sequence. It does
not authorize implementation until the Story and the required workflow artifacts
have been approved.

---

## Goal

Reduce the time required for the Research page to present useful, truthful
readiness information while preserving deterministic readiness semantics,
fail-closed behavior, service responsibilities, and explicit evidence freshness.

---

## Context

The Research page currently requests readiness and integrity through two separate
Gateway endpoints. The frontend waits for readiness before requesting integrity,
although the two projections are independently renderable and independently
cached in Market Intelligence.

Readiness and integrity currently perform distinct database reads and projections.
Readiness also performs bounded replay, fee, and historical OHLC checks. The
existing readiness concurrency and remote-check budget are deliberate safety
controls and must not be weakened as part of this Story.

The current implementation and baseline investigation are documented in:

- `trading-os-web/src/app/features/research/research-overview.ts`
- `trading-os-web/src/app/core/services/research.service.ts`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ResearchReadinessService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ResearchIntegrityService.java`

---

## Problem

The page performs independent frontend requests sequentially, increasing the
time before the complete Research overview is available. Backend projections
also repeat related snapshot and outcome reads and may scan more data than is
necessary for a read-only operational page.

There is no measured decision record yet showing whether frontend concurrency,
targeted persistence queries, shared backend projections, or precomputed data
provides the best improvement for the current dataset and runtime constraints.

---

## Scope

- Establish a reproducible baseline for Research page time-to-first-useful-data,
  time-to-complete-view, request durations, payload sizes, database query timing,
  remote-check timing, cache hit/miss behavior, and error behavior.
- Step 1: issue independent readiness and integrity requests concurrently from
  the frontend while preserving progressive rendering and existing fallback
  semantics.
- Re-measure Step 1 and retain it only if it improves the agreed baseline without
  introducing duplicate requests, stale-state ambiguity, or changed readiness
  semantics.
- Step 2: evaluate and, when justified, bound or parallelize independent backend
  historical evidence lookups, including per-market OHLC lookups, without
  exceeding explicit concurrency, timeout, or remote-call budgets.
- Re-measure Step 2 against Step 1 and document the operational trade-offs.
- Step 3: evaluate targeted persistence projections or aggregate queries that
  avoid loading and deserializing unnecessary snapshots and outcomes.
- Re-measure Step 3 and verify that aggregate values remain equivalent to the
  authoritative domain rules.
- Step 4: evaluate a shared backend research projection or precomputed/materialized
  readiness view when the measured results justify it.
- Require an ADR before implementing Step 4 when it changes service boundaries,
  public/internal contracts, persistence ownership, cache authority, or long-term
  architecture.
- Preserve explicit timestamps, cache freshness, readiness blockers, integrity
  warnings, and fail-closed behavior in every step.
- Add or update focused tests and runtime measurements for each accepted step.

---

## Out of Scope

- Changing S1 thresholds, readiness blockers, replay semantics, fee semantics,
  evidence policy, or fail-closed rules.
- Treating `ready=false` as a performance problem or weakening evidence gates.
- Removing bounded replay, fee, OHLC, timeout, concurrency, or remote-check
  protections.
- Introducing speculative AI, performance metrics, backtest results, or trading
  recommendations into the Research page.
- Changing Gateway authentication, authorization, or route ownership.
- Changing broker, market-data, risk, execution, or persistence responsibilities
  without an approved architectural decision.
- Adding a new cache technology, message broker, database, or service solely for
  this Story without an approved ADR and explicit scope expansion.
- Destructive data migration, data deletion, or evidence reclassification.
- Broad frontend redesign or unrelated page-performance work.
- Committing, pushing, merging, or discarding unrelated worktree changes.

---

## Acceptance Criteria

- [ ] A reproducible baseline report records Research page request ordering,
      time-to-first-useful-data, time-to-complete-view, request durations,
      payload sizes, backend query/projection costs, cache behavior, and errors.
- [ ] Independent readiness and integrity requests are evaluated for concurrent
      execution, and the selected behavior preserves progressive rendering,
      retry behavior, one-request-per-refresh behavior, and integrity fallback.
- [ ] Step 1 is validated with Angular tests, network inspection, and before/after
      timing evidence if it is implemented.
- [ ] Backend historical lookups are evaluated with explicit concurrency,
      timeout, and budget limits; no unbounded fan-out or unsafe remote retry is
      introduced.
- [ ] Any accepted Step 2 change preserves readiness results and is validated
      under remote dependency failure and partial availability.
- [ ] Persistence query or projection improvements are evaluated for result
      equivalence, row volume, deserialization cost, indexes, and transaction
      behavior before implementation.
- [ ] Any accepted Step 3 change preserves authoritative readiness and integrity
      semantics and is covered by focused persistence/application tests.
- [ ] Shared backend projection or precomputation is implemented only if its
      measured benefit justifies its freshness, invalidation, ownership, and
      failure trade-offs.
- [ ] An ADR is approved before any Step 4 change that introduces or changes an
      architectural boundary, contract, persistence ownership, or cache authority.
- [ ] Readiness and integrity responses expose or preserve sufficient timestamps
      and freshness information for users and operators to understand staleness.
- [ ] No duplicate requests, changed S1 thresholds, weakened evidence checks,
      unsafe retries, or changed authentication behavior are introduced.
- [ ] Relevant Angular tests, service tests, production build, formatting checks,
      `git diff --check`, and applicable runtime measurements pass.
- [ ] The final report records which steps were implemented, rejected, deferred,
      or blocked, with measured evidence and residual risks.

---

## Constraints

- Preserve existing service responsibilities and the Research decision boundary.
- Keep readiness and integrity deterministic, truthful, and fail-closed where
  authoritative evidence is unavailable.
- Preserve bounded remote-call concurrency, budgets, timeout behavior, and
  provider-neutral contracts.
- Prefer the smallest measured change that improves the agreed baseline.
- Do not infer cache freshness from HTTP success alone; retain explicit timing or
  freshness metadata where required.
- Use Observables and the async pipe in the Angular frontend where practical.
- Avoid introducing a new cache or aggregation abstraction before its measured
  need and ownership are established.
- Human approval is required before implementation and before any architectural
  decision that becomes necessary.
- Do not commit, push, or merge automatically.

---

## Relevant ADRs

- `None directly identified for Story preparation.`
- An ADR is required before Step 4 when the selected design changes an
  architectural boundary, API contract, persistence ownership, or cache authority.

---

## Relevant Modules

- `trading-os-web`
- `market-intelligence`
- `gateway`
- `market-data` - only where historical OHLC lookup behavior is measured or
  bounded; no ownership change is implied.

---

## Validation

- Capture a baseline with browser/network inspection and backend timing evidence.
- Run focused Angular Research page tests and service tests.
- Run Market Intelligence readiness, integrity, controller, persistence, and
  relevant adapter tests.
- Run the Angular production build and repository formatting checks.
- Run `git diff --check` and review the complete diff.
- Compare before/after request count, critical-path latency, payload size, query
  volume, remote-call volume, cache behavior, and failure behavior.
- Validate cold-cache and warm-cache behavior separately.
- Validate partial failures for readiness, integrity, Market Data, replay, and
  historical fee dependencies.
- Validate that S1 readiness values and blocking reasons remain unchanged for the
  same authoritative data.
- Perform runtime validation only in a controlled environment and without
  weakening authentication, evidence, or safety boundaries.

---

## Definition of Done

- [ ] Repository Analysis approved by human.
- [ ] Implementation Plan approved when required.
- [ ] Baseline and step-by-step measurements recorded.
- [ ] Only measured and approved performance changes implemented.
- [ ] Required ADR approved before any architectural Step 4 implementation.
- [ ] Relevant validation executed successfully.
- [ ] Documentation reconciliation completed.
- [ ] Independent Code Review completed and approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.
