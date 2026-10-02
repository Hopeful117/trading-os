# Story 0065 - Repository Analysis Audit

> This audit is a supporting historical analysis for the canonical Story 0065
> at `docs/architecture/stories/0065-trend-context-strategy-boundary/`.
> It is not a separate Story and does not define a second scope or status.

## 1. Executive Summary

The repository already contains a partial Trend Context to Strategy integration.
It is not part of Story 0064's implementation commit. The integration code,
`ConservativeTrendFollowingEvaluator`, the provenance type, and the draft
Story 0065 boundary documents were introduced together in commit `74b74ed`
(`chore: preserve pending trading os work`, 2026-10-02). That commit preserved
work which had previously been uncommitted. It is therefore best classified as
unfinished Story 0065 work, not incidental Story 0064 work and not a separate
accepted architectural decision.

The generic Strategy architecture is sufficient. No structural redesign is
required. The current implementation does, however, contain a confirmed
provenance defect: `ProductionIntelligencePipeline` evaluates using the
selected `strategyEvidence`, but on MATCH passes the original legacy OHLC
`observation` to `handleMatch`. Consequently the persisted `StrategyMatch`,
`ObservationReference`, validity window, reference price, and pipeline-run
observation projection can identify the wrong evidence.

The existing Conservative Trend Following definition is disabled and
unvalidated. Its current implementation is primarily an integration prototype,
not an FTMO Swing strategy. It also treats favorable Trend Context as a
complete setup because it has no distinct entry-trigger requirement. That is a
product/strategy semantics gap, not a reason to invent trigger rules in this
analysis.

**Classification summary:**

- **CONFIRMED:** Stories 0062-0064 provide typed Trend Context input, a pure
  engine, and a persisted `TREND_CONTEXT` observation.
- **CONFIRMED:** The Strategy domain already provides the required generic
  evaluation, match, and opportunity boundaries.
- **CONFIRMED / DEFECT:** MATCH promotion currently substitutes the original
  OHLC observation for the evidence used by evaluation.
- **PARTIAL:** Typed Trend Context facts reach the evaluation context, but the
  full evidence identity is not carried as a first-class field beyond the
  context digest and the pipeline argument.
- **GAP:** The current Trend Context Strategy path has no explicit trigger
  semantic.
- **DOCUMENTATION DRIFT:** Story 0064 correctly declares StrategyEvaluation,
  StrategyMatch, and TradingOpportunity out of scope, while the later commit
  contains a partial implementation of that next boundary.

## 2. Current Runtime Flow

Story 0064's implemented path is:

```text
Market Data
  -> TrendContextAssessmentInput
  -> TrendContextEngine
  -> TrendContextAnalysisCapability
  -> TrendContextCapabilityContent
  -> TrendContextObservationRule
  -> immutable Observation(type=TREND_CONTEXT)
```

The relevant implementation is in:

- `TrendContextAnalysisCapability` and `TrendContextObservationRule`;
- `TrendContextObservationPayload`;
- `TrendContextCapabilityContent`;
- `ObservationBuilder` and the existing observation persistence adapters.

The later strategy path is:

```text
ProductionIntelligencePipeline
  -> persisted eligible StrategyDefinition
  -> TrendContextEvidenceSelector when required inputs use TREND_CONTEXT_*
  -> StrategyEvaluationContextFactory.resolve(...)
  -> LiveStrategyEvaluationRunner
  -> StrategyEvaluationService
  -> StrategyEvaluator
  -> StrategyEvaluation
  -> StrategyMatchPersister on MATCH only
  -> StrategyMatchOpportunityFactory
  -> OpportunityEngine
  -> TradingOpportunity
```

`ProductionIntelligencePipeline.execute` first builds the legacy OHLC
observation (`ProductionIntelligencePipeline.java:114-124`), loads persisted
definitions, applies governance and legacy observation applicability
(`:126-143`), and then selects evidence per definition (`:146-152`).

This preserves the intended authority chain from ADR-034 and ADR-048:
Observation describes state, StrategyEvaluator owns matching, StrategyMatch is
the immutable match fact, and TradingOpportunity derives from that fact. The
implementation currently fails to preserve the selected Observation identity
at the promotion boundary, as detailed below.

## 3. Existing Strategy Domain Assets to Reuse

The existing architecture is sufficient for this boundary:

- `StrategyDefinition` provides versioned identity, applicability, required
  semantic inputs, parameters, validation status, and operational status.
- `StrategyEvaluationContext` is an infrastructure-free typed semantic
  container with deterministic SHA-256 digest (`strategy/domain/StrategyEvaluationContext.java`).
- `StrategyEvaluator` remains the pure `(definition, context) -> evaluation`
  boundary.
- `StrategyEvaluation` exposes `MATCH`, `NO_MATCH`, `NOT_EVALUABLE`, and
  `FAILED`; direction exists only for `MATCH`.
- `StrategyMatch.fromEvaluation` rejects every non-MATCH status and stores the
  evaluation context digest (`strategy/domain/StrategyMatch.java:79-108`).
- `StrategyMatchPersister` is idempotent and persists only successful matches
  inside the pipeline transaction (`strategy/application/StrategyMatchPersister.java:39-64`).
- `StrategyMatchOpportunityFactory` is strategy-agnostic and projects a
  persisted match into a generic opportunity command.
- `OpportunitySetupSnapshot` already preserves detection-time description,
  triggers, detection time, and optional reference price; it explicitly forbids
  retroactive price lookup (`domain/opportunity/OpportunitySetupSnapshot.java:8-27`).

These assets directly satisfy ADR-034 and ADR-035. A new Strategy architecture,
new evaluation table, or Trend Context-specific opportunity path is not needed.

## 4. Existing Trend Context -> Strategy Integration

The following code is already present:

- `BuiltinStrategies` declares `CONSERVATIVE_TREND_FOLLOWING_V1` and ten
  `TREND_CONTEXT_*` inputs (`BuiltinStrategies.java:74-92`).
- `TrendContextEvidenceSelector` selects an active typed observation only when
  the latest market execution contains the Trend Context capability, completed
  successfully, the observation is active, valid at `evaluatedAt`, and its
  `cutOffAt` is not in the future (`TrendContextEvidenceSelector.java:32-70`).
- `StrategyEvaluationContextFactory.resolve` detects a typed
  `TrendContextObservationPayload`, maps its assessment fields, and creates
  `StrategyEvidenceProvenance` (`StrategyEvaluationContextFactory.java:42-75`).
- `StrategyEvaluationContext` includes provenance in equality, hash code, and
  digest (`StrategyEvaluationContext.java:85-119`).
- `ConservativeTrendFollowingEvaluator` checks all declared inputs and
  provenance before evaluating explicit context criteria.
- `ProductionIntelligencePipeline` keeps the legacy observation for legacy
  definitions and selects Trend Context evidence for definitions declaring
  `TREND_CONTEXT_*` inputs.

This is a bounded extension of the generic context architecture, consistent
with ADR-035 I-3 and I-4 in principle. The implementation has not yet
completed the promotion/provenance contract.

## 5. Git and History Explanation

The history separates the work clearly:

| Evidence | Repository fact | Classification |
|---|---|---|
| Story 0062 | `53f2db8` and related Story 0062 work establish the dedicated input contract and mapping | prerequisite |
| Story 0063 | `05711e6` implements the pure deterministic Trend Context engine | prerequisite |
| Story 0064 | `3ed32b0` integrates the capability and persists typed observations | Story 0064 implementation |
| Story 0064 scope | Its `story.md:77-87` explicitly excludes StrategyEvaluation, StrategyMatch, and TradingOpportunity | authoritative scope boundary |
| Story 0065 work | `74b74ed` adds the strategy selector, semantic mapping, provenance, conservative evaluator, pipeline wiring, tests, and `docs/architecture/stories/0065-trend-context-strategy-boundary/*` | unfinished 0065 work |

No ADR in ADR-034 through ADR-038 records the concrete Trend Context semantic
keys or this implementation. ADR-048 does record the cross-cutting authority
chain and explicitly states that a favorable assessment cannot directly create
a StrategyMatch or TradingOpportunity (`ADR-048.md:177-210`). The code in
`74b74ed` is therefore an implementation attempt guided by those invariants,
not a separately accepted decision.

The current repository also contains the canonical Story 0065 at
`docs/architecture/stories/0065-trend-context-strategy-boundary/story.md`.
Its acceptance criteria and implementation report remain subject to human
review. This audit is retained under the canonical Story directory as
supporting historical analysis.

## 6. Provenance Audit

### 6.1 Context construction

`StrategyEvaluationContextFactory.provenanceOf` captures:

- selected observation ID;
- observation lineage ID and version;
- Trend Context cut-off;
- profile and rule versions;
- input and assessment fingerprints.

The digest includes this provenance and all semantic inputs. This makes two
contexts with different selected observation provenance distinguishable even if
their flattened semantic values are equal. This part is **CONFIRMED / PARTIAL**:
the digest is provenance-safe for identity, but `StrategyEvaluation` exposes
only the digest, not the structured provenance.

### 6.2 Evaluation and match

`StrategyEvaluation` copies `context.digest()` but not the structured
`StrategyEvidenceProvenance` (`strategy/domain/StrategyEvaluation.java:114-127`).
`StrategyMatch` then copies that digest and receives an `observationId` from its
caller (`StrategyMatch.java:84-108`). The domain permits the caller to supply
an observation ID unrelated to the context digest. That is a latent boundary
weakness even though the digest can reveal the mismatch indirectly.

### 6.3 Confirmed substitution defect

In `ProductionIntelligencePipeline.execute`:

```java
Observation strategyEvidence = trendContextEvidence
        .select(marketId, definition.requiredInputs(), evaluatedAt)
        .orElse(observation);

var evaluation = strategyEvaluation.evaluate(
        definition, strategyEvidence, marketId, evaluatedAt);
```

The evaluation uses `strategyEvidence`. But the MATCH branch calls:

```java
handleMatch(evaluation, definition, analysisExecutionId, observation,
        instrument, mode);
```

`handleMatch` then passes that original `observation` to both
`StrategyMatchPersister.persist(..., observation.id())` and the opportunity
command (`ProductionIntelligencePipeline.java:190-209`). This is a confirmed
**DEFECT**:

- `StrategyEvaluationContext` can be based on a Trend Context observation;
- `StrategyMatch.observationId` can nevertheless point to the legacy OHLC
  observation;
- `TradingOpportunity.observations` receives the legacy observation reference;
- the setup snapshot's price and timestamp are extracted from the legacy
  observation;
- `PipelineRun.complete` also records the original observation ID/version
  (`ProductionIntelligencePipeline.java:178-186`).

The strategy match identity remains deterministic because it uses the context
digest, but the separately stored observation reference is not guaranteed to
refer to the evidence that produced that digest. This violates the intended
provenance relationship without requiring a new identity model.

### 6.4 Selector limitations

`TrendContextEvidenceSelector` uses `findLatestByMarketId`, then selects an
active observation created at or after that execution's completion. It does not
carry an explicit supporting `AnalysisExecution` ID in the selected result, nor
does it compare the selected observation's evidence execution ID to the current
pipeline `analysisExecutionId`. This is **PARTIAL** provenance protection:
status, validity, market, and cut-off are checked, but the exact execution
association is inferred through timestamps.

It also selects the latest active observation by `createdAt`, not by a
deterministically explicit observation-to-execution relation. That should be
resolved or explicitly accepted in Story 0065; it must not be silently treated
as equivalent to exact execution provenance.

## 7. TradingOpportunity Snapshot Audit

Story 0029 requires the setup snapshot to preserve deterministic detection-time
evidence and forbids retroactive Market Data reconstruction. The generic factory
does preserve:

- evaluator explanation as `description`;
- persisted match condition results as `triggers`;
- `StrategyMatch.matchedAt` as `detectedAt`;
- the supplied reference price and its observation timestamp;
- `validUntil` supplied by the caller.

For a Trend Context match, the current caller is wrong:

- `ObservationReference` points to the legacy OHLC observation, not the selected
  Trend Context observation;
- `referencePriceOf` reads `closePrice` from the same original OHLC observation;
- `validFrom` and `validUntil` come from the original OHLC observation;
- Trend Context observation validity is not propagated into the opportunity;
- `TrendContextObservationRule` creates empty measurement maps, so the typed
  Trend Context observation itself cannot supply a close price through the
  current generic `referencePriceOf` implementation.

The setup description and trigger facts are correctly derived from the
evaluation/match, but the evidence reference, price context, and validity
window can describe a different observation. This is a **DEFECT**, not a reason
to reconstruct Market Data or to copy the complete Trend Context payload into
the opportunity.

The architectural options are bounded:

1. Use the selected evidence consistently for match persistence and opportunity
   projection, and define how a price-less Trend Context observation is
   represented in the existing optional snapshot price fields.
2. Preserve a separate reference to the source price evidence only if the
   existing Observation contract can provide it without retroactive lookup.
3. Reject a Trend Context match when the required opportunity snapshot cannot
   be formed, if that is the accepted product contract.

Choosing among these options requires human approval of the meaning of a
price-less Trend Context setup. Story 0065 should not invent a price or an
entry trigger.

## 8. ConservativeTrendFollowing Audit

### Definition

`BuiltinStrategies.conservativeTrendFollowing()` creates version 1 with:

- asset class: `CRYPTO`;
- timeframe: `M15`;
- provider: `KRAKEN`;
- direction: `DYNAMIC`;
- scenario: `TREND_CONTEXT`;
- no parameters;
- default governance from `StrategyDefinition.create`, which is
  `UNVALIDATED + DISABLED`.

The definition is included in `BuiltinStrategies.all()` and seeded by
`BuiltinStrategyBootstrap`, but existing persisted governance wins and the
definition is not enabled by bootstrap. Under ADR-036/037/038 it can participate
in production evaluation only after an explicit persisted governance change to
an eligible state. The ordinary `ENABLED` state also requires validation
evidence; the controlled-run exception is reserved for the legacy strategy.

### Required semantic inputs

The evaluator declares and requires:

`ATTENTION`, `DIRECTION`, `REGIME`, `PHASE`, `ALIGNMENT`, `HARD_EXCLUSION`,
`MATERIAL_CONTRADICTION`, `INVALIDATION`, `CUTOFF_AT`, and `VALID`, all under
the `TREND_CONTEXT_` namespace. The first eight categorical/boolean values are
encoded as strings; `CUTOFF_AT` is an `INSTANT`; `VALID` is a boolean-like
string.

### Evaluation semantics

- Missing provenance or any required input: `NOT_EVALUABLE`.
- Attention must be `CONTEXTUALLY_ATTRACTIVE`.
- Direction must be `UP` or `DOWN`; `NEUTRAL` and `UNKNOWN` do not match.
- Regime must be `TRENDING`.
- Phase must be `DIRECTIONAL` or `PULLBACK`.
- Alignment must be direction-compatible.
- Hard exclusions, material contradictions, and analytical invalidation must
  all be absent.
- The encoded validity must be true and the provenance cut-off must not be after
  evaluation time.
- Any failed condition: `NO_MATCH` with condition results and the declared
  required inputs.
- All conditions pass: `MATCH`, with `UP -> LONG` and `DOWN -> SHORT`,
  confidence `1`, and the evaluator explanation.

The semantics are deterministic and do not use wall-clock time, AI, Risk, or
execution. However, they treat favorable context plus context-state filters as
a complete setup. There is no trigger input or trigger condition. This is an
**GAP: PRODUCT / STRATEGY SEMANTICS**, not an implementation authorization to
invent entry-trigger rules.

### Recommendation

Based on the current repository architecture, this definition should remain an
integration prototype/technical fixture for the Trend Context boundary (option
A), not the final FTMO Swing strategy and not a strategy to enable based on
tests. It should not be removed: it proves that the existing generic Strategy
domain can consume typed deterministic evidence. It should not be silently
evolved into the FTMO strategy: the intended 4H/1H/15m hierarchy and entry
trigger semantics require a separate product/strategy decision and versioned
Story.

## 9. Trend Context Semantic-Input Contract Audit

Using `StrategyEvaluationContext` as the translation boundary is architecturally
correct. ADR-035 explicitly defines the context as infrastructure-free and
extensible through `RequiredSemanticInput` keys. Typed `SemanticValue` already
supports decimal, integer, string, instant, and duration values.

The current implementation is nevertheless only **PARTIAL** as a semantic
contract:

- `StrategyEvaluationContextFactory` branches on the string prefix
  `TREND_CONTEXT_` and then on literal key strings. This is not a Strategy-ID
  switch, so it preserves generic orchestration, but it couples the generic
  factory to a concrete semantic namespace.
- Direction, regime, phase, alignment, attention, and boolean flags are all
  flattened into strings. This is compatible with the generic architecture, but
  weakly typed: enum spelling changes become runtime non-matches.
- `VALID` is stored as a string rather than a boolean because the generic
  context has no boolean `SemanticValue` type.
- Unknown observation keys are silently skipped by the generic resolver, with
  the evaluator later producing `NOT_EVALUABLE`. That is safe, but diagnostics
  are indirect.
- `firstTimeframe(definition)` assigns one M15-style context timeframe while a
  Trend Context assessment contains role intervals. The current prototype's
  M15 applicability makes this pass, but it does not model the full 4H/1H/15m
  horizon for a future Swing strategy.

No evidence requires redesigning the entire Strategy context system. The
minimum boundary should keep the generic container and make the typed mapping,
consistency checks, and provenance relationship explicit. A future strategy
Story may decide whether stronger enum/boolean typing is worth a versioned
context-contract change.

## 10. Product / Strategy Semantics Gaps

The intended distinction is:

```text
Trend Context       -> favorable / interesting market context
Strategy             -> deterministic setup including required trigger
TradingOpportunity   -> setup deserving trader attention
TradePlan            -> concrete proposed trade
Risk                 -> deterministic permission or rejection
```

The repository and ADR-048 preserve the first and last boundaries. The
Conservative evaluator currently collapses the first two: a contextually
attractive, directional, trending, aligned, non-invalidated context becomes a
Strategy `MATCH` without a distinct entry trigger. Therefore:

- a favorable Trend Context alone still does not create a match, because a
  matching StrategyDefinition and evaluator are required;
- once this prototype definition is eligible, its current criteria make the
  context effectively sufficient for a match;
- the resulting opportunity is therefore not yet evidence of a complete FTMO
  Swing entry setup.

This must be documented as a semantics gap and deferred strategy decision, not
fixed by adding speculative trigger rules to Story 0065.

## 11. FTMO Swing Compatibility Gaps

The current code is not an FTMO Swing implementation:

- applicability is `CRYPTO` / `M15` / `KRAKEN`, not the intended multi-timeframe
  hierarchy;
- one `StrategyEvaluationContext.timeframe` does not represent 4H bias, 1H
  setup, and 15m trigger roles;
- no entry trigger is declared or evaluated;
- no FTMO-specific risk rules, account constraints, or execution policy are
  present, as required by scope;
- no empirical validation evidence exists for this strategy;
- no strategy ranking, backtesting, or profitability claim is justified.

These are **FUTURE DECISIONS** for a following FTMO Swing strategy Story, not
blockers that should be solved through an unapproved strategy redesign here.

## 12. Architecture Invariants Story 0065 Must Preserve

- Observation remains reusable market-state evidence and does not decide a
  strategy match (ADR-034, ADR-035 I-1/I-2).
- StrategyEvaluator remains the sole authority for MATCH, NO_MATCH,
  NOT_EVALUABLE, and FAILED.
- `StrategyMatch` is immutable and created only from `MATCH`.
- `TradingOpportunity` derives only from a persisted `StrategyMatch`.
- The exact Observation used to build the evaluation context must be the
  provenance reference used for match and opportunity projection.
- Trend Context calculations remain in Trend Context; Strategy consumes typed
  evidence and does not read raw Market Data or rerun the engine.
- Governance remains domain-owned and persisted: no new definition is enabled
  or validated by this work (ADR-036, ADR-037, ADR-038).
- A Trend Context attention outcome never bypasses StrategyEvaluation.
- No Risk, TradePlan, execution, broker, AI, ranking, or FTMO-specific rule is
  introduced.
- Detection-time snapshot values must not be reconstructed retroactively from
  Market Data (Story 0029).

## 13. Concrete Blockers Story 0065 Must Resolve

1. **Evidence identity substitution (DEFECT):** pass the selected evidence
   consistently through match persistence, opportunity projection, validity,
   reference, and pipeline completion; otherwise the digest and stored
   observation provenance disagree.
2. **Price-less Trend Context snapshot contract:** decide how the existing
   optional reference-price fields behave when the selected typed observation
   has no `closePrice` measurement. Do not invent a price.
3. **Validity propagation:** ensure the opportunity validity window corresponds
   to the evidence used for evaluation, not the legacy fallback observation.
4. **Exact execution association:** determine whether selection must bind to the
   current analysis execution rather than infer association from latest-market
   timestamps.
5. **Regression coverage:** add tests that assert the selected Trend Context
   observation ID is used in `StrategyMatch`, `ObservationReference`, setup
   snapshot inputs, and run projection. No current pipeline test asserts this.

These are the minimum truth-restoring issues. A new StrategyEvaluation table or
new opportunity aggregate is not a blocker.

## 14. Explicit Non-Goals

- Do not design the final FTMO Swing strategy.
- Do not choose 4H/1H/15m trigger formulas or thresholds.
- Do not change Trend Context algorithms, profiles, or input contracts.
- Do not add AI or ML strategy authority.
- Do not add automated execution, broker integrations, or FTMO risk rules.
- Do not add backtesting, ranking, or scoring redesign.
- Do not change validation or operational governance states.
- Do not create a direct observation-to-opportunity path.
- Do not redesign the generic Strategy context architecture without evidence.

## 15. Recommended Story 0065 Boundary

Story 0065 should be a focused provenance-safe integration story:

1. Preserve the existing generic Strategy architecture and typed observation
   adapter.
2. Define the exact current-valid Trend Context evidence selection contract,
   including execution association and cut-off/validity rules.
3. Ensure the selected evidence object remains the single source for evaluation,
   StrategyMatch observation reference, opportunity observation reference,
   validity, and detection-time snapshot inputs.
4. Preserve the existing status semantics and persist matches only for MATCH.
5. Keep `ConservativeTrendFollowingV1` disabled and unvalidated, treating it as
   a technical integration prototype.
6. Add focused provenance and snapshot regression tests, including missing,
   stale, expired, future-cutoff, no-price, NO_MATCH, NOT_EVALUABLE, and replay
   cases.

The Story should not include the final Swing strategy definition. If the product
requires a trigger before any prototype match can be considered meaningful, that
requirement should be stated as a deferred decision or handled in the following
strategy Story rather than invented here.

## 16. Open Decisions Requiring Human Approval

- Should a Trend Context match be allowed to create an opportunity when its
  observation has no reference close price, or must the snapshot contract be
  extended with another existing deterministic reference?
- Must evidence selection be bound to the current `analysisExecutionId`, or is
  latest completed same-market Trend Context evidence an accepted reuse policy?
- Should structured Trend Context provenance be carried explicitly in
  `StrategyEvaluation`/`StrategyMatch`, or is digest plus ObservationReference
  sufficient after the substitution defect is corrected?
- Is the current string representation of Trend Context semantic inputs
  acceptable for V1, or should enum/boolean values receive a typed contract in
  a later version?
- Is `ConservativeTrendFollowingV1` explicitly approved as an integration
  fixture, and should its name/description state that it has no entry trigger?
- Should the next FTMO Swing Story own the 4H/1H/15m applicability and trigger
  model rather than extending this boundary Story?

## 17. Explicit Answers

### Q1. Is the existing Strategy architecture sufficient for Story 0065 without a structural redesign?

**Yes.** `StrategyDefinition`, `StrategyEvaluationContext`, the evaluator
registry, `StrategyEvaluation`, `StrategyMatchPersister`, and the generic
match-to-opportunity factory already express the required authority chain.
Only evidence selection, provenance consistency, snapshot semantics, and tests
need bounded work.

### Q2. Is the current Trend Context -> Strategy implementation provenance-safe?

**No.** Context provenance is included in the digest, but the MATCH promotion
path passes the original OHLC observation rather than the selected Trend Context
observation. This can make `StrategyMatch.observationId` and downstream
`TradingOpportunity` evidence/reference data incorrect.

### Q3. Should CONSERVATIVE_TREND_FOLLOWING_V1 be treated as production strategy semantics or primarily as an integration prototype at this stage?

**Primarily as an integration prototype.** It is technically wired and
deterministic, but remains `UNVALIDATED + DISABLED`, is restricted to
`CRYPTO/M15/KRAKEN`, and lacks a distinct entry trigger. It must not be
transformed into the FTMO Swing strategy in this Story.

### Q4. What is the minimum repository change required to complete Story 0065 truthfully?

**Restore one evidence identity through the full MATCH path**, correct the
snapshot/validity behavior for the selected observation including the no-price
case, define exact execution association, and add regression tests proving
`TrendContextObservation -> context digest -> StrategyMatch -> ObservationReference
-> OpportunitySetupSnapshot` consistency. Keep governance unchanged.

### Q5. What decisions should be deferred to the following FTMO Swing strategy Story rather than included in 0065?

Defer the 4H/1H/15m strategy applicability model, entry-trigger definition,
setup semantics beyond Trend Context, FTMO-specific risk/account rules,
performance validation criteria, and any ranking or execution behavior. Also
defer a stronger enum/boolean semantic-input redesign unless the human engineer
decides it is required for the boundary fix.

## Source References

- `docs/architecture/adr/ADR-034.md`
- `docs/architecture/adr/ADR-035.md`
- `docs/architecture/adr/ADR-036.md`
- `docs/architecture/adr/ADR-037.md`
- `docs/architecture/adr/ADR-038.md`
- `docs/architecture/adr/ADR-048.md`
- `docs/architecture/stories/0029-enrich-opportunity-setup-snapshot/story.md`
- `docs/architecture/stories/0062-trend-context-input-contract/story.md`
- `docs/architecture/stories/0063-deterministic-trend-context-engine/story.md`
- `docs/architecture/stories/0064-trend-context-observation-integration/story.md`
- `docs/architecture/stories/0065-trend-context-strategy-boundary/story.md`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/pipeline/ProductionIntelligencePipeline.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/strategy/application/TrendContextEvidenceSelector.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/strategy/application/StrategyEvaluationContextFactory.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/strategy/application/BuiltinStrategies.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/strategy/domain/StrategyEvaluation.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/strategy/domain/StrategyMatch.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/opportunity/StrategyMatchOpportunityFactory.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/opportunity/OpportunitySetupSnapshot.java`

DevLog applicability was checked, but the Story Agent endpoint could not
resolve this request in the current environment. The report therefore relies
on repository history, accepted repository ADRs, and current source code.
