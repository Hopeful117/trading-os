# Story 0100 - Non-blocking Post-Settlement Challenge Reevaluation

## Metadata

- **ID:** `0100`
- **Status:** `PROPOSED`
- **Related Stories:** `0085`, `0086`, `0090`, `0095`, `0099`
- **Related ADRs:** `ADR-028`, `ADR-042`, `ADR-051`, `ADR-052`, `ADR-054`

## Goal

Allow a valid PAPER CASH `LINEAR_PERPETUAL` execution to complete without
indefinite PostgreSQL lock contention when Challenge reevaluation is triggered
after PAPER settlement, while preserving execution atomicity and authoritative
Challenge progression.

## Context

The real PAPER derivative lifecycle runtime validation reached:

```text
PAPER USD/CASH account
    -> ACTIVE Kraken Prop Challenge
    -> fresh Kraken LINEAR_PERPETUAL market
    -> accepted manual TradePlan
    -> approved PRE_TRADE Risk
    -> validated ExecutionIntent
    -> execution pipeline
    -> PaperSettlementService
```

The public execution request then failed to complete within 120 seconds. JVM
and PostgreSQL evidence showed the execution transaction had flushed Account
settlement state and then invoked `ChallengeReevaluationService.reevaluate()`
with `REQUIRES_NEW`. The nested transaction waited on state locked by the outer
execution transaction, while the scheduled Challenge monitor also waited on
related PostgreSQL locks.

The durable state remained unchanged:

```text
ExecutionIntent = VALIDATED
ExecutionAttempt rows = 0
T1 Risk rows = 0
Trade rows = 0
Account realizedBalance = 50000
Account equity = 50000
Open positions = 0
```

The runtime evidence is recorded in:

```text
docs/architecture/stories/0099-execution-validation-transaction-boundary/runtime-resumed-lifecycle-report.md
```

## Problem

PAPER settlement currently combines Account/Trade mutation and post-settlement
Challenge reevaluation in a transaction arrangement that can wait indefinitely
on locks held by the execution transaction itself. This prevents the execution
request from returning and prevents durable PAPER execution state from being
observed.

The fix must establish when Challenge reevaluation is allowed to observe
settlement state and must preserve the invariant that rolled-back settlement
cannot produce durable Challenge progression.

## Scope

- Reconstruct and document the transaction graph from execution through PAPER
  settlement and Challenge reevaluation.
- Establish the required consistency point between settlement commit and
  Challenge reevaluation using current repository architecture, ADRs, and tests.
- Reproduce the PostgreSQL lock contention with bounded integration coverage.
- Implement the smallest transaction-orchestration change that removes the
  indefinite lock wait without weakening Challenge locking.
- Preserve execution atomicity for ExecutionAttempt, Trade, Account mutation,
  intent lifecycle, and execution events according to current semantics.
- Preserve mandatory T1 Risk revalidation and fail-closed behavior.
- Preserve PAPER `CASH` plus `LINEAR_PERPETUAL` settlement semantics and the
  `realizedBalance`/equity authority defined by ADR-052.
- Add PostgreSQL-backed success, rollback, Challenge-failure, and scheduler
  concurrency regressions with bounded completion.
- Validate that Story 0099 execution validation remains green.
- Validate a fresh Compose PAPER execution through durable OPEN Trade and
  committed Account state.

## Out Of Scope

- Changes to Risk policies, Risk rules, T1 authorization, or Challenge rules.
- Removal or weakening of `PESSIMISTIC_WRITE` Challenge locking without an
  independently approved architectural decision.
- Funding, leverage, margin, liquidation, or provider-perfect fee modeling.
- Exact Kraken Risk Day boundary reconstruction.
- Challenge DTO projection improvements for nullable monitoring fields.
- Market Data, Market Intelligence, Broker provider, or Angular redesign.
- Full PAPER close lifecycle and A-F financial validation beyond the smoke checks
  required to prove this Story.
- Increasing HTTP, database, or client timeouts as a substitute for correcting
  transaction orchestration.
- Creating duplicate execution intents or mutating blocked runtime persistence
  as a workaround.

## Constraints

- PostgreSQL 16 behavior is authoritative for the locking regression; H2 alone
  is insufficient.
- All concurrency tests must have bounded completion and fail quickly when lock
  contention returns.
- A settlement that rolls back must not leave Challenge progression derived from
  that settlement.
- Challenge reevaluation failure semantics must be explicit and tested. The
  implementation must not silently swallow the failure or create an undefined
  partial-commit model.
- Scheduler overlap, retries, and duplicate reevaluation triggers must not cause
  duplicate settlement or duplicate Challenge effects.
- Existing runtime reports must remain preserved and must not be included in an
  unrelated production commit.
- The current execution validation transaction boundary from Story 0099 must
  remain effective.

## Acceptance Criteria

- The current execution and settlement transaction graph is documented,
  including propagation, flushes, locks, persistence, events, and remote I/O
  boundaries.
- The required Challenge consistency point relative to settlement commit is
  explicitly established from repository evidence and recorded in the Story
  implementation artifacts.
- PostgreSQL integration coverage deterministically reproduces or represents the
  pre-fix lock contention with a bounded timeout.
- A valid PAPER CASH `LINEAR_PERPETUAL` ExecutionIntent executes through the
  public boundary without indefinite lock waiting.
- T1 Risk remains mandatory and its approval/rejection semantics are unchanged.
- PAPER settlement persists the expected Account and Trade state without Spot
  inventory mutation.
- ExecutionAttempt, Trade, Account mutation, intent lifecycle, and execution
  events retain their required atomicity and lifecycle semantics.
- A settlement rollback removes the settlement mutation and does not leave
  durable Challenge progression from the rolled-back state.
- Challenge reevaluation failure behavior is explicit, bounded, and covered by a
  regression test.
- PAPER settlement can overlap scheduled Challenge monitoring without an
  indefinite lock wait, lost execution, duplicate settlement, or inconsistent
  Challenge state.
- Existing Challenge `PESSIMISTIC_WRITE` protection remains correct.
- Story 0099 execution validation tests remain green.
- Focused execution, settlement, Challenge, and PostgreSQL integration tests
  pass.
- Repository-standard broader validation is executed and reported accurately;
  interrupted or timed-out commands are not reported as passing.
- Fresh Compose runtime validation proves a durable OPEN PAPER Trade, committed
  Account state, coherent Challenge state, and continuing scheduler cycles.

## Expected Validation

- Transaction graph and consistency decision documented before implementation.
- PostgreSQL Testcontainers regression for the pre-fix contention path.
- PostgreSQL success-path integration test for validated execution through PAPER
  settlement and Challenge reevaluation.
- PostgreSQL rollback regression for late execution failure.
- PostgreSQL Challenge-reevaluation-failure regression according to the selected
  consistency model.
- Scheduler/settlement concurrency regression with bounded completion.
- Focused execution and Challenge test suites.
- Module-level `mvn -q test` and `mvn -q verify` where applicable.
- `git diff --check`.
- Fresh Compose runtime acceptance with PostgreSQL lock inspection.

## Architectural Decision Requirement

If repository evidence does not establish whether Challenge reevaluation must be
part of the settlement transaction or must observe only committed settlement,
implementation must stop and propose an ADR before changing transaction
orchestration. No transaction propagation change may be justified solely by
removing the observed wait.

## Human Validation

The implementation must return the transaction graph, consistency decision,
selected architecture, regression evidence, runtime evidence, and independent
review result for human approval before integration.
