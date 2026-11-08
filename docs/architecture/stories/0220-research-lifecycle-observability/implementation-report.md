# Implementation Report - Story 0220

## Outcome

Implemented additive research lifecycle observability without changing the
existing authorization boundary, MCP tool names, Gateway routes, worker model,
report semantics or execution authority.

## Changes

- Added durable `attemptCount`, `currentStage` and `lastProgressAt` fields to
  `ResearchExecution` and `research_executions`.
- Added Flyway migration `V23__research_execution_observability.sql` with
  PostgreSQL/H2-compatible statements and safe defaults for existing rows.
- Persisted claim increments, deterministic/AI/adaptive/final stage changes,
  checkpoints, requeues and terminal stages.
- Changed terminal transitions to reload the latest persisted execution before
  saving, while requiring the worker's claimed attempt generation, preventing a
  stale worker from overwriting a newer retry.
- Added compare-and-set repository transitions for progress, checkpoints and
  terminal results, plus an atomic stale requeue guarded by attempt and the
  observed update timestamp.
- Stale worker terminal transitions are logged as discarded rather than as
  successful or failed lifecycle outcomes when their compare-and-set is
  rejected.
- Extended the status response with stage, attempt count, progress timestamp,
  retrying, report availability and bounded failure categories.
- Added structured completion/failure logs containing bounded lifecycle fields;
  no new metrics stack was introduced because Market Intelligence has no
  existing Actuator/Micrometer instrumentation and the Story did not justify a
  platform change.
- Corrected the MCP contract test to provide and verify the mandatory exact
  `scope_id` forwarding.
- Added concurrency regression tests for stale workers and stale requeue
  snapshots, a PostgreSQL V23 observability round-trip test, and response
  contract tests for retry/report/failure projections.
- Updated the migration-version assertion from `22` to `23`.

## Validation

- Targeted Market Intelligence tests: passed.
- Full Market Intelligence Maven suite: passed after the migration assertion
  update.
- PostgreSQL migration/lifecycle integration test: passed through V23.
- MCP adapter suite: `36 passed`.
- `git diff --check`: passed.
- Full Market Intelligence suite after the concurrency fix: passed.

## Runtime Limitation

Complete external MCP runtime validation remains `RUNTIME_BLOCKED`. No
authorized agent credential and isolated research fixture were available. No
shared runtime state was mutated and no research was started through the
external broker/Gateway workflow.

## Security

- Existing owner/scope authorization paths were not changed.
- Status/report responses continue to resolve through the existing ownership
  checks.
- Failure responses expose only bounded categories and existing stable failure
  codes; exception messages and stack traces are not exposed.
- No credentials, grant metadata or internal provider payloads were added to
  the public contract.

## Git Boundary

No commit, push, merge, deployment or branch operation was performed.
