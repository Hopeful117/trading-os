# Story 0132 - Refactor Trading OS Sidebar and Application Navigation

## Metadata

**ID:** `0132`

**Title:** Refactor Trading OS Sidebar and Application Navigation

**Status:** Draft

**Related Stories:** `0102`, `0104`, `0131`

**Related ADRs:** None identified. Navigation grouping alone does not justify a
new architectural decision unless repository analysis discovers a cross-cutting
constraint.

---

## Goal

Refactor the Trading OS Angular sidebar into a clear, domain-oriented primary
navigation while preserving the existing dark slate visual identity, route
contracts, authentication semantics, and feature behavior.

The resulting navigation must make the product's major functional areas easy to
scan and remain maintainable as future capabilities are added.

---

## Context

Trading OS now contains first-class overview, market intelligence, trading,
account, risk, and research surfaces. The current sidebar is a flat list of
links hardcoded directly in the sidebar template. Research was recently added as
the authenticated `/research` route and must remain directly reachable.

The current application has separate Navbar, Sidebar, and Shell components. The
Navbar currently contains a small set of authenticated feature links in addition
to the Sidebar. The existing layout and responsive behavior must be inspected
before implementation rather than assumed.

The repository remains authoritative for the exact current routes, styling,
tests, and runtime behavior.

---

## Problem

The flat sidebar no longer communicates the product structure clearly. Market
intelligence, trading operations, account context, risk context, research, and
general overview capabilities are mixed together as an arbitrary list.

Active-route behavior is also inconsistent, because navigation items do not
uniformly expose Router-based active state. The sidebar is hidden on narrow
screens without an equivalent primary navigation interaction, and the static
`AI Engine - Online` footer may be misleading if it is not backed by runtime
state.

---

## Scope

- Inspect the current Sidebar, Navbar, Shell, route table, styles, and focused tests.
- Introduce a small typed static navigation model containing sections and items.
- Render the sidebar from the navigation model instead of duplicating link structure in HTML.
- Organize the primary navigation around user-facing domains, starting from:
  - Overview: Dashboard.
  - Market Intelligence: Markets, Economic Context, Financial News, Research.
  - Trading: Opportunities, Positions, Trade History.
  - Account & Risk: Accounts, Challenges.
- Preserve the exact existing public routes unless repository analysis identifies a strong reason not to.
- Keep Research as a first-class destination under Market Intelligence.
- Apply consistent Angular Router active-state behavior to every navigable sidebar item.
- Ensure nested future routes can keep their parent navigation item active where appropriate.
- Define a clear responsibility boundary between Navbar, Sidebar, and Shell without a major Navbar redesign.
- Inspect existing icon conventions and reuse them if present; otherwise keep navigation text-only.
- Preserve the existing visual language: dark slate backgrounds, restrained blue accents, rounded items, subtle borders/shadows, and responsive typography.
- Review the sidebar width and label readability without substantially increasing workspace displacement.
- Decide whether the static AI Engine footer is truthful, should be explicitly non-authoritative, or should be removed.
- Inspect narrow viewport behavior and implement only the smallest robust improvement if the current layout creates a clear usability problem.
- Do not introduce collapsible desktop navigation or collapsible sections unless repository analysis demonstrates a clear benefit without reducing discoverability.
- Add focused Angular tests for sections, destinations, route targets, active state, Research placement, and preservation of major destinations.

---

## Out of Scope

- Research feature redesign.
- Dashboard redesign.
- Decision Workspace redesign.
- Backend or Gateway modifications.
- Authentication, authorization, role, or permission changes.
- User-customizable navigation, drag-and-drop ordering, or persisted preferences.
- NgRx, backend-driven navigation, plugin systems, or configuration-server navigation.
- A full design-system creation or unrelated frontend cleanup.
- A major Navbar redesign.
- Renaming or migrating existing public Angular routes.
- One Angular component per navigation link.
- Emoji or a large icon/UI framework introduced only for navigation.
- AI Engine health monitoring.
- Section collapse or desktop collapse implemented without demonstrated UX value.

---

## Acceptance Criteria

- [ ] The Sidebar presents a clear hierarchy for Overview, Market Intelligence, Trading, and Account & Risk.
- [ ] Dashboard is directly reachable under Overview.
- [ ] Markets, Economic Context, Financial News, and Research are grouped under Market Intelligence.
- [ ] Research remains directly reachable at `/research` and remains protected by the existing authentication guard.
- [ ] Opportunities, Positions, and Trade History remain directly reachable under Trading.
- [ ] Accounts and Challenges remain directly reachable under Account & Risk.
- [ ] Existing major routes remain unchanged unless a documented repository contradiction requires otherwise:
  `/dashboard`, `/accounts`, `/challenges`, `/markets`, `/economic-context`,
  `/financial-news`, `/research`, `/opportunities`, `/positions`, and `/analytics`.
- [ ] Sidebar navigation is rendered from a small typed static model rather than repeated structural markup.
- [ ] Every navigable sidebar item uses Angular Router active-state facilities.
- [ ] Active matching supports future nested routes for the corresponding destination.
- [ ] Active state is not communicated by color alone and remains visible with keyboard focus.
- [ ] The Navbar does not duplicate the complete primary navigation responsibility of the Sidebar.
- [ ] Sidebar and Shell use semantic navigation structure and retain visible keyboard focus states.
- [ ] Sidebar labels remain readable at the supported desktop width without materially reducing workspace priority.
- [ ] Narrow viewport behavior does not catastrophically hide all primary navigation without an understandable alternative, or the decision to defer a mobile interaction is documented with evidence.
- [ ] The AI Engine footer is either backed by actual state, clearly marked as non-authoritative, or removed; no health-monitoring feature is introduced.
- [ ] No icon dependency or large UI framework is introduced solely for this Story.
- [ ] Focused Angular tests cover navigation sections, destinations, route targets, active navigation behavior, Research presence, and no accidental disappearance of major destinations.
- [ ] Existing frontend tests continue to pass.
- [ ] The Angular production build passes.
- [ ] Runtime or visual validation confirms hierarchy, active state, width, footer behavior, and narrow viewport behavior where the environment supports it.
- [ ] `git diff --check` passes and no unrelated or protected files are included.

---

## Constraints

- Preserve existing authenticated route protection and do not broaden feature access.
- Preserve existing public route names and feature behavior.
- Keep the navigation model static, typed, deterministic, and local to the frontend.
- Reuse existing Angular Router facilities and established Observable/template conventions where needed.
- Do not introduce unnecessary RxJS state or a new state-management framework.
- Preserve the existing Trading OS visual identity rather than redesigning the application.
- Keep the implementation small enough to remain understandable without over-componentization.
- Preserve unrelated working-tree changes and the protected functional audit file.
- Do not modify backend services or security configuration.
- Do not commit, push, or merge without explicit human authorization.

---

## Relevant ADRs

None identified at Story creation. Review existing frontend and product architecture documentation during Repository Analysis and stop if a relevant accepted ADR creates a material conflict.

---

## Relevant Modules

- `trading-os-web`

Primary areas:

- `trading-os-web/src/app/layout/sidebar`
- `trading-os-web/src/app/layout/navbar`
- `trading-os-web/src/app/layout/shell`
- `trading-os-web/src/app/app.routes.ts`
- `trading-os-web/src/app/features`

---

## Validation

- Inspect current branch, HEAD, working-tree state, recent navigation changes, and Research Overview merge before implementation.
- Run focused Sidebar, Navbar, Shell, route, and affected feature tests.
- Run the complete Angular test suite with the repository-standard command.
- Run the Angular production build.
- Run formatting and `git diff --check` where configured/applicable.
- Validate all existing major destinations remain present and correctly targeted.
- Validate `/research` remains authenticated and directly reachable.
- Perform runtime or browser validation when the local environment supports it, including desktop and narrow viewport inspection.
- Inspect the final diff for unrelated files, generated artifacts, secrets, and protected-file changes.

---

## Definition of Done

- [ ] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [ ] Implementation completed
- [ ] Relevant validation executed
- [ ] Runtime/visual validation completed where available
- [ ] Diff reviewed in IntelliJ
- [ ] Code Review completed
- [ ] Engineering Report completed
- [ ] Human commit created
