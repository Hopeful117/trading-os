# Story 0127 Investigation Report

## 1. EXECUTIVE DECISION

**E - DURABLE_RECONCILIATION_REQUIRED.**

The current revoke flow can leave Trading Core terminally `REVOKED` while
Broker Service retains an active connection after a callback response is lost.
That divergence survives restart, is not idempotently repaired by retry, and
can affect live execution eligibility. The minimum safe solution is a small
durable Broker Service reconciliation record with post-commit callback retry and
idempotent terminal callback handling in Trading Core. Kafka, a generic outbox,
Saga, and distributed transactions are not justified.

## 2. REPOSITORY STATE

- Branch: `main`.
- HEAD: `1cd03fdbaa94ca8b29fc9587313f90b9068435ed`.
- `origin/main`: `1cd03fdbaa94ca8b29fc9587313f90b9068435ed`.
- Ahead/behind: `0/0`.
- Tracked modified files: none.
- Staged files: none.
- Untracked file: pre-existing `docs/architecture/reports/v1-functional-audit.md`.
- `git fetch origin`: succeeded.
- Story 0125 and Story 0126 artifacts are present in the repository.
- No production implementation, test, configuration, database, runtime,
  credential, or Market Data state was modified.
- DevLog context lookup failed with `429 insufficient_quota`.

## 3. CURRENT OWNERSHIP MODEL

| Fact | Authority | Replica/Projection | Can become stale? | Reconciliation direction |
|---|---|---|---|---|
| Broker account identity | Trading Core | Broker Service account ID reference | YES | Broker Service reads Trading Core |
| Ownership | Trading Core | Broker Service owner ID | YES | Trading Core remains authoritative |
| Connection state | Broker Service technical connection plus Trading Core lifecycle projection | Each service has a copy | YES | Destructive revocation wins |
| Credential reference | Broker Service active reference | Trading Core opaque reference | YES | Clear on terminal revoke |
| Credential existence | Broker Service secret store | None in Trading Core | YES | Secret store/Broker Service dominate |
| Revocation state | Broker Service committed destructive state, projected to Trading Core | Other service's status | YES | Converge to `REVOKED`; never resurrect |

## 4. CURRENT REVOKE SEQUENCE

`DELETE /api/v1/broker-accounts/{id}` in Trading Core calls
`BrokerConnectionCommandClient.revoke`, which calls Broker Service
`DELETE /api/v1/broker-accounts/{id}/credentials`. Broker Service performs
`findOwned`, `SecretRevoker.revoke`, `BrokerConnection.revoke`, and synchronous
`TradingCoreBrokerAccountClient.updateStatus(REVOKED, null)`. Trading Core then
executes `BrokerAccountService.updateStatus` and `BrokerAccount.markRevoked`.

## 5. DURABILITY / COMMIT POINTS

| Step | Service | Durable? | Reversible? | Idempotent? | Divergence risk |
|---|---|---:|---:|---:|---:|
| Secret revoke | Broker Service DB transaction | At commit | YES before commit | NO | YES if future external store/split transaction |
| BrokerConnection revoke | Broker Service outer transaction | At commit | YES before commit | NO explicit guard | YES |
| Trading Core callback | Trading Core transaction | YES when accepted | NO, terminal | NO for duplicate REVOKED | YES |
| HTTP response | Client | NO | N/A | N/A | No new state |

Current callback failure normally rolls back the Broker Service transaction.
The response-lost-after-remote-commit case still leaves Trading Core committed
and Broker Service rolled back.

## 6. PARTIAL FAILURE MATRIX

| Failure point | Broker Service | Trading Core | Credential | User sees | Permanent divergence possible? |
|---|---|---|---|---|---:|
| Authorization/request before Broker command | Unchanged | Unchanged | Unchanged | Error | NO |
| Secret revoke failure | Prior state | Prior state | Active | Error | NO under current DB store |
| Callback fails before Core commit | Rolled back | Prior state | Active | Unconfirmed/error | NO durable divergence |
| Callback commits, response is lost | Rolled back to prior state | `REVOKED` | Active in Broker DB | Unconfirmed/error | YES |
| Broker local commit before callback in a split implementation | `REVOKED` | Prior state | Revoked/absent | Unconfirmed/error | YES |
| Duplicate callback | Current state-dependent | May already be `REVOKED` | Current state-dependent | Conflict/error | YES |

## 7. IDEMPOTENCY ANALYSIS

- Duplicate terminal callback: **UNSAFE**; `markRevoked` rejects a transition
  from `REVOKED` to `REVOKED`.
- Retry after timeout: **UNSAFE**; the callback may already have committed on
  Trading Core while Broker Service rolled back.
- Already revoked connect/rotate/disconnect: **SAFE_DUPLICATE_REJECTION**;
  Story 0126 added terminal guards.
- Revoke with no active credential: **IDEMPOTENT locally**, but callback
  delivery itself is not idempotent.
- Durable callback retry: **NO**.
- Durable duplicate-delivery key: **NO**.

## 8. STORY 0126 INTERACTION

Story 0126 leaves reconciliation unchanged. It correctly prevents UI recovery
actions and backend recovery commands for `REVOKED`, but Angular only refreshes
Trading Core's canonical list. It does not compare Broker Service status. The
backend execution route also selects by execution mode rather than checking the
BrokerAccount lifecycle state.

## 9. SECURITY IMPACT

The dangerous direction is Trading Core `REVOKED` with Broker Service still
`CONNECTED` and its secret active after a lost callback response. Broker Service
can still resolve credentials and execute provider operations. This is an
authority mismatch, not secret leakage. The opposite direction fails closed at
Broker Service provider resolution but leaves stale business/UI state.

## 10. TRADING / PRODUCT IMPACT

Trading Core execution-intent creation verifies ownership but not connection
status. Routing chooses LIVE/PAPER by execution mode. Consequently, stale
`REVOKED` in Trading Core is not sufficient by itself to prove live execution
is rejected if Broker Service remains connected. Stale `CONNECTED` while
Broker Service is revoked is rejected by Broker Service, but can still create
misleading UI and execution intents.

## 11. CURRENT OBSERVABILITY

The original request returns an error when callback delivery fails and Angular
shows safe unconfirmed-revocation feedback. There is no durable pending marker,
callback attempt count, retry schedule, divergence metric, or operator repair
workflow. Existing callback tests are in-memory and do not test restart or lost
response semantics.

## 12. EXISTING RECOVERY MECHANISMS

- Broker Service has an authenticated technical status GET, but Trading Core has
  no client that uses it for reconciliation.
- Trading Core has unrelated execution reconciliation and domain-specific
  durable outboxes; neither handles broker-account lifecycle callbacks.
- Broker Service already has scheduling enabled for fee capture, but no account
  lifecycle scheduler.
- No callback retry, user-triggered account resynchronization, or scheduled
  broker-account comparison exists.

## 13. SOLUTION CANDIDATES

| Option | Restart-safe | Durable | Complexity | New infrastructure | Solves demonstrated risk | V1 fit |
|---|---|---|---|---|---|---|
| A - Do nothing | NO | NO | LOW | NO | NO | NO |
| B - Bounded synchronous callback retry | NO | NO | LOW | NO | PARTIAL | PARTIAL |
| C - Explicit pull resynchronization | PARTIAL | PARTIAL | LOW/MEDIUM | NO | PARTIAL | PARTIAL |
| D - User-triggered reconciliation | NO | NO | LOW | NO | NO | NO |
| E - Durable reconciliation record plus scheduled retry | YES | YES | MEDIUM | NO platform infrastructure; local persistence/scheduler | YES | YES |
| F - Transactional outbox | YES | YES | HIGH | YES | YES | NO |
| G - Message broker/event architecture | YES | YES | VERY HIGH | YES | YES | NO |

## 14. MINIMAL CONSISTENCY REQUIREMENT

`DURABLE_EVENTUAL_CONSISTENCY` is required. Strong synchronous consistency is
not practical across service databases and HTTP. Best effort is insufficient
because the lost-response divergence survives restart and affects a security-
and trading-relevant state. A controlled durable retry path is the minimum.

## 15. SECOND-PASS FALSIFICATION

- Bounded retry fails if Broker Service crashes after Trading Core commits and
  before retry state exists.
- Pull reconciliation fails as the sole mechanism if no user or operator ever
  triggers it.
- Scheduled repair requires a durable pending target and idempotent callback;
  scanning all terminal rows forever is not sufficient.
- A generic outbox or Kafka layer is disproportionate to one terminal domain
  notification and is not needed to survive restart.
- No action is falsified by the response-lost case and the live routing path.

## 16. RECOMMENDED DESIGN

Use a small Broker Service domain reconciliation record or equivalent durable
state. Commit the secret/connection terminal transition and pending callback
target locally, callback after commit, retry pending delivery through existing
scheduling, and mark delivery durably. Make Trading Core's terminal `REVOKED`
callback idempotent for a null credential reference and reject any resurrection
or credential attachment. The safe authority hierarchy is:

`physical credential revocation > Broker Service REVOKED > Trading Core
projection > Angular display`.

This requires no Kafka, generic outbox, Saga, or distributed transaction.

## 17. STORY 0127 SCOPE - IF REQUIRED

Story 0127 should be a bounded implementation Story covering only:

- durable pending terminal-notification state in Broker Service;
- post-commit callback and restart-safe scheduled retry;
- idempotent Trading Core `REVOKED` callback;
- safe metrics/logging and bounded retry/backoff;
- tests for lost response, restart, duplicate callback, duplicate revoke, and
  no credential resurrection.

It should not include Market Data, execution redesign, Kafka, generic event
architecture, or UI redesign.

## 18. FINAL DECISION

**E - DURABLE_RECONCILIATION_REQUIRED.**

```text
Observed repository HEAD = 1cd03fdbaa94ca8b29fc9587313f90b9068435ed

Story 0125 present = YES
Story 0126 present = YES

KNOWN FAILURE

Can credential revocation succeed while Trading Core callback fails = YES,
but current same-database transaction normally rolls back Broker Service state;
the durable lost-response case is the reverse divergence.

Can Broker Service remain REVOKED while Trading Core remains stale = YES if a
future/external secret backend or split local commit exists; not the normal
current same-DB callback-exception path.

Can this divergence survive process restart = YES for the actual lost-response
case.

Can current automatic mechanisms repair it = NO.

Is callback idempotent = NO.

Is duplicate delivery safe = NO.

Is durable retry currently present = NO.

Is manual/user-triggered reconciliation currently present = NO.

Can stale Trading Core state affect trading behavior = YES / PARTIAL; it can
affect execution eligibility and route selection, while Broker Service may still
fail closed in one divergence direction.

Can stale state resurrect revoked credentials = NO in the guarded current
recovery paths.

Can REVOKED be transitioned back to CONNECTED = NO.

REQUIRED CONSISTENCY

DURABLE_EVENTUAL_CONSISTENCY

MINIMAL SAFE SOLUTION

Durable terminal-revocation notification state, post-commit callback retry,
idempotent REVOKED callback, and no resurrection path.

NEW INFRASTRUCTURE REQUIRED = NO

No platform infrastructure is required. A bounded domain persistence change and
existing scheduler support are required.

FINAL DECISION

E - DURABLE_RECONCILIATION_REQUIRED

NEXT ACTION

Create and approve a bounded implementation Story 0127. Do not implement it as
Kafka, a generic outbox, a Saga, or a distributed transaction.
```
