# Story 0148 - Implementation Report

## Status

`IMPLEMENTED_LOCALLY - REAL_PROVIDER_VALIDATION_REQUIRED`

The local implementation is complete for the approved READ-ONLY boundary. Real
cTrader validation remains a separate human-controlled gate. No cTrader order,
cancel, amend, close, SL/TP or execution capability was added.

## Implemented

- Extended the provider-neutral Broker v2 snapshot with account facts, orders,
  bounded deals, completeness, freshness, observation timestamps and explicit
  unavailable reasons.
- Added `AuthoritativeSnapshotCapability` and routed cTrader v2 snapshots through
  the dedicated capability while preserving the existing Kraken fallback path.
- Added the cTrader Broker provider registration with only the authoritative
  READ-ONLY snapshot capability.
- Extended the cTrader WebSocket client with application/account authentication,
  trader, asset, symbol, reconcile, unrealized-P&L and bounded deal reads.
- Added provider-side money precision and volume normalization using cTrader
  `moneyDigits` and volume-in-cents semantics.
- Added owner-bound credential lookup, token expiry checks, connection usability
  checks and exact external-account identity validation.
- Added fail-closed handling for stale, incomplete, unmapped, malformed and
  unavailable observations.
- Extended Trading Core's v2 DTO and rejected observations that are not both
  `COMPLETE` and `FRESH` before replacing local account state.
- Preserved provider-neutral Trading Core contracts; no cTrader DTO or token was
  added to Trading Core.
- Added focused capability and synchronization tests for complete observations,
  external-account mismatch, incomplete/stale observations and provider-neutral
  mapping, including deterministic JSON/WebSocket payload mapping.

## Changed Files

### Broker Service

- `broker-service/src/main/java/com/hope/trading/broker_service/broker/application/service/BrokerOperationServices.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/domain/capability/BrokerCapabilities.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/domain/model/BrokerModels.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/CTraderBrokerProvider.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/authentication/CTraderOpenApiClient.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/authentication/JsonWebSocketCTraderOpenApiClient.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/connection/domain/BrokerConnection.java`
- `broker-service/src/main/resources/db/migration/V8__ctrader_environment_binding.sql`
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/synchronization/CTraderAuthoritativeSnapshotCapability.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/synchronization/CTraderReadOnlyObservation.java`
- `broker-service/src/test/java/com/hope/trading/broker_service/ctrader/synchronization/CTraderAuthoritativeSnapshotCapabilityTest.java`
- `broker-service/src/test/java/com/hope/trading/broker_service/ctrader/authentication/JsonWebSocketCTraderOpenApiClientTest.java`

### Trading Core

- `trading-core/src/main/java/com/hope/trading/trading_core/broker/dto/BrokerAccountSnapshotV2Dto.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/broker/dto/BrokerAccountFactsV2Dto.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/broker/dto/BrokerOrderSnapshotV2Dto.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/broker/dto/BrokerDealSnapshotV2Dto.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/broker/service/BrokerSynchronizationServiceImpl.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/broker/service/BrokerSynchronizationServiceImplTest.java`

## Validation

Passed:

```text
mvn -q -DskipTests compile  [broker-service]
mvn -q -DskipTests compile  [trading-core]
mvn -q -Dtest=JsonWebSocketCTraderOpenApiClientTest,CTraderAuthoritativeSnapshotCapabilityTest,CTraderAuthenticationServiceTest test  [broker-service]
mvn -q -Dtest=BrokerSynchronizationServiceImplTest test  [trading-core]
git diff --check
```

Not completed:

- Full `mvn -q test` for Broker Service exceeded the 120-second execution limit.
- Full `mvn -q test` for Trading Core exceeded the 120-second execution limit.
- No real cTrader account or credential was used.
- No cTrader protocol interaction was validated against demo or live infrastructure.

The full-suite timeouts do not establish a test failure, but they also cannot be
reported as successful completion.

## Known Limitations

- The authoritative observation is read from cTrader on demand; no new durable
  observation-history table was introduced in this Story.
- Trading Core consumes the validated provider-neutral account and position
  facts needed to replace local account state. Orders, bounded deals and
  account margin facts remain available in the neutral snapshot contract but
  are not persisted into a new Trading Core order/deal projection by this
  Story; introducing that projection would require a separate domain decision.
- Current freshness is enforced at the capability boundary with a 30-second
  observation age threshold.
- Equity is composed from provider balance plus the provider's net unrealized P&L
  response; missing P&L makes the observation incomplete.
- Free margin is preserved as unavailable when cTrader does not expose it in the
  read response; this keeps the observation fail-closed.
- Symbol and asset mapping depends on cTrader list responses; missing mappings
  make the observation incomplete rather than guessing a Trading OS instrument.
- cTrader JSON/protobuf field compatibility remains unproven until controlled
  real-provider validation.

## Security Boundary

- Credentials and cTrader transport types remain inside Broker Service.
- Trading Core receives only neutral DTOs.
- cTrader provider registration exposes only `AuthoritativeSnapshotCapability`.
- No execution capability is registered for cTrader.
- Pre-existing untracked architecture reports were not modified or staged.

## Required Next Gate

Produce the Code Review Report, then the Engineering Report. Real cTrader
READ-ONLY validation requires explicit human credentials, a configured cTrader
application with view-only scope, an already bound account and evidence that no
mutation path is available.
