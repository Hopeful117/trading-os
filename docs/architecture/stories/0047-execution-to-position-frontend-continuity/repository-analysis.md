# Repository Analysis - Story 0047

## Scope

Story 0047 addresses the frontend continuity from an execution result to the
selected account positions. It must preserve broker outcome semantics and must
not change execution lifecycle or position business rules.

## Current Evidence

* `PlanPage` rendered execution identifiers, broker status, fills, and safe
  retry/reconcile actions, but discarded the Trade Plan context from execution
  views.
* The execution DTO contains broker-account and fill data but does not contain
  the instrument, direction, quantity, or Trading Account display context.
* `PlanPage` already has the authoritative Trade Plan while the execution flow
  is active, so that context can be preserved without changing the backend
  contract.
* The positions page selects the first available account and does not consume
  an account query parameter.
* Position polling runs every ten seconds. A successful close updates an
  in-memory close state but does not immediately refresh positions.
* Close results are rendered inside the position card, so a closed position can
  disappear before its result remains visible.
* The existing positions API and close API are sufficient for this scope.

## Implementation Boundary

The implementation is frontend-only:

* Preserve the Trade Plan on execution polling and terminal result views.
* Show instrument, direction, quantity, and Trading Account context beside the
  execution result.
* Link terminal execution results to `/positions?accountId=<trading-account>`.
* Initialize the positions page from the account query parameter.
* Refresh positions immediately after close completion or rejection.
* Keep the latest close result visible independently of whether the position is
  still returned by the positions endpoint.
* Keep `UNKNOWN` reconciliation behavior and `FAILED` retry behavior distinct.

No backend, broker, gateway, execution lifecycle, or risk calculation changes
are required by the current evidence.

## Validation Expectations

* Targeted execution and positions tests.
* Full Angular test suite.
* Angular production build.
* Prettier and `git diff --check`.
* Authenticated PAPER runtime validation when a suitable scenario is available.
