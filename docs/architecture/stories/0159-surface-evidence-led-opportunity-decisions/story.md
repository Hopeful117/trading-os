# Story 0159 - Surface Evidence-Led Opportunity Decisions in the Trader Cockpit

## Metadata

**ID:** `0159`
**Title:** Surface Evidence-Led Opportunity Decisions in the Trader Cockpit
**Status:** Review

## Goal

Help the trader move from an active Opportunity to an explicit, evidence-led
human decision inside the Trader Cockpit without bypassing Risk, Trade Plan
validation, or execution boundaries.

## Context

Stories 0157 and 0158 established the account-scoped Trader Cockpit and brought
the existing Market Navigator into that workspace. The repository already
provides Opportunity facts and Trend Context evidence, including explanation,
setup triggers, validity, provenance, contradictions, invalidations, and
freshness-related diagnostics.

The Cockpit currently presents only a compact Opportunity summary and offers
the Trade Plan preparation action. The next bounded product step is to make the
existing evidence inspectable before that action and to let the human record a
presentation-level `NO TRADE` outcome without creating a second decision or
execution authority.

## Trader Question

> Is this Opportunity still valid, what could invalidate it, and should I
> prepare a Trade Plan or stand aside?

## Scope

- Recompose the active Opportunity area in `DecisionWorkspace` around the
  existing evidence in three readable stages: `UNDERSTAND`, `CHALLENGE`, and
  `DECIDE`.
- Present the existing Opportunity facts without recomputing score, confidence,
  ranking, validity, or strategic suitability in Angular.
- Present available Trend Context support, contradictions, invalidations,
  operational status, and freshness/diagnostic information using the existing
  read model and truthful unavailable states.
- Make Opportunity freshness/validity and setup triggers visible before an
  action is selected.
- Provide an explicit `NO TRADE` presentation state for the current Cockpit
  investigation. This state is local to the current workspace/session and is
  not a new durable domain decision or execution command.
- Preserve `PREPARE TRADE` as the existing human-validation boundary and route
  it through `TradePlanService.createFromOpportunity()` and the canonical
  PlanPage workflow.
- Keep the selected account, market, Opportunity, Trend Context, and live
  stream ownership in `DecisionWorkspace`.
- Add focused frontend tests for evidence rendering, unavailable evidence,
  stale/invalid Opportunity presentation, `NO TRADE` state, and Trade Plan
  navigation.

## Out of Scope

- New Opportunity, Observation, Trend Context, or decision persistence.
- A durable Opportunity-level decision aggregate or new decision endpoint.
- Changing Opportunity scoring, ranking, confidence, validity, or invalidation
  semantics.
- New backend endpoints, DTOs, database schema, gateway routes, or API
  ownership rules.
- Risk approval, position sizing, broker mutation, order execution, or automatic
  rejection.
- Projected-loss calculation or new account/risk projections.
- AI interpretation, recommendation, or autonomous decision logic.
- Replacing the existing Trade Plan or Risk workflows.
- Changes to Market Navigator orchestration or market-data stream ownership.

## Acceptance Criteria

- [ ] With an active Opportunity, the Cockpit presents a readable
      `UNDERSTAND → CHALLENGE → DECIDE` flow using existing typed contracts.
- [ ] The Opportunity section exposes its existing direction, scenario,
      timeframe, explanation, setup triggers, evaluated/validity timestamps,
      and provenance where available without inventing derived semantics.
- [ ] The Challenge section exposes available Trend Context status, supporting
      evidence, contradictions, invalidations, diagnostics, and freshness; it
      distinguishes unavailable data from a negative trading conclusion.
- [ ] Stale, expired, invalid, unavailable, and loading states are explicit and
      do not appear as an approved trade recommendation.
- [ ] The trader can select `NO TRADE` for the current investigation and the
      Cockpit clearly shows that state without persisting a new domain decision
      or calling execution services.
- [ ] The trader can select `PREPARE TRADE` only through the existing
      `TradePlanService.createFromOpportunity()` path and is taken to the
      canonical PlanPage workflow.
- [ ] No Risk, Trade Plan, broker, execution, market-data, or backend authority
      is duplicated in the new presentation flow.
- [ ] Clearing or changing the active market or Opportunity clears the prior
      presentation decision and cannot display it for another context.
- [ ] Focused frontend tests cover loaded evidence, loading/unavailable
      evidence, stale/invalid Opportunity states, `NO TRADE`, `PREPARE TRADE`,
      and context changes.
- [ ] The Angular test suite, production build, formatting, diff checks, and
      authenticated desktop/mobile runtime validation pass where available.
- [ ] No backend, schema, persistence, gateway, Risk, execution, or unrelated
      frontend files change.

## Constraints

- Keep `DecisionWorkspace` as the context and navigation coordinator.
- Treat Opportunity and Trend Context API contracts as authoritative.
- Keep presentation state distinct from domain decisions and execution state.
- Do not translate unavailable, failed, or stale evidence into rejection.
- Do not calculate new financial, risk, confidence, ranking, or validity facts
  in the frontend.
- Preserve Angular standalone, Observable, async-pipe, and existing workstation
  component conventions.
- Do not introduce new dependencies, global state, backend contracts, or an ADR
  without evidence.
- Do not commit, push, merge, or discard unrelated work automatically.

## Relevant ADRs

- `docs/architecture/adr/ADR-001.md`
- `docs/architecture/adr/ADR-014.md`
- `docs/architecture/adr/ADR-031.md`
- `docs/architecture/adr/ADR-034.md`
- `docs/architecture/adr/ADR-048.md`

## Relevant Stories

- `0137` - Finalize the Professional Decision Workspace.
- `0156` - Persist Human Trade Plan Decision Provenance.
- `0157` - Establish the Trader Cockpit Foundation.
- `0158` - Integrate the Market Navigator into the Trader Cockpit.

## Relevant Modules

- `trading-os-web/src/app/features/decision-workspace`
- `trading-os-web/src/app/core/models/opportunity.model.ts`
- `trading-os-web/src/app/core/models/trend-context.model.ts`
- `trading-os-web/src/app/core/services/opportunity.service.ts`
- `trading-os-web/src/app/core/services/trend-context.service.ts`
- `trading-os-web/src/app/core/services/trade-plan.service.ts`

## Validation

- Focused `DecisionWorkspace` component and service tests.
- Full Angular test suite.
- Angular production build.
- Prettier and `git diff --check`.
- Authenticated browser validation at desktop and narrow mobile widths where the
  local environment permits.
- Verify that `PREPARE TRADE` uses the existing Trade Plan request and that
  `NO TRADE` performs no backend mutation.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [x] Implementation completed.
- [x] Documentation reconciliation completed.
- [x] Relevant validation executed.
- [x] Independent Code Review approved.
- [ ] Human commit and integration completed.
