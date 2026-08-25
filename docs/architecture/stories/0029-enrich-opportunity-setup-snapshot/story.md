# Story 0029 — Enrich TradingOpportunity with a Deterministic Setup Snapshot

## Goal

Make `TradingOpportunity` a deterministic, historically understandable snapshot of the market setup that triggered the system's attention — without turning it into a `TradePlan`.

## Context

The investigation `docs/architecture/reports/trading-opportunity-trader-value-investigation.md` concluded **DOMAIN_MODEL_GAP (HIGH)**: an Opportunity answers "a strategy matched this market" but not "there is a market setup worth your attention". A trader facing 1330 active opportunities cannot discriminate between them:

- No reference price is captured at detection time
- The `explanation` echoes the strategy name ("Legacy OHLC Trend")
- `StrategyMatch.conditionResults` (the richest trigger context) are dropped during projection
- Price becomes actionable only at TradePlan creation

## Problem

A trader looking at an Opportunity cannot answer: *what happened, why did the strategy match, where was the market, when did it happen?* They must trust the system blindly before preparing a TradePlan.

## Scope

### In scope

- New domain value object `OpportunitySetupSnapshot` (+ trader-oriented trigger facts) owned by `TradingOpportunity`
- Reference price sourced from Observation evidence measurements at detection time (same pipeline transaction, no retroactive Market Data lookup)
- Trigger facts preserved from `StrategyMatch.conditionResults`
- Deterministic setup description from `StrategyEvaluation.explanation()` (evaluator-owned)
- Persistence via existing JSON payload pattern (no destructive migration)
- Additive REST contract (`setup` object in `OpportunityResponse`, null for legacy rows)
- Angular model + Opportunity detail display + minimal list enrichment
- Capability emits `closePrice` metric so observations carry the detection price
- Tests: domain, factory, persistence round-trip, REST, Angular

### Out of scope

- Entry / stop loss / take profit / position sizing / risk-reward / order type (TradePlan-owned)
- Score semantics (unchanged; score=100 limitation acknowledged as separate work)
- Price-based opportunity invalidation engine (snapshot enables future work only)
- Opportunity lifecycle/expiration changes
- Strategy quality or placeholder strategy fixes
- Global Observation API
- AI/LLM-generated explanations (deterministic only)

## Acceptance criteria

1. New opportunities persist a setup snapshot containing reference price (+timestamp), description, trigger facts, detectedAt
2. Same inputs produce byte-identical snapshot content (deterministic, reproducible)
3. Legacy opportunities deserialize with `setup = null`; UI shows honest absence; nothing crashes
4. REST contract is additive; all 18 existing fields unchanged
5. GET /opportunities/{id} never consults Market Data to reconstruct the snapshot
6. TradePlan creation continues to work; Planning Engine still computes entry/stop/targets from live data
7. All existing test suites remain green; new tests cover snapshot creation, immutability, legacy nulls, factory propagation
8. Runtime Active Scan produces opportunities whose snapshot answers: why did it trigger, at what price, when, which conditions matched

## Constraints

- Deterministic core: description and triggers must be reproducible; no LLM involvement
- Strategy-agnostic: no `if strategy == ...` branching in shared factories; evaluator owns explanation semantics
- Historical truth: snapshot immutable across version transitions (status changes copy it verbatim)
- Reference price must come from the observation evidence used by the match — never fetched after the fact
- Preserve separation: StrategyMatch = evidence, TradingOpportunity = setup snapshot, TradePlan = execution proposal

## Relevant ADRs

- ADR-026 (TradingOpportunity aggregate), ADR-027 (TradePlan boundary), ADR-034 (StrategyMatch provenance), ADR-035 (strategy-agnostic pipeline)

## Validation expectations

Market Intelligence Maven tests, Angular tests, Angular production build, Docker rebuild, runtime Active Scan inspection of new + legacy opportunities.
