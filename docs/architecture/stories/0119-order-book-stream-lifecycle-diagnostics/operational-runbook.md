# Story 0119 - Order Book Stream Lifecycle Diagnostics Runbook

## Purpose

Use this runbook to classify the next Kraken stream termination. This Story adds
diagnostics only; automatic reconnect is intentionally not implemented.

## 1. Check Service Lifecycle Logs

```bash
docker logs --since 30m market-data 2>&1 | rg \
  'STREAM_(CONNECTING|CONNECTED|TERMINATING|CLEANUP_COMPLETED)|SUBSCRIPTION_|ORDER_BOOK_(SNAPSHOT_ACCEPTED|SEQUENCE_STARTED)'
```

Correlate events with the same `connectionId`. The important termination fields are:

- `signalType`: `onError`, `onComplete`, or `cancel`;
- `exceptionClass` and `exceptionMessage`;
- `rootCauseClass` and `rootCauseMessage`;
- `sequenceId`, `lastProviderSequence`, and `lastLocalOrdinal`;
- `lastOccurredAt`, `lastKnownAt`, and `occurredToKnownLag`.

## 2. Inspect the Current Sequence

```bash
docker exec market_data-postgres psql -U postgres -d market_data -P pager=off -c \
  "select sequence_id, market_id, depth, status, invalidated_at, invalidation_reason
     from historical_order_book_sequence
    order by invalidated_at desc nulls last;"
```

The diagnostic event must be read before interpreting the persisted generic
`invalidation_reason`. The database remains the audit source for the final sequence
status; logs provide the missing stream lifecycle context.

## 3. Inspect Recent Evidence Bounds

```bash
docker exec market_data-postgres psql -U postgres -d market_data -P pager=off -c \
  "select sequence_id, evidence_type, sequence_ordinal, provider_sequence,
          occurred_at, known_at, trusted
     from historical_order_book_evidence
    order by known_at desc
    limit 10;"
```

Compare `occurred_at` and `known_at` with `occurredToKnownLag` from
`STREAM_TERMINATING`. Do not infer a backlog cause from lag alone; classify it as an
observation until runtime evidence identifies the responsible boundary.

## 4. Interpret the Signal

- `onError`: inspect the exception and root cause; the existing cleanup still
  invalidates the active sequence.
- `onComplete`: the upstream completed normally from Reactor's perspective; no
  Throwable should be expected.
- `cancel`: determine whether the event coincides with explicit local disposal or
  another known lifecycle action. If not, record the cancellation cause as unknown.

## 5. Boundaries

- No automatic reconnect is expected from Story 0119.
- Do not delete or rewrite invalidated evidence.
- Do not treat a connected WebSocket as evidence that the historical sequence is
  trusted or replayable.
- Do not log credentials, authentication headers, or full provider payloads.
