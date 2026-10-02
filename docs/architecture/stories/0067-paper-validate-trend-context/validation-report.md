# Story 0067 - Validation Report

**Status:** BLOCKED by current Trend Context runtime path before the human validation checkpoint

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
