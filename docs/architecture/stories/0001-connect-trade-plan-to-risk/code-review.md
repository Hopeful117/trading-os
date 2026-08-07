# Code Review

## Verdict

Changes requested.

The implementation establishes the intended ADR-028 boundaries and all 271 automated tests pass, but the review found three blocking correctness/security issues and two important durability/validation gaps. The implementation should not proceed to Engineering Report until these findings are resolved and revalidated.

## Findings

### CR-001 — Blocking — The official evaluation and Market Intelligence acknowledgment can diverge permanently

`TradePlanRiskEvaluationService` persists the official evaluation and then synchronously calls Market Intelligence inside the same local transaction. The transaction explicitly does not roll back for `RiskEvaluationException`. If the acknowledgment fails, the evaluation is committed and the request returns 503. A retry with the same idempotency key returns the stored response immediately and never retries the acknowledgment. Conversely, Market Intelligence can commit its lifecycle transition before the Trading Core transaction later fails to commit.

Evidence:

- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationService.java:82`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationService.java:84`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationService.java:226`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationService.java:233`

Required correction:

- persist an acknowledgment/outbox state atomically with the official evaluation;
- deliver the acknowledgment after commit with durable retry and an idempotent Market Intelligence endpoint;
- make idempotent evaluation retries inspect/retry incomplete acknowledgment delivery instead of returning immediately;
- test both failure directions: acknowledgment unavailable after evaluation commit, and Trading Core commit failure after remote acknowledgment.

### CR-002 — Blocking — Any authenticated Broker Service caller can request another account's risk facts

The new endpoint accepts an arbitrary `brokerAccountId`. Broker Service only requires authentication; neither the controller nor `GetRiskSnapshotService` verifies that the authenticated principal owns the requested broker account. Trading Core performs an ownership check before its own call, but that does not protect the Broker Service boundary from direct or compromised internal callers. The response includes balances, positions, stops, trades, and ledger activity.

Evidence:

- `broker-service/src/main/java/com/hope/trading/broker_service/broker/api/controller/BrokerQueryControllers.java:12`
- `broker-service/src/main/java/com/hope/trading/broker_service/security/BrokerSecurityConfiguration.java:24`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/application/service/BrokerOperationServices.java:18`

Required correction:

- authorize the broker account against the authenticated subject or an authenticated service identity with explicit account scope;
- do not rely on possession of the account UUID;
- add endpoint-level tests proving cross-account access is rejected and the correct owner/service scope succeeds.

### CR-003 — Blocking — A Broker snapshot can be marked complete although balances and ledger are not from one coherent observation

`observedAt` is captured before several sequential remote calls. Balance is read first and ledger/history later, with no provider revision, balance re-read, ledger-balance reconciliation, or check that facts are bounded by the actual observation instant. Account activity during collection can therefore combine a pre-trade balance with post-trade ledger entries and still produce `COMPLETE`. Trading Core then reconstructs the daily opening balance by subtracting ledger deltas while ignoring the authoritative running `balance` included in each ledger entry.

Evidence:

- `broker-service/src/main/java/com/hope/trading/broker_service/broker/infrastructure/provider/kraken/capability/KrakenRiskSnapshotCapability.java:34`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/infrastructure/provider/kraken/capability/KrakenRiskSnapshotCapability.java:39`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/infrastructure/provider/kraken/capability/KrakenRiskSnapshotCapability.java:48`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationService.java:341`

Required correction:

- define and implement a coherent capture protocol, such as balance-before/history/balance-after with retry or failure when the balances differ unexpectedly;
- set the snapshot observation boundary from the completed coherent acquisition, not before the first network call;
- reconcile ledger running balances and current balances per asset, including fee semantics;
- reject entries after the coherent observation boundary;
- add a test simulating account activity between provider calls.

### CR-004 — Major — Trade Plan account currency is not checked against Trading Core reporting currency

The Trade Plan validator only checks that its declared account currency equals its sizing currency. It never compares that account currency with `account_risk_configuration.reporting_currency`, which is the currency used to assemble the ADR-028 account context. A stale or incorrectly linked Trading Context can therefore be accepted and silently converted as though it belonged to a differently configured account.

Evidence:

- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationService.java:117`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationService.java:309`

Required correction:

- require normalized `plan.accountCurrency == configuration.reportingCurrency` before requesting financial facts;
- retain the sizing-currency conversion only for a sizing amount explicitly denominated in a different supported currency, if the domain permits it;
- add a focused mismatch test returning `CONTEXT_UNAVAILABLE` or the approved command error.

### CR-005 — Major — Risk-validation acknowledgment provenance is lost on Market Intelligence restart

The acknowledgment link between the accepted Trade Plan version and the official evaluation is stored only in two in-memory maps. After restart, the lifecycle may remain `RISK_VALIDATED` while the exact evaluation link and idempotency record disappear. A replay can then conflict with the evolved plan or create a different acknowledgment history. This weakens the exact-version audit trail introduced by the Story.

Evidence:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/InMemoryRiskValidationAcknowledgmentRepository.java:12`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/InMemoryRiskValidationAcknowledgmentRepository.java:28`

Required correction:

- persist the acknowledgment with unique constraints on accepted plan identity/version and evaluation ID;
- make lifecycle transition plus acknowledgment atomic within Market Intelligence;
- add restart/persistence and concurrent duplicate tests.

## Positive Observations

- Risk Domain remains free of repositories and external calls.
- Trading Core owns context assembly and records component/source provenance.
- Missing configuration, profiles, stops, prices, and conversions fail closed.
- Position identities and snapshot versions are database-issued rather than fabricated.
- Daily PnL now uses half-open `closedAt` boundaries.
- Market Data records conservative direct/inverse conversions and prevents historical look-ahead.
- The implementation did not add execution intent, broker order submission, frontend, or Gateway behavior.

## Validation Reviewed

- `risk-domain`: 21 tests passed.
- `broker-service`: 52 tests passed.
- `market-data`: 30 tests passed.
- `trading-core`: 55 tests passed.
- `market-intelligence`: 113 tests passed.
- Total: 271 tests, zero reported failures or errors.
- `git diff --check`: passed.

The existing tests do not cover the distributed failure, cross-account authorization, coherent-capture race, reporting-currency mismatch, or restart durability scenarios described above.

## Required Next Step

Resolve CR-001 through CR-005, rerun all affected module suites and `git diff --check`, update the Implementation Report with the corrective implementation, then request a new human review before repeating Code Review.
