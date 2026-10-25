# Implementation Plan

## Overview

The implementation would add a deterministic Tradeability assessment to the
existing Market Thesis composition flow. It would reuse existing normalized
market snapshot, spread, order-book/liquidity, OHLC, and News context contracts,
then expose the result as provenance-bearing Thesis evidence and structured
conditions.

The plan preserves ADR-076 ownership: Market Data and News Service remain
authorities for source facts, Market Intelligence owns composition, and Risk,
TradePlan, execution, broker, and human authorization remain unchanged.

The plan is technically feasible, but it is not implementation-ready until the
policy decisions listed under Open Decisions are explicitly resolved.

## Planned Changes

### 1. Establish the Tradeability policy contract

Define a versioned provider-neutral Tradeability assessment and policy boundary
within `market-intelligence`. The contract must represent source availability,
freshness, cutoff compatibility, deterministic findings, severity/polarity, and
fingerprints without becoming a universal score.

This step depends on deciding the authoritative thresholds and event policy; no
thresholds may be invented during implementation.

### 2. Reuse and normalize existing context evidence

Reuse existing snapshot/spread, order-book/liquidity, OHLC range, and News
context contracts. Preserve source provenance and map context statuses into
Market Thesis availability without changing Market Data or News Service
semantics.

The implementation must identify the approved bridge from `IntelligenceContext`
to the durable `Observation` payload/evidence consumed by
`MarketThesisComposer`. It must not duplicate provider payloads or create a
second market-fact authority.

### 3. Compose Tradeability into Market Thesis

Extend the existing Thesis composition boundary so Tradeability contributes
typed supporting, weakening, blocking, or missing evidence and structured
confirmation/blocking conditions.

The state policy must ensure that required unavailable, stale, or unknown
Tradeability evidence cannot produce a fresh `CANDIDATE`, blocking conditions
produce `NO_TRADE`, and non-blocking weakening conditions produce `WATCH`.
Existing Trend Context, structural, and Momentum state semantics must remain
unchanged except for the explicitly approved composition interaction.

### 4. Preserve persistence and contract compatibility

Verify that existing immutable Thesis persistence, fingerprints, API read models,
and downstream provenance references can carry the new evidence without
provider-specific fields or changes to Risk/TradePlan/execution contracts.
Add migrations only if repository inspection during implementation proves that
the existing Thesis persistence cannot represent the approved evidence; no
migration is assumed by this plan.

### 5. Validate deterministic state transitions

Add focused tests for fresh suitable conditions, excessive spread, stale quote,
unavailable/insufficient liquidity, compatible and incompatible OHLC range,
future News facts, relevant event conditions, missing required inputs,
contradictions, deterministic replay, persistence, and state transitions.

Run the affected module validation and inspect the complete diff before review.

## Files to Modify

- `market-intelligence/.../application/thesis/MarketThesisComposer.java`: compose
  Tradeability evidence and apply the approved state policy.
- `market-intelligence/.../domain/thesis/MarketThesisScenario.java` and/or
  related Thesis condition contracts: expose structured Tradeability conditions
  if existing condition representations are insufficient.
- `market-intelligence/.../domain/thesis/MarketThesisEvidence.java` and related
  Thesis domain types only if existing provenance fields cannot represent the
  approved Tradeability snapshot.
- The existing Market Thesis observation payload/adapter boundary: carry the
  approved Tradeability evidence into the durable composition input.
- Existing persistence/web mapping files only if compatibility inspection shows
  that the new read-only evidence cannot be serialized or restored by current
  mappings.

Exact file names beyond the confirmed Thesis composer and domain contracts must
be finalized after the open policy decisions are resolved; this plan does not
invent paths for the context-to-observation bridge.

## Files to Create

- A provider-neutral Tradeability assessment/policy type in the existing
  `market-intelligence` domain package, if no existing type can represent the
  approved contract.
- Focused Tradeability capability/composer tests in the existing Market
  Intelligence test packages.

No new service, frontend artifact, provider adapter, or external dependency is
planned.

## Dependencies

- Existing Market Data snapshot, OHLC, and order-book contracts and freshness
  metadata.
- Existing Market Intelligence spread, order-book/liquidity, and OHLC range
  capabilities.
- Existing News Service normalized event and News contracts.
- Existing `Observation` and `MarketThesisObservationPayload` composition
  boundary.
- ADR-076 and the accepted Stories 0163–0165 as architectural and semantic
  constraints.

No new external library or service dependency is required.

Ordering dependencies:

- Tradeability policy decisions must precede implementation.
- The context-to-observation bridge must be identified before composition code
  is changed.
- Existing contracts and Thesis persistence compatibility must be verified
  before adding new persistence fields or migrations.

## Test Plan

Create or update focused tests covering:

- deterministic Tradeability assessment and fingerprints;
- source cutoff and future-fact exclusion;
- quote freshness and configured spread suitability;
- order-book/liquidity availability, staleness, insufficiency, and provenance;
- compatible, incompatible, abnormal, and unavailable OHLC range context;
- News/economic-event temporal filtering and approved event policy;
- supporting, weakening, blocking, and missing evidence polarity;
- required missingness preventing `CANDIDATE`;
- weakening conditions producing `WATCH`;
- blocking conditions producing `NO_TRADE`;
- immutable Thesis persistence and replay;
- no calls or semantic changes to Risk, TradePlan, Opportunity, execution,
  broker, or human authorization boundaries.

Validation commands:

```bash
cd /home/ludo/workspace/trading-os/market-intelligence && mvn test && mvn verify
cd /home/ludo/workspace/trading-os/market-data && mvn test && mvn verify
cd /home/ludo/workspace/trading-os/news-service && mvn test && mvn verify
cd /home/ludo/workspace/trading-os && git diff --check
```

These commands are validation requirements, not completed results.

## Risks

- **Threshold policy remains unresolved:** Implementing without an approved
  spread/range policy would encode an unapproved trading decision. Mitigation:
  resolve the policy before implementation; human clarification required.
- **Event semantics remain unresolved:** Treating event impact or proximity as
  blocking without an approved policy would exceed the Story. Mitigation:
  define the bounded event predicate and severity first; human clarification
  required.
- **Context-to-observation boundary is unclear:** Directly reading transient
  context from the composer could break replay and provenance. Mitigation:
  approve one durable evidence bridge before implementation; human clarification
  required.
- **Order-book status granularity is limited:** Incorrectly deriving stale or
  insufficient states could create false contradictions. Mitigation: use only
  evidence supported by timestamps and existing replay semantics.
- **State regression risk:** Adding required Tradeability evidence can change
  existing Thesis state outcomes. Mitigation: preserve existing state tests and
  add explicit transition coverage.

## Resolved Implementation Policy

The implementation uses the following bounded V1 policy:

- No universal spread, liquidity, or range suitability threshold is inferred.
  Observations remain explicit and the corresponding policy is
  `NOT_IMPLEMENTED` until an instrument-aware policy is approved.
- Cutoff-compatible snapshot, order-book, OHLC, and News context are required
  for a fresh `CANDIDATE` when Tradeability is evaluated from an approved
  historical or replay source. The current repository has no live order-book
  contract, so Tradeability is intentionally excluded from `LIVE_CURRENT`
  execution plans rather than being evaluated from an unsupported source.
  Missing or stale source context remains blocking for consideration, while an
  unavailable policy remains non-blocking and explicit.
- OHLC range is calculated only from cutoff-compatible candles; no suitability
  conclusion is inferred from the observed range.
- Applicable News events use the normalized source impact and the bounded V1
  temporal window of 30 minutes before a scheduled event and 15 minutes after
  an actual event. `HIGH` is blocking, `MEDIUM` is weakening, and `LOW` is
  informational.
- Durable artifact execution uses the authoritative capability evaluation
  cutoff and preserves artifact freshness and provenance when reconstructing
  context sections.
- `STRICT_OBSERVED` remains limited to the durable replay slices authorized by
  ADR-058. Tradeability is therefore not added to strict plans until durable
  historical snapshot, order-book, and News sources are available; using live
  provider-backed inputs in strict replay is explicitly forbidden.
- `LIVE_CURRENT` plans do not advertise Tradeability until a provider-neutral
  live order-book source is implemented and its freshness/provenance contract
  is approved.

## Validation Checklist

- [ ] Approved Tradeability policy and thresholds are versioned and documented.
- [ ] Approved News/event relevance and severity policy exists.
- [ ] Approved context-to-observation bridge preserves cutoff and provenance.
- [ ] Tradeability remains provider-neutral and deterministic.
- [ ] Missingness and freshness states remain explicit.
- [ ] Required unavailable/stale/unknown evidence cannot yield `CANDIDATE`.
- [ ] Blocking evidence cannot yield `CANDIDATE`.
- [ ] Weakening evidence yields `WATCH` according to the approved policy.
- [ ] Existing Trend, Structure, and Momentum authority is unchanged.
- [ ] Thesis persistence, fingerprints, and read-only contracts remain compatible.
- [ ] Risk, TradePlan, Opportunity, execution, broker, and human authorization
      boundaries are unchanged.
- [ ] Focused tests pass.
- [ ] Affected Maven validation passes.
- [ ] `git diff --check` passes.
- [ ] No frontend, new service, provider-specific contract, or unrelated
      refactoring is introduced.

## Recommendation

Implementation may proceed under the bounded V1 policy above. Future spread,
liquidity, and range suitability thresholds require a new versioned policy
decision and must not be inferred from this implementation.
