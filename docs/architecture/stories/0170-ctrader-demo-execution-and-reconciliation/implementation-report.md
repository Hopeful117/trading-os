# Story 0170 - Implementation Report

## Status

`IMPLEMENTED_LOCALLY - AWAITING REVIEW`

The scoped execution-correctness corrections are implemented locally. No cTrader
order, mutation, runtime account change, or demo execution proof was performed.

## Implemented

- Propagated provider-neutral take-profit prices from Trading Core to Broker
  Service and the cTrader order payload.
- Preserved cTrader protective-stop requirements while allowing protected MARKET
  requests to reach the provider order mapping.
- Resolved full cTrader symbol metadata before submission and validated volume
  against provider min/max/step constraints using cTrader's cent-based volume
  representation.
- Prevented a second entry intent for the same trade-plan version before the
  broker submission path, retaining the existing database uniqueness constraint.
- Corrected the cTrader New Order response correlation to use the official
  `ProtoOAExecutionEvent` payload type `2126`.
- Added focused regression coverage for protective target propagation and
  protected MARKET execution mapping.

## Changed Files

- `broker-service/src/main/java/com/hope/trading/broker_service/broker/api/dto/BrokerApiDtos.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/domain/model/BrokerModels.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/authentication/CTraderOpenApiClient.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/authentication/JsonWebSocketCTraderOpenApiClient.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/execution/CTraderExecutionCapability.java`
- `broker-service/src/test/java/com/hope/trading/broker_service/ctrader/execution/CTraderExecutionCapabilityTest.java`
- `ctrader-openapi-technical/src/main/java/com/hope/trading/ctrader/openapi/protocol/CTraderPayloadTypes.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/CreateExecutionIntentService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/repository/ExecutionIntentRepositoryPort.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/adapter/BrokerExecutionClient.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/adapter/JpaExecutionIntentAdapter.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/mapper/ExecutionRequestMapper.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/persistence/JpaExecutionIntentRepository.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/execution/BrokerExecutionContractTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/execution/infrastructure/mapper/ExecutionRequestMapperRegressionTest.java`

## Validation

Passed:

- `./mvnw -q -Dtest=CTraderExecutionCapabilityTest,JsonWebSocketCTraderOpenApiClientTest,BrokerModelsTest test`
- `./mvnw -q -Dtest=ExecutionRequestMapperRegressionTest,BrokerExecutionContractTest,IdempotencyServiceRegressionTest test`
- `./mvnw -q test` in `broker-service`
- `./mvnw -q test` in `trading-core`
- `mvn -q install -DskipTests` in `ctrader-openapi-technical`
- `git diff --check`

The full Trading Core suite emits existing integration-test migration and
database warnings, but its generated reports contain no non-zero failures or
errors. Safety execution remains disabled and no runtime provider write was
attempted.

## Documentation Reconciliation

No ADR change was required. The Story implementation report records the changed
provider-neutral contract and the cTrader protocol correction. Runtime demo
validation remains a separate human-authorized action.

## Vault Outcome

The Obsidian vault was not consulted. No vault action is proposed.
