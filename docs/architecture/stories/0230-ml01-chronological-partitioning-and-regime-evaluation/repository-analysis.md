# Story 0230 - Repository Analysis

## Purpose

Assess the current repository before preparing an Implementation Plan for the
ADR-097 ML-01 chronological partitioning, temporal-dependence and regime
evaluation protocol. This analysis does not authorize implementation, dataset
execution, scaling, KMeans, model selection or held-out evaluation.

## Workflow Context

- Story `0230` exists at
  `docs/architecture/stories/0230-ml01-chronological-partitioning-and-regime-evaluation/story.md`.
- The Story scope was explicitly approved by the human on 2026-11-12. This does
  not approve this Repository Analysis or a future Implementation Plan.
- ADR-097 is accepted with human scientific approval recorded on 2026-11-12.
- The current branch is
  `story/0229-dukascopy-exploratory-h1-admissibility`, tracking
  `origin/story/0229-dukascopy-exploratory-h1-admissibility`.
- The worktree is dirty with pre-existing Story 0229 implementation changes,
  ADR-096 file transition artifacts, ML-01.1 reports and the new ADR-097. These
  files must not be reverted, folded into Story 0230, or modified as unrelated
  cleanup.
- DevLog context was not available in this tool environment. Repository
  artifacts, accepted ADRs, Stories 0228/0229/0230, current source and reports
  were used as the authoritative context.
- The Obsidian vault was not consulted; current repository evidence is sufficient
  for this local implementation-boundary analysis.

## Authoritative Context

### ADR-097

ADR-097 establishes the following accepted protocol boundary:

- common UTC chronological boundaries;
- 70/15/15 as the primary candidate and 60/20/20 as a predeclared sensitivity;
- one pooled KMeans model with instrument identity excluded from features;
- actual row dependency footprints;
- future-label purge based on dependency timestamps;
- no-embargo and 24-hour candidate embargo diagnostics;
- temporal overlap and non-overlapping-anchor diagnostics;
- train-only scaling and KMeans fitting;
- candidate `k` values 2 through 6 with explicit seeds and dependencies;
- validation-only model selection and an untouched final test;
- descriptive H+4, H+12 and H+24 held-out evaluation;
- separate 60/60, 59/60 and 55/60 quality sensitivity;
- explicit fail-closed behavior and research-only artifacts.

ADR-097 does not approve a winning partition, embargo duration, effective-sample
threshold, stability threshold, KMeans result or held-out conclusion. It also
records that the final post-fix real-data byte-for-byte comparison remains open.
The Implementation Plan must preserve these distinctions rather than turning
candidate diagnostics into scientific conclusions.

### ADR-093 and provider-specific policies

ADR-093 remains authoritative for:

- the four Java-owned features;
- one shared KMeans model across EUR/USD and GBP/USD;
- valid-observation horizons H+4, H+12 and H+24;
- exclusion of future outcomes from features, scaling and model selection;
- global and per-instrument diagnostics;
- non-operational exploratory semantics.

ADR-094, ADR-095 and ADR-096 remain authoritative for strict continuity,
provider-specific research quality and Dukascopy `CONTIGUOUS_ONLY` semantics.
Story 0230 must not apply HistData's UTC-05 session convention to Dukascopy.

## Current Dataset Evidence

The prepared Dukascopy source is:

```text
[2025-10-01T00:00:00Z, 2026-10-01T00:00:00Z)
```

Source fingerprint:

```text
c212d83c0050374400af8e8c3d9961a2bbcf0f9169fd2c149c0513fc3002f198
```

Latest ML-01.1 evidence:

| Instrument | Feature available | Joint available | Joint eligible |
|---|---:|---:|---:|
| EUR/USD | 3,530 | 2,145 | 0 |
| GBP/USD | 2,927 | 1,630 | 0 |

The primary population contains 48 EUR/USD segments and 39 GBP/USD segments,
with a longest segment of 64 anchors for both instruments. Feature lookbacks
and future windows overlap; raw anchor counts cannot be treated as independent
observations.

The latest scientific report records candidate diagnostics only:

| Instrument | Scenario | Train | Validation | Test |
|---|---|---:|---:|---:|
| EUR/USD | 70/15/15 | 1,495 | 308 | 327 |
| GBP/USD | 70/15/15 | 1,099 | 222 | 294 |
| EUR/USD | 60/20/20 | 1,273 | 340 | 515 |
| GBP/USD | 60/20/20 | 919 | 291 | 420 |

The strict 60/60 population has zero joint-available anchors for both
instruments. Partial-H1 windows materially dominate 55/60 feature availability.
These facts require explicit quality-stratum and dependence reporting.

## Current Implementation Evidence

### Java Dukascopy preparation boundary

The relevant package is:

`market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata/`

`DukascopyOfflinePipeline` provides the normalized M1 reader, source validation,
duplicate handling, chronology diagnostics, source checksums and dataset
fingerprints. `DukascopyH1ResearchPipeline` reconstructs UTC H1 buckets from the
parsed M1 evidence and preserves observed minute positions, missing positions,
boundary-minute flags, maximum missing runs, strict state and source
fingerprints.

`Ml01DukascopyH1AdmissibilityEvaluator` and its Dukascopy-specific profile
provide the accepted `ML01_DUKASCOPY_EXPLORATORY_V1` quality decision. The
implementation preserves `FULL_OBSERVED`, `PARTIAL_EXPLORATORY`, `REJECTED` and
unknown/fail-closed behavior without importing HistData session assumptions.

### Existing Dukascopy ML-01.1 row contract

`Ml01DukascopyPreparation` currently provides:

- the four required features through the unchanged Java `VolatilityCalculator`;
- H+4, H+12 and H+24 future outcomes;
- actual future open timestamps in each `Outcome`;
- feature lookback start/end and feature-window counts in each `Row`;
- feature-only and joint-available flags;
- strict/exploratory quality and continuity metadata;
- row, configuration, source and bundle fingerprints;
- `observations.jsonl`, `dataset-manifest.json`, `quality-report.json`,
  `readiness.json`, `partition-manifest.json` and `source-metadata.json`;
- explicit `jointEligible = false`, `kmeansExecuted = false` and
  `modelTrainingExecuted = false` readiness state.

The current row contract does not expose an explicit future dependency end for
each horizon. It stores future open timestamps but not a dedicated `futureEnd`
field. It also has no partition assignment, purge reason, embargo reason,
segment identifier, overlap diagnostics, non-overlapping-anchor sensitivity or
effective-sample report. These are concrete contract gaps for Story 0230.

The current preparation path calculates rows directly from in-memory M1/H1
imports. It does not provide a Dukascopy bundle reader or a dedicated published
bundle validator comparable to the HistData path.

### Existing ML-01.1 runner

`Ml01DukascopyExperimentRunner` is a bounded offline Java entry point that:

- validates the acquisition manifest, checksum, provider, timeframe, price side,
  bounds, dataset fingerprint and per-instrument record counts;
- reads all 24 normalized monthly files;
- generates the 60/60, 59/60 and 55/60 preparation bundles;
- writes an ML-01.1 summary with KMeans and model-training flags set to false.

It is hard-coded to the current source fingerprint, manifest SHA-256, date range
and expected record counts. That is appropriate for the bounded ML-01.1
validation runner but is not yet a versioned ML-01.2/ML-01.3 experiment
configuration or a general Dukascopy bundle consumer.

### Existing HistData preparation boundary

`Ml01HistDataPreparation` demonstrates useful research artifact patterns:

- immutable row and outcome provenance;
- partition configuration with common boundaries;
- partition manifests and readiness artifacts;
- future-window boundary purging;
- permitted within-partition overlap reported as dependence;
- train/test readiness states and explicit scientific approval blockers;
- deterministic row and dataset fingerprints.

It is not a safe Dukascopy entry point. It embeds HistData filenames, source
semantics, UTC-05 behavior, session-transition metadata and HistData policy
fingerprints. Story 0230 may reuse genuinely provider-independent pure concepts
only after the Implementation Plan demonstrates that existing HistData behavior
and fingerprints remain unchanged. A wholesale change to this class would risk
cross-provider policy leakage.

### Python research package

`research/ml` is the repository's existing offline Python consumer and declares
`numpy`, `pandas` and `scikit-learn` dependencies. It already uses
`StandardScaler` inside a scikit-learn preprocessing pipeline and contains
chronological/purged split tests for the older research baseline.

The current Python experiment is not an ML-01.2 implementation:

- it consumes the `research-dataset-bundle-v1` format with `samples.jsonl`, not
  the Dukascopy `observations.jsonl` bundle;
- its validator requires the older manifest, feature and outcome schema;
- it is fixed to a BTC/USD Ridge baseline and a PT4H target;
- it uses an approximate row-count split and fixed four-hour purge;
- it has no KMeans, candidate-k selection, cluster stability or ML-01.3 regime
  evaluation;
- its existing tests prove the older baseline's train/test isolation, not
  ADR-097's dependency-footprint contract.

The repository therefore has an existing scientific Python runtime, but no
existing Dukascopy-compatible ML-01.2/ML-01.3 consumer contract.

## Architectural Boundary Assessment

The repository evidence supports a two-part implementation boundary, subject to
confirmation in the Implementation Plan:

1. **Java producer/validator:** keep source identity, H1 quality, feature and
   outcome computation, dependency timestamps, research policy metadata and
   deterministic bundle fingerprints in the Market Intelligence Java boundary.
2. **Offline Python consumer:** extend the isolated `research/ml` package with a
   dedicated, versioned Dukascopy ML-01 bundle adapter and KMeans/evaluation
   workflow using the declared scikit-learn dependency.

This is a supported interpretation, not an approved architectural decision. The
Implementation Plan must decide whether partition/purge validation is fully
materialized in Java, fully revalidated in Python, or split with a clear single
authority. It must avoid duplicate scientific policy logic that can drift across
languages.

No new service, database, Gateway route, MCP permission or production API is
required by the current evidence. Generated bundles and experiment outputs
remain offline artifacts outside Git.

## Required Contract Gaps

The Implementation Plan must resolve these gaps within Story scope:

1. Add or derive explicit per-horizon dependency end timestamps and preserve all
   actual future observation timestamps.
2. Define the versioned Dukascopy ML-01 experiment manifest independently from
   the older generic `research-dataset-bundle-v1` schema.
3. Define one authoritative partition/purge/embargo representation and its
   fingerprint material.
4. Add row-level exclusion reasons for boundary crossing, embargo, unavailable
   dependencies and out-of-range rows.
5. Add segment, overlap, non-overlapping-anchor and calendar/instrument
   diagnostics without presenting them as independent samples.
6. Define the handoff contract for four numeric features, instrument diagnostics,
   quality strata and H+4/H+12/H+24 outcomes.
7. Add train-only scaler and KMeans configuration/artifact provenance.
8. Add selection-freeze state that prevents test data from entering model
   selection.
9. Add a predeclared uncertainty/block configuration without inventing an
   effective-sample threshold absent from the approved decision.
10. Preserve the existing HistData Python and Java contracts unchanged.

If resolving one of these gaps requires changing an accepted ADR, production
contract or provider responsibility boundary, implementation must stop and seek
human guidance rather than broaden the Story.

## Risks

- A missing explicit `futureEnd` could cause a consumer to reconstruct purge
  windows incorrectly from elapsed time.
- Duplicating partition logic in Java and Python could produce different eligible
  populations or fingerprints.
- Reusing the old Python validator could reject valid Dukascopy artifacts or
  silently coerce them into the wrong BTC/USD schema.
- Reusing the old row-count split would violate common UTC boundary semantics.
- Partial-H1 windows dominate the available 55/60 population and may materially
  affect feature geometry and regime assignments.
- Segment concentration and overlapping windows can make cluster/outcome
  uncertainty look more precise than the evidence supports.
- Test contamination could occur through scaler fitting, model selection,
  quality-profile choice, threshold selection or artifact inspection.
- The final post-fix real-data reproducibility comparison remains incomplete.
- The current worktree contains unrelated uncommitted changes and generated
  reports that must remain outside the Story 0230 implementation diff.

## Proposed Validation Plan

The Implementation Plan should require, at minimum:

- focused Java tests for dependency-footprint serialization and fingerprinting;
- exact common UTC boundary and boundary-inclusivity tests;
- future-dependency crossing and non-crossing purge tests;
- no-embargo versus 24-hour-embargo separation tests;
- leakage-proof failure tests for deliberately crossing rows;
- feature-history overlap tests proving legitimate history is not mislabeled as
  future leakage;
- segment, overlap, non-overlapping-anchor and distribution diagnostics;
- train-only scaling tests with test-value mutation guards;
- KMeans candidate, seed, stability and selection-isolation tests;
- selection-freeze and untouched-test enforcement tests;
- quality-profile separation tests for 60/60, 59/60 and 55/60;
- repeated-run artifact and fingerprint reproducibility tests;
- research-isolation tests proving no canonical Market Data publication;
- targeted `market-intelligence` Maven tests;
- targeted `research/ml` virtual-environment tests when Python files change;
- applicable quality verification and `git diff --check`.

No provider download, broker access, MCP execution or live mutation is
applicable. Full real-data ML-01.2/ML-01.3 execution must remain blocked until
the relevant ADR gates and the implementation workflow authorize it.

## Recommendation

Repository Analysis is complete and supports preparing an Implementation Plan.
The plan should preserve Java as the owner of deterministic feature/outcome and
provenance evidence while defining a single, explicit handoff to the isolated
Python research runtime for KMeans and held-out evaluation. It must first settle
the exact bundle/manifest contract, dependency-footprint representation and
partition/purge authority before implementation begins.

No production code should be changed until this Repository Analysis and the
resulting Implementation Plan receive their respective human approvals.

The next workflow gate is explicit human approval of this Repository Analysis.

## References

- `docs/architecture/stories/0230-ml01-chronological-partitioning-and-regime-evaluation/story.md`
- `docs/architecture/stories/0229-dukascopy-exploratory-h1-admissibility/story.md`
- `docs/architecture/stories/0229-dukascopy-exploratory-h1-admissibility/repository-analysis.md`
- `docs/architecture/stories/0228-dukascopy-historical-forex-acquisition/story.md`
- `docs/architecture/adr/ADR-092-proposed-mcp-research-operational-control-boundary.md`
- `docs/architecture/adr/ADR-093-proposed-ml-01-chronological-experimental-protocol.md`
- `docs/architecture/adr/ADR-094-market-data-continuity-and-verified-closure-evidence-policy.md`
- `docs/architecture/adr/ADR-095-proposed-ml-01-exploratory-research-admissibility-protocol.md`
- `docs/architecture/adr/ADR-096-dukascopy-exploratory-h1-admissibility-and-continuity-policy.md`
- `docs/architecture/adr/ADR-097-ml-01-chronological-partitioning-temporal-dependence-and-regime-evaluation-protocol.md`
- `docs/architecture/reports/ml-01.1-dukascopy-prepared-dataset-scientific-validation-2026-11-12.md`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata/Ml01DukascopyPreparation.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata/Ml01DukascopyExperimentRunner.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata/Ml01HistDataPreparation.java`
- `research/ml/src/research_dataset/experiment.py`
- `research/ml/src/research_dataset/validator.py`
- `TOOLS.md`
