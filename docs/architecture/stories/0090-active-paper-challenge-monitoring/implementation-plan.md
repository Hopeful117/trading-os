# Story 0090 - Implementation Plan

## Plan Status

`PROPOSED - AWAITING HUMAN APPROVAL`

The Repository Analysis was explicitly approved. This plan is limited to the
approved provider-neutral orchestration and scheduler scope.

## Design

### 1. Bounded ACTIVE discovery

Extend `ChallengeInstanceRepository` with a stable ordered Spring Data page
query:

```text
findByStatusOrderByStartedAtAscIdAsc(ACTIVE, Pageable)
```

The monitoring use case reads one page at a time using the configured batch
size. It passes only Challenge IDs to the per-Challenge operation and never
retains discovered mutable entities across transactions.

### 2. Identifier-based reevaluation

Extend `ChallengeReevaluationService` with an identifier-based entry point that:

1. opens the existing `REQUIRES_NEW` transaction;
2. reloads the Challenge by ID with a pessimistic write lock;
3. returns immediately when the Challenge is absent or no longer `ACTIVE`;
4. reloads the Account by the Challenge account ID;
5. reloads the BrokerAccount through the Account reference;
6. skips non-PAPER accounts without invoking Risk;
7. invokes the existing Account Risk monitoring and Challenge evaluator path.

Preserve the existing Account-based settlement entry point. No Risk,
valuation, settlement, or Challenge formulas move into the monitor.

### 3. Active monitoring orchestration

Add `ActiveChallengeMonitoringService` with a directly invokable cycle method.
It will:

- log cycle start and duration;
- page through ACTIVE Challenge IDs;
- call the identifier-based reevaluation for each candidate;
- record candidate count and per-candidate outcome;
- catch and log each candidate failure independently;
- continue processing later candidates after a failure.

The service will not call Market Data directly and will not calculate financial
values. It will rely on the existing `ModeAwareRiskFactsProvider` chain to
refresh PAPER CASH valuation and fail closed on unavailable data.

### 4. Scheduler adapter and configuration

Add a thin `@Scheduled` adapter under the existing Trading Core application
conventions. It will invoke only the monitoring service. Configuration will be
bound through a small application properties record/class with:

```text
enabled
interval
batch-size
```

Use `@ConditionalOnProperty` or the repository-equivalent bean condition so
`enabled=false` prevents scheduler creation while direct orchestration remains
available. The interval will not appear in domain/application logic.

### 5. Concurrency and idempotency

Reuse the existing pessimistic Challenge lock, optimistic entity version, and
`REQUIRES_NEW` transaction. The locked reload ensures a settlement-triggered
reevaluation or another monitoring cycle sees either the current ACTIVE state or
the terminal state. Terminal Challenges are no longer returned by the paged
ACTIVE query. No global lock or scheduler-level transaction will be added.

### 6. Risk Day behavior

Do not add a reset scheduler or historical reconstruction. Tests will verify
that monitoring calls the existing timezone/reset calculation and immutable
first-observation baseline behavior. Documentation will state explicitly:

> NO — periodic monitoring does not capture the exact configured reset instant;
> the baseline remains the first authoritative observation inside the Risk Day.

## Files and Modules

Expected production changes:

- `trading-core/.../challenge/application/ChallengeInstanceRepository.java`
- `trading-core/.../challenge/application/ChallengeReevaluationService.java`
- new `trading-core/.../challenge/application/ActiveChallengeMonitoringService.java`
- new scheduler/configuration classes under the existing Trading Core package
  conventions
- `trading-core/src/main/resources/application.properties`

Expected tests:

- repository paging and terminal filtering;
- orchestration discovery and multiple-candidate processing;
- failure isolation;
- PAPER-only eligibility and LIVE exclusion;
- open-position valuation-to-breach integration;
- open-position valuation-to-pass integration;
- unchanged repeated monitoring and terminal idempotency;
- Risk Day timezone/reset and first-observation semantics;
- concurrent monitoring/settlement lifecycle protection;
- disabled scheduler configuration.

No `risk-domain` production change is planned.

## Validation Plan

Run focused tests first:

```bash
mvn -q -Dtest=ActiveChallengeMonitoringServiceTest,ChallengeReevaluationServiceTest test
mvn -q -Dtest=ChallengePersistenceTest,PaperDerivativeAccountValuationServiceTest test
```

Then run:

```bash
mvn -q verify
```

in `trading-core`, and `mvn -q verify` in `risk-domain` to confirm no Risk
regression. Also run:

```bash
git diff --check
```

Scheduler tests must invoke the adapter/service directly or use deterministic
Spring scheduling controls; no arbitrary sleeps will be introduced.

## Explicit Exclusions

- no provider-specific or Kraken-specific code;
- no market-data event bus or distributed scheduler;
- no automatic order, position, or broker action;
- no funding, fee, leverage, margin, liquidation, payout, or frontend work;
- no repair of the historical PostgreSQL V3/V9 migration ordering defect.

## Approval Required

Human approval is required before implementation begins.
