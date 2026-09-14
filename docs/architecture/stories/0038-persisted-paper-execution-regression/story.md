# Story 0038 - Persisted PAPER Execution Regression

## Metadata

**ID:** `0038`

**Title:** Add a persisted, Spring-context-backed PAPER execution regression

**Status:** Review

## Goal

Prove that the already implemented PAPER Account / Simulated Execution path
works through real Trading Core application and persistence boundaries, not
only through isolated or partially mocked execution tests.

## Context

The PAPER milestone is merged into `main`. Existing coverage verifies the
simulation and settlement path with in-memory fixtures, but does not prove
the same behavior through persisted users, accounts, broker accounts, orders,
fills, and trades in a Spring test context.

This Story is a reliability and regression-testing slice. It does not redesign
PAPER semantics.

## Problem

Persistence wiring, transaction boundaries, ownership propagation, and Spring
bean configuration could regress while isolated execution tests continue to
pass. A persisted application-context test is required to protect the real
vertical path.

## Scope

- Add a Trading Core Spring-backed regression test.
- Persist the user, financial account, and PAPER BrokerAccount where supported
  by the existing model.
- Execute the normal deterministic execution pipeline.
- Isolate only the external market-data and live broker boundaries.
- Verify persisted BrokerOrder, Fill, Account, and Trade state.
- Verify successful terminal execution and a coherent persisted final state.

## Out of Scope

- New PAPER balance or fill semantics.
- LIVE/PAPER uniqueness rules.
- Broker status contract changes.
- New position aggregate architecture.
- Frontend changes.
- Broker Service changes.
- Sandbox credentials or network access.

## Acceptance Criteria

- [x] A persisted PAPER BrokerAccount has `executionMode = PAPER`.
- [x] The PAPER BrokerAccount is owned by the expected user.
- [x] The live Broker Service execution boundary is not invoked.
- [x] The persisted execution invokes the normal execution-time risk gate before
  submission; deterministic T1 behavior remains proven by existing focused
  pipeline tests, not by this persisted test.
- [x] The persisted regression proves BUY-at-ask pricing; existing isolated
  PAPER coverage proves SELL-at-bid pricing, but this Story does not re-prove
  SELL through the persisted Spring path.
- [x] The persisted acknowledgement and fill preserve the BUY `fillPrice`.
- [x] BrokerOrder and Fill are persisted consistently.
- [x] The completed persisted execution produces the expected settlement state;
  exact-once settlement invocation is proven by existing isolated coverage,
  not independently by this persisted test.
- [x] Account balances, equity, and peak equity follow existing semantics.
- [x] The resulting Trade/position projection is persisted correctly.
- [x] Execution reaches the expected successful terminal state.
- [x] The completed PAPER execution persists a coherent final state across
  BrokerOrder, Fill, Account, and Trade reload.
- [x] Existing focused and complete Trading Core tests pass.
- [x] No unrelated behavior is changed.

## Constraints

- Reuse the existing Spring Boot, JPA, Flyway, and H2 test infrastructure.
- Do not require external network access or broker credentials.
- Preserve Trading Core as the owner of PAPER financial state.
- Preserve deterministic risk authority and execution lifecycle transitions.
- Keep provider-specific mechanics inside existing adapters.
- Do not commit, push, or merge automatically.

## Relevant ADRs

- `docs/implementation/ADR-028-implementation.md` - deterministic risk
- `docs/implementation/ADR-029-implementation.md` - execution pipeline
- `docs/architecture/stories/0036-close-execution-feedback-loop/story.md` -
  execution feedback context

## Relevant Modules

- `trading-core`

## Validation

- New persisted PAPER regression.
- Focused broker-account and execution tests.
- Complete Trading Core Maven suite.
- Broker Service suite only if affected.
- `git diff --check`.
- Manual diff review.

## Evidence Classification

| Behavior | Evidence | Coverage level |
|---|---|---|
| SELL uses bid price | `PaperExecutionVerticalRegressionTest.paperExecutionRoutesToSimulationFillsSettlesAndFinalizesForBuyAndSell` | Existing isolated test; not persisted vertical evidence |
| Settlement is invoked exactly once | `PaperExecutionVerticalRegressionTest.paperExecutionRoutesToSimulationFillsSettlesAndFinalizesForBuyAndSell` verifies `times(1)` | Existing isolated test; not persisted vertical evidence |
| Rollback after execution failure | No execution test found that asserts rollback/no partial persistence | Not independently proven |
| Risk validation precedes submission | `ExecutionPipelineOrderRegressionTest.t1RunsBeforeBrokerSubmission` and rejection/unavailable variants | Existing isolated pipeline tests; persisted Story invokes a mocked T1 boundary |

## Definition of Done

- [x] Repository Analysis recorded.
- [x] Implementation Plan recorded.
- [x] Regression implemented.
- [x] Relevant validation executed.
- [x] Diff reviewed by the implementation agent.
- [ ] Human commit created.
