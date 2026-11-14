# Implementation Plan - Story 0243

## Overview

This plan lets a trader launch the **existing** ActiveScan from a passive
candidate, identified by the `TradingOpportunity(PASSIVE_SCAN)` id and version.
It adds no new endpoint, analysis service, aggregate or migration. The changes
are additive:

- two optional request fields;
- server-side candidate revalidation, sharing one rule set with the Story 0242
  feed;
- a fingerprint extension that keeps legacy requests hash-stable;
- immutable provenance stored in the existing `active_scans.payload` JSON;
- read-projection fields.

Active analysis keeps reacquiring evidence through `LIVE_CURRENT`. Passive
evidence is referenced and assessed, never substituted.

This plan is based on the Repository Analysis that the human approved on
2026-11-14, with decisions D1–D5 set to the recommended defaults (see the
analysis "Human Decision Record"). It is a technical recommendation only. It
does not authorize implementation.

All paths are relative to
`market-intelligence/src/main/java/com/hope/trading/market_intelligence/`
(main) or the matching `src/test/java/...` (tests). Only the `market-intelligence`
module and Story documentation change.

## Planned Changes

### 1. Shared passive candidate evaluator (D2)

- Create `application/passive/PassiveCandidateEvaluator` (public, stateless
  `@Component`). Move the per-item rules from
  `PassiveAwarenessProjectionService.project(...)` and `resolveMarket(...)` into
  it, unchanged, as `evaluate(opportunity, now, references)`. It returns the same
  `PassiveAwarenessProjection`, with diagnostics and a `current` flag.
- Add the missing **thesis supersession** rule: diagnostic `THESIS_SUPERSEDED`.
  It fires when a newer thesis exists for the same market, instrument and
  direction, using `MarketThesisRepository.findLatest(marketId, instrument,
  direction)`. Version comparison on the same `lineageId` is the tie-breaker.
  - Batch form for the feed: one `findLatest` per distinct
    (market, instrument, direction) in the bounded source of at most 1,000
    items. If profiling shows this is too costly, add a narrow
    `findLatestByMarketIds` port and adapter method instead.
- `PassiveAwarenessProjectionService` keeps its public `search(...)` contract,
  batch-loads the references as today, and delegates to the evaluator.
  Existing feed behavior is unchanged, except that superseded-thesis candidates
  become `NON_POSITIVE`. This is the intended correction of R-0242-1.
- Add `evaluateOne(OpportunityId, OpportunityVersion, Instant now)`. It loads a
  single candidate's references (opportunity, latest version, StrategyMatch,
  observations, thesis, latest thesis, Market Data identity) for the ActiveScan
  path. Market Data failure raises `MARKET_IDENTITY_UNAVAILABLE`, which callers
  treat as unavailable (fail-closed).

### 2. Request and command fields

- `adapter/web/CreateActiveScanRequestDto`: add nullable
  `UUID sourcePassiveOpportunityId` and `Long sourcePassiveOpportunityVersion`.
  Keep both existing convenience constructors, delegating with `null`.
- `application/scan/CreateActiveScanCommand`: add the same two fields. Keep all
  four existing constructors, delegating with `null`.
  - Add `Optional<PassiveCandidateReference> passiveCandidate()`.
  - The compact constructor rejects exactly one present field with
    `ActiveScanException("PASSIVE_CANDIDATE_REFERENCE_INCOMPLETE", ..., 400)`.
- Create the value object `domain/scan/PassiveCandidateReference(UUID opportunityId, long version)`,
  with version ≥ 1.
- `MarketIntelligenceController.createScan`: pass the two fields through. There
  is no other controller change.

### 3. Idempotency fingerprint

- `ActiveScanFingerprintFactory`: add an overload taking
  `PassiveCandidateReference` (nullable). Existing overloads delegate with `null`.
- `FingerprintPayload`: add `sourcePassiveOpportunityId` and
  `sourcePassiveOpportunityVersion`, both annotated
  `@JsonInclude(JsonInclude.Include.NON_NULL)`. Legacy payload bytes, and so
  legacy hashes, stay identical.
- `ActiveScanApplicationService.create`: compute the fingerprint with the
  candidate reference. The existing replay, conflict and
  `DataIntegrityViolationException` race logic is unchanged.

### 4. Immutable provenance (D1, D4)

- Create `domain/scan/PassiveCandidateProvenance`, an immutable record with
  defensive list copies:
  - `opportunityId`, `opportunityVersion`
  - `strategyMatchId`, `observationIds`
  - `thesisId`, `thesisVersion`
  - `passiveScanRunId`, `passiveScanMarketId`, `passiveAnalysisExecutionId`
  - `marketId`, `provider`, `productType`
  - `opportunityEvaluatedAt`, `thesisSourceCutoff`, `validUntil`, `validatedAt`
  - `List<PassiveEvidenceAssessment> evidenceAssessment`
- Create `domain/scan/PassiveEvidenceAssessment(dimension, status, reason)`, with
  enums:
  - dimension: `EVIDENCE_POLICY`, `CAPABILITIES`, `CUTOFF`, `FRESHNESS`
  - status: `COMPATIBLE_REFERENCE_ONLY`, `INCOMPATIBLE_REACQUIRED`
  - Codes only; no evidence payloads.
- `domain/scan/ActiveScan`:
  - Add an optional `passiveCandidate` field with no mutator.
  - Add overloads of `readyToDispatch`, `completedNoWork` and `restore` that
    accept it. Existing signatures delegate with `null`.
  - `markDispatchRequested` and `reconcileTo` carry the field through unchanged.
  - Accessor: `Optional<PassiveCandidateProvenance> passiveCandidate()`.
- `adapter/persistence/JpaActiveScanRepository`: extend the private record
  `ActiveScanPayload(scopeSnapshot, passiveCandidate)` with a nullable second
  component, and map it in `toEntity` / `toDomain`. Legacy JSON without the field
  deserializes to `null`. **No Flyway migration.**

### 5. Validation inside creation

- Create `application/scan/PassiveCandidateScanValidator` (`@Component`), using
  `PassiveCandidateEvaluator`, `AnalysisExecutionRepository.findById` (passive
  execution provenance: evidence policy, profile, cutoff), the existing
  `FeatureSnapshotRepository` port (passive context fingerprint, read only), and
  `Clock`. Method:
  `validate(PassiveCandidateReference, CreateActiveScanCommand, Instant now)`
  returns `PassiveCandidateProvenance`. Checks, in this order, each failing with
  `ActiveScanException`:

  | Check | Failure |
  |-------|---------|
  | Opportunity version not found | 404 `PASSIVE_CANDIDATE_NOT_FOUND` |
  | `origin != PASSIVE_SCAN` or `accountId` present | 422 `PASSIVE_CANDIDATE_INVALID_ORIGIN` |
  | Requested version is not the latest | 409 `PASSIVE_CANDIDATE_SUPERSEDED` |
  | Diagnostic `THESIS_SUPERSEDED` | 409 `PASSIVE_CANDIDATE_SUPERSEDED` |
  | Diagnostic `MARKET_IDENTITY_UNAVAILABLE` | 503 `PASSIVE_CANDIDATE_IDENTITY_UNAVAILABLE` |
  | Any other diagnostic | 409 `PASSIVE_CANDIDATE_NOT_CURRENT`; the message lists the diagnostic codes only |
  | `scopeMode != SELECTED` or normalized `requestedMarketIds != [candidate.marketId]` (D3) | 422 `PASSIVE_CANDIDATE_MARKET_MISMATCH` |

  On success it builds the provenance and the deterministic section 9.2
  assessment. Evidence policy and capabilities are compared against the passive
  execution provenance and the active plan derived from the command's
  tradeability profile. Freshness and cutoff use the thesis `sourceCutoff`
  against the active 30-second current-evidence bound. Every non-equal dimension
  is recorded as `INCOMPATIBLE_REACQUIRED`.
- `ActiveScanCreationTransaction`:
  - Add a constructor parameter for the validator, marked `@Autowired`.
  - Keep the existing constructor for tests. It delegates to a validator that
    rejects any candidate reference with 503
    `PASSIVE_CANDIDATE_VALIDATION_UNAVAILABLE`, so it fails closed.
  - In `create(...)`, after the in-transaction idempotency re-check and before
    `scopeResolution.resolve(...)`: if the command carries a candidate, call
    `validate(...)` with `clock.instant()`, captured once and reused as
    `createdAt`. Pass the provenance to the `ActiveScan` factory.
  - `replay(...)` performs **no** revalidation.
  - `buildMarkets` is unchanged and keeps `LIVE_CURRENT`.

### 6. Read projections

- `ActiveScanResultProjection`: add a nullable
  `PassiveCandidateProvenance sourcePassiveCandidate` component, populated in
  `ActiveScanReconciliationService` from `scan.passiveCandidate()`.
- `adapter/web/ActiveScanResponse` and `ActiveScanSummary`: add a nullable
  `sourcePassiveCandidate` DTO. It exposes the identity, version, references,
  market/provider/product, timestamps and assessment codes, with no account data
  beyond what the owner-scoped scan already returns.
- Existing JSON fields are unchanged. The new field is `null` for legacy scans.

### 7. Documentation reconciliation

After implementation, check whether any canonical API documentation describes
`POST /api/v1/intelligence/scans`, and update it only if it does. Record the
documentation outcome and the Vault Outcome in `implementation-report.md`. No
Angular or Gateway changes: Gateway already routes `/api/v1/intelligence/**`.

## Files to Modify

- `application/passive/PassiveAwarenessProjectionService.java`: delegate to the
  evaluator. Batch latest-thesis lookup.
- `adapter/web/CreateActiveScanRequestDto.java`: two optional fields.
- `application/scan/CreateActiveScanCommand.java`: two fields, completeness
  rule, `passiveCandidate()`.
- `adapter/web/MarketIntelligenceController.java`: pass fields to the command
  in `createScan`.
- `application/scan/ActiveScanFingerprintFactory.java`: `NON_NULL` candidate
  fields and overload.
- `application/scan/ActiveScanApplicationService.java`: fingerprint includes
  the candidate.
- `application/scan/ActiveScanCreationTransaction.java`: validator wiring and
  provenance hand-off.
- `domain/scan/ActiveScan.java`: optional immutable provenance and overloads.
- `adapter/persistence/JpaActiveScanRepository.java`: payload component.
- `application/scan/ActiveScanResultProjection.java`,
  `application/scan/ActiveScanReconciliationService.java`: projection field.
- `adapter/web/ActiveScanResponse.java`, `adapter/web/ActiveScanSummary.java`:
  response field.
- `docs/architecture/stories/0243-passive-to-active-investigation/story.md`:
  status line only, at completion.

## Files to Create

- `application/passive/PassiveCandidateEvaluator.java`
- `application/scan/PassiveCandidateScanValidator.java`
- `domain/scan/PassiveCandidateReference.java`
- `domain/scan/PassiveCandidateProvenance.java`
- `domain/scan/PassiveEvidenceAssessment.java` (with its nested enums)
- Tests:
  - `application/passive/PassiveCandidateEvaluatorTest`
  - `application/scan/PassiveCandidateScanValidatorTest`
  - `application/scan/PassiveCandidateActiveScanCreationTest`
  - `architecture/PassiveToActiveArchitectureTest`
- `docs/architecture/stories/0243-passive-to-active-investigation/implementation-report.md`

No Flyway migration, no new external dependency, no configuration change.

## Dependencies

- Existing repository ports: `TradingOpportunityRepository.find` and
  `findLatest`, `MarketThesisRepository.findByIdAndVersion` and `findLatest`,
  `StrategyMatchRepository.findByIds`, `ObservationRepository.findByIds`,
  `MarketDataClient.findMarketsByIds`.
- Existing scope resolution, account ownership, challenge, broker capability and
  eligibility services, unchanged.
- The accepted ADR-102 section 9 contract. No ADR change is required.

The order is strict: 1 → 2 → 3 → 4 → 5 → 6 → tests per step → 7. Each step
compiles with existing tests green before the next step starts.

## Test Plan

All tests use deterministic fixtures, `InMemory*Repository` implementations and
mocked Feign clients. No cTrader, Kraken, AI, broker or account-sync contact.

**Evaluator / feed (step 1)**

- The existing `PassiveAwarenessProjectionServiceTest` cases stay green.
- New: superseded thesis is `NON_POSITIVE` with `THESIS_SUPERSEDED`.
- New: WATCH and NO_TRADE theses are not current.
- New: blocking contradiction is not current.
- New: a repeated equivalent detection yields one feed item.
- New: an opportunity that is not the latest version is excluded.

**Request and fingerprint (steps 2–3)**

- **Golden-hash test:** pin the SHA-256 of a fixed legacy request, computed on
  the pre-change code, and assert it is unchanged.
- The same request with a candidate gives a different hash.
- A different version gives a different hash.
- Exactly one field present returns 400 `PASSIVE_CANDIDATE_REFERENCE_INCOMPLETE`.
- Existing `ActiveScanFingerprintFactoryTest` and
  `ActiveScanApplicationServiceTest` stay green.

**Validator (step 5)**

- Valid candidate returns provenance with all references.
- Not found → 404.
- Active-origin or account-bound opportunity → 422.
- Stale version → 409 `SUPERSEDED`.
- Superseded thesis → 409 `SUPERSEDED`.
- Expired opportunity, inactive observation, WATCH thesis, contradiction,
  missing StrategyMatch, provider mismatch → 409 `NOT_CURRENT` with codes.
- Market Data down → 503.
- `ALL_ELIGIBLE` scope, a different market, or extra markets → 422
  `MARKET_MISMATCH`.
- Evidence assessment rows match the matrix, for each tradeability profile.

**Creation integration (`PassiveCandidateActiveScanCreationTest`)**

- A valid candidate creates a scan whose persisted provenance round-trips. The
  execution is `AnalysisExecution(ACTIVE)` with `LIVE_CURRENT`.
- An invalid candidate persists no scan and registers no execution.
- Retry with the same key replays without revalidation, even after the
  candidate expired.
- Same key with a different version returns 409 `IDEMPOTENCY_CONFLICT`.
- Concurrent same-key creation produces one scan (extend the
  `ActiveScanConcurrencyIntegrationTest` pattern).
- Account excluded by the existing scope resolution gives `COMPLETED_NO_WORK`
  with provenance retained.
- Foreign account returns 404.
- Legacy request without fields behaves byte-identically.
- Passive opportunity, thesis, observation and StrategyMatch records are
  unchanged after creation.

**Persistence**

- `ActiveScanPersistenceTest` gains: a legacy payload without `passiveCandidate`
  deserializes; provenance survives `markDispatchRequested` and `reconcileTo`.

**Projection and controller**

- `ActiveScanReconciliationServiceTest`: a `COMPLETED_NO_OPPORTUNITY` scan
  exposes `sourcePassiveCandidate`.
- `ActiveScanControllerTest`: new fields accepted; error codes map to
  `{code, error}` with the expected statuses.
- `MiSecurityIntegrationTest`: unchanged and green.

**Safety (`PassiveToActiveArchitectureTest`, ArchUnit)**

- No class in `application.passive` depends on `ActiveScanApplicationService`
  or `ActiveScanCreationTransaction`.
- `PassiveCandidateScanValidator` does not depend on `tradeplan`, risk, broker,
  execution or AI packages.
- Mock verifications in the creation test show no `TradePlanningEngine` or
  account-synchronization interaction.

**Validation commands**

```bash
cd market-intelligence
../trading-core/mvnw -Dtest='PassiveAwareness*Test,PassiveCandidate*Test,ActiveScan*Test,PassiveToActiveArchitectureTest,TradePlanningEngineTest,MiSecurityIntegrationTest' test
../trading-core/mvnw verify
cd .. && ./scripts/quality-verify.sh
git diff --check
```

SonarQube is applied according to the repository's `quality-validation` rules
for `market-intelligence`. A skipped SonarQube run must be reported as skipped,
not passed.

## Risks

- **Legacy fingerprint drift:** mitigated by `NON_NULL` and the golden-hash
  test, which is written first.
- **Feed behavior change from the supersession fix (D2):** intended. It is
  covered by new feed tests and called out in the Implementation Report.
- **N+1 in latest-thesis lookup:** bounded by the 1,000-item source cap.
  Fallback: a narrow batch port method.
- **Candidate-version race:** not atomically closable (append-only opportunities
  with no lock). Provenance records `validatedAt` and the exact version, and the
  plan makes no "latest at commit" claim.
- **Constructor overloads in Spring:** mark the production constructor
  `@Autowired` explicitly to avoid ambiguous injection.
- **Scope creep:** no Angular, MCP, Gateway, migration, `ArtifactResolver`
  wiring or fixes for the pre-existing R6 items.

## Validation Checklist

- [ ] Only `market-intelligence` and the Story 0243 documentation changed.
- [ ] No Flyway migration, no new endpoint or controller, no new dependency.
- [ ] Legacy fingerprint golden hash unchanged; legacy callers green.
- [ ] Candidate fields: none, both, or 400.
- [ ] Every section 6 check returns its explicit code. No silent fallback.
- [ ] Thesis supersession is rejected both in the feed and at scan creation.
- [ ] Market/provider/product identity is verified, and fails closed when unavailable.
- [ ] Existing account, challenge, capability and eligibility logic unchanged.
- [ ] Active executions remain `LIVE_CURRENT`. Passive evidence is referenced only, with a persisted assessment.
- [ ] Provenance is immutable, persisted in the payload, and projected in the response and summary.
- [ ] Replay does not revalidate. A different candidate under the same key returns 409.
- [ ] `COMPLETED_NO_OPPORTUNITY` retains provenance and is not an error.
- [ ] Passive source records are unmodified.
- [ ] No automatic scan, TradePlan, risk, broker, account-sync or AI interaction.
- [ ] Targeted tests, module `verify`, `quality-verify.sh` and `git diff --check` pass, or any failure is reported.
- [ ] Implementation Report records the documentation and Vault outcomes.

## Recommendation

Ready for implementation.

Every approved decision (D1–D5) maps to a concrete change with a test, and no
architecture conflict remains. This recommendation is technical only and does
not authorize implementation.

## Approval Required

Implementation Plan completed.

Human approval is required before Implementation.

Awaiting explicit human approval.

## Human Decision Record

On 2026-11-14 the human engineer approved this Implementation Plan in the
session with the instruction "Approve the plan". Implementation started from
base commit `7aa7a2a80cd7e60423222a2bc3a9079993a4e725` on branch
`story/0243-passive-to-active-investigation`.
