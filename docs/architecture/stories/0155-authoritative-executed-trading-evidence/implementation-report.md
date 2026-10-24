# Story 0155 - Implementation Report

## Result

Implemented the provider-neutral PAPER executed-trading evidence path with
durable execution provenance. cTrader execution, credentials, payloads and
runtime validation remain untouched and disabled.

## Changes

- Added Flyway migration `V39__persist_paper_trade_execution_provenance.sql`.
- Added the `trade_execution_provenance` persistence entity and repository.
- Recorded immutable provenance for PAPER entry settlement, linked to the
  Trade, ExecutionAttempt, BrokerOrder, fill identity and fill execution time.
- Made provenance creation idempotent by `(broker_order_id, fill_id)`.
- Persisted the attempt and broker order before settlement so the provenance
  foreign keys are valid in the same transaction.
- Added `ExecutedTradingEvidenceReader` using provenance rather than raw
  `Trade.openedAt` values.
- Applied Challenge-effective timezone/reset and `RiskDay` boundaries.
- Wired automatic PAPER Challenge reevaluation to the evidence reader while
  preserving explicit evidence overloads and provider-confirmation behavior.
- Kept legacy HTTP-created trades and broker-synchronized positions out of
  authoritative evidence.
- Added unit coverage for distinct Risk days, Challenge timezone handling,
  malformed provenance and missing Risk-day configuration.
- Added validation for null `openedAt`, provenance schema constraints and
  repeated PAPER entry settlement.
- Added focused broker-fill immutability tests for identical replay and
  conflicting rewrite rejection.
- Made scheduled Challenge reevaluation retry fail-safe when a test or startup
  context temporarily provides no clock instant, with regression coverage.
- Moved provenance access behind the application `TradeExecutionProvenanceStore`
  port and made the production dependency mandatory.
- Preserved immutable broker fill rows so provenance foreign keys remain valid
  across broker-order updates.
- Updated migration history assertions from version 38 to 39.

## Validation

Executed successfully:

```text
mvn -q -DskipTests compile
mvn -q -Dtest=ExecutedTradingEvidenceReaderTest,ChallengeReevaluationServiceTest test
mvn -q -Dtest=PaperSettlementOutboxRollbackPostgresIntegrationTest,ChallengeMonitoringConcurrencyIntegrationTest,PostgresFreshInstallMigrationTest,PaperExitAcceptanceIntegrationTest,ExecutedTradingEvidenceReaderTest,ChallengeReevaluationDeliveryServiceTest test
mvn -q -Dtest=PaperExecutionPersistenceIntegrationTest,PaperExitAcceptanceIntegrationTest test
git diff --check
```

The Story-focused and PAPER integration test batteries completed without test
failures or errors after the final corrections. Test output still contains
non-blocking environment/framework warnings, including Mockito dynamic-agent,
H2/Flyway version and Spring Cloud LoadBalancer cache warnings. The V34
migration error is expected output from the negative test that rejects unknown
legacy providers.

## Authority and Safety

- Challenge evidence is derived only from durable PAPER fill provenance.
- `Trade.openedAt` alone is not used as evidence.
- Evidence is fail-closed for malformed provenance or invalid Challenge Risk-day
  configuration.
- No cTrader execution or provider validation is claimed.
- No new ADR was required because the implementation remains inside the
  provider-neutral execution/evidence boundary defined by ADR-075.

## Documentation Update

- Story updated with implemented acceptance criteria and residual cTrader
  limitations.
- Implementation plan reconciled with the durable provenance design.
- Obsidian vault: not modified; no vault update required.

## Remaining Gate

The initial independent review findings were corrected and the
post-correction review found no confirmed defects. No commit, push or merge was
performed.
