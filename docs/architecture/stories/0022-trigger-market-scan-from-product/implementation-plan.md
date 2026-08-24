# Implementation Plan — Story 0022

Implementable directly from the approved Story, repository analysis and
existing conventions; this plan fixes names and file placement only.

## Files to create

```text
trading-os-web/src/app/core/models/active-scan.model.ts     # contract types + terminal set
trading-os-web/src/app/core/services/active-scan.service.ts # POST scan / GET scan projection
trading-os-web/src/app/features/opportunities/scan-panel/
    scan-panel.ts/.html/.scss/.spec.ts                      # trigger UX + polling session
```

## Files to modify

```text
trading-os-web/src/app/features/opportunities/opportunities.html   # embed panel, wire refresh
trading-os-web/src/app/features/opportunities/opportunities.spec.ts # panel integration test only
```

## Design decisions

1. **Models** — mirror the JSON exactly: `CreateActiveScanRequest`,
   `ActiveScanResponse`, `ActiveScanProgress`, `ActiveScanMarketResult`,
   `StrategyProvenance`, `ScanDiagnostic`; `ACTIVE_SCAN_TERMINAL_STATUSES`
   exported as the single frontend notion of terminality (values copied from
   backend enum; no status invention).
2. **Service** — `createScan(request, idempotencyKey)` sends
   `POST v1/intelligence/scans` with the required header;
   `findScan(scanId)` performs `GET v1/intelligence/scans/{id}`. No logic.
3. **Panel state machine (one reactive stream)** —
   `idle → submitting → running(status,progress) → terminal(result) | error`.
   Run clicks flow through `exhaustMap(runSession$)` so clicks during an
   active session are ignored; the submit button is also `[disabled]` while
   the session is not idle. No manual subscribe in the component class.
4. **Session internals** — create scan → switch to
   `timer(0, POLL_INTERVAL_MS)` piped through `switchMap(findScan)`,
   `takeWhile(v => !terminal, inclusive)`; poll interval provided via
   `SCAN_POLL_INTERVAL_MS` InjectionToken (default 2000ms) for testability.
   Poll errors map to a distinct `trackLost` error view; teardown stops the
   timer via the async pipe unsubscribe.
5. **Account selector** — fed by existing `AccountService`; form requires an
   account (native `required`), objective optional with `maxlength=500`
   mirroring the DTO. New idempotency UUID per run click.
6. **Terminal display** — real status label + progress counts
   (eligible/completed/failed/opportunitiesFound); "View opportunities" links
   to `/opportunities` list; zero-work and zero-found rendered as normal
   outcomes. On terminal completion the panel emits `(scanCompleted)` and the
   Opportunities page calls its existing `refreshOpportunities()`.
7. **Errors** — HTTP 409 → conflict message; 401 → authentication message;
   creation network/validation failures → visible generic error card; no
   stack traces.

## Test plan

* Service: URL/header/body correctness; GET projection mapping.
* Panel: renders accounts; blocks submit without account; running state shown;
  double-click protection (exhaustMap + disabled); bounded polling stops at
  terminal (short injected interval); success with opportunities; success with
  zero found; COMPLETED_NO_WORK; FAILED; 409 conflict; 401; network error;
  scanCompleted emission on terminal completion.
* Opportunities page: panel presence + refresh triggered on scanCompleted
  (existing tests untouched otherwise).

## Validation

Prettier check on changed files, `npm run test:ci`, `npm run build`, bundle
size observation, `git diff --check`. Backend untouched → no backend suites
re-run beyond a zero-diff confirmation.
