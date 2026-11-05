# Repository Analysis - Story 0203

## Repository State

- Branch: `main`, aligned with `origin/main` at the start of this audit.
- The worktree is intentionally dirty with changes from Stories 0198-0202.
- No existing changes were reset, cleaned, staged or rewritten.
- Story 0203 was created as the next available Story number.
- `TOOLS.md` is absent, so no repository-local DevLog lifecycle mapping is
  available.
- DevLog was queried twice with the same request and idempotency key; both calls
  timed out. This audit continues from repository evidence only.
- No authenticated cTrader call was made during this audit. No load test was
  run.
- No repository log files were found for reconstructing historical daily
  provider consumption. Existing source logs and persisted request-safety events
  are the available evidence sources.

## Context Reconciled

The reports for Stories 0198-0202 establish:

- The reference account is external cTrader account `49411967`.
- Runtime snapshot calls have been protected by the cTrader safety guard and
  the `PT20M` snapshot cooldown.
- Stories 0199-0201 added bounded snapshot handling, partial synchronization,
  explicit provider-fact availability and diagnostic-only financial state.
- Story 0202 is currently at Repository Analysis and has not authorized code
  implementation or any new provider investigation.

Relevant architecture remains:

- `CTraderRequestSafetyGuard` owns persistent request safety and snapshot leases.
- `JsonWebSocketCTraderOpenApiClient.Session` is the actual cTrader WebSocket
  emission boundary.
- Broker Service owns provider protocol interpretation.
- Trading Core and Angular must not directly emit cTrader protocol messages.
- `CTRADER_SAFETY_EXECUTION_ENABLED` remains `false`.

## Existing Protections

### Persistent request counter

`CTraderRequestSafetyGuard.reserveRequest(...)` updates the PostgreSQL-backed
`ctrader_request_safety` row before each protocol message. It uses an atomic
`request_count < effectiveRequestLimit()` update and records each reservation in
`ctrader_request_safety_event`.

Current production configuration defaults are:

```text
CTRADER_SAFETY_ENABLED=true
CTRADER_SAFETY_DAILY_REQUEST_BUDGET=1000
CTRADER_SAFETY_ALERT_THRESHOLD=700
CTRADER_SAFETY_HARD_STOP=800
CTRADER_SAFETY_SNAPSHOT_COOLDOWN=PT20M
CTRADER_SAFETY_SNAPSHOT_LEASE=PT30M
```

The effective hard stop is therefore `800`, not `1000`. This is conservative,
but the distinction is not currently surfaced as a first-class budget report.
The counter is global (`GLOBAL`) rather than explicitly partitioned by account;
this is appropriate if the configured scope is a shared provider quota, but it
must be made explicit and observable.

The daily reset uses `LocalDate.now(clock)` with the application clock's zone.
The external provider reset timezone is unknown. The internal window must be
documented as an internal accounting window, not as a claim about FTMO's window.

### Snapshot cooldown and lease

Snapshot requests use a persistent per-account scope (`ACCOUNT:<externalId>`)
with a cooldown and lease. Concurrent or recently completed snapshot requests
are rejected before cTrader authentication and protocol emission.

Expected-margin requests use a separate per-account scope
(`MARGIN:<externalId>`). They are still subject to the global request budget,
but are not blocked by the snapshot cooldown. This separation is intentional
and must not be changed without an explicit priority policy.

### Execution safety

Order submission and position close paths require the execution safety flag. The
current default remains disabled. They also pass through the same WebSocket
`Session` request reservation boundary when enabled.

## Request Inventory

The counts below are static protocol-message reservations. Each fresh WebSocket
`Session` also reserves payload type `0` for connection establishment. Whether
FTMO counts that transport-level event as a server request is unconfirmed; the
internal counter intentionally counts it conservatively.

| Operation | Trigger / owner | Protocol messages per fresh session | Cache / cooldown | Classification |
|---|---|---:|---|---|
| Discover authorized accounts | cTrader authentication service | 2 plus session reservation | None | Required during authorization |
| Authorize account | cTrader authentication service | 2 plus session reservation | None | Required during account binding |
| Detect demo/environment | authentication/binding flow | 2 plus session reservation | None | Required for environment checks |
| Read-only snapshot | Broker snapshot capability, synchronization, dashboard/risk consumers | 8 plus session reservation | Persistent per-account snapshot cooldown/lease | Required but expensive |
| Execution instrument contract | read-only validation or execution preparation | 4 plus session reservation | None | Required for selected operation |
| Instrument metadata | read-only validation | 7 plus session reservation | None | Optional/diagnostic unless required by workflow |
| Current quote | read-only validation / market preparation | 6 plus session reservation | None; subscribe/unsubscribe in same session | Optional, currently uncached |
| Expected margin | LIVE proposed-order preflight | 7 plus session reservation | Separate margin cooldown scope, no value cache | Required before eligible LIVE preflight |
| Submit order | execution capability | 6 plus session reservation | Execution disabled by default; no automatic retry | Mutation, safety-critical |
| Close position | position management capability | 6 plus session reservation | Execution disabled by default; no automatic retry | Mutation, safety-critical |
| Execution reconciliation | execution/position reconciliation | Read-only snapshot cost | Snapshot cooldown/lease | Required after ambiguous mutation |
| Close target/reconciliation | position management capability | Read-only snapshot cost | Snapshot cooldown/lease | Required for safe position authority |

Counts exclude WebSocket heartbeat/control frames and any provider-side
accounting semantics that are not visible in the Java client. They are an
inventory estimate, not a measured FTMO quota count.

## Demonstrated Consumption Risks

### Repeated session setup

Every public cTrader client operation creates a new WebSocket session and repeats
application authentication. Several operations then repeat account
authentication, account list, trader, asset, symbol list and symbol details
requests. There is no shared fresh-data cache or request coalescing at this
boundary.

### Snapshot fan-out

The snapshot cooldown prevents repeated snapshots for the same account during
the configured window, but dashboard refreshes, synchronization actions,
reconciliation and other consumers can still independently attempt the path and
receive controlled deferrals. The repository does not yet demonstrate one
shared in-flight snapshot future or a single refresh coordinator for all callers.

### Read-only validation fan-out

`CTraderReadOnlyValidationService` calls quote, instrument contract and metadata
operations independently. Those operations each open a new session and repeat
common setup. No persistent freshness policy or cache is visible in this
service.

### Required-margin repetition

`CTraderMarginCapability` and `BrokerRequiredMarginClient` correctly use the
provider-authoritative Expected Margin path, but each request creates a new
session and repeats setup. No safe cache keyed by account, instrument, side,
quantity and observation boundary is visible.

### Metrics mismatch

`BrokerOperationsMetrics` records application-level operations such as
`risk_snapshot`, `margin_preview` and `execution`. It does not count each
cTrader protocol message, reservation, rejection, remaining budget, retry,
reconnect or cooldown activation. `ctrader_request_safety_event` records
reservations with `payload_type`, but not trigger, application operation or
outcome. The two telemetry layers cannot currently answer the full request
inventory without correlation.

### Retry and reconnect behavior

The cTrader WebSocket client has bounded connection and response timeouts but no
automatic retry loop in the client. New caller invocations create new sessions,
which can behave as repeated attempts. The separately scheduled broker
revocation reconciliation has bounded retry state, but it does not directly
invoke cTrader protocol calls in the inspected scheduler path. No cTrader
periodic polling scheduler was found in `broker-service/src/main`.

### Frontend distinction

Angular scan/dashboard polling exists, but those are Trading Core HTTP requests
and must not be counted as cTrader requests without tracing the downstream
provider call. The initial audit must preserve this distinction.

## Root-Cause Hypotheses

Confirmed from static evidence:

- Fresh sessions repeat setup messages for each cTrader operation.
- No shared cache/coalescing exists for quote, metadata, instrument contract or
  expected margin at the provider boundary.
- Protocol-level reservation telemetry lacks operation/trigger attribution.
- The effective hard stop is configured below the nominal internal budget.

Not confirmed:

- That Trading OS alone exceeded FTMO's external 2000-request window.
- The actual number of provider requests emitted in any historical day.
- Whether FTMO counts connection reservations, authentication messages,
  heartbeats or only application payloads.
- Whether Angular polling caused provider traffic in a particular runtime.
- Whether multiple application instances share the same database and quota row
  in production.

## Safe Implementation Direction

The implementation plan should remain limited to offline, provider-neutral
protection work:

1. Preserve the existing persistent atomic reservation as the central gate.
2. Add explicit operation/trigger/priority attribution to reservations and
   metrics without logging credentials or provider payloads.
3. Make rejected reservations, effective limit and remaining budget observable.
4. Add bounded retry/reconnect policy tests and verify no retry occurs after a
   budget refusal.
5. Add safe snapshot/request coalescing or freshness reuse only where the
   existing contracts define acceptable staleness and identity boundaries.
6. Keep mutations and safety-critical reconciliation subject to the same budget;
   do not create an unbounded priority bypass.
7. Make real-provider tests explicit opt-in and verify ordinary tests use mocks.
8. Define, but do not execute, a minimal runtime validation protocol with a
   fixed maximum request reservation count.

Any change to quota scope, priority bypass, reset semantics or provider request
classification requires explicit documentation in the implementation plan.

## Validation Baseline

- Existing Broker Service tests cover `CTraderRequestSafetyGuard` persistence,
  limits and snapshot behavior, but do not cover the complete operation/trigger
  attribution requested by Story 0203.
- Existing cTrader client tests are mocked/unit-level and do not require the
  real FTMO account.
- No authenticated provider call was made during this audit.
- No runtime request count can be claimed from repository logs because no
  historical log files were available.
- `git diff --check` and targeted tests must be run after implementation, not as
  a substitute for this analysis.

## Workflow Status

Repository Analysis is complete. The next workflow stage is the Implementation
Plan, but it requires explicit human approval of this analysis first.

No implementation code has been changed for Story 0203.
