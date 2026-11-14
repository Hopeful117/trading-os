# Code Review - Story 0243

## Review Context

| Item | Value |
|------|-------|
| Reviewer | Independent read-only `review` agent, separate from the implementing session |
| Scope | Uncommitted working tree on `story/0243-passive-to-active-investigation`, base `7aa7a2a8` |
| Inputs | Story, approved Repository Analysis (D1–D5), approved Implementation Plan, Implementation Report, ADR-102 sections 8–10 |
| Method | Static reading of the code and diff (including a comparison of `PassiveAwarenessProjectionService` with base `7aa7a2a8`). The reviewer had no shell. Build and test evidence comes from the implementing session's runs, recorded in the Implementation Report. |

## Verdict

**APPROVE_RECOMMENDED.** No confirmed defects.

This is a recommendation. It is not human approval.

## Findings and Resolution

| # | Classification | Finding | Resolution |
|---|----------------|---------|------------|
| 1 | Material risk | Module verify and quality results were a placeholder at review time | **Resolved.** Full module `verify`: 1082 tests, 0 failures, JaCoCo gate met (80.64% line). `git diff --check` clean. `quality-verify.sh` (full) and SonarQube are explicitly reported as not executed. |
| 2 | Improvement | Validation order differs from the plan: scope is checked before superseded/not-current | **Disclosed** as Deviation 5, with the Story 0244 consequence noted |
| 3 | Improvement | Candidate validation precedes account authorization: a foreign account plus a stale candidate gives 409, not 404, with no data leak | **Accepted residual risk.** Matches the approved plan; recorded in the report. |
| 4 | Improvement | Evidence-matrix gaps: recent CUTOFF and non-`LIVE_CURRENT` policy; ownership 404 only proves propagation; no security-integration test with candidate fields | **Partially resolved.** Added `declaredRecentCutoffIsCompatibleAndStrictObservedPolicyIsReacquired` (passes). Ownership remains covered by the existing `ActiveScanScopeResolutionServiceTest`. The auth filter chain is unchanged and covered by `MiSecurityIntegrationTest`. |
| 5 | Improvement | Story 0242 status-line edit is outside the plan's file list | **Disclosed** as Deviation 6. The edit originates from Repository Analysis section 20. |
| 6 | Insufficient evidence | Does the ArchUnit rule cover the Story 0241 scheduler? | **Resolved.** `PassiveScanDispatchScheduler` and `PassiveScanDispatchCoordinator` reside in `application.passive`, which the rule constrains. |

## Verified by the Reviewer

- **Legacy fingerprint stability.** Record-component `@JsonInclude(NON_NULL)`
  omits the null candidate fields under both the plain and the Spring
  `ObjectMapper`. A golden test pins the pre-change hash.
- **Compatibility preserved:**
  - All legacy constructors and factories remain.
  - `@Autowired` is on the production constructors.
  - Legacy payload JSON loads.
- **Fail-closed behavior.** Explicit codes, no silent fallback, replay without
  revalidation, and a different candidate or version under the same key returns
  409.
- **Feed refactor matches base semantics.** The only differences are the
  intended `THESIS_SUPERSEDED` and benign ordering and merge tolerance.
- **JPQL.** `findSupersededThesisIds` guards empty input, excludes null
  `supersedes`, and matches `findLatestValid` semantics.
- **Immutability and authority:**
  - Provenance is immutable.
  - Passive sources are never written.
  - Active executions stay `ACTIVE` with `LIVE_CURRENT`.
  - No tradeplan, risk, broker or AI dependency.
  - The existing auth path is unchanged.
- **Listed deviations 1–4 are acceptable.**

## Remaining Gates

- Human approval of this Code Review.
- Commit, push and PR remain human-controlled. None was performed.

## Human Decision Record

On 2026-11-14 the human engineer approved this Code Review in the session with
the instruction "Approve the review". Commit, push and PR remain separate human
actions.
