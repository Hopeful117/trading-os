# Implementation Plan - Story 0203

## Operating Mode

This Story is offline-first. No authenticated cTrader request, load test or
runtime validation is authorized during implementation. The existing FTMO
account `49411967` remains the only account eligible for a future separately
approved runtime protocol.

Preserve all uncommitted changes from Stories 0198-0202. Do not modify T0/T1,
financial policy or execution semantics. Keep
`CTRADER_SAFETY_EXECUTION_ENABLED=false`. No commit, push, merge, reset, clean,
rebase or branch cleanup is part of implementation.

## Design Decisions

1. Keep `JsonWebSocketCTraderOpenApiClient.Session` as the single protocol
   emission boundary. No alternate cTrader adapter may emit outside the budget
   reservation path.
2. Preserve the existing PostgreSQL-backed atomic global reservation and
   snapshot leases. Extend them rather than introducing a new infrastructure
   component.
3. Treat `1000` as the configurable internal daily budget and the existing
   `800` hard stop as the strict effective ceiling unless configuration tests
   demonstrate a different approved production value. Report both values
   explicitly.
4. Store operation, trigger and priority metadata with each reservation. Do not
   store tokens, credentials, raw payloads or sensitive account data.
5. Count reservation conservatively before emission. A failed send remains a
   reserved internal budget unit because the provider-side accounting outcome
   is not safely knowable.
6. Do not retry after a budget refusal. Keep existing bounded transport
   timeouts and ensure any future retry path is explicit, finite and budgeted.
7. Reuse ADR-087 fresh snapshot reuse and refresh coordination for account
   snapshots. Do not add an independent snapshot polling mechanism.
8. Add request coalescing only for safe concurrent read operations with an
   explicit freshness key and bounded lifetime. Never coalesce mutations or
   ambiguous execution/reconciliation outcomes.
9. Keep the external FTMO threshold `2000/day` as an unverified external fact;
   do not encode it as provider semantics or claim that the internal counter is
   an exact FTMO measurement.

## 1. Central Budget Contract

### Broker Service safety model

- Introduce an internal request metadata value containing:
  - operation;
  - trigger;
  - priority class;
  - external account identifier only where needed for diagnostics.
- Extend `CTraderRequestSafetyGuard.reserveRequest(...)` to accept this metadata
  while preserving a small compatibility overload for existing tests/callers
  during the same change.
- Return or expose a reservation result containing effective limit, count and
  remaining budget where the caller needs diagnostics; keep refusal as a typed
  safety exception.
- Preserve atomic SQL reservation under concurrent instances.
- Make daily window behavior explicit and test it with the configured application
  clock. Do not infer the provider reset timezone.

### Persistence

- Add an additive Broker Service Flyway migration for reservation metadata and,
  if needed, rejected-reservation diagnostics.
- Extend `ctrader_request_safety_event` with bounded operation, trigger and
  priority fields and a reservation outcome/category.
- Preserve the existing event index and add only indexes needed for daily
  aggregation and operational diagnostics.
- Keep the global scope for the shared internal budget. Do not silently change
  it to per-account accounting; if per-account diagnostics are added, they must
  remain subordinate to the global ceiling.

### Configuration

- Preserve environment-backed configuration.
- Add explicit names/documentation for internal budget, alert threshold, hard
  stop and any window zone used by internal accounting.
- Validate that `hardStop <= dailyRequestBudget`, both are positive, and alert
  threshold does not exceed the effective limit.
- Keep defaults conservative and compatible with the current deployment:
  `1000` nominal budget, `700` alert, `800` hard stop.

## 2. Protocol Emission Attribution

- Add operation/trigger metadata at each public `JsonWebSocketCTraderOpenApiClient`
  method.
- Pass metadata through `Session` to every `Session.request(...)`, including
  application authentication, account authentication, symbol, snapshot,
  subscription, expected-margin and mutation messages.
- Keep payload type as protocol evidence, but do not use it as the only
  attribution dimension.
- Attribute session establishment separately from protocol payload messages and
  document that it is a conservative internal reservation.
- Verify every interface implementation path:
  - authentication/account discovery;
  - read-only snapshot;
  - instrument contract and metadata;
  - quote subscription/unsubscription;
  - expected margin;
  - order submission;
  - position close;
  - reconciliation.

## 3. Observability

- Extend Broker Service metrics with:
  - requests reserved;
  - requests emitted/attempted;
  - reservations rejected;
  - remaining effective budget;
  - operation, trigger and priority counts;
  - retry count;
  - reconnect/session count;
  - cooldown activations and refusals.
- Keep metric labels bounded and avoid external account IDs, tokens, payloads or
  high-cardinality correlation IDs.
- Extend the existing internal metrics endpoint or safety diagnostics endpoint
  with a sanitized budget snapshot. Do not create a public provider-facing API.
- Add structured logs for budget refusal and cooldown refusal with safe
  operation/trigger/reason fields.
- Distinguish these states in diagnostics:
  - internal budget exhausted;
  - provider cooldown active;
  - transport timeout;
  - application/provider error;
  - mutation outcome unknown.

## 4. Redundancy Reduction

### Snapshot path

- Confirm the existing ADR-087 latest-snapshot reuse path is used before a new
  provider refresh.
- Ensure concurrent refresh requests share one in-flight refresh or are
  rejected by the existing persistent lease before opening a session.
- Do not add automatic polling or retry after `BROKER_SNAPSHOT_DEFERRED`.

### Read-only metadata path

- Add bounded in-process coalescing for concurrent identical read requests only
  if the current service boundary permits it without stale identity leakage.
- Key entries by broker account, instrument, operation and source version/freshness
  requirement.
- Do not cache across account identity, credential or provider-environment
  changes.
- Expire entries using explicit short TTLs and expose cache hit/miss diagnostics.

### Expected margin

- Do not cache a proposed-order margin result across incompatible quantity,
  side, price, account, instrument or freshness boundaries.
- Coalesce only identical concurrent requests if the provider-neutral contract
  and existing risk flow permit it.
- Keep provider-authoritative Expected Margin as the source of truth.

### Frontend and caller behavior

- Audit Angular dashboard/scan polling only for downstream duplication.
- Do not count Trading Core HTTP requests as cTrader requests.
- Prevent UI refresh actions from creating concurrent duplicate synchronization
  requests where the existing account state already reports a pending/deferred
  refresh.

## 5. Retry and Reconnect Safety

- Verify the cTrader client has no implicit unbounded retry path.
- Add explicit tests showing:
  - a budget refusal is not retried;
  - a timeout does not trigger an unbounded new session;
  - reconnect/session creation consumes budget;
  - ambiguous mutations are not automatically replayed;
  - cooldown refusal does not emit protocol traffic.
- If a retry abstraction is required by current code, configure bounded attempts,
  backoff and jitter and reserve each attempt separately before emission.
- Do not classify a provider error as a retryable transport error without
  existing evidence.

## 6. Test Plan

### Safety guard and persistence

- Available budget reserves atomically.
- Effective hard stop rejects at the configured ceiling.
- Alert threshold is emitted once per internal window.
- Concurrent reservations across threads/instances cannot exceed the ceiling.
- Restart preserves the current day and count.
- New day resets the internal counter using the configured clock.
- Operation, trigger, priority, payload type and outcome metadata persist.
- Budget diagnostics never expose secrets.

### Client emission boundary

- Every session request reserves exactly once before send.
- Session setup is counted consistently.
- All public cTrader client methods use the same guard.
- Send failure remains conservatively reserved.
- Budget refusal prevents WebSocket send.
- No automatic retry follows budget refusal.

### Reuse and deduplication

- Fresh snapshot is reused without provider emission.
- Concurrent snapshot refresh is coalesced or deferred by the persistent lease.
- Identical safe read requests coalesce where implemented.
- Account, instrument, side, quantity and environment changes do not reuse an
  incompatible value.
- Mutations and ambiguous reconciliation are never coalesced or replayed.

### Provider isolation

- Ordinary Maven tests use mocked `CTraderOpenApiClient`/transport.
- No test reads real credentials or external account `49411967` by default.
- No runtime integration test is enabled by a default Maven profile.
- No cTrader endpoint is contacted by the new tests.

## 7. Validation Commands

- Targeted Broker Service safety/client/metrics tests.
- Targeted Trading Core tests only if caller behavior or snapshot reuse changes.
- Angular targeted tests if refresh/polling behavior changes.
- Broker Service compile.
- Applicable Trading Core compile/tests.
- Angular `npm run test:ci` and `npm run build` only if frontend code changes.
- `git diff --check`.
- No authenticated runtime command during implementation.
- A runtime validation plan with a fixed maximum request count is documented,
  but not executed, pending separate human approval.

## 8. Documentation Reconciliation

- Update the Story-local Request Inventory, Consumption Analysis, Root Causes,
  Implemented Protections, Tests, Safety and Runtime Validation Plan artifacts.
- Update Broker Service configuration documentation only if a canonical file
  exists and the new configuration is user-operationally relevant.
- Do not modify ADRs unless implementation reveals a material architectural
  contradiction; then stop and request an ADR decision.

## 9. Expected Deliverables

- Budget/attribution implementation at the cTrader emission boundary.
- Additive persistence migration if required.
- Bounded reuse/coalescing changes limited to safe reads.
- Metrics and sanitized diagnostics.
- Targeted offline tests.
- Story-local implementation report and evidence artifacts.
- Runtime validation protocol requiring a separate explicit approval.

## 10. Stop Conditions

Stop and request human guidance if:

- the existing schema cannot support shared atomic reservation without a new
  infrastructure decision;
- a priority exception would allow traffic to bypass the strict ceiling;
- safe cache semantics cannot be established for an operation;
- a code path can emit cTrader traffic outside the central guard;
- a test or validation path requires real credentials or an authenticated
  provider call;
- implementation would require changing T0/T1, financial policy or execution
  safety.
