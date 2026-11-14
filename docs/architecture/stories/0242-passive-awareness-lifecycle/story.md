# Story 0242 - Expose the Passive Awareness Lifecycle

## Metadata

**ID:** `0242`

**Title:** Expose the Passive Awareness Lifecycle

**Status:** Implementation merged (commit `41b9a741`, PR #294) - Implementation Report, independent code review and human review approval missing; see Story 0243 Repository Analysis section 2

**Priority:** P1 - Trading Intelligence

**Epic:** `0238`

**Related ADRs:** `ADR-025`, `ADR-026`, `ADR-033`, `ADR-034`, `ADR-056`, `ADR-076`, `ADR-087`, `ADR-100`, `ADR-102`

**Predecessor:** `0241`

## Goal

Project deterministic passive observations and valid passive opportunities as an
explainable, freshness-aware awareness feed without inventing a new candidate
aggregate.

## Product Value

The trader can distinguish a market fact, a passive candidate and a current
account-actionable opportunity.

## Existing Components Reused

- `Observation` and observation repositories.
- `MarketThesis` and thesis evidence/provenance.
- `StrategyMatch` and `TradingOpportunity(PASSIVE_SCAN)`.
- Existing opportunity fusion and expiration policies.
- Active scan evidence projection patterns.

## Scope

- Define a passive awareness read projection over existing authoritative models.
- Define a passive candidate as the latest `PASSIVE_SCAN` opportunity version
  whose required StrategyMatch, Observation and `MarketThesis.state=CANDIDATE`
  references are valid at projection time.
- Include canonical identity, provider/product identity and passive provenance.
- Include observation time, evidence cutoff, completion time and validity window.
- Include freshness, quality, missing evidence and contradiction diagnostics.
- Distinguish observation, candidate and opportunity presentation states.
- Apply existing opportunity deduplication and validity semantics.
- Prevent stale or non-positive evidence from appearing current/actionable.
- Provide bounded filtering and deterministic ordering.

## Out of Scope

- A persisted `MarketCandidate` aggregate.
- New strategy scoring or global ranking.
- Automatic active investigation.
- AI wording or notification delivery.
- Changes to Market Thesis authority.

## Acceptance Criteria

- [ ] Observation facts remain distinguishable from passive opportunities.
- [ ] A passive opportunity is shown only when existing thesis and strategy invariants pass.
- [ ] `OpportunityStatus.ACTIVE` on a passive opportunity is displayed as a
      lifecycle state and never presented as account-authorized or trade-ready.
- [ ] Missing, stale or contradictory evidence is explicitly visible.
- [ ] Every item carries market/provider/product identity and provenance references.
- [ ] Expired or superseded items are not presented as current.
- [ ] Repeated equivalent detections do not create unbounded duplicates.
- [ ] The projection does not claim account-specific tradability.
- [ ] Accountless passive opportunities cannot enter trade-plan generation;
      this boundary has a regression test.
- [ ] Read authorization prevents cross-user leakage where applicable.
- [ ] Projection and freshness tests cover positive and non-positive outcomes.
- [ ] Deterministic fixture tests prove a qualifying event creates one candidate,
      a non-qualifying market creates none, and repeated equivalent events do
      not flood the feed.

## Dependencies

Depends on Stories 0240 and 0241. Blocks Story 0243 and the dashboard in 0244.

## Migration and Safety

Use additive read contracts and existing records. Do not reinterpret historical
opportunities or mutate broker/account state.

## Validation

- Projection tests for all evidence states.
- Opportunity deduplication and expiration tests.
- Freshness/cutoff compatibility tests.
- Authorization and no-secret tests.
