# Repository Analysis - Story 0127 Investigation

## Status

Read-only investigation completed. No production implementation is authorized
by this artifact.

## Sources and Authority

- Current repository code, tests, Git state, and runtime configuration are
  authoritative for current behavior.
- ADR-024 assigns BrokerAccount lifecycle authority to Trading Core, technical
  connection and secret authority to Broker Service, and requires controlled
  eventual consistency with idempotence and reconciliation when atomic
  inter-service transactions are impossible.
- Stories 0125 and 0126 are present in the observed baseline. Story 0125
  explicitly leaves callback reconciliation as a residual risk. Story 0126
  explicitly excludes a new reconciliation subsystem and preserves terminal
  REVOKED semantics.
- DevLog context lookup was attempted and failed with provider quota error
  `429 insufficient_quota`. No conclusion depends on unavailable DevLog
  context.
- Obsidian was not consulted; repository code, Stories, ADR-024, and the
  functional audit provide the relevant authoritative context.

## Git State

- Branch: `main`.
- HEAD: `1cd03fdbaa94ca8b29fc9587313f90b9068435ed`.
- `origin/main`: `1cd03fdbaa94ca8b29fc9587313f90b9068435ed`.
- Ahead/behind: `0/0`.
- Tracked modified files: none.
- Staged files: none.
- Pre-existing untracked file: `docs/architecture/reports/v1-functional-audit.md`.
- `git fetch origin` completed successfully.
- No source, test, configuration, database, credential, runtime, or Market Data
  state was modified.

## Story Baseline

- Story 0125 artifacts are present under
  `docs/architecture/stories/0125-revoke-broker-account/`; the implementation
  and review reports document the merged revoke behavior and its residual
  callback risk.
- Story 0126 artifacts are present under
  `docs/architecture/stories/0126-broker-account-connection-recovery/`; the
  implementation report documents successful validation and explicitly states
  that callback reconciliation remains unresolved.
- The V1 Functional Audit finding `GAP-002 - Broker Revocation Has No Durable
  Reconciliation State` is directionally correct, but its simplified ordering
  does not account for the current outer database transaction. The actual
  durable lost-response failure can produce the reverse state divergence.

## Current Ownership Model

| Fact | Authority | Replica/Projection | Can become stale? | Reconciliation direction |
|---|---|---|---|---|
| Broker account business identity | Trading Core | Broker Service `brokerAccountId` reference | Yes, technically lookup can be unavailable | Broker Service reads Trading Core ownership |
| User ownership | Trading Core | Broker Service connection owner and authenticated actor | Yes if records are manually inconsistent | Trading Core ownership remains authoritative |
| Provider identity | Trading Core `BrokerAccount`; Broker Service connection | Broker Service technical copy | Should not diverge under normal creation | Broker Service rejects mismatched ownership/lookup |
| Connection state | Broker Service technical connection for credential usability; Trading Core business lifecycle projection | The other service's status | Yes | Destructive security truth converges toward Broker Service revocation |
| Credential reference | Broker Service owns active reference; Trading Core stores opaque projection | Trading Core `credentialReference` | Yes | Clear Trading Core reference on `REVOKED`; never recreate from the projection |
| Credential existence | Broker Service secret store | No authoritative Trading Core copy | Yes from Trading Core perspective | Secret store and Broker Service connection dominate |
| Revocation state | Broker Service after committed local revoke; Trading Core terminal lifecycle after callback | Other service's status | Yes during failure window | `REVOKED` and absent/invalid credential dominate all recovery paths |

The services should not contain identical state. Trading Core owns business
identity and lifecycle decisions; Broker Service owns whether a technical
connection and usable secret exist. The actual inconsistency is a stale
projection that can permit an invalid business workflow or obscure a security
state, not ordinary presence of two status fields.

## Current Revoke Sequence

```text
HTTP DELETE /api/v1/broker-accounts/{accountId}
    -> Trading Core BrokerAccountController.revoke
    -> BrokerConnectionCommandClient.revoke
    -> Broker Service DELETE /api/v1/broker-accounts/{accountId}/credentials
    -> BrokerCredentialController.revoke
    -> BrokerCredentialConnectionService.revoke
       -> TradingCoreBrokerAccountClient.findOwned
       -> SecretRevoker.revoke (when active reference exists)
       -> BrokerConnection.revoke
       -> TradingCoreBrokerAccountClient.updateStatus(REVOKED, null reference)
    -> Trading Core InternalBrokerAccountController.update
    -> BrokerAccountService.updateStatus(REVOKED, ...)
    -> BrokerAccount.markRevoked
    -> HTTP 204 responses back to the user
```

Verified implementation details:

- Trading Core authorizes the user through its authenticated principal and
  delegates the credential command.
- Broker Service calls Trading Core `findOwned` before resolving its local
  connection.
- Secret revocation and connection mutation occur inside
  `BrokerCredentialConnectionService.revoke`, annotated `@Transactional`.
- The callback is a synchronous Feign call made before that Broker Service
  transaction returns.
- Trading Core's callback update is transactional through
  `BrokerAccountService` and applies the terminal domain transition.
- The normal Trading Core DELETE returns `204` only after the Broker Service
  command returns successfully. A callback exception therefore propagates as
  an unsuccessful request; there is no partial-success response contract.

## Durability / Commit Points

| Step | Service | Durable? | Reversible? | Idempotent? | Failure after this step causes divergence? |
|---|---|---:|---:|---:|---:|
| Trading Core ownership/authorization | Trading Core | No state change | N/A | Yes as a read | No |
| Secret row marked REVOKED | Broker Service DB | Only at transaction commit | Database rollback is possible before commit; physical external stores would differ | No: already revoked throws `SecretRevokedException` | Only if a future external store is used or transaction boundaries change |
| BrokerConnection marked REVOKED and reference cleared | Broker Service DB | Only at outer transaction commit; managed entity is flushed at commit | Rollback possible before commit | No explicit duplicate revoke guard | Yes if committed without successful callback |
| Trading Core callback accepted and committed | Trading Core DB | Yes | No: REVOKED is terminal | No: repeated REVOKED callback currently attempts the same terminal transition and can fail | Yes if response is lost and Broker Service rolls back |
| Original HTTP response received | Client/UI | No domain state | N/A | N/A | No additional state change |

The current implementation does not have a distributed transaction. It does
have one local Broker Service transaction spanning secret-row mutation,
connection mutation, and the synchronous callback attempt. A runtime exception
from the callback normally rolls the Broker Service transaction back. The
callback can nevertheless commit Trading Core first and then lose its response;
in that case Trading Core is `REVOKED` while Broker Service rolls back to its
previous connected state.

## Partial Failure Matrix

| Failure point | Broker Service | Trading Core | Credential | User sees | Permanent divergence possible? |
|---|---|---|---|---|---:|
| Trading Core authorization fails | Unchanged | Unchanged | Unchanged | Authorization/not-found error | No |
| Trading Core to Broker Service request fails before execution | Unchanged | Unchanged | Unchanged | 5xx/transport error | No |
| Broker ownership lookup fails | Unchanged | Unchanged | Unchanged | Broker command error | No |
| Secret revoke fails | Transaction rolls back; connection remains prior state | Unchanged | Active if same DB transaction rolls back | Error; UI does not confirm revoke | No under current DB store |
| Callback is rejected or unavailable before Trading Core commit | Transaction rolls back | Unchanged | Active if same DB transaction rolls back | Error/unconfirmed revoke | No durable divergence, but no retry exists |
| Callback commits Trading Core, response is lost, Broker transaction rolls back | Connected prior state | `REVOKED`, credential reference cleared | Active in Broker Service DB under current store | Error/unconfirmed revoke | Yes |
| Broker local transaction commits before callback in a future/external-store implementation | `REVOKED` | Stale prior state | Revoked/absent | Error/unconfirmed revoke | Yes |
| Duplicate callback after Trading Core already `REVOKED` | Depends on local transaction; callback currently fails terminal transition | `REVOKED` | Depends on Broker transaction | Error/conflict | Yes; duplicate delivery is not safe today |
| User retries original revoke after lost callback response | Broker sees Trading Core `REVOKED`, can attempt local revoke; callback duplicate can fail and roll back | `REVOKED` | May remain active if Broker transaction rolls back | Error/conflict | Yes |
| Trading Core receives callback and persistence fails before commit | Broker transaction rolls back when callback errors | Prior state | Active under current DB store | Error/unconfirmed revoke | No durable divergence under current store |

## Idempotency Analysis

| Operation | Classification | Evidence |
|---|---|---|
| Duplicate revoke while both sides connected | `SAFE_DUPLICATE_REJECTION` only if a secret is already non-active; otherwise performs a new command | Secret revoker rejects non-active secret; revoke method has no explicit terminal guard |
| Duplicate revoke after Broker Service is REVOKED | `UNSAFE`/`UNKNOWN` | `BrokerCredentialConnectionService.revoke` does not call `ensureNotRevoked`; it clears state and then retries the callback |
| Duplicate callback with Trading Core already REVOKED | `UNSAFE` | `BrokerAccount.markRevoked` rejects all transitions from `REVOKED`; no same-state idempotent branch exists |
| Retry after timeout/lost response | `UNSAFE` | Callback may have committed Trading Core while Broker Service rolled back; retry can repeat the failing callback path |
| Already REVOKED account connect/rotate/disconnect | `SAFE_DUPLICATE_REJECTION` | Story 0126 guards both Trading Core and Broker Service paths with `BrokerConnectionRevokedException` / `409` |
| Missing credential reference during revoke | `IDEMPOTENT` for local no-secret path | Revoke skips `SecretRevoker` when the reference is null and still attempts the callback |
| Missing Broker Service connection for never-connected account | `SAFE_DUPLICATE_REJECTION`/local fallback | Trading Core catches only `404` and performs local revoke for the `CREATED` case |
| Repeated Trading Core state update for non-terminal status | `PARTIAL` | `ensurePending` permits some repeated recovery callbacks, but this does not apply to `REVOKED` |

There is no durable callback retry state, no callback operation identifier, and
no generic deduplication record for lifecycle notifications.

## Story 0126 Interaction

Story 0126 reduces accidental misuse but does not reconcile cross-service
state:

- `REVOKED` is terminal in both domain enums and exposes no disconnect,
  reconnect, or rotate action in Angular.
- Broker Service rejects recovery commands for a locally or remotely revoked
  connection before provider validation or secret mutation.
- Angular refreshes canonical Trading Core state after successful commands, but
  the Accounts page does not compare Trading Core state with Broker Service
  connection status.
- A stale Trading Core `CONNECTED` projection can still display a connected
  account after Broker Service has durably revoked it in a future split-commit
  design; current UI visibility is not authorization.
- A stale Trading Core `REVOKED` projection with Broker Service still
  connected can prevent recovery commands through Broker Service's Trading Core
  lookup, but current execution routing uses Trading Core execution mode rather
  than checking lifecycle status. The Broker Service then authenticates through
  its own connection and can potentially execute while Trading Core says
  `REVOKED`.

## Security Impact

The actual current-store lost-response case can leave Trading Core `REVOKED`
while Broker Service retains an active secret and `CONNECTED` connection. That
is not credential exposure, but it is a security-relevant authority mismatch:

- Broker Service's own connection and secret remain usable.
- Trading Core's terminal business state does not prevent its live execution
  routing from creating/submitting an execution intent because execution
  admission verifies ownership but does not verify `BrokerConnectionStatus`.
- Broker Service provider resolution requires its local connection to be
  `CONNECTED`, so the Broker Service can accept the routed operation when the
  secret remains active.

The opposite divergence, Trading Core stale `CONNECTED` while Broker Service
durably `REVOKED`, is safer for credential use because Broker Service rejects
provider resolution and credential recovery. It remains a correctness/UI risk.

No evidence shows secrets are exposed in callbacks, UI responses, or logs. The
current logs record provider account IDs and execution attempts, not secret
material.

## Trading / Product Impact

Stale state can affect more than presentation:

- `ValidateAndCreateService` verifies BrokerAccount ownership but not its
  connection lifecycle status before creating an execution intent.
- `RoutingBrokerExecutionAdapter` selects the live adapter by execution mode,
  not by `BrokerConnectionStatus`.
- Broker Service itself rejects a revoked local connection during provider
  resolution, so one divergence direction fails closed at execution time.
- The reverse direction, Trading Core `REVOKED` with Broker Service
  `CONNECTED`, can leave a routeable active credential and is not proven to be
  rejected by Trading Core before live submission.

The impact is therefore a demonstrated authorization/execution eligibility
risk, not merely stale UI. It does not justify rolling a revoked credential
back to connected.

## Current Observability

- Callback exceptions propagate through Feign and the controller; the original
  revoke request returns an error rather than a confirmed success.
- Angular displays safe failure feedback and does not show callback details.
- Broker Service has normal application logging and execution/provider metrics,
  but no persisted lifecycle-notification attempt, pending-reconciliation
  record, callback metric, or operator-facing divergence state.
- The callback-failure test proves the exception path in memory, but not
  transaction rollback, lost-response behavior, restart recovery, or durable
  cross-service repair.
- A failure can therefore survive only in logs and stale database projections;
  there is no durable diagnostic marker today.

## Existing Recovery Mechanisms

- Existing `GET /api/v1/broker-accounts/{accountId}/connection-status` in
  Broker Service exposes technical status to an authenticated product caller,
  but Trading Core has no client for it and Angular does not use it to compare
  authorities.
- Existing Trading Core internal `GET /internal/v1/broker-accounts/{id}` is
  the ownership lookup used by Broker Service; it is not a Broker Service
  status query.
- Existing execution reconciliation concerns uncertain broker order outcomes,
  not broker-account lifecycle callbacks.
- Existing durable outboxes and scheduled delivery services are domain-specific
  to risk/challenge workflows. No generic lifecycle outbox is present.
- Broker Service already enables scheduling for fee capture, but no lifecycle
  reconciliation scheduler exists.
- No callback retry, user-triggered account resynchronization, or scheduled
  broker-account comparison is present.

## Solution Candidates

| Option | Restart-safe | Durable | Complexity | New infrastructure | Solves demonstrated risk | V1 fit |
|---|---|---|---|---|---|---|
| A - Do nothing / current explicit error | NO | NO | LOW | NO | NO | NO |
| B - Bounded synchronous callback retry | NO | NO | LOW | NO | PARTIAL | PARTIAL |
| C - Idempotent pull-based resynchronization on explicit command | PARTIAL | PARTIAL | LOW/MEDIUM | NO | PARTIAL | PARTIAL |
| D - User-triggered reconciliation on refresh/recovery | NO | NO | LOW | NO | NO | NO |
| E - Small durable lifecycle reconciliation record plus scheduled retry | YES | YES | MEDIUM | NO platform infrastructure; local schema/scheduler required | YES | YES |
| F - Generic transactional outbox | YES | YES | HIGH | YES domain outbox machinery | YES | NO |
| G - Kafka/message broker/event architecture | YES | YES | VERY HIGH | YES | YES | NO |

Bounded retry alone fails the crash-after-commit test. User-triggered repair
does not guarantee that a user returns to the Accounts page. Pull-based repair
is useful as an operator or explicit command, but not as the sole V1 guarantee.

## Minimal Consistency Requirement

Required guarantee: `DURABLE_EVENTUAL_CONSISTENCY`.

Strong synchronous consistency is not realistic across a database mutation,
credential backend, and HTTP callback. Best effort is insufficient because a
lost response can leave a terminal Trading Core state opposite an active
Broker Service credential. Controlled eventual convergence is the minimum that
prevents silent permanent divergence and survives process restart.

## Second-Pass Falsification

### If recommending bounded retry

Broker Service can crash after Trading Core commits the callback and before its
local transaction commits or before any retry state is stored. In-memory retry
cannot repair that state after restart. Reject as sufficient.

### If recommending pull reconciliation

An explicit pull has no guarantee of execution if the user never revisits the
account. It is useful as a repair command, not as the only durable guarantee.

### If recommending scheduled reconciliation

The worker must distinguish intentional `REVOKED` state from pending callback
delivery. A durable pending target/status is required; scanning every revoked
row forever is not a safe substitute. The callback must also be idempotent.

### If recommending durable job/outbox

A generic outbox or message broker is disproportionate. The domain has one
small terminal notification fact, an existing BrokerConnection local store,
and existing scheduling support. A bounded domain record/job is sufficient.

### If recommending no action

The lost-response case can survive restart, current duplicate callback is not
idempotent, and Trading Core's stale terminal projection can affect live
execution routing. No-action is falsified.

## Recommended Design

Recommendation class: **E - DURABLE_RECONCILIATION_REQUIRED**.

The smallest safe design should be:

1. Persist the Broker Service destructive local transition and a durable pending
   Trading Core synchronization target in one Broker Service local transaction.
   The target is terminal `REVOKED`; it must never request restoration.
2. Commit that local transaction before attempting the callback. This makes
   credential revocation and Broker Service `REVOKED` durable even if the
   callback process fails.
3. Attempt the existing callback immediately after commit, then retry pending
   notifications through a bounded scheduled worker using existing scheduling
   support and service authentication.
4. Make Trading Core's `REVOKED` callback idempotent for an already revoked
   account when the incoming credential reference is null. Reject any attempt
   to move `REVOKED` to a non-terminal state or attach a credential reference.
5. Mark notification delivery durably after successful callback. Duplicate
   delivery must remain safe if the delivery marker write fails after the
   callback succeeds.
6. Keep exhausted failures as durable pending/failed operational state with
   safe metrics/logging; never report the command as fully reconciled merely
   because the user request returned.

The minimum domain information is a broker-account identity, target state
`REVOKED`, durable notification status, attempt count, next-attempt time,
last-attempt time, and safe failure metadata. An operation UUID is optional if
the broker account and terminal target uniquely identify the notification.

Authority is not last-writer-wins:

```text
physical credential revocation / absent active secret
    > Broker Service terminal connection state
    > Trading Core lifecycle projection
    > Angular display
```

The mechanism must converge toward revocation. It must not restore a secret or
transition `REVOKED` to `CONNECTED`.

## Story 0127 Scope - If Required

A bounded implementation Story is justified, but this investigation does not
implement it. The scope should be limited to:

- a Broker Service durable pending terminal-notification record or equivalent
  domain persistence;
- a post-commit callback attempt and restart-safe scheduled retry;
- idempotent Trading Core handling of repeated terminal `REVOKED` callbacks;
- bounded retry/backoff and safe lifecycle-notification metrics/logging;
- focused tests for callback rejection, lost response, restart/pending delivery,
  duplicate callback, duplicate revoke, and no credential resurrection;
- no Kafka, generic event platform, Saga coordinator, distributed transaction,
  Market Data change, or UI redesign.

## Final Decision

The investigation proves that a new bounded Story is required. The risk is not
solved by Story 0125 or Story 0126, and neither current synchronous failure
reporting nor user refresh guarantees convergence. A small durable domain
reconciliation mechanism is justified; transactional outbox and broader event
architecture are not.
