# Epic 0238 — Validation Gap Report

**Date:** 2026-11-14
**Status:** Gap analysis complete — pending human review
**Scope:** Residual validation gaps after Story 0245 merge (PR #297, `6d98226`)
**Predecessor:** Integration Audit (`integration-audit.md`) dated 2026-11-14

---

## A. Purpose

This report reclassifies the P1 integration blockers identified in the Epic 0238 Integration Audit
after inspecting the Story 0245 deliverables. It records what was resolved, what was partially
resolved, and what remains open, and identifies the corrective Story 0246 scope.

---

## B. P1 Integration Blocker Reclassification

### F-01 — Gateway routing integration test for `/api/v1/passive-awareness`

| Field | Value |
|---|---|
| Audit status | OPEN — P1 V1 blocker |
| After Story 0245 | **RESOLVED** |
| Evidence | Two new tests in `GatewayDownstreamRoutingIntegrationTest`: `passiveAwarenessGetRoutesToMarketIntelligenceWithJwtForwarding()` and `passiveAwarenessQueryParamsPreservedByGateway()` |

The gateway tests verify:
- Route `/api/v1/passive-awareness` forwards to `market-intelligence` via load-balanced URL
- JWT `Authorization` header is forwarded to the downstream service
- Query parameters (`currentOnly=true`, `size=5`) are preserved by the proxy

**Verdict:** F-01 fully closed. Gateway route regression is now detectable.

---

### F-03 — No cross-service E2E test for the complete V1 passive pipeline

| Field | Value |
|---|---|
| Audit status | OPEN — P1 V1 blocker |
| After Story 0245 | **OPEN** |
| Evidence | `PassiveScanDispatchCoordinator.dispatchOnce()` returns `DispatchResult.disabledResult()` immediately when `passive-dispatch.enabled=false` (default). `grep -r "PassiveScanDispatchCoordinator\|dispatchOnce" market-intelligence/src/test` returns zero matches. |

Story 0245 adds projection-level tests that seed repositories directly. No test exercises the
full pipeline chain:

```text
PassiveMarketUniverseResolver.resolve()
  → PassiveScanDispatchCoordinator.dispatchOnce()
    → PassiveScanRun creation
    → PassiveScanMarket claim + lease
    → AnalysisExecution registration
    → Opportunity persistence (PASSIVE_SCAN origin)
      → PassiveAwarenessProjectionService.query()
```

**Remaining gap:** The coordinator is the central integration point of Epic 0238. Its correct
interaction with the persistence layer under real PostgreSQL schema constraints has not been tested.
A regression in the coordinator's claim, dispatch, or lease-recovery path would not be caught by
any existing test.

**Assigned to:** Story 0246.

---

### F-04 — Deterministic fixture test: qualifying → 1 candidate, non-qualifying → 0, repeated → no flood

| Field | Value |
|---|---|
| Audit status | OPEN — P1 V1 blocker (also R-0242-2 from Story 0242 audit) |
| After Story 0245 | **PARTIALLY_RESOLVED** |
| Evidence | `qualifyingPassiveScanOpportunityAppearsAsCurrentCandidate` (direct seed → projection query), `repeatedAppendOfSameOpportunityProducesExactlyOneCurrentCandidate` (idempotent append), `nonPassiveScanOpportunityIsExcludedFromPassiveFeed` (origin exclusion) |

The projection-level behavior (deduplication, origin exclusion, freshness boundary) is now tested
at the Spring integration level with H2. This satisfies the "no flood from repeated saves" portion
of R-0242-2 and closes the documentation gap in Story 0242.

**Remaining gap:** The qualifying vs non-qualifying determination depends on the
`PassiveMarketUniverseResolver` configuration and the analysis result it triggers through the
coordinator. This determination is **not tested through the pipeline**: the existing test manually
seeds a PASSIVE_SCAN origin opportunity into the repository. A misconfiguration of the universe
resolver or a bug in the coordinator's market selection logic would not be caught.

**Partially closed by Story 0245; residual gap assigned to Story 0246.**

---

## C. Governance Gap Update

The governance gaps identified in the integration audit (G-02-REV, G-03-REV, G-04-CRIT, G-06-DOC)
were resolved retroactively in this session by producing the missing artifacts:

| Gap | Artifact produced | Current status |
|---|---|---|
| G-02-REV (Story 0240 missing review) | `0240-persistent-passive-scan-run/code-review.md`, `engineering-report.md` | ✓ **APPROVED_WITH_NOTES** — pending human commit |
| G-03-REV (Story 0241 missing review) | `0241-bounded-passive-dispatch/code-review.md`, `engineering-report.md` | ✓ **APPROVED_WITH_NOTES** — pending human commit |
| G-04-CRIT (Story 0242 missing all artifacts) | `0242-passive-awareness-lifecycle/implementation-report.md`, `code-review.md`, `engineering-report.md` | ✓ **APPROVED_WITH_NOTES** — pending human commit |
| G-06-DOC (Story 0244 missing engineering report) | `0244-passive-awareness-dashboard/engineering-report.md` | ✓ Produced — pending human commit |

**All governance artifacts are in the working tree as of this session (`70ca1f53`). No further
retrospective governance work is required for Epic 0238 closure.**

---

## D. Epic Closure Criteria Update

| # | Criterion | Before Story 0245 | After Story 0245 | After Story 0246 |
|---|---|---|---|---|
| C-01 | All six Stories (0239–0244) delivered to main | ✓ | ✓ | ✓ |
| C-02 | Story 0242 governance gap resolved | ✗ | ✓ (retrospective artifacts in working tree) | ✓ |
| C-03 | Stories 0240/0241 governance gaps resolved | ✗ | ✓ (retrospective artifacts in working tree) | ✓ |
| C-04 | Story 0244 status and engineering report | ✗ | ✓ (engineering report in working tree) | ✓ |
| C-05 | Gateway routing test for `/api/v1/passive-awareness` (F-01) | ✗ | ✓ | ✓ |
| C-06 | Deterministic fixture test qualifying/non-qualifying/no-flood (F-04) | ✗ | ⚠️ PARTIAL | ✓ |
| C-07 | Cross-service E2E validation (F-03) with coordinator exercised | ✗ | ✗ | **Target** |
| C-08 | Authorization regression at cross-service level | Partial | Partial | ✓ |
| C-09 | Idempotency regression at cross-service level | Partial | Partial | ✓ |
| C-10 | Restart/recovery at cross-service level | Partial | Partial | ✓ |
| C-11 | No broker writes confirmed | ✓ | ✓ | ✓ |
| C-12 | No automatic trade execution from passive path | ✓ | ✓ | ✓ |
| C-13 | Observable failure diagnostics in fixture test | Partial | Partial | ✓ |
| C-14 | Human approval of E2E validation evidence | ✗ | ✗ | Requires Story 0246 approval |

**Epic 0238 is blocked from `V1_INTEGRATION_VALIDATED` by C-07 and C-14.
Story 0246 is the required corrective action.**

---

## E. Story 0245 Final Assessment

| Dimension | Verdict |
|---|---|
| Gateway F-01 closure | ✅ COMPLETE |
| Projection-level correctness (Scenarios B, C, D, H) | ✅ VERIFIED (H2) |
| Coordinator integration (Scenario A, full pipeline) | ✗ NOT_VERIFIED |
| PostgreSQL Testcontainers (AC constraint #2) | ✗ NOT_SATISFIED |
| Net result | Useful projection tests delivered; primary end-to-end claim not met |

The Story 0245 code review (`code-review.md`) verdict is **REQUIRES_CHANGES**. A corrective follow-on
story is required rather than rework of the existing tests, since the existing tests are correct at
the projection level and their value should be preserved.

---

## F. Summary of Open Gaps Requiring Story 0246

| Gap ID | Description | Priority |
|---|---|---|
| GAP-01 | `PassiveScanDispatchCoordinator.dispatchOnce()` never exercised in any integration test | P1 |
| GAP-02 | No Testcontainers PostgreSQL test for the passive scan pipeline | P1 |
| GAP-03 | Qualifying market through coordinator → candidate not verified end-to-end | P1 |
| GAP-04 | Quiet market (no qualifying opportunity) through coordinator not verified | P1 |
| GAP-05 | Lease recovery path under coordinator (crash scenario) not verified on PostgreSQL | P2 |

Story 0246 scope: add a PostgreSQL Testcontainers integration test that calls
`dispatchOnce()` with a controlled universe and asserts the full pipeline output via
`PassiveAwarenessProjectionService.query()`. See `story.md` at
`docs/architecture/stories/0246-passive-scanning-v1-dispatch-integration/story.md`.
