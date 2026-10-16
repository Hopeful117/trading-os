# Story 0111 - Continuous Research Data Lifecycle

## Status

Implemented locally - pending human review.

## Goal

Provide a bounded, disabled-by-default lifecycle that continuously produces
strict research feature snapshots and evaluates objective market outcomes after
their configured maturity horizon.

## Scope

- Schedule bounded research observations for explicitly configured market IDs.
- Align evaluation cutoffs to UTC cadence boundaries.
- Submit only `STRICT_OBSERVED` analysis executions.
- Reuse the existing analysis, feature projection, and outcome services.
- Persist the execution temporal scope needed for restart-safe projection.
- Evaluate configured outcome horizons only after `T0 + H`.
- Expose an internal research readiness view.
- Preserve idempotent immutable snapshot and outcome persistence semantics.
- Keep dataset export explicit and separate from lifecycle acquisition.

## Constraints

- The lifecycle is disabled unless explicitly enabled.
- Market IDs must be bounded and configured consistently with Market Data capture.
- No historical backfill is fabricated after downtime.
- `knownAt` and strict observed eligibility remain authoritative.
- Feature snapshots must not depend on future outcomes.
- Outcomes remain objective facts, not labels, strategy results, P&L, or execution results.
- No ResearchSample table, Python authority, public API, Gateway route, frontend,
  Risk, Broker, or Execution change is introduced.

## Acceptance Criteria

- An enabled lifecycle rejects missing markets, non-positive cadence or delay,
  and duplicate or non-positive outcome horizons.
- Each configured market and aligned cutoff produces at most one strict
  execution through the existing idempotency boundary.
- Restart processing can project completed strict executions without changing
  their evaluation cutoff or evidence policy.
- Feature projection remains independent of future outcomes and preserves the
  existing immutable snapshot identity rules.
- Outcomes are requested only when `now >= T0 + H` and retain existing
  incomplete or unavailable status semantics.
- Readiness reports configured scope, evidence timestamps, snapshots, outcome
  status counts, maturity, and approximate available population.
- Lifecycle startup does not create research facts while disabled.
- Focused tests, full Market Intelligence verification, and diff validation
  pass; runtime validation limitations are documented.

## Relevant Decisions

- ADR-057: historical evidence and `knownAt` semantics.
- ADR-058: explicit strict replay boundary.
- ADR-059: objective future market outcomes and maturity.
- ADR-060: immutable feature snapshots.
- ADR-061 and ADR-062: explicit immutable research selection boundaries.

## Out Of Scope

- General historical synchronization or fabricated backfill.
- New feature or outcome calculations.
- Dataset materialization or automatic export.
- ML training, labels, strategy evaluation, backtesting, or P&L.
- Public product exposure, frontend work, or broker/execution behavior.
