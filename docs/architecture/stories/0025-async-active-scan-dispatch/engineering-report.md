# Engineering Report — Story 0025

## Outcome

Active Scan creation is now genuinely asynchronous. The declared contract
(202 Accepted + Location) is honored in reality; the full Kraken scope
(1 436 eligible markets) dispatches off-thread while Angular tracks progress
live and reaches an honest COMPLETED terminal state through the existing
read-time reconciliation.

## Key numbers

| | Before | After |
|---|---|---|
| POST wall time | ≈161 s → nginx 504 | **3.6 s → 202 + scanId** |
| Tracking | impossible | polling every 2 s |
| Terminal state | never reached | COMPLETED (1379/1379, opp=1329) persisted |

## DevLog evaluation (#46)

With a fresh investigation report committed and warm history,
`get_engineering_context` still did not surface the report nor the coordinator;
the decisive inputs came from repository reading. `search_project_history`
remains the strongest DevLog primitive for this repository.

## Reconciliation decision

SUFFICIENT — child write-back intentionally NOT added (Story constraint #28).
Follow-up documentation clarification suggested (not part of this story):
make the "child rows are dispatch-time snapshots; projection is authoritative"
design explicit in ADR/story docs.

## Remaining issues actually observed

- Full-scope scan duration ≈7 min (sequential worker loop by design, V1).
- Crash-after-202 without client retry leaves a non-terminal scan (V1 local
  dispatcher characteristic; documented in implementation-report).

## Suggested next story

None required by reconciliation verdict. Optional future candidates (not
created): durability story for orphaned scans after crash; docs-only ADR
clarification of projection-authoritative lifecycle.
