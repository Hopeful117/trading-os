# Story 0247-04 - Evidence-Based Opportunity Ranking

## Status

Draft - pending Repository Analysis approval.

## Identity

- **ID:** `0247-04`
- **Epic:** `0247 - Trading Intelligence Evidence and Opportunity Quality V1`
- **Predecessor:** Story `0247-03 - Replayable Deterministic Signal Evaluation`
- **Primary module:** `market-intelligence`
- **Priority:** P1 - Trading Intelligence

## Goal

Rank valid trading opportunities with a deterministic, versioned and explainable policy that
combines existing business priority with explicit evidence-quality dimensions. The ranking must help
the trader decide which valid opportunities to investigate first without changing setup truth,
claiming predictive probability, or introducing trading authority.

## Context

Trading OS already exposes an `OpportunityRankingEngine` and a stable highest-score ordering. The
current `OpportunityScore` represents business priority rather than probability, and the existing
opportunity fusion policy derives it from observation confidence. `MarketThesis` now carries a
versioned `EvidenceQualityAssessment`, while Stories `0247-02` and `0247-03` provide deterministic
outcome and replay artifacts.

The current ranking behavior does not yet provide a versioned policy, evidence-traceable ranking
dimensions, or a structured rationale explaining why one opportunity is prioritized above another.
This Story defines that missing prioritization boundary without changing the truth of a
`StrategyMatch`, the meaning of an `OpportunityScore`, or the semantics of a market outcome.

## Problem

The system can sort opportunities by business score, but it cannot deterministically answer why a
valid opportunity should be investigated before another when evidence freshness, completeness,
provenance or contradiction differ. Adding arbitrary weights could create false precision and could
silently turn a business-priority score into an unapproved probability-like signal.

## Scope

### In scope

- A versioned, provider-neutral opportunity-ranking policy.
- Deterministic ranking of valid opportunities using existing business priority and evidence-quality
  dimensions.
- Explicit treatment of stale, incomplete, unavailable, contradictory and non-applicable evidence.
- Stable tie-breaking that does not depend on collection order or operational timestamps.
- A structured ranking rationale containing policy version, dimensions, values, reasons and outcome.
- A bounded, request-time ranking path over existing opportunity projections.
- Golden ranking fixtures and policy-version comparison tests.
- Replay comparison only as validation evidence for ranking stability, not as a predictive ranking
  input.

### Recommended initial policy boundary

The initial policy should be lexicographic rather than a weighted composite score:

1. Exclude or demote opportunities that are not eligible under the explicit ranking policy.
2. Prefer evidence that is admissible, non-contradictory and applicable.
3. Prefer fresher and more complete evidence where the policy defines an equivalent comparison.
4. Preserve existing business-priority semantics as a distinct ranking dimension.
5. Apply a canonical deterministic tie-breaker using stable opportunity identity and version.

The exact ordering and eligibility rules are Repository Analysis and Implementation Plan decisions,
not implicit requirements of this Story draft.

### Out of scope

- Probability, win rate, expectancy, profitability or calibrated statistical claims.
- Using replay outcomes, market outcomes or executed-trade outcomes as predictive ranking signals.
- Changing `StrategyEvaluator` or historical `MATCH` / `NO_MATCH` truth.
- Replacing or redefining `OpportunityScore` semantics.
- Arbitrary weight tuning without an approved validation strategy.
- Persisting a global ranking snapshot or performing historical backfill.
- Trader-facing Angular changes; those belong to Story `0247-05`.
- AI-generated ranking authority or automatic ActiveScan creation.
- Risk, authorization, broker, execution or position-management changes.
- New providers, microservices or generic workflow infrastructure.

## Acceptance Criteria

- Ranking the same semantic opportunity set with the same policy version produces the same order and
  rationale, independent of input collection order.
- The ranking policy has an explicit stable version and its version participates in the ranking
  result identity or comparison contract where required.
- Every ranking dimension is traceable to existing opportunity, thesis or evidence-quality data;
  no dimension is inferred from an unversioned provider response.
- Stale, incomplete, unavailable, contradictory and non-applicable evidence cannot silently outrank
  fresher, complete and valid evidence under the approved policy.
- Business priority remains distinguishable from evidence quality and is never represented as a
  probability or profitability estimate.
- Tie-breaking is explicit, deterministic and stable across repeated evaluations.
- The ranking rationale identifies included dimensions, policy version, relevant values and reasons
  for ordering or exclusion.
- Changing the ranking policy version produces an explicitly distinguishable comparison without
  mutating the underlying opportunities or their setup truth.
- Replay artifacts may be used to validate ranking reproducibility, but replay results do not become
  an implicit predictive or profitability input.
- The ranking path preserves the distinction between passive candidates, active opportunities,
  TradePlans, risk decisions and execution authority.
- Golden fixtures cover fresh, stale, incomplete, contradictory, unavailable, non-applicable,
  equal-score and equal-quality cases.
- No calibrated probability, profitability, risk, execution or automatic trading semantics are
  introduced.

## Constraints

- Market Data remains authoritative for provider-neutral market identity and observed facts.
- Market Intelligence owns deterministic evidence composition, opportunity interpretation and
  ranking policy.
- `StrategyEvaluator` remains authoritative for setup truth; ranking must not alter historical
  match semantics.
- Trading Core and Risk Domain remain authoritative for account context, risk, authorization and
  execution state.
- Passive monitoring remains account-independent and cannot create trading authority.
- Existing `MarketThesis`, `EvidenceQualityAssessment`, `TradingOpportunity` and
  `OpportunityScore` contracts must be reused unless a concrete contract gap is demonstrated.
- AI remains optional, advisory and downstream of deterministic evidence.
- Ranking must not be described as a recommendation guarantee or a probability estimate.

## Relevant Artifacts

- `docs/architecture/epics/0247-trading-intelligence-evidence-and-opportunity-quality-v1/epic.md`
- `docs/architecture/adr/ADR-033.md`
- `docs/architecture/adr/ADR-034.md`
- `docs/architecture/adr/ADR-035.md`
- `docs/architecture/stories/0247-01-deterministic-evidence-quality-contract/story.md`
- `docs/architecture/stories/0247-02-opportunity-outcome-capture/story.md`
- `docs/architecture/stories/0247-03-replayable-deterministic-signal-evaluation/story.md`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/opportunity/OpportunityRankingEngine.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/opportunity/DeterministicOpportunityFusionPolicy.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/opportunity/OpportunityScore.java`

## Validation Expectations

- Domain and application tests for ranking policy invariants.
- Golden ranking fixtures proving stable ordering and rationale generation.
- Input-order independence and deterministic tie-breaking tests.
- Freshness, completeness, provenance, contradiction, applicability and missing-evidence fixtures.
- Policy-version comparison tests proving underlying opportunities remain unchanged.
- Validation that replay comparison does not become a ranking input or a performance claim.
- Targeted `market-intelligence` Maven verification and `git diff --check`.

## Open Questions For Repository Analysis

- Which existing source should be authoritative when an opportunity and its referenced thesis expose
  different evidence-quality versions?
- Should ineligible opportunities be excluded from the ranked result or retained with an explicit
  non-prioritized state for auditability?
- Is request-time ranking sufficient for the first version, or is a persisted ranking rationale
  required by an existing consumer?
- Which canonical temporal field represents evidence freshness for cross-opportunity comparison?
- Does the existing opportunity projection contain enough evidence-quality data, or is a narrow
  provider-neutral projection extension required?
