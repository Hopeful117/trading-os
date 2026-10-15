# Story 0105 - Implementation Plan

## Plan Status

This Implementation Plan was prepared after explicit approval of the Story 0105
Repository Analysis and was explicitly approved before implementation began.
Implementation and validation are complete; human review remains pending.

## Implementation Boundary

Implement the canonical Analysis Context inside the existing
`market-intelligence` module by extending `IntelligenceContext` and its current
assembly boundary.

The implementation will:

- add an immutable context envelope around the existing typed sections;
- establish one evaluation cut-off for an analysis request;
- preserve and validate section provenance and availability;
- produce a stable context fingerprint from explicit canonical inputs;
- adapt existing Market Data, Trend Context and News contributors to the shared
  boundary;
- attach the context identity to downstream capability/observation lineage where
  an existing contract supports it without a persistence redesign;
- preserve current provider-neutral service boundaries.

The implementation will not create a new service, provider integration, AI
Engine, ML pipeline, model store, vector store or complete raw-source snapshot
persistence system.

## Design Decisions

### Extend `IntelligenceContext`

Retain `IntelligenceContext` as the canonical domain type. Add an immutable
envelope containing at minimum:

- canonical market ID;
- analysis mode/purpose;
- evaluation cut-off;
- context schema/version;
- section requirements or quality summary;
- immutable sections;
- stable `ArtifactFingerprint` or equivalent SHA-256 identity.

The existing section payload records remain typed and provider-neutral. No second
parallel `AnalysisContext` model will be introduced.

### Explicit Evaluation Boundary

Add an explicit evaluation cut-off to the orchestration request/context-building
boundary. The assembler will pass that boundary to contributors through the
existing request contract or a narrowly scoped context-build command.

The implementation must not use `Instant.now()` as an implicit analysis cut-off
when a caller-provided boundary exists. A `Clock` remains injectable for local
current-time defaults and tests.

All contributor responses must be validated against the same boundary before
they are accepted into the context. Facts newer than the boundary become an
explicit diagnostic or unavailable/incomplete result and are never silently
included.

### Canonical Fingerprint

Reuse `domain.artifact.ArtifactFingerprint` where its semantics are sufficient.
Build fingerprint inputs explicitly rather than hashing incidental Java object or
Jackson serialization.

Canonical inputs will include, in deterministic order:

- context schema/version;
- market ID, analysis mode and purpose;
- evaluation cut-off;
- section type and status;
- section policy/requirement identity;
- provenance values and source references available at the boundary;
- payload values in bounded, contract-defined order;
- relevant normalization, profile, rule and calculation versions.

If the existing helper cannot represent a required typed value without losing
meaning, add a focused canonicalizer in Market Intelligence rather than changing
the generic helper for unrelated consumers.

### Availability And Minimum Context

Retain the existing section statuses. Add context-level quality/diagnostic
calculation based on required and optional `ContextRequirement` values.

The assembler will preserve payloads for statuses that allow safe degraded
disclosure, while keeping the status and diagnostic visible. It will not create
neutral values for missing, future, stale or unavailable facts.

The first implementation will use the existing requirements supplied by each
analysis strategy. It will not invent a new global list of required sections.

### Provenance

Extend provenance only with metadata that is available from current normalized
contracts or required to identify the accepted context input, such as:

- source/provider identity where distinct from service identity;
- source record/reference identifiers where already exposed;
- normalization or contract version;
- explicit evaluation cut-off association.

Do not copy raw provider payloads into Market Intelligence. Do not fabricate
source identifiers or timestamps.

### Downstream Lineage

Add context identity references only to existing capability/observation lineage
contracts where this can be done without changing ownership or requiring a new
database boundary. If a durable schema migration is required for a downstream
record, stop and report the boundary rather than silently expanding the Story.

## Technical Tasks

### 1. Domain Envelope

- Add immutable value objects for context identity/version and context-level
  quality/diagnostics if existing types cannot express them.
- Extend `IntelligenceContext` with the envelope fields and immutable defensive
  copies.
- Validate required fields, market identity consistency and non-future cut-off
  values.
- Define deterministic section ordering for exposure and fingerprinting.
- Preserve `select(...)` behavior while making the selected context identity and
  quality explicit.

### 2. Request And Assembly

- Extend `IntelligenceAnalysisRequest` or introduce a focused context-build
  command with an explicit evaluation cut-off.
- Update `IntelligenceContextAssembler` to establish one boundary and pass it to
  every contributor.
- Keep missing contributors as explicit `MISSING` sections.
- Keep contributor failures as safe `UNAVAILABLE` results without leaking raw
  exceptions.
- Compute the context-level fingerprint only after section validation and
  canonical ordering.

### 3. Contributor Adaptation

- Update `MarketDataSectionFactory` to accept an explicit evaluation boundary and
  injectable clock rather than relying on `Instant.now()` for accepted evidence.
- Update `MarketSnapshotContextContributor` to reject or classify future snapshot
  observations explicitly.
- Preserve the existing Trend Context contributor boundary, profile/version,
  role metadata and fingerprint while linking it to the canonical context
  boundary.
- Update `NewsContextContextContributor` to apply the same cut-off to service
  and item/event timestamps where the contract provides enough information.
- Preserve provider-neutral status mapping and bounded collections.

### 4. Consumer And Disclosure Compatibility

- Update deterministic capability input mapping to consume the enriched context
  without reimplementing source ownership or temporal policy.
- Ensure context disclosure continues to enforce classification, consumer and
  Market Intelligence service identity.
- Ensure future AI/LLM ports can receive a selected, bounded context view and
  context identity, without adding an AI implementation.
- Keep account, Risk, broker and execution authority outside this context.

### 5. Lineage Integration

- Identify existing capability execution, artifact, observation and opportunity
  records that already carry input identity or provenance.
- Add the canonical context fingerprint/reference where the current contract can
  accept it without a schema redesign.
- Preserve existing artifact fingerprints and Trend Context fingerprints; do not
  replace their domain-specific identities.

### 6. Tests

Add or extend focused tests for:

- immutable envelope and defensive copies;
- market identity and cut-off validation;
- future source facts being rejected or explicitly degraded;
- identical accepted inputs producing identical fingerprints;
- changed cut-off, status, provenance or payload changing the fingerprint;
- stable section ordering independent of contributor/list order;
- required versus optional section failures;
- missing contributor and runtime failure behavior;
- stale, incomplete, unsupported and unavailable sections;
- Market Data snapshot timestamp/provenance mapping;
- News item/event timestamp and normalization-version preservation;
- Trend Context compatibility and shared cut-off behavior;
- existing capability, passive scan, active scan and observation regressions.

## Expected File Change Set

Expected implementation files, subject to the plan remaining within the stated
boundary:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/IntelligenceContext.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/IntelligenceAnalysisRequest.java`
- new focused domain value objects under
  `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/ContextProvenance.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/context/IntelligenceContextAssembler.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/MarketDataSectionFactory.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/MarketSnapshotContextContributor.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/marketdata/TrendContextRoleHistoryContextContributor.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/news/NewsContextContextContributor.java`
- affected capability/observation lineage classes only when the existing domain
  contract supports a focused reference addition
- focused tests adjacent to the changed classes

Expected Story documentation after implementation:

- `implementation-report.md`
- `runtime-validation-report.md` only if runtime validation is executed and
  produces meaningful evidence

No Market Data, News Service, Trading Core, Risk, Gateway or frontend files are
expected unless implementation proves that an existing provider-neutral contract
cannot carry required source metadata. Such a finding is a stop condition, not
permission to expand the plan silently.

## Validation Sequence

1. Run focused Market Intelligence context, contributor, fingerprint and
   capability tests while iterating.
2. Run the full module validation:

   ```bash
   cd market-intelligence && ./mvnw test
   ```

   Use the repository's available Maven wrapper or equivalent executable command
   if `./mvnw` is not present.

3. Run module verification with coverage when the implementation is complete:

   ```bash
   cd market-intelligence && ./mvnw verify
   ```

4. Run affected Market Data or News Service tests only if their provider-neutral
   contracts are changed or their regression behavior is implicated.
5. Run `git diff --check`.
6. Inspect the complete diff and verify that unrelated untracked reports remain
   untouched.
7. If an executable local stack and deterministic fixtures are available, verify
   reproducible context assembly, stale/unavailable behavior and absence of
   future facts. Do not require live provider calls for this Story.

The `market-intelligence` Maven build declares an 80% JaCoCo bundle coverage
threshold and a Sonar project key `trading-os:market-intelligence`; applicable
quality validation must report those checks accurately.

## Stop Conditions

Stop and request human guidance instead of expanding the implementation if:

- a new service or cross-service persistence boundary appears necessary;
- Market Data or News Service must change provider acquisition rather than only
  expose existing normalized metadata;
- a database migration is required for a downstream context reference and its
  ownership is not already established;
- current contracts cannot distinguish source occurrence from capture/fetch time;
- safe no-lookahead behavior cannot be guaranteed for a source section;
- a global required-section policy would change product behavior without an
  explicit decision;
- Risk, account, broker, execution or authorization facts need to be embedded in
  the market context;
- AI/ML, model training, RAG, embeddings or provider-specific behavior becomes
  necessary;
- existing consumers require an incompatible public API change;
- unrelated worktree changes would need modification or removal.

## Rollback Strategy

- Keep the change isolated to the Story 0105 implementation files.
- Preserve existing section payload types and contributor interfaces wherever
  compatibility is possible.
- If a context consumer cannot migrate safely, retain the existing assembly path
  only temporarily within the same change and stop for a revised plan; do not
  create permanent duplicate context authorities.
- No database migration or cross-service deployment coupling is introduced by
  this plan.
- Human review can discard the uncommitted Story 0105 implementation diff
  without affecting the unrelated untracked reports.

## Expected Outcome

Market Intelligence will expose one canonical, immutable and provider-neutral
Analysis Context whose accepted evidence is tied to one evaluation boundary,
whose quality and provenance are explicit, and whose identity can be reused by
deterministic capabilities and future bounded model consumers.

The implementation will improve temporal safety and reproducibility without
turning Market Intelligence into a source-data owner, Risk authority, broker
adapter or AI engine.

## Approval Boundary

Implementation must not begin until the human explicitly approves this current
Implementation Plan. Approval authorizes only the implementation boundary above;
it does not authorize a new service, database redesign, provider integration,
AI/ML work, commit, push, merge or pull-request creation.
