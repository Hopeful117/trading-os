# Story 0064 - Integrate Trend Context into Market Intelligence

## Metadata

**ID:** `0064`

**Title:** Integrate Trend Context into Market Intelligence Observations

**Status:** IMPLEMENTED - AUTOMATED VALIDATION COMPLETE - HUMAN REVIEW PENDING

**Size:** LARGE

**Implementation Risk:** HIGH

## Goal

Run the pure Trend Context engine through the existing Market Intelligence
capability and execution architecture, then persist a typed, explainable,
versioned `IntelligenceObservation` with complete provenance and validity.

## Context

Stories 0062 and 0063 provide the dedicated input and pure engine. Existing
Market Intelligence already has `IntelligenceContext`, context contributors,
deterministic capability contracts, `AnalysisExecution`, artifact/result
orchestration, `ObservationBuilder`, evidence, supersession, and persistence.

The current historical contributor is fixed to one fifteen-minute series and the
legacy production pipeline still builds the permissive OHLC observation. This
Story must introduce Trend Context without making a favorable assessment an
automatic opportunity.

## Problem

The deterministic result currently has no production capability boundary,
multi-timeframe context assembly, durable observation payload, or read contract.
Without this integration the engine cannot participate in the existing
traceable intelligence lifecycle.

## Scope

### Included

- Register a deterministic Trend Context capability through existing capability
  interfaces, registry, execution planning, and `AnalysisExecution` flow.
- Assemble role-specific BIAS/SETUP/optional TRIGGER history from Market Data
  without transferring Market Data ownership.
- Map source/freshness/gap/closure/synthetic metadata into the dedicated input.
- Execute the pure engine and preserve its factual result, findings,
  contradictions, exclusions, attention outcome, and invalidation.
- Integrate the typed assessment with the existing Observation/evidence model,
  including immutable observation identity, lineage, validity, supersession,
  rule/profile versions, cut-off, source references, and fingerprint.
- Persist and reload the assessment/evidence through existing repositories or a
  minimal typed extension to them.
- Expose a stable authenticated read/application contract for a selected market
  so a later frontend Story does not need to access persistence directly.
- Preserve `AVAILABLE`, `STALE`, `MISSING`, `UNAVAILABLE`, partial/degraded, and
  no-setup semantics without manufacturing direction.
- Keep source references/digests instead of duplicating complete Market Data
  history where the existing contract permits reconstruction.

### Required pipeline

```text
Market Data
    ↓
TrendContextAssessmentInput
    ↓
pure deterministic engine
    ↓
IntelligenceObservation / evidence
    ↓
typed read contract
```

## Out of Scope

- Changing Trend Context algorithms or profile thresholds.
- StrategyEvaluation, StrategyMatch, or TradingOpportunity creation; these are
  Story 0065.
- Direct opportunity creation from `CONTEXTUALLY_ATTRACTIVE`.
- Risk, TradePlan, execution, account authorization, ML, agents, news, or UI
  component implementation.
- Replacing `IntelligenceContext`, `AnalysisExecution`, or generic observation
  persistence with a new platform abstraction.
- Passive scanner expansion or broad scan ranking.

## Architectural Constraints

- ADR-048 governs evidence authority, immutability, lineage, and no look-ahead.
- ADR-020/021 govern context assembly, capability orchestration, degradation,
  and observation promotion.
- ADR-025 governs immutable observations, mandatory evidence, validity, and
  supersession.
- The accepted Trend Context design governs the typed assessment and rule
  versions.
- A favorable assessment remains analytical evidence only.
- Existing unrelated worktree changes must be preserved.

## Acceptance Criteria

- [ ] Trend Context is registered and executable through existing deterministic
      capability/analysis orchestration.
- [ ] Required role history is assembled with the dedicated input contract and
      no direct source-service bypass.
- [ ] The capability preserves role-level freshness, missing, gap, synthetic,
      open-candle, and cut-off findings.
- [ ] A complete assessment produces a typed durable observation/evidence result
      with factual context and separate attention outcome.
- [ ] A `NO_SETUP`, `WATCH`, `UNKNOWN`, stale, unavailable, or invalid result is
      persisted/reported truthfully when the input requires it.
- [ ] Observation lineage preserves source references, evidence windows,
      `AnalysisExecution`, cut-off, profile/rule versions, validity, and
      fingerprint.
- [ ] New evidence creates a new immutable observation/version or explicit
      supersession; historical observations are not rewritten.
- [ ] Persistence/reload reconstructs the same material assessment and evidence.
- [ ] The read/application contract is authenticated and does not expose
      internal persistence or source credentials.
- [ ] No `StrategyMatch`, `TradingOpportunity`, `TradePlan`, Risk result, or
      execution command is created by a favorable assessment alone.
- [ ] Existing deterministic capability, observation, active-scan, and pipeline
      regression tests remain valid.
- [ ] Focused integration/persistence tests and `git diff --check` pass.

## Test Requirements

- Capability registration, requirement, availability, and orchestration tests.
- Market Data adapter tests for role assembly and freshness/degradation states.
- ObservationBuilder/evidence tests for typed payload, required evidence,
  validity, supersession, and no-opportunity outcomes.
- Persistence/reload integration tests with source references and fingerprints.
- API/application tests for successful, stale, missing, unavailable, and invalid
  assessments if a read endpoint is introduced.
- Regression tests proving existing Active Scan and legacy strategy behavior is
  not silently broadened or removed.

## Observability and Provenance Requirements

- Link assessment to `AnalysisExecution`, source references, role intervals,
  profile/rule versions, cut-off, validity, and input fingerprint.
- Record capability status separately from result quality/degradation.
- Preserve late or expired results for audit without replacing the consolidated
  current result.
- Do not log secrets, complete sensitive contexts, or duplicate raw history
  unnecessarily.

## Dependencies

- Story 0062 input contract.
- Story 0063 pure deterministic engine.
- Existing Market Intelligence context, capability, execution, observation, and
  persistence contracts.

## Likely Files/Components Affected

- `market-intelligence/.../adapter/marketdata/`
- `market-intelligence/.../application/capability/`
- `market-intelligence/.../application/planning/` and execution orchestration.
- `market-intelligence/.../application/observation/`
- `market-intelligence/.../domain/observation/`
- Observation persistence adapters/entities and migrations only if required by
  the existing persistence design.
- Market Intelligence controller/read DTOs and focused tests.

## Risks

- Generic Observation currently stores evidence measurements rather than an
  obvious typed assessment payload; the extension must remain minimal and
  immutable.
- Current `ProductionIntelligencePipeline` combines legacy observation,
  strategy evaluation, and opportunity creation in one transaction. This Story
  must not accidentally make Trend Context bypass or replace the Strategy
  boundary.
- The 4h/1h/15m profile requires careful history limits and freshness behavior.

## Definition of Done

- [ ] Story scope approved by the human engineer.
- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Capability and observation integration implemented.
- [ ] Persistence/reload and degradation behavior validated.
- [ ] No direct favorable-assessment-to-opportunity path exists.
- [ ] Human code review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.
