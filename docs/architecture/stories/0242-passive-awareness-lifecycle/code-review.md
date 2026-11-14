# Story 0242 — Code Review

**Date:** 2026-11-14 (retrospective)
**Reviewer:** Independent review (Claude Sonnet 4.6)
**Scope:** `market-intelligence` — passive awareness projection, PassiveCandidateEvaluator, TradePlanningEngine boundary
**Verdict:** **APPROVED_WITH_NOTES**

---

## Findings

### RISK-01 — 19 of 27 diagnostic codes lack explicit test assertions

**Severity:** Medium
**Location:** `PassiveCandidateEvaluator.java` (all diagnostic code branches)
**Description:** The evaluator implements 27 diagnostic rules. Only 8 are explicitly asserted
in test expectations: `THESIS_SUPERSEDED`, `OPPORTUNITY_SUPERSEDED`, `THESIS_NOT_CANDIDATE`,
`THESIS_BLOCKING_CONTRADICTION`, `MARKET_IDENTITY_UNAVAILABLE`, `OBSERVATION_REFERENCE_UNAVAILABLE`,
`MARKET_THESIS_REFERENCE_UNAVAILABLE`, `REQUIRED_EVIDENCE_EXPIRED`.

The remaining 19 (`STRATEGY_MATCH_*` family, `OPPORTUNITY_NOT_ACTIVE`,
`OPPORTUNITY_OUTSIDE_VALIDITY`, `OBSERVATION_NOT_CURRENT`, `THESIS_OUTSIDE_VALIDITY`,
`THESIS_FRESHNESS_INVALID`, `REQUIRED_EVIDENCE_NON_POSITIVE`, `REQUIRED_EVIDENCE_FRESHNESS_INVALID`,
`MARKET_THESIS_OPPORTUNITY_MISMATCH`, `MARKET_IDENTITY_MISSING`, `MARKET_IDENTITY_MISMATCH`,
`PASSIVE_PROVENANCE_INCOMPLETE`, `PASSIVE_SCAN_MARKET_MISMATCH`) have no test that triggers them.

**Risk:** A regression that suppresses one of these codes would go undetected. The evaluator
logic is correct and all branches are reachable; the gap is test coverage, not implementation.
**Recommendation:** Add targeted tests for all 27 codes. Story 0245 includes this in AC-03.

---

### RISK-02 — `findLatestPassive` uses `LIKE '%PASSIVE_SCAN%'` on a TEXT column

**Severity:** Low
**Location:** `SpringDataTradingOpportunityRepository.java:27`
**Description:** The `findLatestPassive` query filters by `WHERE payload LIKE '%PASSIVE_SCAN%'`.
Since `origin` is not a dedicated column, PostgreSQL cannot use an index on this predicate and
must do a sequential scan on `trading_opportunity_versions` for each request. The 1000-item cap
bounds the result size but not the scan cost.

**Risk:** Acceptable at V1 volume. As the opportunity table grows (active + passive + all
versions), this query will degrade. At very large volumes it could become the dominant read
load on a shared table used by the active opportunity pipeline.
**Recommendation:** Add a denormalized `origin` column to `trading_opportunity_versions` and
index it when the table reaches operational scale. Record as a known deferred optimization.

---

### OBSERVATION-01 — Cross-user isolation not explicitly tested for passive awareness

**Severity:** Low
**Location:** `MiSecurityIntegrationTest.java`
**Description:** The security tests verify JWT required (401 without token) and authenticated
access allowed (200 with token). They do not assert that two authenticated users with different
UUIDs see the same response content. This is architecturally correct (passive awareness is
account-independent) but the invariant is not stated as an explicit test contract.
**Recommendation:** Add a test asserting two distinct JWT users receive equivalent projection
content, to document the intentional account-independence as a stable contract.

---

### OBSERVATION-02 — `PassiveCandidateEvaluator.evaluateOne` adds `OPPORTUNITY_SUPERSEDED` after batch evaluation

**Severity:** Low
**Location:** `PassiveCandidateEvaluator.java:82–90`
**Description:** `evaluateOne` appends `OPPORTUNITY_SUPERSEDED` to the diagnostics list produced
by `evaluateBatch`, creating a new projection record. The batch path does not add this code (the
batch by design only evaluates latest versions). This is correct behavior — the story's split
between feed (latest versions only) and single-version lookup (also checks supersession) is
intentional. The test `olderOpportunityVersionIsSuperseded` covers this path.
**Recommendation:** No change needed; worth calling out as a deliberate design choice.

---

### OBSERVATION-03 — `requiredEvidenceNonPositive` logic may be wider than intended

**Severity:** Low
**Location:** `PassiveCandidateEvaluator.java:258–261`
**Description:**
```java
private boolean requiredEvidenceNonPositive(MarketThesisEvidence evidence) {
    return evidence.required() && (evidence.role() != MarketThesisEvidenceRole.SUPPORTING
            || evidence.availability() != MarketThesisAvailability.AVAILABLE);
}
```
This condition marks an evidence item as non-positive if its role is not SUPPORTING **or** if
availability is not AVAILABLE. An item with `role=SUPPORTING` and `availability=MISSING` is
flagged, as is an item with `role=CONTRADICTING` and `availability=AVAILABLE`. This matches
the thesis model where only required SUPPORTING/AVAILABLE evidence qualifies. The logic is
correct but the double-negative condition is not immediately obvious from the predicate.
**Recommendation:** Add a brief comment explaining the intent, or extract to a named method
on `MarketThesisEvidence`.

---

## Safety Verification

| Boundary | Status |
|---|---|
| Passive awareness cannot trigger ActiveScan | ✅ ArchUnit test: `passiveScanningNeverCreatesActiveScans` |
| PassiveCandidate classes have no TradePlan/Risk/broker dependencies | ✅ ArchUnit test: `candidateValidationHasNoTradingAuthorityDependencies` |
| Accountless PASSIVE_SCAN cannot enter TradePlanningEngine | ✅ `TradePlanningEngine.java:134` + regression test |
| JWT required on `/api/v1/passive-awareness` | ✅ `MiSecurityIntegrationTest` |
| No schema migration introduced | ✅ Latest migration V31 predates this story; no new migration |
| No new aggregate introduced | ✅ Projection is a transient read record |

---

## Verdict

The implementation is architecturally correct, safe, and correctly bounded. The qualification
rules are deterministic, properly shared with Story 0243, and correctly guarded by ArchUnit.
The TradePlan isolation boundary is explicitly tested. The primary concerns are test coverage
gaps (RISK-01, addressed in Story 0245) and a known long-term performance risk (RISK-02,
acceptable for V1). No correctness defects found.

**APPROVED_WITH_NOTES** — RISK-01 must be resolved before Story 0245 can be considered
fully validated.
