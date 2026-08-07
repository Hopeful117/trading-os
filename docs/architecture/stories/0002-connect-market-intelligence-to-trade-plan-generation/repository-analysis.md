# Repository Analysis

## Story

Story 0002 — Connect Market Intelligence to Trade Plan Generation.

## Repository State

- Repository: `/home/ludo/Bureau/workspace/trading-os`
- Branch: `main`
- The repository is a Git repository.
- The Story directory exists and contains a readable approved `story.md`.
- Pre-existing working-tree changes were present before this analysis:
  - `.idea/compiler.xml` is modified;
  - `story.md` for Story 0002 is added and modified.
- No application source code was modified during Repository Analysis.

## Executive Summary

The requested production chain is not currently connected. Market Intelligence contains most of the required domain and application building blocks, but they belong to separate, partially overlapping stacks:

1. the public analysis API creates an `AnalysisExecution`, then dispatches to the legacy `IntelligenceOrchestrator` and legacy `AnalysisCapability` implementations;
2. the accepted ADR-023 stack (`ExecutionPlanner`, immutable DAG, `ExecutionEngine`, `CapabilityExecution`, `CapabilityResult`, artifact persistence) exists but has no production `Capability` implementation and is not invoked by the public flow;
3. `ObservationBuilder`, `OpportunityEngine`, `TradingContextRepository`, and `TradePlanApplicationService` exist, but no production application service sequences them after analysis completion;
4. Trade Plan creation is currently exposed only as a separate request that requires callers to already know opportunity identifiers and an existing Trading Context snapshot;
5. no production path creates or imports a `TradingContext` snapshot.

The smallest coherent architecture is to make the ADR-023 planner/engine the authoritative capability-execution stack, adapt the existing deterministic analyses to that contract, and add one post-analysis production pipeline coordinator. The coordinator must call the existing builders/engines rather than duplicate their rules.

Analysis completion may create global Observations and Opportunities automatically. Trade Plan generation must remain an explicit account-scoped continuation because ADR-027 requires a user-owned Trading Context. The continuation command must identify the completed analysis and provide authoritative context and market-price references; it must not infer a user, account, balance, or price from a global analysis.

An Implementation Plan is required before implementation.

## Current Production Paths

### Public analysis path

```text
POST /api/v1/intelligence/analyses
    -> MarketIntelligenceController
    -> AnalysisExecutionService.create
    -> LocalAnalysisExecutionDispatcher
    -> IntelligenceOrchestrator
    -> legacy AnalysisCapability implementations
    -> IntelligenceConsolidator
    -> AnalysisExecution.result
```

This path provides request idempotency and an asynchronous public execution lifecycle, but it bypasses the ADR-023 `ExecutionPlanner` and `ExecutionEngine`. Its capability executions are summary values embedded in `ConsolidatedIntelligence`; they are not the ADR-023 `CapabilityExecution` aggregates consumed by `ObservationBuilder`.

### ADR-023 capability path

```text
PlanningRequest
    -> ExecutionPlanner
    -> immutable ExecutionPlan / DAG
    -> ExecutionEngine
    -> CapabilityExecutionRepository
    -> ArtifactPersistencePort
```

This path implements the accepted execution model, including dependency planning, lifecycle, retries, cancellation, completeness, artifacts, and result provenance. It is tested in isolation but is not wired to the public analysis execution. No production class implements `domain.capability.Capability`, so the Spring `CapabilityRegistry` is empty.

### Observation path

`ObservationBuilder` is the sole observation-creation boundary. It:

- loads ADR-023 capability executions by analysis execution ID;
- accepts only `COMPLETED` executions with `COMPLETE` results;
- invokes a versioned `ObservationConsolidationRule`;
- requires evidence;
- traces evidence to capability results, artifacts, and raw market-data references;
- creates immutable observation versions and supersedes prior active observations.

No production consolidation rule is configured and no production workflow invokes the builder.

### Opportunity path

`OpportunityEngine` is already the exclusive creation/versioning boundary. It:

- resolves active observations;
- validates instrument consistency and AI references;
- delegates synthesis to `OpportunityFusionPolicy`;
- applies the existing deduplication window and logical identity;
- creates a new immutable Opportunity or appends a new version.

No production workflow constructs `CreateOpportunityCommand` from completed observations. A newly created Opportunity is initially `DETECTED`; Trade Planning accepts only `ACTIVE` Opportunities, so the production policy must explicitly perform valid lifecycle transitions rather than bypassing the lifecycle.

### Trade Planning path

`TradePlanningEngine` and `TradePlanApplicationService` already form the exclusive Trade Plan creation path. They:

- load the latest referenced Opportunity versions;
- require compatible active Opportunities;
- load and authorize an exact Trading Context snapshot;
- apply deterministic planning policies and optional validated AI contributions;
- create an immutable Trade Plan version;
- persist it through `TradePlanRepository`;
- publish lifecycle events and metrics.

`TradePlanController` exposes this path, but its caller must provide opportunity IDs, context ID/version, actor ID, and a market price. It does not connect a completed analysis to its derived Opportunities.

## Authoritative Orchestration Model

ADR-023 is authoritative for capability planning and execution. The legacy `IntelligenceOrchestrator` must not remain as a second production capability scheduler after this Story.

The recommended incremental transition is:

1. preserve `AnalysisExecutionService` as the public analysis lifecycle and idempotency boundary;
2. replace the dispatcher's use of `IntelligenceOrchestrator` with an application coordinator that invokes `ExecutionPlanner` and `ExecutionEngine`;
3. adapt the current deterministic analysis behaviors to ADR-023 `Capability` contracts, with all dependencies represented as artifacts;
4. derive the public consolidated result from accepted ADR-023 executions instead of running a parallel analysis stack;
5. invoke a separate post-analysis intelligence-pipeline coordinator only after successful completion criteria are satisfied.

The legacy types may remain temporarily for response compatibility if necessary, but they must not independently schedule or execute production capabilities.

## Proposed End-to-End Flow

```text
Idempotent analysis request
    -> AnalysisExecutionService
    -> ADR-023 ExecutionPlanner
    -> ADR-023 ExecutionEngine
    -> complete CapabilityExecutions + persisted Artifacts
    -> versioned ObservationConsolidationRule(s)
    -> ObservationBuilder
    -> ObservationRepository
    -> deterministic OpportunityProjectionPolicy
    -> OpportunityEngine
    -> Opportunity lifecycle to ACTIVE when eligible
    -> pipeline outcome persisted against AnalysisExecution

Explicit account-scoped planning continuation
    -> completed AnalysisExecution + derived Opportunity references
    -> exact authoritative TradingContext snapshot
    -> timestamped authoritative market-price fact
    -> TradePlanApplicationService
    -> immutable persisted Trade Plan candidate/version
```

This satisfies the Story without triggering risk evaluation or execution. It also preserves ADR-026's global Opportunity model and ADR-027's account-specific Trade Plan model.

## Ownership of Required Facts and Decisions

| Fact or decision | Authority | Existing source | Required change |
| --- | --- | --- | --- |
| Analysis identity, provenance, mode, objective, idempotency | Market Intelligence | `AnalysisExecutionService` / `AnalysisExecution` | Preserve and extend completion outcome references |
| Capability selection and dependencies | Market Intelligence ADR-023 planner | `ExecutionPlanner`, `CapabilityRegistry` | Make it the production path |
| Capability lifecycle and accepted results | Market Intelligence ADR-023 engine | `ExecutionEngine`, `CapabilityExecutionRepository` | Wire to public execution |
| Artifacts and provenance | Producing capability coordinated by Market Intelligence | `ArtifactPersistencePort`, artifact domain | Use ADR-023 artifacts in production |
| Observation eligibility and evidence | Deterministic Market Intelligence rule | `ObservationConsolidationRule`, `ObservationBuilder` | Add explicit versioned rules and orchestration |
| Opportunity synthesis, identity, deduplication, versioning | `OpportunityEngine` and its policies | Existing application/domain services | Add command projection and lifecycle orchestration only |
| Trading Context financial/account facts | User-specific authoritative service; currently not exposed to this flow | No production Market Intelligence assembler | Add/import an explicit authoritative snapshot contract; never infer facts |
| Market price used for planning | Market Data | Existing Market Data snapshot API/client patterns | Acquire or carry a timestamped price fact, not a bare untraceable number |
| Trade Plan policies and creation | `TradePlanningEngine` | Existing Trade Planning stack | Reuse unchanged business rules |
| Trade Plan persistence/versioning | Market Intelligence | JPA `TradePlanRepository` | Reuse |
| Risk decision | Trading Core + Risk Domain | Story 0001 flow | Must not be called by Story 0002 |

## Required Contract Changes

Exact names may be finalized in the Implementation Plan, but the responsibilities are constrained as follows.

### Mandatory Market Intelligence contracts

1. **Authoritative analysis execution coordinator**
   - accepts the current `AnalysisExecution` and `IntelligenceAnalysisRequest`;
   - constructs the ADR-023 `PlanningRequest` from declared capabilities and initial context artifacts;
   - invokes `ExecutionPlanner` then `ExecutionEngine`;
   - completes the existing public `AnalysisExecution` with a response-compatible consolidated result;
   - never re-executes capabilities through the legacy orchestrator.

2. **Versioned observation-production policy/registry**
   - maps eligible complete capability results to one or more `ObservationConsolidationRule` instances;
   - returns an explicit no-observation outcome when results are valid but do not express a signal;
   - rejects failed, skipped, timed-out, cancelled, partial, or provenance-invalid inputs;
   - preserves the rule version in each Observation.

3. **Opportunity projection policy**
   - deterministically maps produced Observations to `CreateOpportunityCommand` values;
   - owns only projection/default selection, not Opportunity construction, deduplication, or versioning;
   - invokes `OpportunityEngine` and its lifecycle API;
   - records whether an Opportunity was created, versioned, reused, or not eligible.

4. **Post-analysis pipeline service and outcome**
   - keyed by `analysisExecutionId`;
   - sequences observation and opportunity production after capability completion;
   - records stage-specific controlled failures and created immutable references;
   - supports idempotent replay without uncontrolled duplicates.

5. **Analysis-to-planning continuation command**
   - references one completed analysis execution;
   - resolves only Opportunities derived from that execution;
   - carries actor identity, exact Trading Context ID/version, and an authoritative market-price reference/value;
   - invokes `TradePlanApplicationService` exactly once per idempotency key;
   - returns the persisted Trade Plan identity/version or a structured controlled failure.

6. **Trace queries/repository extensions**
   - capability executions must be queryable by analysis execution ID (already present);
   - observations must be queryable or indexed by analysis/capability evidence;
   - opportunities must expose exact observation-version lineage rather than only allowing latest-by-ID lookup;
   - the pipeline outcome must retain analysis -> observations -> opportunity -> Trade Plan references.

### Capability contract migration

The current `SpreadAnalysisCapability` and `OhlcRangeAnalysisCapability` implement the legacy interface. They require ADR-023-compatible production adapters or migrations that:

- declare stable capability ID and version;
- declare execution policy, requirements, produced contributions, timeout, and retry policy;
- consume only `CapabilityContext` artifacts;
- emit `CapabilityResult` contributions/artifacts with complete provenance;
- do not call repositories or infrastructure.

Market-data context contributors should remain infrastructure adapters that acquire normalized source facts and materialize initial artifacts. They must not be hidden inside a Capability.

### Trading Context boundary

No existing API creates a production `TradingContext`. A new boundary is mandatory before an end-to-end Trade Plan can be generated. The smallest boundary should import an immutable, versioned snapshot from the user-specific authority and persist it through `TradingContextRepository`. It must include the ADR-027 fields already modeled and enforce actor/account ownership.

The implementation must not reuse ADR-028 risk snapshots as if they were ADR-027 planning snapshots without an explicit mapping contract: their purposes and completeness rules differ. Existing Story 0001 financial facts may be reused behind a dedicated read contract where semantically equivalent.

### Market Data contract

Market Intelligence already has a `MarketDataClient` for market context. Planning currently accepts a bare `BigDecimal marketPrice`; end-to-end traceability requires the coordinator to retain the source market, observation timestamp, captured timestamp/version, and price used. Prefer extending/reusing the existing normalized price snapshot contract over adding a second Market Data endpoint.

No Market Data change is required if its current snapshot contract already exposes the required instrument, price, timestamp, and stable source/version information after exact inspection during planning.

## Persistence Analysis

Current adapters are mixed:

- analysis executions: in-memory;
- ADR-023 capability executions: in-memory;
- ADR-023 artifacts: in-memory;
- observations: in-memory;
- opportunities: in-memory;
- Trading Context snapshots: in-memory;
- Trade Plan versions and risk acknowledgments: PostgreSQL/JPA.

The Story explicitly requires persisted observations and a traceable persisted Trade Plan, while allowing the minimum coherent increment rather than wholesale persistence replacement.

Mandatory persistence work:

- persist an idempotent pipeline-run/outcome record keyed by analysis execution and pipeline version;
- preserve exact references from pipeline run to produced Observation IDs/versions, Opportunity ID/version, Trading Context ID/version, market-price provenance, and Trade Plan ID/version;
- provide uniqueness constraints preventing duplicate stage effects for the same analysis and policy version;
- persist/import Trading Context snapshots used by generated Trade Plans, because durable Trade Plans already reference them and process-local context currently breaks restart-safe retrieval;
- ensure repository operations preserve immutable version semantics.

For the minimum implementation, existing repository abstractions may remain the application-facing contracts. However, leaving analysis executions, artifacts, observations, or opportunities purely process-local weakens restart-safe end-to-end traceability. The Implementation Plan must explicitly select which of these stores become JPA-backed now. At minimum, every durable Trade Plan reference must remain resolvable after restart; otherwise the acceptance criterion is not durably satisfied.

## Migration Implications

Likely Market Intelligence Flyway additions include:

- immutable Trading Context snapshot tables;
- pipeline run/stage outcome and idempotency tables;
- lineage tables or columns connecting analysis executions, observations, opportunities, and Trade Plan versions;
- uniqueness constraints for analysis/pipeline-policy replay;
- potentially durable analysis execution, capability execution, artifact, observation, and opportunity tables if required by the approved persistence cut.

Migrations must be schema-only. They must not seed or infer user IDs, account IDs, balances, prices, context versions, observation identities, opportunity identities, or provenance.

Existing `trade_plan_versions` data must remain compatible. Any new foreign key from a Trade Plan to a durable Trading Context requires an additive migration strategy that accounts for existing rows without fabricating missing contexts. If existing local/development rows cannot be proven to have resolvable contexts, the new constraint must be staged rather than backfilled with synthetic data.

Market Data requires a migration only if price provenance is not already durably represented by its current snapshot model. Trading Core should not require a migration for this Story unless the approved context-read contract needs an additive projection over existing Story 0001 facts.

## Idempotency and Failure Semantics

Idempotency must be stage-aware, not based only on the existing analysis request key.

- Reusing an `AnalysisExecution` must reuse its accepted capability results.
- Observation replay with the same analysis, rule version, and evidence set must return the existing observation/version rather than superseding it again.
- Opportunity replay must use existing deduplication/versioning but must not append a new version solely because the same pipeline run was retried.
- Trade Plan continuation replay must return the same persisted Trade Plan version for the same analysis, context version, market-price snapshot, policy set, and idempotency key.
- A changed context, price snapshot, policy version, or explicitly requested replan is a new planning input and may create a new immutable version.

Controlled stage outcomes should distinguish at least:

- analysis incomplete or not successful;
- no eligible complete capability result;
- no observation signal;
- observation provenance invalid;
- no eligible opportunity;
- opportunity not active;
- Trading Context missing, stale, unauthorized, or inconsistent;
- market-price snapshot missing or stale;
- planning policy conflict or insufficient data;
- persistence conflict or dependency unavailable.

No failure may trigger risk evaluation, execution, or a fail-open Trade Plan.

## Security and Service Boundaries

- Global market analysis, Observations, and Opportunities may be reusable across users as defined by ADR-026.
- Trading Context and Trade Plans are user/account specific under ADR-027.
- Actor identity must come from an authenticated boundary in the production flow, not be trusted solely from a request body. The current `TradePlanController` accepts `actorId` directly and is not a sufficient production authorization boundary for the new continuation.
- Market Intelligence must not call Broker Service directly.
- If Trading Core exposes planning-context facts, Market Intelligence must use an explicit internal broker-neutral read contract with service authentication and ownership enforcement.
- Risk evaluation and broker execution remain out of scope and must not be invoked.

## Required Tests

### Focused unit tests

- legacy deterministic behavior migrated/adapted to ADR-023 Capability contracts;
- capability metadata, artifact requirements, completeness, and provenance;
- observation rule eligibility and evidence mapping;
- failed, incomplete, skipped, timed-out, and cancelled executions create no Observations;
- deterministic observation replay does not create a duplicate version;
- observation-to-opportunity projection;
- Opportunity lifecycle eligibility;
- existing Opportunity logical deduplication and immutable versioning;
- pipeline stage failure classification and short-circuiting;
- Trading Context ownership/version validation;
- market-price freshness/provenance validation;
- Trade Planning invocation and immutable persistence;
- full pipeline idempotent replay.

### Integration tests

- public completed analysis through ADR-023 execution to persisted Observation references;
- completed analysis to active Opportunity;
- explicit account-scoped continuation to persisted Trade Plan;
- exact trace traversal from Trade Plan to context, opportunity, observations, capability executions, artifacts, analysis, and raw market-data references;
- restart/durable repository behavior for every reference persisted by a Trade Plan;
- concurrent duplicate pipeline requests produce one logical result;
- failed intermediate stage produces a controlled outcome and no downstream side effects;
- no call to Trading Core risk evaluation or Broker execution boundaries.

### Regression validation

- complete Market Intelligence Maven test suite;
- Market Data Maven tests only if its contract changes;
- Trading Core Maven tests only if its planning-context read contract changes;
- `git diff --check`.

Each Maven service must be tested independently.

## Implementation Order

1. Finalize the production trigger semantics and authoritative Trading Context acquisition contract.
2. Define pipeline outcome, lineage, idempotency, and controlled-failure contracts.
3. Add required persistence mappings and additive migrations.
4. Adapt/migrate deterministic analyses and context inputs to ADR-023 contracts.
5. Replace the public dispatcher's legacy scheduling with ADR-023 planning/execution while preserving public execution lifecycle and response compatibility.
6. Add versioned Observation consolidation rules and connect `ObservationBuilder`.
7. Add deterministic Opportunity projection, reuse `OpportunityEngine`, and apply valid lifecycle transitions.
8. Add the authenticated account-scoped analysis-to-planning continuation and acquire exact context/price snapshots.
9. Invoke `TradePlanApplicationService`, persist lineage/outcome, and enforce idempotent replay.
10. Add focused and end-to-end tests, run affected Maven suites, and run `git diff --check`.

## Mandatory Work vs Optional Improvements

### Mandatory for Story 0002

- one authoritative ADR-023 capability execution path;
- production ADR-023 capabilities/adapters for the currently active deterministic analyses;
- completed-result eligibility and versioned Observation production;
- Observation-to-Opportunity projection through existing engines;
- valid Opportunity lifecycle handling;
- explicit account-scoped planning continuation;
- authoritative Trading Context and timestamped market-price inputs;
- Trade Plan creation exclusively through the existing Trade Planning application service;
- stage-aware idempotency and trace lineage;
- persistence sufficient for every durable Trade Plan reference to remain resolvable;
- controlled failures and required tests.

### Optional follow-up improvements

- distributed messaging or durable asynchronous queues;
- passive scanner scheduling;
- real AI planning or AI analysis providers;
- frontend and Gateway exposure;
- generalized workflow engine abstraction;
- replacement of every in-memory adapter not required for durable lineage;
- historical backfill of analyses that predate the connected pipeline;
- automatic generation of account-specific Trade Plans for every matching user account.

## Risks

- **Dual orchestration:** retaining both production schedulers would violate ADR-023 and create divergent results.
- **No registered ADR-023 capabilities:** simply wiring the engine without migrating capabilities produces an empty execution, not a working flow.
- **Global/account boundary:** automatically inventing an actor or account from a global analysis violates ADR-026/027.
- **Ephemeral lineage:** durable Trade Plans referencing in-memory context/opportunity data become non-reproducible after restart.
- **Lifecycle bypass:** directly marking an Opportunity active or constructing a Trade Plan outside existing engines would bypass domain rules.
- **Replay-induced versions:** existing observation and opportunity versioning can create unnecessary versions unless pipeline-level idempotency precedes domain invocation.
- **Bare price input:** retaining only a decimal market price loses source and temporal traceability.
- **Security:** trusting body-supplied actor identity enables cross-account planning.
- **Scope expansion:** trying to solve scanning, AI, risk, execution, Gateway, and frontend concerns would obscure the minimum production path.

## Decision Requiring Human Approval

One product/API decision is not fixed by the accepted ADRs:

**Should Trade Plan generation be an explicit account-scoped continuation of a completed global analysis (recommended), or should the initial analysis request itself be made account-scoped and automatically generate a Trade Plan?**

The recommended explicit continuation preserves reusable global Observations/Opportunities, avoids coupling every analysis to one account, allows the caller to choose an exact Trading Context, and aligns with ADR-026 and ADR-027. It still allows a completed analysis to trigger the full pipeline through one dedicated application command.

The exact source contract for Trading Context can be designed in the Implementation Plan from existing Trading Core/account facts, but no implementation may proceed until the trigger choice is approved.

## Recommendation

**Ready for Implementation Planning after approval of the explicit continuation model.**

The architecture is coherent and preserves accepted service boundaries. The work is not safe to implement directly from the Story because it requires coordinated contract migration, persistence decisions, idempotency design, security enforcement, and replacement of the currently authoritative runtime scheduler. A detailed Implementation Plan is mandatory.

## Approval Required

Repository Analysis completed.

Awaiting human approval before Implementation Planning.
