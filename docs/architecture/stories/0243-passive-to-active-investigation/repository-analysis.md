# Repository Analysis - Story 0243

Connect Passive Candidates to Active Investigation.

This analysis describes the current repository and recommends the smallest
additive integration. It does not authorize implementation, migrations,
runtime scans or any change to trading authority.

All paths below are relative to
`market-intelligence/src/main/java/com/hope/trading/market_intelligence/`
unless stated otherwise.

## 1. Repository Baseline

| Item | Value |
|------|-------|
| Analysis date | 2026-11-14 |
| Base branch / HEAD | `main` @ `7aa7a2a80cd7e60423222a2bc3a9079993a4e725` (merge of PR #294), equal to `origin/main` |
| Analysis branch | `story/0243-passive-to-active-investigation` (documentation only) |
| Working tree at start | clean except untracked root `CLAUDE.md` (unrelated, preserved) |
| Latest Flyway migration | `V31__allow_passive_skipped_without_execution.sql` |
| Governance read | `AGENTS.md`, `docs/architecture/stories/README.md`, Epic `0238`, Stories `0239`-`0244` |
| ADRs read | `ADR-033`, `ADR-102` (ACCEPTED 2026-11-13), `ADR-087` (headings only; it governs broker snapshot reuse, not passive evidence reuse) |
| DevLog | `devlog_story_agent` was consulted (task `46a18908-d439-4711-9cd3-16e550fb4d9a`, status `COMPLETED`). Result was `truncated=true`, confidence `LOW`, freshness `UNKNOWN`, and contained only Story metadata. `DEVLOG_CONTEXT_ERROR`: the analysis continued from repository evidence only. |
| Obsidian vault | Not consulted. The Story is repository-specific, and no transverse note was needed. |

Targeted tests executed locally at HEAD (`trading-core/mvnw -o`, module
`market-intelligence`) on 2026-11-14. Result: 60 tests, 0 failures, 0 errors.

| Test class | Tests |
|------------|-------|
| `PassiveAwarenessProjectionServiceTest` | 4 |
| `PassiveAwarenessControllerTest` | 1 |
| `TradePlanningEngineTest` | 13 |
| `ActiveScanApplicationServiceTest` | 12 |
| `ActiveScanFingerprintFactoryTest` | 4 |
| `MiSecurityIntegrationTest` | 26 |

## 2. Story 0242 Delivery Verification

### 2.1 Delivered implementation (FACT)

Commit `41b9a741` "feat(story-0242): expose passive awareness lifecycle" was
merged by PR #294 on 2026-11-14T02:13:49Z. The PR head branch was named
`story/0241-bounded-passive-dispatch`, which is misleading for traceability.

| Concern | Evidence | Verdict |
|---------|----------|---------|
| Read endpoint | `adapter/web/PassiveAwarenessController.java`: `GET /api/v1/passive-awareness` returns `ResponseEntity<PassiveAwarenessPageResponse>` | Delivered |
| Gateway | `gateway/.../GatewayRouteConfig.java` route `passive-awareness` -> `market-intelligence` | Delivered |
| Authentication | `security/MiSecurityConfiguration.java` requires `authenticated()` on `/api/v1/passive-awareness/**`. `MiAgentAccessPolicyFilter` allows agent `GET` on the exact path. Tested by `MiSecurityIntegrationTest` (401 without JWT, 200 with JWT). | Delivered |
| Candidate validity | `PassiveAwarenessProjectionService.project(...)` requires `PASSIVE_SCAN` origin, `OpportunityStatus.ACTIVE`, opportunity/observation/thesis validity windows, a resolvable StrategyMatch, consistent market/direction/observation/execution, a resolvable thesis at an exact version, `MarketThesisState.CANDIDATE`, no blocking contradiction, required evidence `SUPPORTING`+`AVAILABLE`, no future timestamps, passive provenance (`sourceScanId`, `sourceScanMarketId`, `analysisExecutionId`), and Market Data identity. `current = diagnostics.isEmpty()`. | Delivered |
| Latest version | `SpringDataTradingOpportunityRepository.findLatestPassive` selects `max(version)` per `opportunityId` | Delivered (see R-0242-2) |
| Provider/product identity | Resolved via the new `MarketDataClient.findMarketsByIds` -> `market-data` `GET /api/v1/markets/by-ids`. Unavailability is fail-closed: `MARKET_IDENTITY_UNAVAILABLE`, not current. | Delivered |
| Candidate identity/version exposed | `PassiveAwarenessResponse.opportunityId` + `opportunityVersion`, plus `strategyMatchId`, `observationIds`, `thesisId`/`thesisVersion`, `sourceScanId`, `sourceScanMarketId`, `analysisExecutionId`, `sourceCutoff`, `validFrom`/`validUntil` | Delivered |
| Account independence | The response contains no account, risk, broker or execution fields | Delivered |
| TradePlan rejection | `TradePlanningEngine` compatibility check now rejects `PASSIVE_SCAN` without `accountId`. Test: `TradePlanningEngineTest.accountlessPassiveOpportunityCannotEnterTradePlanGeneration`. | Delivered |
| No automatic ActiveScan | The projection is read-only and has no `ActiveScanApplicationService` dependency | Delivered |

### 2.2 Gaps found against Story 0242 acceptance criteria

These are verification findings. Story 0242 application code was not modified.

- **R-0242-1 - Thesis supersession is not checked (CONFIRMED).** The projection
  loads the thesis at the exact referenced version. It never checks whether a
  newer thesis exists in the same `lineageId`/`supersedes` chain, or via
  `MarketThesisRepository.findLatest(marketId, instrument, direction)`. Observation
  supersession is covered indirectly by `ObservationStatus.ACTIVE`. Opportunity
  supersession is covered by the latest-version query. As a result, a candidate
  whose thesis was superseded but is still inside its validity window is shown as
  current. This weakens the AC "Expired or superseded items are not presented as
  current". It is directly relevant to Story 0243 validation (section 6).
- **R-0242-2 - Origin filter by payload substring.** `findLatestPassive` uses
  `payload like '%PASSIVE_SCAN%'` plus a correlated `max(version)` subquery. The
  service re-filters `origin() == PASSIVE_SCAN`, so correctness is preserved.
  However, the `LIMIT 1001` is applied before the exact origin filter, and the
  query is a full-text scan with no index. This is an operational and data risk,
  not a correctness defect.
- **R-0242-3 - Presentation states.** The Story scope requires distinguishing
  observation, candidate and opportunity presentation states. Only
  `PASSIVE_CANDIDATE` and `NON_POSITIVE` exist, and observation-only facts are not
  projected as items. AC "Observation facts remain distinguishable" is met only in
  the sense that observations are never presented as candidates.
- **R-0242-4 - Test coverage below the Story validation list.** There are 4
  projection tests (qualifying, stale/missing, expired evidence, truncation) and 1
  controller test. No test covers: WATCH/NO_TRADE thesis, blocking contradiction,
  superseded opportunity/thesis, repeated equivalent detections (no flood),
  "non-qualifying market creates none" fixture, or provider/product filter.
- **R-0242-5 - Invalid pagination returns 409.** `PassiveAwarenessQuery` throws
  `IllegalArgumentException`, which `MarketIntelligenceExceptionHandler` maps to
  HTTP 409. The repository convention for invalid input elsewhere is 400 (for
  example, `INVALID_LIMIT` in `findRecentScans`).

### 2.3 Governance state

| Gate | Evidence | State |
|------|----------|-------|
| Repository Analysis / Implementation Plan | Present in Story directory | Present |
| Implementation Report | Absent from `0242-passive-awareness-lifecycle/` | **Missing** |
| Independent code review | PR #294 has 0 reviews and 0 comments. No `code-review.md`. SonarQube job `SKIPPED`. Other CI jobs `SUCCESS`. | **Missing** |
| Story status | Previously "Proposed - implementation not authorized", which contradicts the merged code | **Reconciled** (section 20) |

CI success and the merge are not treated as independent review. The remaining
Story 0242 gate is an Implementation Report plus an independent code review with
human approval. R-0242-1 and R-0242-4 should be evaluated in that review.

## 3. Story 0243 Objective and Current State

- `story.md` exists with status "Proposed - implementation not authorized".
  This analysis is the first workflow artifact.
- Objective: a trader explicitly selects a passive candidate and an authorized
  account, then launches the **existing** ActiveScan. The scan preserves immutable
  candidate identity/version and provenance.
- ADR-102 section 9 explicitly sanctions adding optional source passive-candidate
  identity and version to the existing request, command, fingerprint and immutable
  scan provenance. No architectural conflict exists with ADR-033 or ADR-102.

## 4. Existing ActiveScan Architecture

| Component | Path | Relevant contract |
|-----------|------|-------------------|
| REST create | `adapter/web/MarketIntelligenceController.createScan` (`POST /api/v1/intelligence/scans`) | Required header `Idempotency-Key`, `@Valid CreateActiveScanRequestDto`. Calls `authorizeScanAgent(...)`, then `scans.create(...)`, then `findOwnedProjection`. Returns `202 Accepted` + `Location`. |
| Request DTO | `adapter/web/CreateActiveScanRequestDto` | `@NotNull accountId`, `brokerAccountId`, `objective` (max 500), `requestedMarketIds`, `scopeMode`, `tradeabilityEvidenceProfile` |
| Command | `application/scan/CreateActiveScanCommand` | Same fields plus `actorId`, `idempotencyKey`. Infers `SELECTED` when market IDs are present. Defaults profile to `UNSUPPORTED`. |
| Idempotency | `ActiveScanApplicationService.create` | Looks up by `(actorId, idempotencyKey)`. A fingerprint mismatch returns 409 `IDEMPOTENCY_CONFLICT`. A match calls `creation.replay`. A `DataIntegrityViolationException` race re-reads the winner. Uniqueness is backed by `uq_active_scan_actor_key` (V3). |
| Fingerprint | `ActiveScanFingerprintFactory.fingerprint(actorId, accountId, objective, requestedMarketIds, scopeMode, profile)` | SHA-256 over Jackson-serialized private record `FingerprintPayload` |
| Creation | `ActiveScanCreationTransaction.create` (`@Transactional`) | Re-checks idempotency, calls `scopeResolution.resolve(...)`, builds `ActiveScanScopeSnapshot`, then `readyToDispatch` / `completedNoWork`. `buildMarkets` registers `AnalysisExecution(ACTIVE)` with hard-coded `EvidenceAcquisitionPolicy.LIVE_CURRENT` and cutoff `assessmentCutoff`. Dispatch is triggered after commit. |
| Scope / eligibility | `application/scope/ActiveScanScopeResolutionService.resolve` | `requireOwnedAccount` (trading-core `findOwnedAccount`, or `findAgentTradingAccount` for agents), challenge readiness, challenge provider compatibility, broker market capabilities, `MarketEligibilityPolicy`, cTrader current-evidence bootstrap (`CONFIRM_OR_ACQUIRE`, max age 30 s), `StrictObservedMarketEvidenceGate` |
| Dispatch / reconciliation | `ActiveScanDispatchCoordinator`, `ActiveScanDispatchClaimService`, `ActiveScanReconciliationService` | Unchanged by this Story |
| Domain | `domain/scan/ActiveScan` (immutable; `restore`, `markDispatchRequested`, `reconcileTo`), `ActiveScanMarket`, `ActiveScanScopeSnapshot`, `ActiveScanStatus` | No provenance field today |
| Persistence | `adapter/persistence/JpaActiveScanRepository` | Table `active_scans` (V3). `payload TEXT` = JSON `ActiveScanPayload(scopeSnapshot)`. |
| Read projections | `application/scan/ActiveScanResultProjection`, `adapter/web/ActiveScanResponse`, `adapter/web/ActiveScanSummary` | No provenance field today |
| Outcomes | `ActiveScanMarketOutcome` | `EXCLUDED`, `NOT_EVALUABLE`, `RUNNING`, `COMPLETED_NO_OPPORTUNITY`, `OPPORTUNITY_FOUND`, `FAILED`, `CANCELLED`, `EXPIRED` |
| Errors | `ActiveScanException(code, message, status)` and `ActiveScanScopeResolutionException`, both rendered by `MarketIntelligenceExceptionHandler` as `{code, error}` | Reuse for new errors |
| Frontend client | `trading-os-web/src/app/core/services/active-scan.service.ts` | Already posts with an `Idempotency-Key` header |

Active opportunity identity is derived by
`StrategyMatchOpportunityFactory.deriveOpportunityLineageId(matchId, accountId)`,
and deduplication compares observations (`OpportunityDeduplicationPolicy`). Active
results create new StrategyMatches and observations, so **they cannot fuse into
or version the passive opportunity**. The passive source therefore remains
immutable without new code.

## 5. Passive Candidate Reference Model

Per ADR-102 section 8, V1 has no `MarketCandidate` aggregate. **A passive
candidate is a `TradingOpportunity` version** with `origin=PASSIVE_SCAN`. Its
stable identity is `(opportunityId, opportunityVersion)`, which the 0242 response
already exposes. **No new identifier is needed.**

| Required element | 0242 field | Available |
|------------------|------------|-----------|
| Candidate ID / version | `opportunityId`, `opportunityVersion` | Yes |
| Canonical market | `marketId` | Yes |
| Provider / product | `provider`, `productType` (from Market Data) | Yes; null when Market Data is unavailable (then not current) |
| Evidence cutoff | `sourceCutoff` (thesis) | Yes |
| Validity | `validFrom`, `validUntil` (opportunity) | Yes. Thesis validity is not exposed (gap; minor). |
| Provenance | `strategyMatchId`, `observationIds`, `thesisId`/`thesisVersion`, `sourceScanId`, `sourceScanMarketId`, `analysisExecutionId` | Yes |

Recommended request fields, named to avoid implying a new aggregate:
`sourcePassiveOpportunityId: UUID` and `sourcePassiveOpportunityVersion: Long`.
The client sends only these two values. Every other provenance value is resolved
on the server and never trusted from the client.

## 6. Candidate Validation (server-side)

Validation runs on **creation only**, never on idempotent replay (see section 11).

| # | Check | Source | Error (`ActiveScanException`) |
|---|-------|--------|-------------------------------|
| 0 | Exactly zero or two reference fields present | DTO/command | 400 `PASSIVE_CANDIDATE_REFERENCE_INCOMPLETE` |
| 1 | Opportunity version exists | `TradingOpportunityRepository.find(id, version)` | 404 `PASSIVE_CANDIDATE_NOT_FOUND` |
| 2 | `origin == PASSIVE_SCAN` and `accountId` empty | opportunity | 422 `PASSIVE_CANDIDATE_INVALID_ORIGIN` |
| 3 | Requested version is the latest | `TradingOpportunityRepository.findLatest(id)` | 409 `PASSIVE_CANDIDATE_SUPERSEDED` |
| 4 | Not expired / within validity / `ACTIVE` lifecycle | 0242 rules | 409 `PASSIVE_CANDIDATE_NOT_CURRENT` (+ diagnostics) |
| 5 | Observation(s) resolvable, `ACTIVE`, in validity | 0242 rules | 409 `PASSIVE_CANDIDATE_NOT_CURRENT` |
| 6 | Thesis resolvable, `CANDIDATE`, no blocking contradiction, required evidence positive and unexpired | 0242 rules | 409 `PASSIVE_CANDIDATE_NOT_CURRENT` |
| 7 | **Thesis not superseded** (new; closes R-0242-1 for this path) | `MarketThesisRepository.findLatest(marketId, instrument, direction)` or lineage | 409 `PASSIVE_CANDIDATE_SUPERSEDED` |
| 8 | StrategyMatch resolvable and consistent | 0242 rules | 409 `PASSIVE_CANDIDATE_NOT_CURRENT` |
| 9 | Market/provider/product identity resolvable and consistent | 0242 rules + `MarketDataClient.findMarketsByIds` | 409 `PASSIVE_CANDIDATE_NOT_CURRENT`; 503 `PASSIVE_CANDIDATE_IDENTITY_UNAVAILABLE` when Market Data is down (fail closed) |
| 10 | Request scope matches the candidate: `scopeMode=SELECTED` and `requestedMarketIds == [candidate.marketId]` | command vs opportunity | 422 `PASSIVE_CANDIDATE_MARKET_MISMATCH` (see D3) |

**Reuse requirement.** Checks 4, 5, 6, 8 and 9 must reuse one rule
implementation. Recommended approach: extract the per-item evaluation inside
`PassiveAwarenessProjectionService.project(...)` into a package-level evaluator.
Both the feed and the 0243 validator call it, so the feed and the "current"
decision cannot diverge. This refactor touches 0242 code; see D2.

There is no silent fallback. A failed candidate never degrades into a plain
ActiveScan for the same or another market.

## 7. Account Authorization and Eligibility

All existing controls remain authoritative and unchanged:

1. JWT authentication (`MiSecurityConfiguration`). `scanActorId` resolves a user
   or agent principal.
2. Agent principals: `authorizeScanAgent` requires `brokerAccountId` and a
   `scans:run` authorization from `ResearchAuthorizationClient`.
3. `ActiveScanScopeResolutionService.requireOwnedAccount`: account ownership via
   trading-core. Unknown or foreign accounts return 404 (no leakage); trading-core
   unavailability returns 503.
4. Challenge readiness and challenge provider compatibility
   (`PROVIDER_MISMATCH`).
5. Broker market capabilities, `MarketEligibilityPolicy` and the current-evidence
   bootstrap. Market availability or closure surfaces as `EXCLUDED` /
   `NOT_EVALUABLE` decisions.

The passive candidate contributes **only** `requestedMarketIds=[marketId]`. It
grants no eligibility, no account and no actor. If the account cannot trade the
market, the existing path produces `COMPLETED_NO_WORK` with recorded exclusion
reasons. This is a truthful negative, not an error. No synthetic users or
accounts are created.

Pre-existing observation (out of scope; reported only): `brokerAccountId` is not
part of the idempotency fingerprint. The same key with a different
`brokerAccountId` replays the first scan.

## 8. Request and Idempotency Compatibility

Recommended additive changes:

- `CreateActiveScanRequestDto`: add two nullable fields. Keep the existing
  convenience constructors.
- `CreateActiveScanCommand`: add two nullable fields. Keep the existing
  constructors, delegating with `null`.
- `ActiveScanFingerprintFactory.FingerprintPayload`: add both fields **with
  `@JsonInclude(NON_NULL)`**. Without it, Jackson would serialize
  `"sourcePassiveOpportunityId":null`, which would change the hash of every
  legacy request. Existing persisted `request_fingerprint` values would then
  produce false `IDEMPOTENCY_CONFLICT` on replay after deployment. A pinned
  golden-hash regression test is mandatory.

| Case | Behavior |
|------|----------|
| Both absent | Legacy path; identical fingerprint and behavior |
| Both present | Candidate validation, then the existing creation path |
| Only one present | 400 `PASSIVE_CANDIDATE_REFERENCE_INCOMPLETE` |
| Candidate market differs from requested market | 422 `PASSIVE_CANDIDATE_MARKET_MISMATCH` |
| Candidate stale/superseded at creation | 409 with explicit code; no scan persisted |
| Same key, different candidate ID or version | Fingerprint differs, so 409 `IDEMPOTENCY_CONFLICT` |
| Same key, same candidate, retried | Replay of the original scan (no revalidation) |
| Candidate version changes between validation and persistence | See section 12 |

## 9. Evidence Compatibility and Reuse

### 9.1 Repository facts

- ActiveScan **always** registers `AnalysisExecution(ACTIVE)` with
  `EvidenceAcquisitionPolicy.LIVE_CURRENT` (`ActiveScanCreationTransaction.buildMarkets`).
- Passive executions are also `LIVE_CURRENT`, with mode `PASSIVE` and **no
  `evaluationCutoff`** (`PassiveScanCreationTransaction`).
- `ActiveAnalysisStrategy.plan` selects a different capability set from
  `PassiveAnalysisStrategy`. Spread and tradeability capabilities are added when
  the tradeability profile requires them.
- Active current-market evidence has a 30-second maximum age
  (`ActiveScanScopeResolutionService.CURRENT_MARKET_EVIDENCE_MAX_AGE`).
- The ADR-034 reuse machinery (`application/artifact/DefaultArtifactResolver`,
  `domain/artifact/FreshnessEvaluator`) exists but **has no production consumer**.
  Nothing in the active pipeline reuses artifacts from another execution today.
- `FeatureSnapshot` records `contextFingerprint` and `evidencePolicy` per
  execution, so a deterministic comparison is possible.

**Conclusion.** No existing domain mechanism lets an ActiveScan execution consume
passive evidence. Building one would mean wiring `ArtifactResolver` into
context assembly for ACTIVE mode. That is a cross-cutting change to the analysis
pipeline, beyond "smallest additive integration".

### 9.2 Recommended V1 semantics (requires decision D1)

**Always reacquire through the existing `LIVE_CURRENT` path. Reuse passive
evidence by explicit reference only, with a deterministic, persisted
compatibility assessment.** Passive evidence then informs traceability and is
never silently substituted for active inputs. This matches ADR-102's
"otherwise reacquire" branch for every case where compatibility cannot be proven
by an existing mechanism.

| Dimension | Compatible when | If not |
|-----------|-----------------|--------|
| Canonical market | candidate `marketId` == active market | Reject request (422) |
| Provider | Market Data `provider` equal | Reject request (409) |
| Product type | Market Data `productType` equal | Reject request (409) |
| Evidence policy | passive `LIVE_CURRENT` == active `LIVE_CURRENT` | Record `INCOMPATIBLE_POLICY`; reacquire |
| Capability set | passive capabilities superset of active plan | Normally false when a tradeability profile is set. Record `INCOMPATIBLE_CAPABILITIES`; reacquire. |
| Cutoff | Passive has no `evaluationCutoff`; thesis `sourceCutoff` is used | Record the cutoff delta; reacquire |
| Freshness | Thesis/evidence age within the active 30 s current-evidence bound | Practically always stale. Record `STALE`; reacquire. |
| Contradiction / supersession | None (section 6) | Reject request (409) |
| Missing evidence | Required evidence present | Reject request (409) |

Identity, supersession and missing-evidence failures reject the request.
Freshness, policy and capability gaps never reject. They are recorded, and
active analysis reacquires, which is the existing behavior.

## 10. Persistence and Provenance

Recommended: an immutable value object `PassiveCandidateProvenance`, held as an
optional field on `domain/scan/ActiveScan`, set only by the creation factories,
with no mutator. Fields:

- `opportunityId`, `opportunityVersion`
- `strategyMatchId`, `observationIds`, `thesisId`, `thesisVersion`
- `passiveScanRunId` (`sourceScanId`), `passiveScanMarketId`, `passiveAnalysisExecutionId`
- `marketId`, `provider`, `productType`
- `opportunityEvaluatedAt`, `thesisSourceCutoff`, `validUntil`
- `validatedAt`
- `evidenceAssessment` (the per-dimension result from section 9.2; codes only, no payloads)

**Storage (decision D4).** Store it in the existing `active_scans.payload` JSON by
extending the private `ActiveScanPayload` record with a nullable field. This needs
**no Flyway migration** and duplicates no evidence payloads. Legacy rows
deserialize with `null`. A test must prove this against the module's
`ObjectMapper` configuration. The alternative is nullable indexed columns (new
`V32`), which is only justified if querying scans by candidate is required.
Neither Story 0243 nor 0244 requires it.

The passive opportunity, thesis, observation and StrategyMatch records are never
written to by this Story.

## 11. Concurrency and Transaction Boundaries

Validation should run inside `ActiveScanCreationTransaction.create`, after the
in-transaction idempotency re-check and before `scopeResolution.resolve`. A
failure then aborts before any execution registration and before the after-commit
dispatch.

| Race | Behavior |
|------|----------|
| Candidate expires during processing | Validated against one `clock.instant()` captured at creation start. A later expiry does not invalidate the created scan, which is an investigation of a candidate that was current at `validatedAt`. Fail-closed before persistence only. |
| New opportunity version appended between validation and persist | Opportunities are append-only with no row lock to take. Perfect atomicity across this boundary is **not available**. Mitigation: provenance records the exact validated version and `validatedAt`, and the projection exposes it. No claim of "latest at commit time" is made. |
| Two requests select the same candidate (different keys) | Both scans are legitimate (explicit human actions). No uniqueness on the candidate. |
| Same key retried | Existing replay; no revalidation; the original scan is returned even if the candidate later expired |
| Different candidate/version, same key | 409 `IDEMPOTENCY_CONFLICT` via fingerprint, including the `DataIntegrityViolationException` race branch |
| Account eligibility changes during creation | Existing behavior: resolution is a point-in-time snapshot (`ActiveScanScopeSnapshot.resolvedAt`) |
| Market closes during investigation | Existing outcomes (`FAILED` / `NOT_EVALUABLE` / `COMPLETED_NO_OPPORTUNITY`) |
| Evidence stale before execution | Active always reacquires `LIVE_CURRENT`; there is no passive evidence to go stale |

Pre-existing observation: `scopeResolution.resolve` performs remote trading-core
and Market Data calls inside the creation DB transaction, including a cTrader
`CONFIRM_OR_ACQUIRE` snapshot. Story 0243 should not add further remote calls
inside the transaction beyond the single `findMarketsByIds` identity lookup.

## 12. Outcome Semantics

| Situation | Result |
|-----------|--------|
| Valid candidate, opportunity found | 202. Market outcome `OPPORTUNITY_FOUND`. The active opportunity is account-bound (`ACTIVE_SCAN` origin). |
| Valid candidate, no active opportunity | 202. `COMPLETED_NO_OPPORTUNITY`. A successful investigation, not a candidate failure. |
| Stale / expired / superseded candidate | 409 with explicit code; no scan created |
| Missing required evidence | 409 `PASSIVE_CANDIDATE_NOT_CURRENT` with diagnostics |
| Account not owned / not found | 404 (existing) |
| Account ineligible for market / challenge provider mismatch | Existing: `COMPLETED_NO_WORK` with `EXCLUDED` reasons, or `PROVIDER_MISMATCH` |
| Provider unavailable (trading-core / Market Data) | 503 (existing `unavailable`), or 503 `PASSIVE_CANDIDATE_IDENTITY_UNAVAILABLE` |
| Market closed | Existing `EXCLUDED` / `NOT_EVALUABLE` |
| Active analysis failure | Existing `FAILED` |

A negative active result never mutates the passive opportunity. ADR-102 assigns
the passive lifecycle to existing expiration policies. Whether the awareness feed
should *annotate* a candidate with "investigated - no opportunity" is decision D5.
The recommendation is to defer this to 0244 or later as a read-side join. The
passive record must not be mutated.

## 13. REST Integration

- **Reuse `POST /api/v1/intelligence/scans`.** No new controller is justified.
- Errors use the existing `{code, error}` body through `ActiveScanException`.
- Read projections: add a nullable `sourcePassiveCandidate` object to
  `ActiveScanResultProjection`, `ActiveScanResponse` and `ActiveScanSummary`,
  mapped from the persisted provenance. Owner scoping is unchanged
  (`findOwnedProjection(actorId, scanId)`).
- The Gateway already routes `/api/v1/intelligence/**`, so no change is needed.
- The MCP adapter `start_market_scan` is unchanged. Exposing passive references to
  agents is not in scope.

## 14. Story 0244 Frontend Handoff (minimum contract)

1. **Display**: `GET /api/v1/passive-awareness?currentOnly=true` returns items with
   `opportunityId`, `opportunityVersion`, `marketId`, `provider`, `productType`,
   `diagnostics`, `sourceCutoff`, `validUntil`, `presentationState`.
2. **Select**: the client keeps `(opportunityId, opportunityVersion, marketId)` only.
3. **Account**: existing account selection, using the same source as the current
   scan panel.
4. **Submit**: `POST /api/v1/intelligence/scans` with header `Idempotency-Key`
   (generated once per user click and reused on retry) and body
   `{accountId, objective, scopeMode:"SELECTED", requestedMarketIds:[marketId], sourcePassiveOpportunityId, sourcePassiveOpportunityVersion}`.
5. **State**: `202` + `Location`, then poll the existing `GET /scans/{scanId}`.
6. **Errors**: map `PASSIVE_CANDIDATE_*` codes to "candidate no longer current -
   refresh feed"; `IDEMPOTENCY_CONFLICT` to a new key; existing scope errors are
   unchanged.
7. **Navigate**: the existing ActiveScan result view, showing `sourcePassiveCandidate`.

No eligibility logic belongs in Angular. The button is enabled only by
`presentationState == PASSIVE_CANDIDATE` plus a selected account. The server
decides everything else.

## 15. Test Matrix

All tests use deterministic fixtures and test doubles (`InMemory*Repository`,
mocked `TradingCoreAccountClient` / `MarketDataClient`). No cTrader, Kraken, AI or
broker contact.

**Candidate validation** (`ActiveScanCreationTransaction` / validator unit tests):
valid; missing; invalid version; superseded opportunity version; expired; not
`ACTIVE`; non-passive origin; account-bound passive; missing observation; missing
thesis; WATCH/NO_TRADE thesis; superseded thesis; blocking contradiction;
StrategyMatch missing/mismatched; provider/product mismatch; Market Data
unavailable (fail closed); scope/market mismatch; only one field present.

**Account** (existing tests stay green, plus new tests with a candidate present):
authorized; foreign account 404; trading-core unavailable 503; challenge not
ready; challenge provider mismatch; broker capability missing (`EXCLUDED` /
`COMPLETED_NO_WORK`); agent without `brokerAccountId` 403.

**Idempotency** (`ActiveScanFingerprintFactoryTest`, `ActiveScanApplicationServiceTest`,
`ActiveScanConcurrencyIntegrationTest`): **golden legacy hash unchanged**;
identical retry replays; different candidate ID returns 409; different version
returns 409; concurrent creation with same key yields one scan; legacy caller
without fields passes.

**Evidence compatibility**: each section 9.2 row produces the recorded assessment
code. Active execution remains `LIVE_CURRENT` in all cases.

**Persistence**: provenance round-trips through `JpaActiveScanRepository`; a legacy
payload without the field deserializes; provenance cannot be changed by
`markDispatchRequested` / `reconcileTo`.

**Outcomes** (`ActiveScanReconciliationServiceTest`): opportunity found; no
opportunity (not an error, provenance retained); excluded market; analysis failure.

**Safety**: the passive scheduler and dispatch never call
`ActiveScanApplicationService` (dependency/ArchUnit-style or wiring test); no
`TradePlanningEngine`, risk, broker or account-sync invocation during candidate
scan creation (mock verifications); no AI capability added; passive source
records unchanged after scan creation.

**Controller/security** (`MiSecurityIntegrationTest`, controller tests): 401
without JWT; new fields accepted; error code mapping.

## 16. Acceptance Criteria Mapping

| AC | Mechanism |
|----|-----------|
| Explicitly selected authorized account | Existing `@NotNull accountId` + `requireOwnedAccount` (section 7) |
| Existing eligibility authoritative | Candidate only sets `requestedMarketIds`; scope resolution unchanged |
| Passive evidence reused only when compatible and fresh | Section 9.2 (reference-only reuse with recorded assessment; D1) |
| Incompatible evidence reacquired or reported | `LIVE_CURRENT` reacquisition; identity/missing evidence produce explicit 409 |
| Provenance preserved in request/result | Fingerprint fields + `PassiveCandidateProvenance` + projections |
| Candidate identity/version immutable and projected | Domain field without mutator; projection field (section 10) |
| Stale/expired cannot become current | Section 6 validation; active opportunities are new account-bound records |
| Truthful no-opportunity completion | `COMPLETED_NO_OPPORTUNITY` (section 12) |
| No automatic scan from passive scheduling | Safety test (section 15) |
| Existing idempotency/authorization tests green | Golden hash + existing suites |
| No TradePlan, Risk or broker operation | Mock-verification safety tests |

## 17. Risks and Unresolved Decisions

**Risks**

- R1 - Fingerprint hash drift for legacy requests (high impact; mitigated by
  `NON_NULL` + golden test).
- R2 - Rule divergence between the feed and validation if the logic is
  duplicated (mitigated by a shared evaluator; D2).
- R3 - Candidate-version race is not atomically closable (documented fail-closed
  semantics, section 11).
- R4 - Product expectation: "reuse" in V1 means traceable reference plus
  reacquisition, not fewer provider calls (D1).
- R5 - Inherited 0242 gaps (R-0242-1 to R-0242-5), with no independent 0242 review.
- R6 - Pre-existing: `brokerAccountId` absent from fingerprint; remote calls
  inside the creation transaction. Out of scope; reported.

**Decisions requiring human approval**

- **D1** - Evidence reuse semantics for V1: reference-only reuse with recorded
  compatibility and always-reacquire (recommended), or wiring `ArtifactResolver`
  into ACTIVE context assembly (a larger cross-cutting change; would warrant its
  own Story).
- **D2** - Thesis-supersession fix (R-0242-1) and shared evaluator: (a)
  extract the evaluator from `PassiveAwarenessProjectionService` and add the
  supersession check, which fixes the feed too (recommended; touches 0242 code
  under 0243 scope), or (b) 0243-local validator only, with a separate 0242
  corrective change.
- **D3** - When candidate fields are present, should the client still be required
  to send `scopeMode=SELECTED` + `requestedMarketIds=[marketId]` (recommended;
  explicit and verifiable), or should the server derive scope from the candidate?
- **D4** - Provenance storage: `active_scans.payload` JSON without a migration
  (recommended), or nullable indexed columns (Flyway `V32`).
- **D5** - Whether the feed should later annotate candidates with linked active
  scan outcomes (recommended: defer; never mutate the passive record).

## 18. Implementation Sequencing (indicative, not a plan)

1. Shared candidate evaluator + supersession check (per D2) with tests.
2. DTO/command fields + `PASSIVE_CANDIDATE_REFERENCE_INCOMPLETE`.
3. Fingerprint extension with golden-hash regression.
4. `PassiveCandidateProvenance` domain value + payload persistence (per D4).
5. Validation inside `ActiveScanCreationTransaction.create` + error codes.
6. Evidence compatibility assessment recorded in provenance (per D1).
7. Projection/response/summary fields.
8. Safety, concurrency and controller tests; full `market-intelligence verify`.
9. Documentation reconciliation and Implementation Report.

## 19. Explicit Non-Goals

No new controller or active analysis service; no automatic escalation; no
`MarketCandidate` aggregate; no TradePlan, risk, execution, broker write or
account synchronization; no AI invocation; no historical backfill; no change to
passive scheduling or its disabled default; no Angular changes (Story 0244); no
correction of the pre-existing R6 items; no mutation of passive source records.

## 20. Documentation Changes in This Analysis

- Created: `docs/architecture/stories/0243-passive-to-active-investigation/repository-analysis.md`.
- Modified: `docs/architecture/stories/0242-passive-awareness-lifecycle/story.md`,
  status line only. It now records the merged implementation (commit `41b9a741`,
  PR #294) and the missing Implementation Report / code review gates. This
  follows the status convention already used by Story 0240.

No Implementation Plan was produced. Per the repository workflow and the
engineering-story governance, Implementation Planning requires explicit human
approval of this Repository Analysis first.

## Approval Required

Repository Analysis completed.

Recommendation: **READY_WITH_OPEN_DECISIONS** (D1-D5). This recommendation is
technical only and is not approval.

Human approval of this Repository Analysis is required before Implementation
Planning.

## Human Decision Record

On 2026-11-14 the human engineer approved this Repository Analysis in the
session with the instruction "Approve with defaults". This selects the
recommended option for every open decision:

| Decision | Selected option |
|----------|-----------------|
| D1 | Reference-only reuse of passive evidence, with a persisted compatibility assessment. Active analysis always reacquires through `LIVE_CURRENT`. |
| D2 | Extract a shared candidate evaluator from `PassiveAwarenessProjectionService` and add the thesis-supersession check. This also fixes the 0242 feed. |
| D3 | When candidate fields are present, the client must send `scopeMode=SELECTED` and `requestedMarketIds=[candidate.marketId]`. The server verifies the match. |
| D4 | Store provenance in the `active_scans.payload` JSON. No Flyway migration. |
| D5 | Defer feed annotation with active scan outcomes. Never mutate the passive record. |

This record documents a human workflow event. The artifact itself grants no
approval for later gates.
