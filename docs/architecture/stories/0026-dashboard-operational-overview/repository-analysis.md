# Repository Analysis — Story 0026

## Current state confirmed on HEAD (`64f1613`)

### Angular Dashboard

| File | Status |
|---|---|
| `trading-os-web/src/app/features/dashboard/pages/dashboard/dashboard.ts` | Component with viewModel$ (accounts + dashboard polling) |
| `trading-os-web/src/app/features/dashboard/pages/dashboard/dashboard.html` | Template with all current widgets |
| `trading-os-web/src/app/features/dashboard/pages/dashboard/dashboard.scss` | Styles (dark theme) |
| `trading-os-web/src/app/features/dashboard/pages/dashboard/dashboard.spec.ts` | 8 tests |
| `trading-os-web/src/app/core/services/dashboard.service.ts` | `findDashboard(accountId)` → `GET /api/v1/accounts/{id}/dashboard` |
| `trading-os-web/src/app/core/services/account.service.ts` | `getAccounts()` → `GET /api/v1/accounts` |
| `trading-os-web/src/app/core/services/opportunity.service.ts` | `findActive()` → `GET /api/v1/opportunities/active` |
| `trading-os-web/src/app/core/services/active-scan.service.ts` | `findScan(id)` → `GET /api/v1/intelligence/scans/{id}` |
| `trading-os-web/src/app/core/models/dashboard-summary.model.ts` | Full type hierarchy |
| `trading-os-web/src/app/core/models/opportunity.model.ts` | OpportunityResponse type |
| `trading-os-web/src/app/core/models/active-scan.model.ts` | ActiveScanResponse type |

### Backend (no changes expected)

| Component | File | Status |
|---|---|---|
| DashboardController | `trading-core/.../controller/DashboardController.java` | `GET /api/v1/accounts/{id}/dashboard` |
| DashboardQueryService | `trading-core/.../dashboard/service/DashboardQueryService.java` | Orchestrator |
| AccountEquityService | `trading-core/.../dashboard/service/AccountEquityService.java` | Equity authority |
| OpportunityController | `market-intelligence/.../adapter/web/OpportunityController.java` | `GET /api/v1/opportunities/active` |
| MarketIntelligenceController | `market-intelligence/.../adapter/web/MarketIntelligenceController.java` | `GET /api/v1/intelligence/scans/{id}` |

### API contracts confirmed

| Endpoint | Method | Response | Gateway path |
|---|---|---|---|
| `/api/v1/accounts` | GET | `Account[]` | Works via nginx |
| `/api/v1/accounts/{id}/dashboard` | GET | `DashboardSummary` | Works via nginx |
| `/api/v1/opportunities/active` | GET | `OpportunityResponse[]` | Works via nginx (returns []) |
| `/api/v1/intelligence/scans/{id}` | GET | `ActiveScanResponse` | Works via nginx |

### Key observations

1. `OpportunityService.findActive()` already exists and works — returns `[]` when no active opportunities
2. `ActiveScanService.findScan(id)` exists but requires a scanId — no list endpoint
3. The MI scan API has no "list scans" endpoint — only `POST /scans` (create) and `GET /scans/{id}` (get by ID)
4. The dashboard template uses modern Angular 17+ control flow (`@if`, `@for`)
5. The component already uses `async pipe` and `combineLatest` — reactive pattern is established
6. `catchError(() => of([]))` on accounts$ is the bug to fix

### Test baseline

- `dashboard.spec.ts`: 8 tests
- Angular test suite: 216 tests across 36 files

### Files to modify

1. `dashboard.ts` — add MI observables, fix error semantics
2. `dashboard.html` — update template with MI panel, risk explanation, equity source, error states
3. `dashboard.spec.ts` — add new tests
4. `dashboard-summary.model.ts` — no changes needed (types already correct)

### Files NOT to modify

- Any backend Java files
- Any Docker configuration
- Any other Angular components
