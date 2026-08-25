# Implementation Plan — Story 0029

## Design

```
StrategyEvaluator (owns explanation + conditionResults)
        │ StrategyEvaluation
        ▼
StrategyMatchPersister ──► StrategyMatch (immutable fact, unchanged)
        │
        ▼
ProductionIntelligencePipeline.handleMatch
        │  reads observation.evidence() → closePrice + observedAt   (same tx)
        ▼
StrategyMatchOpportunityFactory
        │  builds OpportunitySetupSnapshot:
        │    referencePrice      ← observation evidence closePrice (nullable)
        │    referencePriceAt    ← evidence.observedAt
        │    description         ← evaluation.explanation()
        │    triggers            ← match.conditionResults() → OpportunityTrigger
        │    detectedAt          ← match.matchedAt()
        ▼
CreateOpportunityCommand (+ setupSnapshot, nullable)
        ▼
OpportunityBuilder → TradingOpportunity (+ setupSnapshot, nullable)
        ▼
JPA payload JSON  ──► OpportunityResponse.setup  ──► Angular detail/list
```

## Steps

### Step 1 — Domain value objects

`domain/opportunity/OpportunityTrigger.java`:

```java
public record OpportunityTrigger(String condition, String observedValue) {
    public OpportunityTrigger { condition = required(condition); } // observedValue nullable-safe
}
```

`domain/opportunity/OpportunitySetupSnapshot.java`:

```java
public record OpportunitySetupSnapshot(
        BigDecimal referencePrice, Instant referencePriceAt,
        String description, List<OpportunityTrigger> triggers, Instant detectedAt) {
    // description required non-blank; triggers non-empty; detectedAt required;
    // price pair: both null or both present; defensive copies
}
```

### Step 2 — TradingOpnowledge aggregate

- Add field `setupSnapshot` (nullable), accessor `Optional<OpportunitySetupSnapshot> setup()`
- Constructor 19th arg placed last; invariant: snapshot allowed null only for legacy rows (no enforcement possible — document)
- Update `OpportunityFactory.create(...)` signature (+1 arg)

### Step 3 — Command + factory

- `CreateOpportunityCommand`: add `OpportunitySetupSnapshot setupSnapshot` component (nullable), no validation tightening (legacy callers in tests may pass null? No — production always passes; keep nullable for compat)
- `StrategyMatchOpportunityFactory.command(...)`: new params `StrategyEvaluation evaluation`, `BigDecimal referencePrice`, `Instant referencePriceAt`; build snapshot internally:

```java
List<OpportunityTrigger> triggers = match.conditionResults().stream()
        .map(c -> new OpportunityTrigger(c.conditionId(), c.observedValue())).toList();
OpportunitySetupSnapshot snapshot = new OpportunitySetupSnapshot(
        referencePrice, referencePriceAt, evaluation.explanation(), triggers,
        match.matchedAt());
```

No strategy branching. `passed` dropped deliberately (MATCH ⇒ all passed).

### Step 4 — Pipeline

`handleMatch`: extract from `observation.evidence()` — latest evidence's `measurements.get("closePrice")` and its `observedAt`. Pass to factory. Evidence list is guaranteed non-empty by Observation invariants.

### Step 5 — Capability

`OhlcRangeAnalysisCapability.calculate()`: add `"closePrice"` = last candle close. Declare `MetricContribution("closePrice")`.

### Step 6 — Builder transitions

- `create` / `nextVersion`: take `command.setupSnapshot()`
- `transition`: copy `previous.setup()` verbatim (historical truth immutable across status changes)

### Step 7 — Persistence

- `TradingOpportunityEntity` record: add `OpportunitySetupSnapshot setupSnapshot`
- Mapper: thread through both directions. Jackson handles nested records; legacy payloads deserialize with null. **No DB migration.**

### Step 8 — REST

- New `OpportunitySetupResponse(UUID-free)` record: `{referencePrice, referencePriceAt, description, triggers:[{condition, observedValue}], detectedAt}`
- `OpportunityResponse`: add `setup` field (null when absent); `from()` maps via `value.setup()`

### Step 9 — Angular

- `opportunity.model.ts`: `OpportunityTrigger`, `OpportunitySetup`, optional `setup?: OpportunitySetup | null` on `OpportunityResponse`
- Detail page: "Setup" section — description, reference price + time (`number`/`date` pipes), trigger list, detectedAt; section hidden when `setup` falsy
- List: add compact "Ref. price" column showing `setup?.referencePrice ?? '—'`

### Step 10 — Tests

MI (baseline 305):
- Domain: snapshot creation, immutability (defensive copies), blank description rejected, empty triggers rejected, price-pair coherence, aggregate round-trip incl. null legacy
- Factory: conditionResults propagated, matchedAt propagated, explanation used as description, lineage id unchanged, deterministic (same inputs twice)
- Persistence: JSON round-trip with snapshot; legacy payload without snapshot deserializes null
- REST: response exposes setup; legacy null setup serializes as null
- Capability: closePrice metric equals last close

Angular (baseline 236):
- model typing, detail renders setup section, legacy hides section, ref-price formatting, list column

### Step 11 — Quality gates & runtime

`mvn test` (MI), `ng test`, `ng build`, prettier, `git diff --check`, Docker rebuild, real Active Scan, verify new opportunity snapshot + legacy opportunity honest absence, verify TradePlan creation still works end-to-end.

## Risk assessment

- Low risk: additive everywhere; payload JSON absorbs the new field without schema change
- Main hazard: constructor arity churn across factory/builder/mapper — mechanical, compiler-guided
- Determinism: evaluator explanations embed validity timestamps computed from inputs (never wall-clock) — reproducible given same inputs
