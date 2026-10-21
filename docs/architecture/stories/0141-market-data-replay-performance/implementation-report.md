# Implementation Report - Story 0141

## Decision

**NO SOURCE CHANGE - TESTED SQL ACCELERATIONS REJECTED WITH EVIDENCE**

The Market Data replay bottleneck was measured against the live local PostgreSQL
dataset. The tested index alternatives improved the delta-read query in isolation,
but either duplicated most of the canonical evidence index or severely degraded
sequence selection. No candidate was safe to retain as a durable production
optimization under the current replay and retention boundaries.

## Baseline

The controlled `market-data` PostgreSQL database contained:

| Measure | Value |
|---|---:|
| Database size | `31 GB` |
| Order Book evidence rows | `49,536,141` |
| Capture sequences | `170` |
| Replay artifact rows | `0` |
| Evidence index `ix_historical_order_book_replay` | approximately `3.7 GB` |

The dominant bounded replay path used an active sequence with approximately
`155,000` evidence rows before the latest checkpoint and approximately `5,905`
rows after the checkpoint selected for the measured cutoff.

## Query Evidence

The current replay path was measured as separate phases:

- Sequence selection: approximately `290 ms` on the cold measured execution and
  approximately `22 ms` after warming the relevant pages.
- Latest trusted snapshot lookup: approximately `0.376 ms`.
- Delta loading after the checkpoint: approximately `242 ms` with the current
  index and plan.
- The current delta query used the existing index, filtered trusted evidence, and
  performed a merge/sort before returning the ordered rows.

The results confirm that repository evidence loading, not Java book assembly, is
the material replay cost.

## Candidate Results

### Partial Trusted Replay Index

A temporary partial index on trusted replay rows reduced the measured delta query
to approximately `157 ms`. It was rejected because all `49.6 M` observed rows
were trusted, so it duplicated almost the complete existing index and added
roughly `3.7 GB` of storage and write-maintenance cost without changing the
canonical data shape.

### Reordered Replay Index

A temporary index ordered by `sequence_ordinal` before `known_at` reduced the
isolated delta query to approximately `148 ms` and avoided the sort. It was not
safe as a replacement:

- when the existing index was retained, it added approximately `3.7 GB` of
  duplicate index storage;
- when used as the replacement, sequence selection degraded to approximately
  `11.5 s` because sealed-sequence `known_at` checks could no longer use the
  existing access path efficiently.

The temporary indexes were removed and the original
`ix_historical_order_book_replay` was recreated and verified `indisvalid=true`.

## Implementation Outcome

- No application source change.
- No migration retained.
- No index or cache authority added.
- No replay, sequence, cutoff, provenance, retention, or fail-closed semantics
  changed.
- No evidence rows were modified or deleted.

The next viable optimization requires a Market Data replay design that reduces
the amount of canonical evidence scanned without duplicating the current index
responsibilities. That may require a checkpoint-aware or promoted-artifact
strategy governed explicitly by ADR-067, with representative lifecycle and
retention validation before implementation.

This is the remaining gap recorded for the next Story. It is not implemented or
silently treated as a cache authority by Story 0141.

## Validation

- Baseline Market Data replay tests: passed.
- Full `market-data` Maven test suite: passed.
- PostgreSQL row-count, index inventory, and query-plan inspection: passed.
- Temporary index comparison and restoration: passed.
- Final original replay index validity check: passed.
- `git diff --check`: passed.

Mockito, H2, Hibernate, SpringDoc, and generated development-security warnings
were emitted by the test environment. No test failure was reported.

### Quality Gate

The `market-data` SonarQube analysis was executed. Its Quality Gate failed on
pre-existing project state:

- `new_coverage`: `77.1%` versus the required `80%`;
- `new_violations`: `39` versus the required `0`;
- `new_duplicated_lines_density`: `0.0%`, passed.

No Security Hotspots were reported. Because Story 0141 changed no application
source, these findings are recorded as a validation limitation and are not fixed
within this Story.

## Documentation Reconciliation

Documentation update: required and completed. This Story's implementation report
records the measured baseline, rejected candidates, operational trade-offs,
restored database state, and residual replay design requirement. No public API,
frontend, Gateway, or cross-service documentation changed because no application
behavior changed.

## Vault Outcome

- Vault consulted: no.
- Outcome: no vault action.
- Rationale: repository source, accepted ADRs, local PostgreSQL evidence, and
  Story 0140 measurements were sufficient.

## Review Status

Implementation, documentation reconciliation, and Code Review are complete with
the Quality Gate limitation recorded above. No commit, push, or merge was
performed.
