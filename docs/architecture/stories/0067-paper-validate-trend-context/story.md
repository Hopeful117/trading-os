# Story 0067 - Validate Trend Context in the PAPER Decision Loop

## Metadata

**ID:** `0067`

**Title:** Validate Trend Context in the PAPER Decision Loop

**Status:** Draft

**Size:** LARGE

**Implementation Risk:** HIGH

## Goal

Validate that deterministic Trend Context improves conservative PAPER decision
support from market evidence through human review and the existing TradePlan,
Risk, and explicit execution boundaries.

This Story is validation-first. It may include focused fixes only when the
official journey demonstrates a concrete blocker in the already accepted
contracts.

## Context

Stories 0062-0066 provide the input contract, pure engine, durable Market
Intelligence integration, Strategy boundary, and Decision Workspace projection.
Trading OS already has a PAPER TradePlan/Risk/execution journey and an accepted
human-controlled execution boundary.

The initial Trend Context profile values remain `PAPER VALIDATION HYPOTHESES`.
This Story does not optimize them or claim strategy profitability.

## Problem

The deterministic capability may be technically correct while still failing to
help a conservative trader. PAPER validation must establish whether the outputs
are understandable, selective, fresh, and useful before Trade Planning without
weakening Risk or human authority.

## Scope

### Included

- Official authenticated PAPER Decision Workspace validation.
- Selection of an eligible PAPER market and inspection of current data/freshness.
- Review of a complete aligned context and its evidence/invalidation.
- Review of `NO_SETUP`, `WATCH`, and `UNKNOWN` outcomes.
- Review of healthy bullish and bearish pullbacks.
- Review of extension, contradiction, stale, missing, synthetic, gapped, and
  abnormal-volatility cases where safe fixtures or market evidence support them.
- Verification that a favorable Trend Context does not create a StrategyMatch or
  opportunity without a concrete StrategyDefinition.
- Verification of the optional strategy evaluation/match path where Story 0065
  provides one.
- Human creation/review of a TradePlan only after inspecting the assessment.
- Existing deterministic Risk result and explicit human authorization behavior.
- PAPER execution only when Risk approves and the human explicitly authorizes.
- Evidence/persistence reload and lineage verification.
- Durable validation report recording successful paths, negative evidence, and
  environmental limitations.

## Out of Scope

- Parameter optimization, backtesting claims, profitability claims, or strategy
  validation.
- LIVE execution or provider expansion.
- Weakening Risk, freshness, exclusions, or human authorization to force a
  successful trade.
- ML, LLM, agents, news, macro, ranking, passive scanning, monitoring, or
  automatic trading.
- New Risk rules, sizing, stops, targets, position management, or execution
  architecture.
- Direct database mutation, manual HTTP bridges, hidden endpoints, fabricated
  IDs, or direct broker calls.

## Architectural Constraints

- ADR-048, ADR-014, ADR-028, ADR-029, ADR-041, ADR-047, and the accepted Trend
  Context design remain authoritative.
- The human remains the discretionary and execution authority.
- Risk remains the deterministic financial authorization authority.
- `CONTEXTUALLY_ATTRACTIVE` is not approval and does not require a TradePlan or
  execution.
- A valid no-trade result is positive evidence for the conservative product.
- Existing unrelated worktree changes must be preserved.

## Acceptance Criteria

- [ ] An authenticated PAPER trader can inspect Trend Context before Trade
      Planning through the official Decision Workspace.
- [ ] The journey demonstrates at least one understandable aligned context and
      records its direction, regime, phase, alignment, evidence, and
      invalidation.
- [ ] The journey demonstrates valid `NO_SETUP`/`WATCH`/`UNKNOWN` outcomes
      without manufacturing an opportunity or trade.
- [ ] Stale, incomplete, synthetic, gapped, or unavailable evidence fails safely
      and remains visible to the trader.
- [ ] A favorable assessment alone creates no StrategyMatch, TradingOpportunity,
      TradePlan, Risk approval, or ExecutionIntent.
- [ ] Where an applicable StrategyDefinition exists, the path preserves
      `StrategyEvaluation`/`StrategyMatch` lineage before any opportunity.
- [ ] Any TradePlan follows the existing human review and deterministic Risk
      pipeline without a second path.
- [ ] A rejected or unavailable Risk result creates no ExecutionIntent, broker
      mutation, or PAPER position.
- [ ] An approved PAPER plan requires explicit human execution authorization and
      preserves existing execution/settlement behavior.
- [ ] Assessment, observation, strategy, plan, Risk, and execution references
      remain reconstructable after reload.
- [ ] The validation report records the exact environment, outcomes, negative
      evidence, and any remaining limitations.
- [ ] No claim is made that provisional profile values are optimized or
      profitable.

## Test and Validation Requirements

- Focused deterministic unit/property test suite from Story 0063 remains green.
- Market Intelligence integration, observation persistence, and strategy
  boundary tests remain green.
- Decision Workspace Angular tests and production build remain green.
- Authenticated official PAPER runtime validation; stop before LIVE action.
- Negative tests/evidence for no-opportunity, Risk rejection, stale data, and
  unavailable data.
- `git diff --check`.

## Observability and Provenance Requirements

- Record assessment ID/version, source references, cut-off, profile/rule
  versions, observation lineage, strategy evaluation/match references where
  applicable, TradePlan version, Risk evaluation, and execution references.
- Preserve no-trade and rejected contexts in the validation evidence.
- Do not record credentials, tokens, or unnecessary raw sensitive payloads.

## Dependencies

- Story 0062 input contract.
- Story 0063 deterministic engine.
- Story 0064 Market Intelligence integration.
- Story 0065 Strategy boundary for strategy-path validation.
- Story 0066 Decision Workspace projection.
- Existing PAPER TradePlan/Risk/execution workflow.

## Likely Files/Components Affected

- Validation tests and fixtures in affected Market Intelligence modules.
- `trading-os-web` runtime/test support only if a concrete validation blocker is
  discovered.
- Story implementation/engineering report artifacts, not production scope by
  default.

## Risks

- Market-data freshness or provider availability may limit live PAPER evidence;
  limitations must be reported, not hidden.
- A runtime success path may bias validation toward attractive contexts; explicit
  no-setup and rejected cases are required.
- Runtime validation must not turn into parameter tuning or a new product scope.

## Definition of Done

- [ ] Story scope approved by the human engineer.
- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] PAPER validation scenarios executed with reproducible evidence.
- [ ] Negative/no-trade and Risk-gate evidence recorded.
- [ ] Any concrete blocker fix remains within accepted contracts and is tested.
- [ ] Validation report completed with environmental limitations.
- [ ] Human code review completed.
- [ ] Human commit created.
