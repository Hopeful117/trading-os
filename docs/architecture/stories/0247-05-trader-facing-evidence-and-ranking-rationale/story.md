# Story 0247-05 - Trader-Facing Evidence and Ranking Rationale

## Status

Draft - pending Repository Analysis approval.

## Identity

- **ID:** `0247-05`
- **Epic:** `0247 - Trading Intelligence Evidence and Opportunity Quality V1`
- **Predecessor:** Story `0247-04 - Evidence-Based Opportunity Ranking`
- **Primary modules:** `market-intelligence`, `trading-os-web`
- **Priority:** P1 - Trading Intelligence

## Goal

Expose deterministic evidence-quality information and opportunity-ranking rationale in the existing
trader workflows so that a trader can understand why an opportunity was detected, which evidence
supports or weakens it, and why it is presented before or after another opportunity.

The presentation must improve investigation efficiency without turning evidence quality or ranking
priority into a probability, profitability claim, risk decision, recommendation guarantee, or
execution authority.

## Context

The preceding Epic Stories provide:

- versioned deterministic evidence-quality assessments on `MarketThesis`;
- explicit evidence dimensions for completeness, freshness, provenance, contradiction, applicability
  and missing policy;
- deterministic opportunity outcomes and invalidation observations;
- bounded replay artifacts and reproducibility evidence;
- a versioned evidence-based opportunity-ranking policy with stable tie-breaking and structured
  rationale.

The existing public opportunity API and Angular opportunities workflow already expose opportunities,
market-thesis information, business-priority score semantics and navigation into the decision
workspace. The missing product capability is a clear trader-facing presentation of the new quality
and prioritization information.

## Problem

The trader can currently inspect an opportunity and parts of its thesis, but cannot consistently
distinguish supporting, contradictory, missing or stale evidence in the opportunity workflow. The
ranking rationale is also not yet presented as structured, understandable information.

Without this presentation, deterministic evidence-quality and ranking work remains difficult to use
and may be misread as an unexplained recommendation or as a probability-like score.

## Scope

### In scope

- Extend the existing provider-neutral opportunity read contract only as required to expose the
  versioned evidence-quality and ranking-rationale data produced by Market Intelligence.
- Expose evidence-quality dimensions, states, relevant reasons and freshness/provenance information
  without inventing values unavailable in the authoritative domain model.
- Expose ranking policy version, business-priority semantics, ordering rationale and explicit
  exclusion or unavailable diagnostics where the selected workflow requires them.
- Present the information in existing Angular opportunity surfaces, including the active opportunity
  workflow and the opportunity detail or investigation path where applicable.
- Preserve reactive Angular data flows, explicit loading/error/empty states and responsive dashboard
  conventions.
- Make supporting, contradictory, missing, stale, unavailable and positive evidence states visibly
  distinguishable.
- Make the distinction between business priority and evidence quality explicit in labels and copy.
- Preserve navigation from passive candidates to opportunities, from opportunities to TradePlans and
  from TradePlans to risk and human validation workflows.
- Add frontend service, model, component and accessibility tests for the supported states.
- Add targeted backend/API contract tests if the public response contract changes.

### Out of scope

- Automatic trading, position management, risk evaluation or broker operations.
- Automatic ActiveScan creation or automatic opportunity selection for execution.
- AI-generated evidence, rationale or ranking authority.
- Probability, win-rate, expectancy, profitability or recommendation-guarantee claims.
- Replacing `OpportunityScore` or changing its `BUSINESS_PRIORITY_NOT_PROBABILITY` semantics.
- Changing the deterministic ranking policy, evidence-quality evaluator or setup truth.
- Persisting a separate UI ranking snapshot or performing historical backfill.
- New microservices, generic workflow infrastructure or unrelated Market Data changes.
- Redesigning the Angular application outside the existing opportunity and investigation workflows.
- Introducing account-risk or execution state into Market Intelligence evidence quality.

## Acceptance Criteria

- Supporting, contradictory, missing, stale, unavailable and positive evidence states are visibly and
  unambiguously distinguishable in the trader-facing opportunity workflow.
- A trader can inspect the evidence-quality dimensions relevant to an opportunity without consulting
  internal service logs or source code.
- Ranking rationale is visible and understandable, including the ranking policy version, the
  distinction between business priority and evidence quality, and the relevant reasons for ordering
  or exclusion.
- Evidence and rationale values are sourced from authoritative provider-neutral contracts; the
  frontend does not infer unsupported quality, freshness, probability or profitability values.
- Missing, stale, contradictory or unavailable information is presented as such and is not rendered
  as positive certainty.
- Passive candidates, active opportunities, TradePlans, risk decisions, human validation and
  execution remain distinct workflow concepts and navigation boundaries.
- No UI action introduced by this Story creates an ActiveScan, authorizes risk, places an order,
  changes a position or bypasses human validation.
- Existing Angular reactive conventions are preserved, including Observable-based data flows and
  explicit loading, empty and error states where applicable.
- The presentation remains usable on supported desktop and mobile layouts and provides accessible
  names, states and relationships for the new evidence and rationale elements.
- Existing opportunity loading and decision-workspace navigation continue to work when the new
  evidence or rationale data is absent, unavailable or partially populated according to the API
  contract.
- The ranking policy version and evidence-quality provenance remain distinguishable across API and
  UI representations; the UI does not mutate the underlying opportunity or thesis.

## Constraints

- Market Data remains authoritative for provider-neutral market identity and observed facts.
- Market Intelligence remains authoritative for deterministic evidence composition, evidence-quality
  assessment and opportunity ranking.
- `TradingOpportunity`, `MarketThesis`, `EvidenceQualityAssessment`, `OpportunityScore` and ranking
  rationale contracts must be reused unless a concrete response-contract gap is demonstrated.
- `OpportunityScore` must continue to be labeled and treated as business priority, not probability,
  profitability or risk-adjusted return.
- Trading Core and Risk Domain remain authoritative for account context, risk, authorization and
  execution state.
- Passive monitoring remains account-independent and cannot create trading authority.
- AI remains optional, advisory and downstream of deterministic evidence.
- Existing public API versioning and Gateway routing conventions must be preserved.
- Angular implementation must follow the existing project conventions for standalone components,
  Observables, async pipe, loading/error states and responsive styling.

## Relevant Artifacts

- `docs/architecture/epics/0247-trading-intelligence-evidence-and-opportunity-quality-v1/epic.md`
- `docs/architecture/adr/ADR-033.md`
- `docs/architecture/adr/ADR-034.md`
- `docs/architecture/adr/ADR-035.md`
- `docs/architecture/stories/0247-01-deterministic-evidence-quality-contract/story.md`
- `docs/architecture/stories/0247-02-opportunity-outcome-capture/story.md`
- `docs/architecture/stories/0247-03-replayable-deterministic-signal-evaluation/story.md`
- `docs/architecture/stories/0247-04-evidence-based-opportunity-ranking/story.md`
- `docs/architecture/stories/0247-04-evidence-based-opportunity-ranking/implementation-plan.md`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/OpportunityController.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/OpportunityResponse.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketThesisResponse.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/opportunity/OpportunityRankingService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/opportunity/OpportunityRankingRationale.java`
- `trading-os-web/src/app/core/services/opportunity.service.ts`
- `trading-os-web/src/app/core/models/opportunity.model.ts`
- `trading-os-web/src/app/features/opportunities/`

## Validation Expectations

- Angular service and model contract tests for evidence-quality and ranking-rationale responses.
- Angular component tests for positive, supporting, contradictory, missing, stale, unavailable,
  loading, empty and error states.
- Tests proving the business-priority score is labeled distinctly from evidence quality and is not
  presented as a probability or profitability value.
- Accessibility validation for new labels, status indicators, expandable or detailed rationale
  content and keyboard navigation where applicable.
- Responsive review of the active opportunity and opportunity-detail workflows on desktop and mobile
  layouts.
- Backend/API contract tests for any changed response DTO or controller behavior.
- Angular production build.
- Targeted `market-intelligence` validation when backend contracts change.
- `git diff --check`.

## Open Questions For Repository Analysis

- Should the existing opportunity endpoint return ranking rationale directly, or should a dedicated
  read endpoint be introduced within the existing API boundary?
- Does the current response contract contain enough persisted or request-time data to expose the
  ranking rationale without adding persistence or a new ranking snapshot?
- Which workflow is authoritative for the first presentation: the active opportunity list, the
  opportunity detail view, the decision workspace, or a bounded combination of these surfaces?
- How should excluded opportunities and unavailable rationale be represented without implying that an
  excluded candidate is a ranked opportunity?
- Which evidence references and freshness values are safe and useful for trader-facing display while
  preserving provider-neutral contracts and avoiding internal implementation leakage?
- What responsive and accessible presentation pattern best distinguishes quality states without
  relying on color alone?
