# Story 0029 — Engineering Report

**Date**: 2026-08-25
**Branch**: `feature/story-0029-enrich-opportunity-setup-snapshot`
**Status**: DONE — Ready for human review

---

## Before

A trader looking at an Opportunity knew:

```text
0G/EUR · SHORT · m15 · Score 100
explanation: "Legacy OHLC Trend"
```

No price, no trigger context, no detection instant. The trader had to trust the system blindly.

## After

```text
BCH/AUD · SHORT · m15

Setup at detection
Reference price: 367.05  (observed 22:00:00)
"Directional OHLC trend: short with validity until 2026-08-25T22:30:00Z (15m)"
Matched conditions:
  • directional_price_change: -18.18
Detected 21:57:17
```

The trader can now decide whether the setup deserves a TradePlan.

## Domain model

`OpportunitySetupSnapshot {referencePrice?, referencePriceAt?, description, triggers[], detectedAt}` + `OpportunityTrigger {condition, observedValue}` — owned by `TradingOpportunity`, immutable across version transitions, null only for pre-0029 rows.

## Reference price

Source: observation evidence measurement `closePrice` (last candle close), read inside the same pipeline transaction that creates the opportunity. Timestamp = evidence `observedAt`. The OHLC capability now emits `closePrice`. No retroactive Market Data call.

## Trigger context

`StrategyMatch.conditionResults` projected verbatim into triggers (`conditionId→condition`, `observedValue`). `passed` dropped (MATCH ⇒ all passed). Runtime example: `directional_price_change = -18.18`.

## Setup description

Evaluator-owned `StrategyEvaluation.explanation()` forwarded verbatim by the factory — deterministic, strategy-specific logic stays in evaluators, no LLM involvement.

## Persistence

Existing payload JSON pattern; **no DB migration**; legacy payloads deserialize with null snapshot.

## REST

Additive `setup` object on `OpportunityResponse`; all 18 historical fields unchanged.

## Angular

Detail page "Setup at detection" section (price, description, conditions, detected time); list gains a compact "Ref. price" column with honest fallback.

## Legacy data

`setup: null`, UI hides section — runtime-verified on pre-0029 row.

## StrategyMatch mapping (before → after)

| Data | Before | After |
|---|---|---|
| direction | preserved | preserved |
| matchId | strategyMatchId | strategyMatchId |
| conditionResults | dropped | setup.triggers |
| matchedAt | dropped | setup.detectedAt |
| evaluation.explanation | dropped | setup.description |
| evidence closePrice | not produced | setup.referencePrice(+At) |
| strategyId/Version | dropped | via strategyMatchId lookup |
| contextDigest/marketId/analysisExecutionId | dropped | still dropped (audit-only) |

## TradePlan boundary

Runtime-proven non-regression: plan created from the new opportunity has entry MARKET @ **369.42** (live) vs snapshot **367.05** — Planning Engine untouched and still live-data-driven. entry/SL/TP/sizing/R:R remain TradePlan-owned.

## DevLog usage

- `get_engineering_context`: 60 evidences; again biased to recent ActiveScan/dashboard work; ADRs 026/027/034/035 not surfaced; the fresh trader-value investigation report was also NOT surfaced despite being committed on this repository days ago.
- `search_project_history`: used during the prior investigation (38 results across 7 queries); reused those findings here.
- Repository fallback: essential — all domain/factory/persistence files read directly.
- Verdict: **PARTIALLY** useful; stable domain chain remains invisible to it.

## Quality gates

| Gate | Result |
|---|---|
| MI tests | 318/318 (305+13) |
| Angular tests | 240/240 (236+4) |
| Angular build | success |
| Prettier / git diff --check | clean |
| Docker rebuild | MI + web recreated, UP |
| Runtime Active Scan | 166 opportunities, all with snapshots |

## Trader value (runtime answers)

- Why did it trigger? Directional price change of -18.18 (SHORT)
- At what price context? 367.05 observed at 22:00:00Z
- When? Detected 21:57:17Z
- Which conditions matched? directional_price_change
- Still time-valid? validUntil 22:30Z

## Known limitations

- Score semantics unchanged (all 100) — separate work item
- Price-based invalidation not implemented (snapshot enables future work)
- Legacy evaluator description embeds validity timestamp (deterministic but verbose)
- TradePlan E2E required manual profile creation in local stack (pre-existing env condition)

## Recommendation

APPROVE for human review and merge.
