# Story 0067 - Current-Main Runtime Reconciliation

**Investigation date:** 2026-10-03  
**Repository:** `trading-os`  
**Baseline:** `main` at `80c3b40` (`origin/main`)  
**Mode:** Read-only investigation. No production code, tests, existing
documentation, runtime configuration, credentials, strategy governance,
thresholds, Risk rules, PAPER execution behavior, or broker integration was
changed.

## 1. Executive Conclusion

**Overall classification: RUNTIME_REVALIDATION_REQUIRED.** The evidence is a
mixture of resolved code and unproven runtime behavior:

- The Account projection blocker is **RESOLVED in current code**. The singular
  account endpoint now uses the same profile-enriching service mapping as the
  account list path. The report's `Unavailable` account fields describe the
  earlier runtime observation, not the current source path.
- Story 0067 contains **no confirmed Trend Context OHLC acquisition fix**. Its
  Trend Context changes add logging and preserve the same Feign endpoint,
  parameters, and acquisition semantics. The persistence changes improve
  artifact serialization/reload, but do not make OHLC available.
- The current source contract has no proven endpoint, parameter, interval, or
  authorization mismatch that establishes a code defect. The observed missing
  role history remains **INCONCLUSIVE** at the Market Intelligence to Market
  Data runtime boundary.
- The official authenticated Active Scan was not proven after the current
  account projection code was present. Existing automated tests cannot prove
  service discovery, service-to-service behavior, Docker configuration, or a
  real scan-to-observation path.

The smallest justified next action is one controlled, authenticated runtime
revalidation on current `main`, limited to account/Decision Context projection
and the Active Scan through `TREND_CONTEXT_HISTORY` and the Trend Context read
endpoint. No code change is justified by repository evidence alone.

DevLog was checked through the Story Agent for `trading-os`, but the request
failed with an execution error and returned no usable context. This report
therefore relies on the repository and Git history.

## 2A. Runtime Revalidation Result (2026-10-03)

The controlled authenticated rerun on current runtime state resolved the prior
unproven runtime path for the selected market. The existing PAPER account was
reused; no account, profile, credential, code, or database mutation was made.

- Market: `TBTC/EUR`, provider `KRAKEN`;
- Market ID: `01b8a011-08d2-417c-811f-ec3e4b97dc31`;
- OHLC requests for `FOUR_HOURS`, `ONE_HOUR`, and `FIFTEEN_MINUTES`: HTTP 200;
- Exactly one official Active Scan: `1d7ef60a-3598-4457-bd59-ad2c69279c55`;
- Scan status: `COMPLETED`, candidate/effective count `1 / 1`;
- Analysis execution: `e421f0c3-f3c3-4e5f-8c90-3fc7265914d8`;
- Result quality: `DEGRADED`, with a separate `Legacy OHLC Trend` opportunity;
- Trend Context read endpoint: HTTP 200;
- Decision Workspace displayed a valid version-1 Trend Context assessment;
- No TradePlan, Risk, execution, position, or broker operation was initiated.

The current runtime classification for the scan-to-read path is therefore
**RUNTIME REVALIDATED for this controlled PAPER scenario**. The result does
not establish the broader negative scenario matrix or resolve unrelated
historical runtime observations.

## 2. Current-Main Baseline

### Repository state

| Item | Evidence | Classification |
|---|---|---|
| Branch | `main`, tracking `origin/main` | CONFIRMED |
| HEAD | `80c3b40` merge of PR #66 | CONFIRMED |
| Story 0067 implementation | `ea1b196` | CONFIRMED |
| Story 0067 formatting follow-up | `8af6a37` | CONFIRMED |
| Worktree | Clean at investigation start | CONFIRMED |
| Story status | `Draft` in `story.md` | CONFIRMED |
| Standalone implementation report | Not present | CONFIRMED |
| Validation report | Present; dated 2026-10-02 | CONFIRMED |

Current `main` contains Stories 0062-0067 and the implementation changes made by
Story 0067. `story.md` remains a validation scope and acceptance artifact; its
acceptance checkboxes are not evidence that the authenticated journey passed.

Relevant architectural constraints remain unchanged:

- ADR-048 is accepted and keeps Trend Context as deterministic evidence, not a
  StrategyMatch, Risk approval, or execution command.
- ADR-014 keeps the decision pipeline human-controlled.
- ADR-028 keeps Risk deterministic and separate from analysis.
- ADR-029 keeps execution after Risk approval and explicit authorization.
- ADR-041 preserves the human financial command boundary.
- ADR-047 preserves manual TradePlan origin without fabricating an opportunity.

## 3. Story 0067 Chronology

| Sequence | Evidence | Result |
|---|---|---|
| Initial state | Parent of `ea1b196` | Singular `AccountController` called `getAccountById`, then `AccountMapper.toDto`; Trend Context contributor acquired through `MarketDataClient` without the Story 0067 diagnostic logging. |
| Prior implementation context | `a973538` / `b2c533f` | Story 0066 supplied the Decision Workspace projection and Trend Context read path. |
| Runtime validation | `validation-report.md`, 2026-10-02 | Authenticated PAPER setup and scan were attempted. The report recorded missing profile references, a degraded Legacy OHLC opportunity, no Trend Context read model, and HTTP 404. |
| Story 0067 production commit | `ea1b196`, 2026-10-02 20:59:59 | Singular account mapping was changed to the enriching service method; persistence serialization was hardened; contributor and assembler logging was added; read-model timing was adjusted; tests were added/updated. |
| Web formatting | `8af6a37`, 2026-10-02 21:04:56 | UX formatting only. |
| Merge to current main | `80c3b40`, 2026-10-02 21:27:50 | Story 0067 changes became current `main`. |
| Post-remediation official runtime proof | No repository artifact | Not proven. |

The validation report and the production fixes are in the same Story 0067
implementation commit. Git does not provide an intra-commit timestamp for the
runtime observations. The report must therefore be treated as evidence of a
failure observed during the validation work, not as proof of the state of the
final committed source. In particular, its account blocker text is inconsistent
with the final source committed by `ea1b196`.

## 4. Account Blocker Reconciliation

### Current path

`trading-core/src/main/java/com/hope/trading/trading_core/controller/AccountController.java:36-43`
now calls:

```text
AccountController.getAccount
 -> AccountService.getAccountDtoById
 -> AccountServiceImpl.getAccountDtoById
 -> AccountServiceImpl.toDto
 -> AccountMapper.toDto
 -> RiskPersistence.assignedProfile
 -> TradePlanningProfileRepository.findAssigned
```

`AccountServiceImpl.toDto` at lines 133-143 sets:

- `riskProfileId` and `riskProfileSemanticVersion`;
- `tradePlanningProfileId` and `tradePlanningProfileVersion`.

The Market Intelligence client at
`market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/tradingcore/TradingCoreAccountClient.java:11-17`
uses the singular `GET /api/v1/accounts/{accountId}` endpoint and its response
record includes all four profile fields. Decision Context acquisition calls
that client through
`ActiveScanScopeResolutionService.requireOwnedAccount`.

### Tests

Current focused tests passed:

```text
./trading-core/mvnw -q -f trading-core/pom.xml \
  -Dtest='AccountControllerTest,AccountServiceImplTest,AccountMapperTest' test
```

The tests include:

- `AccountServiceImplTest.singularAccountDtoIncludesAssignedProfiles`;
- `AccountControllerTest.singularAccountEndpointReturnsEnrichedProfiles`.

These prove service/controller mapping with mocked profile stores. They do not
prove a deployed Gateway-to-Trading-Core-to-Market-Intelligence request.

### Classification

**RESOLVED_IN_CODE_NOT_RUNTIME_REVALIDATED.** The original missing-reference
path is no longer possible through the current singular account service path
when the assigned profile stores return profiles. It remains possible for a
real account to have no assigned profile, which is a legitimate unavailable
state and is distinct from the old projection omission.

The following validation-report statements are stale or require historical
qualification:

- Lines 107-119: the observed `Risk profile: Unavailable` and `Planning
  profile: Unavailable` state is historical runtime evidence, not current-main
  source evidence.
- Lines 386-390: the claim that the current account read projection contains
  null profile references conflicts with `ea1b196`'s final source.
- Lines 402-415: the explanation that the singular endpoint directly returns
  `accountMapper.toDto(account)` describes the parent implementation, not
  current `main`.

## 5. Current Trend Context Runtime Path

The current source path is:

```text
Active Scan
 -> ActiveAnalysisStrategy
 -> IntelligenceContextAssembler
 -> TrendContextRoleHistoryContextContributor
 -> MarketDataClient.findOhlc
 -> TREND_CONTEXT_HISTORY artifact
 -> TrendContextAnalysisCapability
 -> TREND_CONTEXT_ASSESSMENT artifact
 -> TrendContextObservationService
 -> TREND_CONTEXT IntelligenceObservation
 -> TrendContextReadService
 -> GET /api/v1/intelligence/trend-context/{marketId}
 -> Decision Workspace
```

### Transition analysis

| Transition | Current implementation | Required input | Failure/degraded behavior | Evidence status |
|---|---|---|---|---|
| Active Scan selection | `ActiveAnalysisStrategy.java:30-46` | Active mode | Selects `trend-context-analysis`, plus spread, OHLC range, and disabled AI capabilities | CONFIRMED / UNIT_PROVEN |
| Context acquisition | `IntelligenceContextAssembler.java:26-62` | Registered contributors | Runtime exception becomes `ContextSection.unavailable`; the exception is logged | CONFIRMED / UNIT_PROVEN |
| Role history | `TrendContextRoleHistoryContextContributor.java:45-112` | Market ID, configured role intervals, limits | Feign/runtime exception is rethrown to assembler; all empty responses return missing history; required-role mapping may retry with larger limits | CONFIRMED / UNIT_PROVEN |
| OHLC calls | `MarketDataClient.java:22-27` | UUID, enum-name interval, limit | Feign failure propagates; empty list is missing context | CONFIRMED / NOT RUNTIME-PROVEN |
| History artifact | `CapabilityAnalysisCoordinator.java:150-189` | Available `TREND_CONTEXT` section with payload | Only available/stale payload sections are materialized; missing/unavailable section produces no history artifact | CONFIRMED / UNIT-PROVEN |
| Capability planning | `CapabilityAnalysisCoordinator.java:136-147`; `TrendContextAnalysisCapability.java:33-46` | Selected capability and exact history artifact | Required artifact may be unresolved by the planner; capability returns `DEGRADED` with no artifact when input is absent | CONFIRMED / UNIT-PROVEN |
| Assessment artifact | `TrendContextAnalysisCapability.java:49-95` | `TrendContextRoleHistory` | Mapper rejection returns degraded; valid history produces typed assessment artifact | CONFIRMED / UNIT-PROVEN |
| Observation | `TrendContextObservationService.java:21-35` | Completed capability with assessment content | Does not build an observation when no assessment artifact exists | CONFIRMED / INTEGRATION_PROVEN |
| Read model | `TrendContextReadService.java:33-65` | Persisted active observation and latest execution | No observation yields missing/404 through the controller; stale or non-current data is labeled accordingly | CONFIRMED / UNIT_PROVEN |
| Workspace | Story 0066 Decision Workspace and `repository-analysis.md:92-105` | Account, eligible market, read endpoint | Unavailable/missing state is surfaced; no fabricated assessment fallback | CONFIRMED / FRONTEND-TEST-PROVEN |

The report's causal explanation at lines 424-438 is consistent with this
source path. It proves why a missing role-history input can lead to HTTP 404;
it does not identify why the internal OHLC call failed.

## 6. MI -> Market Data OHLC Contract Analysis

### HTTP contract

Market Intelligence declares:

```java
@GetMapping("/api/v1/markets/{marketId}/ohlc")
List<OhlcResponse> findOhlc(
    @PathVariable UUID marketId,
    @RequestParam String interval,
    @RequestParam int limit);
```

Market Data exposes the same path in
`market-data/src/main/java/com/hope/trading/market_data/controller/MarketController.java:87-100`:

```java
@RequestParam OhlcInterval interval
@RequestParam(defaultValue = "200") int limit
```

`TrendContextRoleHistoryContextContributor` passes configured enum names:

- `FOUR_HOURS`;
- `ONE_HOUR`;
- `FIFTEEN_MINUTES`.

These values are valid `OhlcInterval` enum constants. The Market Data controller
test exercises `FIFTEEN_MINUTES` and `limit=50` successfully at the MVC boundary.
There is no source evidence of a parameter naming or interval representation
mismatch.

### Routing and authorization

`MarketDataClient` uses Feign service discovery with `name = "market-data"`.
Both services register with Eureka and fetch the registry in their application
properties. Docker Compose starts both services on the same network and passes
`TRADING_CORE_MARKET_DATA_SERVICE_JWT_SECRET` to both containers.

The Feign service-token interceptor only adds `X-Service-Authorization` to
URLs beginning `/internal/`. The OHLC endpoint is under `/api/v1`, and Market
Data security protects only `/internal/**`; `/api/v1/markets/{id}/ohlc` is
permitted without a service token. Therefore the current source does not show
an authentication mismatch for this call. The internal snapshot endpoint has a
different service-token path and is not the call used by the Trend Context
contributor.

This does not prove deployment configuration is correct. It only means that
authorization is not a confirmed source-level blocker for the OHLC endpoint.
Potential runtime causes remain: service discovery/registration, container
environment values, remote response/error behavior, data availability, or an
unobserved Feign exception.

### Empty-result and exception semantics

- An empty response is converted to missing Trend Context history.
- A runtime/Feign exception is logged by the contributor, rethrown, then
  converted by `IntelligenceContextAssembler` to an unavailable context.
- `CapabilityAnalysisCoordinator` does not materialize an unavailable section,
  so no `TREND_CONTEXT_HISTORY` artifact exists.
- The capability is allowed to execute degraded without input and emits no
  assessment artifact.
- No assessment means no persisted `TREND_CONTEXT` observation and ultimately a
  missing/404 read response.

This is safe degradation, but it can conceal the concrete remote cause from the
API result. The current code provides logs for that diagnosis; the validation
report contains no Market Intelligence container log showing the exception.

### Classification

**INCONCLUSIVE.** The contract is internally aligned, but the real MI-to-Market
Data call, service discovery, runtime environment, and remote response were not
proven by repository tests or a post-remediation authenticated scan. There is no
confirmed current code defect preventing `findOhlc` from obtaining OHLC.

## 7. Story 0067 Diff Classification

| Change in `ea1b196` | Classification | Relevance |
|---|---|---|
| `AccountController` delegates singular DTO creation to `AccountService` | BLOCKER_FIX | Fixes the missing profile enrichment path. |
| `AccountService.getAccountDtoById` and implementation | BLOCKER_FIX | Reuses `toDto`, including Risk and Trade Planning profile stores. |
| Account controller/service tests | TEST_ONLY | Prove the profile-enriched mapping with mocks. |
| `TrendContextRoleHistoryContextContributor` logging and request-object plumbing | OBSERVABILITY_ONLY | Runtime semantics of the Feign path and parameters remain unchanged. |
| `IntelligenceContextAssembler` warning log | OBSERVABILITY_ONLY | Makes contributor failure more diagnosable; does not recover OHLC. |
| Jackson field visibility in capability persistence and Trend Context types | BLOCKER_FIX | Plausibly fixes durable artifact serialization/reload after an artifact exists; does not acquire OHLC. |
| `TrendContextReadService` requested-time comparison | BLOCKER_FIX | Changes currentness evaluation after an observation exists; cannot create a missing observation. |
| `TrendContextAnalysisCapability` tests and read/controller test updates | TEST_ONLY | Prove typed capability behavior with supplied artifacts/mocks. |
| Decision Workspace markup/style/spec changes | UX_ONLY / TEST_ONLY | Makes existing states visible and tests the UI; does not alter the backend acquisition boundary. |
| `8af6a37` Prettier formatting | DOCUMENTATION_ONLY in effect | Formatting only; no runtime behavior. |

No Story 0067 diff is a confirmed fix for Market Intelligence Feign routing,
Market Data OHLC availability, or Docker service discovery.

## 8. Test-vs-Runtime Evidence Matrix

| Boundary or claim | Evidence | Classification |
|---|---|---|
| Contributor requests all configured roles with one cutoff | `TrendContextRoleHistoryContextContributorTest` uses mocked `MarketDataClient` and verifies three calls | UNIT_PROVEN |
| Contributor retries/handles mapping and missing history | Contributor implementation and focused unit coverage; no real remote | UNIT_PROVEN / PARTIAL |
| Feign method path and parameter contract | Interface source; no real Feign receiver test | PARTIAL |
| Market Data OHLC controller mapping | `MarketControllerTest` mocks `MarketHistoryService` | INTEGRATION_PROVEN only at controller boundary |
| Market Data service/JWT configuration | Security and properties source; no MI-to-Market-Data test | NOT_PROVEN |
| Service discovery and Docker network routing | Compose/properties source only | NOT_PROVEN |
| Trend Context capability consumes history and emits assessment | `TrendContextAnalysisCapabilityTest` supplies a constructed artifact | UNIT_PROVEN |
| Missing history degrades without fabricated assessment | `missingHistoryIsDegradedWithoutFabricatingAnAssessment` | UNIT_PROVEN |
| Observation persists from completed assessment | `TrendContextObservationIntegrationTest` and persistence tests use fixture/in-memory or Spring persistence boundaries | INTEGRATION_PROVEN |
| Read endpoint maps missing/current observations | `TrendContextReadServiceTest` and controller tests | UNIT_PROVEN / INTEGRATION_PROVEN |
| Account singular endpoint returns profile references | Account service/controller tests with mocked repositories/service | UNIT_PROVEN |
| Real account projection through deployed services | Validation report observed the old null state; no successful post-fix run | RUNTIME_NOT_REVALIDATED |
| Real Active Scan obtains OHLC, persists Trend Context, and returns read data | Validation report records no assessment and HTTP 404; no subsequent success evidence | RUNTIME_NOT_REVALIDATED |

Focused tests executed during this investigation passed:

```text
./trading-core/mvnw -q -f market-intelligence/pom.xml \
  -Dtest='TrendContextRoleHistoryContextContributorTest,TrendContextAnalysisCapabilityTest,ProductionIntelligencePipelineTest,TrendContextObservationIntegrationTest,TrendContextReadServiceTest,TrendContextControllerTest' test

./trading-core/mvnw -q -f trading-core/pom.xml \
  -Dtest='AccountControllerTest,AccountServiceImplTest,AccountMapperTest' test

git diff --check
```

Both Maven commands exited successfully. The test output included only the
existing Mockito dynamic-agent warnings. These results do not elevate mocked
boundaries to runtime proof.

## 9. Documentation Drift Findings

### `story.md`

| Statement | Classification | Finding |
|---|---|---|
| Status `Draft` at line 9 | CURRENT | Still accurate; no human acceptance evidence was found. |
| Runtime validation and durable report are required at lines 61 and 112-123 | CURRENT | Still accurate. |
| Acceptance checkboxes at lines 91-115 | AMBIGUOUS | They are acceptance criteria, not completion evidence; none are checked. |

### `repository-analysis.md`

| Statement | Classification | Finding |
|---|---|---|
| Runtime status “not executed” at lines 344-353 | STALE_DOCUMENTATION | Superseded by the 2026-10-02 validation report. |
| Repository recommendation to proceed without a production fix at lines 377-388 | HISTORICAL_EVIDENCE | Correct for the pre-validation baseline, but not a current runtime result. |
| Repository answers Q1-Q12 | HISTORICAL_EVIDENCE | Mostly code/readiness analysis; should not be read as authenticated runtime proof. |

### `validation-report.md`

| Location / statement | Classification | Finding |
|---|---|---|
| Lines 1-5: blocked report dated 2026-10-02 | CURRENT as historical record | Correctly records the validation attempt and its result. |
| Lines 64-81: services running and authenticated setup | HISTORICAL_EVIDENCE | Valid evidence for that environment/run only. |
| Lines 107-121: account profiles unavailable and Trend Context request not dispatched | HISTORICAL_EVIDENCE | Explains the pre-remediation or intermediate runtime state; not current-main proof. |
| Lines 166-216: degraded scan, Trend Context 404 | HISTORICAL_EVIDENCE | A real observed failure during the run; no later successful rerun is recorded. |
| Lines 376-398: “current blocker” and singular projection target | STALE_DOCUMENTATION / CONTRADICTORY | The account projection conclusion conflicts with current `AccountController` and `AccountServiceImpl`. The Trend Context runtime result remains historical and unresolved by evidence, but “current” is too strong without a post-fix run. |
| Lines 402-415: singular endpoint directly maps through `AccountMapper` | CONTRADICTORY | Describes the parent source, not `main` at `80c3b40`. |
| Lines 416-447: causal Trend Context chain | CURRENT as source explanation, HISTORICAL as runtime result | The causal degradation path remains consistent with source; the remote cause remains unknown. |
| Lines 453-495: deferred work and acceptance table | CURRENT | The validation checkpoints remain unexecuted; the table should distinguish code/test PASS from runtime PASS. |

The eventual report revision should use two separate labels:

```text
Failure observed during validation on 2026-10-02:
  Account profile fields were null and Trend Context read returned 404.

Current blocker on final main:
  Not established for Account projection; Trend Context acquisition remains
  runtime-unrevalidated and therefore unresolved as an operational question.
```

It should not state that the old account projection is a current code defect.
It should retain the 404 as immutable historical evidence and explicitly state
whether a later authenticated run reproduced it.

## 10. Remaining Confirmed Blockers

No current source-level Account projection blocker is confirmed.

The following remain confirmed as validation blockers, not necessarily code
defects:

1. There is no post-remediation authenticated Active Scan evidence showing a
   persisted Trend Context assessment and successful read response.
2. The official validation path has not proven the MI-to-Market Data OHLC call
   through discovery and deployed configuration.
3. The current failure mode degrades to no history, no assessment, no
   observation, and 404; the exact remote exception/result is not recorded.

The following are not confirmed blockers from this investigation:

- a wrong OHLC endpoint;
- wrong interval names;
- missing authorization for the public OHLC path;
- a missing Trend Context capability selection;
- a Trend Context engine or threshold defect;
- an Account mapper defect on current `main`.

## 11. Unknowns Requiring Runtime Evidence

- Does the deployed Market Intelligence container resolve `market-data` through
  Eureka at scan time?
- Does the deployed Market Data container have the expected market history for
  all three required intervals and requested limits?
- Does the real Feign call return an empty list, a 4xx/5xx, a decode error, or
  another exception?
- Does the active scan execute the current image built from `80c3b40`, rather
  than a pre-fix image or stale container?
- Does the singular account response in the deployed stack contain assigned
  Risk and Trade Planning profile references for the validation account?
- After a successful role-history acquisition, does persistence reload the
  `TREND_CONTEXT_HISTORY` and assessment artifacts and create the observation?
- Does `GET /api/v1/intelligence/trend-context/{marketId}` return an assessment
  after that scan?

## 12. Recommended Next Action

**RUNTIME_REVALIDATION_REQUIRED.** Do one narrow, human-authorized,
read-only-after-setup authenticated validation on the final current `main`
runtime:

1. Confirm the singular account response and Decision Context contain the
   assigned profile references.
2. Execute one official Active Scan for an eligible market.
3. Capture the scan/analysis result, Market Intelligence logs for each
   `findOhlc` role call, artifact/observation outcome, and the Trend Context
   read response.
4. Stop before TradePlan, Risk, or execution actions.

If the call fails, the recorded Feign/status/log evidence will distinguish a
concrete code/configuration defect from runtime data or deployment failure. If
it succeeds, update Story 0067's validation documentation to mark the earlier
404 as historical and continue only with the remaining human validation gates.

No follow-up Story should be created from this repository-only investigation,
and no production fix is justified before that evidence exists.

## Explicit Answers

### Q1. Is the Account/Decision Context projection blocker still present on current main?

**No as a source-code defect. RESOLVED in code, RUNTIME_NOT_REVALIDATED.** The
singular endpoint now uses `AccountServiceImpl.toDto`, which enriches assigned
Risk and Trade Planning profile references. The validation report's null fields
are historical evidence from the earlier runtime state.

### Q2. Is there a confirmed code defect currently preventing `TrendContextRoleHistoryContextContributor` from obtaining OHLC?

**No. INCONCLUSIVE.** The current Feign path, endpoint, parameter names, enum
interval values, and public Market Data authorization boundary align in source.
The deployed call and its concrete response/failure are not proven.

### Q3. Does current main contain a remediation that was applied after the runtime failure documented in `validation-report.md`?

**Yes for the Account projection.** `ea1b196` replaces the direct mapper path
with the profile-enriching service path and adds tests. The same commit adds
artifact persistence/read hardening and diagnostics. It does **not** contain a
confirmed OHLC acquisition remediation.

### Q4. Has that remediation been proven through the official authenticated Active Scan -> Trend Context runtime path?

**No. RUNTIME_NOT_REVALIDATED.** The automated tests are mocked/fixture-driven,
and the report contains no successful post-remediation authenticated scan that
produced a Trend Context assessment and read response.

### Q5. Is the 404 from the validation report evidence of current-main behavior, or only historical runtime evidence?

**It is historical runtime evidence from the 2026-10-02 validation attempt.** It
is not sufficient to establish current-main behavior after the committed
Account remediation or current deployment image.

### Q6. Are the existing automated tests capable of proving the missing runtime boundary?

**No. GAP.** They prove contributor logic, capability behavior, persistence
boundaries, controller mapping, and account mapping with mocks or fixtures. They
do not prove real MI Feign transport, service discovery, Docker environment,
service-to-service behavior, or a real Active Scan persisting Trend Context.

### Q7. Which classification applies: `CODE_FIX_REQUIRED`, `RUNTIME_REVALIDATION_REQUIRED`, `DOCUMENTATION_REMEDIATION_REQUIRED`, or `INVESTIGATION_INCONCLUSIVE`?

**`RUNTIME_REVALIDATION_REQUIRED`.** Documentation drift exists and should be
reconciled after evidence is collected, but current repository evidence does not
justify a new code fix or establish that investigation is impossible.

### Q8. What is the smallest next action justified by evidence?

**Run one controlled authenticated Active Scan on the final current `main`,
capture the account projection and MI-to-Market Data OHLC outcome, and stop
before TradePlan/Risk/execution.** Do not implement this action as part of this
investigation.

## 13. Follow-up Runtime Revalidation Result

The recommended runtime revalidation was subsequently completed with explicit
human authorization. The legacy LIVE account from the earlier attempt was not
reused. The official Accounts UI created a current PAPER account with explicit
Risk and Trade Planning Profile assignments.

The authenticated Decision Workspace confirmed the account projection and
Decision Context, then completed one Active Scan for `TBTC/USD`:

- Account: `2ded32af-a161-4ac6-8cea-b1a9f208ab33`;
- Market: `2bb7d23d-84c1-4b9e-878a-727213dec818` (`TBTC/USD`);
- Scan: `f19529dc-52e7-4c44-af2d-0a39c5491cab`;
- Analysis execution: `3b8b6455-9137-40f4-8c68-4ef2e751b7aa`;
- Scan status: `COMPLETED`;
- Market result: `OPPORTUNITY_FOUND`, quality `DEGRADED`;
- Opportunity: `69330a1d-5e5b-31d4-bad3-6a386fa8bf89`, Legacy OHLC Trend.

The Trend Context endpoint returned HTTP 200 with a valid persisted assessment
and observation version `1`. The Decision Workspace displayed the assessment
as `UP / TRANSITIONING / CONTEXTUALLY_DANGEROUS` with `BIAS_TRANSITION`
alignment, role evidence, exclusions, and invalidation findings. The human
reviewer confirmed that the assessment was understandable and useful.

The run continued through the existing human-controlled PAPER lifecycle:

- Manual TradePlan `d7c88cd4-2377-49cb-84c2-e1fb64edf3da` was created and
  human-accepted;
- Risk evaluation `9e193730-4ff6-41ce-9d4b-f4cad4041838` returned `APPROVED`;
- Explicit human execution authorization produced filled PAPER execution
  `93e7b5c1-293d-47a2-a2ac-6c7afb50db54`;
- The position survived Positions-page reload;
- Official close produced execution
  `1fe176dc-4288-4d85-8871-5653793ea080`;
- Positions returned no open position after close and Trade History reloaded
  both executions.

The earlier Account Projection blocker and Trend Context 404 are now
historical runtime evidence for the legacy fixture. This follow-up proves one
successful current PAPER path but does not complete the required negative
scenario matrix or establish profitability.
