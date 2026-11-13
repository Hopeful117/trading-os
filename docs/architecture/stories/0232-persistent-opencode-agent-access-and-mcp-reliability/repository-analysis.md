# Repository Analysis - Story 0232

## Status

Historical analysis status: completed - waiting for human approval before
implementation planning. Later implementation evidence is recorded in the
implementation report; this analysis is not an approval record.

## Context Sources

- Current repository and worktree state.
- ADR-090 and ADR-098.
- Existing validation report `docs/validation/mcp-list-markets-failure-investigation-2026-11-12.md`.
- Existing LIVE validation report `docs/validation/mcp-live-workflow-validation-2026-11-12.md`.
- Historical agent authentication investigation and Story 0222 runtime report.
- DevLog Story Context Agent: checked with stable request; result completed but
  truncated and low-confidence, so repository evidence remains authoritative.

## Worktree Finding

The worktree already contains uncommitted changes across the requested
boundaries, including ADR-090, Gateway routing, MCP server, Trading Core agent
and broker-account code, Angular routes, execution authorization and new E2E
artifacts. These changes predate this analysis and must not be reverted or
silently folded into this Story without review.

## Existing Architecture

- Trading Core persists agent identities, grants and credential hashes.
- `AgentTokenExchangeService` validates the opaque credential and issues a
  short-lived audience-bound agent JWT.
- Gateway and Trading Core validate agent JWTs and Trading Core rechecks the
  persistent agent and credential state on requests.
- MCP Adapter keeps the access JWT in memory and retries one unauthorized GET;
  POST requests are deliberately not replayed.
- The bridge persists the opaque credential only after a successful token
  exchange, but its `connected` state and pairing challenges are process-local.
- Angular `connectToOpenCode` always issues a new credential and performs a
  challenge/connect sequence.

## Confirmed Root Causes

### Repeated human reconnection

The bridge constructs a fresh `BridgeState` on every process start. Pairing
status is therefore false after restart even though the opaque credential can
be loaded from the owner-only local store. The Angular Connect action always
starts a new credential issuance flow, so ordinary restart recovery is coupled
to human interaction and can issue a new credential unnecessarily.

### `list_markets`

The existing investigation confirms the MCP HTTP client rejects an unfiltered
catalog of approximately 1.05 MB against its 262,144-byte response guard. The
current `instrument` filter produces a bounded EUR/USD response. A stale bridge
that does not load the current filter-aware source can still request the
unbounded catalog and reproduce the generic failure. Gateway routing and
market serialization were healthy in the recorded evidence.

### `get_authorized_positions`

The current controller authorizes the exact broker-account grant, then calls
the Broker Service snapshot client and maps provider facts to dashboard
positions. Any runtime exception from the broker snapshot or mapping layer is
collapsed into `AgentBusinessDataUnavailableException` (`503`). The MCP client
then exposes only a generic sanitized tool failure, discarding the HTTP status,
safe body code and correlation data. The precise downstream exception for the
recorded runtime attempt is not established by the available MCP output and
must be captured after structured diagnostics are implemented or by correlated
Gateway/Broker logs.

## Risks and Constraints

- Do not persist raw credentials or access JWTs in the repository or logs.
- Do not retry unsafe POST operations.
- Do not treat incomplete account facts as permission denial or make them
  complete artificially.
- Do not alter FTMO Free cTrader from LIVE or enable execution.
- Existing uncommitted implementation changes may contain partial fixes and
  require diff reconciliation before editing.
- Runtime validation requires a fresh bridge process/source and standard
  Gateway `17080`; the synthetic `27080` runtime is out of scope.

## Proposed Validation Surface

- MCP Adapter client and bridge unit tests for startup load, exchange, expiry,
  concurrency, structured errors and sanitized diagnostics.
- Trading Core agent/business controller tests for revocation, grant scope,
  readiness separation and safe position-read failures.
- Gateway route/security tests for correlation propagation.
- Angular agent-page/service tests proving ordinary reconnect is not mandatory
  while enrollment and revocation remain available.
- Read-only runtime checks for market filtering, account reads, account status
  and positions against the existing FTMO account.

## Gate 1 Decision Required

Human approval is required for this Repository Analysis before implementation
planning and code changes. The next stage will produce an Implementation Plan
covering the credential lifecycle, structured error contract, bounded reads,
readiness separation, tests and runtime validation.
