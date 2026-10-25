# Story 0161 - Trader Cockpit to TradePlan Decision Continuity

## Metadata

**ID:** `0161`
**Title:** Trader Cockpit to TradePlan Decision Continuity
**Status:** IMPLEMENTATION PLAN COMPLETE - AWAITING PLAN APPROVAL

## Goal

Make the transition between the Trader Cockpit investigation and the exact
TradePlan decision workflow feel like one continuous, human-controlled decision
process without merging bounded surfaces or introducing a global frontend store.

## Problem

The Cockpit can create an Opportunity-derived TradePlan, but the current
PlanPage does not expose the plan origin or provide a context-preserving return
to the exact investigation. Navigation currently carries only a market query
parameter. A direct PlanPage load therefore loses the Opportunity context and
may fall back to instrument matching, which is not a reliable identity.

## Scope

- Expose the existing persisted TradePlan origin lineage through a bounded,
  owner-validated read contract.
- Preserve exact Opportunity identity/version, account identity, and market
  identity when those relationships already exist.
- Present a compact PlanPage origin/context header for Opportunity-derived plans.
- Provide an explicit `Back to investigation` navigation to the existing
  DecisionWorkspace using canonical identifiers only.
- Reconstruct fresh account, market, Opportunity, and evidence reads when the
  Cockpit is reopened.
- Distinguish Opportunity-derived and manual TradePlans truthfully.
- Preserve the current projection, acceptance, rejection, Risk, and execution
  boundaries from Story 0160.
- Represent expired, unavailable, deleted, or inaccessible origin context
  truthfully without breaking PlanPage use.
- Preserve TradePlan decision provenance where the existing backend facts expose
  it.

## Out of Scope

- Merging Cockpit, TradePlan, projection, Risk, or execution into one page.
- A global frontend store, NgRx adoption, or browser-history-based continuity.
- New Opportunity, Observation, or evidence persistence.
- Durable Opportunity-level decisions or changing Cockpit-local `NO TRADE`.
- Scanner ranking, market navigation, Opportunity semantics, or Trend Context
  calculations.
- Risk rules, projection calculations, approval semantics, execution, broker,
  Spotware, position monitoring, or AI changes.
- A new cross-domain aggregate or financial authority.
- Generic documentation cleanup for the status inconsistencies of prior Stories.

## Acceptance Criteria

- [ ] An Opportunity-derived TradePlan exposes truthful origin type, exact
      Opportunity identity/version where persisted, account identity, and market
      identity where authoritative.
- [ ] PlanPage presents a compact origin/context header without duplicating the
      Cockpit evidence surface.
- [ ] PlanPage provides an explicit return to the existing DecisionWorkspace for
      Opportunity-derived plans using canonical IDs, not serialized read models.
- [ ] Manual TradePlans remain fully usable and do not display a fabricated
      Opportunity return action.
- [ ] Direct PlanPage loading reconstructs origin context from authenticated
      server data rather than Angular navigation state.
- [ ] `Cockpit -> TradePlan -> refresh -> Back to investigation` restores the
      same account and market and reloads current Opportunity/evidence data.
- [ ] Expired or unavailable Opportunities are not presented as actionable, but
      the TradePlan origin remains understandable where authorized data exists.
- [ ] Current evidence after return is distinguished from the original plan
      origin; no historical snapshot is invented.
- [ ] ACCEPT and REJECT remain TradePlan decisions and are not rewritten as
      Cockpit `NO TRADE` decisions.
- [ ] Existing TradePlan version identity and Story 0160 projection behavior are
      preserved.
- [ ] Owner and account checks prevent cross-user or cross-account context
      restoration through the new or extended read contract.
- [ ] Existing duplicate-prevention semantics remain unchanged and are covered
      by regression tests.
- [ ] Focused backend/frontend tests, affected module suites, Angular build,
      formatting, diff checks, and authenticated desktop/narrow runtime flows
      pass where available.

## Constraints

- `DecisionWorkspace` remains the Cockpit context coordinator.
- TradePlan remains authoritative for exact plan facts and human decisions.
- Risk remains authoritative for deterministic validation.
- Projection remains read-only and backend-owned.
- Execution remains explicit and human-authorized.
- URLs carry identity only; current read models are fetched after navigation.
- Use Observables and existing Angular reactive conventions.
- No new ADR is expected unless implementation requires a new authority,
  persistent cross-domain aggregate, or changed lifecycle semantics.

## Relevant ADRs

- `docs/architecture/adr/ADR-001.md`
- `docs/architecture/adr/ADR-014.md`
- `docs/architecture/adr/ADR-019.md`
- `docs/architecture/adr/ADR-028.md`
- `docs/architecture/adr/ADR-031.md`
- `docs/architecture/adr/ADR-034.md`
- `docs/architecture/adr/ADR-047.md`
- `docs/architecture/adr/ADR-048.md`

## Relevant Stories

- `0157` - Establish the Trader Cockpit Foundation.
- `0158` - Integrate the Market Navigator into the Trader Cockpit.
- `0159` - Surface Evidence-Led Opportunity Decisions in the Trader Cockpit.
- `0160` - Deterministic Trade Scenario Projection.

## Relevant Modules

- `trading-os-web/src/app/features/decision-workspace`
- `trading-os-web/src/app/features/trade-planning/plan-page`
- `trading-os-web/src/app/core/models/trade-plan.model.ts`
- `trading-os-web/src/app/core/services/trade-plan.service.ts`
- `trading-os-web/src/app/core/services/opportunity.service.ts`
- `trading-core/src/main/java/com/hope/trading/trading_core/tradeplanning`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/tradeplan`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/opportunity`

## Validation

- Focused PlanPage, DecisionWorkspace, service, route, and contract tests.
- Affected Market Intelligence and Trading Core module tests when contracts
  change.
- Full Angular test suite, production build, Prettier, and `git diff --check`.
- Authenticated desktop runtime flow through prepare, refresh, return, ACCEPT,
  REJECT, expiry, unavailable origin, and manual-plan cases.
- Narrow/mobile layout validation without introducing duplicated streams.
- Ownership and cross-account/cross-user negative tests.
