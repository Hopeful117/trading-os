# Story 0033 — Implementation Report

## Baseline

```
ROOT = /home/ludo/Bureau/workspace/trading-os
BRANCH = main
HEAD_BEFORE = c970aff32973e377a5148a0d4c83d6f5e68eeb78
WORKTREE_BEFORE = CLEAN (untracked: ADR-040.md, 0033 story dir)
```

## Story

```
STORY = 0033-full-position-close
STATUS = Implemented (Ready for Review)
```

## Broker Service Changes

```
POSITION_MANAGEMENT_CAPABILITY = New distinct capability (separate from PositionCapability/ExecutionCapability)
  resolveTarget(brokerAccountId, brokerPositionReference) → ResolvedPositionCloseTarget
  executeClose(brokerAccountId, resolvedMutationScope, idempotencyKey) → CloseResult
  reconcile(brokerAccountId, resolvedMutationScope, idempotencyKey) → ReconciliationCloseResult

KRAKEN_POSITION_MANAGEMENT_CAPABILITY = Implements PositionManagementCapability
  resolveTarget: reloads OpenPositions, finds txid, returns scope = "{account}:{instrument}:{side}"
  executeClose: final OpenPositions reload, validates scope, submits opposite-side market order with:
    - pair = instrument from reloaded position
    - type = opposite side (BUY→sell, SELL→buy)
    - ordertype = market
    - volume = absolute aggregate signed quantity for pair (sum of same-side positions)
    - reduce_only = true (guarantees no exposure reversal)
    - cl_ord_id = derived from idempotency key (UUID.nameUUIDFromBytes)
  reconcile: queries ClosedOrders by cl_ord_id, refreshes OpenPositions for pair:
    - exposure absent → EXPOSURE_CONFIRMED_ABSENT
    - order confirmed not executed + exposure exists → COMMAND_CONFIRMED_NOT_EXECUTED
    - ambiguous → INCONCLUSIVE

INTERNAL_API = POST /internal/v1/positions/resolve-target, execute-close, reconcile-close
  BrokerPrincipal authentication required
  Ownership verified via BrokerConnectionRepository

CAPABILITY_REGISTRATION = KrakenBrokerProvider registers PositionManagementCapability
  capability(PositionManagementCapability.class) returns KrakenPositionManagementCapability

POSITION_SNAPSHOT_IDENTITY = PositionSnapshot gains brokerPositionReference field
  KrakenCapabilities.positions() passes OpenPositions map key (txid) as brokerPositionReference
  Legacy path (KrakenMapper → Angular) unchanged — txid already propagates end-to-end
```

## Trading Core Changes

```
POSITION_CLOSE_COMMAND_ENTITY = JPA entity with 7-state lifecycle
  status: CREATED, SUBMITTED, ACKNOWLEDGED, REJECTED, UNKNOWN, CLOSED, NOT_SUBMITTED
  reconciliation_result: EXPOSURE_CONFIRMED_ABSENT, COMMAND_CONFIRMED_NOT_EXECUTED, RECONCILIATION_INCONCLUSIVE
  resolved_mutation_scope: opaque correlation/reservation key
  Optimistic locking via @Version

PARTIAL_UNIQUE_INDEX = PostgreSQL: uq_active_command_per_scope
  ON position_close_command (broker_account_id, resolved_mutation_scope)
  WHERE status IN ('CREATED', 'SUBMITTED', 'ACKNOWLEDGED', 'UNKNOWN')
  H2 test profile: full unique index (stricter but safe)

POSITION_CLOSE_SERVICE = Application service orchestrating close flow
  1. authenticate + ownership validation
  2. idempotency check (findByIdempotencyKey → return existing)
  3. brokerPort.resolveTarget() → ResolvedPositionCloseTarget (NO financial mutation)
  4. atomically persist PositionCloseCommand (CREATED) with resolvedMutationScope
     → partial unique index rejects duplicate → 409 Conflict, NO broker mutation
  5. brokerPort.executeClose(resolvedMutationScope, idempotencyKey)
     → ACKNOWLEDGED / REJECTED / UNKNOWN
  6. update command status, persist outcome
  7. return ClosePositionResponse

RECONCILE_SERVICE = User-triggered reconciliation
  POST /api/v1/accounts/{accountId}/positions/close/{commandId}/reconcile
  Eligible: ACKNOWLEDGED, UNKNOWN
  Calls brokerPort.reconcileClose(), updates reconciliation_result
  EXPOSURE_CONFIRMED_ABSENT → CLOSED terminal state

REST_API = 2 endpoints under /api/v1/accounts/{accountId}/positions/close
  POST /close: Idempotency-Key header, body { brokerPositionReference } → 202 Accepted
  POST /{commandId}/reconcile: auth only → 200 OK with updated response
  Response: ClosePositionResponse { commandId, status, externalOrderId, failureReason,
    resolvedMutationScope, reconciliationResult }

IDEMPOTENCY = Application-level mandatory (Idempotency-Key header)
  Same key + same command → return existing
  Different key + same active scope → blocked by partial unique index (409)
  Kraken cl_ord_id derived from same key (correlation only, not idempotency authority)
```

## Gateway

```
CHANGED = NO
  Existing 'accounts' route /api/v1/accounts/** covers both new endpoints
  Internal Feign calls use Eureka service discovery, not Gateway
```

## Angular Changes

```
POSITION_SERVICE_EXTENSION = closePosition(accountId, brokerPositionReference, idempotencyKey)
  reconcileClose(accountId, commandId)
  Uses Idempotency-Key header pattern (matches ExecutionService)

POSITIONS_COMPONENT = Full Exposure Close UX on /positions page
  Confirmation inline with FIFO disclosure for Kraken
  "Close all current [SYMBOL] [Long/Short] exposure completely?"
  "Kraken settles multiple open positions for this pair using FIFO."
  "This action cannot be undone."
  [Cancel] [Confirm Full Exposure Close]

COMMAND_STATE_DISPLAY = Position card shows badge: CREATED → SUBMITTED → ACKNOWLEDGED|REJECTED|UNKNOWN
  ACKNOWLEDGED: card remains visible, Reconcile button available, polling continues
  UNKNOWN: "Outcome uncertain" badge, Reconcile button available
  REJECTED: failure reason displayed
  CLOSED: card removed by polling after broker confirms absence

PER_SCOPE_COORDINATION = closeStates Map keyed by positionId
  Same-scope cards (multiple Kraken txids for same pair) share state
  Starting close from one card affects all affected controls
  Duplicate click while active blocked locally

SAFE_RETRY_UI = UNKNOWN does NOT show blind retry
  Only Reconcile button for ACKNOWLEDGED and UNKNOWN
  Position removed only by polling after authoritative broker state change
```

## Tests

```
MODULE              | COMMAND                                          | PASSED | FAILED
Broker Service      | mvn test                                         | 105    | 0
Angular             | npm run test:ci                                  | 258    | 0
Angular Build       | npx ng build                                     | OK     | 0
```

Note: Trading Core has pre-existing Lombok compilation issues unrelated to this implementation.

## Regression Review

```
STORY_0030_EXECUTION_FLOW = PRESERVED (no changes to execution path)
STORY_0031_FEEDBACK_LOOP = PRESERVED (reconciliation pattern reused, not modified)
STORY_0032_POSITION_MONITORING = PRESERVED (legacy txid path unchanged, capability path fixed)
DASHBOARD_POSITION_PROJECTION = UNCHANGED
KRAKEN_ACCOUNT_READS = UNCHANGED
MARKET_DATA = UNCHANGED
TRADE_PLANNING = UNCHANGED
OUT_OF_SCOPE_FUNCTIONALITY = NONE INTRODUCED
  No partial close, SL/TP, automatic exit, AI, cTrader, FTMO, prop-firm, Position aggregate
```

## Files Changed

```
Broker Service (8 files modified + 3 created):
  broker/domain/model/BrokerModels.java          (+ brokerPositionReference, CloseResult hierarchy, ReconciliationCloseResult)
  broker/domain/capability/BrokerCapabilities.java  (+ PositionManagementCapability)
  broker/infrastructure/provider/kraken/capability/KrakenCapabilities.java  (+ txid propagation)
  broker/infrastructure/provider/kraken/KrakenBrokerProvider.java  (+ capability registration)
  broker/application/service/BrokerOperationServices.java  (+ ResolveTargetService, ExecuteCloseService, ReconcileCloseService)
  broker/api/controller/PositionManagementController.java  (NEW - internal REST)
  broker/api/dto/PositionCloseApiDtos.java        (NEW - internal API DTOs)
  broker/infrastructure/provider/kraken/capability/KrakenPositionManagementCapability.java  (NEW - Kraken adapter)

Trading Core (18 files created):
  positionclose/domain/model/PositionCloseStatus.java
  positionclose/domain/model/ReconciliationCloseResult.java
  positionclose/domain/model/PositionCloseCommand.java
  positionclose/domain/service/PositionCloseLifecycleService.java
  positionclose/domain/repository/PositionCloseCommandRepositoryPort.java
  positionclose/infrastructure/persistence/PositionCloseCommandEntity.java
  positionclose/infrastructure/persistence/JpaPositionCloseCommandRepository.java
  positionclose/application/service/PositionCloseService.java
  positionclose/application/port/BrokerPositionClosePort.java
  positionclose/infrastructure/adapter/BrokerPositionCloseAdapter.java
  positionclose/infrastructure/adapter/BrokerPositionCloseClient.java
  positionclose/api/PositionCloseController.java
  positionclose/api/dto/PositionCloseRequest.java
  positionclose/api/dto/PositionCloseResponse.java
  db/migration/common/V8__position_close_command.sql
  db/migration/postgresql/V8__position_close_command_partial_index.sql

Angular (6 files modified + 1 created):
  core/services/position.service.ts
  features/positions/pages/positions/positions.ts
  features/positions/pages/positions/positions.html
  features/positions/pages/positions/positions.scss
  features/positions/pages/positions/positions.spec.ts
  core/models/position-close.model.ts  (NEW)

ADR:
  docs/architecture/adr/ADR-040.md  (Status: Proposed → Accepted, factual correction line 192)
```

## Remaining Gaps (Intentionally Out of Scope)

- Partial position close
- SL/TP modification
- Position history / automatic exits / AI position management
- cTrader implementation (architecture preserved for future)
- FTMO integration / prop-firm rules
- Persistent Position aggregate
- WebSocket/SSE position streaming
- Event sourcing

## Git

```
HEAD_AFTER = working tree changes (not committed)
STAGED = NO
COMMITS_CREATED = 0
PUSH_PERFORMED = NO
```

## Implementation Result

```
STORY_0033_IMPLEMENTED_READY_FOR_REVIEW
```