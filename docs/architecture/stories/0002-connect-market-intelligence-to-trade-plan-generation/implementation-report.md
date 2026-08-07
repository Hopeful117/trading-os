# Implementation Report

## Story

Story 0002 - Connect Market Intelligence to Trade Plan Generation.

## Status

Blocked before production source changes.

The mandatory stop condition in the approved Implementation Plan was reached:
Trading Core cannot assemble every required ADR-027 `TradingContext` fact from
current authoritative sources without inventing financial values or semantic
mappings.

No partial production pipeline was implemented because the approved outcome
requires the authenticated account-scoped continuation to create a Trade Plan.
Implementing only the global analysis stages would leave the approved end-to-end
flow incomplete and would require later changes to contracts and durable lineage
whose final planning inputs are currently undefined.

## Repository State

- Repository: `/home/ludo/Bureau/workspace/trading-os`
- Branch: `main`, tracking `origin/main`
- Initial working-tree state captured with `git status --short --branch`:

```text
## main...origin/main
 M .idea/compiler.xml
AM docs/stories/0002-connect-market-intelligence-to-trade-plan-generation/story.md
?? docs/stories/0002-connect-market-intelligence-to-trade-plan-generation/implementation-plan.md
?? docs/stories/0002-connect-market-intelligence-to-trade-plan-generation/repository-analysis.md
```

All pre-existing changes were preserved. No commit, push, merge, reset,
checkout, clean, or discard operation was performed.

## Authoritative Inputs Reviewed

The following inputs were read completely before the blocker determination:

- `AGENTS.md`
- the approved Story 0002 Story, Repository Analysis, and Implementation Plan;
- accepted ADR-003, ADR-004, ADR-014, ADR-020, ADR-021, ADR-022, ADR-023,
  ADR-025, ADR-026, ADR-027, and ADR-028;
- implementation documentation for ADR-020, ADR-021, ADR-022, ADR-023,
  ADR-025, ADR-026, ADR-027, and ADR-028;
- Story 0001 implementation plan, implementation report, and engineering
  report;
- the current Market Intelligence, Trading Core, and Market Data contracts and
  persistence mappings relevant to analysis execution, planning context, risk
  facts, and normalized prices.

## Blocker

The approved Implementation Plan requires Trading Core to assemble an immutable
planning-context snapshot containing authoritative account currency, available
capital, buying power, leverage, effective risk and rule profile identities and
versions, existing exposure, execution preferences, and source versions and
provenance. It also explicitly requires implementation to stop when any such
fact cannot be assembled without invention.

Current authoritative coverage is incomplete:

| Required fact | Current authoritative source | Result |
| --- | --- | --- |
| Authenticated actor | JWT-derived `UserDto` principal used by the existing Story 0001 controller | Available |
| Account ownership | Persisted `Account.user` relationship and existing ownership check | Available |
| Account currency | Persisted `Account.baseCurrency` | Value available, but account snapshot version/provenance is absent |
| Available capital | No ADR-027 source; existing balances and ADR-028 valued balance have different semantics | Missing |
| Buying power | No Trading Core persistence or production read port provides it | Missing |
| Effective leverage | Legacy `Rules.maxLeverage` is an upper bound; existing Story 0001 receives leverage from an already-created Market Intelligence context | Missing |
| Effective risk profile ID/version | Persisted semantic risk profile and exact account assignment | Available when explicitly provisioned |
| Effective rule profile ID/version | Individual risk rules and policy versions exist, but no approved mapping defines them as the distinct ADR-027 rule profile | Missing semantic contract |
| Existing exposure | Versioned broker position facts exist | Available as source facts, subject to an explicit planning projection |
| Execution preferences | No authoritative Trading Core model, schema, or read port provides them | Missing |
| Complete source versions/provenance | Broker and risk-profile sources are versioned; account and missing planning facts are not | Incomplete |

Relevant current contracts include:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/tradeplan/TradingContext.java`
  requires positive available capital, buying power, and leverage, non-empty
  risk and rule profiles, exposure, and execution preferences.
- `trading-core/src/main/java/com/hope/trading/trading_core/model/Account.java`
  persists ownership and base currency but has no planning snapshot version or
  provenance.
- `trading-core/src/main/java/com/hope/trading/trading_core/model/Rules.java`
  contains legacy limits, including maximum leverage, but no effective planning
  leverage or execution-preference contract.
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/BrokerRiskFactsPort.java`
  supplies versioned ADR-028 broker facts but does not define available capital,
  buying power, effective planning leverage, or execution preferences.
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/persistence/RiskPersistence.java`
  persists versioned risk profiles and risk-evaluation snapshots, not a complete
  pre-planning ADR-027 snapshot.
- `trading-core/src/main/resources/db/migration/common/V2__trade_plan_risk_evaluation.sql`
  defines risk profiles, account risk configuration, and ADR-028 evaluation
  artifacts but no planning-context or execution-preference source.

Story 0001 cannot close these gaps. Its Market Intelligence transport contains
the existing `TradingContext` values, but Trading Core receives them only after
a Trade Plan already exists. Reusing those values to construct the prerequisite
planning context would be circular. Its ADR-028 balances, profile limits, and
evaluation snapshots also have different purposes and completeness semantics;
relabelling them as available capital, buying power, effective leverage, a rule
profile, or execution preferences would violate the approved no-inference
constraint.

The following prohibited substitutes were not introduced:

- balance or equity treated as available capital;
- buying power calculated from equity, margin, or a leverage limit;
- `maxLeverage` treated as current effective leverage;
- risk policy or profile identity silently treated as the ADR-027 rule profile;
- legacy session/cooldown rules treated as execution preferences;
- zero, empty, default, test-fixture, placeholder, or synthetic financial facts;
- ADR-028 snapshots reused as ADR-027 snapshots without an explicit mapping
  contract.

## Market Data Finding

Independent inspection also confirmed that the approved conditional Market
Data extension would be required after the Trading Core blocker is resolved.
The current `MarketPriceSnapshot` supplies market ID, symbol, bid, ask, last,
tradability, occurrence time, and availability status, but it supplies neither
a Market Data capture timestamp nor a stable source snapshot identity/version.
The current in-memory latest-ticker path does not guarantee that
`(marketId, occurredAt)` uniquely identifies a reproducible snapshot.

Relevant files:

- `market-data/src/main/java/com/hope/trading/market_data/dto/MarketPriceSnapshot.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/MarketPriceSnapshotService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/MarketPriceSnapshotResponse.java`

No Market Data file was modified because the primary mandatory context blocker
stopped implementation first.

## Architecture Delivered

No Story 0002 production architecture was delivered. Existing behavior remains
unchanged:

- the public analysis dispatcher still uses the legacy
  `IntelligenceOrchestrator`;
- ADR-023 remains disconnected from the production public analysis path;
- analysis executions, ADR-023 capability executions and artifacts,
  Observations, Opportunities, and Trading Contexts remain process-local;
- the existing `TradePlanningEngine` and `TradePlanApplicationService` remain
  unchanged;
- no risk evaluation, Execution Intent, broker order, broker call, Gateway
  route, or frontend behavior was triggered or added.

## Modified and Created Files

Production source files modified: none.

Migrations created or modified: none.

Story documentation created:

- `docs/stories/0002-connect-market-intelligence-to-trade-plan-generation/implementation-report.md`

No pre-existing Story artifact or IDE file was modified by this implementation
attempt.

## Tests and Validation

No focused tests were added because production implementation was stopped at
the approved mandatory blocker.

The complete Market Intelligence, Trading Core, and Market Data Maven suites
were not executed. No affected module was modified, and there is no partial
implementation to validate. These suites must be run in full when the blocker
is resolved and Story 0002 production changes are implemented.

Repository validation executed:

```text
git diff --check
Passed with no output.
```

## Remaining Work

Before Story 0002 can be implemented without invention, an approved
authoritative Trading Core contract must provide:

- available capital semantics and source;
- buying power semantics and source;
- effective planning leverage semantics and source;
- the ADR-027 rule-profile identity/version or an explicit approved mapping
  from existing versioned configuration;
- execution-preference identity/content and source;
- immutable version/provenance semantics for every planning-context source;
- explicit projection semantics from broker positions to ADR-027 existing
  exposure.

The design must also decide how these facts are provisioned for existing
accounts without schema backfill or fabricated values. Missing facts must remain
a controlled fail-closed outcome.

After that approval and implementation, the existing Story 0002 plan can resume
at Phase 1. The additive Market Data snapshot provenance contract must also be
implemented before a traceable planning continuation can use a price.

## Recommendation

Blocked. Resolve and approve the missing authoritative ADR-027 planning-context
sources and semantic mappings, then resume the approved Implementation Plan.

---

## Resumed Implementation After ADR-031

ADR-031 and the approved corrective addendum resolved the blocker documented
above. Production implementation resumed without removing or rewriting the
original blocker history.

### Delivered Architecture

- `AnalysisExecutionService` remains the public lifecycle and request-idempotency
  boundary.
- `LocalAnalysisExecutionDispatcher` now delegates exclusively to
  `CapabilityAnalysisCoordinator`; it no longer depends on or invokes
  `IntelligenceOrchestrator`.
- The coordinator materializes normalized Market Data context as initial
  ADR-023 artifacts, calls `ExecutionPlanner` and `ExecutionEngine`, and maps
  accepted terminal results into the existing consolidated public response.
- Spread and OHLC range implement the ADR-023 `Capability` contract while
  retaining one shared deterministic calculation path with their compatibility
  interfaces.
- Only `COMPLETED` capability executions with `COMPLETE` results can enter
  `ObservationBuilder`. A versioned OHLC trend rule derives direction from the
  measured first-open to last-close change; flat or unavailable evidence is a
  controlled no-signal outcome.
- `ProductionIntelligencePipeline` invokes `ObservationBuilder` and
  `OpportunityEngine`, transitions eligible Opportunities through `DETECTED`,
  `ANALYZED`, and `ACTIVE`, and persists stage outcome and exact versions.
- Analysis, capability execution, artifacts, observations/evidence,
  opportunities, Trade Planning Context, pipeline runs, and generation lineage
  use production JPA stores. Exact rule/evidence fingerprints prevent replay
  from creating additional Observation or Opportunity versions.
- Trading Core exposes authenticated
  `POST /api/v1/intelligence/analyses/{analysisExecutionId}/trade-plans` with a
  required `Idempotency-Key` and body containing only `accountId`.
- Trading Core derives actor identity from `UserDto`, verifies account
  ownership, resolves the explicitly assigned immutable Trade Planning Profile,
  persists the continuation, and sends the exact ADR-031 planning context once
  under the same downstream idempotency key.
- Market Intelligence exposes the internal continuation at
  `POST /internal/v1/intelligence/analyses/{analysisExecutionId}/trade-plans`.
  It accepts no caller-supplied Opportunity or price, resolves exact pipeline
  lineage, imports the immutable context with conflict detection, acquires a
  fresh Market Data snapshot, selects ask for long and bid for short, and calls
  the existing `TradePlanApplicationService` transactionally.
- No risk evaluation, Execution Intent, Broker Service call, order, Gateway
  route, or frontend flow was introduced.

### Migrations

- `market-intelligence/src/main/resources/db/migration/V2__production_intelligence_pipeline.sql`
  adds schema-only tables and indexes for analysis executions, capability
  executions/artifacts, observations/evidence, Opportunity versions,
  TradePlanningContext snapshots, pipeline runs, and generation/price lineage.
- `trading-core/src/main/resources/db/migration/common/V6__analysis_trade_plan_continuations.sql`
  adds the schema-only authenticated continuation/idempotency record.
- Existing `trading-core/.../V5__trade_planning_profiles.sql` from the approved
  ADR-031 work remains the source for immutable profile versions and account
  assignments.
- No migration seeds, fabricates, or backfills identities, account facts,
  prices, profile values, context values, or provenance.

### Production Files Added or Changed by the Resumed Story

- Market Intelligence execution: `CapabilityAnalysisCoordinator`,
  `LocalAnalysisExecutionDispatcher`, production artifact types/content,
  ADR-023 spread/OHLC implementations, and persistence-safe aggregate restore
  operations.
- Market Intelligence pipeline: `ProductionIntelligencePipeline`,
  `OhlcTrendObservationRule`, `PlanningPriceSelector`,
  `AnalysisTradePlanGenerationService`, internal controller/request, and JPA
  pipeline/generation entities.
- Market Intelligence persistence: JPA adapters/entities/Spring Data
  repositories for `AnalysisExecution`, `CapabilityExecution`, artifacts,
  Observation/evidence, Opportunity versions, and `TradePlanningContext`;
  production in-memory bean wiring was removed while in-memory classes remain
  available to focused tests.
- Trading Core continuation: `AnalysisTradePlanController`,
  `AnalysisTradePlanGenerationService`, its exception, the Market Intelligence
  Feign contract, and continuation JPA entity/repository.
- Market Data provenance: `MarketPriceSnapshot`,
  `MarketPriceSnapshotService`, and the matching Market Intelligence response
  now include deterministic source snapshot identity/version and `capturedAt`.
- Protected concurrent files, including `.idea/compiler.xml`,
  `AccountController.java`, ADR/Story source documents, quality tooling, and
  POM changes, were not reverted or overwritten.

### Tests Added

- `ProductionCapabilityContractTest`: ADR-023 metadata/input/output,
  deterministic spread/range values, completeness, and artifact provenance.
- `DurableExecutionPersistenceTest`: JPA round-trip resolution of completed
  AnalysisExecution, CapabilityExecution/result, and polymorphic artifact.
- `PlanningPriceSelectorTest`: ask-for-long, bid-for-short, and no fallback for
  missing/neutral executable sides.
- `MarketPriceSnapshotServiceTest`: stable source identity/version and exact
  response capture timestamp while preserving unavailable/unknown outcomes.
- `AnalysisTradePlanControllerTest`: JWT principal supplies actor, request body
  contains only account, and `Idempotency-Key` is mandatory.
- `AnalysisTradePlanGenerationServiceTest`: account ownership, exact assigned
  profile mapping, durable successful replay, and one downstream invocation.
- Existing architecture, observation, opportunity, Trade Planning, risk
  handoff, and persistence tests remain enabled.

### Validation Results

- `market-intelligence: mvn test` - PASS, 122 tests, 0 failures/errors/skips.
- `market-data: mvn test` - PASS, 31 tests, 0 failures/errors/skips.
- Trading Core focused continuation tests - PASS, 4 tests.
- `trading-core: mvn test` - 74 tests executed; 73 pass and the pre-existing,
  timing-sensitive
  `RiskAcknowledgmentOutboxPersistenceTest.claimLeasePreservesExactIdentityAndSupportsDurableExplicitRetry`
  failed its lease assertion on the final run. The same test failed on the
  pre-implementation baseline, passed on an intermediate full run, and is
  outside Story 0002/protected Story 0001 scope.
- `git diff --check` - PASS with no output before this report append; rerun in
  final validation.
- No Sonar server/token scan or deployed PostgreSQL/service E2E was executed in
  this resumed local implementation.

### Remaining Issues

- Stabilize the pre-existing Trading Core outbox lease test independently of
  Story 0002.
- Validate the three-service HTTP flow and migrations against deployed
  PostgreSQL/Market Data in the normal environment before release.
- Human review and approval gates, external Sonar Quality Gate, and commit
  remain intentionally pending.

---

## ADR-031 and Quality Tooling Completion

### Corrected Planning Boundary

- The former financial `TradingContext` and its repository/access contracts
  were replaced by `TradePlanningContext`, `RiskBudget`, typed versioned
  `PlanningPreferences`, and planning-specific references and repositories.
- Position sizing consumes the exact monetary `RiskBudget.amount`; it does not
  derive a budget from capital, balance, equity, buying power, leverage, or a
  percentage of another financial value.
- The Market Intelligence to Trading Core risk handoff now contains the exact
  immutable Trade Plan and planning provenance only. Financial profiles,
  generic maps, leverage, buying power, and exposure were removed.
- Trading Core persists immutable Trade Planning Profile versions and
  append-only account assignment versions. Profile creation, version creation,
  assignment, and effective-profile lookup are authenticated owner operations.
  Missing assignment fails closed and migrations provision no defaults.
- Trading Core continues to own ADR-028 context assembly. Proposed-order margin
  now uses the broker-neutral `RequiredMarginPort`; the current production
  adapter deliberately returns unavailable until an authoritative proposed-order
  margin source is integrated, producing `REQUIRED_MARGIN_UNAVAILABLE` rather
  than reusing planning leverage or inventing a value.

### Quality Tooling Files

- Java module configuration: `broker-service/pom.xml`, `eureka-server/pom.xml`,
  `gateway/pom.xml`, `market-data/pom.xml`, `market-intelligence/pom.xml`,
  `risk-domain/pom.xml`, and `trading-core/pom.xml`.
- Frontend configuration: `trading-os-web/angular.json`, `package.json`,
  `package-lock.json`, `.gitignore`, and `sonar-project.properties`.
- Repository tooling: `.gitignore`, `scripts/quality-verify.sh`,
  `scripts/quality-scan.sh`, `docker-compose.sonar.yml`, and
  `docs/quality-tooling.md`.
- Every Maven module uses pinned JaCoCo `0.8.15`, has a distinct
  `trading-os:<module>` Sonar project key, and produces
  `target/site/jacoco/jacoco.xml` during `verify`.
- The frontend uses pinned `@vitest/coverage-v8` and `@sonar/scan`, emits LCOV,
  and has its own Sonar project key. Generated coverage and scanner output are
  ignored.
- The separate pinned SonarQube Community Build/PostgreSQL Compose stack has no
  application database, network, or startup dependency.
- Documentation records the approved new-code gate: no new high-severity bugs
  or vulnerabilities, all new Security Hotspots reviewed, at least 80 percent
  new-code coverage, and at most 3 percent new-code duplication. Sonar remains
  supplementary to tests, ADR/architecture validation, migration review, and
  human Code Review.

### Complete Production Change Inventory

- Market Data: `MarketPriceSnapshot`, `MarketPriceSnapshotService`, and
  `MarketPriceSnapshotServiceTest` add stable source identity/version and capture
  time without changing normalized price meaning.
- Market Intelligence analysis execution: `LocalAnalysisExecutionDispatcher`,
  `CapabilityAnalysisCoordinator`, `CapabilityEngineConfiguration`,
  `ProductionArtifactTypes`, `DeterministicMeasurements`, the spread/OHLC
  capabilities, and persistence-safe restore operations on execution/artifact
  domain objects.
- Market Intelligence intelligence pipeline: `ObservationBuilder`,
  `ObservationFactory`, `ObservationRehydrator`,
  `OhlcTrendObservationRule`, `ProductionIntelligencePipeline`,
  `PlanningPriceSelector`, `AnalysisTradePlanGenerationService`, and the internal
  continuation controller/request.
- Market Intelligence persistence: the new `Jpa*` and `SpringData*` adapters and
  entities under `adapter/persistence` for analyses, capability executions,
  artifacts, observations/evidence, opportunities, planning contexts, pipeline
  runs, and planning generations; `V2__production_intelligence_pipeline.sql`.
- Market Intelligence Trade Planning: all former `TradingContext` files were
  removed; `TradePlanningContext`, `RiskBudget`, typed `PlanningPreferences`,
  planning repositories/access policy, planning requests/engine/policies,
  Trade Plan mapping/persistence/web contracts, replanning, and the Story 0001
  risk handoff were updated.
- Trading Core ADR-031: `tradeplanning/domain/TradePlanningProfile.java`, profile
  API/application/repository files, `TradePlanningProfileJpaRepository`, and
  `V5__trade_planning_profiles.sql`.
- Trading Core Story 0002 continuation: `AnalysisTradePlanController`,
  `AnalysisTradePlanGenerationService` and exception, continuation entity and
  repository, `MarketIntelligenceTradePlanningClient`, and
  `V6__analysis_trade_plan_continuations.sql`.
- Trading Core Story 0001 handoff correction: `TradePlanRiskPort`,
  `MarketIntelligenceRiskClient`, `TradePlanRiskEvaluationService`,
  `RequiredMarginPort`, `UnavailableRequiredMarginClient`, and focused tests.
- Tests added or updated under Market Intelligence capability, execution,
  pipeline, Trade Planning, persistence, and web packages; Trading Core profile,
  continuation, persistence, controller, and risk packages; and Market Data
  snapshot service tests.
- Pre-existing `.idea/compiler.xml` and `AccountController.java` edits were not
  modified, reverted, staged, or included as Story corrections.

### Final Local Validation

Commands executed from the repository root unless a module directory is shown:

```text
./scripts/quality-verify.sh
./trading-core/mvnw -f trading-core/pom.xml jacoco:report
npm --prefix trading-os-web run test:coverage
npm --prefix trading-os-web run build
docker compose -f docker-compose.sonar.yml config
bash -n scripts/quality-verify.sh && bash -n scripts/quality-scan.sh
env -u SONAR_HOST_URL -u SONAR_TOKEN ./scripts/quality-scan.sh
cd trading-core && ./mvnw -Dtest=TradePlanningProfileJpaRepositoryTest,TradePlanningProfileServiceTest,AnalysisTradePlanGenerationServiceTest,AnalysisTradePlanControllerTest test
cd trading-core && ./mvnw verify
git diff --check
```

Results:

- `risk-domain verify`: PASS, 21 tests.
- `eureka-server verify`: PASS, 1 test.
- `gateway verify`: PASS, 1 test.
- `broker-service verify`: PASS, 61 tests.
- `market-data verify`: PASS, 31 tests.
- `market-intelligence verify`: PASS, 122 tests.
- Trading Core Story 0002/profile focused run: PASS, 7 tests.
- `trading-core verify`: FAIL, 74 tests executed, 73 passing. The sole failure is
  the pre-existing timing-sensitive
  `RiskAcknowledgmentOutboxPersistenceTest.claimLeasePreservesExactIdentityAndSupportsDurableExplicitRetry`;
  the enabled scheduler claims the test row before its lease assertion. No
  Story 0001 outbox code or test was changed.
- Angular coverage: PASS, 25 files and 33 tests; statements 67.18 percent,
  branches 70.87 percent, functions 40.21 percent, lines 64.28 percent.
- Angular production build: PASS with the existing initial bundle budget warning
  (`553.50 kB`, 53.51 kB above the 500 kB warning budget).
- Sonar Compose rendering: PASS.
- Quality script shell syntax: PASS.
- `git diff --check`: PASS with no output.
- npm reported 6 dependency audit findings during installation: 4 moderate and
  2 high. No dependency remediation was added outside the approved tooling.

Coverage reports exist at:

- `risk-domain/target/site/jacoco/jacoco.xml`
- `eureka-server/target/site/jacoco/jacoco.xml`
- `gateway/target/site/jacoco/jacoco.xml`
- `broker-service/target/site/jacoco/jacoco.xml`
- `market-data/target/site/jacoco/jacoco.xml`
- `market-intelligence/target/site/jacoco/jacoco.xml`
- `trading-core/target/site/jacoco/jacoco.xml`
- `trading-os-web/coverage/trading-os-web/lcov.info`

`SONAR_HOST_URL` and `SONAR_TOKEN` were not configured. Remote scans and Quality
Gate checks were therefore not executed. The scan script's fail-closed missing
credential behavior was executed and confirmed. No successful remote analysis
or Quality Gate result is claimed.

### Final Remaining Issues

- The Trading Core full `verify` remains red only because of the existing
  scheduled outbox lease race described above.
- The broker-neutral proposed-order margin port requires an authoritative
  provider before Story 0001 can authorize plans under the corrected ADR-031
  handoff; until then it fails closed.
- PostgreSQL 16/17 migration rehearsal and deployed Trading Core -> Market
  Intelligence -> Market Data HTTP E2E were not available in this local run.
- Remote Sonar scans, server-side gate configuration, human Code Review, and
  human commit remain pending.

### Final Git Status

Final command: `git status --short --branch`.

- Branch: `main`, tracking `origin/main`; no commit was created.
- Porcelain summary with all untracked files: 58 modified, 6 deleted, 73
  untracked, and 2 added-then-modified authoritative input files.
- Pre-existing user changes remain visible at `.idea/compiler.xml` and
  `trading-core/src/main/java/com/hope/trading/trading_core/controller/AccountController.java`.
- Pre-existing authoritative uncommitted inputs remain visible at
  `docs/adr/ADR-031.md` and the Story 0002 Story, Repository Analysis,
  Implementation Plan, and Implementation Report.
- Implementation changes remain unstaged and visible under `market-data`,
  `market-intelligence`, `trading-core`, every Java module POM,
  `trading-os-web`, `scripts`, `docs/quality-tooling.md`, `.gitignore`, and
  `docker-compose.sonar.yml` for human inspection.
- No file was staged, committed, pushed, merged, reset, checked out, cleaned,
  discarded, or used to rewrite history.
