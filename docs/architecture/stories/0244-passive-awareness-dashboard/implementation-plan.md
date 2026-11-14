# Implementation Plan — Story 0244: Passive Awareness Dashboard

**Status: APPROVED 2026-11-14 — D1–D4 confirmed at recommended defaults — implementation authorized**

**Story:** 0244 — Add the Passive Awareness Dashboard
**Epic:** 0238
**Analysis reference:** `repository-analysis.md`
**Author:** OpenClaw (draft)
**Date:** 2026-11-14

---

## Governance Note

Per `AGENTS.md`, an Implementation Plan is required "only when the Story cannot be implemented
safely from the approved Story, Repository Analysis, accepted ADRs, and existing repository
conventions." This plan is provided as optional scaffolding. The Repository Analysis alone is
sufficient for implementation once decisions D1–D4 are approved.

**This plan is a draft. It is not approved. No implementation is authorized until:**
1. The Repository Analysis is approved by the human engineer.
2. Decisions D1–D4 are confirmed.
3. This plan (or the RA alone) is explicitly approved.

Commit, push, and merge require separate human authorization.

---

## Assumptions (from approved Repository Analysis decisions)

This plan assumes decisions at the recommended options from `repository-analysis.md`:

| Decision | Assumed option |
|---|---|
| D1 — Integration point | Tab/section within `/opportunities` — no new route |
| D2 — Pagination | Prev/next buttons |
| D3 — Refresh | Manual refresh only for V1 |
| D4 — Account selector | Inline per candidate, independent of ScanPanel |

If any decision is overridden, the affected increments must be revised before implementation.

---

## Scope

**In scope:**
- `trading-os-web` module only
- New TypeScript model files and service
- New Angular standalone components
- Integration into existing `Opportunities` page
- Vitest unit tests for all new code
- Angular production build verification

**Out of scope:**
- Backend changes (all APIs are merged and complete)
- Gateway changes (passive-awareness route already configured)
- Database migrations
- Commit, push, or merge (human responsibility)

---

## Pre-implementation checklist

Before starting, verify:
- [ ] Working on `main` or a new branch cut from `main` (`97d8a865`)
- [ ] `git status` is clean
- [ ] `npm ci` passes in `trading-os-web/`
- [ ] Existing test suite passes: `npm test` in `trading-os-web/`

---

## Increment 1 — TypeScript model extension

**Purpose:** Add the Story 0243 passive candidate fields to the existing `CreateActiveScanRequest`
interface. This is the only change to an existing application file.

**File:** `trading-os-web/src/app/core/models/active-scan.model.ts`

**Change:** Add two optional fields to `CreateActiveScanRequest` (currently at line 63):

```typescript
export interface CreateActiveScanRequest {
  accountId: string;
  objective?: string;
  requestedMarketIds?: string[];
  scopeMode?: ActiveScanScopeMode;
  sourcePassiveOpportunityId?: string;        // added: Story 0243 backend contract
  sourcePassiveOpportunityVersion?: number;   // added: Story 0243 backend contract (Long >= 1)
}
```

**Verification:** TypeScript compilation succeeds; existing scan-panel tests unchanged.

---

## Increment 2 — New API model

**Purpose:** Define all types for the passive awareness feed.

**File (new):** `trading-os-web/src/app/core/models/passive-awareness.model.ts`

**Contents:**
- `PassiveAwarenessResponse` interface (all 27 response fields from RA §4)
- `PassiveAwarenessPageResponse` interface
- `PassiveAwarenessQueryParams` interface
- `PassiveAwarenessItem` interface (derived view model — extends `PassiveAwarenessResponse` with
  `isCandidate`, `isExpired`, `freshnessSummary`, `diagnosticLabels`)
- `AwarenessFeedView` discriminated union
- `PaginationState` interface
- `DIAGNOSTIC_LABEL_MAP` constant — deterministic code-to-category mapping (RA §7)
- `mapToPassiveAwarenessItem(response: PassiveAwarenessResponse, now?: Date): PassiveAwarenessItem`
  pure function

**Verification:** TypeScript compilation succeeds; no imports from other new files required.

---

## Increment 3 — PassiveAwarenessService

**Purpose:** HTTP service wrapping `GET /api/v1/passive-awareness`.

**File (new):** `trading-os-web/src/app/core/services/passive-awareness.service.ts`

```typescript
@Injectable({ providedIn: 'root' })
export class PassiveAwarenessService {
  private readonly http = inject(HttpClient);

  query(params: PassiveAwarenessQueryParams): Observable<PassiveAwarenessPageResponse> {
    // Build HttpParams from non-null params; call GET v1/passive-awareness
  }
}
```

**Test file (new):** `trading-os-web/src/app/core/services/passive-awareness.service.spec.ts`

Test cases: successful query, all params passed, empty response, 401 error, 503 error,
pagination params on page 1+.

Use `provideHttpClientTesting()` (Vitest + Angular 21 pattern — matches existing test setup).

---

## Increment 4 — PassiveAwarenessFeedComponent (skeleton)

**Purpose:** Reactive feed component with Observable view state.

**Directory (new):** `trading-os-web/src/app/features/opportunities/awareness-feed/`

**File (new):** `awareness-feed/passive-awareness-feed.ts`

```typescript
@Component({
  selector: 'app-passive-awareness-feed',
  imports: [AsyncPipe, ...],
  templateUrl: './passive-awareness-feed.html',
  styleUrl: './passive-awareness-feed.scss',
})
export class PassiveAwarenessFeed {
  private readonly awarenessService = inject(PassiveAwarenessService);
  private readonly refreshSubject = new Subject<void>();
  private readonly filterParams = signal<PassiveAwarenessQueryParams>({ currentOnly: false, page: 0, size: 20 });

  readonly view$: Observable<AwarenessFeedView> = this.refreshSubject.pipe(
    startWith(undefined),
    switchMap(() => this.awarenessService.query(this.filterParams()).pipe(
      map(r => mapToFeedView(r, this.filterParams())),
      catchError(err => of(errorView(err))),
      startWith({ status: 'loading' as const }),
    )),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  refresh(): void { this.refreshSubject.next(); }
  setCurrentOnly(value: boolean): void { this.filterParams.update(p => ({ ...p, currentOnly: value, page: 0 })); this.refresh(); }
  setInstrumentFilter(value: string): void { ... debounced ... }
  goToPage(page: number): void { this.filterParams.update(p => ({ ...p, page })); this.refresh(); }
}
```

Template: `@switch` on `view$.status` — loading skeleton, error card, empty message,
filtered-empty message, loaded grid of `app-passive-candidate-card`.

---

## Increment 5 — PassiveCandidateCardComponent

**Purpose:** Card presentation for a single awareness item.

**Directory (new):** `awareness-feed/passive-candidate-card/`

**File (new):** `passive-candidate-card.ts`

Inputs (signal-based):
- `item = input.required<PassiveAwarenessItem>()`

Output:
- `investigateClicked = output<PassiveAwarenessItem>()`

Template shows:
- Instrument, provider (or "unavailable"), productType, direction
- Presentation state badge ("Candidate" or "Non-Positive")
- Freshness summary
- Expiry badge if `isExpired`
- Diagnostic category label if `!isCandidate` (top category only; full list in `DisclosureSection`)
- Investigate button (enabled only if `isCandidate && !isExpired && !isSuperseded`)

**Test file:** `passive-candidate-card.spec.ts` — all item-level states from RA §17.

---

## Increment 6 — PassiveCandidateInvestigatePanel

**Purpose:** Inline investigation form — account selection + scan launch + result display.

**Directory (new):** `awareness-feed/passive-candidate-investigate-panel/`

**File (new):** `passive-candidate-investigate-panel.ts`

Inputs:
- `candidate = input.required<PassiveAwarenessItem>()`

Output:
- `closed = output<void>()`

Internal state (signals):
- `selectedAccountId = signal('')`
- `idempotencyKey = signal<string | null>(null)` — set once on first "Start investigation" click
- `investigationView = signal<InvestigationView>({ status: 'idle' })`

```typescript
type InvestigationView =
  | { status: 'idle' }
  | { status: 'selecting-account' }
  | { status: 'scanning'; scanId: string }
  | { status: 'no-opportunity' }
  | { status: 'opportunity-found'; opportunityId: string; opportunityVersion: number }
  | { status: 'error'; code: string; message: string };
```

Accounts loaded via `AccountService.getAccounts()` with `async` pipe.

On "Start investigation":
1. If `idempotencyKey()` is null: set `idempotencyKey(crypto.randomUUID())`.
2. Call `ActiveScanService.createScan(request, idempotencyKey())`.
3. On 201/200: start polling `findScan(scanId)` with `timer(2000, 2000).pipe(takeWhile(!terminal), takeUntilDestroyed())`.
4. Navigate or update `investigationView` based on terminal status.

**Test file:** `passive-candidate-investigate-panel.spec.ts` — all investigation workflow cases
from RA §17.

---

## Increment 7 — Filter and pagination controls

**Purpose:** Add filter controls inline in `PassiveAwarenessFeedComponent`.

This increment is part of the `PassiveAwarenessFeedComponent` template and logic — no separate
component required for V1.

Controls:
- `currentOnly` toggle: `<input type="checkbox">` → calls `setCurrentOnly()`
- Instrument filter: `<input type="text">` with 300 ms debounce → calls `setInstrumentFilter()`
- Prev/Next pagination buttons → call `goToPage()`
- Truncation notice when `truncated=true`

---

## Increment 8 — Integration into Opportunities page

**Purpose:** Wire the awareness feed into the existing `Opportunities` page.

**File modified:** `trading-os-web/src/app/features/opportunities/opportunities.ts`

Add `PassiveAwarenessFeed` to `imports`. Add a tab/section toggle to the template.
The existing scan panel and opportunities list remain unchanged.

**File modified:** `trading-os-web/src/app/features/opportunities/opportunities.html`

Add a tab control (`@if` / `@switch`) to toggle between "Active Opportunities" and "Awareness".
The `PassiveAwarenessFeed` component is rendered in the awareness tab.

No account context needs to be passed from `Opportunities` to `PassiveAwarenessFeed` — the
investigation panel manages its own account selection independently.

---

## Increment 9 — Full test suite

**Purpose:** Complete unit tests for all new components and service.

Verify that all test cases from RA §17 are covered. Run the full test suite:

```bash
cd trading-os-web
npm test
```

All 10+ new test files must pass. Existing test files must remain green.

---

## Increment 10 — Production build verification

**Purpose:** Confirm the Angular build succeeds with no compilation errors.

```bash
cd trading-os-web
npm run build -- --configuration production
```

Build must succeed with no errors. Bundle size warnings are acceptable.

---

## Increment 11 — Documentation

**Purpose:** Update story.md acceptance criteria; create implementation report.

- `docs/architecture/stories/0244-passive-awareness-dashboard/story.md`: check off all 10 acceptance criteria.
- `docs/architecture/stories/0244-passive-awareness-dashboard/implementation-report.md`: create
  following the standard template.

---

## Implementation report outline

The implementation report must cover:
1. Repository baseline at time of implementation
2. Workflow and approvals
3. Outcome summary
4. Changes: files created, files modified, files unchanged
5. Acceptance criteria — each criterion verified with evidence
6. Tests executed and results
7. Deviations from the approved plan (if any)
8. Residual risks
9. Final verdict
10. Recommended next step

---

## Validation sequence

```
1. npm ci          (trading-os-web)
2. npm test        (trading-os-web — all existing + new tests)
3. npm run build -- --configuration production  (trading-os-web)
4. git diff --check
5. Human diff review in IDE
```

No backend service tests are required (no backend changes).
