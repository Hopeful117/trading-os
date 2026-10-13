# Story 0093 - PostgreSQL Fresh-Install Migration Integrity

## Metadata

- **ID:** `0093`
- **Status:** `IMPLEMENTED - AWAITING HUMAN REVIEW`
- **Follows:** `0089`, `0090`, `0091`, `0092`

## Goal

Guarantee that Trading Core can migrate an empty PostgreSQL database through the
complete current Flyway history and boot successfully, while preserving a safe
upgrade path for databases that already applied published migrations.

## Scope

- Reproduce and document the PostgreSQL migration-ordering defect.
- Repair the migration topology without removing Risk immutability.
- Add a durable fresh-install PostgreSQL regression test using the production
  Flyway locations.
- Add representative schema, PostgreSQL protection, Challenge, and benchmark
  persistence assertions.
- Add representative historical-upgrade coverage where the repository can prove
  a safe historical boundary.
- Document CI/Testcontainers execution requirements and remaining limitations.

## Constraints

- Do not change Challenge, Risk, Kraken, trading, settlement, or frontend
  business semantics.
- Do not rewrite or renumber published Flyway migrations without proving
  compatibility with existing `flyway_schema_history` records.
- Preserve PostgreSQL Risk artifact immutability.
- Preserve the known 00:30 UTC Risk Day limitation.

## Mandatory Stop Conditions

- Stop if the defect no longer reproduces.
- Stop if no safe compatibility path exists for an existing migration checksum.
- Stop before destructive schema changes or ambiguous historical repair.
