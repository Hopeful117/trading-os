# Story 0065 - Repository Analysis

## Scope and Authority

The repository is authoritative for current implementation. ADR-034 and
ADR-048 govern the Strategy and evidence boundaries. Stories 0062, 0063, and
0064 define the Trend Context input, engine, and durable observation work that
this Story consumes.

No DevLog capability was available in the current environment. This analysis
therefore uses the accepted repository ADRs, preceding Stories, current source,
and related domain investigations.

## Existing and Required Boundaries

### Trend Context Evidence

**EXISTING**

- Story 0064 persists a `TREND_CONTEXT` `Observation` with a typed
  `TrendContextObservationPayload`.
- `TrendContextCapabilityContent` carries the assessment, role source
  references, operational status, assessment time, cut-off, input fingerprint,
  and assessment fingerprint.
- `TrendContextAssessment` carries direction, regime, phase, attention,
  alignment, findings, contradictions, exclusions, invalidations, profile/rule
  versions, and the deterministic fingerprint.
- `TrendContextReadService` already reports current, historical, stale, missing,
  unavailable, and in-progress states.
- `CapabilityAnalysisCoordinator` builds the observation after capability
  execution, and `LocalAnalysisExecutionDispatcher` invokes the production
  pipeline after the analysis lifecycle completes.

**NEEDS EXTENSION**

- Strategy needs an internal selector returning the current typed observation,
  not only the read model. It must require active/status-valid evidence,
  completed supporting analysis, and `cutOffAt <= evaluatedAt`.
- It must not fall back to an old successful assessment merely because it exists.

**MUST NOT CHANGE**

- Strategy must not invoke `TrendContextEngine`, read raw Market Data, or read
  provider payloads.
- The immutable observation remains the analytical evidence boundary.
- `CONTEXTUALLY_ATTRACTIVE` remains evidence, not a match.

### StrategyDefinition

**EXISTING**

- `StrategyDefinition` is an immutable `(strategyId, version)` definition with
  name, description, scenario, direction, applicability, required semantic
  inputs, typed parameters, research reference, and governance metadata.
- Semantic evolution creates a new version; operational and validation status
  are separate.
- Live eligibility requires `ENABLED + VALIDATED`, with the explicit legacy
  `BOOTSTRAP_CONTROLLED_RUN` exception.
- Definitions are persisted in `strategy_definitions` and seeded by
  `BuiltinStrategyBootstrap`.
- `RequiredSemanticInput` is generic (`OBSERVATION` or `FEATURE`), and
  `StrategyParameters` already supports typed decimal, integer, string, and
  duration values.

**NEEDS EXTENSION**

- A Trend Context definition must declare stable semantic keys for selected
  typed facts. The existing declaration model can represent this, but the
  resolver currently only reads scalar measurements from one observation.
- The first concrete strategy can use a dedicated evaluator over declared
  semantic keys and parameters without a new strategy architecture.

**MUST NOT CHANGE**

- A generic observation must not embed a complete strategy.
- The conservative strategy must remain disabled/unvalidated by default.
- Trend Context calculation rules must remain outside Strategy.

### StrategyEvaluation

**EXISTING**

- `StrategyEvaluationContext` contains market ID, instrument, one timeframe,
  evaluation timestamp, typed semantic values, and a deterministic digest.
- `StrategyEvaluator` is a pure `(definition, context) -> evaluation` boundary.
- `StrategyEvaluationService` maps missing inputs to `NOT_EVALUABLE`, valid
  criterion failure to `NO_MATCH`, and unexpected failures to `FAILED`.
- `StrategyEvaluation` contains strategy identity/version, market, evaluated time,
  status, direction/confidence, condition results, explanation, consumed inputs,
  and context digest.
- Evaluations are transient. There is no StrategyEvaluation repository or table.
- `StrategyEvaluationContextFactory` resolves required inputs from evidence
  measurement keys and retains a legacy OHLC convenience path.

**NEEDS EXTENSION**

- Add a generic typed-observation resolver without a Strategy-ID switch.
- Add generic provenance to the context: observation ID/lineage/version, cut-off,
  profile/rule versions, fingerprints, and consumed semantic keys.
- Include immutable provenance and semantic values in the context digest.
- The single current `timeframe` field cannot be the only Trend Context
  applicability input because the observation horizon is `TREND_CONTEXT`, while
  the typed profile contains 4h/1h/15m role intervals.

**MUST NOT CHANGE**

- Evaluators remain deterministic and infrastructure-free.
- `NOT_EVALUABLE` must remain distinct from valid `NO_MATCH`.
- Evaluators must not read wall-clock time.

### StrategyMatch

**EXISTING**

- `StrategyMatch` is an immutable persisted fact created only from `MATCH`.
- It stores match ID, strategy ID/version, market ID, analysis execution ID,
  observation ID, direction, context digest, condition results, matched time,
  and storage time.
- Identity is `(strategyId, strategyVersion, marketId, analysisExecutionId,
  contextDigest)` and is protected by a database unique constraint.
- `StrategyMatchPersister` is transactionally required and resolves replays and
  concurrent duplicate inserts.
- The match table is append-only and can reload a fact independently of the
  current StrategyDefinition row.

**DECISION**

- No new match identity is required. Observation ID identifies an immutable
  observation version; the observation payload retains assessment/input
  fingerprints and cut-off. The context digest binds selected provenance.
- A new evaluation persistence model is not part of the smallest Story 0065
  path. Non-match/not-evaluable outcomes remain transient and diagnostic.

**MUST NOT CHANGE**

- Never persist a match for `NO_MATCH`, `NOT_EVALUABLE`, or `FAILED`.
- Never make a match imply Risk, TradePlan, or execution authorization.
- Never use `assessmentFingerprint` alone as universal match identity.

### TradingOpportunity Coupling

**EXISTING**

- `ProductionIntelligencePipeline` loads governed persisted definitions,
  evaluates each applicable strategy, and for each `MATCH` calls
  `StrategyMatchPersister`, `StrategyMatchOpportunityFactory`, and
  `OpportunityEngine`.
- The opportunity factory is strategy-agnostic and carries the match ID,
  observation reference, condition triggers, explanation, validity, and
  strategy-derived scenario/timeframe.
- Opportunity persistence is attributed to `strategyMatchId`; one match produces
  one opportunity. Multiple matches produce multiple opportunities.

**DECISION**

- Use the existing **StrategyMatch -> TradingOpportunity** path. ADR-034 and
  current code make this the accepted V1 contract.
- Stop the Trend Context path before match creation if there is no matching
  StrategyDefinition or any criterion/evidence gate fails.
- A favorable observation alone cannot invoke opportunity creation.

**MUST NOT CHANGE**

- Do not add a Trend Context-specific opportunity path.
- Do not call Risk, TradePlan, execution, or broker services here.

### Legacy Production Pipeline

**EXISTING**

- The pipeline first builds the legacy `OhlcTrendObservationRule` observation.
- It then filters persisted definitions by governance and current observation
  horizon/provider applicability, evaluates all selected strategies, and
  promotes only matches.
- The legacy OHLC strategy is explicitly an unvalidated bootstrap migration
  vehicle. Existing parity tests protect it.

**NEEDS EXTENSION**

- Evidence selection must be driven by declared semantic requirements so legacy
  definitions keep the OHLC observation while Trend Context definitions receive
  the current typed observation.
- Applicability must use the selected evidence contract/profile, not compare the
  literal `TREND_CONTEXT` observation horizon to one strategy timeframe.
- The existing atomic transaction must continue through observation, match, and
  opportunity for matching strategies.

**MUST NOT CHANGE**

- Do not rewrite legacy OHLC semantics or introduce a second strategy pipeline.
- Do not evaluate disabled/unvalidated definitions.

### Persistence, Freshness, and Temporal Integrity

**EXISTING**

- Definitions, matches, opportunities, and observations have durable JPA/Flyway
  persistence.
- Observations have status, validity, lineage, and version. Trend Context payloads
  have assessment/cut-off and fingerprints.
- Match idempotency already handles same logical strategy/evidence replay.

**NEEDS EXTENSION**

- Current evidence selection must require `ACTIVE`, valid-at-evaluation-time,
  completed supporting execution, and cut-off not later than evaluation time.
- An expired or late observation must not produce a current match. Historical
  evidence remains available for audit, not silent current reuse.

**DECISION**

- Reuse Observation validity/status as authoritative. Do not invent another
  freshness formula or add an evaluation table in this Story.
- Same strategy/version plus same evidence in the same execution follows current
  idempotency. A new observation version or strategy version evaluates separately.

### Multiple Strategies and Direction

**EXISTING**

- The pipeline evaluates all applicable definitions independently.
- `StrategyDirection` and `MatchedDirection` support dynamic strategy direction
  and LONG/SHORT result direction.

**DECISION**

- One Trend Context observation may feed multiple independent strategies.
- `Conservative Trend Following V1` is direction-dynamic: UP -> LONG and DOWN
  -> SHORT only after all explicit criteria pass.
- No confluence, ranking, or order direction beyond the match fact is introduced.

### Security and Ownership

**EXISTING**

- Public Market Intelligence intelligence endpoints are authenticated.
- Active Scan reads are actor-scoped. Internal planning routes use service
  authority and delegated actor checks.
- There is no user StrategyDefinition mutation API; built-in definitions are
  system-seeded.

**DECISION**

- Add no new strategy mutation/evaluation endpoint. Existing authenticated
  analysis/scan entry points remain the boundary.
- User-owned strategy scope is a future concern and is out of scope here.

## Gaps and Risks

1. The pipeline's single legacy observation input must become generic evidence
   selection without changing legacy behavior.
2. `TREND_CONTEXT` is an observation horizon, not a single declared strategy
   timeframe. Trend profile role intervals must drive applicability.
3. StrategyEvaluation is currently transient, so failed evaluations are not
   individually durable. This is an explicit deferred analytics limitation, not
   a reason to introduce a new persistence model in this Story.
4. The internal selector must reuse Trend Context validity semantics rather than
   duplicate or weaken the read boundary.

None is an architectural blocker. These are bounded extensions of existing
Observation and Strategy contracts.

## ADR Decision

**NEW ADR REQUIRED: NO.** ADR-034 and ADR-048 already determine the meaning,
ownership, authority, lineage, validity, and no-look-ahead rules. Story 0065
does not create a new service, persistence authority, or financial boundary.

## Implementation Readiness Gate

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

This readiness result covers only the bounded plan. It is not approval to
implement or enable a validated trading strategy.
