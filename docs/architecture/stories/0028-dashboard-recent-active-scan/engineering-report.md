# Story 0028 — Engineering Report

**Date**: 2026-08-25
**Branch**: `feature/story-0028-dashboard-recent-active-scan`
**Status**: DONE — Ready for human review

---

## What was done

Integrated recent Active Scan activity into the Dashboard MI panel, completing the Market Intelligence visibility in the Dashboard.

### Key changes

1. **ActiveScanSummary interface**: Added to `core/models/active-scan.model.ts`, matching Story 0027 backend contract
2. **findRecent method**: Added to `ActiveScanService`, queries `GET /api/v1/intelligence/scans?limit=N`
3. **scanState$ observable**: Added to Dashboard with independent error/loading from opportunities
4. **Latest scan display**: MI panel now shows active opportunities count AND latest scan status side-by-side
5. **Status labels**: French labels for all 7 scan statuses (Prêt, En attente, En cours, etc.)
6. **Error isolation**: Scan failure does not break opportunities, and vice versa
7. **Tests**: 9 new tests (236 total), covering all scan states and error scenarios

### What was NOT changed

- No backend changes
- No new Angular services
- No scan detail loading
- No account-scoped filtering
- No pagination

---

## Quality gates

| Gate | Status |
|------|--------|
| `ng test` | 236/236 pass |
| `ng build` | Success |
| Prettier | Clean |
| `git diff --check` | Clean |
| Docker rebuild | Done |
| Runtime validation | Scan API confirmed via gateway JWT |

---

## Runtime validation

- Login: `scanprobe`/`Str0ngPass!123`
- Scan API: `GET /api/v1/intelligence/scans?limit=3` returns 3 RUNNING scans
- All required fields present: `scanId`, `accountId`, `status`, `objective`, `createdAt`, `updatedAt`
- No `GET /scans/{id}` called by Dashboard

---

## Known limitations

- Latest scan is actor-scoped, not account-scoped (backend limitation)
- No scan history page (out of scope)
- No `eligibleMarkets` / `opportunitiesFound` fields (out of scope)

---

## Recommendation

APPROVE for human review and merge.
