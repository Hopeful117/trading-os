# Engineering Report - Story 0093

## Result

The PostgreSQL fresh-install migration defect is repaired without removing Risk
immutability or changing Challenge/Risk business semantics.

The effective migration path is now:

```text
V1 ... V2
PostgreSQL V3: historical Risk immutability
V4 ... V30: current common schema and benchmark
PostgreSQL V31: risk_evaluation_t1 immutability
```

## Acceptance Review

- AC1 fresh PostgreSQL migration: passed through V31.
- AC2 defect reproduction: reproduced on `main` at `e13102f`; V3 failed on the
  absent `risk_evaluation_t1` relation.
- AC3 published history safety: V3 was restored to its pre-Story-0034
  published content; the repair is additive at V31.
- AC4 existing upgrade: a database created from an independent published-V3
  fixture upgraded from V30 to V31 successfully, including current checksum
  validation and repeated migration idempotence.
- AC5 Risk immutability: all historical triggers remain and the V31 trigger
  rejects T1 UPDATE/DELETE.
- AC6 Challenge and benchmark persistence: V23/V28/V29/V30 objects and Kraken
  Advanced 50k seed were verified.
- AC7 durable regression test: `PostgresFreshInstallMigrationTest` added.
- AC8 production-like locations: test uses common and PostgreSQL Flyway
  locations and discovers all available migrations automatically.
- AC9 CI path: the backend quality job explicitly verifies Docker before
  running `clean verify` for `trading-core`.
- AC10 scope: no Challenge, Risk rule, Kraken rule, trading, settlement, or
  frontend semantics were changed.

## Existing History Compatibility

The independent test fixture contains the published V3 artifact without the T1
trigger. The V30 upgrade test records V1-V30 using that fixture, then validates
the current migration set and proves the current V3 checksum is accepted. This
supports databases that applied the published pre-Story-0034 V3 history.

Databases that applied the later broken V3 variant before this repair are not
silently claimed compatible: they require an explicit operational Flyway
checksum repair procedure and separate production evidence. This Story does
not modify those historical records.

## Residual Risks

- The complete Spring bootstrap with Challenge monitoring enabled logs a
  pre-existing PostgreSQL parameter-type error from the scheduled active
  Challenge query. The application still starts; this unrelated runtime defect
  is outside Story 0093 scope and was not modified.
- The known Kraken Risk Day limitation remains unchanged: the daily baseline is
  the first authoritative observation within the Risk Day, not guaranteed
  reconstruction of the exact 00:30 UTC provider boundary.
- Testcontainers tests require Docker.
- The PostgreSQL migrations intentionally rely on Flyway's configured
  `schemas`/`defaultSchema` connection search path. The regression test uses a
  non-public generated schema and asserts that objects resolve in that schema;
  deployments changing this Flyway contract require separate validation.

## Human Review

No Git commit, push, merge, or branch integration was performed. The working
tree remains available for human diff review.
