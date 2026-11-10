# Implementation Report - Story 0222

## Amendment

The approved design amendment binds research scopes to an owner-owned broker
account. The account's provider is the universe identity and catalogue markets
are resolved server-side. The former exactly-two-market invariant is removed
for new scopes.

## Amendment Implementation

- Added `brokerAccountId` to new research-scope creation and responses.
- Resolved scope markets from the selected broker provider's market catalogue.
- Updated the Agents UI to select a broker account only.

## Outcome

Implemented the agent-facing historical research inspection and bounded dataset
export foundation across Trading Core, Gateway, Market Intelligence and the MCP
adapter. The implementation preserves the existing `research:run` workflow and
does not add broker, trading, provider-backfill or Python runtime authority.

Runtime agent authorization and isolated historical export remain unverified in
this environment.

## Changes

- Added independent `research:inspect` and `research:export` permissions.
- Added additive historical constraints to research scopes and persisted them
  through a new Trading Core migration.
- Added Trading Core server-side authorization for owner, credential, grant,
  scope, market, interval, cutoff, profile and resource-limit checks.
- Added deterministic readiness diagnostics with `READY`, `INSUFFICIENT`,
  `BLOCKED` and `UNKNOWN` verdicts.
- Added idempotent export job persistence, bounded server-controlled output,
  authorization revalidation before execution, status authorization and export
  metadata responses.
- Added Gateway-backed Market Intelligence routes and focused route coverage.
- Added three MCP tools for readiness inspection, export submission and export
  status; responses remain metadata-only.
- Added operator documentation for the new research permissions.
- Added strict agent access-policy coverage for readiness and export routes.
- Made export dispatch occur after the persistence transaction commits.
- Canonicalized idempotency fingerprints with scope and authorization context.
- Enforced export row/byte/time bounds during dataset construction/publication
  and remove failed output directories.
- Persisted separate Java contract-validation and experiment-eligibility states.
- Added bounded HTTP error mappings for historical research requests.
- Corrected readiness interval filtering and post-cutoff diagnostics.
- Added hard readiness caps, bounded outcome acquisition and deterministic
  available-outcome counting.
- Updated the migration integration assertion from version 23 to version 25.

## Validation

- Trading Core focused tests for grant compatibility and historical constraints:
  passed.
- Market Intelligence focused readiness test: passed.
- Gateway focused route test: passed.
- Market Intelligence Flyway validation: 25 migrations validated and applied
  successfully in the verification run.
- Market Intelligence and Trading Core full `verify` runs exceeded the
  120-second tool limit before a final Maven result was emitted; no failure was
  observed in the captured output.
- MCP Python tests could not run because `pytest` is not installed in the
  active interpreter. The previously executed targeted MCP suite had 36 tests
  passing before this final verification pass.
- `git diff --check`: passed.
- Independent follow-up review: no confirmed defects identified; persistence
  coverage and runtime authorization remain residual validation gaps.

## Runtime Limitation

```text
RUNTIME_AGENT_AUTHORIZATION_NOT_VERIFIED
```

No authorized agent credential, isolated runtime dataset, or live export was
available. No bypass, grant, broker operation, database mutation, commit or
deployment was performed.

## Git Boundary

Changes remain uncommitted in the working tree for human review.
