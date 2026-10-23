# Story 0145 - Implementation Plan

## Status

`Completed`

This plan was based on the approved Repository Analysis and the approved removal
of the synthetic second-provider acceptance criterion. It does not authorize
implementation until explicitly approved. This revision incorporates the
decision to use a versioned broker account snapshot contract rather than
changing the existing `v1` contract in place.

## Outcome

Generalize the existing provider-neutral registration and account-routing paths
without adding a second production provider identity, cTrader code, FTMO code, or
new provider capabilities.

## Planned Changes

### 1. Broker Service Registry Tests

Review and strengthen the existing `BrokerProviderRegistryTest` coverage for:

- registered provider resolution;
- duplicate provider identity rejection;
- unsupported provider rejection;
- immutable supported-provider exposure.

Use the existing `BrokerProviderId.KRAKEN` contract only. Do not add a test,
paper, cTrader, or placeholder enum value.

Expected files:

- `broker-service/src/test/java/com/hope/trading/broker_service/broker/BrokerProviderRegistryTest.java`
- related registry/resolver files only if a concrete defect is found.

### 2. Trading Core Account Routing

Audit affected account-routing call sites that use provider-based lookup. Replace
ambiguous identity resolution only where the canonical `Account.brokerAccountId`
relation is available and the caller already has the BrokerAccount routing ID.

Prioritize:

- `AccountRepository` lookup methods;
- broker-account provisioning and retrieval paths;
- LIVE synchronization or risk-routing paths identified by tests;
- settlement/execution paths only where the current lookup violates ADR-043 and
  the change remains within the Story boundary.

Do not perform a broad mechanical replacement of every `(owner, provider)` query.
Each changed call site must be checked for its actual identity semantics.

### 3. Versioned Broker Account Snapshot Contract

Introduce a broker-neutral `v2` account snapshot contract for account
synchronization:

- keep the existing `v1` endpoint and payload backward-compatible during the
  transition;
- expose the local `BrokerAccount` UUID as `brokerAccountId` in `v2`;
- expose the provider's external account identifier separately as
  `externalAccountId`;
- require the `v2` request to identify the local BrokerAccount explicitly;
- remove the synthetic `kraken-default` value from the new contract;
- do not use provider-specific payloads, SDK types, or provider identifiers in
  Trading Core;
- deprecate and remove the legacy `v1` synchronization path only in a later
  approved Story.

The versioned contract must be adopted on both sides of the service boundary.
The exact endpoint path and transport DTO names remain implementation details,
provided that the public versioning and identity semantics above are preserved.

Expected files, subject to the audit:

- Broker Service account snapshot DTO, controller, service, and focused tests;
- Trading Core account snapshot DTO, Feign client, synchronization service, and
  focused tests.

This change follows the public-contract versioning rule in ADR-030 and does not
require a new ADR.

### 4. Fail-Closed Mapping Validation

Add or reuse application-level validation for:

- missing Account-to-BrokerAccount mapping;
- cross-owner relation;
- provider metadata disagreement where provider correctness is required;
- routing requests that cannot resolve a unique BrokerAccount.
- legacy snapshots that do not provide an authoritative local BrokerAccount
  identity when the `v2` path is required.

Missing or inconsistent mappings must fail before Risk authorization or broker
execution. No missing mapping may become a zero/empty/default account.

### 5. Regression Coverage

Preserve and extend focused tests for:

- Kraken account creation and provider propagation;
- distinct Account and BrokerAccount IDs;
- owner-scoped BrokerAccount access;
- explicit `brokerAccountId` relation resolution;
- ambiguous or missing relation failure;
- `v2` snapshot mapping of local and external account identifiers;
- preservation of the existing `v1` contract during transition;
- existing Broker Service registry behavior.

No live broker calls, credentials, migrations, or runtime mutations are allowed.

## Files To Inspect Before Editing

- `broker-service/src/main/java/com/hope/trading/broker_service/broker/application/registry/BrokerProviderRegistry.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/broker/application/service/BrokerProviderResolver.java`
- Broker Service account snapshot controller, DTO, and service contract
- `broker-service/src/main/java/com/hope/trading/broker_service/connection/domain/BrokerConnection.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/connection/application/BrokerConnectionRepository.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/model/Account.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/repository/AccountRepository.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/application/BrokerAccountService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/application/BrokerAccountRepository.java`
- Trading Core account snapshot client, DTO, and synchronization service
- affected callers found by the account-routing audit

## Constraints

- Preserve ADR-002, ADR-030, ADR-041, ADR-043, and ADR-071.
- Trading Core must not reference cTrader, FTMO, provider DTOs, SDK classes, or
  provider authentication details.
- Do not add `PAPER` as a broker provider.
- Do not add a synthetic provider enum value.
- Do not change the existing `v1` broker account snapshot contract in place.
- Do not expose the provider external account identifier as the canonical local
  `brokerAccountId`.
- Do not change Risk rule semantics, execution authorization, idempotency, or
  unknown-outcome behavior.
- Do not modify historical ADRs or reports.
- Do not create migrations unless the routing implementation proves a schema
  change is strictly required; if so, stop and report `ARCHITECTURE_DECISION_REQUIRED`.

## Validation Commands

From `broker-service`:

```text
mvn test -Dtest=BrokerProviderRegistryTest,BrokerOwnershipTest,BrokerMonitoringTest
```

From `trading-core`:

```text
mvn test -Dtest=BrokerAccountServiceTest,BrokerProviderRegressionTest,BrokerAccountTest
```

Additional focused synchronization and contract tests must be included when
the `v2` account snapshot surfaces are changed.

Repository-wide formatting check:

```text
git diff --check
```

If routing changes affect additional focused tests, include those tests and report
why. Do not run unrelated full builds solely for ceremony.

## Documentation Reconciliation

Expected outcome: update only the canonical API or service documentation if the
repository contains documentation for the account snapshot endpoint. No new ADR
is required because the identity semantics are defined by ADR-043, provider
boundaries by ADR-071, and public-contract versioning by ADR-030. The
Implementation Report must record this conclusion and mention the Story/ADR
evidence.

## Deliverables

- focused production changes, if required by the audit;
- focused tests for changed behavior;
- Story implementation report with validation and documentation outcome;
- no commit or push.
