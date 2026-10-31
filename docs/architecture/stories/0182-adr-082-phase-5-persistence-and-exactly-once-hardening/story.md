# Story 0182 - ADR-082 Phase 5 Persistence and Exactly-Once Hardening

## Metadata

**ID:** `0182`
**Title:** ADR-082 Phase 5 Persistence and Exactly-Once Hardening
**Status:** Draft
**Related ADR:** `ADR-082`
**Related Stories:** `0181`

---

## Goal

Close the residual evidence gaps from Story 0181 / PR #227 by proving CFD
restart reconstruction and durable exactly-once PAPER settlement at the real
PostgreSQL persistence boundary.

This Story hardens and validates ADR-082 Phase 5. It does not start Phase 6.

## Context

Story 0181 explicitly left persistence-context reconstruction and durable
concurrent replay unproven. The current repository contains persisted CFD
contract fields, persisted account state, a durable execution provenance store,
and a unique `(broker_order_id, fill_id)` database invariant. These claims must
be tested through commit, clear/reload, replay, and independent transactions.

## Problem

Focused in-memory tests cannot prove that persisted CFD financial fields remain
sufficient after a real persistence-context clear, nor that concurrent replay
cannot apply account economics twice.

## Scope

- Add PostgreSQL-backed restart reconstruction tests for long and short CFD MTM.
- Add a cross-currency reload test when supported by the existing test seams.
- Add closed-exposure reload and exclusion-from-MTM coverage.
- Add durable replay coverage using the production provenance implementation.
- Add separate-transaction concurrent replay coverage with deterministic race coordination.
- Add rollback atomicity coverage across trade, account, and provenance state.
- Inspect and verify database uniqueness, versioning, transaction boundaries, and locking.
- Re-run Story 0181 and Trading Core regression suites.
- Record the result without enabling CASH + CFD, LIVE CFD, or Risk Domain work.

## Out of Scope

- LIVE CFD reconciliation or provider financial authority.
- CFD risk integration, margin, notional, stop-risk, or equity risk changes.
- Partial exits, reversals, liquidation, or compatibility-matrix changes.
- New provider integrations or broker mutations.
- Accounting-model redesign unless a test exposes a concrete correctness defect.

## Acceptance Criteria

- [ ] Long CFD exposure reconstructs after PostgreSQL flush, clear, reload, and values `+1 USD` at mark `1.10100`.
- [ ] Short CFD exposure reconstructs after PostgreSQL flush, clear, reload, and values `+1 USD` at mark `1.09900`.
- [ ] Cross-currency reconstruction preserves and applies directed quote-to-reporting conversion when practical.
- [ ] A reloaded closed CFD remains closed, is excluded from unrealized MTM, and preserves realized PnL once.
- [ ] Replaying the same settled broker order/fill after commit and reload produces no second economic effect.
- [ ] Two independent transactions and persistence contexts racing the same settlement produce exactly one durable economic effect.
- [ ] The database settlement identity uniqueness invariant is exercised by the integration tests.
- [ ] Settlement rollback leaves no half-closed trade, orphan realized PnL, orphan provenance, or orphan cash mutation.
- [ ] `INVENTORY + CFD` and `CASH + CFD` remain rejected.
- [ ] PAPER execution produces zero broker/provider mutations and cTrader safety remains disabled.
- [ ] Relevant regression tests, the full Trading Core suite, and `git diff --check` pass.
- [ ] No production code changes are made unless a test reveals a concrete correctness defect.

## Constraints

- ADR-082 remains authoritative for CFD financial semantics and Phase boundaries.
- Trading Core remains PAPER authority; Market Data supplies marks and FX facts.
- Missing or invalid financial facts fail closed.
- Use the production `TradeExecutionProvenanceStore` persistence implementation.
- Use separate transactions and persistence contexts for concurrency; do not fake a race with sequential calls.
- Do not use sleeps as the primary race synchronization mechanism when barriers are viable.
- Do not commit, push, merge, or modify unrelated user changes.

## Relevant ADRs

- `docs/architecture/adr/ADR-082-cfd-financial-model-and-mark-to-market-accounting.md`

## Relevant Modules

- `trading-core`

## Validation

- PostgreSQL Testcontainers integration tests for restart, replay, concurrency, and rollback.
- Story 0181 focused CFD and settlement regression tests.
- CFD calculator, valuation, persistence, provenance, SPOT, and LINEAR_PERPETUAL tests.
- `./mvnw -q test` from `trading-core`.
- `git diff --check`.
- Independent review focused on transaction boundaries, race safety, uniqueness, JPA reload, and rollback.

## Definition of Done

- [ ] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [ ] Implementation completed
- [ ] Relevant validation executed
- [ ] Diff reviewed
- [ ] Code Review completed
- [ ] Engineering Report completed
- [ ] Human commit created
