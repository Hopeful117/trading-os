# Independent Code Review - Story 0247-01

## Verdict

`NEEDS CHANGES`

## Findings

### Major - Applicability is inferred from thesis state

**Location:** `EvidenceQualityEvaluator.java:135-141`

The applicability dimension is `SATISFIED` for every thesis except `NO_TRADE`.
The evaluator does not consume existing strategy applicability or provenance
evidence, despite the Story and implementation plan requiring the existing
strategy boundary to be reused.

**Impact:** A thesis can be assessed as supported without demonstrating that
the relevant strategy is applicable.

### Major - Optional negative evidence can produce `SUPPORTED`

**Location:** `EvidenceQualityEvaluator.java:61-79`, `82-99`, `143-166`

Completeness and freshness inspect only required evidence. Optional missing,
unavailable or stale evidence can therefore leave every dimension satisfied and
produce `QualityStatus.SUPPORTED`.

**Impact:** This conflicts with the acceptance criterion that missing,
unavailable and stale evidence cannot be represented as positive certainty.

### Major - Freshness ignores evidence validity windows

**Location:** `EvidenceQualityEvaluator.java:82-99`

Freshness checks the explicit `STALE` availability and source cutoff ordering,
but ignores `observedAt` and `validUntil`.

**Impact:** Evidence whose validity window ended before the evaluation cutoff
can still be classified as fresh and contribute to `SUPPORTED`.

### Major - Fingerprint omits `validUntil`

**Location:** `EvidenceQualityEvaluator.java:178-186`

The input fingerprint omits the evidence validity window. A material change to
`validUntil` therefore does not change the input fingerprint.

**Impact:** Reproducibility, cache identity and audit semantics are weakened.

### Minor - Acceptance fixtures are incomplete

**Location:** `EvidenceQualityEvaluatorTest.java:27-86`

Tests do not independently cover unavailable evidence, validity-window staleness,
optional negative evidence, applicability, provenance degradation or all
quality-relevant fingerprint changes.

### Minor - Assessment provenance is indirect

**Location:** `EvidenceQualityAssessment.java:10-18`, `70-79`

Dimension assessments retain only evidence UUID references. Provider,
instrument, timeframe, source cutoff and rule version remain available only by
joining back to the thesis evidence.

**Impact:** The assessment contract itself does not fully expose the stated
provenance projection.

## Positive Review Notes

- No unauthorized service, broker, risk, execution, frontend, AI or ranking
  boundary was introduced.
- Persistence reuses the existing immutable Market Thesis payload.
- Legacy Market Thesis payload compatibility is preserved.
- Basic deterministic evaluation, ordering independence, negative states and a
  persistence round trip are covered.

## Validation Context

The reviewer did not execute commands. The implementation report records that
focused tests and `mvn verify` passed, but the semantic gaps above require
additional behavior-level tests and corrections.

## Recommended Action

Correct applicability, optional-negative-evidence and validity-window freshness
semantics. Include all material evidence fields in the fingerprint and add the
missing acceptance fixtures before treating the Story as review-ready.
