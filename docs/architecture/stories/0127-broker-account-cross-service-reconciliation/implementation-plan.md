# Implementation Plan - Story 0127

## Status

Implementation Plan completed. Production implementation is not authorized
until this plan receives explicit human approval.

## Planned Outcome

Introduce the smallest domain-specific durable reconciliation mechanism for
terminal broker-account revocation:

```text
local secret/connection revoke + pending notification commit
    -> callback after local commit
    -> durable delivery acknowledgement
    -> restart-safe retry when delivery fails
```

The plan does not introduce Kafka, a generic outbox, Saga coordination, or a
distributed transaction.

## Design Decisions

### 1. Broker Service owns the pending delivery fact

The pending fact belongs in Broker Service because it owns the irreversible
credential and technical connection transition. Trading Core remains the
business lifecycle projection and callback target.

### 2. Local commit precedes callback delivery

The local transaction will persist:

- revoked secret state;
- `BrokerConnection = REVOKED` with no active credential reference;
- one pending terminal notification for the broker account.

The callback will execute only after this transaction commits. This prevents a
callback failure from rolling back the irreversible local security operation.

### 3. Domain-specific reconciliation record, not a generic outbox

Add one bounded persistence model dedicated to broker-account terminal
notification. It must be unique per broker account and target state. Proposed
information:

- reconciliation ID;
- broker-account ID;
- target state, fixed to `REVOKED` for this Story;
- delivery status: `PENDING`, `DELIVERED`, or `FAILED`;
- attempt count;
- next attempt time;
- last attempt time;
- lease/claim expiry for concurrent workers;
- safe last-error code/message;
- created and updated timestamps;
- optimistic version.

No credential material, provider payload, access token, or raw exception body
may be stored.

### 4. Terminal callback is idempotent

Trading Core will accept a repeated `REVOKED` callback when the account is
already `REVOKED` and the credential reference is null. It will reject a
terminal callback carrying a credential reference or any attempt to transition
`REVOKED` to a usable state.

### 5. Retry is durable and bounded per attempt

The scheduler will claim due records with a lease, attempt delivery, and persist
the result in a separate local transaction. A callback that succeeded but whose
delivery acknowledgement failed may be delivered again; idempotent Trading
Core handling makes this safe.

The retry policy will use bounded batch size and configurable delay/backoff. A
`FAILED` record remains durable and observable after retry exhaustion. The
implementation must not report synchronization as confirmed solely because the
initial HTTP command returned.

## Implementation Steps

### 1. Add Broker Service reconciliation persistence

Expected files:

- `broker-service/src/main/resources/db/migration/V6__broker_account_revocation_reconciliation.sql`
- a reconciliation entity under
  `broker-service/src/main/java/com/hope/trading/broker_service/connection/...`
- a Spring Data repository for due-record lookup and atomic claim/update

Add the unique broker-account/target constraint and indexes for delivery status
and next-attempt time. Preserve Flyway plus Hibernate `ddl-auto=validate`
conventions.

The repository must support:

- create-or-return pending record idempotently;
- claim one bounded batch of due records;
- mark delivery successful;
- mark delivery failed and schedule the next attempt;
- release expired leases after process restart.

### 2. Split local revoke from notification delivery

Refactor the current `BrokerCredentialConnectionService.revoke` flow without
changing its public controller contract:

1. Validate ownership and resolve the local connection.
2. In a local transaction, revoke the active secret when present, mark the
   connection terminal, and create/retain the pending notification.
3. Return from the local transaction.
4. Attempt the existing Trading Core callback.
5. Mark the notification delivered or failed in a separate transaction.

The immediate callback may still be attempted within the same request after the
local commit. The request must return an error or an explicit unconfirmed result
when delivery is not confirmed, preserving the existing safe UI behavior.

The implementation must prevent a duplicate request from creating multiple
pending terminal notifications or performing credential rotation/recreation.

### 3. Add restart-safe scheduled delivery

Expected files:

- a Broker Service scheduler under the existing scheduling conventions;
- configuration properties in `application.properties` and applicable profile
  files;
- delivery service tests.

The scheduler will:

- run only when reconciliation delivery is enabled by configuration;
- claim a bounded batch of due records;
- invoke the existing `TradingCoreBrokerAccountClient.updateStatus` callback
  with `REVOKED` and a null credential reference;
- persist delivery or retry state without exposing callback payloads;
- tolerate process restart through durable leases and next-attempt timestamps.

No broker provider call is made by this scheduler.

### 4. Make Trading Core terminal callback idempotent

Expected files:

- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/application/BrokerAccountService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/domain/BrokerAccount.java`
  only if domain-level validation is required;
- `BrokerAccountExceptionHandler.java` only if a new deterministic conflict
  exception is introduced.

For `REVOKED` callbacks:

- null credential reference plus existing `REVOKED` state returns the current
  projection without mutation;
- non-null credential reference is rejected;
- any non-terminal transition remains rejected by the lifecycle state machine.

The callback remains ownership-checked through the existing service principal
and internal endpoint.

### 5. Add safe observability

Add structured lifecycle-notification metrics/logs for:

- notification created;
- immediate callback delivered;
- retry attempted;
- retry scheduled;
- delivery exhausted/failed.

Log only broker-account and reconciliation identifiers plus stable safe reason
codes. Never log authorization headers, credentials, provider payloads, or raw
exception content.

### 6. Add focused tests

Broker Service tests:

- local revoke commits pending notification before callback;
- callback rejection leaves durable pending state;
- response-lost simulation leaves retryable state;
- scheduler retries after process/repository restart simulation;
- duplicate revoke does not recreate or rotate a credential;
- duplicate delivery is safe;
- successful delivery is marked once;
- acknowledgement failure causes safe duplicate retry;
- retry claim leases prevent concurrent duplicate workers;
- no secret material enters reconciliation persistence or logs.

Trading Core tests:

- first `REVOKED` callback transitions the account and clears the reference;
- duplicate null-reference `REVOKED` callback succeeds idempotently;
- non-null terminal callback is rejected;
- `REVOKED` cannot transition to `CONNECTED`, `DISCONNECTED`, or validation
  states;
- ownership and service-principal boundaries remain enforced.

Regression tests:

- existing Story 0125 revoke behavior;
- existing Story 0126 recovery and terminal-state behavior;
- existing connection and credential failure behavior.

## Files Expected To Change

Expected production and migration surface:

- `broker-service/src/main/resources/db/migration/V6__broker_account_revocation_reconciliation.sql`
- Broker Service connection reconciliation entity/repository/service/scheduler
  classes;
- `BrokerCredentialConnectionService.java`;
- `TradingCoreBrokerAccountClient.java` only if delivery metadata or timeout
  behavior requires a contract-preserving change;
- `trading-core/.../BrokerAccountService.java`;
- focused Broker Service and Trading Core tests;
- relevant application properties for bounded retry configuration.

No Angular, Gateway, Market Data, Market Intelligence, provider adapter, or
database unrelated to Broker Service reconciliation should change.

## Transaction and Concurrency Rules

- Secret revocation and BrokerConnection terminal mutation remain one local
  Broker Service transaction.
- Notification creation must commit with the local terminal mutation.
- Callback delivery must not execute inside that local transaction.
- Delivery acknowledgement must use a separate transaction.
- A lease or optimistic claim must prevent multiple scheduler workers from
  concurrently delivering the same pending record without coordination.
- Duplicate callback delivery must be safe even if acknowledgement persistence
  fails after the callback commits.
- No rollback or compensating action may restore a revoked credential.

## API and User Contract

- Preserve the existing public DELETE endpoint.
- Preserve safe error handling for the initial request.
- Do not expose reconciliation internals, provider errors, or credentials to
  Angular.
- Do not add a public reconciliation endpoint unless implementation evidence
  proves it is required for safe operations.
- Internal callback authorization remains service-authenticated.

## Validation Plan

1. Run focused Broker Service reconciliation and revoke tests.
2. Run focused Trading Core lifecycle and callback tests.
3. Run full Broker Service Maven tests.
4. Run full Trading Core Maven tests.
5. Validate Flyway migration against the independent Broker Service database.
6. Run `git diff --check`.
7. Review the complete diff for credential leakage, Market Data changes,
   unrelated schema changes, and unauthorized artifacts.
8. Perform safe runtime checks only; do not invoke real revoke, reconnect,
   credential rotation, trade execution, or Market Data restart.

## Risks and Mitigations

- **Callback succeeds but acknowledgement fails:** duplicate delivery is safe
  because Trading Core accepts repeated terminal callbacks.
- **Broker Service restarts during delivery:** durable pending state and leases
  allow later retry.
- **Trading Core remains unavailable:** pending state remains visible and no
  credential resurrection occurs.
- **External secret-store migration changes rollback semantics:** local terminal
  state and pending notification are persisted before callback delivery.
- **Concurrent revoke/reconciliation:** terminal lifecycle guards, unique
  pending identity, optimistic versioning, and delivery leases prevent unsafe
  duplicate mutation.
- **Retry exhaustion:** durable failed status and safe metrics/logs prevent the
  system from claiming complete synchronization.

## Documentation Outcome

The Story, Repository Analysis, and Investigation Report already document the
authority hierarchy and consistency requirement. Implementation should update
only migration/runtime documentation if the new retry properties require
operational configuration instructions.

## Approval Boundary

No production or test code has been modified for this plan. Implementation may
begin only after explicit human approval of this Implementation Plan.
