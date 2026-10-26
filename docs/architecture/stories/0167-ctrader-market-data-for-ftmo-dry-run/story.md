# Story 0167 - cTrader Market Data for FTMO Dry-Run

## Metadata

**ID:** `0167`
**Title:** cTrader Market Data for FTMO Dry-Run
**Status:** Draft

## Goal

Implement the minimum provider-neutral cTrader Market Data capability required
for future FTMO/cTrader PAPER dry-run analysis to use the intended execution
market instead of Kraken market data.

This Story establishes a clean, read-only market-data boundary. It does not
make Trading OS FTMO challenge ready.

## Context

Trading OS currently provides deterministic Market Intelligence over normalized
market facts, including Trend Context, Market Structure, Momentum and Market
Thesis composition. The current production Market Data coverage is Kraken
oriented, while the intended FTMO execution platform is cTrader.

The repository already contains cTrader authentication, token/account binding,
and read-only account infrastructure in Broker Service. Those boundaries must
be reused where semantically correct without exposing cTrader DTOs, credentials,
or transport details to Market Intelligence or Trading Core.

The target flow is:

```text
cTrader / Spotware
        -> provider adapter
        -> canonical Trading OS Market Data
        -> Market Intelligence
        -> Trend / Structure / Momentum / Tradeability
        -> Market Thesis
```

## Problem

The FTMO-oriented dry-run cannot currently use the intended execution market as
its authoritative analytical source. Using Kraken data would create a market
and execution mismatch for cTrader instruments, spread, freshness, session, and
instrument-constraint evidence.

## Trader Question

> Can the deterministic analysis consume authoritative, cutoff-safe and fresh
> cTrader execution-market facts without depending on cTrader provider types?

## Scope

- Add one provider-neutral cTrader Market Data V1 adapter using the existing
  Market Data provider architecture wherever possible.
- Resolve cTrader symbols to explicit canonical market/instrument identities.
- Normalize cTrader instrument metadata and authoritative constraints.
- Support historical OHLC/trendbar retrieval for the intervals required by the
  current Trend Context, Market Structure, and Momentum contracts.
- Support live bid/ask updates while preserving the spread.
- Support live bar updates only as required to maintain current OHLC context,
  with explicit forming-bar and closed-bar semantics.
- Preserve provider timestamps, received timestamps, freshness, source identity,
  canonical identity, and provider provenance.
- Expose explicit fresh, stale, unavailable, unsupported, invalid-request,
  provider-error, and authentication-error outcomes where existing contracts
  support them.
- Map authoritative cTrader session, symbol availability, and tradability facts
  without replacing them with unsupported time heuristics.
- Define deterministic connect, authenticate, subscribe, receive, disconnect,
  reconnect, resubscribe, token-refresh, and provider-error behavior.
- Prevent duplicate subscriptions and unbounded update queues using existing
  Market Data patterns.
- Keep cTrader provider DTOs and protocol errors inside the adapter boundary.
- Preserve compatibility with existing Kraken provider behavior.
- Verify that Market Intelligence can consume normalized cTrader OHLC without a
  cTrader-specific analytical path.

## Required V1 Capabilities

```text
CANONICAL INSTRUMENT RESOLUTION
INSTRUMENT METADATA
HISTORICAL OHLC
LIVE BID / ASK
LIVE CLOSED BAR UPDATES
TIMESTAMP / FRESHNESS
SESSION / TRADABILITY STATE
PROVIDER PROVENANCE
UNAVAILABLE / STALE / ERROR STATES
```

## Canonical Instrument Identity

The mapping must preserve, where supported:

- provider;
- provider symbol ID;
- provider symbol name;
- canonical market identity;
- asset class;
- base/quote or equivalent instrument semantics;
- price precision;
- quantity/volume precision;
- minimum quantity/volume;
- volume step and maximum volume where available;
- tick size;
- contract/instrument metadata required downstream.

Mappings must be explicit. Similar names must not be treated as equivalent
without authoritative evidence. The cTrader execution instrument must not be
silently mapped to a futures or reference-market instrument.

## Execution-Market Semantics

The implementation must explicitly model cTrader as the execution market. It
must not claim that cTrader CFD data represents CME futures, centralized
exchange order flow, global liquidity, or an underlying-market DOM.

Provider provenance must make this distinction recoverable for later Market
Thesis and Tradeability evidence.

## Historical OHLC

Historical trendbars must be exposed through the canonical Market Data boundary
with:

- canonical instrument identity;
- requested interval;
- open time;
- close/cutoff semantics;
- OHLC values;
- volume only when its provider semantics are explicit;
- source timestamp;
- fetch timestamp;
- provider provenance;
- deterministic ordering;
- pagination where required;
- duplicate handling;
- gap handling;
- explicit unavailable and error states.

Incomplete or current bars must not be reinterpreted as closed bars.

For an analysis cutoff `T`, no fact occurring after `T` may affect the result.
Current provider state must not be used to fill missing historical facts.

## Closed-Bar Semantics

The implementation must define exactly when a cTrader trendbar is eligible as
`CLOSED` for deterministic analysis. Forming bars must remain distinguishable
and must not enter closed-bar analysis paths.

Boundary timestamp tests are required.

## Live Quotes and Bars

Live bid/ask updates must preserve:

- bid;
- ask;
- provider timestamp;
- received timestamp;
- canonical instrument;
- provider identity;
- source reference where applicable;
- freshness.

Bid and ask must not be collapsed into a synthetic last price when downstream
logic requires spread.

Live bar updates must distinguish `FORMING` and `CLOSED` bars. Only eligible
closed bars may be consumed by deterministic closed-bar analysis.

## Freshness and Availability

Use the existing Market Data freshness semantics. Do not create a parallel
cTrader freshness model.

Provider silence, disconnection, authentication expiry, or failed refresh must
eventually become stale or unavailable rather than leaving an old quote looking
current indefinitely.

## Session and Tradability

Map only authoritative cTrader facts about:

- trading sessions;
- market open/closed state;
- symbol trading availability;
- holidays or schedules where provided.

Trading OS defines an open market as one where an order can actually be
executed now. If cTrader cannot provide a complete authoritative real-time
state, expose that limitation explicitly. Do not silently substitute a weekday
or clock heuristic when provider facts are available.

## Volume, Ticks, and DOM

- If cTrader exposes trendbar volume, preserve its exact provider semantics.
- Do not interpret cTrader volume as centralized exchange volume.
- Mark `VOLUME_ANALYTICS_ELIGIBLE = NO` when its semantics are insufficient for
  current Volume analysis, while allowing a correctly labelled raw fact.
- Tick history and live ticks are out of scope unless repository analysis proves
  they are required for the minimum dry-run. Live bid/ask remains required.
- DOM/order book is out of scope. If considered later, cTrader DOM must be
  labelled as visible broker/cTrader liquidity, not global underlying liquidity.

## Service Ownership

Preserve the existing boundary:

```text
BROKER SERVICE
= broker/account/execution connectivity

MARKET DATA SERVICE
= normalized market facts
```

The implementation must determine whether the existing cTrader transport can be
reused, whether a bounded shared transport abstraction is needed, or whether
Market Data requires its own cTrader connection. It must not duplicate a
stateful connection/runtime without evaluating reuse.

If this requires a fundamental new cross-service ownership decision, stop and
request architectural approval before implementation.

## Provider Selection

Extend existing provider routing. Do not hardcode an FTMO-specific branch.
cTrader is a platform/provider integration; FTMO is a challenge or broker
context and must remain distinct.

The account/access-token dependency must be determined from existing cTrader
binding semantics. Tokens and credentials must never appear in Market Data
responses, logs, persistence, metrics, or frontend contracts.

## Market Data API Compatibility

Prefer existing Market Data endpoints and canonical contracts. Do not create
provider-specific public endpoints such as `/api/ctrader/ohlc` unless existing
architecture explicitly requires that pattern.

The preferred conceptual contract is:

```text
market + provider -> canonical market data
```

## Tradeability and Market Intelligence Boundary

Do not tune Tradeability policies in this Story. Ensure normalized cTrader data
can later supply:

- spread evidence;
- freshness;
- session/tradability evidence;
- OHLC range context.

Trend Context, Market Structure, Momentum, and Market Thesis must consume the
same provider-neutral contracts regardless of provider. No provider-specific
Market Thesis fields are permitted.

## Out of Scope

- LIVE order execution or FTMO challenge execution;
- routing PAPER orders to cTrader;
- FTMO challenge rules or lifecycle;
- Risk or TradePlan changes;
- Market Thesis redesign;
- Tradeability threshold tuning;
- AI or LLM interpretation;
- reference/futures market data;
- NQ, ES, CME, or underlying-market mapping;
- advanced order flow;
- DOM/order book implementation;
- a new external data provider;
- frontend redesign;
- a new microservice;
- Kafka or another message broker;
- `FeatureProjectionV1` changes;
- S1 semantic changes;
- provider-specific Market Intelligence contracts;
- cTrader SDK adoption unless separately approved;
- automatic account selection;
- exposing credentials, tokens, or provider DTOs outside the adapter boundary.

## cTrader API Verification

Before implementation, verify the current official cTrader Open API contracts for:

- symbol discovery;
- symbol metadata;
- historical trendbars;
- live trendbars;
- spot bid/ask subscriptions;
- market/session state where available;
- connection, authentication, subscription, and reconnection behavior.

The implementation report must record the authoritative request, response, and
event contracts used. Remembered or inferred API names and message IDs are not
acceptable evidence.

## Provenance

Every normalized cTrader fact must remain traceable to:

- provider;
- provider instrument;
- provider event or request;
- provider timestamp where available;
- capture/received timestamp;
- canonical instrument;
- normalization version where applicable.

## Error Semantics

Provider failures must be normalized without leaking protobuf/provider
exceptions. Where supported by existing contracts, distinguish:

```text
UNAVAILABLE
STALE
UNSUPPORTED
INVALID_REQUEST
PROVIDER_ERROR
AUTHENTICATION_ERROR
```

## Architecture Decision Gate

Existing provider-neutral Market Data and cTrader Broker Service boundaries are
sufficient if this Story adds only an adapter and reuses existing connection
ownership appropriately. A new ADR is required before implementation if the
Story requires:

- moving Market Data authority to Broker Service;
- creating a shared stateful cTrader runtime across services;
- introducing a new inter-service streaming architecture;
- introducing a message broker;
- changing credential or account-binding ownership;
- adding FTMO-specific Market Data domain types;
- changing Risk, TradePlan, execution, `FeatureProjectionV1`, or S1 semantics.

## Acceptance Criteria

- [ ] Official current cTrader Open API contracts used by the adapter are
      verified and recorded.
- [ ] cTrader symbols resolve explicitly to canonical market/instrument
      identities; unknown and ambiguous mappings fail safely.
- [ ] Instrument metadata and supported provider constraints are normalized
      without exposing cTrader DTOs.
- [ ] Historical OHLC is available through the canonical Market Data boundary
      with deterministic ordering, pagination, duplicate handling, gaps,
      cutoff safety, provenance, and explicit error states.
- [ ] Required analytical intervals are derived from the existing Trend Context,
      Market Structure, and Momentum contracts rather than arbitrary hardcoding.
- [ ] Closed-bar eligibility is explicitly defined and tested at timestamp
      boundaries; forming bars cannot enter closed-bar analysis.
- [ ] Live bid/ask updates preserve bid, ask, provider time, received time,
      canonical identity, provenance, and freshness.
- [ ] Live bar updates distinguish forming and closed bars.
- [ ] Fresh, stale, and unavailable states use existing Market Data semantics.
- [ ] Provider silence, disconnect, token expiry, and refresh failure cannot
      leave stale facts appearing current.
- [ ] Authoritative cTrader session, symbol availability, and tradability facts
      are mapped where available; unsupported completeness is explicit.
- [ ] Volume semantics are documented and `VOLUME_ANALYTICS_ELIGIBLE` is not
      asserted without sufficient provider evidence.
- [ ] Connection lifecycle covers connect, authenticate, subscribe, receive,
      disconnect, reconnect, resubscribe, token refresh, and provider error.
- [ ] Duplicate subscriptions are prevented and update handling is bounded by
      existing repository patterns.
- [ ] Provider-specific payloads, errors, credentials, and tokens do not cross
      the adapter boundary.
- [ ] Market Intelligence consumes normalized cTrader OHLC through the same
      provider-neutral path as Kraken.
- [ ] Existing Kraken behavior remains unchanged and regression-tested.
- [ ] PAPER remains simulated and has no accidental LIVE fallback.
- [ ] Focused tests cover mapping, metadata, OHLC, intervals, closed-bar
      semantics, cutoff exclusion, pagination, duplicates, gaps, bid/ask,
      freshness, session state, errors, reconnect/resubscribe, provenance, and
      Market Intelligence compatibility.
- [ ] Runtime validation is clearly separated from implementation completion;
      no live success is claimed without authenticated cTrader validation.
- [ ] Relevant architecture documentation is reconciled.
- [ ] `git diff --check` and affected module validation pass.

## Relevant ADRs

- `docs/architecture/adr/ADR-030.md` - Broker Service architecture and provider
  isolation.
- `docs/architecture/adr/ADR-053.md` - Market identity and provider symbols.
- `docs/architecture/adr/ADR-071.md` - FTMO challenge and cTrader boundary.
- `docs/architecture/adr/ADR-073-ctrader-authentication-and-account-binding.md`
  - cTrader authentication and account binding.
- `docs/architecture/adr/ADR-074-ctrader-authoritative-read-only-synchronization.md`
  - cTrader read-only synchronization boundary.
- `docs/architecture/adr/ADR-077-ctrader-market-data-transport-boundary.md`
  - proposed cTrader Market Data transport and ownership boundary.
- `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md`
  - Market Thesis provider-neutral evidence composition.

## Relevant Stories

- `0163` - Deterministic Market Thesis Composition.
- `0164` - Structural Levels and Objectives.
- `0165` - Deterministic Momentum V1.
- `0166` - Deterministic Market Tradeability Evidence for Market Thesis.
- `0147` - cTrader Authentication and Explicit External Account Binding.
- `0148` - cTrader Authoritative READ-ONLY Synchronization.

## Relevant Modules

- `market-data`
- `broker-service`
- `market-intelligence`
- `trading-core` only for compatibility verification where required

## Validation

- Repository baseline and clean working-tree verification.
- Official cTrader contract verification before implementation.
- Focused Market Data adapter and normalization tests.
- Tests for canonical mapping, metadata, OHLC, cutoff safety, closed-bar
  semantics, bid/ask, freshness, session state, errors, reconnection, and
  provenance.
- Market Intelligence compatibility tests proving the same provider-neutral
  analytical path works with cTrader facts.
- Kraken regression tests.
- Affected Maven tests and formatting checks.
- `git diff --check`.
- Controlled runtime validation only when authenticated cTrader credentials and
  an approved procedure are available.

## Implementation Report Requirements

The implementation report must include:

- repository baseline;
- implemented capabilities;
- cTrader API contracts used;
- service ownership;
- instrument mapping;
- OHLC semantics;
- live quote semantics;
- session semantics;
- freshness semantics;
- provenance;
- error handling;
- connection lifecycle;
- Market Intelligence compatibility;
- Kraken regression;
- test results;
- runtime validation status;
- external blockers;
- documentation changes;
- residual gaps.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed.
- [ ] Documentation reconciliation completed.
- [ ] Relevant validation executed.
- [ ] Diff reviewed in IntelliJ.
- [ ] Independent Code Review approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.

## Final Gate

The implementation report must end with:

```text
========================================
TRADING OS - CTRADER MARKET DATA GATE
========================================

IMPLEMENTATION_COMPLETE:
<YES / NO>

CANONICAL_INSTRUMENT_MAPPING:
<PASS / FAIL>

HISTORICAL_OHLC:
<PASS / FAIL>

CLOSED_BAR_SEMANTICS:
<PASS / FAIL>

LIVE_BID_ASK:
<PASS / FAIL>

FRESHNESS:
<PASS / FAIL>

SESSION_TRADABILITY:
<PASS / PARTIAL / FAIL>

PROVIDER_PROVENANCE:
<PASS / FAIL>

MARKET_INTELLIGENCE_COMPATIBILITY:
<PASS / FAIL>

KRAKEN_REGRESSION:
<PASS / FAIL>

CTRADER_DOM:
<OUT_OF_SCOPE / IMPLEMENTED>

REFERENCE_MARKET_DATA:
<LATER>

FEATURE_PROJECTION_V1:
<UNCHANGED / CHANGED>

S1_SEMANTICS:
<UNCHANGED / CHANGED>

LIVE_CTRADER_RUNTIME_VALIDATED:
<YES / NO>

EXTERNAL_RUNTIME_BLOCKER:
<NONE / SPOTWARE_ACTIVATION / CREDENTIALS / OTHER>

REALISTIC_FTMO_DRY_RUN_READY:
<YES / YES_WITH_LIMITATIONS / NO>

NEXT_HUMAN_ACTION:
<exactly one>
```

## Non-Goals

This Story does not make Trading OS FTMO challenge ready. It removes the
largest data-realism blocker so that later Stories can validate Tradeability
wiring, Trader Cockpit visibility, dry-run workflow, provider runtime behavior,
and decision policies against the intended execution market.
