# Story 0199 - E2E Validation Matrix

Validation date: 2026-11-04. No provider mutation or order submission was performed.

| Area | Check | Result | Evidence / blocker |
|---|---|---|---|
| Safety | Real execution disabled | PASS | `CTRADER_SAFETY_EXECUTION_ENABLED=false`; no order path invoked. |
| Identity | Existing FTMO account | PASS | Existing `FTMO Free cTrader`, external account `49411967`, local broker account `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456`. |
| Provider | Snapshot safety boundary | PASS | Broker Service reached cTrader and returned an explicit incomplete snapshot; no fallback data was used. |
| HTTP | Authenticated synchronization result | PASS | `POST /api/v2/accounts/{id}/synchronize` returned HTTP `409`, `application/problem+json`, `BROKER_ACCOUNT_SNAPSHOT_INCOMPLETE`. |
| Timing | Bounded application response | PASS | Real response completed in `1332 ms`; no unbounded wait observed. |
| Persistence | Incomplete/deferred snapshot admission | PASS | Trading Core did not save the account or replace positions. |
| Error mapping | Provider conflict distinction | PASS | Deferred, provider-conflict and incomplete-snapshot outcomes expose distinct error codes. |
| Partial data | Unavailable or truncated reconciliation collections | PASS | Positions, orders and deals are rejected before local state replacement. |
| Tests | Trading Core focused tests | PASS | Synchronization, HTTP handler and provider-conflict tests passed. |
| Tests | Broker Service focused tests | PASS | cTrader client and authoritative snapshot tests passed. |
| Tests | Full Trading Core suite | BLOCKED | The run exceeded the 120-second tool limit; no failure was attributed to this change. |
| Readiness | Controlled execution | BLOCKED | Provider-authoritative equity, margin and deal facts remain unavailable. |
| Real order | Provider mutation | NOT_TESTED | Explicitly excluded by the Story safety boundary. |

## Result Class

The real synchronization result is `SYNC_FAILED_BOUNDED`: the provider returned
an incomplete snapshot, and the application returned an explicit bounded conflict
without persisting incomplete facts.
