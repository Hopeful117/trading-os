# Story 0145 - Repository Analysis

## Status

`WAITING_FOR_ANALYSIS_APPROVAL`

This artifact is the repository analysis for Story 0145. It does not authorize
implementation.

## Objective

Generalize the physical provider registration and account-routing assumptions so
the existing provider-neutral architecture can support a future second execution
provider without introducing FTMO or cTrader business code into Trading Core.

## Repository Baseline

- Branch: `main`
- HEAD: `a9f16ecb63c888c5f3a214e2fb4dd0035ab93ee7`
- `origin/main`: same commit
- Working tree: pre-existing untracked reports plus the target-lock artifacts;
  no tracked implementation changes were made during this analysis.
- DevLog: unavailable because the configured API account has exhausted credits;
  repository evidence is authoritative for current state.

## Authoritative Context

- `ADR-071` selects FTMO Challenge 2-Step + cTrader as the first prop target but
  explicitly forbids cTrader/FTMO implementation in this scope.
- `ADR-030` requires Broker Service provider isolation, capability composition,
  provider-neutral contracts, and provider-specific mapping inside adapters.
- `ADR-041` requires human authorization, idempotency, unknown-outcome safety,
  and provider-neutral Trading Core execution.
- `ADR-043` requires distinct financial `Account` and execution/routing
  `BrokerAccount` identities with an explicit durable relation and fail-closed
  mapping behavior.
- Story 0145 is proposed and now has explicit user authorization to proceed
  through the repository-analysis workflow, but its current analysis still needs
  human approval before planning.

## Current Implementation

### Broker Service

- `connection.domain.BrokerProviderId` contains only `KRAKEN`.
- `broker.domain.provider.BrokerProvider` already exposes a provider-neutral
  `id()` and capability lookup.
- `BrokerProviderRegistry` resolves providers from injected implementations,
  rejects duplicate IDs, and rejects unsupported IDs.
- `BrokerProviderResolver` resolves the provider from a persisted
  `BrokerConnection`, verifies `CONNECTED` status and an active credential, then
  delegates to the registry.
- `BrokerConnection` stores `brokerAccountId`, `ownerId`, provider, status,
  credential reference, external account ID, and validation metadata.
- `BrokerConnectionRepository` already has owner-scoped lookup methods.
- The concrete provider and capability implementations are isolated below
  `broker.infrastructure.provider.kraken`.

### Trading Core

- `brokeraccount.domain.BrokerProvider` contains only `KRAKEN`.
- `BrokerAccount` stores provider, owner, execution mode, connection status,
  external account ID, and credential reference.
- `BrokerAccountService` accepts the provider from the request and creates the
  PAPER financial `Account` with `brokerAccountId` set to the created
  `BrokerAccount` ID.
- `Account` already has a unique nullable `broker_account_id` column, but this is
  a scalar relation rather than a JPA association.
- `AccountRepository` exposes `findByBrokerAccountId`, but also retains legacy
  `(user, broker)` lookup methods and `existsByUser_UserIdAndBroker`.
- `BrokerAccountRepository` supports owner-scoped lookup but has no reverse
  Account-to-BrokerAccount invariant enforcement.

### Existing Tests

- Broker Service tests cover registry resolution, duplicate providers, unsupported
  providers, ownership, and Kraken capability behavior.
- Trading Core `BrokerProviderRegressionTest` explicitly asserts that the enum
  contains exactly one value, `KRAKEN`.
- Trading Core account-service tests cover provider propagation for Kraken and
  ownership failures.

## Scope Mapping

### Safe within Story 0145

- Replace direct provider assumptions in provider registration/resolution with
  existing neutral interfaces where the current code actually has a generic
  boundary.
- Make account-routing lookups use the explicit `Account.brokerAccountId` mapping
  where the affected workflow currently uses an ambiguous provider lookup.
- Add deterministic missing, ownership, and provider-consistency guards at the
  application boundary without changing Risk rule semantics.
- Preserve Kraken behavior and existing lifecycle/idempotency/fail-closed rules.
- Add tests around the existing neutral contracts.

### Explicitly out of scope

- cTrader, FTMO, OAuth, provider SDKs, credentials, or external endpoints.
- FTMO Challenge rules or multi-phase progression.
- Market Data changes.
- New broker capabilities or execution operations.
- Automatic ambiguous legacy-data backfill.

## Blocking Contradiction

The Story currently requires:

```text
synthetic second-provider registration/routing tests
without introducing a real second-provider adapter
```

The current production contract has only one provider enum value:

```text
BrokerProviderId.KRAKEN
TradingCore BrokerProvider.KRAKEN
```

Adding a synthetic enum value such as `TEST`, `PAPER`, or `CTRADER` would be a
production contract change. It would conflict with the existing regression test,
could require persistence/migration compatibility decisions, and would either
invent a provider identity or leak a future provider into this prerequisite Story.

The current registry cannot test two distinct provider IDs using only
`BrokerProviderId.KRAKEN`; two fake implementations with the same ID correctly
exercise duplicate rejection, not multi-provider resolution.

### Recommended resolution

Split the acceptance criterion before planning:

1. Story 0145 proves provider-neutral registry composition with the existing
   provider ID, duplicate detection, unsupported-provider handling, and a fake
   provider implementation that does not add a production enum value.
2. A later approved Story, after a concrete second-provider identity is accepted,
   adds that provider registration and tests the two-provider routing path.

Alternative: explicitly authorize a neutral test-only provider identity and define
how it is excluded from production persistence/API contracts. That is a new
architectural decision and is not safe to infer.

## Proposed Implementation Plan Shape

No implementation plan is authorized yet. If the acceptance contradiction is
resolved as recommended, the plan should remain limited to:

1. Add or adjust provider-neutral registry tests without adding a production
   provider enum value.
2. Identify each current Trading Core lookup that uses `(owner, provider)` for
   account identity and replace it only where the canonical `brokerAccountId`
   relation is available and authoritative.
3. Add fail-closed tests for missing relation, cross-owner relation, and provider
   mismatch.
4. Preserve all Kraken behavior and run focused Broker Service and Trading Core
   tests.
5. Reconcile documentation only if the implemented behavior changes a canonical
   contract or operational workflow.

The exact files and tests depend on the resolution of the contradiction above.

## Risks

- Treating the scalar `Account.brokerAccountId` field as fully canonical without
  validating schema/backfill state could make existing legacy rows fail closed;
  that is safer than ambiguous routing but may require a separate migration Story.
- Broadly replacing all `(owner, provider)` lookups could change historical PAPER
  behavior outside Story scope.
- Adding a second enum value now would couple the implementation to an unapproved
  provider identity.

## Approval Gate

Human approval is required for this repository analysis, and clarification is
required for the contradictory second-provider test criterion before an
implementation plan can be produced.

Recommended response:

```text
Approve Repository Analysis and resolve Story 0145 by removing the
two-provider acceptance criterion from this Story; defer concrete second-provider
registration to a later Story.
```
