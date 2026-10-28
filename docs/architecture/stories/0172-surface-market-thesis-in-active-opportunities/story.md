# Story 0172 - Surface Market Thesis Decisions in Active Opportunities

## Metadata

**ID:** `0172`
**Title:** Surface Market Thesis Decisions in Active Opportunities
**Status:** Review

## Goal

Make the authoritative Market Thesis decision visible wherever the trader
reviews active Opportunities, including its reasons and evidence, without
turning an Opportunity score into trade authorization.

## Context

ADR-076 establishes Market Thesis as a deterministic, market-scoped and
versioned analytical authority. The existing Opportunity detail response can
project the referenced Thesis, while the active Opportunity list currently
returns only the Thesis reference. This prevents the trader from seeing the
same decision hierarchy consistently across the active Opportunity list and
detail view.

The frontend already distinguishes `NO_TRADE`, `WATCH`, and `CANDIDATE`, and
TradePlan preparation is restricted to a current `CANDIDATE` Thesis. This Story
surfaces those existing contracts; it does not create a new decision authority.

## Problem

The active Opportunity list displays a misleading `Not evaluated` state because
the `/api/v1/opportunities/active` projection omits the expanded Market Thesis.
The detail presentation also needs to expose the decision reasons and the
supporting, contradicting, and missing evidence categories clearly before the
trader considers TradePlan preparation.

## Scope

- Include the referenced, versioned Market Thesis in the active Opportunity
  API projection when it is available.
- Present `NO TRADE`, `WATCH`, and `CANDIDATE` as the primary decision in the
  active Opportunity list and detail view.
- Present Thesis reasons and supporting, contradicting, and missing evidence
  without recomputing or reinterpreting them in Angular.
- Label Opportunity score as signal strength and distinguish it from trade
  authorization.
- Preserve the existing server-side and frontend gate that permits TradePlan
  preparation only for a current `CANDIDATE` Thesis.
- Preserve truthful unavailable behavior for legacy or incomplete Thesis
  payloads.
- Add focused backend and frontend tests for the active-list projection,
  decision rendering, evidence rendering, legacy payloads, and TradePlan gate.
- Keep the decision presentation usable at narrow mobile widths.

## Out of Scope

- Creating or changing Market Thesis composition, persistence, versioning, or
  deterministic state semantics.
- Changing Opportunity scoring, ranking, validity, or strategy matching.
- Creating a durable user-level `NO TRADE` decision or execution command.
- Changing Risk, TradePlan domain validation, broker behavior, or execution.
- Adding a new API endpoint, database schema, gateway route, or provider
  integration.
- Rerunning scans, placing orders, or changing rehearsal/runtime data.

## Acceptance Criteria

- [ ] `GET /api/v1/opportunities/active` includes the expanded Market Thesis
      for Opportunities with a resolvable Thesis reference.
- [ ] The active Opportunity list displays the authoritative Thesis state as
      the primary decision and does not show `Not evaluated` when the Thesis is
      available.
- [ ] The detail view displays the Thesis state, all available reasons, and
      supporting, contradicting, and missing evidence categories.
- [ ] Signal strength is labelled separately from decision state and is not
      presented as trade authorization.
- [ ] TradePlan preparation remains available only for a current `CANDIDATE`
      Thesis and remains routed through the existing TradePlan service path.
- [ ] Missing or partial legacy Thesis fields render safely and truthfully.
- [ ] Narrow mobile layouts keep the decision, evidence, and actions usable.
- [ ] Focused backend and frontend tests pass, along with the Angular
      production build and `git diff --check`.
- [ ] No unrelated behavior or pre-existing worktree changes are modified.

## Constraints

- Treat the persisted Market Thesis as authoritative for its state and
  evidence.
- Keep `NO_TRADE`, `WATCH`, and `CANDIDATE` analytical states distinct from
  Risk approval, TradePlan validation, and execution authority.
- Do not derive new confidence, probability, ranking, or risk semantics in the
  frontend.
- Preserve authenticated API boundaries and broker-neutral service ownership.
- Keep Angular standalone, typed, and compatible with the existing reactive
  conventions.
- Do not commit, push, merge, or discard unrelated work automatically.

## Relevant ADRs

- `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md`
- `docs/architecture/adr/ADR-034.md`

## Relevant Stories

- `0159` - Surface Evidence-Led Opportunity Decisions in the Trader Cockpit.
- `0161` - Trader Cockpit TradePlan Decision Continuity.

## Relevant Modules

- `market-intelligence`
- `trading-os-web/src/app/features/opportunities`
- `trading-os-web/src/app/features/decision-workspace`
- `trading-os-web/src/app/core/models/market-thesis.model.ts`
- `trading-os-web/src/app/core/models/opportunity.model.ts`

## Validation

- Targeted `OpportunityController` Maven tests.
- Full Angular test suite.
- Angular production build.
- `git diff --check`.
- Review of the active-list API payload and detail rendering with `NO_TRADE`,
  `WATCH`, and `CANDIDATE` Thesis states.
- Narrow mobile layout validation where the local runtime permits.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed.
- [ ] Relevant validation executed.
- [ ] Diff reviewed in IntelliJ.
- [ ] Code Review approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.
