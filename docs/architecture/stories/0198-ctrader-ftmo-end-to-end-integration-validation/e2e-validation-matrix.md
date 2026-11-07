# Story 0198 - E2E Validation Matrix

Validation date: 2026-11-07. No provider mutation or order submission was performed.

| Area | Check | Result | Evidence type | Evidence / blocker |
|---|---|---|---|---|
| Runtime | Docker services and Broker Service health | PASS | Environment check | Services running; Broker Service health returned HTTP 200. |
| Runtime | cTrader environment | PASS | Environment check | Effective environment is `live`. |
| Runtime | Execution safety | PASS | Environment check | `CTRADER_SAFETY_EXECUTION_ENABLED=false`. |
| Identity | Existing FTMO connection | PASS | Real application state | Display name `FTMO Free cTrader`, mode `LIVE`, status `CONNECTED`. |
| Identity | Local broker account | PASS | Real application state | Existing local broker account resolved through the normal UI/API path. |
| Identity | Provider account identity | PASS | Real provider validation | Provider response identity matched the configured cTrader connection; external account ID `49411967`. |
| Connectivity | Read-only account snapshot | PASS | Real provider validation | Snapshot returned fresh account data with matching identity. |
| Connectivity | cTrader GBP/USD weekend state | PASS | Real provider validation + runtime validation | cTrader last GBP/USD quote was Friday 21:55 UTC; after the local correction and ticker subscription, Trading OS returned `CLOSED`, `tradable=false`, `WEEKEND`. |
| Account facts | Currency and balance | PASS | Real provider validation | USD balance `50000.00`. |
| Account facts | Positions and orders | PASS | Real provider validation | Empty collections reported; no positions or orders observed. |
| Account facts | Equity | BLOCKED | Real provider validation | Provider did not return equity; no derived value was used. |
| Account facts | Used/free margin | BLOCKED | Real provider validation | Provider did not return used or free margin. |
| Account facts | Deals | BLOCKED | Real provider validation | Deal history unavailable or absent. |
| Account facts | Snapshot completeness | FAIL | Real provider validation | Status `CONNECTED_BUT_FACTS_INCOMPLETE`; completeness `INCOMPLETE`, freshness `FRESH`. |
| Synchronization | Trading Core live synchronization | BLOCKED | Application path | Request was initiated through the authenticated gateway path but did not produce a bounded response during validation; no incomplete snapshot was accepted as synchronized. |
| Market Data | Weekend regression | PASS | Automated test | 92 targeted Market Data tests passed, including Forex Saturday closure when provider metadata incorrectly claims an open interval. |
| Risk | Equity admission | PASS | Automated test + real facts | Existing behavior remains fail-closed when equity is unavailable. |
| Execution | T0/T1 and paper lifecycle | PASS | Automated test | Targeted Trading Core execution and paper lifecycle tests passed. |
| Execution | Ambiguous/idempotent paths | PASS | Automated test | Existing execution guard and regression tests passed. |
| Execution | Isolated PAPER runtime scan for cTrader GBP/USD | PASS | Runtime PAPER validation | `artifacts/paper-runtime-proof-weekend-closure.json`; scan completed with `COMPLETED_NO_WORK` and `NO_TRADE`, with no broker mutation. |
| Execution | Isolated PAPER execution with runtime opportunity | BLOCKED | Runtime validation | Scanner produced no admissible opportunity; manual plan path also rejected stale market state with `MARKET_NOT_TRADABLE`. |
| Frontend | Account status and diagnostics | PASS | Real application state | Angular displayed FTMO account, connection state, and incomplete-facts diagnostics. |
| Frontend | Unit/component tests | PASS | Automated test | 56 files, 562 tests passed. |
| Frontend | Production build | PASS | Automated build | Build completed; existing Angular budget/type warnings remain. |
| Real order | `ProtoOANewOrderReq` / provider mutation | NOT_TESTED | Safety boundary | Explicitly excluded by Story 0198; execution remains disabled. |
| Readiness | First controlled execution | BLOCKED | Evidence synthesis | Mandatory provider-authoritative equity and margin facts remain unavailable; runtime opportunity data is also stale/incomplete. |

## Result Classes

- `PASS`: evidence is sufficient for the scoped check.
- `FAIL`: an observed result violates the required readiness condition.
- `BLOCKED`: the check could not safely complete or required unavailable facts.
- `NOT_TESTED`: explicitly excluded by the Story safety boundary.
