# Persisted PAPER Execution Regression

## Metadata

**ID:** `persisted-paper-execution-regression`

**Title:** Add a persisted, Spring-context-backed PAPER execution regression

**Status:** Approved

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
- Verify successful terminal execution and absence of partial persistence.

## Out of Scope

- New PAPER balance or fill semantics.
- LIVE/PAPER uniqueness rules.
- Broker status contract changes.
- New position aggregate architecture.
- Frontend changes.
- Broker Service changes.
- Sandbox credentials or network access.

## Acceptance Criteria

- [ ] A persisted PAPER BrokerAccount has `executionMode = PAPER`.
- [ ] The PAPER BrokerAccount is owned by the expected user.
- [ ] The live Broker Service execution boundary is not invoked.
- [ ] Normal deterministic risk validation runs before submission.
- [ ] BUY and SELL use the existing ask/bid simulation semantics.
- [ ] The acknowledgement contains `fillPrice`.
- [ ] BrokerOrder and Fill are persisted consistently.
- [ ] Settlement is invoked exactly once.
- [ ] Account balances, equity, and peak equity follow existing semantics.
- [ ] The resulting Trade/position projection is persisted correctly.
- [ ] Execution reaches the expected successful terminal state.
- [ ] No partial state remains after the transaction completes.
- [ ] Existing focused and complete Trading Core tests pass.
- [ ] No unrelated behavior is changed.

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

## Definition of Done

- [ ] Repository Analysis reviewed.
- [ ] Implementation Plan reviewed.
- [ ] Regression implemented.
- [ ] Relevant validation executed.
- [ ] Diff reviewed.
- [ ] Human commit created.
