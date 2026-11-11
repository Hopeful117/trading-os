# Story 0228 - Implementation Report

## Result

The Dukascopy acquisition workflow and Java normalized-data reader are
implemented. The requested full-year network acquisition completed for both
instruments with 24/24 monthly artifacts validated and no failed months.

The generated dataset remains under the ignored local `histData/` directory and
was not added to Git.

## Acquisition Contract

- Provider: Dukascopy through pinned `dukascopy-node` `1.51.1`.
- Timeframe: M1.
- Price side: BID.
- Bounds: `[2025-10-01T00:00:00Z, 2026-10-01T00:00:00Z)`.
- Provider timestamps were interpreted as UTC epoch milliseconds.
- Acquisition used month-level artifacts, bounded batches, request pacing,
  retries and persistent provider cache.
- Empty daily provider responses were accepted as non-trading-day inputs; an
  entirely empty month remained a hard failure.
- Existing raw/month artifacts are never silently replaced. Normalized files
  are written atomically and reruns reuse validated artifacts.

## Reused And Added Components

- Reused `ArtifactFingerprint` for deterministic SHA-256 fingerprints.
- Added `tools/dukascopy/acquire.mjs` for provider acquisition, raw/cache
  preservation, normalization, diagnostics and manifest generation.
- Added `tools/dukascopy/acquire.test.mjs` for offline utility tests.
- Added `DukascopyOfflinePipeline` for Java UTC CSV representation, OHLCV
  validation, duplicate quarantine, missing-interval diagnostics and assembly.
- Added `DukascopyOfflinePipelineTest` for Java boundary, conflict, gap,
  overlap and fingerprint behavior.
- Did not modify `HistDataOfflinePipeline`, `Ml01HistDataPreparation`, ADR-092,
  ADR-093, ADR-094 or ADR-095.

## Dataset Artifacts

Manifest:

`histData/dukascopy-20251001-20261001/acquisition-manifest.json`

Normalized data:

- `histData/dukascopy-20251001-20261001/normalized/eurusd/2025-10.csv` through
  `2026-09.csv`
- `histData/dukascopy-20251001-20261001/normalized/gbpusd/2025-10.csv` through
  `2026-09.csv`

Provider raw/cache data is preserved under the same output root in `raw/` and
`provider-cache/`. The manifest fingerprint is:

`c212d83c0050374400af8e8c3d9961a2bbcf0f9169fd2c149c0513fc3002f198`

## Coverage

| Instrument | Monthly artifacts | Normalized M1 records | Missing-minute diagnostics | Conflicts | Cross-file overlaps |
|---|---:|---:|---:|---:|---:|
| EUR/USD | 12 | 371,614 | 153,986 | 0 | 0 |
| GBP/USD | 12 | 371,266 | 154,334 | 0 | 0 |

The missing-minute counts include normal provider non-trading intervals and
intra-session gaps. They are diagnostics only. No weekend or holiday assumption
was promoted to verified closure, and no prices were interpolated or filled.

Cross-month diagnostics reported five gap intervals per instrument, all aligned
with provider calendar/session discontinuities around month boundaries. The
exact intervals and all monthly diagnostics are in the manifest.

The Java assembly validation produced these per-instrument fingerprints:

- EUR/USD: `768304ffd137ca4bbb739c49f865b3bd33252d3d7e9d3b526586d3fa33d35043`
- GBP/USD: `6b2cec8faf62c0b29cb1407e2823bbe253a6eb1ba7b6e227c0fa6e7752b83e6a`

## Validation

Passed:

- `tools/dukascopy`: `npm test` - 5 tests passed.
- Targeted Market Intelligence Maven tests including
  `DukascopyOfflinePipelineTest` - passed.
- Java full-dataset assembly validation - both instruments parsed, counts
  matched the manifest and cross-file overlaps were zero.
- Full-year acquisition - 24/24 artifacts validated, `complete: true`.
- `git diff --check` - passed after the implementation report update.

The first live probe exposed legitimate empty daily responses on non-trading
days. The downloader was corrected to accept those daily responses while still
rejecting empty monthly results; the full acquisition was then completed.

## Scientific Boundary And Remaining Blockers

- The dataset is an acquired and integrity-diagnosed provider dataset, not an
  ML-01 scientifically approved population.
- Dukascopy gaps remain provider diagnostics; no closure evidence or HistData
  UTC-05 policy was reused.
- H1 aggregation, ML-01 admissibility, chronological partitions, KMeans and
  model training were not run.
- The next ML-01 experiment requires explicit scientific approval of partition,
  purge, embargo and effective-sample parameters, plus a decision on how this
  provider's integrity diagnostics are handled.
