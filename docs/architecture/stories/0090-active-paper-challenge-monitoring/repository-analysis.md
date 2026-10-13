# Repository Analysis

## Starting State

- Starting branch: `main`
- Starting HEAD: `c51fcece` (`origin/main`), including the merged Story 0089
  benchmark commit.
- Worktree: clean before Story 0090 analysis.
- DevLog applicability check: attempted for `trading-os`, but unavailable because
  the connected Story Agent returned an exhausted API quota. Analysis continues
  from repository and canonical Story/ADR artifacts.

## Current Triggering Architecture

Challenge reevaluation currently exists only as an application service invoked
after PAPER settlement. `PaperSettlementService` mutates the Account, flushes,
and invokes `ChallengeReevaluationService.reevaluate(account)` inside its
settlement flow. No active-Challenge scheduler, event trigger, or periodic
monitoring orchestrator currently exists.

Trading Core already enables Spring scheduling in `TradingCoreApplication`.
`RiskAcknowledgmentDeliveryService` is the existing scheduling convention and
uses configurable `@Scheduled` delay properties. There is no dedicated
Challenge monitoring scheduler or `TaskScheduler` abstraction.

## Existing Reevaluation Path

`ChallengeReevaluationService` is a `REQUIRES_NEW` transactional application
service. It locates the ACTIVE Challenge by Account ID using
`ChallengeInstanceRepository.findByAccountIdAndStatus(...)`, which applies a
pessimistic write lock. It loads the immutable ChallengeDefinition, calls
`AccountRiskMonitoringService.evaluate(...)`, delegates lifecycle ordering to
`ChallengeEvaluator`, and persists only a changed terminal Challenge.

`ChallengeEvaluator` remains authoritative for ACTIVE/PASSED/BREACHED behavior,
including Risk-breach-before-target ordering, fresh EQUITY progression, stale or
unavailable equity fail-closed behavior, and terminal idempotency.

## PAPER Valuation and Risk Chain

The existing chain satisfies the Story boundary:

```text
monitoring trigger
  -> ChallengeReevaluationService
  -> AccountRiskMonitoringService
  -> ModeAwareRiskFactsProvider
  -> PaperDerivativeAccountValuationService.refresh(account)
  -> MarketDataClient fresh bid/ask snapshots
  -> Risk ACCOUNT_MONITORING
  -> ChallengeEvaluator
```

`ModeAwareRiskFactsProvider` selects PAPER behavior from `BrokerAccount.executionMode`.
For PAPER CASH it invokes `PaperDerivativeAccountValuationService.refresh`, which
requires CASH, resolves provider-neutral linear-perpetual market identity, rejects
missing/stale/invalid prices, calculates midpoint valuation, and persists the
fresh Account state. The provider converts valuation failure into incomplete Risk
facts; `AccountRiskMonitoringService` then fails closed rather than reusing stale
equity. The monitoring layer must call this path indirectly and must not inspect
prices or calculate financial values.

## Active Challenge Discovery

`ChallengeInstanceRepository` currently supports only Account-scoped ACTIVE
lookup, plus ordinary `JpaRepository` operations. It has no bounded ACTIVE query.
The smallest capability is a Spring Data `Page` query ordered by stable
`startedAt` and `id`, parameterized by `ChallengeStatus.ACTIVE` and
`Pageable`. The orchestrator should consume one page at a time and pass stable
Challenge IDs to a per-Challenge transactional operation rather than holding
discovered mutable entities across the batch.

The per-Challenge operation should reload the Challenge with a pessimistic lock,
load its Account and BrokerAccount, verify `ExecutionMode.PAPER`, and invoke the
existing reevaluation use case. LIVE and incomplete identities are skipped or
recorded as monitoring failures without entering PAPER valuation.

## Transactions and Concurrency

The existing Challenge lookup lock and `ChallengeInstance` optimistic version
provide lifecycle protection. `ChallengeReevaluationService` already uses
`REQUIRES_NEW`, which is appropriate for failure isolation. The new batch must
not wrap all candidates in one transaction. A scheduler cycle should discover
IDs outside the per-Challenge transaction, then invoke one isolated transaction
per candidate. A concurrent settlement or monitoring cycle can therefore either
observe the terminal state and no-op or serialize on the existing Challenge lock.

The implementation must avoid passing a long-lived Account entity from discovery
into reevaluation. The existing `reevaluate(Account)` API is settlement-oriented;
the smallest safe extension is an identifier-based reload path used by the
monitoring orchestration, while preserving the existing settlement caller.

## Configuration and Observability

Scheduling is already enabled globally. The new adapter should use repository
configuration conventions and expose at least enabled, interval, and page-size
properties. The core orchestration must remain directly invokable in tests and
must not depend on sleeping for scheduler timing.

The adapter/orchestrator should log cycle start/end, candidate count, per
Challenge success/transition/skip/failure, and duration. It must not log broker
credentials or serialized Risk/account payloads.

At a five-second interval, every evaluated ACTIVE Challenge currently persists an
ACCOUNT_MONITORING Risk context/evaluation. Approximate audit writes are therefore
12 cycles/minute per Challenge, 720/hour, or 17,280/day: 1 Challenge produces
that volume, 10 produce 172,800/day, and 100 produce 1,728,000/day. This is a
known scaling consideration; removing auditability is out of scope.

## Risk Day Boundary Analysis

`RiskDay` already derives timezone-aware half-open periods from each Account's
`riskTimeZone` and `riskDayResetTime`, including DST-aware `ZoneId` conversion.
`AccountRiskMonitoringService` persists the first authoritative balance observed
for the calculated Risk Day and reuses the immutable persisted baseline.

This does not reconstruct the balance at the exact reset instant. A periodic
trigger, application downtime, or Market Data unavailability cannot safely invent
that historical value. A generic boundary scheduler would still only observe a
post-boundary value unless a historical account-state capability exists.

**Decision:** exact reset-boundary capture is not safely solvable inside Story
0090 without a separate historical snapshot capability. Story 0090 should
implement periodic monitoring only, preserve first-authoritative-observation
semantics, and report:

> NO — Story 0090 performs periodic active monitoring, but the daily baseline
> remains the first authoritative observation inside the Risk Day. Exact
> historical reset-boundary capture remains a documented fidelity gap.

## Architectural Decision

No new ADR is required at analysis time. The scheduler is a thin adapter over an
application orchestration use case and does not change the existing ownership
boundaries. The durable scheduler-vs-event-trigger decision is documented in this
Story; future event triggers can call the same orchestration service.

## Implementation Recommendation

Proceed with a bounded ACTIVE Challenge query, an identifier-based isolated
reevaluation operation, a PAPER-only scheduler adapter, and configurable
properties. Add focused unit/integration coverage for discovery, failure
isolation, open-position breach/pass, idempotency, concurrency, Risk Day
first-observation semantics, and disabled scheduling.

This Repository Analysis is complete and awaits explicit human approval before
Implementation Planning.
