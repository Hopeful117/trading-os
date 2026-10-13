# Code Review

## Review Result

The review findings were addressed in the working tree. The implementation
remains within Story 0089 scope and uses existing generic Risk and Challenge
persistence/runtime behavior.

## Reviewed Areas

- The benchmark is represented by immutable, versioned catalog rows rather than Kraken-specific code paths.
- Daily drawdown and maximum total drawdown both use the generic `0.03` blocking rule semantics.
- The benchmark uses EQUITY progression and `LINEAR_PERPETUAL` product compatibility.
- Official-source provenance and explicitly unmodeled commercial behavior are persisted.
- The migration is repeatable only through immutable identity/version collision failure and handles the existing H2/PostgreSQL constraint-name difference.
- Existing persistence tests remain valid when the database contains the new catalog seed.
- Stale/unavailable equity and UTC/00:30 risk-day rollover are covered by the benchmark test.
- The generic Risk integration suite covers the exact 3% inclusive daily and static total boundaries for the 50k reference.

## Residual Risk

- PostgreSQL 16 validation passed for the V25-to-V30 upgrade path and for the common migration set on a fresh database. A full fresh production-path migration remains blocked by the pre-existing V3-before-V9 ordering defect.
- Kraken fee and funding behavior remain intentionally out of scope and are not reflected in Risk calculations.
- Provider source rules may change; the catalog requires a new versioned definition and policy when the benchmark changes.
