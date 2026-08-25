# Implementation Plan — Story 0026

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
       ├ Broker        └ Latest Scan (by ID)
       ├ Equity
       ├ Risk
       ├ Positions
       ├ Freshness
       └ Alerts
```

No backend changes. No new services. No BFF.

## Implementation steps

### Step 1: Fix accounts loading error semantics

**File:** `dashboard.ts`

Current `accounts$`:
```typescript
private readonly accounts$ = this.accountService.getAccounts().pipe(
  catchError(() => of([])),
  shareReplay({ bufferSize: 1, refCount: true }),
);
```

Problem: HTTP error → `[]` → "Aucun compte" (indistinguishable from empty)

New approach: Track accounts state explicitly.

```typescript
interface AccountsState {
  accounts: Account[];
  loading: boolean;
  error: boolean;
}
```

`accounts$` becomes:
```typescript
private readonly accountsState$ = this.accountService.getAccounts().pipe(
  map(accounts => ({ accounts, loading: false, error: false })),
  catchError(() => of({ accounts: [], loading: false, error: true })),
  startWith({ accounts: [], loading: true, error: false }),
  shareReplay({ bufferSize: 1, refCount: true }),
);
```

### Step 2: Add MI observables

**File:** `dashboard.ts`

Inject `OpportunityService` (already exists in core/services).

```typescript
private readonly opportunityService = inject(OpportunityService);
```

Add MI observables:
```typescript
private readonly activeOpportunities$ = this.opportunityService.findActive().pipe(
  map(opps => ({ opportunities: opps, loading: false, error: false })),
  catchError(() => of({ opportunities: [], loading: false, error: true })),
  startWith({ opportunities: [], loading: true, error: false }),
);
```

Note: No scan listing endpoint exists. The MI panel will show active opportunities only. If a scan is running, the scan panel on the Opportunities page handles that. The Dashboard MI panel is a summary, not a duplicate.

### Step 3: Update view model

**File:** `dashboard.ts`

```typescript
interface DashboardViewModel {
  accountsState: AccountsState;
  selectedAccountId: string | null;
  dashboard: DashboardSummary | null;
  dashboardLoading: boolean;
  dashboardError: string | null;
  mi: {
    activeOpportunities: OpportunityResponse[];
    loading: boolean;
    error: boolean;
  };
}
```

Combine all sources:
```typescript
readonly viewModel$ = combineLatest([
  this.accountsState$,
  this.selectedAccountId,
  this.activeOpportunities$,
]).pipe(
  map(([accountsState, selectedAccountId, mi]) => {
    const accounts = accountsState.accounts;
    const effectiveId = selectedAccountId && accounts.some(a => a.accountId === selectedAccountId)
      ? selectedAccountId
      : (accounts[0]?.accountId ?? null);
    return { accountsState, selectedAccountId: effectiveId, mi };
  }),
  switchMap(({ accountsState, selectedAccountId, mi }) => {
    if (!selectedAccountId) {
      return of({
        accountsState, selectedAccountId, dashboard: null,
        dashboardLoading: false, dashboardError: null, mi,
      });
    }
    return timer(0, 5_000).pipe(
      switchMap(() =>
        this.dashboardService.findDashboard(selectedAccountId).pipe(
          map(dashboard => ({
            accountsState, selectedAccountId, dashboard,
            dashboardLoading: false, dashboardError: null, mi,
          })),
          catchError(() => of({
            accountsState, selectedAccountId, dashboard: null,
            dashboardLoading: false,
            dashboardError: 'Le Dashboard est temporairement indisponible.',
            mi,
          })),
        ),
      ),
      startWith({
        accountsState, selectedAccountId, dashboard: null,
        dashboardLoading: true, dashboardError: null, mi,
      }),
    );
  }),
  shareReplay({ bufferSize: 1, refCount: true }),
);
```

### Step 4: Update template

**File:** `dashboard.html`

#### Account loading/error states

Replace the current loading/error logic with:
```html
@if (vm.accountsState.loading) {
  <section class="state-panel">Chargement des comptes…</section>
} @else if (vm.accountsState.error) {
  <section class="state-panel error">Impossible de charger les comptes. Veuillez réessayer.</section>
} @else if (vm.accounts.length === 0) {
  <section class="state-panel">Aucun compte n'est disponible.</section>
}
```

#### Equity source label

Replace current equity card with:
```html
<article class="stat-card">
  <span>Equity</span>
  <strong>
    @if (dashboard.account.equity !== null) {
      {{ dashboard.account.equity | currency: dashboard.account.currency }}
    } @else {
      Indisponible
    }
  </strong>
  <small>
    @if (dashboard.account.equitySource === 'BROKER') {
      Provenance : broker
    } @else if (dashboard.account.equitySource === 'CALCULATED') {
      Provenance : calculé à partir du compte broker
    } @else {
      Provenance : {{ dashboard.account.equitySource }}
    }
  </small>
</article>
```

#### Risk UNAVAILABLE explanation

Replace the risk panel content when UNAVAILABLE:
```html
@if (dashboard.risk.status === 'UNAVAILABLE') {
  <div class="empty-state">
    Aucune règle de risque configurée pour ce compte.
  </div>
} @else {
  <!-- existing risk rules rendering -->
}
```

#### MI panel (replace placeholder)

```html
<section class="panel mi-panel">
  <div class="panel-title">
    <h2>Market Intelligence</h2>
    <a routerLink="/opportunities" class="panel-link">Voir les opportunités →</a>
  </div>

  @if (vm.mi.loading) {
    <p class="empty-state">Chargement…</p>
  } @else if (vm.mi.error) {
    <p class="empty-state error">Market Intelligence temporairement indisponible.</p>
  } @else {
    <div class="mi-summary">
      <div class="mi-stat">
        <span>Opportunités actives</span>
        <strong>{{ vm.mi.activeOpportunities.length }}</strong>
      </div>
    </div>
  }
</section>
```

#### Performance panel (replace placeholder)

```html
<article class="panel">
  <h2>Performance</h2>
  <div class="empty-state">
    L'historique d'equity n'est pas encore disponible.
  </div>
</article>
```

### Step 5: Add routerLink import

**File:** `dashboard.ts`

Add `RouterLink` to imports:
```typescript
imports: [AsyncPipe, CurrencyPipe, DatePipe, DecimalPipe, RouterLink],
```

### Step 6: Add MI summary styles

**File:** `dashboard.scss`

Add styles for MI panel:
```scss
.mi-summary {
  display: flex;
  gap: 1.5rem;
  padding: 0.5rem 0;
}

.mi-stat {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;

  span { color: #94a3b8; font-size: 0.85rem; }
  strong { font-size: 1.5rem; color: #e2e8f0; }
}

.panel-link {
  font-size: 0.85rem;
  color: #60a5fa;
  text-decoration: none;
  &:hover { text-decoration: underline; }
}

.empty-state {
  color: #94a3b8;
  font-style: italic;
  padding: 1rem 0;

  &.error { color: #f87171; }
}
```

### Step 7: Update tests

**File:** `dashboard.spec.ts`

New tests to add:
1. Account loading state → shows "Chargement des comptes…"
2. Account error state → shows "Impossible de charger les comptes"
3. Account empty state → shows "Aucun compte" (same as before)
4. Equity source CALCULATED → shows "Provenance : calculé à partir du compte broker"
5. Risk UNAVAILABLE → shows "Aucune règle de risque configurée"
6. MI loading → shows "Chargement…"
7. MI error → shows "Market Intelligence temporairement indisponible"
8. MI with active opportunities → shows count
9. MI failure does not break dashboard → account data still shown
10. Performance panel → shows "L'historique d'equity n'est pas encore disponible"

## Risk assessment

- **Low risk:** All changes are Angular-only. No backend modifications.
- **Existing tests:** 8 dashboard tests + 216 Angular tests as baseline.
- **Reactive pattern:** Established in the component — extending, not replacing.
- **API contracts:** All APIs confirmed working via nginx.
