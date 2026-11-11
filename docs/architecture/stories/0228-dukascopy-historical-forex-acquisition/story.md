# Story 0228 - Dukascopy Historical Forex Acquisition And Dataset Assembly

## Metadata

**ID:** `0228`
**Title:** Dukascopy Historical Forex Acquisition And Dataset Assembly
**Status:** Implementation complete - human validation pending
**Priority:** P1 - Research Data Reproducibility
**Related ADRs:** `ADR-092`, `ADR-093`, `ADR-094`, `ADR-095`
**Related Stories:** `Story 0225`, `Story 0226`, `Story 0227`

## Goal

Acquire and validate a reproducible 12-month Dukascopy M1 dataset for EUR/USD
and GBP/USD from `2025-10-01T00:00:00Z` inclusive through
`2026-10-01T00:00:00Z` exclusive, while keeping provider acquisition separate
from ML-01 scientific eligibility.

## Context

The repository contains a local HistData-only research parser and ML-01
preparation path. No Dukascopy implementation or acquisition artifact exists.
Dukascopy's historical minute endpoint provides BID/ASK candle data keyed by
UTC calendar dates, but access is externally rate-limited and the downloader's
default empty-response behavior can silently lose daily artifacts. The new
workflow must therefore add explicit coverage and artifact validation rather
than trusting a successful process exit.

## Scope

- Add a controlled, pinned Dukascopy downloader utility; it is not a permanent
  service and does not add a Node.js microservice.
- Support configurable instruments, inclusive start and exclusive end dates,
  BID/ASK price side, output root, bounded concurrency and retry/backoff.
- Acquire one deterministic provider artifact per UTC month and preserve the
  provider cache/raw response separately from normalized output.
- Make reruns idempotent, recover interrupted months, refuse silent replacement
  of an existing artifact and record per-artifact SHA-256 checksums.
- Normalize provider M1 JSON records to a stable UTC CSV representation with
  explicit provider, instrument, price-side and normalization metadata.
- Detect malformed OHLC, invalid timestamp boundaries, duplicate timestamps,
  conflicting duplicates, ordering problems, missing minutes, month overlaps
  and cross-month continuity gaps without fabricating prices.
- Generate a deterministic acquisition manifest and per-instrument diagnostics.
- Reuse existing Java validation and ML-01 preparation contracts where their
  semantics apply, without importing HistData's UTC-05 policy into Dukascopy.

## Out Of Scope

- Changing ADR-092 through ADR-095 or accepted ML-01 thresholds.
- Treating Dukascopy gaps as verified closures or reusing the HistData weekend
  assumption.
- Interpolation, forward fill, backfill, synthetic candles or silent provider
  merging.
- H1 scientific eligibility approval, partition approval, KMeans or model
  training.
- A permanent Node.js service, new database, Gateway endpoint or MCP command.
- Committing large market-data artifacts to Git.

## Acquisition Contract

- Target interval: `[2025-10-01T00:00:00Z, 2026-10-01T00:00:00Z)`.
- Provider: Dukascopy historical candle endpoint through pinned
  `dukascopy-node` `1.51.1`.
- Timeframe: `m1`.
- Price side: `bid` by default and always explicit in manifests.
- Provider timestamps: epoch milliseconds interpreted as UTC.
- Expected raw months: twelve per instrument; empty provider responses are
  incomplete artifacts, not successful downloads.
- Normalized records: one UTC minute timestamp, open, high, low, close and
  volume, with deterministic ordering and no synthetic values.

## Acceptance Criteria

- [ ] EUR/USD and GBP/USD and arbitrary configured date bounds are supported.
- [ ] Every expected UTC month has a validated provider artifact or an explicit
      failed/missing status in the manifest.
- [ ] A rerun reuses byte-identical validated artifacts and refuses a changed
      replacement without explicit cleanup.
- [ ] Interrupted acquisition can resume without redownloading validated months.
- [ ] HTTP failures, 429 responses and empty responses use bounded retries and
      backoff and remain visible in diagnostics.
- [ ] Raw/provider artifacts and normalized files are separate and checksummed.
- [ ] Normalization rejects malformed records, invalid OHLC, out-of-range
      timestamps, conflicting duplicates and non-deterministic ordering.
- [ ] Diagnostics report per-month counts, missing minutes, duplicate/conflict
      counts, overlaps and cross-month continuity without interpolation.
- [ ] Dataset and manifest fingerprints are deterministic and include provider,
      instrument, bounds, timeframe, price side, tool version and file checksums.
- [ ] Unit tests cover boundaries, validation, idempotency, recovery and stable
      fingerprints without live network calls.
- [ ] The full target acquisition is attempted only after the workflow is
      implemented and tested; actual coverage and blockers are reported.

## Validation Boundary

Validation is local and offline for unit tests. The full acquisition is the only
network operation and must be run as a separate bounded workflow. Generated
market data remains under ignored local storage and is never added to Git.

## Definition Of Done

- [ ] Repository Analysis and Implementation Plan reconciled with the actual
      repository state.
- [ ] Acquisition utility, normalization and diagnostics implemented.
- [ ] Tests and affected-module validation pass.
- [ ] Full target acquisition attempted with actual record counts and checksums.
- [ ] Dataset manifest, fingerprints and remaining provider/scientific blockers
      documented.
- [ ] No commit, push or pull request performed automatically.
