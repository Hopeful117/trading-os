# Story 0146 - Implementation Plan

## Status

`IMPLEMENTATION_IN_PROGRESS`

This plan is based on the approved Repository Analysis, accepted ADR-072, the
human-approved Story scope and the selected option to keep provider identity
opaque in Trading Core. The plan has been materially revised to include the
explicitly selected deterministic migration of existing Kraken rows. This
revision has been human-approved and implementation may proceed within the
documented scope.

## Outcome

Introduce cTrader only inside Broker Service provider registration and establish
an opaque provider-selection reference at the Trading Core connection boundary.
Trading Core continues to route by local `BrokerAccount.id` and does not acquire a
concrete provider enum or provider-name authority.

The result must be representable, owner-scoped and identity-safe, but not
authenticated, synchronized or executable.

## Implementation Steps

### 1. Broker-owned provider identity and opaque selection

Update provider selection without extending concrete provider identity into
Trading Core:

- add `CTRADER` to Broker Service `BrokerProviderId`;
- introduce the opaque broker-owned provider-registration reference defined by
  ADR-072 for connection commands;
- remove, retire or isolate Trading Core's concrete `BrokerProvider` dependency,
  subject to exact call-site audit;
- keep Trading Core APIs and domain models free of `KRAKEN` and `CTRADER` names;
- preserve existing Kraken rows and behavior;
- do not add FTMO to any technical provider registry.
- migrate existing unambiguous Kraken registration values to the stable opaque
  Kraken registration reference in the same deployment boundary;
- fail the migration rather than infer or rewrite ambiguous provider values.

Expected files:

- `broker-service/src/main/java/.../connection/domain/BrokerProviderId.java`;
- the Broker Service provider-registration/connection command contract;
- Trading Core broker-account creation and connection command boundary;
- affected provider request/response and persistence tests only if required.

The exact opaque reference type must be selected during implementation from the
existing contract conventions. It must not be a provider enum, provider name or
provider DTO in Trading Core.

### 2. Capability-less provider behavior

Make the existing provider resolution and credential command paths explicitly
fail closed for a recognized provider without an implementation:

- registry resolution must continue to distinguish a Broker Service provider
  identity from a registered capability implementation;
- no synthetic `BrokerProvider` implementation may be created for cTrader;
- no cTrader request may fall back to `KrakenBrokerProvider`;
- credential validation for a cTrader registration must produce a deterministic unavailable
  result rather than a null map failure or generic accidental error;
- account facts and execution must remain unavailable for cTrader.

Expected files, subject to exact inspection:

- `broker-service/src/main/java/.../broker/application/registry/BrokerProviderRegistry.java`;
- `broker-service/src/main/java/.../broker/application/service/BrokerProviderResolver.java`;
- `broker-service/src/main/java/.../connection/application/BrokerCredentialConnectionService.java`;
- `broker-service/src/main/java/.../credential/application/DefaultRequiredBrokerPermissionsPolicy.java`;
- relevant exception and response classes.

The implementation must not register a fake cTrader capability merely to satisfy
provider lookup.

### 3. BrokerAccount and BrokerConnection consistency

Verify and, only where necessary, enforce the existing identity invariants:

- provider identity is immutable within Broker Service connection state;
- Trading Core provider metadata is not authoritative and must not be extended
  with a concrete cTrader enum;
- local `BrokerAccount.id` and connection `brokerAccountId` remain distinct from
  `externalAccountId`;
- owner-scoped connection lookup is used for commands and validation;
- conflicting legacy provider metadata versus Broker Service authority is rejected;
- an external account ID cannot replace or overwrite the local routing UUID;
- a cTrader account can remain in a non-connected state until a later
  authentication implementation validates it.

Do not add OAuth fields, token metadata, account discovery, provider events or
connection health states in this Story.

### 4. Account routing preservation

Audit affected Trading Core call sites and preserve the Story 0145 contract:

- route through `Account.brokerAccountId` where the financial Account is known;
- retain owner-scoped `BrokerAccount` lookup;
- reject missing mapping and cross-owner mapping before broker calls;
- keep provider metadata validation as a consistency check, not as identity
  resolution;
- do not broaden the change to every legacy `(owner, provider)` query without
  verifying its identity semantics.

Expected areas:

- `trading-core/.../brokeraccount/application/BrokerAccountService.java`;
- `trading-core/.../brokeraccount/application/BrokerAccountRepository.java`;
- `trading-core/.../broker/service/BrokerSynchronizationServiceImpl.java`;
- affected ownership and routing tests.

### 5. Persistence, opaque-reference migration and schema verification

Verify the provider columns and enum persistence:

- confirm `EnumType.STRING` is used for affected provider fields;
- inspect current Flyway schema for provider check constraints;
- add the approved deterministic migration from existing unambiguous `KRAKEN`
  values to the stable opaque Kraken registration reference;
- verify the migration is idempotent and preserves ownership, local account IDs,
  external IDs and connection status;
- fail closed if unexpected or ambiguous provider values are encountered;
- never rewrite or infer legacy provider/account identity.

No migration beyond the deterministic conversion of existing unambiguous Kraken
values is authorized.

### 6. Boundary and regression tests

Add focused tests for:

- Broker Service `KRAKEN` and `CTRADER` provider identity serialization/persistence;
- opaque provider-reference serialization at the Trading Core connection boundary;
- duplicate registry identity rejection;
- unsupported provider rejection;
- recognized cTrader with no registered capability returning deterministic
  unavailable behavior;
- no Kraken fallback for cTrader requests;
- owner mismatch and provider mismatch;
- local BrokerAccount UUID versus external account identity mismatch;
- missing `Account.brokerAccountId` mapping;
- existing Kraken provider, synchronization, ownership and execution behavior;
- absence of FTMO/cTrader SDK or provider payload types in Trading Core.

Tests must use synthetic UUIDs and strings only. No live broker call or credential
material is permitted.

## Explicit Non-Changes

This implementation must not modify:

- cTrader authentication or OAuth;
- cTrader SDK dependencies;
- cTrader provider adapter or capability implementation;
- cTrader account synchronization or RiskSnapshot production;
- FTMO Challenge definitions, phase progression, minimum days, loss rules or
  headroom;
- Risk formulas or Risk authority;
- Market Data, instruments, sizing or execution contracts;
- execution authorization, idempotency, UNKNOWN semantics or reconciliation;
- S1, AI, frontend, migrations unless strictly proven necessary.

## Security and Failure Requirements

- Every connection command remains authenticated and owner-scoped.
- Trading Core remains credential-blind.
- Provider identity recognition must not imply authentication success.
- Capability resolution failures must be explicit and fail closed.
- No account facts means no valid Risk context and no new execution.
- Timeout remains distinct from rejection.
- Unknown execution outcomes remain non-retryable until reconciliation.
- No cTrader or FTMO payload may cross the Broker Service boundary.
- No concrete provider enum or provider name may become Trading Core authority.
- The deterministic Kraken opaque-reference migration is the only authorized
  schema/data migration.

## Validation Commands

From `broker-service`:

```bash
mvn test -Dtest=BrokerProviderRegistryTest,BrokerOwnershipTest,BrokerApiSecurityIntegrationTest,BrokerServiceImplTest
```

From `trading-core`:

```bash
mvn test -Dtest=BrokerProviderRegressionTest,BrokerAccountTest,BrokerAccountServiceTest,BrokerSynchronizationServiceImplTest
```

Additional provider/connection tests added by the implementation must be included.

Repository checks:

```bash
git diff --check
```

If provider persistence or migration behavior changes, run the affected Flyway and
repository integration tests and report them explicitly.

## Stop Conditions

Stop before or during implementation if:

- ADR-071 or ADR-072 is not available as canonical approved context;
- adding a provider reference requires a migration beyond the approved
  deterministic Kraken conversion or an unapproved historical backfill;
- the only way to make cTrader resolve is a fake capability or Kraken fallback;
- provider identity cannot remain consistent between Broker Service and Trading Core;
- credential handling would require cTrader secrets in Trading Core;
- account identity cannot be kept distinct between local and external IDs;
- the change requires cTrader OAuth, synchronization, execution, or FTMO rules;
- existing Kraken behavior or human-control invariants regress.

## Documentation Reconciliation

Update the Story implementation report with:

- the exact provider identity and unavailable-capability behavior;
- persistence/migration verification;
- tests executed and their results;
- explicit confirmation that no cTrader adapter, OAuth, SDK, FTMO rule or
  production execution was added.

ADR-072 is accepted and the deterministic Kraken migration has been selected by
the human owner. If the provider identity model, migration semantics or
connection lifecycle must change beyond ADR-071/ADR-072, stop and request
another ADR instead of expanding this Story.

## Deliverables

- focused provider identity and fail-closed foundation changes;
- focused Broker Service and Trading Core tests;
- updated Story implementation report;
- code review and engineering report artifacts;
- no cTrader adapter, OAuth, SDK, synchronization, execution or FTMO rule code.

## Next Workflow Gate

This materially revised Implementation Plan must pass its own human approval
gate again before implementation may begin. This plan does not itself authorize
production changes.
