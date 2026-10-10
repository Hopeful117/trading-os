# Implementation Plan - Story 0081

## Status

Implementation Plan prepared after explicit approval of the Repository
Analysis. No implementation files have been modified.

## Implementation Strategy

Preserve the account selected on the Opportunities surface as navigation
context, transport it alongside the authoritative `opportunityId` and
`marketId`, and let the Decision Workspace restore the market only after the
account-scoped Decision Context has resolved.

The account identifier is navigation context, not an authorization claim. The
existing account ownership and account-market eligibility endpoints remain
authoritative. No durable `accountId` field will be added to Opportunity unless
the implementation proves that the current scan/navigation contract cannot
preserve context safely and the domain model establishes a single authoritative
account association.

## Step 1 - Make Opportunities Own Navigation Context

Update the Opportunities surface and Scan Panel boundary so the selected scan
account is available to navigation without duplicating account-selection rules.

Planned changes:

* Expose the selected account context from `ScanPanel` to `Opportunities`, or
  otherwise promote the existing selected value to the parent-owned route/UI
  context.
* Preserve the account context in the `/opportunities` navigation state or
  query parameters so it survives the scan result and list-to-detail path.
* Keep scan creation unchanged: `accountId` remains submitted to the existing
  Active Scan API and backend scope resolution.
* Ensure per-market scan-result links include the scan account context together
  with the opportunity id.
* Ensure active-opportunity list links include the current Opportunities account
  context when one is available; links from an account-less direct list remain
  valid and use the existing fallback.

Files expected to change:

* `trading-os-web/src/app/features/opportunities/scan-panel/scan-panel.ts`
* `trading-os-web/src/app/features/opportunities/scan-panel/scan-panel.html`
* `trading-os-web/src/app/features/opportunities/opportunities.ts`
* `trading-os-web/src/app/features/opportunities/opportunities.html`

## Step 2 - Preserve Context Through Opportunity Detail

Update Opportunity Detail navigation so it reads the optional account context
from the route and forwards it with the existing authoritative opportunity and
market identities.

Planned changes:

* Read optional `accountId` navigation context without treating it as proof of
  ownership.
* Preserve `opportunityId` from the route and `marketId` from the loaded
  Opportunity response.
* Add `accountId` to the Decision Workspace link only when it is present and
  non-empty.
* Preserve direct detail URLs without account context; they must still open the
  Workspace and present the account-first fallback.

Files expected to change:

* `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.ts`
* `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.html`

## Step 3 - Restore Workspace Context Safely

Refine `DecisionWorkspace` query-parameter initialization so transported
account and market context are applied in the correct order.

Planned behavior:

1. Read `accountId`, `marketId`, and `opportunityId` as navigation hints.
2. Load the Opportunity context without opening market streams.
3. Resolve the account Decision Context through the existing service.
4. After the account context is ready, restore the requested `marketId` only if
   it is in `eligibleMarketIds`.
5. Load Market Data and open streams only after the existing tradability guard
   succeeds.
6. Clear or reject an absent, stale, ineligible, or non-tradable market with a
   truthful state and no stream subscription.
7. If the account changes, clear account-dependent market and opportunity
   preparation state unless it is revalidated against the new context.
8. Keep manual and Opportunity-origin TradePlan actions unchanged after the
   restored context is valid.

The implementation must avoid introducing a second state manager or broadening
the existing account-context contract.

Files expected to change:

* `trading-os-web/src/app/features/decision-workspace/decision-workspace.ts`
* `trading-os-web/src/app/features/decision-workspace/decision-workspace.html`

## Step 4 - Add Regression Coverage

Extend focused Angular tests before broad validation.

Required cases:

* Selected scan account is propagated to scan-result opportunity links.
* Selected Opportunities account is propagated to list/detail navigation.
* Opportunity Detail forwards `accountId`, `opportunityId`, and authoritative
  `marketId` to the Workspace.
* Workspace restores account and market after asynchronous context resolution.
* Workspace does not open streams before account eligibility is resolved.
* Ineligible, stale, or non-tradable restored markets are not subscribed.
* Changing account clears the restored market and prevents stale context use.
* Direct Opportunity Detail and Workspace entry without account context remains
  actionable through account-first selection.
* Existing Opportunity preparation still reaches the shared PlanPage and does
  not create an ExecutionIntent.

Expected test files:

* `trading-os-web/src/app/features/opportunities/opportunities.spec.ts`
* `trading-os-web/src/app/features/opportunities/scan-panel/scan-panel.spec.ts`
* `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.spec.ts`
* `trading-os-web/src/app/features/decision-workspace/decision-workspace.spec.ts`

If the chosen transport requires a new typed route-context model, add it under
`trading-os-web/src/app/core/models/` and cover it with focused tests. Do not
introduce an application-wide store for this flow.

## Step 5 - Conditional Contract Investigation

Before changing backend code, verify whether the frontend can carry the account
context from the active scan interaction without making a false durable claim
about Opportunity ownership.

Only if that verification fails:

* inspect the Opportunity aggregate, lineage, persistence, and response
  semantics for an authoritative account association;
* document the contradiction or missing authority;
* stop for human architectural guidance if adding account ownership would
  change domain meaning or public contract semantics;
* otherwise implement the smallest additive contract change and add backend
  ownership/provenance tests.

The default plan contains no backend or Gateway changes.

## Validation Sequence

1. Run focused Angular tests for the four affected component areas.
2. Run `cd trading-os-web && npm run test:ci`.
3. Run `cd trading-os-web && npm run build`.
4. Run `git diff --check`.
5. Execute an authenticated PAPER walkthrough:
   * select account;
   * run scan;
   * open a produced opportunity;
   * open Decision Workspace;
   * verify account and market context are restored;
   * verify no duplicate account or market selection is required;
   * verify streams start only after eligibility and tradability resolution;
   * prepare the Opportunity TradePlan;
   * verify no execution request occurs during preparation.
6. Review the complete diff for unrelated changes and documentation impact.

## Expected Deliverables

* Updated Angular navigation and Workspace restoration behavior.
* Focused regression tests for context continuity and stream safety.
* Implementation Report with validation evidence and documentation outcome.
* No backend changes unless the conditional contract investigation proves them
  necessary and the required authority is established.

## Risks Requiring Review

* A global active-opportunity list may contain opportunities from different
  account contexts. The UI must not label a carried account as an immutable
  Opportunity owner unless the backend contract proves that relationship.
* Query parameters can be edited by the client. All account and market
  decisions must continue to be revalidated through authoritative APIs.
* Restoring the market too early could create a stream outside the account's
  eligible set. The asynchronous ordering tests are mandatory.

## Approval Boundary

This plan is ready for human approval. Implementation must not begin until the
human explicitly approves this Implementation Plan.
