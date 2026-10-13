# Story

## Metadata

**ID:** `0095`

**Title:** PostgreSQL-Safe Active Challenge Monitoring Pagination

**Status:** IMPLEMENTATION COMPLETE - AWAITING HUMAN REVIEW

---

## Goal

Make Active Challenge monitoring discover and reevaluate active PAPER
Challenges reliably on PostgreSQL while preserving Story 0090 pagination,
locking, failure-isolation, and monitoring semantics.

---

## Context

The resumed PAPER Challenge validation after Story 0094 reached an ACTIVE
Kraken Prop Advanced 50k Challenge through the public Gateway. The scheduler
then failed before its first evaluation. The runtime evidence is recorded in:

`docs/architecture/stories/0093-postgresql-fresh-install-migration-integrity/e2e-validation-continuation-report.md`

The tested environment used PostgreSQL 16.15, Flyway schema V31, Java 21.0.12,
and Trading Core from `main` commit `f0694da0`.

Story 0090 introduced active Challenge discovery with bounded keyset-style
pagination, stable ordering, PAPER filtering, per-Challenge failure
isolation, identifier-based locked reload, `REQUIRES_NEW` reevaluation, and
cycle statistics. Those guarantees remain authoritative.

---

## Problem

Every active monitoring cycle fails on PostgreSQL with:

```text
SQLState 42P18
ERROR: could not determine data type of parameter $2
```

The generated SQL contains an untyped nullable cursor predicate equivalent to:

```sql
(:afterStartedAt is null or ...)
```

The first monitoring page passes a null cursor, so PostgreSQL cannot infer the
parameter type. The scheduler fails before discovering the ACTIVE Challenge,
leaving monitoring-derived progress fields such as `evaluatedAt`, daily
reference balance, and drawdown values unset.

The exact repository/query behavior must be reproduced and verified before the
implementation approach is selected.

---

## Scope

- Investigate `ChallengeInstanceRepository.findNextByStatus` and all callers.
- Reproduce the failure using the actual repository query against PostgreSQL.
- Fix first-page and continuation pagination without unbounded `findAll()`
  behavior.
- Preserve deterministic ordering by `startedAt` and Challenge ID.
- Preserve same-`startedAt` tie-breaking and multi-page continuation.
- Preserve ACTIVE-only and PAPER-only monitoring semantics.
- Preserve terminal-state exclusion, identifier-based locking, transaction
  boundaries, per-Challenge failure isolation, and cycle statistics.
- Add PostgreSQL-backed repository regression coverage.
- Add monitoring-service integration coverage against PostgreSQL.
- Cover empty state, first page, continuation, same-start timestamp, and
  multiple-page discovery.
- Validate the real scheduler against a fresh PostgreSQL-backed environment.
- Confirm Challenge progress fields are populated after monitoring.
- Document the root cause, selected query design, invariants, and validation.

---

## Out of Scope

- Challenge definition or progression changes.
- Risk rules, Risk hierarchy, Risk Day semantics, or 00:30 UTC fidelity.
- Account valuation, settlement, TradePlan, execution, or Market Data changes.
- Changes to the Story 0090 scheduler architecture or locking model.
- PostgreSQL-specific logic leaking into the Challenge domain.
- Opportunistic indexes or schema changes without evidence they are required.
- Flyway migrations unless investigation proves a schema/index defect.
- Continuing the broader PAPER E2E into TradePlan or execution.
- Creating or implementing a subsequent Story.

---

## Acceptance Criteria

- [ ] The pre-fix PostgreSQL `42P18` failure is reproduced and documented.
- [ ] The exact generated query and nullable parameter typing cause are
      documented.
- [ ] Empty ACTIVE Challenge discovery completes without a PostgreSQL error.
- [ ] First-page ACTIVE Challenge discovery works on PostgreSQL.
- [ ] Cursor-present continuation works with strict keyset semantics.
- [ ] Challenges sharing `startedAt` are ordered and paged deterministically by
      Challenge ID.
- [ ] Multiple pages process every expected Challenge exactly once.
- [ ] Terminal Challenges are not reevaluated.
- [ ] LIVE Challenges remain excluded from the intended PAPER monitoring flow.
- [ ] The actual `ActiveChallengeMonitoringService` succeeds against
      PostgreSQL.
- [ ] A real scheduler cycle discovers and reevaluates an ACTIVE PAPER
      Challenge without SQLState `42P18`.
- [ ] Initial Challenge monitoring/progress fields become populated according
      to current domain semantics.
- [ ] Existing Story 0090 concurrency and locking tests remain passing.
- [ ] No Challenge, Risk, settlement, valuation, TradePlan, execution, or
      provider-fidelity business semantics change.
- [ ] The fresh runtime acceptance test is executed through supported public
      APIs and the Challenge progress is retrieved through Gateway.

---

## Constraints

- Preserve the Trading Core ownership of Challenge monitoring.
- Preserve bounded/keyset pagination; do not replace it with unbounded loading.
- Prefer structurally typed, provider-neutral repository queries over
  PostgreSQL-specific casts when practical.
- Do not weaken locking, transaction, failure-isolation, or PAPER filtering.
- Do not solve the known Risk Day reset-boundary limitation.
- Do not create a schema migration without first proving it is necessary.
- Do not modify unrelated product modules.
- Do not implement Story 0095's successor.
- Do not commit, push, or merge automatically.

---

## Relevant ADRs

None identified at Story creation. Verify relevant accepted ADRs during
Repository Analysis before implementation.

---

## Relevant Modules

- `trading-core`

---

## Validation

- Reproduce the pre-fix repository failure against PostgreSQL.
- Add and run PostgreSQL Testcontainers repository tests.
- Add and run monitoring-service integration tests for empty, active, and
  multi-page cases.
- Run Story 0090 active monitoring and concurrency tests.
- Run `mvn verify` from the appropriate Trading Core/module context.
- Run Risk Domain validation if repository analysis shows it is affected.
- Run `git diff --check`.
- Execute a fresh runtime acceptance test with a real scheduler and retrieve
  progress through Gateway.
- Do not continue the full PAPER E2E beyond Story 0095 acceptance.

---

## Definition of Done

- [ ] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [ ] Implementation completed
- [ ] Relevant validation executed
- [ ] Runtime scheduler acceptance completed
- [ ] Diff reviewed in IntelliJ
- [ ] Code Review approved
- [ ] Engineering Report completed
- [ ] Human commit created
