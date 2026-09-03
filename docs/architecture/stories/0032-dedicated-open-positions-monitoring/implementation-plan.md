# Implementation Plan — Story 0032

## Design

```
[Step 1] Extract PositionQueryService from DashboardQueryService
[Step 2] Refactor DashboardQueryService to delegate to PositionQueryService
[Step 3] Create PositionController endpoint
[Step 4] Create Angular PositionService
[Step 5] Create Angular Positions component
[Step 6] Register /positions route
[Step 7] Update template for error-with-preserved-positions
[Step 8] Write backend tests
[Step 9] Write frontend tests
[Step 10] Full regression
```

## Steps

### Step 1 — Extract PositionQueryService

**Objective:** Extract shared position projection logic from DashboardQueryService into a reusable service.

**Current behavior:** `DashboardQueryService` contains private methods `buildPositions()`, `loadMarkets()`, `loadPrices()`, `normalize()`, and an inner `MarketLookup` class. These are only accessible within DashboardQueryService.

**Required change:**

1. **`PositionQueryService.java` (NEW):** Create service with:
   - `buildPositions(String accountId, Account account)` — main projection method
   - `loadMarkets(List<String> marketIds)` — market catalogue lookup
   - `loadPrices(List<String> marketIds)` — price snapshot lookup
   - `normalize(String symbol)` — symbol normalization
   - `MarketLookup` inner record — market catalogue cache

2. **`DashboardQueryService.java`:** Remove private position methods and inject `PositionQueryService`.

**Files:**
- `trading-core/.../dashboard/service/PositionQueryService.java` (NEW)
- `trading-core/.../dashboard/service/DashboardQueryService.java`

**Tests:** Existing DashboardQueryServiceTest updated to verify delegation.

---

### Step 2 — Refactor DashboardQueryService

**Objective:** Delegate position building to PositionQueryService.

**Current behavior:** DashboardQueryService builds positions inline with private methods.

**Required change:**

1. **`DashboardQueryService.java`:** Replace `buildPositions()` call with `positionQueryService.buildPositions()`.
2. Remove private methods: `buildPositions()`, `loadMarkets()`, `loadPrices()`, `normalize()`, `MarketLookup`.

**Files:** `trading-core/.../dashboard/service/DashboardQueryService.java`

**Tests:** DashboardQueryServiceTest updated. `findPriceSnapshots` verification changes from `times(1)` to `times(2)` due to double position query (initial + equity-adjusted).

---

### Step 3 — Create PositionController Endpoint

**Objective:** Expose `GET /api/v1/accounts/{accountId}/positions` returning `List<OpenPositionDashboardView>`.

**Current behavior:** No dedicated positions endpoint exists.

**Required change:**

1. **`PositionController.java` (NEW):**
   - `GET /api/v1/accounts/{accountId}/positions`
   - Authentication required via `principal(authentication).getUserId()`
   - Account ownership verified
   - Delegates to `PositionQueryService.buildPositions()`
   - Returns `ResponseEntity<List<OpenPositionDashboardView>>`

**Files:** `trading-core/.../controller/PositionController.java` (NEW)

**Tests:** PositionControllerTest with 3 test cases.

---

### Step 4 — Create Angular PositionService

**Objective:** Add position data access service.

**Current behavior:** No Angular service for position data.

**Required change:**

1. **`position.service.ts` (NEW):**
   - `getPositions(accountId)` → `GET /api/v1/accounts/{accountId}/positions`
   - Returns `Observable<OpenPositionDashboardView[]>`
   - Uses `HttpClient` through Gateway

**Files:** `trading-os-web/.../core/services/position.service.ts` (NEW)

**Tests:** TypeScript compilation. Existing service tests pass.

---

### Step 5 — Create Angular Positions Component

**Objective:** Create the positions page with all required states.

**Current behavior:** No Positions component exists.

**Required change:**

1. **`positions.ts` (NEW):** Standalone component with:
   - Account selection via `BehaviorSubject`
   - 10-second polling via `timer(0, 10_000)` + `switchMap`
   - Per-account `lastPositionsByAccount` cache for refresh failure preservation
   - Reactive `viewModel$` observable combining accounts state and positions
   - Helper methods: `pnlClass()`, `protectionStatusLabel()`, `protectionStatusClass()`

2. **`positions.html` (NEW):** Template with:
   - Loading state
   - Error state (initial failure)
   - Warning state (refresh failure with preserved positions)
   - Empty state
   - Position cards with all required fields

3. **`positions.scss` (NEW):** Dark slate dashboard styling with warning variant.

**Files:**
- `trading-os-web/.../features/positions/pages/positions/positions.ts` (NEW)
- `trading-os-web/.../features/positions/pages/positions/positions.html` (NEW)
- `trading-os-web/.../features/positions/pages/positions/positions.scss` (NEW)

---

### Step 6 — Register /positions Route

**Objective:** Make `/positions` navigable.

**Current behavior:** Sidebar has `/positions` link but no route exists.

**Required change:**

1. **`app.routes.ts`:** Add route:
   ```typescript
   { path: 'positions', canActivate: [authGuard], component: Positions }
   ```

**Files:** `trading-os-web/.../app.routes.ts`

---

### Step 7 — Update Template for Preserved-State Display

**Objective:** Show both preserved positions and error banner on refresh failure.

**Current behavior:** `@else if` chain shows either positions OR error, never both.

**Required change:**

1. **`positions.html`:** Restructure conditional chain:
   - `positionsError` with empty positions → full error state
   - `positionsError` with non-empty positions → warning banner + position cards
   - `positionsLoading` with empty positions → loading state

2. **`positions.scss`:** Add `.state-panel.warning` class (amber border/text).

**Files:**
- `trading-os-web/.../features/positions/pages/positions/positions.html`
- `trading-os-web/.../features/positions/pages/positions/positions.scss`

---

### Step 8 — Backend Tests

**Objective:** Verify endpoint, ownership, and projection.

**New tests:**
- `PositionControllerTest`: 4 test cases
  - Empty broker data returns empty list
  - Empty positions returns empty list
  - Position list returns correct projection
  - Non-owner cannot retrieve positions (ownership rejection)

**Regression:** All existing Trading Core tests pass (262 total).

---

### Step 9 — Frontend Tests

**Objective:** Verify all component states and behaviors.

**New tests (15 total):**
- Displays positions when data available
- Displays empty state when no positions
- Displays loading state during initial fetch
- Displays error state when retrieval fails
- Preserves last known state on refresh failure
- Shows Long/Short labels correctly
- Displays protection status
- Displays PnL with correct classes
- Shows empty state when no accounts exist
- Shows error state when accounts request fails
- Polls the selected account
- pnlClass returns correct classes
- protectionStatusLabel returns correct labels
- protectionStatusClass returns correct classes
- No broker-mutating controls exist

**Regression:** All existing Angular tests pass (257 total).

---

### Step 10 — Full Regression

**Modules:**
- Trading Core: 262 tests
- Angular: 257 tests
- Angular production build

**Expected:** All pass. No regressions.
