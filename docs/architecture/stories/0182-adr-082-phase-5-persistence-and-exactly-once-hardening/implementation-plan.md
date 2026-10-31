# Implementation Plan - Story 0182

## Objective

Add durable PostgreSQL evidence for ADR-082 Phase 5 restart reconstruction,
replay idempotency, concurrent settlement, and rollback atomicity without
changing CFD accounting semantics or compatibility policy.

## Phase 1 - Establish Test Fixture

1. Reuse the existing `@SpringBootTest`, PostgreSQL Testcontainers, Flyway,
   `JdbcTemplate`, `EntityManager`, and `TransactionTemplate` conventions in
   `trading-core`.
2. Build deterministic PAPER cash-account and CFD market fixtures with complete
   provider-neutral contract metadata.
3. Keep market valuation facts read-only and deterministic; do not invoke a
   broker client or provider mutation path.
4. Use explicit cleanup or isolated test data consistent with current
   PostgreSQL integration tests.

## Phase 2 - Restart Reconstruction Tests

1. Persist a long CFD exposure with signed quantity `+1000`, entry `1.10000`,
   multiplier `1`, and USD quote/reporting currency.
2. Flush and clear the `EntityManager`, reload the `Account` and its `Trade`
   through repositories, and assert the reloaded object is not the original
   managed instance.
3. Supply a valid read-only mark of `1.10100` and assert gross unrealized PnL
   is `+1 USD`.
4. Repeat for signed quantity `-1000` and mark `1.09900`.
5. Add the directed USD-to-EUR conversion case if the current valuation test
   seam can provide complete conversion facts without production changes.
6. Close a persisted exposure, flush/clear/reload it, assert `CLOSED`, assert
   realized PnL remains persisted once, and prove it is excluded from later
   unrealized MTM.

## Phase 3 - Durable Replay and Rollback

1. Exercise `PaperCfdSettlementService` through Spring with the production
   `JpaTradeExecutionProvenanceStore` bean, not a mock provenance store.
2. Commit a full CFD exit, clear the persistence context, reload the account,
   and replay the identical broker order/fill identity in a new transaction.
3. Assert unchanged cash/equity/realized balance, a closed trade, and exactly
   one provenance row for `(broker_order_id, fill_id)`.
4. Exercise the real unique constraint where possible and assert a duplicate
   provenance attempt cannot leave a second economic mutation committed.
5. Force a realistic post-mutation failure using an existing test seam or an
   outer transaction rollback, without adding production-only failure hooks.
6. Reload after rollback and assert no half-closed trade, orphan realized PnL,
   orphan provenance, or orphan cash mutation.

## Phase 4 - Concurrent Replay

1. Use two `TransactionTemplate` executions backed by independent threads,
   transactions, and persistence contexts.
2. Coordinate both attempts with deterministic barriers/latches around the
   settlement race, specifically after independent provenance checks and
   before the conflicting persistence/commit boundary where the existing test
   seams allow it.
3. Submit the same broker order/fill identity to both attempts.
4. Treat one successful settlement plus a duplicate/conflict/optimistic-lock
   outcome as acceptable only if the final database state has exactly one
   economic effect.
5. Reload in a separate transaction and count final trade state, realized PnL
   application, cash mutation, and provenance rows.
6. If both attempts can commit economic changes, stop and report
   `DURABLE_EXACTLY_ONCE_GAP = YES` rather than weakening the test.

## Phase 5 - Regression Validation

Run, adapting names only to classes present in the repository:

- new Story 0182 PostgreSQL integration tests;
- `PaperCfdSettlementServiceTest`;
- `ExecutionFinalizationStepRegressionTest`;
- `CfdValuationServiceTest`;
- `SettlementCompatibilityPolicyTest`;
- CFD financial calculator tests;
- CFD persistence/projection and provenance tests;
- PAPER SPOT settlement tests;
- PAPER LINEAR_PERPETUAL settlement tests;
- `./mvnw -q test` from `trading-core`;
- `git diff --check`.

## Production-Code Decision Rule

Do not modify production code initially. If an integration test exposes a real
reconstruction or concurrency defect, identify the smallest fix that preserves
the existing accounting model and compatibility boundary. Record root cause,
economic consequence, fix, and proof in the implementation report. Stop for
human guidance if the fix requires a major accounting redesign, CASH + CFD,
LIVE authority, or Risk Domain changes.

## Expected Deliverables

- PostgreSQL-backed integration test additions in `trading-core`.
- Production changes only if a test proves a correctness defect.
- `implementation-report.md` with the requested acceptance matrix and exact
  test results.
- `code-review.md` focused on transaction boundaries, race conditions,
  uniqueness, JPA reload behavior, and rollback semantics.

## Documentation Outcome

The Story artifacts are the canonical documentation for this hardening work.
No ADR change is expected unless implementation reveals a material conflict
with ADR-082. No Phase 6 documentation or implementation will be added.

## Validation Boundary

Implementation stops before commit, push, merge, or Phase 6 work. Human review
remains required after implementation and independent code review.
