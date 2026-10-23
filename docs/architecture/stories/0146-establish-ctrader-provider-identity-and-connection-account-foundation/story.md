# Story 0146 - Establish cTrader Provider Identity and Connection Account Foundation

## Metadata

**ID:** `0146`
**Title:** Establish cTrader Provider Identity and Connection Account Foundation
**Status:** Proposed - awaiting repository analysis approval
**Related Stories:** `0145`
**Related ADRs:** `ADR-030`, `ADR-041`, `ADR-043`, `ADR-071`, `ADR-072`

---

## Context

ADR-071 selects FTMO 2-Step as the first Challenge/business target and cTrader
Open API as the future execution-platform boundary. Story 0145 established the
provider-neutral account-routing contract and explicit `Account.brokerAccountId`
resolution, but intentionally did not add a second production provider identity.

The current repository still contains only `KRAKEN` in the Broker Service and
Trading Core provider identifiers. The provider registry and capability boundary
are structurally provider-neutral, but a cTrader authentication or read-only
adapter Story cannot safely begin while the physical provider identity and
connection/account foundation remain Kraken-only.

## Problem

Trading OS cannot currently represent cTrader as an execution provider while
preserving the separation between:

```text
ChallengeInstance -> Account -> BrokerAccount -> BrokerConnection -> external account
```

Adding cTrader authentication, account discovery, synchronization or execution
before this foundation would mix provider registration with adapter behavior and
would make the exact external account binding ambiguous.

## Goal

Establish the smallest provider-neutral production foundation required to represent
and route a broker account for a future cTrader connection, without placing a
concrete provider identity in Trading Core and without implementing cTrader
transport or provider capabilities.

## Scope

- Add the approved cTrader provider identity only to Broker Service provider
  registration and resolution.
- Replace the Trading Core concrete provider selection dependency with the opaque
  broker-owned provider-registration reference defined by ADR-072.
- Preserve the distinction between FTMO Challenge/business identity and cTrader
  Broker/execution identity.
- Preserve the explicit `Account.brokerAccountId` relation as the financial-to-
  execution routing authority.
- Ensure a cTrader `BrokerAccount` remains owner-scoped and has one explicit
  `BrokerConnection` routing identity.
- Preserve local `BrokerAccount.id`, connection `brokerAccountId`, and provider
  external account identity as distinct values.
- Define deterministic behavior for a recognized cTrader provider that has no
  registered capability implementation yet.
- Extend registry, ownership, routing, persistence, and validation tests with
  synthetic identifiers only; no real credentials or provider calls.
- Update canonical Story/implementation documentation with the resulting
  foundation and validation evidence.

## Out Of Scope

- cTrader SDK or Open API client dependencies.
- OAuth, authorization URLs, callback handling, access tokens, refresh tokens,
  revocation, or account discovery against cTrader.
- cTrader account synchronization, RiskSnapshot production, events, reconnect,
  restart recovery, or provider polling.
- cTrader order placement, cancellation, modification, protection, close,
  execution, or reconciliation.
- FTMO Challenge definition, phase progression, loss rules, minimum trading days,
  fees, swaps, or headroom formulas.
- FTMO classes, FTMO business rules, or FTMO logic inside Broker Service.
- Market Data provider integration, instrument catalogue, mapping, or sizing.
- Changes to Risk formulas, human authorization, idempotency, or unknown-outcome
  semantics.
- Heuristic repair of ambiguous legacy rows. A deterministic migration of the
  existing unambiguous Kraken provider rows to the approved opaque registration
  reference is in scope.
- S1, AI, scanners, frontend workflows, or operational dry-run execution.

## Architecture Constraints

- FTMO remains a Challenge/business provider; cTrader remains a Broker/execution
  provider.
- Trading Core remains provider-neutral and must not contain cTrader DTOs, SDK
  types, OAuth details, provider payloads, or provider error mappings.
- Broker Service owns provider identity resolution, credentials, authentication,
  provider capabilities, transport, mapping, and provider errors.
- `BrokerAccount.id` is the execution-routing identity.
- `Account.accountId` is the financial identity.
- `Account.brokerAccountId` is the only canonical relation for this routing path;
  `(owner, provider)` lookup must not become an identity fallback.
- Provider registration must remain capability-oriented. A recognized provider
  without an adapter must fail closed as unavailable, not be represented by a fake
  capability or a Kraken fallback.
- No cTrader implementation is authorized by this Story.

## Domain Invariants

- Broker Service has exactly one immutable provider identity per connection.
- Trading Core has no concrete provider identity authority; it owns only the local
  BrokerAccount routing identity and opaque provider-selection reference.
- A `BrokerConnection` routes exactly one local `BrokerAccount` and has matching
  owner and provider metadata.
- A local `BrokerAccount` identifier is never replaced by an external cTrader
  account identifier.
- External account identity is optional until authenticated account binding is
  implemented, but when present it is not interchangeable with the local UUID.
- A cTrader account cannot be treated as connected, synchronized, or executable
  merely because the provider enum is recognized.
- An unsupported or capability-less provider cannot produce account facts or an
  execution result.
- Existing Kraken creation, connection, routing, and execution behavior remains
  unchanged.

## Security Constraints

- Preserve authenticated owner checks in Trading Core and Broker Service.
- Never accept `BrokerAccount.id` as authorization without owner validation.
- Do not create, inspect, persist, return, or log real cTrader credentials.
- Do not add secrets, tokens, authorization codes, or provider payloads to
  Trading Core models, APIs, logs, or Story fixtures.
- Service-to-service authentication remains mandatory for internal calls.
- No new endpoint may expose an unowned connection or external account identity.

## Failure Semantics

- Unknown provider identity: reject deterministically as unsupported.
- Recognized cTrader identity without a registered capability: reject deterministically
  as unavailable; never fall back to Kraken.
- Missing `Account.brokerAccountId`: fail closed before facts, Risk, authorization,
  or execution.
- Missing or cross-owner `BrokerAccount`/`BrokerConnection`: reject deterministically.
- Provider mismatch between `Account`, `BrokerAccount`, and `BrokerConnection`:
  reject deterministically.
- Local UUID/external account mismatch: reject deterministically; never infer or
  overwrite either identity.
- Connection status not ready: no provider fact or execution capability may be
  invoked.
- No authoritative provider facts: no valid Risk context and no new execution.
- This Story must not change `timeout != rejection`, UNKNOWN outcome, or no-blind-
  retry semantics.

## Acceptance Criteria

- [ ] Given a supported provider identity, when a `BrokerAccount` is created, then
      the provider is persisted as an immutable execution-provider identity.
- [ ] Given a Broker Service cTrader provider registration, when a connection
      command uses its opaque provider-registration reference, then Trading Core
      does not contain or require a concrete cTrader provider enum or name.
- [ ] Given a cTrader `BrokerAccount` without a provider adapter, when provider
      resolution is requested, then the result is a deterministic unavailable
      failure and no Kraken capability is invoked.
- [ ] Given a cTrader `BrokerAccount` and `BrokerConnection`, when ownership and
      provider metadata match, then the connection routes only to that local
      `BrokerAccount` identity.
- [ ] Given mismatched owner, provider, local UUID, or external identity metadata,
      when routing or validation is requested, then the operation fails closed
      before provider capabilities are invoked.
- [ ] Given an `Account` with no explicit `brokerAccountId`, when the account is
      routed, then no `(owner, provider)` fallback is attempted.
- [ ] Given a recognized cTrader provider with no active authenticated connection,
      when account facts or execution are requested, then no facts or order result
      are produced.
- [ ] Existing Kraken provider registration, capability resolution, ownership,
      synchronization, execution, and unknown-outcome tests remain green.
- [ ] Tests prove that FTMO does not appear in Broker Service provider identity,
      capability, connection, or transport code and that Trading Core does not
      acquire concrete provider identity authority.
- [ ] Tests prove that no cTrader SDK, OAuth, external API call, or production
      provider adapter is introduced by this Story; the only migration is the
      deterministic conversion of existing unambiguous Kraken provider rows to
      the approved opaque registration reference.
- [ ] `git diff --check` passes and the implementation report records all focused
      validation commands and residual limitations.

## Dependencies

- ADR-071 must be accepted and available as canonical repository context before
  implementation planning is approved.
- Story 0145 must remain merged and its v2 account-routing contract must remain
  compatible.
- Existing provider registry, `BrokerAccount`, `BrokerConnection`, service-auth,
  and ownership contracts are required.

## Tests And Evidence

- Broker Service provider registry tests for `KRAKEN`, `CTRADER`, duplicate IDs,
  unsupported IDs, and capability-less provider failure.
- Trading Core provider and `BrokerAccount` persistence/domain tests.
- Cross-owner and provider-mismatch routing tests.
- Account-to-BrokerAccount identity and missing-mapping tests.
- BrokerConnection local/external identity consistency tests.
- Regression tests proving no cTrader credential or payload crosses the Broker
  Service boundary.
- Focused Broker Service and Trading Core test suites.
- `git diff --check`.
- No live broker call, credential mutation, migration, or runtime execution.

## Definition Of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved.
- [ ] cTrader provider identity is represented consistently without an adapter.
- [ ] BrokerAccount/BrokerConnection routing and ownership invariants are tested.
- [ ] Capability-less cTrader resolution fails closed without Kraken fallback.
- [ ] Account identity remains distinct from external provider identity.
- [ ] Human authorization, Risk, idempotency, and UNKNOWN semantics are unchanged.
- [ ] No cTrader SDK, OAuth, provider adapter, synchronization, execution, or
      FTMO business behavior is implemented.
- [ ] ADR-072 is accepted and its opaque provider-selection boundary is preserved.
- [ ] Documentation reconciliation completed.
- [ ] Focused validation and regression suites pass.
- [ ] Code Review and Engineering Report completed.

## Next Workflow Gate

Human approval of the Repository Analysis is required before producing the
Implementation Plan. This artifact does not authorize implementation.
