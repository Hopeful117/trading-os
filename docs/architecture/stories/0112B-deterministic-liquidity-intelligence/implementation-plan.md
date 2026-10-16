# Implementation Plan - Story 0112B

## Status

Approved by the human engineer before implementation.

This plan implements the approved Story 0112B and the accepted ADR-056,
ADR-060, and ADR-063 constraints. It does not implement absorption, trade-flow
analysis, strategy semantics, or changes to `feature-projection-v1`.

## Implementation Boundary

The implementation remains inside `market-data` and `market-intelligence`.

`market-data` owns the internal replay transport contract and continues to own
Order Book evidence truth. `market-intelligence` owns the replay client adapter,
context mapping, deterministic calculations, artifact production, and provenance
of the liquidity interpretation.

No public API, Gateway route, Frontend behavior, Broker, Risk, Execution, or new
service is included.

## Decisions Fixed By This Plan

### 1. Initial Feature Set

The first version uses the complete replayed depth supplied by the context, with
depth bounded by the existing replay request. It produces these ordered numeric
features:

| Feature | Definition | Unit |
| --- | --- | --- |
| `liquidity.best_bid` | Highest valid bid price | `PRICE` |
| `liquidity.best_ask` | Lowest valid ask price | `PRICE` |
| `liquidity.spread_absolute` | `bestAsk - bestBid` | `PRICE` |
| `liquidity.spread_relative` | `spread_absolute / midpoint` | `DECIMAL_FRACTION` |
| `liquidity.spread_bps` | `spread_relative * 10,000` | `BASIS_POINTS` |
| `liquidity.bid_visible_quantity` | Sum of valid bid quantities in replay depth | `QUANTITY` |
| `liquidity.ask_visible_quantity` | Sum of valid ask quantities in replay depth | `QUANTITY` |
| `liquidity.total_visible_quantity` | Bid plus ask visible quantity | `QUANTITY` |
| `liquidity.volume_imbalance` | `(bidQuantity - askQuantity) / (bidQuantity + askQuantity)` | `DECIMAL_FRACTION` |
| `liquidity.microprice` | `(ask * bidQuantity + bid * askQuantity) / (bidQuantity + askQuantity)` using top-level quantities | `PRICE` |

Liquidity concentration is deferred because ADR-063 names it as a candidate but
does not define a deterministic formula, level window, or unit. It must not be
introduced by inference during implementation.

### 2. Numeric and Missingness Rules

- All prices and quantities use `BigDecimal`.
- Replay levels must already be normalized: bids descending and asks ascending.
- A replay with no valid trusted starting point is `UNAVAILABLE` and produces no
  liquidity values.
- Empty or insufficient sides produce `INSUFFICIENT_DATA` for dependent values.
- Non-positive prices, negative quantities, crossed books, or a non-positive
  midpoint produce `INVALID_INPUT` for the affected calculation or result.
- A zero denominator for imbalance or microprice produces
  `INSUFFICIENT_DATA`, never zero or a neutral value.
- Exact equality between best bid and best ask is valid and produces zero spread
  when all other inputs are valid.
- No rounding is applied before fingerprinting. Any serialized/feature display
  scale must be explicit and separate from calculation identity.

The implementation adds `FeatureUnit.QUANTITY` without changing the values or
semantics of `feature-projection-v1`.

### 3. Replay Transport Contract

Add an internal endpoint following existing Market Data conventions:

```text
GET /internal/v1/order-book/replay/{marketId}?depth=25&asOf=<T0>
```

The response must preserve, at minimum:

- replay status and explicit reason;
- market ID and requested depth;
- evaluation cutoff;
- selected sequence ID;
- latest occurred-at and known-at boundaries;
- normalized bids and asks;
- normalization version;
- source start/end ordinals;
- source evidence fingerprint or deterministic aggregate of source fingerprints;
- provider and provider symbol when available.

The Market Data service may extend `OrderBookReplayResult` and its repository
query to carry this provenance. It must not use current provider state as a
fallback. Invalid requests return `400`; unavailable or invalid replay results
remain explicit response values rather than being converted to HTTP success with
fabricated levels.

Add a typed `OrderBookReplayClient` in Market Intelligence using the existing
Feign service configuration. Provider-specific Kraken DTOs remain inside
Market Data.

### 4. Market Intelligence Context Boundary

Add a provider-neutral `OrderBookContext` domain payload containing:

- market identity and depth;
- normalized bid/ask levels;
- evaluation cutoff;
- replay status and reason;
- sequence identity;
- latest occurred-at and known-at values;
- normalization version;
- source evidence provenance.

Add a dedicated `ORDER_BOOK` context section and a contributor that requests
strict replay when the analysis request has a cutoff and strict evidence policy.
The contributor must return explicit unavailable/incomplete sections for missing
cutoffs, unavailable replay, transport errors, or invalid responses. It must not
call the current price snapshot endpoint as fallback.

Live-current analysis without an evaluation cutoff does not silently claim
historical liquidity truth. The plan permits the contributor to mark the section
`UNSUPPORTED` for non-strict requests until a separate live liquidity policy is
approved.

### 5. Deterministic Liquidity Artifact

Add a pure `OrderBookLiquidityCalculator` and a deterministic capability that:

- consumes only `OrderBookContext`;
- has no Spring, network, clock, or mutable global-state dependency;
- emits the ordered feature values and explicit statuses;
- includes algorithm ID/version and parameter fingerprint;
- includes replay sequence, cutoff, normalization, and source evidence
  fingerprints in artifact provenance;
- returns no Opportunity, TradePlan, Risk, or execution output.

The artifact contract is versioned independently, initially as
`order-book-liquidity-v1`. It is persisted through the existing artifact
persistence boundary if that boundary can accept the new typed content without
changing unrelated contracts.

The capability should be registered as a deterministic Market Intelligence
capability with a dedicated artifact type and version. Existing current-quote
`SpreadAnalysisCapability` remains unchanged.

### 6. FeatureSnapshot Boundary

Do not modify `FeatureProjectionV1`, `FeatureGroup` membership, or existing
FeatureSnapshot schema in this implementation.

The new liquidity artifact remains independently versioned and traceable. If
repository constraints prove that a FeatureSnapshot integration is required for
the Story's acceptance, stop before implementing it and produce a revised plan
for an explicit `feature-projection-v2` under ADR-060. Do not add that projection
implicitly.

## Expected Production Changes

### Market Data

- Extend replay result provenance where necessary.
- Add replay response DTO/controller under `/internal/v1/order-book`.
- Add focused controller and replay provenance tests.
- Preserve strict cutoff and valid-sequence selection semantics.

### Market Intelligence

- Add typed replay client and response mapping.
- Add `OrderBookContext`, context section support, contributor, and tests.
- Add `OrderBookLiquidityCalculator`, versioned artifact content, capability, and
  tests.
- Add `FeatureUnit.QUANTITY` only if the new artifact reuses the typed feature
  schema.
- Register the capability through existing deterministic capability conventions.

### Documentation

- Keep this Story and ADR-063 as the canonical scope and architecture references.
- Update relevant API/configuration documentation only if the internal replay
  contract requires repository-standard documentation.
- Do not create a public API or broad architecture rewrite.

## Test Plan

### Replay Contract Tests

- Exact cutoff selection and exclusion of `knownAt > T0` evidence.
- Sequence ID and contiguous delta behavior.
- Unavailable and invalid replay responses.
- Preservation of source ranges, fingerprints, normalization version, and time
  boundaries.
- Controller validation and transport mapping.

### Context Tests

- Strict request with valid replay becomes an available `ORDER_BOOK` section.
- Missing cutoff, unavailable replay, malformed response, and transport failure
  remain explicit degraded states.
- No current snapshot fallback occurs.
- Context provenance and evaluation cutoff are preserved.

### Calculator and Capability Tests

- Valid canonical book calculations for every selected feature.
- Empty side, crossed book, invalid level, zero quantity, and zero denominator
  cases.
- Exact-equal bid/ask zero spread case.
- Deterministic value order, algorithm version, parameter fingerprint, and source
  provenance.
- Capability architecture and absence of Risk/Execution dependencies.

### Repository Validation

- `cd market-data && ./mvnw verify`
- `cd market-intelligence && ./mvnw verify`
- SonarQube analysis for affected projects when configured.
- `git diff --check`
- Representative cross-service replay-to-liquidity validation without current
  state fallback.

## Implementation Sequence

1. Extend and test the Market Data replay provenance contract.
2. Add and test the internal replay controller.
3. Add the Market Intelligence Feign client and response mapper.
4. Add `OrderBookContext` and strict context contribution.
5. Add the pure calculator and versioned artifact content.
6. Register the deterministic capability and connect artifact persistence.
7. Add complete focused tests and architecture checks.
8. Reconcile documentation, run all applicable quality validation, and review the
   complete diff.

## Risks and Stop Conditions

- If the replay repository cannot provide the required source provenance without
  changing Story 0112A semantics, stop and request an explicit scope decision.
- If existing artifact persistence cannot store the new content without changing
  a shared contract, stop rather than introducing an implicit projection or
  generic persistence redesign.
- If a formula requires product policy not defined by ADR-063, stop and request a
  decision rather than selecting a threshold or interpretation silently.
- If strict replay cannot be exposed without weakening service security or
  internal API conventions, stop and report the boundary conflict.

## Approval Gate

This plan is complete and awaits explicit human approval before implementation.
