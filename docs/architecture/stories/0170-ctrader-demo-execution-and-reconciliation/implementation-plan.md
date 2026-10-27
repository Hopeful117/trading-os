# Implementation Plan - Story 0170

## Overview

Implement cTrader demo execution by extending the existing provider-neutral
execution path and adding the smallest cTrader adapter required for order
submission and authoritative reconciliation.

The implementation will preserve the following responsibility split:

```text
Trading Core
    -> human authorization, Challenge/Risk admission, execution lifecycle
Broker Service
    -> cTrader OAuth, permissions, transport, mapping, provider errors,
       order submission, provider reconciliation
cTrader Open API
    -> provider-authoritative account/order/deal/position facts
```

No runtime demo order will be submitted as part of implementation, automated
tests, image rebuilds, or service startup.

## Phase 1 - OAuth Permission And Account Environment

1. Replace the read-only-only OAuth request with the minimum documented scope
   required for trading while retaining account access.
2. Persist the requested/effective permission scope on the authorization
   attempt.
3. Extend cTrader account discovery and selected-account authorization to retain
   provider environment facts, including the raw `isLive` distinction and the
   verified effective execution environment.
4. Extend `BrokerConnection` only with provider-neutral connection facts needed
   to prove execution eligibility; keep cTrader payloads inside the adapter.
5. Mark the connection `CONNECTED` only after complete read-only observation,
   matching account identity, provider-confirmed demo environment, and required
   read permissions.
6. Do not grant `TRADE` merely because OAuth succeeded. Persist it only after
   the provider confirms effective trading permission for the selected account.
7. Ensure unknown or contradictory effective environment facts fail closed.
   Treat the documented FTMO FREE case (`isLive=true` with simulated/demo
   economics) as a provider-semantic translation inside Broker Service, never
   as a generic live-account bypass.

Likely areas:

- `broker-service/.../ctrader/authentication`
- `broker-service/.../connection/domain/BrokerConnection`
- connection migration and provider DTO mapping tests

## Phase 2 - Broker-Neutral Execution Contract

1. Extend the Broker Service execution request contract to carry the existing
   domain `stopLossPrice` without adding cTrader-specific fields.
2. Preserve the existing optional limit price and order type semantics.
3. Keep the domain `ExecutionParameters` as the source of the protective stop;
   do not duplicate Risk calculations in Broker Service.
4. Validate that cTrader demo execution requires a protective stop, while
   preserving existing Kraken and non-Challenge behavior.
5. Preserve stable intent/attempt/idempotency identifiers across Trading Core,
   Feign DTOs, Broker Service, and cTrader protocol requests.

Likely areas:

- `trading-core/.../execution/infrastructure/adapter/BrokerExecutionClient`
- `trading-core/.../execution/infrastructure/mapper/ExecutionRequestMapper`
- `broker-service/.../broker/api/dto/BrokerApiDtos`
- `broker-service/.../broker/domain/model/BrokerModels`
- execution contract regression tests

## Phase 3 - cTrader Protocol Operations

1. Extend `CTraderOpenApiClient` with provider-neutral adapter methods for:
   - symbol/instrument facts required for execution;
   - new order submission;
   - order/deal/position reconciliation;
   - cancellation only where the provider contract permits it.
2. Implement the protocol operations in the existing WebSocket session:
   - application authentication;
   - account authentication;
   - symbol resolution;
   - volume conversion and precision validation;
   - `ProtoOANewOrderReq` mapping;
   - protective stop mapping;
   - client order reference mapping;
   - provider response validation by account identity and request correlation.
3. Map provider errors into the existing broker exception taxonomy:
   - explicit invalid/auth/permission/order rejection -> `Rejected`;
   - timeout, transport interruption, malformed or incomplete response ->
     `Unknown`;
   - unexpected provider response -> `Unknown`, never `Rejected`.
4. Ensure an acknowledgement is not treated as a fill. Fill and position facts
   must come from authoritative order/deal/position observation.

Likely areas:

- `broker-service/.../ctrader/authentication/JsonWebSocketCTraderOpenApiClient`
- cTrader provider protocol constants and payload tests
- cTrader adapter exception mapping tests

## Phase 4 - cTrader Capability Composition

1. Add a cTrader `ExecutionCapability` implementation inside Broker Service.
2. Add a cTrader `ReconciliationCapability` implementation using the stable
   client reference and authoritative provider facts.
3. Expose both capabilities from `CTraderBrokerProvider`.
4. Require connection ownership, `CONNECTED` status, effective `TRADE`
   permission, an eligible effective execution environment, and non-expired
   token before submitting an order.
5. Reject any request whose instrument, volume, stop, or account identity does
   not match current provider facts.
6. Preserve the existing Kraken capability composition and behavior unchanged.

Likely areas:

- `broker-service/.../ctrader/capability`
- `broker-service/.../ctrader/CTraderBrokerProvider`
- `broker-service/.../broker/application/service/BrokerOperationServices`
- provider capability tests

## Phase 5 - Trading Core Admission And Lifecycle

1. Preserve the current explicit execution intent and human-triggered
   `POST /api/v1/executions/{id}/execute` boundary.
2. Add a provider-neutral execution admission contract for facts Broker Service
   can prove, without importing cTrader types into Trading Core.
3. Require a fresh, complete authoritative snapshot before cTrader Challenge
   entry execution.
4. Require active Challenge readiness, provider compatibility, canonical
   instrument compatibility, and T1 Risk approval before submission.
5. Require a valid protective stop for the bounded demo entry proof.
6. Keep execution state transitions deterministic:
   - acknowledgement remains pending until provider facts confirm outcome;
   - explicit rejection finalizes as rejected;
   - unknown result remains unknown and requires reconciliation;
   - no automatic duplicate submission is permitted.
7. Ensure reconciliation results flow through the existing execution lifecycle
   and trade outcome persistence without provider-specific branching.

Likely areas:

- `trading-core/.../execution/application`
- `trading-core/.../execution/infrastructure/adapter`
- `trading-core/.../challenge/application`
- execution lifecycle and admission tests

## Phase 6 - Database And Compatibility Migration

1. Add only the schema fields required for provider-neutral permission and
   environment facts, with safe defaults for existing connections.
2. Preserve existing connected Kraken rows and existing cTrader read-only rows.
3. Do not mark existing cTrader tokens trade-capable through migration.
4. Require reauthorization for the selected cTrader account before `TRADE` can
   be persisted.
5. Add fresh-install and upgrade migration tests.

## Phase 7 - Validation

### Unit And Contract Tests

- OAuth scope and authorization-attempt permission persistence.
- Provider-confirmed demo/live account environment handling.
- Complete read-only observation prerequisite.
- cTrader account identity mismatch and expired-token failures.
- cTrader symbol identity and volume-unit conversion.
- Protective stop mapping and invalid stop rejection.
- Explicit rejection, timeout, transport failure, malformed response, and
  unknown outcome mapping.
- Stable idempotency/client-order reference generation.
- Authoritative order/deal/position reconciliation.
- cTrader provider capability composition.
- Trading Core Challenge/Risk/freshness/human-authorization admission.
- Existing Kraken execution and reconciliation regressions.

### Module Validation

- `broker-service` targeted cTrader and execution tests.
- `trading-core` execution, Challenge, Risk, migration, and API tests.
- Affected Maven module builds.
- `git diff --check`.
- Static/architecture checks applicable to provider-boundary changes.

### Runtime Validation

Runtime validation is separate from implementation and requires explicit human
authorization at the time of execution:

1. Reauthorize the exact cTrader account with the approved trading scope.
2. Confirm provider account ID and the verified effective execution environment.
   For FTMO FREE, record the provider-reported `isLive` value and the separate
   verified simulated/demo classification.
3. Confirm local connection is `CONNECTED` and effective `TRADE` permission is
   present.
4. Confirm active FTMO Challenge readiness is `READY`.
5. Confirm fresh complete snapshot, instrument constraints, quantity, and stop.
6. Submit exactly one minimal demo order.
7. Confirm provider acknowledgement and authoritative fill/order/position facts.
8. Reconcile and close/settle the position according to the approved runtime
   procedure.
9. Record the proof without secrets and verify no live account or second order
   was touched.

## Documentation Reconciliation

Update only the canonical Story and relevant implementation/runtime documentation
if the final implementation changes OAuth configuration, provider capability
composition, operational validation, or supported account behavior.

Do not modify ADR-071 in this Story. If implementation discovers that the
accepted ADR must change, stop and request a separate ADR decision.

## Risks And Mitigations

| Risk | Mitigation |
|---|---|
| OAuth token has account access but no trade permission | Reauthorize and verify effective provider permission before persisting `TRADE` |
| Provider `isLive` flag is mistaken for economic environment | Resolve the
  effective environment from verified provider/business facts; document and
  test the FTMO FREE semantic exception; fail closed on unknown or contradictory
  classification |
| Provider acknowledgement is mistaken for fill | Reconcile authoritative order/deal/position facts |
| Timeout causes duplicate order | Return `Unknown`; reconcile before any retry |
| Stop or volume is invalid for symbol | Resolve current provider symbol constraints and validate before submit |
| cTrader DTO leaks into Trading Core | Keep all protocol types and mapping inside Broker Service |
| Existing cTrader token is reused unsafely | Require reauthorization for trading permission |
| Runtime proof changes Challenge state unexpectedly | Require explicit human authorization and bounded one-order procedure |

## Deliverables

- Updated Story implementation artifacts.
- Provider-neutral execution contract changes where required.
- cTrader OAuth permission/environment validation.
- cTrader execution and reconciliation capability.
- Focused and regression tests.
- Runtime validation procedure and evidence artifact, produced only after
  implementation and explicit human authorization.

## Implementation Preconditions

Implementation is not authorized until this plan is explicitly approved. The
runtime demo order is a separate human-controlled validation action and is not
authorized by approval of this plan alone.
