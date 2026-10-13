# Repository Analysis

## Starting State

- Branch: `main`.
- HEAD: `f0694da0c883d8b727001aebec844cd3c83548f6`.
- Story 0094 is merged; Story 0095 product implementation has not started.
- The worktree contains the Story 0095 artifact and the prior E2E continuation
  report, but no Story 0095 production or test-code changes.
- DevLog applicability was checked before this investigation. The Story Agent
  was unavailable because the connected service reported exhausted quota. This
  analysis therefore uses the repository, canonical Story artifacts, ADRs, and
  runtime evidence.

## Runtime Reproduction

The resumed PostgreSQL-backed E2E environment used PostgreSQL 16.15, Flyway
schema V31, Java 21.0.12, and Trading Core from the current `main` commit.
The public Gateway flow successfully created and activated a PAPER Challenge.
The next scheduled monitoring cycle failed before discovering it.

The generated SQL was:

```sql
select ...
from challenge_instance ci
where ci.status = ?
  and (? is null
       or ci.started_at > ?
       or (ci.started_at = ? and ci.id > ?))
order by ci.started_at, ci.id
fetch first ? rows only
```

PostgreSQL returned:

```text
SQLState 42P18
ERROR: could not determine data type of parameter $2
```

The second parameter is the null first-page value used by `:afterStartedAt`.
It appears in `? is null` without a type-bearing comparison. PostgreSQL cannot
infer its type before evaluating the first page. The same failure recurred on
subsequent scheduler ticks, and the Challenge remained unmonitored.

The current PostgreSQL schema contains the `challenge_instance` primary-key
index and the `(account_id, active_marker)` unique index. No new index is
required to explain or fix this typing failure, so no schema change is
justified by the evidence.

## Affected Production Flow

`ScheduledChallengeMonitoringTrigger` invokes
`ActiveChallengeMonitoringService.monitor()` on the configured interval. The
monitor currently initializes both cursor values to `null` and calls
`ChallengeInstanceRepository.findNextByStatus(...)` for every page.

`ActiveChallengeMonitoringService` owns only bounded discovery and candidate
coordination. It:

- requests `ACTIVE` rows in batches;
- advances the cursor from the final row using `(startedAt, id)`;
- filters incomplete accounts and non-`PAPER` broker accounts;
- invokes identifier-based reevaluation;
- isolates per-Challenge runtime failures;
- reports cycle statistics.

`ChallengeReevaluationService.reevaluate(UUID)` remains the authoritative
per-Challenge operation. It opens a `REQUIRES_NEW` transaction, reloads the
Challenge with the existing pessimistic write lock, no-ops terminal or missing
Challenges, reloads Account and BrokerAccount state, rejects non-PAPER modes,
and delegates Risk/valuation/Challenge transition behavior to existing
services.

No transaction, lock, Risk, valuation, settlement, or scheduler architecture
change is required for this Story.

## Existing Query and Test Coverage

`ChallengeInstanceRepository.findNextByStatus` is the only affected discovery
query. Its current JPQL correctly expresses the intended ordering and strict
continuation semantics, but combines first-page and continuation behavior using
nullable parameters.

Existing H2-backed coverage includes:

- first and second bounded pages;
- stable ordering by `startedAt` and ID;
- terminal-state exclusion;
- multiple-page service processing;
- PAPER-only execution;
- per-Challenge failure isolation;
- identifier-based locked reevaluation concurrency.

The existing repository test does not execute against PostgreSQL, so it cannot
detect PostgreSQL parameter typing behavior. The existing
`PostgresFreshInstallMigrationTest` provides PostgreSQL Testcontainers and
Flyway setup, but does not currently load the JPA repository context or test
Challenge monitoring queries.

The test profile is H2-based. PostgreSQL regression coverage therefore needs a
focused PostgreSQL-backed test configuration or test class, reusing the
repository's existing Testcontainers conventions rather than changing the
production datasource configuration.

## Relevant Architectural Decisions

- ADR-028 requires deterministic, auditable Risk behavior and separation of
  Risk evaluation from infrastructure concerns. This Story must remain a
  repository/monitoring fix and must not move financial calculations.
- ADR-051 keeps settlement-model and instrument-product semantics in Trading
  Core without making Challenge monitoring provider-specific.
- ADR-052 establishes Trading Core as the authority for PAPER CASH realized
  balance and fresh equity valuation. Monitoring must continue to reach that
  path indirectly through `ChallengeReevaluationService`.
- Story 0090 and its implementation artifacts are authoritative for bounded
  discovery, stable ordering, PAPER filtering, locking, `REQUIRES_NEW`, failure
  isolation, and cycle statistics.

## Selected Query Design

Replace the single nullable-cursor query with two repository queries:

1. A first-page query accepting only `status` and `Pageable`, with no cursor
   parameters and no `is null` expression.
2. A continuation query accepting non-null `status`, `afterStartedAt`, and
   `afterId`, using only:

   ```text
   startedAt > afterStartedAt
   OR (startedAt = afterStartedAt AND id > afterId)
   ```

Both queries retain `status = ACTIVE`, ascending `(startedAt, id)` ordering,
and the configured bounded `Pageable`. The monitor selects the first-page
method only while both cursors are unset, then selects the continuation method
after each non-empty batch.

This design is provider-neutral and structurally typed: PostgreSQL sees the
cursor parameters only in comparisons against typed entity attributes. It also
preserves strict keyset semantics, same-timestamp ID tie-breaking, and avoids
unbounded `findAll()` behavior. A PostgreSQL cast is unnecessary and would be a
less portable solution.

The implementation should validate the cursor invariant explicitly: a returned
batch must provide both `startedAt` and `id` for the next query. Existing domain
invariants make those values non-null; no fallback to offset pagination or
unbounded loading is appropriate.

## Required Validation Coverage

Add PostgreSQL-backed repository coverage for:

- empty ACTIVE state;
- first-page discovery with a null cursor eliminated from SQL;
- cursor-present continuation;
- strict exclusion of the cursor row;
- multiple Challenges sharing `startedAt`, ordered by ID;
- multiple pages covering every expected ACTIVE row once;
- terminal rows excluded;
- LIVE rows still excluded by the monitoring service rather than the generic
  status query.

Add PostgreSQL-backed monitoring-service integration coverage proving that the
actual orchestration processes PAPER candidates, preserves per-candidate
failure isolation, and does not reevaluate LIVE or terminal Challenges.

Keep the existing H2 unit and concurrency tests passing. The runtime acceptance
must then start a fresh PostgreSQL-backed environment, allow a real scheduler
cycle, verify absence of SQLState `42P18`, and retrieve the monitored Challenge
through the public Gateway to confirm progress fields are populated.

## Scope Boundary and Recommendation

The affected production scope is limited to:

- `ChallengeInstanceRepository` query methods;
- the cursor branch in `ActiveChallengeMonitoringService`;
- focused PostgreSQL and monitoring integration tests;
- Story 0095 validation and documentation.

No migration, index, Risk Domain change, Challenge progression change, provider
change, or broader E2E continuation is indicated by the current evidence.

Repository Analysis is complete and awaits explicit human approval before an
Implementation Plan is prepared.
