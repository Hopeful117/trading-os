# Implementation Plan - Story 0129

## Approval

This plan requires explicit human approval before production code changes begin.

## Overview

Add durable, bounded Kraken WebSocket termination diagnostics inside
`market-data`. The implementation will preserve the existing sequence
invalidation, bounded recovery, fresh-snapshot, replay, and trust behavior.

The design uses a separate diagnostics record rather than changing canonical
Order Book evidence or sequence eligibility. One physical connection termination
produces one termination record. A separate correlation table associates that
record with every active Order Book sequence observed immediately before cleanup.

## Verified Client Capability

Spring WebFlux `WebSocketSession` exposes `closeStatus()` as
`Mono<CloseStatus>`. It emits a close status when available and completes empty
when the session ended without one. The provider will await/capture this value
before its existing terminal cleanup callback completes. An empty close status
does not prove a remote close.

The implementation will not infer provider or network attribution from Reactor
`ON_COMPLETE` alone.

## Classification

Add a bounded enum with these values:

```text
REMOTE_CLOSE
NETWORK_FAILURE
LOCAL_DISPOSE
APPLICATION_SHUTDOWN
EXPLICIT_UNSUBSCRIBE
UNKNOWN_TRANSPORT_COMPLETION
```

Classification precedence:

1. explicit application shutdown intent;
2. explicit unsubscribe intent;
3. local disposal intent;
4. a reliable close/error signal that supports remote or network attribution;
5. unknown transport completion.

`ON_COMPLETE` without close metadata and without local intent is always
`UNKNOWN_TRANSPORT_COMPLETION`.

`SignalType`, termination classification, and sequence invalidation reason remain
separate fields.

## Durable Model

Add an additive Flyway migration with:

```text
kraken_stream_termination
    termination_id UUID primary key
    connection_id UUID not null unique
    product VARCHAR(16) not null
    classification VARCHAR(40) not null
    signal_type VARCHAR(32) not null
    close_code INTEGER nullable
    close_reason VARCHAR(256) nullable
    exception_class VARCHAR(255) nullable
    exception_message VARCHAR(256) nullable
    root_cause_class VARCHAR(255) nullable
    root_cause_message VARCHAR(256) nullable
    connected_at TIMESTAMP WITH TIME ZONE nullable
    terminated_at TIMESTAMP WITH TIME ZONE not null
    intentional_reason VARCHAR(64) nullable
    created_at TIMESTAMP WITH TIME ZONE not null
```

```text
kraken_stream_termination_sequence
    termination_id UUID not null
    sequence_id UUID not null
    market_id UUID not null
    depth INTEGER not null
    provider_sequence BIGINT nullable
    local_ordinal BIGINT not null
    last_occurred_at TIMESTAMP WITH TIME ZONE nullable
    last_known_at TIMESTAMP WITH TIME ZONE nullable
    primary key (termination_id, sequence_id)
```

The correlation table references the termination and existing sequence/market
rows. Historical records are unaffected because the tables are additive and no
historical attribution is fabricated.

The repository operation is idempotent by `connection_id`. A diagnostic write is
best effort and isolated from the transaction that invalidates sequences or
creates recovery state.

## Runtime Changes

### `KrakenDataStreamProvider`

- Track connection start time alongside the existing connection ID.
- Track explicit local lifecycle intent per product with a bounded enum/value.
- Capture `WebSocketSession.closeStatus()` while the session handler is still
  completing.
- Build one immutable termination context in the existing `doFinally` path.
- Persist termination context before or alongside logging, but never allow a
  persistence exception to interrupt `clearRealtimeStates`, cleanup, or
  `scheduleRecovery`.
- Preserve the existing termination-before-cleanup ordering.
- Clear connection metadata after the termination record has been attempted.
- Keep subscription and recovery behavior unchanged.

`disconnect()` will record application-shutdown intent before closing both
sessions. Existing unsubscribe behavior will not close a physical connection;
its explicit classification is supported for any future/local close path where
that intent is actually known.

### `KrakenStreamLifecycleDiagnostics`

- Add the classification and close metadata to structured lifecycle logging.
- Keep existing bounded exception sanitization.
- Add a small package-visible classification/context factory to make precedence
  deterministic and directly testable.
- Keep sequence diagnostics captured before cleanup.

### New diagnostics types/repository

Add a small provider-neutral model for the termination context and a JDBC
repository under the existing `market-data` service boundary. The model must not
contain raw Kraken payloads, credentials, headers, or unbounded strings.

## Failure Semantics

If termination diagnostics cannot be persisted:

- emit a bounded error log;
- continue the existing sequence invalidation;
- continue live-state cleanup;
- continue the existing bounded recovery path;
- do not alter `durabilityCompromised` or canonical evidence state solely because
  diagnostics failed.

If close metadata is unavailable, persist nullable close fields and
`UNKNOWN_TRANSPORT_COMPLETION` where no stronger classification is proven.

## Files to Modify

- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenDataStreamProvider.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamLifecycleDiagnostics.java`
- `market-data/src/test/java/com/hope/trading/market_data/kraken/websocket/KrakenDataStreamProviderTest.java`
- `market-data/src/test/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamLifecycleDiagnosticsTest.java`

## Files to Create

- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamTerminationClassification.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamTerminationContext.java`
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamTerminationRepository.java`
- `market-data/src/main/resources/db/migration/V8__kraken_stream_termination_context.sql`
- focused repository/context tests under the existing Market Data test packages.

No changes are planned for `OrderBookEvidenceCaptureService`,
`OrderBookEvidenceRepository`, `OrderBookReplayService`, or canonical evidence
tables unless implementation proves that a read-only correlation query cannot
reuse their existing diagnostic data. The trust boundary remains unchanged.

## Test Plan

### Classification and diagnostics

- exception-free `ON_COMPLETE` becomes unknown;
- error captures bounded exception and root-cause metadata;
- close status captures code and bounded reason;
- explicit shutdown/disposal precedence wins over generic completion;
- a more specific context cannot be overwritten by a generic final callback;
- messages are sanitized and bounded.

### Provider lifecycle

- termination is persisted before cleanup completes;
- active sequence diagnostics are correlated before in-memory state is cleared;
- one connection termination creates one durable termination record;
- existing invalidation and Futures recovery ordering remains unchanged;
- diagnostic repository failure does not block cleanup or recovery;
- normal disconnect does not create an unexpected classification.

### Persistence

- termination context round-trip works with nullable close/error fields;
- sequence correlation round-trip preserves provider sequence, local ordinal, and
  timestamps;
- duplicate connection termination is idempotent;
- existing Order Book persistence and replay tests remain unchanged and passing.

## Validation Commands

```text
./mvnw -q -Dtest=KrakenDataStreamProviderTest,KrakenStreamLifecycleDiagnosticsTest,OrderBookEvidenceCaptureServiceTest,OrderBookEvidencePersistenceTest test
./mvnw -q verify
git diff --check
```

No runtime restart, forced disconnect, reconnect, subscription mutation,
database mutation, external Kraken call, or PT1H baseline is authorized during
implementation or validation. Natural runtime termination validation remains
optional and is reported as `PENDING_NATURAL_EVENT` if unavailable.

## Risks

- **False remote attribution:** mitigated by requiring close metadata or a
  reliable error contract and preserving unknown completion otherwise.
- **Cleanup regression:** mitigated by best-effort diagnostic persistence and
  tests that verify invalidation/recovery continue after repository failure.
- **Duplicate records:** mitigated by unique connection ID and idempotent insert.
- **High-cardinality or sensitive data:** mitigated by bounded IDs, fields, and
  sanitized messages; raw payloads and secrets are excluded.
- **Schema coupling:** mitigated by separate additive diagnostics tables and no
  changes to replay eligibility.

## Approval Required

Implementation Plan completed. Human approval is required before code changes.
