# Repository Analysis — Story 0006

## Story Overview

- **Story ID:** `0006`
- **Title:** Persistent Active Scan Orchestration Foundation
- **Status:** Draft
- **Location:** `docs/architecture/stories/0006-persistent-active-scan-orchestration-foundation/story.md`

## DevLog Context

### Retrieval Metadata

- `candidateCount`: `136`
- `selectedCount`: `60`
- `usedTokens`: `3303`
- `contextDigest`: `3b83229e522b8d7c3a87335e5971eb87eee84d635cf1984643059539adc0605c`
- `truncated`: `true`

### Selected DevLog Evidence

- `DEVLOG_CONFIRMED`: DevLog surfaced older active/passive orchestration
  history through the foundational market-intelligence commit
  `5fda25d745aee11370fc9db50112b74299d34845`, showing existing active and
  passive analysis orchestration roots.
- `DEVLOG_CONFIRMED`: DevLog surfaced Story 0004 artifacts and ADR-028/ADR-023
  lineage, confirming the broader repository history around deterministic
  orchestration and idempotent domain transitions.
- `DEVLOG_CONFIRMED`: DevLog surfaced source evidence for
  `ActiveScanScopeResolutionRequest.java`, which confirms Story 0005 code is
  present in current engineering context.
- `NOT_FOUND`: DevLog did not clearly elevate ADR-033, Story 0005, Story 0002,
  `AnalysisExecution` or `PipelineRun` as first-class selected evidence in this
  retrieval.
- `INFERENCE`: DevLog remains historically useful but incomplete for the most
  recent Active Scanner work; repository code and ADRs remain authoritative for
  Story 0006 design.

## Baseline Verification

- `REPOSITORY_CONFIRMED`: repository root is `trading-os`.
- `REPOSITORY_CONFIRMED`: `main` currently points to merge commit `db5eb79`
  and Story preparation branch
  `story/0006-persistent-active-scan-orchestration-foundation` starts exactly
  from that same commit.
- `REPOSITORY_CONFIRMED`: worktree was clean before Story preparation.
- `REPOSITORY_CONFIRMED`: Story 0005 merge commit `db5eb79` includes parent
  `4fbcaad`.
- `REPOSITORY_CONFIRMED`: `ADR-033` is present on `main`.

## Governing ADR Invariants

### ADR-033 — Active and Passive Market Intelligence Orchestration

- `REPOSITORY_CONFIRMED`: Active Scanner is intention-driven.
- `REPOSITORY_CONFIRMED`: Active Scan effective scope is resolved before
  expensive targeted analysis.
- `REPOSITORY_CONFIRMED`: `ActiveScan` is a persistent scan-level orchestration
  concept above `AnalysisExecution`.
- `REPOSITORY_CONFIRMED`: `AnalysisExecution` remains single-market.
- `REPOSITORY_CONFIRMED`: `PipelineRun` remains one-analysis provenance and
  must not be reused as `ActiveScan`.
- `REPOSITORY_CONFIRMED`: Active Scan creation must be idempotent.
- `REPOSITORY_CONFIRMED`: child execution idempotency should be
  deterministically derived from scan-level intent.
- `REPOSITORY_CONFIRMED`: V1 must not claim global cross-market ranking from
  `OpportunityScore`.
- `REPOSITORY_CONFIRMED`: Active Scan does not own final Risk Domain authority.
- `REPOSITORY_CONFIRMED`: Passive Scanner remains independent from the selected
  account.

### ADR-020 — Market Intelligence Architecture

- `REPOSITORY_CONFIRMED`: Market Intelligence owns orchestration, not analysis
  engines themselves.
- `REPOSITORY_CONFIRMED`: active and passive modes reuse the same intelligence
  architecture with different orchestration depth.
- `REPOSITORY_CONFIRMED`: user objectives are legitimate context inputs.

### ADR-023 — Capability Execution Model

- `REPOSITORY_CONFIRMED`: capability orchestration stays inside the existing
  execution engine.
- `REPOSITORY_CONFIRMED`: capabilities never persist directly and never own
  orchestration state.
- `REPOSITORY_CONFIRMED`: Story 0006 must not introduce a second analysis
  engine.

### ADR-025 / ADR-026 / ADR-028 / ADR-030 / ADR-032

- `REPOSITORY_CONFIRMED`: Observations remain deterministic and immutable
  outputs.
- `REPOSITORY_CONFIRMED`: Opportunities remain downstream business synthesis
  outputs.
- `REPOSITORY_CONFIRMED`: Risk remains deterministic, fail-closed and strictly
  downstream.
- `REPOSITORY_CONFIRMED`: Broker Service owns technical broker communication
  only.
- `REPOSITORY_CONFIRMED`: execution-specific trade semantics remain downstream
  from intelligence orchestration.

## Story 0005 Integration Seam

### Scope Resolution Contract

- `REPOSITORY_CONFIRMED`: `ActiveScanScopeResolutionRequest` contains
  `accountId`, `objective`, `requestedMarketIds`.
- `REPOSITORY_CONFIRMED`: `ActiveScanScopeResolutionResult` contains
  `accountId`, normalized `objective`, normalized `requestedMarketIds`,
  `candidateMarketIds`, `decisions`, `effectiveScope`, `resolvedAt`.
- `REPOSITORY_CONFIRMED`: `MarketEligibilityDecision` persists
  `marketId`, `symbol`, `provider`, `eligible`, `reasons`.
- `REPOSITORY_CONFIRMED`: `EffectiveScanScope` contains the final ordered
  `marketIds`.

### Runtime/Behavior Facts

- `REPOSITORY_CONFIRMED`: Story 0005 validates account ownership through
  `TradingCoreAccountClient.findOwnedAccount(accountId)` before reading the
  market catalog.
- `REPOSITORY_CONFIRMED`: requested markets are deduplicated using
  `LinkedHashSet`, preserving first-requested order.
- `REPOSITORY_CONFIRMED`: empty requested scope means "all markets in the
  catalog", ordered deterministically by provider, symbol and `marketId`.
- `REPOSITORY_CONFIRMED`: implemented exclusion reasons are only
  `MARKET_NOT_FOUND` and `MARKET_NOT_TRADABLE`.
- `REPOSITORY_CONFIRMED`: Story 0005 already preserves the exact distinction
  between requested markets, candidate markets, decisions and effective scope.

### Composition Verdict

- `REPOSITORY_CONFIRMED`: Story 0006 can directly compose
  `ActiveScanScopeResolutionService`.
- `REPOSITORY_CONFIRMED`: Story 0006 should consume the resolved scope contract
  and persist a scan snapshot from that contract.
- `REPOSITORY_CONFIRMED`: Story 0006 must not rerun market eligibility logic as
  a second authority layer.

## Scope Snapshot Strategy

| Story 0005 output | Classification | Reason |
|---|---|---|
| actor/user identity | `REFERENCE` | authoritative ownership for future scan access |
| accountId | `REFERENCE` | stable contextual ownership/provenance |
| objective | `SNAPSHOT` | normalized user intent must remain auditable |
| requestedMarketIds | `SNAPSHOT` | caller intent must remain reconstructible |
| candidateMarketIds | `SNAPSHOT` | resolved candidate universe should not be recomputed later |
| eligibility decisions | `SNAPSHOT` | exclusion provenance must survive retries and later reads |
| effectiveMarketIds | `DERIVABLE` | derivable from decisions, but may still be exposed via projection |
| resolvedAt | `SNAPSHOT` | records when scope authority was established |

`DO_NOT_PERSIST`:

- mutable account details;
- mutable broker-account state;
- mutable market-state authority beyond the Story 0005 decision snapshot.

## AnalysisExecution Model

### Aggregate Semantics

- `REPOSITORY_CONFIRMED`: `AnalysisExecution` is an immutable single-market
  aggregate with aggregate id `executionId`.
- `REPOSITORY_CONFIRMED`: provenance includes `marketId`, `mode`, `objective`,
  `strategyVersion`.
- `REPOSITORY_CONFIRMED`: `AnalysisExecutionStatus` states are exactly:
  `REQUESTED`, `ACCEPTED`, `CONTEXT_BUILDING`, `RUNNING`,
  `PARTIALLY_COMPLETED`, `COMPLETED`, `FAILED`, `CANCELLED`, `EXPIRED`.
- `REPOSITORY_CONFIRMED`: `AnalysisExecution` already carries an
  `IdempotencyKey`.

### Persistence

- `REPOSITORY_CONFIRMED`: `analysis_executions` persists one row per execution
  with unique `idempotency_key`.
- `REPOSITORY_CONFIRMED`: analysis execution persistence already lives in the
  same `market-intelligence` module, datasource and Flyway schema as other
  intelligence persistence.

### Creation / Dispatch Seam

- `REPOSITORY_CONFIRMED`: `AnalysisExecutionService.create(...)` currently:
  `findReusable(idempotencyKey)` → create execution → save → dispatch
  immediately.
- `REPOSITORY_CONFIRMED`: this is idempotent for a single analysis request, but
  it dispatches before any external scan-link persistence can be committed.
- `REPOSITORY_CONFIRMED`: this is the main crash-consistency gap for Story
  0006.

### Reuse Verdict

- `REPOSITORY_CONFIRMED`: `AnalysisExecution` is reusable as-is as an
  independently owned child execution.
- `REPOSITORY_CONFIRMED`: Story 0006 must enrich its application seam, not its
  aggregate meaning.

## Existing Idempotency Semantics

### AnalysisExecution

- `REPOSITORY_CONFIRMED`: a reusable analysis is looked up by exact
  `IdempotencyKey` while it is not expired and not terminal-failed/cancelled.
- `REPOSITORY_CONFIRMED`: persistence enforces unique `idempotency_key`.
- `REPOSITORY_CONFIRMED`: duplicate create calls with the same key reuse the
  existing execution and only dispatch once.

### Existing Pattern for Business Idempotency

- `REPOSITORY_CONFIRMED`: `AnalysisTradePlanGenerationService` already uses a
  persisted application-level generation entity with a unique scope
  `(analysis_execution_id, actor_id, account_id, idempotency_key)`.
- `REPOSITORY_CONFIRMED`: that service explicitly handles replay, conflict and
  in-progress semantics through durable state.
- `INFERENCE`: Story 0006 should follow the same application-level idempotency
  style rather than inventing a completely different pattern.

### Story 0006 Readiness

- `REPOSITORY_CONFIRMED`: actor-scoped idempotency is already an established
  user-facing pattern in Trading OS:
  `trading-core` risk evaluations use unique `(actor_id, idempotency_key)` and
  market-intelligence/trading-core trade-plan continuation uses scoped
  idempotency including `actor_id`.
- `REPOSITORY_CONFIRMED`: `AnalysisExecution` uses globally unique
  `idempotency_key`, but that aggregate is an internal single-analysis record,
  not a user-owned orchestration aggregate.
- `INFERENCE`: Story 0006 should follow the actor-scoped user-facing pattern,
  not the internal global analysis key pattern.
- `REPOSITORY_CONFIRMED`: scan-level idempotency requires a new persisted
  actor-scoped scan key.
- `REPOSITORY_CONFIRMED`: child idempotency requires deterministic derivation
  from durable scan identity plus eligible market identity.
- `INFERENCE`: existing infrastructure is close, but Story 0006 still needs
  `SMALL_IDEMPOTENCY_ENRICHMENT_REQUIRED`.

### Exact Scan Idempotency Scope

- `REPOSITORY_CONFIRMED`: preferred Story 0006 constraint is
  `UNIQUE(actor_id, idempotency_key)`.
- `REPOSITORY_CONFIRMED`: replay lookup should therefore be
  `findByActorIdAndIdempotencyKey(actorId, idempotencyKey)`.
- `INFERENCE`: this prevents `User A + key K` from colliding with or exposing
  `User B + key K`.

### Logical Request Equality

- `INFERENCE`: Story 0006 needs a persisted deterministic request fingerprint
  for replay comparison.
- `REPOSITORY_CONFIRMED`: the fingerprint should be computed from the immutable
  pre-resolution scan request identity:
  `actorId`, `accountId`, normalized `objective`, and normalized
  `requestedMarketIds`.
- `REPOSITORY_CONFIRMED`: `objective` normalization must match Story 0005:
  `null -> ""`, then `strip()`.
- `REPOSITORY_CONFIRMED`: `requestedMarketIds` normalization must match Story
  0005:
  remove `null`, deduplicate with first-occurrence preservation, preserve
  resulting order.
- `INFERENCE`: candidate markets, eligibility decisions, effective scope and
  `resolvedAt` must not participate in logical request identity because they are
  outputs of scope resolution rather than caller identity.
- `INFERENCE`: replay algorithm should load by `(actorId, idempotencyKey)` and
  compare the persisted `requestFingerprint`; mismatch returns
  `IDEMPOTENCY_CONFLICT`.

## ActiveScan Ownership Options

| Candidate owner | Verdict | Reason |
|---|---|---|
| `market-intelligence` aggregate | `GOOD_FIT` | owns user intent orchestration above analysis, already hosts Story 0005 scope authority and downstream pipeline |
| `AnalysisExecution` extension | `POOR_FIT` | violates ADR-033 single-market boundary |
| `PipelineRun` extension | `POOR_FIT` | violates per-analysis provenance boundary |
| `trading-core` | `POOR_FIT` | would move market-intelligence orchestration outside the intelligence domain |
| separate scanner service | `POOR_FIT` | unsupported by current repo, too large for V1 |
| generic orchestration framework | `POOR_FIT` | over-abstracted and not required by repository evidence |

### Recommended Owner

- `REPOSITORY_CONFIRMED`: `ActiveScan` should belong to `market-intelligence`.

## Minimum Persistent Model

### ActiveScan

| Field | Classification | Reason |
|---|---|---|
| `scanId` | `REQUIRED` | aggregate identity |
| `actorId` | `REQUIRED` | direct durable ownership |
| `accountId` | `REQUIRED` | selected account provenance |
| `objective` | `REQUIRED` | normalized user intent snapshot |
| `idempotencyKey` | `REQUIRED` | scan replay/recovery identity |
| `requestFingerprint` | `REQUIRED` | exact replay equality check |
| requested scope snapshot | `REQUIRED` | caller intent auditability |
| candidate scope snapshot | `REQUIRED` | Story 0005 candidate authority snapshot |
| eligibility decision snapshot | `REQUIRED` | exclusion provenance without recomputation |
| `status` | `REQUIRED` | exact Story 0006 orchestration state |
| `resolvedAt` | `REQUIRED` | snapshot timing |
| `createdAt` | `REQUIRED` | persistence lifecycle |
| `updatedAt` | `REQUIRED` | persistence lifecycle |
| `startedAt` | `DEFERRED_TO_0007` | richer lifecycle not needed for foundation |
| `completedAt` | `DEFERRED_TO_0007` except zero-child terminal case | only full lifecycle story needs rich terminal timestamps |

### ActiveScanMarket

| Field | Classification | Reason |
|---|---|---|
| row id or composite key | `REQUIRED_STORY_0006` | stable durable child row identity |
| `scanId` | `REQUIRED_STORY_0006` | parent link |
| `ordinal` | `REQUIRED_STORY_0006` | deterministic projection order |
| `marketId` | `REQUIRED_STORY_0006` | market identity |
| `eligible` | `REQUIRED_STORY_0006` | durable scope outcome |
| exclusion reasons snapshot | `REQUIRED_STORY_0006` | explicit diagnostics |
| `status` | `REQUIRED_STORY_0006` | exact per-market linkage/dispatch recovery state |
| nullable `analysisExecutionId` | `REQUIRED_STORY_0006` | durable eligible-child linkage |
| failure code/message | `DEFERRED_TO_0007` except minimal dispatch failure code if needed | richer diagnostics can wait |
| `createdAt` | `REQUIRED_STORY_0006` | persistence lifecycle |
| `updatedAt` | `REQUIRED_STORY_0006` | persistence lifecycle |

### Exact Story 0006 Status Vocabulary

#### ActiveScanStatus

- `READY_TO_DISPATCH`
  - created when the scan is persisted with at least one eligible market whose
    child linkage is durably registered but not yet fully handed off to
    dispatch
  - non-terminal
  - retryable
- `DISPATCH_REQUESTED`
  - entered when every eligible `ActiveScanMarket` has reached persisted
    per-market dispatch-requested state
  - non-terminal
  - retryable
- `COMPLETED_NO_WORK`
  - created immediately when `EffectiveScanScope = []`
  - terminal successful no-work scan
  - replay returns the same scan

#### ActiveScanMarketStatus

- `EXCLUDED`
  - created for ineligible markets from the Story 0005 decision snapshot
  - terminal
  - never dispatched
- `REGISTERED`
  - created for eligible markets once child `AnalysisExecution` is
    persisted/reused and linked
  - non-terminal
  - retryable
- `DISPATCH_REQUESTED`
  - entered before invoking child dispatch, in a separate durable update
  - non-terminal in Story 0006 vocabulary because later stories own final child
    outcome aggregation
  - retryable

## Relationship Model

- `REPOSITORY_CONFIRMED`: direct `ActiveScan -> List<AnalysisExecutionId>` would
  lose excluded-market diagnostics and orchestration state.
- `REPOSITORY_CONFIRMED`: explicit link rows best fit partial persistence,
  retries, zero-child exclusions and future diagnostics.
- `REPOSITORY_CONFIRMED`: smallest robust cardinality is:

```text
ActiveScan
 1
 ↓
 N
ActiveScanMarket
 0..1
 ↓
AnalysisExecution
```

## Transaction Boundary

- `REPOSITORY_CONFIRMED`: `ActiveScan`, `ActiveScanMarket` and
  `AnalysisExecution` will all live in the same `market-intelligence`
  application.
- `REPOSITORY_CONFIRMED`: `market-intelligence` uses one datasource, JPA and
  Flyway schema.
- `REPOSITORY_CONFIRMED`: current repositories use Spring-managed transactions.
- `INFERENCE`: Story 0006 can atomically persist:
  `ActiveScan` + `ActiveScanMarket` + registered/reused `AnalysisExecution`
  linkage in one local transaction.

## Dispatch and Crash-Consistency Gap

### Current Gap

- `REPOSITORY_CONFIRMED`: current create flow dispatches immediately after save.
- `REPOSITORY_CONFIRMED`: if scan/link rows are added outside that seam, child
  dispatch can happen before durable linkage exists.
- `REPOSITORY_CONFIRMED`: current persistence has no `@Version` field, no
  documented pessimistic lock, and no conditional dispatch-claim update on
  `analysis_executions`.
- `REPOSITORY_CONFIRMED`: current `LocalAnalysisExecutionDispatcher.dispatch(...)`
  simply submits work and the async task performs normal status transitions
  later.
- `REPOSITORY_CONFIRMED`: therefore two concurrent threads could currently both
  read `REQUESTED` and both invoke dispatch if Story 0006 added only a naive
  in-memory guard.

### Safe Story 0006 Requirement

- `REPOSITORY_CONFIRMED`: child analyses must not dispatch before their
  `ActiveScan` linkage is durably committed.
- `INFERENCE`: Story 0006 needs a register-then-dispatch seam plus persisted
  per-market state updated before dispatch invocation.
- `INFERENCE`: Story 0006 also needs an atomic persisted child dispatch claim;
  a simple load-and-check of `AnalysisExecution.status == REQUESTED` is not
  concurrency-safe.

### Recommended Small Enrichment

- `INFERENCE`: add an application seam that:
  1. resolves reusable child by deterministic key;
  2. persists/registers child execution without dispatch when needed;
  3. returns the persisted child identity for linkage;
  4. claims the child for dispatch through a persisted conditional transition
     `REQUESTED -> ACCEPTED`;
  5. dispatches only after commit through an explicit application action;
  6. starts actual async processing only if the dispatcher task can atomically
     advance `ACCEPTED -> CONTEXT_BUILDING`.

This is smaller and clearer than rewriting the dispatcher or introducing a new
message bus.

## Exact Dispatch / Crash Semantics

### Before-Commit Invariant

- `REPOSITORY_CONFIRMED`: inside the create transaction, Story 0006 can persist
  `ActiveScan`, all `ActiveScanMarket` rows, and all eligible child
  `AnalysisExecution` links before any dispatch occurs.

### Crash Before After-Commit Dispatch

- `INFERENCE`: if commit succeeds and the process crashes before any after-commit
  handoff runs, the scan remains persisted with eligible markets in
  `REGISTERED`.
- `INFERENCE`: retrying the same logical scan request reuses the existing scan,
  reuses the existing child analyses via deterministic child keys, and resumes
  only markets still in `REGISTERED` or safely re-handoffable
  `DISPATCH_REQUESTED` state.

### Crash After Dispatch-State Update But Before Invocation

- `INFERENCE`: the dispatch coordinator should first persist
  `ActiveScanMarketStatus.DISPATCH_REQUESTED` and atomically claim the child
  execution by transitioning `AnalysisExecutionStatus.REQUESTED ->
  ACCEPTED`, then invoke child dispatch.
- `INFERENCE`: if the process crashes after the status update but before
  invocation, retry observes `DISPATCH_REQUESTED`; if the child
  `AnalysisExecution` is already `ACCEPTED`, retry may invoke dispatcher again,
  but only one async task can actually start processing because processing start
  is guarded by an atomic `ACCEPTED -> CONTEXT_BUILDING` transition.

### Crash After Invocation

- `INFERENCE`: if dispatch was invoked and the process crashes before any later
  bookkeeping, retry again observes `DISPATCH_REQUESTED`.
- `INFERENCE`: safe re-invocation depends on two persisted guards:
  - dispatch claim is atomic through `REQUESTED -> ACCEPTED`
  - async processing start is atomic through `ACCEPTED -> CONTEXT_BUILDING`
- `INFERENCE`: Story 0006 delivery semantics are
  `AT_LEAST_ONCE_IDEMPOTENT`, not exactly-once.

### Duplicate Dispatch Safety

- `REPOSITORY_CONFIRMED`: current `LocalAnalysisExecutionDispatcher` alone is
  not sufficient proof of duplicate-dispatch safety.
- `INFERENCE`: Story 0006 must therefore add an atomic repository-supported
  dispatch-claim update on `analysis_executions`, plus an atomic
  dispatcher-start transition.
- `INFERENCE`: the logical child creation invariant remains exactly-once, while
  dispatch handoff is at-least-once idempotent.

### Exact Post-Commit Write Boundary

- `INFERENCE`: `TransactionSynchronization.afterCommit()` should call a
  dedicated dispatch coordinator.
- `INFERENCE`: that coordinator should call a dedicated
  `@Transactional(propagation = REQUIRES_NEW)` claim method per eligible child.
- `INFERENCE`: the `REQUIRES_NEW` method should atomically:
  1. transition `ActiveScanMarket REGISTERED -> DISPATCH_REQUESTED`
  2. conditionally transition child `AnalysisExecution REQUESTED -> ACCEPTED`
  3. transition parent `ActiveScan READY_TO_DISPATCH -> DISPATCH_REQUESTED`
     when the first eligible child is durably claimed
- `INFERENCE`: if the `REQUIRES_NEW` transaction fails, dispatcher invocation
  must not occur and the child remains retryable from the previously committed
  state.
- `INFERENCE`: this yields two distinct guarantees:
  - exact logical child creation through durable `(scanId, marketId)` linkage
    and deterministic child idempotency
  - at-least-once dispatch handoff guarded by atomic persisted child-state
    claims

### Exact Meaning of `ActiveScanStatus.DISPATCH_REQUESTED`

- `INFERENCE`: in Story 0006, `ActiveScanStatus.DISPATCH_REQUESTED` means:
  **dispatch phase has started because at least one eligible child has been
  durably claimed for handoff**.
- `INFERENCE`: it does not mean all children have completed, all children have
  been invoked, or full scan lifecycle aggregation has occurred.

### Final Crash / Retry Table

- `INFERENCE`: CASE A, crash before T1 commit
  - `ActiveScan`: not committed
  - `ActiveScanMarket`: not committed
  - `AnalysisExecution`: no committed new child linkage from this request
  - retry: recreates or replays the full scan request normally
  - duplicate handoff: no
  - safe because the transaction rolls back atomically
- `INFERENCE`: CASE B, T1 committed and crash occurs before after-commit callback
  - `ActiveScan`: `READY_TO_DISPATCH`
  - eligible `ActiveScanMarket`: `REGISTERED`
  - excluded `ActiveScanMarket`: `EXCLUDED`
  - child `AnalysisExecution`: persisted and still `REQUESTED`
  - retry: reuses scan, reuses children, resumes only resumable eligible rows
  - duplicate handoff: no committed handoff yet
  - safe because durable linkage already exists
- `INFERENCE`: CASE C, callback begins and crash occurs before `REQUIRES_NEW`
  claim commit
  - `ActiveScan`: still `READY_TO_DISPATCH`
  - eligible `ActiveScanMarket`: still `REGISTERED`
  - child `AnalysisExecution`: still `REQUESTED`
  - retry: behaves exactly like CASE B
  - duplicate handoff: no committed claim yet
  - safe because no durable claim was recorded
- `INFERENCE`: CASE D, dispatch claim committed and crash occurs before
  dispatcher invocation
  - `ActiveScan`: `DISPATCH_REQUESTED`
  - claimed `ActiveScanMarket`: `DISPATCH_REQUESTED`
  - child `AnalysisExecution`: `ACCEPTED`
  - retry: may re-invoke dispatcher for the already claimed child
  - duplicate handoff: invocation can repeat
  - safe because actual processing start is still guarded by atomic
    `ACCEPTED -> CONTEXT_BUILDING`
- `INFERENCE`: CASE E, dispatcher invoked and process crashes immediately
  afterward
  - `ActiveScan`: `DISPATCH_REQUESTED`
  - claimed `ActiveScanMarket`: `DISPATCH_REQUESTED`
  - child `AnalysisExecution`: `ACCEPTED` or already advanced by the worker
  - retry: may invoke dispatcher again if the child did not advance beyond the
    guarded start transition
  - duplicate handoff: possible at invocation level
  - safe because only one path can atomically enter processing
- `INFERENCE`: CASE F, two concurrent retries attempt to resume the same child
  - `ActiveScan`: shared persisted scan
  - `ActiveScanMarket`: one row can win the durable claim path at a time
  - child `AnalysisExecution`: only one claim can win `REQUESTED -> ACCEPTED`;
    repeated handoff after `ACCEPTED` is still safe because only one path can
    win `ACCEPTED -> CONTEXT_BUILDING`
  - retry: losers re-read persisted state and continue without creating a new
    child
  - duplicate handoff: possible, duplicate logical child creation: impossible
  - safe because creation and processing-start guarantees are separated and
    explicit

## Empty Effective Scope

- `REPOSITORY_CONFIRMED`: Story 0005 truthfully supports empty effective scope.
- `REPOSITORY_CONFIRMED`: ADR-033 requires persistence for auditability and
  idempotency.
- `INFERENCE`: Story 0006 should persist a zero-child `ActiveScan` with a
  terminal no-work state rather than reject the scan or silently skip
  persistence.

## API Boundary

- `REPOSITORY_CONFIRMED`: existing analysis creation uses `POST
  /api/v1/intelligence/analyses`, `Idempotency-Key`, `202 Accepted` and a
  `Location` header.
- `INFERENCE`: Story 0006 should mirror this style for `POST
  /api/v1/intelligence/scans`.
- `INFERENCE`: a minimal `GET /api/v1/intelligence/scans/{scanId}` read model is
  sufficient for durable creation/state verification in Story 0006.
- `INFERENCE`: full result, cancellation and lifecycle projection should be
  deferred to Story 0007.

## Security / Ownership

- `REPOSITORY_CONFIRMED`: Story 0005 already verifies selected account
  ownership authoritatively via `trading-core`.
- `INFERENCE`: persisting `actorId` directly on `ActiveScan` is the safer
  minimum model for future `GET` / retry / cancel ownership enforcement.
- `REPOSITORY_CONFIRMED`: ownership must not rely only on opaque scan UUIDs.

## Boundaries That Must Remain Unchanged

### PipelineRun

- `REPOSITORY_CONFIRMED`: `PipelineRun` remains one-analysis pipeline provenance
  with unique `(analysis_execution_id, pipeline_version)`.
- `REPOSITORY_CONFIRMED`: Story 0006 must not modify its semantics.

### Observation / Opportunity

- `REPOSITORY_CONFIRMED`: Observation creation remains downstream in the
  existing intelligence pipeline.
- `REPOSITORY_CONFIRMED`: Opportunity creation remains downstream in
  `ProductionIntelligencePipeline` and `OpportunityEngine`.
- `REPOSITORY_CONFIRMED`: sufficient lineage already exists through
  `analysisExecutionId` and `PipelineRun` for Story 0007 result projection.

### Risk / Broker / Passive / AI

- `REPOSITORY_CONFIRMED`: Story 0006 does not require `TradePlanRiskEvaluation`
  or any Risk Domain change.
- `REPOSITORY_CONFIRMED`: Story 0006 does not require broker order execution or
  Broker Service changes.
- `REPOSITORY_CONFIRMED`: `PassiveAnalysisStrategy` remains account-agnostic and
  must remain unchanged.
- `REPOSITORY_CONFIRMED`: orchestration is deterministic; no LLM authority is
  required.

## Persistence Impact

### Expected New Tables

- `active_scans`
- `active_scan_markets`

### Expected Core Constraints

- unique `(actor_id, idempotency_key)` scan identity;
- unique `(scan_id, market_id)` child identity;
- FK `active_scan_markets.scan_id -> active_scans.scan_id`;
- nullable `analysis_execution_id` for excluded markets;
- index on `(actor_id, scan_id)` or equivalent ownership lookup;
- index on `(scan_id, ordinal)` or equivalent deterministic projection;
- non-unique `request_fingerprint` column on `active_scans` for replay equality
  verification;
- optional unique index on `analysis_execution_id` in the link table if one
  child execution must never belong to multiple scan markets.

## Rejected Alternatives

| Alternative | Verdict | Reason |
|---|---|---|
| Put `ActiveScan` in `trading-core` | Rejected | scope/orchestration belongs to market-intelligence |
| Make `AnalysisExecution` multi-market | Rejected | violates ADR-033 |
| Reuse `PipelineRun` as scan aggregate | Rejected | violates provenance boundary |
| Dispatch inside the registration transaction | Rejected | violates durable-link-before-dispatch invariant |
| Introduce Kafka/outbox immediately | Rejected for Story 0006 | not required by current repository evidence |
| Store only effective markets | Rejected | loses exclusion auditability |
| Full lifecycle/result/cancel scan feature now | Rejected | deferred to Story 0007 |

## Story 0006 Coherence Verdict

- `REPOSITORY_CONFIRMED`: reduced Story 0006 scope is coherent if limited to:
  persisted scan, persisted market links, scope snapshot, scan/child
  idempotency, durable linkage, after-commit dispatch, minimal API and tests.
- `REPOSITORY_CONFIRMED`: lifecycle aggregation, cancellation fan-out and rich
  results remain better scoped to Story 0007.
- `REPOSITORY_CONFIRMED`: no new ADR is required if implementation stays inside
  ADR-033.
