# Story 0027 — Engineering Report

**Date**: 2026-08-25
**Branch**: `feature/story-0027-expose-recent-active-scan-summary`
**Status**: DONE — Ready for human review

---

## What was done

Added a bounded, actor-scoped read-only query capability for recent Active Scan activity.

### Key outcome

Before this Story:
```
POST /scans → scanId → GET /scans/{scanId}
```

After this Story:
```
GET /scans?limit=N → Recent ActiveScanSummary[]
```

Any authorized client can now discover recent scan activity without knowing a scanId in advance.

## Quality gates

| Gate | Status |
|------|--------|
| `mvn test` (MI module) | 305/305 pass |
| `git diff --check` | Clean |
| Docker rebuild | Done |
| Runtime validation | All endpoints confirmed |

## Runtime validation

- `GET /scans` → 5 scans returned (default limit)
- `GET /scans?limit=1` → 1 most recent scan
- `GET /scans?limit=2` → 2 most recent scans
- `GET /scans?limit=0` → 400 INVALID_LIMIT
- Actor isolation confirmed (only current actor's scans returned)
- Summary/detail cross-check: all 5 fields match between list and detail endpoints

## Known limitations

- `eligibleMarkets` not included (requires child loading)
- `opportunitiesFound` not included (requires full reconciliation)
- `accountName` not resolved (Trading Core call out of scope)
- No status filtering (deferred to future need)
- No pagination framework (simple limit suffices)

## Recommendation

APPROVE for human review and merge.
