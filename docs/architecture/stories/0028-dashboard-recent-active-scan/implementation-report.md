# Implementation Report — Story 0028

## Branch

`feature/story-0028-dashboard-recent-active-scan` — commit `a8925ef`

## Summary

Integrated recent Active Scan activity into the Dashboard MI panel. The MI panel now shows both active opportunities count and latest scan status side by side.

## Changes

### Model (`active-scan.model.ts`)

- Added `ActiveScanSummary` interface with fields matching Story 0027 contract

### Service (`active-scan.service.ts`)

- Added `findRecent(limit)` → `GET /api/v1/intelligence/scans?limit=N`

### Component (`dashboard.ts`)

- Injected `ActiveScanService`
- Added `opportunitiesState$` and `scanState$` observables
- Composed into `miState$` with separate loading/error per source
- Added `scanStatusLabel()` and `scanStatusClass()` helpers

### Template (`dashboard.html`)

- MI panel now shows opportunities and scan in a row layout
- Scan shows: status label (RUNNING → "En cours"), empty state ("Aucun scan exécuté"), error ("Indisponible")
- Independent error handling for each source

### Styles (`dashboard.scss`)

- Added `.mi-row` flex layout
- Added `.scan-loading`, `.scan-error`, `.empty`, `.terminal`, `.active` classes

## Tests

- 236 tests passing (9 new)
- New tests cover: scan loaded, no scans, scan error, scan error + opportunities, scan loading, terminal status, FAILED status, scanStatusLabel, scanStatusClass

## Quality Gates

- [x] `ng test` — 236/236 passed
- [x] `ng build` — success
- [x] `git diff --check` — clean
- [x] Prettier — clean

## Runtime Validation

- [x] Docker rebuild: trading-web recreated
- [x] `GET /api/v1/intelligence/scans?limit=3` returns 3 RUNNING scans via gateway with JWT
- [x] All required fields present: `scanId`, `accountId`, `status`, `objective`, `createdAt`, `updatedAt`
- [x] No `GET /scans/{id}` called by Dashboard (no detail endpoint usage)
