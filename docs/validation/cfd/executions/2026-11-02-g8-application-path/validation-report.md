# CFD G8 Full Application-Path Qualification

================================================
CFD G8 FULL APPLICATION-PATH QUALIFICATION
================================================

REPOSITORY_SHA:
edb5e7e47eee79d4cbe7491b2b5321b151c029df

BRANCH:
validation/cfd-epic-qualification

EXECUTION_REPORT:
docs/validation/cfd/executions/2026-11-02-g8-application-path/validation-report.md

PRODUCTION_CODE_CHANGED:
NO

========================================
APPLICATION PATH
========================================

APPLICATION_BOUNDARY:
ExecuteTradeService.execute(ExecutionIntentId), preceded by the legitimate
ValidateAndCreateService application setup that verifies the authorized TradePlan,
T0 decision, ownership and PAPER broker-account relationship.

HTTP_BOUNDARY_USED:
NO

IF_NO_HTTP_BOUNDARY_WHY:
The HTTP layer adds JWT/request mapping and unrelated web infrastructure. The
selected application boundary is the actual deterministic execution use case and
retains the production validation, T1, routing, simulated execution, finalization,
compatibility, settlement and persistence collaborators.

PRODUCTION_CALL_GRAPH:
ValidateAndCreateService.validateAndCreate
-> ExecuteTradeService.execute
-> ExecutionTimeRiskRevalidationService.evaluateAndPersist
-> ExecutionValidationStep
-> IdempotencyVerificationStep
-> ExecutionAttemptCreationStep
-> BrokerSubmissionStep
-> RoutingBrokerExecutionAdapter
-> SimulatedExecutionAdapter
-> BrokerResponseProcessingStep
-> ExecutionFinalizationStep
-> PaperCfdSettlementService.resolveProductIdentity/supports/settle
-> SettlementCompatibilityPolicy
-> JpaTradeExecutionProvenanceStore and PostgreSQL persistence

DIRECT_SETTLEMENT_INVOCATION:
NO

BUSINESS_GUARDS_BYPASSED:
NONE

REAL_APPLICATION_BOUNDARY:
YES

========================================
TRADE PLAN / AUTHORIZATION
========================================

TRADE_PLAN_VALID:
YES

OWNER_CORRECT:
YES

ACCOUNT_CORRECT:
YES

PRODUCT:
CFD, EUR/USD

ACCOUNT_MODEL:
CASH

HUMAN_AUTHORIZATION_PRESENT:
YES

HUMAN_AUTHORIZATION_ENFORCED:
YES

NEGATIVE_HUMAN_AUTH_CONTROL:
PASS

BROKER_SUBMISSION_WITHOUT_AUTH:
0

FINANCIAL_MUTATION_WITHOUT_AUTH:
0

========================================
T0 / T1
========================================

T0_PREREQUISITE_PRESENT:
YES

T0_RESULT:
APPROVED, persisted PRE_TRADE evaluation

T0_REQUIRED_MARGIN:
11.00 USD

T1_EXECUTED:
YES

T1_RESULT:
APPROVED/PASS

T1_REQUIRED_MARGIN:
11.02 USD

T1_AUTHORITY_REUSED_FROM_T0:
NO

NEGATIVE_T1_CONTROL:
PASS

FAIL_CLOSED_T1_CONTROL:
PASS, stale authoritative valuation fact

BROKER_SUBMISSION_AFTER_T1_REJECT:
0

FINANCIAL_MUTATION_AFTER_T1_REJECT:
0

========================================
BROKER ROUTING
========================================

ROUTING_STEP:
RoutingBrokerExecutionAdapter.submit

SELECTED_EXECUTION_ADAPTER:
SimulatedExecutionAdapter

SIMULATED_EXECUTION_ADAPTER_USED:
YES

LIVE_PROVIDER_ADAPTER_USED:
NO

AUTHORITATIVE_FILL_PRODUCED_BY_ADAPTER:
YES

BROKER_ORDER_ID:
Runtime-generated UUID, persisted by the application path

FILL_ID:
fill-corr-<runtime-generated UUID>

FILL_INSTRUMENT:
EUR/USD

FILL_SIDE:
BUY

FILL_QUANTITY:
1000

FILL_PRICE:
1.10100 USD per EUR

FILL_FEE:
0 USD

========================================
FINALIZATION / COMPATIBILITY
========================================

FINALIZATION_REACHED_FROM_APPLICATION_FLOW:
YES

COMPATIBILITY_GUARD_ENFORCED:
YES

CASH_CFD:
ALLOWED

INVENTORY_CFD:
REJECTED

UNKNOWN_CFD:
REJECTED

PAPER_CFD_SETTLEMENT_SELECTED:
YES

GENERIC_SPOT_SETTLEMENT_USED:
NO

========================================
DURABLE FINANCIAL RESULT
========================================

TRADE_CREATED:
YES

SIGNED_CFD_EXPOSURE:
+1000 underlying units

EXPECTED_SIGNED_EXPOSURE:
+1000 underlying units

ENTRY_FILL_ID_PERSISTED:
Runtime-generated simulated fill ID, persisted and linked to provenance

ENTRY_PRICE_PERSISTED:
1.10100

AUTHORITATIVE_FILL_PRICE_PERSISTED:
YES

BASE_INVENTORY_BEFORE:
0 EUR

BASE_INVENTORY_AFTER:
0 EUR

BASE_INVENTORY_DELTA_ZERO:
YES

FULL_NOTIONAL:
1101.00 USD

ACTUAL_CASH_DELTA:
0 USD

FULL_NOTIONAL_DEBIT:
NO

FEE_EXPECTED:
0 USD for the simulated adapter

FEE_ACTUAL:
0 USD

FEE_EXACTLY_ONCE:
YES

PROVENANCE_PERSISTED:
YES

========================================
DURABLE RECONSTRUCTION
========================================

COMMIT:
PASS, transaction committed by the application path before reload assertions

PERSISTENCE_CONTEXT_RECONSTRUCTED:
YES

TRADE_RELOADED:
YES

EXPOSURE_RELOADED:
YES

PROVENANCE_RELOADED:
YES

DURABLE_RECONSTRUCTION:
PASS

========================================
NEGATIVE CONTROLS
========================================

NO_HUMAN_AUTH:
PASS

T1_REJECT:
PASS

T1_MISSING_OR_STALE:
PASS

WRONG_OWNER:
PASS

NEGATIVE_CONTROLS_FINANCIAL_MUTATIONS:
0

Application-level replay was not forced. A completed execution intent is a
terminal state and ExecuteTradeService rejects re-submission before T1. G6
independently proves exact same-fill replay and concurrent exactly-once settlement
against PostgreSQL.

========================================
SAFETY
========================================

PAPER_ONLY:
YES

AUTOMATIC_EXECUTION:
NO

HUMAN_AUTHORIZATION_REQUIRED:
YES

CTRADER_SAFETY_EXECUTION_ENABLED:
false

LIVE_CFD_SETTLEMENT_AUTHORIZED:
NO

REAL_CTRADER_ORDERS:
0

REAL_CTRADER_CLOSES:
0

PROVIDER_MUTATIONS:
0

NO_PROVIDER_MUTATION:
YES

========================================
REGRESSION
========================================

G1_REGRESSION:
PASS

G2_REGRESSION:
PASS

G3_REGRESSION:
PASS

G4_REGRESSION:
PASS

G5_REGRESSION:
PASS

G6_REGRESSION:
PASS

G7_REGRESSION:
PASS

========================================
VERIFICATION
========================================

G8_TARGETED_TESTS:
PASS, PaperCfdDeterministicLifecycleIntegrationTest: 10 tests

POSTGRES_G8_TESTS:
PASS, Testcontainers PostgreSQL application and persistence suites

TRADING_CORE_TEST:
PASS, ./mvnw -q test

TRADING_CORE_VERIFY:
PASS, ./mvnw -q verify with 600-second timeout

RISK_DOMAIN_VERIFY:
PASS, risk-domain mvn -q verify

MIGRATION_FRESH_INSTALL:
PASS, PostgresFreshInstallMigrationTest through V51

GIT_DIFF_CHECK:
PASS

UNRELATED_BROKER_COVERAGE_STATUS:
Known unrelated broker-service coverage remains 75.33% against the 80% threshold;
not part of CFD G8 evidence.

========================================
FINDINGS
========================================

HIGH_FINDINGS:
NONE

MEDIUM_FINDINGS:
NONE

LOW_FINDINGS:
NONE

INFO_FINDINGS:
DevLog context request timed out; repository protocol, current source and
executable evidence were used. No provider writes or real broker mutations were
performed.

========================================
FINAL GATES
========================================

G1:
PASS

G2:
PASS

G3:
PASS

G4:
PASS

G5:
PASS

G6:
PASS

G7:
PASS

G8:
PASS

========================================
FINAL DECISION
========================================

CFD_IMPLEMENTED:
YES

CFD_PROVEN:
YES

CFD_VALIDATED:
YES

CFD_VALIDATION_EPIC:
COMPLETE

REAL_PROVIDER_EXECUTION_AUTHORIZED:
NO

AUTOMATIC_TRADING_AUTHORIZED:
NO

READY_FOR_FINAL_HUMAN_REVIEW:
YES

NEXT_REQUIRED_ACTION:
Human review of the complete uncommitted worktree. Do not execute provider-backed
CFD trading or automatic trading.
