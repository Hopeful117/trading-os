# Implementation Report - Story 0232

## Status

Implemented locally - runtime validation completed with limitations; human review required

## Changes

- Added structured, sanitized MCP diagnostics with stable error categories,
  HTTP status, correlation ID, retryability and next action.
- Added correlation ID generation/propagation at Gateway and MCP client
  boundaries.
- Added bridge startup recovery from the owner-only persisted opaque
  credential, with fail-closed behavior when exchange is rejected.
- Added token-exchange locking to prevent concurrent refresh storms.
- Preserved one GET retry after `401` and no automatic POST replay.
- Exposed structured errors from MCP tools instead of generic tool failures.
- Added a Trading Core agent-business exception boundary for safe position-read
  diagnostics.
- Kept account facts completeness separate from grant authorization and did not
  change LIVE, Risk or execution eligibility.
- Reclassified the Angular action as explicit credential rotation/recovery;
  ordinary active-agent reconnect is described as automatic.

## Root Causes

### Repeated reconnection

The bridge pairing state was process-local and the Angular action always issued
a new credential. A restart therefore lost the in-memory pairing state even
when a local credential file existed.

### `list_markets`

The bounded EUR/USD query was routed through Gateway to Market Data, but the
Market Data service was not running and Eureka had no available instance. The
unfiltered catalog remains too large for the MCP response limit; the bounded
filter itself is valid once the service is available.

### `get_authorized_positions`

The provider snapshot can be temporarily deferred while another fresh snapshot
or cooldown is active. Trading Core previously converted this condition into a
generic `503`. The read-only positions path now uses the latest persisted
snapshot for the explicit `BROKER_SNAPSHOT_DEFERRED` condition, while retaining
the structured error boundary for other failures.

## Validation

Passed:

- `mcp-adapter/.venv/bin/python -m pytest mcp-adapter/tests/test_client.py mcp-adapter/tests/test_bridge.py mcp-adapter/tests/test_server.py` - 41 passed.
- `./mvnw -q -Dtest=AgentBusinessControllerTest test` in `trading-core`.
- `./mvnw -q -Dtest=BrokerAccountReadOnlyStatusServiceTest test` in `trading-core`.
- `./mvnw -q -Dtest=MarketControllerTest test` in `market-data`.
- `./mvnw -q -Dtest=GatewayApplicationTests,GatewayDownstreamRoutingIntegrationTest test` in `gateway`.
- `./mvnw -q test` in `trading-core` compiled the changes, but the full run exceeded the execution timeout while unrelated Spring context/database tests were active.
- `npm run check` in `trading-os-web` - 589 tests passed and Angular build completed; existing bundle/template budget warnings remain.
- `git diff --check`.

Runtime evidence:

- Authorized account listing and details succeeded for the existing FTMO Free
  cTrader account.
- Account remained `ExecutionMode.LIVE`, `CONNECTED`, provider identity
  `3d6c3f0a-8d2d-4f4d-9a4c-7a2e1b5c9012`.
- Active opportunities remained empty.
- A bridge restart without UI reconnection loaded the persisted credential but
  received `401 Unauthorized`; it remained disconnected and did not create a
  new identity.
- After starting the standard local bridge with the UI origin explicitly
  allowed, the existing agent was reconnected through the browser's
  `Connecter à OpenCode` action. The UI reported success and the bridge
  `/v1/status` endpoint returned `connected: true` after a page reload.
- After the credential was refreshed through the browser, the bridge was
  restarted without the origin override and recovered the persisted credential
  successfully; `/v1/status` again returned `connected: true`.
- No broker order, position mutation, synchronization, Trade Plan, approval
  or Risk operation was executed.
- Rebuilt and restarted the `trading-app` container after the positions fallback
  change; all four requested MCP reads then succeeded.

## Limitations

- Runtime validation used locally rebuilt containers. Deployment-level validation
  outside the local environment was not performed.
- The first bridge launch used the default development CORS origins and was
  rejected by the UI on port `17085`; the default origin list now includes the
  documented Trading Web ports. This is a local runtime configuration issue,
  not a credential change.
- Fresh MCP session verification completed on 2026-11-12: cTrader session status
  was `active` for plant `ftmo`, environment `live`, with no credential or JWT
  value exposed.
- Read-only runtime checks through the authorized Trading OS surface were
  bounded to the requested operations for broker account
  `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456` and all succeeded after rebuilding:
  - `get_authorized_account` returned `FTMO Free cTrader`,
    `executionMode=LIVE`, `connectionStatus=CONNECTED` and provider identity
    `3d6c3f0a-8d2d-4f4d-9a4c-7a2e1b5c9012`.
  - `get_authorized_positions` returned the bounded structured result `[]`.
  - `get_authorized_account_status` returned
    `CONNECTED_BUT_FACTS_INCOMPLETE`, with `executionAllowed=false` and
    `riskEvaluationAllowed=false`; the snapshot was fresh and positions/orders
    were empty, while used/free margin remained unavailable.
  - `list_markets(instrument=EUR/USD)` returned four distinct market records
    across cTrader CFD/spot, Kraken spot, and Kraken linear perpetual products.
- The runtime boundary remained unchanged: `executionAllowed=false`,
  `riskEvaluationAllowed=false`, human authority remains required for any
  execution, and no broker mutation was performed.
- The four responses contained no credential, JWT, cookie, stack trace or
  provider secret. The failed pre-rebuild calls were not treated as final
  evidence; after the service rebuild, the same bounded reads were repeated
  successfully.
- No credential value, JWT, broker secret or raw provider payload was recorded.

## Documentation Outcome

ADR-099, Story 0232 and this implementation report were added. ADR-099 remains
`Proposed` pending architecture review. No existing ADR was silently
superseded.

## Vault Outcome

The Obsidian vault was not consulted because the repository ADRs, validation
reports and current source were sufficient and authoritative for this local
implementation. No vault action or proposal is required.
