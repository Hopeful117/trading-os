# Repository Analysis - Story 0100

## Scope

This analysis covers the current execution transaction, PAPER settlement,
Challenge reevaluation, scheduler overlap, persistence flushes, event
publication, and remote I/O boundaries. It does not select or implement the
transaction-orchestration change.

## Current Execution Graph

The public execution path is:

```text
ExecuteTradeService.execute
  @Transactional (outer transaction, default REQUIRED)
    -> load ExecutionIntent
    -> ChallengeExecutionAdmissionService.validate
    -> ExecutionTimeRiskRevalidationService.evaluateAndPersist
       -> TransactionTemplate on the same transaction manager
       -> risk context providers may call broker/market dependencies
    -> ExecutionValidationStep
    -> IdempotencyVerificationStep
    -> ExecutionAttemptCreationStep
       -> ExecutionAttempt adapter saveAndFlush
    -> BrokerSubmissionStep
       -> ExecutionAttempt/Intent saves
       -> BrokerExecutionPort.submit
          -> PAPER simulated adapter, or LIVE HTTP Broker Service call
    -> BrokerResponseProcessingStep
       -> build BrokerOrder and Fill for acknowledged result
    -> ExecutionFinalizationStep
       -> PAPER PaperSettlementService.settle
       -> lifecycle transition
       -> BrokerOrder saveAndFlush, including fills
       -> TradeOutcome saveAndFlush for ENTRY
       -> ExecutionAttempt saveAndFlush
       -> ExecutionIntent saveAndFlush
    -> persistent execution event publication
  transaction commit
```

`ExecuteTradeService.execute` is the effective transaction boundary for the
complete execution pipeline. The repository adapters are transactional but do
not create an independent transaction when called from this boundary.

`PaperSettlementService.settle` is also annotated `@Transactional` with the
default `REQUIRED` propagation. It therefore joins the execution transaction.
Its `accountRepository.flush()` is an explicit database flush, not a commit.
The Account, balances, Trade collection, and derived CASH valuation remain
locked and uncommitted until the outer execution transaction completes.

The execution path includes remote I/O inside the outer transaction:

- T1 risk context loading can use broker and market ports.
- LIVE broker submission uses the Broker Service HTTP client.
- PAPER submission is local and does not make a broker call.

The transaction is consequently held across the execution pipeline and any
remote dependency latency. This is existing behavior and is outside Story
0100 except where it explains lock duration.

## Current Settlement and Reevaluation Graph

For a valid PAPER acknowledgement:

```text
ExecutionFinalizationStep
  -> PaperSettlementService.settle [joins outer transaction]
     -> load PAPER BrokerAccount
     -> load Account by broker-account reference
     -> validate owner/provider and settlement/product compatibility
     -> mutate Account balances or realizedBalance/equity
     -> create/update local PAPER Trade
     -> accountRepository.save(account)
     -> accountRepository.flush()
     -> ChallengeReevaluationService.reevaluate(account)
        @Transactional(REQUIRES_NEW)
        -> find active Challenge by account with PESSIMISTIC_WRITE
        -> load Challenge definition
        -> AccountRiskMonitoringService.evaluate
        -> ChallengeEvaluator.evaluate
        -> persist Challenge transition when status changes
  -> continue execution finalization
  -> outer transaction commit
```

The `REQUIRES_NEW` call suspends the outer transaction. It cannot observe the
uncommitted settlement as a committed database state, and its Challenge query
can wait on rows locked by the suspended outer transaction. The outer
transaction cannot commit while the Java call is waiting for the inner
transaction to return. This is a self-induced lock wait, not a missing index
or a timeout configuration problem.

The current catch in `PaperSettlementService` logs and suppresses a runtime
failure from Challenge reevaluation. That makes settlement completion and
Challenge progression independent in practice, but without a durable trigger,
retry state, or explicit failure status. It therefore does not satisfy Story
0100's requirement for explicit failure semantics.

## Lock and Concurrency Graph

The relevant lock ownership is:

```text
Outer execution transaction
  -> Account and AccountBalance rows changed by PAPER settlement
  -> Trade rows/collection changed by settlement
  -> execution persistence rows flushed by repository adapters

Inner Challenge REQUIRES_NEW transaction
  -> ChallengeInstance selected with PESSIMISTIC_WRITE
  -> monitoring reads the Account and evaluates current state

Scheduled monitoring transaction
  -> ChallengeReevaluationService.reevaluate(challengeId)
     @Transactional(REQUIRES_NEW)
     -> ChallengeInstance.findById with PESSIMISTIC_WRITE
     -> Account reload and monitoring evaluation
```

The scheduler calls the same `REQUIRES_NEW` service and catches failures per
Challenge, so it can overlap an execution-triggered reevaluation. The
Challenge pessimistic lock is an intentional serialization boundary and must
remain. Removing or weakening it would violate the Story and the existing
Challenge concurrency design.

The scheduler's paged discovery queries do not use the pessimistic lock, but
each reevaluation by ID does. The execution-triggered path uses the account
lookup query with the same lock mode. The two paths can therefore contend on
the active Challenge row even after the self-induced outer/inner wait is
removed.

## Consistency Point

The required consistency point is:

```text
Challenge reevaluation may consume settlement state only after the settlement
transaction has committed successfully.
```

This follows from the Story's rollback invariant: a settlement that rolls back
must not produce durable Challenge progression derived from that settlement.
It also follows from the current persistence model: the Challenge service
reloads Account state from repositories and cannot safely use the uncommitted
outer persistence context after its transaction is suspended.

The current direct invocation before outer commit violates this consistency
point. Changing `REQUIRES_NEW` to `REQUIRED` would remove the self-suspension,
but would make Challenge reevaluation part of the settlement transaction. That
alternative remains a possible implementation direction only if its failure
and scheduler-concurrency semantics are explicitly approved; it is not a
post-commit observer and would need to preserve atomicity for all Challenge
state changes.

## Events and Persistence

Execution domain events are pulled and published from `ExecuteTradeService`
before the method returns. The persistent event publisher is transactional,
so event rows participate in the current transaction. A publication before a
rollback cannot be treated as a committed post-settlement trigger unless the
event mechanism is changed or an after-commit delivery boundary is introduced.

Repository adapters for ExecutionAttempt, BrokerOrder, fills, and TradeOutcome
use `saveAndFlush`, while Account settlement explicitly flushes. These flushes
make database locks visible earlier but do not establish durable visibility.
The final commit remains the outer `ExecuteTradeService` transaction commit.

## Repository Evidence

Primary implementation evidence:

- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/ExecuteTradeService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/pipeline/ExecutionFinalizationStep.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/PaperSettlementService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/challenge/application/ChallengeReevaluationService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/challenge/application/ActiveChallengeMonitoringService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/challenge/application/ChallengeInstanceRepository.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/adapter/JpaExecutionAttemptAdapter.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/adapter/JpaBrokerOrderAdapter.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/observability/PersistentExecutionEventPublisher.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/challenge/infrastructure/ChallengeMonitoringConcurrencyIntegrationTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/execution/PaperExecutionPersistenceIntegrationTest.java`

Relevant decisions:

- ADR-028 requires deterministic, auditable, fail-closed Risk behavior and
  keeps Risk independent from execution and broker communication.
- ADR-042 keeps Trading Core authoritative for PAPER positions and settlement.
- ADR-051 requires deterministic, fail-closed CASH plus
  `LINEAR_PERPETUAL` compatibility.
- ADR-052 makes `realizedBalance` the PAPER CASH settlement baseline and
  `equity = realizedBalance + unrealized P&L`.

## Analysis Conclusion

The defect is confirmed as transaction orchestration: a nested
`REQUIRES_NEW` Challenge reevaluation is invoked after a settlement flush but
before the outer settlement/execution commit, causing lock contention against
the suspended transaction. The correct observable consistency point is after
successful settlement commit.

The repository does not currently define a durable after-commit Challenge
trigger or explicit reevaluation-failure state. It also does not establish
whether Challenge reevaluation must be atomic with settlement or must be an
after-commit observer. Per Story 0100's Architectural Decision Requirement,
this is an architectural decision that must be approved before changing
transaction propagation or introducing an event/outbox mechanism.

Implementation must therefore stop at this analysis until the human approves
one of the following directions:

1. Same-transaction reevaluation with explicit failure/rollback semantics and
   bounded scheduler interaction; or
2. Committed-settlement plus durable after-commit reevaluation trigger with
   explicit retry/failure semantics.

No production code or tests were changed by this analysis.
