# Implementation Plan - Story 0240

## Overview

Implement the durable passive orchestration boundary in `market-intelligence`.
The change adds a `PassiveScanRun` aggregate with ordered
`PassiveScanMarket` children, immutable `PassiveMarketUniverse` snapshots,
deterministic idempotency, durable linkage to registered
`AnalysisExecution(PASSIVE)` records, conditional lifecycle transitions and a
bounded operational read projection.

The implementation reuses the existing ActiveScan persistence and claim
patterns without copying ActiveScan account ownership or public API behavior.
It uses `AnalysisExecutionService.register`, never `create`, so all parent,
child and execution linkage is committed before any future dispatch handoff.

The plan includes durable physical attempt lineage, but does not add a retry
worker, scheduler, rate limiting or runtime activation. Story 0241 will own
selection, dispatch, retry policy and recurring execution.

This plan is based on the human-approved Repository Analysis for Story 0240.
It is a technical recommendation only and does not authorize implementation.

## Planned Changes

### 1. Add Passive Domain Contracts

Affected component: `market-intelligence` domain/application passive package.

Create immutable contracts for:

- `PassiveScanRun`;
- `PassiveScanMarket`;
- `PassiveScanRunStatus`;
- `PassiveScanMarketStatus`;
- bounded trigger/window metadata;
- deterministic terminal outcome classification;
- bounded lifecycle diagnostics.

Implement explicit factory and restore methods, defensive copies and state
transition methods. The domain must reject illegal transitions, updates to
terminal states and child identity duplicates. The run aggregate must retain
the Story 0239 configuration version, fingerprint algorithm, universe
fingerprint and ordered resolved entries.

Constraint: do not add account, broker-account, user, credential, objective or
ActiveScan ownership to the passive model.

### 2. Define Deterministic Run and Child Semantics

Affected component: passive domain service or pure domain policy.

Implement the state transitions defined by ADR-102:

```text
Run:   REQUESTED -> DISPATCH_REQUESTED -> RUNNING
       RUNNING -> COMPLETED | PARTIALLY_COMPLETED | FAILED
       REQUESTED -> COMPLETED_NO_WORK

Child: REGISTERED -> DISPATCH_REQUESTED -> ACCEPTED -> RUNNING
       RUNNING -> COMPLETED | INSUFFICIENT_EVIDENCE | FAILED
       REGISTERED -> SKIPPED
```

Implement one deterministic run aggregation function. It must distinguish:

- no work from resolver error;
- completed analysis from no opportunity;
- insufficient evidence from successful analysis;
- partial completion from all-failed completion;
- non-terminal children from terminal run outcomes.

The mapper must consume existing `AnalysisExecution` status/quality facts and
must not infer success from opportunity existence.

### 3. Add Additive Database Schema

Affected component: `market-intelligence` Flyway migrations.

Add the smallest additive schema for:

- `passive_scan_runs`;
- `passive_scan_markets`;
- `passive_scan_market_attempts`.

The schema must include:

- UUID primary keys;
- passive run idempotency uniqueness;
- request and universe fingerprints;
- ordered immutable child snapshot fields;
- foreign keys from children and attempts;
- unique `(run_id, market_id)`;
- unique attempt number per logical child;
- unique analysis execution linkage;
- bounded diagnostic columns;
- lease ID and expiry fields;
- retry eligibility timestamp;
- version columns for compare-and-set updates;
- indexes for run reads, ordinal reads and eligible claims.

Use the repository's Flyway numbering and PostgreSQL timestamp conventions.
Do not alter existing ActiveScan tables or existing AnalysisExecution meaning.

### 4. Add Persistence Entities and Repositories

Affected component: `market-intelligence` persistence adapter.

Add JPA entities and Spring Data repositories for the three passive tables.
Follow current entity conventions for UUIDs, enums, timestamps, JSON/text
diagnostics and explicit relationships.

Repository ports must support:

- load by run ID and idempotency key;
- load children in ordinal order;
- insert parent/children/attempts atomically;
- conditional child claim with expected state/version and lease expiry;
- conditional run and child transitions;
- current execution association;
- terminal outcome recording;
- stale lease lookup/recovery;
- bounded operational counts and projection reads.

All claim and recovery updates must be database conditional updates. Java
in-memory locking is not sufficient.

### 5. Implement Atomic Run Creation and Replay

Affected component: passive application orchestration service and creation
transaction.

Expose an internal application operation that accepts:

- a resolved `PassiveMarketUniverse`;
- logical idempotency key;
- trigger context;
- planned window.

The operation must:

1. calculate a canonical request fingerprint without timestamps or diagnostics;
2. replay an existing run only when the fingerprint matches;
3. reject same-key, different-fingerprint requests explicitly;
4. persist the run and immutable ordered child snapshots;
5. register one `AnalysisExecution(PASSIVE)` per selected market using
   `AnalysisExecutionService.register`;
6. persist each child-to-execution and attempt linkage;
7. commit all records in one local transaction;
8. represent an empty valid universe as `COMPLETED_NO_WORK` without creating
   child executions;
9. preserve resolver error diagnostics so unavailable resolution is not
   misreported as successful no work.

Handle unique-key races by loading the winner and applying the same fingerprint
comparison. Do not invoke a dispatcher, scheduler or external service inside
the creation transaction.

### 6. Implement Durable Claim and Handoff Seams

Affected component: passive application claim service.

Implement the internal claim boundary required by Story 0241:

1. claim one eligible child using state, version and lease conditions;
2. set a unique lease ID and bounded expiry;
3. transition `REGISTERED -> DISPATCH_REQUESTED`;
4. atomically claim the linked analysis execution using its existing
   `REQUESTED -> ACCEPTED` transition;
5. transition the run to `DISPATCH_REQUESTED` when appropriate;
6. commit in `REQUIRES_NEW` before any dispatcher invocation.

The service returns the durable handoff request. It does not invoke the
dispatcher and does not schedule recurring work. A stale accepted execution may
use the existing conditional recovery primitive, but no retry loop is added.

### 7. Implement Outcome Recording and Reconciliation

Affected component: passive lifecycle/reconciliation service.

Implement idempotent child outcome recording guarded by lease/version. Repeated
identical terminal outcomes return the durable result. Conflicting terminal
updates cannot overwrite the first terminal fact.

Implement run reconciliation that:

- loads all children from durable state;
- refuses to finalize while any child is non-terminal;
- applies the deterministic aggregation policy;
- records bounded terminal counts/diagnostics;
- conditionally transitions the parent once;
- returns the durable winner when finalizers race.

The reconciler must not create opportunities, alter pipeline records, call
brokers or read the future scheduler configuration.

### 8. Add Bounded Operational Read Projection

Affected component: passive application read port/adapter.

Provide an internal projection containing:

- run identity, status, trigger and planned window;
- configuration/universe fingerprints;
- child counts by state;
- bounded child summaries in ordinal order;
- current execution IDs/statuses where needed;
- terminal summary and diagnostics.

Do not add a public REST endpoint, user authorization model or dashboard API.
Do not load full observation, thesis or opportunity payloads for an operational
status read.

### 9. Add Focused Tests and Validation

Create domain, persistence, transaction, concurrency, restart and safety tests
for the acceptance criteria and validation checklist below. Run targeted tests
first, then the full affected module validation.

## Files to Modify

- `docs/architecture/stories/0240-persistent-passive-scan-run/story.md`
  - only if implementation evidence requires updating implementation metadata;
  - do not mark the Story complete before review and final workflow gates.
- `market-intelligence/src/main/java/.../application/execution/AnalysisExecutionService.java`
  - only if a narrowly scoped passive registration/recovery seam is required by
    the existing contract; preserve all existing callers and semantics.
- `market-intelligence/src/main/java/.../domain/execution/AnalysisExecutionProvenance.java`
  - only if a passive-neutral provenance extension is demonstrably required;
  - do not reuse ActiveScan fields for passive ownership.
- `market-intelligence/src/main/resources/application*.yml`
  - only if an existing migration/test profile requires a repository-standard
    database setting; no scheduler or runtime activation setting is allowed.

No existing ActiveScan entity, table, public controller or broker contract is
expected to change.

## Files to Create

Expected paths may follow the module's established package names during
implementation:

- `market-intelligence/src/main/java/.../domain/passive/PassiveScanRun.java`
  - run aggregate and immutable snapshot metadata.
- `market-intelligence/src/main/java/.../domain/passive/PassiveScanMarket.java`
  - owned logical child and lifecycle state.
- `market-intelligence/src/main/java/.../domain/passive/PassiveScanRunStatus.java`
  - run state enum.
- `market-intelligence/src/main/java/.../domain/passive/PassiveScanMarketStatus.java`
  - child state enum.
- `market-intelligence/src/main/java/.../domain/passive/PassiveScanOutcome.java`
  - deterministic terminal classification if not kept as a policy type.
- `market-intelligence/src/main/java/.../application/passive/PassiveScanRunApplicationService.java`
  - create/replay/read lifecycle boundary.
- `market-intelligence/src/main/java/.../application/passive/PassiveScanCreationTransaction.java`
  - atomic run, child, attempt and execution registration.
- `market-intelligence/src/main/java/.../application/passive/PassiveScanClaimService.java`
  - conditional lease and dispatch-intent claim.
- `market-intelligence/src/main/java/.../application/passive/PassiveScanReconciliationService.java`
  - child outcome and parent finalization.
- `market-intelligence/src/main/java/.../application/port/PassiveScanRunRepository.java`
  - run persistence port.
- `market-intelligence/src/main/java/.../application/port/PassiveScanMarketRepository.java`
  - child claim and lifecycle persistence port.
- `market-intelligence/src/main/java/.../application/port/PassiveScanAttemptRepository.java`
  - physical attempt lineage port.
- `market-intelligence/src/main/java/.../application/port/PassiveScanReadRepository.java`
  - bounded operational projection port, if separate from lifecycle ports.
- `market-intelligence/src/main/java/.../adapter/persistence/JpaPassiveScanRunEntity.java`
  - run JPA mapping.
- `market-intelligence/src/main/java/.../adapter/persistence/JpaPassiveScanMarketEntity.java`
  - child JPA mapping.
- `market-intelligence/src/main/java/.../adapter/persistence/JpaPassiveScanAttemptEntity.java`
  - attempt JPA mapping.
- `market-intelligence/src/main/java/.../adapter/persistence/SpringDataPassiveScanRunRepository.java`
  - run data access.
- `market-intelligence/src/main/java/.../adapter/persistence/SpringDataPassiveScanMarketRepository.java`
  - child conditional updates and queries.
- `market-intelligence/src/main/java/.../adapter/persistence/SpringDataPassiveScanAttemptRepository.java`
  - attempt data access.
- `market-intelligence/src/main/java/.../adapter/persistence/JpaPassiveScanRepository.java`
  - repository port adapter, if required by local conventions.
- `market-intelligence/src/main/resources/db/migration/V29__passive_scan_run_lifecycle.sql`
  - additive passive run, child and attempt schema; use the next available
    migration number if the repository has advanced before implementation.
- focused domain tests for run/child transitions and aggregation;
- persistence tests for uniqueness, reload and foreign keys;
- application tests for replay, conflict, atomic registration, claims,
  reconciliation and restart recovery.

The exact file list is a planning estimate. The implementation must avoid
creating duplicate repository abstractions when one existing port can safely
cover the same boundary.

## Dependencies

### Internal Dependencies

- Story 0239 `PassiveMarketUniverse` and `PassiveMarketEntry`;
- `AnalysisExecutionService.register`;
- `AnalysisExecutionRepository` conditional claim/recovery operations;
- existing `AnalysisExecution` status and provenance contracts;
- existing Flyway, JPA, Spring transaction and repository conventions;
- ActiveScan patterns as implementation references only.

### External Dependencies

No new external library, broker SDK, messaging system, scheduler library or
infrastructure dependency is required.

### Ordering Dependencies

1. Domain contracts and deterministic policies.
2. Migration and persistence mappings.
3. Atomic creation and execution registration.
4. Idempotency conflict handling.
5. Claim/lease seams.
6. Outcome reconciliation and read projection.
7. Tests and module validation.

Story 0241 remains dependent on the completed durable seams but is not
implemented by this plan.

## Test Plan

### Domain Tests

- valid factories and restore behavior;
- immutable universe snapshot and ordered entries;
- duplicate market child rejection;
- all legal run and child transitions;
- illegal backward, duplicate and terminal updates rejected;
- deterministic no-work, success, partial and failure aggregation;
- insufficient evidence never mapped to successful analysis;
- completed no-signal analysis remains successful;
- bounded diagnostics and defensive collections.

Acceptance criteria: lifecycle semantics, identity preservation, truthful
states and insufficient-evidence handling.

### Persistence Tests

- run, child and attempt reload after transaction commit;
- foreign-key enforcement;
- unique run key;
- unique child `(run_id, market_id)`;
- unique attempt number and execution linkage;
- snapshot fields unchanged by later universe changes;
- migration starts with an empty compatible schema;
- bounded read projection does not require full result payloads.

Acceptance criteria: durability, restart reconstruction and persistence safety.

### Application and Concurrency Tests

- first creation persists all linkage before the operation returns;
- same key and same fingerprint replay the existing run;
- same key and different fingerprint returns a conflict;
- concurrent same-key creation yields one durable run;
- concurrent child claims yield one winner;
- stale lease recovery yields one new owner;
- old lease owner cannot write after lease loss;
- duplicate terminal outcome is idempotent;
- conflicting terminal outcome cannot overwrite the durable result;
- finalization races return one durable parent status;
- restart before dispatch reconstructs registered work;
- restart after claim reconstructs lease and accepted execution;
- non-terminal execution remains observable until reconciliation;
- missing linkage becomes a bounded consistency failure.

Acceptance criteria: idempotency, concurrent claims, dispatch ordering and
restart safety.

### Safety and Scope Tests

- passive requests use `AnalysisExecutionMode.PASSIVE`;
- no account/broker/user context is required;
- no dispatcher or scheduler is invoked during run creation;
- no public endpoint is registered;
- no ActiveScan rows are read or mutated as passive ownership;
- no broker, Risk, Trade Plan or AI call occurs;
- no historical reconstruction/backfill is triggered.

### Validation Commands

The Implementation Report must record actual results for:

```bash
cd market-intelligence && mvn -q -Dtest=<focused-passive-tests> test
cd market-intelligence && mvn -q test
cd market-intelligence && mvn -q verify
git diff --check
```

Migration validation must be included in the applicable Spring persistence
tests. No broker, frontend or deployed-environment validation is applicable.
Tests must not be described as passing until actually executed.

## Risks

### Execution Lineage Loss

Risk: overwriting one child execution ID during a retry would lose the durable
history required by ADR-023.

Mitigation: persist `passive_scan_market_attempts` with unique execution and
attempt identities; keep one logical child and link the current attempt.

### Duplicate Dispatch

Risk: process restart between database commit and dispatcher invocation could
create duplicate work.

Mitigation: register executions before dispatch, claim with state/version/lease
conditions, and reuse the existing execution on recovery. Do not create a new
logical child during handoff recovery.

### False Success

Risk: no opportunity or insufficient evidence could be treated as successful
analysis.

Mitigation: classify from persisted execution status/quality, not opportunity
existence; test explicit aggregation cases.

### Configuration Drift

Risk: later Story 0239 configuration changes could alter an existing run.

Mitigation: persist the complete resolved identity snapshot, configuration
version and universe fingerprint; never re-resolve during reconciliation.

### ActiveScan Contract Leakage

Risk: reusing ActiveScan fields could introduce account or actor semantics into
the passive boundary.

Mitigation: keep passive parent/child tables and contracts separate; use only
generic `AnalysisExecution(PASSIVE)` registration and existing neutral pipeline
contracts.

### Scope Expansion

Risk: adding scheduler, retry worker, public API or operational activation would
cross into Stories 0241/0242 and violate the Story boundary.

Mitigation: tests assert no dispatcher/scheduler invocation; implementation
exposes only internal durable seams and does not add runtime enablement.

No blocking architecture conflict remains in the approved analysis. The
implementation must stop and request guidance if the existing execution
contracts cannot register a passive execution without adding ActiveScan-specific
provenance.

## Validation Checklist

- [ ] Only `market-intelligence` application/domain/persistence and Story-local
      documentation are changed.
- [ ] Story 0239 `PassiveMarketUniverse` is consumed as the input contract.
- [ ] Run stores configuration version, fingerprint algorithm, universe
      fingerprint, planned window and trigger context.
- [ ] Each child stores canonical market ID, provider, product type, cadence,
      evidence profile and ordinal.
- [ ] One logical run key has a database uniqueness constraint.
- [ ] One run cannot contain duplicate canonical market children.
- [ ] Same-key same-fingerprint requests replay without new executions.
- [ ] Same-key different-fingerprint requests are rejected.
- [ ] Parent, children, attempts and registered executions commit atomically.
- [ ] No dispatch occurs before the creation transaction commits.
- [ ] Run and child state transitions reject illegal updates.
- [ ] No work, completed, partial, failed and insufficient-evidence outcomes are
      distinct and deterministic.
- [ ] Restart reloads unfinished runs, children, attempts, leases and execution
      linkage.
- [ ] Concurrent claims use conditional state/version/lease updates.
- [ ] Stale owners cannot overwrite newer lease owners.
- [ ] Terminal reconciliation is idempotent and race-safe.
- [ ] Operational reads are bounded and do not load full intelligence payloads.
- [ ] No scheduler, retry worker, public endpoint, broker, Risk, Trade Plan or
      AI behavior is introduced.
- [ ] ActiveScan tables and semantics remain unchanged.
- [ ] Focused tests pass.
- [ ] Full `market-intelligence` tests pass.
- [ ] `mvn -q verify` passes for `market-intelligence`.
- [ ] `git diff --check` passes.
- [ ] Implementation Report records documentation outcome, vault outcome and
      all executed/non-applicable validation.

## Recommendation

Ready for implementation

The approved analysis provides sufficient architecture, lifecycle semantics,
persistence boundaries, test coverage and scope controls. The plan resolves the
retry-lineage and insufficient-evidence design points without enabling runtime
dispatch. This recommendation does not authorize implementation.

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
