# IMPLEMENTATION PLAN
## ADR-029 – Execution Domain

**Related ADR:** ADR-029 – Execution Domain Architecture

**Status:** Implemented

**Implementation report:** `docs/implementation/ADR-029-implementation.md`

---

# Objective

This document defines the implementation strategy for the Execution Domain.

Unlike ADR-029, which defines architectural decisions, this document specifies:

- implementation order
- package organization
- domain model
- interfaces
- application services
- persistence
- testing strategy

The implementation follows an incremental approach to minimize risk while preserving architectural integrity.

---

# High-Level Architecture

```
                    Trading Core

         +-----------------------------+
         |                             |
         |      Execution Domain       |
         |                             |
         +-----------------------------+

        Domain
            │
            ▼
      Application Layer
            │
            ▼
         Ports
            │
            ▼
Infrastructure Adapters
            │
            ▼
       Broker Service
```

The Execution Domain remains completely independent from infrastructure.

---

# Package Structure

```
execution/

    application/
        command/
        query/
        service/
        pipeline/

    domain/

        aggregate/
        model/
        valueobject/
        event/
        exception/
        repository/
        service/

    infrastructure/

        persistence/
        mapper/
        adapter/
        configuration/

    api/

        controller/
        dto/
```

Each package owns a single responsibility.

---

# Domain Aggregates

The Execution Domain initially contains three aggregates.

## ExecutionIntent

Business execution authorization.

Responsibilities:

- references TradePlan
- references Risk approval
- owns lifecycle
- owns idempotency
- owns execution metadata

Persistence:

ExecutionIntentRepository

---

## ExecutionAttempt

Represents one broker submission.

Responsibilities:

- attempt number
- broker correlation
- timestamps
- submission result
- retry metadata

Each ExecutionIntent owns multiple attempts.

---

## BrokerOrder

Represents the broker-side order.

Responsibilities:

- broker identifiers
- execution status
- fills
- execution timestamps
- broker metadata

BrokerOrder remains broker-independent.

---

# Value Objects

Initial value objects.

## ExecutionIntentId

Immutable identifier.

---

## ExecutionAttemptId

Immutable identifier.

---

## BrokerOrderId

Internal identifier.

---

## IdempotencyKey

Immutable business key.

Responsible for duplicate prevention.

---

## ExecutionStatus

Represents ExecutionIntent lifecycle.

---

## AttemptStatus

Represents ExecutionAttempt lifecycle.

---

## BrokerOrderStatus

Represents broker order lifecycle.

Statuses remain independent.

---

# Repository Ports

The domain exposes repository interfaces only.

```
ExecutionIntentRepositoryPort

ExecutionAttemptRepositoryPort

BrokerOrderRepositoryPort
```

Persistence implementations belong to Infrastructure.

---

# Domain Services

The following services belong inside the domain.

## ExecutionValidationService

Validates:

- approval
- expiration
- invariants

No infrastructure dependency.

---

## IdempotencyService

Responsible for:

- duplicate detection
- concurrency validation
- immutable execution identity

---

## RecoveryStrategyService

Determines:

- whether recovery is required
- reconciliation strategy
- resume conditions

---

## ExecutionLifecycleService

Centralizes lifecycle transitions.

All aggregate state changes pass through this service.

---

# Application Services

Application services orchestrate use cases.

They never contain business rules.

Initial services:

```
CreateExecutionIntentService

ExecuteTradeService

RetryExecutionService

RecoverExecutionService

CancelExecutionService

QueryExecutionService
```

Each service orchestrates domain objects.

---

# Execution Pipeline

The execution pipeline is implemented using explicit pipeline components.

```
ExecutionValidationStep

↓

IdempotencyVerificationStep

↓

ExecutionAttemptCreationStep

↓

BrokerSubmissionStep

↓

BrokerResponseProcessingStep

↓

ExecutionFinalizationStep
```

Each step:

- one responsibility
- independently testable
- reusable
- deterministic

---

# Recovery Pipeline

Recovery uses a second pipeline.

```
RecoverableExecutionDiscoveryStep

↓

ExecutionInspectionStep

↓

RecoveryStrategyStep

↓

BrokerReconciliationStep

↓

RecoveryFinalizationStep
```

Recovery remains isolated from normal execution.

---

# Broker Port

Execution communicates with Broker Service through one application port.

```
BrokerExecutionPort
```

Responsibilities:

- submit order
- cancel order
- retrieve execution status
- reconcile execution

No broker SDK enters the domain.

---

# DTO Mapping

Infrastructure maps between:

Broker DTO

↓

Broker Adapter

↓

Domain Objects

↓

Application DTO

Broker payloads never leak into domain entities.

---

# Persistence Strategy

ExecutionIntent

↓

ExecutionAttempt

↓

BrokerOrder

are persisted independently.

Relationships remain explicit through identifiers.

Optimistic locking is enabled on mutable aggregates.

No cascade-based business behavior is allowed.

# Implementation Phases

The Execution Domain should be implemented incrementally.

Each phase delivers a coherent and testable milestone.

---

# Phase 1 – Domain Foundations

## Objective

Create the core domain model without infrastructure.

## Deliverables

Aggregates

- ExecutionIntent
- ExecutionAttempt
- BrokerOrder

Identifiers

- ExecutionIntentId
- ExecutionAttemptId
- BrokerOrderId

Value Objects

- IdempotencyKey
- ExecutionStatus
- AttemptStatus
- BrokerOrderStatus

Exceptions

- InvalidExecutionStateException
- DuplicateExecutionException
- ExecutionExpiredException

Repository Ports

- ExecutionIntentRepositoryPort
- ExecutionAttemptRepositoryPort
- BrokerOrderRepositoryPort

Acceptance Criteria

- Domain compiles
- No Spring dependency
- Unit tests pass

---

# Phase 2 – Execution Pipeline

## Objective

Implement deterministic execution orchestration.

Pipeline

ExecutionValidationStep

↓

IdempotencyVerificationStep

↓

ExecutionAttemptCreationStep

↓

BrokerSubmissionStep

↓

BrokerResponseProcessingStep

↓

ExecutionFinalizationStep

Deliverables

Application Services

- ExecuteTradeService

Pipeline Components

- ExecutionValidationStep
- IdempotencyVerificationStep
- ExecutionAttemptCreationStep
- BrokerSubmissionStep
- BrokerResponseProcessingStep
- ExecutionFinalizationStep

Acceptance Criteria

- Pipeline fully testable
- Invalid states rejected
- Idempotency enforced

---

# Phase 3 – Broker Integration

## Objective

Connect the Execution Domain to the Broker Service.

Deliverables

Port

BrokerExecutionPort

Infrastructure Adapter

BrokerExecutionAdapter

DTO Mappers

- ExecutionRequestMapper
- ExecutionResponseMapper

Acceptance Criteria

- No broker SDK inside domain
- Adapter fully mocked in tests
- Broker abstraction respected

---

# Phase 4 – Recovery Pipeline

## Objective

Implement deterministic recovery.

Pipeline

RecoverableExecutionDiscoveryStep

↓

ExecutionInspectionStep

↓

RecoveryStrategyStep

↓

BrokerReconciliationStep

↓

RecoveryFinalizationStep

Deliverables

Services

- RecoverExecutionService
- RecoveryStrategyService

Acceptance Criteria

- Unknown executions recoverable
- Reconciliation required before retry
- Terminal executions ignored

---

# Phase 5 – Domain Events

## Objective

Introduce immutable business events.

Deliverables

Events

- ExecutionIntentCreated
- ExecutionAttemptCreated
- ExecutionAttemptStarted
- ExecutionAttemptSucceeded
- ExecutionAttemptFailed
- BrokerOrderLinked
- BrokerOrderFilled
- BrokerOrderRejected
- ExecutionRecoveryStarted
- ExecutionRecoveryCompleted

Acceptance Criteria

- Events emitted after every business transition
- Events immutable
- Events independently testable

---

# Phase 6 – Persistence

## Objective

Persist execution state.

Deliverables

JPA Entities

Repositories

Mappers

Optimistic Locking

Database Constraints

Acceptance Criteria

- One active execution attempt
- Idempotency uniqueness enforced
- Version conflicts handled

---

# Phase 7 – REST API

## Objective

Expose execution capabilities.

Controllers

ExecutionController

Endpoints

POST /executions

GET /executions/{id}

GET /executions

POST /executions/{id}/retry

POST /executions/{id}/cancel

POST /executions/recovery

Response Objects

ExecutionDto

ExecutionSummaryDto

Acceptance Criteria

- Controllers return ResponseEntity
- DTOs isolated from domain
- Validation handled at API layer

---

# Phase 8 – Observability

## Objective

Provide operational visibility.

Logging

- execution creation
- broker submission
- retry
- reconciliation
- completion

Metrics

Execution Metrics

- total executions
- successful executions
- failed executions
- cancelled executions
- expired executions

Pipeline Metrics

- validation duration
- broker latency
- reconciliation duration
- recovery duration

Business Metrics

- duplicate prevention count
- recovery count
- retry count
- unknown submission count

Acceptance Criteria

- Metrics exportable
- Structured logs
- Correlation identifiers included

---

# Phase 9 – Testing Strategy

Testing follows the testing pyramid.

## Unit Tests

Coverage

- aggregates
- value objects
- domain services
- lifecycle transitions

Goal

Near 100% domain coverage.

---

## Integration Tests

Coverage

Repositories

Persistence

Optimistic Locking

Broker Adapter

Recovery

---

## Pipeline Tests

Execution Pipeline

Recovery Pipeline

Verify

- correct ordering
- error propagation
- rollback behavior

---

## Contract Tests

BrokerExecutionPort

ExecutionRequest

ExecutionResponse

Guarantee adapter compatibility.

---

## End-to-End Tests

Complete execution flow

TradePlan

↓

Risk Approval

↓

Execution

↓

Broker

↓

Events

↓

Persistence

---

# Error Handling

Errors are classified into categories.

Business Errors

- invalid execution state
- duplicate execution
- expired execution

Infrastructure Errors

- broker unavailable
- timeout
- persistence failure

Recovery Errors

- reconciliation failure
- inconsistent broker state

Each category has dedicated exception types.

---

# Security

Execution must enforce:

- Risk approval verification
- JWT-authenticated user context
- Immutable idempotency
- Optimistic locking
- Complete audit trail

No infrastructure component may bypass domain validation.

---

# Performance

Execution should minimize latency while preserving correctness.

Performance optimizations must never compromise:

- idempotency
- auditability
- determinism
- consistency

Correctness has priority over speed.

---

# Future Extensions

The architecture allows future implementation of:

- multi-broker execution
- smart order routing
- execution batching
- iceberg orders
- TWAP/VWAP algorithms
- execution quality scoring
- execution analytics
- AI-assisted execution monitoring
- distributed recovery workers

These features should reuse the existing execution model rather than replacing it.

---

# Implementation Order

Recommended development sequence.

1. Domain Aggregates
2. Value Objects
3. Repository Ports
4. Domain Services
5. Execution Pipeline
6. Broker Adapter
7. Recovery Pipeline
8. Domain Events
9. Persistence
10. REST API
11. Metrics
12. Integration Tests
13. End-to-End Tests

Each phase should be completed before starting the next.

---

# Definition of Done

The Execution Domain implementation is considered complete when:

✓ All domain invariants are enforced

✓ Execution Pipeline is fully operational

✓ Recovery Pipeline is implemented

✓ Broker integration is abstracted

✓ Domain events are emitted

✓ Persistence is complete

✓ REST API is available

✓ Metrics and logging are operational

✓ Integration tests pass

✓ End-to-end execution flow is validated

✓ Architecture remains compliant with ADR-029
