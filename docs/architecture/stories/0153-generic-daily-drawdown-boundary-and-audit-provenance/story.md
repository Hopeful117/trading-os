# Story 0153 - Generic Daily Drawdown Boundary and Audit Provenance

## Metadata

- **ID:** `0153`
- **Status:** IMPLEMENTED - AWAITING HUMAN REVIEW
- **Related ADRs:** `ADR-031`, `ADR-075`
- **Related Stories:** `0152`
- **Implementation Report:** `implementation-report.md`
- **Code Review Report:** `code-review-report.md`

## Goal

Extend the provider-neutral Risk Domain so a daily drawdown policy can explicitly
represent its breach boundary while preserving deterministic evaluation, legacy
profile behavior, and complete market-context provenance.

## Scope

- Support inclusive and strict daily drawdown boundaries.
- Preserve inclusive behavior for existing profiles through a database default and
  compatibility mapping.
- Persist and load the boundary as part of the generic risk profile rule.
- Validate boundary vocabulary and reject strict boundaries on non-daily rules.
- Preserve the initial market valuation payload separately from the daily baseline
  payload in risk evaluation audit components.
- Add migration, persistence, validator, domain, and integration test coverage.

## Out Of Scope

- FTMO-specific classes, constants, or runtime branches.
- Provider-specific Challenge or broker behavior.
- New drawdown formulas or duplicated Risk calculations.
- cTrader integration, execution, synchronization, or runtime validation.
- Changing the existing semantics of total drawdown or other rules.

## Acceptance Criteria

- Daily drawdown supports explicit `INCLUSIVE` and `STRICT` boundaries.
- Inclusive remains the default for existing and newly migrated profiles.
- Strict daily drawdown allows equality at the configured limit and rejects values
  beyond it.
- Non-daily rules cannot use the strict boundary.
- Invalid or unsupported boundary values fail profile validation closed.
- Migration V36 adds the boundary column, default, and database constraint.
- Persisted strict boundaries round-trip through Trading Core persistence.
- Initial market valuation provenance remains distinct from daily baseline
  provenance in both pre-trade and execution-time risk evaluation.
- Existing risk, persistence, migration, and execution behavior remains covered by
  the affected test suites.
- No provider-specific Challenge or broker branch is introduced.

## Validation

- `mvn -q test` in `risk-domain`.
- Targeted Trading Core tests for profile validation, persistence, risk evaluation,
  execution-time revalidation, and PostgreSQL migrations.
- `git diff --check`.

## Human Boundary

This artifact documents the already implemented work for delivery review. Human
review is required before branch creation, commit, push, pull request creation, or
merge.
