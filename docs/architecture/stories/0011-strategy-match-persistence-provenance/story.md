# Story 0011 — StrategyMatch Persistence & Provenance

**Status:** CLOSED - HUMAN ACCEPTED

## Goal

Introduce `StrategyMatch` as a persisted, immutable, append-only domain fact
(ADR-034): "a specific Strategy version satisfied its conditions for a specific
market and context at a specific time."

Story 0011 remains shadow/incremental: the legacy OHLC opportunity path stays
the sole trader-facing authority. TradingOpportunity does NOT depend on
StrategyMatch yet (Story 0012).

## Domain semantics

- Only `StrategyEvaluationStatus.MATCH` may produce a StrategyMatch.
  `NO_MATCH`, `NOT_EVALUABLE`, `FAILED` never persist anything and never
  produce a pending record. StrategyEvaluation itself remains transient.
- Immutable fact: all-final fields, defensive collection copies, factory +
  rehydrate constructors only, no mutators, insert/read-only persistence.
- Exact strategy attribution: every match stores `strategy_id` +
  `strategy_version` identifying the deterministic semantics forever.

## NO FK to strategy_definitions (approved deviation)

`strategy_id + strategy_version` is logical provenance. Story 0011 intentionally
does NOT create a physical FK because builtin StrategyDefinitions are code-
defined (`BuiltinStrategies`) and `strategy_definitions` is not populated at
runtime; a FK would reject valid builtin matches. No fake seed rows were added.
A future story materializing StrategyDefinition persistence may strengthen this
into referential integrity.

## Live provenance

V1 persists LIVE provenance only: `analysis_execution_id` (mandatory) and
`observation_id` as evidence reference. These are current integration facts,
NOT eternal conceptual requirements of the domain: the StrategyMatch class
depends only on identifiers, never on AnalysisExecution/ActiveScan/Gateway/
MarketData types. Story 0013 (Backtest) will generalize persisted provenance
additively (e.g. evaluation source + nullable live lineage with a partial
unique index). PipelineRun and capability-artifact links are reconstructible
through analysisExecutionId and are deliberately not duplicated.

## Identity & idempotency

- Technical identity: random application-generated UUID `match_id` (PK).
- Logical identity (business key, DB-enforced):
  `(strategy_id, strategy_version, market_id, analysis_execution_id, context_digest)`.
- The database unique constraint is authoritative; the application performs an
  optimistic find-first and maps a constraint violation back to the existing
  row. Concurrent duplicates resolve to exactly one logical match.

## Condition results / context digest

- `condition_results`: immutable JSON payload (ordered array of
  `{conditionId, passed, observedValue}`), written once, consistent with the
  TEXT-payload conventions of the service.
- `context_digest`: exact Story-0010 SHA-256 digest, stored verbatim, never
  recomputed. It supports idempotency, replay comparison, provenance and
  debugging; readable provenance lives in conditionResults.

## Time semantics

- `matched_at` = `StrategyEvaluation.evaluatedAt()` (semantic evaluation time).
  Known nuance: current shadow evaluation receives the pipeline evaluation
  clock rather than the OHLC event time; intentional in 0011.
- `created_at` = storage time. Both are distinct columns, never conflated.

## Transaction model (current implementation)

```text
ProductionIntelligencePipeline @Transactional
    ├─ Observation build (unchanged)
    ├─ legacy TradingOpportunity creation (legacy compatibility)
    ├─ shadow StrategyEvaluation (Story 0010)
    ├─ StrategyMatchPersister.persist for MATCH evaluations
    └─ idempotent insert in the same transaction
```

The current implementation uses the main transaction rather than an
after-commit `REQUIRES_NEW` write. This is the authoritative implementation
because StrategyMatch is required truth for the downstream StrategyMatch-based
opportunity path. A rollback therefore removes the match together with the
surrounding analysis transaction; a successful transaction commits the match
atomically. The database unique constraint remains authoritative for concurrent
duplicates.

## Failure semantics & temporary crash window

Persistence failures participate in the enclosing transaction and therefore do
not leave a committed analysis without its required StrategyMatch truth. The
logical insert remains idempotent through the business-key uniqueness
constraint.

The downstream StrategyMatch-based opportunity path is already supported by
this required-truth transaction model. The legacy opportunity path remains for
compatibility until the later workflow stories remove it explicitly.

## Schema (V5__strategy_match_persistence.sql)

Table `strategy_matches`: PK `match_id`; UNIQUE business key above; indexes on
`analysis_execution_id` and `(strategy_id, strategy_version)`. Additive only;
no existing table modified; no speculative analytics indexes.

## REST API

None. Matches are internal provenance.

## Story 0012 handoff seam

`StrategyMatchRepository.findById / findByIdentity(identity)` returns a
complete fact (strategy id/version, market, direction, digest, conditions,
matchedAt). Opportunity derivation must not re-run the evaluator or reconstruct
conditions from raw OHLC.
