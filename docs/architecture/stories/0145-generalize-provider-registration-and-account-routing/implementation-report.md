# Implementation Report - Story 0145

## Result

Implemented the approved provider-neutral account-routing path without changing
the existing `v1` broker account snapshot contract or adding a production
provider identity.

## Implemented Behavior

- Added an internal `v2` broker account snapshot contract keyed by the local
  `BrokerAccount` UUID.
- Kept the provider external account identifier separate as
  `externalAccountId`.
- Added ownership validation in Broker Service before account capabilities are
  invoked.
- Added explicit `v2` synchronization in Trading Core; it resolves the
  financial `Account` through `Account.brokerAccountId` only.
- Rejected missing mappings, cross-owner mappings, snapshot identity mismatch,
  provider mismatch, and inconsistent legacy provider metadata.
- Preserved the existing `v1` synchronization path for backward compatibility.
- Preserved provider registry behavior and added immutable supported-provider
  coverage.
- No cTrader, FTMO, migration, credential, or risk-rule behavior was added.

## Changed Files

### Broker Service

- `broker-service/src/main/java/com/hope/trading/broker_service/broker/domain/model/BrokerModels.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/application/service/BrokerOperationServices.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/api/controller/BrokerQueryControllers.java`
- `broker-service/src/test/java/com/hope/trading/broker_service/broker/BrokerProviderRegistryTest.java`
- `broker-service/src/test/java/com/hope/trading/broker_service/broker/BrokerApiSecurityIntegrationTest.java`

### Trading Core

- `trading-core/src/main/java/com/hope/trading/trading_core/broker/dto/BrokerAccountSnapshotV2Dto.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/broker/dto/BrokerPositionSnapshotV2Dto.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/broker/apiClient/BrokerApiClient.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/broker/apiClient/BrokerFeignClient.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/broker/service/BrokerSynchronizationService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/broker/service/BrokerSynchronizationServiceImpl.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/controller/AccountSynchronizationV2Controller.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/broker/service/BrokerSynchronizationServiceImplTest.java`

## Documentation Reconciliation

Documentation update: Not required. The implemented identity and ownership
semantics are already defined by ADR-043, the provider boundary by ADR-071, and
contract versioning by ADR-030. No separate canonical API document for the
legacy account snapshot endpoint was found.

## Validation

Passed:

```text
broker-service: mvn test -Dtest=BrokerApiSecurityIntegrationTest,BrokerProviderRegistryTest,BrokerOwnershipTest,BrokerMonitoringTest,BrokerServiceImplTest
trading-core:  mvn test -Dtest=BrokerSynchronizationServiceImplTest,BrokerAccountServiceTest,BrokerProviderRegressionTest,BrokerAccountTest
git diff --check
```

Results:

- Broker Service: 29 tests, 0 failures.
- Trading Core: 15 tests, 0 failures.
- Security coverage confirms the new v2 snapshot endpoint requires a delegated
  actor.

No live broker calls or real credentials were used.

## Vault Outcome

- Vault consulted: no.
- Outcome: no vault action.
- Rationale: the Story concerns repository-owned service contracts and account
  identity invariants already governed by repository ADRs. No curated vault
  context was required.

## Remaining Work

- Coordinate deployment so Trading Core and Broker Service support the v2
  contract together.
- Deprecate and remove the legacy v1 synchronization path in a later approved
  Story.

## Git Boundary

The implementation was committed and delivered through the dedicated Story
branch and pull request after final validation.
