# Repository Analysis - Story 0239

## Metadata

**Story:** `0239 - Define the Bounded Passive Market Universe`

**Status:** Repository Analysis complete - implementation not authorized

**Analysis date:** 2026-11-13

## Scope and Authority

This analysis is based on accepted ADR-102, approved Epic 0238, Story 0239,
the related Stories 0240-0244, current repository source and existing test
conventions. ADR-102 and Epic 0238 establish the architecture and planning
baseline. Story 0239 remains proposed for implementation and no application
code, schema migration, runtime configuration activation or scheduler work is
authorized by this analysis.

The repository is authoritative for the current physical implementation. No
DevLog context was available in the current tool context; this analysis does
not infer historical decisions beyond the repository artifacts listed above.

## Current Repository Evidence

### Canonical market identity

Market Data owns the `Market` entity in
`market-data/src/main/java/com/hope/trading/market_data/model/Market.java`.
The current identity contract contains:

- generated canonical `marketId`;
- `MarketProvider provider`;
- canonical `symbol`;
- optional `providerSymbol`;
- `InstrumentProductType productType`;
- base and quote assets;
- embedded market constraints and operational `MarketState`.

The database uniqueness constraint is `(provider, symbol, product_type)`. The
existing provider and product enums are:

- `MarketProvider`: `KRAKEN`, `CTRADER`, `FTMO`, `BINANCE`, `COINBASE`,
  `BYBIT`;
- `InstrumentProductType`: `SPOT`, `LINEAR_PERPETUAL`, `CFD`.

The current Market Data service exposes lookup methods by provider/symbol and
provider/symbol/product type, but Story 0239 does not need a new lookup or
database aggregate. Market Intelligence already consumes the catalogue through
`MarketDataClient.findAllMarkets()` at `GET /api/v1/markets`.

`MarketResponse` currently carries `marketId`, provider, symbol, base asset,
quote asset, product type and market state. It does not carry `providerSymbol`.
That field is not required to resolve Story 0239 entries because the entry is
bound to the canonical `marketId` and must verify provider/product identity
against the catalogue. Provider-symbol expansion must not be guessed or
introduced as an unrelated compatibility change.

### Operational state is not selection policy

Market Data exposes `TradingStatus` values `UNKNOWN`, `OPEN`, `CLOSED` and
`HALTED`, together with `tradable`, closure reason and last-update time in
`MarketState`. These values are evidence for later operational eligibility;
they are not the source of the global passive configuration.

Market Intelligence's existing `MarketEligibilityPolicy` evaluates activity
and readiness facts for active scan resolution. It is account-independent at
the policy level but is used inside the account-aware `ActiveScanScopeResolutionService`.
It must not be reused as the passive universe resolver, because Story 0239
resolves configured selection and identity only. Story 0240 will apply the
operational and evidence gates before dispatch.

### Existing configuration conventions

Market Intelligence uses Spring `@ConfigurationProperties` classes and
fail-fast validation. `MarketEligibilityProperties` is a component with a
`@PostConstruct` validator, while `ResearchLifecycleProperties` is registered
through `@EnableConfigurationProperties` in
`MarketIntelligenceApplication`.

The new passive configuration should follow these conventions, but it should
be a separate configuration type from active eligibility properties. The
feature must remain disabled when the passive entry list is absent or empty;
there must be no hardcoded demonstration fallback.

## Proposed Implementation Boundary

The implementation belongs in Market Intelligence because ADR-102 assigns it
the passive monitoring selection policy. Market Data remains authoritative for
canonical identity and catalogue records.

The minimum boundary should be:

```text
Spring configuration
    -> passive configuration adapter and validator
    -> MarketDataClient catalogue resolution
    -> PassiveMarketUniverse application contract
    -> future Story 0240 scheduler consumer
```

The future scheduler must depend only on `PassiveMarketUniverse` and its
resolved entry types. It must not read Spring property names, environment
variables or raw configuration objects directly.

No public API, runtime CRUD endpoint, persistence aggregate, broker call or
account synchronization is needed for Story 0239.

## Configuration Shape

The V1 operator-owned configuration should contain a list of entries under a
Market Intelligence passive-universe prefix. Each entry must contain:

- `marketId`: canonical UUID;
- `provider`: exact `MarketProvider` identity;
- `productType`: exact `InstrumentProductType` identity;
- `enabled`: explicit boolean;
- `cadence`: bounded duration or equivalent validated cadence value;
- `minimumEvidenceProfile`: named, validated evidence requirement for the
  future passive execution path.

The exact external property names should follow the repository's existing
Spring configuration format and be finalized during implementation. The raw
property structure must not leak past the adapter.

An empty or absent configured list resolves to a disabled empty universe. A
disabled entry remains a configuration diagnostic and is not silently treated
as an enabled resolved entry. Configuration must reject malformed required
fields, non-positive cadence, unknown enum values, duplicate identity keys and
conflicting identity claims.

The configuration must not contain an account ID, broker-account ID, user ID,
credential reference or synchronization requirement.

## Resolution Algorithm

The resolver should perform the following deterministic steps:

1. Bind and validate raw configuration.
2. Reject entries missing `marketId`, provider or product type.
3. Normalize only representation-safe values such as collection ordering;
   do not normalize provider/product identity by guessing aliases.
4. Reject duplicate `(marketId, provider, productType)` keys.
5. Load the current Market Data catalogue through `findAllMarkets()`.
6. Resolve each enabled entry by exact `marketId`.
7. Require the resolved catalogue record's provider and product type to match
   the configured values exactly.
8. Record explicit diagnostics for unknown, disabled, unavailable or conflicting
   entries. Do not merge entries that share a symbol but differ by provider or
   product type.
9. Sort resolved entries by a stable key, preferably canonical market ID with
   provider and product type as deterministic tie-breakers.
10. Serialize the canonical resolved configuration and compute a stable
    fingerprint using the repository's established hashing convention, or a
    versioned SHA-256 representation if no convention exists.
11. Return an immutable `PassiveMarketUniverse` containing the ordered entries,
    configuration version, fingerprint, resolution timestamp and diagnostics.

The resolver should fail closed for invalid identity conflicts. A valid
configuration may still produce an empty resolved universe when all entries are
disabled or unavailable, provided the diagnostics make that outcome explicit.
The contract must distinguish configuration invalidity from a temporarily
unavailable catalogue so operational behavior can be decided by Story 0240.

## Suggested Application Contracts

The exact names may be adjusted to existing package conventions, but the
boundary should expose equivalent immutable records:

```text
PassiveMarketUniverse
  configurationVersion
  fingerprint
  resolvedAt
  entries: List<PassiveMarketEntry>
  diagnostics: List<PassiveMarketUniverseDiagnostic>

PassiveMarketEntry
  marketId
  provider
  productType
  cadence
  minimumEvidenceProfile

PassiveMarketUniverseDiagnostic
  marketId or configured identity when available
  code
  severity
  message or stable reason code
```

Records should defensively copy collections and should not expose mutable
configuration objects. Story 0240 can persist the ordered entry snapshot and
fingerprint without coupling its run model to Spring configuration.

## Conflict and Availability Semantics

The following cases must be explicit and testable:

- Missing canonical market ID, provider or product type: configuration error.
- Duplicate canonical/provider/product tuple: deterministic configuration
  error.
- Same canonical market ID with a different provider or product type: identity
  conflict; never merge or silently select one record.
- Canonical market ID absent from the current catalogue: unresolved/unknown
  diagnostic; do not fabricate a market.
- Catalogue provider/product differs from configuration: identity conflict;
  do not resolve the entry.
- Entry disabled: disabled diagnostic and no resolved scheduling entry.
- Catalogue or Market Data unavailable: explicit unavailable diagnostic; do not
  infer that the market is resolved or dispatchable.
- Operationally closed, halted or stale market state: preserve the resolved
  identity and defer dispatch eligibility to Story 0240; do not discard the
  configured identity during Story 0239 resolution.

The resolver must preserve provider/product distinctions even when symbols are
equal, such as a spot instrument and CFD sharing a display symbol.

## Ordering and Fingerprint

The ordered result must be independent of configuration file ordering and
catalogue response ordering. The canonical serialization used for the
fingerprint must include the configuration version and every resolved entry's
identity and operational selection fields, including cadence and evidence
profile. It must exclude timestamps and diagnostics that are not part of the
selection policy so repeated equivalent resolution produces the same
fingerprint.

The fingerprint algorithm and serialization version must be named constants or
equivalent explicit contract fields. A future change to the serialization or
selection semantics must change the version and therefore the fingerprint.

## Tests Required by the Story

Targeted Market Intelligence tests should cover:

- valid single and multi-entry resolution;
- absent and empty configuration producing a disabled empty universe;
- missing market ID, provider and product type;
- malformed UUID, enum and cadence values;
- duplicate canonical/provider/product entries;
- same canonical ID with conflicting provider or product type;
- unknown canonical market ID;
- catalogue identity mismatch;
- disabled and unavailable entries with explicit diagnostics;
- deterministic ordering despite different input and catalogue order;
- stable fingerprint for equivalent configuration;
- fingerprint change when identity, cadence, evidence profile or version changes;
- immutable returned entry and diagnostic collections;
- no account, broker synchronization or active-scan eligibility dependency.

Configuration binding tests should follow the existing properties-test style and
verify fail-fast validation. A focused adapter test should mock
`MarketDataClient.findAllMarkets()` and prove that the scheduler-facing
contract is independent of raw framework properties.

No Story 0240 scheduler test or runtime scheduling implementation belongs in
this Story.

## Handoff to Story 0240

Story 0240 should consume only the resolved immutable contract. It must use the
entry cadence, market identity, configuration version and fingerprint to create
the persisted run snapshot, then apply operational state, evidence freshness,
budget, retry and in-flight checks. Story 0239 must not implement those gates,
dispatch, durable run state or execution lifecycle.

## Risks and Open Decisions

- The repository has no existing passive-universe configuration namespace or
  fingerprint convention. Implementation should choose one explicitly and add
  focused tests rather than infer defaults.
- `MarketResponse` omits `providerSymbol`; this is acceptable for the current
  Story 0239 identity contract, but any later provider-routing need must be
  addressed through an explicit Market Data contract change.
- The exact evidence-profile vocabulary is not established by current source;
  it should be constrained to the needs of Story 0240 and not become a hidden
  broker capability model.
- Story 0239 must not turn current active account-aware eligibility into a
  passive authorization rule.

## Validation Plan

Before implementation approval:

- review this analysis against Story 0239 acceptance criteria and ADR-102;
- validate documentation links and headings;
- run `git diff --check`;
- confirm only approval metadata and this Repository Analysis are changed.

During the later implementation phase, run targeted Market Intelligence
configuration/resolution tests and the repository-standard Java validation for
the affected module. No runtime activation or broker validation is required
for this Story.
