# Code Review - Story 0142

## Verdict

`PASS WITH FINDINGS - REMEDIATED`

## Findings

The independent review covered the artifact-first lookup, PostgreSQL-backed
Testcontainers coverage, and optional replay request parameter.

The checkpoint path remains materially beneficial. The new path reads a matching
trusted artifact, excludes invalidated sequences at repository lookup, validates
market/depth/cutoff/provenance, and falls back to canonical replay on mismatch.

ADR-063 and ADR-067 remain sufficient for the implementation. ADR-069 is accepted.

## Validation

- Targeted replay, persistence, and retention tests: passed.
- Full `market-data` Maven test suite: passed after the PostgreSQL Testcontainers
  test change.
- PostgreSQL-backed `OrderBookRetentionPersistenceTest`: passed, 14 tests.
- PostgreSQL checkpoint inventory and query-plan measurements: passed.
- `git diff --check`: passed.
- Independent review: final review completed with `PASS WITH FINDINGS`; the remaining
  released-dependency re-promotion finding was remediated and targeted/full validation
  was rerun.

## Independent Findings

1. **Major - artifact payload semantic verification is incomplete.** Artifact
   validation checks metadata, fingerprints, and persisted evidence fingerprints,
   but does not reconstruct canonical evidence and compare the stored book payload.
2. **Major - invalidation can race with artifact validation.** Eligibility,
   fingerprint, and persisted-evidence checks are separate repository calls. A
   concurrent invalidation can occur after eligibility is checked and before the
   artifact is returned.
3. **Major - full replay-result equivalence is not tested.** Current assertions do
   not cover every timestamp, ordinal, provider, normalization, trust, cutoff, and
   source-fingerprint field.
4. **Major - representative artifact performance measurement is missing.** The
   production-like PostgreSQL dataset still contains no promoted artifacts, so no
   material artifact-first performance claim is established.

## Remediation Implemented

- Canonical payload reconstruction is now required during artifact promotion.
- Artifact fingerprints use versioned HMAC-SHA256 integrity validation at read time,
  with constant-time comparison.
- Artifact-first replay runs inside one transaction and locks the eligible PostgreSQL
  sequence row with `FOR SHARE` until the response is constructed. The transaction
  is intentionally not read-only because PostgreSQL rejects row-locking statements
  in a read-only transaction.
- PostgreSQL tests compare the complete replay result, exercise concurrent invalidation,
  and measure a 10001-row replay; the latest observed median was `639395 us` canonical
  versus `5833 us` artifact-first.

The original implementation findings are addressed. The remaining open Story gap is
representative production-scale artifact reuse measurement. Human approval remains
required before the Story can be marked complete.

## Review Outcome

The Story remains open pending human approval. The implementation commit is created on
the dedicated Story branch; no push or merge was performed.
