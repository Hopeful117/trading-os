# Story 0027 — Expose Recent Active Scan Summary Query

## Goal

Add a bounded, actor-scoped read-only query capability allowing any authorized client to discover recent Active Scan activity without knowing a scanId in advance.

## Context

The investigation (`docs/architecture/reports/active-scan-query-capabilities.md`, branch `investigation/active-scan-query-capabilities`) established that:

- The Active Scan persistence model already contains all fields needed for a recent-scan query
- The index `idx_active_scan_actor_created_at(actor_id, created_at)` already exists
- The current API surface is limited to `POST /scans` (create) and `GET /scans/{scanId}` (read single)
- No list/recent/latest query exists anywhere in the stack
- The existing `ActiveScanResponse` DTO is too heavy for a list (contains 1436+ market items per scan)
- Full reconciliation must NOT be triggered by a summary query
- The ownership model is actor-scoped (`X-Actor-Id` header), not account-scoped

## Problem

A Dashboard or any operational client opened later cannot discover recent scan activity without hardcoding a scanId. The only way to get scan data today is to have triggered the scan yourself and retained the scanId from the POST response.

## Scope

### In scope

- Repository method: `findRecentByActorId(UUID actorId, int limit)`
- Spring Data query method using existing index
- Application service method: `findRecentSummary(UUID actorId, int limit)`
- REST endpoint: `GET /api/v1/intelligence/scans?limit=N`
- Response DTO: `ActiveScanSummary` (scanId, accountId, status, objective, createdAt, updatedAt)
- Limit validation (default, minimum, maximum)
- Unit tests for repository, service, controller
- Ownership isolation test (two actors, no cross-leakage)

### Out of scope

- Frontend / Angular changes
- Dashboard integration
- `eligibleMarkets` count (not stored on active_scans, would require child loading)
- `opportunitiesFound` (requires full reconciliation)
- `accountName` resolution (Trading Core call)
- Account-scoped filtering (`?accountId=`)
- Status filtering (`?status=`)
- Pagination framework (simple `limit` suffices)
- WebSocket / SSE
- Lifecycle modifications
- Gateway changes

## Acceptance criteria

1. `GET /api/v1/intelligence/scans` returns recent scans for the authenticated actor
2. `GET /api/v1/intelligence/scans?limit=N` respects the limit parameter
3. Default limit is applied when no parameter is provided
4. Maximum limit is enforced; exceeding it returns an error
5. Results are ordered by `createdAt DESC, scanId DESC`
6. Actor isolation: actor A never sees actor B's scans
7. Empty result returns `200 OK []`
8. No reconciliation is triggered (no child market/execution loading)
9. All existing tests pass
10. New tests cover: repository query, service mapping, controller endpoint, limit validation, ownership isolation
11. Backend build passes (`mvn verify`)

## Constraints

- Reuse existing hexagonal architecture: port → adapter → Spring Data
- `ActiveScanSummary` must be a new record, not a reuse of `ActiveScanResponse`
- No new database migration or index (existing index is sufficient)
- No Gateway changes (MI already exposed via wildcard)
- Preserve existing endpoint contracts (POST /scans, GET /scans/{id})

## Relevant ADRs

- Investigation: `docs/architecture/reports/active-scan-query-capabilities.md`
- Story 0025: async active scan dispatch
- Story 0026: dashboard operational overview

## Validation expectations

- `mvn verify` passes in market-intelligence module
- `git diff --check` clean
- Runtime: `GET /scans` returns actor-scoped results
- Runtime: `GET /scans?limit=1` returns exactly one result
- Cross-check: summary fields match detail endpoint for same scanId
