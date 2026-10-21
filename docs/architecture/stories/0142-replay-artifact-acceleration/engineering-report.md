# Engineering Report - Story 0142

## Outcome

The implementation update is complete, but Story 0142 is not closed until fresh
independent review. The existing checkpoint-aware replay path is retained and
documented. The artifact-first path now validates canonical payloads at promotion,
uses HMAC integrity at read time, and protects sequence eligibility with a
transactional PostgreSQL lock.

The implementation changed the Market Data replay path and internal replay contract,
plus the Market Intelligence caller contract. No schema migration, index, or service
boundary change was required.

## Evidence

- The controlled PostgreSQL dataset contained approximately `49.75 M` evidence
  rows and `28 GB` of evidence-table storage.
- The representative canonical replay scanned approximately `256 k` rows in
  `654 ms`.
- Starting from the latest valid checkpoint scanned approximately `35 k` rows in
  `218 ms`.
- This measured an approximately `86%` reduction in scanned rows and an
  approximately `67%` reduction in measured query time.
- The promoted-artifact table contained no rows in the production-like measurement
  dataset, so no reuse population or workload repetition justified a production
  performance claim for artifact-first lookup.
- A PostgreSQL Testcontainers benchmark with 10001 evidence rows measured `639395 us`
  canonical versus `5833 us` artifact-first, a `109.61x` speedup.

## Validation

- Targeted replay, persistence, retention, sequence, and lifecycle tests passed.
- The PostgreSQL-backed persistence test passed with 13 tests in a disposable
  PostgreSQL 16 Testcontainer.
- The complete `market-data` Maven test suite passed after the PostgreSQL test
  change.
- PostgreSQL-backed checkpoint inventory and canonical-versus-checkpoint
  measurements passed.
- `git diff --check` passed.
- The new artifact-first source path requires a fresh code review.
- Full replay-result equivalence and forged-payload rejection tests passed.

## Architecture

ADR-063 and ADR-067 remain sufficient for the confirmed checkpoint behavior.
ADR-069 was accepted by the human engineer on 2026-10-21. Existing checkpoints
remain derived replay acceleration state and do not replace canonical evidence.

## Residual Risk

The repository does not yet provide a durable promoted-artifact reuse policy for
repeated frozen research identities. Any future implementation must first provide
representative repeated-workload measurements and explicitly preserve dependency,
invalidation, rebuild, degraded-state, and retention semantics.

## Human Boundary

The Story remains open pending fresh independent review. The prior review findings
were addressed in the implementation update. No commit, push, merge, or ADR
acceptance was performed by the agent.
