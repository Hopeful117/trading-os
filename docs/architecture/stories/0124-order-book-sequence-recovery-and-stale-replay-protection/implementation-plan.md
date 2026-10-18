# Implementation Plan

## Overview

Implement bounded automatic recovery inside `market-data` and make strict
Order Book replay select only the sequence that is valid at the requested
cutoff. The implementation will reuse the current subscription maps,
`KrakenDataStreamProvider`, capture service, repository, and lifecycle
diagnostics.

The core changes are:

1. On unexpected terminal WebSocket signals, invalidate the affected runtime
   sequence, clear live state, and schedule a bounded recovery attempt while
   active subscriptions still exist.
2. On recovery, create a fresh provider connection, replay the active
   subscriptions, and accept a fresh provider snapshot under a new sequence.
3. Make snapshot acceptance atomically supersede persisted active sequences for
   the same market/depth scope.
4. Make strict replay choose the sequence with the latest evidence known at the
   cutoff and return no trusted evidence when that sequence is invalidated or
   when the cutoff contains ambiguous active sequences.

This preserves append-only evidence, provider-neutral ownership, current
checksum conservatism, and the public subscription API.

## Planned Changes

### 1. Add bounded recovery state to `KrakenDataStreamProvider`

- Track whether normal shutdown is in progress so an intentional disconnect
  cannot trigger recovery.
- Track one recovery episode per provider product and prevent concurrent recovery
  attempts for the same product.
- Preserve active subscription registrations across unexpected termination so the
  existing `replayActiveSubscriptions` path remains the source of truth.
- Schedule bounded recovery attempts with explicit backoff and a maximum number
  of consecutive attempts per episode.
- Reset recovery state only after a recovered connection has successfully
  re-established the configured subscription and accepted a fresh snapshot.
- Leave the service in a truthful degraded state after the attempt budget is
  exhausted. Do not fabricate a session, sequence, snapshot, or evidence.

The recovery must not use an unbounded `retryWhen`/`repeat` loop and must not
interfere with explicit unsubscribe or normal application shutdown.

### 2. Preserve the existing fresh-snapshot boundary

- Keep `OrderBookEvidenceCaptureService` as the owner of runtime sequence
  identity.
- Continue rejecting deltas before a snapshot and after any continuity or
  persistence failure.
- Route recovered provider messages through the existing subscription and
  message-dispatch path.
- Ensure a recovered snapshot receives a new `sequenceId` and that no delta can
  append to an invalidated sequence.

If the repository transition fails, capture must remain fail-closed and recovery
must not report success.

### 3. Make sequence supersession atomic

- Add a repository operation dedicated to accepting a new provider snapshot as a
  new sequence.
- In one transaction, invalidate existing `ACTIVE` sequences for the same
  market/depth and persist the new snapshot.
- Roll back the supersession if the new snapshot cannot be persisted.
- Preserve old evidence rows and fingerprints; only lifecycle status metadata may
  change.
- Keep the generic delta/checkpoint append path unchanged except where required
  to share transaction-safe helpers.

This uses the existing `ACTIVE`/`INVALIDATED` schema and avoids a migration unless
implementation validation proves that an explicit current-sequence column is
necessary.

### 4. Close all obsolete active sequences on lifecycle invalidation

- Change lifecycle invalidation to operate on the affected market/depth scope,
  not only the in-memory sequence UUID.
- Invalidate every persisted active sequence for that scope when a terminal
  stream, sequence gap, state invalidation, or persistence failure makes the
  scope untrusted.
- Preserve the existing fail-closed behavior if the invalidation transaction
  cannot be persisted.

This prevents an older persisted sequence from remaining `ACTIVE` after a newer
sequence has become invalid.

### 5. Make strict replay cutoff-aware across sequences

- Change `findTrusted` so it identifies the latest sequence with evidence known
  at the requested cutoff before returning evidence.
- Return evidence only when that selected sequence is `ACTIVE` and its evidence
  is trusted and contiguous for the replay service.
- If the latest sequence at the cutoff is invalidated, return no trusted evidence
  so the replay result is explicitly `UNAVAILABLE` or `INVALID` rather than
  falling back to an older active sequence.
- Prevent replay from mixing evidence from multiple active sequences.
- Preserve `knownAt <= cutoff`, provider-neutral evidence, fingerprints, and the
  existing replay result contract.

No arbitrary freshness threshold will be added to the evidence query. Freshness
remains a consumer policy as required by ADR-063; this change addresses sequence
supersession and invalidation semantics.

### 6. Extend lifecycle diagnostics

Add bounded diagnostics for:

- recovery scheduled;
- recovery attempt started;
- recovery succeeded after a fresh snapshot;
- recovery attempt failed;
- recovery budget exhausted;
- normal shutdown recovery suppression.

Diagnostics must include provider product, connection ID or recovery episode
identity, attempt number, and failure class/message where available. They must
not include credentials, full book payloads, or per-delta logs.

### 7. Preserve public and cross-service boundaries

- Keep the existing Market Data subscription endpoints and request models
  unchanged.
- Keep Trading Core, Market Intelligence, Broker Service, fees, funding,
  FeatureSnapshots, outcomes, Gateway, Risk Domain, and frontend untouched.
- Keep Kraken-specific recovery and transport logic inside the Kraken adapter.
- Preserve the unresolved checksum behavior from Story 0121: non-null
  unvalidated checksums remain rejected and no Spot algorithm is introduced.

## Files to Modify

- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenDataStreamProvider.java`
  - bounded recovery coordination, normal-shutdown suppression, and recovery
    diagnostics integration.
- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookEvidenceCaptureService.java`
  - route new snapshots through atomic sequence supersession and invalidate the
    complete market/depth scope when trust is lost.
- `market-data/src/main/java/com/hope/trading/market_data/service/OrderBookEvidenceRepository.java`
  - add transactional snapshot-start and scope-invalidation operations; make
    strict evidence selection cutoff-aware and single-sequence.
- `market-data/src/main/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamLifecycleDiagnostics.java`
  - add bounded recovery lifecycle events.
- `market-data/src/test/java/com/hope/trading/market_data/kraken/websocket/KrakenDataStreamProviderTest.java`
  - terminal recovery, deduplication, shutdown suppression, bounded failure,
    fresh snapshot, and subscription replay tests.
- `market-data/src/test/java/com/hope/trading/market_data/service/OrderBookEvidenceCaptureServiceTest.java`
  - scope invalidation, atomic snapshot-start behavior, and fail-closed
    persistence cases.
- `market-data/src/test/java/com/hope/trading/market_data/service/OrderBookReplayServiceTest.java`
  - latest-sequence-at-cutoff selection, invalidated-latest behavior, and
    multi-sequence isolation.
- `market-data/src/test/java/com/hope/trading/market_data/repository/OrderBookEvidencePersistenceTest.java`
  - transactional sequence supersession and stale active-sequence exclusion.
- `market-data/src/test/java/com/hope/trading/market_data/kraken/websocket/KrakenStreamLifecycleDiagnosticsTest.java`
  - only if the existing diagnostics test surface does not already cover the new
    events.

## Files to Create

None expected.

The plan prefers small private state and existing diagnostic types. A new
package-level recovery policy type is allowed only if the provider state becomes
unreadable without it; no new service or external abstraction is planned.

## Dependencies

- Internal dependency: `KrakenDataStreamProvider` continues to use the existing
  `MarketSubscriptionServiceImpl` registration path and
  `replayActiveSubscriptions` behavior.
- Internal dependency: capture and repository sequence transitions must be
  implemented before recovery success can be reported.
- Internal dependency: strict replay query changes must be validated against the
  existing `OrderBookReplayResult` contract.
- External dependency: Kraken Futures WebSocket availability is required for
  runtime validation only.
- No new external Maven dependency is required.
- No database migration is planned initially. Existing schema transitions must
  be verified; a migration requires an explicit plan revision if unavoidable.
- Implementation order: repository/capture semantics, provider recovery,
  diagnostics, focused tests, then runtime and module validation.

## Test Plan

### Provider lifecycle tests

- Complete a Futures inbound stream and verify one bounded recovery attempt is
  made while active subscriptions remain.
- Fail a Futures inbound stream and verify recovery starts without fabricating
  active state when connection establishment fails.
- Repeat terminal signals and verify only one recovery attempt is active for the
  product.
- Exhaust the recovery budget and verify the provider remains degraded without an
  unbounded loop.
- Call normal disconnect and verify no recovery is scheduled.
- Verify a recovered connection replays active subscriptions and requires a fresh
  snapshot before accepting deltas.
- Verify recovery diagnostics contain bounded metadata and no payload or secret.

### Capture and persistence tests

- Start a new snapshot sequence and verify prior active sequences for the same
  market/depth are invalidated atomically.
- Make new snapshot persistence fail and verify the prior transaction is not
  partially superseded and capture remains fail-closed.
- Invalidate a scope with multiple persisted active sequences and verify all are
  excluded from trusted replay.
- Verify a delta cannot append after terminal invalidation or persistence
  failure.
- Verify a new valid snapshot creates a different sequence ID and accepts deltas
  only after the snapshot.

### Replay tests

- Verify replay selects only one sequence at a cutoff.
- Verify a later invalidated sequence causes replay to return unavailable/invalid
  rather than falling back to an older active sequence.
- Verify evidence with `knownAt > cutoff` remains excluded.
- Verify multiple active sequences are treated as ambiguous/unavailable rather
  than mixed.
- Preserve existing contiguous replay, missing snapshot, and unexpected snapshot
  behavior.

### Validation commands

Planned commands after implementation:

```text
./mvnw -q -Dtest=KrakenDataStreamProviderTest,OrderBookEvidenceCaptureServiceTest,OrderBookReplayServiceTest,OrderBookEvidencePersistenceTest test
./mvnw -q verify
git diff --check
```

Quality validation must report applicable Market Data checks, JaCoCo results,
and SonarQube status for `trading-os:market-data` if the configured SonarQube
integration is available. No test result is claimed by this plan.

## Risks

- **Reconnect storm:** repeated provider termination could create an unbounded
  loop. Mitigation: one recovery coordinator per product, explicit backoff, a
  bounded attempt budget, and suppression during normal shutdown.
- **Duplicate subscriptions:** recovery could replay a subscription while a
  manual request is in flight. Mitigation: reuse the existing synchronized
  subscription registration and provider active-subscription maps, plus
  recovery deduplication state.
- **Partial sequence transition:** invalidating old sequences before a failed
  snapshot insert could remove the last usable evidence. Mitigation: perform
  supersession and snapshot persistence in one transaction and test rollback.
- **Historical fallback regression:** a replay query could accidentally fall back
  to an older valid sequence after a later sequence is invalidated. Mitigation:
  select the latest sequence at the cutoff first, then require it to be active.
- **Checksum scope expansion:** recovery work could be mistaken for checksum
  validation. Mitigation: preserve current checksum rejection behavior and add a
  regression test for non-null checksum input.
- **Shutdown race:** a normal close could be interpreted as an unexpected
  termination. Mitigation: set and clear an explicit shutdown-suppression state
  around intentional disconnect operations and test it.
- **Runtime provider dependence:** external Kraken behavior may prevent live
  recovery validation. Mitigation: use deterministic connector tests first and
  report runtime validation as unavailable if the provider cannot be exercised.

## Validation Checklist

- [ ] Only `market-data` production code is modified.
- [ ] Existing subscription API and request contracts remain unchanged.
- [ ] Unexpected terminal streams trigger bounded recovery when active
      subscriptions remain.
- [ ] Normal shutdown and explicit unsubscribe do not trigger recovery.
- [ ] Recovery uses a fresh provider snapshot and new `sequenceId`.
- [ ] No delta is accepted before the fresh snapshot.
- [ ] No delta is appended to an invalidated or superseded sequence.
- [ ] All obsolete active sequences for a scope are excluded from replay.
- [ ] Strict replay never falls back from a later invalidated sequence to an
      older active sequence.
- [ ] `knownAt <= cutoff` remains enforced.
- [ ] Existing checksum conservatism remains unchanged.
- [ ] Historical evidence payloads and fingerprints are not rewritten.
- [ ] Lifecycle diagnostics are bounded and secret-free.
- [ ] Recovery attempts are deduplicated and bounded.
- [ ] Focused tests pass.
- [ ] `./mvnw -q verify` passes for `market-data`.
- [ ] `git diff --check` passes.
- [ ] SonarQube/JaCoCo results are reported when applicable.
- [ ] No unrelated modules, services, databases, or runtime data are changed.

## Recommendation

Ready for implementation

The approved Story and Repository Analysis define a bounded Market Data change,
the affected components are known, and the remaining policy choices are fixed
by this plan. Implementation still requires the separate human approval gate.

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
