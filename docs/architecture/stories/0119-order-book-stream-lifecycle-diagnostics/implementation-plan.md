# Implementation Plan - Story 0119

## Status

Pending human approval.

This plan implements the approved Repository Analysis for Story 0119. It is
limited to bounded lifecycle diagnostics and tests. It does not implement
reconnect, retry, sequence recovery, checksum validation, persistence changes, or
replay changes.

## Implementation Boundary

All production changes remain inside `market-data`. The diagnostic context is
in-memory and log-oriented. No database schema, historical evidence record,
sequence identity, fingerprint, or public API changes are required.

## Production Changes

### 1. Connection Diagnostic Context

- Add a small immutable, diagnostic-only connection context owned by the Kraken
  streaming adapter.
- Generate a connection/session identifier when a connection attempt starts.
- Track provider, product/stream, and bounded subscription context where known.
- Keep the identifier out of domain evidence, fingerprints, metrics labels, and
  persistence.

### 2. Reactor Lifecycle Instrumentation

- Log a bounded `STREAM_CONNECTING` event before the connector call.
- Log `STREAM_CONNECTED` after the WebSocket session is established.
- Preserve the existing `doOnError` behavior while logging exception class,
  message, root-cause class, and root-cause message.
- Capture `SignalType` in `doFinally` and emit `STREAM_TERMINATING` before
  `clearRealtimeStates`.
- Emit `STREAM_CLEANUP_COMPLETED` after the existing cleanup operations.
- Do not add `retryWhen`, `repeat`, reconnect, resubscription, or backoff.

### 3. Subscription and Order Book Milestones

- Log bounded `SUBSCRIPTION_REQUESTED` metadata for provider, symbol, channel, and
  depth.
- Preserve existing control-message logging and report explicitly whether a
  provider acknowledgement can be correlated.
- Log the first accepted Order Book snapshot without logging levels or full
  payloads.
- Log sequence start and invalidation context through a read-only diagnostic view
  of the existing active sequence state.
- Optionally log the first accepted delta at DEBUG only if this is available
  without adding per-delta production logging.

### 4. Sequence and Timing Context

- Expose a bounded diagnostic snapshot from `OrderBookEvidenceCaptureService` for
  the active `OrderBookKey`.
- Include sequence ID, last provider sequence, last local ordinal, last
  `occurredAt`, and last `knownAt` when available.
- Update this context only as part of existing snapshot/update acceptance; do not
  change acceptance, persistence, or trust decisions.
- Include the observed `occurredAt`/`knownAt` lag in termination diagnostics when
  both values exist.
- Keep missing context explicitly absent rather than inferred.

### 5. Invalidation Correlation

- Add a termination-category reason at the logging boundary for stream cleanup,
  such as `STREAM_ON_ERROR`, `STREAM_ON_COMPLETE`, or `STREAM_CANCEL`.
- Preserve the existing persisted invalidation reason and schema.
- Do not change sequence lifecycle semantics or resurrect an invalidated sequence.

## Expected Files and Surfaces

Expected changes are limited to:

- `market-data/src/main/java/.../kraken/websocket/KrakenDataStreamProvider.java`;
- `market-data/src/main/java/.../service/OrderBookEvidenceCaptureService.java`;
- a small diagnostic context/value type under the existing `model` or `service`
  package if required;
- focused tests under `market-data/src/test/java/...`;
- `docs/architecture/stories/0119-order-book-stream-lifecycle-diagnostics/`
  for the implementation report and runbook.

No new logging framework, metrics stack, migration, service, or Market Intelligence
file is expected.

## Test Plan

### Reactor Lifecycle

- Complete the inbound stream normally and verify `ON_COMPLETE` is observable
  without a fabricated Throwable.
- Fail the inbound stream and verify `ON_ERROR`, exception, and root-cause context
  are observable.
- Cancel the stream where the existing connector test seam permits it and verify
  `CANCEL` is observable.

### Cleanup Ordering

- Verify termination diagnostics capture the active sequence context before
  `clearRealtimeStates` invalidates and removes it.
- Verify termination without an active sequence remains safe.

### Regression

- Preserve existing snapshot, delta, gap, persistence, and replay behavior.
- Verify no retry, repeat, reconnect, or resubscription operator is introduced.
- Run the focused Kraken/provider and Order Book capture tests.

## Runtime Validation

- Run Market Data focused tests, then `./mvnw test` and `./mvnw verify`.
- Rebuild/restart only `market-data` if validation requires it and record that
  action.
- Verify startup/connection/subscription/snapshot/sequence diagnostics if an
  external subscription is naturally initiated.
- Do not force a provider failure. If no natural termination occurs, report
  `NO_NATURAL_TERMINATION_OBSERVED`.

## Documentation Reconciliation

- Add an implementation report after implementation.
- Add a small runbook section in the Story directory with the actual Docker log
  and PostgreSQL inspection commands, without credentials.
- Record the checksum limitation: non-null checksum values remain rejected and
  null checksum values remain accepted without algorithm verification.
- Record whether a natural termination was observed separately from test-proven
  lifecycle behavior.

## Quality Validation

- Applicable: focused Market Data tests, full Market Data Maven verification,
  JaCoCo threshold, `git diff --check`, and SonarQube if configured.
- Not applicable unless scope expands: Market Intelligence, Trading Core,
  frontend, Gateway, Risk, Broker, and full repository builds.

## Explicit Non-Goals

- No automatic reconnect or recovery.
- No retry or resubscription behavior.
- No checksum implementation or trust-policy change.
- No database migration or diagnostic persistence.
- No sequence/replay redesign.
- No economic research or transaction-cost changes.

## Approval Gate

This plan is complete and awaits explicit human approval before implementation.
