# Story 0119 - Implementation Report

## Status

Implementation complete; human review pending.

## Scope

Implemented bounded Kraken stream lifecycle diagnostics in `market-data` without
adding reconnect, retry, checksum, persistence, replay, or schema behavior.

## Changes

- Added `KrakenStreamLifecycleDiagnostics` for connection, subscription, snapshot,
  termination, and cleanup events.
- Added diagnostic connection IDs that remain in memory and are excluded from
  evidence identity and persistence.
- Preserved Reactor `SignalType` and the error/root-cause chain before existing
  cleanup runs.
- Added active sequence diagnostic context containing sequence ID, market/depth,
  provider sequence, local ordinal, and last `occurredAt`/`knownAt` values.
- Logged `occurredAt`/`knownAt` lag at termination when available.
- Added focused tests for termination context semantics and sequence context
  before invalidation.
- Added the operational runbook at `operational-runbook.md`.

## Known Facts

- `ON_ERROR`, `ON_COMPLETE`, and `CANCEL` are represented by the actual Reactor
  `SignalType` passed to `doFinally`.
- Error diagnostics preserve exception and deepest root-cause class/message.
- Termination diagnostics execute before `clearRealtimeStates`.
- Existing sequence invalidation and replay filtering are unchanged.
- No reconnect, retry, resubscription, checksum implementation, migration, or
  metrics stack was added.
- Non-null checksums remain rejected; null checksums remain accepted under the
  existing provider limitation.

## Test-Proven Behavior

- Error/root-cause extraction is covered by
  `KrakenStreamLifecycleDiagnosticsTest`.
- Normal completion and cancellation remain distinguishable without fabricated
  exceptions.
- Active sequence context is available before invalidation and absent after cleanup
  through `OrderBookEvidenceCaptureServiceTest`.
- Existing Kraken provider, capture, persistence, and replay tests remain covered
  by the targeted test run.

## Validation Commands

- `./mvnw test` in `market-data`: passed, 233 tests, 0 failures, 0 errors.
- `./mvnw verify` in `market-data`: passed, including JaCoCo coverage checks.
- `git diff --check`: passed.

The review follow-up also added provider-level coverage for Futures subscription
diagnostics and termination ordering across `onError`, `onComplete`, and `cancel`.

## Runtime Validation

- Test logs demonstrated connection, subscription, normal completion, and cleanup
  diagnostics through the existing connector seam.
- The existing `market-data` container is running, but its image predates this
  uncommitted implementation and was not rebuilt or restarted by this task.
- No real Kraken termination was forced or observed during this bounded change.
- Recent container logs contained no matching lifecycle diagnostic events.
- Final runtime status is therefore `NO_NATURAL_TERMINATION_OBSERVED`.

## Documentation Outcome

- Added the Story-local operational runbook.
- No ADR was required because the implementation only exposes existing lifecycle
  state and does not alter architecture or trust semantics.

## Vault Outcome

- Vault was not consulted; repository code, Story artifacts, and ADR-063 provided
  the required authoritative context.
- No vault action is proposed.
