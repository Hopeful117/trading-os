# Implementation Report — Story 0022

Date: 2026-08-25 · Branch: `feature/story-0022-trigger-market-scan-from-product`
(base `origin/main` @ `5687223`, Story 0021 merged)

## What was implemented

Frontend-only. Zero backend files modified (verified via `git status`).

### Models (`core/models/active-scan.model.ts`)

Exact mirror of the served contract: `ActiveScanResponse`, `CreateActiveScanRequest`,
`ActiveScanProgress`, `ActiveScanMarketResult`, `StrategyProvenance`,
`ScanDiagnostic`; string-literal unions copied from backend enums
(`ActiveScanStatus`, `AnalysisExecutionStatus`, `MarketEligibilityReason`,
outcome values); `ACTIVE_SCAN_TERMINAL_STATUSES` + `isActiveScanTerminal()`
as the single frontend terminality notion (values from `isTerminal()`).

### Service (`core/services/active-scan.service.ts`)

* `createScan(request, idempotencyKey)` → `POST v1/intelligence/scans` with the
  required `Idempotency-Key` header.
* `findScan(scanId)` → `GET v1/intelligence/scans/{id}` projection.

No lifecycle, eligibility or scoring logic client-side.

### Scan panel (`features/opportunities/scan-panel/`)

Embedded at the top of the existing Opportunities page — one navigation entry,
no new route, no duplicated opportunity rendering.

Reactive session state machine, one stream rendered via `async`:

```text
idle → submitting → running{scan} → terminal{scan}
                  ↘ error{CONFLICT | UNAUTHORIZED | UNAVAILABLE}
```

* **Trigger**: account `<select>` fed by the existing `AccountService`
  (accounts state `{loaded, accounts}` with inline error + retry), optional
  objective input (`maxlength=500`, trimmed; empty omitted), single run button.
* **Double-trigger protection**: run clicks flow through
  `exhaustMap(runSession)` — clicks during an active session are ignored —
  plus `[disabled]="!accountId || (busy$ | async)"`. A fresh
  `crypto.randomUUID()` Idempotency-Key is generated per logical attempt;
  accepted scans are only ever followed up via GET (never re-posted).
* **Polling**: `timer(interval, interval)` → `switchMap(findScan)` →
  `takeWhile(!terminal, inclusive)`; interval injected via
  `SCAN_POLL_INTERVAL_MS` (default 2000 ms) for testability; subscription ends
  automatically at completion or teardown through the async pipe. Poll errors
  map to an explicit UNAVAILABLE view — no silent retry loop, no fabricated
  failure.
* **Running view**: real status label + real progress counts
  (candidates/eligible/excluded/running/completed/failed) + last `updatedAt`;
  explicitly labeled as auto-refreshing; no invented percentages.
* **Terminal views** (backend statuses verbatim):
  * `COMPLETED` — success; shows `opportunitiesFound`; when zero, a dedicated
    note states this is not an error ("No setup matched current strategies").
  * `PARTIALLY_COMPLETED` — "completed with partial results".
  * `FAILED` — distinct failure rendering.
  * `COMPLETED_NO_WORK` — "No eligible market to scan right now" (no progress
    grid shown).
  All terminals emit `(scanCompleted)` once; the Opportunities page binds it to
  its existing `refreshOpportunities()` so the ACTIVE list below refreshes.
* **Errors**: 409 → conflict message; 401/403 → unauthorized message; other →
  unavailable message. No stack traces surfaced.
* Product honesty: wording is "Market scan" / "Market Intelligence" /
  strategies / opportunities — no AI claims anywhere.

## Tests added (21)

| File | Covers |
|---|---|
| `core/services/active-scan.service.spec.ts` | URL/method/header/body mapping, GET projection |
| `features/opportunities/scan-panel/scan-poll-interval.spec.ts` | default interval factory |
| `features/opportunities/scan-panel/scan-panel.spec.ts` | accounts render/retry/error+empty, submit gating, request body & key, repeated-trigger protection (`exhaustMap`), running→terminal progression with deterministic poll subjects, polling stops after terminal, `scanCompleted` emission, zero-found vs COMPLETED_NO_WORK vs FAILED distinctions, 409/401/503 mappings |
| `features/opportunities/opportunities.spec.ts` (extended) | panel embedded; list refresh on `scanCompleted` |

## Validation executed

* Prettier `--check .` — clean (lesson from Story 0021 applied before push).
* `npm run test:ci` — **33 files, 205 tests passing** (baseline 184 + 21).
* `npm run build` — success; initial bundle 586.59 kB (+18.92 kB vs 0021's
  567.67 kB), proportional to a full interactive feature; pre-existing budget
  warning unchanged in kind.
* Coverage of new sources: service/model/template/panel 97–100% LINE;
  poll-interval token covered via default-factory test.
* Backend diff: none → backend suites not re-run (per quality pipeline rules).

## Manual validation attempted

Local stack state unchanged since Story 0021: gateway/broker/market-data/
market-intelligence/eureka containers up, **trading-core down (17081)** and
trading-web container down (17085). Consequences:

* Authenticated journey (login → trigger → follow) still not executable:
  trading-core owns login/JWT.
* Live wiring proof obtained instead — through the running Gateway:
  * `POST /api/v1/intelligence/scans` (with Idempotency-Key probe header) → **401**
  * `GET /api/v1/intelligence/scans/{id}` → **401**

  Both confirm the public routes exist and sit behind JWT enforcement exactly
  as the frontend will call them.
* No production data was falsified to force an opportunity; unit tests cover
  zero-result and no-work outcomes deterministically.
