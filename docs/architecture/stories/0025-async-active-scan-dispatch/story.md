# Story 0025 — Async Active Scan Dispatch

## Goal

Make `POST /api/v1/intelligence/scans` genuinely asynchronous: the endpoint
must return `202 Accepted` + `Location` + `scanId` immediately after commit,
with the sequential claim+dispatch loop executed by a managed worker instead of
the HTTP request thread. The existing Angular polling and read-time
reconciliation must then drive the scan to an observable terminal state without
any frontend change.

## Context

Runtime investigation (`docs/architecture/reports/market-scan-runtime-investigation.md`,
branch evidence prior to this story) proved:

- The API declares an async contract but `afterCommit()` runs the sequential
  claim+dispatch loop over all eligible markets on the request thread.
- Real Kraken scope = 1 436 eligible markets ⇒ ≈161 s request-thread occupancy.
- nginx (`trading-web`) aborts at its default 60 s `proxy_read_timeout` → 504 →
  Angular shows UNAVAILABLE; `scanId` never reaches the client; polling never
  starts.
- Analyses themselves already run asynchronously on virtual threads and reach
  StrategyMatch + TradingOpportunity.
- Backend/frontend lifecycle enums are identical; Angular mapping is correct.

## Problem

Synchronous enqueue/claim/dispatch of the whole scope on the HTTP thread
violates the declared contract and makes the endpoint unusable at realistic
universe size.

## Scope

- Move the dispatch trigger off the request thread while preserving strict
  after-commit semantics (dispatch only observable after the creating
  transaction commits).
- Reuse the repository's existing virtual-thread executor convention; no new
  messaging/outbox infrastructure.
- Keep the per-market loop sequential inside the worker (no aggressive
  parallelization of 1 436 claims).
- Preserve idempotency (Story 0022) and claim semantics
  (`ActiveScanDispatchClaimService`) unchanged.
- Validate at runtime against the real Kraken scope through the unmodified
  Angular UI, including polling sequence, lifecycle progression and terminal
  state via read-time reconciliation.

## Out of scope

- nginx timeout changes (masking, not fixing).
- Any Angular modification (unless polling reveals a concrete new bug).
- Child write-back into `active_scan_markets` (decide AFTER observing real
  polling; reconciliation must get its chance first).
- Parallelizing claims; durable queue/outbox; strategy/risk/opportunity
  algorithm changes.

## Acceptance criteria

1. Automated test proves POST completion is decoupled from dispatch completion
   (blocked-coordinator technique, no timing flakiness).
2. After-commit guarantee preserved: rollback ⇒ no dispatch scheduling.
3. All existing Active Scan tests stay green without weakening assertions.
4. Runtime: real-scope POST returns 202 + scanId in ~seconds (independent of
   total dispatch duration); browser shows POST 202 then GET polls; scan
   reaches an honest terminal state (`COMPLETED`, `PARTIALLY_COMPLETED`,
   `FAILED` or `COMPLETED_NO_WORK` — never faked); `opportunitiesFound`
   matches backend projection; Story 0022 refresh triggers on terminal.
5. Explicit verdict documented: read-time reconciliation SUFFICIENT or
   INSUFFICIENT, with evidence.
6. Zero Angular code changes; zero nginx config changes.

## Constraints

- Deterministic domain untouched (capabilities, strategies, matching,
  opportunities, risk, broker).
- Worker failure semantics must be documented honestly (no speculative retry
  framework).
- Shutdown/crash-after-202 behavior documented as architectural
  characteristic.

## Relevant prior art

Investigation report (primary evidence); Stories 0006/0007 (orchestration,
lifecycle+reconciliation), 0022 (idempotent creation + UX contract).

## Validation expectations

MI Maven tests; Angular tests + build (non-regression, expect no changes);
Docker rebuild of market-intelligence; live Kraken-scope run observed through
browser network log and correlated DB/log timeline; `git diff --check`.
