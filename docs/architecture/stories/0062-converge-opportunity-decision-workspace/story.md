# Story 0062 - Converge Opportunity Decisions into the Decision Workspace

## Metadata

**ID:** `0062`
**Title:** Converge Opportunity Decisions into the Decision Workspace
**Status:** In Progress

## Goal

Make `DecisionWorkspace` the canonical account-scoped decision surface while
preserving `Opportunities` as the Market Intelligence triage and discovery
surface.

Both entry points must converge on the same TradePlan, deterministic Risk,
human approval, and execution lifecycle.

## Context

Stories `0056`, `0057`, and `0060` established the account-first Workspace,
account-scoped market context, and contextual MANUAL TradePlan ticket. Story
`0021` established the authenticated Opportunities list and detail journey.

The existing Opportunity path still leaves the Workspace, asks for the account
again, and creates a plan through a separate preparation page. This duplicates
presentation and loses live market context even though the downstream backend
pipeline is already shared.

## Problem

The product currently exposes two visually separate preparation journeys:

```text
Opportunity -> OpportunityDetail -> PreparePlanPage -> PlanPage
DecisionWorkspace -> ManualTradeTicket -> PlanPage
```

The distinction between an analytical Opportunity and a human MANUAL proposal
is valid, but the account, market, live context, and downstream decision surface
should not be duplicated.

## Scope

* Preserve `/opportunities` as the scan result and opportunity triage surface.
* Add an authoritative `marketId` to newly produced Opportunity responses.
* Navigate an active Opportunity into `DecisionWorkspace` with its
  `opportunityId` and `marketId`.
* Let the user select or confirm the account in the Workspace.
* Display Opportunity provenance and an explicit `Prepare from opportunity`
  action in the Workspace.
* Create an Opportunity-origin TradePlan through the existing API and navigate
  to the existing `PlanPage`.
* Preserve the existing `PreparePlanPage` route as a compatibility fallback.
* Keep MANUAL and OPPORTUNITY as distinct TradePlan origins while sharing the
  same downstream lifecycle.
* Add focused backend and Angular regression tests.

## Out of Scope

* Removing the Opportunities list or scan functionality.
* Merging Opportunity and MANUAL domain concepts.
* A second Risk or execution pipeline.
* Automatic TradePlan creation or execution.
* LIVE runtime execution.
* Reworking Market Intelligence scoring, ranking, or strategy semantics.
* Removing the compatibility preparation route before runtime validation.

## Acceptance Criteria

* [ ] Active Opportunity responses expose an authoritative `marketId` when
      produced by the current analysis pipeline.
* [ ] An active Opportunity can open the account-scoped Decision Workspace with
      its Opportunity and market context.
* [ ] The Workspace never opens a market stream before account eligibility is
      resolved.
* [ ] The Workspace displays the Opportunity context without presenting it as
      Risk approval.
* [ ] `Prepare from opportunity` creates an OPPORTUNITY TradePlan using the
      existing authenticated API and no ExecutionIntent.
* [ ] The existing MANUAL ticket continues to create MANUAL TradePlans.
* [ ] Both origins reach the existing PlanPage, Risk, human authorization, and
      execution lifecycle without origin-specific execution code.
* [ ] The legacy preparation route remains functional during migration.
* [ ] Frontend and backend focused tests, production build, and `git diff
      --check` pass.
* [ ] No unrelated worktree changes are modified.

## Constraints

* Preserve ADR-001, ADR-014, ADR-028, ADR-029, ADR-031, ADR-043, ADR-044, and
  ADR-047.
* Market Intelligence remains authoritative for Opportunity identity and
  provenance.
* Trading Core remains authoritative for account ownership and TradePlan
  orchestration.
* Market Data remains authoritative for market identity and live facts.
* Risk remains deterministic and fail-closed.
* Human approval remains mandatory before execution.
* Preserve Observable and async-pipe Angular conventions.
* Do not commit, push, merge, or remove unrelated user changes.

## Relevant ADRs

* `docs/architecture/adr/ADR-001.md`
* `docs/architecture/adr/ADR-014.md`
* `docs/architecture/adr/ADR-028.md`
* `docs/architecture/adr/ADR-029.md`
* `docs/architecture/adr/ADR-031.md`
* `docs/architecture/adr/ADR-043.md`
* `docs/architecture/adr/ADR-044.md`
* `docs/architecture/adr/ADR-047.md`

## Relevant Modules

* `market-intelligence`
* `trading-core`
* `gateway` if public contracts require routing changes
* `trading-os-web`

## Validation

* Market Intelligence opportunity/domain/persistence tests.
* Trading Core opportunity TradePlan orchestration tests.
* Angular Opportunity, Workspace, and route tests.
* Angular production build and test suite.
* Authenticated PAPER runtime walkthrough through both entry points.
* `git diff --check`.

## Definition of Done

* [x] Story scope recorded.
* [ ] Implementation completed.
* [ ] Relevant validation executed.
* [ ] Runtime convergence evidence recorded.
* [ ] Diff reviewed in IntelliJ.
* [ ] Code Review approved.
* [ ] Human commit created.
