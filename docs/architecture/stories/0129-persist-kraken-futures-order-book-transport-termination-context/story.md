# Story 0129 - Persist Kraken Futures Order Book Transport Termination Context

## Metadata

**ID:** `0129`
**Status:** Implementation completed - awaiting Code Review
**Related Stories:** `0112A`, `0122`, `0124`
**Related ADRs:** `ADR-063`

## Goal

Persist enough Kraken Futures WebSocket termination context to distinguish, when
the underlying client exposes the information:

- remote close;
- network or transport failure;
- local disposal;
- application shutdown;
- explicit unsubscribe;
- unknown transport completion.

The capability is for observability and attribution. It must not change strict
historical replay trust semantics or attempt to keep uncertain sequences alive.

## Context

Recent runtime observations show repeated Kraken Futures WebSocket terminations
with:

```text
STREAM_TERMINATING
signalType=onComplete
exceptionClass=null
rootCauseClass=null
```

The current lifecycle correctly invalidates the affected Order Book sequence,
recovers from a fresh provider snapshot, and creates a new sequence. However,
the persisted invalidation reason is generic and the termination source is not
durably attributable after logs rotate.

The previous investigation found no evidence of provider sequence gaps,
out-of-order deltas, checksum failures, persistence failures, application
restarts, duplicate subscription owners, or a Story 0124 replay defect.

## Problem

The system cannot reliably answer why a WebSocket connection terminated after
runtime logs are unavailable. In particular, `onComplete` alone does not prove
that the remote peer closed the connection.

The missing attribution prevents a safe follow-up decision between provider,
network, transport, and local lifecycle remediation.

## Scope

- Verify the actual WebSocket client lifecycle capabilities.
- Introduce or reuse an explicit bounded termination classification.
- Capture close status/reason when the client exposes them.
- Capture bounded exception and root-cause metadata for error termination.
- Correlate termination context with connection ID and affected sequence ID.
- Capture last trusted provider sequence, local ordinal, and known timestamp when
  already available in memory.
- Preserve connected and terminated timestamps so connection lifetime is
  derivable.
- Distinguish explicit shutdown, unsubscribe, local disposal, and unexpected
  transport completion when local intent is known.
- Persist future termination context durably enough to survive log rotation and
  application restart.
- Improve structured lifecycle logging without logging secrets or payloads.
- Preserve current sequence invalidation and fresh-snapshot recovery behavior.
- Add focused unit, persistence, recovery, and trust-regression tests.
- Add Story 0129 documentation and runtime validation guidance.

## Out of Scope

- Changing sequence invalidation semantics.
- Reusing invalidated Order Book evidence.
- Stitching evidence across an uncertain transport boundary.
- Current live Order Book, REST, or OHLC execution fallbacks.
- Implementing Kraken Futures checksum validation.
- Designing a new heartbeat or ping/pong protocol.
- Optimizing reconnect delays, retry counts, or backoff.
- Forcing reconnects or intentionally terminating a WebSocket for validation.
- Fixing the upstream provider or network cause before it is attributable.
- Introducing a new messaging platform, service, or database.
- Creating a public diagnostics API unless an existing internal diagnostics
  contract naturally supports it.
- Running the PT1H Economic Machinery Baseline.
- Fabricating attribution for historical sequences.

## Termination Classification

Use existing canonical concepts where available. Otherwise use a bounded
classification equivalent to:

```text
REMOTE_CLOSE
NETWORK_FAILURE
LOCAL_DISPOSE
APPLICATION_SHUTDOWN
EXPLICIT_UNSUBSCRIBE
UNKNOWN_TRANSPORT_COMPLETION
```

An `onComplete` signal without reliable close metadata must remain
`UNKNOWN_TRANSPORT_COMPLETION` or an equivalent unknown value. It must not be
classified as `REMOTE_CLOSE` by inference.

Keep separate:

```text
sequence invalidation reason
transport termination classification
```

For example, a sequence may have:

```text
invalidation reason = TRANSPORT_TERMINATION
termination classification = UNKNOWN_TRANSPORT_COMPLETION
```

Define and test precedence so a later generic lifecycle callback cannot replace
a more specific known cause.

## Persistence Requirements

Evaluate the existing sequence schema and diagnostics models before introducing
new persistence. Choose the smallest coherent design.

If a schema change is required:

- use an additive Flyway migration;
- preserve all existing sequence and evidence rows;
- use nullable fields for historical records without context;
- do not fabricate historical attribution;
- do not couple diagnostic persistence failure to trust cleanup or recovery.

Future termination context should correlate, when known:

- classification;
- signal type;
- close code/status;
- close reason;
- exception class;
- root-cause class;
- sanitized exception message when safe and useful;
- connection ID;
- sequence ID;
- market/provider identity;
- connected and terminated timestamps;
- last provider sequence;
- last local ordinal;
- last evidence `knownAt`;
- intentional lifecycle reason;
- recovery correlation only if an existing lightweight concept can be reused.

Do not persist credentials, authorization headers, full request headers, raw
WebSocket payloads, or secrets.

## Trust Invariants

The implementation must preserve:

```text
WebSocket uncertainty
        -> active Order Book sequence invalidated
        -> fresh provider snapshot required for recovery
```

Strict replay must continue to:

- exclude invalidated sequences;
- require trusted evidence with `knownAt <= cutoff`;
- use one valid reconstruction sequence;
- fail closed when reconstruction is unavailable or invalid;
- avoid live, REST, OHLC, future-known, and synthetic fallbacks.

Diagnostic persistence failure must not prevent:

- sequence invalidation;
- in-memory cleanup;
- recovery;
- fresh-snapshot sequence creation.

## Acceptance Criteria

- [ ] The actual WebSocket client termination capabilities are documented in the
      implementation report.
- [ ] A bounded termination classification exists or an equivalent canonical
      concept is reused.
- [ ] `onComplete` without reliable close evidence remains explicitly unknown.
- [ ] Known local shutdown, unsubscribe, and disposal intent is distinguishable
      from unexpected transport completion where the implementation can prove it.
- [ ] Remote close code and reason are captured when the client exposes them and
      remain null/unknown otherwise.
- [ ] Exception and root-cause metadata are bounded and sanitized.
- [ ] Connection ID and affected sequence ID are correlated when available.
- [ ] Last provider sequence, local ordinal, and last known evidence time are
      captured from existing trusted in-memory state when available.
- [ ] Connected and terminated timestamps allow connection lifetime derivation.
- [ ] A physical connection termination produces no contradictory duplicate
      durable termination records.
- [ ] A later generic lifecycle callback cannot overwrite a more specific cause.
- [ ] Diagnostic persistence failure cannot prevent sequence invalidation,
      cleanup, or fresh-snapshot recovery.
- [ ] Sequence invalidation reason and transport termination classification remain
      separate concepts.
- [ ] Existing sequence invalidation semantics are unchanged.
- [ ] Existing recovery semantics are unchanged.
- [ ] Invalidated historical evidence remains non-replayable.
- [ ] Focused tests cover unknown completion, transport errors, intentional
      unsubscribe/shutdown, specificity preservation, persistence round-trip,
      recovery, and trust regression.
- [ ] No secrets or provider payloads are persisted or logged.
- [ ] Story documentation records runtime validation and any unavailable client
      capabilities.

## Validation

- Focused Kraken WebSocket lifecycle tests.
- Order Book evidence capture and sequence invalidation tests.
- Sequence recovery tests proving fresh snapshot and new sequence behavior.
- Persistence tests for termination context round-trip and nullable historical
  compatibility.
- Strict replay regression tests proving invalidated evidence remains excluded.
- Tests proving diagnostic persistence failure does not block cleanup/recovery.
- Relevant Market Data module test and verification suites.
- `git diff --check`.
- Runtime observation of natural termination events only, if one occurs without
  forcing a restart, disconnect, unsubscribe, or reconnect.

If no natural termination occurs after deployment, report:

```text
RUNTIME_TERMINATION_VALIDATION = PENDING_NATURAL_EVENT
```

## Runtime Safety

The implementation must not:

- restart `market-data` automatically;
- force a Kraken WebSocket reconnect;
- alter desired subscriptions;
- modify Order Book evidence or sequence state outside normal lifecycle behavior;
- run migrations destructively;
- execute economic replay or the PT1H baseline.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] WebSocket client capabilities verified.
- [ ] Termination context implementation completed within scope.
- [ ] Additive persistence migration completed if required.
- [ ] Relevant tests and module validation pass.
- [ ] Trust and recovery semantics are unchanged and verified.
- [ ] Documentation reconciliation completed.
- [ ] Runtime validation completed or explicitly marked pending a natural event.
- [ ] Code Review completed and approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.
