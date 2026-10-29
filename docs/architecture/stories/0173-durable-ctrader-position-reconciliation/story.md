# Story 0173 - Durable cTrader Position Reconciliation and External Close Handling

## Metadata

**ID:** `0173`
**Title:** Durable cTrader Position Reconciliation and External Close Handling
**Status:** Implemented Locally - Awaiting Review
**Related ADRs:** `ADR-071`, `ADR-074`, `ADR-078`
**Related Stories:** `0170`

---

## Goal

Make broker position synchronization durable and safe for the cTrader-backed
execution path. A locally persisted trade must retain its stable broker
position identity, survive synchronization and restart, reflect partial or
full external closes, and never be recreated incorrectly after the provider
has confirmed that the position no longer exists.

The Story must improve reconciliation without submitting, closing, cancelling,
or modifying any broker order or position.

---

## Context

Trading Core currently synchronizes broker snapshots by removing all locally
open trades and recreating them from the latest provider positions. This loses
local identity and lifecycle continuity. It prevents reliable correlation of
external closes, can discard execution provenance, and makes synchronization
unsafe when provider observations are incomplete or temporarily unavailable.

cTrader exposes stable position identity and authoritative deal history. The
broker-neutral synchronization boundary must preserve those facts without
leaking cTrader protocol types into Trading Core. cTrader account identity,
execution mode, and provider environment remain separate facts. A connected
cTrader demo or simulated provider account is still Trading OS `LIVE` when an
external broker boundary is involved; `PAPER` remains entirely local.

---

## Problem

The existing position replacement behavior does not distinguish:

- the same provider position observed again;
- a provider position whose size changed;
- a position closed externally;
- an incomplete or stale provider snapshot;
- a local trade that has no provider identity;
- a provider position that has never been observed locally.

Without stable identity and authoritative close correlation, synchronization can
create duplicate trades, overwrite durable lifecycle data, resurrect closed
positions, or silently lose exit facts and realized PnL.

---

## Scope

- Persist a stable provider-neutral broker position reference on the local trade
  model and database representation.
- Populate and preserve the reference from cTrader authoritative snapshots.
- Reconcile an existing open trade by broker position reference instead of
  deleting and recreating every open trade.
- Update quantity, side, instrument, entry facts, and current provider facts
  for an existing position without losing local execution provenance.
- Close or settle a local trade only when provider-authoritative deal history
  proves that the corresponding broker position was fully closed.
- Persist exit price, exit time, realized PnL, and provider close/deal
  reference when those facts are available from the authoritative close event.
- Handle partial closes without incorrectly marking the local trade closed.
- Treat a missing provider position as a close only when the authoritative
  observation is complete, fresh, and the correlated deal history proves the
  close; otherwise preserve the local open trade and surface the observation
  as incomplete or uncertain.
- Make synchronization idempotent across repeated calls, reconnects, and
  application restarts.
- Prevent a closed trade from being resurrected by a later snapshot containing
  the same provider reference.
- Preserve manual synchronization as the trigger and keep synchronization
  read-only with respect to broker operations.
- Add focused tests for cTrader mapping, reconciliation, partial close,
  external full close, stale/incomplete snapshots, restart persistence,
  idempotency, and no-resurrection behavior.

---

## Out of Scope

- Submitting, cancelling, replacing, or closing any cTrader order or position.
- Automatic broker retries or write-side reconciliation commands.
- Autonomous trading, strategy selection, opportunity ranking, or AI authority.
- Redesigning the generic execution pipeline or broker capability model beyond
  the contracts required to expose authoritative position/deal facts.
- Changing risk formulas, Challenge rules, execution-mode semantics, or
  provider environment classification.
- Introducing cTrader protocol DTOs into Trading Core, Risk Domain, Market
  Intelligence, or the frontend.
- Replacing the existing manual synchronization trigger with a scheduler.
- Reworking Kraken synchronization beyond compatibility-preserving changes.
- Treating a cTrader demo label or provider `isLive` value as a substitute for
  the existing execution-boundary rules.

---

## Acceptance Criteria

- [ ] Every provider-backed local trade can persist a stable broker position
      reference when the provider supplies one.
- [ ] A cTrader authoritative snapshot maps its stable position identity into
      the broker-neutral position contract without exposing cTrader payload
      types outside Broker Service.
- [ ] Repeated synchronization of the same provider position updates the
      existing local trade and does not create a duplicate or replace its local
      execution/provenance identity.
- [ ] A provider position size change is reconciled as a quantity update or
      partial-close state according to authoritative facts, not as a new trade.
- [ ] A local trade is marked closed only after a complete and fresh provider
      observation proves the position is absent and correlated authoritative
      deal history proves the full close.
- [ ] A partial close preserves the local trade as open with the remaining
      quantity and does not apply full-close exit facts prematurely.
- [ ] When available, the closed trade persists authoritative exit price, exit
      timestamp, realized PnL, and the closing broker deal/reference.
- [ ] An incomplete, stale, contradictory, or unavailable snapshot never
      closes a local trade and never removes its durable open state.
- [ ] A provider position that is absent from local state is imported once by
      stable provider identity and is not duplicated on subsequent syncs.
- [ ] A closed local trade is never resurrected by a later snapshot containing
      the same provider position reference.
- [ ] Synchronization is idempotent across repeated calls and application
      restart, including after persisted trades are reloaded from the database.
- [ ] Synchronization performs no broker write operation and does not submit,
      cancel, replace, or close an order or position.
- [ ] Existing Kraken behavior, PAPER behavior, risk/provenance lineage, and
      manual synchronization behavior remain compatible.
- [ ] Focused regression tests and affected module validation pass, including
      `git diff --check`.
- [ ] No cTrader credentials, access tokens, or sensitive provider payloads are
      logged, persisted outside the intended model, or committed.

---

## Constraints

- Preserve the responsibility boundaries in `ADR-071` and `ADR-074`.
- Preserve the `PAPER`/`LIVE` execution boundary defined by `ADR-078`.
- Keep provider protocol mapping, provider errors, and deal-history acquisition
  inside Broker Service.
- Keep Trading Core provider-neutral and deterministic.
- Treat incomplete or stale observations as unsafe for destructive local state
  transitions.
- Do not infer a close from transport failure, timeout, missing optional deal
  data, or an incomplete position snapshot.
- Do not use local timestamps or current market prices as substitutes for
  provider-authoritative exit facts.
- Preserve pre-existing working-tree changes and do not perform Git integration
  actions automatically.
- Do not execute any live, demo, or simulated broker mutation during tests,
  startup, migration, synchronization, or validation.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-071.md`
- `docs/architecture/adr/ADR-074-ctrader-authoritative-read-only-synchronization.md`
- `docs/architecture/adr/ADR-078-paper-live-execution-boundary.md`

---

## Relevant Modules

- `broker-service`
- `trading-core`
- `ctrader-openapi-technical`

---

## Validation

- Broker-neutral position and deal contract tests.
- cTrader authoritative snapshot mapping tests with stable position identity.
- Trading Core synchronization tests for create, update, partial close, full
  external close, stale/incomplete observations, restart persistence,
  idempotency, and no resurrection.
- Persistence migration and reload tests for the broker position reference and
  exit facts.
- Existing Kraken synchronization and PAPER regression tests.
- Affected Maven module tests and `git diff --check`.
- Static and architecture-boundary checks applicable to the changed modules.
- Read-only runtime evidence only; no broker write operation is authorized by
  this Story.

---

## Definition of Done

- [ ] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [x] Implementation completed
- [x] Documentation reconciliation completed
- [x] Relevant validation executed
- [ ] Code Review completed
- [ ] Engineering Report completed
- [ ] Human commit created
- [ ] Pull request externally validated by the human engineer
