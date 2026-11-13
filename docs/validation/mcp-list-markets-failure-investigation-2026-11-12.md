# MCP `list_markets` Failure Investigation

**Date:** 2026-11-12
**Mode:** Focused, read-only runtime diagnosis
**Verdict:** `ROOT_CAUSE_CONFIRMED`

## Executive Finding

The failure is in the MCP HTTP client response-size guard, not in authentication, Gateway routing, market-data authorization, downstream availability, or JSON serialization.

`GET /api/v1/markets` returns a valid HTTP 200 catalog of approximately 1,054,483 bytes. The MCP client default maximum response size is 262,144 bytes. The client consumes the response until it exceeds that limit and raises:

```text
Gateway response exceeds the configured MCP response limit
```

The MCP framework exposes this as the generic sanitized tool failure:

```text
Error executing tool list_markets
```

The Gateway log confirms the authenticated request completed with HTTP 200. Therefore the previous `list_markets` failure was incorrectly interpreted as an authorization or catalog availability problem because the MCP tool surface did not expose the sanitized client exception detail.

## Reproduction Evidence

The existing paired MCP session was used once with sanitized input:

```json
{}
```

Observed result:

```text
Error executing tool list_markets
```

No credential or authorization header was exposed.

Runtime evidence around the request:

- Gateway log: `GET /api/v1/markets status=200 OK durationMs=35`
- Gateway log timestamp: `2026-11-12T14:52:25.124919907Z`
- Direct Market Data request: HTTP 200
- Full catalog response size: `1,054,483` bytes
- MCP client configured/default limit: `262,144` bytes
- Filtered direct response for `instrument=EUR/USD`: HTTP 200, `2,576` bytes

No downstream error code, 401, 403, 404, timeout, or invalid-JSON error was observed for the catalog request.

## Exact Request Path

```text
OpenCode
  -> MCP tool list_markets(instrument=None)
  -> TradingOsClient.get("/api/v1/markets", None)
  -> HTTP GET http://127.0.0.1:17080/api/v1/markets
     Authorization: Bearer <opaque agent token>
  -> Gateway route "markets"
  -> lb://market-data
  -> MarketController.findAll()
  -> MarketService.findAll()
  -> MarketMapper.toDto(...)
  -> HTTP 200 JSON catalog
  -> MCP client response-size check
  -> GatewayClientError after 262,144 bytes
```

### MCP tool

`mcp-adapter/app/server.py:20-26` calls:

```text
GET /api/v1/markets
```

The optional `instrument` query parameter is passed only when supplied. No invalid parameter was sent in the reproduction.

### MCP HTTP client

`mcp-adapter/app/client.py:114-148`:

- sends the bearer token;
- receives the HTTP response;
- reads response chunks into a byte buffer;
- raises `GatewayClientError("Gateway response exceeds the configured MCP response limit")` when the buffer exceeds `max_response_bytes`;
- validates JSON only after the size check.

`mcp-adapter/app/client.py:230-239` defaults `TRADING_OS_MAX_RESPONSE_BYTES` to `262144`.

### Gateway

`gateway/src/main/java/com/hope/trading/gateway/config/GatewayRouteConfig.java:142-145` routes `/api/v1/markets/**` to `lb://market-data`.

`gateway/src/main/java/com/hope/trading/gateway/security/SpringSecurityConfig.java:26-39` requires authentication for non-public routes. The agent authentication filter accepts the paired agent token before the regular user JWT filter, and the Gateway log confirms the request completed with HTTP 200.

### Market Data

`market-data/src/main/java/com/hope/trading/market_data/controller/MarketController.java:32-49` serves the catalog through `MarketService.findAll()` and maps each entry to `MarketResponse`.

`market-data/src/main/java/com/hope/trading/market_data/security/MarketDataSecurityConfiguration.java:39-44` explicitly permits `GET /api/v1/markets/**` and restricts only synchronization and subscription mutations.

The direct unauthenticated Market Data request returned HTTP 200, which is consistent with that service security contract. It was used only as a non-mutating diagnostic comparison; it did not bypass the Gateway for the MCP request.

## Gateway and Downstream Observations

| Boundary | Observation | Result |
|---|---|---|
| MCP tool implementation | Correct path and optional filter handling | Not the failing mechanism |
| MCP HTTP client | Rejects body after 262,144 bytes | Confirmed failing component |
| Gateway route | `GET /api/v1/markets` routed to Market Data | Healthy; HTTP 200 in Gateway log |
| Gateway authentication | Agent token accepted for the request | Not an authentication failure |
| Market Data authorization | GET catalog is `permitAll()` | Not a missing `markets:read` grant at this boundary |
| Market catalog service | Returns full valid JSON catalog | Healthy for this request |
| Response serialization | Direct response is valid JSON and filtered response is small | Not a serialization incompatibility |
| Network/connectivity | No timeout; Gateway completed in 35 ms | Not a connectivity failure |

No correlation ID was emitted in the relevant Gateway access log, so no request correlation identifier is available for this failure. The timestamp and exact path are the available sanitized correlation evidence.

## Comparison With Working MCP Tools

The working account tools and `list_markets` share the same MCP client and bearer-token exchange:

- both call `client_from_environment()`;
- both use `TradingOsClient.get()`;
- both send the agent access token through `Authorization`;
- both use the same 262,144-byte response guard and sanitized error translation.

The relevant difference is response size and target service:

- account reads return a small account-scoped JSON payload from Trading Core;
- positions return an empty/small list in the observed session;
- the unfiltered market catalog contains approximately 1.05 MB of JSON.

The market-related MCP tool is therefore specifically affected by catalog cardinality, not by the agent's account permission. The existing `instrument` parameter provides a bounded path: the observed EUR/USD response was 2,576 bytes.

## Runtime Version Assessment

The local MCP bridge process is an existing `.venv/bin/python -m app.bridge` process started on 2026-11-10. Its active source contract matches the inspected `client.py`, including the 262,144-byte default.

The Gateway and Market Data Docker images were created on 2026-11-10, before the current repository HEAD merge on 2026-11-12. This is a deployment freshness risk, but it is not the root cause of this failure:

- the running Gateway logged the expected `/api/v1/markets` route request as HTTP 200;
- the running Market Data service returned the expected catalog response;
- the response-limit guard is present in the local MCP client contract and is independently covered by tests.

No restart, rebuild, or redeployment was performed.

## Root-Cause Analysis

### Verdict: `ROOT_CAUSE_CONFIRMED`

- **Failing component:** MCP Adapter HTTP client
- **Method:** `TradingOsClient._request`
- **Source:** `mcp-adapter/app/client.py:131-135`
- **Exact mechanism:** the unfiltered catalog exceeds `max_response_bytes=262144`; the client raises before JSON validation and before returning the tool result
- **Expected behavior:** a market discovery tool should return a bounded response or expose a bounded/paginated catalog contract compatible with the MCP response limit
- **Actual behavior:** `list_markets` requests the entire catalog without a limit, while the client rejects responses above 256 KiB

This is not a `401`, `403`, `404`, `5xx`, timeout, downstream failure, or response serialization failure.

## Minimal Proposed Fix

### Immediate operational workaround

Use the existing `instrument` parameter for known candidates, for example one bounded request for `EUR/USD` and one for `GBP/USD`. The direct filtered response is below the client limit. This requires no code, grant, credential, or runtime change.

This is sufficient for the intended AI Research E2E only after the research scope explicitly authorizes the selected market IDs.

### Smallest durable application fix

Make market discovery bounded by contract rather than increasing the global response cap. Accept one of the following minimal designs after human approval:

1. Add a bounded `limit`/pagination contract to the market catalog endpoint and MCP tool.
2. Add a dedicated bounded MCP market-discovery tool that requires an instrument filter or returns a capped result set.

Simply increasing `TRADING_OS_MAX_RESPONSE_BYTES` would remove the immediate error but preserves unbounded catalog growth and increases the memory/transport limit for every MCP response. It is not the preferred corrective action.

No fix was implemented during this diagnosis.

## Regression Coverage

Existing coverage:

- `mcp-adapter/tests/test_client.py:163-184` verifies oversized response rejection and the sanitized error message.
- `market-data/src/test/java/com/hope/trading/market_data/controller/MarketControllerTest.java:73-94` verifies catalog listing and instrument filtering.
- `market-data/src/test/java/com/hope/trading/market_data/security/MarketDataSecurityIntegrationTest.java:250-254` verifies public catalog availability.

Required regression coverage for the corrective change:

- a realistic catalog payload above 256 KiB is handled by the chosen bounded contract;
- filtered EUR/USD and GBP/USD responses remain below the MCP client limit;
- pagination or limit parameters are forwarded exactly through MCP and Gateway;
- no unbounded catalog request remains as the default MCP discovery path;
- response-size protection remains active for unrelated MCP endpoints.

## Impact on AI Research E2E

The failure is understood and has a safe immediate workaround: use bounded instrument-filtered market reads. The AI Research E2E cannot resume automatically because scope authorization and exact market grants still need to be verified, but the previous `list_markets` response-limit failure is no longer an unexplained blocker.

After the human confirms the research scope permits two selected canonical markets, the E2E may resume using bounded filtered market reads without changing code or runtime configuration. A durable unfiltered market-discovery fix should still be handled as a separate approved implementation task.

## Final Status

- Application code modified: no
- Runtime restarted or redeployed: no
- Credentials or grants modified: no
- Mutating Trading OS/cTrader operation: no
- Research execution started: no
- Root cause: confirmed MCP response-size limit
- E2E resume: possible with bounded filtered reads after research authorization is verified

## Continuation: 2026-11-12 MCP Filter Propagation Diagnosis

### Verdict

`ROOT_CAUSE_CONFIRMED`

The active MCP bridge process is stale. It was started before the repository version that added the `instrument` parameter to `list_markets`.

### Process and source evidence

- Active bridge PID: `3646399`
- Process: `.venv/bin/python -m app.bridge`
- Process start: `2026-11-10 01:56:18`
- Process working directory: `/home/ludo/workspace/trading-os/mcp-adapter`
- Python executable: `/home/ludo/workspace/trading-os/mcp-adapter/.venv/bin/python`
- Current imports resolve to the repository's `app/server.py` and `app/client.py`.
- The active process started before commit `b8b90c1f` (`2026-11-10 23:07:05`), which added the `instrument` parameter and query forwarding to `list_markets`.

The current source contains the filtered handler, while the source committed before `b8b90c1f` contained the previous unfiltered handler:

```text
Current:  list_markets(instrument) -> GET /api/v1/markets?instrument=...
Previous: list_markets()          -> GET /api/v1/markets
```

The running bridge loaded the previous implementation at process startup and cannot propagate the caller's `instrument` argument, even though the current repository contract does.

### Exact active request and response

The active request is:

```text
GET http://127.0.0.1:17080/api/v1/markets
```

Query parameters: none.

The Gateway recorded HTTP 200. The unfiltered catalog is approximately 1,054,483 bytes. The MCP client then applies its 262,144-byte limit and raises `Gateway response exceeds the configured MCP response limit`, surfaced by MCP as `Error executing tool list_markets`.

The direct filtered Market Data request remains healthy: `GET /api/v1/markets?instrument=EUR/USD` returns HTTP 200 and approximately 2,576 bytes.

### Runtime conclusion

- MCP bridge environment: correct repository virtual environment and working directory.
- Loaded bridge code: stale relative to the current source because the process predates the filter commit.
- Gateway routing: healthy.
- Market Data: healthy.
- HTTP client response decoding: not reached for the active unfiltered request because the size guard fires first.
- Persisted credential: a separate diagnostic process reported `Agent credential exchange failed`; no credential contents were inspected. The active bridge still performs working account reads using its in-memory credential, so this is a separate stale-credential observation.

### Smallest safe corrective action

Restart the local MCP bridge through the normal OpenCode MCP lifecycle so it loads the current repository source. This is an operational action requiring human approval under the current constraints; it was not performed during diagnosis.

After restart, verify that the tool schema exposes the optional `instrument` parameter and repeat only the EUR/USD and GBP/USD filtered reads. No application, grant, credential, Gateway, or Market Data change is required for this failure.

### Regression recommendation

Add adapter coverage that invokes `list_markets` with `instrument="EUR/USD"` and asserts the HTTP client receives `params={"instrument": "EUR/USD"}`. Retain the oversized-response test and add an operational process/version check so a bridge started before the adapter contract cannot be mistaken for the current implementation.

## Continuation: 2026-11-12 Bridge Restart Attempt

The stale bridge process was stopped after explicit human approval. Its MCP wrapper was also stopped, but the existing OpenCode parent did not automatically recreate the local MCP child.

- Old bridge PID `3646399`: stopped
- MCP wrapper PID `3646396`: stopped
- OpenCode parent: left running to avoid interrupting the active session
- Bridge port `127.0.0.1:17891`: currently unavailable
- Services, application code, grants, credentials, and research state: unchanged

The normal OpenCode MCP lifecycle did not respawn the bridge from this session. The minimum remaining action is to reload or restart OpenCode so it recreates the configured `trading-os` local MCP process. No filtered market call or research operation was attempted after the bridge stop.
