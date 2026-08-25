# Implementation Plan — Story 0027

**Date**: 2026-08-25

## Design Source

`docs/architecture/reports/active-scan-query-capabilities.md`

## Changes

### 1. ActiveScanRepository (port)

Add method:

```java
List<ActiveScan> findRecentByActorId(UUID actorId, int limit);
```

### 2. SpringDataActiveScanRepository

Add Spring Data derived query:

```java
List<JpaActiveScanEntity> findByActorIdOrderByCreatedAtDescScanIdDesc(UUID actorId, Pageable pageable);
```

Spring Data derives the query from the method name. The `Pageable` parameter provides the limit.

### 3. JpaActiveScanRepository (adapter)

Implement `findRecentByActorId`:

```java
@Override
public List<ActiveScan> findRecentByActorId(UUID actorId, int limit) {
    Pageable pageable = PageRequest.of(0, limit);
    return springData.findByActorIdOrderByCreatedAtDescScanIdDesc(actorId, pageable)
        .stream()
        .map(this::toDomain)
        .toList();
}
```

### 4. ActiveScanSummary (new DTO)

New record in `adapter/web/`:

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

### 5. ActiveScanApplicationService

Add method:

```java
@Transactional(readOnly = true)
public List<ActiveScanSummary> findRecentSummary(UUID actorId, int limit) {
    return repository.findRecentByActorId(actorId, limit).stream()
        .map(scan -> new ActiveScanSummary(
            scan.scanId(),
            scan.accountId(),
            scan.status(),
            scan.objective(),
            scan.createdAt(),
            scan.updatedAt()
        ))
        .toList();
}
```

### 6. MarketIntelligenceController

Add endpoint:

```java
@GetMapping("/scans")
public ResponseEntity<List<ActiveScanSummary>> findRecentScans(
        @RequestHeader("X-Actor-Id") String rawActorId,
        @RequestParam(defaultValue = "10") int limit) {
    UUID actorId = actorId(rawActorId);
    // validate limit: 1..100
    List<ActiveScanSummary> summaries = applicationService.findRecentSummary(actorId, limit);
    return ResponseEntity.ok(summaries);
}
```

### 7. Limit validation

- Default: 10
- Minimum: 1
- Maximum: 100
- Invalid (<=0 or >100): return 400 with existing error convention

## Files Modified

| File | Change |
|------|--------|
| `application/port/ActiveScanRepository.java` | Add `findRecentByActorId` |
| `adapter/persistence/SpringDataActiveScanRepository.java` | Add derived query |
| `adapter/persistence/JpaActiveScanRepository.java` | Implement adapter method |
| `adapter/web/ActiveScanSummary.java` | New file |
| `application/scan/ActiveScanApplicationService.java` | Add `findRecentSummary` |
| `adapter/web/MarketIntelligenceController.java` | Add `GET /scans` |

## Files NOT Modified

- Domain entities
- ActiveScanStatus
- ActiveScanReconciliationService
- ActiveScanMarket / ActiveScanMarketRepository
- Gateway
- Angular frontend
- Docker
- Database migrations

## Tests

| Test | Location |
|------|----------|
| Repository: findRecentByActorId | New or existing repo test |
| Service: findRecentSummary mapping | ActiveScanApplicationServiceTest |
| Controller: GET /scans | MarketIntelligenceControllerTest |
| Controller: limit validation | MarketIntelligenceControllerTest |
| Controller: ownership isolation | MarketIntelligenceControllerTest |
| Controller: empty result | MarketIntelligenceControllerTest |

## Reconciliation

**NOT USED.** The summary query reads directly from `active_scans`. No child loading.

## Child loading

**NONE.** No `ActiveScanMarket`, `AnalysisExecution`, `PipelineRun`, or `TradingOpportunity` is loaded.

## Database

No migration. No new index. Existing `idx_active_scan_actor_created_at` is sufficient.

## Gateway

No changes. MI is already exposed via wildcard routing.
