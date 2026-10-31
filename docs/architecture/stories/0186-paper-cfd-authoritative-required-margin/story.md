# Story 0186 - PAPER CFD Authoritative Proposed-Order Margin Boundary

## Metadata

**ID:** `0186`
**Title:** PAPER CFD Authoritative Proposed-Order Margin Boundary
**Status:** Blocked - authoritative PAPER margin semantics unavailable
**Related ADR:** `ADR-082`
**Related Stories:** `0183`, `0184`, `0185`

## Objective

Establish whether Trading OS can expose an authoritative proposed-order margin
fact for PAPER CFD through `RequiredMarginPort` without confusing it with
current used margin, position margin, provider leverage, or a theoretical
`notional / leverage` calculation.

## Decision

```text
MARGIN_MODEL_DECISION = UNAVAILABLE
PAPER_CFD_REQUIRED_MARGIN_AUTHORITATIVE = NO
SEMANTICALLY_AUTHORITATIVE = NO
NEW_ARCHITECTURAL_DECISION_REQUIRED = YES
READY_TO_RETRY_ADR_082_PHASE_8 = NO
```

No production implementation is made. `CASH + CFD` remains rejected.

## Exact Missing Boundary

The repository has no proven PAPER CFD source for the incremental required
margin of a proposed order:

- `BrokerRequiredMarginClient` routes PAPER requests to a Broker Service
  provider preview, making the provider adapter the source rather than the
  local PAPER financial authority.
- The Kraken preview calculates `quantity * price / leverage` and defaults to
  `CASH_LEVERAGE = 1`; the repository does not establish this as the canonical
  CFD margin contract for PAPER.
- cTrader read-only observations expose account `usedMargin` and `freeMargin`,
  plus current position margin, but no proposed-order margin operation.
- The cTrader investigation records `leverageInCents = 10000` but states that
  actual EURUSD margin fields were unavailable and margin confidence was only
  `MEDIUM`.
- Existing exposure, hedging/netting, account tiers, instrument margin rules,
  and dynamic provider semantics are not represented as authoritative inputs.

Therefore no defensible PAPER value such as `11 USD` may be produced from the
EUR/USD fixture solely by dividing `1100 USD` by `100` leverage.

## Invariants

```text
CURRENT_USED_MARGIN != PROPOSED_REQUIRED_MARGIN
CURRENT_POSITION_MARGIN != PROPOSED_REQUIRED_MARGIN
PAPER_CFD_REQUIRED_MARGIN_PREVIEW = AVAILABLE_BUT_NON_AUTHORITATIVE
PAPER_CFD_REQUIRED_MARGIN_AUTHORITATIVE = UNAVAILABLE
CASH_PLUS_CFD = REJECTED
INVENTORY_PLUS_CFD = REJECTED
RISK_DOMAIN_CHANGED = NO
CTRADER_SAFETY_EXECUTION_ENABLED = false
```

## Required Future Decision

An approved architecture must define one of:

1. A provider-neutral authoritative PAPER margin policy and its provenance;
2. A provider/reference margin operation whose semantics intentionally define
   the PAPER simulation; or
3. A complete canonical contract containing authoritative margin parameters,
   including exposure interaction and currency semantics.

Until that decision and evidence exist, the correct implementation is
fail-closed unavailability, not a new calculator.
