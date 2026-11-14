# Implementation Plan - Story 0241

## Overview

Implement bounded recurring passive dispatch inside `market-intelligence` by
connecting the Story 0239 resolved universe and Story 0240 durable lifecycle to
a disabled-by-default Spring scheduler.

The implementation must select due markets deterministically, create at most a
bounded run, claim passive children durably, dispatch already-registered
`AnalysisExecution(PASSIVE)` records, recover stale claims, reconcile terminal
outcomes and expose bounded operational metrics.

This plan is based on the human-approved Repository Analysis for Story 0241.
It is a technical recommendation only. It does not authorize implementation,
scheduler activation, runtime scans, broker operations or deployment changes.

## Scope and Constraints

### In scope

- Internal system-owned recurring scheduler.
- Disabled-by-default configuration and bounded resource settings.
- Deterministic due-market selection and fairness ordering.
- Explicit eligibility evaluation using existing Market Data contracts.
- Durable scheduler/run idempotency.
- Maximum two markets per run by default.
- Maximum one concurrent passive execution by default.
- Durable capacity and child claims.
- Dispatch after transaction commit through
  `AnalysisExecutionService.dispatchRegistered`.
- Finite retry/backoff and lease recovery.
- Terminal child outcome recording and run reconciliation.
- Bounded metrics and diagnostics.
- Unit, persistence/concurrency, restart and safety tests.

### Preserved boundaries

- `PassiveMarketUniverseResolver` remains the source of resolved identity.
- `PassiveScanRun` remains the durable orchestration aggregate.
- `AnalysisExecution` remains the one-market execution aggregate.
- `PassiveAnalysisStrategy` and the deterministic pipeline remain unchanged
  unless a narrowly scoped dispatch seam is required.
- No user, account, broker-account or agent identity is attached.
- No public scheduling endpoint is added.
- No ActiveScan is created automatically.
- No TradePlan, risk evaluation or broker operation is introduced.
- AI remains disabled and historical backfill remains out of scope.

## Affected Module and Components

Only `market-intelligence` should be changed.

Likely affected components:

- `application.passive`: scheduler, coordinator, eligibility and retry policy.
- `application.port.PassiveScanRunRepository`: due selection, capacity and
  recovery operations.
- `adapter.persistence`: JPA entity/repository support and migration.
- `config`: scheduler and resource properties.
- `config.SchedulingConfiguration`: only if scheduler registration requires a
  small existing-convention adjustment.
- existing `AnalysisExecutionService`, only if dispatch/recovery integration
  requires an additive internal seam.
- `application.metrics` or the existing metrics abstraction, if present, for
  bounded operational counters and timers.

No Gateway, Trading Core, Broker Service, frontend or cTrader module should be
modified.

## Implementation Sequence

### 1. Add validated scheduler configuration

Add a typed configuration boundary for:

- `enabled`, default `false`;
- fixed delay, proposed default `PT5M`;
- maximum markets per run, default `2`;
- maximum concurrent passive executions, default `1`;
- maximum claims per tick;
- maximum retry attempts;
- retry base and maximum backoff;
- claim lease duration;
- provider budget settings or references to an existing budget service.

Validate all values as positive and reject an enabled configuration with an
invalid delay or capacity. The scheduler must not read raw property names from
the application service.

Suggested property namespace:

```properties
market-intelligence.passive-dispatch.enabled=false
market-intelligence.passive-dispatch.fixed-delay=PT5M
market-intelligence.passive-dispatch.max-markets-per-run=2
market-intelligence.passive-dispatch.max-concurrent-executions=1
market-intelligence.passive-dispatch.max-claims-per-tick=2
market-intelligence.passive-dispatch.max-attempts=3
market-intelligence.passive-dispatch.retry-base-delay=PT1M
market-intelligence.passive-dispatch.retry-max-delay=PT30M
market-intelligence.passive-dispatch.claim-lease=PT30S
```

The exact binding class and property names may follow repository conventions,
but defaults must remain disabled and bounded.

### 2. Define durable due and capacity state

Use the smallest additive persistence change compatible with the 0240 model.
Do not mutate the immutable universe snapshot fields.

Add to the passive child or its operational state, as appropriate:

- `nextEligibleAt`;
- last terminal outcome timestamp if not already represented;
- retry/backoff state needed for deterministic due selection;
- bounded dispatch diagnostic fields if required.

Add one database-backed passive capacity lease/resource record or an equivalent
existing durable claim mechanism. It must provide:

- stable resource identity;
- owner/lease identity;
- lease expiry;
- compare-and-set acquisition;
- conditional release or expiry recovery;
- no dependency on JVM-local locks.

Use a new additive Flyway migration, expected as `V30__bounded_passive_dispatch.sql`
unless repository history requires another version. The migration must preserve
existing 0240 records and be compatible with `ddl-auto=validate`.

The capacity representation is an implementation detail of the passive
dispatch boundary, not a second orchestration aggregate.

### 3. Add explicit eligibility decisions

Introduce a small deterministic eligibility policy/service that returns a typed
decision or reason set rather than a Boolean. It must evaluate:

1. enabled universe entry and resolved identity;
2. current catalogue identity match;
3. operational `MarketState` and tradability;
4. evidence profile availability/freshness;
5. cadence due time;
6. compatible in-flight passive execution;
7. retry budget;
8. provider request budget;
9. run and durable capacity budget.

Reuse `MarketDataClient`, existing fact/readiness services and
`TradeabilityEvidenceProfile`. Do not add provider-specific identity mapping to
the scheduler.

Eligibility results should distinguish at least:

- `DUE_AND_ELIGIBLE`;
- `NOT_DUE`;
- `MARKET_CLOSED`;
- `MARKET_UNAVAILABLE`;
- `IDENTITY_UNAVAILABLE`;
- `INSUFFICIENT_EVIDENCE`;
- `IN_FLIGHT`;
- `PROVIDER_BUDGET_EXHAUSTED`;
- `RETRY_BUDGET_EXHAUSTED`;
- `CAPACITY_EXHAUSTED`.

Closed/unavailable entries must not cause unnecessary provider calls. Missing
evidence must remain an explicit non-positive outcome, never a signal.

### 4. Implement deterministic due selection

Add a repository read/claim operation that orders candidates by:

```text
nextEligibleAt ASC
then marketId ASC
then provider ASC
then productType ASC
```

Limit selection to the configured batch size and remaining capacity. Use
database conditions to exclude non-terminal compatible executions, active
leases, exhausted retries and not-yet-due entries.

Generate a deterministic scheduler idempotency key from:

- universe fingerprint;
- configuration version;
- cadence/window bucket;
- scheduler trigger version.

Do not use a random UUID as the scheduler request identity. A duplicate tick
must load the existing run or lose a unique-key race and perform no duplicate
registration.

When no entry is due, return without creating a run or execution. Keep
`COMPLETED_NO_WORK` for an explicitly created valid run whose selected children
all produce no work, consistent with ADR-102.

### 5. Add bounded run creation coordination

Create an internal `PassiveScanDispatchCoordinator` or equivalent service that:

1. resolves `PassiveMarketUniverse`;
2. evaluates and orders eligible entries;
3. acquires durable passive capacity;
4. creates/replays the `PassiveScanRun` with the deterministic key;
5. limits the snapshot to the configured maximum markets;
6. releases capacity if no run was created or no dispatch can proceed;
7. invokes child claim/dispatch coordination after the creation transaction.

The coordinator must not call a dispatcher inside the run creation transaction.
It must not create a second execution if `PassiveScanRunApplicationService`
returns an existing run.

### 6. Add scheduler-owned claim and handoff

Add a passive dispatch loop modeled on `ActiveScanDispatchCoordinator`, but use
the passive contracts:

1. obtain an eligible child from durable state;
2. call `PassiveScanClaimService.claim(runId, scanMarketId)`;
3. treat `null` as a lost race or duplicate suppression;
4. after claim transaction commit, call
   `AnalysisExecutionService.dispatchRegistered(analysisExecutionId)`;
5. record dispatch diagnostics without changing terminal state on handoff
   success alone;
6. leave the child recoverable if dispatch throws.

The loop must dispatch at most the configured remaining capacity. The local
virtual-thread executor may carry the handoff, but it is not the concurrency
authority.

### 7. Implement retry and lease recovery

Classify failures into retryable and permanent categories at the passive
boundary. Do not retry terminal provider or evidence outcomes indefinitely.

For retryable failures:

- increment persisted attempt count;
- compute deterministic bounded backoff;
- persist `nextEligibleAt`/`nextRetryAt`;
- create a new physical attempt lineage only when the existing execution model
  explicitly permits a new logical execution attempt;
- preserve the original run/market logical identity.

For permanent failures or exhausted retries:

- persist explicit failure code/reason;
- prevent immediate redispatch;
- allow the run reconciler to produce a truthful terminal or partial state.

Recovery must use lease ID, expected status and version. A stale recovery loser
must not modify the winner's state. Accepted execution recovery must reuse
`AnalysisExecutionService.requeueAcceptedForRecovery`.

### 8. Reconcile outcomes and capacity

After dispatch and on every scheduler tick, reconcile runs whose children have
terminal execution state. Use:

- `PassiveScanRunApplicationService.recordOutcome`;
- `PassiveScanReconciliationService.reconcile`;
- lease/version conditional updates.

Release the passive capacity lease only after the durable run/execution state is
terminal or after a safely recoverable lease expiry. Repeated reconciliation
must be idempotent and must not overwrite a terminal winner.

No opportunity, thesis or trade-plan logic belongs in the scheduler.

### 9. Add metrics and diagnostics

Reuse existing observability conventions and add low-cardinality metrics for:

- scheduler ticks and disabled ticks;
- resolved, due, not-due and skipped markets;
- created/replayed runs;
- duplicate suppression and claim races;
- active passive execution count;
- dispatched executions;
- retry attempts and exhaustion;
- provider/evidence budget skips;
- lease recovery;
- dispatch/reconciliation latency;
- terminal run outcomes.

Do not put run UUID, scan-market UUID, provider symbol or raw exception text in
metric labels. Persist bounded per-run/per-market diagnostics instead.

### 10. Tests and validation

Add tests in the affected `market-intelligence` test packages.

#### Configuration and scheduler

- disabled by default;
- invalid interval/capacity rejected;
- no configured markets;
- no due markets creates no run/execution;
- one due market;
- two-market batch limit;
- fixed delay invocation through a deterministic trigger.

#### Eligibility and fairness

- closed market;
- unavailable provider/catalogue;
- stale or missing evidence;
- unsupported evidence capability;
- cadence not due;
- compatible in-flight execution;
- provider budget exhausted;
- retry budget exhausted;
- stable ordering and starvation prevention.

#### Concurrency and persistence

- concurrent scheduler ticks produce one logical run;
- concurrent capacity claims have one winner;
- concurrent child claims have one winner;
- maximum one active passive execution;
- lease expiry recovery;
- duplicate dispatch suppression;
- restart before and after handoff.

#### Retry and reconciliation

- deterministic backoff;
- retryable failure;
- permanent failure;
- exhausted retries;
- terminal execution before reconciliation;
- duplicate reconciliation;
- truthful completed, partial, failed and no-work states.

#### Safety

- no account/broker identity;
- no broker client interaction;
- no synchronization;
- no TradePlan/risk/ActiveScan creation;
- no AI or historical acquisition;
- scheduler remains inactive unless explicitly enabled.

Use deterministic clocks, repository doubles and controlled Market Data
responses. Tests must not contact cTrader, Kraken or other live providers.

Validation sequence:

```text
targeted Story 0241 tests
market-intelligence test suite
market-intelligence mvn verify
git diff --check
backend quality/coverage validation when the repository gate is applicable
```

Runtime scan validation is not part of this Story and must remain disabled.

## Files to Create or Modify

Expected paths are estimates and may follow existing package conventions.

Likely new files:

- `market-intelligence/src/main/java/.../config/PassiveDispatchProperties.java`
- `market-intelligence/src/main/java/.../application/passive/PassiveScanDispatchScheduler.java`
- `market-intelligence/src/main/java/.../application/passive/PassiveScanDispatchCoordinator.java`
- `market-intelligence/src/main/java/.../application/passive/PassiveScanEligibilityService.java`
- `market-intelligence/src/main/java/.../application/passive/PassiveScanEligibility.java`
- `market-intelligence/src/main/java/.../adapter/persistence/JpaPassiveDispatchCapacityEntity.java`
- `market-intelligence/src/main/java/.../adapter/persistence/SpringDataPassiveDispatchCapacityRepository.java`
- `market-intelligence/src/main/resources/db/migration/V30__bounded_passive_dispatch.sql`

Likely modified files:

- `application/port/PassiveScanRunRepository.java`;
- `adapter/persistence/JpaPassiveScanRunRepository.java`;
- `adapter/persistence/SpringDataPassiveScanMarketRepository.java`;
- `application/passive/PassiveScanRunApplicationService.java`;
- `application/passive/PassiveScanClaimService.java` only if lease duration or
  recovery needs configuration injection;
- `application/passive/PassiveScanReconciliationService.java` only if capacity
  release belongs at reconciliation;
- `application/execution/AnalysisExecutionService.java` only for an additive
  internal recovery/dispatch seam;
- `application.properties` only for documented false-by-default bindings;
- relevant tests and Story 0241 implementation documentation.

No public controller, Gateway route, broker contract or frontend file should be
changed.

## Transaction Boundaries

1. Universe resolution and eligibility reads occur outside asynchronous work.
2. Capacity acquisition and deterministic run creation use a short local
   transaction.
3. Existing 0240 creation transaction persists parent, children, execution
   registrations and initial attempt together.
4. Child claim uses `REQUIRES_NEW` and commits before dispatch.
5. Dispatcher invocation occurs outside the claim transaction.
6. Outcome recording and reconciliation use short conditional transactions.
7. Capacity release is conditional and idempotent.

No transaction may surround provider calls, pipeline execution or asynchronous
handoff.

## Risks and Controls

| Risk | Control |
| --- | --- |
| Duplicate ticks | Deterministic run key and unique idempotency constraints. |
| Duplicate child dispatch | Versioned child claim plus execution claim. |
| Cross-instance over-capacity | Database-backed capacity lease, not JVM state. |
| Lost handoff | Durable accepted state and lease recovery. |
| Infinite retries | Persisted finite attempt budget and bounded backoff. |
| Starvation | Due-time plus stable identity ordering and bounded batches. |
| False success | Reconcile from execution status/quality, not opportunity existence. |
| Provider boundary leakage | Market Data contracts only; no broker service calls. |
| Accidental runtime activation | `enabled=false` default and explicit tests. |
| Configuration drift | New runs snapshot the resolved universe; existing runs remain immutable. |

## Acceptance Criteria Mapping

Each Story criterion maps to the following implementation evidence:

- Batch limit: bounded due-selection query and coordinator test.
- No-due behavior: scheduler returns before run creation and test verifies no
  `AnalysisExecution` registration.
- Eligibility: typed decision tests cover cadence, market state, evidence,
  in-flight, provider and retry budgets.
- Concurrency: durable capacity and child claims plus concurrent integration test.
- Duplicate suppression: deterministic key and existing uniqueness constraints.
- Lease recovery: expired claim and accepted execution recovery tests.
- Retry budget: persisted count/backoff and exhaustion tests.
- Provider failure isolation: one failed market does not prevent another bounded
  child from dispatching.
- Fair ordering: stable selection ordering and repeated-tick starvation test.
- Truthful terminal state: reconciliation tests for all terminal aggregates.
- Disabled scheduler: configuration binding and scheduler guard tests.
- Metrics: low-cardinality instrumentation assertions.
- Test coverage: targeted and full module validation evidence.

## Non-Goals

- No implementation in this change.
- No scheduler activation or runtime scan.
- No distributed scheduler or leader election.
- No public scheduling API.
- No user watchlists or account-specific monitoring.
- No broker synchronization, order placement or position management.
- No TradePlan or risk execution.
- No automatic ActiveScan escalation.
- No AI invocation or historical backfill.
- No notification system.
- No generic priority engine or workflow framework.

## Plan Approval Gate

This plan is ready for human review. Explicit approval of this exact plan is
required before implementation may begin.
