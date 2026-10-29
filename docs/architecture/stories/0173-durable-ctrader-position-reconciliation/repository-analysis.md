# Repository Analysis - Story 0173

## Status

`READY_FOR_HUMAN_APPROVAL`

This analysis is based on the approved Story 0173, the current repository at
`1473f8db6315a9ab9f769c0d6d2bed1aee5376dc`, accepted ADRs, and targeted source
and test inspection. No implementation has been performed.

## Story Understanding

Story 0173 is a read-only reconciliation correction. It must preserve local
trade continuity across repeated broker snapshots, application restart,
partial external closes, and complete external closes. It must not turn
synchronization into a broker mutation path.

The key safety rule is asymmetric:

- provider-authoritative complete and fresh facts may advance local state;
- stale, incomplete, contradictory, unavailable, or non-correlated facts must
  not perform destructive local transitions;
- a provider position reference is the identity used for deduplication;
- a closed local trade must not be recreated from a later observation of the
  same provider reference.

## Repository Baseline

| Item | Current value |
|---|---|
| Canonical Story | `docs/architecture/stories/0173-durable-ctrader-position-reconciliation/story.md` |
| Repository HEAD | `1473f8db6315a9ab9f769c0d6d2bed1aee5376dc` |
| Branch | `main` |
| Working tree | Dirty; unrelated Dockerfiles and runtime/network artifacts must be preserved |
| DevLog | Story Agent was unavailable because the upstream provider returned HTTP 429; analysis continues from repository evidence |
| Obsidian vault | Not consulted; no transverse note was required to establish current repository behavior |
| Current synchronization trigger | Authenticated `POST /api/v2/accounts/{brokerAccountId}/synchronize` |
| Current cTrader capability | Authoritative read-only account snapshot |
| Broker mutations | Explicitly out of scope for this Story |

## Architectural Authority

### ADR-071

`ADR-071` keeps cTrader authentication, transport, provider mapping, provider
errors, account synchronization, and provider capability composition inside
Broker Service. Trading Core owns reconciliation decisions, execution state,
deterministic business orchestration, and provider-neutral contracts.

The ADR requires read-only synchronization before execution, complete and fresh
facts for authoritative decisions, fail-closed behavior for provider divergence,
and no provider-specific DTOs or branches in Trading Core.

### ADR-074

`ADR-074` defines the cTrader authoritative read-only boundary. Broker Service
normalizes account, position, order, deal, identity, timestamp, completeness,
and freshness facts. Trading Core consumes only the provider-neutral snapshot.

The ADR explicitly makes stable provider identifiers the deduplication authority
for positions, orders, and deals. It also states that incomplete or stale facts
must not be used as current authoritative input and that persisted observations
do not become fresh merely because the process restarts.

### ADR-078

`ADR-078` preserves the PAPER/LIVE execution boundary. A provider-backed cTrader
account remains Trading OS `LIVE` even when its economic environment is demo or
simulated. This Story only synchronizes provider-backed facts and must not alter
that classification or call external mutations from PAPER.

## Current Provider-Neutral Contracts

`trading-core` already receives the following V2 snapshot shape:

- `BrokerAccountSnapshotV2Dto` contains account identity, balances, positions,
  orders, deals, observed timestamps, completeness, freshness, and unavailable
  reasons.
- `BrokerPositionSnapshotV2Dto` already exposes `brokerPositionReference`,
  signed quantity, entry price, observation time, margin, and stop loss.
- `BrokerDealSnapshotV2Dto` exposes a provider trade reference, instrument,
  side, quantity, price, fees, swap, realized PnL, and close time.

The equivalent Broker Service model is `BrokerModels.BrokerAccountSnapshotV2`:

- `PositionSnapshot` includes `brokerPositionReference`.
- `ClosedTrade` currently contains `providerTradeReference`, but no explicit
  broker position reference or close-event correlation field.
- `SnapshotCompleteness` and `SnapshotFreshness` already distinguish complete /
  incomplete and fresh / stale observations.

This means the account snapshot boundary is close to the required shape, but the
deal contract is not yet sufficient to prove that a specific deal closed a
specific persisted position. The implementation plan must resolve this without
exposing cTrader protocol payloads in Trading Core.

## Current cTrader Mapping

`CTraderAuthoritativeSnapshotCapability` maps the provider-owned
`CTraderReadOnlyObservation` into `BrokerAccountSnapshotV2` and preserves:

- cTrader position ID as `brokerPositionReference`;
- cTrader deal ID as `ClosedTrade.providerTradeReference`;
- deal quantity, price, commission, swap, realized PnL, and execution timestamp;
- complete/incomplete state derived from observation completeness and a 30-second
  freshness policy.

`JsonWebSocketCTraderOpenApiClient` obtains trader, assets, symbols, positions,
orders, unrealized PnL, and a bounded deal list in one read-only observation
window. Position IDs are read from `positionId`. Deal IDs are read from
`dealId`, and close facts are read from `closePositionDetail`.

The current cTrader `Deal` and neutral `ClosedTrade` mapping does not retain a
position ID from the deal payload. The current bounded deal query also uses a
time window and `maxRows=1000`; therefore the implementation must not assume
that every returned deal is a close for the locally persisted position, or that
absence from the returned list proves that no close exists.

Existing tests cover position-reference mapping, complete/fresh snapshots,
account identity mismatch, omitted empty collections, missing position margin,
and basic deal fact mapping. They do not cover position-to-deal correlation or
local lifecycle reconciliation.

## Current Trading Core State

### Trade Persistence

`trading-core` `Trade` is a JPA entity mapped to `trades` with:

- generated local `tradeId` and optimistic-lock `version`;
- symbol, side/type, entry price, quantity, open/close timestamps;
- current/exit price, PnL, stop/take-profit and risk fields;
- `TradeStatus.OPEN`, `CLOSED`, or `CANCELLED`;
- account ownership.

It has no stable broker position reference, provider close/deal reference, or
explicit provenance for provider-derived exit facts. `TradeDto` and
`TradeMapper` also do not expose broker identity or provider exit references.

Existing analytics treat `closedAt != null` as closed/open state in several
queries, while synchronization uses `TradeStatus.OPEN`. The plan must preserve
these existing semantics or make the transition consistently and explicitly.

### Synchronization Behavior

`BrokerSynchronizationServiceImpl.synchronize(UUID, UUID)` currently:

1. validates the owner and broker-account mapping;
2. fetches a V2 broker snapshot;
3. validates provider registration, broker account identity, and external account
   identity;
4. rejects snapshots that are not `COMPLETE` and `FRESH`;
5. replaces balances;
6. calls `replaceOpenPositions`;
7. recalculates and saves the account.

`replaceOpenPositions` removes every local `TradeStatus.OPEN` trade from the
account collection, then creates a new trade for every provider position. It
does not consult the existing `TradeRepository` for identity matching, does
not preserve local trade IDs or execution provenance, and does not process
deals, close facts, partial closes, or previously closed trades.

The older username-based synchronization path has the same destructive pattern
through `synchronizeOpenTrades`. The Story scope is the V2 explicit
broker-account path, but compatibility impact on the legacy path must be
verified before changing shared trade fields or mappings.

### Manual Trigger

`AccountSynchronizationV2Controller` exposes the authenticated manual trigger
at `POST /api/v2/accounts/{brokerAccountId}/synchronize`. No scheduler or
automatic provider write path is involved in the current flow.

### Existing Position Close Boundary

Trading Core already has a provider-neutral position-close port carrying a
`brokerPositionReference`, and Broker Service exposes resolve/execute/reconcile
close operations. That path is mutation-oriented and must remain separate from
this Story. The new synchronization behavior must not call it when a position
disappears from a read-only snapshot.

## Persistence And Migration Findings

Trading Core uses `spring.jpa.hibernate.ddl-auto=validate` and Flyway locations:

```text
classpath:db/migration/common
classpath:db/migration/{vendor}
```

The existing common migrations include trade and execution persistence changes,
and PostgreSQL-specific migrations include immutable-risk and position-close
indexes. A new durable broker identity or exit-fact field therefore requires a
forward Flyway migration and corresponding schema validation coverage. Hibernate
must not be relied on to create or alter the `trades` table.

The repository already uses PostgreSQL integration tests with `ddl-auto=validate`,
so migration compatibility must cover both a fresh install and an existing
database containing trades without broker references.

## Confirmed Gaps

1. `Trade` cannot persist `brokerPositionReference`.
2. `Trade` cannot persist a provider close/deal reference or distinguish which
   provider fact supplied exit price, close time, and realized PnL.
3. `replaceOpenPositions` destroys local open-trade identity and provenance.
4. Synchronization does not match provider positions to existing trades.
5. Synchronization does not reconcile partial quantity changes.
6. Synchronization does not consume deals to close or settle local trades.
7. The neutral deal contract has no explicit position-correlation field.
8. A later snapshot can recreate a previously closed position because no
   durable no-resurrection guard exists.
9. The current test suite does not cover restart reload, idempotent update,
   partial close, externally confirmed close, or no-resurrection behavior.
10. Legacy username-based synchronization still uses replacement semantics and
    must be assessed for compatibility when shared model fields change.

## Required Implementation Shape

The implementation plan should preserve this flow:

```text
manual authenticated synchronization
    -> owner/account/provider identity validation
    -> complete and fresh provider-neutral snapshot
    -> position matching by durable broker position reference
    -> non-destructive update/import of open positions
    -> authoritative deal correlation for partial/full close
    -> durable local trade update
    -> deterministic account recalculation and persistence
```

The plan must explicitly decide:

- whether provider position identity is globally unique or must be scoped by
  broker account and provider registration;
- how legacy/local trades without a broker reference are handled without
  accidental duplicate matching;
- what provider-neutral deal correlation fields are required;
- how multiple close deals are aggregated for one local trade;
- how partial close quantity and realized PnL are represented;
- whether no-resurrection is guaranteed by status/reference matching alone or
  requires an additional immutable lifecycle marker;
- how duplicate or repeated deal observations remain idempotent;
- how migration backfills existing rows, if at all;
- how to preserve Kraken and PAPER behavior without weakening cTrader safety.

No implementation should infer a full close solely from a missing position in a
snapshot. The implementation must require the snapshot's complete/fresh
authority and a matching provider close fact, or preserve the local trade and
surface uncertainty according to the existing observation boundary.

## Risks And Decisions Required In The Plan

| Risk or decision | Required treatment |
|---|---|
| Deal-to-position correlation | Verify official cTrader payload semantics and introduce only provider-neutral fields needed for correlation |
| Multiple deals per close | Define deterministic aggregation and idempotency rules before persistence changes |
| Missing historical deals | Treat bounded observation limits as insufficient evidence for closure; fail closed |
| Existing trades without provider identity | Define a safe migration/reconciliation policy; do not match by symbol alone without explicit authority |
| Closed-position resurrection | Ensure closed local references are not reopened or duplicated by later snapshots |
| Partial close accounting | Separate remaining open quantity from cumulative realized exit facts |
| JPA collection/orphan removal | Avoid destructive collection replacement and preserve existing entity identity |
| Schema compatibility | Add Flyway migration and validate fresh and upgraded PostgreSQL schemas |
| Kraken compatibility | Keep the provider-neutral contract optional/backward-compatible where Kraken facts are unavailable |
| Runtime safety | Keep all synchronization operations read-only and retain existing cTrader safety guard behavior |

## Validation Expectations

### Contract And Adapter Tests

- cTrader position ID maps to the neutral position reference.
- cTrader deal facts map without leaking cTrader DTOs.
- Position/deal correlation is preserved for a close event.
- Incomplete and stale observations retain their fail-closed semantics.
- No provider mutation method is called during snapshot synchronization.

### Trading Core Tests

- First import creates one durable provider-backed trade.
- Repeated identical snapshots update the same trade ID and do not duplicate.
- Quantity changes reconcile correctly.
- Partial close preserves an open trade with the remaining quantity.
- Full external close persists exit price, close time, realized PnL, and close
  reference.
- Missing position with incomplete/stale/unavailable deal facts does not close
  the local trade.
- A closed trade is not resurrected by repeated or later snapshots.
- Restart/reload preserves provider identity and closed lifecycle state.
- Legacy trades without provider references follow the approved safe policy.

### Persistence And Module Validation

- Fresh and upgrade Flyway validation for the `trades` schema.
- Existing `TradeRepository` analytics regressions.
- Existing Kraken and PAPER synchronization regressions.
- Affected `trading-core` and `broker-service` Maven tests.
- `git diff --check`.
- Architecture checks proving cTrader protocol types remain in Broker Service.

### Runtime Boundary

Only read-only synchronization evidence is permitted. The Story does not
authorize a cTrader order, cancel, amend, close, or other broker mutation.

## Documentation Reconciliation

The implementation is likely to affect the canonical Story and possibly
provider-neutral synchronization documentation if the snapshot contract or
operational behavior changes. ADR-074 should not be modified unless the
implementation reveals a contradiction with its accepted read-only boundary.
Any required ADR change must stop implementation and become a separate decision.

## Repository Analysis Conclusion

Story 0173 is implementable within the existing architecture, but it requires
coordinated changes in Broker Service contracts/mapping, Trading Core trade
persistence, synchronization logic, and migration/tests. The current code has
the provider position identity and observation freshness/completeness foundations
needed to proceed, but it lacks durable local identity and authoritative
position-to-deal lifecycle reconciliation.

The next workflow stage is an Implementation Plan. Implementation must not begin
until this Repository Analysis has been explicitly approved.

## Vault Outcome

The Obsidian vault was not consulted. Current repository source and accepted ADRs
were sufficient. No vault action is proposed.
