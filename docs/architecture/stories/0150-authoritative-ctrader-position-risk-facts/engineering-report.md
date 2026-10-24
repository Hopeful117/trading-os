# Engineering Report - Story 0150

## Repository Baseline

```text
BASELINE = 87a2bc31d5b22afc5eb2569f48afaa0b1e18328c
BRANCH = main
ORIGIN_MAIN = 87a2bc31d5b22afc5eb2569f48afaa0b1e18328c
LATEST_MERGED_STORY = 0149 / PR #167
LATEST_RELEVANT_ADR = ADR-074
```

## Story / ADR Status

Story 0150 is implemented against the accepted ADR-074 provider-neutral
authoritative snapshot boundary. Story 0149 is the verified prerequisite.
No new ADR was required.

## Current Open-Position Risk Blocker

Resolved locally: cTrader position margin and current stop-loss evidence now
reach existing deterministic Risk. Real provider validation remains open.

## Required and Provider Fact Summary

The current Risk engine needs position identity, instrument, signed quantity,
entry price, current valuation, position margin and protection status/stop
evidence. Identity, instrument, quantity and entry were already authoritative;
current valuation remains the existing market-data responsibility. cTrader
provides direct `usedMargin`, `stopLoss` and stable `positionId` facts. Missing
margin fails closed; missing stop is explicit unprotected state.

## Position Margin and Protection

Position margin is `POSITION_MARGIN = AUTHORITATIVE` from cTrader
`ProtoOAPosition.usedMargin`, scaled with the provider's `moneyDigits`. No
heuristic allocation or formula was added.

Protection is `PROTECTIVE_STOP = AUTHORITATIVE` from the current position
`stopLoss` field. The provider-neutral mapping uses a deterministic position-
derived evidence reference and provenance. No order mutation or inferred
protection is performed.

## Contract and Risk Changes

The provider-neutral position contracts now carry nullable `margin` and
`stopLoss`. `ModeAwareRiskFactsProvider` maps them into the existing Risk
position margin and `Stop` fields. Risk formulas, `risk-domain` classes and
execution authorization remain unchanged.

## Failure Semantics

- Missing/invalid margin: snapshot incomplete, Risk unavailable.
- Missing stop-loss: complete provider fact with `UNPROTECTED` status.
- Stale/incomplete snapshot: existing Story 0148/0149 fail-closed path.
- Position identity or symbol mapping failure: existing provider observation
  fails closed.
- Provider timeout/authentication failure: existing unavailable path.
- Restart/reconnect: existing fresh post-authentication observation required.

## Persistence, Provenance and Observability

Existing snapshot provenance serialization is reused. No raw provider response
table, new freshness system, or unsafe logging was added. The existing
unavailability reasons and Risk provenance contain the normalized facts used.

## Regression and Validation

- Kraken regression path remains on its compatibility constructors and legacy
  Risk capability.
- PAPER remains local and does not require cTrader fields.
- Broker Service cTrader mapper/capability tests passed.
- Trading Core provider, Risk and synchronization tests passed.
- Both affected modules compiled successfully.
- `git diff --check` passed.
- Real cTrader position validation was not run.

## FTMO Critical Path

The local open-position fact blocker is resolved. The next hard prerequisite is
controlled real cTrader READ-ONLY position-Risk validation, not FTMO formula
implementation.

## Runtime Blocker

Runtime preparation is complete: the local cTrader LIVE account exists, the
OAuth route reaches Broker Service through Gateway, and authorization is
requested with scope `accounts`. The cTrader Open API application is currently
`Submitted`; cTrader rejects the authorization attempt with
`OA client is not in active state`.

Therefore the real provider validation gate is blocked pending Spotware
approval. No external cTrader account is bound and no execution or protection
mutation has been attempted. After activation, a fresh OAuth attempt must be
performed before the position-level Risk facts can be validated against the
provider.

## Next Story

`Story 0151 - Controlled cTrader READ-ONLY Position Risk Validation`

## Final Gate

========================================
CTRADER POSITION RISK FACTS GATE
========================================

REPOSITORY BASELINE:
87a2bc31d5b22afc5eb2569f48afaa0b1e18328c

STORY:
0150 - Authoritative cTrader Position-Level Risk Facts

ADR REQUIRED:
NO

ADR:
NONE - ADR-074 already governs the boundary

STORY 0149 PREREQUISITE:
READY

OPEN-POSITION RISK BLOCKER:
RESOLVED LOCALLY / REAL VALIDATION REQUIRED

CTRADER POSITION IDENTITY:
AUTHORITATIVE

POSITION MARGIN:
AUTHORITATIVE

PROTECTIVE STOP EVIDENCE:
AUTHORITATIVE

TAKE-PROFIT EVIDENCE:
NOT_REQUIRED

POSITION PNL:
NOT_REQUIRED

COMMISSION/SWAP:
NOT_REQUIRED

INSTRUMENT MAPPING:
READY

PROVIDER SNAPSHOT COMPLETENESS:
READY

PROVIDER SNAPSHOT FRESHNESS:
READY

OPEN-POSITION RISK ELIGIBILITY:
READY FOR COMPLETE FACTS / UNPROTECTED STATE REMAINS RULE-DEPENDENT

MISSING FACTS FAIL-CLOSED:
YES

STALE FACTS FAIL-CLOSED:
YES

POSITION IDENTITY MISMATCH FAIL-CLOSED:
YES

TRADING CORE CTRADER COUPLING:
NO

BROKER SERVICE FTMO COUPLING:
NO

RISK FORMULA CHANGES:
NONE

KRAKEN REGRESSION:
PASS

PAPER V1 REGRESSION:
PASS

REAL CTRADER POSITION RISK VALIDATED:
NO

RUNTIME BLOCKER:
PENDING SPOTWARE APPROVAL - OA APPLICATION STATUS SUBMITTED

CTRADER EXECUTION ENABLED:
NO

FTMO RULES IMPLEMENTED:
NO

S1 REQUIRED:
NO

AI REQUIRED:
NO

IMPLEMENTATION STATUS:
COMPLETE LOCALLY / REAL VALIDATION REQUIRED

FTMO CRITICAL PATH STATUS:
Local position-fact blocker resolved; controlled READ-ONLY cTrader validation is blocked pending Spotware approval.

NEXT ENGINEERING STORY:
Story 0151 - Controlled cTrader READ-ONLY Position Risk Validation

NEXT HUMAN ACTION:
Run the bounded READ-ONLY cTrader position-risk validation procedure.

## Closure Decision

Story 0150 is closed for the implemented local scope. The deterministic
provider-neutral position-risk propagation is complete, reviewed and locally
validated. The real cTrader position-risk validation remains an explicit open
gap because Spotware approval is still pending and the OA application remains
in `Submitted` state.

This closure does not claim real-provider validation, cTrader execution
readiness or FTMO readiness. Runtime validation belongs to Story 0151 and must
resume only after the OA application becomes active/approved.
