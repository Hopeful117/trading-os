# Repository Analysis - Story 0161

## Baseline

- Current branch: `main`.
- `HEAD`: `6898cac4a32ec729daa506148776a185f3510524`.
- `origin/main`: `6898cac4a32ec729daa506148776a185f3510524`.
- Story 0160 is merged through PR #180 and its Story artifact is closed.
- Working tree was clean before this analysis.
- DevLog applicability check was attempted for Cockpit/TradePlan continuity but
  failed with `LLM_PROVIDER_ERROR`; no DevLog evidence is used as authority.
- No `TOOLS.md` repository-local DevLog configuration was present.
- Obsidian was not consulted; the repository and canonical Stories provide the
  relevant current project context.

## Current Multi-Page Journey

The current browser path is:

```text
/decision-workspace?accountId=&marketId=&opportunityId=
  -> account-scoped DecisionWorkspace
  -> embedded Active Scanner / Market Navigator
  -> market selection and live market investigation
  -> Opportunity loaded from opportunityId
  -> UNDERSTAND / CHALLENGE / DECIDE
  -> PREPARE TRADE
  -> POST /api/v1/trade-plans/opportunities/{id}/trade-plans
  -> /trade-planning/plans/{planId}/versions/{version}?marketId={marketId}
  -> exact TradePlan load
  -> current market data and Story 0160 projection
  -> ACCEPT / REJECT or Risk workflow
  -> generic links back to /opportunities or /positions
```

The Cockpit is already the frontend orchestration boundary. It owns account,
market, Opportunity, Trend Context, and market-stream context. The PlanPage is a
separate bounded surface and owns exact plan presentation, projection display,
human decision actions, Risk handoff, and execution readiness.

## Context Lineage

| Context | Current source | Classification | Finding |
|---|---|---|---|
| Account | `TradePlanResponse.tradingAccountId`, planning context snapshot, Cockpit `accountId` | Preserved | PlanPage knows the account but does not use it to restore Cockpit context. |
| Market | Opportunity `marketId`; temporary PlanPage `marketId` query parameter; instrument fallback | Partial | The persisted TradePlan response does not expose market identity. Instrument fallback is ambiguous. |
| Opportunity | `TradePlan.rationale().opportunities()` with id/version; response exposes only IDs | Partial | Exact lineage exists in the backend but version/context is not exposed to PlanPage. |
| Scan | Opportunity `sourceScanId` and `sourceScanMarketId` | Preserved behind origin | Not carried through the current PlanPage contract. |
| Observation | TradePlan rationale observation IDs; persisted observation repository | Preserved as references | No historical evidence snapshot is required for current return continuity. |
| TradePlan | Exact route `planId/version`; append-only versions | Preserved | Version identity and projection route are correct. |
| Decision | Market Intelligence domain has `decisionActorId/decisionAt`; Trading Core transport omits them | Partial | PlanPage does not show decision actor/time. |
| Risk | Existing Risk endpoint and Story 0160 projection | Preserved | Must not be moved or duplicated. |
| Execution | Existing explicit execution flow | Preserved | Must not be expanded by this Story. |

## Opportunity to TradePlan Provenance

`TradePlanService.createFromOpportunity()` is the canonical transition. The
backend stores an `OpportunityPlanReference` containing the Opportunity id and
exact version in the TradePlan rationale. Opportunity-derived plans also retain
the observation references required by the domain invariant.

The public Market Intelligence response currently exposes:

- `origin`;
- `opportunityIds`;
- `observationIds`;
- `tradingAccountId`;
- decision actor/time at the Market Intelligence response boundary.

The Trading Core Feign `PlanTransport` drops the exact opportunity versions,
market identity, and decision actor/time before the response reaches the
frontend. The current UI therefore has only partial provenance.

Classification: `PARTIAL_PROVENANCE`, with a bounded read-contract extension
required for reliable continuity.

## Direct Load and Refresh

`PlanPage` loads the exact route `planId/version` from the server. For proposal
views it loads the market using the `marketId` query parameter when available,
otherwise it matches the plan instrument against the entire market catalogue.
This keeps the page functional on a direct link but does not guarantee identity
when instruments are duplicated across providers or product types.

The page does not currently load the Opportunity origin, display an origin
header, or construct a return link to `DecisionWorkspace`. Existing later-state
links go to the generic Opportunities route. Browser Back is therefore the only
way to recover the exact investigation path, which is insufficient for refresh
and deep-link continuity.

## Context Restoration

`DecisionWorkspace` already reconstructs fresh reads from query identities:

- `accountId` resolves account and Risk/Dashboard context;
- `marketId` resolves market, Trend Context, and owned market streams;
- `opportunityId` triggers a fresh Opportunity read and checks its market id.

This is the correct restoration boundary. The Story should pass canonical IDs
back to this existing route rather than serializing evidence, financial facts,
or projection values in the URL.

If the Opportunity is expired, the current Cockpit already classifies it as
non-actionable and prevents `PREPARE TRADE`. If it is unavailable, the existing
Opportunity loading/error state can remain truthful while the PlanPage remains
usable.

## Persistence Decisions

### Opportunity persistence: `NOT_REQUIRED`

The canonical `TradingOpportunityRepository` has a JPA implementation backed by
`trading_opportunity_versions`; the transient `UserOpportunityRepository` is a
separate user projection and is not the source used by TradePlan generation or
the public Opportunity API. The TradePlan stores the exact Opportunity id and
version. No new Opportunity persistence is required for this continuity Story.

### Observation persistence: `NOT_REQUIRED`

Observations have a JPA repository and TradePlans persist observation references.
The return workflow requires current Opportunity and Trend Context reads, not an
invented historical evidence snapshot. Observation durability is therefore not
needed to provide the bounded navigation continuity requested here.

## Duplicate Plan Behavior

Classification: `DUPLICATE_PLAN_GUARDED`.

`OpportunityTradePlanGenerationService` looks for an existing latest plan for
the Opportunity, actor, and account and returns it as reused. This behavior is
already account- and actor-scoped. The Story must preserve it and add regression
coverage rather than introduce new duplicate semantics.

## Manual, Accepted, and Rejected Plans

- Manual plans use `TradePlanOrigin.MANUAL` and cannot contain Opportunity
  references by domain invariant. They must not show a fabricated return action.
- ACCEPT and REJECT create new append-only TradePlan versions and retain the
  origin rationale. They must remain distinct from Cockpit-local `NO TRADE`.
- Market Intelligence stores `decisionActorId` and `decisionAt`, but the current
  Trading Core transport and frontend do not display them. Exposing these facts
  is a bounded continuity/orientation improvement if the existing contract is
  extended; it is not a decision-history redesign.

## Security and Ownership

TradePlan creation verifies account ownership in Trading Core and Opportunity
generation verifies the Opportunity account match in Market Intelligence. The
public Market Intelligence Opportunity controller does not visibly filter its
generic list/detail responses by the authenticated owner, so continuity must
not fetch an arbitrary Opportunity id from the browser and treat that response
as authorized context.

The existing TradePlan read path is the safe ownership boundary: Trading Core
passes the authenticated actor to Market Intelligence, and
`InternalTradePlanDecisionController` calls `TradePlanDecisionService.loadForActor`.
That service verifies the TradePlanningContext owner before returning the plan.
The new origin context should be derived inside this already owner-scoped read
path, with an additional account/reference consistency check where necessary.
The frontend must never construct authorization from query parameters.

## Architecture Classification

Expected changes are primarily:

- `READ_PROJECTION`: expose existing TradePlan/Opportunity lineage through an
  authenticated owner-scoped read contract;
- `API_EXPOSURE_ONLY`: carry exact origin context through existing TradePlan
  responses;
- frontend navigation/presentation: origin header and explicit return link.

No new aggregate, database migration, financial authority, global store, or ADR
is expected if the ownership gap can be closed inside existing TradePlan read
boundaries. If that requires a new cross-service ownership authority or a new
persistent aggregate, implementation must stop for an ADR decision.

## Performance and Reactive Coordination

The Cockpit already uses `switchMap`, cancellation, `shareReplay`, and explicit
market stream teardown. Return navigation should reuse this coordinator. PlanPage
should load one bounded origin context alongside the exact plan and must not
start Cockpit streams or fan out per-Opportunity requests. No N+1 origin lookup
should be introduced.

## Documentation and Prior-Story Inconsistency

Repository code and merged commits show Stories 0157, 0158, and 0159 implemented
in the Cockpit, while their current Story artifacts remain in `Review` or
`Proposed` states. Story 0160 is closed and records this continuity gap. This is
documentation/workflow drift, not a reason to rewrite historical Story status
inside 0161. The new Story relies on current repository behavior as authority.

## Recommended Smallest Coherent Implementation

1. Extend the authenticated TradePlan read contract with a typed origin context
   derived from the persisted exact TradePlan rationale and planning context.
2. Enforce owner/account checks before exposing referenced Opportunity context.
3. Carry the context through Trading Core to the frontend, including origin type,
   account id, market id, Opportunity id/version, and current origin status or
   unavailable state where authoritative.
4. Add a compact PlanPage context header and a return link to
   `/decision-workspace` with identity query parameters only.
5. Keep DecisionWorkspace's existing fresh reconstruction and actionability
   semantics unchanged.
6. Add focused positive and negative tests for direct load, refresh, account
   mismatch, cross-user access, expiry/unavailability, manual plans, duplicate
   reuse, ACCEPT, REJECT, and no execution mutation.

## Gate

Repository Analysis is complete. Human approval is required before an
Implementation Plan is produced or any application implementation file is
changed.
