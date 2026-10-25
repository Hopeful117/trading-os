# Implementation Plan - Story 0161

## Plan Status

Prepared after explicit approval of the corrected Story 0161 Repository
Analysis. Implementation requires explicit human approval of this plan.

## Overview

Extend the existing authenticated TradePlan read contract with a small,
server-derived origin context, carry it through Trading Core, and render it on
PlanPage as a compact orientation header with an explicit return to the existing
DecisionWorkspace.

The context will be derived from the exact persisted TradePlan version and its
`OpportunityPlanReference`, not from browser state or a generic Opportunity
lookup. DecisionWorkspace already knows how to reconstruct fresh account,
market, Opportunity, Trend Context, and stream state from canonical identifiers;
this Story will reuse that coordinator rather than introduce a global store or
duplicate evidence.

The implementation remains read-only with respect to continuity. Existing
TradePlan decisions, Story 0160 projection, Risk validation, and execution
boundaries remain unchanged.

## Planned Changes

### 1. Define the bounded origin read model

Affected component: Market Intelligence TradePlan application/web boundary.

- Add a typed origin context representation for an exact TradePlan version.
- Include origin type (`OPPORTUNITY` or `MANUAL`), account id, market id when
  authoritative, exact Opportunity id/version, current Opportunity status and
  validity information, and optional scan provenance already stored on the
  Opportunity.
- Represent unavailable, expired, ambiguous, or inconsistent origin context
  explicitly instead of fabricating a return target.
- Resolve only the exact persisted Opportunity reference from the TradePlan
  rationale. For a single Opportunity origin, expose a return target; for
  manual or multiple/ambiguous origins, do not fabricate one.
- Verify that the referenced Opportunity account matches the TradePlan
  planning context account before exposing context.

Constraint: this is a read projection only. It must not mutate Opportunity,
TradePlan, Risk, Challenge, or execution state.

### 2. Extend the owner-scoped TradePlan contract

Affected components:

- Market Intelligence `TradePlanResponse` and internal decision controller.
- Trading Core `MarketIntelligenceTradePlanningClient.PlanTransport`.
- Trading Core public TradePlan decision controller/orchestration mapping.

- Add the origin context as additive response fields so existing consumers
  remain compatible.
- Build the response through the existing `loadForActor` path, which already
  validates the TradePlanningContext owner.
- Keep actor/account ownership checks server-side; never accept owner or origin
  identity from frontend query parameters as authorization input.
- Preserve existing response fields, exact `planId/version`, decision status,
  and Story 0160 projection compatibility.
- Carry existing `decisionActorId` and `decisionAt` facts through the internal
  and public read response where already available, without creating a decision
  history model.

Classification: additive API exposure plus bounded read projection. No route
rename or breaking version change is planned.

### 3. Add PlanPage context presentation and navigation

Affected components:

- `trading-os-web/src/app/core/models/trade-plan.model.ts`.
- `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.ts`.
- `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.html`.
- PlanPage stylesheet only if the existing layout needs a small context block.

- Add typed origin context and decision provenance to the frontend model.
- Render a compact header for Opportunity-derived plans showing the authoritative
  instrument/market, direction, account identity, origin status, and whether the
  current Opportunity remains actionable.
- Add `Back to investigation` only when a valid canonical return context exists.
- Navigate to the existing `/decision-workspace` route with `accountId`,
  `marketId`, and `opportunityId`/version identity only.
- Render truthful unavailable/expired origin states without blocking the
  TradePlan or projection workflow.
- Render manual plans as `MANUAL` with no fabricated Opportunity action.
- Display decision actor/time on accepted/rejected states when available.
- Keep `CURRENT`, `STOP`, `TARGET`, Risk/Reward, and all financial values
  backend-owned and unchanged.

The PlanPage must remain coherent on direct load and refresh because all origin
facts come from the exact server response for the route's plan/version.

### 4. Preserve Cockpit reconstruction semantics

Affected component: existing `DecisionWorkspace` route/query coordination.

- Reuse the current query-param restoration behavior rather than adding a
  navigation singleton or global store.
- Confirm that account changes clear prior market/Opportunity state and that the
  restored Opportunity is loaded fresh.
- Preserve existing actionability rules: expired/non-actionable Opportunities
  may be shown for context but cannot be prepared again.
- Do not alter scanner ranking, Trend Context calculation, market stream
  ownership, or Cockpit-local `NO TRADE` semantics unless a regression test
  demonstrates a continuity-specific defect.

Expected code impact is test coverage and only minimal route-coordination change
if validation reveals an existing restoration defect.

### 5. Preserve duplicate and decision semantics

Affected components: existing TradePlan generation/decision tests.

- Keep Opportunity + actor + account reuse behavior unchanged.
- Verify a returned/reloaded Opportunity does not cause a second plan request by
  navigation alone.
- Verify ACCEPT and REJECT remain TradePlan versioned decisions and are not
  translated into Cockpit `NO TRADE`.
- Verify manual TradePlans do not acquire an Opportunity return context.

### 6. Reconcile documentation

- Update the Story implementation report after implementation with the actual
  files, validation, ownership result, and runtime evidence.
- Update API/frontend documentation only if the repository contains canonical
  documentation for the TradePlan response contract that becomes inaccurate.
- Do not rewrite historical status fields of Stories 0157-0159 as part of this
  Story.
- Record that no database migration or persistence change was required.

## Files to Modify

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/TradePlanResponse.java`
  - Add the typed origin context and existing decision provenance to the
    serialized read response.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/InternalTradePlanDecisionController.java`
  - Resolve the origin context through the already owner-scoped TradePlan load.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/tradeplan/TradePlanDecisionService.java`
  - Reuse or expose the exact loaded plan/context boundary needed by the read
    projection without changing decision lifecycle semantics.
- `trading-core/src/main/java/com/hope/trading/trading_core/tradeplanning/infrastructure/MarketIntelligenceTradePlanningClient.java`
  - Extend `PlanTransport` and compatibility constructors for the additive
    origin/decision fields.
- `trading-os-web/src/app/core/models/trade-plan.model.ts`
  - Add typed origin context and decision provenance models.
- `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.ts`
  - Derive return-link state from the server response and preserve it across
    proposal/accepted/rejected/Risk views.
- `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.html`
  - Render origin, account/market orientation, current origin status, decision
    provenance, and the explicit return link.
- `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.scss`
  - Add only bounded responsive styling if required by the context header.
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.spec.ts`
  - Add or update restoration and actionability regression tests.
- `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.spec.ts`
  - Add direct-load, origin, return-link, manual, expiry, decision, and
    unavailable-context tests.
- Relevant Market Intelligence and Trading Core controller/service tests
  - Cover owner-scoped origin projection and additive contract mapping.

## Files to Create

- A bounded Market Intelligence origin-context projection/service class under
  `application/tradeplan` may be created if keeping resolution out of the web
  controller is necessary. It must remain a read-side adapter over existing
  repositories and market identity services.
- A dedicated origin-context test fixture or contract test may be created where
  existing fixtures cannot express exact Opportunity version, account mismatch,
  or unavailable origin states.

No database migration, aggregate, global frontend store, or external dependency
is planned.

## Dependencies

- Existing persisted `TradePlan` rationale and `TradePlanningContext`.
- Existing `TradingOpportunityRepository` and `MarketDataClient` provider-neutral
  market identity lookup.
- Existing owner-scoped `TradePlanDecisionService.loadForActor` boundary.
- Existing Trading Core Feign contract and authenticated Gateway path.
- Existing Angular Router, `ActivatedRoute`, Observable, and AsyncPipe patterns.

No new external library or infrastructure dependency is required.

Ordering:

1. Implement and test the Market Intelligence origin projection.
2. Extend the internal/public TradePlan response and Trading Core transport.
3. Update frontend types and PlanPage presentation/navigation.
4. Add/adjust Cockpit restoration and regression tests.
5. Reconcile documentation and run the full validation sequence.

## Test Plan

### Market Intelligence

- Exact Opportunity reference resolves id/version, market, account, and current
  status.
- Manual TradePlan returns `MANUAL` with no origin action.
- Expired Opportunity remains visible as non-actionable context.
- Missing Opportunity, missing market, multiple origins, and account mismatch
  return truthful unavailable context without failing the plan read.
- A cross-user TradePlan read remains rejected by `loadForActor`.
- Existing ACCEPT/REJECT response mapping retains decision actor/time.

### Trading Core

- Additive `PlanTransport` mapping preserves all existing fields and carries the
  origin context without changing endpoint paths.
- Authenticated actor remains the source of owner authorization.
- Cross-account origin context cannot be returned for a valid plan id.
- Existing TradePlan decision and Risk handoff behavior is unchanged.

### Frontend

- Opportunity-derived PlanPage shows origin, account, market, and return link.
- Return link contains canonical IDs only and targets DecisionWorkspace.
- Manual PlanPage has no fabricated Opportunity return action.
- Direct PlanPage load and refresh use server-derived origin context.
- Expired and unavailable origin states are truthful and do not block plan use.
- ACCEPT and REJECT show persisted decision provenance when present.
- Existing projection, Risk, execution, and no-mutation behavior remains intact.
- DecisionWorkspace restores account/market/Opportunity from query identity and
  reloads current evidence.
- Account mismatch, cross-user context, and rapid context changes are rejected
  or cancelled safely.

### Validation Commands

Targeted first:

```text
cd market-intelligence && ./mvnw -Dtest=<origin and trade-plan contract tests> test
cd trading-core && ./mvnw -Dtest=<trade-plan contract and ownership tests> test
cd trading-os-web && npm run test:ci -- --include=<affected tests>
cd trading-os-web && npm run build
cd trading-os-web && npx prettier --check .
git diff --check
```

Then run the full affected Maven suites and the full Angular suite. If the local
environment is available, validate authenticated desktop and narrow/mobile
flows through Gateway, including direct PlanPage load, refresh, return, expiry,
manual plan, ACCEPT, and REJECT.

Expected success: all applicable tests and builds pass, no new blocking budget
or formatting errors are introduced, and no execution/Risk mutation occurs from
continuity navigation.

## Risks

- **Origin projection accidentally becomes a new authority:** keep it derived
  from persisted TradePlan/Opportunity facts and forbid commands or mutations.
- **Cross-user Opportunity leakage:** derive context only after
  `loadForActor` ownership validation and verify account consistency; do not use
  the generic public Opportunity endpoint as an authorization source.
- **Ambiguous market identity:** use persisted Opportunity `marketId`; return
  unavailable rather than matching instrument heuristically.
- **Stale current evidence:** label current Opportunity state separately from
  original plan origin and let DecisionWorkspace reload fresh evidence.
- **Regression of Story 0160:** keep projection loading and backend values
  untouched; add regression assertions for `CURRENT`, `STOP`, `TARGET`, and
  provenance.
- **Duplicate plan creation on return:** preserve the current guarded reuse path
  and ensure navigation does not call preparation automatically.
- **Multiple Opportunity references:** expose no fabricated single return route;
  render an explicit ambiguous/unavailable origin state.
- **Response compatibility:** make new JSON fields additive and preserve existing
  Feign compatibility constructors/tests.

No unresolved architecture conflict remains. An ADR is not required unless the
implementation reveals a need for a new authority or persistent cross-domain
aggregate.

## Validation Checklist

- [ ] Story 0161 scope remains limited to continuity and orientation.
- [ ] Exact TradePlan version is the source of origin context.
- [ ] Origin context is resolved through an owner-scoped server path.
- [ ] Account and Opportunity account match are verified.
- [ ] Opportunity-derived, manual, expired, unavailable, and ambiguous states
      are represented truthfully.
- [ ] Market identity comes from authoritative `marketId`, not instrument
      matching.
- [ ] PlanPage renders origin/account/market context and explicit return action.
- [ ] Return URL contains identities only.
- [ ] DecisionWorkspace performs fresh reconstruction and owns streams.
- [ ] Story 0160 projection remains unchanged and read-only.
- [ ] ACCEPT, REJECT, Risk, and execution semantics remain unchanged.
- [ ] Duplicate-prevention behavior remains guarded.
- [ ] Cross-user and cross-account tests pass.
- [ ] Focused backend/frontend tests pass.
- [ ] Full affected Maven suites pass.
- [ ] `npm run test:ci` passes.
- [ ] Angular production build passes.
- [ ] Prettier and `git diff --check` pass.
- [ ] Authenticated desktop and narrow/mobile runtime flows pass where available.
- [ ] No migration, global store, AI, Spotware, or position-monitoring work is
      introduced.
- [ ] Implementation Report records documentation and vault outcomes.

## Recommendation

Ready for implementation

This is a technical recommendation only. It does not authorize implementation.

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
