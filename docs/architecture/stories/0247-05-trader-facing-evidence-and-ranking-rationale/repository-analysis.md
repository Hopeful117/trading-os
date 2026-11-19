# Repository Analysis - Story 0247-05

## Scope

This analysis covers Story `0247-05 - Trader-Facing Evidence and Ranking Rationale`.
The Story was approved by the human owner for Repository Analysis. Implementation is not authorized
by this document.

The repository is currently at `main` revision `09e028eec0864ccf8b4abae98fcee6c9cc8a9aac`, with
the Story artifact present as an uncommitted file. Story `0247-04` is merged. DevLog was queried for
applicability, but its returned context was truncated and low confidence; it did not retain the full
Story or frontend/API source evidence. The current repository, the canonical Story, the Epic and
accepted ADRs are therefore authoritative for this analysis.

## Product Boundary

The Epic defines this Story as the trader-facing completion of the deterministic evidence and ranking
work delivered by Stories `0247-01` through `0247-04`. The intended result is an explainable
investigation workflow, not a recommendation engine or an execution feature.

The presentation must preserve these distinctions:

```text
evidence quality -> opportunity ranking -> trader investigation
                                      -> TradePlan
                                      -> RiskEvaluation
                                      -> human validation
                                      -> execution
```

The UI must not collapse evidence quality, business priority, risk authorization or broker execution
into one status or score.

## Existing Backend Contracts

### Opportunity API

`OpportunityController` exposes the public versioned opportunity API under `/api/v1/opportunities`:

- paginated latest-opportunity search;
- latest opportunity by ID;
- active opportunities;
- opportunity history.

The controller resolves the exact referenced thesis ID/version only for responses where thesis data is
included. The active endpoint currently returns expanded thesis data. The list endpoint intentionally
returns a lighter response.

The Gateway already routes `/api/v1/opportunities/**` to `market-intelligence` through the dedicated
`opportunities` route. No new Gateway route is justified for this Story if the existing opportunity
contract is extended additively.

### Opportunity response

`OpportunityResponse` currently contains:

- immutable opportunity identity and version;
- lifecycle, instrument, direction, scenario, timeframe, type and origin;
- `score` and `scoreSemantics`;
- explanation and observation/AI references;
- market, account, scan and analysis lineage;
- optional `MarketThesisReference`;
- optional setup snapshot and expanded `MarketThesisResponse`.

The response does not expose `EvidenceQualityAssessment` or `OpportunityRankingRationale`.

`scoreSemantics` already carries the explicit value
`BUSINESS_PRIORITY_NOT_PROBABILITY`. This label must remain unchanged and visible anywhere the score
is shown with evidence quality.

### Market Thesis response

`MarketThesisResponse` currently exposes:

- thesis identity, lineage and version;
- market and instrument;
- thesis state and scenario;
- complete, supporting, contradicting and missing evidence lists;
- thesis reasons;
- evaluation and source-cutoff timestamps;
- validity, composer and input-fingerprint provenance.

The response does not expose the persisted `EvidenceQualityAssessment`. The existing evidence arrays
are useful for trader display, but they do not replace the assessment because they do not expose the
versioned quality policy, normalized dimension states, overall quality status or structured quality
reason explanations.

### Evidence quality

`EvidenceQualityAssessment` is an immutable, non-probabilistic domain contract containing:

- `policyVersion`;
- `evaluatedAt` and `sourceCutoff`;
- overall `QualityStatus` (`SUPPORTED`, `DEGRADED`, `BLOCKED`, `UNAVAILABLE`);
- six explicit dimensions;
- dimension states including `SATISFIED`, `PARTIAL`, `MISSING`, `STALE`, `CONTRADICTED`,
  `UNAVAILABLE`, `NOT_IMPLEMENTED` and `NOT_APPLICABLE`;
- structured reason code and explanation pairs;
- evidence provenance;
- input and result fingerprints.

The `EvidenceQualityEvaluator` is the deterministic authority for producing this assessment. The
frontend must render its values and must not recalculate them from raw evidence arrays.

### Ranking

Story `0247-04` added:

- `OpportunityRankingService` for request-time exact thesis resolution;
- `EvidenceBasedOpportunityRankingPolicy` with version `opportunity-ranking/v1`;
- `OpportunityRankingRationale` with ranking policy version, evidence-quality policy version,
  quality status, dimension decisions, structured reasons and fingerprints;
- `OpportunityRankingResult` with ordered entries and explicit excluded-candidate diagnostics.

The ranking service is registered as a Spring bean but is not currently used by
`OpportunityController`. The public opportunity API still reads repository collections directly and
sorts them by requested presentation fields. The active list therefore does not yet expose the
evidence-based order or its rationale.

The ranking service accepts an explicit list of opportunities, so the active endpoint can reuse the
existing active projection without changing repository ownership or adding persistence. The service
does not expose a rank position or a peer-comparison sentence; the ordered result and per-entry
rationale are the current authoritative application boundary.

## Existing Frontend Workflow

### Opportunity list

`OpportunityService` currently provides:

- `findActive()` using `/v1/opportunities/active`;
- `findById()` using `/v1/opportunities/{id}`.

The `Opportunities` standalone component uses Observable streams, `switchMap`, `catchError`,
`startWith` and `shareReplay`. It renders active opportunities in a responsive, horizontally
scrollable table with explicit loading, error and empty states.

The list already shows:

- thesis decision state and one thesis reason;
- instrument, market type, direction, type and timeframe;
- business-priority score;
- setup reference price and validity;
- the opportunity explanation.

The table currently does not show evidence-quality dimensions, evidence freshness/provenance,
ranking policy version or ranking rationale. It also renders the backend collection order rather than
an explicitly ranked response.

### Decision Workspace

The active opportunity loaded by `DecisionWorkspace` uses `OpportunityService.findById()`. The
workspace already renders an evidence-led opportunity review containing:

- thesis state and version;
- validity;
- support, contradiction and missing counts;
- scenario, confirmation and invalidation conditions;
- relevant levels;
- expandable supporting, contradictory and missing evidence cards;
- evaluation time, source cutoff and thesis input fingerprint.

This is the strongest existing surface for the detailed rationale because it already sits before
TradePlan preparation and risk validation. It also explicitly displays that the thesis view does not
authorize execution.

### Opportunity detail route

`OpportunityDetail` is currently a compatibility route. It loads an opportunity and immediately
redirects to `/decision-workspace`, preserving opportunity, version, market and account context. It
does not provide an independent detail presentation surface. Story implementation should therefore
not assume that this route is a separate UI target unless the approved plan explicitly changes that
behavior.

### Existing frontend models and tests

`OpportunityResponse` and `MarketThesisResponse` TypeScript models mirror the current API but have no
quality-assessment or ranking-rationale types. Existing service and component tests use representative
opportunity and thesis fixtures, including missing/contradictory evidence arrays and loading/error
states. Decision Workspace tests already cover thesis rendering and workflow behavior and should be
extended only where the new display is placed.

Angular uses standalone components, Angular 21, RxJS 7.8, Vitest and the scripts:

```text
npm run test:ci
npm run build
npm run check
```

## Architectural Constraints

The following constraints are authoritative:

- Market Data remains authoritative for provider-neutral market identity and observed facts.
- Market Intelligence owns evidence composition, `EvidenceQualityAssessment` and ranking policy.
- `StrategyEvaluator` and `StrategyMatch` remain authoritative for setup truth.
- `OpportunityScore` remains business priority and never becomes probability, profitability or risk.
- Trading Core and Risk Domain remain authoritative for account context, risk, authorization and
  execution.
- Passive awareness remains account-independent and cannot create trading authority.
- AI remains optional, advisory and downstream of deterministic evidence.
- Existing `Observation`, `MarketThesis`, `TradingOpportunity` and ranking contracts should be reused.
- No new microservice, provider, persistence table, ActiveScan operation, broker path or execution
  authority is justified.
- Gateway routing already covers the existing opportunity API and should not be duplicated.
- Angular data flow should remain reactive and use Observables/async pipe where practical.
- Accessibility must not rely on color alone to communicate evidence quality.

These constraints are consistent with the Epic and ADR-033, ADR-034 and ADR-035.

## Recommended Implementation Boundary

The smallest coherent implementation should be additive across the existing API and two existing
Angular surfaces:

1. Add provider-neutral response DTOs for evidence quality and ranking rationale, mapped directly from
   the authoritative domain/application contracts.
2. Extend `OpportunityResponse` and the expanded thesis response additively rather than duplicating
   evidence-quality fields into `TradingOpportunity` or `MarketThesis`.
3. Wire the active opportunity projection through `OpportunityRankingService` so the existing active
   workflow receives deterministic order and per-entry rationale. Excluded candidates must not be
   presented as ranked opportunities; unavailable or unassessed data must remain explicit.
4. Preserve existing search and history semantics unless the Implementation Plan explicitly selects
   the ranking policy for those endpoints. The current evidence supports applying ranking first to the
   active trader workflow, not silently redefining every generic sort parameter.
5. Expose the same additive rationale and quality fields on the opportunity-by-ID response used by
   Decision Workspace, resolving the exact referenced thesis version.
6. Update Angular models and the `OpportunityService` only if a new query or response shape requires
   it. Prefer the existing `findActive` and `findById` flows over introducing a second parallel API.
7. Add compact quality-state and rationale presentation to the active list, and detailed dimension,
   reason and provenance presentation to the existing Decision Workspace evidence review.
8. Use explicit text labels and accessible status attributes. Do not use color as the only signal.
9. Keep all ranking and quality computation server-side. The frontend renders authoritative values and
   handles absent or unavailable optional fields without manufacturing defaults.

The exact DTO shape, whether ranking is applied to `/active` or exposed through a bounded dedicated
read operation, and the final placement of the detailed panel remain Implementation Plan decisions.
The Repository Analysis recommends the additive existing-endpoint approach because the Gateway,
service, route and workspace already form a complete path.

## Contract and Data Gaps

The concrete gaps are:

- no API representation for `EvidenceQualityAssessment`;
- no API representation for `OpportunityRankingRationale`;
- no controller integration for `OpportunityRankingService`;
- no rank position or explicit peer-comparison result in the current ranking application result;
- no Angular model for quality dimensions or rationale reasons;
- no active-list presentation for evidence quality or ranking rationale;
- no Decision Workspace presentation for normalized quality dimensions or ranking rationale.

The gaps do not justify:

- copying quality data into `TradingOpportunity`;
- adding a ranking snapshot table;
- recalculating quality in Angular;
- adding a new service or Gateway route;
- changing risk, TradePlan or execution contracts.

## Risks

- Rendering a raw business score beside quality states without explicit semantics could be interpreted
  as a probability or recommendation guarantee.
- Applying ranking only in the frontend would produce a second, non-authoritative ordering policy.
- Returning a thesis without its exact evidence-quality assessment could make a positive thesis state
  appear more certain than the persisted assessment supports.
- Treating excluded candidates as low-ranked opportunities would blur lifecycle and eligibility
  semantics.
- Exposing provider or internal implementation fields without a stable display contract could leak
  infrastructure details into the public API.
- Color-only badges could make contradictory, missing or stale states inaccessible.
- A large evidence ledger in the active table could damage responsive usability; summary and detail
  views should have different information densities.
- Changing the existing generic list sort behavior without an explicit decision could break existing
  consumers.

## Questions Requiring Implementation Plan Decisions

- Should `/api/v1/opportunities/active` become the ranked active projection, or should a bounded
  `/api/v1/opportunities/ranked` read endpoint be added while preserving `/active` compatibility?
- Should evidence quality live under `thesis.evidenceQuality`, under an opportunity-level `ranking`
  object, or both with clearly separated ownership?
- Which provenance fields are trader-useful and safe to expose: policy versions, timestamps,
  evidence references and fingerprints, or a narrower subset?
- Should the active list display a rank position, a compact quality summary, or only stable ordering
  with a link to detailed rationale?
- Should excluded candidates be absent from the active list and represented only by diagnostics, or is
  there an existing consumer that requires an explicit excluded state?
- Should the Decision Workspace show the full dimension ledger by default or behind an accessible
  expandable section?
- Does the API need a dedicated response version change, or are additive nullable fields compatible
  with all current consumers and fixtures?

## Validation Plan

### Backend/API tests

- Response mapping tests for quality status, all six dimensions, dimension states, structured reasons,
  policy versions, timestamps and fingerprints.
- Controller tests proving exact thesis version resolution and additive response compatibility.
- Active projection tests proving deterministic ranking order and explicit exclusion handling.
- Tests proving no ranking or quality computation is performed in the frontend.
- Gateway route tests only if a route changes; the current route should remain unchanged.

### Angular tests

- Service/model tests for the enriched active and detail responses.
- Active-list tests for supported, degraded, blocked/unavailable, stale, contradictory, missing and
  loading/empty/error states.
- Decision Workspace tests for detailed dimension and rationale rendering.
- Tests that business priority is labeled separately from evidence quality and probability.
- Keyboard/accessibility assertions for status labels, expandable content and non-color indicators.
- Responsive layout review for table summary and detailed rationale panels.

### Repository validation

- Targeted `market-intelligence` Maven tests when backend contracts change.
- From `trading-os-web`: `npm run test:ci` and `npm run build`.
- `git diff --check`.
- Existing Gateway route and relevant integration tests when public routing or response behavior changes.

## Affected Areas

Likely affected areas, subject to the approved Implementation Plan:

- `market-intelligence/.../adapter/web/OpportunityResponse.java`;
- `market-intelligence/.../adapter/web/MarketThesisResponse.java`;
- `market-intelligence/.../adapter/web/OpportunityController.java`;
- response DTO/mapping classes under `market-intelligence/.../adapter/web/`;
- `market-intelligence/.../application/opportunity/OpportunityRankingService.java` and result mapping;
- backend controller and mapping tests;
- `trading-os-web/src/app/core/models/opportunity.model.ts`;
- `trading-os-web/src/app/core/models/market-thesis.model.ts`;
- `trading-os-web/src/app/core/services/opportunity.service.ts` only if required by the selected API;
- `trading-os-web/src/app/features/opportunities/`;
- `trading-os-web/src/app/features/decision-workspace/`;
- corresponding Angular tests and styles.

Expected unchanged areas:

- StrategyEvaluator, StrategyMatch and setup truth;
- EvidenceQualityEvaluator and ranking policy semantics;
- OpportunityScore calculation and meaning;
- Risk Domain, TradePlan, Trading Core, Broker Service and execution;
- replay and market-outcome persistence;
- Gateway routes unless the plan demonstrates a concrete routing gap.

## Conclusion

The repository already contains the deterministic evidence and ranking primitives required by Story
`0247-05`, and the Angular Decision Workspace already provides the correct evidence-led investigation
boundary. The remaining work is an additive API projection and a focused presentation layer, not a
new ranking algorithm or workflow authority.

The safest implementation is to keep Market Intelligence authoritative for quality and ordering,
extend the existing opportunity/thesis read contracts, apply the ranked projection to the existing
active workflow, and present compact summaries in the list with detailed rationale in the Decision
Workspace. The Implementation Plan must resolve the endpoint/DTO placement questions before code is
written.
