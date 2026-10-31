# Repository Analysis - Story 0182

## Repository State

- `REMOTE_MAIN`: `6172b27320d9e32a7ca89df66b5530ff041997a6`
- `LOCAL_BASE`: `6172b27320d9e32a7ca89df66b5530ff041997a6`
- `LOCAL_HEAD`: `6172b27320d9e32a7ca89df66b5530ff041997a6` before Story artifacts
- `BRANCH`: `story/0182-adr-082-phase-5-persistence-hardening`
- `WORKTREE_STATE`: clean before Story artifacts
- Previous merge: PR #227, `6172b27320d9e32a7ca89df66b5530ff041997a6`

The repository was fetched from `origin` before inspection. The observed
remote main matches the previously documented merge, but the SHA was verified
from the current remote rather than trusted.

## Context Sources

Story 0181 and its implementation report/code review explicitly identify
restart-context reconstruction and durable concurrent replay as residual gaps.
ADR-082 requires persisted signed CFD exposure, deterministic MTM, atomic full
exit settlement, exactly-once realized PnL, explicit FX conversion, and a
closed compatibility matrix.

DevLog context was requested for this Story but the DevLog MCP returned
`Authentication is required`. No DevLog evidence was used as a substitute for
repository inspection.

## Current Implementation

`PaperCfdSettlementService` is a Spring `@Transactional` PAPER-only boundary.
It loads the account, checks provenance by `(broker order, fill)`, creates or
closes CFD trades, updates realized balance/equity, flushes the account, and
then writes provenance. `CfdValuationService` reconstructs the financial
contract exclusively from persisted `Trade` fields and obtains read-only mark
and directed FX facts.

`ExecutionFinalizationStep` routes identified PAPER CFD instruments to the
dedicated service without changing `SettlementCompatibilityPolicy`.

## Persistence Evidence

- `Trade` persists signed quantity, entry/current/exit prices, product type,
  currencies, quantity unit, multiplier, precision/constraints, and contract
  provenance. It has a JPA `@Version` field.
- `Account` persists settlement model, balances, equity, realized balance,
  valuation status/timestamps, and trades. It has a JPA `@Version` field.
- `AccountRepository` and `TradeRepository` use standard Spring Data JPA
  loading; no explicit pessimistic lock is present for this settlement path.
- `JpaTradeExecutionProvenanceStore` is the production persistence adapter and
  uses `saveAndFlush`.
- Migration `V39__persist_paper_trade_execution_provenance.sql` defines unique
  `(broker_order_id, fill_id)` on both broker fills and settlement provenance,
  with a foreign key to the persisted fill identity.
- Testcontainers PostgreSQL infrastructure already exists in Trading Core,
  including persistence and rollback integration tests. Existing tests use
  `EntityManager.flush()` and `EntityManager.clear()` for reload evidence.

## Correctness Risk to Prove

The service performs a read-before-write provenance check and does not use an
explicit pessimistic account lock. Optimistic version fields may prevent both
account mutations from committing, while the unique provenance constraint
protects the settlement identity. The actual concurrent behavior, exception
handling, and transaction rollback must be demonstrated with two independent
transactions and persistence contexts. A read-before-write check alone is not
accepted as proof.

The test must also verify that an insert conflict or optimistic conflict cannot
leave an economic mutation committed without its provenance, and that a
duplicate replay after a committed settlement returns without changing cash,
realized PnL, or trade state.

## Required Test Coverage

1. Persist a long CFD, flush/clear/reload it, and value it at `1.10100` for `+1 USD`.
2. Persist a short CFD, flush/clear/reload it, and value it at `1.09900` for `+1 USD`.
3. Preserve directed USD-to-EUR conversion after reload when the current test seam supports it.
4. Close and reload a CFD; prove it remains closed and excluded from later unrealized MTM.
5. Commit an exit with the production provenance adapter, clear/reload, replay the same identity, and count one provenance/economic effect.
6. Coordinate two separate transactions around provenance lookup and mutation, then assert one final closed trade, one realized application, one cash mutation, and one provenance row.
7. Force a realistic settlement failure and verify atomic rollback across trade, account, and provenance persistence.
8. Re-run the existing compatibility, calculator, valuation, persistence, provenance, SPOT, and LINEAR_PERPETUAL regressions plus the full Trading Core suite.

## Scope and Stop Conditions

No evidence currently requires a financial-model redesign, a compatibility
change, LIVE authority work, or Risk Domain changes. If PostgreSQL integration
tests show that concurrent attempts can both mutate account economics, that the
unique identity cannot represent the settlement, or that transaction boundaries
allow divergence, implementation must stop and report the defect rather than
weakening the tests or adding an unapproved accounting architecture.

## Recommendation

Proceed to Implementation Planning only after explicit human approval of this
Repository Analysis. The plan should remain test-first and production-code
neutral unless an integration test demonstrates a concrete correctness defect.
