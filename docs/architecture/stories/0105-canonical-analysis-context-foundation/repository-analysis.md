# Story 0105 - Repository Analysis

## Analysis Status

Repository Analysis is complete and requires explicit human approval before an
Implementation Plan is produced. No implementation files have been changed by
this analysis.

## Repository State

- Current branch: `main`.
- `HEAD` and `origin/main`: `e744c5b48f80f238e18346f34081d6d4ce6fffc7`.
- The canonical Story 0105 was created at
  `docs/architecture/stories/0105-canonical-analysis-context-foundation/story.md`
  with status `Draft`, following explicit human authorization.
- Existing unrelated untracked reports for Stories 0073, 0074, 0098, 0099 and
  0100 are present and must remain untouched.
- `git diff --check` passes for the Story artifact.
- DevLog context could not be retrieved because the DevLog capability is not
  available in this environment. The current repository and accepted ADRs are
  authoritative for this analysis.
- The Obsidian vault was not consulted. The Story is bounded by current
  repository contracts and accepted architecture documents; no transverse vault
  guidance is required to establish the current implementation state.

## Repository Overview

Trading OS is split into independent Spring services and an Angular frontend.
The relevant path for Story 0105 is:

```text
Market Data ------------+
                        +--> Market Intelligence --> observations,
News Service -----------+                           opportunities, plans
Trading Core/Risk ------- separate authoritative financial context
```

Market Data owns normalized market facts, market identity, source timestamps,
freshness and market state. News Service owns normalized news and economic
calendar facts. Market Intelligence owns context assembly, capability
orchestration and promotion of derived results. Trading Core and Risk remain
authoritative for user/account financial context and authorization.

This ownership model is consistent with ADR-020, ADR-025, ADR-031 and ADR-048.

## Current Implementation

### Existing Intelligence Context

The current `market-intelligence` module already contains an
`IntelligenceContext` domain object:

`market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/IntelligenceContext.java`

It currently:

- stores a map of `ContextSectionType` to `ContextSection`;
- copies the map into an immutable map;
- exposes individual sections and the complete map;
- supports selecting a subset of sections.

It does not currently carry:

- canonical context identity;
- market identity at the envelope level;
- analysis purpose or context mode;
- an explicit evaluation cut-off;
- a context schema/version;
- policy, profile or normalization version references;
- a stable canonical serialization or fingerprint;
- a complete context-level diagnostic/quality summary;
- explicit bounded ordering rules.

### Existing Sections

`ContextSection` is an immutable record containing:

- section type;
- section status;
- sensitivity;
- optional typed payload;
- optional provenance;
- optional message.

`ContextSectionStatus` already distinguishes:

- `AVAILABLE`;
- `STALE`;
- `MISSING`;
- `UNAVAILABLE`;
- `UNSUPPORTED`;
- `INCOMPLETE`.

The current `blocksAnalysis()` method treats all statuses other than
`AVAILABLE` and `STALE` as blocking. This is useful but currently applies at
section level only; there is no context-level policy describing required versus
optional sections for each analysis purpose.

`ContextProvenance` currently contains only:

- `source`;
- `sourceOccurredAt`;
- `fetchedAt`.

The record is a useful base but does not yet capture source record identifiers,
contract/normalization versions, source provider identity separately from
service identity, or the context cut-off against which the evidence was
accepted.

### Current Context Assembly

`IntelligenceContextAssembler` receives an
`IntelligenceAnalysisRequest` and a list of `ContextRequirement` values. It:

- resolves one contributor per section type;
- loads each requested section once;
- converts an unknown contributor into `MISSING`;
- converts contributor runtime failures into `UNAVAILABLE`;
- returns the current thin `IntelligenceContext` map.

The assembler is therefore the correct existing extension point. A separate
context service or parallel assembly abstraction is not justified by the current
architecture.

`ContextContributor` already declares section ownership and a disclosure
descriptor. `DefaultContextDisclosurePolicy` verifies the Market Intelligence
service identity, maximum classification and consumer permissions. This is a
usable authorization boundary for future bounded AI/LLM disclosure, but it does
not yet provide canonical context identity or temporal validation.

### Current Request Envelope

`IntelligenceAnalysisRequest` currently contains:

- analysis ID;
- market ID;
- execution mode;
- objective;
- optional account ID;
- optional scan ID;
- optional scan market ID.

The analysis request has no explicit evaluation cut-off. Contributors can
therefore choose their own current time unless they independently establish a
boundary.

This is the central temporal gap for Story 0105. The request contains account
and scan identifiers for orchestration, but those identifiers must not cause
authoritative account or Risk facts to be embedded in the market Analysis
Context.

## Current Contributors And Source Contracts

### Market Snapshot

`MarketSnapshotContextContributor` obtains a price snapshot from Market Data and
maps it into `MarketSnapshotContext`. It preserves:

- canonical `marketId`;
- symbol;
- last price;
- bid and ask;
- tradability;
- occurrence timestamp.

`MarketDataSectionFactory` adds service provenance and capture time and marks a
snapshot stale based on the configured `intelligence.context.stale-after`
duration or the source status.

Current limitations:

- freshness is computed relative to fetch/current time, not a request-level
  evaluation cut-off;
- the snapshot payload does not preserve capture time itself;
- source/provider record identity and contract version are not present;
- the factory uses `Instant.now()` in one overload, which is not reproducible
  without a supplied clock;
- status handling is based on string values from the adapter response.

### Historical OHLC / Trend Context

`TrendContextRoleHistoryContextContributor` is already more temporally careful:

- it establishes one `boundary` from an injected `Clock`;
- acquires the configured roles;
- maps the responses using that boundary;
- retries bounded history expansion up to five attempts and 720 candles;
- preserves profile ID/version and rule version in the role-history payload;
- records source occurrence and boundary/fetch time in provenance.

The Trend Context input model already carries stronger fields such as a
fingerprint, role series, gap findings and profile/rule versions. This is useful
prior art for the canonical context contract.

However, its boundary is local to that contributor. The overall
`IntelligenceContext` does not expose or enforce the same boundary across the
market snapshot and news sections. The contributor also acquires each role via
the existing OHLC endpoint rather than consuming a single immutable source
snapshot.

### News

`NewsContextContextContributor` calls the internal News Service context endpoint
and maps normalized economic events and financial news into `NewsContext`.
It preserves source update/fetch timestamps and normalization versions inside
the event/item payloads and service-level source/fetch timestamps in
`ContextProvenance`.

It maps the News Service status into the existing section statuses and treats
runtime client failure as `UNAVAILABLE`.

Current limitations:

- the request does not provide a temporal cut-off to News Service;
- the contributor accepts the returned event/news window as delivered without
  proving that every item is at or before the analysis cut-off;
- service-level provenance is present, but there is no context-level digest of
  the complete ordered input;
- null/unknown statuses are handled conservatively but not through a shared
  typed status contract.

## Relevant Components

### Market Intelligence

Relevant code areas include:

- `domain/IntelligenceContext.java`;
- `domain/ContextSection.java`;
- `domain/ContextProvenance.java`;
- `domain/ContextPayload.java`;
- `domain/IntelligenceAnalysisRequest.java`;
- `application/context/IntelligenceContextAssembler.java`;
- `application/context/ContextContributor.java`;
- `application/context/DefaultContextDisclosurePolicy.java`;
- `adapter/marketdata/*ContextContributor.java`;
- `adapter/news/NewsContextContextContributor.java`;
- `domain/trendcontext/*`;
- capability, observation, opportunity and active-scan consumers.

The module has both JPA-backed entities and in-memory adapters. Observations,
analysis executions, opportunities, capability executions and several related
records have durable adapters, while artifact stores, some repositories and
metrics remain in memory. Story 0105 should not silently turn this into a
complete persistence redesign.

### Market Data

Market Intelligence consumes Market Data through:

`market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/MarketDataClient.java`

The client exposes market catalogue, price snapshots, OHLC and bounded market
facts. The existing provider-neutral response models preserve canonical market
IDs and relevant observation timestamps, but the current context assembly does
not consistently carry all of those fields to the context envelope.

### News Service

Market Intelligence consumes:

`GET /internal/v1/news/context/{marketId}`

through `NewsClient`. The normalized response contains event/news arrays,
status, service-level source/fetch timestamps and item-level source update,
publication and fetch timestamps. It is an appropriate source contract for the
Story, but temporal filtering against an analysis cut-off must be made explicit.

### Trading Core And Risk

ADR-031 makes Trading Core and Risk authoritative for account-specific planning
and financial authorization. Market Intelligence may produce planning inputs,
observations, opportunities and Trade Plans, but it must not assemble an
authoritative financial Risk snapshot.

The new Analysis Context must therefore remain a market/intelligence evidence
contract. If an analysis requires account or Risk information, it must consume
the separate authoritative boundary rather than adding those facts to this
contract.

## Dependencies

### Existing Dependencies

- `ADR-020` shared context and orchestration responsibility.
- `ADR-021` AI execution governance and least-privilege disclosure.
- `ADR-022` freshness and artifact reuse.
- `ADR-025` immutable observation and evidence lineage.
- `ADR-031` separation of planning context from financial Risk context.
- `ADR-033` passive/active analysis orchestration.
- `ADR-048` source authority, temporal integrity, immutability and provenance.
- Existing Market Data market identity, snapshot and OHLC contracts.
- Existing News Service normalized event/news contracts.
- Existing Trend Context input and fingerprint implementation.

### Required Decisions For Implementation Planning

The implementation plan must resolve, without expanding into a new service:

- exact envelope fields and value-object boundaries;
- whether `evaluationCutoff` is added to `IntelligenceAnalysisRequest`, a
  separate analysis context command, or both;
- canonical section ordering and canonical serialization rules;
- whether source references are identifiers only or may include bounded payload
  digests;
- required versus optional sections per analysis mode;
- whether a context fingerprint uses the existing `ArtifactFingerprint` utility
  or a dedicated canonical serializer;
- how current contributors reject or exclude future facts;
- which downstream observations and opportunities persist the context identity.

These are implementation details within the approved Story scope, except where
they require a new persistence or service boundary. Such a boundary would need
a separate ADR and human approval.

## Architecture Alignment

The Story is aligned with accepted architecture:

- Market Intelligence remains the context assembler and orchestrator.
- Market Data and News Service remain fact owners.
- Deterministic analysis remains reproducible and fail-explicit.
- AI/LLM capabilities receive policy-selected bounded context rather than
  autonomous service access.
- Observations remain immutable in meaning and traceable to evidence.
- Risk and execution authority remain outside Market Intelligence.

The current code is partially aligned:

- immutable records and copied lists/maps are already used;
- section status and provenance exist;
- disclosure policy exists;
- Trend Context has a stronger temporal input and fingerprint model.

The current code is not yet sufficient for the Story:

- no context-level cut-off or identity exists;
- contributor boundaries are not globally coordinated;
- freshness and temporal checks are inconsistent across contributors;
- provenance is too coarse for complete reproducibility;
- canonical ordering/serialization/fingerprint is absent at context level;
- context-level minimum requirements and quality are not explicit;
- some source values are obtained using current time directly;
- downstream lineage does not consistently reference one canonical context ID.

## Risks

### Temporal And Look-Ahead Risk

The largest risk is combining a current price snapshot, historical candles and
news returned at different times while treating them as one analysis input. A
local contributor clock is not enough; the context envelope must establish one
evaluation boundary and every source adapter must prove compliance.

### Provenance Loss

Adding only an envelope fingerprint without preserving source IDs, source times,
fetch times and normalization versions would create a reproducible hash of an
incomplete input, not a reproducible analysis.

### False Completeness

`STALE`, `INCOMPLETE`, `UNAVAILABLE` and `MISSING` must not collapse into a
neutral section or an apparently valid empty list. Optional degraded context may
be allowed, but the resulting quality must remain explicit.

### Scope Expansion

The existing domain has multiple persistence adapters and context-like objects.
Attempting to persist every raw source payload or redesign all Trade Planning
and Risk context in this Story would exceed the approved scope and conflict with
ADR-031.

### Provider Leakage

Source/provider metadata is required for lineage, but provider payloads, symbols,
transport behavior and provider-specific status interpretation must remain in
infrastructure adapters.

### Serialization Stability

Jackson polymorphic payloads currently use `@class` as the type property. A
fingerprint based on incidental Java/Jackson serialization would be fragile
unless the canonical representation and type names are explicitly controlled.

### Boundedness

OHLC expansion is already bounded to 720 candles per role. News and other
sections require equivalent explicit limits before being disclosed to
capabilities or future models.

## Decision Matrix

| Option | Description | Architectural fit | Temporal/reproducibility quality | Scope risk | Recommendation |
|---|---|---:|---:|---:|---|
| A | Extend existing `IntelligenceContext` with an immutable envelope, cut-off, version, canonical section rules and fingerprint | High | High | Low | **Preferred** |
| B | Replace it with a new parallel `AnalysisContext` model and migrate all consumers immediately | Medium | High | High | Reject for now |
| C | Keep section-local temporal/fingerprint logic and document conventions only | Medium | Low | Low | Reject |
| D | Create a new shared Context Service or persistence boundary | Low | Potentially high | Very high | Reject; separate ADR required |

Option A preserves the existing Market Intelligence boundary and makes the
smallest coherent change. It should reuse current section payloads,
`ContextContributor`, disclosure policy and Trend Context input metadata while
adding the missing envelope semantics. Option A does not imply that every source
must be persisted in this Story.

## Recommendations

1. Extend the existing `IntelligenceContext` rather than create a second context
   abstraction or service.
2. Introduce an explicit request/context evaluation cut-off supplied by the
   orchestration boundary; do not let each contributor select its own current
   time.
3. Define a typed context envelope containing market identity, purpose/mode,
   cut-off, version, section quality and stable identity.
4. Define a canonical representation for fingerprinting that uses explicit field
   ordering, stable type names, normalized timestamps and bounded lists.
5. Preserve source and producer lineage at section and item/evidence level where
   available; do not replace missing source metadata with fabricated values.
6. Define required/optional section policies per analysis mode and make minimum
   context failures explicit.
7. Adapt Market Snapshot and News contributors to the shared cut-off before
   adding new data sources.
8. Reuse the Trend Context input's existing validation and fingerprint patterns,
   but do not move Trend Context-specific semantics into generic context code.
9. Add context identity references to derived observation/capability lineage
   where the existing persistence contract can support them without a migration
   decision beyond this Story.
10. Keep full durable raw-source snapshot storage, training data, labels and
    model infrastructure as separate follow-up work.

## Open Questions

- Should the context cut-off be mandatory for every analysis mode, including
  manually initiated current analyses?
- Which sections are required for passive scans, active scans, deterministic
  capabilities and future AI/LLM capabilities respectively?
- Should a stale section with a payload be disclosed as usable evidence, or only
  as a diagnostic reference, for each consumer?
- What source identifiers are guaranteed by Market Data and News Service and
  which must be added to their provider-neutral contracts?
- Should context identity be a UUID, a content fingerprint, or both?
- Which context metadata must be persisted with observations and opportunities in
  the first implementation?
- Is a canonical JSON representation acceptable for fingerprinting, or should a
  typed deterministic serializer be used to avoid Jackson implementation
  details?
- What is the approved bounded item count for news and other future sections?
- Does the existing internal service authentication configuration permit the
  required context metadata without changing trust boundaries?
- Is durable context snapshot storage required for the first operational use, or
  can source references and bounded immutable payloads satisfy the Story?

## Recommended Next Stage

Proceed to an Implementation Plan only after explicit human approval of this
Repository Analysis.

The Implementation Plan should be limited to Option A: extending the existing
Market Intelligence `IntelligenceContext` boundary, adding focused temporal and
reproducibility semantics, and migrating the current contributors and lineage
consumers without introducing a new service, provider integration, AI engine or
Risk context.

No implementation, migration, ADR creation or plan has been started yet.
