# Repository Analysis - Story 0081

## Status

Repository Analysis completed after explicit human approval of Story `0081`.
Implementation planning is not started. No application code has been modified.

## Context Sources

* Current repository source and tests.
* Approved Story `0081`.
* Story `0056` - Account-First Market Decision Context.
* Story `0057` - Account-Scoped Market Workspace.
* Story `0062` - Converge Opportunity Decisions into the Decision Workspace.
* ADR-001, ADR-014, ADR-043, ADR-044, and ADR-047.

DevLog was checked during the preceding investigation but was unavailable due
to provider quota exhaustion. The analysis therefore relies on current
repository evidence; the repository remains authoritative for physical state.

## Current Implementation

### Scan Account Context

`trading-os-web/src/app/features/opportunities/scan-panel/scan-panel.ts`
stores the selected account in the component-local `accountId` field. The scan
request includes that value and the backend response model
`ActiveScanResponse` also contains `accountId`.

The scan panel exposes only `scanCompleted` to its parent. It does not expose
the selected account as an output, nor does the parent Opportunities page own a
shared account/context state.

### Opportunity Navigation

`OpportunityResponse` currently contains `id`, `marketId`, and analytical
provenance, but no account identifier.

The per-market scan result links to `/opportunities/{id}` using only the
opportunity identifier. The active opportunity list also navigates using only
the opportunity identifier.

`opportunity-details.html` then links active opportunities to:

```text
/decision-workspace?opportunityId=<id>&marketId=<marketId>
```

The authoritative market identity is therefore already available and does not
need to be reconstructed from `instrument`.

### Decision Workspace Restoration

`DecisionWorkspace` reads `accountId`, `marketId`, and `opportunityId` from query
parameters.

The current constructor loads the Opportunity as soon as `opportunityId` is
present. Account context resolution is driven by `accountId`. The market is
restored only inside the account-change branch, and only when an account query
parameter is present. With the current Opportunity navigation, the Workspace
starts in `select-account` and the market must be selected again after context
resolution.

The existing `marketView$` correctly guards market loading with the resolved
Decision Context and `eligibleMarketIds`. Market streams are opened only after
the market is loaded and confirmed tradable.

### Authoritative Contracts

`DecisionContextService.resolve(accountId)` calls the account-scoped Market
Intelligence context endpoint. The response contains the authoritative account
and eligible market set.

`AccountService.getAccounts()` retrieves accounts available to the authenticated
trader. Trading Core remains authoritative for ownership. A query parameter or
other frontend transport must not be treated as proof of ownership.

Market Data remains authoritative for current market facts and tradability.
The Workspace must continue to validate the restored market against the resolved
account context before subscribing.

## Confirmed Gap

The current flow loses the selected scan account between these stages:

```text
ScanPanel.accountId
  -> ActiveScanRequest.accountId
  -> ActiveScanResponse.accountId
  -> opportunity navigation without accountId
  -> DecisionWorkspace account selection
```

This is a frontend continuity gap. It is not currently evidence that the
Opportunity domain should own an account identity.

The key unresolved implementation choice is whether account context should be
transported from the active scan interaction through frontend navigation, or
whether an authoritative account association must be added to the Opportunity
read contract. The repository does not establish that every persisted
Opportunity has one account owner, so the latter must not be assumed.

## Affected Files and Surfaces

### Primary

* `trading-os-web/src/app/features/opportunities/scan-panel/scan-panel.ts`
* `trading-os-web/src/app/features/opportunities/scan-panel/scan-panel.html`
* `trading-os-web/src/app/features/opportunities/opportunities.ts`
* `trading-os-web/src/app/features/opportunities/opportunities.html`
* `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.ts`
* `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.html`
* `trading-os-web/src/app/features/decision-workspace/decision-workspace.ts`
* `trading-os-web/src/app/features/decision-workspace/decision-workspace.html`
* Corresponding Angular models, route tests, and component tests.

### Conditional

* Market Intelligence Opportunity response and persistence code, only if the
  authoritative account-context investigation proves a backend contract change
  is required.
* Trading Core, only if an ownership or preparation contract must be clarified.

## Architecture Assessment

The Story can likely be implemented within the existing frontend composition
without a new service, state manager, realtime transport, or ADR, provided the
account is treated as navigation context and is revalidated by existing
authoritative APIs.

A backend contract or persistence change requires renewed architectural review
if it introduces a durable account ownership meaning for opportunities, changes
the scope of the active opportunity endpoint, or moves account eligibility
authority away from Trading Core/Market Intelligence contracts already used by
the Decision Workspace.

The existing TradePlan, Risk, human-approval, and ExecutionIntent boundaries
remain unchanged. Story `0081` should not modify those flows.

## Risks and Edge Cases

* The active opportunity list may contain opportunities from more than one
  account or from origins that are not tied to the current scan interaction.
  Context propagation must not claim an account association that the backend
  cannot prove.
* A market may become ineligible or non-tradable between scan completion and
  Workspace restoration. The Workspace must clear or reject it without opening
  streams.
* The user may change accounts after arriving from an opportunity. The previous
  market and any account-dependent opportunity preparation state must be
  revalidated or cleared.
* Direct URLs may contain only an opportunity or only a market. The existing
  account-first fallback must remain truthful and actionable.
* Query parameters are observable client input. They cannot replace JWT,
  ownership, or account-context validation.
* Loading the Opportunity before account context resolution must not create a
  false impression that its market is already valid for the selected account.

## Existing Test Coverage

Current tests cover:

* Opportunity Detail navigation with `opportunityId` and `marketId`.
* Decision Workspace account-first loading.
* Query-parameter market restoration when `accountId` is already present.
* Ineligible URL markets not opening Market Data streams.
* Account changes clearing selected market and subscriptions.
* Opportunity-origin TradePlan preparation without an ExecutionIntent.

Missing regression coverage for Story `0081` includes:

* scan account propagation through result/detail/workspace navigation;
* asynchronous restoration of account and market context;
* stale or mismatched account/market/opportunity combinations;
* direct-entry fallback with absent account context;
* clearing Opportunity context when the account changes;
* proof that no stream opens before restored eligibility and tradability.

## Validation Baseline

The Story requires the repository-standard frontend checks:

```text
cd trading-os-web && npm run test:ci
cd trading-os-web && npm run build
git diff --check
```

An authenticated PAPER walkthrough is required because unit tests cannot prove
that the real scan, navigation, account-context resolution, market restoration,
and preparation sequence remains coherent.

## Analysis Conclusion

Story `0081` is implementable within the identified boundaries. The physical
implementation gap is confirmed in the Angular navigation/state composition.
No blocking contradiction with the approved Story or accepted ADRs was found.

The next workflow stage is Implementation Planning. It requires explicit human
approval of this Repository Analysis before any implementation files are
changed.
