# ADR-074 - cTrader Authoritative READ-ONLY Synchronization Boundary

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-10-23 |
| **Decision Makers** | Project Owner, Trading OS Architecture |
| **Related ADRs** | ADR-028, ADR-031, ADR-040, ADR-071, ADR-072, ADR-073 |
| **Related Stories** | Story 0148 |

---

## 1. Context

Story 0147 establishes cTrader authentication and explicit external-account
binding, but intentionally does not synchronize account facts. The current
provider-neutral v2 snapshot contains balances and positions with one observation
timestamp. It does not express equity, margin, orders, deals/fills, completeness,
observation coherence or freshness.

ADR-071 requires authoritative account identity, balance, equity, margin, open
positions, pending orders, orders, deals/fills, realized and unrealized P&L,
commissions, swaps, observation time, freshness and completeness before any
future cTrader execution admission. ADR-028 and ADR-031 require Trading Core to
assemble financial snapshots and Risk context, while Broker Service supplies
broker facts and owns provider-specific synchronization.

cTrader obtains account, position, order and deal facts through separate
provider messages. Those responses can have different provider timestamps and
cannot be assumed to form one atomic provider snapshot. A partial or stale
observation must not be presented as authoritative financial input.

## 2. Decision

Broker Service SHALL own cTrader READ-ONLY synchronization, provider transport,
provider mapping, response identity validation and provider-fact normalization.

Broker Service SHALL expose an extended provider-neutral authoritative account
observation containing, when supported and validated:

- bound external account identity and account currency;
- balance, equity and provider-reported margin facts;
- open positions;
- pending orders and their provider status;
- bounded deals/fills and realized transaction facts;
- provider identifiers, monetary precision, volume semantics and timestamps;
- observation start/end, provider update timestamps, completeness and freshness
  metadata;
- explicit missing-fact or unavailable reasons.

Trading Core SHALL consume only this provider-neutral observation and SHALL remain
responsible for assembling its deterministic `AccountSnapshot`, `PortfolioSnapshot`
and Risk Evaluation Context according to ADR-028. Broker Service SHALL not apply
FTMO rules, risk profiles, drawdown rules, position sizing or execution approval.

The existing `BrokerAccountSnapshotV2` boundary SHALL be extended, or replaced by
an equivalent provider-neutral contract, rather than exposing cTrader DTOs or
making Trading Core invoke cTrader operations independently. The implementation
must preserve backward compatibility for existing Kraken behavior where the
current contract remains supported.

## 3. Observation Semantics

One synchronization is a bounded read operation. It is complete only when every
fact required by the requested observation contract has been obtained and passes
identity and validation checks. Separate cTrader responses are associated with
one observation window; their individual provider timestamps are preserved.

The observation SHALL distinguish at least:

```text
COMPLETE
PARTIAL
UNAVAILABLE
STALE
IDENTITY_MISMATCH
AUTHENTICATION_INVALID
TRANSPORT_FAILURE
```

Only `COMPLETE` and `FRESH` facts may be used by Trading Core as current
authoritative input. A missing scalar provider fact is not converted to zero or
an inferred value. cTrader may omit collection fields when the corresponding
account state is empty; for the position, order, unrealized-PnL and deal
collections, omission is therefore normalized to an empty collection only after
the response identity, account balance, valuation asset and pagination facts
have been validated. When no provider event timestamp exists because all event
collections are empty, the bounded synchronization capture time is used as the
observation timestamp and remains distinct from any provider event timestamp.
If cTrader omits a required scalar fact, the observation remains incomplete.

Freshness is based on explicit synchronization completion and a configured
staleness policy. An observation persisted for history or diagnostics does not
become fresh merely because the process restarts. Reconnection, token expiry,
provider disconnect and uncertain response require a new authenticated
authoritative synchronization.

## 4. Identity and Ownership Invariants

Every synchronization SHALL follow:

```text
authenticated owner
  -> local BrokerAccount
  -> owner/provider BrokerConnection
  -> durable bound cTrader externalAccountId
  -> provider response ctidTraderAccountId
```

The provider response account ID must exactly match the durable binding and the
configured demo/live environment. A mismatch fails closed. Provider data must
never create, replace or silently change the binding, and no fallback to another
provider or account is allowed.

## 5. Persistence and Restart

Broker Service may persist normalized observations for history, diagnostics and
deduplication, but persisted facts are not automatically current financial
authority. A current authoritative observation requires a successful
post-authentication synchronization after restart or reconnect.

If the implementation persists a current observation for operational reuse, its
owner, local BrokerAccount, external account identity, version, observation
window, completeness and freshness must be persisted together. Any persistence
failure during a synchronization fails the synchronization closed rather than
publishing a misleading complete observation.

Stable provider identifiers are the deduplication authority for positions,
orders, deals and balance operations where cTrader supplies them.

## 6. Security and Capability Boundary

- cTrader access tokens, refresh tokens, transport sessions, DTOs and provider
  errors remain inside Broker Service.
- The first synchronization uses view-only authorization and does not require
  trading scope.
- No cTrader order, cancel, amend, close, SL/TP or execution capability may be
  registered, exposed or inferred by this ADR.
- Provider errors are translated into provider-neutral unavailable outcomes
  without leaking raw payloads or credentials.
- Logs, metrics and exceptions must redact credentials and sensitive provider
  payloads.

## 7. Official Contract Constraints

This decision is based on the official cTrader Open API documentation consulted
on 2026-10-23:

- https://help.ctrader.com/open-api/account-authentication
- https://help.ctrader.com/open-api/messages
- https://help.ctrader.com/open-api/model-messages
- https://help.ctrader.com/open-api/proxies-endpoints

The documentation defines account identity, account authorization scope, position,
order and deal models, provider timestamps, money precision via `moneyDigits`,
provider-specific symbol IDs and separate demo/live endpoints. It does not prove
that the target FTMO account exposes every requested fact with one atomic
timestamp; controlled real-account READ-ONLY validation remains required.

## 8. Rejected Alternatives

### Expose cTrader DTOs to Trading Core

Rejected because it violates ADR-071 and ADR-072, couples Trading Core to a
provider protocol and weakens provider replacement.

### Treat account, positions, orders and deals as independently current

Rejected because separate provider responses can describe different moments and
would allow an incoherent financial context.

### Reuse the last persisted observation after restart or disconnect

Rejected because historical facts are not proof of current provider state and
would violate fail-closed freshness semantics.

### Let Broker Service calculate FTMO/Risk decisions

Rejected because ADR-028 and ADR-031 assign deterministic financial snapshot
assembly and authorization to Trading Core/Risk Domain.

### Add cTrader execution while implementing synchronization

Rejected because read-only synchronization is a prerequisite and execution is a
separate approved scope.

## 9. Scope Boundary

This ADR authorizes the architecture for Story 0148 only:

- cTrader READ-ONLY provider synchronization;
- provider-neutral account, position, order and deal observations;
- completeness, coherence and freshness metadata;
- owner/account identity validation;
- controlled normalized observation persistence if required by implementation.

It does not authorize:

- cTrader execution or mutation;
- FTMO Challenge rules or lifecycle;
- Market Data integration;
- AI, S1 or event-bus work;
- changes to Risk formulas or human authorization;
- live provider validation without explicit human credentials and procedure;
- a cTrader SDK or transport dependency decision beyond the existing adapter
  boundary.

## 10. Consequences

Positive:

- Provider authority remains isolated in Broker Service.
- Trading Core receives sufficient facts without cTrader leakage.
- Incomplete, incoherent, stale or cross-account observations fail closed.
- Risk remains deterministic and assembled at the correct architectural boundary.
- Restart and reconnect cannot silently promote historical facts to current facts.
- The same contract can support later providers without cTrader-specific core
  branches.

Trade-offs:

- The provider-neutral contract must grow beyond balances and positions.
- Synchronization must coordinate multiple cTrader responses and timestamps.
- Real-account validation is required to prove field availability and semantics.
- Persistence, deduplication and freshness rules add operational complexity.

## 11. Required Follow-Up

After approval, Story 0148 must produce an Implementation Plan covering the
provider-neutral contract, cTrader adapter mapping, observation state model,
ownership checks, persistence/restart behavior, tests and controlled READ-ONLY
validation. No implementation is authorized before that plan is approved.
