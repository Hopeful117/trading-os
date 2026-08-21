# Story

## Metadata

**ID:** `0010`

**Title:** Deterministic StrategyEvaluator

**Status:** In Progress

---

## Goal

Introduce the deterministic StrategyEvaluator boundary required by ADR-034:
`StrategyDefinition + StrategyEvaluationContext → StrategyEvaluator →
StrategyEvaluation`, reusable identically by live Scanner evaluation and
future historical Backtest evaluation, without changing any trader-facing
behavior.

---

## Context

ADR-034 (Accepted) establishes that Strategy owns setup semantics and that a
single shared evaluator prevents backtest/live drift. Story 0009 delivered the
persisted `StrategyDefinition` foundation. The current pipeline still decides
opportunity semantics through `OhlcTrendObservationRule`.

---

## Problem

There is no deterministic, infrastructure-free evaluation boundary; the OHLC
trend pseudo-strategy cannot be attributed, versioned, replayed or reused by a
future Backtest.

---

## Scope

- `StrategyEvaluationContext`: typed semantic inputs (`SemanticValue`
  DECIMAL/INTEGER/STRING/INSTANT/DURATION), market identity, timeframe,
  authoritative `evaluatedAt`, deterministic SHA-256 content digest.
- `StrategyEvaluation`: transient result with authoritative status
  (`MATCH | NO_MATCH | NOT_EVALUABLE | FAILED`); concrete direction exists if
  and only if status is MATCH (domain-enforced invariant); condition results,
  confidence, explanation, consumed inputs, context digest.
- `StrategyEvaluator` port + `StrategyEvaluatorRegistry` (exactly one evaluator
  per definition).
- `LegacyOhlcTrendEvaluator` implementing bootstrap strategy
  `LEGACY_OHLC_TREND_V1` (fixed UUID identity, version 1,
  **validationStatus = UNVALIDATED**, DRAFT lifecycle): priceChange > 0 → MATCH
  LONG; < 0 → MATCH SHORT; == 0 → NO_MATCH; missing input → NOT_EVALUABLE;
  validity = observedAt + PT30M; horizon 15m; confidence 1.
- `BuiltinStrategies` code-defined provider (no migration seeding).
- `StrategyEvaluationService` (status mapping: missing input → NOT_EVALUABLE,
  unexpected errors → FAILED).
- `ShadowStrategyParityMonitor` wired into `ProductionIntelligencePipeline`:
  DEBUG parity diagnostics, bounded WARN on mismatch, in-memory counters.
- Time rule: evaluators never read wall-clock time; all time logic derives from
  context-supplied timestamps.

---

## Out of Scope

StrategyMatch persistence (0011), TradingOpportunity derivation (0012),
Backtest (0013), validation workflow (0014), scanner targeting (0015), Quant
Research, Trader Analytics, REST API, migrations, DSL/scripting, AI authority,
Risk/Broker changes.

---

## Invariants

1. Evaluator purity: pure function of (definition, context). No Clock /
   Instant.now / repositories / HTTP / Feign / JPA / Random / AI.
2. Status is the single source of truth for match semantics.
3. NO_MATCH is not failure; NOT_EVALUABLE is not a negative signal; FAILED is
   not a trading signal.
4. Observation remains strategy-independent and unmutated.
5. Trader-facing pipeline behavior unchanged (shadow mode only).

---

## Acceptance Criteria

1. Positive/negative/zero priceChange parity with legacy rule (LONG / SHORT /
   no-signal).
2. Missing required input → NOT_EVALUABLE.
3. Supplied evaluatedAt is authoritative; validity derives from observedAt +
   parameter duration.
4. Repeated identical evaluation yields identical results and digest.
5. MATCH requires direction; non-MATCH forbids direction (domain-invariant
   tested).
6. Exact strategyId/version attribution on every evaluation.
7. Context digest stable across equivalent contexts and input insertion order.
8. Evaluator has no forbidden dependencies (architecture-level verification by
   package isolation; no imports of adapter/pipeline/web/JPA types).
9. Full module regression suite green; runtime shadow benchmark shows zero
   mismatches on real analyses.

---

## Story 0011 Handoff

Every successful evaluation already carries all StrategyMatch provenance:
strategyId, strategyVersion, marketId, analysisExecutionId (available at call
site), evaluatedAt, matched/direction, condition results, confidence, consumed
inputs (observation references available at call site), contextDigest — ready
to persist without redesign.
