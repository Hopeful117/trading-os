# Corrective Code Review

## Verdict

Approved.

The corrective implementation resolves CR-001 through CR-005 from the original Code Review. No new blocking or major finding was identified in the corrected paths.

## Resolution Verification

### CR-001 — Distributed acknowledgment consistency

Resolved.

- Trading Core persists an acknowledgment outbox row in the same transaction as an approved official evaluation.
- Remote delivery starts only after the evaluation transaction completes.
- Claim, success, and failure transitions use independent transactions while the HTTP call runs without a database transaction.
- Failed and expired claims are retried durably.
- An exact evaluation retry reuses the official result and attempts pending delivery again.
- Market Intelligence accepts identical at-least-once delivery idempotently.

Reviewed files include:

- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/RiskAcknowledgmentDeliveryService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/persistence/RiskPersistence.java`
- `trading-core/src/main/resources/db/migration/common/V4__risk_acknowledgment_outbox.sql`

### CR-002 — Broker account authorization

Resolved.

- The endpoint derives the owner identity from the authenticated `BrokerPrincipal`.
- Broker Service verifies `(brokerAccountId, ownerId)` through the authoritative connection repository before resolving the provider or credentials.
- Cross-account access fails before a provider operation can occur.

Reviewed files include:

- `broker-service/src/main/java/com/hope/trading/broker_service/broker/api/controller/BrokerQueryControllers.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/application/service/BrokerOperationServices.java`
- `broker-service/src/test/java/com/hope/trading/broker_service/broker/BrokerApiSecurityIntegrationTest.java`

### CR-003 — Coherent Broker capture

Resolved.

- Kraken capture uses balance-before and balance-after reads with bounded retries.
- The observation boundary is assigned after acquisition.
- Facts after that boundary fail closed.
- Ledger entries are ordered and reconciled through their running balances, fee semantics, and terminal current balance.
- Trading Core independently validates the ledger chain and reconstructs opening balance from the first authoritative running balance.

Reviewed files include:

- `broker-service/src/main/java/com/hope/trading/broker_service/broker/infrastructure/provider/kraken/capability/KrakenRiskSnapshotCapability.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationService.java`
- corresponding Broker and Trading Core focused tests.

### CR-004 — Reporting currency

Resolved.

- Trade Plan account and sizing currencies are normalized using locale-independent rules.
- Both must equal the configured Trading Core reporting currency before Broker or Market Data facts are requested.
- Mismatches produce structured fail-closed outcomes.

### CR-005 — Durable Market Intelligence acknowledgment

Resolved.

- Trade Plan versions and acknowledgment links are persisted through JPA/Flyway.
- The latest plan version is pessimistically locked.
- The `ACCEPTED -> RISK_VALIDATED` append and acknowledgment insert share one Market Intelligence transaction.
- Database uniqueness protects accepted plan identity/version and evaluation identity.
- Identical retries survive restart; conflicts and concurrent losers roll back.

Reviewed files include:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/tradeplan/TradePlanRiskHandoffService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaTradePlanRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaRiskValidationAcknowledgmentRepository.java`
- `market-intelligence/src/main/resources/db/migration/V1__durable_trade_plan_risk_acknowledgments.sql`

## Validation

- Risk Domain: 21 tests passed.
- Broker Service: 61 tests passed.
- Market Data: 30 tests passed.
- Trading Core: 66 tests passed.
- Market Intelligence: 117 tests passed.
- Total: 295 tests, zero failures or errors.
- `git diff --check`: passed.
- `docker compose config --quiet`: passed.

Focused corrective coverage includes transaction rollback, failed delivery and retry, duplicate delivery, ownership isolation, unstable balance acquisition, ledger mismatch and fee semantics, currency mismatch, restart durability, and concurrent acknowledgment conflicts.

## Non-blocking Deployment Validation

- Rehearse the new migrations against disposable PostgreSQL 16 databases and schema copies before deployment.
- Validate coherent capture and ledger semantics with a Kraken sandbox account.
- `TradingContextRepository` remains process-local; making Trading Context durable requires separate approved scope.

## Recommendation

The corrective implementation is approved for the next Engineering Story gate. No commit, push, merge, or Engineering Report was produced during this review.
