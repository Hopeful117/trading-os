# Story 0067 - Validation Report

**Status:** BLOCKED by current Trend Context runtime path before the human validation checkpoint

This header records the initial validation state. Later dated sections preserve
the subsequent successful PAPER runtime evidence and final human-decision
reconciliation; the historical status is not the final closure classification.

**Validation date:** 2026-10-02

**Scope:** Validation only. No production code, tests, thresholds, strategy
governance, broker credentials, or broker operations were changed. A dedicated
validation user, PAPER trading account, and one official market scan were
created through the official UI after explicit human authorization. No direct
database mutation was used.

## 1. Automated Technical Validation

### Results

| Command | Result | Evidence |
|---|---|---|
| `./trading-core/mvnw -q -f market-intelligence/pom.xml -Dtest='TrendContextEngineTest,TrendContextCanonicalScenarioTest,TrendContextObservationIntegrationTest,TrendContextReadServiceTest,TrendContextJpaObservationPersistenceTest,ProductionIntelligencePipelineTest,Story0065TrendContextStrategyTest,GenericPipelineProofTest,StrategyMatchRequiredTruthTest,TrendContextEvidenceSelectorTest,TrendContextControllerTest' test` | PASS | 86 tests, 0 failures, 0 errors across the selected reports |
| `./trading-core/mvnw -q -f trading-core/pom.xml -Dtest='*PaperExecutionPersistenceIntegrationTest,*PaperSettlementExitTest,*PaperExitAcceptanceIntegrationTest,*TradePlanRiskEvaluationServiceTest,*AnalysisTradePlanControllerTest,*OpportunityTradePlanOrchestrationServiceTest,*ExecutionDomainTest,*ExecutionValidationStepRegressionTest,*ExecutionAttemptCreationStepRegressionTest,*IdempotencyVerificationStepRegressionTest,*PositionControllerTest' test` | PASS | Selected reports present with 0 failures and 0 errors; PAPER reports cover 8 tests, Risk 20 tests |
| `./trading-core/mvnw -q -f risk-domain/pom.xml test` | PASS | 25 tests, 0 failures, 0 errors |
| `npm run test:ci -- --include='src/app/core/services/trend-context.service.spec.ts' --include='src/app/features/decision-workspace/decision-workspace.spec.ts'` | PASS | 2 files, 29 tests |
| `npm run build -- --configuration production` | PASS | Production build completed; existing bundle/style budget warnings |
| `git diff --check` | PASS | No whitespace errors |

The first Maven invocation was corrected to the repository convention from
`scripts/test-all.sh`, which uses `trading-core/mvnw` for Market Intelligence
and Risk Domain. That wrapper-path correction was not a product or test
failure.

The broad Market Intelligence command `./trading-core/mvnw -q -f
market-intelligence/pom.xml test` exceeded the 120-second tool timeout while
producing test reports. It is classified as **NOT EXECUTED**;
the focused 86-test command above is the authoritative completed baseline for
this validation run.

## 2. Controlled Negative Evidence

| Scenario | Result | Evidence or limitation |
|---|---|---|
| `NO_SETUP` | PASS | Covered by deterministic Trend Context canonical scenario tests and read-model states |
| `WATCH` | PASS | Covered by deterministic Trend Context canonical scenario tests |
| `UNKNOWN` | PASS | Covered by deterministic Trend Context canonical scenario tests/read service tests |
| Stale evidence | PASS | Trend Context engine/read service and frontend stale/historical semantics are covered |
| Missing/unavailable evidence | PASS | Observation/read-service and controller tests preserve unavailable/missing states |
| Incomplete evidence | PASS | Deterministic input/observation tests cover incomplete inputs; no runtime fixture used |
| Synthetic/gapped input | NOT REPRODUCED | No supported authenticated runtime fixture was available; no thresholds or data were altered |
| Abnormal volatility | NOT REPRODUCED | No supported authenticated runtime fixture was available |
| Market-data failure | PASS | `ProductionIntelligencePipelineTest.marketDataFailureFailsTheRunAtObservationStage` |
| No applicable strategy | PASS | `ProductionIntelligencePipelineTest.ineligibleStrategiesAreNeverEvaluated` and Story 0065 governance tests |
| Multiple strategy matches | PASS | Pipeline test proves no hidden singular first-pick |
| Risk rejection | PASS | `TradePlanRiskEvaluationServiceTest` includes rejected/unavailable Risk behavior |
| Risk unavailable | PASS | Risk service tests cover dependency failure and fail-closed behavior |
| No execution after rejected/unavailable Risk | PASS | Risk and execution boundary tests verify no unsafe continuation |
| No PAPER position after blocked execution | PASS | PAPER execution guards and Risk boundary tests; no runtime position was created |

The PASS values in this section are automated deterministic/fixture evidence,
not human product judgments and not authenticated production-runtime evidence.

## 3. Runtime Environment

### Validation rerun

The validation commands were rerun on 2026-10-02 without modifying application
code, test code, thresholds, strategy governance, credentials, or broker state.
The authorized browser rerun created a dedicated validation user, a PAPER
trading account, and one official market scan through the UI.

Current read-only runtime evidence:

- `docker compose ps`: all expected application and PostgreSQL services are
  running;
- Web root `http://localhost:17085/`: HTTP 200;
- Gateway health request `http://localhost:17080/actuator/health`: HTTP 200;
- Trading Core health request `http://localhost:17081/actuator/health`: HTTP 404;
- Gateway market and intelligence requests without JWT: HTTP 401.

No broker credentials were available. The official browser journey was
executed through authentication, PAPER account creation, account selection,
and market selection. Scan, assessment, TradePlan, RiskEvaluation,
ExecutionIntent, position, and broker-operation steps remain **NOT EXECUTED**.

### Authenticated browser checkpoint

A new validation user was registered and logged in through the official browser
UI. The authenticated `Decision Workspace` then displayed:

```text
No trading account is available for this decision context.
```

The authenticated `Accounts` page initially displayed no broker connection. Its
Kraken connection form requires an API key and API secret for a broker
connection, but no broker credentials were available for this validation run;
none were requested from the application, inspected, invented, or recorded.
The PAPER account path did not require broker credentials and was used instead.

### Authenticated PAPER rerun after official account creation

Using the same authenticated browser session, the official PAPER account
creation flow completed successfully with the available risk policy and an
initial simulated balance. The resulting account identity was returned by the
application as `779e6e43-793a-4087-939b-4b63b835fab0`.

The Decision Workspace then loaded 1360 markets and selected `BNT/USD`, market
ID `6639ce1d-f17d-4405-bed1-6385b8a0bda8`. Market metadata and live market data
loaded successfully. The selected account summary nevertheless displayed:

```text
Risk profile: Unavailable
Planning profile: Unavailable
```

The browser network trace recorded a successful
`GET /api/v1/intelligence/decision-context/{accountId}` response, but no
`GET /api/v1/intelligence/trend-context/{marketId}` request was dispatched in
this state. The current rerun therefore confirms the account/Decision Context
projection blocker and classifies direct Trend Context runtime retrieval as
**NOT REPRODUCIBLE** for this account state. The earlier authenticated runtime
evidence of an official scan followed by a Trend Context HTTP 404 remains
recorded below as separate prior evidence.

The official Opportunities scan was then executed through the browser for the
same account and market, without creating a TradePlan. The application returned:

- Scan ID: `faeb7f26-7af1-4fe4-be60-2b75cf60aa54`;
- Scan status: `COMPLETED`;
- Analysis execution ID: `3555a71b-d374-4493-b05a-351a477ddc57`;
- Result quality: `DEGRADED`;
- Scan outcome: `OPPORTUNITY_FOUND`;
- Opportunity ID: `4be0ad4f-6e76-3a08-861d-29533496872b`, version 3;
- Opportunity scenario: `Legacy OHLC Trend`;
- Opportunity observation: `41e0bd3a-d24b-4b6c-ae06-c92cc562ed17`;
- Strategy Match ID: `cb4a4442-1575-441d-a40d-a28fd9ff1734`.

This is a legacy OHLC strategy result and is not attributed to Trend Context.
No TradePlan creation, Risk evaluation, execution intent, position, or broker
operation was initiated.

`docker compose ps` showed the following services running:

- Eureka Server;
- Gateway;
- Trading Core;
- Broker Service;
- Market Data;
- Market Intelligence;
- Trading Web;
- PostgreSQL services for Trading Core, Broker Service, Market Data, and
  Market Intelligence.

Supported endpoint checks returned:

- Web root: HTTP 200;
- Eureka applications endpoint: HTTP 200;
- Gateway actuator request: HTTP 401, confirming the route is protected;
- Trading Core actuator request: HTTP 403, confirming protection;
- Market Data actuator request: HTTP 404, not a valid health endpoint for this
  deployment;
- Gateway market catalogue request without JWT: HTTP 401.

An initial browser navigation with a stale local token was redirected by the
application to `/error?status=401` with `Session expirée`. After logout and a
new official login, authentication succeeded.

## 4. Trend Context Runtime Evidence

**Result: BLOCKED**

The official mechanism is present in the repository:

```text
active scan or analysis
  -> Market Intelligence pipeline
  -> IntelligenceObservation
  -> Trend Context read model
  -> Decision Workspace
```

The supported read endpoint is:

```text
GET /api/v1/intelligence/trend-context/{marketId}
```

The supported runtime preparation was executed through application APIs using
a newly created validation user and a PAPER account. No credentials are
recorded here. The account was provisioned with a supported Risk profile and
the application-created effective Trade Planning Profile is readable through
`GET /api/v1/trade-planning-profiles/accounts/{accountId}/effective`.

Runtime evidence:

- Market: `BNT/USD`;
- Provider: `KRAKEN`;
- Market ID: `6639ce1d-f17d-4405-bed1-6385b8a0bda8`;
- Account ID: `2d14e281-8832-4a3b-ae00-caddd4174a68`;
- Active scan ID: `a60290ca-13bd-48c3-adc3-35be0fed8dfc`;
- Analysis execution ID: `06c0e410-8fdb-4ba1-9bd0-3d4c18c24618`;
- Scan status: `COMPLETED`;
- Analysis status: `COMPLETED`;
- Result quality: `DEGRADED`;
- Scan outcome: `OPPORTUNITY_FOUND`;
- Opportunity: `80c23d83-2169-3654-bc6d-9e6cf202e17a`, version 3;
- Opportunity scenario: `Legacy OHLC Trend`;
- Opportunity observation: `6c56c15a-4808-4b98-8f1e-6ebaac02fe49`;
- Opportunity timeframe: `m15`;
- Opportunity direction: `LONG`;
- Trend Context read: HTTP 404;
- Direct `POST /api/v1/intelligence/analyses`: HTTP 403.

The official scan therefore produces a Legacy OHLC observation/opportunity but
does not produce a current Trend Context read model for the selected market.
The direct analysis endpoint is not an available fallback for this authenticated
user. No hidden trigger, direct database mutation, fabricated ID, or direct
broker call was used.

## 5. Human Product Validation

**Result: BLOCKED**

The required visual/product checkpoint was not simulated because no current
Trend Context assessment became visible. The human must inspect the following
only after the runtime blocker is resolved and a real current assessment is
visible in Decision Workspace:

1. selected market correctness;
2. freshness and cut-off clarity;
3. direction;
4. regime;
5. phase;
6. BIAS, SETUP, and optional TRIGGER clarity;
7. alignment and contradictions;
8. supporting evidence;
9. invalidation;
10. analytical-evidence versus Risk/execution authority labeling;
11. usefulness before deciding whether to prepare a trade.

The agent must stop here and wait for the human’s findings and explicit
continuation. No TradePlan or discretionary execution action was taken.

## 6. Strategy Boundary Evidence

**Result: PASS**

Runtime validation: **NOT EXECUTED**.

The selected tests prove that a favorable context is not independently a
StrategyMatch and that a strategy must satisfy governance and applicability
before evaluation. The production pipeline also proves that no applicable
strategy produces a truthful no-signal outcome.

`CONSERVATIVE_TREND_FOLLOWING_V1` remained `UNVALIDATED` and `DISABLED`.
It was not enabled or modified.

No opportunity is attributed to Trend Context without actual persisted
StrategyDefinition, StrategyEvaluation, StrategyMatch, and Observation lineage.

## 7. TradePlan Evidence

**Result: PASS**

Official runtime validation: **NOT EXECUTED**.

The selected Trading Core TradePlan tests passed, including opportunity-origin
orchestration and analysis handoff coverage. The frontend requires an explicit
human action to create an opportunity-based or manual TradePlan. No plan was
created during this run.

## 8. Risk Evidence

**Result: PASS**

Official runtime validation: **NOT EXECUTED**.

The selected Risk suite passed, including 20
`TradePlanRiskEvaluationServiceTest` cases. Rejection and dependency failure
paths remain fail-closed. No RiskEvaluation ID was created by this validation
run.

## 9. Human Execution Authorization

**Result: NOT EXECUTED**

Risk approval would not be treated as execution authorization. The agent did
not reach this checkpoint and did not infer authorization from any analytical
or automated state.

## 10. PAPER Execution Evidence

**Result: PASS**

Official runtime validation: **NOT EXECUTED**.

The completed PAPER integration tests passed:

- `PaperExecutionPersistenceIntegrationTest`: 1 test;
- `PaperSettlementExitTest`: 3 tests.

They prove simulated execution can persist an intent, attempt, filled
BrokerOrder, fill, account balances, and open Trade without calling the live
broker adapter. No runtime ExecutionIntent, attempt, BrokerOrder, fill, or
settlement was created in this run.

## 11. Position / Close / Reload Evidence

**Result: PASS**

Official runtime validation: **NOT EXECUTED**.

`PaperExitAcceptanceIntegrationTest` passed 4 tests, including entry, close,
reload, empty position query, idempotent replay, concurrent close protection,
and rollback behavior. No runtime position was created or closed.

## 12. Provenance Chain

No new runtime provenance exists. The expected chain is supported by the
existing contracts:

```text
Market / Provider / Timeframe / Cut-off
  -> AnalysisExecution / PipelineRun
  -> IntelligenceObservation / Trend Context assessment
  -> StrategyDefinition / StrategyEvaluation / StrategyMatch
  -> TradingOpportunity
  -> TradePlan + version + human decision
  -> RiskEvaluation + result
  -> ExecutionIntent / attempt / BrokerOrder / fill
  -> Trade / Position
```

All runtime fields in this chain are **NOT EXECUTED** for the current run.

## 13. Environmental Limitations

- The Docker runtime was available.
- An authenticated validation session was created through the supported UI, but
  no existing PAPER account was available to that user.
- The supported account-connection form requires Kraken API credentials even for
  PAPER mode; those credentials were not available.
- No credentials were requested, printed, or inspected.
- A validation user was created through the official registration UI with
  explicit human direction; no trading or broker account was created.
- Current provider freshness and eligible-market state remain unverified.
- The broad Market Intelligence suite exceeded the tool timeout; focused
  relevant tests completed successfully.

## 14. Technical Debt

The full frontend suite reproduced the existing Story 0066 issue:

```text
Test files: 43 passed, 3 failed
Tests:      341 passed, 9 failed
```

Failures:

- `src/app/core/services/token.service.spec.ts`: 7 failures;
- `src/app/app.spec.ts`: 1 failure;
- `src/app/layout/shell/shell.spec.ts`: 1 failure.

The error is `Cannot read properties of undefined` for `localStorage` in the
jsdom/Vitest environment. The run also reports Node’s
`localStorage is not available because --localstorage-file was not provided`.
This reproduces the prior environment issue and does not implicate Story 0067
or the Story 0066 Trend Context files. It was not modified opportunistically.
The requested historical `336 passed / 9 failed` count was not reproduced; the
current repository run reports `341 passed / 9 failed`.

Production build warnings remain for bundle and component-style budgets. The
build still exits successfully.

## 15. Story Blockers

**Current blocker: current Trend Context production path**

Exact failures:

- authenticated PAPER provisioning succeeds through supported APIs;
- the official active scan completes for `BNT/USD` with `OPPORTUNITY_FOUND`,
  but the result is `DEGRADED` and the opportunity is `Legacy OHLC Trend`;
- `GET /api/v1/intelligence/trend-context/6639ce1d-f17d-4405-bed1-6385b8a0bda8`
  returns HTTP 404;
- `POST /api/v1/intelligence/analyses` for the same market returns HTTP 403;
- the account read projection has `tradePlanningProfileId: null` and
  `executionMode: null`, while the supported effective-profile endpoint
  returns a profile and the broker-account endpoint returns `PAPER`;
- the Decision Context projection likewise reports null Risk and Trade Planning
  profile references;
- no current Trend Context assessment reaches Decision Workspace;
- required human inspection cannot begin.

This is now a concrete accepted-contract/runtime integration blocker, not merely
missing credentials. A non-trivial production fix is not authorized by this
validation task. The smallest likely investigation targets are the official
scan-to-Trend-Context capability selection and the account/Decision Context
projection mapping, but no implementation change is made here.

## 15.1 Investigation Conclusion

### Account projection

**Result: BLOCKED**

The singular Trading Core endpoint used by Market Intelligence is:
`GET /api/v1/accounts/{accountId}`. `AccountController.getAccount()` returns
`accountMapper.toDto(account)` directly. `AccountMapper` does not populate the
Risk Profile or Trade Planning Profile references.

The list endpoint follows a different path: `AccountServiceImpl.toDto()` enriches
the mapped account from the assigned Risk and Trade Planning Profile stores
before returning it. This explains why the Accounts page shows both profiles
while Decision Context displays both as `Unavailable`.

### Scan to Trend Context

**Result: BLOCKED**

The source confirms that `ActiveAnalysisStrategy` explicitly selects
`trend-context-analysis`. The failure is therefore not an intentional exclusion
of the capability.

The confirmed execution chain is:

1. `TrendContextRoleHistoryContextContributor` returns a missing Trend Context
   section when no role history is acquired.
2. `CapabilityAnalysisCoordinator` materializes no
   `TREND_CONTEXT_HISTORY` artifact.
3. `ExecutionPlanner` permits the required Trend Context artifact to remain
   missing because the requirement accepts partial context.
4. `TrendContextAnalysisCapability` runs without an input artifact and returns
   `DEGRADED` with no assessment artifact.
5. `TrendContextObservationService` only builds an observation when a completed
   capability contains an assessment artifact, so no `TREND_CONTEXT` observation
   is persisted.
6. `TrendContextReadService` consequently has no current observation and the
   official read returns `404`.

This explains how the same scan can remain `DEGRADED` while the independent
Legacy OHLC strategy still creates an opportunity. The immediate source of the
missing role history in the deployed run is **NOT REPRODUCIBLE** from the
authenticated analysis-detail endpoints, which returned `403`; browser reads
of the Market Data OHLC endpoints for BNT/USD did return 60 candles for the
required intervals. The remaining runtime target is the internal
Market Intelligence-to-Market Data capability call or its service-token/path
configuration, not Trend Context decision logic itself.

No production fix, strategy enablement, threshold change, direct database
mutation, TradePlan creation, Risk evaluation, or execution authorization was
performed.

## 16. Deferred Work

- Human inspection of a real current Trend Context assessment;
- explicit human decision to create a TradePlan;
- runtime Risk rejection and approval evidence;
- explicit human authorization before any PAPER execution;
- runtime PAPER fill, position reload, close, and history reload;
- authenticated runtime provenance capture;
- any future Market Structure, SwingPoint, HH/HL/LH/LL, or TrendLine work.

`CONSERVATIVE_TREND_FOLLOWING_V1` remains disabled. No methodology tuning or
profitability claim is made.

## 17. Final Acceptance Checklist

| Criterion | Result |
|---|---|
| Automated Trend Context tests green | PASS |
| Market Intelligence observation/persistence tests green | PASS |
| Strategy boundary tests green | PASS |
| Decision Workspace tests green | PASS |
| TradePlan tests green | PASS |
| Risk boundary tests green | PASS |
| PAPER execution integration green | PASS |
| PAPER close/reload acceptance green | PASS |
| Production frontend build green | PASS |
| `git diff --check` | PASS |
| Full frontend suite | FAIL: 9 pre-existing jsdom localStorage failures |
| Authenticated PAPER runtime | BLOCKED |
| Current Trend Context runtime evidence | BLOCKED |
| Human Trend Context usefulness judgment | NOT EXECUTED |
| Human TradePlan decision | NOT EXECUTED |
| Human execution authorization | NOT EXECUTED |
| Runtime PAPER execution/close/reload | NOT EXECUTED |
| No profitability or optimization claim | PASS |
| `CONSERVATIVE_TREND_FOLLOWING_V1` remains disabled/unvalidated | PASS |

## Human Continuation Request

To continue, the production/runtime blocker must first be reviewed and fixed
through an approved follow-up scope. After a current Trend Context assessment
is visible, the human must complete the Section 5 checklist. Only after the
human reports that assessment as understandable/useful may the workflow proceed
to the next stop before any PAPER execution authorization.

---

## 18. Current-Main Runtime Revalidation

**Revalidation date:** 2026-10-03
**Source commit validated:** `80c3b40384a1ce78912e5740d638b81878e2c826`
(`main`, merge of PR #66)
**Primary classification:** `ACCOUNT_PROJECTION_RUNTIME_DEFECT`

This section records a separate current-main runtime attempt. The original
2026-10-02 evidence above is preserved unchanged as **HISTORICAL FAILURE**.
The current result below is **CURRENT-MAIN RESULT**.

### 18.1 Runtime/services state

The existing Docker Compose runtime was stale relative to the source commit, so
the normal documented `docker compose up --build -d` workflow was used. The
first restart encountered a stale generated Eureka container-name conflict; the
normal volume-preserving `docker compose down --remove-orphans` followed by
`docker compose up -d` restored the runtime. No volumes were destroyed and no
database rows were mutated directly.

The rebuilt application containers were created from the current source on
2026-10-02 22:56Z. At the time of validation:

- Eureka: running;
- Gateway: running;
- Trading Core: running;
- Market Data: running;
- Market Intelligence: running;
- Trading Web: running;
- PostgreSQL dependencies: running.

Eureka returned HTTP 200 for the application registry and HTTP 200 for each of
`MARKET-INTELLIGENCE`, `MARKET-DATA`, and `TRADING-CORE` application lookups.
The web root returned HTTP 200. The Gateway actuator remained protected, as
expected.

### 18.2 Account projection result

The existing authenticated browser session selected the existing account:

- Account ID: `65bc843f-3bab-43af-bcdb-564630f2f950`;
- Account name: `kraken account`;
- Base currency: `EUR`.

The supported authenticated `GET /api/v1/accounts` response returned HTTP 200,
but the selected account contained:

```text
riskProfileId: null
riskProfileSemanticVersion: null
tradePlanningProfileId: null
tradePlanningProfileVersion: null
```

The official Decision Workspace displayed:

```text
Risk profile: Unavailable
Planning profile: Unavailable
```

The supported authenticated
`GET /api/v1/intelligence/decision-context/65bc843f-3bab-43af-bcdb-564630f2f950`
response returned HTTP 200 and contained the same null profile references in
its `account` object. This confirms that the Decision Context account state
available to the workspace did not contain the assigned profile references.

The singular account request was initiated through the authenticated browser
application boundary but did not complete within the browser request timeout;
the Decision Context response independently confirms the same null account
projection fields received by Market Intelligence.

**Result: `ACCOUNT_PROJECTION_RUNTIME_FAIL`.**

The source remediation is present in the validated image:
`AccountController` delegates to `AccountService.getAccountDtoById`, and
`AccountServiceImpl.toDto` contains the profile enrichment calls. The rebuilt
runtime nevertheless returned null references for this account. This is a
current runtime failure, but this run does not establish whether the cause is
profile assignment state for this account, a runtime persistence/projection
path discrepancy, or another deployment-level issue. No code was changed.

### 18.3 Pre-scan Market Data OHLC availability

**NOT EXECUTED.** The validation instructions require stopping the Active Scan
branch when Account Projection fails. No pre-scan OHLC request was issued, so
there is no current-main 4H/1H/15m result from this run.

| Interval | Result |
|---|---|
| `FOUR_HOURS` | NOT EXECUTED |
| `ONE_HOUR` | NOT EXECUTED |
| `FIFTEEN_MINUTES` | NOT EXECUTED |

### 18.4 Official Active Scan identity/result

**NOT EXECUTED.** No scan was created. No scan, analysis, market selection,
capability selection, or execution identity was generated by this revalidation.
The prior scan IDs in this report remain historical and were not reused.

### 18.5 MI -> Market Data role-call evidence

**NOT OBSERVED.** Because the Account Projection stop condition was reached
before an Active Scan, no `MarketDataClient.findOhlc(...)` call was caused by
this run. The following role outcomes are therefore not available:

| Role | Interval | Result |
|---|---|---|
| BIAS | `FOUR_HOURS` | NOT OBSERVED |
| SETUP | `ONE_HOUR` | NOT OBSERVED |
| TRIGGER | `FIFTEEN_MINUTES` | NOT OBSERVED |

No authorization headers, JWTs, credentials, or raw provider payloads were
recorded.

### 18.6 Trend Context artifact pipeline result

No Active Scan was run after the rebuilt deployment, so this revalidation did
not produce or inspect new Trend Context pipeline artifacts:

| Stage | Current-main revalidation result |
|---|---|
| `TREND_CONTEXT` context section | UNKNOWN / NOT EXECUTED |
| `TREND_CONTEXT_HISTORY` artifact | UNKNOWN / NOT EXECUTED |
| `TREND_CONTEXT_ASSESSMENT` artifact | UNKNOWN / NOT EXECUTED |
| Completed `trend-context-analysis` capability | UNKNOWN / NOT EXECUTED |
| Persisted `TREND_CONTEXT` IntelligenceObservation | UNKNOWN / NOT EXECUTED |

### 18.7 Trend Context read endpoint

**NOT EXECUTED.** The authenticated read request
`GET /api/v1/intelligence/trend-context/{marketId}` was not issued in this
run because the account projection stop condition occurred first. The previous
HTTP 404 remains **HISTORICAL FAILURE**, not a result of this current-main
attempt.

### 18.8 Decision Workspace result

The official Decision Workspace was opened with the authenticated existing
account. It loaded the account and eligible-market context, but displayed both
assigned profiles as `Unavailable`. No market was selected for Trend Context
inspection, no Trend Context request was dispatched, and no trade-planning or
execution action was taken.

### 18.9 Remaining blocker

The current-main blocker reached by this run is the Account/Decision Context
profile projection for the selected account. It prevents the required official
Active Scan branch from being started under the prescribed stop conditions.

This run does **not** confirm the earlier Market Intelligence-to-Market Data
OHLC hypothesis and does **not** confirm a later Trend Context pipeline defect.
Those boundaries remain untested in this attempt.

### 18.10 Smallest justified next action

Diagnose the supported account/profile assignment and singular account
projection state for account `65bc843f-3bab-43af-bcdb-564630f2f950` using
application-level evidence only, then rerun the account projection check. Do
not modify code, mutate the database directly, create another account, or
continue to OHLC/Active Scan until the account projection passes.

---

## 19. Successful PAPER End-to-End Revalidation

**Validation date:** 2026-10-03
**Runtime path:** authenticated Trading OS web application through Gateway
**Source/runtime:** current rebuilt `main` runtime
**Scenario:** controlled PAPER validation, no LIVE broker action

Section 18 remains the historical result for the legacy LIVE account. The
following run used a newly provisioned PAPER account through the official
Accounts UI and completed the supported decision-to-execution lifecycle.

### 19.1 PAPER account and profile projection

The official Accounts UI created:

- Trading Core account: `2ded32af-a161-4ac6-8cea-b1a9f208ab33`;
- Broker account: `c3d7306a-1185-40c0-aa44-fee16a9a8602`;
- Display name: `Story 0067 PAPER Validation`;
- Provider/mode/status: `KRAKEN / PAPER / CREATED`;
- Initial capital: `10,000 USD`;
- Risk Profile: `0a10c7e2-9d1e-4f5a-b6c8-123456789043` version `1.0.0`;
- Trade Planning Profile: `115a0394-49fe-43a7-af79-6e09f980f8d5` version `1`.

`GET /api/v1/accounts` returned HTTP 200 with both exact profile references.
The authenticated Decision Workspace also returned HTTP 200 and displayed:

```text
Risk profile: 1.0.0
Planning profile: 1
```

The previous account projection blocker is therefore resolved for a current
PAPER-provisioned account. The legacy LIVE account remains unchanged.

### 19.2 Trend Context runtime evidence

The authenticated Decision Workspace selected:

- Market: `TBTC/USD`;
- Market ID: `2bb7d23d-84c1-4b9e-878a-727213dec818`;
- Provider: `KRAKEN`;
- Market state: `OPEN`.

The official Active Scan was submitted for that market:

- Scan ID: `f19529dc-52e7-4c44-af2d-0a39c5491cab`;
- Analysis execution ID: `3b8b6455-9137-40f4-8c68-4ef2e751b7aa`;
- Candidate/effective market count: `1 / 1`;
- Scan status: `COMPLETED`;
- Market outcome: `OPPORTUNITY_FOUND`;
- Result quality: `DEGRADED`;
- Opportunity: `69330a1d-5e5b-31d4-bad3-6a386fa8bf89`, `SHORT`, `Legacy OHLC Trend`.

The degraded Legacy OHLC opportunity is recorded as separate evidence. The
Trend Context read endpoint subsequently returned HTTP 200 with a persisted
valid assessment, observation version `1`, and the following visible result:

```text
Direction: UP
Regime: TRANSITIONING
Phase: TRANSITION
Attention: CONTEXTUALLY_DANGEROUS
Alignment: BIAS_TRANSITION
Profile: CONSERVATIVE_SWING_V1 1.0.0
Rules: trend-context-rules-v1
```

The assessment exposed BIAS, SETUP, and TRIGGER role evidence, cut-off data,
exclusions, invalidation `INVALIDATION_UP_V1`, and findings including
`TRANSITION_ACTIVE`, `FAILED_BREAK_RECLAIM`, and `INSUFFICIENT_SWINGS`.
The human reviewer confirmed that the Trend Context was understandable and
useful before Trade Planning.

### 19.3 TradePlan and Risk evidence

A manual TradePlan was created from the official Decision Workspace:

- Plan ID: `d7c88cd4-2377-49cb-84c2-e1fb64edf3da`;
- Initial version: `1`;
- Direction/order: `SHORT / MARKET`;
- Quantity: `0.001`;
- Reference price: `81,203.3`;
- Stop loss: `82,000`;
- Take profit: `79,000`;
- Expected risk: `0.80 USD`;
- Risk/reward: `2.8R`.

The human explicitly accepted the plan. Deterministic Risk evaluation returned:

- Evaluation ID: `9e193730-4ff6-41ce-9d4b-f4cad4041838`;
- Result: `APPROVED`.

No execution occurred before the separate human execution authorization.

### 19.4 PAPER execution, reload, and close

After explicit human authorization, the PAPER execution completed:

- Execution ID: `93e7b5c1-293d-47a2-a2ac-6c7afb50db54`;
- Broker order: `SIM-3a1f5f7a-e2ea-4508-8dbd-f3eb02c51c61`;
- Broker status: `FILLED`;
- Filled quantity: `0.001`;
- Average price: `81,203.40`;
- Fees: `0.0000`.

The Positions page returned HTTP 200 after navigation and browser reload, and
showed one open `TBTC/USD` short position with the expected quantity, entry,
stop, take profit, exposure, and risk.

The position was then closed through the official Positions UI:

- Close endpoint: `POST /api/v1/accounts/2ded32af-a161-4ac6-8cea-b1a9f208ab33/positions/close`;
- Close execution: `1fe176dc-4288-4d85-8871-5653793ea080`;
- Close broker status: `FILLED`;
- Close filled quantity: `0.001`;
- Close average price: `83,967.00`.

After the close, the Positions page returned HTTP 200 and displayed no open
positions. Trade History returned HTTP 200 and displayed both the opening and
closing executions. The opening execution retained continuity to the Trade
Plan and account position context.

### 19.5 Current scenario result

| Boundary | Result |
|---|---|
| Current PAPER account/profile projection | PASS |
| Decision Context | PASS |
| Official Active Scan | PASS, degraded Legacy OHLC result also recorded |
| Trend Context persistence/read | PASS |
| Human Trend Context usefulness validation | PASS |
| Manual TradePlan creation | PASS |
| Human TradePlan acceptance | PASS |
| Deterministic Risk evaluation | PASS, APPROVED |
| Explicit execution authorization | PASS |
| PAPER fill | PASS |
| Position reload | PASS |
| PAPER close | PASS |
| Position empty after close | PASS |
| Trade History reload | PASS |
| LIVE execution | NOT EXECUTED |

This is successful evidence for one controlled PAPER scenario. It does not
complete the full Story acceptance matrix: `NO_SETUP`, `WATCH`, `UNKNOWN`,
stale/incomplete/synthetic/gapped/abnormal-volatility cases, negative Risk
runtime evidence, and broader lineage assertions remain separate validation
work. No profitability or optimization claim is made.

## 20. Current Runtime Revalidation: TBTC/EUR

**Validation date:** 2026-10-03
**Boundary:** Stop after Trend Context read. No TradePlan, Risk evaluation,
execution intent, position, or broker operation was initiated.

The existing authenticated PAPER account and Decision Context were reused:

- Account ID: `2ded32af-a161-4ac6-8cea-b1a9f208ab33`;
- BrokerAccount ID: `c3d7306a-1185-40c0-aa44-fee16a9a8602`;
- Risk Profile: `0a10c7e2-9d1e-4f5a-b6c8-123456789043`, version `1.0.0`;
- Trade Planning Profile: `115a0394-49fe-43a7-af79-6e09f980f8d5`, version `1`.

### Market and OHLC evidence

The authenticated Markets UI selected the following currently open market:

- Symbol: `TBTC/EUR`;
- Provider: `KRAKEN`;
- Market ID: `01b8a011-08d2-417c-811f-ec3e4b97dc31`;
- Market state: `OPEN`.

The market chart requested all required Story intervals successfully:

| Interval | Endpoint result |
|---|---|
| `FOUR_HOURS` | HTTP 200 |
| `ONE_HOUR` | HTTP 200 |
| `FIFTEEN_MINUTES` | HTTP 200 |

### Official Active Scan

Exactly one official scan was submitted from Opportunities using the existing
PAPER account and only the selected `TBTC/EUR` market:

- Scan ID: `1d7ef60a-3598-4457-bd59-ad2c69279c55`;
- Scan status: `COMPLETED`;
- Candidate/effective market count: `1 / 1`;
- Eligible/completed/failed: `1 / 1 / 0`;
- Analysis execution ID: `e421f0c3-f3c3-4e5f-8c90-3fc7265914d8`;
- Result quality: `DEGRADED`;
- Outcome: `OPPORTUNITY_FOUND`;
- Opportunity ID: `a457fefb-f49a-37ec-b17d-4b750e317a44`, version `3`;
- Opportunity: `SHORT`, `POSITIONAL`, `m15`, score `100`;
- Scenario/rationale: `Legacy OHLC Trend`;
- Supporting observation ID: `9b4ed30b-8cc1-4cc4-afce-5861bbffe3ba`.

The scan therefore produced a legacy OHLC opportunity and is not attributed to
Trend Context.

### Trend Context read evidence

Decision Workspace selected the same account and `TBTC/EUR`, then loaded:

```text
GET /api/v1/intelligence/decision-context/2ded32af-a161-4ac6-8cea-b1a9f208ab33 -> 200
GET /api/v1/intelligence/trend-context/01b8a011-08d2-417c-811f-ec3e4b97dc31 -> 200
```

The visible deterministic read model showed:

```text
Status: AVAILABLE
Validity: VALID
Observation version: 1
Direction: UNKNOWN
Regime: UNKNOWN
Phase: UNDETERMINED
Attention outcome: NO_SETUP
Alignment: INSUFFICIENT_DIRECTION
Profile / rules: CONSERVATIVE_SWING_V1 1.0.0 / trend-context-rules-v1
```

Role evidence was present for `BIAS`, `SETUP`, and `TRIGGER`. The BIAS role was
`UP / TRANSITIONING / TRANSITION` with `CONFIRMED_BEARISH_BREAK`; SETUP and
TRIGGER remained unknown/undetermined with insufficient confirmed swings. The
read model exposed `CUTOFF_EXCLUDED` and `OPEN_CANDLE_EXCLUDED` findings, plus
`TRANSITION_ACTIVE` and `INSUFFICIENT_SWINGS` evidence findings.

This confirms the current authenticated scan-to-observation-to-read path for a
controlled PAPER scenario. It also confirms that Trend Context remains
analytical evidence only and is explicitly labeled as not Risk approval.

## 21. Final PEPE/USD Human-Decision Reconciliation

This section records the subsequent real-market validation result. No additional
scan or production/test change is performed by this documentation reconciliation.

The human reviewer inspected the corresponding PEPE/USD charts and confirmed
that the deterministic Trend Context output was understandable and consistent
with the observed market structure:

```text
Operational status: AVAILABLE
Validity: VALID
Observation version: 1
Direction: DOWN
Regime: TRENDING
Phase: PULLBACK
Attention: NO_SETUP
Alignment: CONFLICTING
Contradiction: TIMEFRAME_CONTRADICTION
Observation ID: 8ddfe840-2861-41bc-987d-cab86a9a6a1c
```

The evidence included active-transition and failed-break-reclaim findings. No
separate `assessmentId` was exposed by the supported runtime contract.

This remains `ALIGNED_CONTEXT_NOT_OBSERVED`. It is not recorded as an aligned
context success. It is recorded as **REAL-MARKET NEGATIVE DECISION-SUPPORT
VALIDATION** because the system correctly rejected an apparently interesting
trending/pullback state when multi-timeframe evidence was conflicting:

```text
DOWN / TRENDING / PULLBACK
  -> TIMEFRAME_CONTRADICTION
  -> CONFLICTING / NO_SETUP
  -> no TradePlan
  -> no RiskEvaluation
  -> no execution
```

The human reviewer confirmed that the system did not encourage a trade and that
the `NO_SETUP` result was appropriate. This is positive evidence for the
conservative decision-support objective.

The canonical Story requires validation of aligned support but does not
explicitly require waiting for a naturally occurring aligned market state. The
aligned deterministic scenarios, real BIAS_TRANSITION assessment, real UNKNOWN /
NO_SETUP assessment, PEPE/USD rejection, successful PAPER lifecycle, and human
chart review are reconciled as total evidence. The absence of a naturally
occurring aligned state is therefore a
`HUMAN_ACCEPTED_VALIDATION_DEVIATION`, not a technical blocker or a reason to
manufacture market context.

The current contract exposes observation identity and lineage, but not a
separate assessment ID. The observation ID above is sufficient for the evidence
actually exposed and is classified `ASSESSMENT_ID_NOT_EXPOSED_ACCEPTED`. No ID
was invented and no production code was changed.

The human product-validation gate is complete. Story scope/acceptance review,
human code review, and the final Story status transition remain explicit human
gates. No code review is claimed here. Final closure classification:
`READY_FOR_HUMAN_CLOSURE`.

Future product learning only: high transaction volume alone is insufficient for
market discovery. Separate user-controlled market discovery filters from
deterministic system candidate selection, where candidate ranking represents
`ANALYSIS PRIORITY`, not trade probability, opportunity score, or a buy/sell
recommendation. This is outside Story 0067; no Market Structure work or new
Story is created here.
