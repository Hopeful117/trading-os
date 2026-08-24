# Implementation Plan — Story 0023

## Backend — Market Intelligence (new seams)

1. `application/tradeplan/OpportunityTradePlanGenerationService`:
   opportunity ACTIVE check (id; optional expected version), fresh price via
   `MarketDataClient` + `PlanningPriceSelector` + max-age validation (same
   semantics as analysis path), context snapshot save, delegate to
   `TradePlanApplicationService.create`. Errors as typed failures
   (OPPORTUNITY_NOT_ELIGIBLE, MARKET_PRICE_UNAVAILABLE, …).
2. `adapter/web/InternalOpportunityTradePlanController`:
   `POST /internal/v1/intelligence/opportunities/{opportunityId}/trade-plans`
   (`Idempotency-Key` header, body = actor/account/context payload identical
   in shape to the analysis request's context block).
3. `application/tradeplan/TradePlanDecisionService`: decide(planId, version,
   actorId, decision) implementing stale-version / ownership /
   idempotent-repeat / illegal-transition semantics over
   `TradePlanApplicationService.transition`.
4. `adapter/web/InternalTradePlanDecisionController`:
   `POST /internal/v1/trade-plans/{planId}/versions/{version}/decisions`.
5. Owner-checked internal read: `GET /internal/v1/trade-plans/{planId}/versions/{version}`
   returning `TradePlanResponse` (403 when context ownerId ≠ actor).
6. Tests: generation happy/422/503; decisions accept/reject/repeat/conflict/
   stale/wrong-owner.

## Backend — Trading Core (public boundary)

7. Extend Feign client with the three MI internal calls.
8. `tradeplanning/api/OpportunityTradePlanController`:
   `POST /api/v1/trade-plans/opportunities/{opportunityId}/trade-plans`
   (JWT principal → actorId, Idempotency-Key, `{accountId}`) → orchestration
   service: account ownership, profile currency check, context build, MI call.
9. `tradeplanning/api/TradePlanDecisionController`:
   `POST /api/v1/trade-plans/{planId}/versions/{version}/decisions`
   (`{decision}`; actor from JWT).
10. `tradeplanning/api/TradePlanQueryController`:
    `GET /api/v1/trade-plans/{planId}/versions/{version}` (JWT actor passed
    for owner check).
11. Typed response records mirroring the fields the UI needs (no raw maps).
12. Tests: orchestration mapping/ownership/profile-currency errors; decision
    passthrough incl. actor substitution; read passthrough.

Gateway: unchanged (route exists). risk-domain: untouched.

## Frontend

13. `core/models/trade-plan.model.ts`, `core/services/trade-plan.service.ts`
    (create/getLatestVersioned/decide), extend risk evaluation call through a
    small `core/services/trade-plan-risk.service.ts` (existing endpoint).
14. Routes (authGuard): `trade-planning/new/:opportunityId`,
    `trade-planning/plans/:planId/versions/:version`.
15. `features/trade-planning/prepare-plan-page/`: opportunity load (must be
    ACTIVE), account selector (AccountService), single-flight create,
    navigate to plan page on success.
16. `features/trade-planning/plan-page/`: discriminated state machine
    proposal → deciding → accepted → evaluatingRisk → riskDecision
    (+ rejected, error variants); proposal facts incl. sizing/risk-reward/
    expiration/rationale/provenance; explicit Accept/Reject buttons;
    separate "Evaluate risk" action post-accept; verbatim decision rendering
    with warnings/violations; honest missing values; no execution CTA ever.
17. Opportunity detail: "Prepare trade plan" CTA when ACTIVE.
18. Tests per mission §60–61 including machine transitions, double-trigger,
    decision nuances and stale/error paths.

## Validation

Prettier; `npm run test:ci`; `npm run build`; Maven test for market-intelligence
and trading-core; `git diff --check`. Bundle delta documented.
