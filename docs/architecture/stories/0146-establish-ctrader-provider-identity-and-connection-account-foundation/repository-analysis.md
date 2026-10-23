# Story 0146 - Repository Analysis

## Status

`Approved`

This artifact records the repository analysis for Story 0146 after selecting the
opaque provider-identity option. ADR-072 and this migration revision were
human-approved. The artifact does not authorize implementation by itself.

## Repository Baseline

| Item | Current value |
|---|---|
| Branch | `main` |
| HEAD | `26f8832306c41938897c1fdd19a1c028e1d33882` |
| `origin/main` | `26f8832306c41938897c1fdd19a1c028e1d33882` |
| Latest merged implementation | Story 0145, PR #163 |
| Working tree | pre-existing untracked ADR/reports plus the new Story 0146 artifacts |
| Production code changes in this stage | none |
| DevLog | unavailable from the prior applicability check due `429 insufficient_quota` |

ADR-071 has been explicitly validated by the human for this Story. The current
working tree still contains it as an untracked file and it is not yet present in
`HEAD`; this remains a repository-delivery concern, not permission to bypass the
ADR boundary. Story 0146 therefore depends on ADR-071 being available as canonical
repository context before implementation planning is approved.

## Authoritative Context

- ADR-071 separates FTMO Challenge/business responsibility from cTrader
  Broker/execution responsibility.
- ADR-071 assigns provider authentication, transport, mapping, errors, connection
  lifecycle and capabilities to Broker Service.
- ADR-071 keeps Challenge state, deterministic Risk, execution authorization,
  execution state and reconciliation decisions in Trading Core.
- ADR-043 requires separate financial `Account` and execution `BrokerAccount`
  identities with an explicit relation.
- Story 0145 established the v2 broker snapshot contract, explicit
  `Account.brokerAccountId` routing and fail-closed mapping checks, but deliberately
  did not add a second production provider.
- ADR-072 is required to reconcile the existing Trading Core provider enum with
  the provider-neutral constraints in ADR-030, ADR-041 and ADR-071.
- ADR-072 is currently a draft and is the required architecture artifact for the
  selected option.

## Current Implementation

### Broker Service

- `BrokerProviderId` contains only `KRAKEN`.
- `BrokerProviderRegistry` composes provider implementations by provider ID,
  rejects duplicate IDs and rejects unsupported IDs.
- `BrokerProviderResolver` resolves a provider from an owner-independent
  `BrokerConnection`, then requires connected status and an active credential.
- `BrokerConnection` persists local `brokerAccountId`, owner, provider,
  credential reference, technical status and optional external account ID.
- Ownership-scoped repository methods already exist for connection retrieval and
  revocation.
- The provider capability boundary is generic (`AuthenticationCapability`,
  `AccountCapability`, `PositionCapability`, `OrderCapability`,
  `RiskSnapshotCapability`, execution and reconciliation capabilities).
- Production composition contains only `KrakenBrokerProvider`.
- Credential validation is selected through a provider-keyed validator map and only
  Kraken has a concrete validator.
- Required permission policy and secret metadata also use the provider enum, so
  adding a provider identity must not accidentally imply authentication support.

### Trading Core

- `BrokerProvider` contains only `KRAKEN`, but its concrete provider identity in
  Trading Core conflicts with the provider-neutral architecture and must not be
  extended with `CTRADER`.
- `BrokerAccount` persists immutable provider, owner, local ID, execution mode,
  connection status, credential reference and optional external account ID.
- `Account` has a unique nullable `brokerAccountId` scalar relation.
- `BrokerAccountRepository` has owner-scoped lookup by local ID.
- `BrokerAccountService` creates the financial `Account` with the local
  `brokerAccountId` relation.
- Story 0145 v2 synchronization resolves the financial Account from that explicit
  relation and rejects owner, provider, local identity and external identity
  mismatches.
- `BrokerConnectionCommandClient` and internal connection update contracts already
  carry provider-neutral provider/status/credential-reference data.

### Challenge and Risk

- `ChallengeInstance` references one `Account` and one immutable definition/version.
- `ChallengeDefinition` has one profit target and one immutable Risk policy
  reference; no phase sequence exists.
- `ChallengeProgress` exposes daily and total headroom fields, but the current
  projection returns them as unavailable rather than duplicating Risk formulas.
- Risk has a provider-neutral immutable account snapshot boundary containing balance,
  equity, margin, positions, closed trades, ledger entries, observation version and
  completeness metadata.
- These generic Risk boundaries can consume future cTrader facts, but no cTrader
  fact source exists.

## Identity and Routing Findings

The canonical path is now structurally available:

```text
ChallengeInstance
    -> Account.accountId
        -> Account.brokerAccountId
            -> BrokerAccount.id
                -> BrokerConnection.brokerAccountId
                    -> externalAccountId
```

The Story must preserve these rules:

- local `BrokerAccount.id` is the routing identity;
- external cTrader account ID is a provider identity, not a local substitute;
- `Account.brokerAccountId` is the canonical relation;
- `(owner, provider)` lookup is never an identity fallback;
- connection owner and provider must match the local BrokerAccount;
- a provider enum does not imply an active capability implementation.

## Proposed Change Boundary

The implementation plan may safely consider:

1. adding `CTRADER` only to Broker Service provider registration;
2. introducing the opaque provider-registration reference defined by ADR-072 at
   the Trading Core connection-command boundary;
3. verifying that Broker Service provider persistence and API serialization remain
   compatible;
4. extending provider/ownership/routing validation for a recognized but
    capability-less cTrader identity;
5. preserving explicit local/external account identity semantics;
6. adding deterministic tests for unsupported capability resolution and no-Kraken-
    fallback behavior;
7. adding provider-neutral documentation and regression evidence;
8. migrating existing unambiguous Kraken provider values to the stable opaque
   registration reference, failing closed on unexpected values.

The plan must not add a cTrader adapter merely to make the new provider reference
resolvable.
The correct result for a recognized provider with no registered capability is a
deterministic unavailable failure.

## Persistence and Migration Assessment

The inspected provider fields use string enum persistence. Existing migration files
do not establish a known provider-specific check constraint requiring a migration
for an additional enum value. This must be confirmed during planning and validation
against the exact schema before implementation.

The human owner has selected the deterministic migration of existing unambiguous
Kraken provider values to the stable opaque registration reference. No heuristic
repair or other historical-row rewrite is authorized. Any unexpected or
ambiguous value must fail the migration and return an architecture decision
requirement.

## Security and Failure Boundaries

- Trading Core remains credential-blind.
- Broker Service remains the only credential and provider authentication boundary.
- Owner checks remain mandatory in both services.
- A recognized but unauthenticated or capability-less cTrader account cannot
  produce account facts, Risk context or execution results.
- Unsupported, mismatched, missing or cross-owner identities fail closed.
- No real credentials, tokens, provider calls or external account discovery are
  permitted.
- Human authorization, unknown outcome and no-blind-retry semantics remain outside
  the change boundary and must be regression-tested.

## Affected Areas

Expected implementation areas, subject to plan verification:

- `broker-service/.../connection/domain/BrokerProviderId.java`
- `broker-service/.../broker/application/registry/BrokerProviderRegistry.java`
- `broker-service/.../broker/application/service/BrokerProviderResolver.java`
- `broker-service/.../connection/application/BrokerCredentialConnectionService.java`
- provider-neutral provider/credential policy tests
- `trading-core/.../brokeraccount/domain/BrokerProvider.java`
- `trading-core/.../brokeraccount/application/BrokerAccountService.java`
- Trading Core BrokerAccount/routing regression tests
- Story 0146 implementation and review artifacts

No cTrader infrastructure package, SDK dependency, OAuth controller, adapter,
Market Data module, Risk formula, Challenge rule or execution module should be
affected by this Story.

## Risks

- Adding `CTRADER` to an enum without an explicit capability-less failure path could
  make connection creation appear supported while authentication is absent.
- Existing provider-keyed policies may throw generic enum errors if the new identity
  is not handled deliberately.
- Frontend/API provider serialization may expose a selectable provider before the
  backend can connect it; this must remain an explicit unavailable state, not a
  false connected state.
- A hidden database check constraint could require a migration; no migration should
  be invented before schema verification.
- Updating only one service's provider identity would create cross-service contract
  drift.

## Validation Expectations

The Implementation Plan should require, at minimum:

- Broker Service registry tests for Kraken, cTrader identity, duplicate IDs and
  unavailable capability resolution;
- Broker Service credential/permission tests proving no cTrader validator is
  accidentally selected;
- connection owner/provider/local-external identity tests;
- Trading Core BrokerAccount/provider persistence and API contract tests;
- explicit Account-to-BrokerAccount routing and missing-mapping regressions;
- existing Kraken provider, synchronization, ownership and execution suites;
- provider-boundary search proving no cTrader SDK or FTMO business code crosses
  into Trading Core;
- schema/migration verification;
- `git diff --check`.

No live integration or production execution validation belongs in this Story.

## Approval Gate

Human approval is required for this Repository Analysis.

Approval will allow the workflow to produce the Implementation Plan. It does not
authorize implementation, cTrader authentication, read-only synchronization,
execution, migration creation, or FTMO Challenge rule work.
