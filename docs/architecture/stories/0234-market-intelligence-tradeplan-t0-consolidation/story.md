# Story 0234 - Market Intelligence Quality and TradePlan/T0 Consolidation

## Metadata

**ID:** `0234`
**Title:** Market Intelligence Quality and TradePlan/T0 Consolidation
**Status:** In Progress
**Priority:** P0
**Related ADRs:** `ADR-076`, `ADR-079`, `ADR-086`
**Related Stories:** `0166`, `0168`, `0195`, `0196`, `0198`

## Goal

Clarify the distinction between opportunity signal strength, evidence quality
and TradePlan eligibility; preserve deterministic tradeability missingness; make
the existing TradePlan-to-T0 workflow reliably testable; and restore H2
integration-test compatibility without weakening LIVE safety boundaries.

## Scope

- Make the existing `OpportunityScore` business-priority semantics explicit to
  API and frontend consumers.
- Verify and, only where demonstrated, correct propagation of tradeability
  evidence and diagnostics.
- Make the existing V69 schema change portable to the supported H2 integration
  test configuration while preserving PostgreSQL behavior and migration
  integrity.
- Add deterministic tests for valid candidate admission, `NO_TRADE` rejection,
  capability-scoped Risk diagnostics, mandatory missing facts, identity and
  stale-version rejection, and public TradePlan-to-T0 invocation.
- Document the resulting contracts, limitations and validation evidence.

## Non-Goals

- No LIVE broker calls, synchronization, order, position or authorization.
- No fabricated LIVE opportunity or synthetic broker.
- No new provider, ingestion subsystem, microservice, AI model or scoring model.
- No change to Risk thresholds, policy versions or fail-closed execution gates.
- No conversion of missing tradeability or margin facts into positive evidence.

## Acceptance Criteria

- `OpportunityScore` remains the deterministic `0..100` business-priority score
  derived from observation confidence and is explicitly not a probability.
- API/MCP and Angular consumers can distinguish signal score, analysis quality
  and Market Thesis eligibility.
- A high-scoring `NO_TRADE` opportunity remains non-actionable and visibly
  blocked; `DEGRADED` analysis quality remains visible.
- Tradeability preserves market identity, cutoff and freshness. Existing
  `LIVE_CURRENT` behavior remains explicit and fail-closed except for the
  narrowly approved Forex quote-based profile defined by ADR-100 and Story 0235.
- Available tradeability evidence reaches Market Thesis; unavailable,
  stale, unknown and not-implemented evidence retains its precise diagnostic.
- V69 starts successfully under H2 and PostgreSQL semantics remain unchanged;
  no applied migration is silently rewritten without checksum assessment.
- Deterministic tests cover valid candidate, `NO_TRADE`, partial evidence,
  mandatory missing evidence, identity mismatch and stale version behavior.
- Partial Risk results never become execution authorization and mandatory
  incomplete invariants block aggregate approval.
- No test reaches a real broker or modifies the FTMO LIVE account.

## Validation

- Targeted Market Intelligence score, thesis, tradeability and TradePlan tests.
- Trading Core orchestration and V69 migration/integration tests.
- Risk Domain and Trading Core evidence-admission tests.
- Angular tests/build if presentation contracts change.
- `git diff --check`.

## Safety Invariants

```text
SIGNAL_SCORE != PROBABILITY = true
NO_TRADE != TRADE_RECOMMENDATION = true
MISSING_TRADEABILITY != POSITIVE_EVIDENCE = true
PARTIAL_RISK_RESULT != EXECUTION_AUTHORIZATION = true
MANDATORY_INCOMPLETE_INVARIANT = EXECUTION_BLOCKED
CTRADER_LIVE_CALLS = false
```
