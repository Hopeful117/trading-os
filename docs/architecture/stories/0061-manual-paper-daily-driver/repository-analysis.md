# Repository Analysis - Story 0061

## Scope

Story 0061 validates the complete authenticated MANUAL PAPER lifecycle using the
Decision Workspace and the existing deterministic Risk and execution pipeline.

## Current Repository Evidence

Stories 0056 and 0057 provide account-first market context. Story 0058 provides
MANUAL TradePlan creation. Story 0059 preserves manual risk provenance. Story
0060 provides the reusable workspace-integrated manual ticket.

The existing opportunity-origin PAPER journey had already been validated by Story
0048. The primary question is whether manual-origin plans can use the same
acceptance, Risk, ExecutionIntent, PAPER settlement, position, close and history
contracts without a second path.

## Expected Responsibility Boundary

* The Web application gathers user intent and explicit human authorization.
* Market Intelligence owns MANUAL TradePlan creation and planning lifecycle.
* Trading Core assembles authoritative Risk and execution context.
* Risk Domain decides deterministically.
* Broker Service/PAPER adapter owns order and fill behavior.
* Trading Core owns persisted PAPER position and valuation state.

## Validation Risks

* Current valuation may be unavailable or stale and must fail closed.
* Duplicate Risk submission must not create conflicting authorization state.
* An existing authorized Intent must be recoverable without creating a second one.
* Reloads must preserve position, close and history continuity.

## Implementation Boundary

Included:

* Official authenticated PAPER UI validation.
* Minimal fixes for concrete blockers.
* Negative evidence for rejected/unavailable Risk.
* Position, close and history continuity verification.

Excluded:

* LIVE execution.
* New Risk rules or execution architecture.
* Direct database or broker operations.
* Advanced position management.

## Validation Expectations

* Focused backend and frontend tests.
* Angular production build/check.
* Official authenticated PAPER walkthrough.
* `git diff --check`.

## Architectural Assessment

No new ADR is required. The Story validates the existing decision pipeline and
human authority boundaries.
