# Repository Analysis — Story 0033

## Story

0033 — Full Exposure Close

## Repository State

| Field | Value |
|---|---|
| Branch | `main` |
| HEAD | `c970aff` |
| Working tree | Clean (untracked: ADR-040.md, 0033 story dir only) |
| Story 0032 state | Merged (PR #28) |

## Governing ADRs

| ADR | Status | Story Impact | Implementation Alignment |
|---|---|---|---|
| ADR-001 | Accepted | Human authority: user is final decision maker | Aligned — explicit human confirmation required for close |
| ADR-014 | Accepted | Pipeline terminates with human validation | Aligned — close requires human validation gate |
| ADR-029 | Accepted | UNKNOWN semantics, reconciliation, no blind retry | Aligned — close inherits UNKNOWN/reconciliation model |
| ADR-030 | Accepted | Broker capability architecture, provider isolation | Aligned — new PositionManagementCapability follows existing pattern |
| ADR-040 | **Proposed** | Position Management Command Architecture | Governing ADR — defines close architecture |

---

## Current Architecture

### Microservice Topology

```
Gateway
  ├─ trading-core
  ├─ broker-service
  ├─ market-intelligence
  ├─ market-data
  └─ risk-domain
```

### Position Read Path (Existing)

```
Provider (Kraken OpenPositions API)
  → KrakenMapper.toPosition(key, value)
    → Position.brokerPositionId = txid
      → BrokerPositionFact.positionId
        → OpenPositionDashboardView.positionId
          → Angular positionId
```

This legacy path already propagates the Kraken txid end-to-end. **No change needed for the /positions page.**

### Capability Path (Existing — Needs Fix)

```
KrakenCapabilities.positions()
  → PositionSnapshot (MISSING brokerPositionReference)
    → (downstream projection)
```

**Gap:** `KrakenCapabilities.positions()` at line 30 drops the txid (map key) when creating `PositionSnapshot`. The fix is adding `brokerPositionReference` to the record and passing the txid.

---

## Existing Broker Service Components

### BrokerCapabilities.java

**Path:** `broker-service/src/main/java/com/hope/trading/broker_service/broker/domain/capability/BrokerCapabilities.java`

Existing capability interfaces:

| Interface | Responsibility |
|---|---|
| `AuthenticationCapability` | Verify broker account credentials |
| `AccountCapability` | Retrieve account snapshot |
| `PositionCapability` | List open positions as `List<PositionSnapshot>` |
| `OrderCapability` | List orders, cancel orders |
| `ExecutionCapability` | Execute trade orders |
| `ReconciliationCapability` | Reconcile execution outcomes |
| `RiskSnapshotCapability` | Risk snapshot retrieval |

**Story 0033 adds:** `PositionManagementCapability` (distinct from `PositionCapability`).

### BrokerModels.java

**Path:** `broker-service/src/main/java/com/hope/trading/broker_service/broker/domain/model/BrokerModels.java`

Key types:

| Type | Purpose | Story 0033 Change |
|---|---|---|
| `PositionSnapshot` | Position data projection | Add `brokerPositionReference` field |
| `ExecutionRequest` | Order execution request | No change |
| `ExecutionResult` sealed interface | Acknowledged / Rejected / Unknown | No change — close uses parallel `CloseResult` |
| `ReconciliationRequest` / `ReconciliationResult` | Execution reconciliation | No change — close uses separate reconciliation |

**New types needed:** `ResolveTargetRequest`, `ResolvedPositionCloseTarget`, `ExecuteCloseRequest`, `CloseResult` (sealed interface), `ReconcileCloseRequest`, `ReconciliationCloseResult`.

### KrakenCapabilities.java

**Path:** `broker-service/src/main/java/com/hope/trading/broker_service/broker/infrastructure/provider/kraken/capability/KrakenCapabilities.java`

Implements all 6 capability interfaces. **Does not implement `RiskSnapshotCapability`** (handled by separate `KrakenRiskSnapshotCapability`).

**Story 0033 change:** Add `PositionManagementCapability` implementation to `KrakenCapabilities` or to a new `KrakenPositionManagementCapability` class.

### KrakenBrokerProvider.java

**Path:** `broker-service/src/main/java/com/hope/trading/broker_service/broker/infrastructure/provider/kraken/KrakenBrokerProvider.java`

Routes capability requests by type:

```java
public <T> Optional<T> capability(Class<T> type) {
    if (type.isInstance(capabilities)) return Optional.of(type.cast(capabilities));
    if (type.isInstance(riskSnapshots)) return Optional.of(type.cast(riskSnapshots));
    return Optional.empty();
}
```

**Story 0033 change:** Register `PositionManagementCapability` so `capability(PositionManagementCapability.class)` returns the implementation.

### BrokerOperationServices.java

**Path:** `broker-service/src/main/java/com/hope/trading/broker_service/broker/application/service/BrokerOperationServices.java`

Inner `@Service` classes: `GetAccountService`, `GetPositionsService`, `GetOrdersService`, `ExecuteOrderService`, `CancelOrderService`, `ReconcileExecutionService`, `GetRiskSnapshotService`.

**Story 0033 adds:** `ResolveTargetService` and `ExecuteCloseService` inner classes.

### Broker ExecutionController.java (Broker Service)

**Path:** `broker-service/src/main/java/com/hope/trading/broker_service/broker/api/controller/ExecutionController.java`

Internal REST controller at `/internal/v1/executions`:
- `POST /` — execute order
- `POST /reconcile` — reconcile execution
- `POST /{externalOrderId}/cancel` — cancel order

**Story 0033 adds:** `PositionManagementController` at `/internal/v1/positions` with `resolve-target`, `execute-close`, and `reconcile-close` endpoints.

---

## Existing Trading Core Components

### ExecutionIntentEntity.java

**Path:** `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/persistence/ExecutionIntentEntity.java`

JPA entity pattern:
- UUID PK with `@GeneratedValue(strategy=UUID)`
- `@Version long version` for optimistic locking
- Public fields (no getters/setters)
- Unique constraints on `idempotency_key` and `(trade_plan_id, trade_plan_version)`
- Status as `VARCHAR(32)`

**Story 0033 follows same pattern** for `PositionCloseCommandEntity`.

### ValidateAndCreateService.java

**Path:** `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/ValidateAndCreateService.java`

15-step validation chain pattern:
1. Authentication
2. Ownership validation
3. Prerequisite validation (TradePlan, RiskEvaluation)
4. Parameter derivation from authoritative state
5. Persistence
6. Status transition

**Story 0033 adapts this pattern** for position close: validation is simpler (no TradePlan/RiskEvaluation), but includes scope reservation.

### RecoverExecutionService.java

**Path:** `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/RecoverExecutionService.java`

4-step pipeline pattern for reconciliation:
1. `ExecutionInspectionStep` — load current intent state
2. `RecoveryStrategyStep` — determine recovery strategy
3. `BrokerReconciliationStep` — call `BrokerExecutionClient.reconcile()`
4. `RecoveryFinalizationStep` — transition lifecycle

**Story 0033 reuses this pattern** for position close reconciliation, with a separate `PositionCloseReconcileService`. The pipeline concept is reused but the steps are simpler (no recovery strategy needed — just query provider state).

### ExecutionController.java (Trading Core)

**Path:** `trading-core/src/main/java/com/hope/trading/trading_core/execution/api/ExecutionController.java`

REST controller at `/executions`:
- `POST /validate` — validate and create intent
- `POST /{id}/execute` — execute trade
- `POST /{id}/reconcile` — reconcile
- `POST /recovery` — recover all

**Story 0033 adds:** `PositionCloseController` at `/api/v1/accounts/{accountId}/positions/close` — follows different URL convention (nested under accounts).

### PositionController.java (Trading Core)

**Path:** `trading-core/src/main/java/com/hope/trading/trading_core/controller/PositionController.java`

REST controller at `/api/v1/accounts/{accountId}/positions`:
- `GET /` — list positions
- Delegates to `BrokerApiClient` → `BrokerDashboardMapper`

**Story 0033 adds:** close/reconcile endpoints at `/api/v1/accounts/{accountId}/positions/close` — same base path.

### BrokerApiClient (Trading Core)

**Path:** `trading-core/src/main/java/com/hope/trading/trading_core/infrastructure/adapter/BrokerApiClient.java`

Feign client calling Broker Service public API (`/api/v1/broker-accounts/...`). **Story 0033 adds a separate Feign client** for internal Broker Service endpoints (`/internal/v1/positions/...`) — or extends the existing `BrokerConnectionCommandClient` which already calls internal paths.

### Flyway Migration Conventions

**Common migrations** (`trading-core/src/main/resources/db/migration/common/`):
- `V1__trading_core_clean_install_baseline.sql`
- `V2__trade_plan_risk_evaluation.sql`
- `V4__risk_acknowledgment_outbox.sql`
- `V5__trade_planning_profiles.sql`
- `V6__analysis_trade_plan_continuations.sql`
- `V7__accounts_unique_broker_per_user.sql`

**PostgreSQL-specific** (`trading-core/src/main/resources/db/migration/postgresql/`):
- `V3__enforce_immutable_risk_artifacts.sql`

**Next migration:** `V8` in common/ for `position_close_command` table. PostgreSQL partial unique index in `postgresql/V8__...sql`.

**Column conventions:** UUIDs default `gen_random_uuid()`, timestamps `TIMESTAMP WITH TIME ZONE NOT NULL`, optimistic locking `BIGINT NOT NULL DEFAULT 0`, status `VARCHAR(32) NOT NULL`, monetary `NUMERIC(30,12)`.

---

## Gateway Analysis

### Current Routes

**Path:** `gateway/src/main/java/com/hope/trading/gateway/config/GatewayRouteConfig.java`

| Route | Path | Target |
|---|---|---|
| `users` | `/api/v1/users/**` | trading-core |
| `accounts` | `/api/v1/accounts/**` | trading-core |
| `broker-accounts-credentials` | `/api/v1/broker-accounts/{id}/credentials\|validate\|connection-status` | broker-service |
| `broker-accounts` | `/api/v1/broker-accounts/**` | trading-core |
| `trade-plans` | `/api/v1/trade-plans/**` | trading-core |
| `opportunities` | `/api/v1/opportunities/**` | market-intelligence |
| `markets` | `/api/v1/markets/**` | market-data |
| `market-data-ws` | `/ws/market-data` | market-data-ws |
| `intelligence` | `/api/v1/intelligence/**` | market-intelligence |
| `executions` | `/api/v1/executions/**` | trading-core |

### Story 0033 Route Coverage

| Endpoint | Route Match | Change Required |
|---|---|---|
| `POST /api/v1/accounts/{accountId}/positions/close` | `accounts` → `/api/v1/accounts/**` | NO |
| `POST /api/v1/accounts/{accountId}/positions/close/{commandId}/reconcile` | `accounts` → `/api/v1/accounts/**` | NO |

**Evidence:** The `accounts` route at line 50-53 uses `/api/v1/accounts/**` wildcard. Story 0032 already proved this routes correctly (GET positions works through Gateway). No Gateway route change needed.

**Internal Feign calls:** Trading Core → Broker Service calls go through service discovery (Eureka), not Gateway. The existing `BrokerConnectionCommandClient` already calls `/internal/v1/...` paths via service discovery. Story 0033 follows the same pattern.

```
GATEWAY_CHANGE_REQUIRED = NO
```

---

## Angular /positions Architecture (Story 0032)

### Component Structure

**Path:** `trading-os-web/src/app/features/positions/pages/positions/`

| File | Lines | Purpose |
|---|---|---|
| `positions.ts` | 149 | Standalone component with reactive state |
| `positions.html` | 161 | Template with account selector + card grid |
| `positions.scss` | 278 | Dark theme styles |
| `positions.spec.ts` | 230 | Component tests |

### Reactive Data Flow

```typescript
BehaviorSubject<string | null> selectedAccountId$
  → combineLatest([accountService.accounts$, selectedAccountId$])
    → switchMap(timer(0, 10_000) → positionService.getPositions(accountId))
      → BehaviorSubject<ViewModel>
```

### PositionCard Display

Each card renders `OpenPositionDashboardView`:
- Symbol, side (Long/Short), quantity
- Entry/current price, unrealized PnL
- SL/TP levels, protection status
- Exposure and risk metrics
- Timestamps

### PositionService

**Path:** `trading-os-web/src/app/core/services/position.service.ts`

Single method: `getPositions(accountId)` → `GET /v1/accounts/{accountId}/positions`.

**Story 0033 adds:** `closePosition()`, `reconcileClose()`, `getCloseCommand()`.

### ExecutionService Pattern (Reference)

**Path:** `trading-os-web/src/app/core/services/execution.service.ts`

5 methods: `validate()`, `execute()`, `getExecution()`, `retry()`, `reconcile()`. Uses `@RequestHeader('Idempotency-Key')` pattern. **Story 0033 follows this pattern** for close/reconcile.

### OpenPositionDashboardView Model

**Path:** `trading-os-web/src/app/core/models/dashboard-summary.model.ts`

Fields: `positionId`, `symbol`, `side`, `quantity`, `entryPrice`, `currentPrice`, `unrealizedPnl`, `marketId`, `brokerAccountId`, `accountId`, `protectionStatus`, `exposure`, `risk`, `stopLossPrice`, `takeProfitPrice`, etc.

**`positionId` is the Kraken txid** — already propagated from legacy path. This becomes the `brokerPositionReference` for close.

---

## Reconciliation Patterns Reusable from Story 0031

### RecoverExecutionService Pipeline

```
inspection → strategy → reconciliation → finalization
```

- **Inspection:** Load current state, determine if recoverable
- **Strategy:** Choose recovery action based on state
- **Reconciliation:** Call broker-service reconcile endpoint
- **Finalization:** Transition lifecycle based on reconciliation result

### Story 0033 Reconciliation Adaptation

**Separate service:** `PositionCloseReconcileService` — not mixed into execution recovery.

**Simpler pipeline** (no strategy step needed):
1. Load command state (verify ACKNOWLEDGED or UNKNOWN)
2. Call broker-service reconcile endpoint (provider queries order state + refreshes positions)
3. Map provider evidence to `ReconciliationCloseResult`
4. Update command: set `reconciliation_result`, transition to CLOSED if `EXPOSURE_CONFIRMED_ABSENT`

**Broker Service reconcile endpoint:** New `PositionManagementCapability.reconcile()` — queries provider order state using stored `cl_ord_id` and refreshes `OpenPositions` to verify exposure state.

---

## Persistence / Flyway Conventions

### Table Pattern

```sql
CREATE TABLE {entity} (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    {foreign_keys}  UUID NOT NULL,
    {fields}        ...,
    status          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);
```

### Constraint Naming

```sql
CONSTRAINT ck_{entity}_{check}
CONSTRAINT uk_{entity}_{unique}
```

### Index Naming

```sql
idx_{entity}_{column}
```

### Next Migration

- **Common:** `V8__position_close_command.sql`
- **PostgreSQL:** `V8__position_close_command_partial_index.sql` (partial unique index)

---

## Concurrency Considerations

### PostgreSQL Partial Unique Index

```sql
CREATE UNIQUE INDEX uq_active_command_per_scope
ON position_close_command (broker_account_id, resolved_mutation_scope)
WHERE status IN ('CREATED', 'SUBMITTED', 'ACKNOWLEDGED', 'UNKNOWN');
```

**Guarantees:**
- At most one active command per broker account + mutation scope
- Survives concurrent HTTP requests, different idempotency keys, multiple Trading Core instances
- Does not rely on application-level locking
- TERMINAL states (REJECTED, CLOSED, NOT_SUBMITTED) excluded

### H2 Testing Limitation

H2 does not support PostgreSQL partial unique indexes. **Strategy:**
- H2 test profile uses full unique index on `(broker_account_id, resolved_mutation_scope)` — stricter but safe
- Dedicated PostgreSQL integration test validates the partial index behavior
- Concurrent test uses real database operations (`@SpringBootTest` + H2 or Testcontainers)

### Optimistic Locking

`PositionCloseCommandEntity` uses `@Version long version` — same pattern as `ExecutionIntentEntity`. Optimistic lock failure → retry or return conflict.

---

## Crash-Window Analysis

### A. Crash Before Reservation

```
PERSISTED_STATE    = nothing
FINANCIAL_RISK     = none
RECOVERY_PATH      = user retries from Angular (no active command blocks)
BLIND_RETRY_SAFE   = YES (no command exists)
```

### B. Crash After Reservation, Before Broker Submission

```
PERSISTED_STATE    = nothing (transaction rolls back)
FINANCIAL_RISK     = none
RECOVERY_PATH      = user retries from Angular (no active command blocks)
BLIND_RETRY_SAFE   = YES (no command exists)
```

**Implementation note:** The current implementation uses a single `@Transactional` method for the entire close flow. CREATED and SUBMITTED are intermediate states within the same transaction — they are not independently durable. If the transaction fails before commit, all intermediate database changes (including CREATED) roll back. This prevents orphaned CREATED states from blocking recovery.

### C. Crash During Broker Submission

```
PERSISTED_STATE    = position_close_command UNKNOWN (exception caught, transaction commits)
FINANCIAL_RISK     = broker may have accepted
RECOVERY_PATH      = user clicks Reconcile
BLIND_RETRY_SAFE   = NO
```

**Important:** A database rollback does NOT imply the broker command was not executed. If the broker call started before the crash, the financial mutation may have occurred.

### D. Broker Executes, HTTP Response Lost

```
PERSISTED_STATE    = position_close_command UNKNOWN (timeout exception caught, transaction commits)
FINANCIAL_RISK     = position may be closing
RECOVERY_PATH      = user clicks Reconcile; provider state queried
BLIND_RETRY_SAFE   = NO
```

**Important:** A database rollback does NOT imply the broker command was not executed. If the broker call completed before the crash, the financial mutation may have occurred.

### E. Crash After Broker ACK, Before Trading Core Persist

```
PERSISTED_STATE    = position_close_command ACKNOWLEDGED (transaction commits)
FINANCIAL_RISK     = broker accepted close
RECOVERY_PATH      = user clicks Reconcile → EXPOSURE_CONFIRMED_ABSENT → CLOSED
BLIND_RETRY_SAFE   = NO
```

---

## Story Artifact Conventions

### Reference Stories

| Story | Directory | Artifacts |
|---|---|---|
| 0030 | `0030-connect-risk-decision-to-human-controlled-execution/` | story.md, repository-analysis.md, implementation-plan.md, implementation-report.md, engineering-report.md, code-review.md |
| 0031 | `0031-close-the-execution-feedback-loop/` | story.md, repository-analysis.md, implementation-plan.md, implementation-report.md, engineering-report.md, code-review.md |
| 0032 | `0032-dedicated-open-positions-monitoring/` | story.md, repository-analysis.md, implementation-plan.md, implementation-report.md, engineering-report.md, code-review.md |

### Convention

- Directory: `docs/architecture/stories/{NNNN}-{kebab-case-title}/`
- Artifact files: lowercase kebab-case
- Lifecycle: story.md → repository-analysis.md → implementation-plan.md → implementation-report.md → engineering-report.md → code-review.md
- Heading: `# {Document Type} — Story NNNN`

---

## Implementation Constraints

### ADR-040 Status

```
ADR_040_STATUS = Proposed
IMPLEMENTATION_ALLOWED = NO
```

Story 0033 is defined against ADR-040 decisions. Implementation may proceed once ADR-040 reaches `Accepted`.

### Files to Modify (Summary)

| Module | File | Action |
|---|---|---|
| broker-service | `BrokerModels.java` | Add `brokerPositionReference` to `PositionSnapshot` |
| broker-service | `KrakenCapabilities.java` | Pass txid as `brokerPositionReference` |
| broker-service | `KrakenBrokerProvider.java` | Register `PositionManagementCapability` |
| broker-service | `BrokerCapabilities.java` | Add `PositionManagementCapability` interface |
| broker-service | `BrokerOperationServices.java` | Add inner service classes |
| trading-core | `V8__position_close_command.sql` (common) | New migration |
| trading-core | `V8__position_close_command_partial_index.sql` (postgresql) | New migration |
| trading-os-web | `position.service.ts` | Add close/reconcile methods |
| trading-os-web | `positions.ts` | Add close command handling |
| trading-os-web | `positions.html` | Add close UI |
| trading-os-web | `positions.scss` | Add close/command styles |

### Files to Create (Summary)

| Module | File | Purpose |
|---|---|---|
| broker-service | `PositionManagementCapability.java` | Capability interface |
| broker-service | `KrakenPositionManagementCapability.java` | Kraken adapter |
| broker-service | `PositionManagementController.java` | Internal REST controller |
| broker-service | `PositionCloseApiDtos.java` | Internal API DTOs |
| trading-core | `PositionCloseCommandEntity.java` | JPA entity |
| trading-core | `PositionCloseCommandRepository.java` | Spring Data repository |
| trading-core | `PositionCloseCommand.java` | Domain model |
| trading-core | `PositionCloseStatus.java` | Status enum |
| trading-core | `ReconciliationCloseResult.java` | Reconciliation result enum |
| trading-core | `PositionCloseLifecycleService.java` | State machine |
| trading-core | `PositionCloseService.java` | Application service |
| trading-core | `PositionCloseController.java` | REST controller |
| trading-core | `BrokerPositionCloseClient.java` | Feign client |
| trading-core | DTOs, ports, adapters | Supporting types |
| trading-os-web | `position-close.model.ts` | Angular models |

### Gateway

```
GATEWAY_CHANGE_REQUIRED = NO
```

Existing `accounts` route with `/api/v1/accounts/**` covers both new endpoints. Internal Feign calls use service discovery.

### Scope Exclusions

**NOT in scope:** partial close, SL/TP, trailing stop, automatic exit, AI recommendation, position history, bulk close, multi-account, cTrader, FTMO, prop-firm rules, persistent Position aggregate, streaming/WebSocket/SSE, event sourcing.
