# Engineering Report - Story 0243

## 1. Repository Baseline

| Item | Value |
|------|-------|
| Base commit | `7aa7a2a80cd7e60423222a2bc3a9079993a4e725` (`main`, merge of PR #294) |
| Branch | `story/0243-passive-to-active-investigation` |
| State | Uncommitted; ready for human commit |
| Unrelated files | Untracked root `CLAUDE.md`, preserved and not part of this Story |

## 2. Workflow and Approvals

| Gate | Artifact | Human approval |
|------|----------|----------------|
| Gate 1 - Repository Analysis | `repository-analysis.md` | 2026-11-14, "Approve with defaults" (D1–D5 at the recommended options) |
| Gate 2 - Implementation Plan | `implementation-plan.md` | 2026-11-14, "Approve the plan" |
| Code Review | `code-review.md` | 2026-11-14, "Approve the review" |

DevLog lifecycle registration was skipped. `TOOLS.md` has no DevLog repository
mapping (`DEVLOG_LIFECYCLE_ERROR`, non-blocking). DevLog context was consulted
once during analysis. It returned truncated, low-confidence metadata only.

## 3. Outcome

A trader can explicitly launch the **existing** account-aware ActiveScan from a
passive candidate. The candidate is identified by its
`TradingOpportunity(PASSIVE_SCAN)` id and version. The server revalidates the
candidate, all existing account, challenge and eligibility controls stay
authoritative, and the scan records immutable passive provenance.

There is no new endpoint, analysis engine, aggregate, migration, configuration
or dependency. Existing callers are unaffected: their fingerprints are
byte-identical, as proven by a golden-hash test.

## 4. Implementation Summary

- Shared `PassiveCandidateEvaluator`: one rule set for the 0242 feed and the
  0243 scan path. It adds `THESIS_SUPERSEDED`, which closes R-0242-1 in the feed.
- Optional `sourcePassiveOpportunityId` and `sourcePassiveOpportunityVersion` on
  the existing request and command. Sending only one returns 400.
- `PassiveCandidateScanValidator` inside `ActiveScanCreationTransaction.create`,
  with explicit codes:
  - 404 `NOT_FOUND`
  - 422 `INVALID_ORIGIN`, 422 `MARKET_MISMATCH`
  - 409 `SUPERSEDED`, 409 `NOT_CURRENT`
  - 503 `IDENTITY_UNAVAILABLE`, 503 `VALIDATION_UNAVAILABLE`
  - Replay never revalidates.
- Fingerprint extended with `NON_NULL` candidate fields.
- Immutable `PassiveCandidateProvenance`, with a deterministic evidence
  assessment, stored in the `active_scans.payload` JSON. Projected as
  `sourcePassiveCandidate` in the scan response and summary.
- Active executions remain `AnalysisExecution(ACTIVE)` with `LIVE_CURRENT`.
  Passive evidence is referenced only (D1).

Six deviations from the plan are recorded and accepted in review. See the
Implementation Report, "Deviations from the Approved Plan".

## 5. Acceptance Criteria

| Criterion | Status | Evidence |
|-----------|--------|----------|
| Active investigation always requires an explicitly selected authorized account | Met | Existing `@NotNull accountId` and `requireOwnedAccount`, unchanged. Ownership failure propagates (creation test). |
| Existing ActiveScan eligibility remains authoritative | Met | The candidate only sets `requestedMarketIds`. Ineligible account gives `COMPLETED_NO_WORK` (creation test). |
| Passive evidence reused only when compatibility and freshness pass | Met (reference-only, D1) | Evidence assessment tests. Nothing is reused for computation. |
| Incompatible evidence reacquired or reported unavailable | Met | `LIVE_CURRENT` asserted on the registered execution. Unavailable identity returns 503. |
| Passive-to-active provenance preserved in request and result | Met | Fingerprint, payload round-trip, controller JSON and reconciliation tests |
| Candidate identity and version immutable and in read projections | Met | Immutability test; projection, response and summary fields |
| Stale or expired candidates cannot silently become current | Met | 409 `NOT_CURRENT` / `SUPERSEDED` tests; feed supersession test |
| Active investigation may truthfully complete with no opportunity | Met | Reconciliation test: `COMPLETED_NO_OPPORTUNITY` with provenance retained |
| No automatic active scan from passive scheduling | Met | ArchUnit rule over `application.passive` (includes the 0241 scheduler and coordinator) |
| Existing ActiveScan idempotency and authorization tests remain green | Met | Full module verify green, plus golden-hash test |
| No TradePlan, Risk or broker operation | Met | ArchUnit dependency rule; creation-path collaborators; no-write verifications |

## 6. Tests Executed

| Run | Result |
|-----|--------|
| `market-intelligence` full `verify` | **1082 tests, 0 failures, 0 errors, 0 skipped**. JaCoCo checks met, line coverage 80.64%. |
| New tests | 41 cases in 8 classes, plus 1 added reconciliation case. All passing; the last validator addition was re-run after the review. |
| `git diff --check` | Clean |
| Not executed | Full `scripts/quality-verify.sh` (7 untouched modules + frontend `npm ci`/tests); SonarQube |

All tests are deterministic. Nothing contacted cTrader, Kraken, AI, a broker or
account synchronization.

## 7. Code Review

An independent read-only review agent recommended approval and found no
confirmed defects. Every finding was resolved, disclosed, or accepted as a
residual risk; see `code-review.md`. The human approved the review on
2026-11-14.

## 8. Documentation and Vault Outcome

- **Documentation update: Not required.** `README.md` does not document the
  `POST /scans` request body, so no canonical contract text became inaccurate.
- **Story 0242 status line** was reconciled with the merged state during
  analysis. Its own Implementation Report and independent review are still
  missing.
- **Vault:** not consulted; outcome is no vault action; there is no proposal
  artifact.

## 9. Residual Risks

- **Candidate-version race.** It cannot be closed atomically: opportunities are
  append-only. The provenance records the validated version and `validatedAt`.
- **Error ordering.** Candidate validation precedes the account check, so a
  foreign account with a stale candidate returns 409 rather than 404. No data
  leaks.
- **Error precedence for Story 0244.** Scope mismatch is checked before
  freshness: a request that is both mis-scoped and stale returns 422, not 409.
- **Transaction scope.** One Market Data identity call runs inside the creation
  transaction, in addition to the pre-existing scope-resolution calls.
- **Pre-existing, unchanged:**
  - `brokerAccountId` is not part of the fingerprint.
  - Story 0242 gaps R-0242-2 to R-0242-5 remain.
  - The Story 0242 review gate is still open.

## 10. Final Verdict

Story 0243 is implemented, validated at module level, and independently
reviewed, with all three human gates approved. It is ready for human commit and
PR. Integration (commit, push, PR, merge) has not been performed.

## 11. Recommended Next Step

Commit the Story 0243 artifact set on
`story/0243-passive-to-active-investigation` and open a PR. CI then runs the full
multi-module quality gate and SonarQube, neither of which was executed locally.
Story 0244 can then consume the contract in Repository Analysis section 14.
