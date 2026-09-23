# Story 0061 - Validate the MANUAL PAPER Daily Driver

## Metadata

**ID:** `0061`
**Title:** Validate the MANUAL PAPER Daily Driver
**Status:** Draft

## Goal

Prove that an authenticated trader can complete a normal Web-based discretionary
PAPER trade lifecycle from the account-scoped Decision Workspace using a
`MANUAL` TradePlan and the existing deterministic Risk and execution pipeline.

The target flow is:

```text
PAPER account
    -> Decision Workspace
    -> eligible market and current context
    -> MANUAL TradePlan
    -> human acceptance
    -> deterministic Risk verdict
    -> explicit human execution authorization
    -> PAPER fill
    -> persisted position and valuation
    -> reload
    -> full close
    -> reload
    -> history and TradePlan continuity
```

## Context

Stories 0056 and 0057 provide account-first market context. Story 0058 provides
authenticated MANUAL TradePlans. Story 0059 added manual-origin risk provenance
compatibility. The current worktree contains Story 0060, which embeds a reusable
manual ticket in the Decision Workspace, but that work is uncommitted and
requires human review.

The opportunity-origin PAPER journey was runtime-validated by Story 0048. The
manual journey has only reached creation and a deterministic risk rejection due
to unavailable current valuation. The existing services appear origin-neutral;
this Story must validate that conclusion through the official UI and must fix
only concrete blockers discovered during that validation.

## Problem

Trading OS has the individual manual, Risk, execution, PAPER settlement,
position and history capabilities, but does not yet have accepted runtime
evidence that they form one usable MANUAL PAPER daily workflow.

Without this validation, the product cannot distinguish a real daily-driver
blocker from a missing advanced feature, and the trader may still need manual
HTTP calls, fabricated identifiers or a workaround around Risk.

## Scope

### Included

* Human review of the current Story 0060 workspace-integrated manual ticket as a
  prerequisite to this Story's accepted baseline.
* Official authenticated Web journey using a PAPER account only.
* Selection of an eligible market and inspection of the existing market context.
* Creation of a canonical `MANUAL` TradePlan from the normal UI.
* Human acceptance of the proposed plan.
* Deterministic Risk Evaluation using authoritative account, portfolio, market
  and rule facts.
* Explicit human authorization before Execution Intent creation.
* Existing PAPER execution, settlement, position persistence, valuation and
  reload behavior.
* Full exposure close through the existing official UI.
* Execution/history and TradePlan continuity verification.
* Focused tests or minimal fixes only when the official journey demonstrates a
  concrete blocker in these existing contracts.
* A durable runtime acceptance report with exact evidence and negative evidence.

### Required negative evidence

* A rejected or unavailable Risk verdict does not create an Execution Intent,
  broker mutation or Position.
* No direct database change, manual HTTP bridge, hidden endpoint or direct broker
  call is used.

## Out of Scope

* New Risk rules, thresholds, sizing algorithms or Risk authority.
* A second manual execution pipeline or direct broker endpoint.
* Bypassing or weakening fail-closed Risk behavior.
* LIVE execution or provider-specific changes.
* Opportunity journey redesign or Opportunity-to-Workspace convergence.
* AI Engine, News Service, economic calendar, passive scanning or monitoring
  agent.
* Partial close, stop modification, take-profit automation or advanced analytics.
* SaaS, distributed events or additional brokers.
* Removing the standalone manual route while it remains a valid fallback.

## Acceptance Criteria

* [ ] A reviewed Decision Workspace lets an authenticated user select a PAPER
      account and an eligible market without copying identifiers.
* [ ] The user can inspect the selected market's existing ticker, OHLC, order
      book, recent trades, market state and freshness states.
* [ ] The normal UI creates a persisted TradePlan whose origin is `MANUAL`, with
      the selected account and market and no synthetic Opportunity.
* [ ] The user can accept the MANUAL plan through the existing TradePlan
      lifecycle.
* [ ] The existing deterministic Risk pipeline produces an explicit approved,
      rejected or unavailable verdict for the MANUAL plan.
* [ ] Account ownership, planning context, market facts and Risk facts remain
      server-authoritative; no client-provided fact overrides them.
* [ ] A rejected or unavailable MANUAL Risk result creates no Execution Intent,
      broker mutation or Position.
* [ ] Only an approved Risk result exposes and permits the existing explicit
      human execution authorization.
* [ ] An approved MANUAL plan can use the existing Execution Intent and PAPER
      settlement path without an Opportunity-specific branch or second pipeline.
* [ ] A filled MANUAL PAPER execution creates a persisted position visible in the
      normal Positions page.
* [ ] The position remains visible after page reload and displays the existing
      valuation/freshness state.
* [ ] The user can close the full PAPER exposure through the normal UI, and the
      closed/empty state remains after reload.
* [ ] Execution history retains continuity to the execution and MANUAL TradePlan
      where the existing contracts expose that relationship.
* [ ] The journey uses no direct database mutation, manual backend HTTP call,
      fabricated ID, hidden development endpoint, Risk bypass or direct broker
      call.
* [ ] Focused affected tests pass, Angular build/check passes where frontend
      changes are made, and `git diff --check` passes.
* [ ] Runtime evidence records the exact successful path and any valid negative
      Risk evidence.

## Constraints

* ADR-001 remains authoritative for human authority and non-autonomous trading.
* ADR-014 remains authoritative for the decision pipeline.
* ADR-028 remains authoritative for deterministic, explainable and fail-closed
  Risk decisions.
* ADR-029 remains authoritative for Execution Intent and execution boundaries.
* ADR-043 remains authoritative for PAPER position authority and account/broker
  identity.
* ADR-044 remains authoritative for authenticated actor propagation and
  ownership.
* ADR-047 remains authoritative for MANUAL TradePlan provenance and the shared
  lifecycle.
* Existing uncommitted worktree changes must be preserved and reviewed rather
  than discarded or reformatted.
* No risk threshold may be weakened to manufacture an approved scenario.
* A temporary market-data failure must be reported as a runtime blocker, not
  hidden or converted into a successful execution claim.

## Architectural Decision

`NEW_ADR_REQUIRED = NO`.

This Story validates and, if necessary, minimally repairs existing contracts. A
new ADR is required only if evidence shows that MANUAL plans need a different
domain owner, Risk authority, account identity model or execution pipeline.

## Relevant ADRs

* `ADR-001` - Trading OS Vision and Human Authority
* `ADR-014` - Trading Decision Pipeline
* `ADR-028` - Deterministic Risk Domain
* `ADR-029` - Execution Domain Architecture
* `ADR-031` - Trade Planning and Risk Context Responsibilities
* `ADR-043` - Account Identity and PAPER Position Authority
* `ADR-044` - Inter-Service Trust and Actor Propagation
* `ADR-047` - Manual Trade Plans as a First-Class Trade Plan Origin

## Related Stories

* `0048` - Validate the PAPER Trading Journey through the Web Application
* `0055` - Make PAPER Trade Plan Sizing Compatible with Effective Risk
* `0056` - Account-First Market Decision Context
* `0057` - Account-Scoped Market Workspace
* `0058` - Create Manual-Origin Trade Plans
* `0059` - Evaluate Manual Trade Plans through Deterministic Risk
* `0060` - Market-Integrated MANUAL Trade Ticket

## Relevant Modules

* `trading-os-web`
* `trading-core`
* `market-intelligence`
* `gateway` only if an existing public route is proven to block the journey
* `risk-domain` only for regression coverage; no rule changes are expected

## Validation

* Focused manual TradePlan creation, lifecycle and risk-handoff tests.
* Focused Trading Core Risk and Execution tests covering MANUAL provenance and
  shared contracts.
* Angular Decision Workspace, manual ticket and PlanPage tests when affected.
* Angular production build/check when frontend changes are made.
* Official authenticated runtime journey against a PAPER account.
* `git diff --check`.

Runtime validation must stop before any LIVE action. The accepted success target
requires an approved MANUAL Risk result, explicit human execution, PAPER fill,
reload, full close, reload and history continuity. A rejected or unavailable
Risk result is valid negative evidence but does not satisfy the success path.

## Definition of Done

* [ ] Story scope approved by the human engineer.
* [ ] Existing Story 0060 worktree changes reviewed and accepted or explicitly
      adjusted without discarding unrelated work.
* [ ] No second manual Risk or execution pipeline introduced.
* [ ] Acceptance criteria validated with repository and runtime evidence.
* [ ] Runtime report records success, negative evidence and environmental limits.
* [ ] Human code review completed.
* [ ] Human commit created.
