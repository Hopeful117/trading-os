# Story 0140 - Complete Research Backend Projection Performance

## Metadata

**ID:** `0140`
**Title:** Complete Research Backend Projection Performance
**Status:** CLOSED - HUMAN ACCEPTED
**Related Stories:** `0139`
**Related ADRs:** `ADR-020`, `ADR-021`, `ADR-022`, `ADR-025`, `ADR-031`, `ADR-048`, `ADR-066`, `ADR-068`
**Related Report:** `docs/architecture/stories/0139-research-page-load-performance/implementation-report.md`

This Story is a continuation of Story 0139. It closes the backend gaps left by
Steps 3 and 4; it does not introduce a new product feature.

---

## Goal

Complete the measured backend performance work for the Research page while
preserving exactly the same readiness, integrity, evidence, freshness, and
fail-closed semantics.

The Story must produce an evidence-based final decision for:

```text
Step 3: targeted persistence projections
    -> Step 4: shared or precomputed Research projection, only if justified
```

## Context

Story 0139 implemented the frontend concurrency improvement and bounded historical
OHLC execution. It deliberately deferred:

- targeted persistence projections and aggregate queries;
- a shared backend projection or precomputed readiness view.

The implementation report identified the remaining runtime bottleneck in bounded
replay and fee checks, while snapshot, outcome, and OHLC phases remained small.
The current Research page must therefore be measured before introducing more
durable projection or cache machinery.

ADR-068 defines the ownership, freshness, invalidation, and measurement rules for
this continuation.

## Problem

The Research backend may load and deserialize more snapshots and outcomes than the
read-only contract requires, and readiness/integrity may repeat related reads.
There is not yet sufficient evidence to determine whether targeted queries are
enough or whether a shared/precomputed projection would materially improve the
critical path without creating stale or unexplainable truth.

## Scope

### Step 3: targeted persistence projections

- Capture query plans, row counts, transferred data, deserialization cost, and
  projection timing for the current readiness and integrity reads.
- Identify duplicate or unnecessary reads and define the smallest targeted query
  or aggregate needed by the existing contracts.
- Implement targeted persistence projections only where measurements justify them.
- Prove semantic equivalence against the current authoritative implementation.
- Validate indexes, transaction behavior, and migration cost before adding schema
  changes.

### Step 4: shared or precomputed projection

- Measure the post-Step-3 critical path and determine whether a shared or
  precomputed Research projection is justified.
- If justified, define and implement explicit ownership, freshness, invalidation,
  rebuild, degraded-state, and failure semantics under ADR-068.
- If not justified, record the evidence-based rejection and close the step without
  speculative architecture.
- Do not create a new cache authority based only on TTL.

### Validation and observability

- Preserve structured phase timing for cache, persistence, projection, OHLC, replay,
  fee, and total readiness work.
- Compare cold-cache and warm-cache behavior before and after each accepted change.
- Validate readiness/integrity under source outage, partial availability, stale
  projection, invalidation, and rebuild scenarios.
- Record the final decision, measured benefit, operational trade-offs, and
  residual bottleneck in the Story implementation report.

## Out of Scope

- New user-facing Research capabilities or new trading workflows.
- Changing S1 thresholds, blockers, signal semantics, evidence policy, replay,
  fee, OHLC, timeout, concurrency, or remote-call budgets.
- Treating `ready=false` as a performance defect.
- Frontend redesign beyond compatibility with the optimized existing contracts.
- AI, performance analytics, backtesting, or trading recommendations.
- New cache technology, message broker, database, or service solely for speed
  without an accepted ADR and measured justification.
- Changing Gateway authentication, authorization, or route ownership.
- Destructive migration, data deletion, or evidence reclassification.

## Acceptance Criteria

- [ ] A reproducible Step 3 baseline records query plans, row volume,
      deserialization cost, duplicate reads, cache state, and phase timings.
- [ ] Each accepted targeted persistence change has measured benefit and proves
      semantic equivalence with the pre-change authoritative result.
- [ ] Readiness and integrity retain their current independent contracts and
      failure behavior.
- [ ] Cold-cache and warm-cache measurements are recorded before and after the
      accepted Step 3 changes.
- [ ] The post-Step-3 bottleneck is measured and the Step 4 decision is explicit.
- [ ] If Step 4 is implemented, ownership, freshness, invalidation, rebuild,
      degraded-state, and fail-closed behavior conform to ADR-068.
- [ ] If Step 4 is rejected, the rejection is supported by runtime/query evidence
      and the remaining bottleneck is explicitly recorded.
- [ ] No TTL-only cache becomes a source of truth or replaces invalidation.
- [ ] Readiness values, blockers, integrity warnings, timestamps, and freshness
      semantics remain unchanged for identical authoritative inputs.
- [ ] Source outage, partial availability, stale data, invalidation, and rebuild
      behavior are covered by focused tests.
- [ ] Relevant Market Intelligence tests, frontend tests, production build,
      formatting checks, runtime measurements, and `git diff --check` pass.
- [ ] No new product capability or unrelated architecture is introduced.
- [ ] The implementation report records the accepted/rejected status of Steps 3
      and 4, measured results, residual risks, and documentation reconciliation.

## Constraints

- ADR-068 must be accepted before implementing any Step 4 architectural change.
- Preserve the existing Market Intelligence ownership of Research readiness and
  integrity.
- Preserve authoritative Market Data evidence and deterministic S1 behavior.
- Keep projections read-only and account/authorization safe.
- Never convert stale, missing, or unknown evidence into a successful value.
- Prefer targeted queries before shared or precomputed state.
- Do not introduce unbounded fan-out, unsafe retries, or relaxed remote budgets.
- Do not commit, push, merge, or discard unrelated worktree changes automatically.

## Relevant Modules

- `market-intelligence`
- `market-data` - only for measured historical evidence/replay dependencies
- `trading-os-web` - only for contract compatibility and regression validation

## Validation

- Run targeted `market-intelligence` readiness, integrity, persistence, and
  controller tests.
- Run relevant `market-data` replay/evidence tests when query or replay behavior is
  measured.
- Run Angular Research page tests and the production build.
- Run architecture/formatting checks and `git diff --check`.
- Perform controlled authenticated runtime measurements through Gateway.
- Compare request count, critical-path latency, payload size, query volume,
  deserialization time, remote-call volume, cache behavior, and failure behavior.
- Validate identical authoritative inputs produce identical readiness and integrity
  semantics before and after optimization.

## Definition of Done

- [x] ADR-068 accepted by human before any Step 4 architectural implementation.
- [x] Repository Analysis approved by human.
- [x] Implementation Plan approved when required.
- [x] Step 3 measured, implemented where justified, and validated.
- [x] Step 4 implemented only if justified, or explicitly rejected with evidence.
- [x] Relevant validation and runtime measurements pass.
- [x] Documentation reconciliation completed.
- [x] Independent Code Review completed and approved.
- [x] Engineering Report completed.
- [ ] Human commit created.

## Closure Note

The Story is closed by explicit human instruction after the implementation review
and engineering report. The remaining unchecked commit item is intentionally
separate from this agent's work because Git integration remains human-controlled.

## Remaining Gap

The measured Market Data replay bottleneck remains outside the Market Intelligence
ownership boundary. A future Story must evaluate checkpoint-aware replay or
promoted replay artifacts under ADR-063 and ADR-067, including invalidation,
provenance, dependency release, rebuild, and retention semantics.
