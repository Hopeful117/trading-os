# Story 0244 — Engineering Report

**Date:** 2026-11-14
**Status:** Merged — PR #296 (`0d8e8107` on `main`) — pending independent code review

---

## 1. Summary

Story 0244 delivered the Passive Awareness Dashboard: a tab within the existing `/opportunities`
page that surfaces the passive awareness feed and provides a user-controlled entry point into the
existing ActiveScan workflow. All 10 acceptance criteria were satisfied and confirmed by the test
suite.

---

## 2. Delivery Evidence

| Item | Evidence |
|---|---|
| Implementation | PR #296, commits `673f8d64`, `0d8e8107` |
| Merge | `c034970f` on `main` (2026-11-14) |
| Test suite | 66 test files, 635 tests, all passing |
| Production build | 1.03 MB, no errors after budget raise to 1.5 MB |
| Implementation Report | `implementation-report.md` (2026-11-14) |

---

## 3. Validation Results

```
Test Files: 66 passed (66)
Tests:      635 passed (635)

Production build: 1.03 MB — no errors
```

New tests: 38 (7 service + 9 feed + 11 card + 11 investigation panel).
Pre-existing tests: unchanged and green.

---

## 4. Acceptance Criteria

| AC | Validated | Evidence |
|---|---|---|
| Dashboard displays monitored markets and awareness state | ✅ | `passive-awareness-feed.html` loaded state; card renders instrument, provider, state |
| Passive candidates show detection rationale and evidence | ✅ | Card shows freshness, scenario, cutoff; diagnostics collapsible section |
| Stale, missing, contradictory evidence not hidden | ✅ | NON_POSITIVE items rendered with diagnostic labels; null provider handled |
| Distinguishes passive candidate from active opportunity | ✅ | `presentationState` mapped to explicit labels; separate tab from active table |
| User can select a candidate and start ActiveScan flow | ✅ | `PassiveCandidateInvestigatePanel` → `createScan` → polling → navigate |
| Account selection required before active investigation | ✅ | `canStart = selectedAccountId() !== '' && status === 'idle'` |
| Passive and active states visually and semantically distinct | ✅ | Separate tab; separate component; distinct CSS classes and labels |
| No automatic scan/order/risk/broker operation from feed | ✅ | Safety regression test confirms `createScan` not called on load |
| Angular tests cover loading, empty, stale, unavailable, positive states | ✅ | 38 new tests across service, feed, card, panel |
| Existing active scan tests and production build remain green | ✅ | All 635 tests pass; production build has no errors |

---

## 5. Architecture Notes

### Tab integration (D1 — approved)

`PassiveAwarenessFeed` added as a tab in `/opportunities`. The `activeTab` signal on `Opportunities`
controls which tab is visible. The existing `ScanPanel` and opportunity table render only when
`activeTab() === 'active'`. No new Angular route created.

### Manual refresh only (D3 — approved)

No automatic polling on the awareness feed. Manual refresh via Subject + startWith pattern.
`takeUntilDestroyed()` prevents memory leaks on navigation away.

### Idempotency (D4 — approved)

`crypto.randomUUID()` generated once per explicit Investigate click, stored in
`storedIdempotencyKey`, reused on retry, cleared on close.

### Polling token

`PASSIVE_SCAN_POLL_INTERVAL_MS` injection token (factory default 2000ms) enables test isolation
without `fakeAsync`. Tests inject `useValue: 25` with Subject-based controlled emission, consistent
with the existing `SCAN_POLL_INTERVAL_MS` pattern in `ScanPanel`.

### Model gap resolved

`CreateActiveScanRequest` now includes `sourcePassiveOpportunityId?: string` and
`sourcePassiveOpportunityVersion?: number`, closing the Story 0243 contract gap identified in
the Repository Analysis.

---

## 6. Residual Risks

| Ref | Risk | Impact | Mitigation |
|---|---|---|---|
| R-0244-1 | Bundle at 1.03 MB; `maximumWarning` threshold exceeded | Low | Budget raised; lazy-loading awareness feed recommended if bundle continues to grow beyond 1.5 MB |
| R-0244-2 | `DecisionContextService` not mocked in `opportunities.spec.ts` | Low | Pre-existing gap; `readiness$` only uses it on non-empty account selection; no test regression introduced |
| R-0244-3 | No cross-service E2E test for the passive awareness feed | Medium | Addressed by Story 0245 |

---

## 7. Gateway Integration

The passive awareness endpoint is already routed at `GatewayRouteConfig.java:146`:

```
/api/v1/passive-awareness/** → market-intelligence
```

Angular URL construction `${gatewayUrl}v1/passive-awareness` = `/api/v1/passive-awareness` matches.
Security enforced at market-intelligence (`MiSecurityConfiguration`; JWT required; 401 without it).

**Gap:** No gateway routing integration test covers this route. Tracked in audit finding F-01
and Story 0245 acceptance criteria.

---

## 8. Outstanding Gates

| Gate | Status |
|---|---|
| Implementation complete | ✅ |
| Test suite passing | ✅ |
| Production build passing | ✅ |
| PR merged | ✅ |
| Independent code review | ✗ — pending |
| Human approval of review findings | ✗ — pending |

---

## 9. Recommended Next Step

Independent code review. After review, update this report and close the governance gate.
Cross-service validation covered by Story 0245.
