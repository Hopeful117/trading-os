# Code Review - Story 0182

## Review Scope

Reviewed the Story 0182 test fixture, migration, migration expectation
updates, transaction boundaries, provenance identity, concurrency barrier,
reload behavior, and rollback assertions.

## Findings

The independent review identified a compatibility-boundary ambiguity: the
fixture uses a CASH account because the existing PAPER CFD accounting service
requires its cash ledger, while the global compatibility policy must continue
to reject CASH + CFD. This was resolved without changing production
compatibility: the integration fixture now asserts the policy rejection and
documents that the CASH account is an internal accounting test seam only.

The migration review gap was closed by asserting the exact V48 constraint
definition and the removal of the superseded constraint. The concurrent test
now asserts that exactly one of the two racing settlement calls commits.

The production migration is justified by a concrete PostgreSQL defect exposed
by the new short-CFD persistence test. It keeps positive quantities for
non-CFD open trades while allowing non-zero signed quantities for open CFDs.

The concurrent test uses independent `TransactionTemplate` executions and
threads, coordinates the provenance read with latches, and accepts the
expected optimistic conflict only after asserting one final economic effect.
The replay test uses the production `JpaTradeExecutionProvenanceStore` bean.
The rollback test verifies trade status, realized balance, realized PnL, and
provenance after the outer transaction is rolled back.

## Residual Risks

- The full Trading Core suite passed when rerun with a longer execution window.
- The test intentionally proves the current optimistic-lock behavior rather
  than introducing a pessimistic account lock. Future changes to the entity
  versioning or settlement transaction boundary must preserve the same
  exactly-once assertions.
- The full suite emits expected test logs for deliberately simulated
  challenge/risk failures; those logs are not Story 0182 failures.

## Review Status

The findings above are addressed in the current worktree. This is an
implementation review record, not an approval or merge decision. Independent
human validation remains required before commit, push, or merge.
