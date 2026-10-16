# Implementation Plan - Story 0112A

## Status

Pending human approval.

This plan implements the approved Story 0112A and accepted ADR-063. It does not
implement liquidity features or modify Market Intelligence feature projections.

## Implementation Boundary

All production implementation remains inside `market-data`, plus the existing
local configuration surface required to activate the bounded capture. No new
service, broker behavior, public API, Gateway route, frontend behavior, Risk
logic, or Execution behavior is included.

## Production Changes

### 1. Confirm Provider Feed Contract

- Add representative Kraken Futures `PF_XBTUSD` snapshot and update fixtures based
  on the actual feed shape already handled by `KrakenDataStreamProvider`.
- Preserve any provider sequence metadata when present.
- Record the absence of provider sequence metadata explicitly when the feed does
  not provide it; do not synthesize a provider sequence number.
- Confirm checksum input and validation semantics before making evidence trusted.

### 2. Add Provider-Neutral Evidence Domain

- Add immutable domain records for canonical Order Book evidence.
- Represent `SNAPSHOT` and `DELTA` evidence types.
- Include market/provider/provider-symbol provenance, depth, sequence identity,
  local sequence ordering, `occurredAt`, `knownAt`, checksum metadata,
  normalization version, trust state, and content fingerprint.
- Reuse `OrderBookLevel` numeric conventions with validated `BigDecimal` prices
  and quantities.
- Enforce canonical bid and ask ordering before fingerprinting.
- Use `order-book-normalization-v1` as the initial normalization version.
- Keep current derived snapshot metrics out of canonical evidence identity.

### 3. Add PostgreSQL Persistence

- Add a Flyway migration for immutable Order Book evidence and derived checkpoints.
- Add append-only JPA entities and repositories following the existing
  `HistoricalOhlcEvidence` persistence conventions.
- Add unique deterministic fingerprints and conflict-safe insert behavior.
- Store enough provenance to distinguish provider snapshots from Trading OS
  checkpoints.
- Add indexes for market/depth/sequence/order and cutoff replay selection.
- Do not mutate or reuse the historical OHLC schema.

### 4. Add Capture Trust Boundary

- Introduce a capture service between provider mapping and the operational event
  publisher.
- Assign `knownAt` at trusted acquisition acceptance time.
- Validate normalized level values, depth, timestamps, checksums, and sequence
  continuity where provider metadata permits it.
- Persist accepted evidence before exposing it as eligible research evidence.
- Keep invalid checksum, malformed, out-of-order, or persistence-failed messages
  out of trusted replay.
- Preserve bounded diagnostic information without making raw provider payloads part
  of the canonical V1 evidence contract.

### 5. Add Sequence Lifecycle and Recovery

- Create a new `sequenceId` from each trusted full provider snapshot.
- Assign a monotonic local `sequenceOrdinal` to accepted evidence.
- Invalidate the current sequence on reconnect, restart, checksum failure,
  detected gap, invalid snapshot, or persistence failure.
- Clear untrusted in-memory state and require a new trusted snapshot before
  publishing/replaying trusted state.
- Do not merge deltas across invalidated sequences.
- Preserve the existing live subscription behavior while adding explicit trust and
  persistence state around it.

### 6. Add Checkpoints and Replay

- Persist a complete derived checkpoint at most every five minutes for an active
  sequence, subject to the bounded operational design in ADR-063.
- Mark checkpoints as derived and retain their source sequence and evidence range.
- Add a replay service/port that selects one valid sequence and a snapshot or
  checkpoint eligible at `T0`.
- Apply only same-sequence deltas with `knownAt <= T0`.
- Return explicit unavailable/invalid results for missing snapshots, invalidated
  sequences, gaps, insufficient evidence, or stale/untrusted state.
- Keep consumer freshness policy separate from evidence identity and replay truth.

### 7. Add Bounded Capture Configuration and Observability

- Add disabled-by-default configuration for the bounded market and depth policy
  using existing Market Data configuration conventions.
- Configure local activation only for:
  - Market ID `e23ba62a-995f-4755-8deb-11a71ab3788b`;
  - provider symbol `PF_XBTUSD`;
  - depth `25`.
- Do not activate the full market catalogue.
- Expose or log the minimum operational signals from ADR-063:
  - last valid snapshot time;
  - last `knownAt`;
  - active sequence;
  - reconnects;
  - checksum failures;
  - sequence resets;
  - persisted evidence/checkpoint counts;
  - snapshot age;
  - capture and persistence failures.

## Expected Files and Surfaces

Expected additions or changes are limited to the following areas; exact names are
to be selected during implementation without changing the approved semantics:

- `market-data/src/main/java/.../model/` for evidence and sequence domain types.
- `market-data/src/main/java/.../adapter/persistence/` for immutable entities,
  repositories, and inserts.
- `market-data/src/main/java/.../service/` for capture, trust, checkpoint, and
  replay orchestration.
- `market-data/src/main/java/.../kraken/` for provider metadata/checksum mapping
  only.
- `market-data/src/main/resources/db/migration/` for the PostgreSQL migration.
- `market-data/src/main/resources/application.properties` for safe defaults.
- `docker-compose.yml` and ignored local `.env` only if the existing local
  configuration convention requires mappings for the bounded opt-in capture.
- `market-data/src/test/java/...` for focused unit and persistence tests.

No `market-intelligence` production file should change unless a narrowly scoped
replay contract adapter is proven necessary by the implementation and remains
within ADR-063. Liquidity interpretation remains Story 0112B.

## Persistence Semantics

- Canonical snapshots and deltas are immutable and retained in V1.
- Checkpoints are derived, immutable, and never treated as provider snapshots.
- Duplicate semantic evidence is idempotent by fingerprint.
- Conflicting semantic evidence is preserved as a distinct immutable record or
  rejected explicitly; it is never silently overwritten.
- PostgreSQL remains the canonical store.
- No compaction, deletion, archival workflow, or new streaming infrastructure is
  introduced in this Story.

## Replay Semantics

The replay contract must prove:

```text
one valid sequence
+ trusted starting snapshot/checkpoint
+ same-sequence eligible deltas
+ knownAt <= T0
=> reconstructed Order Book state at T0
```

Failure must be explicit and fail closed. Current provider state, evidence after
`T0`, evidence from a prior invalid sequence, and interpolated gaps are forbidden.

## Test Plan

### Domain and Normalization Tests

- Validate required identity, timestamps, depth, levels, and checksum metadata.
- Validate bid/ask canonical ordering and `BigDecimal` values.
- Validate `occurredAt`/`knownAt` semantics.
- Validate deterministic fingerprints and normalization versioning.

### Provider Adapter Tests

- Map Kraken Futures snapshots and updates to provider-neutral evidence.
- Preserve `PF_XBTUSD`, depth, timestamps, and checksum metadata.
- Validate the actual checksum algorithm and reject invalid content.
- Cover missing or unavailable provider sequence metadata explicitly.

### Sequence and Recovery Tests

- Start a sequence only from a trusted full snapshot.
- Apply valid deltas in order.
- Reset on reconnect, checksum failure, malformed snapshot, sequence gap, and
  persistence failure.
- Prove that deltas from invalidated sequences are not applied.
- Prove restart requires a fresh snapshot.

### Persistence and Replay Tests

- Verify append-only inserts and fingerprint idempotency in PostgreSQL/H2 where
  supported by existing conventions.
- Verify checkpoint provenance and distinction from provider snapshots.
- Replay at exact cutoff boundaries.
- Exclude evidence with `knownAt > T0`.
- Reject cross-sequence replay and return explicit unavailable/invalid results.

### Runtime Validation

- Build and run the bounded Market Data service.
- Activate only `PF_XBTUSD` at depth 25 in the local runtime.
- Confirm evidence and checkpoint persistence growth.
- Observe reconnect/checksum/reset metrics.
- Restart the service and confirm a new trusted sequence is established.
- Run `git diff --check`.

## Quality Validation

Applicable validation:

- Market Data targeted Maven tests.
- Market Data full Maven verification, including the existing JaCoCo 80% line
  coverage threshold.
- PostgreSQL migration/integration validation.
- `git diff --check`.
- SonarQube validation if configured for the affected Market Data project.

No full repository Maven build, frontend build, or unrelated service validation is
required unless implementation changes expand beyond the approved boundary.

## Explicit Non-Goals

- No liquidity formulas or derived Order Book features.
- No `FeatureSnapshot` or projection-version changes.
- No raw provider payload research contract.
- No catalog-wide activation.
- No historical provider backfill.
- No new infrastructure platform or service.
- No commit, push, merge, or branch cleanup by the coding agent.

## Implementation Risks

- Provider checksum and sequence semantics may require a bounded adapter decision
  during implementation. Any material contradiction with ADR-063 must stop and be
  reported rather than silently resolved.
- Event persistence rate may exceed the bounded PostgreSQL pilot assumptions. The
  implementation must measure it and stop scope expansion if operational limits
  are reached.
- The current live publisher is best-effort; it must not be used as the only
  persistence boundary.
- Application startup and Eureka registration must not be used to claim research
  evidence is available before the capture path is verified.

## Approval Gate

This plan is complete and awaits explicit human approval before implementation.
