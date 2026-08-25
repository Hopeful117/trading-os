# Repository Analysis — Story 0028

## Backend Contract (Story 0027)

- `GET /api/v1/intelligence/scans?limit=N` → `List<ActiveScanSummary>`
- `ActiveScanSummary`: `scanId`, `accountId`, `status`, `objective`, `createdAt`, `updatedAt`
- No backend changes needed

## Frontend Entry Points

| File | Purpose | Change |
|------|---------|--------|
| `core/models/active-scan.model.ts` | ActiveScanSummary interface | Add interface |
| `core/services/active-scan.service.ts` | findRecent(limit) | Add method |
| `dashboard/pages/dashboard/dashboard.ts` | Dashboard component | Add scanState$, compose miState$ |
| `dashboard/pages/dashboard/dashboard.html` | Template | Add latest scan display |
| `dashboard/pages/dashboard/dashboard.scss` | Styles | Add scan stat styles |
| `dashboard/pages/dashboard/dashboard.spec.ts` | Tests | Add 9 new scan tests |

## Constraints

- Reuse existing `ActiveScanService` (no new service)
- No backend changes
- Client-side only
- No detail endpoint call
