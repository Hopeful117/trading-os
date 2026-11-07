# Implementation Report - Story 0217

## Status

`IMPLEMENTED - READ-ONLY ACCOUNT BOUNDARY; SYNCHRONIZATION DEFERRED`

## Implemented

- Replaced the MCP adapter human-JWT dependency with opaque agent credential
  exchange and process-local token caching.
- Bounded token response handling and reauthentication; GET requests may retry
  once after `401`, while POST requests are never replayed automatically.
- Added explicit read-only MCP account, account-status and position operations.
- Added exact agent route allow-listing and default-deny behavior in Trading Core.
- Preserved agent, owner, grant, resource and credential lifecycle checks.
- Prevented broker failures from being represented as successful empty data.
- Propagated validated agent context only through server-issued service-JWT
  delegation claims.
- Rejected client-controlled delegation headers and agent-as-service attempts.
- Kept synchronization, execution, broker credential management and agent
  administration unavailable.

## Documentation Reconciliation

Updated `AGENTS.md`, `TOOLS.md` and `mcp-adapter/README.md` to document the
process-local MCP token lifecycle, retry boundary and signed delegation rule.
No API specification or migration was required.

## Validation

Executed successfully:

```text
mcp-adapter: .venv/bin/pytest
mcp-adapter: .venv/bin/python -m compileall -q app tests
trading-core: ./mvnw -q test
broker-service: ./mvnw -q test
gateway: ./mvnw -q test
git diff --check
```

The suites include the added MCP cache/retry tests, agent boundary tests,
Feign delegation tests and Broker Service delegated-claim tests. Framework,
Mockito, H2/Flyway and local development warnings did not fail the suites.

## Deferred Scope

- Account synchronization remains disabled because the required agent-originated
  human-approval semantics are not represented as an explicit authorization
  boundary.
- Operation-audit completeness for every enabled MCP operation remains a
  follow-up validation item.
- No cTrader synchronization, broker order, live runtime operation or real
  credential provisioning was performed.

## DevLog and Vault

- DevLog Story Agent was requested twice with the same idempotency key and timed
  out both times. No DevLog evidence was used as authority.
- Obsidian vault was not consulted; no vault action is proposed.

## Repository State

No commit, push, merge, deployment or branch cleanup was performed by this
implementation step.
