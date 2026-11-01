# Implementation Plan - Story 0191

## Outcome

Harden the existing V49 PAPER `MarginPolicy` persistence boundary with the
smallest forward-only PostgreSQL change and focused integration evidence. Keep
the current local PAPER authority, T1 valuation behavior, LIVE provider path,
and compatibility rejection unchanged.

## Implementation Sequence

### 1. Confirm migration and persistence conventions

- Reconfirm `origin/main`, current branch, latest migration, and the preserved
  untracked Story directories before editing.
- Verify PostgreSQL's behavior for unconstrained numeric scale and the existing
  append-only trigger convention using the repository's Testcontainers setup.
- Do not edit V49 or any migration before V49.

### 2. Add the next forward migration

Create the next actual migration after V49, expected to be `V50`, using the
repository's PostgreSQL migration convention. The migration will:

- change `margin_rate` to an exact numeric representation that does not silently
  round input before validation;
- add checks for `margin_rate > 0`, `margin_rate <= 1`, and
  `scale(margin_rate) <= 8`;
- add required nonblank policy identity/version/provenance/source checks and
  effective/expiry ordering checks where they are not already represented by
  `NOT NULL` constraints;
- preserve the existing composite policy identity and account/policy foreign
  keys;
- add the smallest deterministic assignment constraints needed to reject
  duplicate authority at the same account/effective boundary without blocking
  legitimate historical reassignment;
- install append-only database protection for policy economics, and assignment
  history if existing repository conventions and lifecycle tests confirm that
  assignment history is never mutated in place;
- avoid rewriting existing policy economics or seeding a default.

The precision design must prove that `0.123456789` is rejected by PostgreSQL,
not rounded. If the current `NUMERIC(38,18)` coercion prevents that proof, the
column must use an unconstrained exact numeric form plus an explicit scale check;
the JPA mapping must remain compatible with that schema.

### 3. Align the persistence mapping without changing authority

- Keep `(id, version)` as the durable policy identity.
- Keep `MarginPolicy` domain validation unchanged or strengthen it only where
  required to match the database boundary.
- Align the JPA `marginRate` mapping with the final V50 numeric representation
  so Hibernate does not reintroduce silent scale conversion.
- Preserve all policy fields through `model(...)`, including source metadata,
  scope/instrument, effective/configured/expiry timestamps, reporting scale, and
  revocation state.
- Keep assignment attached to the financial `Account`; retain broker account
  ownership/routing checks as an application concern.
- Preserve explicit version creation and assignment insertion. Never update an
  existing policy row to create V2.
- Make resolver ambiguity behavior explicit and fail closed where the current
  ordered historical assignment model cannot establish exactly one applicable
  authority. Do not replace it with an unordered first-row query.

### 4. Add focused PostgreSQL integration evidence

Add a clearly named Testcontainers integration class following the existing
Trading Core PostgreSQL test pattern. The class will use real Flyway,
PostgreSQL, repositories/persistence, transactions, and the application
resolver. It will cover:

- valid policy creation, persistence, assignment, commit, `EntityManager.clear`,
  new transaction, and reload;
- exact preservation of policy ID, version, source/provenance, instrument scope,
  effective/configured/expiry timestamps, reporting scale, revocation state, and
  margin rate;
- financial `Account` assignment and rejection of invalid account/policy
  references;
- real resolver usage after reconstruction, followed by the explicit EUR/USD
  fixture (`1000 * 1.10000 * 1 * 0.01 = 11.00 USD`);
- V1 persistence followed by independent V2 creation, proof that V1 remains
  `0.01`, V2 is `0.02`, and explicit reassignment resolves V2 without deleting or
  mutating V1;
- direct PostgreSQL rejection of zero, negative, greater-than-one, and
  nine-decimal rates, with eight-decimal acceptance;
- direct update/delete rejection for immutable policy economics;
- duplicate/ambiguous assignment rejection according to the confirmed schema
  semantics;
- timestamp and provenance round-trip behavior without fallback substitution.

The test will call the real PAPER margin resolution path where the existing
application wiring permits it. If the full resolver requires unrelated market
fixtures, the test will keep the database lifecycle proof direct and add a
focused resolver fixture through existing test seams rather than changing
production boundaries.

### 5. Extend migration tests

Update `PostgresFreshInstallMigrationTest` and related historical paths to:

- expect V50 as the latest successful migration;
- assert the MarginPolicy constraints, indexes, foreign keys, and immutability
  trigger are present;
- prove fresh installation and historical upgrade reach the same final schema;
- classify the known V34 legacy-provider failure as pre-existing, regression, or
  not reproduced without modifying V34.

### 6. Preserve regression boundaries

Run or extend focused regressions proving:

- Story 0190 T0/T1 tests remain green and T1 still uses fresh valuation
  `sourcePrice` and `FreshnessRequirement.t1()`;
- `PAPER_MARGIN_AUTHORITY` remains the local versioned policy;
- LIVE margin remains provider-authoritative;
- Risk Domain, Broker Service, settlement compatibility, and cTrader safety
  behavior remain unchanged;
- `CASH + CFD`, `INVENTORY + CFD`, and `UNKNOWN + CFD` remain rejected.

## Expected Files

Likely files, subject to implementation evidence:

```text
trading-core/src/main/resources/db/migration/postgresql/V50__harden_paper_margin_policy_persistence.sql
trading-core/src/main/java/com/hope/trading/trading_core/risk/margin/MarginPolicyPersistence.java
trading-core/src/main/java/com/hope/trading/trading_core/risk/margin/MarginPolicy.java
trading-core/src/test/java/com/hope/trading/trading_core/risk/margin/MarginPolicyPostgresIntegrationTest.java
trading-core/src/test/java/com/hope/trading/trading_core/migration/PostgresFreshInstallMigrationTest.java
docs/architecture/stories/0191-adr-083-margin-policy-persistence-hardening/implementation-report.md
docs/architecture/stories/0191-adr-083-margin-policy-persistence-hardening/code-review.md
```

The migration location/name will be adjusted only if the current Flyway
configuration demonstrates a different repository convention. No unrelated
Story directories may be staged or modified.

## Validation Plan

Run focused checks first:

```text
MarginPolicy domain tests
MarginPolicy persistence/resolver tests
MarginPolicyPostgresIntegrationTest
PostgresFreshInstallMigrationTest
PaperRequiredMarginClientTest
PaperMarginCalculatorTest
ExecutionTimeRiskRevalidationServiceTest
SettlementCompatibilityPolicyTest
```

Then run:

```bash
cd trading-core
./mvnw -q test
./mvnw -q verify
```

Run Risk Domain verification according to repository convention, even though no
Risk Domain source is expected to change, and run `git diff --check`.

Every result will be reported as `PASS`, `FAIL`, `TIMEOUT`, or `NOT_RUN`. The
known V34 migration case will be reported separately from new V50 behavior.

## Safety and Non-Goals

```text
V49_MODIFIED = NO
CASH_PLUS_CFD_ENABLED = NO
RISK_DOMAIN_CHANGED = NO
BROKER_SERVICE_CHANGED = NO
LIVE_MARGIN_AUTHORITY_CHANGED = NO
PAPER_PROVIDER_PREVIEW_USED = NO
CTRADER_SAFETY_EXECUTION_ENABLED = false
REAL_CTRADER_ORDERS = 0
REAL_CTRADER_CLOSES = 0
PROVIDER_MUTATIONS = 0
```

This Story proves persistence readiness for the next deterministic full PAPER
CFD lifecycle Story. It does not implement the full settlement/MTM lifecycle or
authorize `CASH + CFD`.

## Approval Boundary

This plan requires explicit human approval before implementation begins.
