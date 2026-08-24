# Engineering Report — Story 0022

Date: 2026-08-25 · Branch: `feature/story-0022-trigger-market-scan-from-product`

## Outcome

The Market Intelligence → Trader loop is now usable from the product:

```text
Trader (Opportunities page)
   ↓ "Run market scan" panel — account + optional objective
POST /api/v1/intelligence/scans  (Idempotency-Key, JWT via Gateway/X-Actor-Id)
   ↓ 202 + Location
GET /api/v1/intelligence/scans/{scanId} polled until terminal status
   ↓ real progress + truthful outcome (found / zero-found / no-work / failed)
(scanCompleted) → existing ACTIVE opportunities list refreshes
```

V1 DoD closure blocker #3 ("trigger market analysis") is implemented at product
level; the empty-opportunities screen is now a legitimate state reachable and
explainable through the UI.

## DevLog MCP usage

* `get_engineering_context` called first with the scan-implementation intent.
* **Freshness**: repository-structure evidence resolved at `5687223` (= HEAD,
  includes merged Story 0021); git-history collector still one merge cycle
  behind (`010ed05`). Noted as a minor sync observation; no action taken.
* Most relevant evidence returned: source snapshot of
  `ActiveScanDispatchClaimService.java` (dispatch/claim lifecycle) at current
  revision, plus Story 0019 commits proving public routing of MI trader APIs.
  No API-layer/controller/DTO evidence was available in context — the contract
  audit therefore went directly to the repository, which answered everything.
* Resources opened: none this story (commit summaries + source snapshot
  sufficed).
* `search_project_history`: not required — no historical question arose that
  the code itself did not answer.
* Git fallbacks: only for branch-convention confirmation.

## Active Scan audit summary (contract discovered)

* `POST /api/v1/intelligence/scans` — headers `Idempotency-Key` (required),
  `X-Actor-Id` (Gateway-injected from JWT); body `{accountId!, objective?≤500,
  requestedMarketIds?}`; returns **202** + `ActiveScanResponse` + Location.
* Server-side idempotency: actor+key+fingerprint reuse or `409 CONFLICT`.
* Async lifecycle by design: `READY_TO_DISPATCH → DISPATCH_REQUESTED → RUNNING
  → COMPLETED | PARTIALLY_COMPLETED | FAILED | COMPLETED_NO_WORK`.
* Projection carries progress counts (incl. `opportunitiesFound`) and
  per-market results with exclusion reasons, diagnostics and truthful strategy
  provenance.
* Ownership (`requireOwnedAccount` vs Trading Core) and market eligibility are
  enforced server-side during scope resolution.

## Backend changes

**None.** Audit concluded Case A (public API already sufficient). Gateway
routing existed since Story 0019; identity chain complete.

## Frontend architecture

* `core/models/active-scan.model.ts`, `core/services/active-scan.service.ts`.
* `features/opportunities/scan-panel/`: single reactive session state machine
  (`idle | submitting | running | terminal | error{reason}`), `exhaustMap`
  double-trigger protection, bounded polling via injected
  `SCAN_POLL_INTERVAL_MS`, embedded once in the Opportunities page (no new
  route, no nav duplication).

## Quality pipeline

| Check | Result |
|---|---|
| Prettier `--check .` | PASS |
| `npm run test:ci` | PASS — 33 files, 205 tests (184 baseline preserved + 21) |
| `npm run build` | PASS — initial bundle 586.59 kB (+18.9 kB) |
| New-code coverage | 97–100% LINE (gate ≥80%) |
| `git diff --check` / working tree | CLEAN |

## Manual validation

* Environment limitation (unchanged since 0021): trading-core container down →
  no login/JWT → authenticated journey not executable locally. Documented, not
  masked; not fixed opportunistically.
* Partial live validation through the running Gateway:
  `POST /api/v1/intelligence/scans` → **401**, `GET …/scans/{id}` → **401** —
  routes exist and are JWT-protected exactly as consumed by the new frontend.
* Zero-result/no-work/failure paths covered deterministically by unit tests;
  no data falsification.

## Bundle impact

567.67 kB → 586.59 kB initial (+18.92 kB). Proportional to an interactive
feature (panel, polling, states); pre-existing budget warning unchanged in
kind; no optimization undertaken (out of scope).

## Remaining limitations

* Market selection not exposed yet (backend `requestedMarketIds` unused by UI).
* Per-market scan results not displayed (aggregates only).
* Lost poll tracking shows unavailable; no re-attach to a running scanId.
* Poll errors stop tracking rather than retrying silently (deliberate).
* Authenticated end-to-end demonstration still pending on this machine
  (trading-core down) — same blocker as Story 0021's manual validation.
* Footer "AI Engine ● Online" placeholder untouched (explicitly out of scope).

## Suggested next Story

The logical next frontier in the trader journey is now:

```text
Opportunity → Trade Plan
```

i.e. surfacing TradePlan creation/consultation for an opportunity (DoD
blocker #4), consuming `/api/v1/trade-plans/analyses/{executionId}/trade-plans`
orchestration already routed publicly. This crosses into Trading Core risk
acknowledgment territory and is meaningfully larger than 0022 — it should be
its own Story after human review of this one. No Story artifacts created.
