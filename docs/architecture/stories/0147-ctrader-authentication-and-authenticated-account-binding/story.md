# Story 0147 - cTrader Authentication and Authenticated Account Binding

## Metadata

**ID:** `0147`
**Title:** cTrader Authentication and Authenticated Account Binding
**Status:** Implemented locally - real provider validation required
**Related Stories:** `0145`, `0146`
**Related ADRs:** `ADR-024`, `ADR-030`, `ADR-041`, `ADR-043`, `ADR-071`, `ADR-072`, `ADR-073`

---

## Context

Trading OS V1 is closed and the product is in `PRE_CHALLENGE_READINESS`. The
selected target is FTMO 2-Step as the business challenge and cTrader Open API as
the execution-platform boundary. Story 0145 generalized provider-neutral account
routing. Story 0146 added the cTrader provider registration and opaque provider
reference without implementing cTrader connectivity.

The current repository can represent a local `BrokerAccount`, a technical
`BrokerConnection`, an opaque credential reference and an optional external
account ID. It cannot yet represent the secure cTrader authorization lifecycle,
authorized-account discovery, explicit human account selection or deterministic
external-account binding.

## Goal

Establish the smallest secure Broker Service foundation that can authenticate a
user with cTrader Open API, discover the accounts authorized by that user,
explicitly select one account and bind its authoritative external identifier to
exactly one owner-scoped local `BrokerAccount`/`BrokerConnection`.

This Story establishes trust in identity before trust in account data. It stops
before account synchronization, Risk readiness and execution.

## Scope

- register the cTrader application identity and configured redirect boundary;
- implement the cTrader authorization initiation and callback correlation;
- exchange authorization codes for protected access/refresh token material;
- authenticate the cTrader application transport sufficiently to discover
  authorized accounts;
- expose owner-scoped, provider-scoped discovery results without credentials;
- require explicit human selection of one authorized cTrader account;
- bind the selected authoritative cTrader account identifier deterministically to
  the existing local `BrokerAccount`/`BrokerConnection`;
- preserve the distinction between provider recognition, authentication, binding,
  synchronization, Risk readiness and execution readiness;
- define expiry, revocation, disconnect, restart, replay and reauthorization
  semantics;
- keep cTrader-specific protocol types and credentials inside Broker Service;
- preserve Kraken behavior and prevent cTrader execution capabilities.

## Out Of Scope

- balance, equity, margin, positions, orders, deals, fees, swaps or ledger
  synchronization;
- RiskSnapshot production or Risk formula changes;
- cTrader market-data integration;
- cTrader order placement, modification, cancellation, close or reconciliation;
- FTMO Challenge rules, phases, targets, drawdown or minimum days;
- S1, AI or scanner work;
- automatic account selection or implicit rebinding;
- real credentials in tests, fixtures, Git or logs;
- a cTrader SDK unless separately justified and approved;
- live trading, challenge creation or production execution validation.

## Architecture Constraints

- FTMO remains a business/challenge provider; cTrader remains an execution
  provider.
- Trading Core remains cTrader-agnostic and credential-blind.
- Broker Service owns cTrader OAuth, token material, transport/session,
  provider errors, discovery and binding.
- `Account.accountId`, `BrokerAccount.id`, `BrokerConnection.id`, provider
  registration reference, cTrader external account ID and FTMO Challenge identity
  remain distinct.
- `Account.brokerAccountId` remains the canonical financial-to-execution relation.
- The existing Broker Service secret ports remain the only secret persistence
  boundary.
- cTrader authorization must request view-only `accounts` scope for this Story.
- No provider capability capable of execution may be registered or exposed.
- Every operation is authenticated, owner-scoped and provider-scoped.
- `AUTHENTICATED + BOUND` does not imply synchronized facts, Risk readiness or
  execution readiness.

## Acceptance Criteria

- [ ] cTrader authorization initiation is created only for an owner-owned local
      `BrokerAccount` using the cTrader provider registration reference.
- [ ] The authorization URL contains only the configured client identity,
      approved redirect URI and view-only `accounts` scope; no secret is exposed
      to the frontend or URL beyond the documented provider parameters.
- [ ] Callback correlation validates owner, local account, provider, expiry and
      one-time state consumption before exchanging the authorization code.
- [ ] Missing, invalid, expired, replayed, cross-owner and provider-mismatched
      callbacks fail closed without creating a binding.
- [ ] Authorization codes are never persisted, logged or returned after token
      exchange.
- [ ] Access and refresh tokens are stored only as encrypted Broker Service
      secret material behind an opaque credential reference.
- [ ] Trading Core receives no cTrader OAuth DTO, token, authorization code,
      client secret or provider transport payload.
- [ ] Authorized cTrader accounts are discovered using the provider's
      authoritative account identifier and returned only to the owning user.
- [ ] Zero accounts produces a deterministic no-eligible-account result.
- [ ] Multiple accounts remain pending explicit human selection; the first
      returned account is never selected implicitly.
- [ ] Selecting one authorized account validates owner, provider, authorization
      attempt and account eligibility before binding.
- [ ] The selected external cTrader account ID is stored separately from all
      local Trading OS identities and binds to exactly one owner-scoped local
      BrokerAccount/BrokerConnection.
- [ ] Rebinding the same external account is idempotent; changing the external
      account requires an explicit controlled transition and never occurs during
      silent reauthorization.
- [ ] Token expiry, refresh failure, revocation and transport disconnect preserve
      the durable external identity but fail closed for current usability and
      facts availability.
- [ ] Restart does not silently change ownership, provider identity or external
      account binding and does not enable execution from persisted history.
- [ ] cTrader execution, modification, cancellation, close and reconciliation
      remain unavailable and fail closed.
- [ ] Existing Kraken provider registration, connection, synchronization,
      capability and execution behavior remains green.
- [ ] No FTMO concept enters the cTrader adapter or Broker Service provider
      authentication implementation.
- [ ] No real provider credential or live cTrader call is used in deterministic
      tests; real-provider validation is explicitly reported as required.

## Required Focused Tests

- authorization initiation and configured scope;
- valid callback completion;
- missing, invalid, expired and replayed state;
- cross-owner and provider-mismatch callback;
- authorization denial and token acquisition failure;
- account discovery with zero, one and multiple accounts;
- explicit selection, same-account idempotency and different-account rebinding
  rejection;
- expired/revoked token and transport disconnect behavior;
- restart persistence and fail-closed readiness;
- credential/token redaction and Trading Core boundary inspection;
- capability-less execution failure for cTrader;
- Kraken regression coverage.

## Validation Boundary

```text
LOCAL_DETERMINISTIC_TEST = REQUIRED
REAL_PROVIDER_VALIDATION = REQUIRED
CTRADER_ACCOUNT_SYNCHRONIZED = NO
RISK_READY_FROM_CTRADER = NO
CTRADER_EXECUTION_ENABLED = NO
S1_REQUIRED_FOR_CTRADER_AUTH = NO
AI_REQUIRED_FOR_CTRADER_AUTH = NO
```

Real-provider validation, if later authorized, must use human-provided cTrader
application/account configuration, view-only scope, no orders, and a documented
revocation/cleanup procedure.

## Next Workflow Gate

Human approval of the Repository Analysis is required before the
Implementation Plan may be produced. This Story does not authorize production
code changes by itself.
