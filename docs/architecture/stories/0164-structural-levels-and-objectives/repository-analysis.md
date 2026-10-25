# Repository Analysis - Story 0164

## Story

0164 - Structural Levels and Objectives for Market Thesis

## Repository State

| Field | Value |
|---|---|
| Branch | `main` |
| HEAD | `1b90aabb8da54ef994f443d02228629649f4edcc` |
| Remote baseline | `origin/main` points to the same commit |
| Working tree | Story 0164 artifacts are untracked; no production implementation changes |
| Story 0163 | Merged by PR #183; implementation commit `5f129a94` |
| DevLog | Unavailable: Story Agent failed with an upstream provider error |
| Obsidian | Not consulted; repository and accepted ADRs provide the required project context |

The repository is authoritative for the current physical implementation. The
Story is treated as the scope authority after explicit human approval. ADR-076
remains the governing architectural authority.

## Analysis Sources

### Story and architecture

- `docs/architecture/stories/0164-structural-levels-and-objectives/story.md`
- `docs/architecture/stories/0163-market-thesis-composition/story.md`
- `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md`
- `docs/architecture/adr/ADR-020.md`
- `docs/architecture/adr/ADR-025.md`
- `docs/architecture/adr/ADR-027.md`
- `docs/architecture/adr/ADR-028.md`
- `docs/architecture/adr/ADR-034.md`
- `docs/architecture/adr/ADR-038.md`

### Current Market Thesis implementation

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/thesis/MarketThesisComposer.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/thesis/MarketThesis.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/thesis/MarketThesisEvidence.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/thesis/MarketThesisScenario.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/thesis/MarketThesisLevel.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/pipeline/ProductionIntelligencePipeline.java`

### Existing structural authorities

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/marketstructure/MarketStructureEngine.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/marketstructure/MarketStructureResult.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/marketstructure/MarketStructureSwing.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/marketstructure/MarketStructureRelationEvidence.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/trendcontext/TrendContextEngine.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/trendcontext/TrendContextAssessment.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/trendcontext/TrendContextTimeframeAssessment.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/trendcontext/StructuralLevel.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/trendcontext/ProtectedLevel.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/trendcontext/TrendInvalidation.java`

## Current Analytical Pipeline

The current production path is:

```text
Market Data
    -> capability artifacts
    -> Trend Context / Market Structure analysis
    -> Observation consolidation
    -> Market Thesis composition
    -> StrategyEvaluation
    -> StrategyMatch / TradingOpportunity
    -> TradePlan
    -> Risk
    -> human authorization
    -> execution
```

Story 0163 added thesis persistence in `ProductionIntelligencePipeline`.
`persistThesis(...)` composes from the persisted `Observation`, market identity,
analysis execution identity, and cutoff before the strategy loop completes.

The current thesis composer does not directly receive an unrestricted context
object or query Market Data. Its effective input is an Observation containing a
`TrendContextObservationPayload` and generic Observation evidence.

## Existing Structural Evidence

### Market Structure

`MarketStructureResult` is already provider-neutral and carries:

- market/provider/symbol/interval identity;
- cutoff and availability;
- algorithm, rule, policy, and parameter versions;
- input and result fingerprints;
- retained and suppressed swings;
- all structural swings;
- HH, HL, LH, LL and equality relation evidence.

`MarketStructureSwing` preserves pivot and confirmation timestamps, price,
source IDs, evidence windows, and suppression state.

The engine only emits confirmed, closed, non-synthetic, cutoff-valid structural
evidence. It rejects duplicate conflicts, insufficient history, invalid input,
and incompatible evidence. This is sufficient authority for a thesis-level
projection and does not justify a second level engine.

### Trend Context

`TrendContextAssessment` and its role assessments already expose:

- bias/setup/trigger timeframe roles;
- direction, regime, phase, and timeframe alignment;
- retained and suppressed confirmed swings;
- HH/HL/LH/LL relations;
- protected levels;
- structural break status and reclaim information;
- pullback and extension assessment;
- ATR and EMA evidence;
- structural levels;
- structural invalidation;
- freshness and evidence references.

`TrendInvalidation` contains a rule ID, thesis direction, protected level,
triggering candle, invalidation price, and evidence reference. This is a valid
thesis invalidation basis but is not an executable Stop.

## Current Market Thesis Gap

`MarketThesisComposer` currently:

- maps Trend findings to supporting evidence;
- maps Trend contradictions to weakening or blocking contradictions;
- maps Trend invalidations to blocking contradictions;
- maps timeframe assessments to supporting or missing evidence;
- projects `TrendContextTimeframeAssessment.levels()` as generic structural
  `MarketThesisLevel` values;
- projects invalidation prices as generic `INVALIDATION` levels;
- creates a generic directional confirmation condition;
- creates a generic pending structural invalidation condition when no active
  invalidation exists.

It does not currently:

- distinguish protected, invalidation, objective, and obstacle roles in a
  deterministic level policy;
- select a next structural objective;
- identify opposing levels between a reference price and an objective;
- preserve all source-level timestamps and evidence references in the thesis
  level contract;
- expose why a level is relevant to the scenario;
- represent objective or obstacle absence as a structured analytical result.

The existing `MarketThesisLevel` value contains only:

```text
reference, role, timeframe, value
```

The implementation boundary must therefore be evaluated carefully before adding
fields or a new value type. The Story requires richer semantics, but does not
authorize an architectural expansion beyond Market Thesis.

## Proposed Composition Boundary

The smallest coherent implementation boundary is a Market Intelligence
domain/application component that:

1. accepts validated existing Market Structure and Trend Context evidence;
2. verifies market, instrument, timeframe, cutoff, freshness, and rule
   compatibility;
3. derives typed thesis-level roles from existing authoritative facts;
4. selects a directional next objective from existing opposing structural
   levels;
5. identifies intervening opposing levels when a valid reference price exists;
6. emits explicit missingness when objective or obstacle geometry cannot be
   determined;
7. passes structured results into the existing immutable Market Thesis model.

The component must not recalculate swings, relations, breaks, reclaims, or
protected levels. It must not fetch provider data directly.

## Objective and Obstacle Semantics

The repository supports a bounded structural policy:

- For a long scenario, an objective may be the nearest valid opposing high
  above the reference price, subject to the approved timeframe and freshness
  policy.
- For a short scenario, an objective may be the nearest valid opposing low
  below the reference price, subject to the same policy.
- Other opposing structural levels between the reference price and objective
  are obstacles.
- A protected level that invalidates the scenario is an invalidation basis, not
  an objective or executable Stop.
- No level may be promoted to an objective solely because it is numerically
  distant or because it resembles a liquidity pool.

The exact selection policy is an implementation-plan decision. It must be
explicit, deterministic, provenance-preserving, and covered by representative
bullish, bearish, missing, and conflicting cases.

## Reference Price Dependency

Obstacle ordering and distance require a valid scenario reference price. The
current Market Thesis composer does not directly consume a market snapshot.

Implementation planning must decide whether an already-authoritative,
provider-neutral cutoff price can be carried into the composition input without
changing Market Data ownership. If no valid reference price is available, the
implementation must preserve an explicit unavailable/unknown result rather than
derive one from an unrelated timestamp or structural level.

This is the primary open implementation decision. It is not a reason to add a
provider adapter or cTrader dependency.

## Authority and Compatibility Analysis

### Market Data

Remains authoritative for normalized prices, market identity, source freshness,
and any reference quote. No Market Data analyzer semantics need to change.

### Market Intelligence

Owns the deterministic composition of existing structural facts into the
Market Thesis scenario and evidence model.

### Market Structure and Trend Context

Remain authoritative for their existing typed semantics. Story 0164 should use
their outputs and provenance rather than duplicate or reinterpret their engines.

### TradePlan

May later copy thesis objective/invalidation context into immutable rationale,
but Story 0164 must not change executable Entry, Stop, Target, expiration, or
planning policy.

### Risk

Remains the financial authorization authority. Structural objective and
invalidation are analytical context only.

### StrategyMatch and TradingOpportunity

Remain unchanged. Structural objective evidence must not create or rank an
Opportunity.

### Human authority

Remains required before execution. `CANDIDATE` remains analytical state only.

## Data and Provider Assessment

The selected capability works with existing generic OHLC-derived structural
evidence. It does not require:

- cTrader Market Data;
- cTrader quotes;
- cTrader DOM;
- external reference-market data;
- volume semantics;
- AI interpretation.

The capability should therefore be developed provider-neutrally and must not be
blocked on the cTrader integration.

## Risks

1. A new level value could accidentally become a second market-structure
   authority. Mitigation: consume existing typed results and retain source
   fingerprints.
2. Objective selection could invent a target or liquidity claim. Mitigation:
   select only existing compatible structural levels and use explicit absence.
3. Structural invalidation could be confused with an executable Stop. Mitigation:
   preserve separate thesis role and downstream planning boundaries.
4. A missing reference price could lead to fabricated obstacle distances.
   Mitigation: represent distance and ordering as unavailable.
5. Opposing levels could be treated as a universal score. Mitigation: preserve
   typed evidence and contradiction severity without confidence averaging.
6. Timeframe mixing could create misleading objectives. Mitigation: require an
   explicit timeframe policy and provenance compatibility.
7. State changes could accidentally become Risk gating. Mitigation: preserve
   ADR-076 analytical state semantics and add negative boundary tests.

## Open Implementation Decisions

The Implementation Plan must resolve:

- whether richer level semantics extend `MarketThesisLevel` or introduce a
  focused thesis value object;
- the exact source priority across Trend Context roles and Market Structure
  intervals;
- the compatible reference-price source and its freshness requirements;
- objective selection for bullish and bearish scenarios;
- whether an intervening obstacle is context-only, weakening, or blocking under
  the existing thesis policy;
- how objective and obstacle absence are represented;
- how source timestamps and evidence references are serialized and persisted;
- whether any read-contract compatibility changes are required.

These are implementation decisions within ADR-076 unless they change authority
or executable planning semantics.

## Blockers and Stop Conditions

No blocker currently prevents Implementation Planning.

Implementation must stop and request renewed architectural guidance if it
requires:

- modifying Market Structure or Trend Context semantics;
- creating a competing structure or level engine;
- adding provider-specific or cTrader contracts to Market Thesis;
- inventing liquidity or reference-market facts;
- changing executable Stop/Target, TradePlan, Risk, or execution behavior;
- changing `FeatureProjectionV1` or S1 semantics;
- adding AI or a universal confidence score;
- introducing a new service or authority boundary.

## Validation Baseline

The affected module is `market-intelligence`.

The current baseline validation was previously executed successfully:

```text
mvn -f market-intelligence/pom.xml test
756 tests passed
git diff --check
passed
```

Implementation-specific validation must add focused coverage for:

- bullish and bearish objective selection;
- protected level and invalidation role composition;
- obstacle ordering and absence;
- incompatible provenance and stale evidence;
- missing reference price;
- deterministic fingerprints and replay;
- immutable thesis persistence;
- no changes to Risk, execution, Opportunity, TradePlan, or research
  semantics.

## Repository Analysis Conclusion

Story 0164 is implementable within the approved ADR-076 boundary. The
repository already contains sufficient deterministic structural facts and
provenance to implement the capability without cTrader Market Data or a new
analyzer. The highest-risk design point is selecting and validating a reference
price for objective/obstacle ordering; absence must remain explicit when that
input is unavailable.

The next permitted workflow stage is an Implementation Plan. This Repository
Analysis does not authorize implementation and does not constitute approval of
the future plan.
