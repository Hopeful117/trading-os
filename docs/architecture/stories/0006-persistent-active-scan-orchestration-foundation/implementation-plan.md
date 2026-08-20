# Implementation Plan — Story 0006

## Objective

Implement the smallest durable `ActiveScan` orchestration foundation in
`market-intelligence` so that one Story 0005 resolved scope can be persisted,
linked to exactly one logical `AnalysisExecution(ACTIVE)` per eligible market,
and safely dispatched only after the durable linkage is committed.

## Architecture Decision

Story 0006 will be implemented entirely inside `market-intelligence`.

The implementation will:

1. call Story 0005 scope resolution;
2. persist one `ActiveScan`;
3. persist one `ActiveScanMarket` row per resolved candidate market;
4. register or reuse child `AnalysisExecution(ACTIVE)` instances for eligible
   markets using deterministic child idempotency;
5. persist child linkage before dispatch;
6. dispatch eligible children after commit through the existing dispatcher seam
   enriched with a pre-dispatch registration path.

Story 0006 intentionally stops before aggregate lifecycle/result projection,
scan cancellation fan-out and per-market result read models.

## MUST_REUSE

- `ActiveScanScopeResolutionService`
- `ActiveScanScopeResolutionRequest`
- `ActiveScanScopeResolutionResult`
- `MarketEligibilityDecision`
- `EffectiveScanScope`
- existing `AnalysisExecution` aggregate and persistence
- existing `AnalysisExecutionDispatcher`
- existing active analysis strategy / `AnalysisExecutionMode.ACTIVE`
- existing web/API conventions for `Idempotency-Key`, `202 Accepted` and
  `Location`

## MUST_NOT_MODIFY

- `AnalysisExecution` single-market semantics
- `PipelineRun` ownership and cardinality
- Observation model
- Opportunity model
- Trade Planning
- Risk Domain
- Broker Service
- PassiveAnalysisStrategy semantics

## MUST_NOT_DUPLICATE

- Story 0005 eligibility authority
- account ownership authority
- market tradability authority
- pipeline provenance semantics
- Risk Domain authority
- broker execution authority

## DEFER_TO_STORY_0007

- aggregate child-completion reconciliation
- terminal result projection
- `PARTIALLY_COMPLETED` scan semantics
- scan cancellation fan-out
- detailed per-market diagnostics beyond minimal orchestration state
- opportunity/result projection APIs

## OUT_OF_SCOPE

- global cross-market ranking
- "best trade" claims
- `OpportunityScore` normalization
- AI interpretation/ranking
- Trade Plan generation during scan creation
- broker order execution
- generic workflow engine
- scheduler/background recovery service unless a blocker proves it is required

## Step 1 — Add ActiveScan Domain Aggregate

### Module

- `market-intelligence`

### Package

- `com.hope.trading.market_intelligence.domain.scan`

### Files

- `NEW` `ActiveScan.java`
- `NEW` `ActiveScanId.java` or reuse raw `UUID` if repository conventions stay
  simple
- `NEW` `ActiveScanStatus.java`
- `NEW` value object(s) for scope snapshot sections if needed

### Responsibility

Represent one persisted user-triggered active scan request above child
`AnalysisExecution`s.

### Required Fields

- `scanId`
- `actorId`
- `accountId`
- `objective`
- `idempotencyKey`
- `requestFingerprint`
- requested-market snapshot
- candidate-market snapshot
- eligibility snapshot
- exact `ActiveScanStatus`
- `resolvedAt`
- `createdAt`
- `updatedAt`

### Inputs

- authenticated actor id
- Story 0005 resolved scope result
- scan-level idempotency key

### Outputs

- durable aggregate ready for persistence and child linkage

### Invariants

- one logical scan request = one `ActiveScan`
- scan-level idempotency is scoped by `(actorId, idempotencyKey)`
- same `(actorId, idempotencyKey)` with different logical request fingerprint is
  a conflict
- aggregate stores scope snapshot, not mutable market/account authority
- empty effective scope is allowed

### Tests

- aggregate creation from resolved scope
- required-field validation
- empty-scope construction

## Step 2 — Add ActiveScanMarket Link Model

### Module

- `market-intelligence`

### Package

- `com.hope.trading.market_intelligence.domain.scan`

### Files

- `NEW` `ActiveScanMarket.java`
- `NEW` `ActiveScanMarketStatus.java`

### Responsibility

Represent one persisted per-market orchestration row inside a scan, including
excluded markets that intentionally have no child analysis.

### Required Fields

- row identity
- `scanId`
- `ordinal`
- `marketId`
- `eligible`
- exclusion reasons snapshot
- exact `ActiveScanMarketStatus`
- nullable `analysisExecutionId`
- `createdAt`
- `updatedAt`

### Inputs

- one `MarketEligibilityDecision`
- market ordinal in deterministic requested/candidate order

### Outputs

- durable per-market scan linkage row

### Invariants

- at most one row per `(scanId, marketId)`
- excluded markets never receive `analysisExecutionId`
- eligible markets transition through exact Story 0006 states without losing
  deterministic linkage

### Tests

- excluded row stores diagnostics and null child execution
- eligible row stores child execution id
- deterministic ordinal preservation

## Step 3 — Add Scan Persistence Ports and JPA Adapters

### Module

- `market-intelligence`

### Package

- `application.port`
- `adapter.persistence`

### Files

- `NEW` `ActiveScanRepository.java`
- `NEW` `JpaActiveScanEntity.java`
- `NEW` `JpaActiveScanMarketEntity.java`
- `NEW` `SpringDataActiveScanRepository.java`
- `NEW` `SpringDataActiveScanMarketRepository.java`
- `NEW` `JpaActiveScanRepository.java`

### Responsibility

Persist scan aggregate and per-market rows in the existing market-intelligence
database.

### Persistence

- table `active_scans`
- table `active_scan_markets`

### Required Constraints

- unique `(actor_id, idempotency_key)`
- unique `(scan_id, market_id)`
- FK from `active_scan_markets.scan_id` to `active_scans.scan_id`
- nullable `analysis_execution_id`
- deterministic ordering column/index
- persisted `request_fingerprint`

### Transaction Boundary

- same local Spring transaction manager as `analysis_executions`

### Tests

- persistence round-trip
- unique constraint expectations
- nullable child linkage for excluded rows

## Step 4 — Add ActiveScan Application Command / Service

### Module

- `market-intelligence`

### Package

- `com.hope.trading.market_intelligence.application.scan`

### Files

- `NEW` `CreateActiveScanCommand.java`
- `NEW` `ActiveScanApplicationService.java`
- `NEW` exception types for idempotency conflict / ownership / unavailable
  scope if needed

### Responsibility

Own scan creation orchestration:

1. resolve Story 0005 scope;
2. create/reuse scan;
3. persist scan and scan-market rows;
4. register/reuse child analyses for eligible markets;
5. persist child linkage;
6. schedule dispatch after commit.

### Inputs

- actor identity from the authenticated request
- `Idempotency-Key`
- `accountId`
- `objective`
- optional `requestedMarketIds`

### Outputs

- persisted `ActiveScan`
- minimal response projection

### Idempotency Invariant

- same `(actorId, idempotencyKey)` + same logical request fingerprint → same
  `ActiveScan`
- same `(actorId, idempotencyKey)` + different logical request fingerprint →
  explicit conflict

### Logical Request Equality

- persist a deterministic `requestFingerprint`
- fingerprint fields are exactly:
  - `actorId`
  - `accountId`
  - normalized `objective`
  - normalized `requestedMarketIds`
- `objective` normalization must match Story 0005:
  `null -> ""`, then `strip()`
- `requestedMarketIds` normalization must match Story 0005:
  remove `null`, deduplicate with first-occurrence preservation, preserve
  resulting order
- candidate scope, effective scope, decisions and `resolvedAt` do not
  participate in request identity

### Dispatch Invariant

- no child analysis dispatch before scan/linkage commit

### Tests

- create scan from mixed scope
- replay same request returns same scan
- idempotency conflict on request mismatch
- empty scope persists zero-child scan

## Step 5 — Enrich AnalysisExecution Application Seam

### Module

- `market-intelligence`

### Package

- `com.hope.trading.market_intelligence.application.execution`

### Files

- `MODIFY`
  `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/execution/AnalysisExecutionService.java`
- `MODIFY` supporting ports only if strictly required

### Responsibility

Introduce the smallest safe seam allowing persisted child registration before
dispatch.

### Recommended Change

- add a registration path that creates or reuses a persisted
  `AnalysisExecution` without dispatch;
- add an explicit dispatch-claim method for an already persisted execution;
- add an explicit dispatcher-start method for an already claimed execution;
- keep the existing public `create(...)` behavior backwards compatible by
  composing registration + immediate dispatch for current consumers.
- make dispatch claim an atomic persisted transition
  `REQUESTED -> ACCEPTED`
- make dispatcher start an atomic persisted transition
  `ACCEPTED -> CONTEXT_BUILDING`

### Why This Option

- clearer semantics than transaction synchronization wrapped around the current
  `create(...)` method;
- keeps existing consumers stable;
- allows Story 0006 to link child executions durably before dispatch;
- minimizes risk of duplicate dispatch.

### Inputs

- `IntelligenceAnalysisRequest`
- deterministic child idempotency key
- trace/request ids

### Outputs

- persisted reusable child execution id
- explicit dispatch action

### Tests

- registration persists without dispatch
- dispatch claim succeeds once from `REQUESTED`
- concurrent claim attempts cannot both succeed
- dispatcher start succeeds once from `ACCEPTED`
- duplicate dispatcher invocation no-ops after the first successful start
- old create path remains backwards compatible

## Step 6 — Add Deterministic Child Idempotency Derivation

### Module

- `market-intelligence`

### Package

- `application.scan` or adjacent support package

### Files

- `NEW` small helper/value object if needed, for example
  `ActiveScanChildKeyFactory.java`

### Responsibility

Derive one stable child `IdempotencyKey` per logical scan + eligible market.

### Inputs

- persisted scan identity or durable scan-level idempotency identity
- `marketId`
- `ACTIVE` mode

### Outputs

- `IdempotencyKey`

### Invariants

- retries for same logical scan and same market reuse the same child execution
- key generation is internal implementation detail, not public API contract
- one logical eligible child per `(scanId, marketId)` regardless of retry count

### Tests

- same scan + same market → same child key
- same scan + different market → different child key
- duplicate requested market does not produce duplicate child key

## Step 7 — Add After-Commit Dispatch Coordination

### Module

- `market-intelligence`

### Package

- `application.scan`

### Files

- `NEW` small coordination component if needed
- `MODIFY` scan application service

### Responsibility

Dispatch eligible child analyses only after the transaction committing
`ActiveScan`, `ActiveScanMarket` and `AnalysisExecution` linkage has succeeded.

### Recommended Mechanism

- Spring `TransactionSynchronization` or equivalent local after-commit hook
  inside the application service

### Why

- local, minimal, same-process;
- no event broker required;
- sufficient for Story 0006 invariant;
- preserves intent if dispatch fails after commit because the durable scan/link
  rows already exist.

### Exact Owning Components

- `ActiveScanApplicationService`
  - registers the `afterCommit` callback from the create transaction
- `ActiveScanDispatchCoordinator`
  - receives the post-commit callback and iterates resumable eligible children
- `ActiveScanDispatchClaimService`
  - owns `@Transactional(propagation = REQUIRES_NEW)` durable dispatch claim
  - exposes a method such as `claimForDispatch(scanId, marketId, executionId)`
    that performs only durable claim work
- `AnalysisExecutionService`
  - owns the atomic `AnalysisExecution` state-transition seam used by durable
    claim and dispatcher-start

### Exact Story 0006 State Model

`ActiveScanStatus`

- `READY_TO_DISPATCH`
  - creation condition: scan committed with at least one eligible market and at
    least one eligible `ActiveScanMarket` still `REGISTERED`
  - allowed transition: `READY_TO_DISPATCH -> DISPATCH_REQUESTED`
  - terminal: no
  - retry behavior: retry resumes undispatched eligible markets
- `DISPATCH_REQUESTED`
  - creation condition: at least one eligible `ActiveScanMarket` has been
    durably claimed for handoff and advanced to `DISPATCH_REQUESTED`
  - allowed transition: none in Story 0006
  - terminal: no
  - retry behavior: retry may safely re-handoff children whose
    `AnalysisExecution` is `ACCEPTED` or still claimable from `REQUESTED`,
    because actual processing start is atomically guarded
- `COMPLETED_NO_WORK`
  - creation condition: `EffectiveScanScope = []`
  - allowed transition: none
  - terminal: yes
  - retry behavior: replay returns same zero-child scan

`ActiveScanMarketStatus`

- `EXCLUDED`
  - creation condition: Story 0005 decision marks the market ineligible
  - allowed transition: none
  - terminal: yes
  - retry behavior: never creates or dispatches a child execution
- `REGISTERED`
  - creation condition: eligible child `AnalysisExecution` persisted/reused and
    linked
  - allowed transition: `REGISTERED -> DISPATCH_REQUESTED`
  - terminal: no
  - retry behavior: retry promotes still-undispatched children
- `DISPATCH_REQUESTED`
  - creation condition: row durably marked immediately before dispatch
    invocation
  - allowed transition: none in Story 0006
  - terminal: no
  - retry behavior: retry may re-invoke dispatcher, but only one path can
    actually advance the child execution from `ACCEPTED` into processing

### Dispatch-Failure Behavior

- dispatch failure after commit does not delete or roll back the scan;
- persisted link state remains recoverable;
- request retry reuses same scan and same child executions and can attempt the
  missing dispatches again.

### Delivery Semantic

- exact handoff semantic is `AT_LEAST_ONCE_IDEMPOTENT`
- Story 0006 must not claim exactly-once dispatch
- the exactly-one invariant applies to logical child creation, not transport
  handoff count

### Retry Algorithm

1. load or create `ActiveScan` by `(actorId, idempotencyKey)`
2. if existing, compare `requestFingerprint`
3. if mismatched, return conflict
4. reuse existing child `AnalysisExecution` rows through deterministic child
   keys
5. for each resumable eligible child, run `REQUIRES_NEW` dispatch-claim logic
6. after each successful durable claim commit, invoke dispatcher handoff
7. resume only eligible `ActiveScanMarket` rows in:
   - `REGISTERED`
   - `DISPATCH_REQUESTED`
8. do not create a second logical child for any `(scanId, marketId)`

### Exact REQUIRES_NEW Claim Transaction

The dedicated `REQUIRES_NEW` claim method must atomically:

1. conditionally update `ActiveScanMarket.status` from `REGISTERED` to
   `DISPATCH_REQUESTED`
   or no-op if already `DISPATCH_REQUESTED` during resume;
2. conditionally update child `AnalysisExecution.status` from `REQUESTED` to
   `ACCEPTED`;
3. conditionally update parent `ActiveScan.status` from `READY_TO_DISPATCH` to
   `DISPATCH_REQUESTED` when the first eligible child is durably claimed;
4. commit before any dispatcher invocation.

If this transaction fails, dispatcher invocation must not occur.

### Selected Atomic Claim Mechanism

- use repository-supported conditional updates, not a naive load/check
- `ActiveScanDispatchClaimService` must durably claim the per-market row and
  child execution in one `REQUIRES_NEW` transaction
- `AnalysisExecutionService` must provide a small atomic transition seam
  equivalent to:
  - `REQUESTED -> ACCEPTED` for durable dispatch claim
  - `ACCEPTED -> CONTEXT_BUILDING` for actual async processing start
- a second concurrent caller may re-invoke dispatcher handoff, but must not be
  able to win both persisted transition claims

### Current Race Assessment

- current repository evidence does not prove duplicate dispatch impossible
- `JpaAnalysisExecutionEntity` has no `@Version`
- no pessimistic locking is present on the existing dispatch path
- current `LocalAnalysisExecutionDispatcher` submits work without an earlier
  durable dispatch claim
- therefore a naive `if status == REQUESTED then dispatch` check is not safe
  under concurrent retries

### Final Guarantee Vocabulary

- `EXACTLY_ONCE_LOGICAL_CREATION`
  - same logical scan + same market creates exactly one logical child
    `AnalysisExecution`
- `AT_LEAST_ONCE_DISPATCH`
  - dispatcher handoff may be invoked more than once across retries/crashes
- `IDEMPOTENT_PROCESSING`
  - only one worker path may atomically start processing by winning
    `ACCEPTED -> CONTEXT_BUILDING`

### Final Crash / Retry Table

- CASE A: crash before T1 commit
  - `ActiveScan`: no durable row
  - `ActiveScanMarket`: no durable row
  - `AnalysisExecution`: no durable child linkage from this attempt
  - retry: recreate/replay full request
  - duplicate handoff: no
- CASE B: T1 committed, crash before callback
  - `ActiveScan`: `READY_TO_DISPATCH`
  - eligible `ActiveScanMarket`: `REGISTERED`
  - child `AnalysisExecution`: `REQUESTED`
  - retry: reuse scan and child, resume eligible `REGISTERED` rows
  - duplicate handoff: no prior handoff occurred
- CASE C: callback begins, crash before claim commit
  - durable state unchanged from CASE B
  - retry: same as CASE B
  - duplicate handoff: no durable claim committed
- CASE D: claim committed, crash before dispatcher invocation
  - `ActiveScan`: `DISPATCH_REQUESTED`
  - claimed `ActiveScanMarket`: `DISPATCH_REQUESTED`
  - child `AnalysisExecution`: `ACCEPTED`
  - retry: may invoke dispatcher again
  - duplicate handoff: possible
  - safe because processing start still requires atomic
    `ACCEPTED -> CONTEXT_BUILDING`
- CASE E: dispatcher invoked, process crashes immediately afterward
  - `ActiveScan`: `DISPATCH_REQUESTED`
  - claimed `ActiveScanMarket`: `DISPATCH_REQUESTED`
  - child `AnalysisExecution`: `ACCEPTED` or later if the worker already began
  - retry: may re-invoke dispatcher
  - duplicate handoff: possible
  - safe because only one path can start processing
- CASE F: two concurrent retries resume the same child
  - one caller can win `REQUESTED -> ACCEPTED`
  - repeated invocation after `ACCEPTED` is still safe because only one worker
    can win `ACCEPTED -> CONTEXT_BUILDING`
  - duplicate logical child creation: impossible
  - duplicate handoff invocation: possible and tolerated

### Tests

- linkage exists before dispatch invocation
- after-commit hook does not run on rollback
- post-commit dispatch failure preserves durable rows
- crash-after-commit-before-dispatch retry resumes `REGISTERED` rows
- crash-after-status-update retry resumes only children still in `REQUESTED`
- `REQUIRES_NEW` claim failure prevents dispatcher invocation
- concurrent callers cannot both win `REQUESTED -> ACCEPTED`
- concurrent dispatcher starts cannot both win `ACCEPTED -> CONTEXT_BUILDING`
- `ActiveScanStatus.DISPATCH_REQUESTED` means at least one child was durably
  claimed, not that all children were invoked

## Step 8 — Add Minimal Web/API Surface

### Module

- `market-intelligence`

### Package

- `adapter.web`

### Files

- `MODIFY`
  `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketIntelligenceController.java`
- `NEW` `CreateActiveScanRequestDto.java`
- `NEW` `ActiveScanResponse.java`
- `NEW` minimal read response DTO if separated from create response
- `MODIFY` exception handler only if required for new error mapping

### Proposed API

- `POST /api/v1/intelligence/scans`
- `GET /api/v1/intelligence/scans/{scanId}`

### Request

- `accountId`
- `objective`
- optional `requestedMarketIds`

### Headers

- required `Idempotency-Key`
- optional `X-Request-Id`
- optional `X-Trace-Id`

### Response Semantics

- `202 Accepted`
- `Location: /api/v1/intelligence/scans/{scanId}`
- minimal response includes scan id, minimal status, account id, objective,
  requested/candidate/effective snapshot summary and child linkage summary

### Ownership

- actor identity sourced from authenticated request context
- all reads must verify persisted scan ownership

### Tests

- create endpoint acceptance response
- idempotent replay response
- ownership rejection on read

## Step 9 — Add Minimal Read Model

### Module

- `market-intelligence`

### Package

- `application.scan`
- `adapter.web`

### Files

- `NEW` minimal query method on scan repository
- `NEW` response projection mapping

### Responsibility

Expose enough durable state to verify Story 0006 behavior without pulling full
Story 0007 result semantics.

### Must Expose

- `scanId`
- minimal scan status
- `accountId`
- `objective`
- requested/candidate/effective snapshot
- per-market eligibility and child execution linkage summary

### Must Not Expose Yet

- ranked opportunities
- scan-level final result synthesis
- cancellation state fan-out
- cross-market scoring

### Tests

- read persisted scan after creation
- read empty-scope scan
- read mixed eligible/excluded scan

## Step 10 — Migration Design

### Module

- `market-intelligence`

### Files

- `NEW` next Flyway migration under
  `market-intelligence/src/main/resources/db/migration/`

### Expected Objects

- `active_scans`
- `active_scan_markets`

### Expected Columns

`active_scans`

- `scan_id UUID PRIMARY KEY`
- `actor_id UUID NOT NULL`
- `account_id UUID NOT NULL`
- `idempotency_key VARCHAR(200) NOT NULL`
- `request_fingerprint VARCHAR(64) NOT NULL`
- `status VARCHAR(40) NOT NULL`
- `objective TEXT NOT NULL`
- snapshot column(s) for requested/candidate/decision data
- `resolved_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `updated_at TIMESTAMP WITH TIME ZONE NOT NULL`

`active_scan_markets`

- row id or composite PK
- `scan_id UUID NOT NULL`
- `ordinal INTEGER NOT NULL`
- `market_id UUID NOT NULL`
- `eligible BOOLEAN NOT NULL`
- exclusion reason snapshot column
- `status VARCHAR(40) NOT NULL`
- `analysis_execution_id UUID NULL`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `updated_at TIMESTAMP WITH TIME ZONE NOT NULL`

### Constraints / Indexes

- unique `(actor_id, idempotency_key)` on `active_scans`
- unique `(scan_id, market_id)` on `active_scan_markets`
- FK `scan_id -> active_scans(scan_id)`
- index on `(scan_id, ordinal)`
- index on `(actor_id, idempotency_key)` for replay lookup
- index on `(actor_id, created_at)` or equivalent ownership lookup
- optional unique index on `analysis_execution_id`

### Snapshot Storage Guidance

- prefer compact JSON/TEXT snapshot columns for ordered request/candidate and
  decision snapshots unless repository conventions prove normalized child
  storage is required;
- do not persist mutable account or market authority copies.

## Step 11 — Automated Test Plan

### Focused Existing Tests To Keep Green

- `ActiveScanScopeResolutionServiceTest`
- `ActiveScanScopeResolutionControllerTest`
- `AnalysisExecutionServiceTest`
- `AnalysisExecutionStrategyTest`

### New Tests

#### Domain / Application

- create scan from resolved mixed scope
- persist actor/account/objective/snapshot
- empty effective scope
- excluded market creates no child execution
- eligible markets create one child each
- duplicate requested markets do not create duplicate children

#### Idempotency

- replay same scan request returns same scan
- replay same scan does not duplicate children
- reused scan key with different request conflicts
- same raw key under different actors does not collide
- deterministic child key reuse across retries

#### Transaction / Dispatch

- child linkage exists before dispatch
- after-commit dispatch runs only after successful commit
- `REGISTERED -> DISPATCH_REQUESTED` is durably written inside a dedicated
  `REQUIRES_NEW` transaction before dispatcher invocation
- dispatch failure preserves persisted scan/linkage
- retry after partial dispatch does not duplicate children
- retry after commit-before-dispatch resumes `REGISTERED` children
- retry after `DISPATCH_REQUESTED` may re-invoke dispatcher, but only one path
  can atomically advance `AnalysisExecutionStatus.ACCEPTED ->
  CONTEXT_BUILDING`
- duplicate dispatch handoff is idempotent-at-least-once rather than
  exactly-once
- crash after claim commit before dispatcher invocation is recoverable
- concurrent retries cannot both successfully claim the same child dispatch
  handoff

#### Persistence

- unique scan idempotency
- unique `(scan_id, market_id)`
- excluded row allows null `analysis_execution_id`
- migration integration under H2/PostgreSQL-compatible test conventions if
  module already supports it

#### Security / Boundary

- authenticated actor cannot read another actor's scan
- Risk Domain not invoked
- no Trade Plan generation invoked
- no broker execution invoked
- passive strategy behavior unchanged

### Realistic Test Count

- approximately 16 to 24 new tests across domain, application, persistence and
  web layers

## Predicted Production Impact Map

### NEW

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScan.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScanStatus.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScanMarket.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScanMarketLinkState.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/CreateActiveScanCommand.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanApplicationService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanChildKeyFactory.java` if needed
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/ActiveScanRepository.java`
- persistence entities/repositories/adapters for `active_scans` and
  `active_scan_markets`
- request/response DTOs for scan creation/read
- Flyway migration for new tables

### MODIFY

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketIntelligenceController.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/execution/AnalysisExecutionService.java`
- possibly
  `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/AnalysisExecutionDispatcher.java`
  only if explicit persisted-dispatch seam needs a small signature-safe helper

### UNCHANGED

- `AnalysisExecution` domain semantics
- `ProductionIntelligencePipeline`
- `JpaIntelligencePipelineRunEntity`
- Observation domain/application
- Opportunity domain/application
- Trade plan generation and risk handoff
- Risk Domain
- Broker Service
- `PassiveAnalysisStrategy`
- Gateway routes unless current API exposure requires a simple passthrough

## Story 0006 Boundary Verdict

This reduced Story 0006 remains coherent if implementation is constrained to:

- persisted `ActiveScan`;
- persisted `ActiveScanMarket`;
- Story 0005 composition;
- scan/child idempotency;
- child registration before dispatch;
- after-commit dispatch;
- minimal create/read API;
- persistence and tests.

Any richer lifecycle/result/cancellation work must stop and be deferred to
Story 0007.

## ADR Verdict

`NO_NEW_ADR_REQUIRED`

Existing ADR-033 plus current execution/persistence ADRs already authorize this
implementation slice as long as it stays inside the boundaries above.
