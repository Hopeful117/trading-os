# Story 0106 - Deterministic Market Intelligence Baseline

## Metadata

- **ID:** `0106`
- **Title:** Deterministic Market Intelligence Baseline
- **Status:** IMPLEMENTATION COMPLETE - HUMAN STORY REVIEW PENDING
- **Related Stories:** `0062`, `0063`, `0071`, `0105`
- **Related ADRs:** `ADR-020`, `ADR-021`, `ADR-033`, `ADR-048`, `ADR-049`, `ADR-056`

## Goal

Establish a small, deterministic and provider-neutral Market Intelligence
baseline that produces reusable evidence without introducing trading decisions,
ML, LLM or execution authority.

## Context

Story 0105 established the canonical `IntelligenceContext`, including an
evaluation cut-off, typed sections, provenance, availability semantics and a
stable context fingerprint. Existing Market Intelligence capabilities already
calculate spread, OHLC range, Market Structure and Trend Context, but their
cross-family deterministic boundary was not documented as one baseline.

## Scope

- Define the deterministic evidence boundary for normalized facts, features,
  analyses and future interpretation.
- Provide typed quote-quality measurements: midpoint, absolute spread, relative
  spread and basis points.
- Provide bounded OHLC temporal measurements: range, price change and simple
  return.
- Preserve existing Market Structure and Trend Context authorities without
  creating a duplicate trend engine.
- Ensure Trend Context history respects the request evaluation cut-off and does
  not retain future evidence.
- Preserve explicit missing, invalid and unavailable semantics.
- Add focused deterministic and temporal regression tests.

## Out Of Scope

- Generic scores, spread regimes, predictions or profitability claims.
- RSI, MACD, Bollinger Bands or new generic indicator catalogues.
- Order-book, imbalance, absorption or trade-tape analysis.
- News sentiment or semantic headline analysis.
- Opportunities, TradePlans, Risk, authorization or execution changes.
- ML, LLM, feature stores, labels, outcomes or new persistence boundaries.
- Frontend, provider integration and public API changes.

## Acceptance Criteria

- [x] Quote-quality calculations are typed, deterministic and provider-neutral.
- [x] Bounded OHLC calculations expose range and simple return without creating
      strategy semantics.
- [x] Existing artifact/capability compatibility is preserved.
- [x] Trend Context uses the request evaluation cut-off when supplied.
- [x] Future OHLC evidence is excluded from Trend Context input history.
- [x] Missing and invalid input states remain explicit and are not converted to
      neutral values.
- [x] Focused tests cover calculations, capability outputs and temporal cutoff
      behavior.
- [x] Full Market Intelligence verification and whitespace validation pass.
- [ ] Human Story review and final acceptance.
- [ ] Human commit.

## Constraints

- Market Data remains authoritative for normalized market facts.
- Market Intelligence remains the analysis orchestrator and must not become Risk
  or execution authority.
- Deterministic calculations must not acquire data, read clocks for evidence
  eligibility, call ML/LLM services or access mutable global state.
- Existing unrelated worktree changes must remain untouched.

## Expected Validation

- Focused Market Intelligence domain, contributor and capability tests.
- Full `market-intelligence` Maven test and verification suites.
- `git diff --check`.
- Review of the complete diff and Story-local documentation.
