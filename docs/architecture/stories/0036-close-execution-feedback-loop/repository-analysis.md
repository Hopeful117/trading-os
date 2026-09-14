# Repository Analysis - Story 0036

## Repository Checkpoint

| Field | Value |
|---|---|
| Story | `0036-close-execution-feedback-loop` |
| Main | `03e3a6fbcea8d07402408bc1206dd09af72af5fd` |
| Origin main | `03e3a6fbcea8d07402408bc1206dd09af72af5fd` |
| Status in story.md | Draft |
| TradeOutcome entity | Not present |
| trade_outcome migration | Not present |
| TradeOutcome read API | Not present |

## Scope Reconciliation

The Story 0036 file defines the durable `TradeOutcome` feedback loop:

```text
ExecutionIntent -> TradeOutcome -> strategy provenance -> closed outcome
```

The implementation merged in PR #34 is the PAPER Account / Simulated Execution
milestone. It delivers `ExecutionMode`, PAPER routing, simulated fills,
settlement, and the vertical PAPER execution test. It does not implement the
`TradeOutcome` entity, strategy provenance denormalization, close linkage, or
trade-outcomes API defined by Story 0036.

The root-level `PAPER_ACCOUNT_V1_IMPLEMENTATION_REPORT.md` documents that
PAPER milestone, but it is not an implementation report for the current Story
0036 scope. The distinction is now recorded here so the repository does not
implicitly mark Story 0036 as complete.

## Current Findings

- `ExecutionIntent`, `BrokerOrder`, and `ExecutionAttempt` are persisted.
- PAPER settlement creates or updates the legacy `Trade` projection.
- No persisted `TradeOutcome` aggregate or table exists.
- No execution-to-strategy provenance read model exists.
- `PositionCloseCommand` does not update a `TradeOutcome`.
- No `/api/v1/accounts/{accountId}/trade-outcomes` endpoint exists.

## Decision

Story 0036 remains `Draft` and is not implementation-complete. The PAPER
milestone must not be used as evidence that the TradeOutcome acceptance
criteria have been met.

## Consequence

Future implementation of Story 0036 requires its own approved implementation
slice. It must preserve the already merged PAPER execution behavior and must
not modify the legacy `Trade` contract as a substitute for `TradeOutcome`.
