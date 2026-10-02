# Story 0065 - Connect Trend Context to Strategy Evaluation and Strategy Match

## Metadata

**ID:** `0065`
**Title:** Connect Trend Context to Strategy Evaluation and Strategy Match
**Status:** FORMALIZED - IMPLEMENTATION COMPLETE - HUMAN REVIEW REQUIRED
**Size:** LARGE
**Implementation Risk:** HIGH

## Formalization State

This document is the canonical Story 0065 specification. It formalizes the
bounded integration between the persisted Trend Context observation and the
existing generic Strategy evaluation pipeline.

The implementation is present in the repository, but the Story remains subject
to human review and approval. No commit, merge, enablement, or production
validation of the Conservative Trend Following strategy is implied.

### Implemented Boundary

- Trend Context semantic inputs are resolved from the persisted typed observation.
- Evidence selection is scoped to the requested analysis execution and its
  completed Trend Context capability executions.
- Observation identity and version are preserved through evaluation, match
  persistence, opportunity projection, and pipeline completion.
- `Conservative Trend Following V1` remains `DISABLED` and `UNVALIDATED`.
- Existing legacy OHLC strategy behavior remains the fallback for strategies
  that do not declare Trend Context inputs.

### Validation Evidence

- Market Intelligence compilation passes.
- Market Intelligence unit and integration tests pass.
- Focused provenance regression coverage rejects evidence from another analysis
  execution.
- `git diff --check` passes.

### Human Review Required

- Confirm the acceptance criteria below against the complete diff.
- Confirm the implementation report and repository analysis are accepted as
  supporting artifacts.
- Decide whether the Story may move from human review to integration.
- Keep the Conservative Trend Following strategy disabled until separately
  validated and approved.

## Goal

Allow a persisted, valid `TREND_CONTEXT` `IntelligenceObservation` to be
consumed by the existing versioned `StrategyDefinition` and deterministic
`StrategyEvaluation` boundaries, producing a persisted `StrategyMatch` only
when the declared strategy criteria pass.

The authority chain remains:

```text
Market Data -> Trend Context evidence -> IntelligenceObservation
    -> StrategyEvaluation -> StrategyMatch -> TradingOpportunity
    -> Human review -> TradePlan -> Risk -> human execution authorization
```

## Context

Story 0064 persists a typed `TrendContextObservationPayload` inside an immutable
observation. The payload contains the deterministic assessment, attention
outcome, role assessments, profile/rule versions, cut-off, input fingerprint,
assessment fingerprint, findings, contradictions, exclusions, and invalidation
evidence.

The Strategy bounded module already provides versioned persisted
`StrategyDefinition`, governance, generic semantic inputs, deterministic
evaluators, immutable `StrategyMatch`, and generic match-to-opportunity
projection. The current gap is the evidence adapter: the production pipeline
still evaluates strategies against the legacy OHLC observation, while Trend
Context uses a typed payload and the `TREND_CONTEXT` observation horizon.

## Problem

Without an explicit typed-evidence boundary, implementation would either rerun
`TrendContextEngine`, read raw Market Data from Strategy code, flatten away
provenance, or turn `CONTEXTUALLY_ATTRACTIVE` into a match without explicit
strategy criteria.

## Included Scope

- Define generic semantic input and provenance support for selected typed
  observation facts.
- Select only the current valid Trend Context observation for a strategy that
  declares Trend Context inputs.
- Map factual Trend Context fields into the existing semantic context without
  embedding Trend Context calculations in strategy logic.
- Preserve observation identity/version, typed provenance, context digest,
  consumed inputs, assessment cut-off, and evaluation timestamp.
- Define the first concrete `Conservative Trend Following V1` criteria using
  only existing Trend Context assessment fields. Keep it disabled/unvalidated
  by default; do not claim profitability or enable it silently.
- Evaluate through the existing evaluator registry and statuses.
- Persist `StrategyMatch` only for `MATCH`.
- Reuse the existing match-to-opportunity lifecycle for legitimate matches.
- Keep the production pipeline strategy-agnostic and preserve legacy OHLC
  strategy behavior.

## Required Pipeline

```text
current valid Trend Context Observation
    -> generic StrategyEvaluationContext assembly
    -> versioned StrategyDefinition evaluator
    -> MATCH / NO_MATCH / NOT_EVALUABLE / FAILED
    -> persist StrategyMatch only for MATCH
    -> existing opportunity projection only for persisted match
```

## First Concrete Strategy

`Conservative Trend Following V1` must require explicit conditions beyond
attention alone:

- attention is `CONTEXTUALLY_ATTRACTIVE`;
- direction is `UP` or `DOWN`, not `UNKNOWN` or `NEUTRAL`;
- regime is `TRENDING`;
- phase is `DIRECTIONAL` or `PULLBACK`;
- alignment is direction-compatible;
- no hard exclusion, material contradiction, or analytical invalidation exists;
- the observation is active and valid at evaluation time;
- the assessment cut-off is not later than evaluation time;
- required evidence is complete/current.

`NO_SETUP`, `WATCH`, `UNKNOWN`, and `CONTEXTUALLY_DANGEROUS` do not produce a
match. UP maps to LONG and DOWN maps to SHORT only after all criteria pass.
This is strategy evaluation, not an order or execution instruction.

The definition is persisted/bootstrapped only as `DISABLED` and `UNVALIDATED`
unless separately approved validation evidence exists.

## Out of Scope

- Trend Context algorithms, profiles, freshness formulas, or payload changes.
- Re-running Trend Context or reading raw/provider Market Data from Strategy.
- A second strategy engine or replacement of the evaluator registry.
- Risk, TradePlan, execution, broker, ML, agents, news, ranking, or UI work.
- Direct observation-to-match/opportunity promotion.
- Broad legacy pipeline cleanup or rewriting legacy OHLC semantics.
- A new persisted StrategyEvaluation table; current evaluation outcomes remain
  transient and successful matches remain the persisted facts.
- Story 0066 Decision Workspace presentation.

## Architectural Constraints

- ADR-034 governs StrategyDefinition, StrategyEvaluation, StrategyMatch, and
  TradingOpportunity.
- ADR-048 governs evidence authority, immutable lineage, validity, no-look-ahead,
  and prohibits direct Trend Context promotion.
- Trend Context owns reusable deterministic evidence; Strategy owns setup
  criteria.
- Existing evaluation statuses and immutable match identity remain authoritative.
- A valid StrategyMatch may use the existing V1 opportunity path; a Trend Context
  observation alone may not.
- No AI/ML component determines deterministic match conditions.
- Existing unrelated worktree changes must be preserved.

## Acceptance Criteria

- [ ] A versioned StrategyDefinition declares required Trend Context inputs.
- [ ] Generic context assembly consumes the typed observation without recalculation
      or raw Market Data access.
- [ ] Context preserves observation identity/version, cut-off, fingerprints,
      profile/rule versions, digest, and consumed inputs.
- [ ] Conservative Trend Following V1 has explicit versioned criteria and is
      disabled/unvalidated by default.
- [ ] Current valid evidence plus matching criteria produces `MATCH` and one
      persisted StrategyMatch.
- [ ] Any failed criterion produces `NO_MATCH` and no StrategyMatch.
- [ ] Missing, stale, expired, invalid, unavailable, or future-cutoff evidence
      produces safe `NOT_EVALUABLE`/non-match behavior and never an optimistic
      match.
- [ ] `NO_SETUP`, `WATCH`, `UNKNOWN`, and `CONTEXTUALLY_DANGEROUS` do not match.
- [ ] Attractive evidence with no applicable/matching strategy creates no match
      and no opportunity.
- [ ] A valid StrategyMatch remains the only input to opportunity creation.
- [ ] Legacy strategy, pipeline, opportunity, Active Scan, and Story 0064
      behavior remains compatible.
- [ ] Replay, new observation versions, new strategy versions, and multiple
      independent strategies preserve existing identity semantics.
- [ ] No Risk, TradePlan, execution authorization, or broker activity is caused.
- [ ] Focused/regression tests and `git diff --check` pass.

## Test Requirements

- Versioned definition criteria, governance, disabled default, and evaluator
  registration.
- Typed semantic input and provenance resolution.
- MATCH, NO_MATCH, NOT_EVALUABLE, stale, expired, invalid, missing, unavailable,
  and future-cutoff cases.
- Explicit analytical outcome behavior for all five Trend Attention values.
- Attractive observation without a matching definition produces no opportunity.
- Match persistence, lineage, context digest, and existing idempotency.
- New strategy version, new observation version, and multiple strategy cases.
- Legacy evaluator/parity, production pipeline, opportunity, Active Scan, and
  Story 0064 regression tests.
- No Risk, broker, ML, or LLM dependency in evaluator unit tests.

## Observability / Provenance

Retain strategy ID/version, evaluation status, condition results, explanation,
evaluation time, source observation ID/lineage/version, cut-off, profile/rule
versions, input/assessment fingerprints, consumed inputs, context digest, and
match identity. Keep the complete typed payload in the immutable observation;
do not duplicate it in StrategyMatch.

## Dependencies

- Story 0064 typed persisted Trend Context observation and validity boundary.
- Existing StrategyDefinition, evaluator, StrategyEvaluation, StrategyMatch, and
  opportunity infrastructure.
- ADR-025, ADR-034, ADR-036/037/038, and ADR-048.

## Likely Components

- `strategy/domain/StrategyEvaluationContext.java`;
- `strategy/application/StrategyEvaluationContextFactory.java` or a generic
  typed-observation resolver;
- `LiveStrategyEvaluationRunner` and evaluator registry;
- `BuiltinStrategies`/bootstrap for the disabled conservative definition;
- `ProductionIntelligencePipeline` for generic evidence selection and Trend
  Context applicability;
- existing Observation and StrategyMatch persistence boundaries;
- focused strategy, pipeline, persistence, opportunity, and regression tests.

## Risks

- The current pipeline assumes one legacy observation and its horizon; an
  incorrect extension could skip Trend Context strategies or broaden all
  strategies.
- Flattening typed evidence without provenance makes matches non-reconstructible.
- Treating attention as the whole strategy makes StrategyDefinition meaningless.
- Late or expired evidence could create a current match without strict cut-off
  and validity checks.
- A production definition could accidentally become an unvalidated opportunity
  source if governance filtering is bypassed.

## Definition of Done

- [ ] Story, Repository Analysis, and Implementation Plan approved.
- [ ] Typed Trend Context evidence reaches generic StrategyEvaluation without
      recalculation.
- [ ] Conservative criteria are explicit, versioned, disabled, and unvalidated
      by default.
- [ ] Match/no-match/not-evaluable/no-opportunity behavior is validated.
- [ ] Existing match-to-opportunity lifecycle is reused only after MATCH.
- [ ] Lineage, validity, cutoff, and replay behavior are validated.
- [ ] Legacy, Active Scan, and Story 0064 regressions pass.
- [ ] Human review, Engineering Report, and human commit are complete.
