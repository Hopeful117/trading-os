# Story 0246 — Independent Code Review

**Date:** 2026-11-19
**Reviewer:** Independent repository review
**Scope:** Story 0246 implementation and the two changed files reported by the implementation report
**Verdict:** `APPROVED_WITH_NOTES`

This review records the approved corrective production scope exception and the accepted limitations
for conditional Epic 0238 closure. It is a review record, not a human approval or a merge decision.

---

## 1. Reviewed Scope

- `PassiveScanDispatchIntegrationTest`
- `SpringDataPassiveScanMarketRepository`
- `PassiveScanClaimService`
- `PassiveScanDispatchCoordinator`
- Related analysis-execution repository transitions
- Story 0246 acceptance criteria and implementation evidence

---

## 2. Review Findings

### FINDING-01 — Accepted corrective production defect fix

**Classification:** Accepted corrective production defect fix — documented scope exception
**Severity:** P1 defect discovered and corrected during validation

`PassiveScanClaimService.claim()` runs in a `REQUIRES_NEW` transaction. It first loads a
`PassiveScanMarket`, performs a JPQL bulk update through `claimMarket()`, and then reloads the
market before applying the optimistic versioned transition to `ACCEPTED`.

Without `clearAutomatically = true`, the bulk update changes the database row but does not update
Hibernate's first-level persistence context. The subsequent lookup can therefore return the stale
`REGISTERED`, version-zero entity. The following transition then uses the wrong status and version,
causing the coordinator claim to fail with `Passive scan market claim was lost`.

The change:

```java
@Modifying(clearAutomatically = true)
```

is correct for this path. Clearing the context makes the subsequent lookup read the claimed row
with its incremented version and `DISPATCH_REQUESTED` status.

The approved exception is limited to this annotation. It does not authorize additional production
changes under Story 0246.

### FINDING-02 — Persistence-context clearing risk assessed

**Classification:** No issue identified in the current transaction; defensive risk documented
**Severity:** Low residual risk

Clearing the context can discard unsaved managed changes. In the current `claim()` transaction, the
market is read, capacity is acquired through a bulk/native update, the market is claimed, the
analysis execution is claimed, and the market is reloaded. There is no intentional pending entity
mutation that must survive the clear operation. The transaction boundary is isolated by
`REQUIRES_NEW`, so caller-managed entities are not cleared.

The fix is therefore safe for the reviewed implementation. Future changes that add pending managed
updates to this transaction must preserve the flush and reload ordering explicitly.

### FINDING-03 — Related bulk update methods

**Classification:** No confirmed defect; defensive improvement not required for this Story

The following methods were inspected:

- `transitionStatus()`
- `recordOutcome()`
- `recoverExpired()`

They use versioned conditional updates and are called through repository/application boundaries that
do not currently perform the same stale-entity read-after-bulk-update sequence as `claim()`. No
confirmed defect was found that justifies changing them in Story 0246. Adding automatic context
clearing later may be a defensive improvement, but it should be driven by a demonstrated read-after-
bulk-update requirement and validated transactionally rather than applied mechanically.

The comparable analysis-execution and capacity bulk updates were also reviewed. Their current
conditional updates preserve optimistic/concurrency predicates. No new production change is
recommended from this review.

### FINDING-04 — Integration test exercises the coordinator boundary, not the complete analysis pipeline

**Classification:** Accepted V1 residual limitation
**Severity:** P2 evidence gap

The test does exercise `PassiveScanDispatchCoordinator.dispatchOnce()` against PostgreSQL and
verifies durable `PassiveScanRun`, `PassiveScanMarket`, and `AnalysisExecution` registration.

The downstream analysis dispatcher is mocked. The test then completes the registered execution and
manually seeds the observation, `MarketThesis`, `StrategyMatch`, and `TradingOpportunity` records
used by the passive awareness projection. Consequently, it verifies the coordinator/persistence
boundary and the projection query, but not the complete production analysis pipeline from dispatched
analysis through generated opportunity.

This limitation is accepted for conditional V1 closure under D-02. It must not be described as full
cross-service or full production analysis E2E coverage.

### FINDING-05 — Scheduler bean is mocked rather than absent

**Classification:** Accepted V1 test-scope limitation
**Severity:** P2 acceptance wording mismatch

The test enables `market-intelligence.passive-dispatch.enabled=true`, which causes the conditional
scheduler bean to be created. `@MockitoBean PassiveScanDispatchScheduler` replaces it with a no-op
mock, preventing scheduled execution during the test.

This provides effective scheduler isolation for the test, but it does not satisfy the literal Story
0246 requirement that the scheduler bean be absent. AC-04 must therefore remain partially satisfied
and must not be reported as literal compliance.

### FINDING-06 — Qualifying and quiet scenarios are bounded but not production-generated

**Classification:** Accepted V1 residual limitation
**Severity:** P2 evidence gap

Scenario A proves that a qualifying projection can be queried after coordinator persistence and
execution registration. It does not prove that the real analysis dispatcher generated the seeded
artifacts.

Scenario B proves that no pre-existing candidate is returned after a quiet mocked dispatch. It does
not prove a completed real analysis result of `NO_MATCH` or an equivalent non-positive outcome.

The existing projection and strategy tests provide supporting evidence for those domain rules.
The missing integrated downstream generation evidence is accepted under D-02.

---

## 3. Acceptance Criteria Assessment

| AC | Assessment | Evidence / limitation |
|---|---|---|
| AC-01 | SATISFIED | `PostgreSQLContainer`, Spring Boot context, PostgreSQL driver, and Flyway-backed schema are configured in the test. |
| AC-02 | PARTIALLY_SATISFIED | Coordinator, run, market, and execution registration are real; downstream artifacts are completed or seeded manually. |
| AC-03 | PARTIALLY_SATISFIED | Quiet projection result is verified, but the analysis dispatcher is mocked and no real non-positive analysis result is produced. |
| AC-04 | PARTIALLY_SATISFIED | Scheduler execution is prevented by a mock; the bean is present rather than absent. |
| AC-05 | SATISFIED | `MarketDataClient` is mocked and deterministic facts are supplied. |
| AC-06 | SATISFIED | A controlled `Clock` is supplied and used for freshness-sensitive assertions. |
| AC-07 | REPORTED SATISFIED | The implementation report records the relevant projection and Gateway regression suites as passing; this review did not rerun them. |
| AC-08 | REPORTED SATISFIED | The implementation report records the Market Intelligence build as passing with 1091 tests; this review did not rerun it. |

---

## 4. Safety and Transaction Assessment

| Property | Assessment |
|---|---|
| PostgreSQL schema behavior | Covered by the reported Testcontainers test and Flyway migrations; not rerun in this review. |
| Optimistic claim transition | Correct conditional version/status predicates; stale-cache defect corrected. |
| Claim lease behavior | Coordinator claim and capacity acquisition are exercised; restart recovery remains unverified at PostgreSQL integration level. |
| Idempotency | Existing deterministic keys and repository constraints provide supporting evidence; repeated dispatch is not asserted by this test. |
| External providers | `MarketDataClient` is mocked; no live provider dependency is intended. |
| Scheduler safety | Scheduled execution is prevented by a mock; bean-absence wording is not met literally. |
| Broker safety | No broker dependency or broker write is introduced by the passive path. |
| ActiveScan safety | Passive scanning remains separated from automatic ActiveScan creation by architecture tests and package boundaries. |
| TradePlan/risk safety | Passive dispatch does not create TradePlans or invoke risk authority. |

---

## 5. Verdict

`APPROVED_WITH_NOTES`

No unresolved P0/P1 production defect was identified after reviewing the corrective JPA change and
the surrounding transaction boundaries. The PostgreSQL test is valuable and exposes the real claim
path. Its evidence is intentionally narrower than a complete production analysis E2E test, and the
scheduler isolation is effective but not literal bean absence.

The review supports conditional Epic 0238 closure only with the accepted scope exception and residual
limitations documented in the Epic closure report. Human approval remains required.
