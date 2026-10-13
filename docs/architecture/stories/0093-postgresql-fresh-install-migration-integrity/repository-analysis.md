# Repository Analysis - Story 0093

## Repository State

- Branch: `main`
- HEAD: `e13102f8c33b49762f6d698544c10c6a7123ea6b`
- HEAD subject: merge of Story 0092 Challenge dashboard workflow
- Working tree: clean before analysis artifacts
- DevLog: unavailable because the configured provider previously returned an
  exhausted-quota error; repository evidence is authoritative for current state.

## Current Migration Topology

Trading Core configures Flyway with one global version namespace:

```properties
spring.flyway.baseline-on-migrate=false
spring.flyway.locations=classpath:db/migration/common,classpath:db/migration/{vendor}
```

The production datasource is PostgreSQL. The test profile uses H2, where
`{vendor}` resolves to H2 and therefore does not execute the PostgreSQL
directory.

Current common migrations are `V1` through `V30`. PostgreSQL-specific
migrations are:

- `postgresql/V3__enforce_immutable_risk_artifacts.sql`
- `postgresql/V15__position_close_command_partial_index.sql`

Flyway validates 30 migrations and merges both locations by version. The
directories are not independent sequences. Therefore the effective early order
is:

```text
common V1
common V2
postgresql V3
common V4 ... V30
```

`common/V9__execution_time_risk_revalidation.sql` creates
`risk_evaluation_t1`. `postgresql/V3` creates the immutable trigger for that
table at line 28, before V9 has executed.

## Defect Reproduction

Reproduction was run against a genuinely empty PostgreSQL 16.15 database in a
temporary Docker container:

```text
postgres:16
database: trading_os
schema: empty
application: Trading Core Spring Boot Flyway configuration
```

Command:

```bash
timeout 45s mvn -q spring-boot:run \
  -Dspring-boot.run.arguments='--spring.datasource.url=jdbc:postgresql://localhost:55432/trading_os ...'
```

Observed Flyway order:

```text
V1 - trading core clean install baseline
V2 - trade plan risk evaluation
V3 - enforce immutable risk artifacts
```

The migration failed and rolled back:

```text
SQL State: 42P01
ERROR: relation "risk_evaluation_t1" does not exist
Location: db/migration/postgresql/V3__enforce_immutable_risk_artifacts.sql
Line: 28
Statement: CREATE TRIGGER immutable_risk_evaluation_t1 ... ON risk_evaluation_t1
```

The Trading Core application consequently failed during Flyway initialization
before JPA bootstrap completed.

## Historical Migration Safety

Story 0034 introduced `common/V9` and modified the existing PostgreSQL V3 file
to add the `risk_evaluation_t1` trigger in commit `b41a68a0`.

The parent version of V3, which is the published migration already present
before Story 0034, contains all immutable Risk triggers except the T1 trigger.
This is material evidence that V3 is an already-published migration and that
existing databases may have the original V3 checksum recorded.

The current V3 cannot have been applied through the standard fresh-install path
because it fails before V9. A database with the current modified V3 checksum
would require separate evidence; no such database is available in the
repository. Existing standard histories must be treated as containing the
pre-Story-0034 V3 checksum.

## Risk Immutability Scope

V3 protects these tables against `UPDATE` and `DELETE` through the shared
`reject_trading_core_immutable_mutation()` trigger function:

- `risk_profile`
- `risk_profile_rule`
- `risk_day_baseline`
- `risk_component_snapshot`
- `risk_context_snapshot`
- `risk_evaluation`
- `risk_evaluation_t1`

The repair must preserve all existing triggers and add the T1 protection only
after `risk_evaluation_t1` exists.

## Challenge and Benchmark Migrations

- `common/V23` creates `challenge_definition` and `challenge_instance`.
- `common/V28` adds Challenge equity progression and settlement requirements.
- `common/V29` adds instrument product type.
- `common/V30` seeds the Kraken Prop Advanced 50k benchmark and its Risk Policy.

The fresh-install regression must migrate through V30 and verify
`challenge_definition`, `challenge_instance`, the benchmark identity, and the
Risk Policy seed.

## Existing Test Coverage Gap

The current tests use H2 with the common migrations. Existing migration tests
load individual SQL resources or assert seeded data through H2-backed Spring
tests. No PostgreSQL/Testcontainers dependency or complete PostgreSQL Flyway
fresh-install test exists in `trading-core`.

The existing `docker-compose.yml` uses PostgreSQL 16 for Trading Core, so
PostgreSQL 16 is the repository convention for the regression test.

## Candidate Repair Direction

The evidence supports an additive, checksum-preserving repair relative to the
published V3:

1. Restore V3's published content without the T1 trigger, so its historical
   checksum matches existing standard installations.
2. Add a new versioned PostgreSQL migration after `common/V30` that creates the
   `risk_evaluation_t1` immutable trigger once V9 has created the table.
3. Verify fresh migration and existing migration upgrade behavior, including
   all immutable Risk triggers.

This direction still requires implementation-plan approval and explicit tests
for the historical checksum boundary before code changes are made.

## Rejected or Unsafe Alternatives

- Renaming PostgreSQL V3 to V31 is unsafe: it removes a migration already
  recorded in existing `flyway_schema_history`, creates a missing migration,
  and does not by itself preserve the old trigger set or checksum.
- Editing the current V3 while leaving its new T1 trigger in place is unsafe:
  fresh PostgreSQL still fails at V3, and existing installations may fail
  Flyway checksum validation.
- Removing Risk immutability is forbidden because it makes fresh installation
  pass by weakening a required architectural protection.
- Configuring Flyway to ignore missing migrations or checksums would hide
  history corruption rather than repair the migration topology.

## Gate Result

The defect is confirmed and its root cause matches the historical report. The
repository analysis is complete. No migration or application source file was
modified during investigation.
