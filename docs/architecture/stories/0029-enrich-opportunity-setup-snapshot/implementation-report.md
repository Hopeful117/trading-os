# Implementation Report — Story 0029

**Date**: 2026-08-25
**Branch**: `feature/story-0029-enrich-opportunity-setup-snapshot`

## Summary

`TradingOpportunity` now persists a deterministic `OpportunitySetupSnapshot` at detection time: reference price (+ observation instant), evaluator-owned setup description, matched trigger facts, and the match instant. Legacy opportunities deserialize with a null snapshot and are handled honestly by the UI.

## Changes

### Domain (`domain/opportunity/`)
- **NEW** `OpportunityTrigger` record — `{condition, observedValue}`; blank observedValue normalized to null
- **NEW** `OpportunitySetupSnapshot` record — `{referencePrice, referencePriceAt, description, triggers, detectedAt}`; price pair must be both-null-or-both-present; ≥1 trigger required; defensive copies
- `TradingOpportunity` +19th nullable constructor arg; accessor `Optional<OpportunitySetupSnapshot> setup()`
- `OpportunityFactory.create(...)` signature extended

### Application
- `CreateOpportunityCommand` + `setupSnapshot` component (nullable)
- `StrategyMatchOpportunityFactory.command(...)`: new params (evaluation, referencePrice, referencePriceAt); builds snapshot from `match.conditionResults()` → triggers, `evaluation.explanation()` → description, `match.matchedAt()` → detectedAt. No strategy branching.
- `ProductionIntelligencePipeline.handleMatch`: extracts `closePrice` + evidence `observedAt` from the already-loaded Observation (same transaction — never a retroactive Market Data call)
- `OhlcRangeAnalysisCapability`: emits new `closePrice` metric (= last candle close)
- `OpportunityBuilder`: create/nextVersion take command snapshot; **transition copies previous snapshot verbatim** (historical truth immutable)

### Persistence
- `TradingOpportunityEntity` internal record + mapper thread the snapshot through the existing payload JSON column. **No DB migration needed**; legacy payloads deserialize with null.

### REST
- **NEW** `OpportunitySetupResponse` (+ nested `Trigger`) with `Optional`-based factory
- `OpportunityResponse`: additive `setup` field (null for legacy rows); all 18 historical fields unchanged

### Angular
- `opportunity.model.ts`: `OpportunityTrigger`, `OpportunitySetup`, optional `setup?`
- Detail page: "Setup at detection" section — reference price + observed time, description, matched conditions list, detected time; hidden when setup absent
- List: compact "Ref. price" column with honest "—" fallback

## Tests

| Suite | Baseline | Final |
|---|---|---|
| Market Intelligence | 305 | **318** (+13) |
| Angular | 236 | **240** (+4) |

New MI coverage: snapshot validation (price pair coherence, blank fields, defensive copies), factory propagation (conditionResults/matchedAt/explanation/reference price), determinism (identical inputs → identical snapshot), generic strategy acceptance (fake strategy), payload JSON round-trip, legacy-payload null deserialization, transition preservation, REST projection (new + legacy).
New Angular coverage: detail setup section render, triggers with/without observedValue, legacy section hidden, list ref-price cell.

## Quality gates

- [x] `mvn test` (MI): 318/318
- [x] `ng test`: 240/240
- [x] `ng build`: success (pre-existing budget warning)
- [x] Prettier: clean
- [x] `git diff --check`: clean

## Runtime validation (Docker rebuild + real Active Scan)

Scan `aee1ad35-bfbe-4702-9219-cd9ac3c945cf` (166 markets). Results:

1. **New opportunity** `fc93bea6…` (BCH/AUD SHORT):
   ```json
   "setup": {
     "referencePrice": 367.05,
     "referencePriceAt": "2026-08-25T22:00:00Z",
     "description": "Directional OHLC trend: short with validity until 2026-08-25T22:30:00Z (15m)",
     "triggers": [{ "condition": "directional_price_change", "observedValue": "-18.18" }],
     "detectedAt": "2026-08-25T21:57:17.679667886Z"
   }
   ```
2. **Legacy opportunity** `c812ade6…` (EXPIRED, pre-0029): `"setup": null` — no crash, honest absence.
3. **TradePlan non-regression**: plan `29fec077…` created from the new opportunity — entry MARKET @ **369.42** (live market price ≠ snapshot 367.05, proving the Planning Engine still uses live data), stop 373.1142, target 362.0316, R:R 2.0.
4. Environment note: plan creation initially failed because the local account had no Trade Planning Profile (pre-existing data condition, unrelated to this Story); resolved by creating/assigning an EUR profile via Trading Core's direct port.

## Score semantics

Unchanged — score remains observation-confidence×100 (=100 for all current opportunities); separate work item.

## Price-based invalidation

Not implemented. The snapshot now provides the data substrate (reference price + timestamp) that a future contextual invalidation engine would compare against.
