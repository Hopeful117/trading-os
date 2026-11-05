# Story 0201 - E2E Validation Matrix

| Area | Expected | Evidence | Result |
|---|---|---|---|
| Service startup | Broker Service and Trading Core start | Docker Compose logs | PASS |
| Schema | V53 applied | Trading Core Flyway log | PASS |
| Account visibility | Existing FTMO account visible in normal UI | `/accounts` UI | PASS |
| Read-only synchronization | Authenticated V2 sync completes without provider mutation | UI status `SYNCED_PARTIAL` | PASS |
| Snapshot freshness | Freshness preserved explicitly | `INCOMPLETE / FRESH` | PASS |
| Balance | Provider balance visible with currency | `50000 USD` | PASS |
| Positions | Empty is distinct from unavailable | `AVAILABLE_EMPTY` | PASS |
| Orders | Empty is distinct from unavailable | `AVAILABLE_EMPTY` | PASS |
| Deals | Unavailable is explicit | `UNAVAILABLE_OR_ABSENT` | PASS |
| Financial omissions | Equity/margin omissions remain explicit | UI unavailability reasons | PASS |
| Risk posture | Partial state remains diagnostic-only | `TRADING_NOT_AUTHORIZED` | PASS |
| Derived equity provenance | Candidate is not admitted as authoritative risk evidence | No authoritative candidate persisted | PASS |
| Challenge context | Existing FTMO Challenge active and ready | Challenge `09163438-7c6d-4f58-9b98-c1d2c68e38ae`, `READY` | PASS |
| Market scan | Live bounded scan without execution | EUR/USD and GBP/USD evaluable; US30/USD and XAU/USD excluded for insufficient history | PASS |
| Trade Plan to T0/T1 | Representative result without submission | Plan accepted as version `2`; valuation ambiguity corrected; retry currently deferred by `BROKER_SNAPSHOT_DEFERRED` cooldown; no execution intent | BLOCKED |
| Provider mutation safety | No broker order mutation | No execution rows; safety flag disabled | PASS |
| Angular regression | Full frontend suite | 56 files / 563 tests | PASS |
| Production artifact | Frontend production build | `npm run build` | PASS |

## Blocking Prerequisite

The Trade Plan has received the explicit human decision and reached the T0 risk
evaluation. Market valuation now selects the cTrader Spot instrument explicitly.
The next T0 retry is deferred by the existing broker snapshot cooldown; T1 and
execution remain fail-closed until an authoritative risk context is available.
