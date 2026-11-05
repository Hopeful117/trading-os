# E2E Validation Matrix - Story 0200

## Safety Preconditions

| Check | Result | Evidence |
|---|---|---|
| Target external account | PASS | `49411967` confirmed in the existing FTMO mapping |
| Trading Core broker account | PASS | `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456` |
| Execution mode | PASS | `LIVE` boundary retained; observation remains READ-ONLY |
| Provider execution flag | PASS | `CTRADER_SAFETY_EXECUTION_ENABLED=false` |
| Order mutation | PASS | No order API was invoked by the implementation or validation |

## Local Contract Matrix

| Scenario | Expected behavior | Result |
|---|---|---|
| Fresh complete snapshot | Persist authoritative account and available collections | PASS, targeted and persistence tests |
| Fresh incomplete snapshot with balance | Persist balance and capability metadata; return `SYNCED_PARTIAL` | PASS, targeted and persistence tests |
| Unavailable positions | Preserve existing local positions; record unavailable status | PASS, targeted test |
| Truncated deals | Do not apply close/baseline mutation; record truncated status | PASS, targeted test |
| Available empty positions/orders | Distinguish from unavailable collections | PASS, controller/service tests |
| Missing equity provenance | Do not copy or derive equity | PASS, persistence test and existing risk contract |
| Stale snapshot | Reject before mutation | PASS, existing targeted regression test |
| Identity mismatch | Reject before mutation | PASS, existing targeted regression test |
| Duplicate position reference | Reject before mutation | PASS, existing targeted regression test |
| Repeated synchronization | Do not duplicate local trades | PASS, existing targeted/persistence tests |
| Partial result API | Return structured diagnostic-only JSON | PASS, controller test |

## Runtime Matrix

| Scenario | Result | Evidence / Limitation |
|---|---|---|
| Trading Core image rebuild | PASS | `docker compose build trading-app` |
| Trading Core startup | PASS | Container started and registered with Eureka |
| Flyway V52 migration | PASS | Production log: schema advanced from v51 to v52 |
| Authenticated V2 synchronization request | PASS | HTTP 200 from the authenticated browser session |
| Provider snapshot outcome | PASS | `INCOMPLETE/FRESH`; Trading Core logged start and completion |
| Persisted FTMO partial metadata | PASS | PostgreSQL row matched the V2 response for external account `49411967` |
| Risk authorization | BLOCKED AS DESIGNED | Response is diagnostic-only; no execution authorization is claimed |
| Provider mutation | NOT PERFORMED | Safety flag remained disabled |

## Runtime Next Step

Stabilize or trace the Gateway/Eureka route, then perform one bounded retry of
the authenticated V2 synchronization. Record only the structured status,
persisted capabilities, freshness, completeness and bounded reasons.
