# ADR-084 - LIVE Financial Facts and Provider Preflight Readiness

## Status

Review

## Date

2026-11-03

## Decision Type

LIVE provider financial facts, capability-based account observations, and
provider-authoritative proposed-order margin

## Related ADRs

- ADR-028 - Deterministic Risk Engine Architecture
- ADR-043 - Account Identity and Mode-Specific Risk Facts
- ADR-071 - FTMO Challenge Target and cTrader Broker Boundary
- ADR-074 - cTrader Authoritative READ-ONLY Synchronization Boundary
- ADR-078 - PAPER/LIVE Execution Boundary
- ADR-082 - Provider-Independent CFD Financial Model and Mark-to-Market Accounting
- ADR-083 - PAPER Margin Authority and Simulation Policy

## Related Stories

- Story 0148 - cTrader Authoritative READ-ONLY Account Synchronization
- Future Story: Implement capability-based LIVE financial readiness and cTrader
  required-margin capability

---

## 1. Context

ADR-071 requires authoritative financial facts before LIVE execution admission.
ADR-074 established the Broker Service boundary for coherent, fresh,
owner-scoped cTrader READ-ONLY synchronization. ADR-082 established that LIVE
CFD financial facts and margin remain provider-authoritative. ADR-083 established
that PAPER margin is a local deterministic policy while LIVE margin remains
provider-authoritative.

The current provider-neutral snapshot contract contains account-level fields for
`balance`, `equity`, `usedMargin` and `freeMargin`. The current cTrader
qualification has established neutral propagation of account position model,
margin calculation model, balance, valuation asset, monetary scale, leverage and
zero current exposure for the qualified account. Those values are validation
evidence, not universal contract constants.

Official cTrader Open API documentation establishes provider facts including:

- `ProtoOATrader.balance`;
- account position and total margin calculation models;
- position state and pending-order state;
- position-level `usedMargin`;
- position-level gross and net unrealized PnL;
- `ProtoOAExpectedMarginReq` and `ProtoOAExpectedMarginRes` for expected margin.

The consulted documentation does not establish general current-account snapshot
fields for `equity`, account-level `usedMargin` or `freeMargin`. The
`equity` field of `ProtoOADepositWithdraw` is specific to a balance operation and
is not a general current-account equity source. Position-level margin facts do
not establish a generic account-level aggregation formula.

The official references consulted for these protocol facts are:

- https://help.ctrader.com/open-api/model-messages/
- https://help.ctrader.com/open-api/messages/
- https://help.ctrader.com/open-api/profit-loss-calculation/

Trading OS must therefore distinguish the facts required to describe the
current account from facts required to evaluate a particular risk policy and
facts required for a particular proposed order. A universal complete snapshot
would either invent provider semantics or make unrelated read-only capabilities
unusable.

---

## 2. Problem

Trading OS must answer these questions before a manual LIVE CFD order:

1. Is the correct owner-bound account being observed?
2. Is the current account observation coherent and fresh?
3. Which current positions, pending orders, PnL and position margins exist?
4. Which historical facts are required by the selected risk policy?
5. What margin does the proposed order require under provider semantics?
6. Does the selected deterministic risk policy have all facts it requires?
7. Is human authorization and every other execution gate present?

These questions do not require one universal scalar snapshot. In particular:

```text
current account used margin != required margin for proposed trade
observation completeness != risk readiness
risk readiness != preflight readiness
preflight readiness != execution authorization
```

---

## 3. Decision

Trading OS adopts the following architecture:

```text
OPTION_D = CAPABILITY_BASED_CURRENT_ACCOUNT_SNAPSHOT
           + SEPARATE_PROVIDER-AUTHORITATIVE_LIVE_MARGIN_PREFLIGHT
```

`BrokerAccountSnapshotV2` represents a provider-neutral current account
observation. It does not declare that every conceivable financial fact is
available for every broker.

LIVE proposed-order margin is a separate provider-neutral financial fact
resolved through the existing `RequiredMarginPort` boundary:

```text
cTrader ExpectedMargin
    -> Broker Service provider adapter
    -> provider-neutral RequiredMarginPort
    -> Trading Core risk/preflight
    -> Risk Domain normalized monetary fact
```

The cTrader adapter is not implemented or provider-validated by this ADR.
Actual cTrader support requires a future implementation, a capability support
matrix and controlled provider validation.

No cTrader equity, free-margin or generic account-used-margin formula is
approved by this ADR.

---

## 4. Readiness Semantics

### 4.1 Observation completeness

An observation is `COMPLETE` only when all facts declared mandatory by the
requested observation capability have been obtained, identity-validated,
coherent and fresh enough for that capability.

`COMPLETE` does not mean that every financial fact supported by every broker is
present. A capability may be complete while a separate optional or unsupported
capability is unavailable.

An observation is `INCOMPLETE` when one or more facts required by its declared
capability are missing, stale, ambiguous, incoherent or unsupported.

Partial READ-ONLY display may remain available for an incomplete observation.
Missing facts must never silently become zero, empty, false or derived values
unless a separate approved semantic contract explicitly allows that result.

### 4.2 Risk readiness

An account is `RISK_READY` only when every current and historical fact required
by the selected deterministic risk policy is authoritative and fresh enough.

Examples:

```text
equity-dependent drawdown policy + equity unavailable
    -> NOT_RISK_READY

daily realized-loss policy + incomplete deal history
    -> NOT_RISK_READY

current-exposure policy + positions unavailable
    -> NOT_RISK_READY
```

Missing a fact that the selected policy does not consume does not automatically
make every unrelated risk capability unavailable.

### 4.3 Preflight readiness

LIVE `PREFLIGHT_READY` requires:

```text
RISK_READY
+ required instrument and valuation facts
+ provider-authoritative required-margin quote for the proposed order
+ all existing deterministic preflight gates
```

If the required-margin quote is missing, stale or unsupported, LIVE preflight
fails closed.

### 4.4 Execution readiness

`EXECUTION_READY` requires:

```text
PREFLIGHT_READY
+ fresh T1 revalidation
+ human authorization
+ enabled execution capability
+ correct account and environment identity
+ all existing execution safety gates
```

Approval of this ADR never authorizes provider execution, real-money execution
or automatic trading.

---

## 5. Financial Fact Authority

### 5.1 Balance

The source of truth for LIVE `balance` is the provider-authoritative current
account fact normalized by Broker Service.

### 5.2 Equity

The source of truth for LIVE `equity` is provider-authoritative current account
equity.

For cTrader, this ADR approves no local derivation from balance, PnL, deposits,
withdrawals or any other fields. `ProtoOADepositWithdraw.equity` is not promoted
to a general current-account equity source.

When a selected risk policy requires equity and authoritative equity is
unavailable:

```text
equity-dependent risk = FAIL_CLOSED
```

No fallback to balance, locally reconstructed PnL, estimated equity or event
equity is allowed without a future approved semantic contract.

### 5.3 Account used margin

The source of truth for account-level `usedMargin` is a provider-authoritative
account-level fact with validated semantics, when the provider supplies one.

Position-level margin remains a separate fact. Trading OS must not automatically
sum position margins or apply `MAX`, `SUM` or `NET` aggregation without a
validated provider contract that covers the account model and supported exposure
semantics.

### 5.4 Free margin

The source of truth for LIVE `freeMargin` is a provider-authoritative capacity
fact when available and semantically validated.

For cTrader, the following is not approved as provider truth:

```text
freeMargin = equity - usedMargin
```

Missing free margin does not universally block cTrader or unrelated READ-ONLY
capabilities. It blocks only a selected risk or capacity policy that explicitly
requires provider-reported free margin and has no approved replacement fact.

### 5.5 Position used margin

The source of truth for position `usedMargin` is the provider position fact,
normalized by Broker Service with its monetary scale, identity and observation
metadata.

Position margin must not be promoted to account margin or proposed-order margin
without an explicit approved semantic contract.

### 5.6 Unrealized PnL

The source of truth for LIVE unrealized PnL is the provider-authoritative
position-level fact. Gross and net PnL remain distinct. Coverage, position
identity, monetary scale, observation time and provenance must be preserved.

An empty PnL collection may represent zero current position PnL only when the
provider contract proves that the collection is authoritative and complete.
Missing PnL data must not be converted to account-level zero PnL.

### 5.7 Required margin for a proposed LIVE trade

Required margin is a provider-authoritative proposed-trade fact resolved before
provider mutation through `RequiredMarginPort`.

It is not:

- current account used margin;
- current position margin;
- current account free margin;
- notional exposure;
- leverage;
- an order rejection;
- execution authorization.

Failure, staleness or unsupported semantics produce an unavailable fact and no
provider order may be submitted.

---

## 6. PAPER and LIVE Authority

This ADR clarifies and preserves ADR-083:

```text
PAPER -> local deterministic versioned MarginPolicy
LIVE  -> provider-authoritative margin semantics
```

PAPER may calculate proposed-order margin locally because Trading OS owns the
simulated financial semantics and the calculation is governed by an immutable,
versioned `MarginPolicy`.

LIVE Trading Core must not reproduce a provider-specific margin engine merely to
make a provider fit the generic snapshot. Broker Service owns provider-specific
interpretation and exposes only the normalized provider-neutral fact.

The existing `RequiredMarginPort` remains the provider-neutral abstraction for
both modes. Its implementations differ by authority model:

```text
PAPER -> deterministic local MarginPolicy implementation
LIVE  -> provider-authoritative Broker Service implementation
```

No overlapping second margin port is introduced by this decision.

---

## 7. Current, Historical and Proposed Facts

Trading OS separates these conceptual categories:

### Current account facts

- balance;
- current positions;
- pending orders;
- position-level PnL;
- position-level margin;
- provider account model and scaling metadata.

### Historical risk facts

- deals and fills;
- realized PnL over a selected interval;
- daily realized loss;
- challenge history.

### Proposed-trade facts

- required margin for the proposed order;
- proposal-specific valuation and contract facts.

Historical deal data does not become current account state merely because it is
returned in the same provider synchronization operation. Implementation may
transport related facts together, but availability and readiness semantics remain
separate.

---

## 8. Deal History Availability

Historical deal data has independent collection availability semantics:

```text
AVAILABLE_EMPTY
AVAILABLE_NON_EMPTY
UNAVAILABLE_OR_ABSENT
TRUNCATED
```

A missing or omitted repeated deal field must not automatically become an
authoritative empty collection unless the provider protocol proves that
semantic. `hasMore=false` alone is not sufficient proof for an omitted field.

Deal history is policy-dependent for risk readiness. It is required when the
selected policy consumes daily realized PnL, realized-loss limits or deal-based
reconciliation. It is not universally required for current-account display or
unrelated current-state policies.

The decoder and neutral propagation details belong to a follow-up Story, not to
this ADR.

---

## 9. Risk and Prop-Firm Safety

The following rules remain fail-closed:

- daily loss;
- maximum loss;
- drawdown;
- equity floors;
- equity-based exposure rules;
- any policy explicitly requiring authoritative equity.

None may fall back to:

- balance;
- balance plus locally reconstructed PnL;
- deposit/withdraw event equity;
- estimated equity;
- a provider rejection after order submission.

Provider rejection is not preflight validation. Risk validation and required
margin resolution must occur before provider mutation.

---

## 10. Ownership Boundaries

### Broker Service

Broker Service owns:

- provider connectivity;
- provider protocol interpretation;
- provider scaling and precision;
- provider capability detection;
- provider-authoritative financial facts;
- cTrader ExpectedMargin adaptation;
- deal collection availability interpretation;
- provider error translation.

### Trading Core

Trading Core owns:

- provider-neutral contracts;
- risk-policy fact requirements;
- risk readiness;
- preflight readiness;
- T0/T1 orchestration;
- execution gates;
- human authorization.

Trading Core must not contain cTrader DTOs, cTrader protocol messages,
`moneyDigits` semantics, cTrader enum handling or provider-specific margin
engines.

### Risk Domain

Risk Domain consumes normalized provider-neutral facts only. It remains
deterministic and does not calculate provider margin or interpret cTrader
protocol semantics.

---

## 11. Compatibility with Existing ADRs

This ADR creates a new decision rather than rewriting accepted historical ADRs.

### ADR-071

This ADR clarifies the interpretation of execution minimums as policy-scoped
readiness requirements. It does not weaken the invariant that missing
safety-critical facts block the relevant risk and execution path. Account
identity, freshness, completeness, open exposure and applicable historical facts
remain required where the selected policy consumes them.

### ADR-074

This ADR extends the meaning of snapshot completeness to be capability-scoped.
It preserves Broker Service ownership, coherent observation, freshness,
identity validation, explicit missing reasons and fail-closed risk admission.
`COMPLETE` no longer implies that every possible financial capability is
available.

### ADR-082

This ADR clarifies and preserves provider authority for LIVE CFD financial
semantics. It does not approve local equity, free-margin or generic account
margin derivation and does not change the Risk Domain boundary.

### ADR-083

This ADR clarifies and preserves the PAPER/LIVE margin authority split and
reuses `RequiredMarginPort`. It does not replace the versioned local PAPER
`MarginPolicy` or make PAPER depend on Broker Service.

### Other related ADRs

ADR-028, ADR-043 and ADR-078 remain applicable. No accepted ADR is superseded by
this decision.

---

## 12. Rejected Alternatives

### Universal strict snapshot

Rejected because it conflates observation completeness, risk readiness and
execution readiness. It also makes unrelated missing facts disable every
provider capability.

### Local cTrader equity derivation

Rejected because the provider semantics and complete input contract are not
established. A formula that appears financially plausible is not provider
authority.

### `freeMargin = equity - usedMargin`

Rejected as cTrader provider truth because both account-level inputs and the
provider treatment of costs, bonuses, reservations and other semantics are not
established.

### Generic `sum(position.usedMargin)`

Rejected because position margin does not establish account-level aggregation
under every supported `MAX`, `SUM` or `NET` account model.

### Provider order rejection as margin validation

Rejected because the provider mutation would occur before deterministic risk
validation and could create financial exposure.

---

## 13. Consequences

### Positive consequences

- Current account observation remains provider-neutral.
- No cTrader financial scalar is invented.
- Partial READ-ONLY capability remains usable.
- Risk remains fail-closed for facts actually required by its policy.
- PAPER and LIVE authority remain explicit and different.
- Proposed-order margin is correctly separated from current account state.
- Multi-broker support improves because Trading Core consumes business facts.
- Provider rejection cannot be used as a substitute for preflight risk validation.

### Costs and trade-offs

- Snapshot availability and readiness require more explicit modeling.
- Risk readiness becomes policy-dependent rather than a universal boolean.
- Provider capability/support matrices are required.
- A future cTrader ExpectedMargin adapter and controlled validation are required.
- Equity-dependent cTrader prop-firm rules remain blocked until authoritative
  equity is available.
- Deal history requires independent availability semantics.
- The number of deterministic readiness and fail-closed test combinations grows.

---

## 14. Security and Safety Invariants

The following invariants remain mandatory:

```text
NO_AUTOMATIC_TRADING_AUTHORIZATION = true
NO_PROVIDER_MUTATION_DURING_OBSERVATION = true
NO_PROVIDER_ORDER_BEFORE_PREFLIGHT_READY = true
NO_T1_ADMISSION_WITH_REQUIRED_FACTS_MISSING = true
NO_SAFETY_CRITICAL_FINANCIAL_APPROXIMATION = true
NO_PROVIDER_SEMANTICS_IN_RISK_DOMAIN = true
HUMAN_AUTHORIZATION_REQUIRED = true
CTRADER_SAFETY_EXECUTION_ENABLED = false
```

Architecture approval does not authorize credentials, provider calls, preflight,
controlled execution or trading.

---

## 15. Implementation Implications

This ADR authorizes no production implementation. A future Story may address,
in the smallest coherent sequence:

1. deal availability propagation;
2. capability-based snapshot semantics;
3. policy-dependent risk readiness;
4. preflight readiness evaluation;
5. `RequiredMarginPort` refinement where necessary;
6. cTrader ExpectedMargin adaptation;
7. T0/T1 integration;
8. provider capability and support-matrix validation.

No implementation may add cTrader-specific financial semantics to Trading Core or
Risk Domain.

---

## 16. Story 0148 Boundary

Story 0148 established the cTrader authoritative READ-ONLY synchronization
foundation. This ADR does not expand or relabel that Story.

Story 0148 remains subject to its separate controlled READ-ONLY provider
validation gate. The financial/readiness architecture described here requires a
separate governed implementation Story after this ADR is accepted.

Proposed follow-up Story title:

```text
Implement capability-based LIVE financial readiness and cTrader required-margin capability
```

No Story number is assigned by this ADR.

---

## 17. Validation Boundary

This ADR is documentation-only and has not used cTrader credentials, provider
connections, provider observations or trading operations.

Before implementation is considered ready:

```text
ADR_APPROVAL = REQUIRED
FOLLOW_UP_STORY_APPROVAL = REQUIRED
PRODUCTION_IMPLEMENTATION = NOT_AUTHORIZED
REAL_PROVIDER_EXECUTION = NOT_AUTHORIZED
AUTOMATIC_TRADING = NOT_AUTHORIZED
```

---

## 18. Decision Summary

```text
LIVE_ACCOUNT_STATE = CAPABILITY_BASED_PROVIDER_NEUTRAL_OBSERVATION
LIVE_PROPOSED_MARGIN = SEPARATE_PROVIDER_AUTHORITATIVE_PREFLIGHT_FACT
REQUIRED_MARGIN_PORT = REUSED
PAPER_MARGIN_AUTHORITY = LOCAL_VERSIONED_MARGIN_POLICY
LIVE_MARGIN_AUTHORITY = PROVIDER
EQUITY_DERIVATION = NOT_APPROVED
FREE_MARGIN_DERIVATION = NOT_APPROVED
GENERIC_ACCOUNT_MARGIN_AGGREGATION = NOT_APPROVED
MISSING_EQUITY_FOR_EQUITY_POLICY = FAIL_CLOSED
MISSING_FREE_MARGIN = NOT_UNIVERSALLY_BLOCKING
MISSING_REQUIRED_MARGIN_QUOTE = ALWAYS_BLOCKING
OBSERVATION_COMPLETE != RISK_READY
RISK_READY != PREFLIGHT_READY
PREFLIGHT_READY != EXECUTION_AUTHORIZATION
PRODUCTION_CODE_CHANGED = NO
PROVIDER_REQUESTS = 0
PROVIDER_MUTATIONS = 0
```
