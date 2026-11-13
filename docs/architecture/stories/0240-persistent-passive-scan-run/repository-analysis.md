# Repository Analysis - Story 0240

## Metadata

- **Story:** `0240 - Persist the PassiveScanRun Lifecycle`
- **Status:** Repository Analysis complete - implementation not authorized
- **Analysis date:** 2026-11-13
- **Repository:** `trading-os`
- **Affected module:** `market-intelligence`
- **Implementation Plan:** Not created; the Repository Analysis approval gate is
  still pending.

## Scope and Authority

This analysis is based on the current repository at `main`, accepted ADR-102,
ADR-033, ADR-023 and ADR-057, approved Epic 0238, Story 0240, Story 0239 and
the current ActiveScan and AnalysisExecution implementations.

The repository is authoritative for current physical behavior. ADR-102 and Epic
0238 establish the planning and architectural baseline, but they do not
authorize Story 0240 implementation, migrations, runtime scheduling or scan
execution.

No application code, migration, runtime configuration, scheduler, broker call,
AI invocation or scan execution was performed for this analysis.

## Repository Baseline

- `HEAD`: `76480d09c706b348aa377e5c6ab3f76f5315e620`
- `HEAD` is the merge commit for PR #291 and is aligned with `origin/main`.
- Current branch: `main`.
- Tracked worktree changes: none.
- Untracked worktree paths preserved without modification:
  - `docs/architecture/stories/0238-v1-market-scanning-opportunity-discovery/`
  - `docs/architecture/stories/0240-persistent-passive-scan-run/`
  - `docs/architecture/stories/0241-bounded-passive-dispatch/`
  - `docs/architecture/stories/0242-passive-awareness-lifecycle/`
  - `docs/architecture/stories/0243-passive-to-active-investigation/`
  - `docs/architecture/stories/0244-passive-awareness-dashboard/`
- The untracked paths were present before this analysis and are not treated as
  implementation evidence.

## Engineering Context

### DevLog

No DevLog MCP context or repository-local DevLog mapping was available in this
workflow. The analysis therefore uses repository artifacts and current source
only; no historical decision is inferred from the missing DevLog context.

### Obsidian

The vault was queried for `PassiveScanRun` and `Story 0240`; no relevant notes
were returned. The vault did not contribute project evidence.

### Governing Artifacts

- `docs/architecture/adr/ADR-102-proposed-passive-market-discovery-and-active-opportunity-investigation.md`
  is accepted and defines the passive run and child lifecycle.
- `docs/architecture/adr/ADR-033.md` preserves the Active/Passive boundary,
  single-market `AnalysisExecution` semantics and durable orchestration
  responsibility.
- `docs/architecture/adr/ADR-023.md` requires explicit execution lifecycle,
  durable retry attempts and no hidden orchestration inside capabilities.
- `docs/architecture/adr/ADR-057.md` keeps historical evidence ownership in
  Market Data and does not authorize historical backfill from operational
  passive monitoring.
- `docs/architecture/stories/0238-v1-market-scanning-opportunity-discovery/story.md`
  approves the roadmap baseline but not individual Story implementation.
- `docs/architecture/stories/0239-passive-market-universe/story.md` and its
  implementation artifacts provide the resolved-universe contract consumed by
  this Story.

## Story 0239 Verification

Story 0239 was implemented on the dedicated branch, reviewed independently,
validated by local tests and `mvn -q verify`, pushed as commit `01ea2067`, and
merged by PR #291 into `main` with merge commit
`76480d09c706b348aa377e5c6ab3f76f5315e620`.

The implementation available on `main` includes:

- `PassiveMarketUniverseProperties` with startup validation;
- `PassiveMarketUniverseResolver`;
- immutable `PassiveMarketUniverse`;
- immutable `PassiveMarketEntry`;
- immutable `PassiveMarketUniverseDiagnostic`.

The resolver returns exact canonical `marketId`, provider and product identity,
cadence, minimum evidence profile, deterministic ordering, configuration
version and versioned SHA-256 fingerprint. It returns diagnostics for disabled,
unknown, conflicting and unavailable catalogue entries without guessing an
identity.

Story 0240 must consume only this resolved application contract. It must not
read Spring properties or re-resolve the Market Data catalogue. The persisted
run must snapshot the resolved entries and fingerprint so later configuration
changes cannot alter an existing run.

### Story 0239 Documentation Reconciliation

The Story 0239 documents initially contained older
implementation-review-pending wording even though PR #291 was merged after the
checks passed and the final independent review reported no material findings.
This documentation discrepancy was reconciled minimally during this workflow
by recording the merge and review evidence without changing acceptance
checkboxes to claims that were not independently revalidated in this analysis.

## Existing Architecture Findings

### ActiveScan Aggregate

Relevant paths:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScan.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScanMarket.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScanStatus.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/scan/ActiveScanMarketStatus.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanApplicationService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanCreationTransaction.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/ActiveScanRepository.java`

Confirmed reusable patterns:

- immutable domain objects with explicit factory/restore methods;
- a persisted parent aggregate above single-market executions;
- separate child rows preserving ordinal, identity, state and execution linkage;
- parent and child state transitions represented by explicit methods;
- an application service that computes a request fingerprint and handles replay;
- a creation transaction that persists parent, children and registered analysis
  executions before dispatch;
- empty effective scope represented by a durable terminal no-work parent;
- repository methods for conditional state transitions.

ActiveScan-specific behavior that must not be copied into Story 0240:

- actor, account, broker-account and objective ownership;
- account-aware scope resolution and market eligibility;
- `ActiveScanMarket` exclusion reasons;
- ActiveScan public REST ownership and response projections;
- `scanId` and `scanMarketId` as ActiveScan provenance fields.

### ActiveScan Dispatch and Reconciliation

Relevant paths:

- `ActiveScanDispatchClaimService`;
- `ActiveScanDispatchCoordinator`;
- `ActiveScanReconciliationService`;
- `JpaActiveScanRepository`;
- `SpringDataActiveScanRepository`;
- `SpringDataActiveScanMarketRepository`.

The current claim pattern is a `REQUIRES_NEW` transaction using conditional
updates:

1. load the child and verify parent ownership;
2. transition child `REGISTERED -> DISPATCH_REQUESTED` conditionally;
3. conditionally claim the linked `AnalysisExecution` using
   `REQUESTED -> ACCEPTED`;
4. transition the parent when dispatch begins;
5. invoke the dispatcher only after the claim transaction returns.

The coordinator resumes work asynchronously on a dedicated executor. The
current implementation is a useful local handoff pattern, but its current
ActiveScan ownership checks and state vocabulary are not passive contracts.

The existing ActiveScan reconciliation service derives terminal state by reading
child executions and pipeline provenance. Story 0240 can reuse the principle of
reconciliation from authoritative child records, but must not reuse the
account-specific projection or opportunity presentation logic.

### AnalysisExecution

Relevant paths:

- `domain/AnalysisExecutionMode.java`;
- `domain/IntelligenceAnalysisRequest.java`;
- `domain/execution/AnalysisExecution.java`;
- `domain/execution/AnalysisExecutionStatus.java`;
- `domain/execution/AnalysisExecutionProvenance.java`;
- `domain/execution/IdempotencyKey.java`;
- `application/execution/AnalysisExecutionService.java`;
- `application/port/AnalysisExecutionRepository.java`;
- `adapter/persistence/JpaAnalysisExecutionRepository.java`;
- `adapter/persistence/SpringDataAnalysisExecutionRepository.java`;
- `adapter/persistence/JpaAnalysisExecutionEntity.java`.

Confirmed behavior:

- `AnalysisExecution` remains one-market and supports `PASSIVE` mode;
- `AnalysisExecutionService.register` creates or reuses an execution without
  dispatching it;
- `AnalysisExecutionService.create` registers, claims and dispatches immediately;
- `AnalysisExecutionService.claimForDispatch` performs an atomic conditional
  `REQUESTED -> ACCEPTED` transition;
- the database has a globally unique `analysis_executions.idempotency_key`;
- `AnalysisExecutionService.dispatchRegistered` reconstructs the request from
  persisted provenance;
- `LocalAnalysisExecutionDispatcher` protects actual processing start with an
  atomic `ACCEPTED -> CONTEXT_BUILDING` transition;
- `AnalysisExecutionRepository.requeueAcceptedForRecovery` provides a
  conditional stale-accepted recovery primitive;
- failed, cancelled and expired executions are terminal, while partial
  completion can continue to a final completed state.

Story 0240 should call `register`, not `create`, while creating a run. This
ensures that the parent and child linkage can commit before any dispatch handoff.
The passive request must use `mode=PASSIVE`, `accountId=null`,
`scanId=null`, and `scanMarketId=null`; the PassiveScanRun child row is the
authoritative passive relationship. Reusing ActiveScan's scan fields would leak
an ActiveScan-specific meaning into passive provenance.

### PassiveAnalysisStrategy and Pipeline

Relevant paths:

- `application/strategy/PassiveAnalysisStrategy.java`;
- `application/pipeline/ProductionIntelligencePipeline.java`;
- `application/execution/LocalAnalysisExecutionDispatcher.java`.

`PassiveAnalysisStrategy` is already account-independent and selects the
existing deterministic capabilities. It must remain unchanged by Story 0240.
The production pipeline already creates observations, market theses and
`TradingOpportunity(PASSIVE_SCAN)` where existing deterministic invariants
allow it. Story 0240 only records the orchestration lifecycle and execution
linkage; it must not inspect or create opportunities itself.

An analysis completing without an opportunity is still a valid completed
analysis. It must not be converted into a run failure. Conversely, a failed or
insufficient-evidence execution must not be presented as a successful passive
child.

### Existing Persistence and Migrations

Relevant migrations:

- `market-intelligence/src/main/resources/db/migration/V2__production_intelligence_pipeline.sql`;
- `market-intelligence/src/main/resources/db/migration/V3__active_scan_orchestration_foundation.sql`;
- `market-intelligence/src/main/resources/db/migration/V20__historical_reconstruction_lifecycle.sql`.

Repository conventions are additive Flyway migrations named `V<number>__...sql`,
UUID identifiers, `TIMESTAMP WITH TIME ZONE`, explicit foreign keys, relational
uniqueness constraints and bounded text/JSON payload columns for snapshots or
diagnostics.

The current ActiveScan tables do not use JPA `@Version`; conditional updates on
expected state provide the concurrency guard. Historical reconstruction also
demonstrates persisted lease ownership using `lease_id` and conditional stale
claim updates. The passive design should reuse these mechanisms without adding a
generic workflow framework.

### Transaction Conventions

The current application uses Spring transactions at orchestration seams:

- `ActiveScanCreationTransaction.create` is `@Transactional`;
- `ActiveScanDispatchClaimService.claimForDispatch` is
  `@Transactional(REQUIRES_NEW)`;
- `ActiveScanReconciliationService.reconcileOwned` is `@Transactional`;
- persistence adapters use local transactional methods;
- `TransactionSynchronization.afterCommit()` is used for asynchronous
  post-commit handoff.

This is sufficient for a local transactional passive run creation and durable
claim boundary. Kafka, RabbitMQ, an outbox framework or distributed leader
election are not justified by current evidence and are outside Story 0240.

## Proposed PassiveScanRun Domain Model

### Aggregate Boundary

`PassiveScanRun` should be the aggregate root in `market-intelligence`.
`PassiveScanMarket` should be an owned child entity/value-backed persistence
row within that aggregate boundary, not an independently addressable aggregate.

Separate child rows are required because child state, execution linkage, leases,
attempts and diagnostics must be updated and claimed independently while the
parent remains reconstructible. This is the same parent/child persistence shape
used by ActiveScan, but without account ownership or ActiveScan semantics.

### Run Fields

The smallest durable run snapshot should contain:

| Field | Classification | Purpose |
|---|---|---|
| `runId` | required | stable aggregate identity |
| `idempotencyKey` | required | logical run replay key |
| `requestFingerprint` | required | detects same-key semantic conflicts |
| `triggerContext` | required | system/operator/internal trigger provenance |
| `plannedWindowStart` | required | immutable execution window context |
| `plannedWindowEnd` | required | immutable execution window context |
| `configurationVersion` | required | Story 0239 configuration provenance |
| `universeFingerprint` | required | exact resolved universe identity |
| `fingerprintAlgorithm` | required | fingerprint interpretation/version |
| `status` | required | run lifecycle |
| `requestedAt` | required | lifecycle audit |
| `dispatchRequestedAt` | nullable | first durable dispatch handoff |
| `startedAt` | nullable | first accepted/running child |
| `completedAt` | nullable | terminal lifecycle timestamp |
| `updatedAt` | required | reconciliation/concurrency audit |
| `diagnostics` | bounded snapshot | resolver and run-level diagnostics |
| `terminalSummary` | bounded snapshot | deterministic outcome counts |
| `version` | required | optimistic/concurrency version |

The run must not persist mutable account, broker-account, credential, user,
position or Market Data catalogue state.

### Child Fields

Each `PassiveScanMarket` snapshot should contain:

| Field | Classification | Purpose |
|---|---|---|
| `passiveScanMarketId` | required | stable child identity |
| `runId` | required | parent relationship |
| `ordinal` | required | stable resolved-universe order |
| `marketId` | required | canonical market identity |
| `provider` | required | exact Story 0239 provider snapshot |
| `productType` | required | exact Story 0239 product snapshot |
| `cadence` | required | cadence context used by future dispatch |
| `minimumEvidenceProfile` | required | evidence policy context |
| `status` | required | child lifecycle |
| `currentAnalysisExecutionId` | nullable | current physical attempt linkage |
| `attemptCount` | required | bounded retry accounting |
| `lastFailureCode` | nullable | bounded diagnostics |
| `lastFailureReason` | nullable | bounded diagnostics |
| `nextRetryAt` | nullable | future retry eligibility |
| `leaseId` | nullable | durable claim owner |
| `leaseExpiresAt` | nullable | recovery boundary |
| `dispatchRequestedAt` | nullable | handoff audit |
| `acceptedAt` | nullable | execution claim audit |
| `startedAt` | nullable | actual analysis start audit |
| `completedAt` | nullable | terminal child audit |
| `updatedAt` | required | lifecycle/concurrency audit |
| `version` | required | optimistic/concurrency version |

The child does not duplicate the Market Data catalogue. It stores only the
identity and selection policy fields already resolved by Story 0239.

### Physical Attempts

ADR-023 requires every physical retry to create a new
`CapabilityExecution`-level attempt. `AnalysisExecutionService` also treats
failed executions as non-reusable, so one child row cannot safely overwrite its
only execution ID when a retry is introduced.

The recommended schema therefore has a small
`passive_scan_market_attempts` table owned by `PassiveScanMarket`:

- `attempt_id` primary key;
- `passive_scan_market_id` foreign key;
- `attempt_number`;
- `analysis_execution_id` foreign key;
- `idempotency_key`;
- created/accepted/started/completed timestamps;
- bounded failure/diagnostic payload;
- unique `(passive_scan_market_id, attempt_number)`;
- unique `analysis_execution_id`;
- unique `(passive_scan_market_id, idempotency_key)`.

The child row keeps the current attempt ID and aggregate attempt count. This
preserves one logical market child while retaining every physical

## Lifecycle Rules

### Run States

Use the ADR-102 states without adding a new state:

```text
REQUESTED -> DISPATCH_REQUESTED -> RUNNING
RUNNING -> COMPLETED | PARTIALLY_COMPLETED | FAILED
REQUESTED -> COMPLETED_NO_WORK
```

Required semantics:

- `REQUESTED`: the run and immutable child snapshot are durably created; no
  child dispatch claim has been committed.
- `DISPATCH_REQUESTED`: at least one child has entered durable dispatch handoff;
  the run remains non-terminal while other children can still be registered or
  claimed.
- `RUNNING`: at least one child has been accepted or started and terminal
  reconciliation is not yet complete.
- `COMPLETED`: every child has a successful completed analysis outcome. A valid
  no-signal analysis remains successful if its analysis result is complete.
- `PARTIALLY_COMPLETED`: all children are terminal and at least one child is
  `COMPLETED`, while another is `FAILED`, `INSUFFICIENT_EVIDENCE` or `SKIPPED`.
- `FAILED`: no child produced a reliable completed result and at least one child
  failed or ended with insufficient evidence, or the resolved universe could
  not produce dispatchable work because of an error diagnostic.
- `COMPLETED_NO_WORK`: there are no child markets to process and no error
  diagnostic requiring a failed run. Empty configuration and all-disabled
  configuration are examples.

The implementation must allow a truthful initial `REQUESTED -> FAILED` outcome
when the resolved universe contains no entries because of an explicit resolver
error such as catalogue unavailability. This uses an existing run state rather
than inventing a new one and prevents operational failure from being reported
as no work.

Run terminal states are immutable. Reconciliation must never downgrade a run or
report success while any child is non-terminal.

### Child States

Use the ADR-102 child states:

```text
REGISTERED -> DISPATCH_REQUESTED -> ACCEPTED -> RUNNING
RUNNING -> COMPLETED | INSUFFICIENT_EVIDENCE | FAILED
REGISTERED -> SKIPPED
```

Required semantics:

- `REGISTERED`: snapshot and a registered `AnalysisExecution` linkage exist;
  no dispatch handoff has been claimed.
- `DISPATCH_REQUESTED`: durable dispatch intent/lease has been recorded before
  dispatcher invocation.
- `ACCEPTED`: the linked analysis execution has been atomically claimed as
  `ACCEPTED`; dispatch handoff may be retried safely.
- `RUNNING`: the linked execution has reached `CONTEXT_BUILDING`, `RUNNING` or
  a non-terminal partial execution state.
- `COMPLETED`: the linked execution is terminal `COMPLETED` with complete
  quality. It may contain no opportunity; opportunity existence is not the
  passive run success criterion.
- `INSUFFICIENT_EVIDENCE`: the execution cannot support a reliable passive
  result. The initial deterministic mapping should classify terminal
  `PARTIAL`/`DEGRADED` quality or an explicit evidence-unavailable result here,
  rather than silently treating it as success.
- `FAILED`: the linked execution is `FAILED`, `CANCELLED` or `EXPIRED`, or a
  durable linkage/consistency failure prevents reliable interpretation.
- `SKIPPED`: no execution is dispatched for this child because a deterministic
  pre-dispatch condition says it cannot be processed. The reason must be
  persisted. It is not an alias for failed execution.

Retries are physical attempts under one logical child. A retry must not create a
second `PassiveScanMarket` row. A new physical attempt must have a new

## Integration with Story 0239

`PassiveScanRunApplicationService.create` should accept a resolved
`PassiveMarketUniverse` and a small internal run request containing the logical
idempotency key, trigger context and planned window. It should snapshot:

- `configurationVersion`;
- `fingerprintAlgorithm`;
- `fingerprint`;
- `resolvedAt` as resolution provenance;
- the ordered `PassiveMarketEntry` list;
- resolver diagnostics;
- the planned window and trigger context.

For each entry, the child snapshot preserves exactly:

- canonical `marketId`;
- exact provider;
- exact product type;
- cadence;
- minimum evidence profile;
- ordinal.

The service must not load the future configuration again when reading or
reconciling a persisted run. It must not persist the entire Market Data
catalogue, provider symbols, credentials or mutable market state.

## Idempotency and Concurrency

### Logical Run Identity

The logical run identity is the exact `idempotencyKey` within the passive run
namespace. The key is supplied by the future scheduler boundary in Story 0241;
Story 0240 must not invent recurring scheduling semantics.

The persisted request fingerprint should include, in canonical order:

- idempotency namespace/version;
- trigger context;
- planned window start and end;
- universe configuration version;
- fingerprint algorithm;
- universe fingerprint;
- ordered resolved entry identities and selection fields.

The fingerprint must exclude creation timestamps and runtime diagnostics.

### Run Creation

The database must enforce `UNIQUE(idempotency_key)` for the passive namespace.
Creation follows the existing ActiveScan pattern:

1. calculate the request fingerprint;
2. load an existing run by idempotency key;
3. replay it only when the fingerprint matches;
4. reject a same-key different-fingerprint request with an explicit conflict;
5. otherwise create the parent, child snapshots, physical attempt records and
   registered passive executions in one local transaction;
6. handle a unique-key race by loading the winning row and applying the same
   fingerprint comparison.

### Child Identity

The logical child uniqueness constraint is `UNIQUE(run_id, market_id)`. The
resolved Story 0239 contract already rejects duplicate canonical identities and
conflicting provider/product declarations. Provider and product remain stored
on the row for audit and catalogue mismatch detection; they are not discarded
from the uniqueness model.

The initial physical execution key should be deterministic, for example:

```text
passive-scan-v1:{runId}:market:{marketId}:attempt:1
```

The exact key format is an implementation detail, but it must be versioned,
bounded and stable. A retry uses the same logical child and a new attempt suffix.

### Concurrent Creation and Claims

Application pre-checks improve replay behavior but are not concurrency guards.
The database uniqueness constraints and race recovery are mandatory.

Child dispatch claims must be conditional database operations, not Java
in-memory locks. A claim must check expected child state and an unexpired lease
in one update, set a new lease ID and expiry, and increment only the durable
attempt/claim metadata intended by the policy.

Run and child updates should use either:

- explicit compare-and-set updates by expected state plus version; or
- JPA `@Version` for ordinary updates combined with explicit conditional claim
  queries for dispatch/recovery.

The recommended approach is a `version BIGINT` column on both run and child,
with explicit expected-state/version updates for claims and reconciliation. It
matches current repository conventions while making stale writers observable.

## Transaction Boundaries

### Run Creation Transaction

One `@Transactional` application transaction must persist:

1. the run identity, request fingerprint and immutable universe metadata;
2. every ordered child snapshot;
3. the initial physical attempt row for each child;
4. each registered `AnalysisExecution(PASSIVE)`;
5. each child-to-execution linkage.

No dispatcher invocation, scheduler callback or external provider call may occur
inside this transaction. If any child or execution insert fails, the full run
creation rolls back and a retry can replay the logical key safely.

### Dispatch Handoff Transaction

Story 0240 should expose a durable claim seam for Story 0241. A future dispatch
operation should use a `REQUIRES_NEW` transaction to:

1. claim one eligible child lease;
2. transition child `REGISTERED -> DISPATCH_REQUESTED`;
3. atomically transition the linked execution `REQUESTED -> ACCEPTED`;
4. transition the run to `DISPATCH_REQUESTED` when appropriate;
5. commit before invoking `AnalysisExecutionDispatcher`.

The dispatcher invocation is outside the claim transaction. If the process dies
after commit and before invocation, the lease and accepted execution are
recoverable; the logical child is not recreated.

### Execution Association

Initial association belongs in the run creation transaction. A separate
association transaction is only needed for a future retry attempt, and it must
insert a new attempt row under the existing logical child with a deterministic
attempt key before dispatch.

### Outcome and Reconciliation

Child outcome recording must be conditional on the current lease/version and
must tolerate duplicate reconciliation. A repeated identical terminal outcome
returns the already persisted state; a conflicting terminal outcome is rejected
or recorded as an integrity diagnostic without overwriting the first terminal
fact.

Run finalization reads all child rows in one transaction, derives the terminal
status deterministically, and uses a conditional parent update. Concurrent
finalizers may race, but only one state transition can win and both must return
the durable winner.

## Restart and Recovery

### Restart Before Dispatch

The run remains `REQUESTED`; children remain `REGISTERED`; registered
`AnalysisExecution`s remain `REQUESTED`. A future Story 0241 recovery pass can
claim them without creating another run, child or execution.

### Restart After Dispatch Intent

The child remains `DISPATCH_REQUESTED` with its lease and the linked execution
is `ACCEPTED`. Recovery checks lease expiry and re-hands off the same execution
or safely requeues the stale accepted execution using the existing conditional
`requeueAcceptedForRecovery` primitive. It must never create a second logical
child.

### Restart During Analysis

The child remains `RUNNING` while the linked execution is non-terminal. The
future recovery/reconciliation path reloads the execution and continues to
observe it. An expired execution becomes a deterministic failure or a bounded
new physical attempt according to the Story 0241 retry policy.

### Completion Before Reconciliation

The child remains non-terminal only until the persisted `AnalysisExecution`
terminal status is observed and recorded. Reconciliation is idempotent and can
be rerun after restart. Existing Observation, MarketThesis, PipelineRun and
TradingOpportunity history remains authoritative; the passive run only stores
the outcome classification and references.

### Expired Leases

An expired lease can be claimed by a new recovery owner only through a
conditional database update. The old owner cannot write after lease loss. Any
late completion must be ignored unless the child still matches the owner token
and version.

### Missing Linkage

Missing child-to-execution linkage is a durable consistency failure, not a new
execution creation opportunity during reconciliation. The child must become
`FAILED` with a bounded diagnostic, or remain retryable only through an explicit
new-attempt transaction that first repairs the missing durable association.

Story 0240 provides the state and claim primitives. Story 0241 owns recurring
selection, retry/backoff policy, concurrency budgets, lease recovery workers and
actual dispatcher activation.

## Persistence Design

### Recommended Additive Tables

1. `passive_scan_runs`
2. `passive_scan_markets`
3. `passive_scan_market_attempts` if retries are included in this Story; this
   is recommended for durable physical execution lineage.

No existing ActiveScan table should be modified.

### `passive_scan_runs` Constraints and Indexes

- primary key on `run_id`;
- unique passive namespace idempotency key;
- non-unique index on `(status, updated_at)` for recovery/read projections;
- non-unique index on `(planned_window_start, planned_window_end)` if future
  due-run lookup requires it;
- bounded `request_fingerprint`, `universe_fingerprint` and algorithm/version
  columns;
- explicit foreign-key-free JSON/text diagnostics because diagnostics are a
  bounded snapshot, not a relational authority.

### `passive_scan_markets` Constraints and Indexes

- primary key on `passive_scan_market_id`;
- foreign key to `passive_scan_runs(run_id)`;
- unique `(run_id, market_id)`;
- foreign key to the current `analysis_executions(execution_id)` when present;
- unique current execution linkage if one current execution may belong to only
  one logical child;
- index `(run_id, ordinal)` for deterministic reads;
- index `(status, lease_expires_at, next_retry_at)` for future claims;
- bounded provider/product/status/failure fields;
- version column for compare-and-set updates.

### `passive_scan_market_attempts` Constraints and Indexes

- primary key on `attempt_id`;
- foreign key to `passive_scan_markets(passive_scan_market_id)`;
- foreign key to `analysis_executions(execution_id)`;
- unique `(passive_scan_market_id, attempt_number)`;
- unique `analysis_execution_id`;
- index `(passive_scan_market_id, attempt_number)`;
- index `(status, lease_expires_at)` if attempt claim state is stored there.

JSON/text is appropriate for bounded diagnostics and the immutable run
provenance envelope. Identity, status, timestamps, attempts, leases and
idempotency fields must remain relational so constraints and conditional updates
are enforceable by PostgreSQL.

### Migration

The implementation must add one or more sequential Flyway migrations after
`V28__research_hypotheses.sql`, using the repository's existing naming and
`TIMESTAMP WITH TIME ZONE` conventions. No migration is created by this
analysis.

## Application Boundaries

The minimum application boundary should be one passive orchestration service
plus repository ports, not one service per operation:

### Creation and Read

- create/replay a run from a resolved `PassiveMarketUniverse`;
- load a run by ID or idempotency key;
- load children in ordinal order;
- expose a bounded internal read projection for operational status.

### Durable Lifecycle Commands

- claim a child for dispatch with lease/version conditions;
- associate or create a new physical attempt under one logical child;
- record accepted/running/terminal child outcome;
- reconcile run status from child outcomes;
- recover an expired lease without creating duplicate logical work.

These are internal application seams for Story 0241 and future operational
readers. Story 0240 must not add a public REST endpoint, user ownership model,
account authorization or scheduler activation.

## Story 0241 Handoff

Story 0241 should receive these durable capabilities from Story 0240:

- create/replay a run using the immutable resolved-universe snapshot;
- select children by persisted cadence context and state;
- claim a child with a durable lease and compare-and-set state transition;
- obtain the current physical `AnalysisExecution(PASSIVE)` request;
- requeue/recover stale accepted handoffs safely;
- record terminal child outcomes idempotently;
- finalize truthful run status;
- query bounded operational counts and diagnostics.

Story 0241 owns the five-minute-or-larger scheduler check, due selection,
concurrency/provider/retry budgets, lease recovery worker, dispatcher handoff and
runtime enablement. Story 0240 must leave these capabilities inert unless called
by a future approved scheduler boundary.

## Completion and Failure Semantics

The deterministic aggregation table is:

| Child terminal set | Run status |
|---|---|
| zero children, no error diagnostic | `COMPLETED_NO_WORK` |
| all children `SKIPPED` | `COMPLETED_NO_WORK` |
| all children `COMPLETED` | `COMPLETED` |
| at least one `COMPLETED` plus any `FAILED`, `INSUFFICIENT_EVIDENCE` or `SKIPPED` | `PARTIALLY_COMPLETED` |
| no `COMPLETED`, at least one `FAILED` or `INSUFFICIENT_EVIDENCE` | `FAILED` |
| any child non-terminal | no terminal run status; remain `DISPATCH_REQUESTED` or `RUNNING` |

`SKIPPED` is explicit non-work, not successful analysis. `INSUFFICIENT_EVIDENCE`
is explicit non-positive evidence, not `COMPLETED`. A valid completed analysis
with no strategy match remains `COMPLETED`; candidate creation is not the run
completion criterion.

## Testing Strategy

### Domain Tests

- valid run and child factory invariants;
- immutable universe/version/fingerprint snapshot;
- exact provider/product identity preservation;
- duplicate child identity rejection;
- all legal run and child transitions;
- invalid backward and terminal transitions rejected;
- deterministic terminal aggregation for all rows above;
- no-work versus resolution-error distinction;
- insufficient evidence versus successful no-signal analysis;
- partial completion preserving successful siblings;
- bounded diagnostic lengths and immutable collections.

### Persistence and Transaction Tests

- atomic parent/child/initial-attempt creation;
- rollback when any execution registration or child insert fails;
- unique run idempotency race with same fingerprint replay;
- same key with different universe fingerprint rejected;
- unique `(run_id, market_id)` race;
- unique attempt and execution linkage constraints;
- child state compare-and-set under concurrent claims;
- optimistic version conflict handling;
- stable execution association after reload;
- migration creation and foreign-key compatibility.

### Recovery Tests

- restart before dispatch leaves `REQUESTED`/`REGISTERED` state;
- restart after durable dispatch claim allows safe re-handoff;
- restart during analysis reconstructs non-terminal child state;
- expired lease can be reclaimed by one owner only;
- late owner cannot overwrite a newer lease/version;
- duplicate terminal reconciliation is idempotent;
- missing execution linkage produces bounded failure diagnostics;
- exhausted attempts do not create unbounded physical executions.

### Safety Tests

- no `accountId`, broker account or user identity is required;
- no call to Trading Core, Broker Service or Risk Domain;
- no AI engine invocation;
- no scheduler bean activation or recurring work;
- no historical reconstruction or backfill call;
- no public endpoint or authorization expansion;
- passive execution remains `AnalysisExecutionMode.PASSIVE`.

### Deferred to Story 0241

- scheduler wake-up and due cadence selection;
- concurrency and provider budgets;
- recurring retry/backoff policy;
- runtime lease-recovery worker;
- actual dispatcher activation from scheduled work;
- deployment enablement and operational metrics for recurring scans.

## Acceptance Criteria Mapping

| Story 0240 criterion | Repository Analysis design |
|---|---|
| one logical run key creates one run | unique idempotency key plus fingerprint replay/conflict |
| same request does not duplicate children | unique child identity and deterministic execution keys |
| same key with different fingerprint rejected | persisted request fingerprint comparison |
| identity preserved | relational child snapshot of all Story 0239 identity fields |
| durable before dispatch | one local creation transaction and no in-transaction dispatcher call |
| truthful run states | deterministic terminal aggregation table |
| insufficient evidence explicit | dedicated child state and conservative quality mapping |
| restart reconstruction | parent, child, attempt, execution, lease and version fields |
| concurrent claims safe | conditional DB updates, lease token and version constraints |
| persistence/idempotency/state tests | targeted matrix above plus full module validation after implementation |

## Risks and Open Decisions

### Material Implementation Decisions

1. **Retry lineage:** the recommended attempt table is required if Story 0240
   persists physical retries. If retries are explicitly deferred to Story 0241,
   the schema may be split, but the handoff must preserve this decision and not
   overwrite prior execution lineage.
2. **Insufficient-evidence classification:** the recommended V1 policy maps
   terminal non-complete quality and explicit evidence-unavailable outcomes to
   `INSUFFICIENT_EVIDENCE`. This must be encoded in one deterministic mapper,
   not inferred by the scheduler.
3. **Execution recovery timing:** the existing passive strategy timeout and the
   existing accepted-dispatch recovery lease must be checked together. A future
   implementation must not let a lease outlive an execution in a way that
   causes repeated stale handoffs or false failure classification.
4. **Trigger and planned-window vocabulary:** Story 0241 must define the exact
   system-owned trigger context and window key format before enabling recurring
   creation. Story 0240 should store the fields without coupling to a scheduler
   implementation.

### Non-Blocking Risks

- The current `AnalysisExecution` persistence uses a JSON payload for rich
  provenance and no generic JPA version field. Passive claim updates should use
  explicit compare-and-set SQL rather than assuming entity dirty checking is
  sufficient.
- The current local dispatcher is in-process and not a durable queue. Story
  0240 can make intent durable and recovery-safe, but exactly-once external
  dispatch is not achievable and must not be claimed.
- Operational status projection must remain bounded; it must not load complete
  observations, theses or opportunity payloads for every run read.

## Implementation Sequencing After Approval

The coding agent should implement in this order after both required approvals:

1. Domain contracts, invariants, lifecycle transitions and deterministic outcome
   aggregation.
2. Additive Flyway schema and JPA persistence entities/repositories with
   uniqueness, foreign keys, indexes and version fields.
3. Atomic run creation from `PassiveMarketUniverse`, including immutable child
   snapshot and registered passive execution linkage.
4. Idempotent replay/conflict handling and duplicate-race tests.
5. Durable child claim, lease and compare-and-set transition seams, without
   scheduler activation.
6. Idempotent child outcome recording and run reconciliation.
7. Restart reconstruction and stale-lease recovery primitives.
8. Bounded internal read projection, focused persistence tests, migration tests
   and full `market-intelligence` validation.

## Explicit Non-Goals

- recurring scheduler invocation;
- scheduler enablement or deployment configuration;
- provider request budgets and rate limiting;
- dashboard or public REST API;
- passive candidate aggregate or awareness projection;
- ActiveScan modifications;
- account eligibility, broker synchronization, Risk or Trade Plan operations;
- AI invocation or interpretation;
- historical backfill or research execution;
- global opportunity ranking;
- notifications;
- Kafka, RabbitMQ, outbox infrastructure or generic workflow engine;
- changing the meaning of existing `AnalysisExecution`, `PipelineRun` or
  ActiveScan records.

## Readiness Verdict

`READY_FOR_REPOSITORY_ANALYSIS_APPROVAL`

The repository contains the required orchestration, persistence and execution
patterns for a controlled implementation in `market-intelligence`. The next
workflow gate is explicit human approval of this Repository Analysis. No
Implementation Plan has been created because the Engineering Story workflow
requires that approval before planning.

## Validation

- Repository baseline and Git state inspected.
- ADR-102, ADR-033, ADR-023 and ADR-057 inspected.
- Epic 0238, Story 0239 and Story 0240 inspected.
- ActiveScan, AnalysisExecution, PassiveAnalysisStrategy, pipeline, persistence,
  transaction and migration patterns inspected.
- Obsidian targeted search completed with no relevant result.
- No application tests were run because this is an analysis-only task and no
  application code was changed.
