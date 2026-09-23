# Implementation Plan - Story 0062

## Plan Status

Implementation and focused validation are complete. Human code review and the
human commit remain pending. Production role acquisition, capability
registration, execution lifecycle and observation integration are intentionally
deferred to Story 0064.

## Implementation Boundary

Implement only the normalized source metadata, immutable Trend Context input
contract, role-aware mapping, validation, canonical fingerprint, and focused
regression coverage required by Story 0062.

Do not implement:

- pivot, HH/HL/LH/LL, structure, direction, regime, break, transition,
  pullback, EMA, ATR, extension, phase, alignment, or attention calculations;
- Trend Context capability registration or `AnalysisExecution` lifecycle;
- `IntelligenceObservation` persistence, supersession, or production read API;
- StrategyEvaluation, StrategyMatch, TradingOpportunity, Risk, TradePlan,
  execution, frontend, ML, news, or agent behavior;
- uncontrolled multi-timeframe production orchestration from Story 0064.

## Step 1 - Extend the Normalized OHLC Contract

### Goal

Make source versus synthetic status and acquisition provenance explicit at the
Market Data normalized boundary while preserving the existing OHLC behavior.

### Files/components likely changed

- `market-data/.../model/OhlcEvent.java`;
- `market-data/.../kraken/helper/KrakenRestOhlcMapper.java`;
- `market-data/.../kraken/helper/KrakenOhlcMapper.java`;
- `market-data/.../kraken/brokerClient/KrakenMarketData.java`;
- the narrow clock/provenance wiring needed to record local fetch time.

### Contract introduced/modified

Add explicit normalized fields:

- `synthetic`;
- deterministic `sourceId`;
- `fetchedAt`.

Keep existing fields and semantics. Provider-created candles set
`synthetic=false`. The normalizer-created candles set `synthetic=true`.
`occurredAt` remains source/provider occurrence or response watermark; it must not
be relabeled as local fetch time. Use an injected `Clock` for `fetchedAt`, never
wall-clock access hidden inside fingerprinting or domain validation.

The source ID must be provider-independent and stable for the available identity
tuple, for example provider/market/symbol/interval/open-time. Do not claim that
it identifies provider revisions because the current provider payload has no
revision identity.

### Tests added/updated

- Mapper tests assert provider candles are explicitly non-synthetic.
- Mapper tests assert provider, market, symbol, source ID, source occurrence, and
  fetch time are preserved.
- Existing websocket mapper tests assert the same additive metadata.
- Existing constructor fixtures are updated without changing their business
  assertions.

### Compatibility considerations

`OhlcEvent` is used by REST and websocket paths and is serialized directly by the
public Market Data controller. Keep all existing JSON fields and add metadata
only. Update all Java constructor call sites. Do not change endpoint paths,
interval names, or the 1..720 validation in this step.

### Completion condition

Every provider-created `OhlcEvent` has explicit non-synthetic/source/fetch
metadata, and all existing Market Data code compiles against the additive
contract.

## Step 2 - Harden Historical Normalization

### Goal

Preserve normalization facts and make gap and duplicate behavior deterministic.

### Files/components likely changed

- `market-data/.../service/OhlcHistoryNormalizer.java`;
- `market-data/.../service/OhlcHistoryNormalizerTest.java`;
- provider history tests in `KrakenMarketDataTest` and mapper tests as needed.

### Contract introduced/modified

Update normalization to:

- require/validate the requested interval against candle intervals;
- canonicalize ascending order;
- detect identical duplicates by `(interval, openTime)` identity and normalized
  content, collapse them, and retain a deterministic duplicate finding if the
  selected result type carries findings;
- reject conflicting duplicates rather than select first or last;
- fill gaps with explicit `synthetic=true` candles;
- preserve source/provider identity and source/fetch metadata;
- keep synthetic candles closed only as normalized placeholders, with the
  synthetic marker making them ineligible for deterministic calculations;
- avoid silently truncating required future profile requests. The existing 720
  service/provider limit remains authoritative.

If gap findings cannot be carried by the existing `List<OhlcEvent>` return type,
derive them deterministically from explicit synthetic candles in the dedicated
Trend Context mapper. Do not introduce a broad wrapper solely for generic legacy
consumers.

### Tests added/updated

- real candle remains `synthetic=false`;
- every inserted missing interval is `synthetic=true`;
- synthetic candle metadata and timestamps are asserted;
- ordering is deterministic;
- identical duplicates collapse and are visible to the dedicated mapper;
- conflicting duplicates fail explicitly;
- existing empty-history and tail-limit behavior remains valid;
- provider/source metadata survives gap creation.

### Compatibility considerations

The legacy normalizer still returns `List<OhlcEvent>` under Option A. Existing
generic consumers continue receiving a list. Their behavior is unchanged except
that the JSON/domain event now truthfully identifies synthetic entries.

### Completion condition

No normalized history candle can be mistaken for synthetic or real by inspecting
prices, and duplicate/gap behavior is covered by focused tests.

## Step 3 - Extend the Market Data HTTP Response Additively

### Goal

Expose the explicit normalized metadata to Market Intelligence without changing
the generic `HistoricalOhlcContext` contract.

### Files/components likely changed

- `market-intelligence/.../adapter/marketdata/OhlcResponse.java`;
- `market-intelligence/.../adapter/marketdata/MarketDataClient.java` only if a
  response contract or bounded request helper is required;
- Market Data controller contract test and new MI response fixtures.

### Contract introduced/modified

Add to `OhlcResponse` the normalized fields required by the domain mapper:

- `synthetic`;
- `sourceId`;
- `fetchedAt`;
- any explicit normalized source/watermark field required to distinguish it from
  candle `closeTime`.

Keep provider/symbol/interval/closed and all existing OHLC fields. Do not add
Trend Context roles or profile fields to this generic response. Do not change
`HistoricalOhlcContext`, `OhlcPoint`, `ContextSectionType`, or the legacy
contributor.

### Tests added/updated

- Market Data controller JSON test asserts legacy OHLC fields and new metadata.
- Feign/response mapping fixture deserializes metadata correctly.
- Existing generic historical-context tests, if introduced by this Story, prove
  they still map the old fields without requiring Trend Context metadata.

### Compatibility considerations

This is an additive wire contract. Existing consumers that ignore unknown JSON
fields remain compatible. The current generic MI contributor continues to discard
the extra fields by design.

### Completion condition

Market Intelligence can receive all source/synthetic/fetch fields needed for a
dedicated mapper, while generic history consumers remain unchanged.

## Step 4 - Add Immutable Trend Context Domain Values

### Goal

Create the domain-only input contract that a future pure engine can consume
without knowing HTTP, Spring, Market Data classes, or persistence.

### Files/components likely changed

New package:

`market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/trendcontext/`

Likely types:

- `TrendContextAssessmentInput`;
- `TrendContextRole`;
- `TrendContextRoleDefinition`;
- `TrendContextRoleSeries`;
- `TrendContextCandle`;
- `TrendContextProfile`;
- `TrendContextGapFinding`;
- `TrendContextFreshness`;
- `TrendContextSourceReference`;
- `TrendContextInputValidation` or a narrowly named validator/factory.

### Contract introduced/modified

Use final immutable classes/records with defensive copies and no infrastructure
dependencies.

`TrendContextAssessmentInput` must contain market identity, assessment/cut-off,
profile ID/version, rule version, immutable role series, provenance/freshness
facts, exclusions/gaps, and a computed SHA-256 fingerprint.

`TrendContextRoleSeries` must expose both all normalized candles and the derived
calculation-ready list. The latter includes only valid, closed, non-synthetic,
cut-off-eligible candles. The engine must not need to infer eligibility.

`TrendContextProfile.conservativeSwingV1()` carries exactly the accepted
4h/1h/15m role defaults and provisional parameter values. It performs only
configuration validation. Do not add retained algorithm output fields.

### Tests added/updated

Add domain tests for:

- immutability/defensive list and map copies;
- valid BIAS/SETUP and optional TRIGGER;
- missing BIAS/SETUP;
- optional missing TRIGGER;
- role interval ordering and duplicate interval rejection;
- invalid profile parameter values;
- malformed OHLC and impossible timestamps;
- explicit open/synthetic exclusion from calculation-ready evidence;
- insufficient eligible history;
- no future calculation evidence after the cut-off.

### Compatibility considerations

These types are new and are not introduced into legacy context payloads or
existing capability APIs. They must not import `OhlcEvent`, `OhlcResponse`,
Spring, Feign, JPA, Risk, account, broker, ML, or execution types.

### Completion condition

A future deterministic engine can receive an accepted input containing only
domain values and can never confuse an open or synthetic candle with eligible
calculation evidence.

## Step 5 - Add the Dedicated Market Data-to-Input Mapper

### Goal

Translate richer Market Data responses into `TrendContextAssessmentInput` while
keeping source ownership in Market Data and generic historical context unchanged.

### Files/components likely changed

New adapter/application types under:

`market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/`

Likely components:

- `TrendContextInputMapper`;
- a role-to-history request/response carrier if required by the existing style;
- a bounded response normalization helper only if not already covered by the
  domain factory.

### Contract introduced/modified

The mapper accepts an immutable role-to-`OhlcResponse` collection plus:

- selected `TrendContextProfile`;
- `assessmentAt`;
- `cutOffAt`;
- explicit rule version.

It maps provider strings to neutral source identity, copies all candle metadata,
derives gap findings from explicit synthetic candles, retains excluded evidence,
and builds the domain input. It must not fetch data, register a capability,
persist an observation, or access wall-clock time.

It must reject a response whose role interval does not match the profile, whose
market/provider identity conflicts across roles, or whose required role is
absent. Missing optional TRIGGER is represented as absence plus a deterministic
availability/provenance fact, not manufactured candles.

### Tests added/updated

Mapping tests prove preservation of:

- market ID and symbol;
- provider/source identity;
- role and interval;
- OHLCV and timestamps;
- `closed` and `synthetic`;
- source ID;
- source occurrence/watermark and fetch time;
- gaps and excluded-candle reasons;
- cut-off and profile/rule versions;
- calculation-ready filtering.

### Compatibility considerations

Do not replace or alter `HistoricalOhlcContextContributor`. The new mapper is a
parallel adapter used by later Trend Context orchestration. This prevents
legacy `ohlc-range-analysis` behavior from changing in Story 0062.

### Completion condition

Given deterministic per-role Market Data fixtures, the mapper creates the same
accepted domain input and preserves all required evidence without network or
runtime-clock access.

## Step 6 - Implement Canonical Fingerprinting

### Goal

Make identical accepted inputs and versions produce identical fingerprints and
make material evidence/version changes visible.

### Files/components likely changed

- `TrendContextAssessmentInput` or a dedicated domain
  `TrendContextInputFingerprint` helper;
- tests under `market-intelligence/src/test/java/.../trendcontext/`.

### Contract introduced/modified

Canonicalize:

- fixed role order;
- profile/rule/contract versions;
- source references;
- all candle fields and exclusion findings;
- canonical ascending candle order;
- canonical decimal and instant formats.

Use the repository's SHA-256 lowercase hexadecimal convention. Do not use random
IDs, persistence IDs, map insertion order, logging timestamps, or object identity.

### Tests added/updated

- same input replay gives identical digest;
- role map insertion order is irrelevant;
- allowable candle input order is irrelevant;
- material candle field changes alter the digest;
- synthetic/closed/source/fetch/cut-off/profile/rule changes alter the digest;
- no wall-clock drift occurs.

### Compatibility considerations

The existing `ArtifactFingerprint` and `StrategyEvaluationContext` conventions
remain unchanged. The Trend Context digest is a new domain fingerprint and does
not change legacy artifact or strategy digests.

### Completion condition

Fingerprint tests demonstrate replay determinism and material-change
differentiation.

## Step 7 - Regression Coverage and Validation

### Goal

Prove that the new contract is safe without starting Stories 0063 or 0064.

### Files/components likely changed

- focused Market Data tests under `market-data/src/test/java`;
- focused Market Intelligence domain/adapter tests under
  `market-intelligence/src/test/java`;
- only existing test fixtures whose constructors must reflect the additive
  `OhlcEvent` contract.

### Contract introduced/modified

No new production contract beyond Steps 1 through 6. No generic historical
consumer is migrated.

### Tests added/updated

Market Data:

- real candle remains real;
- synthetic gap creation is explicit;
- ordering and duplicate behavior;
- normalization compatibility;
- provider/source metadata preservation;
- controller response compatibility.

Input validation/mapping:

- valid BIAS/SETUP/optional TRIGGER;
- missing required roles and optional trigger absence;
- malformed OHLC and impossible timestamps;
- future/crossing-cut-off/open candle behavior;
- synthetic exclusion;
- conflicting duplicate;
- insufficient history and interval mismatch;
- source metadata/gap preservation.

Fingerprint:

- replay determinism;
- role/candle ordering canonicalization;
- material input/profile/rule changes.

Regression:

- existing `OhlcHistoryNormalizerTest` behavior remains valid where semantics
  are unchanged;
- existing Kraken history/mapper tests pass;
- existing generic `OhlcRangeAnalysisCapability` tests pass unchanged.

### Completion condition

Run targeted module tests and `git diff --check` successfully. A full root Maven
build is not expected because the repository has independent service builds.

## Validation Commands

The implementation pass should run, at minimum:

```text
mvn test                         # market-data
mvn test                         # market-intelligence
git diff --check
```

Commands must be executed from each module's directory or with the repository's
existing module-specific Maven invocation. Do not report a test as passing unless
it actually ran successfully.

No runtime sandbox, frontend, Strategy, observation, or execution validation is
part of Story 0062.

## Explicit Non-Goals

- No Trend Context algorithm.
- No changes to generic `HistoricalOhlcContext` or `OhlcPoint`.
- No production role-specific acquisition orchestration.
- No capability registration or AnalysisExecution lifecycle.
- No observation persistence or read API.
- No StrategyEvaluation, StrategyMatch, Opportunity, Risk, TradePlan, execution,
  frontend, ML, news, or agent work.
- No ADR or Story scope change.

## Plan Completion

The implementation is complete for Story 0062 only when:

- explicit source/synthetic/fetch metadata is present at the normalized boundary;
- the immutable domain input validates roles, evidence, cut-off, provenance, and
  profile without algorithm calculations;
- excluded evidence remains inspectable while calculation-ready evidence is safe;
- fingerprints are stable and material changes are detected;
- generic history consumers remain compatible;
- focused Market Data and Market Intelligence tests plus `git diff --check` pass;
- no Story 0063 or Story 0064 production behavior has been introduced.
