# Story 0033 — Full Exposure Close

## Metadata

**ID:** `0033`

**Title:** Full Exposure Close

**Status:** Draft

---

## Goal

After monitoring an open position through the `/positions` page, the authenticated user can explicitly request a **full exposure close** for the selected broker position's exposure scope.

The command must:
- target the exposure scope via an opaque broker position reference (read identity);
- never increase or reverse exposure;
- never trust stale frontend quantity or side;
- follow the proven `ACKNOWLEDGED / REJECTED / UNKNOWN` + reconciliation safety model;
- require explicit human confirmation;
- for Kraken: close all current exposure for the pair using provider-native FIFO semantics;
- for future cTrader: close the specific command-addressable position by `positionId`.

---

## Context

Story 0030 established human-controlled execution.
Story 0031 closed the execution feedback loop.
Story 0032 added dedicated open-position monitoring.

The current lifecycle is:

```text
Market Intelligence
        ↓
Opportunity
        ↓
Trade Plan
        ↓
Risk
        ↓
Human Execution
        ↓
Execution Feedback
        ↓
Open Position Monitoring
```

Story 0033 extends this with:

```text
Open Position Monitoring
        ↓
Full Exposure Close
```

The governing architectural decision is ADR-040 (Position Management Command Architecture). That ADR is currently `Proposed`; this Story is defined against its decisions and may proceed to implementation once ADR-040 reaches `Accepted` under normal repository workflow.

---

## Problem

Four concrete issues prevent safe position close:

**1. No Close Capability:** The `/positions` page is read-only. The user sees the position but cannot act on it.

**2. Frontend State Is Stale:** The page polls every 10 seconds. The displayed quantity and side may no longer match broker-authoritative state.

**3. Provider Semantics Differ:** Kraken closes via opposite-side market order with `reduce_only=true` at the **pair/exposure level with mandatory FIFO** — it cannot target an individual OpenPositions txid. cTrader uses an explicit `ProtoOAClosePositionReq` targeting `positionId`. Trading Core must not know either mechanism.

**4. Exposure-Reversal Risk:** Without provider-level safeguards, an opposite-side order for excess quantity could flip a long to short (or vice versa).

**5. UNKNOWN Must Not Retry:** A timeout on a close command could mean the broker already executed it. Blind retry could create an opposite position.

**6. Identity ≠ Command Addressability (Kraken):** The Kraken OpenPositions `txid` provides read identity for display, but is NOT independently command-addressable. Selecting one displayed position and requesting "full close" will close the **entire current exposure for that pair via FIFO**, potentially affecting multiple displayed position entries.

---

## Scope

### In scope

- Fix capability-based `PositionSnapshot` to preserve `brokerPositionReference` (txid)
- Add `PositionManagementCapability` with distinct `resolveTarget` and `executeClose` operations to Broker Service capability architecture
- Implement Kraken provider close using `reduce_only=true` on opposite-side market order guaranteeing no exposure reversal — operates at pair/exposure level with FIFO
- Add two-phase broker-neutral close port between Trading Core and Broker Service (resolution + execution)
- Add Trading Core REST endpoint: `POST /api/v1/accounts/{accountId}/positions/close`
- Command lifecycle with application-level idempotency
- `ACKNOWLEDGED / REJECTED / UNKNOWN` outcomes with explicit user-triggered reconciliation for both ACKNOWLEDGED and UNKNOWN
- Angular: explicit human confirmation on `/positions` page with provider-accurate scope description
- Angular: command state reflecting resolved close scope (created / submitted / acknowledged / rejected / unknown)
- Angular: reconcile button for ACKNOWLEDGED and UNKNOWN states
- Position polling continues; reconciliation confirms convergence
- Ownership defense in depth: Trading Core + Broker Service
- Focused tests for: scope resolution, execution, ownership, idempotency, authoritative reload, exposure-reversal safety, UNKNOWN handling, reconciliation, FIFO-aware Kraken behavior, multi-position same-symbol discrimination

### Out of scope

- partial close
- SL modification
- TP modification
- trailing stop
- automatic exit
- AI recommendation
- position history
- bulk close / close all
- multi-account management
- cTrader implementation
- FTMO integration
- prop-firm rules
- new persistent Position aggregate
- position streaming / WebSocket / SSE
- event sourcing

---

## Acceptance Criteria

* [ ] AC1: Authenticated owner can initiate **FULL EXPOSURE CLOSE** for one open position from `/positions`.

* [ ] AC2: Full Exposure Close requires explicit human confirmation (modal or inline confirmation step).

* [ ] AC3: The frontend sends the opaque brokerPositionReference of the observed position that initiated the action. Broker Service uses it only as a broker-authoritative resolution handle. The actual mutation target is resolved server-side according to provider command addressability — not account + instrument.

* [ ] AC4: Frontend quantity and side are **not** authoritative command inputs. They are informational only.

* [ ] AC5: Scope resolution happens before financial mutation. Phase 1 (resolveTarget) performs zero financial mutation. Phase 2 (executeClose) is called only after atomic scope reservation succeeds.

* [ ] AC6: If the target exposure no longer exists during scope resolution, **no broker mutation occurs**; user receives a clear non-success outcome (NOT_SUBMITTED).

* [ ] AC7: Kraken provider executes the close using an opposite-side market order with `reduce_only=true`, which **guarantees no exposure increase/reversal**.

* [ ] AC8: For Kraken with multiple same-symbol positions: the operation closes the **full current exposure for the pair** using FIFO; the UI does not promise independent targeting of a single OpenPositions txid.

* [ ] AC9: Application-level idempotency prevents duplicate financially equivalent close commands. Idempotency key is transported via `Idempotency-Key` HTTP header only.

* [ ] AC10: Provider acknowledgement, rejection, and uncertain outcome remain distinguishable (`ACKNOWLEDGED / REJECTED / UNKNOWN`).

* [ ] AC11: `UNKNOWN` does not trigger blind retry. A new command becomes safe only when broker-authoritative evidence establishes the prior command was not executed and the exposure still exists.

* [ ] AC12: Both `ACKNOWLEDGED` and `UNKNOWN` outcomes are resolved through explicit user-triggered reconciliation (POST endpoint), not automatic background workers.

* [ ] AC13: The UI communicates command state without optimistically deleting broker-authoritative position state.

* [ ] AC14: Angular position polling remains authoritative for the displayed open-position list. Position disappearance shown by polling is visual but does NOT silently mutate persisted command state.

* [ ] AC15: Trading Core remains free of Kraken-specific close mechanics (`reduce_only`, `cl_ord_id` not visible).

* [ ] AC16: `TradePlan` and entry `RiskEvaluation` are **not** prerequisites for Full Exposure Close.

* [ ] AC17: No partial close, SL/TP management, autonomous close, cTrader or FTMO work is introduced.

* [ ] AC18: Command target resolution is server-side: brokerPositionReference is a resolution handle; Broker Service resolves the provider-authoritative mutation scope. Trading Core remains free of provider-specific scope resolution.

* [ ] AC19: In-flight command protection is keyed by resolved mutation scope, not by individual brokerPositionReference. Two concurrent equivalent commands for the same mutation scope produce at most one broker mutation.

* [ ] AC20: ACKNOWLEDGED does not mean exposure confirmed closed. The command remains ACTIVE until explicit reconciliation confirms exposure absence.

* [ ] AC21: Reconciliation terminology reflects observable evidence and does not overclaim causality. Exposure absent after reconciliation does not necessarily prove our command caused the absence.

* [ ] AC22: Fresh target absence at resolution time is distinct from post-reconciliation exposure absence. Fresh absence results in no broker mutation and NOT_SUBMITTED.

* [ ] AC23: All UI entries belonging to the active mutation scope reflect that close command appropriately (same-scope Kraken cards cannot independently submit another active close).

* [ ] AC24: Kraken Full Exposure Close operates at full current pair/exposure scope using FIFO semantics. The resolved mutation scope is pair-level aggregate, not individual txid.

* [ ] AC25: Reconciliation is triggered by explicit user action via `POST /api/v1/accounts/{accountId}/positions/close/{commandId}/reconcile`. Automatic background reconciliation is not introduced.

* [ ] AC26: Concurrency guard uses a PostgreSQL partial unique index on `(broker_account_id, resolved_mutation_scope)` WHERE status IN active states. At most one active command per mutation scope is enforced at the database level.

* [ ] AC27: ClosePositionResponse includes `resolvedMutationScope` (opaque) and `reconciliationResult` fields.

* [ ] AC28: Command lifecycle uses 7 persisted states (CREATED, SUBMITTED, ACKNOWLEDGED, REJECTED, UNKNOWN, CLOSED, NOT_SUBMITTED). Reconciliation outcomes are stored as a separate `reconciliation_result` dimension.

* [ ] AC29: Scope resolution and financial execution are separate semantic operations. Resolution returns an opaque scope; execution consumes a reserved scope. No combined operation exists.

* [ ] AC30: Unresolved financially equivalent commands cannot be replaced or cancelled by a different idempotency key or another brokerPositionReference resolving to the same scope. 409 Conflict is returned.

* [ ] AC31: Final provider revalidation during execution must not reinterpret the scope into a different exposure scope. If scope semantics are no longer safe, a safe non-mutation outcome is returned.

* [ ] AC32: `resolvedMutationScope` is opaque. Angular treats it as an opaque correlation key. No provider-specific values are exposed.

* [ ] AC33: `cl_ord_id` (Trading OS supplied client correlation ID) and `txid` (Kraken provider-generated identifier) are distinct and never conflated.

* [ ] AC34: Legacy frontend position identity propagation is already present. Story 0033 does NOT implement new frontend identity propagation — only the capability-based `PositionSnapshot` fix.

---

## Constraints

- Preserve existing position architecture (broker-authoritative external state projected through `BrokerPositionFact`).
- Respect ADR-001 (human authority), ADR-014 (decision pipeline), ADR-029 (execution UNKNOWN/reconciliation), ADR-030 (broker capability architecture), ADR-040 (position management command architecture).
- Trading Core owns user intent and business safety; Broker Service owns provider translation.
- Angular must not call Broker Service directly.
- Position data is sensitive; cross-user position exposure is unacceptable.
- Do not introduce a new persistent Position aggregate.
- Do not introduce AI, recommendations, or monitoring agents.
- Do not commit, push, or merge automatically.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-001.md` — Trading OS Vision (human authority)
* `docs/architecture/adr/ADR-014.md` — Trading Decision Pipeline
* `docs/architecture/adr/ADR-029.md` — Execution Domain Architecture (UNKNOWN, reconciliation)
* `docs/architecture/adr/ADR-030.md` — Broker Service Architecture (capability pattern)
* `docs/architecture/adr/ADR-040.md` — Position Management Command Architecture

---

## Relevant Modules

* `broker-service` — `PositionManagementCapability`, Kraken close adapter, internal API
* `trading-core` — position close port/adapter, application service, REST controller, command lifecycle/persistence if needed
* `trading-os-web` — Angular `/positions` page extension, confirmation UX, command state, position service extension

---

## Repository Baseline

```text
ROOT = /home/ludo/Bureau/workspace/trading-os
BRANCH = main
HEAD = c970aff
WORKTREE = clean (untracked: ADR-040.md, 0033 story dir)
STORY_0032_STATE = merged (PR #28)
ADR_040_STATUS = Proposed
```

---

## Position Authority (from ADR-040)

```text
POSITION_AUTHORITY = broker-authoritative external trading state
POSITION_SOURCE = Broker Service → PositionCapability → List<PositionSnapshot>
APPLICATION_PROJECTION = OpenPositionDashboardView (reuse existing)
PERSISTED = no (positions are live broker state, not persisted in Trading Core)
```

---

## Position Identity Changes

ADR-040 requires an opaque broker position reference used as read identity / resolution handle.

**Two read paths exist — they differ:**

```text
LEGACY PATH (active /positions page — ALREADY WORKS):
    KrakenMapper.toPosition(key, value) → Position.brokerPositionId (= txid)
    → BrokerPositionFact.positionId → OpenPositionDashboardView.positionId → Angular positionId
    STATUS: txid ALREADY propagates end-to-end. No change needed for the /positions page.

CAPABILITY PATH (new broker capability architecture — NEEDS FIX):
    KrakenCapabilities.positions() → BrokerModels.PositionSnapshot
    STATUS: PositionSnapshot MISSING brokerPositionReference. KrakenCapabilities.positions() line 30
    drops the txid (map key) when creating PositionSnapshot.
```

**KRAKEN_POSITION_REFERENCE_SOURCE:** OpenPositions API response map key (txid string, e.g., `TF5GVO-T7ZZ2-6NBKBI`)

**Required changes (capability path only):**
1. `BrokerModels.PositionSnapshot` add `String brokerPositionReference`
2. `KrakenCapabilities.positions()` pass txid as `brokerPositionReference`
3. `BrokerDashboardMapper.toPositionFact` already passes `position.getBrokerPositionId()` → `BrokerPositionFact.positionId` (no change)
4. `OpenPositionDashboardView.positionId` already propagated (no change)
5. Angular `OpenPositionDashboardView.positionId` already available (no change)

**Impact on Story 0033:** The `/positions` page already displays the txid as `positionId`. The close command uses this existing `positionId` value as the `brokerPositionReference` resolution handle. No frontend identity propagation work is required — the gap is only in the internal capability-based PositionSnapshot contract.

**IMPORTANT:** Story 0033 does NOT implement new frontend position identity propagation. The legacy path already does this. The only identity change is fixing the capability-based `PositionSnapshot` to preserve the txid.

---

## New Capability: PositionManagementCapability

Following existing capability naming (`PositionCapability`, `ExecutionCapability`, `OrderCapability`):

```java
public interface PositionManagementCapability {
    ResolvedPositionCloseTarget resolveTarget(ResolveTargetRequest request);
    CloseResult executeClose(ExecuteCloseRequest request);
}
```

**Resolution operation — NO financial mutation:**

```java
public record ResolveTargetRequest(
    UUID brokerAccountId,
    String brokerPositionReference  // opaque read identity / resolution handle
) {}

public record ResolvedPositionCloseTarget(
    UUID brokerAccountId,
    String resolvedMutationScope    // opaque application-level correlation key
) {}
```

The resolution operation validates the selected read identity against fresh broker state and returns the provider-authoritative mutation scope. It performs zero financial mutation. It is safe to call repeatedly.

**Execution operation — financial mutation:**

```java
public record ExecuteCloseRequest(
    UUID brokerAccountId,
    String resolvedMutationScope,   // opaque, from resolution phase
    String idempotencyKey
) {}

public sealed interface CloseResult permits Acknowledged, Rejected, Unknown {}
public record Acknowledged(String externalOrderId, String correlationId) implements CloseResult {}
public record Rejected(String externalOrderId, String reasonCode) implements CloseResult {}
public record Unknown(String reasonCode) implements CloseResult {}
```

The execution operation performs final provider-authoritative revalidation immediately before mutation, then executes the close. It must not reinterpret the scope into a different exposure scope.

**Critical distinction:**

```text
resolution = NO financial mutation, validates read identity, returns opaque scope
execution  = financial mutation, requires reserved scope, performs provider close
```

---

## Broker-Neutral Close Intent

Two distinct operations at the Trading Core → Broker Service boundary:

### Phase 1: Scope Resolution (no financial mutation)

```java
ResolveTargetRequest(brokerAccountId, brokerPositionReference)
→ ResolvedPositionCloseTarget(brokerAccountId, resolvedMutationScope)
```

**Field classification:**

| Field | Classification |
|-------|----------------|
| `brokerAccountId` | REQUIRED (from authenticated context) |
| `brokerPositionReference` | REQUIRED (opaque read identity / resolution handle, from Angular — NOT the mutation target) |

`brokerPositionReference` is NOT the final mutation target. Broker Service owns target resolution.

### Phase 2: Financial Execution (after atomic scope reservation)

```java
ExecuteCloseRequest(brokerAccountId, resolvedMutationScope, idempotencyKey)
→ CloseResult (ACKNOWLEDGED | REJECTED | UNKNOWN)
```

**Field classification:**

| Field | Classification |
|-------|----------------|
| `brokerAccountId` | REQUIRED |
| `resolvedMutationScope` | REQUIRED (opaque, from Phase 1 — NOT re-derived) |
| `idempotencyKey` | REQUIRED (client-generated, from original request) |
| `quantity` | NOT_REQUIRED (server derives from authoritative reload) |
| `side` | NOT_REQUIRED (server derives from authoritative reload) |
| `reduceOnly` | NOT_REQUIRED (provider mechanic — Kraken adapter applies internally) |

---

## Authoritative Reload Boundary (ADR-040)

```text
Phase 1 — Resolution (NO financial mutation):
    Trading Core:
        - authentication
        - ownership validation
    Broker Service:
        - reloads open broker positions
        - resolves brokerPositionReference (resolution handle) to opaque mutation scope
        - returns ResolvedPositionCloseTarget

Phase 2 — Reservation:
    Trading Core:
        - atomically persists command with resolvedMutationScope
        - partial unique index enforces at most one active command per scope
        - if conflict: return 409 — NO broker mutation

Phase 3 — Execution (financial mutation):
    Trading Core:
        - calls Broker Service executeClose with reserved scope
    Broker Service:
        - final provider-authoritative revalidation (reload/revalidate current state)
        - ensures scope semantics are still safe
        - does NOT reinterpret scope into a different exposure scope
        - derives current side + aggregate quantity
        - submits provider-specific command
```

This three-phase model minimizes TOCTOU race by performing the final provider-authoritative read immediately before mutation inside the provider adapter, while ensuring scope is reserved before any financial mutation occurs.

**Critical invariant:** If final provider revalidation shows that the originally resolved scope can no longer be safely matched, the provider adapter must NOT mutate a different scope. It must return a safe non-mutation outcome.

---

## TOCTOU Safety (ADR-040)

A position may change between reload and mutation. The Story must enforce provider-side safety guarantees:

- **Kraken**: `reduce_only=true` on opposite-side market order — provider guarantees the order cannot open/increase opposite exposure
- Server reload alone is not sufficient protection against every race

---

## Kraken Close Implementation Contract

The Kraken adapter MUST translate broker-neutral `CloseRequest` to an AddOrder with:

- `pair` = instrument from reloaded position
- `type` = opposite of position side (BUY→sell, SELL→buy)
- `ordertype` = market
- `volume` = absolute value of reloaded **aggregate signed quantity for the pair** (sum of all same-side open positions)
- `reduce_only` = `true`
- `cl_ord_id` = derived from idempotency key (as current execution does)

**Safety invariant:** `reduce_only=true` guarantees that if volume exceeds current position, residual is cancelled; if no position exists, order is rejected. Exposure reversal is impossible at provider level.

**FIFO behavior:** When multiple same-side positions exist for the pair, Kraken settles oldest first. The `volume` submitted equals the total current reducible exposure; the provider applies FIFO across all entries. This is the intended and documented behavior.

---

## Trading Core Endpoint

Based on existing conventions (`/api/v1/accounts/{accountId}/positions` for GET):

```text
POST /api/v1/accounts/{accountId}/positions/close
Headers: Authorization: Bearer <JWT>, Idempotency-Key: <uuid>
Body: { "brokerPositionReference": "string" }
Response: 202 Accepted with ClosePositionResponse
Errors: 401, 403, 404 (position not found), 409 (conflict — unresolved command for same mutation scope), 503 (broker unavailable)
```

Response: `ResponseEntity<ClosePositionResponse>`

```java
public record ClosePositionResponse(
    String commandId,
    String status,           // CREATED | SUBMITTED | ACKNOWLEDGED | REJECTED | UNKNOWN | CLOSED | NOT_SUBMITTED
    String externalOrderId,
    String failureReason,
    String resolvedMutationScope,  // opaque application-level correlation/reservation key
    String reconciliationResult    // EXPOSURE_CONFIRMED_ABSENT | COMMAND_CONFIRMED_NOT_EXECUTED | RECONCILIATION_INCONCLUSIVE | null
) {}
```

**resolvedMutationScope semantics:**

```text
resolvedMutationScope
    = opaque application-level correlation/reservation key

Used for:
    database active-scope uniqueness
    UI same-scope coordination
    command correlation

NOT:
    Position ID
    provider-specific pair/contract
    frontend command authority
    persistent Position aggregate
```

Angular must treat `resolvedMutationScope` as opaque.

Note: ACKNOWLEDGED does not mean exposure confirmed closed. The frontend must not remove the position until broker-authoritative reconciliation confirms absence.

---

## Command Lifecycle & Persistence

ADR-040 recommends not reusing `ExecutionIntent` unchanged (TradePlan/entry semantics). Story 0033 introduces a minimal dedicated lifecycle with **7 persisted states** and a separate **reconciliation evidence dimension** (not persisted as lifecycle states).

### Persisted Lifecycle States

```text
PositionCloseCommand
    → CREATED
    → SUBMITTED
    → ACKNOWLEDGED | REJECTED | UNKNOWN
    → (terminal) CLOSED | NOT_SUBMITTED
```

**State semantics:**

| State | Active/Terminal | Blocks Equivalent Command | Reconcilable | Meaning |
|-------|----------------|--------------------------|-------------|---------|
| `CREATED` | ACTIVE | YES | NO | Command persisted with resolved scope, not yet submitted to broker |
| `SUBMITTED` | ACTIVE | YES | NO | Command sent to Broker Service, awaiting broker response |
| `ACKNOWLEDGED` | ACTIVE | YES | YES | Broker accepted the command — **does NOT mean exposure confirmed closed** |
| `REJECTED` | TERMINAL | NO | NO | Broker rejected the command (e.g., position not found) |
| `UNKNOWN` | ACTIVE | YES | YES | Broker submission outcome uncertain — requires explicit user-triggered reconciliation |
| `CLOSED` | TERMINAL | NO | NO | Broker-authoritative evidence confirms the relevant exposure scope is absent |
| `NOT_SUBMITTED` | TERMINAL | NO | NO | Fresh command, target exposure already absent at resolution time — no broker mutation occurred |

**ACTIVE states** are included in the partial unique index. **TERMINAL states** are excluded.

### Reconciliation Evidence Dimension (Separate Column)

Reconciliation outcomes are stored in a `reconciliation_result` column, NOT as lifecycle states:

| Reconciliation Result | Meaning |
|----------------------|---------|
| `EXPOSURE_CONFIRMED_ABSENT` | Provider state confirms target exposure is absent — safe, no new close needed |
| `COMMAND_CONFIRMED_NOT_EXECUTED` | Provider evidence confirms command was not executed — exposure may still exist |
| `RECONCILIATION_INCONCLUSIVE` | Provider evidence insufficient — cannot confirm or deny execution |

This separation preserves the distinction between command progression (lifecycle) and evidence quality (reconciliation). A command in UNKNOWN state with EXPOSURE_CONFIRMED_ABSENT means "we don't know if our command caused it, but the exposure is gone."

### State diagram

```text
CREATED
  │ scope reserved, ready for submission
  ↓
SUBMITTED ──────────→ REJECTED (terminal)
  │ broker accepted
  ↓                 ↘ UNKNOWN ──────→ user clicks Reconcile
ACKNOWLEDGED            │                    ↓
  │ user clicks     │              reconciliation_result set
  │ Reconcile       │                    ↓
  ↓                 │            CLOSED | stays UNKNOWN
CLOSED (terminal)    │
                     │
NOT_SUBMITTED ───────┘ (terminal: exposure absent at resolution)
```

**Critical distinction — ACKNOWLEDGED ≠ EXPOSURE CONFIRMED CLOSED:**

```text
ACKNOWLEDGED means the broker accepted the command.
It does NOT mean the exposure has disappeared.
The command remains ACTIVE until explicit reconciliation confirms exposure absence.
```

### ACKNOWLEDGED Convergence

ACKNOWLEDGED is an ACTIVE state. The command remains unresolved until explicit evidence establishes exposure absence.

Convergence mechanism:

```text
ACKNOWLEDGED command in UI
    ↓
User clicks "Reconcile"
    ↓
POST /api/v1/accounts/{accountId}/positions/close/{commandId}/reconcile
    ↓
Broker Service queries provider-authoritative state
    ↓
Provider-authoritative evidence determines exposure state:
    exposure absent → reconciliation_result = EXPOSURE_CONFIRMED_ABSENT → CLOSED
    exposure present + command not executed → stays ACKNOWLEDGED (may retry new command)
    evidence insufficient → stays ACKNOWLEDGED
```

This is the SAME reconciliation endpoint used for UNKNOWN. Both ACKNOWLEDGED and UNKNOWN are eligible.

Angular position polling remains authoritative for the displayed open-position list. Position disappearance shown by polling is a visual indicator, but does NOT silently mutate the persisted command. Only explicit backend reconciliation transitions the command to CLOSED.

### Fresh absence vs post-reconciliation absence

```text
Fresh command: target exposure already absent at resolution time
    → no broker mutation
    → NOT_SUBMITTED (not reported as successful close)

Post-reconciliation: exposure now absent
    → safe conclusion: no new close needed
    → CLOSED with reconciliation_result = EXPOSURE_CONFIRMED_ABSENT
    → without claiming causation
```

**CLOSED semantics:**

```text
CLOSED = broker-authoritative evidence confirms the relevant exposure scope is absent
CLOSED ≠ broker ACK received
CLOSED ≠ frontend card disappeared
```

The command may become CLOSED after explicit reconciliation based on authoritative provider evidence.

**Safe retry semantics:**

```text
UNKNOWN + COMMAND_CONFIRMED_NOT_EXECUTED + exposure still exists
    → new explicit human Full Exposure Close may become safe
    → but do NOT automatically create or submit the new command
    → user must initiate a new Full Exposure Close
```

Persistence: new lightweight table `position_close_command` with columns: `id`, `account_id`, `broker_account_id`, `broker_position_reference`, `resolved_mutation_scope`, `idempotency_key`, `status`, `reconciliation_result`, `external_order_id`, `failure_reason`, `created_at`, `updated_at`, `version`.

---

## Idempotency & Concurrency

### Idempotency Transport

The codebase convention is `@RequestHeader("Idempotency-Key")` consistently (see `ExecutionController`, `TradePlanRiskEvaluationController`, `AnalysisTradePlanController`, `OpportunityTradePlanController`). Story 0033 follows this convention.

```text
IDEMPOTENCY_TRANSPORT = Idempotency-Key HTTP header (request-header only)
```

The request body contains `brokerPositionReference` only. The idempotency key is NOT in the request body.

### Idempotency Semantics

Reuses existing `IdempotencyKey` pattern. Client generates UUID, sends in `Idempotency-Key` header. Trading Core persists and checks before processing. Kraken `cl_ord_id` derived from same key.

```text
Same key + same logical command
    → return/reuse existing command result, no second broker mutation

Same key + different logical command
    → conflict/reject

Different key + same unresolved mutation scope
    → BLOCKED (different idempotency key must never bypass an unresolved equivalent command)
```

### Atomic Concurrency Guard

**PostgreSQL partial unique index** enforces at most one active command per mutation scope at the database level:

```sql
CREATE UNIQUE INDEX uq_active_command_per_scope
ON position_close_command (broker_account_id, resolved_mutation_scope)
WHERE status IN ('CREATED', 'SUBMITTED', 'ACKNOWLEDGED', 'UNKNOWN');
```

This guarantees:
- At most one unresolved financially equivalent Full Exposure Close command per broker account + resolved mutation scope
- Survives different idempotency keys, concurrent HTTP requests, application threads, browser refresh, and multiple Trading Core instances
- Does not rely on frontend state or application-level locking
- TERMINAL states (REJECTED, CLOSED, NOT_SUBMITTED) are excluded from the unique constraint

**Cancellation/replacement is NOT allowed.** An unresolved financially equivalent command MUST NOT be replaced merely because another command arrives with a different idempotency key or another brokerPositionReference resolving to the same scope. The existing command remains active. No second broker mutation occurs. The new request receives 409 Conflict.

**Chicken-and-egg resolution:**
1. Phase 1: Broker Service resolves scope (returns `ResolvedPositionCloseTarget` to Trading Core) — zero financial mutation
2. Phase 2: Trading Core atomically persists command with status=CREATED + resolvedMutationScope
3. Partial unique index rejects duplicate if another command for the same scope already exists
4. If index violation: return 409 Conflict to Angular — NO broker mutation
5. Only after successful reservation: Trading Core calls Phase 2 (executeClose)

**Safe retry gate:** A financially equivalent new Full Exposure Close command must not be submitted while a previous command for the same mutation scope remains unresolved. A new command becomes safe only when broker-authoritative evidence establishes that the prior command was not executed AND the exposure still exists.

---

## Reconciliation

Reuses Story 0031 principle. Reconciliation is triggered by **explicit user action**, not a background worker.

### Reconciliation Trigger

Both `ACKNOWLEDGED` and `UNKNOWN` commands are eligible for reconciliation. The UI offers a "Reconcile" action when the command is in either state. The user clicks it, which sends a POST request to the reconciliation endpoint.

```text
ACKNOWLEDGED or UNKNOWN state in UI
    ↓
User clicks "Reconcile"
    ↓
POST /api/v1/accounts/{accountId}/positions/close/{commandId}/reconcile
    ↓
Broker Service queries provider-authoritative state
    ↓
Sets reconciliation_result on the command
    ↓
If EXPOSURE_CONFIRMED_ABSENT → CLOSED
    ↓
UI reflects terminal state
```

### Reconciliation Endpoint

```text
POST /api/v1/accounts/{accountId}/positions/close/{commandId}/reconcile
Headers: Authorization: Bearer <JWT>
Eligible statuses: ACKNOWLEDGED, UNKNOWN
Response: 200 OK with updated ClosePositionResponse
Errors: 401, 403, 404 (command not found), 409 (command not in ACKNOWLEDGED or UNKNOWN state)
```

### Kraken Reconciliation

Query provider order state using the stored `cl_ord_id` (client-supplied correlation ID, derived from idempotency key) and refresh `OpenPositions` to verify exposure state for the pair.

Note: `cl_ord_id` is the Trading OS supplied client correlation identifier. The Kraken `txid` is the provider-generated order/position identifier. These are separate identities and must not be confused.

### Provider-Neutral Reconciliation Outcomes

| Provider Order Evidence | Exposure State | Reconciliation Result | Lifecycle Transition |
|------------------------|----------------|----------------------|---------------------|
| matching order confirmed executed | absent | EXPOSURE_CONFIRMED_ABSENT | ACKNOWLEDGED/UNKNOWN → CLOSED |
| order evidence unavailable | absent | EXPOSURE_CONFIRMED_ABSENT | ACKNOWLEDGED/UNKNOWN → CLOSED |
| order confirmed not submitted | still exists | COMMAND_CONFIRMED_NOT_EXECUTED | stays ACKNOWLEDGED/UNKNOWN |
| provider result unresolved | still exists | RECONCILIATION_INCONCLUSIVE | stays ACKNOWLEDGED/UNKNOWN |

**No blind retry.** A new command becomes safe only when broker-authoritative evidence establishes that the prior command was not executed AND the exposure still exists.

**Reconciliation idempotency:** Multiple reconcile requests for the same command are safe — they re-query provider state and update the reconciliation result.

---

## Angular UX

### Confirmation Flow

```
/positions page
    ↓
User clicks "Close Exposure" on position card
    ↓
Confirmation dialog/inline:
    "Close all current [SYMBOL] [Long/Short] exposure completely?"
    "Kraken settles multiple open positions for this pair using FIFO."
    "This action cannot be undone."
    [Cancel] [Confirm Full Exposure Close]
    ↓
POST /api/v1/accounts/{accountId}/positions/close
    ↓
Position card shows command state: CREATED → SUBMITTED → ACKNOWLEDGED | REJECTED | UNKNOWN
    ↓
ACKNOWLEDGED:
    - position card remains visible
    - command feedback remains visible
    - Reconcile button available
    - user may explicitly reconcile to confirm convergence
    - Angular position polling continues — may visually show exposure gone,
      but does NOT silently mutate persisted command state
    ↓
UNKNOWN:
    - position card remains
    - shows "Outcome uncertain" with Reconcile button
    ↓
Reconcile clicked:
    POST /api/v1/accounts/{accountId}/positions/close/{commandId}/reconcile
    ↓
CLOSED: position card eventually removed by polling after broker confirms absence
```

**Key UX difference from previous design:** The confirmation explicitly states the FIFO scope for Kraken when multiple positions exist for the pair. The button label changes from "Close" to "Close Exposure" to accurately reflect the operation scope.

### Per-Scope Command Reflection

If several Kraken cards resolve to the same exposure scope: starting Full Exposure Close from one card must cause all affected controls to reflect the same in-flight operation. Do not leave another same-scope card offering an apparently independent close. The UI does not provide the safety guarantee, but it must accurately represent command scope.

### Per-Position Command State

Command state is associated with the **resolved close scope**, not the individual brokerPositionReference. The selected brokerPositionReference remains the initiating read identity, but all UI entries affected by that same scope must reflect the same active command.

For Kraken: two txids for the same pair may map to the same mutation scope. Backend unresolved-command protection must use the **resolved mutation scope** (broker account + pair/exposure scope). A different idempotency key MUST NOT bypass an unresolved financially equivalent command for the same mutation scope.

Duplicate close submissions for same mutation scope while active/uncertain are prevented server-side and locally in the UI.

### Position Disappeared Before Command

If authoritative reload finds no matching reference:

```text
Command rejected with "Position no longer exists" (no broker mutation)
UI shows informational state; position card already removed by polling or stays with warning
```

### Stale Frontend Quantity

Intent is **FULL EXPOSURE CLOSE THIS SCOPE**, not "close the quantity I saw 10 seconds ago".

Authoritative reload derives current aggregate size; command closes whatever remains.

---

## Tests

### Backend (minimum)

1. authenticated owner can request full exposure close
2. non-owner cannot close position
3. scope resolution call performs zero financial mutation
4. broker position reference used only as resolution handle — server resolves mutation scope
5. multiple Kraken txids resolving to same mutation scope are handled correctly
6. position no longer exists during resolution → no broker mutation, NOT_SUBMITTED outcome
7. stale frontend quantity not trusted
8. exact opaque reference used for read identity / ownership validation
9. provider rejection surfaced
10. provider unavailable/uncertain → UNKNOWN
11. UNKNOWN does not trigger blind retry
12. idempotency prevents duplicate application command
13. different idempotency key while unresolved equivalent command → blocked (no CANCELLED transition)
14. concurrent equivalent commands → at most one broker mutation (partial unique index enforced)
15. reconciliation path resolves uncertain result
16. Kraken close mechanics do not leak into Trading Core
17. Kraken implementation guarantees no exposure reversal
18. Kraken FIFO behavior: full pair exposure submitted, provider applies FIFO
19. fresh exposure absent at resolution → no mutation, NOT_SUBMITTED (not reported as successful close)
20. UNKNOWN + exposure absent → no further close required without false causality
21. UNKNOWN + provider command not executed + exposure still present → new explicit command may become safe
22. ACKNOWLEDGED does not imply exposure disappearance
23. reconciliation endpoint returns 409 if command not in ACKNOWLEDGED or UNKNOWN state
24. reconciliation endpoint returns updated reconciliation_result and lifecycle state
25. ACKNOWLEDGED → explicit reconciliation → CLOSED when exposure absent
26. ACKNOWLEDGED remains active before convergence
27. UNKNOWN → reconcile → inconclusive stays blocked
28. command lifecycle uses 7 persisted states; reconciliation outcomes stored separately
29. successful scope reservation required before execute call
30. partial unique index conflict → execute call count = 0

### Kraken Provider Tests

- FULL EXPOSURE CLOSE command → Kraken AddOrder with `reduce_only=true` on opposite-side market order
- Verify mechanism guarantees no exposure increase/reversal
- Oversized volume → provider auto-resizes/cancels residual
- No position → provider rejects
- Multiple same-pair positions: verify aggregate volume equals sum of same-side positions, FIFO applied

### Position Identity Tests

- Legacy path regression: existing txid propagation remains intact (KrakenMapper → Angular)
- Capability path new test: Kraken OpenPositions map key → PositionSnapshot.brokerPositionReference

### Trading Core Tests

- account ownership
- request validation
- scope resolution call performs zero financial mutation
- successful scope reservation required before execute call
- idempotency (including different-key bypass prevention)
- concurrent equivalent command → at most one broker mutation (partial unique index)
- partial unique index conflict → execute call count = 0
- command lifecycle (7 persisted states)
- broker result mapping
- UNKNOWN
- reconciliation endpoint (triggers user-initiated reconciliation)
- reconciliation 409 when command not in ACKNOWLEDGED or UNKNOWN state
- resolvedMutationScope returned in response and is opaque

### Broker Service Tests

- broker account ownership
- position authoritative reload
- exact position lookup (read identity)
- position missing
- current aggregate quantity derivation for pair/exposure scope
- provider command invocation
- ACKNOWLEDGED / REJECTED / UNKNOWN mapping
- scope resolution: brokerPositionReference → opaque resolvedMutationScope
- scope resolution for multiple same-symbol positions
- final revalidation does not reinterpret scope into different exposure

### Angular Tests

- Full Exposure Close action visible for open position
- confirmation required with FIFO disclosure when applicable
- confirmation describes full exposure scope (not individual txid close)
- cancel confirmation sends nothing
- confirmed close calls service once
- duplicate click while command active does not double-submit
- same-scope cards cannot independently submit another active close
- success/acknowledged state displayed — does not remove position immediately
- rejected state displayed
- UNKNOWN state displayed safely — does not offer blind retry
- reconcile button visible when command in ACKNOWLEDGED or UNKNOWN state
- reconcile button calls reconciliation endpoint
- reconcile result displayed with appropriate messaging
- position remains until authoritative refresh removes it
- refresh after successful close
- other position cards remain usable

---

## Regression Expectations

Story implementation must preserve:

- Story 0030 execution flow
- Story 0031 execution feedback/reconciliation
- Story 0032 position monitoring
- dashboard position projection
- Kraken account reads
- market data
- trade planning

Story 0033 must NOT introduce:

- partial close
- SL/TP modification
- automatic position management
- AI exit decisions
- close-all / bulk operations
- position history
- cTrader implementation
- FTMO integration
- prop rules
- persistent Position aggregate
- streaming / WebSocket / SSE

---

## Database Impact

```text
DATABASE_MIGRATION_REQUIRED = YES
```

New lightweight table `position_close_command` (or equivalent) for command lifecycle persistence supporting idempotency, UNKNOWN, reconciliation, auditability. Includes `resolved_mutation_scope` column for in-flight command protection.

---

## API Contracts

### External (Trading Core → Angular)

```
POST /api/v1/accounts/{accountId}/positions/close
Headers: Authorization: Bearer <JWT>, Idempotency-Key: <uuid>
Body: { "brokerPositionReference": "string" }
Response: 202 Accepted with ClosePositionResponse { commandId, status, externalOrderId, failureReason, resolvedMutationScope, reconciliationResult }
Errors: 401, 403, 404 (position not found), 409 (conflict — unresolved command for same mutation scope), 503 (broker unavailable)

POST /api/v1/accounts/{accountId}/positions/close/{commandId}/reconcile
Headers: Authorization: Bearer <JWT>
Eligible statuses: ACKNOWLEDGED, UNKNOWN
Response: 200 OK with updated ClosePositionResponse
Errors: 401, 403, 404 (command not found), 409 (command not in ACKNOWLEDGED or UNKNOWN state)
```

### Internal (Trading Core → Broker Service)

```text
Phase 1 — Scope Resolution (NO financial mutation):
ResolveTargetRequest(brokerAccountId, brokerPositionReference)
→ ResolvedPositionCloseTarget(brokerAccountId, resolvedMutationScope)

Phase 2 — Financial Execution:
ExecuteCloseRequest(brokerAccountId, resolvedMutationScope, idempotencyKey)
→ CloseResult (ACKNOWLEDGED | REJECTED | UNKNOWN)
```

### Broker Service → Provider

```text
CloseRequest(brokerAccountId, resolvedMutationScope, idempotencyKey)
→ CloseResult (ACKNOWLEDGED | REJECTED | UNKNOWN)
```

The provider adapter resolves the actual mutation target from the opaque scope.

---

## Sequence

```text
User
  ↓ clicks Close Exposure, confirms
Angular
  ↓ POST /positions/close {brokerPositionReference, idempotencyKey}
  ↓ brokerPositionReference is the read identity / resolution handle
Trading Core
  ↓ auth, ownership, idempotency check
  ↓ calls Broker Service: resolveTarget(brokerAccountId, brokerPositionReference)
Broker Service
  ↓ broker account ownership
  ↓ reloads open positions from provider (PositionCapability)
  ↓ resolves brokerPositionReference against fresh broker state
  ↓ returns ResolvedPositionCloseTarget { brokerAccountId, resolvedMutationScope }
  ↓ NO financial mutation occurs in this call
Trading Core
  ↓ atomically persists command (CREATED) with resolvedMutationScope
  ↓ partial unique index enforces at most one active command per scope
  ↓ if index conflict: return 409 Conflict — NO broker mutation
Trading Core
  ↓ calls Broker Service: executeClose(brokerAccountId, resolvedMutationScope, idempotencyKey)
Broker Service
  ↓ final provider-authoritative revalidation (reload/revalidate current state)
  ↓ ensures scope semantics are still safe — does NOT reinterpret into different scope
  ↓ derives current side + aggregate quantity for exposure scope
  ↓ calls PositionManagementCapability.close()
Kraken Adapter
  ↓ AddOrder(reduce_only=true, opposite side, aggregate quantity, cl_ord_id)
  ↓ ACKNOWLEDGED(orderId) | REJECTED(reason) | UNKNOWN(reason)
Broker Service
  ↓ maps to CloseResult
Trading Core
  ↓ updates command status
  ↓ returns ClosePositionResponse
Angular
  ↓ shows CREATED → SUBMITTED → ACKNOWLEDGED/REJECTED/UNKNOWN
  ↓ ACKNOWLEDGED does not remove position — polling continues
  ↓ user may explicitly reconcile to confirm convergence
```

---

## Explicit Out-of-Scope Section

- partial close
- SL/TP modification
- automatic position management
- AI exit decisions
- close-all / bulk operations
- position history
- cTrader implementation
- FTMO integration
- prop rules
- persistent Position aggregate
- streaming / WebSocket / SSE

---

## Definition of Done

* [ ] ADR-040 architectural decisions respected.
* [ ] Opaque broker position reference propagates end-to-end (legacy path: KrakenMapper → Angular already works; capability path: PositionSnapshot fixed to preserve txid).
* [ ] brokerPositionReference is consistently documented and used as resolution handle.
* [ ] Full Exposure Close works end-to-end for Kraken using reduce_only=true only.
* [ ] No exposure reversal is possible through the close path.
* [ ] Scope resolution and financial execution are separate semantic operations.
* [ ] Atomic scope reservation occurs before any financial mutation.
* [ ] Reservation conflict causes zero broker mutation (409 Conflict).
* [ ] Ownership/security tests pass.
* [ ] Idempotency tests pass (including different-key bypass prevention).
* [ ] In-flight command protection keyed by resolved mutation scope (PostgreSQL partial unique index).
* [ ] Unresolved commands cannot be replaced/cancelled by a different key.
* [ ] ACKNOWLEDGED has explicit backend convergence path via reconciliation.
* [ ] UNKNOWN/reconciliation tests pass with evidence-based terminology.
* [ ] Reconciliation endpoint exists, requires explicit user action, accepts ACKNOWLEDGED and UNKNOWN.
* [ ] Command lifecycle uses 7 persisted states; reconciliation outcomes stored separately.
* [ ] ClosePositionResponse includes opaque resolvedMutationScope and reconciliationResult.
* [ ] resolvedMutationScope is opaque — no provider-specific values exposed.
* [ ] cl_ord_id and txid are distinct and never conflated.
* [ ] Idempotency transport is singular: Idempotency-Key HTTP header.
* [ ] Legacy frontend position identity propagation is already present (no new work).
* [ ] Same-symbol multi-position FIFO-aware tests pass.
* [ ] Angular confirmation and feedback tests pass (FIFO disclosure verified).
* [ ] Angular reconcile button visible for ACKNOWLEDGED and UNKNOWN states.
* [ ] Existing Story 0030–0032 regressions pass.
* [ ] Angular production build passes.
* [ ] Backend relevant test suites pass.
* [ ] Quality pipeline passes.
* [ ] No out-of-scope functionality introduced.

---

## Validation

* Trading Core tests (position close endpoint, scope resolution, ownership, command lifecycle, idempotency, reconciliation endpoint, concurrency guard)
* Broker Service tests (capability resolution + execution, Kraken adapter, authoritative reload, provider result mapping, scope resolution, final revalidation)
* Angular tests (positions page extension, confirmation, command state, polling integration, reconcile button for ACKNOWLEDGED+UNKNOWN)
* Angular production build
* Manual verification: `/positions` close exposure flow works end-to-end
* Architecture validation against ADR-001, ADR-014, ADR-029, ADR-030, ADR-040

---

## Story Lifecycle

| Status | Value |
|---|---|
| IMPLEMENTATION_COMPLETE | YES |
| FINAL_SAFETY_REVIEW_COMPLETE | YES |
| HUMAN_ACCEPTANCE | YES |
| STORY_0033_ACCEPTED | YES |
| ADR_040_STATUS | Accepted |

**Date accepted**: 2026-09-05

## Transaction Semantics (Verified)

The Full Exposure Close orchestration executes inside a single `@Transactional` boundary. Intermediate lifecycle states (CREATED, SUBMITTED) are not independently durable across process/database transaction failure. If the transaction fails before commit, all intermediate database changes roll back.

**Design-time concern addressed**: A separately committed CREATED state would create an orphan/reservation recovery problem. The current single transaction prevents this specific orphaned CREATED state.

**Financial uncertainty preserved**: A database rollback does NOT imply the broker command was not executed. If the broker call started before the crash, the financial mutation may have occurred.

## PostgreSQL Verification Debt

The production PostgreSQL partial unique index has NOT been verified in a real PostgreSQL environment.

**Current verified state**:
- H2 test profile: Full unique index (stricter but safe) — tested
- PostgreSQL partial unique index: NOT tested

**Production invariant**:
```sql
UNIQUE (broker_account_id, resolved_mutation_scope)
WHERE status IN ('CREATED', 'SUBMITTED', 'ACKNOWLEDGED', 'UNKNOWN')
```

**Future verification should cover**:
1. One ACTIVE command succeeds
2. Concurrent equivalent ACTIVE command fails
3. Different scope succeeds
4. Terminal command allows a future ACTIVE command for the same scope
5. Concurrent transactions cannot create two ACTIVE commands for the same scope