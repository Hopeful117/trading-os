# Implementation Plan - Story 0051

## Plan Status

`PROPOSED - HUMAN APPROVAL REQUIRED`

## Phase 1 - Define contracts

Create immutable broker-neutral contracts for:

```text
BrokerCapabilities
LeverageCapability
MarginPreviewRequest
MarginPreviewResponse
CapabilitySource
```

Every response must include source identity, version, and observation time.

Do not expose Kraken DTOs or exceptions.

## Phase 2 - Implement provider capabilities

Add provider capability interfaces inside Broker Service. The Kraken adapter
maps provider facts into the neutral contracts. PAPER uses the same provider
capability model without requiring live credentials for local simulation.

Broker Service reports facts only. Trading Core and Risk Domain decide whether
those facts authorize a plan.

## Phase 3 - Implement margin preview

Add a synchronous internal margin-preview operation. It must:

1. validate the service caller;
2. resolve provider and instrument identity;
3. obtain current technical provider facts;
4. calculate or retrieve provider margin without exposing provider rules;
5. return a versioned neutral fact;
6. fail closed when the result is unavailable or stale.

The operation must not mutate orders, positions, or accounts.

## Phase 4 - Integrate Trading Core

Add a Trading Core client and context assembler that consumes the neutral
capability and margin facts. Trading Core remains responsible for account
ownership, context coherence, and the decision to invoke the Risk Domain.

Market Data remains the source for market metadata and prices used outside the
broker technical fact boundary.

## Phase 5 - Tests

Add:

* contract serialization tests;
* provider mapping tests;
* stale/unavailable capability tests;
* margin preview tests;
* service JWT tests;
* Trading Core integration tests;
* regression tests for LIVE and PAPER separation.

## Expected Files

The exact paths must follow current Broker Service conventions. Candidate areas:

```text
broker-service/src/main/java/.../broker/
broker-service/src/test/java/.../broker/
trading-core/src/main/java/.../risk/infrastructure/client/
trading-core/src/test/java/.../risk/
```

## Validation Commands

```text
cd broker-service && mvn test
cd trading-core && mvn test
cd risk-domain && mvn test
git diff --check
```

## Exit Criteria

* contracts are broker-neutral;
* capabilities and margin are versioned and sourced;
* stale data fails closed;
* Market Data ownership is preserved;
* no risk decision is made by Broker Service;
* tests pass;
* human approval is obtained before implementation.
