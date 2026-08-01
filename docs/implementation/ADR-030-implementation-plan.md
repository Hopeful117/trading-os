# IMPLEMENTATION PLAN

## ADR-030 – Broker Service Architecture

**Related ADR:** ADR-030 – Broker Service Architecture

**Status:** Planned

---

# Objective

This document defines the implementation strategy for the Broker Service.

Unlike ADR-030, which defines the architecture, this document specifies:

- implementation order
- package organization
- provider architecture
- application services
- REST API
- testing strategy

The implementation follows an incremental approach to validate the provider architecture before integrating multiple brokers.

---

# High-Level Architecture

```text
                Trading Core
                      │
                      ▼
             +------------------+
             |  Broker Service  |
             +------------------+
                      │
         ┌────────────┴────────────┐
         ▼                         ▼
  Provider Registry         REST API
         │
         ▼
    Broker Provider
         │
         ▼
 Infrastructure
         │
         ▼
 External Broker
```

The Broker Service remains the single integration point between Trading Core and external brokers.

---

# Package Structure

```text
broker/

    application/

        command/
        query/
        service/
        registry/

    domain/

        provider/
        capability/
        model/
        valueobject/
        exception/

    infrastructure/

        provider/
        client/
        mapper/
        configuration/
        security/
        monitoring/

    api/

        controller/
        dto/
```

Each package owns a single responsibility.

---

# Core Interfaces

The Broker Service exposes the following core abstractions.

```text
BrokerProvider

BrokerProviderRegistry

AuthenticationCapability

AccountCapability

PositionCapability

OrderCapability

ExecutionCapability

ReconciliationCapability
```

Every broker implementation must conform to these interfaces.

---

# Provider Structure

Each provider follows the same internal organization.

```text
provider/

    kraken/

        authentication/

        client/

        dto/

        mapper/

        capability/

        configuration/
```

No provider implementation may leak outside its package.

---

# Broker Models

The Broker Service exposes broker-neutral models.

Initial models include:

- AccountSnapshot
- PositionSnapshot
- OrderSnapshot
- ExecutionRequest
- ExecutionResult

Provider DTOs remain internal.

---

# Provider Registry

The registry is responsible for:

- provider registration
- provider discovery
- provider resolution
- capability lookup

Provider selection remains dynamic.

---

# Authentication

Authentication is implemented per provider.

Responsibilities:

- credential loading
- request signing
- nonce generation
- timestamp generation

Authentication never reaches the Trading Core.

---

# REST Client

Provider communication is isolated behind dedicated clients.

Responsibilities:

- endpoint resolution
- HTTP requests
- serialization
- deserialization
- timeout handling
- error translation

No business logic belongs in REST clients.

---

# Mapping Layer

Provider data is translated through dedicated mappers.

```text
Provider DTO

↓

Mapper

↓

Broker Model

↓

Trading Core DTO
```

Provider payloads never reach application services.

---

# REST API

The Broker Service exposes REST endpoints for internal services.

Controllers:

- AccountController
- PositionController
- OrderController
- ExecutionController

Controllers always return `ResponseEntity`.

---

# Application Services

Application services orchestrate provider interactions.

Initial services:

```text
GetAccountService

GetPositionsService

GetOrdersService

ExecuteOrderService

CancelOrderService

ReconcileExecutionService
```

Services contain orchestration only.

---

# Phase 1 – Foundations

## Objective

Create the Broker Service skeleton.

### Deliverables

- Spring Boot application
- package structure
- core interfaces
- provider contracts
- configuration

### Acceptance Criteria

- application starts
- dependency injection works
- contracts compile

---

# Phase 2 – Provider Framework

## Objective

Implement reusable provider infrastructure.

### Deliverables

- BrokerProvider
- BrokerProviderRegistry
- capability interfaces
- provider discovery
- provider configuration

### Acceptance Criteria

- providers register successfully
- registry resolves providers
- empty provider can be loaded

---

# Phase 3 – Kraken Provider

## Objective

Implement the first production provider.

### Deliverables

- authentication
- REST client
- DTOs
- mappers
- capabilities

### Acceptance Criteria

- account retrieval works
- order retrieval works
- execution works
- reconciliation works

---

# Phase 4 – Trading Core Integration

## Objective

Connect Trading Core to Broker Service.

### Deliverables

- REST endpoints
- OpenFeign integration
- DTO mapping

### Acceptance Criteria

- Trading Core retrieves broker data
- provider abstraction preserved
- no broker SDK reaches Trading Core

---

# Phase 5 – REST API

## Objective

Expose Broker Service capabilities.

### Deliverables

Controllers

- AccountController
- PositionController
- OrderController
- ExecutionController

DTOs

Validation

### Acceptance Criteria

- controllers return ResponseEntity
- DTO validation succeeds
- API documented

---

# Phase 6 – Observability

## Objective

Provide operational visibility.

### Deliverables

Logging

- provider requests
- execution
- reconciliation

Metrics

- provider latency
- request count
- failures
- authentication failures

Health

- provider availability
- readiness
- liveness

### Acceptance Criteria

- structured logs
- metrics exported
- health endpoints operational

---

# Phase 7 – Testing Strategy

Testing follows the testing pyramid.

## Unit Tests

Coverage

- providers
- mappers
- capabilities
- registry

Goal

Near complete coverage of deterministic components.

---

## Integration Tests

Coverage

- REST API
- provider registration
- Spring configuration
- authentication

---

## Contract Tests

Verify every provider implements the BrokerProvider contract.

---

## End-to-End Tests

Complete flow

```text
Trading Core

↓

Broker Service

↓

Provider

↓

Broker

↓

Response
```

---

# Security

The Broker Service enforces:

- JWT authentication
- provider credential isolation
- secret externalization
- audit logging

No provider secret is exposed outside infrastructure.

---

# Performance

Performance should optimize:

- connection reuse
- HTTP pooling
- timeout configuration
- serialization efficiency

Correctness always has priority over latency.

---

# Future Extensions

The architecture allows future support for:

- Paper Provider
- additional exchanges
- Forex brokers
- stock brokers
- WebSocket
- FIX
- gRPC

New providers reuse the existing provider architecture.

---

# Implementation Order

Recommended development sequence.

1. Core Interfaces
2. Package Structure
3. Provider Registry
4. Capability Interfaces
5. Authentication
6. REST Client
7. Mapping Layer
8. Kraken Provider
9. REST API
10. Trading Core Integration
11. Observability
12. Testing

Each phase should be completed before starting the next.

---

# Definition of Done

The Broker Service implementation is considered complete when:

✓ Provider architecture implemented

✓ Registry operational

✓ Kraken provider functional

✓ REST API available

✓ Trading Core integrated

✓ Metrics operational

✓ Health checks available

✓ Integration tests pass

✓ Contract tests pass

✓ Architecture remains compliant with ADR-030