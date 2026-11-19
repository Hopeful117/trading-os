# Story 0247-01 - Define the Deterministic Evidence Quality Contract

## Metadata

**ID:** `0247-01`
**Title:** Define the Deterministic Evidence Quality Contract
**Status:** Implementation complete - changes required after independent review
**Priority:** P1 - Trading Intelligence
**Epic:** `0247`
**Predecessor:** Epic `0238` passive scanning foundation
**Related ADRs:** `ADR-021`, `ADR-033`, `ADR-034`, `ADR-056`, `ADR-057`
**Repository Analysis:** `repository-analysis.md`

## Goal

Establish a versioned, deterministic, explainable assessment of the quality of
the evidence already assembled by Market Intelligence.

The assessment must help distinguish a technically interesting candidate from
one supported by complete, fresh, attributable, applicable and non-contradictory
evidence. It must not represent probability, profitability or execution
authority.

## Scope

- Define the evidence-quality assessment contract in the Market Intelligence domain.
- Represent explicit dimensions for completeness, freshness, provenance,
  contradiction, applicability and missing-policy state.
- Preserve evidence source, provider, instrument, timeframe, cutoff and rule
  version where already available.
- Produce deterministic reason codes and a stable fingerprint.
- Version the assessment policy and make the result persistable.
- Integrate only with existing Market Thesis, tradeability, strategy and
  opportunity boundaries where a concrete contract gap is demonstrated.
- Add domain and persistence round-trip tests.

## Out of Scope

- Probability, win-rate, expectancy or profitability claims.
- New indicators, providers or scanner services.
- Opportunity ranking or weight changes.
- Opportunity outcome evaluation or replay infrastructure.
- AI interpretation or automatic ActiveScan creation.
- Risk, authorization, broker or execution changes.
- Duplication of rules owned by `MarketThesisComposer` or
  `TradeabilityCalculator`.

## Acceptance Criteria

- [ ] Identical inputs produce identical assessments.
- [ ] Completeness, freshness, provenance, contradiction, applicability and
      missing-policy states are explicit.
- [ ] Missing, unavailable, stale, contradictory and not-implemented evidence
      cannot be represented as positive certainty.
- [ ] Each assessment contains version, deterministic reasons and a stable
      fingerprint.
- [ ] Existing Market Thesis and tradeability semantics are reused rather than
      duplicated.
- [ ] The assessment can be persisted and reconstructed without changing its
      meaning.
- [ ] Fixtures cover fresh, stale, missing, contradictory, unavailable and
      not-implemented evidence.
- [ ] No probability or profitability semantics are introduced.

## Constraints

- Market Data remains authoritative for observed market facts and identity.
- Market Intelligence owns deterministic evidence composition and opportunity
  interpretation.
- Trading Core and Risk Domain remain authoritative for account, risk,
  authorization and execution state.
- Passive monitoring remains account-independent.
- AI remains optional and downstream of deterministic evidence.
- The implementation must not enable automatic trading or ActiveScan creation.

## Validation

- Market Intelligence unit and domain tests.
- Persistence round-trip tests where the assessment is stored.
- Stable fingerprint tests, including input ordering.
- Negative evidence fixtures for stale, missing, contradictory, unavailable and
  not-implemented states.
- Applicable module quality checks and `git diff --check`.
