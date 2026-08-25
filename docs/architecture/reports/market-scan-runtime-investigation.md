# Investigation Report — Market Scan Runtime Behavior

**Branch:** `investigation/market-scan-runtime-behavior`
**Date:** 2026-08-25
**Scope:** diagnosis only — no functional code modified.
**Trigger:** user test: scan accepted → backend logs show work running →
frontend displays an error before backend logs stop → backend continues → logs stop.

## Executive Summary

**Most probable cause of the user-visible error.** The scan-creation endpoint is
contractually asynchronous (`202 Accepted` + `Location`) but its implementation
**blocks the HTTP request thread for the whole dispatch phase**. For the full
Kraken universe (1 436 eligible markets), the sequential per-market
claim+dispatch loop executed inside `TransactionSynchronization.afterCommit()`
held the Tomcat thread for ≈161 s. The nginx reverse-proxy inside the
`trading-web` container cut the exchange at its default `proxy_read_timeout`
(60 s) and returned its own HTML **504 Gateway Time-out** to Angular, which maps
504 → `UNAVAILABLE` → “Market Intelligence is unavailable. Try again later.”
Because the POST never delivered the `scanId`, tracking never started.
Meanwhile Market Intelligence kept analyzing asynchronously and produced real
StrategyMatches and TradingOpportunities.

**Is Active Scan complete end-to-end? PARTIAL.**
Creation, scope resolution, dispatch claiming, market-context acquisition,
capability analysis, strategy evaluation, StrategyMatch and TradingOpportunity
derivation are implemented and runtime-proven. Two gaps:

1. **Blocking POST** — breaks the async contract the API itself declares.
2. **Missing child write-back** — `active_scan_markets.status` never advances
   past `DISPATCH_REQUESTED` in production code; terminal truth exists only via
   read-time reconciliation against `analysis_executions`.

## User Reproduction

Reproduced live (Playwright/Chromium, fresh user + synchronized Kraken account):

| T+offset | Event |
|---|---|
| T0 | Click “Run market scan” → `POST /api/v1/intelligence/scans` |
| T+60s | Response: **HTTP 504**, body = nginx HTML page |
| T+60s | Panel → error “Market Intelligence is unavailable…” (`UNAVAILABLE`) |
| T+20s…T+3min | MI logs: opportunities created per market on virtual threads |
| T+161s | MI logs `Broken pipe` writing the now-useless 202 response |

Panel showed “Starting scan…” the whole time; **no GET polling was ever sent**
(the scanId was never received).

## Failing Request & Origin (Q1–Q3)

- Failing request: `POST /api/v1/intelligence/scans`.
- Status/body seen by Angular: **504**, nginx HTML body.
- Producer of the 504: **nginx in trading-web** (`proxy_pass http://gateway:8080`,
  default `proxy_read_timeout 60s`). Gateway SCG has no timeout configured;
  Market Intelligence is the component that overstayed (root cause);
  Angular mapping of an unexpected 504 to UNAVAILABLE is legitimate.

## Scan Final State (Q5)

```
active_scans            : DISPATCH_REQUESTED × 3   (no terminal state reached)
active_scan_markets     : DISPATCH_REQUESTED 4002, REGISTERED 135  (frozen)
analysis_executions     : COMPLETED 3985, RUNNING 14, REQUESTED 135,
                          ACCEPTED 1, CONTEXT_BUILDING 2
```

Executions terminate; aggregate and child rows stay pre-terminal.

## Root Cause (precise)

`ActiveScanApplicationService.create()` registers `afterCommit()` →
`ActiveScanDispatchCoordinator.resume(scanId)` which loops **sequentially over
all eligible markets**: DB claim per market
(`ActiveScanDispatchClaimService.claimForDispatch`) then
`AnalysisExecutionService.dispatchRegistered(...)`. Each analysis runs async on
a virtual-thread executor, but the loop itself is synchronous on the request
thread. 1 436 markets ⇒ minutes; nginx aborts at 60 s; the client never gets
the promised `202 {scanId}`.

Secondary: no production code writes `ActiveScanMarketStatus` beyond
`REGISTERED`/`DISPATCH_REQUESTED`; `classify()` reconstructs truth from
`analysis_executions` at read time.

## Lifecycle Mapping (Q12)

Backend enum and frontend union are identical (7 states, same terminals incl.
`COMPLETED_NO_WORK`). Frontend handling of every state is correct
(running vs terminal vs emit). Enum-mismatch hypothesis refuted — the frontend
never mis-handled any state because it never obtained a scanId.

## Runtime Timeline (reproduction, correlated)

```
T0        POST /scans reaches MI (DispatcherServlet first request ever)
T0+3.5s   aggregate + 1 436 child rows persisted (created_at/updated_at)
T0+3.5s   afterCommit -> resume(): sequential claim+dispatch loop starts
T0+22s    first opportunities appear in MI logs (virtual threads)
...       analyses continue; loop still claiming/dispatching children
T+60s     nginx cuts connection -> browser gets 504 -> Angular error view
T+161s    dispatch loop ends; controller builds response; write fails (Broken pipe)
after     executions keep completing asynchronously (COMPLETED count grows)
final     aggregate remains DISPATCH_REQUESTED; no GET ever reconciles it
```

Q4: at error display the scan is `DISPATCH_REQUESTED` (non-terminal).
Q6: yes — post-error log activity belongs to the same scan’s executions.

## Pipeline Outcome (Q7–Q9)

- Q7 StrategyMatch reached: **YES** (pipeline logs “Strategy … MATCH”).
- Q8 TradingOpportunity reached: **YES** (“created opportunity OpportunityId[…]”
  for dozens of markets; `trading_opportunity_versions` table populated).
- Q9 zero-opportunity would be valid (`COMPLETED_NO_WORK` exists for that), but
  here opportunities WERE produced — the failure is upstream of semantics.

## Completeness Matrix

| Stage | Implemented | Wired | Tested | Runtime proven |
|---|---|---|---|---|
| scan creation | ✅ | ✅ | ✅ | ✅ |
| scope resolution | ✅ | ✅ | ✅ | ✅ (1 436 mkts resolved) |
| dispatch claim | ✅ | ✅ | unit | ✅ |
| market context | ✅ | ✅ | unit | ✅ (executions reached RUNNING) |
| capabilities | ✅ | ✅ | unit | ✅ (ConsolidatedIntelligence produced) |
| strategy evaluation | ✅ | ✅ | unit | ✅ (“Legacy OHLC Trend v1 MATCH”) |
| StrategyMatch | ✅ | ✅ | ✅ | ✅ |
| Opportunity derivation | ✅ | ✅ | ✅ | ✅ |
| result projection | ✅ | read-time only | ✅ | ⚠️ never exercised by a client |
| terminal state (aggregate) | ✅ | via reconciliation only | ✅ | ❌ never reached on any real scan |
| frontend tracking | ✅ | ✅ | ✅ | ❌ unreachable (scanId never returned) |

## Classification

**Combination — one blocking-POST defect (primary, user-facing) + one
persistence-completeness gap (secondary).**

Primary = backend orchestration defect (D): synchronous dispatch loop inside an
endpoint declared asynchronous. Not A (frontend false-negative: it never got a
scanId to track), not B (tracking endpoint never used), not F (dependencies all
present and healthy).

Secondary = partial implementation (E): child-row lifecycle write-back absent;
aggregate truth depends entirely on read-time reconciliation, so no external
reader ⇒ frozen status forever.

## Existing Test Coverage

Unit/integration tests exist for creation, idempotency conflict, claim,
lifecycle transition rules, reconciliation derivation and frontend polling
(scan-panel specs, poll-interval token). What no test covers: wall-clock
behavior of `resume()` at realistic universe size through the HTTP boundary —
i.e., exactly where this defect lives.

## DevLog Evaluation

- Freshness: `resolvedRevision e289ee4` == HEAD (fresh, no staleness).
- `get_engineering_context`: 60 evidences but only 2 scan-domain hits
  (`ActiveScanDispatchClaimService`, a Kraken dispatch test). Nothing on
  lifecycle states, projection, reconciliation, or the Angular flow.
- `search_project_history("active scan")`: excellent — surfaced Stories 0005/
  0006/0007/0012 commits (scope resolution, orchestration foundation,
  reconciliation+projection, opportunity-from-match) and the cold-snapshot
  commit; gave the historical skeleton in one call.
- Targeted query for lifecycle-state introduction returned 0 matches (keyword
  sensitivity); repository grep was needed anyway.
- Verdict: DevLog accelerated the *history* dimension; the *runtime behavior*
  diagnosis required direct code reading + live reproduction. It did NOT hint
  at either defect.

## Recommended Fix (not implemented)

Smallest coherent correction honoring the existing async contract:
in `create()`, do not run `resume()` inside `afterCommit()` on the request
thread — hand the scanId dispatch to an async executor (the same virtual-thread
executor already used by the dispatcher) or rely on an outbox/scheduler tick.
The POST then returns `202 {scanId}` immediately; tracking proceeds as designed.
Optionally raise nginx `proxy_read_timeout` as defense-in-depth (config only).
Secondary story candidate: wire execution/market completion back into
`active_scan_markets` (or accept read-time derivation explicitly and document it).

## Suggested Next Story (title + objective only)

**“Async Active Scan dispatch — return 202 immediately and make scans reach
terminal state through polling.”**
