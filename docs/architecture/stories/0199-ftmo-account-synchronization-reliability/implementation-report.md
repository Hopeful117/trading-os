# Story 0199 - Implementation Report

## Root Cause

The real cTrader snapshot initially hit the Broker Service safety guard, and the
downstream `409 BROKER_SNAPSHOT_DEFERRED` was not translated by Trading Core and
was exposed as an ambiguous `403`. After the cooldown, the provider snapshot was
reached and returned incomplete financial and collection facts. Those explicit
availability states must not be treated as authoritative when positions, orders
or deal history are unavailable or truncated.

## Changes

- Added duration and failure diagnostics around the Trading Core snapshot call.
- Translated only a Feign `409` whose body contains
  `BROKER_SNAPSHOT_DEFERRED` to that public error code.
- Translated other provider conflicts to `BROKER_PROVIDER_CONFLICT` rather than
  misclassifying them as a cooldown.
- Rejected unavailable or truncated positions, orders and deal collections before
  local reconciliation or persistence.
- Added a 30-second aggregate deadline to the cTrader read-only observation while
  preserving the existing per-request bounds.
- Added focused regression tests for error classification and partial collections.

## Real Validation

The final authenticated real request used the existing FTMO account and returned:

- HTTP `409 Conflict`;
- `code=BROKER_ACCOUNT_SNAPSHOT_INCOMPLETE`;
- `completeness=INCOMPLETE`, `freshness=FRESH`;
- reasons: `DEAL_HISTORY_UNAVAILABLE`, `OBSERVATION_INCOMPLETE`,
  `EQUITY_UNAVAILABLE`, `USED_MARGIN_UNAVAILABLE`, `FREE_MARGIN_UNAVAILABLE`;
- `durationMs=1332`;
- no account or trade persistence;
- no provider mutation.

The result is classified as `SYNC_FAILED_BOUNDED`, not `SYNC_SUCCESS`, because
the provider-authoritative snapshot remains incomplete.

## Validation

- Focused Trading Core synchronization and handler tests passed.
- Focused Broker Service cTrader client and snapshot tests passed.
- Docker Trading Core image built successfully.
- `git diff --check` passed.
- The full Trading Core suite was attempted but exceeded the 120-second tool
  limit; it is reported as incomplete rather than passing.

## Safety and Remaining Blockers

No credentials, tokens or secrets were exposed. No order was submitted. Risk and
execution remain fail-closed because provider-authoritative equity, margin and
deal facts are unavailable.

## Documentation Outcome

Updated only the Story-local validation matrix and implementation report required
by Story 0199. No architecture or policy documentation required changes.

## Vault Outcome

The Obsidian vault was not consulted. No vault action is proposed; this work is
fully determined by the Story, current repository code and runtime evidence.

## Next Action

Investigate why the cTrader provider omits equity, margin and deal facts, then
repeat the same bounded read-only synchronization validation without deriving
replacement financial values.
