# Story 0150 - Repository Analysis

## Status

`IMPLEMENTATION ANALYSIS RECORDED`

## Repository Baseline

| Item | Current value |
|---|---|
| Branch | `main` |
| HEAD | `87a2bc31d5b22afc5eb2569f48afaa0b1e18328c` |
| origin/main | `87a2bc31d5b22afc5eb2569f48afaa0b1e18328c` |
| Latest merged Story | Story 0149, PR #167 |
| Latest relevant ADR | ADR-074, cTrader authoritative READ-ONLY synchronization |
| Next valid Story | `0150` |
| Working tree | Nine pre-existing untracked architecture reports; no implementation changes at analysis start |
| DevLog | Applicability check attempted once; unavailable due provider failure |

## Story 0149 Prerequisite

Story 0149 proved that complete and fresh provider-neutral account facts could
reach the deterministic Risk boundary. Its mapping deliberately marked snapshots
with open positions incomplete because the v2 position contract did not carry
position margin or protection evidence.

## Current Risk Requirements

| Risk fact | Required when position open | Present before Story 0150 | Authority / missing behavior |
|---|---:|---:|---|
| Position identity | Yes | Yes | Stable provider reference already present |
| Instrument identity | Yes | Yes | cTrader symbol mapped through provider symbol list |
| Direction and quantity | Yes | Yes | Signed normalized quantity |
| Entry price | Yes | Yes | Provider VWAP/position price |
| Current valuation | Yes | No direct v2 field | Derived by existing `MarketValuationPort` |
| Position margin | Yes | No | cTrader already read `usedMargin` but discarded it |
| Stop-loss evidence | Yes for protected automated Risk | No | cTrader position `stopLoss` was discarded |
| Stop identity | Audit/provenance | No | Normalized deterministic evidence reference required |
| Take-profit | No current Risk requirement | No | Not implemented |
| Position P&L | No additional field required | Indirect | Included in account equity and not double-counted |
| Commission / swap | No current open-position Risk requirement | No | Not implemented |
| Position observation timestamp | Yes | Yes | Existing position observation timestamp |

## Risk Path

```text
CTraderReadOnlyObservation
    -> BrokerAccountSnapshotV2
    -> BrokerAccountSnapshotV2Dto
    -> ModeAwareRiskFactsProvider
    -> RiskFactsProvider.Position
    -> TradePlanRiskEvaluationService.positions(...)
    -> risk-domain PositionSnapshot
```

The existing Risk calculation requires `position.margin()` and protection
status/stop facts. It obtains current market valuation independently through
`MarketValuationPort`, so this Story does not duplicate P&L or valuation logic.

## Provider Evidence

cTrader documents position `usedMargin` as margin used for the position in
deposit currency, scaled by `moneyDigits`. It documents current position
`stopLoss` and stable `positionId`. The existing adapter already normalized
`usedMargin` and requested `returnProtectionOrders=true`; the missing work was
provider-neutral propagation, not a new provider calculation.

## Architecture Decision

No new ADR is required. ADR-074 already requires Broker Service to own provider
normalization and permits extending `BrokerAccountSnapshotV2`. Trading Core
continues to consume only provider-neutral facts and Risk formulas remain
unchanged.
