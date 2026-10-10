# Story 0081 - Preserve Opportunity Decision Context

## Metadata

**ID:** `0081`
**Title:** Preserve Opportunity Decision Context
**Status:** Review

---

## Goal

An authenticated trader who selects an account, runs a market scan, and opens
one of its opportunities reaches the account-scoped Decision Workspace without
having to select the same account and market again.

The opportunity detail remains a useful explanation and triage surface, while
the Decision Workspace becomes the continuous decision surface with the
opportunity, account, and eligible market context preserved.

---

## Context

Stories `0056`, `0057`, `0060`, and `0062` established the account-first
Decision Workspace, account-scoped market context, contextual manual trading,
and Opportunity convergence.

The current implementation already transports `opportunityId` and `marketId`
from Opportunity Detail to the Workspace. The scan account remains local to the
scan panel, however, and is not transported through the Opportunity navigation.
The Workspace therefore starts without an account and cannot restore the market
until the trader selects an account again.

The current investigation also established that `ActiveScan` owns an
`accountId`, while the frontend `OpportunityResponse` does not currently expose
one. The implementation must establish the authoritative context source rather
than infer an account from an instrument symbol or market id.

---

## Problem

The current journey is:

```text
Opportunities
  -> select account
  -> run scan
  -> open opportunity
  -> open Decision Workspace
  -> select account again
  -> select market again
```

This causes unnecessary input, loses the relationship between the scan and its
account context, and allows the trader to select a different account from the
one used to produce the opportunity. It also prevents the market context from
being restored safely before account eligibility has been resolved.

---

## Scope

* Preserve the selected scan account when navigating from scan results or the
  Opportunities surface to Opportunity Detail and the Decision Workspace.
* Preserve the authoritative `marketId` associated with the selected
  opportunity.
* Define and implement the authoritative transport of account context between
  the scan, Opportunity Detail, and Decision Workspace without deriving it from
  a free-form instrument symbol.
* Allow the Decision Workspace to restore the transported account and market
  context after the account context has been resolved.
* Keep the Workspace account ownership and market eligibility checks
  authoritative; transported URL or frontend state is only context input.
* Prevent market streams from opening before the restored account context proves
  the market eligible and tradable.
* Preserve a truthful fallback for direct Opportunity Detail or Workspace URLs
  where no account context is available.
* Make the selected account, selected market, and Opportunity context visible
  so the trader can confirm the decision context before preparing a Trade Plan.
* Add focused Angular regression tests for context propagation, restoration,
  account changes, stale or ineligible markets, and direct-entry fallback.
* Add backend contract or persistence changes only if the investigation proves
  that the current authoritative Opportunity/ActiveScan model cannot transport
  the required account context safely.

---

## Out of Scope

* Removing the Opportunities list, scan controls, or Opportunity Detail.
* Removing the compatibility `/trade-planning/prepare/:opportunityId` route.
* Merging MANUAL and OPPORTUNITY TradePlan origins.
* Changing Opportunity scoring, ranking, expiration, or strategy semantics.
* Reimplementing account ownership, market eligibility, tradability, Risk, or
  broker rules in Angular.
* Automatic TradePlan creation, Risk evaluation, authorization, or execution.
* Adding persistent user workspace preferences or a new state-management
  framework.
* Introducing a new realtime transport or changing Market Data ownership.
* Changing unrelated navigation or standalone `/markets` behavior.

---

## Acceptance Criteria

* [ ] After an account is selected for a scan, opening an opportunity produced
      by that scan preserves the account context through Opportunity Detail and
      into Decision Workspace.
* [ ] The active Opportunity navigation carries an authoritative opportunity
      identity and market identity; it never resolves the market from the
      instrument display string.
* [ ] When valid account context is present, Decision Workspace resolves that
      account and restores the opportunity market without requiring duplicate
      account or market input.
* [ ] The Workspace does not open a market stream until the restored account
      context confirms the market is eligible and the market is confirmed
      tradable.
* [ ] A transported account or market that is missing, not owned, stale, or
      ineligible is rejected or cleared truthfully; frontend state cannot bypass
      backend ownership or eligibility checks.
* [ ] Changing the account clears the restored market and prevents stale
      opportunity context from being used with the new account unless the
      relationship is explicitly revalidated.
* [ ] The Workspace displays the selected account, market, and Opportunity
      context together and distinguishes analytical Opportunity information from
      Risk approval or execution authorization.
* [ ] Direct entry without account context remains actionable through the
      existing account-first selection flow and does not fabricate a market or
      account association.
* [ ] The existing `Prepare from opportunity` action still creates an
      OPPORTUNITY TradePlan through the existing authenticated API and does not
      create an ExecutionIntent during preparation.
* [ ] Angular tests cover propagation, restoration after asynchronous context
      resolution, invalid context, account changes, direct entry, and stream
      suppression.
* [ ] `npm run test:ci`, `npm run build`, and `git diff --check` pass.
* [ ] No unrelated behavior or pre-existing user changes are modified.

---

## Constraints

* Trading Core remains authoritative for account identity and ownership.
* Market Intelligence remains authoritative for Opportunity identity,
  provenance, and scan scope.
* Market Data remains authoritative for market identity and tradability facts.
* Risk remains deterministic and fail-closed.
* Frontend-provided context must be treated as a navigation hint and validated
  against authoritative APIs.
* Do not infer account identity from instrument, provider, or market display
  text.
* Preserve Angular standalone components, typed contracts, Observables, and
  async-pipe conventions.
* Preserve human approval before Risk-authorized execution.
* Do not commit, push, merge, or discard unrelated changes automatically.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-001.md` - Trading OS Vision and Human Authority
* `docs/architecture/adr/ADR-014.md` - Trading Decision Pipeline
* `docs/architecture/adr/ADR-043.md` - Account and BrokerAccount Identity
* `docs/architecture/adr/ADR-044.md` - Inter-Service Trust and Actor Propagation
* `docs/architecture/adr/ADR-047.md` - Opportunity-Origin TradePlan Context

---

## Relevant Stories

* `0056` - Account-First Market Decision Context
* `0057` - Account-Scoped Market Workspace
* `0062` - Converge Opportunity Decisions into the Decision Workspace

---

## Relevant Modules

* `trading-os-web`
* `market-intelligence` only if the authoritative account-context contract
  requires an additive change
* `trading-core` only if account ownership or TradePlan preparation contracts
  require clarification

---

## Validation

* Focused Angular tests for Opportunities, Opportunity Detail, Decision
  Workspace, routing, and scan-context propagation.
* Regression tests proving no market-data subscription occurs before account
  eligibility resolution.
* Existing Opportunity-origin TradePlan orchestration and preparation tests.
* Angular `npm run test:ci`.
* Angular production `npm run build`.
* `git diff --check`.
* Authenticated PAPER walkthrough covering scan account selection, opportunity
  opening, workspace restoration, context confirmation, and preparation without
  duplicate account or market selection.

---

## Definition of Done

* [ ] Repository Analysis approved
* [ ] Implementation Plan approved when required
* [ ] Implementation completed
* [ ] Relevant validation executed
* [ ] Authenticated runtime walkthrough completed
* [ ] Diff reviewed in IntelliJ
* [ ] Code Review approved
* [ ] Engineering Report completed
* [ ] Human commit created
