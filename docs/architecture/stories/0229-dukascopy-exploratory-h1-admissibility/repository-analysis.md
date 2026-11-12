# Story 0229 - Repository Analysis

## Purpose

Assess the current repository before preparing an Implementation Plan for the
Dukascopy-specific implementation of accepted ADR-096. This analysis does not
authorize implementation, dataset generation, ML execution, KMeans or any
change to accepted ADRs or existing HistData behavior.

## Workflow Context

- Story `0229` exists at
  `docs/architecture/stories/0229-dukascopy-exploratory-h1-admissibility/story.md`.
- The Story scope was explicitly approved by the human. This does not approve
  this Repository Analysis or a future Implementation Plan.
- Current branch is `main` at commit `373dcff391607310bf02f442bc91fbfabc1e6c1c`.
- The worktree contains the new ADR-096, four untracked ML-01.1 reports and the
  Story 0229 directory. These artifacts are pre-existing context for this
  analysis and must not be discarded or silently folded into implementation.
- No application-code changes are present in the current worktree.
- The DevLog Story Agent was unavailable in this tool environment. Repository
  evidence, accepted ADRs, Story 0229, Story 0228 and existing ML-01 artifacts
  were used as the authoritative current context.
- The Obsidian vault was not consulted; repository artifacts are sufficient for
  this local Java boundary analysis.

## Authoritative Context

### ADR-096

ADR-096 is accepted and defines the provider-scoped research direction:

- profile `ML01_DUKASCOPY_EXPLORATORY_V1`;
- 55/60 minimum unique valid M1 observations;
- first and last minute required;
- exact duplicate collapse with metadata;
- conflicting duplicates, invalid OHLC, malformed data and chronology failures
  rejected;
- missing-minute positions and maximum missing run recorded;
- no additional missing-run rejection threshold;
- no interpolation, forward-fill, backfill or synthetic candles;
- `FULL_OBSERVED`, `PARTIAL_EXPLORATORY`, `REJECTED` and `UNKNOWN_QUALITY`;
- `CONTIGUOUS_ONLY` continuity only;
- no assumed Dukascopy session transitions;
- feature and outcome availability checked independently;
- diagnostic 24-H1 counts not treated as joint eligible population;
- no authorization for ML-01.2 or KMeans.

### ADR-093

ADR-093 remains authoritative for ML-01 scientific semantics:

- four Java-owned features;
- 33 valid H1 observations for feature calculation;
- H+4, H+12 and H+24 valid-observation horizons;
- future outcomes excluded from feature construction and fitting decisions;
- chronological partitions, purging and embargo required before scientific
  evaluation;
- strict and exploratory populations remain distinct;
- an unavailable or insufficient population must fail closed.

### ADR-094

ADR-094 remains authoritative for strict historical evidence and continuity:

- `CONTIGUOUS`, `VERIFIED_CLOSURE`, `UNEXPLAINED_GAP`,
  `INVALID_OBSERVATION` and `UNKNOWN_CLOSURE_EVIDENCE` remain distinct;
- closure verification requires scoped, versioned, fingerprinted evidence;
- a calendar pattern or elapsed-time shape is not closure evidence;
- unexplained and unknown gaps reject continuity-dependent windows;
- no consumer-side exploratory decision may rewrite strict Market Data facts.

### ADR-095

ADR-095 is an accepted HistData-specific exploratory protocol. Its quality-state
names and research-only separation are useful conceptual precedents, but its
fixed UTC-05 source semantics and weekend-session assumption must not be reused
for Dukascopy. Existing HistData behavior must remain unchanged.

### Story 0228

Story 0228 acquired the Dukascopy source independently from scientific
eligibility. It defines:

- Dukascopy M1 BID data;
- provider timestamps interpreted as UTC;
- the 12-month bounds
  `[2025-10-01T00:00:00Z, 2026-10-01T00:00:00Z)`;
- deterministic monthly artifacts and normalized CSVs;
- no source repair, provider merging, synthetic prices or HistData session
  policy reuse.

The dataset fingerprint is
`c212d83c0050374400af8e8c3d9961a2bbcf0f9169fd2c149c0513fc3002f198`.

## Current Implementation Evidence

### Dukascopy M1 reader

`DukascopyOfflinePipeline` is located at:

`market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata/DukascopyOfflinePipeline.java`

It currently provides:

- UTC CSV parsing with explicit `timestamp,open,high,low,close,volume` header;
- minute-aligned timestamp validation;
- positive OHLC and non-negative volume validation;
- exact duplicate collapse;
- conflicting duplicate quarantine;
- sorted accepted M1 observations;
- source-level missing-interval diagnostics;
- per-import quality events and source fingerprints;
- multi-file assembly and cross-file overlap reporting;
- deterministic dataset fingerprinting with provider, side and normalization
  version.

It does not currently provide:

- UTC H1 bucket aggregation;
- H1 bucket-level missing-minute positions;
- first/last minute status per bucket;
- maximum consecutive missing-minute run per bucket;
- original strict H1 quality state or continuity classification;
- a Dukascopy research admissibility decision;
- a Dukascopy research dataset manifest or row bundle.

The reader's `assemble(...)` method reports cross-file overlaps but does not
itself reject them. The implementation plan must define the research-boundary
precondition for an overlap-free instrument assembly without weakening the
existing reader contract.

### HistData pipeline and admissibility contracts

`HistDataOfflinePipeline` already provides a richer, but source-specific,
contract:

- fixed UTC-05 source parsing;
- `AggregationState` values `COMPLETE`, `INCOMPLETE`, `INVALID` and
  `SESSION_UNCERTAIN`;
- H1 aggregation;
- `H1BucketQualityEvidence` with missing positions, boundary flags, maximum run,
  source fingerprint, strict state and local classifications;
- `Ml01H1AdmissibilityEvaluator` returning the four research quality states;
- `Ml01H1AdmissibilityDecision` with stable rejection reasons and provenance.

The evaluator's coherence checks hardcode HistData's `LocalDateTime` and
UTC-05 close-time relationship. It cannot be reused unchanged for Dukascopy.
The profile constant also names `ML01_HISTDATA_EXPLORATORY_V1`, so sharing the
constant would conflate two provider policies.

The planning stage must choose the smallest safe reuse or extraction boundary.
It must not copy the HistData parser, silently change its source semantics, or
make a generic quality-policy framework without an approved architectural need.

### Existing ML-01 preparation

`Ml01HistDataPreparation` already demonstrates the required research output
shape:

- feature-only and future-horizon processing;
- H+4, H+12 and H+24 outcome calculations;
- quality and continuity provenance;
- row and dataset fingerprints;
- `dataset-manifest.json`, `quality-report.json`, partition and readiness
  artifacts;
- explicit `REQUIRES_SCIENTIFIC_APPROVAL` readiness when partition parameters are
  absent;
- no canonical `FeatureSnapshot` publication.

However, it is not provider-neutral. It hardcodes HistData filenames, source
profile, source semantics, normalization version, source checksums and the
ADR-095 session continuity policy. It must not be changed casually to accept
Dukascopy. The plan must either isolate a Dukascopy preparation boundary or
extract only genuinely provider-independent pure contracts while preserving
existing HistData output and fingerprints.

### Features

`FeatureProjectionV2` defines the four required feature names and schema. The
actual deterministic OHLC calculations are in
`domain/volatility/VolatilityCalculator`:

- ATR period: 14;
- ATR baseline length: 20;
- total required history: 33 observations;
- exact adjacent timestamps unless verified closure gaps are explicitly allowed;
- normalized ATR, realized volatility, compression ratio and directional
  efficiency.

The calculator accepts `HistoricalOhlcContext` and `OhlcPoint`, both of which
are provider-neutral value contracts. The future Dukascopy preparation can
adapt its UTC H1 records into these values without changing formulas. The plan
must preserve the research-only provenance and avoid creating canonical
`FeatureSnapshot` evidence from exploratory partial candles.

### Outcomes

`MarketOutcomeCalculator` exposes `market-outcome-v1`, but its current public
contract calculates forward return, future high/low returns and future range.
The existing ML-01 HistData preparation additionally calculates future realized
volatility, future directional efficiency and absolute future net return inline.

This is a material contract boundary. The Implementation Plan must verify exact
compatibility and decide whether to reuse an existing deterministic calculation,
extract a pure shared calculation, or keep a Dukascopy research-local adapter.
It must not silently substitute the existing `MarketOutcomeCalculator` outputs
for ADR-093's three required ML-01 outcomes or change an accepted definition.

## Existing Tests And Validation

Relevant existing tests use Java 21, Maven, JUnit 5, AssertJ and temporary
filesystem fixtures:

- `DukascopyOfflinePipelineTest`: UTC parsing, exact/conflicting duplicates,
  missing intervals, cross-file overlap and fingerprints;
- `HistDataOfflinePipelineTest`: source validation, duplicate handling,
  chronology, aggregation and session-uncertain buckets;
- `Ml01H1AdmissibilityEvaluatorTest`: threshold, boundary, provenance,
  contradiction and bucket-quality decisions;
- `Ml01HistDataPreparationTest`: features, outcomes, partial provenance,
  continuity, bundle validation and scientific partition gating;
- `Ml01ExploratorySessionContinuityEvaluatorTest`: HistData-specific session
  behavior, which must remain regression coverage and must not be repurposed for
  Dukascopy assumptions.

The relevant module is `market-intelligence`. Repository validation conventions
are documented in `TOOLS.md`; targeted Maven tests and `git diff --check` are the
minimum applicable checks. The full quality workflow may be required by the
Implementation Plan after the affected files are known.

## Required Implementation Boundary

The Implementation Plan should remain limited to a Dukascopy-specific research
boundary that:

1. consumes already parsed and assembled `DukascopyOfflinePipeline` M1 evidence;
2. reconstructs UTC H1 buckets without reparsing or repairing source data;
3. emits immutable bucket-level quality evidence;
4. evaluates `ML01_DUKASCOPY_EXPLORATORY_V1` independently from the HistData
   profile;
5. preserves strict quality and continuity classifications;
6. evaluates only `CONTIGUOUS_ONLY` continuity;
7. adapts eligible H1 records to the unchanged Java feature calculator;
8. computes or reuses exact ADR-093-compatible future outcomes;
9. produces separate diagnostics for feature, per-horizon and joint eligibility;
10. fingerprints source, policy, continuity, feature, outcome and bundle
    configuration;
11. returns explicit readiness blockers for unapproved partitions, purge,
    embargo and minimum effective samples;
12. keeps all outputs research-only and outside canonical production evidence.

The plan must not add:

- a second Dukascopy parser;
- a provider-neutral generic policy engine;
- HistData timezone or session assumptions;
- assumed Dukascopy closure transitions;
- production FeatureSnapshot writes;
- a new service, endpoint, MCP permission or database schema;
- model training, KMeans or clustering execution.

## Expected Contract Gaps

The planning stage must resolve these concrete gaps:

1. Dukascopy has M1 records and import-level diagnostics but no H1 aggregation
   evidence contract.
2. Existing H1 quality/evaluator contracts reference HistData source semantics
   and profile identity.
3. Existing ML-01 preparation embeds provider-specific manifest and continuity
   behavior.
4. Existing outcome definitions are split between the generic market outcome
   calculator and the ML-01 HistData preparation.
5. Existing `ResearchDatasetBuilder` requires canonical `FeatureSnapshot` and
   `STRICT_OBSERVED` inputs, so it cannot silently consume exploratory partial
   rows without violating the research boundary.
6. No Dukascopy-specific policy fingerprint or bundle validation contract exists.
7. No approved chronological partition, purge, embargo or minimum effective
   sample values exist for this dataset.

These gaps are implementation-planning decisions, not permission to broaden the
Story. If resolving any gap requires a material architecture or product
decision beyond ADR-096, the workflow must stop and request human guidance.

## Risks And Open Questions

- A careless H1 implementation could infer missingness from lossy OHLC instead
  of M1 positions.
- Reusing the HistData evaluator could apply UTC-05 semantics to UTC Dukascopy
  timestamps.
- Reusing HistData preparation wholesale could emit HistData policy IDs or
  assumed-session metadata in Dukascopy artifacts.
- Partial H1 candles may understate high/low, ATR, realized volatility,
  compression and directional-efficiency measurements.
- Missingness may be nonrandom and concentrated around provider session
  boundaries.
- Rolling row counts may overstate independent sample support.
- The accepted 55/60 policy direction does not establish scientific validity or
  authorize ML-01.2.
- Outcome calculations must preserve valid-observation horizons and must not
  substitute elapsed-time windows.
- Existing untracked reports and ADR-096 must remain preserved and outside any
  implementation diff unless explicitly included by later workflow scope.

No contradiction was found between Story 0229, ADR-096, ADR-093 or ADR-094.
The principal dependency is a provider-specific H1 quality/evidence contract
and a safe separation from the HistData preparation implementation.

## Validation Plan

The Implementation Plan should require at minimum:

- focused Dukascopy H1 aggregation and quality-evidence tests;
- profile tests for 54, 55, 59 and 60 valid minutes;
- boundary, duplicate, invalid, malformed, chronology and unknown-provenance
  tests;
- exact continuity and missing-H1 rejection tests;
- feature availability tests using unchanged `VolatilityCalculator` formulas;
- independent H+4, H+12 and H+24 outcome tests;
- joint eligibility and diagnostic-count separation tests;
- quality-stratum and instrument-separation tests;
- deterministic row, policy and bundle fingerprint tests;
- regression tests proving HistData and accepted session behavior are unchanged;
- targeted `market-intelligence` Maven tests;
- applicable quality verification and `git diff --check`.

No provider download, dataset repair, MCP execution, model training, KMeans or
broker/provider mutation is applicable to this Story.

## Recommendation

Repository Analysis is complete and supports preparing an Implementation Plan.
The Implementation Plan must first settle the smallest Dukascopy-specific
contract boundary and the exact outcome-calculation reuse strategy. It must not
start Java implementation until this analysis and the resulting plan receive
their respective human approvals.

The next workflow gate is explicit human approval of this Repository Analysis.

## References

- `docs/architecture/stories/0229-dukascopy-exploratory-h1-admissibility/story.md`
- `docs/architecture/stories/0228-dukascopy-historical-forex-acquisition/story.md`
- `docs/architecture/stories/0226-ml01-exploratory-h1-admissibility-evaluator/story.md`
- `docs/architecture/stories/0227-ml01-exploratory-session-continuity/story.md`
- `docs/architecture/adr/ADR-092-proposed-mcp-research-operational-control-boundary.md`
- `docs/architecture/adr/ADR-093-proposed-ml-01-chronological-experimental-protocol.md`
- `docs/architecture/adr/ADR-094-market-data-continuity-and-verified-closure-evidence-policy.md`
- `docs/architecture/adr/ADR-095-proposed-ml-01-exploratory-research-admissibility-protocol.md`
- `docs/architecture/adr/ADR-096-dukascopy-exploratory-h1-admissibility-and-continuity-policy.md`
- `docs/architecture/reports/ml-01.1-dukascopy-12-month-dataset-readiness-2026-11-12.md`
- `docs/architecture/reports/ml-01.1-dukascopy-gap-continuity-sensitivity-2026-11-12.md`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata/DukascopyOfflinePipeline.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata/Ml01HistDataPreparation.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/volatility/VolatilityCalculator.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/outcome/MarketOutcomeCalculator.java`
