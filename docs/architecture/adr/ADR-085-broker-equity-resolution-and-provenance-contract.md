# ADR-085 - Broker Equity Resolution and Provenance Contract

## Status

ACCEPTED

## Date

2026-11-03

## Decision Type

Broker equity resolution, financial-fact provenance and fail-closed risk input

## Related ADRs

- ADR-028 - Deterministic Risk Engine Architecture
- ADR-043 - Account Identity and Mode-Specific Risk Facts
- ADR-071 - FTMO Challenge Target and cTrader Broker Boundary
- ADR-074 - cTrader Authoritative READ-ONLY Synchronization Boundary
- ADR-075 - Provider-Neutral Multi-Phase Challenge Domain
- ADR-078 - PAPER/LIVE Execution Boundary
- ADR-082 - Provider-Independent CFD Financial Model and Mark-to-Market Accounting
- ADR-084 - LIVE Financial Facts and Provider Preflight Readiness

## Related Stories

- Story 0148 - cTrader Authoritative READ-ONLY Account Synchronization
- Story 0193 - LIVE Financial Readiness and cTrader Required Margin

---

## 1. Context

Trading OS has completed the technical T1 remediation without declaring LIVE
readiness. The cTrader account can be authenticated and observed in READ-ONLY
mode, but the normalized observation for the reference account contains:

```text
balance       = 50,000.00 USD
equity        = unavailable
used margin   = unavailable
free margin   = unavailable
positions     = available and empty
orders        = available and empty
unrealized PnL = available and empty
deals         = unavailable
freshness     = FRESH
completeness  = INCOMPLETE
```

The selected `FTMO_2STEP_50K_RISK` policy requires equity, balance, positions
and starting balance. The Risk Engine therefore correctly returns a blocking
context-unavailable result. This ADR investigates the source and contract of
equity; it does not authorize an implementation or make T1 LIVE-ready.

The relevant reference account is:

```text
Trading Core account: 7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456
cTrader account:      49411967
Environment:          LIVE
Program:              FTMO_FREE
Policy:               FTMO_2STEP_50K_RISK 1.0.0
```

---

## 2. Problem

Trading OS must distinguish three materially different situations:

1. The broker directly reports a current equity value with authoritative
   semantics.
2. The broker exposes enough validated facts for Trading OS to derive an equity
   value under an explicit accounting contract.
3. The facts are insufficient, stale, incoherent or semantically ambiguous, so
   equity must remain unavailable.

The system must not silently convert an unknown equity into balance, zero or a
locally estimated value. It must also not expose provider-specific cTrader
messages to Trading Core or let a broker adapter implement FTMO policy rules.

---

## 3. Technical Findings

### 3.1 Account balance

The official cTrader Open API `ProtoOATrader` model exposes:

- `ctidTraderAccountId`;
- `balance`;
- `balanceVersion`;
- `depositAssetId`;
- optional account model, margin calculation model, leverage, bonuses and
  `moneyDigits`.

`balance` is the current account balance and its integer value is scaled using
`moneyDigits`. Trading OS currently maps this through the cTrader adapter's
`money(...)` conversion and preserves the deposit asset as the valuation asset.

Sources:

- cTrader model messages: <https://help.ctrader.com/open-api/model-messages/>
- `JsonWebSocketCTraderOpenApiClient.mapObservation(...)` in
  `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/authentication/JsonWebSocketCTraderOpenApiClient.java`.
- ADR-074, sections 2, 3 and 7.

### 3.2 Current account equity

The official `ProtoOATrader` model does not document a general current-account
`equity` field. cTrader documents an `equity` field on
`ProtoOADepositWithdraw`, but that value is the equity after a specific balance
operation. It is an event-specific historical fact, not a general current
account equity endpoint.

The current adapter therefore reads a possible `trader.equity` field only as an
optional provider response field and does not invent a value when it is absent.
The normalized snapshot marks `EQUITY_UNAVAILABLE` and remains incomplete.

Sources:

- `ProtoOATrader` and `ProtoOADepositWithdraw` model definitions:
  <https://help.ctrader.com/open-api/model-messages/>
- `CTraderAuthoritativeSnapshotCapability.map(...)` in
  `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/synchronization/CTraderAuthoritativeSnapshotCapability.java`.
- `JsonWebSocketCTraderOpenApiClient.mapObservation(...)`, especially the
  explicit null-preserving equity mapping and comment at the equity resolution
  block.
- ADR-084, section 5.2.

**Confirmed conclusion:** the official contract reviewed does not establish a
provider-reported general current equity source for cTrader.

### 3.3 Open positions

`ProtoOAReconcileReq` returns current open positions and pending orders for a
specific `ctidTraderAccountId`. `ProtoOAReconcileRes` contains repeated
`position` and `order` collections. Position facts include a provider position
identifier, trade data, VWAP/entry price, position status, update timestamp,
swap, commission, used margin, money precision and protection fields.

The cTrader adapter maps these collections into provider-neutral positions and
orders. It preserves collection availability so that an omitted collection is
not confused with an unavailable response after correlation and account
validation.

Sources:

- `ProtoOAReconcileReq` and `ProtoOAReconcileRes`:
  <https://help.ctrader.com/open-api/messages/>
- `ProtoOAPosition`:
  <https://help.ctrader.com/open-api/model-messages/>
- `CTraderReadOnlyObservation.CollectionState` and position mapping in
  `CTraderReadOnlyObservation.java` and
  `JsonWebSocketCTraderOpenApiClient.java`.
- ADR-074, sections 2 and 3.

### 3.4 Unrealized PnL

`ProtoOAGetPositionUnrealizedPnLReq` requests unrealized PnL for all open
positions of an account. `ProtoOAGetPositionUnrealizedPnLRes` returns:

- the account identifier;
- response `moneyDigits`;
- repeated `ProtoOAPositionUnrealizedPnL` values.

Each `ProtoOAPositionUnrealizedPnL` contains the position ID,
`grossUnrealizedPnL` and `netUnrealizedPnL`. The official model describes both
values in the account deposit currency. The documented net value does not
include potential closing commission.

The adapter validates PnL coverage against the observed position identifiers.
Missing, duplicate or mismatched position PnL makes the PnL collection
incomplete.

Sources:

- cTrader PnL calculation:
  <https://help.ctrader.com/open-api/profit-loss-calculation/>
- `ProtoOAGetPositionUnrealizedPnLRes` and
  `ProtoOAPositionUnrealizedPnL`:
  <https://help.ctrader.com/open-api/messages/> and
  <https://help.ctrader.com/open-api/model-messages/>
- PnL coverage validation in
  `JsonWebSocketCTraderOpenApiClient.mapObservation(...)`.

### 3.5 Currency, commissions, swaps and conversion

The PnL response is documented in the account deposit currency, so an
additional quote-to-deposit conversion should not be applied to that response.
This does not prove that every other financial field has identical currency
semantics. Each field must retain its own money precision and source metadata.

The cTrader position model exposes:

- swap charged on the open position;
- current commission related to the position;
- mirroring commission where applicable;
- used margin in deposit currency;
- `moneyDigits` for monetary fields.

The cTrader close-position detail exposes gross profit, swap, commission,
balance, an optional quote-to-deposit conversion rate and an optional PnL
conversion fee. These close-detail fields describe realized execution events;
they do not by themselves provide a current account equity snapshot.

The official PnL contract states that net unrealized PnL excludes potential
closing commission. Therefore, the following cannot be assumed without a
validated contract:

```text
equity = balance + sum(netUnrealizedPnl)
```

It is unknown from the reviewed contract whether the position commission field,
the net PnL value, or a separate provider calculation fully represents all costs
needed by FTMO's equity definition at the observation instant. This is a
critical unknown for a broker-derived strategy.

Sources:

- `ProtoOAPosition`, `ProtoOAPositionUnrealizedPnL` and
  `ProtoOAClosePositionDetail`:
  <https://help.ctrader.com/open-api/model-messages/>
- FTMO Trading Objectives:
  <https://ftmo.com/en/trading-objectives/>
- ADR-082, sections 3.4, 3.5 and 3.8.

### 3.6 Observation and temporal coherence

The cTrader account, reconciliation, PnL and deal data are obtained through
separate requests. cTrader also emits execution events and account-scoped
invalidated-token events, but the protocol does not turn the separate read
responses into one atomic financial snapshot.

The current adapter records an observation start, provider-derived observation
time and completion time. It correlates responses to the requested account,
preserves provider timestamps where available and applies a freshness limit.
This is sufficient to describe a bounded observation window, not to prove that
balance, positions and PnL were evaluated at one identical provider instant.

Consequently, a derived equity requires an explicit temporal coherence policy.
At minimum it must reject:

- responses from different account IDs;
- responses outside the observation window;
- stale positions or PnL;
- incomplete position-to-PnL coverage;
- reconnect or token invalidation during collection;
- unknown pagination or truncation state;
- balance changes not reconciled with the collected positions and PnL.

Sources:

- cTrader `ProtoOAReconcileReq/Res`, `ProtoOAExecutionEvent` and account token
  invalidation messages:
  <https://help.ctrader.com/open-api/messages/>
- ADR-074, sections 1, 3, 4 and 5.
- `CTraderAuthoritativeSnapshotCapability` and
  `CTraderReadOnlyObservation` in Broker Service.

### 3.7 Account type and FTMO environment

cTrader exposes account type, margin calculation type, leverage, deposit asset
and environment-specific endpoints. These facts can affect provider financial
semantics and must be retained as account facts.

The reviewed cTrader documentation does not establish that an FTMO account
exposes a general equity field, nor that the cTrader Open API response uses the
same observation timing or rounding rules as FTMO's Account MetriX controls.
FTMO describes its challenge accounts as simulated accounts and publishes its
own equity definition and daily recalculation rules. A cTrader account being
marked LIVE in Trading OS is therefore not proof that Trading OS can reproduce
FTMO's authoritative compliance calculation.

Sources:

- cTrader account and model messages:
  <https://help.ctrader.com/open-api/model-messages/>
- FTMO Challenge overview:
  <https://ftmo.com/en/challenge/>
- FTMO Trading Objectives:
  <https://ftmo.com/en/trading-objectives/>
- ADR-071, ADR-074 and ADR-084.

---

## 4. Equity Strategies

### 4.1 Strategy A - broker-reported equity

Under this strategy, Broker Service consumes a current account equity field that
the provider documents as current account equity, normalizes its amount, asset,
precision, source timestamp and identity, and publishes it as
`BROKER_REPORTED`.

This is the preferred strategy when available because it preserves provider
authority and avoids reconstructing provider accounting rules.

For the cTrader contract reviewed here, no such general current-account field
has been established. `ProtoOADepositWithdraw.equity` is not sufficient because
it is tied to a balance operation. The current cTrader observation therefore
cannot satisfy this strategy.

Assessment:

| Criterion | Assessment |
|---|---|
| Availability on reviewed cTrader contract | Not established |
| Authority | Highest if current-account semantics are documented |
| Freshness | Must be provider-observed and bounded |
| Precision | Provider `moneyDigits` must be preserved |
| FTMO compatibility | Not demonstrated; provider and FTMO clocks/semantics may differ |
| Current decision | Keep unavailable until an official or controlled provider contract exists |

### 4.2 Strategy B - validated broker-derived equity

The conceptual starting point is:

```text
equity = balance + unrealized net PnL - applicable unsettled costs
```

This is not an approved formula by itself. A future derived capability could be
considered only when all of the following are true:

1. The balance and all open positions belong to the exact bound account.
2. Position reconciliation is complete, non-truncated and current.
3. PnL coverage is one-to-one with the complete open-position set.
4. PnL is explicitly denominated in the account deposit currency.
5. PnL `moneyDigits` and all monetary rounding are preserved.
6. The treatment of commission, swap, mirroring commission and potential closing
   commission is explicitly documented and tested.
7. Balance changes and execution events are reconciled within the observation
   window.
8. No reconnect, token invalidation or uncertain response occurred during the
   observation.
9. The observation has a configured maximum age and a bounded timestamp skew.
10. The derived result preserves references to every source observation and the
    exact derivation version.
11. The selected risk policy explicitly accepts `BROKER_DERIVED` equity.
12. The derived result is not represented as provider-reported equity.

For the current cTrader implementation, these preconditions are not all met:
deals are unavailable in the reference observation, the current account equity
field is absent, and the precise relationship between net unrealized PnL,
position commissions and potential closing commission has not been proven to
match FTMO's calculation at the same instant.

Assessment:

| Criterion | Assessment |
|---|---|
| Availability | Technically possible only for sufficiently complete observations |
| Authority | Trading OS-derived, not provider-reported |
| Freshness | Requires explicit observation-window and mark-age guarantees |
| Precision | Requires provider precision and deterministic rounding |
| FTMO compatibility | Not demonstrated; must be treated as an approximation until validated |
| Current decision | Do not use for FTMO equity-dependent risk |

### 4.3 Strategy C - equity unavailable

This strategy keeps equity unavailable when the provider-reported field is
absent or when a derived calculation fails any required guarantee. The result
must include bounded reasons such as:

```text
EQUITY_PROVIDER_FIELD_UNAVAILABLE
EQUITY_POSITION_COVERAGE_INCOMPLETE
EQUITY_PNL_CURRENCY_UNCONFIRMED
EQUITY_COST_SEMANTICS_UNCONFIRMED
EQUITY_OBSERVATION_NOT_COHERENT
EQUITY_OBSERVATION_STALE
EQUITY_ACCOUNT_ID_MISMATCH
EQUITY_RECONCILIATION_INTERRUPTED
EQUITY_DERIVATION_NOT_ACCEPTED_BY_POLICY
```

The Risk Engine must return a blocking context-unavailable result whenever the
selected policy requires equity and the result is unavailable. No unavailable
value may be changed into zero, balance, estimated equity or the last persisted
equity.

Assessment:

| Criterion | Assessment |
|---|---|
| Availability | Always semantically safe |
| Authority | Explicitly no equity authority claimed |
| Freshness | Not applicable to a missing value; source reasons remain traceable |
| Precision | No value is emitted |
| FTMO compatibility | Safe for fail-closed admission, not sufficient for evaluating a trade |
| Current decision | Required default for the reference cTrader account |

---

## 5. Architecture Decision

The accepted responsibility boundary is:

```text
cTrader API
    |
    v
cTrader Adapter
    |
    v
Broker Service observation and equity resolver
    |
    +-- BROKER_REPORTED
    +-- BROKER_DERIVED, only under an approved capability contract
    +-- UNAVAILABLE
    |
    v
Provider-neutral Broker Snapshot
    |
    v
Trading Core
    |
    v
RiskFactRequirements and deterministic Risk Engine
```

The equity resolver belongs in Broker Service because it owns provider
transport, response correlation, provider timestamps, money precision,
provider-specific cost semantics and normalized snapshot authority. It must not
contain FTMO rules or select whether a trade is strategically appropriate.

Trading Core consumes only a provider-neutral monetary fact and evaluates
whether its provenance is accepted by the selected policy. The Risk Domain
remains deterministic and provider-agnostic.

The accepted behavior is:

1. Accept `BROKER_REPORTED` only when its provider contract explicitly defines
   current account equity.
2. Treat `BROKER_DERIVED` as a separate provenance with derivation metadata,
   never as a provider-reported field.
3. Require explicit policy acceptance for derived equity.
4. Keep cTrader equity `UNAVAILABLE` for `FTMO_2STEP_50K_RISK` until the
   derivation contract and controlled validation demonstrate sufficient
   semantics.
5. Continue to fail closed for all equity-dependent risk evaluation.

This decision is compatible with ADR-084: capability completeness remains
policy-scoped, and unsupported financial facts do not make unrelated read-only
capabilities unusable.

### 5.1 Availability, quality and admissibility

Equity resolution SHALL distinguish three independent dimensions:

```text
PROVENANCE   = where the value came from
QUALITY      = whether the observation satisfies freshness, coherence,
               completeness and source guarantees
ADMISSIBILITY = whether a policy or capability authorizes this value
```

The resulting states are not interchangeable:

```text
available and admissible
available but not admissible
unavailable
```

`BROKER_REPORTED` identifies origin only. It does not automatically make a
value FTMO-authoritative. `BROKER_DERIVED` identifies a calculated value only.
It must never be presented as provider-reported and is not admissible for
`FTMO_2STEP_50K_RISK` under this ADR. `UNAVAILABLE` contains no numeric
substitute.

Broker Service SHALL provide normalized value, provenance and quality evidence.
Trading Core SHALL resolve policy requirements and determine whether the
provenance and quality guarantees are admissible for the requested capability.
The Risk Engine SHALL receive only facts that have passed those deterministic
checks. A required but non-admissible equity SHALL produce a fail-closed risk
outcome, even when a numeric value is available for display or diagnostics.

---

## 6. Proposed Equity Contract and Invariants

The future provider-neutral contract should carry, at minimum:

```text
amount                  optional monetary value
currency                required when amount is present
availability             REPORTED | DERIVED | UNAVAILABLE
provenance               provider/account/source identity
resolutionMethod         BROKER_REPORTED | BROKER_DERIVED | UNAVAILABLE
observedAt               provider or bounded observation timestamp
capturedAt               Trading OS capture timestamp
freshness                FRESH | STALE | UNKNOWN
observationId            source observation reference
sourceVersion            provider or adapter observation version
completeness             guarantees for the source facts
reasons                  bounded unavailable or qualification reasons
derivationVersion        required for BROKER_DERIVED
sourceReferences         balance, position and PnL source references
```

Required invariants:

- An amount is never present with `UNAVAILABLE`.
- `BROKER_REPORTED` requires a documented provider current-equity source.
- `BROKER_DERIVED` requires all derivation preconditions and source references.
- `UNAVAILABLE` carries no numeric substitute.
- Currency and monetary precision are explicit and validated.
- Account identity and owner binding are exact.
- `observedAt` and `capturedAt` are distinct when they represent different
  clocks.
- Freshness is evaluated at the decision boundary, not at persistence or
  process restart.
- The provenance cannot be downgraded or upgraded implicitly by Trading Core.
- Risk policy acceptance is explicit: a policy may require reported equity, or
  may separately permit a validated derived equity.
- A persisted historical equity is not current authority without a fresh
  authoritative observation.

The policy decision can therefore be expressed as:

```text
equity required
AND provenance accepted by policy
AND amount/currency/precision valid
AND identity valid
AND completeness valid
AND freshness valid
    -> usable risk fact

otherwise
    -> CONTEXT_UNAVAILABLE / fail closed
```

No classes, enums or code contracts are introduced by this ADR.

---

## 7. FTMO Compatibility

FTMO's published Trading Objectives define equity for maximum daily loss and
maximum loss as:

```text
Balance + open positions P/L +/- swaps - commissions
```

FTMO also defines daily and maximum-loss reference points using specific
00:00 CE(S)T or end-of-day rules, depending on the applicable product and
version. The published rules for the 2-Step process describe a 5% maximum daily
loss and a 10% maximum loss against initial simulated capital, but the exact
account program and rule version must remain configuration and policy concerns.

The following equivalences are **not demonstrated** by the current cTrader
contract:

- cTrader's current `netUnrealizedPnL` plus position facts equals FTMO's current
  open-position P/L including all applicable costs.
- cTrader's provider observation timestamp matches FTMO's risk-clock boundary.
- cTrader rounding and precision produce the same threshold comparison as FTMO.
- cTrader's commissions, swaps, mirroring fees and potential closing commission
  have identical inclusion and timing semantics.
- A Trading OS derived equity would equal FTMO Account MetriX equity.

Therefore:

- Trading OS equity may be used for its own deterministic risk policy only when
  its provenance and semantics are accepted by that policy.
- A derived equity must never be described as FTMO-authoritative.
- A risk buffer may reduce Trading OS exposure to model uncertainty, but it does
  not prove equivalence with FTMO and must be separately approved as policy.
- Until validation proves otherwise, missing cTrader equity remains a blocker
  for the equity-dependent FTMO policy.

---

## 8. Risk Analysis and Required Responses

| Failure scenario | Detection | Required behavior |
|---|---|---|
| Position missing | Reconcile completeness, expected position set and identity checks | `UNAVAILABLE`; block equity-dependent risk |
| PnL stale | Provider PnL timestamp and maximum age | `UNAVAILABLE`; do not reuse old PnL |
| Conversion absent | PnL currency and account deposit asset validation | `UNAVAILABLE`; no one-to-one conversion assumption |
| Commission omitted | Provider field semantics and cost coverage contract | `UNAVAILABLE` unless policy explicitly permits the known limitation |
| Swap misinterpreted | Position/deal semantics and sign validation | `UNAVAILABLE`; preserve raw provenance inside Broker Service |
| Double counting | Position ID, deal ID, balance version and observation deduplication | `UNAVAILABLE`; never aggregate uncertain events |
| Non-atomic snapshot | Observation window and cross-response timestamp validation | `UNAVAILABLE` when skew exceeds contract |
| Reconnection during observation | Session lifecycle, token invalidation and request correlation | Discard observation and reacquire |
| Rounding error | `moneyDigits`, decimal scale and deterministic rounding policy | Reject or preserve exact scaled values; never use binary approximation |
| Wrong account | Exact `ctidTraderAccountId` and durable binding comparison | Identity mismatch; fail closed |
| Out-of-order broker event | Provider version/timestamp and reconciliation after event stream gap | Reconcile before publishing current facts |
| Balance/positions inconsistency | Balance version, execution events and bounded re-read | `UNAVAILABLE` until reconciled |
| Deals unavailable | Deal collection availability and policy requirements | Block only policies requiring deals; preserve unrelated read-only capability |

---

## 9. Consequences

### Positive

- Provider authority remains inside Broker Service.
- Trading Core receives a provider-neutral fact with explicit provenance.
- Derived financial data cannot be mistaken for provider-reported authority.
- FTMO policy logic remains in policy/risk boundaries, not cTrader infrastructure.
- Missing or ambiguous data continues to fail closed.
- Future brokers can implement their own reported or derived capabilities without
  cTrader-specific branches in the Risk Domain.
- Partial READ-ONLY observations remain useful without becoming risk authority.

### Trade-offs

- cTrader equity-dependent LIVE risk remains unavailable until a source contract
  is approved and validated.
- A derived equity capability requires substantial reconciliation, cost and
  timestamp validation.
- Provider-reported and derived paths require separate tests and diagnostics.
- FTMO equivalence cannot be claimed solely from cTrader PnL availability.
- The normalized snapshot contract must carry provenance and source references.

---

## 10. Component Impacts

This ADR proposes no implementation. If accepted, a future Story would be
expected to address the following boundaries:

### cTrader Adapter

- Keep Protobuf/JSON fields, scaling, event correlation and provider error
  handling inside the adapter.
- Establish whether a current-equity field is actually available in the target
  account/API response.
- Preserve account, position, PnL, commission, swap and timestamp evidence.

### Broker Service

- Own observation coherence and any future equity resolution capability.
- Publish `BROKER_REPORTED`, `BROKER_DERIVED` or `UNAVAILABLE` explicitly.
- Never apply FTMO drawdown or execution decisions.

### Broker Snapshot

- Represent monetary value, currency, provenance, freshness, completeness and
  source references without collapsing derived data into provider facts.

### Trading Core

- Request facts according to `RiskFactRequirements`.
- Accept only provenance permitted by the selected policy.
- Keep account identity, challenge state and orchestration responsibility.

### Risk Engine and Policy

- Remain deterministic and provider-neutral.
- Declare whether equity must be provider-reported or whether a specific
  validated derivation is acceptable.
- Continue returning a blocking result for required unavailable equity.

---

## 11. Compatibility With ADR-084

This ADR refines, but does not supersede, ADR-084:

- ADR-084 remains authoritative that LIVE equity is provider-authoritative unless
  a future approved semantic contract explicitly permits a derived fact.
- `ProtoOADepositWithdraw.equity` is not promoted to general current equity.
- Missing required equity remains `NOT_RISK_READY`.
- Capability-scoped completeness remains valid.
- Free margin and account-level used margin remain independent facts and are not
  inferred from equity.
- Required proposed-order margin remains a separate provider-authoritative
  capability.

No rule in this ADR changes the `FTMO_2STEP_50K_RISK` policy or authorizes LIVE
execution.

---

## 12. Validation Strategy After Approval

### 12.1 Offline validation

A future implementation must prove, with deterministic fixtures:

- reported equity preserves provider amount, currency, precision and timestamp;
- derived equity requires complete position/PnL coverage;
- gross and net PnL remain distinct;
- commission, swap and mirroring commission are not silently discarded;
- potential closing commission semantics are explicitly handled;
- account currency and PnL currency are validated;
- duplicate, missing, stale and out-of-order facts fail closed;
- reconnect and token invalidation discard the observation;
- wrong-account responses fail closed;
- provenance and source references survive normalization;
- policy acceptance rejects unapproved derived equity;
- old persisted observations cannot become current authority;
- PAPER behavior remains unchanged.

### 12.2 LIVE READ-ONLY validation

Only after human approval and a controlled procedure, validate against the bound
account in READ-ONLY mode:

1. Confirm owner, local account, external account ID and environment.
2. Record the exact cTrader API version, account type, deposit asset and
   `moneyDigits`.
3. Capture account, reconcile, PnL, deal and execution-event responses within a
   bounded observation window.
4. Compare provider-visible balance, open positions, PnL, commissions and swaps
   against the normalized observation.
5. Compare any candidate derived equity against an independently observable
   provider UI or account report, without treating that comparison as proof of
   universal API semantics.
6. Repeat with no positions and with controlled non-mutating account states where
   possible.
7. Record discrepancies, timestamp skew, rounding and cost treatment.
8. Keep risk evaluation and execution disabled until the evidence is reviewed.

No LIVE test or provider call is authorized by this ADR. Any future validation
requires explicit human authorization and a separate controlled procedure.

---

## 13. Open Questions for Human Validation

1. Does the target cTrader/FTMO account expose a documented current-account
   equity field through any supported API response not used by the current
   adapter?
2. If not, is a Trading OS-derived equity acceptable for any FTMO risk policy,
   or must FTMO policies require `BROKER_REPORTED` forever?
3. Which exact cTrader commission fields must be included to reproduce the
   published FTMO equity definition at the decision boundary?
4. Does cTrader `netUnrealizedPnL` already include current position commission,
   or must the position commission field be applied separately?
5. How should mirroring commission and potential closing commission be handled?
6. What maximum timestamp skew and observation age are acceptable for a derived
   equity fact?
7. What independent FTMO reference is authorized for controlled read-only
   validation, and what discrepancy margin is acceptable?
8. Should a risk buffer be part of the FTMO policy, or is the policy required to
   represent only exact known facts?
9. Should the normalized contract expose one equity field with provenance, or a
   structured resolution result containing all candidate methods and rejected
   reasons?

---

## 14. Accepted Decision

Trading OS accepts an explicit provider-neutral equity resolution
contract owned by Broker Service and consumed by Trading Core through provenance
and policy requirements.

The contract should support broker-reported, validated broker-derived and
unavailable outcomes, but the current cTrader account should remain
`UNAVAILABLE` for equity-dependent FTMO risk evaluation. A derived value must
not be accepted merely because `balance + netUnrealizedPnL` produces a number.
It requires complete account identity, position/PnL coverage, currency,
precision, cost and temporal guarantees, plus explicit policy acceptance.

The general provenance contract is accepted. This acceptance does not authorize
any new equity calculation method, does not authorize `BROKER_DERIVED` for
`FTMO_2STEP_50K_RISK`, does not change FTMO thresholds and does not make T1
LIVE-ready. Implementation requires a separate Engineering Story and remains
subject to the open questions and validation requirements in this ADR.
