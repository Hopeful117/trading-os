# Implementation Plan

## Story

Story 0002 — Connect Market Intelligence to Trade Plan Generation.

## Approved Architecture

The approved model keeps market analysis, Observations, and Opportunities global. Trade Plan generation is an explicit account-scoped continuation of a completed analysis.

```text
Global analysis request
    -> Market Intelligence public AnalysisExecution
    -> ADR-023 capability plan and execution
    -> durable artifacts and capability results
    -> durable observations
    -> durable active opportunity versions

Authenticated account-scoped continuation
    -> Trading Core validates user/account ownership
    -> Trading Core assembles an immutable planning-context snapshot
    -> Market Intelligence internal planning command
    -> Market Intelligence resolves the completed analysis/opportunities
    -> Market Intelligence acquires a timestamped Market Data price snapshot
    -> existing TradePlanApplicationService
    -> durable immutable Trade Plan candidate/version
```

The implementation must not invoke risk evaluation, create an Execution Intent, call Broker Service from Market Intelligence, or execute an order.

## Scope of Module Changes

### Market Intelligence — mandatory

- make ADR-023 the only production capability scheduler;
- connect analysis completion to Observation and Opportunity production;
- add durable repositories for the lineage required by a durable Trade Plan;
- accept a trusted internal account-scoped planning command;
- acquire and retain Market Data price provenance;
- invoke the existing Trade Planning application service;
- expose controlled pipeline and planning outcomes.

### Trading Core — minimal mandatory contract

- add the authenticated public continuation entry point;
- authorize the account against the authenticated user;
- assemble the ADR-027 planning-context snapshot from authoritative account/configuration projections;
- call Market Intelligence through a broker-neutral internal contract;
- do not reuse the risk-evaluation command and do not trigger risk evaluation.

### Market Data — additive contract only if required

- extend the existing internal price-snapshot response with stable snapshot provenance if the current `(marketId, occurredAt)` pair cannot be guaranteed unique and reproducible;
- preserve existing callers and normalized price semantics.

No other backend module and no Angular source is in scope.

## Contract Design

### 1. ADR-023 analysis execution coordinator

Add a Market Intelligence application service, tentatively `CapabilityAnalysisCoordinator`, invoked by `LocalAnalysisExecutionDispatcher`.

Inputs:

- `AnalysisExecution` ID;
- `IntelligenceAnalysisRequest`;
- declared analysis strategy/capability selection;
- initial artifacts assembled from existing Market Data context adapters.

Responsibilities:

1. construct `PlanningRequest` with the public analysis ID as `analysisExecutionId`;
2. call `ExecutionPlanner.plan` exactly once;
3. call `ExecutionEngine.execute` exactly once;
4. derive `ConsolidatedIntelligence` from accepted ADR-023 results;
5. complete/fail the existing `AnalysisExecution` through its current lifecycle;
6. invoke the post-analysis pipeline only for an execution whose accepted results meet eligibility rules.

`LocalAnalysisExecutionDispatcher` must no longer depend on or invoke `IntelligenceOrchestrator`. The legacy orchestrator and response model may remain temporarily for compatibility/tests, but they must not be Spring production entry points or schedule capabilities after this change.

Cancellation must propagate to the ADR-023 `ExecutionControl`. Late results remain governed by the existing `ExecutionEngine` rules.

### 2. Initial market-data artifacts

Add an application port such as `InitialAnalysisArtifactProvider` and implement it with the existing Market Data client/context adapters.

For the first increment it must materialize only the facts required by the existing production analyses:

- normalized market identity/symbol;
- normalized current price snapshot including bid, ask, last price, tradability, status, and occurrence time;
- normalized OHLC series for the requested interval.

Each `StoredArtifact` must contain:

- stable artifact identity/type/version;
- public market scope and analysis mode;
- parameters fingerprint;
- input fingerprint derived from exact source facts;
- source and production timestamps;
- producer and analysis execution provenance.

Unavailable or stale required facts must produce a planning/execution failure or skip defined by ADR-023, never a fabricated artifact.

### 3. Production capability migration

Create ADR-023 `Capability` implementations for the currently active deterministic behaviors:

- spread analysis;
- OHLC range analysis.

Prefer extracting/reusing pure calculation functions from the existing legacy implementations. Do not maintain two independent copies of business calculations.

Each capability must declare:

- stable ID and semantic version;
- category and deterministic origin;
- supported execution policy/mode;
- required artifact types and versions;
- produced artifact/contribution types;
- timeout and retry behavior;
- completeness rules.

Capabilities consume `CapabilityContext` only. They cannot access Feign clients, repositories, another capability, Spring state, or persistence.

The output must include deterministic measurements and evidence-ready facts. A valid no-signal result uses `CapabilityResult.noOpportunity`; partial output cannot be promoted to an Observation.

### 4. Consolidated public result adapter

Add a mapper from ADR-023 `ExecutionSummary` and accepted `CapabilityResult` values to the existing `ConsolidatedIntelligence` response.

The mapper must preserve:

- the original analysis ID, market ID, mode, and timestamps;
- capability IDs/versions and terminal states;
- accepted findings/measurements;
- missing/stale context warnings;
- `COMPLETE`, `PARTIAL`, `DEGRADED`, or `FAILED` quality semantics.

It must not schedule work or reinterpret failed results as successful findings.

### 5. Observation production

Add a registry of versioned `ObservationConsolidationRule` implementations for the migrated capabilities. Rules must be deterministic and explicit about:

- eligible capability IDs/versions;
- required `COMPLETE` results;
- observation type, horizon, validity window, and categories;
- evidence measurements, thresholds, observed time, and confidence contribution;
- no-signal outcome.

Extend `ObservationBuilder` with an idempotent creation operation keyed by:

```text
analysisExecutionId
+ consolidationRuleVersion
+ ordered evidence capability-execution IDs
+ evidence/artifact input fingerprints
```

Replay of the same key returns the existing Observation. A genuinely changed evidence set may produce the next immutable lineage version and supersede the previous active version using the existing factory/repository operation.

Invalid, incomplete, failed, skipped, timed-out, cancelled, or provenance-inconsistent results create no Observation and return a structured stage outcome.

### 6. Opportunity projection and lifecycle

Add a deterministic `OpportunityProjectionPolicy` that converts eligible active Observations from one analysis into zero or more `CreateOpportunityCommand` values.

For the first production rules:

- instrument comes from the authoritative analysis market identity artifact;
- direction/scenario/timeframe are derived only from explicit Observation content/measurements;
- origin maps from the analysis mode/origin;
- evaluated/validity timestamps come from the evidence and rule output;
- AI references remain empty because real AI integration is out of scope.

The policy invokes `OpportunityEngine.create`; it must not instantiate an Opportunity. Existing logical identity, 15-minute equivalence policy, immutable versions, and repository behavior remain authoritative.

After creation/versioning, the pipeline applies only valid `OpportunityEngine.transition` operations required to reach `ACTIVE`. If the current ADR-026 lifecycle requires deterministic enrichment before activation, encode that eligibility in the projection policy and transition through `ANALYZED` rather than skipping states.

Add pipeline-level idempotency before invoking `OpportunityEngine`, so replay does not append a new Opportunity version solely because the same analysis was reprocessed.

### 7. Post-analysis pipeline outcome

Add a `ProductionIntelligencePipeline` application service and immutable `IntelligencePipelineRun` model.

Stable key:

```text
analysisExecutionId + pipelineVersion
```

States:

- `RUNNING`;
- `COMPLETED`;
- `COMPLETED_NO_SIGNAL`;
- `FAILED_OBSERVATION`;
- `FAILED_OPPORTUNITY`.

The run records exact:

- analysis execution ID;
- capability execution/result/artifact references;
- Observation IDs and versions;
- Opportunity IDs and versions;
- policy/rule/pipeline versions;
- stage timestamps;
- controlled failure code/message.

Only one run for the stable key may produce side effects. Concurrent/replayed calls load the existing terminal result or resume from the last durably completed stage.

### 8. Authenticated Trading Core continuation

Add a Trading Core controller following the existing Story 0001 security convention:

```text
POST /api/v1/intelligence/analyses/{analysisExecutionId}/trade-plans
Idempotency-Key: <required>
Body: { "accountId": "..." }
```

The authenticated `UserDto` principal supplies the actor ID. The body must not accept `actorId`, balance, buying power, leverage, price, risk profile, rule profile, or Opportunity IDs.

Add a Trading Core application service, tentatively `AnalysisTradePlanGenerationService`, that:

1. validates the authenticated user owns the requested trading account;
2. loads the current authoritative account, portfolio/exposure, effective risk/rule configuration, and execution preferences needed by ADR-027;
3. creates an immutable `PlanningContextSnapshot` with a persistent ID/version and capture timestamp;
4. calls Market Intelligence once through an internal Feign port with the snapshot and idempotency key;
5. returns the exact Trade Plan ID/version or a controlled error.

The planning snapshot is separate from ADR-028 `RiskEvaluationContext`. Existing Story 0001 read models may be reused only through explicit mappings for semantically identical facts. Missing configuration or financial facts fail closed; no balance, leverage, profile, exposure, or preference may be inferred.

The Trading Core idempotency key is scoped by user, account, analysis execution, and operation. Reuse returns the same downstream result.

### 9. Internal Market Intelligence continuation

Add an explicit internal endpoint:

```text
POST /internal/v1/intelligence/analyses/{analysisExecutionId}/trade-plans
Idempotency-Key: <required>
```

The internal request contains:

- authenticated actor ID asserted by Trading Core;
- account ID;
- immutable planning-context snapshot ID/version/captured-at;
- account currency, available capital, buying power, leverage;
- effective risk profile and rule profile identities/versions;
- existing exposure and execution preferences;
- source snapshot versions/provenance.

Market Intelligence must:

1. require a terminal successful/eligible analysis;
2. load the terminal pipeline run and exact active Opportunity versions derived from it;
3. validate market/instrument consistency;
4. import the context snapshot idempotently and reject any payload mismatch for an existing ID/version;
5. acquire the current normalized price snapshot from Market Data using the analysis market ID;
6. require `AVAILABLE`, tradable, positive, and within a configured freshness bound;
7. select the conservative executable planning price explicitly (direction-aware ask for long, bid for short; no fallback to zero/last when the required side is absent);
8. invoke `TradePlanApplicationService.create` with the exact opportunities/context/actor/price;
9. persist the planning continuation and full lineage atomically with the created Trade Plan;
10. return a structured success or controlled failure.

The existing public `TradePlanController` may remain for existing compatibility, but this new connected production path must not trust its body-supplied actor ID.

### 10. Price provenance

Introduce a Market Intelligence value object such as `PlanningPriceSnapshot` containing:

- market ID and symbol;
- selected price and selected side;
- bid, ask, and last price;
- tradability/status;
- source occurrence timestamp;
- capture timestamp;
- source snapshot identity/version where available.

Persist it with the planning continuation. Extend Market Data's existing internal snapshot DTO additively only if a stable source identity/version is absent. Existing response fields and callers must remain compatible.

## Persistence Plan

Use existing repository ports as application boundaries and replace production in-memory beans with JPA adapters where durable lineage requires resolution after restart.

### Market Intelligence durable aggregates

Add additive Flyway migrations and JPA adapters for:

1. `analysis_executions`
   - exact lifecycle, idempotency key/scope, provenance, trace metadata, policy, result quality, timestamps, consolidated response payload;
   - unique reusable idempotency key constraint.

2. `capability_executions`
   - execution/attempt/group IDs, analysis ID, capability ID/version, state, timestamps, failure/skip/result payloads;
   - unique execution ID; indexed analysis ID.

3. `capability_artifacts`
   - artifact identity/version/scope/fingerprints, provenance, validity/freshness metadata, immutable payload;
   - unique analysis/artifact cache key and producing execution reference.

4. `observations` and `observation_evidence`
   - immutable Observation versions and status/linkage;
   - evidence, capability-result traces, artifact/raw-data traces;
   - unique observation id and idempotency fingerprint;
   - atomic supersede operation.

5. `trading_opportunity_versions`
   - immutable opportunity versions, status, identity dimensions, score, validity, origin, explanation;
   - exact Observation and AI-analysis references;
   - sequential version constraint and equivalence-query indexes.

6. `trading_context_snapshots`
   - exact snapshot ID/version, owner/account, currencies/capital/leverage/profiles/exposure/preferences, capture/source versions;
   - unique ID/version; immutable payload mismatch rejection.

7. `intelligence_pipeline_runs` and lineage children
   - unique analysis execution/pipeline version;
   - stage state/outcome and exact artifact/Observation/Opportunity references.

8. `analysis_trade_plan_generations`
   - scoped idempotency key;
   - analysis, actor/account, context, price snapshot, exact Opportunity versions, Trade Plan ID/version;
   - state/failure/timestamps;
   - unique operation scope and idempotency constraints.

### Existing Trade Plan schema

Keep `trade_plan_versions` append-only. Add only:

- exact Opportunity ID/version lineage if the current serialized rationale does not preserve versions sufficiently;
- optional foreign-key-ready context linkage using an additive/staged constraint;
- no backfilled synthetic contexts for pre-existing rows.

The Trade Plan append and generation-record completion must share one transaction in Market Intelligence. Refactor transaction ownership to an application transaction boundary if required; do not rely on nested independent repository transactions.

### Trading Core persistence

Persist the assembled planning context/outbound command result or reuse a versioned authoritative snapshot table only if it already represents every ADR-027 field. Add:

- immutable planning-context snapshot ID/version and source versions;
- generation idempotency/outcome record if downstream replay alone cannot guarantee the public contract.

No financial values are seeded by migration.

### Migration safety

- migrations are additive and schema-only;
- do not fabricate identities, versions, ownership, balances, profiles, exposure, timestamps, or provenance;
- preserve existing Story 0001 tables and data;
- stage foreign keys that cannot be validly applied to existing Trade Plan rows;
- validate PostgreSQL-specific constraints against disposable PostgreSQL 16 in addition to H2 tests when possible.

## Transaction and Concurrency Rules

- Persist capability terminal result and artifacts atomically per capability attempt.
- Persist Observation replacement and superseded predecessor atomically.
- Persist Opportunity append with a locked/latest-version or database uniqueness strategy.
- Acquire/insert the pipeline-run idempotency record before stage side effects.
- Persist imported Trading Context only when the exact ID/version payload is new; identical replay succeeds, differing replay fails.
- Persist Trade Plan append and generation success lineage in one Market Intelligence transaction.
- A failed remote call must leave a retryable generation state, not a false success.
- Concurrent identical requests must converge on one logical Observation set, Opportunity version, and Trade Plan version.

## Controlled Error Contract

Use stable codes and HTTP semantics across Trading Core and Market Intelligence:

- `ANALYSIS_NOT_FOUND` — 404;
- `ANALYSIS_NOT_COMPLETE` — 409;
- `ANALYSIS_NOT_ELIGIBLE` / `NO_SIGNAL` — 422;
- `PIPELINE_FAILED` — 422 or 409 according to terminal/retryable state;
- `ACCOUNT_NOT_FOUND` — 404;
- `ACCOUNT_FORBIDDEN` — 403;
- `PLANNING_CONTEXT_INCOMPLETE` — 422;
- `PLANNING_CONTEXT_CONFLICT` — 409;
- `OPPORTUNITY_NOT_ELIGIBLE` — 422;
- `MARKET_PRICE_UNAVAILABLE` / `MARKET_PRICE_STALE` — 503;
- existing `PlanningFailureReason` codes — 422;
- `IDEMPOTENCY_CONFLICT` — 409;
- dependency transport failure — 503 without downstream side effects.

Do not expose provider payloads, stack traces, secrets, or broker-specific values.

## Implementation Phases

### Phase 1 — Durable execution foundation

- add migrations and JPA adapters for analysis executions, capability executions, and artifacts;
- replace only the corresponding production in-memory beans;
- preserve in-memory adapters for focused unit tests;
- add repository contract and migration tests.

Exit criteria: analysis/capability/artifact state survives repository restart and retains exact provenance.

### Phase 2 — ADR-023 production execution

- add initial-artifact provider;
- migrate spread and OHLC range calculations to ADR-023 capabilities;
- add the ADR-023 coordinator and consolidated-result mapper;
- rewire dispatcher/cancellation;
- disable legacy scheduler as a production path.

Exit criteria: the public analysis endpoint executes only through ADR-023 and existing response behavior remains controlled.

### Phase 3 — Durable intelligence pipeline

- add Observation rules/idempotency and JPA repository;
- add Opportunity projection/lifecycle and JPA repository;
- add pipeline run/outcome/lineage persistence;
- invoke the pipeline after eligible analysis completion.

Exit criteria: completed analysis produces durable traceable Observations and eligible active Opportunities; replay is stable.

### Phase 4 — Account-scoped continuation

- add Trading Core authenticated endpoint/service and context assembly;
- add Trading Core -> Market Intelligence internal client;
- add Market Intelligence context import and generation service/controller;
- acquire/validate Market Data price snapshot;
- invoke existing Trade Planning service and persist complete generation lineage.

Exit criteria: one authenticated continuation from a completed analysis produces one durable Trade Plan candidate without risk or execution side effects.

### Phase 5 — Validation and cleanup

- add full-path integration, concurrency, restart, traceability, and architecture tests;
- remove production wiring to the legacy orchestrator without deleting compatibility types unnecessarily;
- run all affected module suites and static diff validation;
- document exact results in `implementation-report.md`.

## Test Plan

### Market Intelligence focused tests

- ADR-023 metadata and artifact contracts for each production capability;
- unchanged spread/OHLC deterministic calculations;
- planner selection and graph shape;
- dispatcher uses ADR-023 and never invokes legacy scheduling;
- cancellation and late-result behavior;
- consolidated-response mapping for complete/partial/degraded/failed executions;
- complete result creates Observation with full evidence;
- failed/incomplete/skipped/timed-out/cancelled result creates none;
- no-signal result creates no Observation and returns controlled outcome;
- Observation idempotency and legitimate new version/supersede;
- Observation projection to Opportunity;
- Opportunity valid lifecycle path, deduplication, and versioning;
- pipeline replay and concurrent duplicate requests;
- repository restart/resolution for execution, artifact, Observation, Opportunity, context, and lineage;
- internal continuation rejects incomplete analysis, unrelated Opportunity, context mismatch, stale/unavailable price, and unauthorized actor/context mismatch;
- direction-aware bid/ask selection;
- Trade Plan is created only through `TradePlanApplicationService` and stored once;
- exact end-to-end lineage query;
- no risk or execution boundary invocation.

### Trading Core focused tests

- authentication required;
- account ownership enforced;
- request body cannot override actor or financial facts;
- complete planning-context mapping with source versions;
- missing authoritative account/configuration/exposure fact fails closed;
- idempotent downstream call and response reuse;
- Market Intelligence controlled errors are translated without retrying unsafe duplicate writes;
- risk-evaluation service is never invoked.

### Market Data focused tests, if contract changes

- snapshot identity/version is stable and corresponds to the returned price/timestamp;
- unavailable/unknown markets remain explicit;
- existing snapshot callers remain compatible.

### Full validation commands

Run each Maven service independently:

```text
cd market-intelligence && ./mvnw test
cd trading-core && ./mvnw test
cd market-data && ./mvnw test   # only when modified
git diff --check
```

If the modules do not provide `mvnw`, use the repository's available `mvn test` convention and record the exact command. Never report a suite as passing unless it completed successfully.

## Acceptance-Criteria Trace

| Story criterion | Planned proof |
| --- | --- |
| Completed analysis triggers production pipeline | ADR-023 dispatcher integration + pipeline integration test |
| Eligible results create Observations | Versioned rules + ObservationBuilder tests |
| Invalid/incomplete/failed results create none | eligibility matrix tests |
| Evidence/source traceability | durable artifact/evidence lineage assertions |
| Observations create/update Opportunity | projection integration test |
| Deduplication/versioning preserved | existing and replay-focused tests |
| Trading Context authoritative | authenticated Trading Core assembler + immutable import tests |
| Existing Trade Planning flow invoked | service interaction/architecture test |
| Trade Plan/version persisted | JPA integration and restart test |
| Complete chain traceable | end-to-end lineage query test |
| Replay controlled | sequential and concurrent idempotency tests |
| Controlled intermediate failure | stage outcome and API error tests |
| One orchestration model | dependency/architecture test excluding legacy scheduler |
| Existing tests green | complete affected Maven suites |
| No risk/execution | negative interaction and architecture tests |

## Constraints for the Implementation Engineer

- Treat the approved Story, Repository Analysis, this plan, accepted ADRs, and current repository state as authoritative.
- Preserve all pre-existing user changes, including IDE files; do not discard or include unrelated edits.
- Do not commit, push, merge, reset, or rewrite history.
- Do not add a dependency unless already available in the affected module's managed Spring stack and strictly required; prefer current JPA, Flyway, Feign, validation, and test dependencies.
- Do not duplicate Opportunity, Trade Planning, risk, or execution business rules.
- Do not create placeholder identities, synthetic versions, inferred balances, zero/default financial facts, fail-open outcomes, or untraceable prices.
- Do not call Broker Service from Market Intelligence.
- Do not modify Angular, Gateway, Broker Service, Risk Domain, or unrelated modules.
- Stop and report if authoritative Trading Core sources cannot assemble every required `TradingContext` field without invention.
- Stop and report if an accepted ADR contradicts a required contract rather than silently selecting a new architecture.
- Produce `docs/stories/0002-connect-market-intelligence-to-trade-plan-generation/implementation-report.md` with modified files, migrations, tests, results, and remaining issues.

## Human Review Checkpoints After Implementation

The engineer should inspect in IntelliJ:

- production bean wiring proves only ADR-023 schedules capabilities;
- capabilities contain no infrastructure/repository access;
- persistence mappings retain exact immutable versions and provenance;
- migrations contain no fabricated data/backfill;
- Trading Core ownership and authentication cannot be bypassed;
- Market Intelligence internal contract cannot accept caller-selected Opportunity IDs or price values;
- transaction/idempotency behavior cannot create duplicate Observation, Opportunity, or Trade Plan versions;
- no risk-evaluation, execution-intent, broker, Gateway, or frontend changes entered the diff.

## Approval Required

Implementation Plan completed.

Awaiting explicit human approval before Implementation delegation.

---

## Approved Corrective Addendum — ADR-031 and Quality Tooling

The initial implementation attempt reached its mandatory stop condition because
the original ADR-027 `TradingContext` required financial facts that belong to
ADR-028. The human engineer subsequently approved ADR-031 and explicitly
authorized implementation to resume with SonarQube and JaCoCo integrated into
the engineering workflow.

This addendum supersedes every part of the plan that requires Market
Intelligence or Trading Core to construct the former financial
`TradingContext`. All other Story 0002 requirements remain in force.

### Corrected planning boundary

- Replace Market Intelligence `TradingContext` with `TradePlanningContext`.
- `TradePlanningContext` contains identity/version/timestamp, owner/account,
  account currency, exact `RiskBudget`, and exact versioned planning
  preferences only.
- Trading Core persists explicit immutable Trade Planning Profiles and
  account assignments. Missing configuration fails closed; migrations create
  schema only and seed no profile, assignment, amount, preference, or version.
- Position sizing uses the explicit monetary `RiskBudget.amount`; it no longer
  derives risk from account capital or a percentage of an inferred balance.
- Remove buying power, leverage, risk/rule profiles, exposure, prop-firm facts,
  and generic financial maps from the planning contract and risk handoff.
- Trading Core continues to assemble all ADR-028 financial snapshots
  independently when the exact accepted Trade Plan is later evaluated.
- Market Intelligence supplies only the immutable Trade Plan, its account and
  planning-context references, and complete intelligence/planning provenance.

### Trading Core configuration operations

Add authenticated account-owner operations to create a new immutable Trade
Planning Profile version and assign an effective version to an account. Use
typed contracts for risk budget and strategic preferences. Do not introduce
defaults that silently authorize planning.

The account-scoped Story 0002 continuation resolves the effective profile,
builds the exact `TradePlanningContext` snapshot, and sends it to Market
Intelligence. It must not read ADR-028 financial snapshots for plan generation.

### SonarQube and JaCoCo

Integrate quality tooling without creating a root Maven aggregator or changing
service independence:

1. Add a pinned JaCoCo Maven plugin configuration to every Java module so
   `verify` produces `target/site/jacoco/jacoco.xml`.
2. Configure Maven Sonar project keys per module and keep credentials external
   through `SONAR_HOST_URL` and `SONAR_TOKEN`.
3. Configure Angular coverage/LCOV and a frontend Sonar project without
   committing generated coverage or scanner output.
4. Add repository scripts/documentation for independent module verification
   and Sonar scans. Scripts must fail on test, scan, or Quality Gate failure.
5. Add a separate local SonarQube Community Build + PostgreSQL Compose file;
   do not couple quality infrastructure to application startup or reuse an
   application database.
6. Document an initial Clean-as-You-Code gate: no new high-severity bug or
   vulnerability, all new Security Hotspots reviewed, new-code coverage at
   least 80%, and new-code duplication at most 3%.
7. Record test, coverage, scan, and gate results in the Implementation Report.
   If no configured Sonar server/token is available, execute all local tests
   and coverage generation, validate scanner configuration where possible,
   and report the external scan as not executed rather than claiming success.

SonarQube supplements tests, ADR validation, architecture review, migration
review, and human Code Review; it never replaces them.

### Corrected implementation order

1. Implement and test ADR-031 domain and persistence changes.
2. Correct the Market Intelligence risk handoff and Story 0001 compatibility.
3. Resume Story 0002 Phase 1 through Phase 5 using the corrected context.
4. Integrate JaCoCo across Java modules and LCOV for Angular.
5. Add SonarQube local infrastructure, module configuration, scripts, and
   documentation.
6. Run each affected Maven suite independently, Angular checks, coverage
   generation, `docker compose config`, configured Sonar scans when available,
   and `git diff --check`.
7. Append the resumed implementation and exact validation results to
   `implementation-report.md`; preserve the original blocker history.

### Corrective approval

ADR-031, this corrective addendum, SonarQube integration, JaCoCo integration,
and resumption of Story 0002 implementation were explicitly approved by the
human engineer on 2026-08-01.
