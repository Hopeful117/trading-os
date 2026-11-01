# Implementation Report - Story 0191

## Result

Implemented the ADR-083 persistence hardening on branch
`story/0191-adr-083-margin-policy-persistence-hardening`.

The implementation adds PostgreSQL defense in depth and proves the persisted
PAPER policy authority through commit, persistence-context reconstruction, reload,
real resolver usage, and deterministic margin calculation. `CASH + CFD` remains
rejected.

## Baseline

```text
REMOTE_MAIN: dfafedb1fb76ca2d4895ab0ad20a36ef80488345
LOCAL_BASE: dfafedb1fb76ca2d4895ab0ad20a36ef80488345
LOCAL_HEAD: dfafedb1fb76ca2d4895ab0ad20a36ef80488345
BRANCH: story/0191-adr-083-margin-policy-persistence-hardening
STORY: 0191
PREVIOUS_LATEST_MIGRATION: V49
NEW_LATEST_MIGRATION: V50
```

Pre-existing untracked files were preserved and not staged:

```text
docs/architecture/stories/0185-adr-082-phase-8-cash-cfd-compatibility/
docs/architecture/stories/0187-paper-margin-authority-adr/
docs/architecture/stories/0189-adr-082-phase-8-cash-cfd-compatibility-retry/
```

Unrelated tracked frontend changes in `trading-os-web` were also preserved and
excluded from this Story.

## Changes

- Added PostgreSQL migration `V50__harden_paper_margin_policy_persistence.sql`.
- Changed `margin_rate` to unconstrained exact numeric storage with an explicit
  PostgreSQL scale check, preventing silent nine-decimal rounding.
- Added database checks for policy nonblank fields and effective/expiry ordering.
- Added an append-only PostgreSQL trigger for `margin_policy` updates/deletes.
- Kept the composite `(id, version)` identity and existing assignment foreign key.
- Aligned the JPA margin-rate mapping with exact numeric storage.
- Replaced Hibernate streaming `findFirst()` calls with bounded result-list reads;
  PostgreSQL otherwise closed the result set during assignment resolution in the
  integration lifecycle.
- Added PostgreSQL/Testcontainers evidence for reload, assignment, V1/V2
  preservation, expiry/revocation/timestamp/provenance round-trip, invalid rates,
  immutable mutation, duplicate/foreign-key-invalid assignments, and the real
  PAPER resolver producing `11.00 USD`.
- Updated fresh-install and historical migration assertions from V49 to V50.

## Documentation Reconciliation

Documentation update: completed. Story 0191 artifacts were added. No ADR,
architecture boundary, API contract, runtime configuration, or user-facing
documentation required an update because the implementation hardens the existing
ADR-083 persistence boundary without changing its semantics.

Vault Outcome: the vault was not consulted; no vault action is proposed. The
repository ADRs and current source were sufficient, and no curated vault content
was modified.

## Validation

```text
COMPILE: PASS
FOCUSED_DOMAIN_AND_REGRESSION_TESTS: PASS
POSTGRES_POLICY_INTEGRATION_TESTS: PASS (5 tests)
POSTGRES_MIGRATION_TESTS: PASS (6 tests)
TRADING_CORE_TEST: PASS
TRADING_CORE_VERIFY: PASS
RISK_DOMAIN_VERIFY: PASS (mvn used; module has no mvnw)
GIT_DIFF_CHECK: PASS
```

Regression evidence included in the focused/full Trading Core runs:

```text
STORY_0190_T1: PaperRequiredMarginClientTest, ExecutionTimeRiskRevalidationServiceTest
LIVE_MARGIN_AUTHORITY: BrokerRequiredMarginClientTest
COMPATIBILITY_REJECTION: SettlementCompatibilityPolicyTest
RISK_BOUNDARY: TradePlanRiskEvaluationServiceTest, RiskPersistenceTest
CTRADER_SAFETY: existing Trading Core safety/configuration regressions
```

The migration log reports the expected V34 legacy-provider rejection inside the
test named `rejectsUnknownLegacyProviderRowsDuringMigration`; the test suite
passes and no V34 migration was changed. Fresh installation, production Flyway
configuration, and historical upgrade paths reach V50.

## Safety

```text
V49_MODIFIED: NO
HISTORICAL_MIGRATIONS_MODIFIED: NO
PAPER_MARGIN_AUTHORITY: LOCAL_VERSIONED_POLICY
LIVE_MARGIN_AUTHORITY: PROVIDER
RISK_DOMAIN_CHANGED: NO
BROKER_SERVICE_CHANGED: NO
CASH_CFD: REJECTED
INVENTORY_CFD: REJECTED
UNKNOWN_CFD: REJECTED
SETTLEMENT_COMPATIBILITY_CHANGED: NO
CTRADER_SAFETY_EXECUTION_ENABLED: false
REAL_CTRADER_ORDERS: 0
REAL_CTRADER_CLOSES: 0
PROVIDER_MUTATIONS: 0
```
