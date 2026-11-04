# Repository Analysis - Story 0196

## Git Safety

- Current branch: `story/0195-evidence-based-risk-evaluation`.
- Current HEAD: `4f2fe044`.
- `HEAD` is already contained in `origin/main` at `e373de33`; Story 0195 is
  merged.
- The working tree contains 10 pre-existing modifications in cTrader/Story 0194
  and architecture documentation files. They are unrelated to Story 0196 and
  must not be overwritten, staged or rewritten.
- No Story 0196 artifact existed before this analysis. No commit, push, merge,
  reset, stash or destructive checkout was performed.

## Authoritative Constraints

- ADR-084 defines capability-scoped observation completeness and policy-scoped
  risk readiness; it rejects universal snapshot completeness.
- ADR-085 requires explicit equity provenance, quality, freshness, identity and
  policy admissibility. Missing or inadmissible equity remains unavailable.
- ADR-086 requires invariant-scoped evidence, partial diagnostics and aggregate
  fail-closed authorization.
- The Risk Domain remains provider-neutral and deterministic.

## Current Admission Flow

1. `RiskFactRequirements.from(EffectiveRiskRuleSet)` builds a policy-level union
   and an `invariants` map in
   `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/RiskFactRequirements.java`.
2. `ModeAwareRiskFactsProvider` validates the authoritative snapshot primarily
   through the union booleans and marks the whole snapshot incomplete when a
   required equity fact is not admissible.
3. `TradePlanRiskEvaluationService.requireBroker` rejects any incomplete
   snapshot before constructing the Risk Domain context.
4. `ExecutionTimeRiskRevalidationService` applies the same global precondition
   at T1.
5. `DeterministicRiskEngine` can already emit per-rule `INCOMPLETE` results for
   unavailable derived metrics, and `RiskResultAggregator` withholds decisions.

## Confirmed Gap

The Risk Domain's partial-evaluation capability is present, but Trading Core can
prevent it from being reached. In particular, an absent equity can cause the
whole broker snapshot to be rejected even when balance, positions, stops and
position-risk inputs are independently admissible.

The implementation must therefore distinguish:

- structural snapshot/coherence failures that invalidate all dependent facts;
- invariant-specific unavailable or inadmissible facts;
- facts that are complete and admissible for an independent invariant.

## Required Implementation Boundary

- Broker Service continues to publish normalized observations and provenance.
- Trading Core resolves policy requirements and produces invariant-specific
  admission diagnostics.
- Risk Domain receives normalized facts plus deterministic invariant admission
  state; it must not interpret broker protocols or FTMO provenance.
- The aggregate result remains blocked when any mandatory invariant is incomplete,
  failed or unevaluated.

## Main Risks

- Treating an unavailable collection as an empty collection could falsely pass a
  position or exposure rule.
- Removing `requireBroker` globally could allow a malformed or incoherent
  snapshot into unrelated calculations.
- Reusing T0 admission at T1 could authorize against stale facts.
- Adding a generic evidence framework would exceed ADR-086 scope.

## Validation Plan

- Add a no-equity partial-evaluation fixture at the Risk Domain boundary.
- Add Trading Core admission tests for reported, derived, stale, incoherent and
  identity/currency-mismatched facts.
- Add tests proving T1 re-reads facts and never submits an incomplete result.
- Preserve Kraken, cTrader safety, policy and execution regression coverage.
- Run only local tests; no provider credentials or LIVE calls.

## DevLog Context

DevLog Story Agent was invoked with a stable idempotency key but failed with
`EOF reached while reading`. No usable DevLog context was available. The current
repository and ADRs are therefore the authoritative sources for this analysis.
