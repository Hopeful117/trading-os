# Code Review - Story 0041

## Review Status

The automated implementation review and human code review are complete. This
document records approval for the implementation only; it is not a merge or
commit action.

## Review Scope

- PAPER/LIVE position-source selection.
- Canonical Account/BrokerAccount ownership and execution-mode routing.
- Local PAPER OPEN Trade projection and stable position identity.
- Neutral position facts and source metadata.
- PAPER bid/ask valuation and explicit valuation status.
- LIVE Broker Service preservation.
- Frontend protection against PAPER close/reconcile actions.
- Regression tests and validation evidence.

## Findings

No remaining blocking or non-blocking implementation finding was identified
after the corrective pass.

### Resolved P2 - Fresh snapshots without timestamps were accepted

`PositionQueryService.currentPrice()` now requires `price.occurredAt() != null`
alongside the fresh status and positive executable-side quote. A fresh snapshot
without a timestamp is classified as unavailable and covered by
`PositionQueryServiceTest.keepsFreshSnapshotWithoutTimestampUnvalued`.

## Positive Review Observations

- PAPER routing returns before any Broker Service account query.
- Persistence integration proves an OPEN Trade survives a real persistence
  context clear and is returned through the HTTP endpoint.
- PAPER positions use local OPEN Trades and local Trade IDs.
- LIVE positions retain Broker Service/provider authority.
- BUY and SELL valuation use executable-side bid and ask respectively.
- Currency mismatch, stale data, missing markets, and ambiguous normalized
  symbols do not fabricate a current price or PnL.
- The frontend suppresses broker-only close/reconcile behavior for PAPER.
- Distinct financial Account and BrokerAccount identifiers are covered by
  controller fixtures.

## Residual Risks

- PAPER close, settlement, realized PnL, fees, slippage, and generalized
  position aggregation remain out of scope.
- The Angular production build passes with existing bundle-size budget
  warnings.
- Existing unrelated worktree changes require separate human diff review.
- The Angular UI has no direct trade-entry surface; UI onboarding and position
  reading pass, but trade creation still requires the existing trade-plan flow
  or the backend API.

## Validation

- Focused Story 0041 backend tests passed.
- Persistence/reload/HTTP integration test passed independently.
- Complete Trading Core Maven test suite passed.
- Angular CI tests passed.
- Angular production build passed with bundle-size budget warnings.
- `git diff --check` passed.

The focused backend suite was rerun after the corrective change and passed.

The rebuilt runtime stack was also exercised through Gateway. Fractional PAPER
quantity persisted correctly after the Flyway precision migration. When Market
Data did not provide a usable current snapshot, the position remained
explicitly unvalued rather than receiving a fabricated price or PnL.

## Approval

`NO_REMAINING_IMPLEMENTATION_FINDINGS`

`STORY_0041 = APPROVED`

Commit and merge remain pending and were not performed.
