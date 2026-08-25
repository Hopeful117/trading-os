# Repository Analysis — Story 0027

**Date**: 2026-08-25
**Branch**: `feature/story-0027-expose-recent-active-scan-summary` (from `main` at `2ef8929`)

## Current State

### ActiveScanRepository (port)

**File**: `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/ActiveScanRepository.java`

9 methods. All reads are single-scan by actorId+scanId or actorId+idempotencyKey. No list/recent/latest method exists.

### SpringDataActiveScanRepository

**File**: `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/SpringDataActiveScanRepository.java`

Extends `JpaRepository<JpaActiveScanEntity, UUID>`. 3 custom methods: `findByActorIdAndIdempotencyKey`, `findByActorIdAndScanId`, `transitionStatus`. No list query.

### JpaActiveScanRepository (adapter)

**File**: `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaActiveScanRepository.java`

Implements `ActiveScanRepository`. Maps between domain and JPA entities. All methods delegate to SpringDataActiveScanRepository.

### MarketIntelligenceController

**File**: `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketIntelligenceController.java`

7 endpoints. Scan-related:
- `POST /scans` → `createScan()`
- `GET /scans/{scanId}` → `findScan()`
- `POST /scans/scope` → `resolveScope()`

No `GET /scans` (collection) endpoint.

### ActiveScanApplicationService

**File**: `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/scan/ActiveScanApplicationService.java`

3 public methods: `create`, `findOwned`, `findOwnedProjection`. All for single-scan operations.

### ActiveScan (domain entity)

11 fields: scanId, actorId, accountId, objective, idempotencyKey, requestFingerprint, scopeSnapshot, status, resolvedAt, createdAt, updatedAt.

### ActiveScanStatus

7 values: READY_TO_DISPATCH, DISPATCH_REQUESTED, RUNNING, PARTIALLY_COMPLETED, COMPLETED, FAILED, COMPLETED_NO_WORK. 4 terminal.

### Database

Index `idx_active_scan_actor_created_at ON (actor_id, created_at)` exists. Supports the recommended query.

### ActiveScanResponse (current DTO)

Contains full scan data including `markets[]` (1436+ items) and `progress` (computed by reconciliation). Too heavy for a list.

## Confirmed from Investigation

All findings from the investigation report remain valid on HEAD:
- No list query exists anywhere
- actorId is the ownership boundary
- accountId is scan data, not authorization
- Index exists
- Reconciliation is only triggered by `findOwnedProjection`
- Terminal statuses are immutable

## Gap

Missing: repository list method, application service summary method, REST collection endpoint, summary DTO.
