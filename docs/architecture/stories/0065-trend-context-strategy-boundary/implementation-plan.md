# Story 0065 - Implementation Plan

## Implementation Boundary

Extend the existing Market Intelligence Strategy module so a persisted current
Trend Context observation can feed the existing generic evaluator and match
persistence path.

Do not change Trend Context calculations, create a new evaluator service, add a
StrategyEvaluation table, or enable an unvalidated strategy.

## 1. Trend Context Evidence Selection

1. Add or refactor an internal application selector at the Observation
   repository boundary. It accepts market ID, required semantic inputs, and
   evaluation time, and returns the current typed observation plus provenance.
2. Require `TREND_CONTEXT` type, typed payload, requested market, `ACTIVE` status,
   valid-at-evaluation-time validity, completed supporting analysis, and
   `cutOffAt <= evaluatedAt`.
3. Do not fall back to an old successful assessment when no current valid
   observation exists. Return safe unavailable/not-evaluable behavior.
4. Reuse the same validity/status semantics as `TrendContextReadService`; do not
   invent another freshness formula.
5. Keep legacy OHLC selection available for legacy definitions. Selection is based
   on declared semantic requirements/evidence contract, never a concrete strategy
   ID.

## 2. Generic Typed Semantic Context

1. Extend the semantic context boundary so a typed-observation resolver can map
   selected `RequiredSemanticInput` values.
2. Key the resolver by observation type/payload contract, not by strategy ID.
3. Map only facts required by the definition, such as attention, direction,
   regime, phase, alignment, exclusion/contradiction/invalidation state, and
   assessment/cut-off timestamps.
4. Add generic immutable provenance containing observation ID, lineage/version,
   cut-off, profile/rule versions, input fingerprint, and assessment fingerprint.
5. Include semantic values and provenance in the deterministic context digest.
6. Keep the complete typed payload in the Observation and preserve the existing
   scalar measurement and legacy OHLC resolution paths.

## 3. Conservative Strategy Definition and Evaluator

1. Define a stable ID/version for `Conservative Trend Following V1`.
2. Declare explicit Trend Context semantic inputs and typed parameters for the
   accepted criteria. Do not add new indicators or formulas.
3. Bootstrap the definition as `DISABLED` and `UNVALIDATED`; no live match is
   possible without a separate governance decision.
4. Register a deterministic evaluator through `StrategyEvaluatorRegistry`.
5. Require explicit condition results for attractive attention, directional bias,
   trending regime, accepted phase, compatible alignment, no hard exclusion,
   no material contradiction/invalidation, and current valid evidence.
6. Map UP to LONG and DOWN to SHORT only after all criteria pass. Never create an
   order, plan, Risk result, or broker action.

## 4. StrategyEvaluation Integration

1. Extend `LiveStrategyEvaluationRunner` or its context collaborator to accept
   generic selected evidence while preserving the existing Observation overload.
2. Preserve current statuses:
   - `MATCH` when every declared condition passes;
   - `NO_MATCH` for valid evidence with a failed criterion;
   - `NOT_EVALUABLE` for missing/stale/invalid/unavailable required evidence;
   - `FAILED` only for unexpected failures.
3. Treat `WATCH`, `NO_SETUP`, and `CONTEXTUALLY_DANGEROUS` as valid but
   non-matching outcomes; treat unknown/invalid evidence as not evaluable.
4. Keep evaluation time caller-supplied and wall-clock independent.
5. Keep non-match evaluations transient under the existing model; do not add a
   new evaluation persistence model in this Story.

## 5. Production Pipeline Integration

1. Refactor `ProductionIntelligencePipeline` to provide each strategy with the
   appropriate immutable evidence observation for its declared inputs.
2. Preserve the legacy OHLC observation and legacy strategy/parity behavior.
3. For Trend Context declarations, select the current typed observation. Do not
   call `TrendContextEngine` or fetch Market Data from this path.
4. Replace the assumption that one observation horizon is sufficient for every
   strategy. For Trend Context, compare declared role/timeframe requirements with
   the payload profile without strategy-ID branching.
5. Apply governance filtering before evaluation. Disabled/unvalidated definitions
   must not reach the live evaluator.
6. Evaluate multiple strategies independently against the same immutable evidence.
7. Pass the selected observation ID to `StrategyMatchPersister` and use its
   validity window in the opportunity command.
8. Preserve the existing transaction, PipelineRun, and zero-to-many opportunity
   semantics.

## 6. Match and Opportunity Boundary

1. Reuse `StrategyMatchPersister` unless a minimal additive provenance correction
   is proven necessary.
2. Verify the context digest binds selected typed-observation provenance; do not
   use `assessmentFingerprint` alone as identity.
3. Persist only `MATCH` evaluations.
4. Reuse `StrategyMatchOpportunityFactory` and `OpportunityEngine` only after a
   persisted match exists.
5. Preserve one opportunity per match, deterministic opportunity lineage, and
   `strategyMatchId` attribution.
6. Verify no-match/not-evaluable paths never invoke opportunity creation.
7. Do not call Risk, TradePlan, execution, or broker services.

## 7. Persistence and Lineage

1. Reuse existing strategy definition, observation, strategy match, and
   opportunity tables.
2. Persist the conservative definition as disabled/unvalidated if bootstrap is
   the repository-native registration path.
3. Do not add `strategy_evaluations` persistence in this Story.
4. Verify round-trip preservation of observation ID/version, strategy ID/version,
   analysis execution ID, context digest, condition results, and timestamps.
5. Verify the referenced observation reload exposes assessment/input fingerprints
   and cut-off.
6. Verify replay/version semantics:
   - same strategy/version + same evidence + same execution -> existing match;
   - same assessment fingerprint in a new observation version -> new evaluation;
   - same observation with a new strategy version -> independent evaluation;
   - different strategies -> independent matches/opportunities.

## 8. Read / API Contract

1. Add no public endpoint in Story 0065. Existing authenticated analysis, match,
   opportunity, and Trend Context read boundaries are sufficient.
2. Defer Decision Workspace projections to Story 0066. Future projections should
   use existing provenance, not persistence internals.

## 9. Security and Ownership

1. Keep evaluation behind the existing authenticated analysis/Active Scan flow.
2. Add no public strategy mutation or arbitrary cross-user evaluation API.
3. Preserve actor ownership and service authentication conventions.
4. Do not expose credentials, raw provider data, or JWTs to Strategy code.

## 10. Focused Tests to Add

### Definition and Evaluator

- Definition identity/version, explicit inputs, disabled/unvalidated governance,
  evaluator registry registration, and criterion-level pass/fail.
- UP -> LONG and DOWN -> SHORT; neutral/unknown direction rejected.

### Evidence and Validity

- Typed payload resolution without Trend Context recalculation.
- Observation identity/version, cut-off, fingerprints, profile/rule versions,
  and digest preservation.
- Current match; expired/stale/missing/unavailable/future-cutoff not evaluable.
- No historical fallback as current evidence.

### Outcomes and Authority

- Attractive plus all criteria -> MATCH.
- Attractive plus one failed criterion -> NO_MATCH.
- WATCH, NO_SETUP, UNKNOWN, and DANGEROUS -> no match.
- Attractive evidence without a matching definition -> no match/no opportunity.
- Successful match -> existing opportunity path only; no Risk/TradePlan/
  execution/broker interaction.

### Replay and Regression

- Existing StrategyMatch transaction/idempotency behavior.
- New strategy version, new observation version, and multiple strategy cases.
- Legacy evaluator/parity, pipeline, opportunity attribution, Active Scan, and
  Story 0064 regressions.

## 11. Validation

Run the focused Story 0065 tests, existing strategy/pipeline/opportunity/Active
Scan regressions, Story 0064 tests, the Market Intelligence module verification,
and `git diff --check`.

Automated tests prove contract behavior only. They do not validate profitability
or authorize enablement of the conservative strategy.

## 12. Stop Conditions

Stop for architectural review if:

- typed evidence cannot reach Strategy without raw Market Data coupling or
  recalculation;
- StrategyMatch meaning conflicts with ADR-034/048;
- a match bypasses human/Risk boundaries;
- cut-off/validity cannot be enforced;
- a new cross-cutting evaluation persistence model is required;
- explicit versioned criteria cannot represent the conservative strategy; or
- a second competing strategy architecture becomes necessary.

## Readiness Gate

```text
TREND CONTEXT EVIDENCE BOUNDARY: RESOLVED
STRATEGY DEFINITION MODEL: RESOLVED
STRATEGY EVALUATION SEMANTICS: RESOLVED
STRATEGY MATCH SEMANTICS: RESOLVED
TRADING OPPORTUNITY BOUNDARY: RESOLVED
VALIDITY / NO-LOOK-AHEAD: RESOLVED
LINEAGE / IDEMPOTENCY: RESOLVED
SECURITY / OWNERSHIP: RESOLVED

NEW ADR REQUIRED: NO

STORY 0065 IMPLEMENTATION READY: YES
```

This plan is design material only. It does not authorize implementation or
enablement of `Conservative Trend Following V1`.
