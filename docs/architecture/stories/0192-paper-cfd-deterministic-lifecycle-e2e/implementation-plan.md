# Implementation Plan - Story 0192

> **Superseded scope note:** This plan originally targeted a positive PAPER CFD
> lifecycle. That scope is blocked by ADR-051/ADR-083. The current Story only
> implements preparatory T0/T1 and fail-closed compatibility evidence. The
> positive phases below are historical and deferred until Phase 8 authorization.

## Objective

Add focused PostgreSQL/Testcontainers evidence for the complete deterministic
PAPER CFD lifecycle, using the existing production T0/T1, human authorization,
execution, finalization, settlement, valuation, and provenance boundaries. Do
not enable `CASH + CFD` or redesign execution/accounting.

## Phase 1 - Confirm the Integration Seam

1. Reconfirm the Story branch, baseline, preserved untracked Story directories,
   and current migration state before editing.
2. Reuse existing `@SpringBootTest`, PostgreSQL Testcontainers, Flyway,
   `JdbcTemplate`, `EntityManager`, and `TransactionTemplate` patterns.
3. Reuse existing risk integration fixtures to create a valid persisted CFD
   trade plan, official T0 approval, account risk configuration, and human-
   authorized execution intent.
4. Use Mockito only for deterministic read-only market/risk facts and simulated
   PAPER market prices. Do not replace the production T1 service, execution
   pipeline, finalization step, or CFD settlement service.
5. Identify the existing execution-intent command path that enforces the
   persisted approved risk reference. If the current fixture cannot reach that
   path without bypassing a required authority boundary, stop and report the
   missing seam rather than weakening production validation.

## Phase 2 - Positive Full Lifecycle (Deferred)

1. Create an isolated PAPER account and complete provider-neutral CFD market
   contract fixture with deterministic entry and exit prices.
2. Produce and persist the official T0 risk evaluation through the existing
   `TradePlanRiskEvaluationService` boundary.
3. Create the execution intent through the existing human authorization path and
   assert it references the approved T0 evaluation.
4. Configure a fresh T1 valuation that passes and execute through the real
   `ExecuteTradeService`.
5. Assert the local `SimulatedExecutionAdapter` path is used, with no external
   broker/provider mutation, and assert persisted execution attempt/order/fill
   evidence.
6. Assert `PaperCfdSettlementService` creates exactly one open CFD trade with
   the correct signed quantity, entry price, fee-adjusted realized balance,
   equity, and provenance.
7. Clear and reload the account, change the read-only mark, invoke the existing
   MTM refresh, and assert unrealized PnL/equity changes while realized balance,
   signed quantity, and open status remain unchanged.
8. Create the authorized full-close execution intent for the target trade,
   execute it through the same production PAPER pipeline, and assert the exit
   fill, realized PnL, exit fee, closed status, zero open exposure, and persisted
   post-close state after reload.

## Phase 3 - T1 Rejection and No-Mutation Proof

1. Create an otherwise valid T0-approved and human-authorized ENTRY intent using
   the same production setup.
2. Configure an independently acquired fresh T1 valuation/fact set that
   deterministically rejects at `ExecutionTimeRiskRevalidationService`.
3. Execute through `ExecuteTradeService` and assert the T1 rejection is
   persisted before submission.
4. Assert no broker submission, simulated fill, execution settlement, trade,
   exposure, balance/equity mutation, or provenance row is created.
5. Assert the global `SettlementCompatibilityPolicy` still rejects `CASH + CFD`.

## Phase 4 - Exactly-Once Replay Proof (Deferred)

1. Use the real production provenance store and committed entry/exit evidence.
2. Replay the same committed fill identity after persistence-context clear and
   reload, preferably in a new transaction.
3. Assert the replay returns the existing trade/economic result without creating
   a second trade, fee, balance mutation, close, or provenance row.
4. If the full execution replay path exposes an existing idempotency boundary,
   exercise it; otherwise retain the direct settlement replay assertion already
   established by the existing hardening integration test and document the
   boundary precisely.

## Production-Code Decision Rule

Do not modify production code initially. If the fail-closed routing proof
exposes a real defect, identify the smallest fix preserving:

- T0 and T1 deterministic authority;
- mandatory human authorization;
- local PAPER-only execution;
- deferred CFD settlement and exactly-once semantics;
- global compatibility rejection;
- LIVE/provider authority and cTrader safety.

Stop for human guidance if the required fix would enable a new settlement
combination, change Risk Domain/Broker Service/LIVE behavior, add a migration,
or require a new architectural boundary.

## Expected Files

Likely deliverables, subject to fixture discovery:

```text
trading-core/src/test/java/com/hope/trading/trading_core/execution/PaperCfdDeterministicLifecycleIntegrationTest.java
docs/architecture/stories/0192-paper-cfd-deterministic-lifecycle-e2e/story.md
docs/architecture/stories/0192-paper-cfd-deterministic-lifecycle-e2e/repository-analysis.md
docs/architecture/stories/0192-paper-cfd-deterministic-lifecycle-e2e/implementation-plan.md
docs/architecture/stories/0192-paper-cfd-deterministic-lifecycle-e2e/implementation-report.md
```

Production files are not expected unless the integration test proves a concrete
correctness defect. No unrelated Story directory may be staged or modified.

## Validation Plan

Run focused checks first:

- the new PAPER CFD lifecycle Testcontainers integration test;
- existing `PaperCfdSettlementPersistenceHardeningIntegrationTest`;
- existing `PaperCfdSettlementServiceTest`;
- T0 risk evaluation and T1 revalidation tests;
- execution-intent authorization and execution pipeline integration tests;
- `SettlementCompatibilityPolicy` tests;
- CFD valuation and financial calculator tests.

Then run from `trading-core`:

```bash
./mvnw -q test
./mvnw -q verify
```

Run `git diff --check` and report every check as `PASS`, `FAIL`, `TIMEOUT`, or
`NOT_RUN`. No test will be reported as passing unless it was executed.

## Safety and Non-Goals

```text
CASH_PLUS_CFD_ENABLED = NO
INVENTORY_PLUS_CFD_ENABLED = NO
RISK_DOMAIN_CHANGED = NO
BROKER_SERVICE_CHANGED = NO
LIVE_AUTHORITY_CHANGED = NO
REAL_BROKER_ORDERS = 0
CTRADER_PROVIDER_MUTATIONS = 0
EXTERNAL_CLOSES = 0
MIGRATIONS_ADDED = 0
```

## Approval Boundary

This plan requires explicit human approval before implementation begins.
