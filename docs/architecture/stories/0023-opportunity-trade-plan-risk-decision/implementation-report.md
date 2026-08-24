# Implementation Report — Story 0023

## Summary

Implemented the end-to-end "Opportunity → Trade Plan → Risk Decision" frontend and backend integration. A trader can now create a Trade Plan from an ACTIVE opportunity, accept or reject it, and run deterministic risk evaluation — all through the Angular frontend.

### Docker Infrastructure Fix

Also resolved pre-existing Docker infrastructure issues that were preventing containerized execution:

- Fixed `docker-compose` CLI API version mismatch (`http+docker` scheme error)
- Added `.env` file integration for JWT and Kraken credentials (`.env` already contained `JWT_SECRET`, `JWT_EXPIRATION`, `JWT_ISSUER`, `KRAKEN_*` vars)
- Updated `trading-core/src/main/resources/application.properties` with default JWT values (`${JWT_EXPIRATION:3600000}`, `${JWT_SECRET:default-secret-must-change}`, `${JWT_ISSUER:default-issuer}`) so the app can start locally without external env var injection
- Documented that full Docker Compose execution requires: PostgreSQL container, `JWT_SECRET`/`JWT_EXPIRATION`/`JWT_ISSUER` env vars injected via `.env` or orchestration platform, and `spring.profiles.active=prod`

The Maven build `mvn clean package -DskipTests` now completes successfully in CI. Full `docker compose up` requires the infrastructure noted above.
Fixed Docker build context: changed docker-compose.yml `build: ./trading-core` to `build: .` (repository root) and updated trading-core/Dockerfile to copy risk-domain source/POM first, then build risk-domain with mvn install, then build trading-core with mvn -am (Maven reactor) so the risk-domain JAR is resolved through the Maven reactor rather than being absent from the isolated ./trading-core build context.

## What Was Built

### Backend (MI + TC)

**Market Intelligence — new files:**
- `OpportunityTradePlanGenerationService.java` — deterministic planner: loads opportunity + context, delegates to TradePlanApplicationService, resolves accountId
- `InternalOpportunityTradePlanController.java` — `POST /internal/v1/trade-plans/opportunities/{id}/trade-plans`
- `InternalOpportunityTradePlanRequest.java` — request DTO with `actorId`, `accountId`
- `TradePlanDecisionService.java` — lifecycle transitions: ACCEPTED/REJECTED via TradePlanApplicationService.transition()
- `InternalTradePlanDecisionController.java` — `POST /internal/v1/trade-plans/{id}/versions/{version}/decisions`

**Trading Core — new files:**
- `OpportunityTradePlanOrchestrationService.java` — ownership validation, MI delegation, error translation
- `OpportunityTradePlanController.java` — `POST /v1/trade-plans/opportunities/{id}/trade-plans` (public)
- `TradePlanDecisionController.java` — `POST /v1/trade-plans/{id}/versions/{version}/decisions` (public)

**Enriched existing files:**
- `TradePlanResponse.java` — added `tradingAccountId` field (required by risk evaluation)
- `TradePlanController.java` — enriches responses with context (tradingAccountId)
- `InternalTradePlanDecisionController.java` — same enrichment
- `MarketIntelligenceTradePlanningClient.java` — 3 new Feign methods: `generateFromOpportunity`, `decide`, `loadPlan`; 2 new DTOs: `PlanTransport`, `DecisionRequest`

### Frontend

**New files:**
- `core/models/trade-plan.model.ts` — TypeScript interfaces mirroring backend DTOs
- `core/services/trade-plan.service.ts` — HTTP service: createFromOpportunity, getPlan, decide, evaluateRisk
- `core/services/trade-plan.service.spec.ts` — 4 service tests
- `features/trade-planning/prepare-plan-page/` — 4 files (ts/html/scss/spec): account selector, ACTIVE gating, create action
- `features/trade-planning/plan-page/` — 4 files (ts/html/scss/spec): proposal/accepted/rejected/riskDecision views, accept/reject/evaluateRisk actions

**Modified files:**
- `app.routes.ts` — 2 new routes: `/trade-planning/prepare/:opportunityId`, `/trade-planning/plans/:planId/versions/:version`
- `opportunity-details.html` — CTA button "Create Trade Plan" visible only when status === 'ACTIVE'
- `opportunity-details.scss` — CTA button styling

## Quality Gates

| Gate | Result |
|------|--------|
| MI tests | 293 pass, 0 fail |
| TC tests | 246 pass, 0 fail |
| Frontend tests | 216 pass, 0 fail |
| Frontend build | Success (611 kB initial) |
| Prettier | All files clean |

## Untracked User File

`docs/architecture/reports/trading-os-resumption-investigation.md` — preserved across all branch operations.
