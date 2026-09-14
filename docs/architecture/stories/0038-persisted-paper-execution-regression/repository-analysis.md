# Repository Analysis - Story 0038

## Current State

The PAPER execution path was already implemented by the merged PAPER account
milestone and wired to the web application by Story 0037. Existing execution
tests covered the simulator and settlement with isolated fixtures, but no test
exercised the complete path through a Spring application context, Flyway, H2,
and the real Trading Core persistence adapters.

## Gap

The missing evidence was persistence durability across the execution graph:

- persisted user, financial account, and PAPER broker account;
- persisted execution intent, attempt, broker order, and fill;
- persisted balances, equity, and trade projection after settlement;
- reload of the final state from repositories after clearing the persistence
  context.

Without this coverage, isolated tests could pass while JPA relationships,
transaction boundaries, or Flyway schema wiring were broken.

## Constraints

- Preserve existing PAPER pricing, risk, lifecycle, and settlement semantics.
- Use the existing Spring Boot, JPA, Flyway, and H2 test infrastructure.
- Mock only external market-data and live-broker boundaries.
- Do not change Broker Service or frontend behavior.

## Readiness

The gap is suitable for a focused regression Story. No new domain aggregate or
execution behavior is required.
