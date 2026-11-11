# Story 0228 - Implementation Plan

## Plan

1. Add a pinned `tools/dukascopy` package with a CLI and testable pure
   functions for month enumeration, record validation, normalization,
   diagnostics and fingerprint material.
2. Implement month-level acquisition through `dukascopy-node` with explicit
   BID/ASK configuration, bounded batches, request pacing, retries and 429
   backoff. Persist provider cache/raw responses and generated month files
   atomically; refuse checksum-changing replacement.
3. Normalize valid records into per-instrument UTC CSV files and generate a
   manifest containing coverage, checksums, counts and diagnostics.
4. Add unit tests using injected downloader responses; do not call the live
   provider from ordinary tests.
5. Run the utility's tests and existing affected Java tests.
6. Execute the requested full-year acquisition as a separate network step,
   preserve any partial result, and report actual coverage and blockers.

## Constraints

- No changes to ADR-092 through ADR-095.
- No reuse of HistData's UTC-05 or weekend assumptions.
- No synthetic observations, interpolation, forwarding or silent merging.
- No commit, push or pull request.
