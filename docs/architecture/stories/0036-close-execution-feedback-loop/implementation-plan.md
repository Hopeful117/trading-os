# Implementation Plan - Story 0036

## Status

Planning only. No Story 0036 production implementation is included in this
checkpoint.

## Planned Slices

1. Define the `TradeOutcome` entry snapshot and provenance value objects within
   Trading Core's execution bounded context.
2. Define the additive internal TradePlan provenance contract supplied by
   Market Intelligence, without changing opportunity-generation semantics.
3. Add persistence mapping, migration, indexes, and repository ports.
4. Capture execution and strategy provenance at successful entry finalization,
   with idempotent finalization and fill-aware entry pricing.
5. Add the account-scoped read API and authorization checks.
6. Add behavior-level tests for creation, missing provenance, multiple
   provenance records, immutable snapshots, persistence, ownership, and API
   filtering.

## Constraints

- Preserve the existing ExecutionIntent, BrokerOrder, and ExecutionAttempt
  lifecycle.
- Preserve T0/T1 risk behavior.
- Keep the legacy `Trade` entity and broker synchronization path unchanged.
- Keep provider-specific behavior out of Trading Core's domain model.
- Do not infer a position-close outcome from broker position and order
  identities. Close association and realized-PnL allocation require a separate
  approved Story.
- Do not introduce event-driven infrastructure or new dependencies.
- Do not claim completion until the Story 0036 acceptance criteria are
  implemented and validated.

## Required Validation

- Targeted TradeOutcome domain and persistence tests.
- Execution finalization integration tests.
- Authorized read API tests.
- Complete Trading Core suite.
- `git diff --check`.
