# Implementation Report - Story 0192

## Outcome

Implemented preparatory PostgreSQL/Testcontainers CFD risk and compatibility
evidence and closed the direct settlement bypass. The Story does not authorize
or prove a positive PAPER CFD settlement lifecycle because ADR-051 and
ADR-083 keep `CASH + CFD` fail-closed.

## Changes

### Production

`PaperCfdSettlementService` no longer assigns a manual UUID to a JPA-generated
`Trade` before persistence. It now flushes the account, resolves the newly
persisted trade from the returned managed aggregate using its generated
identity, and records provenance against that identity. A fallback UUID is
retained only for focused in-memory unit repositories that do not emulate JPA
generated IDs. The lookup no longer selects a trade by ambiguous business
fields such as symbol, timestamp, and status.

The finalization selection now identifies CFD products explicitly and returns a
fail-closed unavailable/incompatible result instead of silently falling back to
generic settlement. `PaperCfdSettlementService` independently enforces the
same compatibility policy before any mutation.

### Tests

`PaperCfdDeterministicLifecycleIntegrationTest` now covers:

- persisted T0 approval reference through the existing authorization path;
- real T1 revalidation while keeping incompatible CFD settlement blocked;
- real persisted T0 and T1 risk decisions;
- T1 rejection transition before submission/settlement;
- absence of external broker submission;
- direct service rejection and application-path rejection for `CASH + CFD`.

Positive CFD accounting and replay suites are explicitly disabled as deferred
capability evidence until compatibility authorization exists; they are not
reported as current acceptance evidence.

## Validation

```text
PaperCfdDeterministicLifecycleIntegrationTest: PASS (3 tests)
PaperCfdSettlementServiceTest: DEFERRED (compatibility blocked)
PaperCfdSettlementPersistenceHardeningIntegrationTest: DEFERRED (compatibility blocked)
PaperExecutionPersistenceIntegrationTest: PASS
ExecutionPipelineTest: PASS (12 tests)
ExecutionTimeRiskRevalidationServiceTest: PASS (12 tests)
SettlementCompatibilityPolicyTest: PASS (2 tests)
trading-core ./mvnw -q test: PASS
trading-core ./mvnw -q verify: PASS
git diff --check: PASS
```

The full suite still emits the known historical Flyway V34 migration
failure-path log during migration coverage; the test result itself passes and
V34 was not changed.

## Acceptance Evidence and Limitation

The new integration test exercises authorization, real
`TradePlanRiskEvaluationService` and `ExecutionTimeRiskRevalidationService`
decisions, persistence, and fail-closed settlement routing. External market and
broker facts are controlled at their infrastructure boundaries; T0 and T1
decisions are not mocked.

Both the application route and the settlement service reject `CASH + CFD`
before any accounting mutation. Enabling a positive public/application CFD path
requires ADR-083 implementation and a separately approved Phase 8 decision.

## Documentation Outcome

Documentation update: the Story-local Story, Repository Analysis,
Implementation Plan, Implementation Report, and Code Review are sufficient. No
ADR or general repository documentation changed because the implementation
preserves the existing lifecycle and compatibility architecture.

## Vault Outcome

The vault was not consulted during Repository Analysis because current
repository ADRs, Stories, source, and tests contained the required context. No
vault action is proposed.

## Safety Invariants

```text
CASH_PLUS_CFD_ENABLED = NO
INVENTORY_PLUS_CFD_ENABLED = NO
RISK_DOMAIN_CHANGED = NO
BROKER_SERVICE_CHANGED = NO
LIVE_AUTHORITY_CHANGED = NO
REAL_BROKER_ORDERS = 0
CTRADER_PROVIDER_MUTATIONS = 0
MIGRATIONS_ADDED = 0
```

The preparatory fail-closed evidence is complete. Positive CFD settlement
remains blocked pending ADR-083 implementation and Phase 8 authorization.
