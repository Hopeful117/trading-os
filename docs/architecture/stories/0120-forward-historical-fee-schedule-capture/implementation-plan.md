# Implementation Plan - Story 0120

## Status

Implementation Plan prepared after explicit human approval of the Story and
Repository Analysis. This plan requires separate human approval before code
changes begin.

## Implementation Outcome

Implement bounded, passive, forward-only Kraken fee-schedule capture in Broker
Service.

The implementation will use Kraken `TradeVolume` as a current account/pair fee
observation. It will not claim historical provider effective times. For a valid
first observation at local time `T`:

```text
effectiveFrom = T
effectiveTo   = null
observedAt    = T
knownAt       >= T
origin        = CAPTURED
```

When a later valid observation has a different canonical schedule, the previous
open captured interval will be closed at the new observation boundary before the
new interval is appended. An identical observation will be idempotent and will
not create another row.

## Scope of Changes

### 1. Provider contract and Kraken mapping

- Add the smallest provider-neutral fee-schedule acquisition capability under
  the existing Broker Service capability boundary.
- Add Kraken-specific request/response DTOs under the Kraken infrastructure
  adapter only.
- Invoke `/0/private/TradeVolume` with:
  - the configured bounded instrument;
  - `aclass=derivatives` semantics represented in the provider request;
  - `fee_schedule=true` where useful for response validation;
  - the existing credential session and request signer.
- Map the active/current maker and taker fee values for the requested pair to
  `BigDecimal` decimal fractions by dividing documented percentage values by
  100 at the provider boundary.
- Reject responses when provider, pair, product class, maker/taker values,
  settlement asset, or active fee values cannot be resolved truthfully.
- Preserve existing provider exception translation and do not expose Kraken
  response DTOs through domain or application contracts.

### 2. Capture configuration and account scope

- Add Broker Service configuration properties for:
  - capture enabled flag, default `false`;
  - fixed delay or equivalent bounded cadence;
  - configured broker account IDs;
  - configured instrument/product mapping;
  - optional capture source/normalization version values.
- Add scheduling support to Broker Service using the existing repository
  convention: scheduling enabled by the application, scheduler excluded from
  the test profile, and capture disabled unless explicitly configured.
- Capture only configured connected accounts. Resolve credentials through
  `KrakenCredentialSession`; do not use global environment credentials as an
  account identity fallback.
- Keep each account/instrument capture bounded and independently failure-safe so
  one provider failure does not prevent other configured observations.

### 3. Forward-only persistence orchestration

- Add an application service that:
  1. obtains the current provider observation;
  2. normalizes and validates it;
  3. obtains one local observation timestamp from an injected `Clock`;
  4. computes the canonical schedule fingerprint;
  5. compares it with the open captured schedule for the same account/provider/
     product/instrument scope;
  6. returns without writing when the fingerprint is unchanged;
  7. closes the previous open schedule at the new observation timestamp and
     appends the new schedule when the fingerprint changed.
- Extend the repository/application port only as required to support an atomic
  close-and-append operation while preserving the existing fingerprint and
  overlap invariants.
- Use a transaction boundary that prevents an observable overlapping captured
  interval during a schedule transition.
- Preserve `HistoricalFeeScheduleReader` behavior unchanged for callers.
- Do not persist partial, synthetic, zero, guessed, or backdated schedules.

### 4. Credential permission handling

- Verify the current Kraken credential permission model against the documented
  `Funds permissions - Query` requirement.
- Reuse the existing read-only credential policy when it is semantically
  sufficient.
- If the current permission vocabulary cannot represent the required query
  permission truthfully, add the smallest provider-neutral permission change
  and update its validation tests. Do not broaden permissions or weaken
  credential validation.

### 5. Tests

Add focused tests for:

- TradeVolume request path, pair/class parameters, signing, and credential use;
- successful current maker/taker mapping;
- percentage-to-decimal-fraction normalization;
- missing pair, missing maker/taker, malformed decimal, unsupported product,
  and ambiguous response failures;
- provider authentication, authorization, rate-limit, unavailable, and
  protocol failures;
- first observation becoming eligible only from its local capture boundary;
- unchanged observations being idempotent;
- changed observations closing the previous open interval without overlap;
- strict `knownAt` cutoff behavior remaining unchanged;
- disabled capture and bounded account/instrument configuration;
- scheduler exclusion from the test profile;
- concurrent duplicate capture safety where the existing repository pattern
  supports it.

## Expected Files and Areas

Expected changes are limited to Broker Service:

- `broker-service/src/main/java/.../BrokerServiceApplication.java`
- `broker-service/src/main/java/.../broker/domain/capability/`
- `broker-service/src/main/java/.../broker/application/port/`
- `broker-service/src/main/java/.../broker/application/service/`
- `broker-service/src/main/java/.../broker/infrastructure/persistence/`
- `broker-service/src/main/java/.../broker/infrastructure/provider/kraken/`
- `broker-service/src/main/java/.../broker/scheduler/` or the repository’s
  established scheduler package
- `broker-service/src/main/java/.../broker/config/` or the established
  configuration package
- corresponding focused tests under
  `broker-service/src/test/java/`

No new database migration is expected because V3–V5 already provide the schema,
rate-unit constraint, fingerprint uniqueness, and PostgreSQL no-overlap
constraint. A migration may be added only if implementation reveals a concrete
contract gap that cannot be solved within the existing schema; that would
require returning to the approval gate.

## Invariants

- No current provider response is used for an execution time before its local
  capture boundary.
- No provider-effective timestamp is invented from current tier data.
- `observedAt <= knownAt` always holds.
- Captured schedules for one account/provider/product/instrument scope never
  overlap.
- Identical semantic observations produce one fingerprint and one row.
- Provider-specific Kraken types terminate at the adapter boundary.
- Account credentials remain resolved through encrypted stored references.
- Broker Service does not make strategy, Risk, execution, settlement, or P&L
  decisions.

## Validation Plan

1. Run focused Broker Service tests for provider mapping, capture orchestration,
   persistence, temporal reads, and scheduler/configuration behavior.
2. Run:

   ```text
   cd broker-service && ./mvnw verify
   ```

3. Run `git diff --check`.
4. Validate the running Broker Service database without reset:
   - Flyway remains valid;
   - existing rows, if any, remain intact;
   - no captured schedule overlap exists;
   - capture remains disabled unless explicitly configured.
5. If safe credentials and the required Kraken permission are available, perform
   one bounded runtime capture for the configured instrument and verify one
   truthful canonical row, its local forward boundary, rate unit, fingerprint,
   and strict reader eligibility.
6. If real provider validation is unavailable, report it as blocked evidence;
   do not claim runtime capture success and do not fabricate a row.

## Non-Goals and Stop Conditions

Stop implementation and request guidance if:

- Kraken does not return a defensible account/pair maker/taker observation;
- the current permission model cannot safely represent or validate the required
  query permission without an architectural decision;
- the existing schema cannot express forward-only semantics without changing the
  approved contract;
- atomic interval transition cannot be implemented without weakening the
  no-overlap invariant;
- implementation requires public APIs, cross-service transport, or changes to
  Trading Core or Market Intelligence;
- provider behavior would require historical backfill or backdating.

## Documentation and Reporting

- Preserve Story 0120 and this plan as the scope record.
- Update runtime/operator documentation only if the implementation adds a new
  required configuration or activation procedure.
- The eventual Implementation Report must state the actual provider outcome,
  row count/runtime evidence, validation commands, documentation outcome, and
  whether real provider capture was available.
- No commit, push, merge, or deployment is part of implementation.
