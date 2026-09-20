# Repository Analysis - Story 0052

## Baseline

```text
STORY = 0052-paper-execution-via-broker-contract
BRANCH = story/0050-paper-risk-context (planning artifact; implementation branch to be created later)
```

## Existing Authority

ADR-029 assigns execution lifecycle, idempotency, recovery, reconciliation
decisions, and audit to the Execution Domain in Trading Core.

ADR-030 assigns provider communication and technical translation to Broker
Service.

ADR-042 assigns position authority by mode:

```text
LIVE  -> Broker/provider
PAPER -> Trading Core local state
```

This Story must not reinterpret a shared execution contract as shared position
authority.

## Current Implementation

`SimulatedExecutionAdapter` currently performs PAPER market lookup and returns a
synthetic acknowledged fill. `PaperSettlementService` mutates local balances
and trades. `RoutingBrokerExecutionAdapter` selects the mode-specific adapter.

ADR-042 identifies the required local transaction and settlement invariants.

The existing Execution Domain already contains concepts for:

* ExecutionIntent;
* ExecutionAttempt;
* idempotency;
* broker order and fill;
* unknown outcomes;
* reconciliation;
* explicit execution states.

## Candidate Design

Keep one conceptual execution pipeline with mode-specific technical mutation:

```text
Trading Core Execution Domain
    +--> PAPER -> local simulator + local settlement
    +--> LIVE  -> Broker Service -> provider
```

Shared concepts include authorization, idempotency, audit, version binding, and
neutral outcomes. Authority and mutation mechanics remain mode-specific.

## Risks

* A shared status model must not imply external reconciliation for PAPER.
* Local PAPER failures require rollback or explicit recoverable state.
* Existing frontend contracts may assume broker position references.
* Current simulated adapter does not model partial fills or pending orders;
  adding those now would exceed the first round-trip scope.

## Validation Plan

* Entry execution regression tests for PAPER and LIVE.
* Idempotency and concurrent execution tests.
* Version mismatch and revalidation tests.
* Unknown and reconciliation tests for LIVE.
* Local rollback/recovery tests for PAPER.
* Full local PAPER close tests.
* Angular state tests for neutral execution statuses.

## Workflow Gate

```text
ADR_CONFLICT_CHECK = completed against ADR-028, ADR-029, ADR-030, ADR-042, ADR-044
REPOSITORY_ANALYSIS = completed
IMPLEMENTATION_PLAN = required
HUMAN_APPROVAL = required before implementation
```
