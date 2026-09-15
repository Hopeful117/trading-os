# Engineering Report - Story 0041

## Executive Summary

Story 0041 makes persisted PAPER OPEN trades visible through the normal
positions endpoint without treating them as broker-authoritative positions.
LIVE queries retain the existing Broker Service path. PAPER valuation is a
read-only projection based on local Trade state and fresh executable-side
Market Data quotes.

## Delivered Behavior

```text
authenticated Account.accountId
    -> canonical Account.brokerAccountId
    -> linked BrokerAccount.ownerId and ExecutionMode
    -> LIVE: Broker Service/provider PositionFact
    -> PAPER: local OPEN Trade -> PositionFact
    -> Market Data batch snapshots
    -> neutral OpenPositionDashboardView
```

PAPER position IDs are local persisted Trade IDs. No broker transaction ID,
provider position reference, or mutation scope is manufactured. Each OPEN Trade
is projected separately; opposite-side or same-symbol trades are not merged.

For fresh quotes with a non-null occurrence timestamp, BUY positions are marked
at bid and SELL positions at ask. Stale, unavailable, unresolved, ambiguous, or
unsupported-currency valuation is returned explicitly and never replaced by a
fabricated current price or PnL. A `FRESH` snapshot without a timestamp is also
treated as unavailable.

The frontend suppresses LIVE-only close/reconcile actions and provider-specific
FIFO messaging for PAPER positions.

## Acceptance Evidence

- PAPER source selection is tested at the controller boundary.
- PAPER controller routing verifies zero Broker Service interaction.
- Distinct Account and BrokerAccount identifiers are exercised in fixtures.
- Local OPEN Trade mapping uses persisted Trade IDs.
- Fresh bid/ask valuation and deterministic PnL are tested.
- Stale, missing-timestamp, unsupported-currency, and ambiguous-market states
  are tested.
- LIVE broker position behavior remains covered by existing controller and
  valuation tests.
- Frontend PAPER safety behavior is covered by positions and dashboard tests.

## Validation

- Focused Story 0041 backend tests: passed.
- Complete Trading Core Maven test suite: passed.
- Angular CI tests: passed.
- Angular production build: passed with bundle-size budget warnings.
- `git diff --check`: passed.

## Limitations

PAPER close, settlement, realized PnL, fees, slippage, and generalized position
aggregation remain out of scope.

## Review State

`STORY_0041_APPROVED_READY_FOR_COMMIT_AND_MERGE`

The persistence/reload/HTTP gate is closed. Implementation review and human
approval are complete. Commit and merge remain pending and were not performed
by this task.
