# Story 0150 - Authoritative cTrader Position-Level Risk Facts

## Metadata

**ID:** `0150`
**Title:** `Authoritative cTrader Position-Level Risk Facts`
**Status:** `Closed with validation gap - real provider validation required`
**Related Stories:** `0148`, `0149`
**Related ADRs:** `ADR-028`, `ADR-031`, `ADR-071`, `ADR-072`, `ADR-074`

## Goal

Extend the existing provider-neutral authoritative snapshot so Trading Core can
evaluate open cTrader positions using the position-level facts required by the
existing deterministic Risk rules.

## Scope

- Preserve cTrader position margin from the provider's `usedMargin` field.
- Preserve the current cTrader position stop-loss price as authoritative
  protection evidence.
- Carry the facts through `BrokerAccountSnapshotV2` and the existing
  `ModeAwareRiskFactsProvider` boundary.
- Keep missing position margin fail-closed.
- Represent a provider-confirmed absent stop as `UNPROTECTED`, without inventing
  protection or mutating the provider.
- Preserve provider-neutral identity, precision and observation freshness.

## Out Of Scope

- FTMO Challenge rules or formulas.
- cTrader execution or protection mutation.
- New Risk formulas.
- Commission, swap, take-profit or position P&L mapping not required by current
  Risk rules.
- Generic pending-order exposure.

## Acceptance Criteria

- [x] Open-position margin is carried from cTrader's authoritative position
      `usedMargin` fact.
- [x] Current position stop-loss is carried as provider-authoritative protection
      evidence.
- [x] A position without a stop remains explicitly `UNPROTECTED`.
- [x] Missing position margin remains Risk-ineligible.
- [x] Position identity and instrument mapping remain provider-neutral.
- [x] Snapshot completeness and freshness semantics are reused unchanged.
- [x] Risk formulas remain unchanged.
- [x] Kraken and PAPER paths remain independent of cTrader-specific fields.
- [x] cTrader execution remains unavailable.

## External Provider Evidence

- cTrader Open API model messages document `ProtoOAPosition.usedMargin`,
  `stopLoss`, `positionId`, `moneyDigits`, `commission` and `swap`:
  `https://help.ctrader.com/open-api/model-messages`
- cTrader `ProtoOAReconcileReq` documents `returnProtectionOrders` and the
  position/order observation boundary:
  `https://help.ctrader.com/open-api/messages`
- Documentation consulted: `2026-10-23`.

## Validation Boundary

```text
LOCAL_DETERMINISTIC_VALIDATION = COMPLETE
REAL_CTRADER_POSITION_RISK_VALIDATION = REQUIRED
CTRADER_EXECUTION = NO
FTMO_RULES = NO
```

## Runtime Validation Blocker

The local cTrader account was created successfully and the OAuth route was
validated through Gateway and Broker Service. The cTrader Open API application
currently has status `Submitted`, so Spotware approval is still pending.

The authorization attempt cannot proceed while the OA client is inactive and
returns `Application authentication failed: OA client is not in active state`.
No workaround is safe or authorized. Runtime validation must resume only after
the application becomes `Active`/approved.
