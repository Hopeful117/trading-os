# Story 0119 - Order Book Stream Lifecycle Diagnostics

## Metadata

**ID:** `0119`

**Title:** Order Book Stream Lifecycle Diagnostics

**Status:** Draft

---

## Goal

Make Kraken Order Book stream termination diagnosable without changing recovery,
trust, replay, or historical evidence semantics.

The next termination must distinguish Reactor completion, error, and cancellation,
preserve the available exception context, and correlate the event with the active
connection, subscription, and Order Book sequence before cleanup invalidates it.

---

## Context

The current Market Data flow invalidates the active Order Book sequence from the
WebSocket lifecycle `doFinally` callback. The previous runtime investigation
confirmed this application-level path but could not determine whether the upstream
stream ended by normal completion, error, or cancellation.

This Story is an observability prerequisite for a later recovery Story. It does not
implement reconnect or resubscription.

---

## Problem

The current lifecycle logs do not preserve the Reactor signal, Throwable/root cause,
connection identity, subscription context, active sequence identity, or last accepted
evidence context before cleanup clears in-memory state. The persisted invalidation
reason is therefore too generic to diagnose the next real termination.

---

## Scope

- Instrument Kraken stream connection lifecycle transitions.
- Instrument bounded Order Book subscription, snapshot, and sequence lifecycle events.
- Capture `ON_ERROR`, `ON_COMPLETE`, and `CANCEL` before cleanup.
- Preserve available Throwable and root-cause metadata without logging secrets or full payloads.
- Correlate termination diagnostics with the active Order Book sequence and last accepted evidence context.
- Add bounded timing/lag diagnostics without per-delta INFO logging.
- Add focused tests for termination signals and cleanup ordering.
- Add a small operational runbook for investigating the next termination.

---

## Out of Scope

- Automatic reconnect, retry, backoff, or resubscription.
- Fresh sequence recovery or sequence-state redesign.
- Checksum implementation or checksum-policy changes.
- Gap-policy changes, database migrations, new infrastructure, or new services.
- Economic research, FeatureSnapshots, execution replay, fee/funding capture, Risk, PAPER, ML, or LLM work.

---

## Acceptance Criteria

- [ ] `ON_ERROR`, `ON_COMPLETE`, and `CANCEL` are distinguishable in diagnostics.
- [ ] Error diagnostics preserve exception and root-cause class/message when available.
- [ ] Termination context is captured before `clearRealtimeStates` removes active state.
- [ ] Connection, subscription, snapshot, and sequence lifecycle events are observable.
- [ ] Active sequence identity and last accepted evidence context are included when available.
- [ ] Timing/lag context is observable without per-delta INFO logging.
- [ ] Existing invalidation, persistence, replay, and trust behavior is unchanged.
- [ ] No reconnect or retry behavior is introduced.
- [ ] Focused tests cover all supported termination signals and cleanup ordering.
- [ ] A repository-specific operational runbook explains how to diagnose the next termination.
- [ ] `git diff --check` and Market Data validation pass.

---

## Constraints

- Preserve Market Data ownership of provider acquisition and historical evidence.
- Respect ADR-063 and existing sequence invalidation semantics.
- Do not add persistence solely for diagnostic connection identity.
- Do not log credentials, authentication headers, or full provider payloads.
- Keep metrics bounded and free of high-cardinality sequence IDs or exception messages.
- Do not commit, push, merge, or modify unrelated worktree changes automatically.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-063.md` - Historical Order Book evidence and replay semantics.
- `docs/architecture/adr/ADR-057.md` - Historical evidence and `knownAt` semantics.

No new ADR is required unless implementation reveals an architectural decision that cannot be resolved within these accepted boundaries.

---

## Relevant Modules

- `market-data`

---

## Validation

- Focused Market Data unit tests for Reactor termination signals and cleanup ordering.
- Existing Order Book acquisition, persistence, gap, and replay regressions.
- Market Data `mvn test` and `mvn verify`.
- Safe runtime startup diagnostics for the affected service when possible.
- `git diff --check`.

---

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Diagnostics implemented within scope.
- [ ] Relevant validation executed.
- [ ] Operational runbook completed.
- [ ] Code Review approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.
