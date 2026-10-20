# Story 0135 - Establish Workstation Presentation Primitives and Compact Context Strip

## Metadata

**ID:** `0135`

**Title:** Establish workstation presentation primitives and compact context strip

**Status:** Review - implementation complete, pending code review

---

## Goal

Establish the smallest coherent workstation presentation vocabulary and use it
to compress the account-scoped Decision Workspace context into a compact,
readable context strip without changing workspace ownership, data semantics, or
the decision pipeline.

---

## Context

Story 0134 established `WorkspaceContextView` and the account-scoped context
surface. The governing design report recommends a staged workstation evolution:
first introduce bounded presentation primitives and improve the context strip;
only later migrate the full Decision Workspace into a panel/grid composition.

The current implementation is Angular-only and already has authoritative
account, market, tradability, freshness, workflow, and Opportunity projections.
The new presentation layer must consume those projections rather than create
new data ownership or lifecycle management.

References:

- `docs/architecture/reports/professional-decision-workspace-density-panel-architecture.md`
- `docs/architecture/reports/trading-os-professional-workspace-ux-investigation.md`
- `docs/architecture/stories/0134-establish-account-scoped-trading-workspace-context/story.md`

---

## Problem

The current context surface is semantically correct but is presented as a set
of oversized cards with inconsistent local status, metric, and disclosure
patterns. This consumes vertical space and does not establish a reusable,
bounded visual vocabulary for the subsequent workspace panel work.

---

## Scope

- Add small workstation-scoped standalone Angular primitives for:
  - panel containers;
  - panel headers;
  - status badges;
  - freshness indicators;
  - metric values;
  - disclosure sections where an existing context disclosure can use it.
- Keep primitive APIs presentation-only and based on content projection or small
  explicit inputs.
- Evolve the existing `WorkspaceContext` presentation into a compact
  `WorkspaceContextStrip` while preserving `WorkspaceContextView` and its
  semantics.
- Use the primitives in the context strip and at least one existing disclosure
  surface or equivalent current workspace presentation.
- Preserve explicit loading, unavailable, error, unknown, tradability,
  freshness, workflow, Opportunity, and next-action states.
- Add scoped workstation tokens and numeric typography only where needed for the
  context strip and primitives.
- Preserve the existing parent-owned stream and HTTP lifecycle.
- Add focused Angular tests for primitive rendering, context states,
  accessibility labels, and unchanged authoritative semantics.

---

## Out of Scope

- Full desktop CSS Grid or panel migration of Decision Workspace.
- Selector rail, decision column, panel relocation, or chart/order-book layout
  redesign.
- Embedded Trade Plan, Risk, execution, position, or monitoring panels.
- New backend endpoints, DTOs, services, or persistence.
- New HTTP requests, WebSocket subscriptions, global stores, or caches.
- Recalculation of eligibility, freshness, Risk, execution authorization, or
  Opportunity validity in Angular.
- New domain state machines or changes to `WorkspaceContextView` semantics.
- Design-system-wide primitives or unrelated feature refactoring.
- Changes to Story 0134 behavior, S1/research surfaces, or protected files.

---

## Acceptance Criteria

- [ ] Story 0134 `WorkspaceContextView` remains the authoritative input and its
      route, account, market, Opportunity, freshness, and stream semantics are
      unchanged.
- [ ] Workstation presentation primitives are standalone, bounded, documented
      by their APIs, and contain no business or data-loading logic.
- [ ] `WorkspaceContext` is presented as a compact responsive context strip
      with clear hierarchy and explicit unavailable/unknown states.
- [ ] Status and freshness dimensions remain visually distinct and do not rely
      on color alone.
- [ ] Numeric and tabular values use stable workstation typography without
      introducing a global typography redesign.
- [ ] Existing disclosure content remains keyboard-accessible and uses the new
      disclosure convention where applicable.
- [ ] No new HTTP requests or WebSocket subscriptions are introduced.
- [ ] Focused Angular tests cover the primitives and all existing context-state
      branches relevant to the strip.
- [ ] Angular CI tests, production build, formatting checks, and `git diff
      --check` pass.
- [ ] Desktop and narrow viewport validation confirms readable density,
      accessible labels/focus, and no excessive vertical growth.
- [ ] No desktop panel/grid migration or unrelated behavior changes are made.

---

## Constraints

- Preserve the account-scoped Decision Workspace as the canonical decision
  surface.
- Preserve parent-owned stream lifecycle and existing shared projections.
- Keep Angular presentational; do not introduce Risk or execution semantics.
- Respect accepted ADRs and Story 0134 authority boundaries.
- Do not introduce unrelated dependencies.
- Do not commit, push, merge, or modify protected unrelated files.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-001.md` - human-controlled trading platform.
- `docs/architecture/adr/ADR-014.md` - deterministic decision pipeline.
- `docs/architecture/adr/ADR-031.md` - planning context versus Risk context.
- `docs/architecture/adr/ADR-047.md` - Trade Plan origin and human authority.

---

## Relevant Stories

- `0134` - Establish the Account-Scoped Trading Workspace Context.

---

## Relevant Modules

- `trading-os-web/src/app/features/decision-workspace`
- `trading-os-web/src/app/shared`

---

## Validation

- Focused `WorkspaceContext`/`WorkspaceContextStrip` and primitive tests.
- Complete Angular `npm run test:ci`.
- Angular production build.
- Repository formatting checks and `git diff --check`.
- Desktop and narrow viewport inspection where the local frontend runtime is
  available.
- Network inspection confirming no additional HTTP or WebSocket activity.

---

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Human approval received for this Story.
- [ ] Implementation completed.
- [ ] Relevant validation executed.
- [ ] Diff reviewed in IntelliJ.
- [ ] Code Review approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.
