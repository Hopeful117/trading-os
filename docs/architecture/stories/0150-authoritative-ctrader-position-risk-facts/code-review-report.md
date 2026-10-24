# Story 0150 - Code Review Report

## Scope

Review of the cTrader position margin/protection propagation, provider-neutral
contracts, Risk mapping, fail-closed behavior, compatibility paths and tests.

## Findings

### Resolved - Position margin was discarded

The cTrader adapter already read position `usedMargin`, but the normalized
position omitted it. The fact is now carried through Broker Service and Trading
Core into `RiskFactsProvider.Position.margin()`.

**Disposition:** fixed.

### Resolved - Current stop-loss was discarded

The adapter now preserves the position stop-loss price as provider-authoritative
protection evidence. It does not claim a stop exists when cTrader reports none.

**Disposition:** fixed.

### Resolved - Open positions were always marked Risk-ineligible

The Story 0149 mapping no longer rejects every non-empty position list. It only
rejects positions missing required margin evidence. Existing Risk rules still
reject unprotected positions where the trade-plan mode requires protection.

**Disposition:** fixed.

### Resolved - Provider-specific stop provenance

The independent review identified the `CTRADER_POSITION_STOP_LOSS` provenance
literal in Trading Core as a provider coupling leak. It has been replaced by
the provider-neutral `BROKER_POSITION_STOP_LOSS` value.

**Disposition:** fixed.

### Resolved locally - Zero stop-loss representation

The independent review identified that cTrader may represent an absent stop as
`stopLoss = 0`. Zero is now normalized as absent protection and therefore
`UNPROTECTED`; negative values remain invalid and fail closed. JSON boundary
coverage was added for both the zero-stop and missing-margin paths.

**Disposition:** fixed locally; real provider confirmation remains required.

### Resolved - JSON boundary coverage

Tests now cover a cTrader position with an explicit zero stop-loss and a
position with missing `usedMargin`, including preservation of the position and
the incomplete observation reason.

**Disposition:** fixed.

## Residual Risks

- Real cTrader validation is still required to confirm JSON field shapes,
  provider money scaling and account-specific semantics.
- Runtime validation is currently blocked because the cTrader Open API
  application is `Submitted` and not yet active/approved by Spotware.
- Protection is currently represented from the documented current position
  `stopLoss` field; separate protection-order identity is not needed by current
  Risk rules and remains outside scope.
- Take-profit, commission, swap and position-level P&L are not mapped because
  current Risk rules do not require them.

## Review Result

`PASS WITH REAL-PROVIDER VALIDATION GAP`
