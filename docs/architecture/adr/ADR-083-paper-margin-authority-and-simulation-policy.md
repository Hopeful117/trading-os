# ADR-083 - PAPER Margin Authority and Simulation Policy

## Status

Accepted - human approval recorded

## Date

2026-10-31

## Decision Type

PAPER financial authority, proposed-order margin semantics, and simulation
policy boundary

## Related ADRs

- ADR-028 - Deterministic Risk Engine Architecture
- ADR-043 - Account Identity and Mode-Specific Risk Facts
- ADR-051 - Account Settlement Semantics and Instrument Product Identity
- ADR-075 - Provider-Neutral Multi-Phase Challenge Domain
- ADR-078 - PAPER/LIVE Execution Boundary
- ADR-081 - cTrader Instrument Product Classification
- ADR-082 - Provider-Independent CFD Financial Model and Mark-to-Market Accounting

## Related Stories

- Story 0183 - ADR-082 Phase 6 LIVE CFD Authoritative Fact Mapping and Reconciliation
- Story 0184 - ADR-082 Phase 7 CFD Risk Integration
- Story 0185 - ADR-082 Phase 8 CASH + CFD Compatibility Authorization
- Story 0186 - PAPER CFD Authoritative Proposed-Order Margin Boundary
- Story 0187 - PAPER Margin Authority and Simulation Policy ADR

---

## 1. Context

ADR-082 defines CFD financial contracts, mark-to-market accounting, provider
authority boundaries, and the requirement that required margin be an authoritative
fact. It deliberately does not define the missing PAPER margin authority.

Story 0186 confirmed the current boundary:

```text
PAPER_MARGIN_PREVIEW_RUNTIME = AVAILABLE
PAPER_MARGIN_PREVIEW_AUTHORITY = UNPROVEN
PAPER_REQUIRED_MARGIN_AUTHORITY = UNAVAILABLE
```

The current PAPER path calls Broker Service/provider preview infrastructure:

```text
PAPER
  -> BrokerRequiredMarginClient
  -> Broker Service provider preview
  -> RequiredMarginPort.Fact
```

That path can return a value, but it does not define a reproducible local PAPER
simulation contract. The Kraken implementation currently derives
`quantity * price / leverage` and defaults leverage to one. cTrader observations
provide current account/position margin and leverage metadata, not a complete
provider-neutral proposed-order margin operation. Using any of these facts as
PAPER authority would leave exposure interaction, netting/hedging, currency,
policy version, and provenance ambiguous.

`InstrumentFinancialContract` already defines what an instrument economically
is. It does not define account-specific collateral policy. `RequiredMarginPort`
already provides the application boundary for a normalized monetary fact used by
T0 and T1. The missing decision is the source and semantics that may produce that
fact for PAPER.

The decision must remain provider-neutral, reproducible, deterministic, and
small enough to implement without creating an institutional portfolio-margin
engine.

---

## 2. Problem

Trading OS must answer all of the following before PAPER CFD margin can be
implemented:

- What economic environment does a PAPER account simulate?
- Who owns PAPER required-margin truth?
- What exactly does proposed-order required margin measure?
- Which policy applies to an account and instrument?
- How does existing exposure affect the result?
- What are the supported netting, hedging, static, tiered, and dynamic cases?
- Which currency and FX evidence are authoritative?
- How are policy provenance and historical reproducibility preserved?
- What happens when policy or a required fact is missing?
- How does T1 use the policy after T0?
- What happens to the current PAPER provider preview path?

Without these decisions, a calculator would invent economic semantics and ADR-082
Phase 8 must remain blocked.

---

## 3. Decision

Trading OS SHALL use a **hybrid/reference-derived local simulation policy** for
PAPER margin:

```text
provider/reference/user configuration
        -> normalized provider-neutral MarginPolicy
        -> immutable versioned PAPER account assignment
        -> local deterministic T0/T1 calculation
        -> RequiredMarginPort.Fact
```

The runtime authority for PAPER is the immutable, versioned local `MarginPolicy`
assigned to the PAPER Trading Core account or its explicitly owned simulation
profile. The provider/reference source is configuration provenance only. PAPER
margin decisions must not require a Broker Service/provider call.

The policy is provider-neutral and may record whether it was:

```text
TRADING_OS_DEFINED
USER_CONFIGURED
PROVIDER_DERIVED
PROVIDER_VERIFIED
```

These provenance categories describe how the policy was obtained. They do not
make a provider-derived policy a live provider authority, and they do not imply
provider certification unless independently verified.

PAPER required margin remains unavailable until an applicable complete policy,
instrument contract, exposure state, price, reporting currency, and required FX
facts exist. No implicit global margin default is allowed.

LIVE remains provider-authoritative. LIVE required margin continues to use the
provider-neutral Broker Service boundary where the provider supplies a complete,
fresh, authoritative fact. LIVE is not routed through the PAPER policy.

This ADR does not change the current `RequiredMarginPort`, Risk Domain,
settlement compatibility, or production code. The later implementation Story
must evolve the application request/resolution boundary to require one canonical
resolved context before margin calculation. A `brokerAccountId` is a routing
identity and must not be treated as the financial `Account` identity.

The mandatory future resolution context contains:

```text
financialAccountId       = canonical PAPER financial authority
brokerAccountId          = optional execution-routing correlation only
policyId + policyVersion = immutable assigned policy
contractId + contractVersion
exposureSnapshotId + exposureVersion
referencePriceFact
reportingCurrency
valuation/FX snapshot context
decisionBoundary (T0 or T1) and requestedAt
```

The future `RequiredMarginPort.Request` or a dedicated application adapter must
carry or reference this complete context. The current broker-keyed request is
not an acceptable final PAPER request shape, and the implementation must reject
any resolution that cannot prove the financial-account-to-routing-account
binding.

---

## 4. Authority Model

```text
PAPER account local state
  + canonical Market Data contract/marks/FX
  + assigned immutable MarginPolicy
      -> Trading Core local proposed-order margin fact
      -> RequiredMarginPort.Fact
      -> normalized monetary Risk input

LIVE provider/account authority
      -> Broker Service provider-neutral required-margin fact
      -> RequiredMarginPort.Fact
      -> normalized monetary Risk input
```

The authority invariants are:

```text
PAPER_MARGIN_AUTHORITY = LOCAL_VERSIONED_POLICY
LIVE_MARGIN_AUTHORITY = PROVIDER
PAPER_RUNTIME_BROKER_MARGIN_DEPENDENCY = NO
RISK_DOMAIN_PROVIDER_NEUTRAL = YES
REQUIRED_MARGIN_FACT = MONETARY_AND_PROVENANCED
```

Market Data remains authoritative for canonical instrument metadata, market
marks, and directed FX valuation facts. Trading Core owns PAPER account state,
policy assignment, exposure reconstruction, calculation orchestration, and
provenance assembly. Risk Domain interprets the normalized monetary fact and
does not calculate margin.

---

## 5. Domain Boundaries

### 5.1 InstrumentFinancialContract

`InstrumentFinancialContract` describes the instrument:

- canonical instrument identity and product type;
- base and quote currencies;
- quantity unit;
- positive contract multiplier;
- price and quantity precision;
- minimum quantity and increment;
- contract provenance.

It answers **what the instrument is**. It must not contain account-specific
leverage, margin rate, exposure offsets, hedging rules, account tiers, or policy
version.

### 5.2 MarginPolicy

`MarginPolicy` describes how an account supplies collateral for an instrument
under a PAPER simulation. It answers **how this account's simulation requires
collateral**.

The future provider-neutral concept must contain, directly or through immutable
referenced rules:

- policy identity and immutable version;
- source category and source reference;
- effective timestamp and configuration timestamp;
- supported product and instrument scope;
- canonical margin-rate rule;
- exposure model and supported netting behavior;
- monetary precision and rounding policy;
- policy provenance.

The implementation may choose the exact class decomposition, but it must not
merge account margin policy into `InstrumentFinancialContract` or FTMO challenge
rules.

### 5.3 Challenge policy

FTMO or other challenge rules remain separate policy domains:

- daily loss;
- maximum loss;
- profit target;
- challenge duration and phase rules.

A PAPER margin policy may emulate an account's collateral economics without
becoming a challenge-policy object.

---

## 6. PAPER Simulation Semantics

A PAPER account simulates the economic rules of its assigned local
`MarginPolicy`, not an implicit broker account and not a live provider endpoint.

Provider/reference templates may be imported or selected during configuration,
but the normalized policy snapshot becomes the simulation authority. Subsequent
runtime calculations use the local snapshot and current local/Market Data facts.

The minimum V1 simulation model is:

```text
explicit policy assignment
static margin rate
linear contract notional
single-account reporting currency
per-instrument netting
no portfolio offsets
no cross-instrument collateral optimization
```

Unsupported semantics are unavailable rather than approximated.

---

## 7. Proposed-Order Required Margin

`RequiredMarginPort.Fact` represents:

> The incremental collateral, in a monetary currency, required to accept the
> proposed order at the decision boundary under the applicable authority model.

It is not:

- current account used margin;
- current account free margin;
- current position margin;
- total margin after execution;
- notional exposure;
- leverage;
- a risk budget;
- execution authorization.

For V1 netting semantics, let:

```text
existingSignedQuantity = current local signed exposure for the instrument
orderSignedQuantity    = signed proposed order quantity
resultingSignedQuantity = existingSignedQuantity + orderSignedQuantity
margin(q)               = abs(q) * referencePrice * contractMultiplier * marginRate
requiredMargin         = max(margin(resultingSignedQuantity)
                             - margin(existingSignedQuantity), 0)
```

The calculation is performed in quote currency before explicit conversion to
account reporting currency. The formula is a semantic description of the
approved V1 model, not an authorization to implement it in this Story.

Consequences for supported netting transitions:

- no existing position plus an opening order: required margin for the new
  exposure;
- same-direction increase: incremental margin for the increased exposure;
- partial reduction: zero new required margin because collateral is released,
  not required;
- full close: zero new required margin;
- direction reversal: margin for the residual exposure in the new direction,
  less any existing collateral released, floored at zero.

The current exposure and resulting exposure must be based on the same canonical
instrument and contract. Ambiguous, stale, conflicting, or unavailable exposure
fails closed.

---

## 8. Margin Policy Scope and Precedence

The policy assignment belongs to the PAPER financial `Account`, or to an
explicitly owned account-level simulation profile when that boundary is
introduced. `BrokerAccount` remains the connection and execution-mode identity;
it is not the source of PAPER financial truth merely because it identifies a
provider registration.

The canonical future relationship is:

```text
financial Account.id
    -> exactly one applicable PAPER simulation assignment
    -> immutable MarginPolicy.id/version
    -> optional BrokerAccount.id for execution routing only
```

ADR-043 records that the repository currently lacks a durable, reliable
`Account`-to-`BrokerAccount` relation and that T1 has identity confusion. This
ADR does not pretend that gap is implemented. The later implementation Story
must establish the relation and reject a margin request when the financial
account, routing account, or ownership binding is ambiguous.

An immutable policy contains applicable rules with this precedence:

```text
exact instrument rule
    > product-type rule
    > explicitly configured account default
```

An omitted rule is not an implicit zero or universal provider rule. If the
precedence chain does not resolve exactly one complete applicable rule, margin is
unavailable. A future implementation may initially support only exact CFD
instrument rules and reject broader scopes until their semantics are implemented.

No global mutable default may silently alter an existing PAPER account's
economics.

---

## 9. Canonical Margin Representation

V1 uses a **margin rate**, not leverage, as the canonical policy representation.

```text
marginRate = collateral required / quote-currency notional
```

The rate is an exact positive decimal ratio:

```text
0 < marginRate <= 1
```

Leverage may be displayed as an explanatory derived ratio when useful, but it is
not stored as the canonical semantic input. `1:100` and `1%` must not be accepted
as independently competing policy representations.

The future implementation must validate:

- non-null, finite decimal value;
- strictly positive value;
- value no greater than one for this V1 model;
- explicit scale/precision limits defined by the policy contract;
- no implicit conversion from provider leverage fields.

V1 fixes the precision contract as follows:

- `marginRate` is represented with no more than 8 fractional decimal places;
- a rate with more than 8 fractional places is rejected rather than silently
  rounded;
- intermediate multiplication and FX composition use exact `BigDecimal`
  arithmetic without an intermediate scale or implicit `MathContext`;
- conversion to reporting currency occurs before final monetary rounding;
- the applicable currency metadata must provide the reporting-currency monetary
  scale; a missing or conflicting scale is unavailable;
- final positive collateral is rounded toward positive infinity (`CEILING`) to
  that scale, so rounding never understates required collateral;
- negative or zero rates, invalid scale, and non-finite/overflowing results are
  unavailable.

No provider or JVM default rounding policy is permitted.

---

## 10. Existing Exposure, Netting, and Hedging

V1 supports a single signed net position per account and canonical instrument.
Existing exposure is therefore required for incremental margin semantics.

```text
NETTING = SUPPORTED_V1
HEDGING = UNSUPPORTED_V1
```

The implementation must reject or return unavailable for:

- multiple independently marginable hedged positions;
- provider-specific hedge offsets;
- ambiguous position identity or direction;
- unsupported partial-fill or pending-order exposure semantics;
- portfolio-level collateral offsets;
- cross-instrument collateral optimization.

This restriction is intentional. Trading OS currently does not establish a
provider-neutral PAPER hedging model, and inventing one would make the margin
policy non-reproducible.

The current PAPER settlement representation is a list of persisted `Trade`
records with exact target-trade exits; it is not itself a canonical net-position
boundary. The future implementation must introduce an explicit read-side
exposure snapshot for margin that:

- loads all open CFD trades for the canonical financial account and instrument;
- validates signed quantity, contract identity/version, status, and timestamps;
- aggregates their signed quantities only under the V1 netting policy;
- preserves the existing target-trade settlement semantics; and
- returns `AMBIGUOUS_EXPOSURE` when any required trade is missing, conflicting,
  hedged, or not representable by V1.

Until that snapshot boundary exists, PAPER required margin remains unavailable;
the ADR does not reinterpret the existing trade list as an implemented net
position.

---

## 11. Static, Tiered, and Dynamic Margin

```text
STATIC_MARGIN = SUPPORTED_V1
TIERED_MARGIN = UNSUPPORTED_V1
DYNAMIC_MARGIN = UNSUPPORTED_V1
```

V1 supports one static rate per resolved policy rule. Tier thresholds, dynamic
leverage schedules, time-varying provider rates, liquidation tiers, and account
equity-dependent rates require a later ADR or an explicit extension of this
policy contract. They fail closed until then.

---

## 12. Currency and FX Semantics

The calculation currency is the instrument quote currency. The output currency
consumed by Risk is the PAPER account reporting currency (`Account.baseCurrency`).

```text
quote-currency required margin
        -> explicit directed Market Data FX fact
        -> account reporting currency required margin
```

Identity conversion is valid only when the currencies are equal. Otherwise:

- a complete directed conversion path is required;
- each leg is oriented `fromCurrency -> toCurrency` and its rate means units of
  target currency per one source currency;
- multi-leg paths are acyclic, ordered, and composed by multiplication;
- every leg must have positive rate, effective time, capture time, and
  provenance;
- the composed rate must match the valuation boundary's reported
  quote-to-reporting rate;
- Market Data must return exactly one canonical ordered path for the requested
  currency pair; zero paths and multiple valid paths are both unavailable;
- every leg's `effectiveAt` and `capturedAt` must be no later than the decision
  boundary, and the maximum age is the explicit `maxObservationAge` supplied by
  the valuation snapshot; missing, invalid, or negative age metadata is
  unavailable;
- the path's newest effective/capture timestamp is the path timestamp used for
  freshness and provenance;
- missing, stale, ambiguous, or reverse-direction FX makes margin unavailable.

No implicit `rate = 1` conversion is permitted for different currencies.

---

## 13. Provenance and Lifecycle

Every PAPER required-margin fact must be explainable through at least:

- policy identity;
- immutable policy version;
- policy source category and source reference;
- policy effective timestamp;
- instrument and product identity;
- financial-contract identity/version;
- exposure source/version and observed time;
- reference-price source/version and observed time;
- calculation timestamp;
- calculation currency and rounding policy;
- FX source/provenance, version, effective time, and capture time when used.

The policy assigned to a PAPER account is immutable. A policy update is an
append-only assignment of a new policy/version and does not mutate the meaning
of historical risk evidence, trade plans, or decisions. Policy versions are
never physically deleted while referenced by evidence.

At T0, the resolver atomically reads the financial-account binding and captures
the exact policy identity/version in the risk evidence. Assignment updates use
optimistic versioning; a concurrent update causes the T0 resolution to retry or
fail closed rather than mix assignment and policy versions. A new default does
not silently rewrite an in-flight decision.

At T1, the resolver looks up the exact immutable T0 policy version, not the
current account default. Supersession alone does not invalidate that immutable
version, but explicit revocation, expiry before the decision boundary, missing
version data, or failed assignment/ownership verification causes T1 to fail
closed. A replacement policy requires a new decision boundary.

---

## 14. T0 and T1

### T0

T0 resolves:

```text
applicable immutable policy version
+ canonical instrument financial contract
+ proposed order signed quantity and reference price
+ current local net exposure
+ current account reporting currency
+ fresh Market Data FX facts when required
    -> proposed incremental required margin
```

The policy identity/version and all source provenance are persisted with the risk
evidence through the existing application evidence boundary.

### T1

T1 re-evaluates with:

```text
the same policy identity/version captured at T0
+ fresh execution-time contract/exposure/price/account facts
+ fresh Market Data FX facts when required
```

T1 must not silently reuse the T0 amount. If the T0 policy version is no longer
available, no longer applicable, or has been superseded without an immutable
version lookup, T1 fails closed. A newly selected policy is not substituted into
an existing plan without an explicit new decision boundary.

Required margin validity does not authorize execution. Human authorization and
all existing execution gates remain separate.

---

## 15. Failure Semantics

The following produce `UNAVAILABLE` / `CONTEXT_UNAVAILABLE` and prevent risk
approval:

```text
MISSING_POLICY          -> unavailable
UNSUPPORTED_POLICY      -> unavailable
MISSING_CONTRACT        -> unavailable
MISSING_MARK            -> unavailable
MISSING_FX              -> unavailable when conversion is required
AMBIGUOUS_EXPOSURE      -> unavailable
STALE_FACT              -> unavailable
CONFLICTING_PROVENANCE  -> unavailable
INVALID_ROUNDING        -> unavailable
```

The implementation must not fall back to zero, current used margin, position
margin, leverage inference, or a provider call for PAPER authorization.

---

## 16. Existing PAPER Provider Preview

The current `BrokerRequiredMarginClient -> Broker Service -> provider preview`
path is not PAPER financial authority. Until the replacement implementation is
deployed, a PAPER resolution through this path must be treated as
`PAPER_PREVIEW_NON_AUTHORITATIVE` and unavailable for PAPER authorization. The
existing non-empty fact must not be labelled authoritative merely because it
passes amount, freshness, and provenance validation.

The later implementation Story shall:

1. remove the PAPER preview from the required-margin authorization path;
2. preserve it, if useful, only as configuration-import or diagnostic evidence;
3. label any imported value with source category and verification status;
4. prevent a provider preview from silently becoming a local policy or changing
   an assigned policy version;
5. retain the LIVE provider preview/fact path where its semantics are complete
   and authoritative.

No runtime PAPER margin decision may depend on Broker Service availability.

---

## 17. Risk and Mode Boundaries

`RequiredMarginPort.Fact` remains the application-facing boundary. Risk Domain
continues receiving normalized monetary required margin and does not receive or
calculate margin rate, leverage, provider metadata, hedging rules, or policy
mechanics.

The later implementation must also preserve diagnostic failure causes at the
Trading Core application boundary. The current `Optional<Fact>` compatibility
shape may remain the final risk-facing projection, but resolution must retain a
deterministic reason such as `MISSING_POLICY`, `AMBIGUOUS_EXPOSURE`,
`MISSING_FX`, or `INVALID_ROUNDING` in application evidence/logging. Collapsing
all causes to `Optional.empty()` at the last boundary must not erase the reason
needed for audit and operator explanation.

```text
PAPER -> local MarginPolicy calculation in Trading Core
LIVE  -> provider-authoritative fact through Broker Service
RISK  -> normalized monetary fact only
```

`PAPER_FINANCIAL_AUTHORITY = TRADING_CORE` and
`LIVE_FINANCIAL_AUTHORITY = PROVIDER` remain unchanged.

---

## 18. Configuration and Provider Templates

A PAPER account must receive its policy through explicit configuration or
assignment. A missing assignment makes proposed-order margin unavailable.

Future templates may be labelled:

```text
TRADING_OS_DEFINED
USER_CONFIGURED
PROVIDER_DERIVED
PROVIDER_VERIFIED
```

Templates are not created by this ADR. A label such as `FTMO-like` must not imply
FTMO certification or import FTMO challenge rules. Provider-derived templates must
be normalized into the provider-neutral policy before assignment and must retain
their origin and verification status.

---

## 19. Acceptance Examples

The following is a **HYPOTHETICAL POLICY EXAMPLE**, not current Trading OS truth.

```text
Instrument: EUR/USD CFD
Signed order quantity: +1000 EUR underlying units
Reference price: 1.10000 USD/EUR
Contract multiplier: 1
Quote notional: 1100 USD
Approved static margin rate: 1%
```

Then:

```text
required margin = 1100 USD * 1% = 11 USD
```

This result is valid only after the policy, contract, exposure semantics,
rounding, reporting currency, and required provenance are authoritative.

Without a policy:

```text
policy missing
    -> required margin unavailable
    -> T0/T1 risk cannot approve
    -> execution remains blocked
```

---

## 20. Rejected Alternatives

### Alternative 1 - `notional / leverage` without a policy contract

Rejected. Leverage does not specify contract multiplier, exposure interaction,
netting/hedging, currency, rounding, effective time, or provenance.

### Alternative 2 - use current `usedMargin`

Rejected. Used margin describes current account collateral, not incremental
collateral for a proposed order.

### Alternative 3 - use current position margin

Rejected. Position margin describes existing exposure and cannot define the
incremental result of an order, reduction, or reversal.

### Alternative 4 - call Broker Service for every PAPER decision

Rejected. It violates the ADR-082 PAPER authority boundary, couples simulation to
provider availability, and does not guarantee complete provider semantics.

### Alternative 5 - skip required margin for PAPER CFD

Rejected. It would weaken deterministic risk and make the margin gate optional.

### Alternative 6 - hardcode cTrader/FTMO semantics into Trading Core

Rejected. It leaks provider/challenge semantics into a provider-neutral domain and
would not represent other margin-based products.

### Alternative 7 - explicit local provider-neutral versioned policy

Accepted as the foundation of this decision. It preserves local authority,
reproducibility, provenance, and future reference-derived templates while
remaining implementable as a bounded V1.

---

## 21. Consequences

### Positive

- PAPER margin has one explicit local authority.
- Historical decisions can be reconstructed from immutable policy versions.
- Provider/reference information can be used without coupling runtime simulation
  to Broker Service.
- `InstrumentFinancialContract` remains separate from account collateral policy.
- Risk Domain remains provider-neutral and receives only normalized monetary facts.
- Unsupported exposure, hedge, tiered, dynamic, and FX cases fail closed.
- LIVE provider authority remains unchanged.

### Costs and Limitations

- A policy assignment and versioned persistence boundary must be implemented.
- Existing `RequiredMarginPort.Fact` evidence must retain policy provenance through
  the application evidence boundary.
- V1 does not support hedging, tiered/dynamic rates, portfolio offsets, or
  cross-instrument collateral optimization.
- Configuration/import validation is required before a policy can be assigned.
- `CASH + CFD` remains blocked until implementation and validation are complete.

---

## 22. Implementation Sequence

This ADR authorizes no implementation by itself. A later Engineering Story should
proceed in this order:

1. Introduce a provider-neutral immutable `MarginPolicy` domain/application
   model, including policy provenance, static margin rules, netting scope,
   rounding, and effective version.
2. Define and persist explicit policy assignment to the PAPER account or owned
   simulation profile, with immutable historical lookup.
3. Implement deterministic V1 netting-only incremental required-margin
   calculation using the canonical instrument contract and static margin rate.
4. Implement explicit quote-to-reporting currency conversion and provenance
   validation through Market Data valuation facts.
5. Route PAPER `RequiredMarginPort` resolution to the local policy and preserve
   LIVE provider-authoritative resolution.
6. Remove the PAPER provider preview from the authorization path; retain it only
   as explicitly labelled import/diagnostic evidence if needed.
7. Integrate policy identity/version and calculated evidence at T0/T1, including
   policy-change and unsupported-case fail-closed tests.
8. Add representative PAPER CFD regression and restart/reproducibility evidence.
9. Only after implementation and validation, create a separate compatibility
   authorization Story to reconsider ADR-082 Phase 8.

---

## 23. Architectural Invariants

```text
PAPER_MARGIN_AUTHORITY = LOCAL_VERSIONED_POLICY
LIVE_MARGIN_AUTHORITY = PROVIDER
PAPER_MARGIN_POLICY = PROVIDER_NEUTRAL
PAPER_RUNTIME_BROKER_MARGIN_DEPENDENCY_FOR_AUTHORIZATION = NO
USED_MARGIN != PROPOSED_REQUIRED_MARGIN
POSITION_MARGIN != PROPOSED_REQUIRED_MARGIN
UNCONFIGURED_MARGIN_POLICY = UNAVAILABLE
UNSUPPORTED_MARGIN_SEMANTICS = UNAVAILABLE
POLICY_PROVENANCE = REQUIRED
POLICY_VERSIONING = REQUIRED
T1_REVALIDATION = REQUIRED
INSTRUMENT_FINANCIAL_CONTRACT_SEPARATE = YES
RISK_DOMAIN_MARGIN_FORMULA = NONE
FTMO_CHALLENGE_RULES_SEPARATE = YES
CASH_PLUS_CFD = STILL_REJECTED
INVENTORY_PLUS_CFD = STILL_REJECTED
ADR_082_PHASE_8_RETRIED = NO
CTRADER_SAFETY_EXECUTION_ENABLED = false
PRODUCTION_CODE_CHANGED = NO
```

## 24. Approval Boundary

This ADR is now an architectural authority following explicit human approval on
2026-10-31. It must not be treated as implemented until a later Story completes
the implementation sequence and validation.
