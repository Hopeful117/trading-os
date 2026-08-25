# Implementation Plan — Story 0028

## Architecture

**CLIENT_COMPOSITION** — Angular composes Trading Core Dashboard API + Market Intelligence APIs independently.

```
Angular Dashboard
       │
       ├───────────────┐
       │               │
       ▼               ▼
Trading Core       Market Intelligence
Dashboard API          APIs
       │               │
       ├ Account       ├ Active Opportunities
       ├ Broker        └ Recent Scans (GET /scans?limit=N)
       ├ Equity
       ├ Risk
       ├ Positions
       ├ Freshness
       └ Alerts
```

No backend changes. Reuses existing `ActiveScanService`.

## Implementation steps

### Step 1: Add ActiveScanSummary interface

**File:** `core/models/active-scan.model.ts`

Add interface matching Story 0027 backend contract:

```typescript
export interface ActiveScanSummary {
  scanId: string;
  accountId: string;
  status: ActiveScanStatus;
  objective: string | null;
  createdAt: string;
  updatedAt: string;
}
```

### Step 2: Add findRecent to ActiveScanService

**File:** `core/services/active-scan.service.ts`

```typescript
findRecent(limit: number = 10): Observable<ActiveScanSummary[]> {
  return this.http.get<ActiveScanSummary[]>(
    `${environment.gatewayUrl}v1/intelligence/scans`,
    { params: { limit: limit.toString() } },
  );
}
```

### Step 3: Add scanState observable to Dashboard

**File:** `dashboard.ts`

Inject `ActiveScanService`:

```typescript
private readonly activeScanService = inject(ActiveScanService);
```

Add scan observable with independent error/loading:

```typescript
private readonly scanState$ = this.activeScanService.findRecent(1).pipe(
  map((recentScans) => ({ recentScans, loading: false, error: false })),
  catchError(() => of({ recentScans: [], loading: false, error: true })),
  startWith({ recentScans: [], loading: true, error: false }),
);
```

Compose into `miState$`:

```typescript
private readonly miState$: Observable<MiState> = combineLatest([
  this.opportunitiesState$,
  this.scanState$,
]).pipe(
  map(([opp, scan]) => ({
    activeOpportunities: opp.activeOpportunities,
    opportunitiesLoading: opp.loading,
    opportunitiesError: opp.error,
    recentScans: scan.recentScans,
    scanLoading: scan.loading,
    scanError: scan.error,
  })),
);
```

Update `MiState` interface:

```typescript
interface MiState {
  activeOpportunities: OpportunityResponse[];
  opportunitiesLoading: boolean;
  opportunitiesError: boolean;
  recentScans: ActiveScanSummary[];
  scanLoading: boolean;
  scanError: boolean;
}
```

### Step 4: Add status label helpers

**File:** `dashboard.ts`

```typescript
scanStatusLabel(status: ActiveScanStatus): string {
  switch (status) {
    case 'READY_TO_DISPATCH': return 'Prêt';
    case 'DISPATCH_REQUESTED': return 'En attente';
    case 'RUNNING': return 'En cours';
    case 'PARTIALLY_COMPLETED': return 'Partiellement terminé';
    case 'COMPLETED': return 'Terminé';
    case 'FAILED': return 'Échoué';
    case 'COMPLETED_NO_WORK': return 'Terminé (aucun résultat)';
  }
}

scanStatusClass(status: ActiveScanStatus): string {
  return isActiveScanTerminal(status) ? 'terminal' : 'active';
}
```

### Step 5: Update template

**File:** `dashboard.html`

Replace MI panel with separate opportunity and scan sections:

```html
<section class="panel mi-panel">
  <div class="panel-title">
    <h2>Market Intelligence</h2>
    <a routerLink="/opportunities" class="panel-link">Voir les opportunités →</a>
  </div>
  <div class="mi-content">
    <div class="mi-row">
      <!-- opportunities loading/error/count -->
      <!-- scan loading/error/status/empty -->
    </div>
  </div>
</section>
```

### Step 6: Add scan styles

**File:** `dashboard.scss`

```scss
.mi-row {
  display: flex;
  gap: 2rem;
  padding: 0.5rem 0;
}

.scan-loading { color: #64748b; }
.scan-error { color: #f87171; }
.empty { color: #64748b; font-size: 1.1rem; }
.terminal { color: #94a3b8; }
.active { color: #38bdf8; }
```

### Step 7: Update tests

**File:** `dashboard.spec.ts`

Add `ActiveScanService` mock. New tests:
1. Latest scan status shown
2. Empty scans shows "Aucun scan exécuté"
3. Scan error state
4. Scan error does not break opportunities
5. Scan loading indicator
6. Terminal scan statuses (COMPLETED, FAILED)
7. scanStatusLabel returns correct labels
8. scanStatusClass returns correct classes
9. Opportunities error does not break scan

## Risk assessment

- **Low risk:** All changes are Angular-only. No backend modifications.
- **Existing tests:** 227 dashboard tests as baseline.
- **Reactive pattern:** Extending existing composition, not replacing.
- **API contract:** `GET /api/v1/intelligence/scans?limit=N` confirmed working.
