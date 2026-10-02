# Repository Analysis - Story 0060

## Scope

Story 0060 embeds the existing manual TradePlan form in the account-scoped
Decision Workspace without duplicating market streams, account selection or
TradePlan business logic.

## Current Repository Evidence

Story 0057 already owns selected account, eligible market, market identity,
tradability, ticker, OHLC, order book, recent trades and freshness state. Story
0058 already owns the authenticated MANUAL TradePlan API and lifecycle.

The standalone manual route contains reusable form and submission behavior, but
its independent account and market selectors discard the workspace context. The
smallest coherent change is to extract a reusable `ManualTradeTicket`, compose it
from `DecisionWorkspace`, and keep the standalone route as a fallback.

## Responsibility Boundary

* Decision Workspace owns selected account-scoped market context.
* Market Data remains authoritative for market facts and tradability.
* The ticket owns user input and request normalization only.
* Market Intelligence owns MANUAL TradePlan creation and provenance.
* Trading Core and Risk remain authoritative downstream.

## Current Gaps

1. The standalone form is not contextual to the selected workspace market.
2. The workspace does not expose the manual ticket action.
3. Form/submission logic risks duplication if extracted carelessly.

## Implementation Boundary

Included:

* Reusable ticket extraction.
* Workspace composition and context inheritance.
* Standalone route reuse.
* Focused Angular tests and PAPER creation validation.

Excluded:

* New Risk or execution architecture.
* Opportunity journey changes.
* Direct broker calls.
* LIVE execution.

## Validation Expectations

* Angular focused and full suites.
* Production build and formatting checks.
* Authenticated PAPER creation through the official UI.
* `git diff --check`.

## Architectural Assessment

No new ADR is required. The change composes existing workspace, TradePlan and
market-streaming boundaries.
