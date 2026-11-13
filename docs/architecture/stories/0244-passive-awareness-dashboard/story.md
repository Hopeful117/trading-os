# Story 0244 - Add the Passive Awareness Dashboard

## Metadata

**ID:** `0244`

**Title:** Add the Passive Awareness Dashboard

**Status:** Proposed - implementation not authorized

**Priority:** P1 - Trading Intelligence

**Epic:** `0238`

**Related ADRs:** `ADR-033`, `ADR-076`, `ADR-087`, `ADR-102`

**Predecessors:** `0242`, `0243`

## Goal

Expose a minimal reactive Angular dashboard feed for passive awareness and an
explicit command to begin active investigation.

## Product Value

The trader can understand monitored markets, evidence freshness and candidate
rationale without confusing passive discovery with an active or executable
trade decision.

## Existing Components Reused

- Existing Opportunities page and scan panel.
- Angular reactive services and async-pipe patterns.
- Existing ActiveScan service and account selection.
- Existing opportunity and Market Thesis models/components.

## Scope

- Add a passive awareness feed to the existing dashboard/opportunity surface.
- Show market/provider/product identity, origin, rationale and evidence status.
- Show observation time, cutoff, freshness and explicit missing-data states.
- Distinguish observation, passive candidate and active opportunity labels.
- Provide a user-triggered transition to existing ActiveScan.
- Require account selection for the active action.
- Refresh the feed through bounded polling or existing read refresh patterns.
- Preserve responsive dark-slate dashboard conventions.

## Out of Scope

- Push notifications.
- Automatic active escalation.
- New frontend framework or state-management library.
- AI-generated explanations.
- Trade execution controls.
- Account-independent data hidden behind account-scoped authorization.

## Acceptance Criteria

- [ ] Dashboard displays monitored markets and their current awareness state.
- [ ] Passive candidates show why they were selected and which evidence supports them.
- [ ] Stale, missing and contradictory evidence are not hidden.
- [ ] The dashboard distinguishes passive candidate lifecycle state from
      account-actionable active opportunity state.
- [ ] User can select a candidate and start the existing ActiveScan flow.
- [ ] Account selection is required before active investigation.
- [ ] Passive and active states are visually and semantically distinct.
- [ ] No automatic scan, order, risk or broker operation occurs from the feed.
- [ ] Angular tests cover loading, empty, stale, unavailable and positive states.
- [ ] Existing active scan tests and production build remain green.

## Dependencies

Depends on Stories 0242 and 0243. This is the final V1 product slice.

## Migration and Safety

Reuse existing routes and components where practical. No backend authority is
duplicated in Angular. No credentials, provider payloads or trading controls are
exposed.

## Validation

- Angular unit tests and production build.
- Reactive loading/error/empty-state tests.
- Existing active scan regression tests.
- Fixture-based end-to-end demonstration with AI and broker writes disabled.
