# Implementation Plan - Story 0247-05

## Status

Prepared after human approval of the Repository Analysis. Implementation is not authorized until
this plan is explicitly approved.

## Implementation Objective

Expose the deterministic evidence-quality assessment and evidence-based opportunity-ranking rationale
through the existing opportunity API and Angular investigation workflow. The implementation will keep
Market Intelligence authoritative for quality and ordering, preserve existing score and thesis
semantics, and introduce no new execution or risk authority.

## Decisions

### Existing API boundary

Use the existing `/api/v1/opportunities` API and its Gateway route. Do not add a new microservice,
Gateway route, ranking snapshot endpoint or persistence table.

The following responses will be extended additively:

- `GET /api/v1/opportunities/active` will return the existing list shape with an optional ranking
  projection on each item.
- `GET /api/v1/opportunities/{id}` will return the existing expanded thesis plus the ranking
  projection for that exact latest opportunity.

The generic paginated search endpoint and history endpoint retain their current presentation-sort and
history semantics in this Story unless a concrete consumer contract gap is discovered during
implementation. They must not silently switch from requested sorting to evidence ranking.

### Server-side ranking projection

The controller will call `OpportunityRankingService` for the active projection instead of rebuilding
ranking behavior locally.

- The active endpoint will pass its current active opportunities to `OpportunityRankingService.rank`.
- Ranked candidates will be emitted first in the policy order.
- Active opportunities excluded by the ranking service will remain visible after ranked candidates,
  but with an explicit excluded ranking outcome and diagnostic code/explanation. They must not be
  presented as ranked opportunities.
- Each ranked item receives a stable one-based position derived from the ordered application result.
- The latest-by-ID response will rank the single exact opportunity only, preserving exact thesis
  resolution and providing its rationale or exclusion diagnostic.

This keeps the existing active awareness workflow complete while making unavailable or unassessed
quality explicit instead of silently dropping an active opportunity from the trader's view.

### Response contracts

Add provider-neutral web DTOs under the existing Market Intelligence web adapter boundary.

`EvidenceQualityResponse` will expose the persisted assessment without recomputing it:

- quality policy version;
- evaluated-at and source-cutoff timestamps;
- overall quality status;
- all dimension/state/evidence-reference entries;
- structured reason code and explanation pairs;
- evidence provenance needed for trader-facing freshness and traceability;
- input and result fingerprints.

`OpportunityRankingResponse` will expose the request-time presentation projection:

- ranking policy version;
- outcome (`RANKED` or `EXCLUDED`);
- one-based position when ranked;
- structured rationale when ranked;
- exclusion code and explanation when excluded.

`MarketThesisResponse` will add optional `evidenceQuality` mapped from the exact referenced thesis.
`OpportunityResponse` will add optional `ranking` mapped from the ranking result. Existing fields and
their names remain unchanged, especially `scoreSemantics`.

All new fields are additive and nullable/optional for responses produced by existing paths that do not
perform ranking, such as ActiveScan result projections and the generic search endpoint.

### Exact evidence and provenance

The server will continue to resolve the exact `MarketThesisReference` ID and version. It will never
resolve the latest thesis as a fallback.

The response mapper will only expose quality and rationale values from the exact resolved thesis and
ranking result. It will not infer quality from the thesis state, evidence-array lengths or timestamps.

### Angular presentation

The existing `Opportunities` active table will add a compact, text-labelled quality/ranking summary:

- rank position when available;
- evidence-quality status;
- a concise distinction between evidence quality and business-priority score;
- an explicit unavailable/excluded state and diagnostic where applicable.

The existing `DecisionWorkspace` thesis review will add the detailed presentation:

- quality policy version and overall status;
- each evidence-quality dimension and state;
- structured quality reasons and ranking reasons;
- relevant freshness/provenance timestamps and fingerprints where useful;
- ranking policy version and position/outcome;
- explicit copy stating that quality and business priority are not probability, profitability or
  execution authorization.

The compatibility `OpportunityDetail` route remains a redirect to the Decision Workspace. No separate
detail page is introduced.

Presentation rules:

- render authoritative values from the API only;
- use text, labels and accessible attributes in addition to color;
- keep the active table compact and put full evidence detail behind the existing expandable thesis
  ledger or an equivalent accessible disclosure;
- preserve Observable/async-pipe loading, empty and error flows;
- treat absent optional quality/ranking fields as unavailable, never as positive evidence.

### Backend compatibility

The existing `OpportunityController` constructor and tests will be updated to inject the ranking
service while preserving the controller's current endpoint paths and response behavior for callers that
do not request ranked active data.

`ActiveScanResponse` and other internal response projections will not be changed unless compilation or
a demonstrated response-contract requirement requires the additive fields. They are not the primary
trader-facing workflow for this Story.

## Planned Changes

### Backend response mapping

Add or update the following under `market-intelligence`:

- `EvidenceQualityResponse` and nested immutable response records;
- `OpportunityRankingResponse` and nested rationale response records;
- `MarketThesisResponse` to include optional evidence quality;
- `OpportunityResponse` to include optional ranking;
- `OpportunityController` to use `OpportunityRankingService` for active and exact latest responses;
- mapper helpers that preserve ranked/excluded identity and version.

The response DTOs must remain provider-neutral and must not expose broker credentials, internal service
tokens or infrastructure transport details.

### Frontend models and service

Update:

- `trading-os-web/src/app/core/models/opportunity.model.ts`;
- `trading-os-web/src/app/core/models/market-thesis.model.ts`;
- `trading-os-web/src/app/core/services/opportunity.service.ts` only if the selected response/query
  requires a service contract change.

The model types will represent all API states explicitly, including optional quality and ranking data,
dimension states, structured reasons, provenance and exclusion diagnostics.

### Frontend components and styles

Update the existing components rather than creating a parallel workflow:

- `trading-os-web/src/app/features/opportunities/opportunities.ts` and `.html/.scss` for compact
  active-list ranking and quality display;
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.html/.scss` for detailed
  quality dimensions and ranking rationale;
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.ts` only if a small display
  helper is needed for stable labels or state grouping.

No changes are planned to TradePlan creation, risk validation, authorization or execution actions.

## Test Plan

### Backend unit and controller tests

Add or update tests to verify:

- `EvidenceQualityAssessment` maps all quality statuses, dimensions, states, reasons, timestamps,
  provenance and fingerprints without mutation or inference;
- `OpportunityRankingRationale` maps both policy versions, dimensions, structured reasons and
  fingerprints;
- active opportunities are returned in deterministic ranking order;
- excluded active opportunities remain visible only with explicit excluded diagnostics;
- ranked positions are stable and one-based;
- exact thesis ID/version resolution is preserved;
- a missing or unassessed exact thesis is not represented as positive quality;
- existing search, latest, active and history endpoint behavior remains compatible where ranking is
  not selected;
- controller response mapping handles absent optional fields;
- existing `OpportunityControllerTest` and ranking tests remain green.

### Angular service and model tests

Extend `opportunity.service.spec.ts` and fixtures to cover enriched active and detail responses,
including optional quality/ranking fields and excluded/unavailable outcomes.

### Angular component tests

Extend the active opportunities and Decision Workspace tests for:

- supported/positive quality;
- degraded and stale quality;
- contradictory quality;
- missing, not-implemented and unavailable quality;
- excluded ranking outcome;
- loading, empty and error states;
- business-priority score labelled as non-probabilistic;
- ranking policy/version visibility;
- accessible text and disclosure behavior;
- unchanged navigation into Decision Workspace and onward TradePlan/risk workflows.

### Validation commands

From `market-intelligence`:

```bash
mvn -q -Dtest=OpportunityControllerTest,<ranking-and-response-tests> test
mvn -q -DskipTests package
```

From `trading-os-web`:

```bash
npm run test:ci
npm run build
```

Also run:

```bash
git diff --check
```

The broader repository quality workflow remains applicable at PR validation time.

## Files Expected To Change

Likely backend files:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/OpportunityController.java`;
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/OpportunityResponse.java`;
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketThesisResponse.java`;
- new response DTOs under `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/`;
- controller, mapper and response contract tests under `market-intelligence/src/test/`.

Likely frontend files:

- `trading-os-web/src/app/core/models/opportunity.model.ts`;
- `trading-os-web/src/app/core/models/market-thesis.model.ts`;
- `trading-os-web/src/app/core/services/opportunity.service.ts` and its tests if required;
- `trading-os-web/src/app/features/opportunities/opportunities.ts`;
- `trading-os-web/src/app/features/opportunities/opportunities.html`;
- `trading-os-web/src/app/features/opportunities/opportunities.scss`;
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.ts` only if needed;
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.html`;
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.scss`;
- corresponding Angular tests.

Expected unchanged areas:

- `EvidenceQualityEvaluator` and `EvidenceBasedOpportunityRankingPolicy` semantics;
- `TradingOpportunity`, `MarketThesis`, `OpportunityScore` and StrategyMatch truth;
- risk, execution, broker, Trading Core and Gateway routing;
- replay and market-outcome persistence;
- `OpportunityDetail` redirect semantics unless a concrete compatibility issue is discovered.

## Risks And Mitigations

- **False certainty:** use explicit status labels, reason explanations and non-probabilistic score copy.
- **Policy duplication:** keep ranking and quality computation in Market Intelligence; Angular only
  renders response values.
- **Contract drift:** map exact versioned thesis and rationale contracts and add controller tests.
- **Active candidate disappearance:** preserve excluded active candidates with explicit diagnostics.
- **Responsive overload:** use compact list summaries and detailed expandable workspace content.
- **Compatibility regression:** keep generic search/history behavior and existing route paths unchanged.
- **Authority leakage:** do not add risk, authorization, execution or ActiveScan actions.

## Definition Of Done

- Existing active opportunity and Decision Workspace workflows expose evidence quality and ranking
  rationale from authoritative server data.
- Supporting, contradictory, missing, stale and unavailable states are visibly distinguishable.
- Business priority remains explicitly distinct from evidence quality and probability.
- Excluded candidates and unavailable rationale are explicit and not presented as ranked certainty.
- Existing TradePlan, risk, human validation and execution boundaries remain unchanged.
- Backend response/controller tests pass.
- Angular tests and production build pass.
- `git diff --check` passes.
- No implementation begins before explicit human approval of this plan.
