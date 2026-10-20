# Story 0137 - Finalize the Professional Decision Workspace

## Metadata

**ID:** `0137`
**Title:** Finalize the Professional Decision Workspace
**Status:** Review

## Goal

Finalize the Decision Workspace as a responsive, accessible, runtime-truthful
professional trading workstation without expanding its product or domain scope.

## Context

Stories 0135 and 0136 established the workstation primitives, context strip, and
desktop panel composition. This Story closes the approved UI chantier through
responsive, accessibility, lifecycle, and runtime validation.

## Scope

- Validate the workspace at desktop, intermediate, and narrow viewports.
- Preserve the semantic heading and DOM order: context, selectors, quote, chart,
  Trend Context, action region, Order Flow, and constraints.
- Keep Trend Context summary-first with accessible, user-controlled evidence
  disclosure.
- Prevent intrinsic panel content from causing horizontal overflow.
- Verify loading, unavailable, stale, and error states remain truthful.
- Verify account-first, market-first, Opportunity-first, navigation, and stream
  teardown behavior.
- Verify HTTP and WebSocket activity remains scoped and non-duplicated.

## Out of Scope

- Backend, Risk, execution, persistence, API, DTO, or service-contract changes.
- New trading capabilities, Decision History, Position Intelligence, scanners,
  AI synthesis, news, cTrader, or prop-firm functionality.
- New global state, stream multiplexing, caching, or unrelated frontend changes.

## Acceptance Criteria

- [ ] Desktop, intermediate, and narrow layouts are usable without catastrophic
      overflow.
- [ ] The active workspace remains concurrently scannable and follows the
      approved semantic order.
- [ ] Trend Context summary is visible while detailed evidence is collapsed by
      default and expands through an accessible disclosure control.
- [ ] Account and market selection remain compact, account-scoped, and truthful.
- [ ] Quote, chart, Trend Context, Order Book, Recent Trades, actions, and
      constraints preserve existing contracts and state semantics.
- [ ] Keyboard focus, headings, landmarks, labels, tables, and disclosures are
      usable and understandable.
- [ ] Stream activation, teardown, route restoration, and action navigation do
      not introduce duplicate or unsafe activity.
- [ ] Angular tests, production build, formatting, and diff checks pass.
- [ ] No backend, Risk, execution, S1, or unrelated files change.

## Constraints

- Preserve existing service ownership and deterministic business authority.
- Keep `DecisionWorkspace` as the orchestration boundary.
- Keep Trend Context analytical and non-authoritative for Risk or execution.
- Keep human approval required for Trade Plan and execution workflows.
- Do not commit, push, merge, or alter user-owned unrelated changes.

## Relevant ADRs

- `docs/architecture/adr/ADR-001.md`
- `docs/architecture/adr/ADR-014.md`
- `docs/architecture/adr/ADR-031.md`
- `docs/architecture/adr/ADR-047.md`

## Relevant Stories

- `0135` - Establish workstation presentation primitives and compact context
  strip.
- `0136` - Recompose Decision Workspace into the Professional Trading
  Workstation.

## Relevant Modules

- `trading-os-web/src/app/features/decision-workspace`
- `trading-os-web/src/app/shared/workstation`
- `trading-os-web/src/app/features/markets`

## Validation

- `npm run test:ci`
- `npm run build`
- Prettier check and `git diff --check`
- Browser validation at approximately 1920px, 1440px, 1024px, and 390px.
- Runtime data and network inspection where the local environment permits.

## Definition of Done

- [ ] Repository Analysis completed.
- [ ] Implementation completed.
- [ ] Relevant validation executed.
- [ ] Documentation reconciliation completed.
- [ ] Independent Code Review approved.
- [ ] Human commit created.
