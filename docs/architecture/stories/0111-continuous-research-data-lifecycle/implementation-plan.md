# Implementation Plan - Story 0111

This plan records the implementation shape used for the current worktree
change. It is generated retrospectively and does not constitute human approval.

## Production Changes

1. Add typed `research.lifecycle` configuration with safe disabled-by-default
   behavior and validation for bounded markets, cadence, delay, and horizons.
2. Add a scheduled lifecycle service that aligns UTC cutoffs, submits strict
   executions, and uses deterministic idempotency keys.
3. Reuse existing execution, projection, and outcome application services
   rather than duplicating domain calculations.
4. Extend analysis execution persistence with evaluation cutoff and evidence
   policy so asynchronous processing retains temporal meaning.
5. Add the migration and adapter/port changes required to query strict research
   executions by market and cutoff.
6. Add an internal readiness endpoint exposing lifecycle scope, evidence,
   snapshots, outcome maturity/status, and approximate available population.
7. Document local enablement, restart behavior, and safe disablement.

## Test Changes

- Verify UTC cutoff alignment and deterministic lifecycle idempotency.
- Verify disabled startup performs no lifecycle work.
- Verify strict evidence policy and explicit evaluation cutoff propagation.
- Verify outcomes are requested only after each configured horizon matures.
- Verify configuration validation and duplicate horizon rejection.
- Run the complete `market-intelligence` Maven verification.
- Run `git diff --check`.

## Explicit Non-Goals

No historical backfill, new provider path, feature or outcome calculation,
ResearchSample persistence, automatic dataset export, ML integration, Python
authority, public API, Gateway, frontend, Risk, Broker, or Execution behavior is
included.

## Documentation

- Add the Story artifact set.
- Record implementation behavior and operational enablement.
- Record validation limitations and the SonarQube result without claiming a
  successful Quality Gate.
