# Story 0228 - Repository Analysis

## State And Provenance

- Current local branch is `story/0227-ml01-exploratory-session-continuity`.
- Local `main` is synchronized with `origin/main` at the merge of PR #284.
- The current worktree has two unrelated untracked ML-01.1 reports; they are
  preserved and excluded from Story 0228 changes.
- No Dukascopy Story, implementation, script, configuration, pull request or
  commit exists in the repository or accessible PR history.
- DevLog capability was unavailable in this tool environment; current
  repository state, accepted ADRs and existing Story/report artifacts are used.

## Existing Components

- `HistDataOfflinePipeline` parses the HistData semicolon format, applies a
  fixed UTC-05 source offset, validates OHLC, quarantines conflicting duplicate
  timestamps, sorts eligible observations and aggregates H1 candles.
- `Ml01H1AdmissibilityEvaluator` and `Ml01HistDataPreparation` implement the
  research-only ML-01.1/ADR-095 path, including source checksums, manifests,
  fingerprints and H1 feature/outcome preparation.
- `HistoricalOhlcEvidence` and related Market Data contracts are provider
  neutral, but their persisted service path is not a bulk Dukascopy downloader.
- `HistoricalClosureEvidenceSource` deliberately has no authoritative provider
  implementation. Dukascopy acquisition must not create closure evidence.
- `ArtifactFingerprint` provides deterministic SHA-256 fingerprinting and can be
  reused for manifests.

## Provider Evidence

The current Dukascopy documentation indicates:

- historical prices support `1min`, explicit `start` and `end` epoch
  milliseconds, UTC day starts and explicit BID/ASK sides;
- the Dukascopy Node downloader maps M1 to daily minute candle artifacts and
  emits `[timestamp, open, high, low, close, volume]` records;
- the downloader defaults do not safely fail on empty/non-200 artifacts, so the
  wrapper must require retries, reject empty months and validate expected
  coverage;
- HTTP 429 responses are rate-limited and require bounded backoff. A direct
  probe of the free instrument-list endpoint returned HTTP 429, so access is a
  practical risk and the full acquisition must preserve partial diagnostics.

## Architectural Decision

Use a standalone pinned Node utility under `tools/dukascopy` rather than a
permanent service or a second Java parser. The utility owns provider transport,
month batching, raw artifact persistence and normalization diagnostics. Java
ML-01 code remains unchanged and consumes only explicitly validated normalized
inputs in a later step.

This keeps Dukascopy's UTC and BID/ASK semantics explicit and prevents
HistData's fixed UTC-05 weekend convention from leaking into the new source.

## Risks And Blockers

- Provider rate limiting or WAF access may prevent a complete 12-month run.
- Dukascopy candle availability may contain legitimate weekend gaps; these are
  diagnostics only until a separate scientific closure decision exists.
- The existing `Ml01HistDataPreparation` expects fixed HistData filenames and
  must not be changed as part of acquisition unless a later approved adapter
  contract requires it.
- No large dataset artifact may be committed.
