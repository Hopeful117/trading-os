# Implementation Plan — Story 0007

## Objective

Implement the smallest truthful lifecycle reconciliation and trader-facing
result projection layer above Story 0006 so that an actor-owned `ActiveScan`
can be polled as an understandable unit of work without changing creation,
dispatch, idempotency or downstream ownership boundaries.

## Architecture Decision

Story 0007 will be implemented inside `market-intelligence` by enriching the
existing actor-owned GET path.

The implementation will:

1. extend `ActiveScan` lifecycle semantics;
2. load persisted child truth in batch;
3. classify per-market child outcomes from `AnalysisExecution`,
   `ConsolidatedIntelligence`, `PipelineRun` and `TradingOpportunity` lineage;
4. reconcile aggregate lifecycle forward-only on read;
5. return a trader-facing projection from the existing GET endpoint;
6. avoid child-creation, dispatch or cancellation side effects during
   reconciliation.

Story 0007 intentionally stops before cancellation and before any frontend
implementation.

## MUST_REUSE

- `ActiveScan`
- `ActiveScanMarket`
- `ActiveScanRepository`
- `ActiveScanApplicationService` ownership boundary
- Story 0005 scope snapshot persisted by Story 0006
- Story 0006 actor-scoped ownership model
- existing `AnalysisExecution` aggregate
- existing `ConsolidatedIntelligence`
- existing `ProductionIntelligencePipeline` provenance
- existing `TradingOpportunity` model
- existing `MarketIntelligenceController` GET endpoint
- existing `ResponseEntity` controller conventions

## MUST_NOT_MODIFY

- Story 0005 scope resolution rules
- Story 0006 creation/idempotency/dispatch semantics
- `AnalysisExecution` single-market ownership
- `PipelineRun` ownership
- `TradingOpportunity` lifecycle semantics
- Risk Domain
- Broker integration
- Passive Scanner behavior
- Gateway trust model

## MUST_NOT_DUPLICATE

- account ownership authority
- market eligibility authority
- child execution status authority
- pipeline provenance authority
- opportunity persistence authority
- risk authorization authority
- broker execution authority

## DEFER_TO_STORY_0008

- scan cancellation command
- `CANCELLING` aggregate state
- `CANCELLED` aggregate state
- child cancellation fan-out
- cancellation retries and crash recovery

## OUT_OF_SCOPE

- scheduler-based reconciliation
- domain events / Kafka / SSE / WebSocket
- global cross-market ranking
- Trade Plan integration changes
- Risk evaluation UX changes
- broker execution UX
- challenge progress model
- AI ranking or interpretation

## Phase 1 — Extend ActiveScan Lifecycle Vocabulary

### Files Likely Affected

- `MODIFY` `market-intelligence/.../domain/scan/ActiveScanStatus.java`
- `MODIFY` `market-intelligence/.../domain/scan/ActiveScan.java`
- `NEW` or `MODIFY` lifecycle helper in
  `market-intelligence/.../application/scan`

### Behavior Added

- add `RUNNING`, `PARTIALLY_COMPLETED`, `COMPLETED`, `FAILED`
- preserve `READY_TO_DISPATCH`, `DISPATCH_REQUESTED`, `COMPLETED_NO_WORK`
- add forward-only transition semantics suitable for read-side reconciliation

### Invariants

- terminal states never regress
- `PARTIALLY_COMPLETED` is terminal
- `COMPLETED_NO_WORK` remains terminal no-work
- no cancellation states are introduced

### Tests

- lifecycle transition tests
- invalid regression tests
- terminal-state idempotency tests

### Completion Condition

The domain can express the approved Story 0007 lifecycle without reworking
Story 0006 creation/dispatch semantics.

## Phase 2 — Introduce Read-Side Child Truth Loading Seams

### Files Likely Affected

- `MODIFY` `market-intelligence/.../application/port/AnalysisExecutionRepository.java`
- `MODIFY` `market-intelligence/.../adapter/persistence/JpaAnalysisExecutionRepository.java`
- `MODIFY` `market-intelligence/.../adapter/persistence/SpringDataAnalysisExecutionRepository.java`
- `MODIFY` `market-intelligence/.../adapter/persistence/JpaIntelligencePipelineRunRepository.java`
- `MODIFY` or `NEW` opportunity read seam in
  `market-intelligence/.../application/port`
- `MODIFY` or `NEW` JPA adapter for batch opportunity lookup

### Behavior Added

- batch load linked child `AnalysisExecution`s for one scan
- batch load pipeline runs for the linked child execution ids
- resolve projected opportunities efficiently from pipeline lineage

### Invariants

- deterministic market ordering remains based on `ActiveScanMarket.ordinal`
- no read path may create missing child executions
- no read path may dispatch child executions
- no duplicated opportunity truth is persisted

### Tests

- batch load by ids
- deterministic ordering preservation
- opportunity lineage lookup tests
- no N+1 regression-oriented repository tests where practical

### Completion Condition

One scan GET can obtain all required persisted child truth without obviously
naive per-child repository loops.

## Phase 3 — Add Per-Market Outcome Classification

### Files Likely Affected

- `NEW` projection/read-model type(s) under
  `market-intelligence/.../application/scan`
- `NEW` classifier/helper for child truth classification

### Behavior Added

- classify child truth into scan-relevant buckets such as:
  - excluded
  - in-flight
  - completed with no opportunity
  - completed with opportunity
  - failed
  - expired
- separate internal status vocabulary from trader-facing semantics

### Invariants

- no-opportunity is not failure
- raw `AnalysisExecution.PARTIALLY_COMPLETED` is not exposed as scan terminal
  partial completion
- `OpportunityScore` is exposed only with its current truthful meaning

### Tests

- completed with no opportunity
- completed with opportunity
- runtime failure
- expired child
- mixed child classification

### Completion Condition

The application layer can derive a stable trader-facing per-market outcome from
persisted child truth.

## Phase 4 — Add Aggregate Lifecycle Reconciliation Service

### Files Likely Affected

- `NEW` reconciliation service in `market-intelligence/.../application/scan`
- `MODIFY` `ActiveScanApplicationService`
- `MODIFY` `ActiveScanRepository` if forward-only persistence helper needs
  slight enrichment

### Behavior Added

- reconcile one owned scan from persisted child truth
- compute progress counts
- derive aggregate status
- persist forward-only scan status transitions when required

### Invariants

- reconciliation is actor-owned
- reconciliation is side-effect free except for forward-only aggregate status
  persistence
- reconciliation is idempotent for unchanged child truth
- terminal scan states never regress
- `READY_TO_DISPATCH` may still remain visible if no child truth has advanced
  yet

### Tests

- zero eligible markets
- child still `REQUESTED`
- child `ACCEPTED`
- child `CONTEXT_BUILDING`
- child `RUNNING`
- all children success
- mixed success/failure
- all failure
- repeated reconciliation idempotent
- terminal state never regresses

### Completion Condition

The service can turn existing persisted scan + child truth into a truthful
aggregate lifecycle classification and forward-only update.

## Phase 5 — Replace Minimal GET with Trader-Facing Projection

### Files Likely Affected

- `MODIFY` `market-intelligence/.../adapter/web/MarketIntelligenceController.java`
- `MODIFY` `market-intelligence/.../adapter/web/ActiveScanResponse.java`
- `MODIFY` `market-intelligence/.../application/scan/ActiveScanApplicationService.java`
- `NEW` projection DTOs if `ActiveScanResponse` becomes too large to remain
  clean

### Behavior Added

- actor-owned GET returns:
  - scan identity/context
  - reconciled status
  - timestamps
  - progress counters
  - per-market projection
  - projected opportunity data through lineage

### Invariants

- `ResponseEntity` conventions preserved
- no separate `/results` endpoint unless repository realities prove the GET
  split necessary
- ownership semantics identical to Story 0006

### Tests

- GET owned running scan
- GET completed scan
- GET partially completed scan
- GET failed scan
- GET no-work scan
- GET foreign scan
- deterministic JSON ordering

### Completion Condition

The existing GET endpoint returns the approved trader-readable projection.

## Phase 6 — Validate Crash / Recovery Compatibility

### Files Likely Affected

- primarily tests
- possible small read-side helper refinement in reconciliation service

### Behavior Added

- none to creation/dispatch
- verify that read-side reconciliation remains truthful after:
  - crash after scan commit before dispatch
  - crash after dispatch claim
  - partial child completion
  - service restart while scan is in flight

### Invariants

- no second child `AnalysisExecution` is ever created
- GET after restart reconstructs state from persisted facts
- no read path mutates child orchestration

### Tests

- crash-window oriented application/persistence tests
- repeated polling after simulated restart state

### Completion Condition

Story 0007 proves compatibility with Story 0006 crash/retry guarantees.

## Phase 7 — Regression and Runtime Validation

### Files Likely Affected

- tests only
- no production behavior change beyond already planned implementation

### Behavior Added

- comprehensive Story 0007 validation matrix
- runtime benchmark procedure through Gateway

### Invariants

- Story 0005 remains green
- Story 0006 orchestration/idempotency remains green
- Passive Scanner remains unchanged
- no Risk/Broker side effects are introduced

### Tests

- domain tests
- application tests
- persistence tests
- web tests
- regression suites
- runtime benchmark script/procedure if repository workflow expects it later

### Completion Condition

Story 0007 behavior is validated by focused automated tests plus a
post-implementation runtime benchmark plan.

## Recommended File Targets

### Production Files Likely Added

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanReconciliationService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanResultProjection.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanMarketProjection.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanProgress.java`
- optional classifier helper such as
  `ActiveScanMarketOutcomeClassifier.java`

### Production Files Likely Modified

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScan.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScanStatus.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanApplicationService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/ActiveScanResponse.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketIntelligenceController.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/AnalysisExecutionRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaAnalysisExecutionRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/SpringDataAnalysisExecutionRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaIntelligencePipelineRunRepository.java`
- opportunity repository adapter and/or port files if batch lookup is added

### Production Files Likely Unchanged

- Story 0005 scope resolution classes
- Story 0006 dispatch claim classes
- `LocalAnalysisExecutionDispatcher`
- `ProductionIntelligencePipeline` write semantics
- Risk Domain
- Broker Service
- Gateway trust-boundary implementation

### Migration Files

- `NONE EXPECTED`

## Test Plan

### DOMAIN_TEST

- zero eligible markets
- child `REQUESTED`
- child `ACCEPTED`
- child `CONTEXT_BUILDING`
- child `RUNNING`
- all children success
- mixed success/failure
- all failure
- child `EXPIRED`
- completed analysis with zero opportunity
- completed analysis with opportunity
- deterministic lifecycle precedence
- terminal state never regresses
- repeated reconciliation idempotent

### APPLICATION_TEST

- owned scan reconciliation
- unknown scan
- foreign actor scan
- batch child loading
- opportunity projection
- repeated polling
- no child creation during reconciliation
- no dispatch during reconciliation

### PERSISTENCE_TEST

- load scan markets deterministically by ordinal
- batch `AnalysisExecution` lookup
- pipeline lineage query
- opportunity lookup by pipeline-linked id/version
- status persistence when reconciliation advances aggregate status

### WEB_TEST

- GET owned running scan
- GET completed scan
- GET partial scan
- GET failed scan
- GET no-work scan
- GET foreign scan
- deterministic JSON projection
- `ResponseEntity` contract

### REGRESSION_TEST

- Story 0005 scope tests
- Story 0006 orchestration/idempotency tests
- `AnalysisExecution` tests
- Passive Scanner tests
- opportunity lifecycle tests

## Runtime Benchmark Plan

Expected post-implementation benchmark through Gateway:

1. authenticated trader creates an `ActiveScan`;
2. receives `202 Accepted`;
3. polls owned GET;
4. sees `RUNNING` while children execute;
5. sees deterministic progress counts;
6. sees excluded markets;
7. sees successful market result;
8. sees no-opportunity result when reproducible;
9. sees failure-class result when safely reproducible;
10. eventually reaches truthful terminal state;
11. opportunity lineage is visible when produced;
12. repeated GET does not mutate child orchestration;
13. another actor cannot read the scan;
14. no broker order is created;
15. no Risk Domain authorization occurs;
16. Passive Scanner remains independent.

## Completion Criteria

Story 0007 is complete when:

- the existing actor-owned GET returns a truthful trader-facing lifecycle and
  result projection;
- aggregate lifecycle reconciliation is forward-only and idempotent;
- no-opportunity outcomes are represented explicitly as successful analysis;
- opportunity lineage is projected through existing truth;
- no new migration is required unless repository evidence changes during
  implementation;
- automated tests and runtime benchmark evidence validate the Story without
  modifying Story 0005 or Story 0006 ownership boundaries.
