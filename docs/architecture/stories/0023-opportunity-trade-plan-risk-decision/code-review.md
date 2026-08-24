# Code Review — Story 0023

## Overall Assessment

The implementation is clean, well-structured, and follows existing codebase conventions. The backend correctly preserves the responsibility chain: TC is the public boundary, MI owns the aggregate, risk-domain is deterministic. The frontend uses reactive patterns consistent with the rest of the application.

## Positive Observations

1. **Responsibility chain preserved** — TC orchestrates ownership validation and error translation; MI owns the aggregate; risk-domain evaluates deterministically
2. **Test coverage** — 20 new backend tests (MI 13 + TC 7), 10 new frontend tests (service 4 + prepare 3 + plan 3); all passing
3. **Existing patterns followed** — Angular state machines with discriminated unions, Observable-based state derivation, shareReplay for caching, exhaustMap for single-flight
4. **Contract enrichment is minimal** — `tradingAccountId` field added to responses is additive, non-breaking
5. **Idempotency** — creation and risk evaluation use Idempotency-Key headers

### Infrastructure

6. **Docker infrastructure fixed** — resolved `docker compose` CLI API version mismatch; added `.env` file integration for JWT/Kraken credentials; added default JWT values to `application.properties` for local startup without external env vars. Full `docker compose up` requires PostgreSQL container + env vars injected via orchestration platform.

## Concerns

### Moderate

1. **`crypto.randomUUID()` in frontend** — Used in `prepare-plan-page.ts` for idempotency keys. This is available in all modern browsers but not in older environments. Should use a polyfill or UUID library for broader compatibility if needed.

2. **Route parameter type** — `PlanPage` reads `version` as `Number(params.get('version'))`. If the URL has a non-numeric version, this silently produces `NaN` which triggers error state. Consider explicit validation.

3. **`evaluateRisk` in `PlanPage` casts `tradingAccountId`** — The field is now in the model, but the plan-page.ts still accesses it via a type assertion in the evaluateRisk method. This is now unnecessary since `tradingAccountId` is in the interface. Minor cleanup opportunity.

### Low

4. **Missing `ErrorInterceptor` test** — The `TradePlanService` uses `HttpClient` which goes through the app's `ErrorInterceptor`. The service spec doesn't verify error handling behavior. Acceptable for unit tests but worth noting for integration testing.

5. **Bundle size warning** — Frontend bundle is 611 kB (exceeds 500 kB budget by 111 kB). Pre-existing issue, not caused by this story.

## Files Changed

### New (20 files)
- `market-intelligence/src/main/java/.../InternalOpportunityTradePlanController.java`
- `market-intelligence/src/main/java/.../InternalOpportunityTradePlanRequest.java`
- `market-intelligence/src/main/java/.../InternalTradePlanDecisionController.java`
- `market-intelligence/src/main/java/.../OpportunityTradePlanGenerationService.java`
- `market-intelligence/src/main/java/.../TradePlanDecisionService.java`
- `market-intelligence/src/test/.../OpportunityTradePlanGenerationServiceTest.java`
- `market-intelligence/src/test/.../TradePlanDecisionServiceTest.java`
- `trading-core/src/main/java/.../OpportunityTradePlanController.java`
- `trading-core/src/main/java/.../TradePlanDecisionController.java`
- `trading-core/src/main/java/.../OpportunityTradePlanOrchestrationService.java`
- `trading-core/src/test/.../OpportunityTradePlanOrchestrationServiceTest.java`
- `trading-os-web/src/app/core/models/trade-plan.model.ts`
- `trading-os-web/src/app/core/services/trade-plan.service.ts`
- `trading-os-web/src/app/core/services/trade-plan.service.spec.ts`
- `trading-os-web/src/app/features/trade-planning/prepare-plan-page/*` (4 files)
- `trading-os-web/src/app/features/trade-planning/plan-page/*` (4 files)

### Modified (7 files)
- `market-intelligence/src/main/java/.../TradePlanResponse.java`
- `market-intelligence/src/main/java/.../TradePlanController.java`
- `market-intelligence/src/test/.../TradePlanControllerTest.java`
- `trading-core/src/main/java/.../MarketIntelligenceTradePlanningClient.java`
- `trading-os-web/src/app/app.routes.ts`
- `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.html`
- `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.scss`

## Recommendation

Approve for merge. The moderate concerns are non-blocking and can be addressed in follow-up stories.
