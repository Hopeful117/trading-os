# Implementation Report - Story 0243

## Summary

A trader can now launch the **existing** ActiveScan from a passive candidate.
The candidate is identified by its `TradingOpportunity(PASSIVE_SCAN)` id and
version. The change is additive and confined to the `market-intelligence`
module:

- two optional request fields;
- server-side candidate revalidation, sharing one rule set with the Story 0242
  feed;
- a hash-stable fingerprint extension;
- immutable provenance stored in the existing `active_scans.payload` JSON;
- read-projection fields.

There is no new endpoint, controller, aggregate, Flyway migration, configuration
change or dependency.

| Item | Value |
|------|-------|
| Base commit | `7aa7a2a80cd7e60423222a2bc3a9079993a4e725` |
| Branch | `story/0243-passive-to-active-investigation` (uncommitted) |
| Approved inputs | Repository Analysis and Implementation Plan, both human-approved 2026-11-14; decisions D1–D5 at defaults |

## Implemented

### 1. Shared candidate evaluator (D2)

- New `application/passive/PassiveCandidateEvaluator` (`@Component`) holds the
  per-candidate rules moved unchanged from `PassiveAwarenessProjectionService`.
- New rule `THESIS_SUPERSEDED`: a referenced thesis is superseded when another
  thesis names it in `supersedes`. This is resolved by the new batch port method
  `MarketThesisRepository.findSupersededThesisIds`, a JPA `select distinct
  e.supersedes … where e.supersedes in :ids`. It fixes R-0242-1 in the feed as well.
- New `evaluateOne(id, version, now)` handles the exact-version path. It adds
  `OPPORTUNITY_SUPERSEDED` when a newer version exists, and reports Market Data
  unavailability separately so callers can fail closed.
- `PassiveAwarenessProjectionService` keeps its public `search` contract and
  delegates to the evaluator. The legacy 6-argument constructor is retained.

### 2. Request and command

- `CreateActiveScanRequestDto` and `CreateActiveScanCommand` gain
  `sourcePassiveOpportunityId` and `sourcePassiveOpportunityVersion`.
- If only one is present: 400 `PASSIVE_CANDIDATE_REFERENCE_INCOMPLETE`. A
  version below 1: 400 `PASSIVE_CANDIDATE_REFERENCE_INVALID`.
- All existing constructors are retained.
- New value object `domain/scan/PassiveCandidateReference`.

### 3. Fingerprint

`ActiveScanFingerprintFactory` adds the candidate to `FingerprintPayload` with
`@JsonInclude(NON_NULL)`. A golden test pins the legacy hash
`fd275f3b…590a`, computed on the base commit before any change, and it still
matches.

### 4. Provenance (D1, D4)

- New immutable `domain/scan/PassiveCandidateProvenance` and
  `PassiveEvidenceAssessment` (codes only).
- `ActiveScan` gains an optional field with no mutator. Every transition carries
  it, and the legacy factory signatures are retained.
- `JpaActiveScanRepository.ActiveScanPayload` gains a nullable component. **No
  migration.**

### 5. Validation in creation

New `application/scan/PassiveCandidateScanValidator`, invoked in
`ActiveScanCreationTransaction.create` after the in-transaction idempotency
re-check and before scope resolution, with a single captured `clock.instant()`.

| Condition | Result |
|-----------|--------|
| Unknown version | 404 `PASSIVE_CANDIDATE_NOT_FOUND` |
| Non-passive or account-bound opportunity | 422 `PASSIVE_CANDIDATE_INVALID_ORIGIN` |
| Scope is not `SELECTED` = [candidate market] (D3) | 422 `PASSIVE_CANDIDATE_MARKET_MISMATCH` |
| Newer opportunity version or thesis superseded | 409 `PASSIVE_CANDIDATE_SUPERSEDED` |
| Market Data down | 503 `PASSIVE_CANDIDATE_IDENTITY_UNAVAILABLE` |
| Any other diagnostic | 409 `PASSIVE_CANDIDATE_NOT_CURRENT`, with the codes listed |

- Replay never revalidates.
- The legacy test constructor fails closed with 503
  `PASSIVE_CANDIDATE_VALIDATION_UNAVAILABLE`.
- `buildMarkets` is unchanged, so active executions remain
  `AnalysisExecution(ACTIVE)` with `LIVE_CURRENT`.
- The evidence assessment is deterministic:
  - EVIDENCE_POLICY: passive execution provenance.
  - CAPABILITIES: real `PassiveAnalysisStrategy.plan` vs
    `ActiveAnalysisStrategy.plan` for the request's tradeability profile.
  - CUTOFF: passive `evaluationCutoff`.
  - FRESHNESS: thesis `sourceCutoff` vs the active 30 s bound.
  - Any non-match is recorded `INCOMPATIBLE_REACQUIRED`. Nothing is reused for
    computation.

### 6. Read projections

`ActiveScanResultProjection`, `ActiveScanResponse` and `ActiveScanSummary` expose
a nullable `sourcePassiveCandidate`, through the new
`PassiveCandidateSourceResponse`. The field is `null` for legacy scans, and
existing fields are unchanged.

## Deviations from the Approved Plan

1. **Thesis supersession lookup.** The plan named
   `MarketThesisRepository.findLatest(market, instrument, direction)` with a
   fallback to "a narrow batch port method". I used the fallback,
   `findSupersededThesisIds`. It checks the exact `supersedes` relation already
   used by the repository's own `findLatestValid` query. It is a single batched
   query and does not depend on validity windows or direction-string
   normalization.
2. **No `FeatureSnapshotRepository` read.** The active execution's context does
   not exist yet when the scan is created, so a context-fingerprint comparison is
   impossible at that point. Capability compatibility is computed from the real
   strategy plans instead. The validator therefore depends on
   `AnalysisExecutionRepository.findById` and the two strategy beans.
3. **`ActiveScanScopeResolutionService.CURRENT_MARKET_EVIDENCE_MAX_AGE`** went
   from `private` to `public` so the validator reuses the existing 30 s bound
   instead of duplicating it. No behavior change.
4. **Concurrent same-key creation.** No new multi-threaded test was added. The
   candidate fields only change the fingerprint input, and the race branch in
   `ActiveScanApplicationService.create` is unchanged. It remains covered by
   `ActiveScanConcurrencyIntegrationTest` (green). A same-key, different-version
   conflict is covered sequentially in `PassiveCandidateActiveScanCreationTest`.

5. **Validation order.** The scope check (422
   `PASSIVE_CANDIDATE_MARKET_MISMATCH`) runs right after the origin check,
   before the superseded/unavailable/not-current checks. The plan listed it
   last. The scope check needs no evidence, so a malformed request gets a
   deterministic client error first. Consequence for Story 0244: a request that
   is both mis-scoped and stale receives 422, not 409.
6. **Story 0242 status line.** `0242-passive-awareness-lifecycle/story.md` (status
   line only) was changed during Repository Analysis, as recorded in analysis
   section 20. It is outside this plan's file list and is included in this diff
   for transparency.

## Documentation Outcome

`Documentation update: Not required`.

The canonical API description in `README.md` (section "Market Intelligence")
documents the analyses and `scans/scope` endpoints, but not the
`POST /api/v1/intelligence/scans` request body. So there is no existing contract
text that the new optional fields make inaccurate. Other mentions are historical
reports and validation records, which are not rewritten. The Story 0244 frontend
contract is defined in the Repository Analysis, section 14.

## Vault Outcome

- Vault consulted during Repository Analysis: no.
- Outcome: **no vault action**. The work is repository-specific. The one
  transverse lesson ("pin a golden hash before adding optional fields to a
  hashed idempotency payload; use `NON_NULL`") is recorded here and in the test's
  Javadoc. It does not justify a proposal on its own.
- No `vault-outcome.json` or proposal artifact was generated.

## Validation

All runs used the local Maven wrapper (`trading-core/mvnw -o`) in
`market-intelligence`, with deterministic fixtures, mocked Feign clients and
the H2 test profile. No cTrader, Kraken, AI, broker or account-sync contact.

### New tests (41 cases, all passing)

| Class | Cases | Covers |
|-------|-------|--------|
| `PassiveCandidateEvaluatorTest` | 8 | Qualifying; superseded thesis (feed + single); WATCH/NO_TRADE; blocking contradiction; older version superseded; unknown version; Market Data failure; repeated detection gives one item |
| `PassiveCandidateScanValidatorTest` | 12 | Full provenance; evidence matrix (profile-dependent capabilities, undeclared and recent cutoff, STRICT_OBSERVED policy, stale freshness, missing passive execution); 404 / 422 origin / 422 scope / 409 superseded / 409 not current / missing refs / provider mismatch / 503 |
| `PassiveCandidateActiveScanCreationTest` | 9 | Valid scan with `ACTIVE` + `LIVE_CURRENT` + account; invalid candidate persists nothing and skips scope resolution; replay without revalidation after expiry; same key with different version gives 409; legacy request on a candidate key gives 409; ineligible account gives `COMPLETED_NO_WORK` with provenance; ownership failure propagates; incomplete reference gives 400; legacy wiring fails closed; no writes to passive sources |
| `ActiveScanFingerprintLegacyStabilityTest` | 2 | Golden legacy hash; candidate and version change the hash |
| `PassiveCandidateProvenancePersistenceTest` | 2 | JPA payload round-trip; legacy payload without the field loads |
| `PassiveCandidateProvenanceImmutabilityTest` | 3 | Survives all transitions; defensive copies; legacy factories carry none |
| `PassiveCandidateScanControllerTest` | 3 | Fields reach the command; provenance in JSON; `{code, error}` mapping; 400 on incomplete reference |
| `PassiveToActiveArchitectureTest` (ArchUnit) | 2 | `application.passive` never depends on ActiveScan creation; `PassiveCandidate*` has no tradeplan/risk/broker/trading-core/AI dependency |

`ActiveScanReconciliationServiceTest` gains 1 case: a `COMPLETED_NO_OPPORTUNITY`
candidate scan keeps provenance in the projection and the stored scan.

### Existing suites

Run after the change: 134 tests across the passive awareness, ActiveScan,
scope resolution, TradePlan and security suites. 0 failures.

### Module verify and quality

| Check | Result |
|-------|--------|
| `../trading-core/mvnw -o verify` (market-intelligence, full suite) | **BUILD SUCCESS**: 1082 tests, 0 failures, 0 errors, 0 skipped. JaCoCo: "All coverage checks have been met". |
| Module LINE coverage (JaCoCo XML) | 80.64% (17433/21617), at or above the 80% backend gate in `scripts/check-backend-coverage.py` |
| New class coverage | `PassiveCandidateScanValidator` 92/97, `PassiveCandidateEvaluator` 120/137, `PassiveCandidateSourceResponse` 16/16, `PassiveCandidateProvenance` 7/8 lines |
| `git diff --check` | Clean |
| `./scripts/quality-verify.sh` | **Not executed in full.** It verifies all 8 Java modules and runs `npm ci` plus the frontend tests. Only `market-intelligence` changed, so the applicable part (module verify + its coverage gate) was run. The 7 untouched modules and the frontend were not re-verified. |
| SonarQube | **Not executed.** No local SonarQube analysis was run, so no Quality Gate status is claimed. |

## Safety

- No automatic ActiveScan: enforced by ArchUnit.
- No TradePlan, risk, broker, account-synchronization or AI dependency: enforced
  by ArchUnit and by the collaborator set of the creation test.
- Passive opportunity, thesis and observation records are never written:
  verified with mock `never()` checks.
- No scans, scheduler activation, backfills, deployments, credential changes,
  commits or pushes were performed.

## Residual Risks

- **Candidate-version race.** It cannot be closed atomically: opportunities are
  append-only, with nothing to lock. The provenance records the exact validated
  version and `validatedAt`, and no "latest at commit time" claim is made.
- **Candidate validation runs before account authorization.** This is the
  approved order. A caller with a foreign account and a stale candidate receives
  409 `PASSIVE_CANDIDATE_NOT_CURRENT` instead of the account 404. No account data
  leaks: candidates are account-independent, and messages contain diagnostic
  codes only. The request costs one Market Data call and some DB reads before the
  account check. `authorizeScanAgent` still runs first for agent principals.
- **Remote call inside the creation transaction.** The validator adds one Market
  Data identity call inside the creation transaction. Scope resolution already
  made remote calls there (pre-existing R6).
- **Feed behavior change (intended).** Candidates whose thesis is superseded now
  appear as `NON_POSITIVE` in `GET /api/v1/passive-awareness`.
- **Pre-existing, unchanged:** `brokerAccountId` is absent from the fingerprint;
  the inherited Story 0242 gaps R-0242-2 to R-0242-5 remain.
- **Story 0242 review still missing.** The Story 0242 independent review gate is
  still open. Part of the 0242 behavior is now exercised by the new evaluator
  tests.

## Next Workflow Stage

Code Review. This report does not constitute approval.
