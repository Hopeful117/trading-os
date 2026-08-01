# Implementation Report

## Overview

Implementation was stopped before source changes because the approved stop condition was reached. Trading Core cannot assemble a coherent immutable ADR-028 risk context from the current account, broker, market, and rule contracts without inventing mandatory values or semantics.

The blocking gaps are:

- `AccountSnapshot` requires a versioned daily-start balance and daily closed PnL. Trading Core stores neither a daily-start balance nor a reliable daily closed PnL; the existing daily PnL query selects trades by `openedAt`, not trades closed during the day.
- The current Broker Service client exposes `getAccount()` without an account identifier, so Trading Core cannot prove that broker balances and positions belong to the requested Trading Core account.
- ADR-028 requires each open position to contain a UUID identity and monetary `lossAtStop`. Broker positions use provider string identifiers, and the Kraken mapper does not populate stop loss, so the required position loss cannot be calculated safely.
- ADR-028 traceability requires account, portfolio, market, and rule-set snapshot versions. The current financial facts and Trading Core rules do not expose those versions, and no portfolio identity exists.
- Trading Core rules do not expose policy/rule versions or a configured maximum-exposure rule. Mapping unrelated fields or assigning synthetic versions would invent risk configuration.
- Broker position exposure is not contractually guaranteed to use the account currency, and no currency-conversion snapshot is available for accounts whose currency differs from an instrument's quote currency.

Per the approved plan and delegation instructions, fail-open defaults, generated placeholder identities, synthetic versions, zero loss for positions without stops, and inferred daily balances were not introduced. The flow was not partially implemented because doing so could not produce a valid normal evaluation and would leave the approved cross-service contract without a usable coherent context source.

## Modified Files

None.

## New Files

- `docs/stories/0001-connect-trade-plan-to-risk/implementation-report.md`

## Tests

- `risk-domain`: `mvn test` passed. Tests run: 21; failures: 0; errors: 0; skipped: 0.
- No focused Market Intelligence or Trading Core tests were added or run because implementation stopped before source changes.
- No network-dependent tests were run.

## Validation

- Initial `git status --short --branch` confirmed branch `main` and only the approved user-owned untracked `repository-analysis.md` and `implementation-plan.md` artifacts.
- `mvn test` in `risk-domain` passed with `BUILD SUCCESS`.
- `git diff --check` passed with no output before this report was created.
- The local Risk Domain artifact was not installed because Trading Core integration was not implemented.
- Complete Market Intelligence and Trading Core Maven suites were not run because neither module was modified and the implementation was blocked before integration.

## Deviations

- All planned Market Intelligence contract, lifecycle endpoint, and focused-test changes were not implemented because the end-to-end flow cannot consume a coherent ADR-028 context from Trading Core.
- All planned Trading Core dependency, Feign adapter, context assembly, engine orchestration, idempotency repository, API, and focused-test changes were not implemented because mandatory source facts are unavailable.
- The required focused and complete Market Intelligence and Trading Core validation was not executed because there was no implementation to validate.
- No `risk-domain` production code was modified, as required.
- No Execution Domain, broker-execution, frontend, Gateway, Market Data, or unrelated module behavior was modified.

## Remaining Work

- Approve and implement an account-specific Broker Service snapshot contract that identifies the requested broker account and supplies complete, timestamped broker-neutral account and position facts.
- Define authoritative daily-start balance and daily closed PnL sources, including day boundary and treatment of deposits, withdrawals, fees, funding, and corrections.
- Provide stable broker-neutral UUID position identity or approve a deterministic identity mapping contract.
- Provide stop-loss or another authoritative existing-position maximum-loss source so `PositionSnapshot.lossAtStop` can be populated without defaulting to zero.
- Define account-currency valuation and conversion snapshots for positions whose quote currency differs from the account currency.
- Introduce authoritative portfolio identity/version and account, market, and rule-set snapshot version semantics.
- Provide versioned effective risk profiles that map explicitly to ADR-028 rules, including maximum exposure and policy/rule provenance.
- Reapprove Story 0001 implementation after the required source contracts and ownership decisions are available.

## Recommendation

Blocked.

---

## Resumed Implementation — 2026-08-01

### Result

Implementation resumed after the human explicitly approved the architecture and resolved the context blockers recorded above. Story 0001 is now implemented across Broker Service, Market Data, Trading Core, and Market Intelligence. `risk-domain` production code remains unchanged.

The implemented flow is:

```text
Exact accepted Trade Plan version
    -> Trading Core authenticated/idempotent command
    -> account-scoped complete Broker risk facts
    -> historical/current Market Data valuation facts
    -> immutable persisted ADR-028 context snapshots
    -> deterministic risk-domain evaluation
    -> persisted official result
    -> approval-only Market Intelligence acknowledgment
```

No Execution Intent, execution attempt, broker write operation, Gateway route, frontend behavior, or broker-order behavior was added or changed.

### Implemented Changes

#### Broker Service

- Added `GET /internal/v1/broker-accounts/{brokerAccountId}/risk-snapshot` for an exact half-open risk-day interval.
- Added broker-neutral account, balance, equity, margin, position, protective-stop, closed-trade, settlement-asset, and ledger facts.
- Added complete bounded Kraken pagination for trade and ledger history. Incomplete or changing pagination fails closed.
- Added persistent database-issued snapshot versions.
- Added durable stable UUID position mappings keyed by broker account, provider, and provider position reference. Provider references remain provenance only.
- Added strict Kraken asset/instrument normalization so provider aliases do not escape the adapter.
- Added active protective-stop correlation and protected quantity. Missing, partial, or ambiguous protection marks the snapshot incomplete.
- Added `broker-service/src/main/resources/db/migration/V2__broker_risk_snapshots.sql`.

Modified Broker Service files:

- `broker-service/src/main/java/com/hope/trading/broker_service/broker/api/controller/BrokerQueryControllers.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/application/service/BrokerOperationServices.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/domain/capability/BrokerCapabilities.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/domain/model/BrokerModels.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/infrastructure/provider/kraken/KrakenBrokerProvider.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/infrastructure/provider/kraken/capability/KrakenCapabilities.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/infrastructure/provider/kraken/client/KrakenResilientClient.java`
- `broker-service/src/test/java/com/hope/trading/broker_service/broker/KrakenCapabilitiesTest.java`

New Broker Service files:

- `broker-service/src/main/java/com/hope/trading/broker_service/broker/infrastructure/persistence/RiskSnapshotPersistence.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/infrastructure/provider/kraken/capability/KrakenRiskSnapshotCapability.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/infrastructure/provider/kraken/mapper/KrakenAssetNormalizer.java`
- `broker-service/src/main/resources/db/migration/V2__broker_risk_snapshots.sql`
- `broker-service/src/test/java/com/hope/trading/broker_service/broker/KrakenAssetNormalizerTest.java`
- `broker-service/src/test/java/com/hope/trading/broker_service/broker/KrakenRiskSnapshotCapabilityTest.java`
- `broker-service/src/test/java/com/hope/trading/broker_service/broker/RiskSnapshotPersistenceTest.java`
- `broker-service/src/test/java/com/hope/trading/broker_service/broker/RiskSnapshotServiceContractTest.java`

#### Market Data

- Added persisted immutable price observations and valuation snapshots.
- Added `POST /internal/v1/valuation-snapshots/batch` for reporting currency, valuation timestamp, instruments, and assets.
- Added same-currency identity plus direct-bid and inverse-ask FX conversion. Multi-hop and unrelated parity are not used.
- Enforced source `effectiveAt` and `capturedAt` at or before the requested valuation timestamp, preventing historical look-ahead.
- Added configured staleness enforcement and persisted provenance containing source price selection, source timestamps, observation age, maximum age, conversion legs, policy version, and database-issued snapshot version.
- Added Flyway and changed the main schema policy from Hibernate `update` to `validate`.
- Added a legacy-only V1 baseline and forward V2 valuation migration so verified existing schemas can be baselined at V1 and then receive V2.

Modified Market Data files:

- `market-data/pom.xml`
- `market-data/src/main/java/com/hope/trading/market_data/service/TickerEventPublisher.java`
- `market-data/src/main/resources/application.properties`
- `market-data/src/test/java/com/hope/trading/market_data/service/MarketPriceSnapshotServiceTest.java`
- `market-data/src/test/resources/application-test.properties`

New Market Data files:

- `market-data/src/main/java/com/hope/trading/market_data/config/TimeConfiguration.java`
- `market-data/src/main/java/com/hope/trading/market_data/controller/InternalValuationController.java`
- `market-data/src/main/java/com/hope/trading/market_data/dto/ValuationSnapshotBatchRequest.java`
- `market-data/src/main/java/com/hope/trading/market_data/dto/ValuationSnapshotBatchResponse.java`
- `market-data/src/main/java/com/hope/trading/market_data/model/PriceObservation.java`
- `market-data/src/main/java/com/hope/trading/market_data/model/ValuationConversionLeg.java`
- `market-data/src/main/java/com/hope/trading/market_data/model/ValuationFact.java`
- `market-data/src/main/java/com/hope/trading/market_data/model/ValuationSnapshot.java`
- `market-data/src/main/java/com/hope/trading/market_data/repository/PriceObservationRepository.java`
- `market-data/src/main/java/com/hope/trading/market_data/repository/ValuationSnapshotRepository.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/ValuationSnapshotService.java`
- `market-data/src/main/resources/db/migration/V1__market_data_baseline.sql`
- `market-data/src/main/resources/db/migration/V2__risk_valuation_snapshots.sql`
- `market-data/src/test/java/com/hope/trading/market_data/service/ValuationSnapshotServiceTest.java`

#### Trading Core

- Added dedicated Trade Plan, Broker risk-fact, and Market valuation ports with Feign adapters.
- Added explicit account risk configuration for broker account, IANA risk timezone, reporting currency, and stable portfolio identity. Missing configuration fails closed.
- Added immutable semantic risk profiles, exact account assignments, explicit `MAX_POSITION_RISK`, `MAX_EXPOSURE`, and `DAILY_DRAWDOWN` rules, and policy/rule/assignment provenance. Legacy `Rules` are not promoted or backfilled.
- Added DST-correct risk-day calculation using `[00:00, next 00:00)` in the configured account timezone.
- Added immutable daily baseline, component snapshots, root context snapshot, official evaluation, result, and idempotency persistence. Risk snapshot versions are database-issued.
- Reconstructs day-start asset balances only from complete broker balances and ledger facts, then values them at risk-day start. An existing baseline remains immutable and is reused.
- Computes daily closed PnL from broker trades selected by `closedAt`, converting each trade's PnL and fee settlement asset at its closure timestamp.
- Corrected the legacy Trading Core repository selection from `openedAt` to half-open `closedAt` boundaries.
- Computes existing-position `lossAtStop` from the approved conservative current market fact, exact conversion provenance, broker-confirmed stop, direction, and fully protected quantity. Missing or partial protection fails closed.
- Assembles ADR-028 through `RiskEvaluationContextBuilder`, invokes `RiskEngines.standard`, and persists the complete context/result.
- Added structured `CONTEXT_UNAVAILABLE` outcomes without invoking Risk Domain when mandatory context is absent.
- Added `POST /api/v1/trade-plans/{tradePlanId}/versions/{version}/risk-evaluations` with authenticated ownership and mandatory `Idempotency-Key`.
- Added exact command idempotency and approval-only Market Intelligence acknowledgment.
- Added Flyway legacy V1, Story V2, PostgreSQL immutable-artifact V3, and changed main schema policy to `ddl-auto=validate`.

Modified Trading Core files:

- `trading-core/pom.xml`
- `trading-core/src/main/java/com/hope/trading/trading_core/config/TimeConfiguration.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/helper/TimeUtils.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/repository/TradeRepository.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/service/TradeAnalyticsServiceImpl.java`
- `trading-core/src/main/resources/application.properties`
- `trading-core/src/test/resources/application-test.properties`

New Trading Core files:

- `trading-core/src/main/java/com/hope/trading/trading_core/risk/api/TradePlanRiskEvaluationController.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/RiskDay.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/RiskEvaluationException.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/RiskEvaluationModels.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/BrokerRiskFactsPort.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/MarketValuationPort.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/TradePlanRiskPort.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/client/BrokerRiskClient.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/client/MarketIntelligenceRiskClient.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/client/MarketValuationClient.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/persistence/RiskPersistence.java`
- `trading-core/src/main/resources/db/migration/common/V1__trading_core_clean_install_baseline.sql`
- `trading-core/src/main/resources/db/migration/common/V2__trade_plan_risk_evaluation.sql`
- `trading-core/src/main/resources/db/migration/common/README.md`
- `trading-core/src/main/resources/db/migration/postgresql/V3__enforce_immutable_risk_artifacts.sql`
- `trading-core/src/test/java/com/hope/trading/trading_core/config/TimeConfigurationTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/repository/TradeRepositoryClosedAtTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/risk/application/RiskDayAndReconstructionTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationServiceTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/risk/architecture/RiskEvaluationArchitectureTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/risk/infrastructure/client/MarketValuationClientTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/risk/infrastructure/persistence/RiskPersistenceTest.java`

#### Market Intelligence

- Added an exact latest-`ACCEPTED` Trade Plan risk snapshot containing the exact referenced Trading Context and execution parameters.
- Added context identity and sizing/account-currency coherence checks.
- Added idempotent approved-evaluation acknowledgment linked to exact accepted plan version and evaluation UUID.
- Identical acknowledgment retries return the existing link. Conflicting evaluations or changed plan versions require a new evaluation.
- Only `APPROVED` and `APPROVED_WITH_WARNINGS` append `RISK_VALIDATED`; no transition to `READY_TO_EXECUTE` is introduced.

Modified Market Intelligence files:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketIntelligenceExceptionHandler.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/config/TradePlanningConfiguration.java`

New Market Intelligence files:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/InMemoryRiskValidationAcknowledgmentRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/InternalTradePlanRiskController.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/RiskValidationAcknowledgmentRequest.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/RiskValidationAcknowledgmentRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/tradeplan/RiskValidationAcknowledgment.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/tradeplan/RiskValidationDecision.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/tradeplan/TradePlanRiskHandoffException.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/tradeplan/TradePlanRiskHandoffService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/tradeplan/TradePlanRiskSnapshot.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/adapter/web/InternalTradePlanRiskControllerTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/tradeplan/TradePlanRiskHandoffServiceTest.java`

### Tests and Validation

Commands executed successfully after the final integration corrections:

```text
cd risk-domain && mvn test
Tests run: 21, failures: 0, errors: 0, skipped: 0

cd risk-domain && mvn install -DskipTests
BUILD SUCCESS

cd broker-service && ./mvnw test
Tests run: 52, failures: 0, errors: 0, skipped: 0

cd market-data && ./mvnw test
Tests run: 30, failures: 0, errors: 0, skipped: 0

cd trading-core && ./mvnw test
Tests run: 55, failures: 0, errors: 0, skipped: 0

cd market-intelligence && ../trading-core/mvnw test
Tests run: 113, failures: 0, errors: 0, skipped: 0

git diff --check
Passed with no output

git status --short --branch
Branch remained main; all implementation changes remained uncommitted and the three user-owned Story artifacts remained present.
```

Focused tests additionally covered:

- broker account isolation, stable UUID mapping, strict provider normalization, stop correlation, partial/ambiguous protection, pagination completeness, and persistent versions;
- same/direct/inverse FX, conservative bid/ask selection, stale facts, late-arrival no-look-ahead, source/version/staleness provenance, and immutable persisted valuation snapshots;
- risk-day DST boundaries, ledger reconstruction, immutable baseline reuse, `closedAt` selection, closure-time currency conversion, missing configuration/profile/FX/stop, current-market stop loss, rule provenance, database-issued versions, exact Trade Plan version, idempotency, and execution-package isolation;
- exact accepted Trade Plan snapshot mapping and idempotent approval-only lifecycle acknowledgment.

### Deviations and Limitations

- The original Implementation Plan selected in-memory Trading Core evaluation storage and expected no Broker Service or Market Data changes. The resumed human delegation explicitly superseded those points by requiring durable Trading Core context/evaluation persistence plus read-only Broker and Market Data contracts. The original plan and blocker report were preserved as historical records.
- No production PostgreSQL containers were running during implementation. The existing unmanaged Trading Core and Market Data production schemas could not be inspected directly. Baselines were derived from the current JPA mappings and validated with Flyway plus Hibernate `validate` on H2 PostgreSQL mode.
- Existing unmanaged Trading Core and Market Data databases require an operator to back up and verify their real schemas before explicitly baselining at V1. Automatic baselining is disabled. No account configuration, profile, assignment, baseline, maximum exposure, or other financial value is seeded or backfilled.
- The PostgreSQL-specific Trading Core V3 immutable-artifact triggers were not executed against a live PostgreSQL server in this environment. They remain a required deployment validation.
- Broker Service's existing test profile still uses Hibernate schema creation rather than running its PostgreSQL Flyway history. The forward V2 SQL was inspected and the persistence behavior was tested on H2, but a real PostgreSQL migration rehearsal remains required.
- Market Intelligence Trade Plans, Trading Contexts, and risk-validation acknowledgment links remain in memory because durable Market Intelligence storage was not part of this Story's approved persistence scope.
- Valuation supports same-currency, direct, and inverse conversion only. Missing direct/inverse conversion fails closed; multi-hop is intentionally not implemented.

### Remaining Risks

- Rehearse Broker Service V1 -> V2, Market Data verified-baseline V1 -> V2, and Trading Core verified-baseline V1 -> V2 -> PostgreSQL V3 against disposable PostgreSQL 16 databases and schema-only copies of the deployed databases before deployment.
- Provision explicit account risk configuration, complete semantic risk profiles, and exact account assignments before attempting evaluation. Existing accounts intentionally return `CONTEXT_UNAVAILABLE`.
- Validate the read-only Kraken facts against a sandbox account containing open positions, protective stops, ledger activity, and closed trades. Broker order submission remains outside this Story.

### Resumed Recommendation

Implementation complete and ready for human diff review. No code review artifact was created, and no commit, push, merge, reset, or discard operation was performed.

---

## Corrective Implementation — 2026-08-01

### Result

All findings CR-001 through CR-005 from the approved Code Review were implemented. The historical `code-review.md` was preserved unchanged. No Execution Intent, broker order, frontend, Gateway, or Risk Domain production change was introduced.

### CR-001 — Distributed Acknowledgment Consistency

- Trading Core now inserts a durable `risk_acknowledgment_outbox` row in the same local transaction as each approved official evaluation.
- Market Intelligence is never called from the official evaluation transaction. Immediate delivery begins only after the `TransactionTemplate` commit succeeds.
- A scheduled durable dispatcher retries due deliveries with persisted attempt count, exponential backoff, pessimistic claims, claim tokens, and expiring leases.
- Claim, success, and failure state changes use separate `REQUIRES_NEW` transactions; the remote HTTP operation runs outside a database transaction.
- An exact idempotent evaluation retry returns the original official evaluation without context reassembly and explicitly retries any incomplete acknowledgment delivery.
- At-least-once redelivery preserves the exact evaluation ID, Trade Plan ID/version, decision, and evaluation timestamp. Market Intelligence returns the existing acknowledgment for an identical duplicate.
- Tests cover failure after the official commit, successful retry, a duplicate caused by a lost remote response, atomic evaluation/outbox rollback, persistence across retry, and no remote call when official persistence or commit fails.

### CR-002 — Broker Account Authorization

- The Broker risk snapshot controller now derives the actor from authenticated `BrokerPrincipal` rather than the request path.
- `GetRiskSnapshotService` authorizes the requested account through `BrokerConnectionRepository.findByBrokerAccountIdAndOwnerId` before provider resolution, credential access, or Kraken communication.
- Possession of a broker account UUID and an unrelated authenticated JWT no longer grants access.
- Endpoint and service tests prove owner success, cross-account `403 BROKER_AUTHORIZATION_FAILED`, and denial before provider resolution.

### CR-003 — Coherent Broker Capture

- Kraken risk capture now brackets account/history acquisition with balance-before and balance-after reads and retries the complete capture up to three times when balances change.
- `observedAt` is assigned only after acquisition completes.
- Closed trades and ledger entries after the observation boundary make the snapshot incomplete.
- Ledger fee semantics are explicit: `amount` is the signed gross movement, `fee` is a non-negative debit in the same asset, and net balance movement is `amount - fee`.
- Broker Service validates each asset's chronological running balances and terminal ledger balance against the final current balance. Mismatch, negative fee, unstable balances, and boundary violations fail closed.
- Trading Core independently verifies observation boundaries, non-negative fees, running balances, terminal current balances, and reconstructs the opening balance from the first authoritative running balance. It no longer ignores the ledger `balance` field.
- Tests cover stable acquisition, account activity between provider calls, bounded instability, running and terminal ledger mismatch, facts after observation, and fee application exactly once.

### CR-004 — Reporting Currency

- Trading Core normalizes Trade Plan account and sizing currencies with `Locale.ROOT` and requires both to equal the configured reporting currency before requesting Broker or Market Data facts.
- The accepted same-currency sizing invariant remains in force; no unapproved cross-currency sizing contract was introduced.
- A mismatch returns `CONTEXT_UNAVAILABLE` with `TRADE_PLAN_ACCOUNT_CURRENCY_MISMATCH` or `TRADE_PLAN_SIZING_CURRENCY_MISMATCH` and performs no financial-fact request.

### CR-005 — Durable Market Intelligence Acknowledgment

- Market Intelligence production wiring now persists immutable Trade Plan versions and risk-validation acknowledgments through Spring Data JPA.
- The acknowledgment use case acquires a pessimistic lock on the latest Trade Plan version and performs the exact `ACCEPTED -> RISK_VALIDATED` append plus acknowledgment insert in one transaction.
- Database constraints uniquely bind `(trade_plan_id, accepted_trade_plan_version)` and `evaluation_id`, with foreign keys to the accepted and resulting validated versions.
- Identical retries return the persisted acknowledgment. Conflicting plan/evaluation linkage is rejected, and a losing concurrent transaction rolls back its lifecycle append.
- Evaluation and acknowledgment timestamps are normalized to database microsecond precision so restart retries compare identically.
- The schema-only Market Intelligence V1 migration creates no inferred Trade Plans, evaluations, acknowledgments, timestamps, or decisions.
- Restart-style and concurrent integration tests verify durable identical retry, one lifecycle append, uniqueness, and rollback on conflicting linkage.
- Docker Compose now provisions and wires a service-owned Market Intelligence PostgreSQL database using the repository's existing PostgreSQL infrastructure pattern.

### Corrective Files

Broker Service:

- `broker-service/src/main/java/com/hope/trading/broker_service/broker/api/controller/BrokerQueryControllers.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/application/service/BrokerOperationServices.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/infrastructure/provider/kraken/capability/KrakenRiskSnapshotCapability.java`
- `broker-service/src/test/java/com/hope/trading/broker_service/broker/BrokerApiSecurityIntegrationTest.java`
- `broker-service/src/test/java/com/hope/trading/broker_service/broker/KrakenRiskSnapshotCapabilityTest.java`
- `broker-service/src/test/java/com/hope/trading/broker_service/broker/RiskSnapshotServiceContractTest.java`

Trading Core:

- `trading-core/src/main/java/com/hope/trading/trading_core/TradingCoreApplication.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/RiskAcknowledgmentDeliveryService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/persistence/RiskPersistence.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/risk/application/RiskAcknowledgmentDeliveryServiceTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/risk/application/RiskDayAndReconstructionTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationServiceTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/risk/infrastructure/persistence/RiskAcknowledgmentOutboxPersistenceTest.java`
- `trading-core/src/test/resources/application-test.properties`

Market Intelligence:

- `market-intelligence/pom.xml`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaRiskValidationAcknowledgmentEntity.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaRiskValidationAcknowledgmentRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaTradePlanEntity.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaTradePlanId.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaTradePlanRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/SpringDataRiskValidationAcknowledgmentRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/SpringDataTradePlanRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/TradePlanRepository.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/tradeplan/TradePlanRiskHandoffService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/config/TradePlanningConfiguration.java`
- `market-intelligence/src/main/resources/application.properties`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/adapter/persistence/RiskValidationAcknowledgmentPersistenceIntegrationTest.java`
- `market-intelligence/src/test/resources/application-test.properties`

Deployment wiring:

- `docker-compose.yml`

### Corrective Migrations

- `trading-core/src/main/resources/db/migration/common/V4__risk_acknowledgment_outbox.sql`
- `market-intelligence/src/main/resources/db/migration/V1__durable_trade_plan_risk_acknowledgments.sql`

Both migrations are schema-only and intentionally contain no data backfill.

### Focused Validation

```text
cd broker-service && ./mvnw -Dtest=KrakenRiskSnapshotCapabilityTest,RiskSnapshotServiceContractTest,BrokerApiSecurityIntegrationTest test
Tests run: 17, failures: 0, errors: 0, skipped: 0

cd trading-core && ./mvnw -Dtest=TradePlanRiskEvaluationServiceTest,RiskDayAndReconstructionTest,RiskAcknowledgmentDeliveryServiceTest,RiskPersistenceTest,RiskAcknowledgmentOutboxPersistenceTest test
Tests run: 29, failures: 0, errors: 0, skipped: 0

cd market-intelligence && ../trading-core/mvnw -Dtest=RiskValidationAcknowledgmentPersistenceIntegrationTest,TradePlanRiskHandoffServiceTest,InternalTradePlanRiskControllerTest test
Tests run: 9, failures: 0, errors: 0, skipped: 0
```

### Complete Validation

```text
cd risk-domain && mvn install
Tests run: 21, failures: 0, errors: 0, skipped: 0
BUILD SUCCESS; local artifact installed

cd broker-service && ./mvnw test
Tests run: 61, failures: 0, errors: 0, skipped: 0

cd market-data && ./mvnw test
Tests run: 30, failures: 0, errors: 0, skipped: 0

cd trading-core && ./mvnw test
Tests run: 66, failures: 0, errors: 0, skipped: 0

cd market-intelligence && ../trading-core/mvnw test
Tests run: 117, failures: 0, errors: 0, skipped: 0

Total complete-suite tests: 295

docker compose config --quiet
Passed with no output

git diff --check
Passed with no output
```

### Deviations

- The original Story implementation left Market Intelligence Trade Plans and acknowledgment links in memory. CR-005 necessarily superseded that limitation by adding service-owned JPA/Flyway persistence and PostgreSQL Compose wiring.
- No message broker was introduced. Trading Core uses a transactional outbox and explicit durable retry as requested.
- No different sizing-currency conversion was added because the accepted Market Intelligence contract requires sizing currency to equal account currency.

### Remaining Risks

- New Trading Core and Market Intelligence migrations were validated with H2 in PostgreSQL mode, not rehearsed against a live PostgreSQL 16 schema. Deployment still requires a disposable PostgreSQL migration rehearsal.
- Existing process-local Market Intelligence Trade Plans and acknowledgments cannot be backfilled because no authoritative durable source exists. The migration correctly invents no data.
- `TradingContextRepository` remains in memory. Persisted acknowledgment retries survive restart, but loading a new risk snapshot after restart still requires the referenced Trading Context to be rebuilt or made durable in a separate approved scope.
- The coherent Kraken protocol and accounting semantics are covered with deterministic provider fixtures but still require sandbox validation against real Kraken account activity and ledger payloads.
- Trading Core acknowledgment delivery is intentionally at-least-once. Safety depends on the now-persistent Market Intelligence endpoint's exact-identity idempotency, which is enforced by application checks and database uniqueness.

### Corrective Recommendation

Corrective implementation and validation are complete. Changes remain uncommitted for human review. No new Code Review or Engineering Report was created.
