# Implementation Plan - Story 0172

This plan is limited to the approved Story boundary: expose the existing
Market Thesis read model in the active Opportunity projection and present it in
the existing Opportunities views. No new decision authority is introduced.

## Files To Modify

```text
market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/OpportunityController.java
market-intelligence/src/test/java/com/hope/trading/market_intelligence/adapter/web/OpportunityControllerTest.java
trading-os-web/src/app/features/opportunities/opportunities.ts
trading-os-web/src/app/features/opportunities/opportunities.html
trading-os-web/src/app/features/opportunities/opportunities.scss
trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.ts
trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.html
trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.scss
trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.spec.ts
trading-os-web/src/app/features/decision-workspace/decision-workspace.ts
trading-os-web/src/app/features/decision-workspace/decision-workspace.spec.ts
```

No other currently modified files may be included in the Story commit.

## Implementation Steps

1. Change only the active Opportunity controller projection so a resolvable
   `marketThesis` reference produces the expanded `MarketThesisResponse`.
2. Add a controller test proving that the active endpoint includes the
   expanded Thesis and preserves the reference when present.
3. Keep the Opportunity list decision-first, displaying the Thesis state and a
   concise reason while retaining the existing row navigation behavior.
4. Keep the detail view authoritative and explicit: render state, all reasons,
   and supporting, contradicting, and missing evidence from the response.
5. Preserve the existing `ACTIVE` plus `CANDIDATE` TradePlan gate and make no
   direct TradePlan or execution calls from the new presentation sections.
6. Apply the same complete-Thesis validation at the Decision Workspace gate so
   navigation cannot expose preparation for a partial candidate payload.
7. Use safe fallbacks for partial legacy Thesis arrays without inventing
   evidence or changing the typed API contract.
8. Apply narrow-mobile layout rules to keep decision, evidence, and actions
   readable.

## Test Plan

- Backend: `OpportunityControllerTest` and `OpportunityControllerMappingTest`.
- Frontend: decision state, reason/evidence rendering, partial Thesis payloads,
  non-`CANDIDATE` TradePlan blocking, and the Decision Workspace candidate gate.
- Full frontend suite: `npm run test:ci`.
- Production build: `npm run build`.
- Formatting/diff validation: `git diff --check`.

## Delivery Boundary

- Do not include pre-existing changes in Docker Compose, Trading Core,
  challenge dashboard, scan scope, scan panel, or rehearsal captures.
- Do not create a branch, commit, push, pull request, merge, or delete branches
  until the implementation-plan gate and subsequent delivery approvals are
  explicitly satisfied.

## Current Implementation Note

The focused code changes and most validation described here already exist in
the working tree from the preceding review cycle. The remaining evidence gap is
the direct backend assertion for the expanded Thesis in the active endpoint.
This note does not replace the required implementation-plan approval or permit
delivery actions.
