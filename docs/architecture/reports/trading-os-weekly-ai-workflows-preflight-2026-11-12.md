# Trading OS Weekly AI Workflows Preflight

**Date:** 2026-11-12
**Scope:** AI Research and AI-Assisted Trading operational readiness
**Mode:** Read-only preflight; no workflow execution, deployment, migration, restart, or credential access

## Executive Verdict

The environment is suitable for continued controlled inspection, but it is **not approved for live E2E AI Research or AI-Assisted Trading execution**.

- **AI Research:** `BLOCKED` for operational E2E validation. The MCP bridge is reachable but returns HTTP 403 without an approved browser-origin pairing, agent credential, and explicit research grant. No research workflow was started.
- **AI-Assisted Trading:** `BLOCKED` for execution validation. The deterministic plan/risk/approval contracts exist, but no current authorized account, approved trade plan, risk evaluation, human approval challenge, or broker-safe paper/demo target was verified.
- **Runtime:** `PARTIAL`. Core containers are running and selected unauthenticated health probes pass, but several protected services cannot be assessed without approved authentication.
- **Repository:** `READY FOR HUMAN REVIEW`. The worktree is clean on `main`; HEAD matches the local remote-tracking reference.

Historical execution-readiness evidence also identifies unresolved risks around Trading Account/Broker Account identity mapping, explicit human approval after risk evaluation, and Broker Service ownership enforcement. These risks must be closed or explicitly accepted before any live-boundary validation.

## Safety Boundary

This preflight did not:

- start research, scan, export, risk evaluation, approval, synchronization, or execution workflows;
- call mutating cTrader or Trading OS operations;
- access credentials, tokens, broker secrets, or private keys;
- build, deploy, migrate, restart, or modify running services;
- perform Git integration actions.

The only repository change from this task is this report.

## Repository Baseline

- Branch: `main`
- HEAD: `22c14c92a3b9e96b271ef0abee186dbfc3de90bf`
- Worktree: clean before report creation
- Local remote-tracking state: `HEAD == refs/remotes/origin/main`
- Latest remote `main` verification: commit `22c14c92` via GitHub read-only inspection
- PR #287 / Story 0230: merged; Story branches were already removed locally and remotely

## Runtime Evidence

Docker services inspected as running:

- Gateway
- Trading Core
- Market Intelligence
- Market Data
- Broker Service
- AI Engine
- News Service
- Eureka
- Trading OS Web
- PostgreSQL

No Docker healthcheck is configured for these containers, so `running` is not equivalent to application readiness.

Observed non-mutating HTTP probes:

| Component | Result | Interpretation |
|---|---:|---|
| Broker Service | HTTP 200, `{"status":"UP"}` | Basic application health available |
| AI Engine | HTTP 200; `enabled=true`, `shadowMode=true`, `provider=openai_chatgpt` | AI engine is enabled in shadow mode, not an execution authority |
| Eureka | HTTP 200 | Discovery endpoint reachable |
| Trading OS Web | HTTP 200 | Frontend reachable |
| Gateway | HTTP 401 | Protected endpoint reachable; authentication required |
| Trading Core | HTTP 403 | Protected endpoint reachable; authorization required |
| Market Data | HTTP 403 | Protected endpoint reachable; authorization required |
| News Service | HTTP 401 | Protected endpoint reachable; authentication required |
| MCP bridge `127.0.0.1:17891` | HTTP 403 | Bridge reachable but request origin/pairing is not established |

Capacity observed:

- Root filesystem: approximately 69 GB free, 84% used
- Docker images: approximately 22.21 GB
- Docker volumes: approximately 7.68 GB
- Build cache: approximately 33.09 GB, including approximately 16.44 GB reclaimable

Capacity is not an immediate blocker for bounded tests, but historical export or build activity should be monitored before execution.

## MCP and Permission Readiness

The MCP adapter declares the following relevant tool families:

- market and thesis reads;
- opportunity reads and Trade Plan creation;
- authorized account, status, and position reads;
- deterministic Trade Plan risk evaluation;
- human approval challenge creation and status reads;
- account synchronization request/status/execution;
- bounded market research and status/report retrieval;
- historical research readiness inspection and bounded dataset export/status.

The Trading Core permission model includes:

- `research:run`
- `research:inspect`
- `research:export`
- `scans:run`
- `plans:create`
- `trade-plan-risk:evaluate`
- `trade-plan-approval:request`
- `trade-plan-approval:status:read`

The permission-to-resource compatibility map scopes research run/export to a research scope, research inspection to a broker account, and trading-plan operations to a broker account.

Current readiness remains `UNVERIFIED` because:

- no authenticated administrative session was available to inspect current grants;
- no agent credential was retrieved or exposed;
- no browser-origin pairing was attempted;
- the bridge returned 403 for the unauthenticated probe;
- no authorized account or research scope UUID was available for a read-only workflow check.

## AI Research Readiness

### Required chain

```text
Agent identity
  -> explicit research grant
  -> bounded scope and markets
  -> idempotent research request
  -> status polling
  -> structured advisory report
```

### Verdict: BLOCKED

The code contracts support bounded research and explicit idempotency. However, the operational prerequisites were not verified. Starting a research request would require an actual scope ID, two canonical market IDs, a stable idempotency key, a valid agent credential, and an active grant. None was available for safe execution during this preflight.

The historical research implementation reports were inspected, but historical implementation evidence is not treated as current runtime proof.

## AI-Assisted Trading Readiness

### Required chain

```text
Opportunity
  -> Trade Plan
  -> deterministic risk evaluation
  -> human approval challenge
  -> approved operation
  -> Trading Core execution state
  -> Broker Service boundary
```

### Verdict: BLOCKED

The repository contains explicit contracts for account-scoped plan creation, risk evaluation, human approval challenges, and approved operation execution. No current end-to-end execution was attempted because the preflight did not verify:

- an explicitly authorized broker account;
- a current paper account or approved demo/sandbox account;
- a valid opportunity and immutable Trade Plan version;
- a successful deterministic risk evaluation;
- a human approval challenge approved by the human trader;
- broker/provider identity and ownership alignment.

Trading OS `PAPER` must remain entirely inside Trading OS. A broker demo or challenge account remains `LIVE` at the Trading OS boundary because an external provider is involved.

## Blocker Matrix

| ID | Severity | Blocker | Evidence | Required resolution |
|---|---|---|---|---|
| B-01 | P0 | No verified current agent credential/grants | Administrative grant state unavailable; MCP bridge probe returned 403 | Human/admin pairing and grant inspection through the approved path |
| B-02 | P0 | No verified authorized account or research scope | No account/scope identifiers available for safe read-only workflow calls | Select and verify an explicit paper/demo or research scope |
| B-03 | P0 | No human-approved trade plan | No risk evaluation or approval challenge exists for this run | Human must review and approve a specific plan after deterministic risk evaluation |
| B-04 | P0 | Live-boundary safety not currently proven | Historical execution-readiness report records unresolved identity and ownership risks | Reconcile Trading Account/Broker Account mapping and Broker Service ownership enforcement |
| B-05 | P1 | Protected-service readiness incomplete | Gateway, Trading Core, Market Data, and News require auth and were not accessed with approved credentials | Run authenticated, non-mutating health/read checks |
| B-06 | P1 | No current E2E research result | No research request was initiated by design | Run one bounded research workflow only after B-01/B-02 are resolved |
| B-07 | P2 | Disk pressure could affect exports/builds | 84% root usage; 16.44 GB reclaimable build cache | Monitor capacity and clean only through an approved maintenance task |

## Safe Next Steps

1. Human verifies the MCP bridge pairing flow and establishes a valid agent credential without exposing it to the report or chat.
2. Human/admin confirms active grants for the exact research scope and broker account.
3. Validate account mode and ownership mapping using read-only endpoints only.
4. Run a single bounded AI Research workflow with explicit markets, scope, depth, and idempotency key.
5. Inspect the research status and advisory report; do not infer readiness from a submitted-only response.
6. For trading, use a specifically approved PAPER target or a broker demo/challenge target whose external boundary is explicitly acknowledged as `LIVE`.
7. Create and inspect a Trade Plan, evaluate deterministic risk, obtain human approval, and stop before any broker mutation unless the human explicitly authorizes the next operation immediately before execution.

## Evidence References

- `.opencode/opencode.json`
- `mcp-adapter/app/server.py`
- `mcp-adapter/app/bridge.py`
- `mcp-adapter/README.md`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/model/AgentPermission.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/application/AgentGrantCompatibility.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/agent/api/AgentBusinessController.java`
- `docs/architecture/adr/ADR-088-mcp-first-agent-interaction-boundary.md`
- `docs/architecture/adr/ADR-089-native-trading-research-agent-mcp-responsibility-boundary.md`
- `docs/architecture/adr/ADR-090-agent-identity-permissions-delegated-authorization.md`
- `docs/architecture/adr/ADR-092-proposed-mcp-research-operational-control-boundary.md`
- `docs/investigations/execution-readiness.md`
- `docs/architecture/stories/0219-authorize-agent-research-end-to-end/implementation-report.md`
- `docs/architecture/stories/0222-agent-native-historical-research/implementation-report.md`
- `docs/architecture/stories/0198-ctrader-ftmo-end-to-end-integration-validation/e2e-validation-matrix.md`
- `docs/architecture/stories/0174-ctrader-demo-execution-safety-contract/story.md`
