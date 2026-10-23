# Story 0149 - Authoritative Provider Facts to Deterministic Risk Integration

## Metadata

**ID:** `0149`
**Title:** `Authoritative Provider Facts to Deterministic Risk Integration`
**Status:** `Implemented locally - real provider validation required`
**Related Stories:** `0148`
**Related ADRs:** `ADR-028`, `ADR-031`, `ADR-071`, `ADR-072`, `ADR-074`

## Goal

Allow Trading Core to consume the existing provider-neutral authoritative v2
account snapshot as deterministic Risk input without weakening provider
ownership, freshness checks or fail-closed behavior.

## Scope

- Route live Risk fact loading through the provider-neutral v2 snapshot when it
  contains authoritative account facts.
- Preserve the existing Kraken RiskSnapshot path as a compatibility fallback
  when the v2 route is retryably unavailable or exposes its legacy compatibility
  shape.
- Validate broker-account, provider-registration and external-account identity
  before mapping facts.
- Preserve authoritative settlement assets and restrict bounded deals to the
  requested Risk interval.
- Fail closed for stale, incomplete, malformed or insufficient position Risk
  facts.
- Keep cTrader transport, DTOs, credentials and provider decisions outside Risk
  Domain and Trading Core business rules.

## Out Of Scope

- FTMO Challenge formulas or lifecycle rules.
- cTrader execution or mutation.
- New position valuation, margin or protective-stop provider facts.
- Changes to deterministic Risk formulas.
- Real cTrader account validation.

## Acceptance Criteria

- [x] Fresh and complete authoritative v2 account facts reach the existing
      deterministic Risk facts boundary.
- [x] Stale or incomplete authoritative observations fail closed.
- [x] Snapshot identity mismatches fail closed without legacy fallback.
- [x] Retryable v2 unavailability preserves the existing Kraken Risk path.
- [x] Deals outside the requested Risk interval are excluded.
- [x] Deal settlement assets are preserved without substitution.
- [x] Open positions without provider margin/protection facts remain ineligible
      for Risk evaluation.
- [x] No provider-specific type or execution capability is added to Risk Domain.
- [x] Existing Risk, synchronization and focused regression tests remain green.

## Validation Boundary

```text
LOCAL_DETERMINISTIC_VALIDATION = COMPLETE
REAL_CTRADER_READ_VALIDATION = REQUIRED
CTRADER_EXECUTION = NO
FTMO_CHALLENGE_INTERPRETATION = NO
```
