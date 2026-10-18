# Story 0124 - Order Book Sequence Recovery and Stale Replay Protection

## Metadata

**ID:** `0124`
**Title:** Order Book Sequence Recovery and Stale Replay Protection
**Status:** Draft
**Related Stories:** `0119`, `0121`, `0122`, `0123`
**Related ADRs:** `ADR-063`, `ADR-064`

## Goal

Restore trustworthy historical Order Book accumulation after a stream
termination or transport interruption, while ensuring that strict replay cannot
select a stale sequence merely because its database status remains `ACTIVE`.

## Context

The overnight runtime investigation found that the current Order Book sequence
accumulated evidence and then terminated normally. That sequence was invalidated,
but an older sequence remained `ACTIVE` and stale. The canonical replay reader
therefore still exposed old evidence as eligible for later cutoffs.

ADR-063 requires the system to fail closed after a reconnect or unknown update
gap, discard untrusted in-memory state, obtain a fresh provider snapshot, and
start a new sequence. ADR-064 requires economic replay to use one valid trusted
sequence and to return an explicit unavailable or invalid result when a complete
reconstruction cannot be established.

The provider-specific Kraken Futures checksum protocol remains unresolved under
Story 0121. This Story must preserve the current conservative checksum behavior
and must not infer or implement the Spot checksum protocol for Futures.

## Problem

The current lifecycle can leave a stale Order Book sequence marked `ACTIVE` after
the current stream has terminated and its newer sequence has been invalidated.
Strict replay filters by `sequence.status = 'ACTIVE'`, but does not establish that
the selected sequence is the current valid sequence for the requested cutoff.

The stream also does not currently establish a fresh capture sequence after the
observed terminal event. This creates two risks:

- historical replay may return stale evidence from an obsolete sequence;
- new capture may not resume with a fresh trusted snapshot and sequence.

## Scope

- Define and implement the lifecycle state transition for a terminal WebSocket
  session or transport interruption.
- Ensure the interrupted or obsolete active sequence cannot remain eligible for
  trusted strict replay.
- Resume acquisition from a fresh provider snapshot and a new `sequenceId`.
- Preserve sequence continuity and fail-closed behavior for gaps, malformed
  snapshots, persistence failures, and trust failures.
- Make strict replay return an explicit unavailable or invalid result when no
  complete valid sequence exists at the requested cutoff.
- Preserve sufficient lifecycle diagnostics for sequence invalidation, recovery,
  reconnect count, latest valid snapshot, and capture failures.
- Add focused Market Data tests covering terminal recovery, stale sequence
  exclusion, fresh sequence creation, idempotency, and strict replay behavior.
- Validate the bounded Kraken Futures scope currently used by the runtime:
  `PF_XBTUSD`, depth `25`.

## Out of Scope

- Establishing or implementing a Kraken Futures checksum algorithm; this remains
  blocked by Story 0121.
- Applying the Kraken Spot WebSocket v2 checksum algorithm to Futures.
- Rewriting, deleting, compacting, or retroactively trusting existing historical
  evidence.
- Treating stale evidence, missing evidence, or unavailable funding as zero.
- Changes to FeatureSnapshots, outcomes, fees, funding semantics, economic
  formulas, Trading Core, Broker Service, Risk Domain, Gateway, or frontend
  behavior.
- Introducing a new messaging or storage platform.
- Expanding capture beyond the approved bounded market/depth rollout.
- Adding automatic retries for unsafe broker write operations.

## Acceptance Criteria

- [ ] A terminal WebSocket session or transport interruption invalidates or
      quarantines the affected active capture sequence before it can be used as
      current trusted replay evidence.
- [ ] Recovery obtains a fresh valid provider snapshot and persists it under a
      new `sequenceId` before accepting deltas into the recovered sequence.
- [ ] At most one current valid capture sequence is replay-eligible for the
      bounded market/depth scope, without rewriting historical evidence.
- [ ] Strict replay never selects an obsolete stale sequence solely because its
      status is `ACTIVE`.
- [ ] Strict replay returns an explicit unavailable or invalid result when no
      complete valid sequence can reconstruct the requested cutoff.
- [ ] Sequence gaps, malformed snapshots, persistence failures, and unresolved
      trust failures continue to fail closed.
- [ ] Existing checksum behavior remains conservative and unchanged while Story
      0121 is blocked.
- [ ] Lifecycle diagnostics expose the terminal event, sequence invalidation,
      recovery attempt, fresh sequence, and recovery failure when applicable.
- [ ] Focused Market Data tests pass, including repeated terminal/recovery
      behavior and idempotent persistence.
- [ ] Market Data validation passes without changing unrelated services or
      economic semantics.

## Constraints

- Preserve the Market Data ownership boundary defined by ADR-063 and ADR-064.
- Keep canonical evidence append-only and provider-neutral.
- Do not use the live in-memory book or a current REST response as historical
  replay fallback.
- Respect `knownAt <= cutoff` for strict historical reads.
- Do not claim perfect network-gap detection when the provider does not expose a
  complete sequence protocol.
- Keep provider-specific payload and transport behavior inside the Kraken
  infrastructure adapter.
- Do not weaken trust validation to keep the stream active.
- Do not commit, push, merge, or modify unrelated pre-existing work.

## Relevant ADRs

- `docs/architecture/adr/ADR-063.md` - Historical Order Book Evidence and Replay Semantics
- `docs/architecture/adr/ADR-064.md` - Historical Execution and Transaction-Cost Research Architecture

## Relevant Modules

- `market-data`

## Validation

- Targeted Market Data unit and integration tests.
- PostgreSQL-backed persistence and replay validation for the bounded market/depth
  scope.
- Runtime verification of terminal-session recovery and fresh sequence creation
  without historical evidence mutation.
- Strict replay checks for stale active sequence exclusion and explicit
  unavailable/invalid results.
- `git diff --check`.
- Relevant independent Market Data Maven validation.

## Definition of Done

- [ ] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [ ] Implementation completed
- [ ] Relevant validation executed
- [ ] Diff reviewed in IntelliJ
- [ ] Code Review approved
- [ ] Engineering Report completed
- [ ] Human commit created
