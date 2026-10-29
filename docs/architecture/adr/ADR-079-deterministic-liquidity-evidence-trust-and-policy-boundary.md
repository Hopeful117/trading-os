# ADR-079 - Deterministic Liquidity Evidence Trust and Policy Boundary

| | |
|---|---|
| **Status** | Review |
| **Date** | 2026-10-30 |
| **Decision Type** | Market Intelligence and Market Data Evidence Architecture |
| **Related ADRs** | ADR-006, ADR-020, ADR-058, ADR-065, ADR-069, ADR-076, ADR-077 |
| **Related Stories** | Story 0166, Story 0167 |

---

## 1. Context

Trading OS currently observes several Tradeability inputs:

- normalized quotes and spread;
- order-book replay and visible depth measurements;
- historical OHLC range;
- temporally bounded News and economic-event context.

The current domain does not have an approved deterministic spread suitability
threshold, liquidity suitability threshold, or range suitability policy. A valid
two-sided order book therefore proves only:

```text
liquidity evidence was observed
```

It does not prove:

```text
liquidity is suitable
```

The distinction is required before cTrader Level II acquisition can become a
decision input. cTrader supports incremental depth events through
`ProtoOASubscribeDepthQuotesReq`, `ProtoOADepthEvent`, and
`ProtoOAUnsubscribeDepthQuotesReq`, but its documented depth events do not
provide a provider event timestamp, provider event sequence, checksum, or a
guaranteed initial complete snapshot.

Kraken has stronger existing evidence semantics: provider sequence and timestamp
semantics, checksum capability, durable persistence, and contiguous replay
validation. Those properties improve chronology and replay trust; they do not
automatically make Kraken liquidity suitable.

`LIVE_CURRENT` Tradeability is currently unsupported. Required missing or
unknown Tradeability evidence fails closed in Market Thesis. This ADR does not
relax that behavior merely to remove `NO_TRADE`, `DEGRADED`, or
`LIQUIDITY_UNAVAILABLE` outcomes.

The architecture must distinguish:

```text
unknown liquidity
!= poor liquidity
!= suitable liquidity
```

It must also permit deterministic local replay without overstating what the
provider evidence proves.

## 2. Problem

Spread, visible depth, imbalance, activity, volume, and trade count are related
market observations but are not interchangeable liquidity conclusions:

```text
spread != liquidity
depth quantity != executable quantity
```

A tight spread can support quote validity and spread observation. It cannot
silently satisfy a Level II liquidity requirement. Likewise, a normalized
provider-neutral order-book structure must not erase whether chronology came
from the provider or was reconstructed locally by Trading OS.

The system needs one provider-neutral contract that preserves evidence
provenance, expresses multidimensional trust, defines cTrader lifecycle
boundaries, and prevents unapproved measurements from becoming suitability
decisions.

## 3. Decision

Trading OS establishes a provider-neutral **Liquidity Evidence Trust and Policy
Boundary**.

The boundary separates four concerns:

1. observed order-book evidence;
2. evidence trust and replay semantics;
3. deterministic liquidity measurements;
4. versioned suitability policy evaluation.

An observation remains an observation until an approved policy evaluates it.
Policy activation requires explicit provenance for every active threshold:

```text
SOURCE
CALIBRATION_METHOD
SCOPE
POLICY_VERSION
```

Without that provenance, measurements remain observed but unclassified. No
arbitrary universal liquidity threshold is authoritative.

### 3.1 Spread and liquidity are separate

Spread suitability and liquidity suitability are independent dimensions.

Top-of-book evidence may support:

- quote validity;
- midpoint and spread observation;
- an explicitly configured spread policy.

It cannot satisfy a Level II liquidity requirement. A liquidity-qualified
Candidate requires the Level II evidence required by the active Candidate
policy.

### 3.2 Basic and advanced Tradeability

Trading OS recognizes a conceptual distinction without requiring classes with
these exact names.

**Basic Tradeability** covers the minimum current-context predicates:

- market availability or executability state;
- a fresh valid quote;
- an approved spread policy, when configured;
- required evidence validity and freshness;
- basic liquidity evidence when the active policy requires it;
- blocking News conditions.

**Advanced Tradeability** covers later calibrated interpretations such as:

- relative depth;
- imbalance;
- concentration;
- depth deterioration;
- trade-size-aware liquidity;
- market impact and slippage semantics.

Advanced evidence may be unavailable without invalidating unrelated observations,
but it cannot be silently promoted to a required Candidate predicate.

### 3.3 Level II Candidate rule

```text
LEVEL_II_REQUIRED_FOR_V1_CANDIDATE = CONDITIONAL
```

If the active Candidate policy requires liquidity suitability, ready, fresh,
trustworthy, and policy-classified Level II evidence is mandatory. Top-of-book
alone cannot satisfy a liquidity-qualified Candidate.

A future Candidate policy may explicitly omit Level II. Such a Candidate must
not be represented as liquidity-qualified.

## 4. Evidence Trust Contract

Evidence trust is composable. It must not be reduced to a single
`TRUSTED`/`UNTRUSTED` flag.

The conceptual trust properties are:

- source authenticity;
- provider-time authority;
- local observation time;
- provider sequence authority;
- local ordering;
- continuity confidence;
- snapshot completeness;
- local persistence;
- replayability;
- freshness;
- analysis cutoff semantics;
- connection generation.

The canonical evidence contract must preserve, where available:

- provider and provider symbol;
- canonical instrument identity;
- bid and ask levels, price, quantity, and side;
- local observation and persistence times;
- provider time;
- provider sequence;
- local sequence or ordering;
- connection generation;
- initialization and snapshot-completeness status;
- continuity status;
- freshness;
- analysis cutoff;
- normalization version;
- evidence fingerprint.

Provider-specific metadata remains provenance. Provider neutrality does not mean
provider equivalence.

### 4.1 Kraken profile

Kraken evidence may provide:

- provider sequence semantics;
- provider timestamp semantics;
- checksum validation;
- durable persistence;
- contiguous replay validation;
- strong provider chronology.

This profile supports strong provider-chronology replay when the applicable
sequence is valid, complete, persisted, and eligible at the cutoff. It does not
declare the resulting liquidity suitable without an approved liquidity policy.

### 4.2 cTrader profile

cTrader depth evidence is authenticated provider evidence reconstructed from
incremental events. The documented event contract does not prove:

- provider event timestamp;
- provider event sequence;
- checksum integrity;
- a guaranteed initial complete snapshot.

The permitted cTrader replay claim is:

> Trading OS can deterministically replay the locally persisted book state it
> observed and knew by local cutoff `T` for a specific connection generation.

The system must not claim:

> Trading OS can reconstruct the complete provider book at provider timestamp
> `T` with Kraken-equivalent continuity guarantees.

## 5. Provider Neutrality Invariants

A provider-neutral order-book model may normalize common semantics while
preserving trust and provenance. The following equivalences are forbidden:

```text
cTrader local order == Kraken provider sequence
cTrader local cutoff == provider timestamp
visible broker depth == global market liquidity
valid reconstructed book == guaranteed complete provider snapshot
spread == liquidity
missing depth == zero liquidity
```

Both strong provider-chronology replay and local-observation replay may be
deterministic. They are not semantically interchangeable.

## 6. cTrader Book Lifecycle

A reconstructed cTrader book starts unusable:

```text
UNINITIALIZED
    -> RECONSTRUCTING / OBSERVING
    -> READY only when required invariants are proven
```

The first received depth event must not automatically establish readiness.

Readiness requires a proven local reconstruction contract, including, as
applicable:

- fixed provider, account/source binding, and instrument identity;
- sufficient initialization evidence;
- valid two-sided normalized state;
- local persistence success;
- known connection generation;
- no known continuity or reconstruction gap;
- freshness within the applicable policy.

If sufficient initialization cannot be established, the evidence remains
unavailable or unknown and is not decision-authoritative.

### 6.1 Reconnect

The required lifecycle is:

```text
connection lost
    -> reconstructed book invalidated
    -> continuity becomes uncertain
    -> resubscribe
    -> reconstruct
    -> re-establish readiness
```

A pre-disconnect book must not silently remain authoritative after reconnect.
The prior state may remain inspectable or replayable only under its own valid
evidence boundary; it must not be carried into the new connection generation.

### 6.2 Stale evidence

Stale liquidity evidence remains historical evidence. It may remain inspectable
or replayable where the replay contract permits it, but it cannot satisfy a
fresh Candidate liquidity predicate.

Existing Market Thesis stale-state semantics remain governed by the Thesis
composition policy. This ADR only establishes that stale evidence is not fresh
evidence.

## 7. Replay and Cutoff Semantics

Strong provider chronology may use provider chronology together with
`knownAt <= evaluationCutoff` and contiguous sequence validation.

Local-observation replay uses:

```text
evidence known and persisted by Trading OS by local cutoff T
```

The evidence must preserve which temporal authority applies. A local cutoff is
not silently represented as provider time.

Replay must remain fail-closed for incomplete, ambiguous, invalidated, or
otherwise unverifiable evidence. Replay acceleration must preserve the existing
ADR-065 and ADR-069 sequence, provenance, cutoff, and equivalence boundaries.

## 8. Liquidity Policy Governance

The V1 policy hierarchy is:

```text
global evidence/trust contract
    -> instrument/provider policy binding
    -> optional session calibration profile
```

Account rules, challenge rules, position sizing, exposure, and financial
authorization do not belong in liquidity policy. They remain Risk and TradePlan
responsibilities.

Current evidence is insufficient to approve universal cross-instrument numeric
thresholds. Absolute depth cannot be treated as comparable across EUR/USD,
GBP/USD, indices, commodities, and crypto without an explicit scope and
calibration basis.

The policy lifecycle is:

```text
CAPTURE_ONLY
    -> OBSERVE
    -> CALIBRATED_POLICY
    -> DECISION_ACTIVE
```

The following semantic distinction is mandatory:

| State | Meaning | Candidate effect when required |
|---|---|---|
| `UNAVAILABLE` / `UNKNOWN` | Sufficient evidence does not exist or trust is unresolved | Cannot satisfy the predicate; fail closed |
| Observed but unclassified | Evidence exists, but no approved suitability policy can judge it | Cannot satisfy the predicate |
| `UNSUITABLE` | Approved policy evaluated valid evidence and failed | Blocks Candidate |
| `SUITABLE` | Approved policy evaluated valid evidence and passed | May satisfy the predicate |

Observed but unclassified evidence must use repository-compatible terminology,
such as `POLICY_UNDEFINED` or equivalent. It must not be converted into either
poor or suitable liquidity.

## 9. Capture-Only Boundary

Capture-only cTrader depth may:

- collect provider events;
- normalize evidence;
- persist evidence and provenance;
- validate initialization and reconnect lifecycle;
- reconstruct local state;
- replay locally persisted evidence;
- measure distributions for later calibration.

Capture-only cTrader depth must not:

- promote a Candidate;
- classify liquidity suitable or unsuitable without an approved policy;
- activate `LIVE_CURRENT` Tradeability authority;
- treat the first event as a complete book;
- preserve a trusted book across reconnect;
- treat local order as provider sequence;
- treat local time as provider time;
- fabricate missing liquidity.

## 10. Candidate and Human Authority Invariants

Unknown or unclassified required liquidity cannot satisfy Candidate promotion.
This is a failed proof of a required predicate, not a claim that the market is
poorly liquid.

Missing required Tradeability evidence continues to fail closed in Market Thesis.
`LIVE_CURRENT` Tradeability remains unsupported until a provider-neutral live
order-book source and its freshness and provenance contract are approved.

Tradeability and Market Thesis remain decision support. Human discretion may
investigate a `WATCH` or `NO_TRADE` result, but human discretion does not permit
the system to relabel missing or unclassified evidence as suitable. Risk remains
the authority for deterministic account and financial constraints.

## 11. Architectural Ownership

### Market Data

Market Data owns:

- provider transport and source binding;
- authentication and subscription lifecycle;
- book reconstruction and initialization;
- reconnect invalidation;
- normalization and persistence;
- freshness metadata and evidence provenance;
- provider-specific chronology and replay state.

### Market Intelligence

Market Intelligence owns:

- deterministic liquidity measurements;
- trust-aware interpretation;
- versioned liquidity policy evaluation;
- Tradeability evidence composition.

### Market Thesis

Market Thesis owns:

- composition of supporting, weakening, blocking, and missing evidence;
- deterministic `NO_TRADE`, `WATCH`, and `CANDIDATE` conclusions;
- immutable evidence, policy, and cutoff provenance.

### Strategy and Opportunity

Strategy consumes authoritative Thesis and evidence. Strategy must not redefine
global liquidity semantics. Strategy/Opportunity gating remains a separate P0-C
concern; this ADR only provides the stable liquidity result that a future gate
can consume.

### Risk

Risk owns account risk, exposure, sizing, challenge rules, and financial
authorization. Risk does not become the owner of market-liquidity suitability.

## 12. Alternatives Rejected

### Treat all provider books as equivalent

Rejected because cTrader local reconstruction lacks the provider chronology
guarantees available from Kraken.

### Use spread as a liquidity proxy

Rejected because spread measures quote cost, not visible depth or executable
quantity.

### Use absolute depth thresholds across all instruments

Rejected because quantity units, price scales, sessions, providers, and market
structures are not comparable without scope and calibration.

### Treat missing depth as zero liquidity

Rejected because absence of evidence is not evidence of poor liquidity.

### Enable `LIVE_CURRENT` before policy approval

Rejected because it would make unsupported evidence authoritative and weaken the
existing fail-closed behavior.

### Introduce an opaque universal liquidity score

Rejected because a score would hide missingness, trust differences, calibration,
and contradiction semantics.

### Put liquidity policy inside account Risk

Rejected because market suitability and account financial authorization have
different owners and inputs.

### Require Kraken-level chronology from every provider

Rejected because it would unnecessarily exclude truthful local-observation
evidence. Provider capabilities should be explicit rather than erased.

### Make cTrader Level II immediately Candidate-authoritative

Rejected because initialization completeness, reconnect continuity, and policy
calibration are not yet proven.

## 13. Consequences

### Positive

- Provider-specific evidence remains truthful.
- cTrader capture can proceed without becoming Candidate authority.
- Future calibration can use persisted observations.
- Deterministic decisions remain auditable and replayable.
- Provider-neutral domain contracts preserve provenance.
- Required unknown or unclassified liquidity remains fail-closed.
- P0-C receives a stable future liquidity result without owning its semantics.

### Trade-offs

- Evidence requires more trust and provenance metadata.
- cTrader requires explicit initialization and reconnect lifecycle states.
- Capture and replay create storage and operational costs.
- Calibration must precede decision authority.
- Provider-specific trust profiles require additional interpretation logic.
- Conservative `NO_TRADE` outcomes remain possible while evidence or policy is
  unavailable.

## 14. Implementation Boundary

The next implementation slice is:

```text
Capture-Only cTrader Depth Evidence with Trust-Aware Reconstruction and Replay
```

It will be separately scoped. This ADR authorizes neither that implementation nor
any cTrader execution behavior.

The future capture-only implementation must preserve all invariants in Sections
6, 7, 8, and 9 before any later policy activation is considered.

This ADR does not change Strategy, Market Thesis production behavior, Risk,
TradePlan, execution, broker behavior, runtime configuration, or `LIVE_CURRENT`
Tradeability.

## 15. Status and Required Human Decision

This ADR is in `Review`. It requires human architecture approval before the
capture-only implementation Story is started.
