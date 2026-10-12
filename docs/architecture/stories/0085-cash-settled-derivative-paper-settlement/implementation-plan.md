# Implementation Plan - Story 0085

## Implementation Boundary

Extend the existing Trading Core PAPER settlement boundary without introducing
a new accounting or position architecture.

## Planned Changes

1. Resolve and enforce the existing Account/Product compatibility policy.
2. Identify `CASH + LINEAR_PERPETUAL` as the cash derivative settlement path.
3. Preserve the existing inventory branch unchanged for `INVENTORY + SPOT`.
4. For derivative entry, update the existing Trade lifecycle and deduct only
   the fill fee from equity.
5. For derivative exit, skip balance mutation and apply existing gross P&L less
   the closing fee to equity.
6. Preserve full-exit validation, persistence, transactions, idempotency, and
   Challenge reevaluation.
7. Add focused unit coverage and retain existing spot regression coverage.

## Invariants

- Unsupported or ambiguous combinations fail before financial mutation.
- Cash derivative entries and exits never mutate AccountBalance records.
- Entry fee and exit fee are each applied exactly once.
- A closed trade cannot be exited again.
- Existing inventory spot balances and equity remain unchanged in behavior.
- No mark-to-market or continuous valuation is introduced.

## Validation Plan

- Focused `PaperSettlementExitTest` and cash derivative tests.
- Full `mvn -q verify` in `trading-core`.
- `git diff --check`.
- Review the complete diff and Story scope.
