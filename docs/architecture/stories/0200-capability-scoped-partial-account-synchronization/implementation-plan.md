# Implementation Plan - Story 0200

## Operating Mode

This Story remains READ-ONLY with respect to the provider. The existing FTMO
Free cTrader account `49411967` may be used for controlled synchronization only.
No order, OAuth restart, credential rotation, account creation or provider
mutation is authorized.

Preserve all uncommitted Story 0198 and Story 0199 files and implementation
changes. No commit, push, merge or branch cleanup is part of this work.

## Design Decision

Implement capability-scoped synchronization in Trading Core without weakening
the Broker Service observation contract and without adding cTrader semantics to
Trading Core.

The minimal representation will be:

- a structured provider-neutral synchronization result returned by the V2
  endpoint;
- durable account-level observation metadata for completeness, freshness,
  timestamps, collection availability and bounded reasons;
- existing balances and positions updated only when the corresponding snapshot
  collection is authoritative and valid;
- unavailable or truncated collections retained as unavailable metadata and
  skipped for mutation;
- provider equity, account margin, free margin and deal-derived financial facts
  left unavailable unless already authoritative;
- risk evidence continues to be read from the fresh provider snapshot and
  remains fail-closed for mandatory missing evidence.

No separate provider-specific persistence model is introduced.

## 1. Provider-Neutral Observation Metadata

Add the smallest account-level persistence projection needed to avoid losing
capability status between synchronizations:

- observation completeness and freshness;
- observation and capture/completion timestamps;
- positions, orders, PnL and deals availability;
- bounded unavailability reasons.

Use provider-neutral enums or persisted strings consistent with the existing
snapshot DTOs. Add one common migration following the existing Trading Core
Flyway naming convention. Keep fields nullable for existing accounts and avoid
persisting raw provider payloads, credentials or secrets.

The metadata is descriptive only. It must not make a stale local value current,
and it must not be consumed as a substitute for a fresh risk snapshot.

## 2. Synchronization Service Refactor

Change `BrokerSynchronizationService` V2 synchronization from `void` to a
provider-neutral result record containing at least:

- account ID and observation timestamps;
- status such as `SYNCED_COMPLETE` or `SYNCED_PARTIAL`;
- persisted capability names;
- collection availability;
- completeness and freshness;
- bounded reasons;
- an explicit `riskReady=false`/diagnostic-only indication where applicable.

Update `BrokerSynchronizationServiceImpl` in this order:

1. Validate owner, account mapping, LIVE mode, provider identity, external
   account identity, freshness and mandatory account identity facts before
   mutation.
2. Reject null/malformed snapshots, stale observations, invalid account facts,
   duplicate position references and invalid available position facts atomically.
3. Persist valid account metadata and available balances when the account fact
   contract is valid.
4. Reconcile positions only when position availability is
   `AVAILABLE_EMPTY` or `AVAILABLE_NON_EMPTY`.
5. Skip position mutation for `UNAVAILABLE_OR_ABSENT` or `TRUNCATED`; retain
   existing local trades and expose the capability status as unavailable.
6. Apply deal-based close/baseline logic only when deal availability permits
   authoritative use. Never close or alter realized PnL from an unavailable or
   truncated deal collection.
7. Do not infer order state from an unavailable order collection. Record its
   availability; do not introduce order persistence in this Story.
8. Copy provider equity only when its existing provenance and quality contract
   is satisfied. Never clear or synthesize prior equity because the new snapshot
   lacks equity.
9. Save once inside the existing transaction and return the structured result.

The existing `recalculateAccount` path must remain limited to the legacy
non-V2 synchronization path. V2 synchronization must not locally recalculate
LIVE equity.

## 3. HTTP Boundary

Update `AccountSynchronizationV2Controller` to return the structured result as
JSON with `ResponseEntity`.

Use HTTP 200 for a valid fresh partial observation that persisted at least one
safe capability. Continue using explicit conflict/error responses for stale,
malformed, identity-invalid, provider-invalid or otherwise unsafe observations.

Update exception and controller tests so the response distinguishes:

- successful complete synchronization;
- successful partial synchronization;
- rejected incomplete/stale observation when no safe persistence is possible;
- identity and validation failures.

The existing `/api/v1` synchronization path remains unchanged.

## 4. Persistence Migration and Compatibility

Add one Trading Core common migration for the account observation projection.

The migration must:

- be additive and safe for existing accounts;
- use bounded string lengths for reasons and enum values;
- avoid provider-specific columns or raw JSON payloads;
- preserve existing account equity, balances and trades;
- be covered by the existing persistence integration test setup.

If repository conventions require a separate PostgreSQL migration, follow the
existing common/PostgreSQL split rather than introducing a new migration style.

## 5. Tests

Extend `BrokerSynchronizationServiceImplTest` and related persistence tests with:

- fresh partial snapshot persists balance and available empty positions/orders
  while equity, margin and deals remain unavailable;
- unavailable positions preserve existing trades and metadata records the
  unavailable capability;
- truncated positions/deals preserve prior state and do not apply close logic;
- available empty collections are distinguishable from unavailable collections;
- available positions update existing trades without duplication;
- unavailable deals never close a trade or alter realized PnL;
- invalid identity, provider mismatch, stale snapshot, malformed account facts,
  duplicate references and invalid position facts remain atomic failures;
- repeated identical partial snapshots are idempotent;
- unavailable equity is not copied, derived or accepted by risk evidence;
- aggregate risk authorization remains blocked when mandatory evidence is
  missing.

Update `AccountSynchronizationV2ControllerTest` for the structured 200 partial
response and existing conflict mappings.

Run or extend Broker Service snapshot contract tests only if the DTO or neutral
availability contract changes. Preserve existing cTrader tests for empty,
incomplete, stale and identity-mismatch observations.

## 6. Controlled Runtime Validation

After focused tests and module builds:

1. Confirm the existing account and owner mapping through the normal application
   path without printing credentials.
2. Confirm `CTRADER_SAFETY_EXECUTION_ENABLED=false`.
3. Invoke the authenticated V2 synchronization endpoint once.
4. Record only route, status, duration, safe account identifier, persisted
   capability set, completeness, freshness and bounded reasons.
5. Verify balance and any authoritative empty current-state facts are visible
   without presenting equity, margin or deals as available.
6. Confirm risk remains not ready for the FTMO equity-dependent policy.
7. Confirm no provider mutation or order request occurred.

Classify the result as `SYNC_PARTIAL_SUCCESS`, `SYNC_COMPLETE` or
`SYNC_FAILED_BOUNDED`.

## 7. Validation Commands

Run focused checks first, then the affected module suites:

```text
git diff --check
mvn -Dtest=BrokerSynchronizationServiceImplTest,AccountSynchronizationV2ControllerTest test
mvn test                         # trading-core
mvn test                         # broker-service, when the neutral contract changes
```

Run the persistence integration test and production module build when the
environment permits. Report timeouts separately from test failures.

## 8. Documentation Reconciliation

Update only Story-local artifacts during this Story:

- `implementation-report.md` with changed files, tests, runtime result, safety
  status and remaining blockers;
- `e2e-validation-matrix.md` with the controlled runtime evidence.

No ADR change is expected. If implementation reveals that the approved ADRs do
not define the required persistence or partial-state semantics, stop before
expanding scope and report the contradiction.

## 9. Expected Files

Likely implementation surface, subject to verification during implementation:

- `trading-core/src/main/java/.../broker/service/BrokerSynchronizationService.java`;
- `trading-core/src/main/java/.../broker/service/BrokerSynchronizationServiceImpl.java`;
- `trading-core/src/main/java/.../broker/dto/` for the synchronization result;
- `trading-core/src/main/java/.../controller/AccountSynchronizationV2Controller.java`;
- `trading-core/src/main/java/.../model/Account.java`;
- `trading-core/src/main/resources/db/migration/common/V52__*.sql`;
- Trading Core synchronization, controller and persistence tests;
- Story-local implementation and E2E reports.

No frontend change is currently expected because the current web client uses
the legacy V1 synchronization endpoint. Re-evaluate this only if repository
evidence shows the V2 response is consumed by a user-facing client.

## Approval Boundary

Implementation must not begin until this Implementation Plan is explicitly
approved by the human engineer.
