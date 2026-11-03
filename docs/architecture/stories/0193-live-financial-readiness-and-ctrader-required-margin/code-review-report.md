# Independent Code Review Report - Story 0193

## Result

**NEEDS FOLLOW-UP / PROVIDER VALIDATION BLOCKED.**

The independent review examined the Story, ADR-084, implementation plan, full
working-tree diff and untracked files.

## Findings

### Corrected

- cTrader ExpectedMargin request/response IDs were wrong. They are now
  `2139/2140`.
- The ExpectedMargin response volume was not checked against the requested
  provider-scaled volume. The adapter now rejects mismatches.
- Deterministic coverage was added for the documented payload names and response
  volume identity.

### Remaining

- Deal history is transported with independent availability, but the current
  risk model still requires a non-null `dailyClosedPnl`. A complete
  policy-dependent implementation requires an explicit normalized contract for
  unavailable historical PnL rather than silently using zero.
- Dedicated LIVE T0/T1 tests for missing, stale and changed provider margin are
  still thinner than the Story acceptance criteria require. Existing tests cover
  T0 missing-margin fail-closed behavior, T1 margin resolution, and the
  execution pipeline's no-submission behavior for unavailable T1, but not the
  complete LIVE integration matrix.

## Provider Validation Boundary

- The rebuilt Broker Service was started with cTrader execution disabled.
- READ-ONLY account instrument validation succeeded for the connected LIVE
  account and returned `EURUSD` and `XAUUSD`.
- ExpectedMargin was attempted through the internal margin-preview path and was
  blocked by the persisted cTrader snapshot cooldown.
- Real cTrader orders: `0`.
- Real cTrader closes: `0`.
- Provider mutations: `0`.

The cooldown was not bypassed and the safety database was not modified.

## Recommendation

Before Story acceptance, decide whether the normalized risk context should model
unavailable daily realized PnL explicitly, or whether all currently supported
risk profiles intentionally require deal history. Then add the corresponding
T0/T1 LIVE regression matrix and retry READ-ONLY ExpectedMargin after the safety
cooldown expires.
