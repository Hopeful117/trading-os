# Story 0235 - Capability-Aware Forex Quote-Based LIVE Tradeability

## Metadata

**ID:** `0235`
**Title:** Capability-Aware Forex Quote-Based LIVE Tradeability
**Status:** Implementation complete - code review pending
**Priority:** P0 - capability-aware market decision evidence
**Related ADRs:** `ADR-079`, `ADR-100`, `ADR-076`, `ADR-086`
**Related Stories:** `0166`, `0167`, `0234`

## User Problem

The current Tradeability capability rejects every `LIVE_CURRENT` request because
it treats current quote acquisition and live Level II liquidity evidence as the
same requirement. This blocks basic quote-based analysis for Forex CFD markets,
even when a strategy needs only a fresh, valid two-sided quote and does not claim
liquidity suitability.

## Goal

Make Tradeability evidence requirements capability-aware. Permit a narrowly
bounded Forex quote-based `LIVE_CURRENT` analysis profile while preserving
fail-closed behavior for strategies that require Level II, replay, calibrated
liquidity or other stronger evidence.

## Scope

- Separate temporal evidence acquisition policy from strategy-specific evidence
  requirements.
- Define a deterministic basic quote-based Forex profile for `LIVE_CURRENT`.
- Require a fresh, valid, market-consistent two-sided quote and preserve quote
  provenance, source identity, cutoff and freshness semantics.
- Keep Level II-dependent strategies blocked unless their approved evidence
  contract is satisfied.
- Preserve explicit `UNKNOWN`, `UNAVAILABLE`, `STALE` and
  `POLICY_UNDEFINED` diagnostics.
- Preserve the distinction between spread observation and liquidity suitability.
- Add focused capability and calculator tests for accepted and rejected profiles.
- Validate the resulting behavior through the existing Market Thesis and
  TradePlan/T0 boundaries without broker mutation.
- Update the superseding ADR and the Story 0234 contract references only where
  required for consistency.

## Out Of Scope

- Treating spread as a liquidity or execution-quality conclusion.
- Enabling `LIVE_CURRENT` for Level II, order-flow, depth, imbalance, impact or
  liquidity-qualified strategies without their required evidence.
- Fabricating quotes, spreads, order books, timestamps or provider provenance.
- Adding a provider, market-data ingestion subsystem or broker adapter.
- Introducing universal spread, depth or liquidity thresholds.
- Changing Risk thresholds, T0/T1 authority, TradePlan semantics or execution
  authorization.
- Promoting a quote-based result directly to `CANDIDATE` without the existing
  Market Thesis and strategy eligibility policies.
- Any cTrader synchronization, order, position or account mutation.

## Constraints

- `ExecutionMode.LIVE` remains the correct mode for an external cTrader account.
- The human trader remains the final decision authority.
- Level II evidence retains ADR-079 initialization, continuity, provenance and
  freshness requirements.
- Historical and replay-oriented `STRICT_OBSERVED` behavior remains unchanged.
- Current artifact execution must not silently convert live observations into
  historical evidence.
- Existing uncommitted work must be preserved; no commit, push or PR is part of
  this Story.

## Acceptance Criteria

- A Forex quote-based `LIVE_CURRENT` request with a valid, fresh,
  market-consistent two-sided quote can produce deterministic quote and spread
  evidence.
- A quote-based result explicitly identifies that liquidity suitability was not
  evaluated and does not claim Level II qualification.
- A strategy or capability requiring Level II remains blocked when order-book
  evidence is absent, stale, incomplete, identity-inconsistent or otherwise
  outside its approved trust contract.
- Non-Forex or unsupported instrument profiles remain fail-closed unless an
  explicit capability policy admits them.
- Missing, stale, future, invalid or identity-inconsistent quote evidence keeps
  its precise diagnostic and cannot become positive evidence.
- `STRICT_OBSERVED` cutoff, provenance and replay behavior remains unchanged.
- Market Thesis and opportunity gating do not treat a high-quality quote as
  proof of suitable liquidity or automatic Candidate eligibility.
- Tests cover accepted Forex quote-based LIVE_CURRENT, stale and invalid quotes,
  unsupported instruments, Level II-required strategies, identity mismatch and
  cutoff/freshness failures.
- Targeted TradePlan/T0 validation proves no change to deterministic Risk or
  execution authorization boundaries.
- No test reaches a real broker or modifies the FTMO LIVE account.

## Validation Expectations

- Targeted Market Intelligence capability, strategy and tradeability tests.
- Market Thesis/opportunity regression tests for missingness and eligibility.
- Targeted TradePlan/T0 orchestration and boundary tests.
- Relevant module quality checks and `git diff --check`.
- Read-only market-data evidence only; no synchronization or broker mutation.

## Safety Invariants

```text
LIVE_CURRENT_QUOTE != LIVE_CURRENT_LEVEL_II = true
SPREAD_OBSERVED != LIQUIDITY_SUITABLE = true
QUOTE_VALIDITY != CANDIDATE_ELIGIBILITY = true
MISSING_REQUIRED_EVIDENCE != POSITIVE_EVIDENCE = true
CTRADER_MUTATION = false
```
