# Story 0157 - Establish the Trader Cockpit Foundation

## Metadata

**ID:** `0157`
**Title:** Establish the Trader Cockpit Foundation
**Status:** Review

## Goal

Make the existing account-scoped Decision Workspace a usable first Trader
Cockpit surface for discretionary trading by exposing authoritative account and
Risk context alongside the existing market investigation workflow.

## Context

The frontend already has an account-first `DecisionWorkspace`, workstation
presentation primitives, and a provider-neutral Dashboard summary containing
account, Risk, position, alert, and freshness facts. The Cockpit should compose
these existing capabilities rather than create a new dashboard authority or
replace the specialized Trade Plan and Positions workflows.

Relevant prior work includes Stories 0134, 0135, 0136, and 0137. Current
architecture keeps `DecisionWorkspace` as the frontend orchestration boundary,
Trading Core authoritative for account and Risk facts, and human approval
required before Trade Plan or execution workflows.

## Problem

The current Decision Workspace primarily presents market selection and market
investigation. It does not yet give the trader a compact, persistent view of the
selected account's usable capital, Risk status, active exposure, alerts, and
data freshness while investigating a market. This makes the primary daily
decision surface incomplete and forces context switching to the Dashboard.

## Scope

* Evolve the existing `DecisionWorkspace` shell into the first bounded Trader
  Cockpit surface.
* Load the existing account-scoped Dashboard summary through the established
  frontend service and preserve its authoritative data semantics.
* Add a compact account and Risk context region showing balance/equity, daily
  PnL, drawdown, Risk status, and data freshness with truthful loading,
  unavailable, degraded, and error states.
* Surface bounded active-position and alert summaries sufficient for situational
  awareness, linking to the existing specialized workflows where applicable.
* Preserve the current account-first market selection, market investigation,
  opportunity preparation, Trade Plan, and execution boundaries.
* Add focused Angular tests for composition, account changes, state semantics,
  and navigation/link behavior.

## Out of Scope

* New backend endpoints, DTOs, persistence, or service ownership changes.
* A new BFF, global store, or second dashboard aggregation authority.
* Replacing `PlanPage`, open-position monitoring, challenges, news, or history
  with Cockpit implementations.
* AI synthesis, autonomous trading, strategy/ranking changes, or new Risk rules.
* cTrader work, broker contract changes, execution behavior, or schema changes.
* Broad redesign of unrelated frontend routes or workstation primitives.

## Acceptance Criteria

* [x] Selecting an account loads its existing Dashboard summary without loading
      account data before account selection.
* [x] The Decision Workspace presents compact account, Risk, exposure, alert,
      and freshness context while preserving the existing market investigation
      surface.
* [x] Account changes cancel or replace prior account-scoped context without
      displaying facts from the previous account.
* [x] Loading, unavailable, degraded, stale, and error states are explicit and
      do not imply Risk approval or execution authorization.
* [x] Position and alert summaries link to existing workflows without creating
      duplicate domain logic.
* [x] Existing account-first market selection, Opportunity preparation, Trade
      Plan navigation, and stream lifecycle behavior remain intact.
* [x] Focused Angular tests, the frontend production build, formatting, and
      `git diff --check` pass.
* [x] No backend, Risk, execution, broker, schema, or unrelated frontend files
      change.

## Constraints

* Preserve existing service responsibilities and deterministic business
  authority.
* Keep `DecisionWorkspace` as the frontend orchestration boundary.
* Reuse the existing Dashboard summary contract and workstation primitives.
* Keep Cockpit facts observational; they must not authorize or reject trades.
* Keep human approval required for Trade Plan and execution workflows.
* Do not introduce new dependencies or global state without an approved ADR.
* Do not commit, push, merge, or alter unrelated user changes automatically.

## Relevant ADRs

* `docs/architecture/adr/ADR-001.md`
* `docs/architecture/adr/ADR-014.md`
* `docs/architecture/adr/ADR-019.md`
* `docs/architecture/adr/ADR-031.md`
* `docs/architecture/adr/ADR-047.md`

## Relevant Stories

* `0134` - Establish account-scoped trading workspace context.
* `0135` - Establish workstation presentation primitives and compact context
  strip.
* `0136` - Recompose Decision Workspace into the Professional Trading
  Workstation.
* `0137` - Finalize the Professional Decision Workspace.

## Relevant Modules

* `trading-os-web/src/app/features/decision-workspace`
* `trading-os-web/src/app/features/dashboard`
* `trading-os-web/src/app/core/services/dashboard.service.ts`
* `trading-os-web/src/app/core/models/dashboard-summary.model.ts`
* `trading-os-web/src/app/shared/workstation`

## Validation

* Targeted Decision Workspace and new Cockpit component Angular tests.
* Existing Dashboard frontend tests.
* Angular production build.
* Prettier check and `git diff --check`.
* Browser validation at desktop and narrow mobile widths where the local
  environment permits.

## Definition of Done

* [ ] Repository Analysis approved.
* [ ] Implementation Plan approved when required.
* [x] Implementation completed.
* [x] Relevant validation executed.
* [x] Documentation reconciliation completed.
* [x] Independent Code Review approved.
* [ ] Human commit created.
