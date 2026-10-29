# Implementation Plan - Story 0173

## Status

`READY_FOR_HUMAN_APPROVAL`

Implementation is not authorized by this plan until the human engineer
explicitly approves it.

## Overview

Replace destructive open-position replacement with durable, provider-neutral
reconciliation keyed by the broker position reference. Extend the existing
authoritative snapshot contract only where required to correlate a close deal
to its position, persist the identity and exit facts in Trading Core, and keep
all cTrader protocol details inside Broker Service.

The implementation remains read-only against brokers. It will not invoke the
existing position-close mutation path or any cTrader order capability.

## Design Decisions

### Identity

- `brokerPositionReference` is the durable identity for a provider-backed
  position.
- Identity is scoped by the local Trading Core `Account`, not globally across
  all providers or accounts.
- Existing trades without a broker reference are not matched by symbol, side,
  quantity, or entry price. They remain legacy/local records until an explicit
  safe policy is established by the implementation tests.
- A closed trade retaining a broker reference is terminal for reconciliation.
  A later snapshot with that reference is not imported or reopened. A provider
  reference collision with a closed trade is treated as a reconciliation
  conflict and does not mutate the closed trade.

### Close Facts

- Preserve the existing `exitPrice`, `closedAt`, and `pnl` fields for the
  established closed-trade API behavior.
- Add a dedicated cumulative `realizedPnl` field so partial close realization
  is not confused with open-position unrealized PnL.
- Persist the closing broker deal/provider reference separately from the
  position reference. If multiple deals close one position, retain a stable
  deterministic aggregate reference or the last authoritative close reference
  according to the finalized contract tests.
- For a full close, set `exitPrice` to the authoritative quantity-weighted
  average close price, `closedAt` to the authoritative final close timestamp,
  `realizedPnl` to the aggregate authoritative realized PnL, and `pnl` to the
  same realized total for compatibility with existing closed-trade analytics.
- For a partial close, reduce the open quantity and accumulate realized PnL,
  but leave the trade `OPEN`, `closedAt`, and `exitPrice` unset until the final
  close is proven.

### Authority And Fail-Closed Rules

- Position updates require a complete and fresh snapshot.
- A missing position is never sufficient by itself to close a local trade.
- A full external close requires both:
  - complete and fresh provider observation proving the position is absent; and
  - one or more correlated authoritative close deals whose quantities account
    for the remaining local quantity.
- If the provider payload cannot provide a verified position-to-deal
  correlation, the deal remains non-correlating and the local trade stays open.
- Stale, incomplete, unavailable, contradictory, or identity-mismatched facts
  must not remove, close, or recreate local trades.

## Phase 1 - Confirm And Extend Provider-Neutral Deal Correlation

1. Verify the cTrader deal payload field that identifies the affected position
   for close deals using the existing protocol mapping and official provider
   contract. Do not infer correlation from instrument, side, timestamp, or
   account alone.
2. Extend `CTraderReadOnlyObservation.Deal` with the verified optional provider
   position reference when cTrader supplies it.
3. Extend `BrokerModels.ClosedTrade` and
   `BrokerDealSnapshotV2Dto` with the broker-neutral position reference while
   retaining the existing provider deal/trade reference.
4. Map the field in `JsonWebSocketCTraderOpenApiClient` and
   `CTraderAuthoritativeSnapshotCapability`.
5. Preserve the existing behavior for providers that cannot supply the field;
   their deals must not be used to prove a cTrader position close.
6. Add adapter and contract tests for correlated, uncorrelated, incomplete,
   and repeated deal facts.

Likely areas:

- `broker-service/.../ctrader/authentication/JsonWebSocketCTraderOpenApiClient`
- `broker-service/.../ctrader/synchronization/CTraderReadOnlyObservation`
- `broker-service/.../ctrader/synchronization/CTraderAuthoritativeSnapshotCapability`
- `broker-service/.../broker/domain/model/BrokerModels`
- `trading-core/.../broker/dto/BrokerDealSnapshotV2Dto`

## Phase 2 - Persist Durable Trade Identity And Exit Facts

1. Add `brokerPositionReference` to `Trade`, scoped through its account.
2. Add `realizedPnl` and the provider close/deal reference needed for durable
   close provenance.
3. Update `TradeDto` and `TradeMapper` only if the existing public trade API
   intentionally exposes these facts; do not expose provider-specific payloads.
4. Add a common Flyway migration after the current Trading Core migration
   sequence, expected as `V43__durable_broker_position_reconciliation.sql`:
   - add nullable columns for existing rows;
   - preserve all existing trade records;
   - add a scoped index on `(account_id, broker_position_reference)`;
   - enforce uniqueness for non-null provider references where supported by the
     repository's migration conventions.
5. Add fresh-schema and upgrade-schema validation coverage with existing
   `ddl-auto=validate` and Flyway configuration.
6. Add repository methods for account-scoped provider-reference lookup if the
   reconciliation service cannot safely operate from the account aggregate.

Likely areas:

- `trading-core/.../model/Trade`
- `trading-core/.../repository/TradeRepository`
- `trading-core/.../helper/TradeMapper`
- `trading-core/.../dto/TradeDto`
- `trading-core/src/main/resources/db/migration/common/V43__...sql`

## Phase 3 - Replace Destructive Synchronization

1. Keep the current owner, provider-registration, broker-account, external
   account, completeness, and freshness checks unchanged.
2. Load existing account trades and index them by non-null
   `brokerPositionReference`.
3. For every provider position:
   - match an existing open trade by account-scoped reference;
   - update provider-owned current facts in place while preserving local trade
     identity and execution/provenance fields;
   - import an unknown provider reference once as a new open trade;
   - skip a reference belonging to a closed trade and record a conflict outcome
     without reopening it;
   - never match a legacy null-reference trade by heuristics.
4. Compare complete/fresh provider positions with local open trades that have a
   provider reference.
5. For positions absent from the current provider set, select only correlated
   close deals and apply deterministic quantity/exit aggregation.
6. Keep a local trade open when the provider facts do not prove the full close.
7. Persist changes through the existing transaction and account aggregate,
   avoiding `removeIf` replacement and orphan-removal side effects.
8. Recalculate account values without treating realized partial-close facts as
   current unrealized PnL.
9. Preserve the legacy username synchronization behavior unless shared model
   changes require a compatibility update; it must not regress existing Kraken
   tests.

Likely areas:

- `trading-core/.../broker/service/BrokerSynchronizationServiceImpl`
- `trading-core/.../model/Account`
- `trading-core/.../repository/TradeRepository`
- synchronization tests and trade persistence tests

## Phase 4 - Idempotency, Restart, And No-Resurrection Behavior

1. Ensure repeated snapshots update the same JPA trade row and do not append a
   duplicate entity.
2. Ensure repeated deal lists do not double-count quantity or realized PnL.
3. Ensure a persisted closed trade remains closed after reload and after later
   snapshots containing the same provider reference.
4. Ensure a stale/incomplete snapshot leaves the durable local state unchanged
   for destructive transitions while still allowing safe non-destructive
   observation handling according to the final contract.
5. Use optimistic locking or an explicit deterministic conflict outcome if
   concurrent synchronization can update the same account.
6. Add tests covering repeated synchronization, repeated deals, persistence
   reload, partial close followed by final close, and closed-reference
   collision.

## Phase 5 - Documentation Reconciliation

1. Update the Story artifacts with implementation status, changed files, and
   final validation evidence after implementation.
2. Update canonical synchronization documentation only if the public contract,
   operator behavior, or persistence expectations changed.
3. Do not modify ADR-071, ADR-074, or ADR-078 unless implementation proves a
   material contradiction; such a contradiction must stop implementation and
   require a separate architecture decision.
4. Record that no broker mutation or runtime cTrader write was performed.

## Phase 6 - Validation

### Focused Tests

- `CTraderReadOnlyObservation` and cTrader mapper contract tests.
- `CTraderAuthoritativeSnapshotCapabilityTest` for stable position and deal
  correlation.
- `BrokerSynchronizationServiceImplTest` for create, update, partial close,
  full external close, stale/incomplete protection, idempotency, and no
  resurrection.
- Trade repository/entity tests for migration fields and reload behavior.
- Existing `BrokerPositionCloseServicesTest` and position-close adapter tests
  to prove synchronization does not invoke mutation paths.

### Regression And Module Checks

- Full `broker-service` tests.
- Full `trading-core` tests.
- PostgreSQL persistence/migration tests applicable to the changed schema.
- Existing Kraken and PAPER behavior tests.
- Architecture-boundary checks for provider-neutral Trading Core contracts.
- `git diff --check`.

### Runtime Boundary

Only authenticated, read-only account synchronization may be exercised after
implementation. No cTrader order, position close, cancellation, amendment, or
other broker mutation is authorized by this plan.

## Expected Deliverables

- Provider-neutral position/deal contract changes.
- cTrader read-only mapping changes for verified deal correlation.
- Durable Trading Core trade identity and exit-fact persistence.
- Flyway migration and schema validation coverage.
- Non-destructive synchronization implementation.
- Focused and regression tests.
- Updated Story implementation artifacts and implementation report.

## Risks And Stop Conditions

Stop and request human guidance if:

- official cTrader payload semantics cannot prove position-to-deal correlation;
- implementing the close contract requires changing an accepted ADR;
- existing Kraken or PAPER semantics require a provider-specific branch in
  Trading Core;
- migration compatibility for existing trades cannot be established safely;
- provider facts are insufficient to distinguish partial from full close;
- any validation would require a broker mutation or real account operation.

## Implementation Preconditions

Implementation is not authorized until this Implementation Plan is explicitly
approved. Approval does not authorize any cTrader broker mutation or runtime
trade operation.
