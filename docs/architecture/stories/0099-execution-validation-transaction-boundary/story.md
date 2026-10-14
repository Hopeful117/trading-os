# Story 0099 - Execution Validation Transaction Boundary

## Metadata

- **ID:** `0099`
- **Status:** `COMPLETED`
- **Related Stories:** `0088`, `0098`
- **Related ADRs:** None identified during repository inspection

## Goal

Ensure execution validation runs inside an application transaction before it
loads the pessimistically locked ACTIVE Challenge, creates an ExecutionIntent,
and publishes the validation events.

## Context

Runtime validation of Story 0098 reached authenticated execution validation but
failed before ExecutionIntent creation with `TransactionRequiredException`.
The failure was surfaced as an empty `403` response even though it was an
infrastructure transaction failure.

`ChallengeInstanceRepository.findByAccountIdAndStatus` uses
`PESSIMISTIC_WRITE`, so its execution path requires an active transaction.

## Scope

- Establish the transaction boundary on the public validation application
  service entry point.
- Ensure Spring can proxy that service so the annotation is effective at
  runtime.
- Add regression coverage for the transaction annotation and Spring proxy.
- Preserve existing Challenge compatibility, Risk, idempotency, lifecycle,
  event, and HTTP validation behavior.

## Out Of Scope

- Changes to Risk rules or Market Data.
- Changes to Challenge compatibility rules.
- Changes to T1 or broker execution behavior.
- Changes to exception status mapping unrelated to the transaction failure.
- Full PAPER execution or broker connectivity.

## Acceptance Criteria

- `ValidateAndCreateService.validateAndCreate` executes within a Spring
  transaction when invoked through the application context.
- The service is proxyable by Spring transaction management.
- Existing execution validation tests remain green.
- Existing execution package tests remain green.
- No execution intent is created before the existing validation and Challenge
  compatibility checks complete.
- No Risk, Market Data, Challenge, or provider-specific behavior changes.
- Runtime PostgreSQL validation is reported separately if not executed.

## Resolution Evidence

- PostgreSQL Testcontainers validation acquires the ACTIVE Challenge
  `PESSIMISTIC_WRITE` lock successfully.
- The lifecycle collaborator observes an active transaction.
- A late lifecycle failure rolls back the persisted ExecutionIntent and its
  execution event.
