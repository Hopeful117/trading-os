# Story 0099 - Repository Analysis

## Current Evidence

- `ValidateAndCreateService.validateAndCreate` is the public application
  entry point for `POST /api/v1/executions/validate`.
- `ChallengeInstrumentCompatibilityService.validate` calls
  `ChallengeInstanceRepository.findByAccountIdAndStatus`.
- That repository method is annotated with `@Lock(PESSIMISTIC_WRITE)`.
- Before this change, `ValidateAndCreateService` had no transaction boundary
  and was declared `final`.
- `ExecuteTradeService.execute` already establishes the repository convention
  with Spring `@Transactional`.
- The service is declared as an explicit `@Bean` in `ExecutionConfiguration`.
- The runtime report for Story 0098 records `No active transaction` and
  `TransactionRequiredException` before ExecutionIntent creation.

## Root Cause

The validation use case performed a pessimistic-lock repository query without
an active transaction. Adding only a method annotation would not be sufficient
while the service remained `final`, because Spring's default class-based proxy
could not proxy the target class.

## Affected Boundaries

- Trading Core execution validation application service.
- Spring transaction proxy creation.
- Challenge repository lock acquisition.

The following boundaries are intentionally unchanged:

- Risk evaluation and T1 revalidation.
- Challenge product compatibility decisions.
- Execution lifecycle transitions.
- Idempotency and event publication semantics.
- Broker and PAPER settlement paths.

## Risks

- The current tests prove annotation presence and Spring proxy creation, but do
  not yet reproduce the exact PostgreSQL `PESSIMISTIC_WRITE` validation flow.
- The live runtime flow has not been rerun after the fix.
- The canonical Story 0099 artifact was missing before this work; no prior
  approved acceptance criteria were available beyond the runtime blocker and
  repository conventions.

## Repository Decision

Use the existing application-service transaction pattern: make the validation
service proxyable and annotate its public use-case method with Spring
`@Transactional`. Do not move transaction management into the controller, the
Challenge repository, Risk, or domain logic.
