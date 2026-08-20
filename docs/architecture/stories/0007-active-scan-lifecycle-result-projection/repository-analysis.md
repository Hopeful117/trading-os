# Repository Analysis — Story 0007

## Story Overview

- **Story ID:** `0007`
- **Title:** Persistent Active Scan Lifecycle Reconciliation & Result Projection
- **Status:** Draft
- **Location:** `docs/architecture/stories/0007-active-scan-lifecycle-result-projection/story.md`

## DevLog Context

### Retrieval Path

- `DEVLOG_CONFIRMED`: native OpenClaw/Kiko MCP path via
  `mcp__devlog.get_engineering_context`

### Retrieval Metadata

- `candidateCount`: `136`
- `selectedCount`: `60`
- `usedTokens`: `3363`
- `contextDigest`: `aadbcc6c86a07dc4540ff0db95d7400ab89ae3afd1b74577621e1c8905d6a9aa`
- `truncated`: `true`

### Selected DevLog Evidence

- `DEVLOG_CONFIRMED`: DevLog surfaced the foundational market-intelligence
  commit `5fda25d745aee11370fc9db50112b74299d34845`, which aligns with the
  repository reality that active and passive orchestration already share the
  same analytical primitives.
- `DEVLOG_CONFIRMED`: DevLog surfaced broad ADR and story history around
  deterministic orchestration and architecture documentation.
- `DEVLOG_CONFIRMED`: DevLog returned current-analysis context explicitly
  focused on Story 0007 preparation.
- `UNDER_REPRESENTED`: DevLog did not prominently surface Story 0005, Story
  0006 or ADR-033 as top selected evidence despite their direct relevance.
- `INFERENCE`: DevLog remains useful for historical context, but current
  repository code and approved Story artifacts remain authoritative for Story
  0007 design.

## Governing Invariants

### ADR-033

- `REPOSITORY_CONFIRMED`: Active Scanner is intention-driven orchestration,
  not a second analysis engine.
- `REPOSITORY_CONFIRMED`: `ActiveScan` exists above single-market
  `AnalysisExecution`.
- `REPOSITORY_CONFIRMED`: `AnalysisExecution` remains single-market.
- `REPOSITORY_CONFIRMED`: `PipelineRun` remains one-analysis provenance.
- `REPOSITORY_CONFIRMED`: cross-market ranking must not be invented from
  `OpportunityScore`.
- `REPOSITORY_CONFIRMED`: Active/Passive distinction belongs to orchestration
  semantics, not duplicated market-analysis implementation.

### Story 0005

- `REPOSITORY_CONFIRMED`: Story 0005 owns deterministic account-aware scope
  resolution.
- `REPOSITORY_CONFIRMED`: requested markets, candidate markets, eligibility
  decisions and effective scope are already separated and persisted into the
  Story 0006 scan snapshot.
- `REPOSITORY_CONFIRMED`: Story 0007 must not rerun or reinterpret market
  eligibility rules.

### Story 0006

- `REPOSITORY_CONFIRMED`: Story 0006 owns durable `ActiveScan` creation,
  `ActiveScanMarket` persistence, actor-scoped idempotency, request
  fingerprinting, child `AnalysisExecution` registration, durable linkage,
  after-commit dispatch and crash-safe retry foundation.
- `REPOSITORY_CONFIRMED`: Story 0006 intentionally stopped before lifecycle
  reconciliation, progress projection, result projection and cancellation.

## Current ActiveScan Model

### Domain Aggregate

Source:

- `market-intelligence/.../domain/scan/ActiveScan.java`

`REPOSITORY_CONFIRMED`: `ActiveScan` currently persists:

- `scanId`
- `actorId`
- `accountId`
- `objective`
- `idempotencyKey`
- `requestFingerprint`
- `scopeSnapshot`
- `status`
- `resolvedAt`
- `createdAt`
- `updatedAt`

### Current Lifecycle

Source:

- `market-intelligence/.../domain/scan/ActiveScanStatus.java`

`REPOSITORY_CONFIRMED`: current statuses are exactly:

- `READY_TO_DISPATCH`
- `DISPATCH_REQUESTED`
- `COMPLETED_NO_WORK`

`REPOSITORY_CONFIRMED`: only `COMPLETED_NO_WORK` is terminal today.

### Current Status Semantics

`REPOSITORY_CONFIRMED`:

- `READY_TO_DISPATCH`
  - persisted eligible scan exists;
  - dispatch phase has not yet been durably started.
- `DISPATCH_REQUESTED`
  - at least one eligible child has been durably claimed for dispatch.
- `COMPLETED_NO_WORK`
  - effective scope contained zero eligible markets.

`REPOSITORY_CONFIRMED`: Story 0006 does not yet expose aggregate child
resolution semantics.

## Current ActiveScanMarket Model

Source:

- `market-intelligence/.../domain/scan/ActiveScanMarket.java`
- `market-intelligence/.../domain/scan/ActiveScanMarketStatus.java`

`REPOSITORY_CONFIRMED`: `ActiveScanMarket` currently persists:

- `scanMarketId`
- `scanId`
- `ordinal`
- `marketId`
- `eligible`
- `exclusionReasons`
- `status`
- `analysisExecutionId`
- `createdAt`
- `updatedAt`

`REPOSITORY_CONFIRMED`: current market statuses are exactly:

- `EXCLUDED`
- `REGISTERED`
- `DISPATCH_REQUESTED`

`REPOSITORY_CONFIRMED`:

- excluded markets have no `analysisExecutionId`;
- eligible markets always have an `analysisExecutionId`;
- ordering is deterministic through `ordinal`.

## Current Orchestration Chain

Sources:

- `MarketIntelligenceController`
- `ActiveScanApplicationService`
- `ActiveScanDispatchCoordinator`
- `ActiveScanDispatchClaimService`
- `AnalysisExecutionService`

`REPOSITORY_CONFIRMED`: current flow is:

`POST /api/v1/intelligence/scans`  
↓  
`MarketIntelligenceController.createScan(...)`  
↓  
actor header validation  
↓  
`ActiveScanApplicationService.create(...)`  
↓  
Story 0005 scope resolution  
↓  
persist `ActiveScan`  
↓  
persist `ActiveScanMarket` rows  
↓  
register/reuse eligible child `AnalysisExecution(ACTIVE)`  
↓  
commit T1  
↓  
`afterCommit()` delegates to `ActiveScanDispatchCoordinator.resume(scanId)`  
↓  
`ActiveScanDispatchClaimService.claimForDispatch(...)` in `REQUIRES_NEW`  
↓  
eligible child `REQUESTED -> ACCEPTED` claim  
↓  
scan-market `REGISTERED -> DISPATCH_REQUESTED` claim  
↓  
optional scan `READY_TO_DISPATCH -> DISPATCH_REQUESTED`  
↓  
`AnalysisExecutionService.dispatchRegistered(...)`

`REPOSITORY_CONFIRMED`: current read endpoint is actor-owned GET on the same
controller, but it returns a mostly raw persisted view rather than a reconciled
projection.

## Current GET Shape

Sources:

- `MarketIntelligenceController.findScan(...)`
- `ActiveScanApplicationService.findOwned(...)`
- `ActiveScanResponse`

`REPOSITORY_CONFIRMED`: current GET loads:

- one `ActiveScan` by `(actorId, scanId)`;
- ordered `ActiveScanMarket` rows by `scanId`;
- no linked `AnalysisExecution` rows;
- no `PipelineRun`;
- no `TradingOpportunity`;
- no progress counters;
- no per-market outcome classification beyond raw scan-market status.

`REPOSITORY_CONFIRMED`: current response exposes:

- scan identity and actor/account fields;
- scan status;
- idempotency key;
- requested/candidate/effective markets;
- timestamps;
- raw market rows with `eligible`, `status`, `analysisExecutionId`,
  `exclusionReasons`.

`INFERENCE`: Story 0007 should enrich this actor-owned GET rather than adding a
  second result surface immediately.

## AnalysisExecution Lifecycle

Sources:

- `AnalysisExecutionStatus`
- `AnalysisExecution`
- `LocalAnalysisExecutionDispatcher`
- `IntelligenceConsolidator`

### Current Statuses

`REPOSITORY_CONFIRMED`: child execution statuses are exactly:

- `REQUESTED`
- `ACCEPTED`
- `CONTEXT_BUILDING`
- `RUNNING`
- `PARTIALLY_COMPLETED`
- `COMPLETED`
- `FAILED`
- `CANCELLED`
- `EXPIRED`

### Current Transitions

`REPOSITORY_CONFIRMED`:

- `REQUESTED -> ACCEPTED | FAILED | CANCELLED | EXPIRED`
- `ACCEPTED -> CONTEXT_BUILDING | FAILED | CANCELLED | EXPIRED`
- `CONTEXT_BUILDING -> RUNNING | FAILED | CANCELLED | EXPIRED`
- `RUNNING -> PARTIALLY_COMPLETED | COMPLETED | FAILED | CANCELLED | EXPIRED`
- `PARTIALLY_COMPLETED -> RUNNING | COMPLETED | FAILED | CANCELLED | EXPIRED`

### Terminal States

`REPOSITORY_CONFIRMED`:

- `COMPLETED`
- `FAILED`
- `CANCELLED`
- `EXPIRED`

### Important Semantic Observations

- `REPOSITORY_CONFIRMED`: `LocalAnalysisExecutionDispatcher` currently moves a
  normally finished execution to `COMPLETED`, even when the consolidated
  intelligence status is `FAILED`; in that case `resultQuality` becomes
  `DEGRADED` and the downstream pipeline is skipped.
- `REPOSITORY_CONFIRMED`: `AnalysisExecution.FAILED` today primarily means
  execution/runtime failure, not simply "no trading signal" and not every
  degraded analytical result.
- `REPOSITORY_CONFIRMED`: `AnalysisExecution.PARTIALLY_COMPLETED` exists in the
  domain, but the current local dispatcher path does not actively persist that
  state for normal Active Scan flow.
- `INFERENCE`: Story 0007 cannot derive truthful scan semantics from
  `AnalysisExecution.status` alone.

## Consolidated Intelligence Truth

Sources:

- `ConsolidatedIntelligence`
- `IntelligenceExecutionStatus`
- `IntelligenceConsolidator`

`REPOSITORY_CONFIRMED`: consolidated analytical result status is:

- `COMPLETE`
- `PARTIAL`
- `DEGRADED`
- `FAILED`

`REPOSITORY_CONFIRMED`:

- `FAILED` means no completed capability result, no findings and effectively
  unusable analytical output;
- `PARTIAL` / `DEGRADED` are still analytical outputs, not infrastructure
  failures.

`INFERENCE`: usable scan result classification should distinguish:

- execution terminality;
- analytical result usability;
- downstream pipeline outcome.

## PipelineRun Relationship

Sources:

- `ProductionIntelligencePipeline`
- `JpaIntelligencePipelineRunEntity`
- `JpaIntelligencePipelineRunRepository`

`REPOSITORY_CONFIRMED`:

- one pipeline run is persisted per `(analysisExecutionId, pipelineVersion)`;
- `PipelineRun` remains downstream provenance for one `AnalysisExecution`;
- pipeline states currently include:
  - `RUNNING`
  - `COMPLETED`
  - `COMPLETED_NO_SIGNAL`
  - `FAILED_OBSERVATION`
  - `FAILED_OPPORTUNITY`

### No-Signal Semantics

`REPOSITORY_CONFIRMED`: when observation building concludes that no signal
should be produced, the pipeline persists:

- `state = COMPLETED_NO_SIGNAL`
- `failureCode = NO_SIGNAL`

This is not an orchestration failure.

`REPOSITORY_CONFIRMED`: `AnalysisExecution` may therefore be successfully
completed while `PipelineRun` truthfully says "completed with no opportunity".

## Opportunity Lineage

Sources:

- `ProductionIntelligencePipeline`
- `JpaIntelligencePipelineRunEntity`
- `TradingOpportunityRepository`
- `TradingOpportunity`

`REPOSITORY_CONFIRMED`:

- opportunities are created by `OpportunityEngine` inside the production
  pipeline, not by `ActiveScan`;
- `JpaIntelligencePipelineRunEntity` stores `opportunityId` and
  `opportunityVersion`;
- `TradingOpportunityRepository` can resolve a latest or exact-version
  opportunity by id/version;
- one `AnalysisExecution` can produce zero or one opportunity in the current
  production pipeline path;
- zero opportunities is a normal outcome;
- current repository code does not persist `opportunityId` on
  `ActiveScanMarket`.

### Cardinality Finding

- `REPOSITORY_CONFIRMED`: zero opportunities per child is valid.
- `REPOSITORY_CONFIRMED`: one opportunity per child is the current production
  path.
- `INFERENCE`: Story 0007 should project opportunity lineage through
  `PipelineRun` and not duplicate `opportunityId` onto `ActiveScanMarket`.

## Success / Failure / In-Flight Classification for Scan Reconciliation

### Success-Class

- `PROPOSED`: `AnalysisExecution` terminal with usable analytical result and a
  pipeline state of either:
  - `COMPLETED`
  - `COMPLETED_NO_SIGNAL`
- `PROPOSED`: `COMPLETED_NO_SIGNAL` must be exposed as successful
  no-opportunity analysis, not failure.

### Failure-Class

- `PROPOSED`: child execution status `FAILED`
- `PROPOSED`: child execution status `CANCELLED`
- `PROPOSED`: child execution status `EXPIRED`
- `PROPOSED`: child execution `COMPLETED` with consolidated result status
  `FAILED`
- `PROPOSED`: pipeline states `FAILED_OBSERVATION` and `FAILED_OPPORTUNITY`

### In-Flight

- `PROPOSED`: `REQUESTED`
- `PROPOSED`: `ACCEPTED`
- `PROPOSED`: `CONTEXT_BUILDING`
- `PROPOSED`: `RUNNING`
- `PROPOSED`: `PARTIALLY_COMPLETED`
- `PROPOSED`: any missing downstream lineage where the child is not terminal

`INFERENCE`: Story 0007 requires a child truth classifier rather than a simple
status passthrough.

## Lifecycle Source-of-Truth Options

### Option A — ActiveScan Status as Independent Truth

- `POOR_FIT`
- `INFERENCE`: this would duplicate child state and risks contradiction after
  crash/retry or delayed child completion.

### Option B — Derive Everything Only on GET Without Persisted Scan Status

- `ACCEPTABLE_BUT_WEAKER`
- `INFERENCE`: technically possible, but worse for cheap polling and aggregate
  readability, and it would underuse the aggregate boundary already introduced
  by Story 0006.

### Option C — Hybrid Persisted Aggregate Status Reconciled from Child Truth

- `RECOMMENDED`
- `REPOSITORY_CONFIRMED`: current aggregate already persists status.
- `INFERENCE`: Story 0007 should preserve that field as the trader-facing
  aggregate lifecycle while reconciling it from persisted child truth and
  persisting only forward-only transitions.

## Reconciliation Model Options

### Option A — Synchronous Reconciliation on GET

- `GOOD_FIT`
- `REPOSITORY_CONFIRMED`: current service topology is single-service and
  same-database for scan, child execution and pipeline provenance.
- `REPOSITORY_CONFIRMED`: GET already has actor ownership boundary and can be
  enriched without adding new orchestration infrastructure.
- `INFERENCE`: this is the smallest production-compatible V1.

### Option B — Domain Events from AnalysisExecution

- `OVERKILL`
- `INFERENCE`: no current event-driven infrastructure is needed for Story
  0007.

### Option C — Scheduler / Reconciliation Worker

- `OVERKILL`
- `INFERENCE`: not needed to make scan polling truthful in the current
  architecture.

### Option D — Hybrid Event + Read Repair

- `FUTURE`
- `INFERENCE`: may become useful later, but premature for the current scope.

## Recommended Reconciliation Model

- `PROPOSED`: actor-owned synchronous read-side reconciliation on
  `GET /api/v1/intelligence/scans/{scanId}`
- `PROPOSED`: load all required persisted child truth
- `PROPOSED`: compute deterministic per-market outcomes
- `PROPOSED`: compute aggregate progress and lifecycle
- `PROPOSED`: persist only forward-only scan status transitions when the newly
  derived status is strictly later than the currently persisted aggregate
  status
- `PROPOSED`: keep reconciliation idempotent for unchanged persisted child
  truth

## Forward-Only Lifecycle Risk

`REPOSITORY_CONFIRMED`: current repository already supports atomic status
transitions through `transitionScanStatus(...)` and `transitionMarketStatus(...)`.

`INFERENCE`: Story 0007 can follow the same pattern for forward-only scan
status transitions and avoid status regression after a scan becomes terminal.

Important case:

- `REPOSITORY_CONFIRMED`: `READY_TO_DISPATCH` may still legitimately exist
  after creation and before any child claim starts, including after a crash
  before after-commit dispatch;
- `INFERENCE`: GET reconciliation must preserve that possibility and should not
  force `RUNNING` until child truth proves dispatch/execution has actually
  progressed.

## API Surface

### Current

- `REPOSITORY_CONFIRMED`: `POST /api/v1/intelligence/scans`
- `REPOSITORY_CONFIRMED`: `GET /api/v1/intelligence/scans/{scanId}`

### Story 0007 Direction

- `PROPOSED`: enrich the existing GET with lifecycle summary, progress and
  per-market result projection.
- `PROPOSED`: defer a separate `/results` endpoint unless the response becomes
  too large or semantically split later.

## Security Boundary

`REPOSITORY_CONFIRMED`:

- Active Scan endpoints are actor-owned through `X-Actor-Id` propagated by the
  authenticated Gateway;
- `market-intelligence` is no longer normally host-exposed on `8084`;
- GET ownership currently resolves by `(actorId, scanId)` and returns `404`
  for foreign actors.

`INFERENCE`: Story 0007 result enrichment must remain under this same trust
boundary and must not create any new bypass path.

## Persistence / Migration Assessment

### Existing Schema Support

Source:

- `V3__active_scan_orchestration_foundation.sql`

`REPOSITORY_CONFIRMED`:

- `ActiveScan.status` is persisted as `VARCHAR(40)`;
- `ActiveScanMarket.status` is persisted as `VARCHAR(40)`;
- progress counters are not persisted;
- scope snapshot already lives in JSON payload;
- opportunity linkage already exists through `intelligence_pipeline_runs`.

### Migration Verdict

- `PROPOSED`: `NO_MIGRATION_REQUIRED`

Justification:

- adding new `ActiveScanStatus` enum values does not require a schema change;
- Story 0007 should derive progress and opportunity projection instead of
  persisting redundant counters or payload copies;
- current lineage already stores opportunity references on pipeline runs.

## Query / N+1 Assessment

### Current Risk

`REPOSITORY_CONFIRMED`:

- current GET only loads scan + ordered scan markets;
- there is no existing batch read API for:
  - scan-linked `AnalysisExecution`s;
  - pipeline runs by multiple `analysisExecutionId`s;
  - opportunities by multiple ids/versions.

### Story 0007 Impact

`INFERENCE`: a naive implementation could produce polling-time N+1 patterns:

- one query for scan;
- one query for markets;
- N queries for child executions;
- N queries for pipeline runs;
- N queries for opportunities.

### Recommendation

- `PROPOSED`: add batch/scoped read seams for child executions and pipeline
  lineage.
- `PROPOSED`: add an efficient opportunity resolution seam for the projected
  subset needed by one scan.
- `PROPOSED`: keep ordering deterministic via persisted `ordinal`.

## Compatibility Risks

- `REPOSITORY_CONFIRMED`: `AnalysisExecution.PARTIALLY_COMPLETED` has different
  semantics from the approved scan-level `PARTIALLY_COMPLETED`; Story 0007 must
  document and isolate that difference explicitly.
- `REPOSITORY_CONFIRMED`: `AnalysisExecution.COMPLETED` alone does not prove an
  opportunity exists.
- `REPOSITORY_CONFIRMED`: `OpportunityScore` is explicitly a business priority
  from 0 to 100 and not a probability of success.
- `INFERENCE`: exposing raw internal statuses directly would mislead traders and
  therefore violates Story 0007 user value.

## Story 0007 Design Recommendation

- `PROPOSED`: extend `ActiveScanStatus` with `RUNNING`,
  `PARTIALLY_COMPLETED`, `COMPLETED`, `FAILED`.
- `PROPOSED`: keep `COMPLETED_NO_WORK` unchanged.
- `PROPOSED`: do not introduce `CANCELLING` / `CANCELLED` in this Story.
- `PROPOSED`: use synchronous actor-owned GET reconciliation as the V1 model.
- `PROPOSED`: project opportunities through `PipelineRun` lineage.
- `PROPOSED`: treat no-opportunity as a valid successful analysis outcome.
- `PROPOSED`: defer cancellation to Story 0008.
