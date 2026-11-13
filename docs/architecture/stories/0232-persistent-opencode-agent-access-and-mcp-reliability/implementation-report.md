# Implementation Report - Story 0232

## Status

Implementation locally present - deterministic validation passed; operational
validation partial; human review required

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

ADR-099, Story 0232 and this implementation report were reconciled. ADR-099 is
now `Accepted` under the project owner's bounded approval recorded in the ADR.
No existing ADR was silently superseded, and no approval was inferred beyond
that explicit project-owner instruction.

## Requirement-to-Evidence Matrix

| Requirement | Implementation evidence | Validation status |
|---|---|---|
| Persistent agent identity | `AgentIdentity` is Trading Core persistence | Implemented; standard database contains active OpenCode identities |
| Persistent authorization grants | `AgentGrant` is Trading Core persistence | Implemented; grants remain server-owned and default-deny |
| Root credential storage | Hashed credential in Trading Core; opaque owner-only local file | Implemented; current local file is stale and does not match the server |
| Normal restart recovery | Bridge startup loads and exchanges persisted credential | Unit-tested; operationally passed after fresh Connect, not proven with the current stale file |
| JWT renewal | In-memory expiry handling and one GET retry after `401` | Unit-tested |
| Revocation | Agent and credential state checked at exchange and request validation | Unit-tested and fail-closed by design |
| Credential rotation | Human UI issuance revokes prior active credential | Implemented; historical credential versions remain auditable |
| Lost-credential recovery | Human Connect issues a replacement credential | Operationally demonstrated; automatic recovery is prohibited |
| Environment switching | Gateway URL is configured, but no explicit non-secret binding record exists | Not implemented; proposed in ADR-099 and requires approval |
| Observability | Sanitized code, status, correlation and retry diagnostics | Implemented and tested; root exchange remains intentionally non-enumerating |
| Human authorization boundaries | Enrollment, rotation and grants remain human-controlled; execution remains separately authorized | Implemented and preserved |
| Authority binding | Owner-only atomic binding record stores normalized Gateway authority and optional configured issuer/audience metadata | Implemented and tested; stable server authority identifier is not returned by the current token exchange contract |
| Explicit connection semantics | Reconnect uses bridge recovery; Rotate and Recover issue replacement credentials through the existing human flow | Implemented and tested; no new REST endpoint was added |

## Documentary Reconciliation

The Story status now distinguishes local implementation from incomplete runtime
validation. Implementation-level criteria supported by source and tests are
marked complete. The OpenCode restart criterion remains open because the
current local root credential is invalid and no uninterrupted standard-runtime
restart proof exists. The Repository Analysis and Implementation Plan remain
historical workflow artifacts that state their original approval gate; their
approval was not inferred from this report.

The current failure is not a JWT renewal defect. The local credential is absent
from the checked server-side credential set, while the standard database and
active identities remain present. Human recovery is therefore required and is
consistent with the existing security boundary. Environment binding and a
distinct reconnect/status workflow are proposed in ADR-099 but are not
implemented or authorized.

The approved implementation adds no privileged self-provisioning, automatic
root rotation, grant broadening or financial authority. A separate logical
agent identity remains the operational recommendation for each independent
OpenCode/bridge installation and environment.

## Proposed Implementation Sequence After ADR Approval

1. Add non-secret local authority-binding metadata and fail-closed mismatch
   detection.
2. Separate status/reconnect from credential rotation in the UI and service
   contract.
3. Add deterministic mismatch, legacy-store and multi-instance tests.
4. Re-run standard and isolated E2E authentication tests with separate
   authorities.
5. Perform controlled read-only restart validation with a human-provisioned
   credential.

## Current Implementation Validation

- MCP Adapter focused suite: 48 tests passed.
- Angular `npm run check`: 62 test files and 595 tests passed; production build
  completed with pre-existing Angular diagnostic and bundle/style budget
  warnings.
- Standard-runtime validation remains pending because the persisted standard
  root credential is still invalid and no credential rotation was performed.
- Human recovery is required before validating standard OpenCode, bridge and
  Gateway restart continuity. No broker mutation, synchronization, TradePlan,
  Risk evaluation, approval or execution was performed.

## Vault Outcome

The Obsidian vault was not consulted because the repository ADRs, validation
reports and current source were sufficient and authoritative for this local
implementation. No vault action or proposal is required.
