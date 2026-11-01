# Code Review - Story 0190

## Scope

Reviewed the T0/T1 request construction, PAPER margin resolver, valuation
freshness semantics, focused tests, LIVE routing, Risk Domain boundary, and
compatibility policy.

## Findings

No blocking correctness finding was identified in the changed T1 path.

The review verified:

- T1 uses `FreshnessRequirement.t1()` at orchestration and resolver boundaries.
- PAPER margin uses the supplied valuation Fact `sourcePrice`.
- PAPER no longer performs a duplicate Market Data call or uses a provider
  preview fallback.
- Missing valuation context, mismatched freshness metadata, stale facts, and
  missing source price fail closed.
- Existing local exposure and exact T0 policy-version lookup remain in place.
- LIVE continues through the provider-backed `BrokerRequiredMarginClient` path.
- Risk Domain remains unchanged and receives only normalized monetary margin.
- Human authorization, T1 execution gating, settlement routing, and compatibility
  policy remain unchanged.

## Residual Risks

- ADR-083 database immutability and eight-decimal rate constraints remain Story
  0189 blockers and were intentionally not addressed.
- No full PAPER T0/T1/settlement E2E was added in this focused Story.
- Story 0188 still has no tracked `code-review.md` artifact.
- Full-suite and migration validation results must remain visible in the final
  report.

## Recommendation

Ready for human review of the T1 correctness change. Do not enable `CASH + CFD`.
