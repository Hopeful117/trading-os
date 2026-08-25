# Story 0027 — Implementation Report

**Date**: 2026-08-25
**Branch**: `feature/story-0027-expose-recent-active-scan-summary`
**Status**: DONE

---

## Contract

```http
GET /api/v1/intelligence/scans?limit=N
Authorization: Bearer <JWT>
X-Actor-Id: <UUID>
```

Returns `200 OK` with `List<ActiveScanSummary>`.

## Default limit

10

## Maximum limit

100

## Minimum limit

1

Invalid limits (<=0 or >100) return `400 Bad Request` with `INVALID_LIMIT` error code.

## ActiveScanSummary

```java
public record ActiveScanSummary(
    UUID scanId,
    UUID accountId,
    ActiveScanStatus status,
    String objective,
    Instant createdAt,
    Instant updatedAt
) {}
```

## Files modified

| File | Change |
|------|--------|
| `application/port/ActiveScanRepository.java` | Added `findRecentByActorId(UUID, int)` |
| `adapter/persistence/SpringDataActiveScanRepository.java` | Added `findByActorIdOrderByCreatedAtDescScanIdDesc(UUID, Pageable)` |
| `adapter/persistence/JpaActiveScanRepository.java` | Implemented `findRecentByActorId` using `PageRequest` |
| `adapter/web/ActiveScanSummary.java` | New file — summary record with `from(ActiveScan)` factory |
| `application/scan/ActiveScanApplicationService.java` | Added `findRecentSummary(UUID, int)` |
| `adapter/web/MarketIntelligenceController.java` | Added `GET /scans` endpoint |

## Repository query

```java
List<ActiveScan> findRecentByActorId(UUID actorId, int limit);
```

Implemented via Spring Data derived query:

```java
List<JpaActiveScanEntity> findByActorIdOrderByCreatedAtDescScanIdDesc(UUID actorId, Pageable pageable);
```

Ordering: `created_at DESC, scan_id DESC`.

## Ownership

Actor-scoped via `X-Actor-Id` header. The `actorId` is parsed and validated by the existing `actorId()` helper method. The repository query filters by `WHERE actor_id = ?`. Client cannot supply `actorId` as a parameter.

## Reconciliation

**NOT USED.** The summary query reads directly from `active_scans` table. No `ActiveScanReconciliationService` call. No child market/execution/opportunity loading.

## Child loading

**NONE.** No `ActiveScanMarket`, `AnalysisExecution`, `PipelineRun`, or `TradingOpportunity` is loaded.

## Database

No migration. No new index. Existing `idx_active_scan_actor_created_at(actor_id, created_at)` supports the query.

## Gateway

No changes. MI is already exposed via wildcard routing.

## Performance

Single table scan with index. No N+1. No child loading. Bounded by limit.
