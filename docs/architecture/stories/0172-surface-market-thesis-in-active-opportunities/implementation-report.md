# Implementation Report - Story 0172

## Outcome

Implemented the active Opportunity Market Thesis projection and decision-first
presentation without creating a new decision or execution authority.

## Changes

- The active Opportunity API now expands a resolvable `marketThesis` reference
  into the existing `MarketThesisResponse`.
- The Opportunities list presents `NO TRADE`, `WATCH`, or `CANDIDATE` before
  the Opportunity facts and labels the numeric score as signal strength.
- The Opportunity detail view presents Thesis state, reasons, and supporting,
  contradicting, and missing evidence.
- Partial and invalid Thesis payloads render as unavailable rather than as an
  empty or authoritative decision.
- TradePlan preparation remains restricted to complete `CANDIDATE` Thesis data,
  both in the Opportunity detail view and in Decision Workspace.
- Narrow mobile layout handling was added for the decision and evidence panel.
- Added backend and frontend regression coverage for the active projection,
  evidence rendering, non-candidate blocking, incomplete Thesis payloads, and
  state-only candidate payloads.

## Documentation Reconciliation

Story 0172 and its Repository Analysis and Implementation Plan are the
canonical documentation for this focused change. ADR-076 remains unchanged:
Market Thesis is analytical authority and does not authorize Risk, TradePlan,
broker, or execution actions.

## Validation

- Angular `npm run test:ci`: passed, 56 test files and 565 tests.
- Angular `npm run build`: passed during the preceding validation cycle; the
  build reports existing bundle and component-style budget warnings.
- Market Intelligence targeted Maven tests:
  `OpportunityControllerTest` and `OpportunityControllerMappingTest` passed,
  5 tests total.
- `git diff --check`: passed before the latest frontend-only validation cycle.
- DevLog context lookup failed because the upstream provider was rate-limited;
  repository and ADR sources were used instead.

## Scope Review

The Story files are limited to the Market Intelligence Opportunity projection,
the Opportunities and Decision Workspace presentation gates, their tests, and
the Story artifacts. Existing unrelated worktree changes in Docker Compose,
Trading Core challenge code, scan scope, scan panel, and rehearsal captures
must remain excluded from the Story commit.

## Review State

Implementation and focused validation are complete. Independent review found and
the implementation corrected partial Thesis and state-only `CANDIDATE` handling.
The Story is ready for delivery preparation after the human confirms the
Story-file allow-list and authorizes dedicated branch creation.
