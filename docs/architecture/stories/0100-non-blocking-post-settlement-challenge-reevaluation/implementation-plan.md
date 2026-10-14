# Story 0100 - Implementation Plan

## Architectural Decision

Implement ADR-054: PAPER settlement creates a durable Challenge reevaluation
request in the settlement transaction; delivery occurs after commit in a
separate transaction with claim, lease, retry, and explicit failure state.

## Changes

1. Add the PostgreSQL/common migration for a Challenge reevaluation outbox
   table keyed idempotently by the settlement trigger, with account identity,
   request status, attempt count, claim token, lease, next-attempt time,
   failure message, and timestamps.
2. Add persistence for the outbox using the existing Risk acknowledgment
   outbox conventions: transactional enqueue, pessimistic claim, lease expiry,
   delivered update, failed retry scheduling, and bounded due-item lookup.
3. Add an application service that performs one claimed Challenge reevaluation
   in its own transaction and a scheduled delivery pass for due requests.
4. Change `PaperSettlementService` to enqueue the reevaluation request instead
   of directly calling `ChallengeReevaluationService` before the outer commit.
   The enqueue must occur after the settlement state is prepared and must join
   the settlement transaction.
5. Add a post-commit delivery attempt only as a latency optimization; durable
   scheduled delivery remains authoritative and execution must return without
   waiting for Challenge reevaluation.
6. Preserve the existing Challenge `REQUIRES_NEW` reevaluation service and
   `PESSIMISTIC_WRITE` repository locks.
7. Preserve T1 Risk, execution lifecycle persistence, execution events,
   PAPER CASH `LINEAR_PERPETUAL` settlement, realized-balance authority, and
   existing scheduler behavior.

## Expected Affected Areas

- `trading-core` Challenge application and persistence packages.
- `trading-core` PAPER settlement orchestration.
- `trading-core` database migrations and test configuration.
- Story 0100 ADR and implementation artifacts.

No changes are planned to Risk rules, broker adapters, Market Data, Gateway,
frontend behavior, or Challenge pessimistic locking.

## Regression Coverage

Add PostgreSQL-backed tests for:

1. The pre-fix direct reevaluation path represented with a bounded lock timeout.
2. Successful PAPER CASH `LINEAR_PERPETUAL` execution returning without
   waiting on Challenge reevaluation and leaving one durable pending/delivered
   trigger.
3. Settlement rollback removing both settlement mutations and its outbox
   request.
4. Challenge reevaluation failure leaving the committed settlement intact,
   recording a retryable outbox failure, and completing within a bounded time.
5. Concurrent scheduler and post-commit delivery using one claim and retaining
   Challenge `PESSIMISTIC_WRITE` protection without duplicate progression.
6. Retry after an expired lease and idempotent delivery after an already
   delivered request.
7. Existing Story 0099 transaction-boundary and execution persistence tests.

Assertions must cover Account realized/equity state, Trade state, execution
intent/attempt lifecycle, event persistence, Challenge state, outbox state, and
bounded completion. Tests must run against PostgreSQL; H2-only coverage is not
sufficient for the lock regression.

## Validation

- Focused Challenge, settlement, and execution tests.
- PostgreSQL Testcontainers concurrency and rollback tests.
- `mvn -q -Dtest=... test` for affected focused suites.
- `mvn -q test` and `mvn -q verify` for `trading-core` where executable.
- `git diff --check`.
- Fresh Compose PAPER CASH `LINEAR_PERPETUAL` execution with PostgreSQL lock
  inspection and scheduler-cycle evidence.
- Report unavailable, interrupted, or timed-out validation explicitly.

## Constraints

- Do not change Risk policies or T1 semantics.
- Do not remove or weaken Challenge pessimistic locks.
- Do not make Challenge reevaluation asynchronous without durable request state.
- Do not swallow reevaluation failures without persisted retry state.
- Do not duplicate execution intents or mutate the database outside the
  application transaction.
- Do not increase timeouts as a workaround.
- Do not include the existing untracked runtime reports in the production
  implementation diff.
- Do not commit or push automatically.
