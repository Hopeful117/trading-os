# Story 0028 — Integrate Recent Active Scan into Dashboard

## Goal

Complete the Dashboard MI panel with recent Active Scan activity, allowing the trader to see the latest scan status at a glance.

## Context

Story 0026 established the Dashboard MI panel with active opportunities. Story 0027 added `GET /api/v1/intelligence/scans?limit=N` returning `ActiveScanSummary[]`. This Story connects the two.

## Problem

The Dashboard shows active opportunities count but cannot answer: "What is the most recent Market Intelligence scan and what is its status?"

## Scope

### In scope

- Add `ActiveScanSummary` interface to frontend models
- Add `findRecent(limit?)` to `ActiveScanService`
- Add `scanState$` observable to Dashboard
- Display latest scan status in MI panel
- Empty state: "Aucun scan exécuté"
- Error state: separate from opportunities error
- Status labels in French
- Tests for all new states

### Out of scope

- Backend changes
- Scan detail loading (`GET /scans/{id}`)
- Scan history page
- eligibleMarkets / opportunitiesFound
- Account-scoped filtering
- Pagination
- New Angular services

## Acceptance criteria

1. MI panel shows latest scan status with timestamp
2. Empty scans shows "Aucun scan exécuté" (not error)
3. Scan query failure shows error without breaking opportunities
4. Opportunities failure does not break latest scan display
5. Account dashboard unaffected by MI failures
6. No `GET /scans/{id}` called by Dashboard
7. All existing tests pass
8. New tests cover: scan loaded, no scans, scan error, various statuses

## Constraints

- Reuse `ActiveScanService` (no new service)
- Reactive composition (Observable + async pipe)
- No backend changes
- Use existing `ActiveScanSummary` contract from Story 0027
