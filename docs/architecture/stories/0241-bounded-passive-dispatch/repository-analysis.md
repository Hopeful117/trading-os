# Repository Analysis - Story 0241

## 1. Repository Baseline

- **Story:** `0241 - Add Bounded Recurring Passive Dispatch`
- **Analysis date:** 2026-11-13
- **Repository:** `trading-os`
- **Branch:** `main`
- **HEAD:** `c28a246d91bb0f1a77c7a053c63ea305cdb4aaef`
- **Remote:** `origin/main` is aligned with `HEAD`.
- **Working tree:** clean at analysis start; no unrelated changes were present.
- **Delivery evidence:** Story 0239 was merged through PR #291 at
  `76480d09c706b348aa377e5c6ab3f76f5315e620`. Story 0240 implementation was
  included in PR #292 and merged at `c28a246d91bb0f1a77c7a053c63ea305cdb4aaef`.

No application code, migration, scheduler, runtime configuration, broker call,
AI invocation or scan execution was performed for this analysis.

## 2. Scope and Authority

The authoritative inputs are:

- accepted `ADR-102`;
- `ADR-021`, `ADR-033`, `ADR-087` and `ADR-100`;
- approved Epic `0238`;
- Stories `0239`, `0240` and `0241`;
- current `market-intelligence` source, tests, migrations and configuration.

The repository is authoritative for current physical behavior. ADR-102 and Epic
0238 authorize planning, not implementation or scheduler activation. The
Engineering Story workflow requires human approval of this Repository Analysis
before an Implementation Plan may be produced. Consequently, no 0241
Implementation Plan is created in this change.

DevLog context was not available through the current tool set. No historical
decision is inferred from that absence. The Obsidian vault was not used because
the repository contains the governing ADRs and current implementation evidence
needed for this analysis.

## 3. Current Implementation Findings

Story 0239 is present and merged. Its `PassiveMarketUniverseResolver` returns a
validated, deterministically ordered `PassiveMarketUniverse` containing exact
canonical market ID, provider, product type, cadence and minimum evidence
profile. It also returns diagnostics, configuration version and a fingerprint.

Story 0240 is present and merged. The current implementation provides:

- `PassiveScanRun` and `PassiveScanMarket` immutable lifecycle objects;
- `PassiveScanRunApplicationService` as the application seam;
- `PassiveScanCreationTransaction` as a local transactional creation boundary;
- `PassiveScanClaimService` with `REQUIRES_NEW` claim and recovery methods;
- `PassiveScanReconciliationService` for durable child-to-run aggregation;
- `PassiveScanRunRepository` and JPA/Spring Data adapters;
- `AnalysisExecutionService.register` linkage in `PASSIVE` mode;
- Flyway `V29__passive_scan_run_lifecycle.sql`;
- domain, persistence, race and restart-oriented tests.

The current 0240 boundary does not provide:

- a recurring passive scheduler;
- due-time selection based on a prior successful or attempted run;
- market-state or evidence preflight before run creation;
- provider request budgets for passive dispatch;
- a durable global passive concurrency budget;
- retry/backoff advancement of `next_retry_at`;
- a dispatcher coordinator dedicated to passive runs;
- scheduler or dispatch metrics.

These are the intended 0241 responsibilities and must not be added by copying
the ActiveScan model wholesale.

## 4. Reusable Components

### Passive contracts

`PassiveMarketUniverse`, `PassiveMarketEntry` and the resolver are the only
selection-policy input. The scheduler must consume the resolved contract and
must not read Spring property fields directly or re-resolve catalogue identity.

`PassiveScanRunApplicationService.create` performs same-key fingerprint replay
and rejects a conflicting fingerprint. `PassiveScanCreationTransaction.create`
persists the run, registers one `AnalysisExecution(PASSIVE)` per selected
market, persists child linkage and persists the initial physical attempt before
returning.

### Durable claims and recovery

`PassiveScanClaimService.claim` uses a new transaction, a versioned conditional
database claim, a generated lease ID and a 30-second lease. It then claims the
linked analysis execution and transitions the child to `ACCEPTED`. The service
returns the lease; it does not dispatch asynchronously.

`PassiveScanClaimService.recover` checks run ownership, lease expiry and lease
identity. For an `ACCEPTED` child it invokes the existing
`AnalysisExecutionService.requeueAcceptedForRecovery` primitive, then performs a
conditional child recovery.

### Analysis execution

`AnalysisExecutionService.register` is the correct creation operation because it
does not dispatch. `dispatchRegistered` must be called only after the claim
transaction commits. The existing database idempotency key is globally unique,
and `LocalAnalysisExecutionDispatcher` starts work only after an atomic
`ACCEPTED -> CONTEXT_BUILDING` transition.

### ActiveScan patterns

`ActiveScanDispatchClaimService` and `ActiveScanDispatchCoordinator` establish
the local handoff pattern: claim in a transaction, dispatch outside it, and use
the persisted execution as the handoff identity. Their account ownership,
public API, scope eligibility and scan-specific state must not leak into the
passive path.

### Scheduling and executors

`SchedulingConfiguration` already enables Spring scheduling. Existing scheduled
drivers use `@Scheduled` and are disabled from test profiles where appropriate.
`IntelligenceExecutionConfiguration` exposes a virtual-thread
`analysisExecutionDispatcherExecutor` and a separate virtual-thread
`scanDispatchExecutor`. These executors are transport mechanisms, not durable
resource budgets; they cannot by themselves enforce the Story 0241 concurrency
contract across restarts or instances.

## 5. Scheduler Ownership

The smallest suitable boundary is a package-private or internal application
component in `market-intelligence`, for example a
`PassiveScanDispatchScheduler` that depends on a passive orchestration service.
It should be system-owned and account-independent:

```text
Spring @Scheduled tick
    -> PassiveScanDispatchCoordinator.tick()
    -> durable due selection and capacity claim
    -> PassiveScanRunApplicationService.create()
    -> PassiveScanClaimService.claim()
    -> AnalysisExecutionService.dispatchRegistered()
```

The scheduler must not use a user, account, broker account, agent principal or
synthetic ActiveScan owner. No public endpoint is required.

V1 should use one local Spring scheduler with a deployment-configured fixed
delay. It must be disabled by default and activated only by an explicit
configuration property. A proposed boundary is:

```properties
market-intelligence.passive-dispatch.enabled=false
market-intelligence.passive-dispatch.fixed-delay=PT5M
```

The exact property names remain an implementation detail for the approved plan.
The five-minute value is a proposed default, not a domain invariant. The
scheduler must not start work merely because a tick occurred.

There is no distributed scheduler or leader-election framework in V1. That does
not permit unsafe duplicate dispatch: deterministic run keys and database-backed
claims remain mandatory. If multiple application instances run concurrently,
duplicate ticks must converge on one durable run and one child/execution claim.
If the implementation cannot prove that property, it must fail closed and leave
work for a later tick rather than dispatching optimistically.

## 6. Configuration and Activation

Story 0239 already binds `market-intelligence.passive-universe` and defaults its
entries to an empty list. That empty configuration is safe. Story 0241 must add
only scheduler configuration and resource budgets; it must not add demonstration
markets, credentials, broker settings or runtime activation by default.

Proposed operator-controlled settings:

- scheduler enabled flag, default `false`;
- wake-up fixed delay, minimum five minutes by validation or documented policy;
- maximum markets per run, default `2`;
- maximum concurrent passive executions, default `1`;
- maximum dispatch claims per tick;
- maximum retry attempts, finite and default bounded;
- retry backoff base and maximum delay;
- passive claim lease duration;
- provider request budget window and limit, if an existing provider-neutral
  budget port can be reused.

Configuration validation must reject non-positive values and must not silently
convert an invalid setting into an unlimited value.

## 7. Market Eligibility

Eligibility must be represented as explicit facts or a typed decision, not one
opaque Boolean. For each resolved universe entry, the scheduler should evaluate
these predicates in order:

1. The entry is enabled and was resolved without an identity error.
2. The canonical market, provider and product identity still match the Market
   Data catalogue response.
3. The market is operationally eligible according to `MarketResponse.marketState`:
   `tradable=true`, an executable trading status and a non-stale state update.
4. The required evidence profile is available or can be safely acquired through
   existing Market Data contracts.
5. The per-market cadence is due based on persisted passive history, not only on
   process memory.
6. No compatible non-terminal `AnalysisExecution(PASSIVE)` exists for the same
   canonical/provider/product identity.
7. The run, retry, provider and concurrency budgets are available.

Closed or unavailable markets must be recorded as explicit skipped or
insufficient-evidence outcomes only when a run has already been created for
them. They must not trigger unnecessary provider requests. Missing evidence is
not a market signal and must never be converted into a successful analysis.

The existing `MarketDataClient` exposes catalogue, market state, price snapshot
and market-facts calls. It does not currently expose a dedicated passive
eligibility decision. Story 0241 should introduce the smallest internal adapter
or reuse an existing fact/readiness service; it must not duplicate provider
mapping or guess provider/product identity.

## 8. Cadence and Fairness

The persisted child has cadence, attempt count, `nextRetryAt` and timestamps,
but Story 0240 does not yet persist a `nextEligibleAt` or last-terminal cadence
decision. 0241 must add the smallest durable representation needed for due
selection, preferably on the passive market child or a run-selection projection.
It must not mutate the immutable market snapshot.

Recommended ordering:

```text
next eligible time ASC
then canonical market ID ASC
then provider ASC
then product type ASC
```

Retry backoff must move the next eligible time forward. A market that is always
due must not monopolize a batch. Selection must be bounded and must continue
from the stable ordered query on the next tick.

Configuration changes apply only to future run snapshots. Existing runs retain
their stored universe identity, cadence and evidence profile.

When no market is due, the scheduler should avoid creating a run or an
`AnalysisExecution`. `COMPLETED_NO_WORK` remains useful for an explicitly
created valid run whose resolved universe contains no executable child, or for
durable child outcomes where all selected entries are skipped. This preserves
ADR-102 semantics while satisfying the stronger Story 0241 requirement that a
wake-up with no due market creates no analysis execution.

Whether a no-due scheduler tick should create an operational audit record is an
open product/operations decision. The safe default is no run and a metric only.

## 9. Dispatch Lifecycle

The proposed lifecycle reuses the existing durable contracts:

```text
Scheduler wakes
    -> resolve PassiveMarketUniverse
    -> evaluate due/eligible entries
    -> claim bounded scheduler capacity
    -> create one durable PassiveScanRun
    -> claim child with PassiveScanClaimService
    -> commit claim and AnalysisExecution ACCEPTED state
    -> dispatchRegistered outside the transaction
    -> observe AnalysisExecution state
    -> recordOutcome with lease/version guard
    -> reconcile run
```

0240 already registers `AnalysisExecution` records during run creation. 0241
must not call `AnalysisExecutionService.create` and must not register a second
execution for the same run/market. The logical execution identity is the
existing child `analysisExecutionId`; physical attempt lineage remains in
`passive_scan_market_attempts`.

The dispatch coordinator must treat `claim()` returning `null` as duplicate
suppression or lost race, not as a failure. It must dispatch only after the
claim transaction succeeds. A dispatch exception must leave durable state
recoverable and must not report success.

## 10. Concurrency Budgets and Bounded Dispatch

The initial policy is maximum two markets per run and maximum one concurrent
passive execution. The one-execution limit must be enforced by durable state,
not by `ExecutorService` size or an in-memory semaphore.

The smallest safe implementation is a database-backed conditional capacity
claim associated with the passive dispatch boundary. It may be represented by a
single-row lease/resource record or an equivalent existing durable claim
mechanism. The claim must include owner/lease identity, expiry and a compare-
and-set update. It must be released or recovered from terminal execution state.

If the project explicitly accepts a single application instance for V1, that
assumption must be recorded in configuration and the safe failure behavior for a
second instance must be fail-closed. The current repository does not provide
evidence that a process-local counter is sufficient, so a process-local counter
alone is not acceptable for Story 0241.

Provider request budgets must be checked before evidence acquisition or dispatch.
Provider-specific limits remain in provider adapters or existing provider-neutral
budget services. Passive dispatch must not call cTrader or Kraken broker
operations, and must not synchronize accounts.

No unbounded queue may be introduced. The scheduler should claim at most the
remaining durable capacity and leave excess due entries for a later tick.

## 11. Retry, Leases and Recovery

The first implementation must distinguish the logical run/market identity from
physical handoff attempts:

- one child and one logical `AnalysisExecution` per run/market;
- one persisted attempt row per physical dispatch attempt;
- finite retry count on the child;
- persisted `nextRetryAt` or equivalent due timestamp;
- deterministic exponential or stepped backoff with a configured maximum;
- one claim lease ID and expiry for each handoff;
- no retry after permanent failure or terminal execution;
- no retry after the finite budget is exhausted.

Recovery behavior:

- **Crash before claim:** child remains `REGISTERED`; the next tick can claim it.
- **Crash after claim before dispatch:** lease expiry makes the child recoverable;
  the linked execution is requeued only through the existing recovery primitive.
- **Crash during execution:** the durable `AnalysisExecution` remains the source
  of truth; a non-terminal stale accepted state is recovered conditionally.
- **Crash after execution before reconciliation:** the next reconciliation pass
  reads the terminal execution and records the child outcome idempotently.
- **Provider unavailable or insufficient evidence:** record explicit non-positive
  outcome, then retry only if the classified failure is retryable and budget
  remains.
- **Concurrent scheduler invocations:** unique scheduler/run identity and
  versioned child/capacity claims make one invocation win; losers do no dispatch.

Duplicate callbacks or repeated reconciliation must return the durable terminal
winner. Conflicting terminal updates must not overwrite the first recorded fact.

## 12. Provider and Market Data Boundaries

The passive scheduler may consume Market Data catalogue/state/facts contracts and
the existing deterministic analysis pipeline. It must preserve the exact
canonical market ID, provider and product type from the resolved universe.

It must not:

- substitute Kraken evidence for a cTrader entry or the reverse;
- infer a market as open from a quote alone;
- perform historical backfill;
- require research-grade historical completeness;
- access broker credentials or account synchronization;
- create TradePlans or invoke risk evaluation;
- create an ActiveScan automatically;
- enable AI invocation.

`PASSIVE` is an analysis mode, not trading authority. A cTrader demo or FTMO
account remains outside the passive scanner boundary; no account context should
be attached to the passive `IntelligenceAnalysisRequest`.

## 13. Transaction and Idempotency Design

The transaction boundaries should remain narrow:

1. **Selection read:** resolve universe and inspect due/eligibility state. No
   asynchronous dispatch or provider write occurs here.
2. **Run creation transaction:** use the scheduler's deterministic idempotency
   key, call the existing creation service, and commit run/children/execution
   registrations together.
3. **Claim transaction:** use `REQUIRES_NEW` and conditional child plus capacity
   updates. Commit before calling `dispatchRegistered`.
4. **Dispatch handoff:** call the existing dispatcher outside the claim
   transaction. Do not hold a database transaction over analysis work.
5. **Outcome transaction:** record the child result using lease/version guards.
6. **Reconciliation transaction:** aggregate durable child/execution facts and
   conditionally finalize the run.

The scheduler idempotency key should be deterministic from the configured
universe fingerprint and a cadence window/start bucket, not from a random UUID.
Two concurrent ticks for the same window must load the same run or observe the
unique-key winner. A changed universe fingerprint under the same key must be a
conflict, never a merge.

The existing 0240 unique `(run_id, market_id)` and unique execution linkage
constraints remain the final duplicate barrier. Any new capacity row or
selection projection must use the same database transaction and conditional
update conventions.

## 14. Metrics and Diagnostics

Reuse the existing application logging/metrics conventions. Add bounded counters
and timers without raw UUID labels:

- scheduler wake-ups and disabled ticks;
- resolved universe entries;
- due, not-due and skipped markets;
- created runs and no-work decisions;
- successful and losing duplicate claims;
- dispatched executions;
- active passive execution count;
- retry attempts and retry exhaustion;
- provider-budget consumption and budget skips;
- unavailable/closed-market and insufficient-evidence outcomes;
- dispatch and reconciliation latency;
- lease recovery;
- terminal run outcomes.

Per-run and per-market details belong in the existing bounded diagnostics and
projection fields, not metric labels. Diagnostics must be truncated or bounded
according to the existing persistence conventions.

## 15. Test Matrix

### Scheduler

- disabled by default and no scheduling call when disabled;
- configurable fixed delay with the proposed five-minute minimum;
- empty universe and no-due universe produce no analysis execution;
- due market creates one bounded run;
- maximum two selected markets per run;
- deterministic due-time and stable identity ordering;
- repeatedly due market cannot starve another due market.

### Eligibility

- disabled/unresolved universe entry;
- closed or non-tradable market;
- unavailable or stale market state;
- missing, stale or insufficient evidence;
- provider/product capability mismatch;
- compatible in-flight passive execution;
- provider budget exhausted;
- retry budget exhausted.

### Claims and concurrency

- one passive execution maximum is enforced through durable capacity;
- concurrent scheduler ticks produce one logical run;
- concurrent child claims have one winner;
- repeated claim after acceptance suppresses duplicate dispatch;
- lease expiry permits exactly one recovery winner;
- multiple application instances cannot exceed the durable capacity contract.

### Recovery and reconciliation

- crash before claim;
- crash after claim before dispatcher handoff;
- stale accepted execution requeue;
- terminal execution before reconciliation;
- duplicate outcome recording;
- conflicting terminal outcome;
- retryable failure with deterministic backoff;
- permanent failure and retry exhaustion;
- truthful completed, partial, failed and no-work run states.

### Safety

- no user/account/broker identity is attached;
- no broker service call or account synchronization occurs;
- no TradePlan or risk evaluation is created;
- no AI call or historical acquisition occurs;
- no ActiveScan is created automatically;
- scheduler remains disabled without explicit configuration.

Tests must use deterministic clocks, repository doubles and controlled Market
Data responses. No live cTrader, Kraken or external provider calls are allowed.

## 16. Acceptance Criteria Mapping

| Story 0241 criterion | Repository Analysis disposition |
| --- | --- |
| Bounded markets per run | Apply a configurable batch limit before `PassiveScanRun` creation; default two. |
| No-due wake-up creates no execution | Return before run creation when no market is due; record a metric only. |
| Full deterministic eligibility | Use typed predicate results for identity, market state, evidence, cadence, in-flight, provider, retry and capacity checks. |
| Bounded concurrency | Enforce through a durable capacity claim, not executor size. |
| Duplicate suppression | Deterministic run key plus 0240 unique child/execution constraints. |
| Lease recovery | Reuse `PassiveScanClaimService.recover` and existing execution requeue semantics. |
| Finite retry/backoff | Persist attempt count and next eligibility; classify retryable/permanent outcomes. |
| Provider isolation | Use Market Data and existing provider-neutral boundaries; no broker operations. |
| Fair ordering | Persist due time and order by due time plus stable market identity. |
| Truthful terminal run state | Reuse `PassiveScanReconciliationService` after every terminal child outcome. |
| Disabled by default | Add an explicit false-by-default scheduler guard. |
| Metrics | Add bounded scheduler/selection/dispatch/retry/recovery counters and timers. |
| Test coverage | Implement the mandatory scheduler, eligibility, concurrency, recovery and safety matrix. |

## 17. Risks and Unresolved Decisions

1. **Durable global capacity:** the repository has child leases but no global
   passive capacity lease. Human approval is needed for the smallest additive
   capacity representation, preferably one database-backed resource lease.
2. **No-due audit semantics:** decide whether a no-due tick is metrics-only or
   creates a `COMPLETED_NO_WORK` operational record. The safe default is no run.
3. **Market-state contract:** identify the existing typed readiness/evidence
   service to call before dispatch; do not invent a provider-specific scheduler
   adapter.
4. **Retry classification:** agree on the finite retryable/permanent failure
   taxonomy and backoff defaults. 0240 stores fields but does not implement the
   retry policy.
5. **Multi-instance deployment:** no leader election is planned. The durable
   claim/capacity design must be accepted as the V1 safety mechanism, or the
   deployment must explicitly enforce a single active instance.
6. **Configuration limits:** decide whether the five-minute minimum is validated
   as a hard deployment constraint or documented as an operational default.
7. **Metrics implementation:** confirm whether the existing metrics abstraction
   is in Market Intelligence or whether a small module-local instrumentation
   adapter is required.

## 18. Implementation Sequencing

After human approval of this Repository Analysis, the Implementation Plan should
sequence the work as follows:

1. Add disabled-by-default scheduler configuration and typed resource limits.
2. Add durable due-time and capacity-selection primitives without changing the
   immutable 0240 snapshot.
3. Add explicit eligibility evaluation using existing Market Data contracts.
4. Add deterministic scheduler run-key generation and bounded run creation.
5. Add passive dispatch coordination around `claim()` and
   `dispatchRegistered()`.
6. Add finite retry/backoff, lease recovery and terminal reconciliation wiring.
7. Add bounded metrics and per-run diagnostics.
8. Add deterministic unit, persistence/concurrency, restart and safety tests.
9. Validate with the affected module suite without enabling runtime scanning.

No implementation plan is produced now because the current Engineering Story
workflow requires explicit human approval of this Repository Analysis first.

## Explicit Non-Goals

- no scheduler activation in the current change;
- no application-code modification;
- no new generic workflow framework;
- no distributed scheduler or leader-election service;
- no public scheduling endpoint;
- no user/account-specific passive monitoring;
- no account synchronization or broker operation;
- no order, position, TradePlan or risk execution;
- no automatic ActiveScan escalation;
- no AI invocation;
- no historical backfill;
- no notifications;
- no new passive candidate aggregate;
- no global market ranking engine.

## Readiness Verdict

`READY_WITH_OPEN_DECISIONS`

The repository contains the required 0239/0240 durable contracts and the
existing execution/claim boundaries needed to implement 0241. The analysis is
concrete enough for human review, but the durable global capacity mechanism,
no-due audit semantics, readiness boundary and retry taxonomy require explicit
approval before planning.
