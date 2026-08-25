# Repository Analysis — Story 0029

## Investigation confirmation on HEAD (`21b1d52`)

All conclusions from `docs/architecture/reports/trading-opportunity-trader-value-investigation.md` re-verified:

| Finding | Status on HEAD |
|---|---|
| `StrategyMatch.conditionResults` exists (conditionId, passed, observedValue) | ✅ confirmed (`StrategyMatch.java:173`) |
| `TradingOpportunity` has no price reference | ✅ confirmed — 18 fields, none is a price |
| Factory drops condition results | ✅ confirmed — uses 2 of 11 match fields (`matchId`, `direction`) |
| `TradePlan` remains first object with entry/stop/targets | ✅ confirmed (`ExecutionParameters`) |
| `OpportunityResponse` faithful to domain (18 fields, 1:1) | ✅ confirmed |
| UI faithful to API contract | ✅ confirmed |

## Key architectural discovery

The pipeline already persists everything needed for a snapshot **in the same transaction**:

1. **`StrategyEvaluation.explanation()`** — evaluator-produced, deterministic, occurrence-specific description (e.g. "Directional OHLC trend: long with validity until …"). Currently **dropped**: never persisted to StrategyMatch, never reaches Opportunity.
2. **`Observation.evidence[].measurements`** — persisted absolute prices (`highestPrice`, `lowestPrice`). The capability does NOT yet emit the last close; adding `closePrice` to `OhlcRangeAnalysisCapability.calculate()` is one line. Evidence carries `observedAt` (= last candle close time) — the exact timestamp for the reference price.
3. **`match.matchedAt()`** and **`match.conditionResults()`** — available on the persisted match inside `handleMatch()`.
4. `observationId` is NOT lost in practice: the pipeline passes `new ObservationReference(observation.id())` explicitly; only the factory-level drop is nominal.

## Architecture decision: what is the immutable setup snapshot?

> **The immutable setup snapshot owned by TradingOpportunity is the deterministic market context that justified the opportunity at detection time: the observed reference price (+ its observation instant), the evaluator's deterministic setup description, the matched condition facts, and the match instant.**

It records *what was true when the opportunity was created* — never what is true now. Live market state and execution parameters remain outside.

### Concept naming

`OpportunitySetupSnapshot` (domain record) + `OpportunityTrigger` (condition fact). Existing abstractions were evaluated and rejected:
- Reusing `ConditionResult` directly would couple the opportunity domain to the strategy module's internal type; a thin projection keeps boundaries clean while preserving values verbatim.
- No new taxonomy beyond these two records.

### Trigger representation decision — Option A/B hybrid

`ConditionResult.passed` is dropped (a MATCH only persists passed conditions — verified in both evaluators), so triggers carry `{condition, observedValue}`. Condition IDs ("directional_price_change", "range_expansion") are already business-readable and stable across strategies → no renaming taxonomy invented.

### Reference price source decision

**Source**: Observation evidence measurements key `closePrice` (added to capability output), read from the already-loaded `Observation` inside `handleMatch()` — same transaction, same OHLC data the strategy consumed. Timestamp = evidence `observedAt`. **No Market Data call after the fact.**

Nullable by design inside the snapshot (honest absence if a future strategy's evidence lacks price data); present for all opportunities created after this Story.

### Strategy identity decision

`strategyMatchId` is sufficient provenance: it deterministically identifies the immutable match row that stores `strategyId` + `strategyVersion`. Duplicating identity fields on the Opportunity would create redundancy without trader value. Not added.

### Setup description responsibility

Lives in the **StrategyEvaluator** (already does). The factory merely forwards `evaluation.explanation()`. No strategy-specific branching introduced anywhere in shared code. Deterministic + reproducible (no wall-clock injection).

### Persistence decision

Existing JSON payload pattern: `TradingOpportunityEntity` internal record serialized into the `payload` TEXT column. Adding the snapshot field requires **no DB migration**: new rows serialize it; legacy rows deserialize with `setupSnapshot = null` via Jackson default. Dedicated columns unchanged.

### REST contract decision

Additive nested object on `OpportunityResponse`:

```json
"setup": {
  "referencePrice": 64120.5,
  "referencePriceAt": "2026-08-25T20:30:00Z",
  "description": "…",
  "triggers": [{ "condition": "directional_price_change", "observedValue": "-12.5" }],
  "detectedAt": "2026-08-25T20:30:00Z"
}
```

`setup` absent/null for pre-0029 rows. All existing 18 fields untouched.

## Mapping matrix BEFORE → AFTER

| StrategyMatch / evaluation data | BEFORE | AFTER |
|---|---|---|
| direction | preserved | preserved (unchanged) |
| matchId | strategyMatchId | strategyMatchId (unchanged) |
| conditionResults | dropped | `setup.triggers[]` |
| matchedAt | dropped | `setup.detectedAt` |
| evaluation.explanation | dropped | `setup.description` |
| observation evidence closePrice | not produced | capability emits → `setup.referencePrice` |
| evidence.observedAt | unused for opportunity | `setup.referencePriceAt` |
| strategyId/strategyVersion | dropped | still not duplicated (via strategyMatchId lookup) |
| contextDigest / analysisExecutionId / marketId | dropped | still dropped (audit-only, no trader value) |

## Impact surface

- Domain: new records; `TradingOpportunity` +19th nullable constructor arg
- Application: command, factory, builder (create / nextVersion / transition), pipeline handleMatch
- Capability: emit `closePrice`
- Persistence: entity record + mapper (payload JSON)
- REST: response record + new setup DTO
- Angular: model, detail section, list column
- Tests: MI suite (305 baseline), Angular suite (236 baseline)

No Gateway changes (response shape flows through). No Trading Core changes. No DB migration.
