# Implementation Plan - Story 0176

## Decision Boundary

The user approved the architectural extension required for partial close. The
implementation will therefore extend the existing broker-neutral command
contract rather than encode cTrader-only quantity semantics in an opaque scope.

The extension must remain compatible with full close, Kraken behavior and PAPER
close behavior.

## Architectural Record

Add a focused ADR amendment/decision record, using the next available ADR
identifier, documenting:

- optional provider-neutral close quantity;
- `null` quantity as authoritative full close;
- positive quantity as partial close request;
- provider-specific volume conversion remaining in Broker Service;
- partial reconciliation as a distinct broker-neutral result;
- no-blind-retry behavior for unknown outcomes;
- no protection modification in this Story.

The ADR must explicitly supersede the current ADR-040 partial-close exclusion
for this capability while preserving its provider boundary and full-close
semantics.

## Contract Propagation

Extend the close request path with an optional `BigDecimal closeQuantity`:

`Angular/API request -> Trading Core close command -> broker port -> Feign DTO -> Broker Service capability`

Required behavior:

- `null` means full close;
- non-null means partial close in Trading OS base quantity units;
- zero and negative values are rejected at the provider-neutral boundary;
- the quantity is persisted with the durable close command so retries and
  restart recovery cannot reinterpret the request.

Extend reconciliation results to distinguish:

- exposure absent;
- exposure still present with an authoritative remaining quantity;
- command confirmed not executed;
- inconclusive.

The Trading Core close command needs a durable partial-close terminal state and
remaining quantity/result data. Add the smallest migration required by the
existing command persistence model; do not introduce a new position aggregate.

## cTrader Adapter

Add `CTraderPositionManagementCapability` in Broker Service and register it in
`CTraderBrokerProvider`.

### Resolve

- Validate the broker account belongs to the authenticated owner and is a
  cTrader account.
- Require the configured provider environment to match the bound connection.
- Require the durable cTrader `positionId` reference.
- Read an authoritative account observation and require exactly one matching
  provider position belonging to the bound external account.
- Persist an opaque provider-owned mutation scope containing the account and
  position identity needed by Broker Service; do not expose cTrader payloads to
  Trading Core.
- Fail closed for missing, unknown or ambiguous identity.

### Execute

- Check `CTraderRequestSafetyGuard.requireExecutionEnabled()` before provider
  mutation.
- Revalidate cTrader account, environment, permission and position identity.
- Read the current provider position before constructing the close request.
- For full close, use the current authoritative provider volume, not stale local
  quantity.
- For partial close, convert the requested Trading OS quantity through
  `CTraderExecutionInstrumentContract.providerVolume(...)` and reject invalid,
  non-representable, below-minimum, above-maximum or invalid-step quantities.
- Submit `ProtoOAClosePositionReq` semantics: account ID, exact position ID and
  provider volume. No opposite-side synthetic order is used.
- Classify explicit provider errors as rejected and transport/ambiguous outcomes
  as unknown.
- Use the existing idempotency key as correlation, but do not claim provider
  exactly-once semantics that cTrader does not guarantee.

### Reconcile

- Re-read authoritative cTrader position and deal facts for the exact position
  identity.
- Determine unchanged, partially reduced or fully absent exposure.
- Return the new broker-neutral partial/absent result only from authoritative
  provider evidence.
- Never blindly resubmit after unknown.
- Preserve Story 0173 no-resurrection and restart behavior.

## Persistence

Extend the existing Trading Core position-close command persistence with the
minimum fields required for:

- requested close quantity;
- partial-close/reconciliation result;
- authoritative remaining quantity where applicable.

Use a new Trading Core Flyway migration only if the existing entity cannot
persist these facts without ambiguity. No Broker Service or Market Data schema
change is planned.

## Tests

### Broker Service

- capability resolution for cTrader, Kraken and unsupported providers;
- cTrader position identity and account/environment validation;
- full close for long and short positions;
- partial close quantity conversion and validation;
- exact cTrader close payload fields;
- safety-disabled rejection before provider mutation;
- provider rejection and unknown transport result;
- authoritative partial/full reconciliation;
- duplicate/unknown retry behavior;
- no provider call for already-closed positions.

### Trading Core

- API and Feign quantity propagation;
- full-close backward compatibility;
- partial close persistence and lifecycle transitions;
- partial reconciliation retaining remaining quantity;
- full reconciliation reaching terminal state;
- idempotent duplicate and concurrent close commands;
- restart/reload behavior;
- no-resurrection regression.

### Gateway and frontend

- preserve the existing `/api/v1/accounts/**` route;
- retain the broker-neutral Angular workflow;
- update only API models/tests if the optional quantity or partial result must
  be displayed.

## Validation

Run at minimum:

```text
mvn -f broker-service/pom.xml test
mvn -f trading-core/pom.xml test
mvn -f gateway/pom.xml test
npm run test:ci (trading-os-web)
npm run build (trading-os-web)
git diff --check
```

No cTrader credentials, authenticated provider mutation, DEMO close, LIVE close,
or runtime safety-flag activation will be performed.

## Non-Changes

- No Market Data V21-V23 runtime migration.
- No order-book backfill, retention, depth subscription or storage movement.
- No strategy activation or risk-policy change.
- No automatic position management.
- No stop-loss/take-profit modification.
- No frontend redesign.
