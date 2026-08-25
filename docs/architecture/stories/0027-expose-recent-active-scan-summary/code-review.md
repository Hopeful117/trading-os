# Story 0027 — Code Review

**Date**: 2026-08-25
**Status**: APPROVED

---

## Security

- **Actor-scoped**: All reads filtered by `actor_id` from `X-Actor-Id` header
- **No cross-user leakage**: Verified by `findRecentScansDoesNotLeakAcrossActors` test
- **actorId not client-controlled**: Parsed from header, validated as UUID, throws `AUTHENTICATION_REQUIRED` on missing/invalid
- **No accountId filter**: Correctly omitted per Story scope

## Performance

- **Bounded query**: `LIMIT ?` via Spring Data `Pageable`
- **Existing index**: `idx_active_scan_actor_created_at` used
- **No child loading**: Summary reads only from `active_scans` table
- **No reconciliation**: `ActiveScanReconciliationService` never called
- **No N+1**: Single query returns all results

## Architecture

- **Repository port respected**: `ActiveScanRepository` interface extended
- **Adapter respected**: `JpaActiveScanRepository` implements via `SpringDataActiveScanRepository`
- **Application service owns orchestration**: `findRecentSummary` maps domain to DTO
- **Controller remains thin**: Limit validation + actor parsing + delegation
- **Existing patterns followed**: `ResponseEntity`, `@ControllerAdvice`, standalone MockMvc tests

## Semantics

- **Latest = most recently created**: `ORDER BY created_at DESC, scan_id DESC`
- **Persisted status may be stale for non-terminal scans**: Acceptable for summary view
- **Terminal statuses authoritative**: `COMPLETED`, `FAILED`, etc. are immutable
- **No new status taxonomy**: Reuses existing `ActiveScanStatus`

## Scope

- **No frontend**: Angular files untouched
- **No lifecycle redesign**: Dispatch, claiming, reconciliation unchanged
- **No new analytics**: No `eligibleMarkets`, `opportunitiesFound`, or derived metrics
- **No account filter**: `?accountId=` intentionally omitted
- **No pagination framework**: Simple `limit` parameter

## Tests

- 4 service tests: ordering, limit, empty actor, no reconciliation
- 7 controller tests: empty list, summaries, limit, invalid limits, actor requirement, actor isolation
- All 305 MI module tests pass
- InMemoryActiveScanRepository implementations updated in 3 other test files
