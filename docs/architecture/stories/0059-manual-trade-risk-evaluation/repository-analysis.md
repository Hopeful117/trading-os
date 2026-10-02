# Repository Analysis - Story 0059

## Scope

Story 0059 is intended to prove that a `MANUAL` TradePlan can use the existing
Trading Core Risk Evaluation pipeline without an Opportunity-specific branch or
a second authorization path.

## Current Repository Evidence

Story 0058 already provides the normalized `TradePlan` origin, authenticated
manual author, account references, planning context and explicit execution
inputs. The existing risk handoff is TradePlan-based and should therefore remain
origin-neutral.

The implementation commit for this Story changed only the cross-service risk
provenance contract:

* `TradePlanRiskSnapshot` preserves TradePlan origin and manual provenance;
* `TradePlanRiskHandoffService` forwards the same immutable context for manual
  plans;
* `MarketIntelligenceRiskClient` preserves the compatible transport contract;
* focused handoff and client tests cover the added provenance.

## Current Gaps

The repository evidence does not show a dedicated end-to-end manual risk
evaluation implementation or runtime proof for this Story. Story 0061 later
records that manual risk provenance compatibility exists, but the complete
approved PAPER path was validated in that later Story.

## Responsibility Boundary

* Trading Core remains authoritative for account, market, portfolio and rule
  context assembly.
* Risk Domain remains authoritative for deterministic financial decisions.
* Market Intelligence owns TradePlan provenance and risk-handoff context.
* Manual rationale is explanatory input and cannot authorize a plan.
* Execution Intent and broker mutation remain outside this Story.

## Implementation Boundary

Included:

* Preserve manual origin and provenance through the existing risk handoff.
* Keep opportunity-origin compatibility.
* Add focused regression coverage.

Excluded:

* New Risk rules or thresholds.
* New execution behavior.
* Frontend changes.
* Runtime approval or PAPER execution proof.

## Validation Expectations

* Focused Market Intelligence handoff tests.
* Focused Trading Core client tests.
* Existing opportunity-origin regressions.
* `git diff --check`.
* A separate runtime validation is required before claiming the full Story
  acceptance criteria.

## Architectural Assessment

No new ADR is required. The delivered change composes the existing TradePlan,
Risk Evaluation and authenticated ownership boundaries.
