# Code Review - Story 0042

## Findings

### Resolved P1 - Stale or unavailable market data could be settled

`SimulatedExecutionAdapter` now rejects PAPER execution unless the snapshot is
tradable, `FRESH`, and timestamped. A stale positive bid/ask can no longer
create an exit fill.

### Resolved P1 - Insufficient PAPER balances were silently ignored

`PaperSettlementService` now rejects missing or insufficient asset balances
before completing settlement. The target Trade and Account remain unchanged
when the required balance is unavailable.

### Resolved - Acceptance-proof integration tests

Resolved by `PaperExitAcceptanceIntegrationTest`, which now covers the
persisted ENTRY -> OPEN -> EXIT -> CLOSED reload path, a real concurrent
two-request race, idempotent replay, rollback, and PAPER broker isolation.

### Low - Compatibility-shaped response (NON_BLOCKING, FUTURE_REFINEMENT)

PAPER responses reuse `PositionCloseResponse`, leaving broker-oriented fields
null. This avoids a broad API redesign and does not leak a provider reference,
but a later neutral close-response refinement may improve explicit local status
semantics.

## Positive Checks

- LIVE position-close service and broker adapter were not changed.
- PAPER uses `tradeId`, not `brokerPositionReference`.
- Exit side and quantity are derived server-side.
- Settlement uses the existing PnL calculator.
- No last-price fallback or short borrowing model was introduced.
- Existing Trading Core and frontend regression suites passed.
- Independent review regression tests cover stale snapshot rejection and
  insufficient/missing exit balances without financial mutation.

## Verdict

`APPROVED`

No unresolved authority-boundary, scope-expansion, or acceptance-evidence
finding remains.

```text
BLOCKING_FINDINGS = NONE
HUMAN_REVIEW = APPROVED
```
