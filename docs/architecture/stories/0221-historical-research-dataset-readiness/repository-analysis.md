# Repository Analysis - Story 0221

## Baseline

- Repository: `/home/ludo/workspace/trading-os`
- Current branch: `story/0220-research-lifecycle-observability`
- Current local HEAD: `b1b44720`
- Working tree: clean before this analysis artifact was created
- `origin/main`: `b21bc3fe`, merge commit for PR #275 / Story 0220
- Story `0221` is the next available story ID in
  `docs/architecture/stories/`
- Docker Compose services are running locally, including Market Data, Market
  Intelligence, three PostgreSQL containers and News Service.

The local branch is the merged Story 0220 source branch and is not the local
`main` branch. The remote integration branch proves Story 0220 is merged;
current local branch state must not be changed during analysis.

## Context Sources

Read:

- six Research Intelligence audit reports dated 2026-11-08;
- Story 0220 `story.md`, `implementation-plan.md` and
  `implementation-report.md`;
- ADR-056, ADR-057, ADR-058, ADR-060, ADR-061, ADR-062, ADR-063, ADR-069,
  ADR-079 and ADR-080;
- `TOOLS.md` and the current Java/Python implementation.

DevLog context could not be prepared: no repository DevLog mapping or DevLog
MCP capability is available in the current tool environment. The repository
and accepted ADRs are therefore the authoritative sources for this analysis.
The Obsidian vault was not consulted because the required evidence is
repository-local and no transverse note is needed to determine the current
scope or runtime limitation.

## Existing Research Path

```text
Market Data historical evidence
  -> strict historical reconstruction at an explicit cutoff
  -> deterministic analysis execution
  -> immutable FeatureSnapshot
  -> explicitly selected immutable ObservedMarketOutcome
  -> ResearchDatasetBuilder
  -> atomic versioned bundle export
  -> Python validator / diagnose / offline experiment
```

### Java reconstruction and export

- `HistoricalReconstructionPreflight` requests strict observed OHLC evidence,
  validates identity, provenance, cutoff eligibility, interval continuity and
  the one-hour future range needed for the outcome.
- `HistoricalReconstructionService` runs preflight, historical deterministic
  analysis, feature projection, outcome evaluation and controlled export under
  a persisted lifecycle lease.
- `ResearchDatasetBuilder` pairs one exact feature snapshot with explicitly
  selected outcomes for each configured horizon. It preserves exclusions for
  missing, immature, unavailable or mismatched outcomes and can require
  evidence known at or before the snapshot cutoff.
- `ResearchDatasetExportService` obtains snapshots from the canonical
  repository, resolves each selected outcome by fingerprint, derives the
  maximum context `knownAt` from the stored analysis execution and refuses
  feature evidence known after the snapshot cutoff.
- `ResearchDatasetBundleExporter` writes `dataset-manifest.json`,
  `samples.jsonl` and `README.md` through a temporary directory, verifies
  sample checksums and records population, feature schema, outcome schema,
  exclusion counts and deterministic fingerprint material.
- Existing manifest metadata does not currently include explicit coverage
  statistics, expected-versus-observed cadence, gap diagnostics, stale
  observation counts, effective sample size or overlapping-horizon diagnostics.

### Python consumer

`research/ml` is an offline consumer, not a data acquisition system. The
validator already checks:

- manifest and sample schema;
- sample checksum and dataset fingerprint;
- stable sample IDs and fingerprints;
- strict evidence policy and `knownAt <= evaluationCutoff` recursively in
  feature provenance;
- feature missingness and schema/type consistency;
- explicit outcome selection, maturity and provenance;
- duplicate semantic identities and declared counts.

`experiment.py` already performs chronological splitting, overlap purging,
train-only preprocessing, readiness gates and deterministic baselines. It
blocks insufficient datasets rather than emitting model metrics.

## Historical Evidence Inventory

| Evidence family | Owner / storage contract | Provider / identity | Retention and readiness evidence |
|---|---|---|---|
| OHLCV | Market Data `historical_ohlc_evidence` and strict range controllers | Provider-neutral market UUID, interval and open time; provider provenance retained | Immutable `CAPTURED` / backfill-aware records with `knownAt`, fingerprints and gap-aware range responses. Actual row count and date bounds are not verified in this run. |
| Quote / spread | Current Market Data facts and deterministic spread analysis | Canonical market identity; no separate verified historical quote dataset found | Contract exists for current context; historical persistent coverage for a research bundle is not proven. |
| Order book | Market Data `historical_order_book_evidence`, sequences, segments and replay | Provider, provider symbol, market UUID, sequence and local ordinal; `occurredAt` and `knownAt` | Immutable trusted evidence and bounded retention contracts exist. No runtime counts, sequence coverage or replay population are verified. |
| Funding / borrowing | Market Data `historical_funding_evidence`; broker fee schedule is separate | Market UUID, provider symbol, product type, effective interval and `knownAt` | Append-only fingerprinted repository and strict reads exist. No evidence that a populated research-compatible funding export exists. |
| Market structure / volatility / momentum | Market Intelligence deterministic capabilities and `FeatureSnapshot` projection | Feature schema `feature-projection-v1`, analyzer and artifact provenance | Contract and fixture support exist. No current persisted population is verified. |
| Feature snapshots | Market Intelligence `feature_snapshots` | Market UUID, evaluation cutoff, context and identity fingerprints | Immutable append-only contract and population query exist. Actual count and coverage are unknown because runtime reads returned 401. |
| Outcomes | Market Intelligence `observed_market_outcomes` | Complete identity: market, evaluation cutoff, horizon, definition version and outcome fingerprint | Immutable outcome versions and exact fingerprint lookup exist. Actual maturity/completeness counts are unknown. |
| Economic events | News Service `economic_events` | Source event identity, scheduled/actual timestamps, currencies and market IDs | Persistence and bounded query contracts exist. Provider synchronization is incomplete per audit; runtime population is unverified. |
| Financial news | News Service `financial_news_items` | Source item identity, published timestamp, currencies and market IDs | Persistence and bounded query contracts exist. Runtime population and historical temporal availability are unverified. |
| Opportunity / strategy outcomes | Market Intelligence opportunity and strategy persistence | User/account and market context, not a canonical ML label contract | Existing trading-domain outcomes must not be substituted for objective market outcomes in R3. |

### Data classification

- **Implemented persistence contracts:** OHLC evidence, order-book evidence,
  funding evidence, feature snapshots, objective outcomes, economic events and
  financial news.
- **Fixture-backed data:** one committed Java-generated bundle with one sample,
  one synthetic-looking market UUID, `PT1H`, strict observed policy and a
  valid fingerprint. It is suitable for cross-language contract validation only.
- **Actual local runtime data:** not established. Docker containers being up is
  not evidence that their tables contain usable historical data.
- **Actual deployed data:** not inspected or claimed.
- **Provider data not ingested:** possible for funding, order-book, news and
  economic history; this cannot be quantified without authorized provider or
  runtime evidence.

## Runtime Data Availability

Read-only GET requests were attempted against the running local Gateway for:

- `/api/v1/intelligence/research/readiness`;
- `/api/v1/intelligence/research/integrity`;
- `/api/v1/markets`;
- `/api/v1/news/events`.

All returned HTTP `401` without an authorized user or agent credential. Internal
service endpoints require service authentication and were not bypassed. No
database credentials were inspected, no SQL was executed, and no runtime state
was changed.

Therefore the current runtime verdict is:

```text
RUNTIME_DATA_NOT_VERIFIED
```

The historical audit's earlier approximately five-week OHLC / zero feature
snapshot / seven outcome observation is retained as historical, environment-
specific context only. It is not treated as current measured data.

## Temporal Integrity Assessment

The existing contracts preserve the required direction of evidence flow:

- OHLC and order-book models distinguish source/event time from `knownAt`.
- Strict historical selection requires `knownAt <= cutoff` and rejects current
  provider fallback.
- Feature projection excludes outcomes and future evidence from feature identity.
- Dataset export derives feature evidence availability from execution context
  sections and excludes snapshots whose maximum evidence `knownAt` is after the
  snapshot cutoff.
- Outcome maturity and future evidence remain separate from the feature
  snapshot and are validated by the Python consumer.
- Timestamps are normalized through `Instant` in Java and timezone-aware UTC
  parsing in Python.

Observed limitations:

- The bundle manifest does not expose a direct export-time temporal quality
  section for coverage, gaps, stale observations or duplicate diagnostics.
- `knownAt` availability is derived from context sections rather than a
  per-feature evidence inventory, so the current export can prove a bounded
  maximum but not explain every source-level gap in the manifest.
- `ResearchSamplingCadence.ONE_PER_FEATURE_SNAPSHOT` does not by itself define
  an expected physical candle cadence; gap reporting must not infer continuity
  from sample rows without an explicit evidence cadence.

## ML Readiness

| Experiment | Required evidence | Current conclusion |
|---|---|---|
| Volatility regime clustering | Dense, continuous OHLC-derived volatility features across multiple regimes and markets | Contractually possible; real coverage unverified; not ready to claim. |
| Market regime clustering | Versioned structure/trend features with stable cutoffs and enough non-overlapping windows | Feature projection exists; population and effective sample size unverified. |
| Liquidity regime detection | Trusted order-book replay with sequence continuity and explicit provider/local chronology | Replay contract exists; runtime evidence and calibration are unverified. |
| Anomaly detection | Comparable observations, explicit missingness and stable market identity | Feasible as a future offline experiment only after population measurement. |

No model will be trained in this Story. A future experiment must report
effective sample size and overlapping horizon effects rather than equating raw
row count with independent observations.

## Smallest Supported Implementation Scope

The inventory supports a narrow improvement after approval:

1. Extend the existing Python validation/diagnostic output, without creating a
   second validator, to emit a deterministic quality report containing counts,
   observed cutoff coverage, duplicate identity diagnostics, missingness,
   outcome completeness, provenance status, fingerprint and explicit
   `not_determinable` fields where the bundle contract lacks an expected cadence.
2. Add focused fixture tests for deterministic quality output, sparse history,
   large cutoff gaps, missing outcomes, multiple markets/timeframes and
   insufficient population.
3. Validate the existing Java export path against the committed fixture and
   document that no real bundle can be produced until authorized runtime data is
   available.

No Java schema or persistence change is justified yet. Adding expected cadence,
retention or source coverage fields to the canonical manifest would require a
separate contract decision unless the implementation proves existing metadata
is insufficient for the selected quality report.

## Risks And Stop Conditions

- Do not report fixture counts as real historical coverage.
- Do not query or mutate the databases by extracting credentials from the local
  stack.
- Do not start the controlled reconstruction command without explicit operator
  approval and a known market/cutoff/output root.
- Stop if quality reporting requires changing the canonical dataset format or
  inventing an expected cadence not established by existing evidence.
- Stop if an implementation would require provider-specific dependencies in the
  canonical research domain.

## Recommendation

Repository Analysis recommends approval for a plan focused on extending the
existing Python quality report and tests, while documenting the runtime data
blocker. The expected final Story verdict is currently
`RUNTIME_DATA_NOT_VERIFIED`; it may only change after authorized read-only data
access produces measured coverage and a validated controlled export.

## Workflow Gate

Repository Analysis is complete. Implementation planning must not begin until
the human explicitly approves this analysis.
