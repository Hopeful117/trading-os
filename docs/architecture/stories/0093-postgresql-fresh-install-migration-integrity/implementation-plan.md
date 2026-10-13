# Implementation Plan - Story 0093

## 1. Preserve the Published V3 Checksum

- Restore `postgresql/V3__enforce_immutable_risk_artifacts.sql` to the
  published content that existed before Story 0034.
- Keep the shared immutability function and all existing Risk artifact triggers
  unchanged.
- Remove only the trigger declaration that references `risk_evaluation_t1`,
  because that table did not exist at V3's historical execution point.
- Verify the restored file matches the historical Git version and document the
  checksum compatibility rationale.

This is not a new semantic migration. It restores the already-published V3
artifact so standard existing databases retain their historical checksum.

## 2. Add a Version-Safe PostgreSQL Repair Migration

- Add `postgresql/V31__enforce_immutable_risk_evaluation_t1.sql`.
- Create only the `immutable_risk_evaluation_t1` trigger using the existing
  `reject_trading_core_immutable_mutation()` function.
- Make the migration execute after `common/V30`, which creates the latest
  Challenge and Kraken benchmark data.
- Do not modify Risk behavior, Challenge semantics, or the shared trigger
  function.

The migration remains additive for a database that has completed the historical
V1-V30 path.

## 3. Add PostgreSQL Test Infrastructure

- Add the smallest repository-compatible Testcontainers PostgreSQL dependency
  to `trading-core` test scope.
- Use PostgreSQL 16, matching `docker-compose.yml`.
- Keep the test opt-in or CI-compatible according to the existing Maven test
  conventions; document Docker/Testcontainers requirements and the exact
  command.
- Use the production Flyway locations:
  `classpath:db/migration/common,classpath:db/migration/{vendor}`.

## 4. Fresh-Install Regression Test

Create a durable test using a genuinely empty PostgreSQL container that:

- runs Flyway from the configured locations through the latest migration;
- verifies Flyway reaches V31 without failure;
- verifies representative early schema objects such as `users`, `accounts`,
  and `execution_attempt`;
- verifies `risk_evaluation_t1` and its indexes;
- verifies Challenge tables and the Kraken Advanced 50k benchmark seed;
- verifies the shared immutability function and all Risk immutability triggers,
  including `immutable_risk_evaluation_t1`;
- verifies an attempted update/delete of an inserted immutable Risk artifact is
  rejected by PostgreSQL.

The test must discover future migrations automatically through the configured
locations rather than maintaining a manual migration list.

## 5. Historical Upgrade Regression Test

Use the same PostgreSQL engine to create a database at the historical V30
boundary using an independent published-V3 test fixture, then run the current
migration set to V31. This validates that the current V3 checksum matches the
supported published artifact rather than only validating the current file
against itself.

Assertions:

- V1-V30 remain recorded without checksum validation failure;
- V31 executes exactly once;
- a repeated current migration call does not add history rows;
- `risk_evaluation_t1` and its trigger exist after upgrade;
- existing Risk immutability triggers remain present;
- existing benchmark rows remain readable;
- no duplicate object or data-loss condition occurs.

The test must not start from the latest schema or manually apply only selected
SQL objects. It must exercise Flyway's versioned migration behavior at the
historical V30-to-current boundary.

## 6. Application Bootstrap Validation

- Reuse the PostgreSQL Flyway migration result to validate the Spring context
  where practical, with external service discovery disabled.
- If full application bootstrap is too coupled to unavailable services, retain
  the Flyway plus representative schema assertions and document the exact
  limitation rather than weakening the migration test.

## 7. Documentation Reconciliation

Update Story 0093 artifacts with:

- exact pre-fix failure and migration order;
- historical checksum evidence;
- selected repair and rejected alternatives;
- fresh-install and upgrade test commands/results;
- CI and Docker/Testcontainers execution path;
- explicit confirmation that the Risk Day 00:30 UTC limitation remains
  unchanged.

## 8. Validation

Run, as applicable:

```text
mvn -q -Dtest=<targeted PostgreSQL migration tests> test
mvn -q test
mvn -q verify
git diff --check
```

If CI configuration does not execute the new PostgreSQL test, make only the
minimal Story-specific adjustment required to run it regularly. Do not redesign
the pipeline.

## Rejected Alternatives

- Renaming V3 to V31: unsafe because existing histories record V3 and Flyway
  would report a missing migration; it also leaves the historical trigger
  semantics ambiguous.
- Leaving the current V3 and adding a later repair: insufficient because fresh
  PostgreSQL fails before the later migration can run.
- Ignoring Flyway checksum/missing-migration validation: unsafe because it hides
  migration history divergence.
- Removing all immutability triggers: forbidden because it weakens Risk audit
  guarantees.
- Adding a second table creation or manually applying V9 SQL from V31: unsafe
  because it duplicates the common migration's schema responsibility.

## Expected Files

- `trading-core/src/main/resources/db/migration/postgresql/V3__enforce_immutable_risk_artifacts.sql`
- `trading-core/src/main/resources/db/migration/postgresql/V31__enforce_immutable_risk_evaluation_t1.sql`
- `trading-core/src/test/java/com/hope/trading/trading_core/migration/PostgresFreshInstallMigrationTest.java`
- `trading-core/src/test/resources/db/migration/postgresql-published/V3__enforce_immutable_risk_artifacts.sql`
- `trading-core/src/test/resources/db/migration/postgresql-published/V15__position_close_command_partial_index.sql`
- `trading-core/pom.xml` if Testcontainers is not already available
- Story 0093 implementation and engineering reports
