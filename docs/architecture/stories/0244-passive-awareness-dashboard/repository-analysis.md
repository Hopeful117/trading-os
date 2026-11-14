# Repository Analysis — Story 0244: Passive Awareness Dashboard

**Analysis date:** 2026-11-14
**Analyst:** OpenClaw
**Story:** 0244 — Add the Passive Awareness Dashboard
**Epic:** 0238 — V1 Market Scanning and Opportunity Discovery

---

## 1. Repository Baseline

| Item | Value |
|---|---|
| Analysis branch | `story/0243-passive-to-active-investigation` |
| Analysis branch HEAD | `684f55fe` — `feat(story-0243): launch ActiveScan from passive candidates` |
| Effective baseline | `main` @ `97d8a865` — merge of PR #295 (all Story 0239–0243 implementations) |
| Working tree | Clean — one untracked file: `CLAUDE.md` (unrelated, preserved) |
| Latest Flyway migration | `V31__allow_passive_skipped_without_execution.sql` |
| ADRs read | ADR-033, ADR-102 |
| Preceding story merges | PR #291 (0239), PR #292 (0240), PR #293/#294 (0241+0242), PR #295 (0243) |

All backend contracts referenced in this analysis are implemented and merged.
The Angular application has not yet been extended for passive awareness.

---

## 2. Story Objective and Product Value

### Objective

Expose `GET /api/v1/passive-awareness` through the existing Angular application and provide
an explicit, user-controlled transition to the existing ActiveScan workflow via
`POST /api/v1/intelligence/scans`.

### Product Value

The trader can understand monitored markets, evidence freshness and candidate rationale
without confusing passive discovery with an active or executable trade decision.

Passive awareness becomes the first step in a human-controlled investigation path:

```
Passive Scanner (backend)
      ↓
GET /api/v1/passive-awareness
      ↓
Angular Awareness Feed
      ↓
Trader reviews candidates
      ↓
Trader selects candidate + account
      ↓
POST /api/v1/intelligence/scans (existing endpoint)
      ↓
Existing ActiveScan + ActiveScan result
      ↓
Human trading decision
```

### Product Constraints (from story.md)

- Reuse existing Opportunities page, ScanPanel, AccountService, ActiveScanService.
- Do not create a new dashboard app, new state-management library, or duplicate the scan workflow.
- A passive candidate is not an executable trade.
- No automatic escalation to ActiveScan, scheduler activation, or broker operation.

---

## 3. Existing Angular Architecture

### Framework

| Item | Value |
|---|---|
| Angular version | 21.2.x (`@angular/*` at `^21.2.0`) |
| Component model | Standalone — Angular 19+ default; `standalone: true` is implicit |
| Template syntax | Angular 17+ built-in control flow (`@if`, `@switch`, `@for`, `@empty`) |
| Testing | **Vitest** (`vitest ^4.0.8`, `@vitest/coverage-v8`, `jsdom`). Not Karma/Jasmine |

### Reactive pattern

The application uses two complementary reactive styles consistently:

**RxJS Observables + `async` pipe** — for all async data-fetching (HTTP, services):
- Discriminated union view state: `{ status: 'loading' } | { status: 'error' } | { status: 'loaded'; data: T }`
- Refresh: `Subject<void>` + `startWith(undefined)` + `switchMap`
- Polling: `timer(intervalMs, intervalMs).pipe(switchMap(...), takeWhile(..., true))`
  with interval provided via `InjectionToken` for testability
- `exhaustMap` to prevent concurrent in-flight user actions
- `shareReplay({ bufferSize: 1, refCount: true })` for multicasting

**Angular Signals** — for local component state only:
- `input()` / `output()` on component boundaries (Angular 17+)
- `signal()` for local mutable state (e.g. selected account ID)
- `effect()` for reacting to input signal changes (e.g. `embeddedAccountId` in ScanPanel)
- Signals are NOT used for async data-fetching — Observables and `async` pipe remain the standard

### Design system

Custom workstation system in `trading-os-web/src/app/shared/workstation/`:
- `WorkspacePanel` / `WorkspacePanelHeader` — standard card/panel container
- `FreshnessIndicator` — existing freshness display primitive
- `StatusBadge` — existing status badge primitive
- `MetricValue` — labeled metric display
- `DisclosureSection` — expandable content

Angular Material is installed (`@angular/material ^21.2.14`, `@angular/cdk ^21.2.14`) but is
not used in the Opportunities/ScanPanel surface. Story 0244 should follow the same pattern —
use the custom workstation system, not Material.

### Authentication

Functional `HttpInterceptorFn` at `core/interceptors/auth-interceptor.ts`:
- Skips `/login`, `/register`, and `environment.mcpBridgeUrl`
- Attaches `Authorization: Bearer <token>` to all other requests
- On 401: removes token, redirects to `/error?status=401`
- On 401/403: redirects to `/error?status=<n>`

No additional auth configuration is required for Story 0244.

### Key existing services

| Service | File | Methods |
|---|---|---|
| `ActiveScanService` | `core/services/active-scan.service.ts` | `createScan(request, idempotencyKey)`, `resolveScope(request)`, `findScan(scanId)`, `findRecent(limit)` |
| `OpportunityService` | `core/services/opportunity.service.ts` | `findActive()`, `findById(id)` |
| `AccountService` | `core/services/account.service.ts` | `getAccounts()`, `getAccount(id)` |
| `MarketService` | `core/services/market.service.ts` | `findAll()`, `findById(id)` |

### Key existing components

| Component | Route/location | Responsibility |
|---|---|---|
| `Opportunities` | `/opportunities` | Lists active opportunities; hosts `ScanPanel` |
| `ScanPanel` | Child of `Opportunities` | Full scan workflow: account selector, market selector, POST scan, poll, navigate |
| `WorkspacePanel` | `shared/workstation/` | Standard card container |
| `FreshnessIndicator` | `shared/workstation/` | Freshness display |
| `StatusBadge` | `shared/workstation/` | Status label badge |

**Account selector:** No dedicated standalone component. Account selection is an inline
`<select>` inside `ScanPanel`'s template, bound to a local signal, populated from `AccountService`.
The `Opportunities` component receives account changes via `ScanPanel`'s `(accountSelected)` output.

---

## 4. Existing REST Contracts

### Passive Awareness Feed (from Story 0242 — merged, gateway-routed)

```
GET /api/v1/passive-awareness
Authorization: Bearer <token>  (required; 401 without it)
Gateway route: passive-awareness → /api/v1/passive-awareness/** → lb://market-intelligence
                (GatewayRouteConfig.java:146)

Query parameters:
  instrument    string   optional — case-insensitive filter
  provider      string   optional — case-insensitive filter
  productType   string   optional — case-insensitive filter
  currentOnly   boolean  default false
  page          int      default 0  (zero-based)
  size          int      default 20 (1–200)

Response 200: PassiveAwarenessPageResponse {
  items:     PassiveAwarenessResponse[]
  page:      number
  size:      number
  total:     number     — count after filtering, before pagination
  truncated: boolean    — true when backing store exceeded 1000 items
}
```

#### PassiveAwarenessResponse fields

| Field | Type | Notes |
|---|---|---|
| `opportunityId` | string (UUID) | Passive candidate ID |
| `opportunityVersion` | number (long) | Candidate version — required for ActiveScan |
| `current` | boolean | true iff `diagnostics` is empty |
| `diagnostics` | string[] | From `PassiveCandidateEvaluator` — see below |
| `lifecycleState` | string | `opportunity.status().name()` — e.g. `"ACTIVE"` |
| `presentationState` | string | `"PASSIVE_CANDIDATE"` when current, else `"NON_POSITIVE"` |
| `instrument` | string | Canonical instrument label |
| `direction` | string | e.g. `"LONG"`, `"SHORT"` |
| `scenario` | string | Strategy scenario |
| `timeframe` | string | Analysis timeframe |
| `origin` | string | Always `"PASSIVE_SCAN"` |
| `marketId` | string (UUID) | Canonical market ID |
| `provider` | string \| null | From resolved `MarketResponse`; null if unavailable |
| `productType` | string \| null | From resolved `MarketResponse`; null if unavailable |
| `strategyMatchId` | string (UUID) | Reference only |
| `observationIds` | string[] | Observation UUIDs (reference only) |
| `observationTimes` | string[] | ISO-8601 Instants |
| `thesisId` | string (UUID) | Market thesis reference |
| `thesisVersion` | number \| null | |
| `sourceScanId` | string (UUID) | Originating passive scan run |
| `sourceScanMarketId` | string (UUID) | Originating passive scan market record |
| `analysisExecutionId` | string (UUID) | Originating analysis execution |
| `evaluatedAt` | string (Instant) | When this projection was evaluated |
| `sourceCutoff` | string \| null | Thesis evidence cutoff; null if thesis unavailable |
| `validFrom` | string (Instant) | Validity window start |
| `validUntil` | string \| null | Validity window end; null = open-ended |
| `createdAt` | string (Instant) | When the opportunity was created |

**What the API does NOT expose:**
- Raw observation objects (observations are embedded in the thesis/opportunity chain)
- Confidence scores, rankings, or probabilities
- AI-generated explanations
- Provider-specific trade setup details
- Real-time price data

#### Diagnostic codes from `PassiveCandidateEvaluator`

An item is `current=true` only when this list is empty. All 27 possible codes:

```
OPPORTUNITY_NOT_ACTIVE, OPPORTUNITY_OUTSIDE_VALIDITY, OPPORTUNITY_SUPERSEDED,
STRATEGY_MATCH_MISSING, STRATEGY_MATCH_REFERENCE_UNAVAILABLE,
STRATEGY_MATCH_MARKET_MISMATCH, STRATEGY_MATCH_DIRECTION_MISMATCH,
STRATEGY_MATCH_OBSERVATION_MISMATCH, STRATEGY_MATCH_EXECUTION_MISMATCH,
STRATEGY_MATCH_FRESHNESS_INVALID,
OBSERVATION_REFERENCE_UNAVAILABLE, OBSERVATION_NOT_CURRENT,
MARKET_THESIS_REFERENCE_UNAVAILABLE, THESIS_NOT_CANDIDATE,
THESIS_OUTSIDE_VALIDITY, THESIS_SUPERSEDED, THESIS_FRESHNESS_INVALID,
THESIS_BLOCKING_CONTRADICTION, REQUIRED_EVIDENCE_NON_POSITIVE,
REQUIRED_EVIDENCE_EXPIRED, REQUIRED_EVIDENCE_FRESHNESS_INVALID,
MARKET_THESIS_OPPORTUNITY_MISMATCH,
MARKET_IDENTITY_MISSING, MARKET_IDENTITY_UNAVAILABLE, MARKET_IDENTITY_MISMATCH,
PASSIVE_PROVENANCE_INCOMPLETE, PASSIVE_SCAN_MARKET_MISMATCH
```

---

### ActiveScan Creation (extended by Story 0243 — merged)

```
POST /api/v1/intelligence/scans
Authorization: Bearer <token>
Idempotency-Key: <string>   (required header)
Gateway route: market-intelligence → /api/v1/intelligence/** → lb://market-intelligence
                (GatewayRouteConfig.java:162)

Body: CreateActiveScanRequestDto {
  accountId:                      string (UUID)   — required
  brokerAccountId?:               string (UUID)   — optional
  objective?:                     string          — max 500 chars
  requestedMarketIds?:            string[]        — optional
  scopeMode?:                     'SELECTED' | 'ALL_ELIGIBLE'
  tradeabilityEvidenceProfile?:   string          — optional
  sourcePassiveOpportunityId?:    string (UUID)   — NEW: Story 0243
  sourcePassiveOpportunityVersion?: number (Long) — NEW: Story 0243, must be >= 1
}
```

Both `sourcePassiveOpportunityId` and `sourcePassiveOpportunityVersion` must be either both
present or both absent. Providing only one returns 400 `PASSIVE_CANDIDATE_REFERENCE_INCOMPLETE`.

For passive-to-active investigation, the request must set:
- `scopeMode: 'SELECTED'`
- `requestedMarketIds: [candidateMarketId]`
- `sourcePassiveOpportunityId`: from the candidate
- `sourcePassiveOpportunityVersion`: from the candidate

#### Error codes for the passive-to-active flow

| Code | HTTP | Condition |
|---|---|---|
| `PASSIVE_CANDIDATE_REFERENCE_INCOMPLETE` | 400 | Only one of id/version provided |
| `PASSIVE_CANDIDATE_REFERENCE_INVALID` | 400 | version < 1 |
| `PASSIVE_CANDIDATE_NOT_FOUND` | 404 | Evaluator found no matching opportunity |
| `PASSIVE_CANDIDATE_SUPERSEDED` | 409 | diagnostics contains OPPORTUNITY_SUPERSEDED or THESIS_SUPERSEDED |
| `PASSIVE_CANDIDATE_NOT_CURRENT` | 409 | projection.current() is false |
| `PASSIVE_CANDIDATE_INVALID_ORIGIN` | 422 | origin != PASSIVE_SCAN |
| `PASSIVE_CANDIDATE_MARKET_MISMATCH` | 422 | scopeMode != SELECTED or requestedMarketIds != [candidateMarketId] |
| `PASSIVE_CANDIDATE_IDENTITY_UNAVAILABLE` | 503 | Market data unavailable |

Error response body: `{ "code": "...", "error": "..." }`

**Error precedence note (from Story 0243 engineering report residual risks):**
Scope mismatch (`MARKET_MISMATCH`, 422) is checked before freshness; a request that is both
mis-scoped and stale returns 422, not 409.

#### Angular model gap — requires fix in Story 0244

The existing `CreateActiveScanRequest` TypeScript interface at
`trading-os-web/src/app/core/models/active-scan.model.ts:63` does not yet include the Story 0243
passive candidate fields:

```typescript
// Current (incomplete)
export interface CreateActiveScanRequest {
  accountId: string;
  objective?: string;
  requestedMarketIds?: string[];
  scopeMode?: ActiveScanScopeMode;
}

// Required (Story 0244 must add)
export interface CreateActiveScanRequest {
  accountId: string;
  objective?: string;
  requestedMarketIds?: string[];
  scopeMode?: ActiveScanScopeMode;
  sourcePassiveOpportunityId?: string;        // NEW
  sourcePassiveOpportunityVersion?: number;   // NEW
}
```

This is the only backend change that the Angular model must reflect.

---

## 5. Recommended UI Integration Point

### Options evaluated

**Option A — Tab or section within existing `/opportunities` route (recommended)**
- Adds an "Awareness" tab/section alongside the active opportunities list
- Reuses the route, auth guard, layout context, and account selection output binding
- ScanPanel adjacency is preserved — both surfaces benefit from the same account context
- No new route, no new guard wiring
- Minimum new code

**Option B — Dedicated route `/awareness`**
- Independent navigation entry; cleaner URL
- Requires a new route definition in `app.routes.ts`, new auth guard application,
  and duplicated account selection context
- Adds navigation entries without a product requirement that justifies the separation in V1

**Recommendation: Option A.** Integrate as a tab or section within the existing Opportunities
page. The passive awareness feed and the active scan launch are complementary — the user journey
moves naturally from reviewing a candidate to launching a scan on the same page. No new Angular
route is required. If the product later needs a dedicated navigation entry for awareness, that
can be added as a named outlet or a new route in a future story.

---

## 6. Reactive Data Flow

```
PassiveAwarenessService
  → GET /api/v1/passive-awareness?{params}
  → Observable<PassiveAwarenessPageResponse>

PassiveAwarenessFeedComponent
  filterParams: signal({ currentOnly, instrument, page }) — local state
  private refreshSubject = new Subject<void>()

  view$: this.refreshSubject.pipe(
    startWith(undefined),
    switchMap(() =>
      this.awarenessService.query(this.filterParams()).pipe(
        map(r => ({ status: 'loaded' as const, page: r })),
        catchError(err => of({ status: 'error' as const, httpStatus: extractStatus(err),
                               code: extractCode(err) })),
        startWith({ status: 'loading' as const }),
      )
    ),
    shareReplay({ bufferSize: 1, refCount: true })
  )

  refresh(): void { this.refreshSubject.next(); }
  applyFilter(params): void { this.filterParams.set(params); this.refreshSubject.next(); }
```

Filter state changes (currentOnly toggle, instrument search, page navigation) trigger a
refresh via the `refreshSubject`, re-running the HTTP query with the latest `filterParams`.

The feed component does NOT trigger scans. Scan launch is a user-initiated action on a
separate `PassiveCandidateInvestigatePanel` child component.

---

## 7. Presentation Model

### Two API presentation states

| `presentationState` | `current` | Diagnostics | UI label | Card style |
|---|---|---|---|---|
| `PASSIVE_CANDIDATE` | true | (none) | "Candidate" | Active — full detail, Investigate button enabled |
| `NON_POSITIVE` | false | 1+ codes | "Non-Positive" | Muted — collapsed by default, diagnostics visible |

### Frontend view model

```typescript
// trading-os-web/src/app/core/models/passive-awareness.model.ts

export interface PassiveAwarenessResponse {
  opportunityId: string;
  opportunityVersion: number;
  current: boolean;
  diagnostics: string[];
  lifecycleState: string;
  presentationState: 'PASSIVE_CANDIDATE' | 'NON_POSITIVE';
  instrument: string;
  direction: string;
  scenario: string;
  timeframe: string;
  origin: string;
  marketId: string;
  provider: string | null;
  productType: string | null;
  strategyMatchId: string;
  observationIds: string[];
  observationTimes: string[];
  thesisId: string;
  thesisVersion: number | null;
  sourceScanId: string;
  sourceScanMarketId: string;
  analysisExecutionId: string;
  evaluatedAt: string;
  sourceCutoff: string | null;
  validFrom: string;
  validUntil: string | null;
  createdAt: string;
}

export interface PassiveAwarenessPageResponse {
  items: PassiveAwarenessResponse[];
  page: number;
  size: number;
  total: number;
  truncated: boolean;
}

// Derived view model — mapped in the component, not the service
export interface PassiveAwarenessItem extends PassiveAwarenessResponse {
  isCandidate: boolean;        // derived: presentationState === 'PASSIVE_CANDIDATE'
  isExpired: boolean;          // derived: validUntil !== null && Date.now() > Date.parse(validUntil)
  freshnessSummary: string;    // derived: human-readable relative to evaluatedAt
  diagnosticLabels: string[];  // deterministic mapping from diagnostics[]
}

export type AwarenessFeedView =
  | { status: 'loading' }
  | { status: 'error'; httpStatus: number; code?: string }
  | { status: 'empty' }                // total === 0, no active filters
  | { status: 'filtered-empty' }       // total === 0, filters are active
  | { status: 'loaded'; items: PassiveAwarenessItem[]; pagination: PaginationState };

export interface PaginationState {
  page: number;
  size: number;
  total: number;
  truncated: boolean;
}

export interface PassiveAwarenessQueryParams {
  instrument?: string;
  provider?: string;
  productType?: string;
  currentOnly?: boolean;
  page?: number;
  size?: number;
}
```

### Diagnostic display mapping (deterministic — no AI)

Group the backend diagnostic codes into user-visible categories for status badges.
No free-text generation — only deterministic code-to-label mapping:

| Category | Codes | Short label |
|---|---|---|
| Validity | `OPPORTUNITY_OUTSIDE_VALIDITY`, `THESIS_OUTSIDE_VALIDITY`, `REQUIRED_EVIDENCE_EXPIRED` | "Outside validity" / "Evidence expired" |
| Supersession | `OPPORTUNITY_SUPERSEDED`, `THESIS_SUPERSEDED` | "Superseded" |
| Evidence quality | `THESIS_BLOCKING_CONTRADICTION`, `REQUIRED_EVIDENCE_NON_POSITIVE`, `REQUIRED_EVIDENCE_FRESHNESS_INVALID`, `THESIS_FRESHNESS_INVALID`, `STRATEGY_MATCH_FRESHNESS_INVALID` | "Stale evidence" / "Contradictory" |
| Market data | `MARKET_IDENTITY_UNAVAILABLE`, `MARKET_IDENTITY_MISSING`, `MARKET_IDENTITY_MISMATCH` | "Market unavailable" |
| Reference integrity | `STRATEGY_MATCH_MISSING`, `MARKET_THESIS_REFERENCE_UNAVAILABLE`, `OBSERVATION_REFERENCE_UNAVAILABLE`, `MARKET_THESIS_OPPORTUNITY_MISMATCH` | "Reference missing" |
| Inactive | `OPPORTUNITY_NOT_ACTIVE`, `THESIS_NOT_CANDIDATE`, `OBSERVATION_NOT_CURRENT` | "No longer active" |
| Mismatch | `STRATEGY_MATCH_MARKET_MISMATCH`, `STRATEGY_MATCH_DIRECTION_MISMATCH`, etc. | "Validation mismatch" |

Show the highest-severity category label alongside the diagnostic count.
Full diagnostic code list is available in an expandable detail section.

### Display fields

**Directly from API (no derivation needed):**
- Market identity: `instrument`, `provider`, `productType`, `marketId`
- Awareness state: `presentationState`, `current`, `lifecycleState`
- Direction: `direction`
- Scenario/timeframe: `scenario`, `timeframe`
- Evidence timestamps: `evaluatedAt`, `sourceCutoff`, `validFrom`, `validUntil`
- Diagnostics: `diagnostics[]`

**Safely derived by frontend:**
- `isCandidate = presentationState === 'PASSIVE_CANDIDATE'`
- `freshnessSummary = formatRelative(evaluatedAt)` — e.g. "3 min ago"
- `isExpired = validUntil !== null && Date.now() > Date.parse(validUntil)`
- `timeUntilExpiry = validUntil !== null ? formatRelative(validUntil) : null`
- `diagnosticLabels = mapDiagnosticCodes(diagnostics)`

---

## 8. Awareness States

### Feed-level states

| State | Trigger | UI behavior |
|---|---|---|
| `loading` | Initial query in-flight | Spinner or skeleton cards |
| `error` | HTTP error (401, 503, timeout) | Error message with code |
| `empty` | `total === 0`, no active filters | "No monitored markets have been analyzed yet" |
| `filtered-empty` | `total === 0`, filters active | "No results for the current filters" + clear-filters button |
| `loaded` | Successful response | Candidate cards + non-positive cards |

A quiet market is not an application failure. `empty` must not imply the scanner is broken.

### Item-level states

| Condition | Display |
|---|---|
| `current=true`, no diagnostics | Full candidate card — Investigate button enabled |
| `current=false`, diagnostics present | Muted card — top diagnostic label visible, details expandable |
| `validUntil` in the past | Expiry badge on card header |
| `provider` or `productType` is null | "Market data unavailable" label instead of provider/product |
| `sourceCutoff` is null | "Evidence cutoff unknown" note in details |
| `diagnostics` contains supersession code | Superseded badge — do not show Investigate button |

---

## 9. Freshness and Diagnostics

### Freshness display

- Primary freshness: time elapsed since `evaluatedAt` — shown on every card
- Evidence cutoff: `sourceCutoff` — shown in expanded details when non-null
- Validity window: `validFrom` to `validUntil` — shown in expanded details

Do not compute or display raw provider data age — this is not available from the API.

### Diagnostics display

- `current=true` items: show no diagnostics (there are none)
- `current=false` items: show the top-category diagnostic label on the card header;
  reveal full code list in an expandable disclosure section
- Do not show raw code names to the user — use the mapping table from Section 7

---

## 10. Filtering and Pagination

### Server-supported filters

| Parameter | Type | Default | UI control |
|---|---|---|---|
| `currentOnly` | boolean | false | Toggle — "Candidates only" |
| `instrument` | string | null | Text input — debounced (300 ms) |
| `page` | int | 0 | Previous/next buttons |
| `size` | int | 20 | Fixed for V1 |

### V1 exclusions

Do not introduce in V1:
- `provider` or `productType` dropdowns (server-supported, deferred to V2)
- Infinite scroll (requires client-side state management beyond V1 scope)
- Client-side filtering on the paginated response (misrepresents the dataset)

### Pagination display

Show `page`, `size`, `total` from the response. Disable "Previous" on page 0.
Disable "Next" when `(page + 1) * size >= total`.
When `truncated=true`, show a note: "Results truncated — apply filters to narrow."

---

## 11. Refresh Strategy

### Recommended approach: manual refresh only (V1)

The passive scanner runs on the backend's own schedule (5-minute cadence, ADR-102 §7).
The frontend reads persisted awareness state. There is no product requirement for automatic
refresh of the awareness feed in V1. Manual refresh satisfies the acceptance criteria.

```typescript
// In PassiveAwarenessFeedComponent
private readonly refreshSubject = new Subject<void>();

readonly view$ = this.refreshSubject.pipe(
  startWith(undefined),
  switchMap(() => this.awarenessService.query(this.currentParams()).pipe(...)),
);

refresh(): void {
  this.refreshSubject.next();
}
```

### Constraints

- Use `switchMap` on the refresh subject — cancels the previous in-flight request on refresh.
- Use `takeUntilDestroyed()` (Angular 16+) on any subscription that must end when the
  component is destroyed. The `async` pipe handles this automatically for `view$`.
- Do not use `interval()` or `timer()` for automatic feed refresh.
- Do not create an `ActiveScan` on refresh.

Polling (`timer()` + `takeWhile`) is used only for waiting on a scan that has already been
created by explicit user action — this matches the existing `ScanPanel.pollUntilTerminal()`
pattern precisely.

---

## 12. Candidate Selection and ActiveScan Launch

### User interaction sequence

```
1. User opens awareness feed (GET /api/v1/passive-awareness)
2. User reviews a PASSIVE_CANDIDATE item
3. User clicks "Investigate"
4. Account selector appears (inline — reuse <select> pattern from ScanPanel)
5. User selects an account
6. "Start investigation" button becomes enabled
7. User clicks "Start investigation"
8. UI generates idempotency key (once per click — stored in component state)
9. UI sends POST /api/v1/intelligence/scans:
     accountId: selectedAccountId
     scopeMode: 'SELECTED'
     requestedMarketIds: [item.marketId]
     sourcePassiveOpportunityId: item.opportunityId
     sourcePassiveOpportunityVersion: item.opportunityVersion
     Idempotency-Key: storedKey
10. UI polls GET /api/v1/intelligence/scans/:scanId until terminal
11a. OPPORTUNITY_FOUND → navigate to /decision-workspace?...
11b. COMPLETED_NO_WORK → show inline "Analysis complete — no active opportunity found"
11c. FAILED → show inline error with diagnostic
11d. Error from POST → show mapped error (see Section 4 error codes)
```

### Key design decisions

**Investigation panel vs. ScanPanel reuse:**
`ScanPanel` manages account selection, market selection (with scope resolution), scan history,
and the full polling/navigation workflow. It was not designed to receive a pre-selected market
and passive candidate context. The simplest V1 approach is a dedicated
`PassiveCandidateInvestigatePanel` component that reuses `ActiveScanService` and `AccountService`
directly — no force-fit of `ScanPanel`.

**Idempotency key:**
Generate `crypto.randomUUID()` once per explicit "Start investigation" click. Store the key
in component state keyed to `opportunityId`. Reuse the stored key if the user retries without
navigating away — this returns the existing idempotent scan rather than creating a duplicate.
Reset the stored key on navigation away from the candidate.

**Account selector:**
No dedicated standalone component exists. Use an inline `<select>` bound to a `signal<string>('')`,
populated from `AccountService.getAccounts()`. This matches the existing pattern in `ScanPanel`.

**Frontend validation is a usability safeguard only.** The server validates:
- Candidate existence and currency
- Account authorization
- Market eligibility
- Passive candidate provenance

Do not bypass the existing server validation by pre-checking eligibility in the frontend.

### Component interaction model

```
PassiveAwarenessFeedComponent
  ├── PassiveCandidateCardComponent (for each item)
  │     └── [Investigate button click]
  │           ↓
  └── PassiveCandidateInvestigatePanel (per candidate, shown inline on click)
        ├── AccountService.getAccounts()     → account <select>
        ├── ActiveScanService.createScan()   → POST /api/v1/intelligence/scans
        └── ActiveScanService.findScan()     → polling until terminal
```

---

## 13. Account Selection

The investigation flow requires an explicitly selected authorized account.

The `Investigate` button must be disabled until an account is selected.
No default account should be pre-selected silently.

Account loading:
```typescript
readonly accounts$ = this.accountService.getAccounts().pipe(
  catchError(() => of([])),
  startWith([])
);
```

The `selectedAccountId` is a `signal<string>('')`. The "Start investigation" button is
enabled only when `selectedAccountId() !== ''`.

Do not pre-populate the account from the passive candidate (candidates are account-independent).
Do not reuse the account selection from a parallel active scan form on the same page — keep
the investigation flows independent to avoid state sharing bugs.

---

## 14. Navigation and Result Presentation

### After successful scan creation and terminal poll

| Terminal status | Markets result | UI action |
|---|---|---|
| `COMPLETED` | `OPPORTUNITY_FOUND` | Navigate to `/decision-workspace?opportunityId=...&opportunityVersion=...&marketId=...&accountId=...` |
| `COMPLETED` | `COMPLETED_NO_OPPORTUNITY` | Show inline: "Analysis complete — no active opportunity found for this candidate." |
| `COMPLETED_NO_WORK` | — | Show inline: "Analysis complete — market was not analyzed." |
| `PARTIALLY_COMPLETED` | — | Show inline: "Analysis partially completed — see scan details." |
| `FAILED` | — | Show inline error with diagnostic |

Navigation to `/decision-workspace` uses the same query parameter pattern as
`Opportunities.openOpportunity()` — reuse the existing route without modification.

Do not create a new ActiveScan result page. The existing `/decision-workspace` route is sufficient.

The source candidate context is preserved in the investigation panel (not the feed) — the feed
remains visible and refreshable after a scan is launched.

---

## 15. Security

- All requests flow through the existing `auth-interceptor.ts` — no additional auth configuration.
- `GET /api/v1/passive-awareness` enforces JWT at the Spring Security filter chain level
  (confirmed by `MiSecurityIntegrationTest`). A 401 response redirects the Angular user to the
  error page via the interceptor.
- `POST /api/v1/intelligence/scans` requires JWT and `Idempotency-Key` — both are added by
  the service layer, not the user.
- Account UUID is held only in component `signal` state — not in `localStorage`, `sessionStorage`,
  or any shared store.
- UI visibility does not constitute trading authorization. The server revalidates all constraints.
- No broker credentials are exposed in the Angular application.
- No passive candidate triggers a trade execution — the investigation path requires explicit
  account selection and an explicit "Start investigation" action.

---

## 16. Accessibility

- All status badges: text label + icon — never color alone.
- Action button labels: `aria-label="Investigate [instrument] passive candidate"`.
- Keyboard navigation: all interactive elements reachable via `Tab`; actions invocable via `Enter`.
- Loading state: `aria-live="polite"` region for the feed status.
- Disabled "Start investigation" button: `disabled` attribute + `title` or `aria-describedby`
  explaining "Select an account to continue".
- Diagnostic codes: shown as text — never as color-only indicators.
- Responsive layout: single-column card stack on viewports below 768 px.
- Expiry and freshness: shown as text ("Expired", "3 min ago") alongside time values.

---

## 17. Testing Strategy

All tests use **Vitest** with fixture data and mocked HTTP. No live HTTP, no broker contact.

### `passive-awareness.service.spec.ts`

- Successful query: response mapped correctly
- All query parameters passed to HTTP correctly
- Empty response (total=0) returned without error
- 401 → observable errors with 401 status
- 503 → observable errors with 503 status
- Pagination parameters passed correctly on page 1+

### `passive-awareness-feed.component.spec.ts`

- Loading state shown during pending HTTP
- Loaded state: candidate items rendered
- Loaded state: non-positive items rendered (muted style)
- Empty state: message shown when total=0 and no active filters
- Filtered-empty state: message + clear-filters shown when total=0 with active filters
- Error state: error message shown for HTTP failure
- Manual refresh button triggers re-query
- `currentOnly` toggle changes query params and re-queries
- Instrument input (debounced) changes query params and re-queries
- Pagination "Next" increments page and re-queries
- Pagination "Previous" decrements page and re-queries
- Component destroy cancels pending subscription

### `passive-candidate-card.component.spec.ts`

- Candidate card shows instrument, provider, productType
- Candidate card shows freshness derived from `evaluatedAt`
- Expired candidate shows expiry badge
- Diagnostic codes mapped to labels — no raw codes shown to user
- Non-positive card shows top diagnostic label
- Investigate button present on PASSIVE_CANDIDATE items
- Investigate button absent on NON_POSITIVE items
- Superseded item shows superseded badge, no Investigate button

### `passive-candidate-investigate-panel.component.spec.ts`

- Accounts loaded from `AccountService.getAccounts()`
- "Start investigation" disabled before account selected
- "Start investigation" enabled after account selected
- POST body: `accountId`, `scopeMode='SELECTED'`, `requestedMarketIds=[marketId]`,
  `sourcePassiveOpportunityId`, `sourcePassiveOpportunityVersion` all present and correct
- Idempotency key generated once per click
- Same key reused on retry without navigation (not a new UUID)
- 409 `PASSIVE_CANDIDATE_NOT_CURRENT` → stale error message shown
- 409 `PASSIVE_CANDIDATE_SUPERSEDED` → superseded error message shown
- 404 `PASSIVE_CANDIDATE_NOT_FOUND` → not-found error message shown
- 503 `PASSIVE_CANDIDATE_IDENTITY_UNAVAILABLE` → unavailable error message shown
- Scan polling: RUNNING → COMPLETED_NO_WORK → "no opportunity found" message shown
- Scan polling: RUNNING → OPPORTUNITY_FOUND → navigate to `/decision-workspace`

### Safety regression tests

- Loading the awareness feed does NOT trigger `POST /api/v1/intelligence/scans`
- Refreshing the feed does NOT trigger a scan or broker operation
- No test contacts live broker or cTrader infrastructure
- Existing `opportunities.spec.ts` passes unchanged
- Existing `scan-panel.spec.ts` passes unchanged
- Angular production build: `npm run build -- --configuration production` succeeds

---

## 18. Acceptance Criteria Mapping

| Story AC | Implementation coverage |
|---|---|
| Dashboard displays monitored markets and awareness state | `PassiveAwarenessFeedComponent` + card layout showing all items |
| Passive candidates show detection rationale and evidence | `diagnosticLabels`, `sourceCutoff`, `evaluatedAt`, `scenario`, `timeframe` |
| Stale, missing, contradictory evidence not hidden | `NON_POSITIVE` items shown by default (`currentOnly=false`); diagnostics visible |
| Distinguishes passive candidate from account-actionable opportunity | `presentationState` mapping: "Candidate" vs "Non-Positive"; explicit labels; no "opportunity" terminology for passive items |
| User can select a candidate and start the existing ActiveScan flow | "Investigate" → `PassiveCandidateInvestigatePanel` → `ActiveScanService.createScan()` |
| Account selection required before active investigation | "Start investigation" button disabled until account selected |
| Passive and active states visually and semantically distinct | Separate card styles, explicit labels, no shared state between feed and scan panel |
| No automatic scan/order/risk/broker operation from feed | Verified by safety regression tests |
| Angular tests cover loading, empty, stale, unavailable and positive states | Service and component unit test matrix above |
| Existing active scan tests and production build remain green | Regression suite |

---

## 19. Implementation Sequencing

Eleven increments in dependency order:

| # | Increment | Files created or modified |
|---|---|---|
| 1 | TypeScript model extension | `core/models/active-scan.model.ts` — add optional passive candidate fields |
| 2 | New API model | `core/models/passive-awareness.model.ts` — new file |
| 3 | `PassiveAwarenessService` + tests | `core/services/passive-awareness.service.ts`, `passive-awareness.service.spec.ts` |
| 4 | `PassiveAwarenessFeedComponent` (view skeleton) | `features/opportunities/awareness-feed/passive-awareness-feed.ts`, `.html`, `.scss` |
| 5 | `PassiveCandidateCardComponent` + diagnostic mapping | `features/opportunities/awareness-feed/passive-candidate-card/` |
| 6 | `PassiveCandidateInvestigatePanel` | `features/opportunities/awareness-feed/passive-candidate-investigate-panel/` |
| 7 | Filter and pagination controls | Inline in `PassiveAwarenessFeedComponent` |
| 8 | Integration into `Opportunities` page | `features/opportunities/opportunities.ts` — add awareness tab/section |
| 9 | Full test suite | Spec files for all new components |
| 10 | Production build verification | `npm run build -- --configuration production` |
| 11 | Documentation | `story.md` AC checked off; `implementation-report.md` |

Component file name conventions follow the existing kebab-case pattern (e.g. `scan-panel.ts`).
Selector convention: `app-passive-awareness-feed`, `app-passive-candidate-card`,
`app-passive-candidate-investigate-panel`.

---

## 20. Risks and Unresolved Decisions

### Open decisions (require human approval)

| Ref | Decision | Recommended option | Alternative |
|---|---|---|---|
| D1 | Integration point: tab within `/opportunities` vs. dedicated `/awareness` route | Tab within `/opportunities` — no new route | New route — cleaner URL, more routing work |
| D2 | Pagination UX: prev/next buttons vs. show-more | Prev/next — matches a paginated API | Show-more — better UX, more complex state |
| D3 | Refresh: manual only vs. bounded polling (e.g. every 2 minutes) | Manual only for V1 | Bounded polling — more live, more complexity |
| D4 | Account selector: inline per card vs. shared with the existing scan panel account context | Inline per card — independent, no cross-contamination | Shared — consistent, risk of state sharing bugs |

### Technical risks

| Risk | Mitigation |
|---|---|
| `provider` and `productType` are null when market data is unavailable | UI must handle null values; never use optional chaining with fallback that crashes |
| `validUntil` is nullable — expiry detection must be null-safe | Use `validUntil !== null && ...` before comparing |
| Idempotency key generated on every render cycle | Generate once per click, store in component signal, not derived from template |
| Polling memory leak on navigation | Use `takeUntilDestroyed()` — not `takeUntil(destroy$)` with manual `Subject` |
| Story 0242 open review gate (R-0242-2 to R-0242-5) | Pre-existing residual risk — not Story 0244's scope |

---

## 21. Non-Goals

- New standalone frontend application.
- New Angular route for passive awareness (D1 — decided as a tab within `/opportunities`).
- Push or WebSocket notifications.
- Automatic ActiveScan escalation triggered by the awareness feed.
- AI-generated explanations, rankings, or confidence scores.
- Trade execution controls on the awareness feed.
- Chart or price visualization components.
- Global state management library (NgRx, Akita, etc.).
- Duplication of the existing `ScanPanel` component.
- Gateway route changes — the passive awareness route is already configured at
  `GatewayRouteConfig.java:146`.
- Database migrations — no backend schema changes are required.
- Backend service changes — Story 0244 is a pure frontend implementation.

---

## Verdict

**READY_WITH_OPEN_DECISIONS**

Decisions D1–D4 are recommended but require human approval before implementation begins.
Once D1–D4 are approved (or confirmed at recommended options), implementation can proceed
directly from this Repository Analysis without a separate Implementation Plan.

---

## Recommended Next Step

Present this Repository Analysis for human approval. Confirm decisions D1–D4 at the recommended
options (or override as needed). Once approved, proceed with Implementation increment 1
(TypeScript model extension) as the smallest coherent starting point.
