# Story 0070: Market Eligibility Filtering

## Metadata

**ID:** `0070`
**Title:** Market Eligibility Filtering
**Status:** CLOSED - HUMAN ACCEPTED
**Size:** Large
**Risk:** High
**Predecessor:** Story 0069 - Market Facts Foundation
**Investigation:** `docs/architecture/reports/market-eligibility-filtering-investigation.md`

The implementation is present in the repository and has been reviewed and
accepted by the human engineer. The human Git commit remains pending under the
repository workflow; this status does not authorize another implementation,
commit, push, or merge operation.

## Goal

Introduce a deterministic elimination-first eligibility layer before expensive
Active Scan analysis.

The layer must prevent markets that fail explicit technical or analytical
eligibility conditions from entering deeper analysis while preserving explicit
user scope and existing service ownership boundaries.

The goal is not to identify the best markets. It is to establish whether a
market may proceed to deeper analysis under the current required evidence and
hard eligibility rules.

## Context

Story 0068 established user-controlled market discovery over canonical
catalogue fields. Story 0069 established broker-neutral `MarketActivityFact`
and `MarketDataReadiness` contracts in `market-data`, including typed statuses,
bounded OHLC acquisition, freshness, raw-versus-normalized evidence, and
provenance.

The current Active Scan implementation already has an eligibility boundary, but
it only evaluates market existence and `MarketState.tradable`:

- `market-intelligence/.../ActiveScanScopeResolutionService` resolves candidate
  IDs and creates `MarketEligibilityDecision` values;
- `MarketEligibilityDecision` currently contains a boolean and reason list;
- `MarketEligibilityReason` currently contains `MARKET_NOT_FOUND` and
  `MARKET_NOT_TRADABLE`;
- `ActiveScanApplicationService` persists the scope snapshot and registers one
  `AnalysisExecution` per eligible market;
- `ActiveScanScopeSnapshot` and `ActiveScanDecisionSnapshot` preserve current
  scan decisions;
- `MarketDataClient` does not yet expose the internal Market Facts endpoint;
- `market-data` exposes `GET /internal/v1/market-facts/{marketId}` through
  `InternalMarketFactsController`.

The current `ActiveScanScopeResolutionRequest` and
`CreateActiveScanCommand` represent requested scope only as nullable
`List<UUID> requestedMarketIds`. `ActiveScanScopeResolutionService` currently
interprets null or empty IDs as the complete catalogue. This conflicts with
ADR-033's requirement that full-universe scanning be an explicit product or
user choice. Story 0070 must align the request contract with that existing ADR
intent rather than preserve accidental null/empty behavior.

The current Market Facts authorization defaults
`MARKET_DATA_MARKET_FACTS_AUTHORIZED_CALLERS` to `trading-core`. The existing
configuration also has a `market-intelligence` trusted secret property, but
Market Intelligence is not enabled as a default Market Facts caller. The
required service-to-service authorization must be configured explicitly without
weakening JWT validation or exposing the internal endpoint publicly.

The authoritative design investigation is:

`docs/architecture/reports/market-eligibility-filtering-investigation.md`

## Problem

Active Scan can currently proceed from catalogue existence and tradability to
deep analysis without a deterministic, reusable check that the required Market
Facts evidence is available, supported, fresh, sufficiently complete, and
usable.

The current behavior also allows an omitted or empty requested-market list to
be interpreted as the entire catalogue. That can turn a missing scope into a
large analysis request and violates the distinction between explicit selected
scope and explicit full-scope intent.

Without this Story:

- missing Market Facts can be mistaken for an eligible market;
- stale, unsupported, incomplete, or synthetic evidence can reach deeper
  analysis without an explicit result;
- budget exhaustion can be confused with market exclusion;
- eligibility reasons can depend on rule evaluation order;
- Active Scan provenance cannot identify which eligibility policy produced its
  effective scope;
- a scope omission can accidentally trigger full-catalogue analysis.

## Product Goal

The product flow for this Story is:

```text
explicit requested scope
        |
existing account / market eligibility
        |
required Market Facts readiness eligibility
        |
deterministic Market Eligibility Policy
        |
  +-----------+-----------------+
  |           |                 |
EXCLUDED  NOT_EVALUABLE     ELIGIBLE
                              |
                       Active Scan analysis
```

Eligibility may remove markets from an explicit scope. It must never add a
market to that scope.

## Semantic Definitions

### `ELIGIBLE`

`ELIGIBLE` means:

> No applicable required deterministic exclusion rule rejected the market, and
> all required technical evidence was evaluable and acceptable.

`ELIGIBLE` does not mean:

- interesting;
- high quality;
- recommended;
- profitable or likely profitable;
- bullish or bearish;
- likely to produce a setup;
- strategy-compatible;
- `StrategyEvaluation` success;
- `StrategyMatch`;
- `TradingOpportunity`;
- `TradePlan`;
- Risk-approved;
- safe to trade;
- execution-authorized.

### `EXCLUDED`

`EXCLUDED` means that at least one applicable required hard gate evaluated
successfully and deterministically rejected the market. All cheap applicable
reasons already available from the same evidence must be preserved.

### `NOT_EVALUABLE`

`NOT_EVALUABLE` means that no exclusion was established, but one or more
required hard gates could not be evaluated reliably. Missing, unavailable,
unsupported, insufficient, stale, or budget-exhausted evidence must remain
explicit and must not be converted to zero, `PASS`, or `ELIGIBLE`.

### Per-rule outcomes

The implementation must preserve semantically equivalent outcomes for each
applicable rule:

- `PASS`: the rule evaluated successfully and did not reject the market;
- `EXCLUDE`: the rule evaluated successfully and rejected the market;
- `NOT_EVALUABLE`: required evidence was not sufficient for reliable evaluation;
- `NOT_APPLICABLE`: the rule intentionally does not apply under the explicit
  current policy.

Exact enum names may follow existing repository conventions, but a boolean-only
model is insufficient if it loses the distinction between exclusion and
uncertainty.

## Current Architecture

### Ownership

| Concern | Owner |
|---|---|
| Market catalogue, `MarketState`, normalized OHLC, `MarketActivityFact`, `MarketDataReadiness` | `market-data` |
| Provider acquisition and provider-specific mapping | `market-data` provider adapters, including `KrakenMarketData` |
| Deterministic interpretation of facts for eligibility | `market-intelligence` |
| Active Scan scope, effective IDs, child registration, dispatch, reconciliation | `market-intelligence` Active Scan |
| Account-specific eligibility and financial Risk | Trading Core / Risk Domain |
| AI interpretation | None in this Story; no authority |

Provider-specific acquisition must remain inside Market Data. Market
Intelligence must consume a typed provider-neutral contract and must not parse
Kraken payloads, symbols, errors, or credentials.

### Current flow and insertion point

```text
Market Data catalogue
    -> explicit scope request
    -> ActiveScanScopeResolutionService
    -> existing account / market eligibility
    -> Market Eligibility Policy       [Story 0070]
    -> ActiveScanScopeSnapshot
    -> ActiveScanApplicationService
    -> one AnalysisExecution per effective market
    -> deterministic Market Intelligence capabilities
    -> observations / opportunities / TradePlan
    -> Trading Core Risk and human authorization
```

The policy belongs inside or adjacent to the existing `application.scope`
boundary. It must not become a new scanner, a second analysis engine, or a
generic rules framework.

## Target Flow

The target scope operation is:

```text
resolve explicit scope mode and IDs
        |
validate account ownership and existing scope authority
        |
resolve candidate IDs only from the explicit scope
        |
evaluate catalogue existence and MarketState cheaply
        |
acquire required Market Facts only for markets still requiring evaluation
        |
aggregate typed rule outcomes
        |
persist decisions used by the Active Scan
        |
register AnalysisExecution only for ELIGIBLE markets
```

The invariant is:

```text
effectiveScope subset-of explicitRequestedScope
```

Eligibility may only keep or remove a requested market. It may never add a
market because it was found in the catalogue, cache, provider response, or
another user's scope.

## Scope

### Included

- A deterministic hard-gate eligibility policy in `market-intelligence`.
- Reuse/evolution of the existing `MarketEligibilityDecision` and
  `MarketEligibilityReason` boundary.
- Aggregate outcomes equivalent to `ELIGIBLE`, `EXCLUDED`, and
  `NOT_EVALUABLE`.
- Per-rule outcomes equivalent to `PASS`, `EXCLUDE`, `NOT_EVALUABLE`, and
  `NOT_APPLICABLE`.
- Explicit requested-scope semantics distinguishing:
  - selected-market scope;
  - explicit full/all-eligible scope;
  - absent, invalid, or ambiguous scope.
- Removal of the accidental `null/empty -> complete catalogue` behavior for
  Active Scan requests.
- Preservation of explicit user scope as an upper bound.
- Hard gates for market existence and current tradability.
- Hard gates for required Market Facts status and readiness evidence.
- Cheap-first evaluation and preservation of all already-known cheap reasons.
- A deterministic evaluation budget before broad per-market Market Facts
  acquisition.
- Explicit budget-exhaustion semantics as `NOT_EVALUABLE` or `DEFERRED`, never
  market exclusion.
- A narrow typed Market Intelligence client integration for the existing
  internal Market Facts contract.
- Explicit Market Intelligence service authorization for the internal Market
  Facts endpoint using existing JWT mechanisms.
- Policy identity, policy version, rule versions, cutoff, and relevant facts
  status/calculation provenance in the Active Scan decision snapshot.
- Reuse of the existing `ActiveScanScopeSnapshot` and
  `ActiveScanDecisionSnapshot` persistence mechanism.
- Unit, integration, contract, security, scope, budget, and no-side-effect
  tests based on domain invariants.

### Hard gates

#### 1. Market existence

A requested market that is not present in the resolved catalogue must not enter
the effective scope and must produce an explicit exclusion reason equivalent to
`MARKET_NOT_FOUND`.

#### 2. Tradability

A market whose current `MarketState` does not permit real-time trading must not
enter the effective scope and must produce an explicit reason equivalent to
`MARKET_NOT_TRADABLE`.

The Story reuses the existing `MarketState.tradable` semantics established by
ADR-010. It does not redefine tradability, exchange sessions, constraints, or
Risk.

#### 3. Required data unavailable

Required Market Facts with `MarketFactStatus.UNAVAILABLE` must not produce
`ELIGIBLE`.

#### 4. Required data unsupported

Required Market Facts with `MarketFactStatus.UNSUPPORTED` must not be treated as
valid evidence and must not produce `ELIGIBLE`.

#### 5. Required data stale

Required activity/readiness evidence outside the explicit freshness contract
must not be treated as current evidence and must not produce `ELIGIBLE`.

#### 6. Insufficient completed history

When the required readiness request does not contain the required completed
history, the market must not produce `ELIGIBLE`.

#### 7. Data integrity and completeness

Required evidence containing unacceptable missing intervals, conflicting
duplicates, synthetic normalization, cadence violations, or another existing
`MarketDataReadiness` failure must not silently become acceptable because the
normalized series appears continuous.

The implementation must consume the existing 0069 fields and status semantics.
It must not invent new Market Data completeness semantics in Market Intelligence.

### Required Market Facts request

The consumer of eligibility must supply or select an explicit readiness
requirement for the intended deeper analysis. The eligibility layer must not
infer readiness from the existence of a Market Facts endpoint, from a cache
hit, or from a Trend Context implementation detail. The requirement must be
represented as a typed policy/configuration input and preserved in the
assessment provenance.

It must preserve generic parameters such as:

- OHLC interval;
- lookback/depth;
- minimum completed evidence;
- observation cutoff/boundary;
- maximum observation age/freshness;
- acceptable completeness/synthetic policy.

The Story does not choose universal values for these parameters. If the
consuming Active Scan analysis policy does not establish them, implementation
must represent them as explicit configuration/policy inputs and stop before
deploying without approved values. A readiness requirement must not be encoded
as a generic Market Data default solely for this Story.

Do not introduce Trend Context role names such as `BIAS`, `SETUP`, or `TRIGGER`
into the generic eligibility or Market Data contracts.

### Evaluation budget

Before per-market Market Facts acquisition is allowed across a broad scope, the
implementation must apply a deterministic operational budget that is explicit
in configuration/policy. It must cover at least:

- maximum markets requiring Market Facts evaluation;
- bounded provider acquisition/request work;
- behavior when the budget is exhausted.

For V1, a Market Facts evaluation attempt is counted conservatively when the
eligibility policy decides to evaluate a market and invokes the typed Market
Facts boundary. The count increments regardless of whether Market Data serves
the result from its current process-local cache or performs provider
acquisition, because the current contract does not expose cache-hit/provider-
acquisition accounting to Market Intelligence. Cache reuse may reduce provider
work, but it must not allow the eligibility layer to bypass its configured
evaluation budget.

The implementation must not assume that the current bounded in-memory cache is
a catalogue-wide projection or a durable budget ledger. A cache hit is still
usable evidence only after it satisfies the consumer's explicit readiness
requirement and cutoff/freshness rules.

An optional evaluation duration bound may be included if supported by existing
repository conventions, but no value may be invented here.

The budget is not ranking:

```text
maximum fact evaluations = operational safety bound
Top N markets = relevance/quality claim
```

The Story does not define a universal maximum market count, provider rate
limit, request duration, or provider concurrency value. These remain explicit
configuration values requiring approval before implementation/runtime use.

### Cheap-first evaluation

The implementation should evaluate in this order, while preserving required
uncertainty:

1. membership in the explicit requested scope;
2. catalogue existence;
3. current `MarketState.tradable`;
4. already available capability/readiness evidence;
5. bounded Market Facts acquisition only for markets still requiring it.

Once a cheap hard gate deterministically excludes a market, the implementation
must not acquire additional expensive facts merely to add more reasons. It must
preserve all cheap applicable reasons already available from the same evidence.

## Explicit Scope Semantics

The current nullable `requestedMarketIds` representation is insufficient for
the required distinction. The implementation must evolve the existing Active
Scan request/application boundary, using current contracts and conventions, so
that it represents one of these explicit modes:

### Selected-market scope

The request explicitly identifies one or more market IDs. Only those IDs may be
candidate IDs. A missing catalogue market is recorded as an explicit
`MARKET_NOT_FOUND` decision and is never replaced by another market.

### Full/all-eligible scope

The request explicitly identifies that the product/user selected the broad
eligible scope. The representation must be distinct from an omitted list and
must be visible in the scope/provenance used by the scan.

### Absent, invalid, or ambiguous scope

An omitted, null, empty, malformed, or otherwise ambiguous scope must not
silently become full-catalogue analysis. It must be rejected or represented as
an explicit non-evaluable/invalid request according to existing API error
conventions.

`resolveDecisionContext()` currently uses a null requested list to load a
catalogue context. That account-context use must not accidentally authorize a
full Active Scan. The implementation must distinguish account context loading
from an Active Scan's explicit broad-scope command.

### Existing-client migration

Existing clients that send only `requestedMarketIds` must be migrated through a
bounded compatibility path agreed before implementation. The migration must
not reinterpret null or empty input as full scope. Until a client sends an
explicit selected or full/all-eligible scope mode, the request must be rejected
or returned as an explicit invalid/non-evaluable request according to the
approved API convention.

The compatibility path may accept a non-empty `requestedMarketIds` list as
selected scope when that interpretation is unambiguous. It must not silently
accept omitted/empty IDs as broad scope, and it must have a defined removal or
sunset point rather than becoming a second long-term scope contract.

## Eligibility Outcome Aggregation

Use these V1 semantics:

1. If any applicable hard gate returns `EXCLUDE`, aggregate outcome is
   `EXCLUDED`.
2. If no gate excludes but one or more required hard gates return
   `NOT_EVALUABLE`, aggregate outcome is `NOT_EVALUABLE`.
3. `ELIGIBLE` is valid only when every applicable required hard gate returns
   `PASS`.
4. Missing evidence never becomes zero, `PASS`, or `ELIGIBLE`.
5. Disabled/non-applicable logic is explicit `NOT_APPLICABLE`, not missing
   evidence.

All V1 technical readiness gates are required and fail closed for progression to
Active Scan. Budget exhaustion is a `NOT_EVALUABLE` or repository-equivalent
deferred outcome, not `EXCLUDED`.

## Exclusion Reasons and Provenance

The existing `MarketEligibilityDecision.reasons` list must evolve or be
augmented without losing its current purpose. All cheap applicable reasons
already known from the same catalogue/facts evidence must be retained and must
not depend on first-rule order.

The persisted decision used by an Active Scan must preserve enough provenance to
reconstruct the effective scope, including where practical:

- requested scope mode and requested IDs;
- effective IDs;
- aggregate eligibility outcome;
- all exclusion/not-evaluable reasons;
- assessment cutoff and evaluation timestamp;
- policy identity and policy version;
- applicable rule identity/version;
- Market Facts status and calculation version;
- relevant fact evidence timestamp/status references;
- budget exhaustion/deferred reason where applicable.

The existing `ActiveScanScopeSnapshot` and `ActiveScanDecisionSnapshot` are the
required persistence boundary. Do not introduce a standalone eligibility table,
read model, or persistence authority in this Story.

## Market Facts Integration

`market-data` remains the owner of `MarketActivityFact`,
`MarketDataReadiness`, `MarketFactStatus`, normalized OHLC evidence, and
provider acquisition.

`market-intelligence` must consume the existing internal endpoint through a
narrow typed integration. The likely contract boundary is:

```text
market-intelligence MarketDataClient
    -> /internal/v1/market-facts/{marketId}
    -> market-data InternalMarketFactsController
    -> MarketFactsService
```

The implementation plan must account for:

- a typed `MarketDataClient` method and response mapping;
- preservation of `MarketFactStatus` and readiness diagnostics required for
  rule evaluation;
- timestamps, observation cutoff/freshness, and calculation version;
- no raw Kraken DTOs or provider-specific error parsing;
- no duplicated activity/readiness calculation in Trend Context or Active Scan;
- bounded request parameters from the approved readiness requirement.

No new provider integration, Market Data service, shared library, or generic
policy engine is authorized.

## Service JWT and Security

The internal Market Facts endpoint must remain protected by the existing service
JWT mechanism.

The implementation must configure the explicit `market-intelligence` caller
through the existing allowlist and trusted-secret properties, including the
deployment environment, without displaying or hard-coding secrets.

Required security behavior:

- authorized `market-intelligence` service caller can consume Market Facts;
- unauthorized service caller is rejected;
- unauthenticated caller is rejected;
- public Market Data routes are not broadened to expose Market Facts;
- JWT validation, audience, issuer, caller allowlist, and secret handling are
  not weakened;
- provider credentials and raw provider payloads are not exposed.

## Active Scan Integration

The expected integration point is inside or adjacent to
`ActiveScanScopeResolutionService`, after explicit scope candidate IDs have
been resolved and before `ActiveScanScopeSnapshot` is created.

`ActiveScanApplicationService` remains responsible for:

- persisting the scope and decision snapshot;
- registering `AnalysisExecution` only for effective `ELIGIBLE` markets;
- dispatching and reconciling child analyses.

The eligibility policy must not own scan persistence, dispatch, child
registration, or reconciliation.

Markets with `EXCLUDED` or `NOT_EVALUABLE` outcomes must not register deeper
analysis children. Their decisions remain visible in the scan snapshot.

The implementation must preserve manually selected markets as a distinct user
scope. This Story does not implement future defensive-policy overrides, but it
must not make future relevance policies indistinguishable from hard technical
eligibility.

## Persistence Boundary

No standalone eligibility persistence is included.

Eligibility results evaluated outside a scan may remain ephemeral. Decisions
actually used by an Active Scan must be represented through the existing
`ActiveScanScopeSnapshot` / `ActiveScanDecisionSnapshot` mechanism, extended only
as needed for aggregate status and provenance.

Historical scan decisions must not be rewritten when a later fact, policy, or
rule version changes. A later policy evaluates a new assessment; it does not
mutate the earlier scan snapshot.

## Policy Versioning

The implementation must use a simple deterministic policy identity/version and
rule identity/version sufficient to answer:

> Which eligibility rules produced this Active Scan effective scope?

The policy version must be captured in scan provenance. Market Facts'
`calculationVersion` remains owned by Market Data and should be retained as
input provenance, not replaced by the eligibility policy version.

Do not introduce a generic dynamic policy engine, expression language, plugin
framework, scripting layer, Drools, or AI policy engine.

## Out of Scope

The following are explicitly excluded from V1:

- `LOW_ACTIVITY` and `LOW_24H_ACTIVITY`;
- activity or volume thresholds;
- activity ranking, percentile filtering, relevance scores, or
  `AnalysisPriorityScore`;
- Top-N quality selection;
- ATH, historical drawdown, or any "90% below ATH" rule;
- spread, liquidity, slippage, depth, or order-book filtering;
- generic volatility or Trend Context ATR filtering;
- Market Structure, `SwingPoint`, HH/HL/LH/LL, BOS, CHOCH, or trend lines;
- StrategyEvaluation, StrategyMatch, TradingOpportunity, TradePlan, Risk,
  execution, broker orders, or position mutations;
- AI, News, macroeconomic analysis, or agent authority;
- cross-provider ranking or cross-currency normalization;
- a standalone eligibility table/read model;
- Passive Scanner redesign or passive-awareness persistence;
- a new microservice, shared policy library, or generic rules framework;
- frontend redesign or new market-discovery UX.

`MarketActivityFact` may be carried as contract provenance if required by the
existing Market Facts response, but it must not be used as a default exclusion
rule in this Story.

## Acceptance Criteria

### AC1 - Explicit scope is authoritative

**Given** an explicit selected-market scope or an explicit full/all-eligible
scope, **when** eligibility runs, **then** the effective scope is a subset of
the explicit requested scope representation.

### AC2 - No implicit full catalogue

**Given** an absent, null, empty, invalid, or ambiguous Active Scan scope,
**when** scope is resolved, **then** full-catalogue analysis does not occur
unless the request explicitly represents full/all-eligible scope.

### AC3 - Selected scope cannot broaden

**Given** a selected scope containing markets A and B, **when** the catalogue,
cache, provider response, or eligibility evaluation contains market C, **then** C
does not become a candidate, decision, effective market, facts request, or
AnalysisExecution solely because it was discovered.

### AC4 - Market existence

**Given** a requested market ID not present in the resolved catalogue, **when**
eligibility runs, **then** it is not effective and an explicit market-not-found
reason is retained.

### AC5 - Tradability

**Given** a market with `MarketState.tradable=false`, **when** eligibility runs,
**then** it is `EXCLUDED` with an explicit non-tradable reason and no deeper
analysis child is registered.

### AC6 - Required facts unavailable

**Given** required Market Facts return `UNAVAILABLE`, **when** eligibility runs,
**then** the market is not `ELIGIBLE` and the unavailable state is retained.

### AC7 - Required facts unsupported

**Given** required evidence returns `UNSUPPORTED`, **when** eligibility runs,
**then** the market is not `ELIGIBLE` and unsupported evidence is not treated as
valid or zero-valued evidence.

### AC8 - Stale evidence

**Given** required evidence exceeds the explicit freshness contract, **when**
eligibility runs, **then** the market is not `ELIGIBLE` and the stale reason and
evidence timestamp remain explainable.

### AC9 - Insufficient history

**Given** required completed history is below the explicit readiness
requirement, **when** eligibility runs, **then** the market is not `ELIGIBLE`
and the insufficient-history diagnostics are retained.

### AC10 - Evidence integrity

**Given** required evidence contains unacceptable missing intervals, conflicting
duplicates, synthetic normalization, cadence violations, or another existing
readiness failure, **when** eligibility runs, **then** normalized continuity
does not silently make the market `ELIGIBLE`.

### AC11 - Successful eligibility

**Given** a market is in explicit scope, exists, is tradable, and all required
facts are supported, fresh, sufficiently complete, and acceptable, **when** all
required hard gates pass, **then** the aggregate result is `ELIGIBLE`.

### AC12 - Required uncertainty

**Given** no hard gate has excluded a market but one required hard gate cannot be
evaluated, **when** aggregation completes, **then** the result is
`NOT_EVALUABLE`, not `ELIGIBLE`.

### AC13 - Multiple reasons

**Given** several cheap applicable hard gates fail and their evidence is already
available, **when** aggregation completes, **then** all known applicable reasons
are preserved and output does not depend on first-rule order.

### AC14 - Cheap-first cost behavior

**Given** a market is already deterministically excluded by a cheap hard gate,
**when** eligibility continues, **then** no additional expensive Market Facts
request is made solely to collect more reasons.

### AC15 - Budget safety

**Given** the configured Market Facts evaluation budget is exhausted, **when**
remaining in-scope markets are reached, **then** they are `NOT_EVALUABLE` or
`DEFERRED` with an operational budget reason and never `EXCLUDED`.

### AC16 - No ranking

**Given** an eligibility result, **then** it contains no quality score, rank,
percentile, priority score, or Top-N selection and does not reorder markets as a
quality claim.

### AC17 - Market Data ownership

**Given** eligibility requires provider evidence, **when** that evidence is
acquired, **then** provider-specific acquisition and mapping remain inside
`market-data` and its provider adapters.

### AC18 - Typed Market Facts contract

**Given** Market Intelligence consumes Market Facts, **then** the contract is
typed, provider-neutral, preserves status/diagnostics/timestamps/calculation
version, and does not expose raw Kraken payloads or provider credentials.

### AC19 - Security

**Given** a Market Facts request, **then** only the explicitly authorized
`market-intelligence` service caller can access the internal endpoint;
unauthenticated and unauthorized service callers are rejected.

### AC20 - Provenance

**Given** an Active Scan is created, **then** its persisted scope/decision
snapshot preserves requested scope, effective scope, aggregate outcome,
reasons, assessment cutoff, policy identity/version, rule versions, and relevant
Market Facts status/calculation provenance where available.

### AC21 - No standalone persistence

**Given** an eligibility evaluation occurs outside an Active Scan, **then** it
does not create a standalone eligibility persistence record. Decisions used by a
scan use the existing Active Scan snapshot boundary.

### AC22 - No trading authority

**Given** eligibility returns `ELIGIBLE`, **then** it does not create or imply a
`StrategyMatch`, `TradingOpportunity`, `TradePlan`, Risk approval, execution
intent, broker order, or position mutation.

### AC23 - Determinism

**Given** equivalent facts, scope, policy, rule versions, and cutoff, **when**
eligibility is evaluated repeatedly, **then** equivalent aggregate statuses,
reasons, and effective scope are produced.

### AC24 - Manual scope distinction

**Given** a specifically selected market passes all required hard gates, **then**
it remains representable as a manually selected analysis target even though this
Story does not implement future relevance or defensive-policy overrides.

## Required Test Strategy

Tests must target domain invariants rather than only line coverage.

### Rule-level tests

Future implementation tests must cover:

- existing and missing market;
- tradable and non-tradable `MarketState`;
- `MarketFactStatus.AVAILABLE`;
- `INSUFFICIENT_DATA`;
- `STALE`;
- `UNAVAILABLE`;
- `UNSUPPORTED`;
- insufficient completed history;
- missing intervals;
- synthetic evidence;
- duplicate evidence;
- conflicting evidence;
- cadence violations where relevant;
- exact freshness boundary;
- just-over freshness boundary;
- deterministic `Clock` behavior;
- missing evidence never becoming zero or `PASS`.

### Aggregate tests

Future implementation tests must prove:

- all required rules passing produces `ELIGIBLE`;
- one hard exclusion produces `EXCLUDED`;
- multiple cheap exclusions retain all known reasons;
- no exclusion plus required `NOT_EVALUABLE` produces `NOT_EVALUABLE`;
- disabled/non-applicable behavior is explicit `NOT_APPLICABLE`;
- budget exhaustion produces `NOT_EVALUABLE`/`DEFERRED`, never `EXCLUDED`;
- rule ordering does not change results;
- equivalent facts and cutoff produce equivalent results.

### Scope tests

Future implementation tests must cover:

- one explicitly selected market;
- several explicitly selected markets;
- a market outside requested scope never being added;
- a requested nonexistent market;
- explicit full/all-eligible scope;
- null scope;
- empty scope;
- ambiguous scope;
- a cache containing a market outside requested scope;
- a catalogue containing a market outside requested scope;
- eligibility never broadening scope;
- the current account-context catalogue loading path not silently becoming an
  Active Scan full-scope command.

### Cost and budget tests

Use conceptual scope scenarios of 10, 50, 100, and 500 markets as test
scenarios only. They are not domain thresholds.

Tests must prove:

- Market Facts acquisition never exceeds configured budget;
- cheap-gate exclusions do not trigger unnecessary history calls;
- budget exhaustion preserves uncertainty;
- no hidden ranking or Top-N selection occurs;
- no market outside requested scope triggers a facts request;
- bounded history/request parameters are passed to the existing Market Facts
  contract.
- a cache-served Market Facts evaluation still consumes the conservative
  eligibility evaluation budget;
- cache reuse cannot turn the configured budget into an unbounded catalogue
  evaluation.

### Integration and security tests

Require integration coverage for:

```text
Market Intelligence
    -> authenticated Market Data Market Facts contract
    -> eligibility policy
    -> Active Scan effective scope and snapshot
```

Use provider-shaped facts where relevant, without requiring external live-provider
calls in deterministic automated tests.

Require security coverage for:

- authorized `market-intelligence` caller -> Market Facts accessible;
- unauthorized service caller -> rejected;
- unauthenticated caller -> rejected.

### No-side-effect tests

Eligibility evaluation must create no `StrategyMatch`,
`TradingOpportunity`, `TradePlan`, Risk Evaluation, execution intent, broker
order, or position mutation. Only the normal downstream Active Scan flow may
register its expected `AnalysisExecution` after effective scope resolution.

### Runtime regression policy

Any correctness defect discovered through runtime validation must receive a
minimal deterministic regression test before it is considered fixed. Provider
behavior must use provider-shaped contract coverage where appropriate, not only
simplified domain fixtures.

## Implementation Plan

This is a proposed plan for later implementation, not implementation work.

1. **Resolve the explicit Active Scan scope contract.** Evolve the existing
   `CreateActiveScanRequestDto`, `CreateActiveScanCommand`,
   `ActiveScanScopeResolutionRequest`, and relevant controller mapping so
   selected scope, explicit all-eligible scope, and absent/ambiguous scope are
   distinct. Preserve the separate account-context use of catalogue loading.
2. **Evolve the existing eligibility decision boundary.** Extend
   `MarketEligibilityDecision` and `MarketEligibilityReason` only as needed to
   represent aggregate uncertainty, all hard-gate reasons, and typed
   provenance. Avoid creating a parallel eligibility authority.
3. **Add the smallest deterministic policy component.** Place the policy next to
   the existing `application.scope` boundary. Prefer explicit typed code and
   existing decision conventions over a generic rule framework. Apply cheap
   gates before Market Facts acquisition.
4. **Add typed Market Facts consumption.** Extend
   `market-intelligence`'s `MarketDataClient` with the narrow internal method
   for `/internal/v1/market-facts/{marketId}` and add broker-neutral response
   DTO mapping. Reuse the actual Market Facts request/status fields; do not
   duplicate calculations or expose Kraken DTOs.
5. **Define the readiness requirement as configuration/policy input.** Use the
    consuming analysis policy's explicit interval, lookback, minimum completed
    candles, cutoff/freshness, and completeness requirements. Do not invent
    universal values or Trend Context role names, and do not make a Market Data
    default serve as an implicit eligibility requirement.
6. **Add the evaluation budget.** Configure bounded market-fact evaluation and
    provider acquisition using existing configuration patterns. Do not introduce
    a universal market count or claim provider rate limits. Make exhaustion
    produce `NOT_EVALUABLE`/`DEFERRED` with an operational reason. Count every
    typed Market Facts evaluation attempt conservatively, including cache-served
    results, because the current contract does not expose cache-hit accounting.
7. **Integrate before snapshot creation.** Update
   `ActiveScanScopeResolutionService` to evaluate only explicit candidates,
   then update `ActiveScanApplicationService` only as needed so effective
   `ELIGIBLE` IDs register child executions and all decisions are persisted.
8. **Extend scan provenance.** Evolve `ActiveScanScopeSnapshot` and
   `ActiveScanDecisionSnapshot` to preserve scope mode, aggregate outcome,
   policy/rule versions, cutoff, reasons, and relevant Market Facts provenance.
   Do not create a standalone eligibility persistence model.
9. **Configure service authorization.** Add the narrow
   `market-intelligence` caller to the existing Market Facts authorization
   mechanism using deployment-provided secret values. Do not hard-code or log
   secrets and do not expose the endpoint publicly.
10. **Implement invariant-focused tests.** Add the rule, aggregation, scope,
    budget, client-contract, security, and no-side-effect tests defined above.
    Use fixed clocks and provider-shaped fixtures.
11. **Validate the relevant independent modules.** Run targeted and full
    `market-intelligence` and `market-data` tests as appropriate, security
    integration tests, `git diff --check`, and document any unrelated failures.

Likely existing change areas are limited to:

- `market-intelligence` scope/application/domain/adapter-web and Market Data
  client packages;
- `market-data` existing Market Facts security/configuration contract only when
  required for the authorized caller;
- Active Scan persistence mapping for existing snapshots;
- corresponding test packages and configuration fixtures.

No new service, persistence authority, provider adapter, or frontend redesign
belongs in this plan.

## Security

The internal Market Facts endpoint remains under Spring Security and service JWT
validation. The implementation must use the existing
`MARKET_DATA_MARKET_FACTS_AUTHORIZED_CALLERS` and
`MARKET_INTELLIGENCE_MARKET_DATA_SERVICE_JWT_SECRET` mechanisms or their current
repository equivalents.

The Story must not:

- disable JWT;
- make `/internal/v1/market-facts/**` public;
- authorize arbitrary callers;
- hard-code secrets;
- log secrets or provider credentials;
- expose raw provider payloads through Market Intelligence.

## Risks

- The current Active Scan request contract does not distinguish omitted scope
  from explicit all-scope intent.
- Per-market Market Facts acquisition can become an accidental catalogue-wide
  OHLC crawler without budget enforcement.
- Current in-memory Market Facts caching is process-local and bounded to 256
  successful entries, so it cannot be treated as a catalogue-wide projection.
- The exact readiness interval/lookback/freshness requirement is not established
  by current Active Scan code and must not become an invented universal default.
- Changing `MarketEligibilityDecision` may affect Active Scan response and
  persistence mappings; compatibility must be checked against existing clients.
- Deployment may not yet provide the Market Intelligence trusted JWT secret or
  caller allowlist.
- `NOT_EVALUABLE` semantics can be lost if external API DTOs retain only a
  boolean `eligible` field.

## Deferred Work

Deferred from this Story:

- `LOW_ACTIVITY`, `LOW_24H_ACTIVITY`, and all activity thresholds;
- ATH and historical drawdown facts/policies;
- spread, liquidity, slippage, depth, and generic volatility facts;
- Market Structure and strategy-adjacent filtering;
- ranking, relevance, `AnalysisPriorityScore`, and Top-N quality selection;
- Passive Scanner and maintained catalogue-wide eligibility/readiness projections;
- standalone eligibility persistence;
- AI explanation or relevance authority;
- frontend eligibility presentation;
- cross-provider and cross-currency comparison;
- provider bulk acquisition and rate-limit strategy.

## ADR Assessment

**ADR_REQUIRED: NO** for the narrow V1 defined here.

The Story aligns with:

- ADR-006: Market Data owns market information and provider-neutral facts;
- ADR-010: `MarketState` represents current tradability and remains owned by
  Market Data;
- ADR-014: deterministic evidence precedes downstream interpretation and human
  authorization;
- ADR-033: Active Scan resolves eligibility before expensive analysis, keeps
  requested/effective scope distinct, and distinguishes eligibility from
  relevance;
- ADR-048: deterministic evidence is versioned, provenance-bearing, safe on
  missing/stale inputs, and not a trading authorization.

The explicit full-scope representation is an alignment with ADR-033, not a
redefinition of it. An ADR decision is required before implementation only if
the approved design expands into a new durable eligibility authority,
catalogue-wide maintained facts lifecycle, shared cross-service policy engine,
cross-provider/currency comparison authority, or a material change to ADR-033's
scope semantics.

## Human Decisions

### Resolved architectural decisions

The Story body resolves the following decisions; they are not implementation
or deployment blockers:

- scope is explicit and distinguishes `SELECTED` from `ALL_ELIGIBLE`;
- `ABSENT`, `NULL`, `EMPTY`, and ambiguous scope never imply `ALL_ELIGIBLE`;
- readiness is consumer-driven through typed policy/configuration inputs;
- eligibility does not own universal readiness constants;
- V1 budgets typed Market Facts evaluation attempts, with one typed invocation
  consuming one unit even when Market Data serves the result from cache;
- Market Intelligence does not require provider-request or cache-hit accounting;
- budget exhaustion produces `NOT_EVALUABLE`/deferred, never `EXCLUDED`;
- the budget is an operational safety bound, not ranking or Top-N selection.

The exact Java enum, DTO, and field names, and the concrete invalid-request
HTTP/error mapping, may follow repository conventions during implementation.

### Runtime and deployment configuration

The following values remain runtime/deployment configuration, not unresolved
architecture:

- readiness interval;
- readiness lookback/depth;
- minimum completed evidence;
- maximum observation age/freshness duration;
- completeness and synthetic-evidence tolerance;
- maximum Market Facts evaluations per scan, or the repository-equivalent
  configuration name;
- optional evaluation duration, only if retained by the implementation;
- deployment JWT secret and caller-allowlist configuration.

These values must be typed, validated, and supplied through the established
configuration mechanisms. They do not block implementation of the contracts,
validation, deterministic tests with fixture values, eligibility policy, scope
semantics, budget mechanism, provenance, or service integration. Concrete
environment values may still be required before runtime deployment.

### Genuine implementation compatibility details

- A legacy non-empty `requestedMarketIds` list may be interpreted as
  `SELECTED` when unambiguous.
- Legacy null, empty, or omitted `requestedMarketIds` must not mean `ALL`; an
  explicit `ALL_ELIGIBLE` intent is required.
- The exact sunset/removal mechanics for the legacy request shape may be
  handled during implementation if current clients require migration.

## Definition of Done

### Implementation ready

- [ ] Story scope and architectural decisions are approved.
- [ ] Repository Analysis approved when required by the workflow.
- [ ] Contracts are explicit and typed, including scope mode and readiness
      requirements.
- [ ] Deterministic tests use approved fixture values for readiness and budget
      configuration.
- [ ] Implementation preserves `effectiveScope subset-of explicitRequestedScope`.
- [ ] Null/empty/ambiguous scope cannot silently trigger full-catalogue scan.
- [ ] Explicit selected and explicit all-eligible scope are distinct.
- [ ] Existing selected-list clients have an approved bounded migration path;
      omitted/empty legacy scope cannot become full scope.
- [ ] Hard-gate outcomes preserve `ELIGIBLE`, `EXCLUDED`, and
      `NOT_EVALUABLE` semantics.
- [ ] Required uncertainty fails closed and budget exhaustion is not exclusion.
- [ ] Readiness requirements come from the consuming analysis policy and are
      recorded as typed inputs/provenance.
- [ ] Every Market Facts evaluation attempt consumes the conservative budget,
      including cache-served evaluations.
- [ ] All cheap known reasons are retained without unnecessary expensive calls.
- [ ] Market Facts integration is typed, broker-neutral, bounded, and
      authenticated for `market-intelligence` only.
- [ ] Active Scan snapshots preserve policy/rule/cutoff/fact provenance.
- [ ] No standalone eligibility persistence authority is introduced.
- [ ] No score, ranking, Top-N, LOW_ACTIVITY, ATH, spread, liquidity,
      volatility, Market Structure, AI, Risk, or execution behavior is added.
- [ ] Invariant-focused unit, contract, integration, security, scope, budget,
      and no-side-effect tests pass.
- [ ] Relevant independent module validation and `git diff --check` pass.
- [ ] Implementation report records changed files, validation, limitations, and
      unresolved risks.

### Runtime deployment ready

- [ ] Concrete readiness and `maxMarketFactEvaluationsPerScan` environment
      values are validated for the target deployment.
- [ ] Optional evaluation duration is configured if retained by the
      implementation.
- [ ] Market Intelligence JWT secret and caller-allowlist configuration are
      provisioned without exposing or hard-coding secrets.
- [ ] Code review and human approval are complete.
- [ ] Human commit is created.
