# Story 0222 Runtime Synchronization And Agent Research Validation

**Date:** 2026-11-08  
**Baseline:** `main` at `c7954b40a07e5e74da2b2176c2ac99f792061537`  
**Verdict:** `VALIDATION_INCOMPLETE`

## 1. Executive Summary

This report covers pre-deployment verification and the approved minimal runtime
synchronization. No migrations were applied manually, grants were changed, or
research/export operations were executed.

The runtime is stale or revision-unproven for Story 0222:

- Trading Core is running an image created before the merge and its logs show
  only 59 migrations, while the merged source adds `V60`.
- Market Intelligence is running an image created before the merge and its logs
  show only 22 migrations, while the merged source adds `V24` and `V25`.
- The MCP bridge started before the merge. Its loaded server cannot include the
  merged `inspect_historical_research_readiness` tool until the MCP process is
  restarted.
- Current agent authentication is operational when the existing bridge is
  paired: token exchange and the markets read both succeeded previously.
- No authorized scope, market and `research:inspect` grant was available to
  this preflight, so readiness inspection was not attempted.

The approved deployment rebuilt and recreated only Trading Core and Market
Intelligence successfully. The required MCP bridge stop was then performed,
but OpenCode did not respawn the local MCP child and no supported CLI reconnect
control was available. Readiness validation is incomplete pending human MCP
reconnection and pairing.

## 2. Verified Git Baseline

| Item | Result | Classification |
|---|---|---|
| Branch | `main` | OBSERVED |
| HEAD | `c7954b40a07e5e74da2b2176c2ac99f792061537` | OBSERVED |
| HEAD | Story 0222 merge commit / PR #277 | OBSERVED |
| Tracking | `main` equals `origin/main` | OBSERVED |
| Tracked changes | None | OBSERVED |
| Untracked files | Four pre-existing reports plus this report | OBSERVED |
| Required baseline | Matches requested merge | OBSERVED |

The four untracked reports are:

- `agent-authentication-401-root-cause-investigation-2026-11-08.md`
- `agent-native-historical-research-architecture-investigation-2026-11-08.md`
- `historical-data-next-milestone-investigation-2026-11-08.md`
- `story-0222-post-merge-operational-validation-2026-11-08.md`

## 3. Pre-Deployment Runtime Inventory

Docker application containers are running, but none defines a Docker healthcheck.
Unauthenticated actuator probes reach the services but return `401` or `403`;
this is not health proof.

| Component | Running | Image/source revision | Health | Update required |
|---|---|---|---|---|
| Gateway | Yes | Image `sha256:6f65169e...`; source revision UNKNOWN; created 09:32:30+01:00 | Reachable; actuator `401`; no Docker healthcheck | No Story 0222 production diff in the merge; revision synchronization otherwise UNVERIFIED |
| Trading Core | Yes | Image `sha256:856fe1e7617c83b93ee102a94c956b775b310af700db8e236f51ef0d4898b0b4`; built after approval | Started successfully; no Docker healthcheck | Synchronized |
| Market Intelligence | Yes | Image `sha256:4247dfe2d58d1f05a03b112326f22cb2b2b74ebc176b06c64e1d3e3079181960`; built after approval | Started successfully; no Docker healthcheck | Synchronized |
| MCP Adapter | Bridge PID `744733` was stopped after deployment; listener now down | Previously connected; no current bridge status | Human reconnect required |
| Market Data | Yes | Image `sha256:76ef36b3...`; created 2026-11-07 21:49:21+01:00 | Reachable; actuator `403`; no Docker healthcheck | No |
| Eureka | Yes | Image created 2026-11-07 21:49:20+01:00 | Running; no Docker healthcheck | No |
| PostgreSQL services | Yes | Persistent Postgres containers running | No Docker healthchecks | No recreation proposed |

The abbreviated image IDs above are intentionally non-sensitive and sufficient
to identify the observed containers without exposing configuration values.

## 4. Story 0222 Affected Services

The merge contains substantial production changes in Trading Core and Market
Intelligence, including historical authorization, readiness, export lifecycle,
and migrations. The Gateway change in the merge is test-only; its relevant
agent and Market Intelligence routes already exist in the current source and
no Gateway rebuild is required for the Story 0222 runtime behavior.

The MCP adapter adds the readiness and controlled-export tool definitions in
`mcp-adapter/app/server.py`. The local bridge process predates the merge, so
its in-memory Python module state is stale even though the working-tree source
is current.

## 5. Disk Capacity Preflight

| Measurement | Result | Classification |
|---|---:|---|
| Root filesystem | 449G total, 420G used, 6.6G available, 99% | OBSERVED |
| Root inodes | 29,933,568 total, 3,129,552 used, 26,804,016 free, 11% used | OBSERVED |
| Docker images | 16.37G total; 5.372G reclaimable | OBSERVED |
| Docker build cache | 18.22G total; 7.241G reclaimable | OBSERVED |
| Docker volumes | 97.37G total; 442.2M reclaimable | OBSERVED |
| Market Data volume | 85.1G | OBSERVED |
| Trading Core volume | 3.2G | OBSERVED |
| Market Intelligence volume | 210.2M | OBSERVED |
| Research working tree | 671M in prior report | OBSERVED, not remeasured here |

Current inode headroom is adequate. Byte headroom is not comfortable for a
full-stack build. The existing Trading Core and Market Intelligence images are
approximately 299M and 405M respectively; the Gateway image is approximately
273M. A targeted two-image build may fit in 6.6G, but exact temporary Maven
layer and cache growth is UNKNOWN. A full-stack rebuild is not acceptable under
the current capacity margin.

No Docker cleanup, volume deletion, cache pruning, order-book deletion or
filesystem cleanup was performed.

Minimal storage remediation proposal, if the targeted build preflight shows
insufficient headroom: obtain separate human approval for a non-destructive
Docker build-cache review/removal, or provision additional host capacity.
Do not delete volumes or historical order-book data.

## 6. Deployment Plan And Approval Status

### Exact services to rebuild

1. `trading-app` from `trading-core/Dockerfile`.
2. `market-intelligence` from `market-intelligence/Dockerfile`.

The target source is the verified merge commit. No Gateway rebuild is proposed
because Story 0222 introduced no Gateway production-code change in the merge;
its route behavior remains separately runtime-unverified.

### Exact services to recreate

1. `trading-app`, with its existing configuration and the existing
   `trading_os` PostgreSQL volume preserved.
2. `market-intelligence`, with its existing configuration and the existing
   `market_intelligence_data` PostgreSQL volume preserved.

No database container, Market Data container, Broker Service, Gateway, Eureka,
News Service, AI Engine or Web container recreation is proposed.

### Database migration implications

Normal application startup is Flyway-managed. The pre-deployment logs showed:

- Trading Core validated 59 migrations and applied none.
- Market Intelligence validated 22 migrations and applied none.

The approved startup applied, through normal service startup only:

- Trading Core `V60__historical_research_scope_constraints.sql`; one migration
  was applied and the schema reached `v60`.
- Market Intelligence advanced to `v25`; three pending migrations were applied.

This was expected migration scope and was not performed manually.

### Expected downtime

The two application containers would be unavailable during their individual
recreation and startup windows. Exact duration is UNKNOWN; expected impact is
limited to Trading Core and Market Intelligence, with no planned database or
MCP downtime during image build. Readiness validation should wait for startup
and migration completion.

### Executed commands

```bash
docker compose build trading-app market-intelligence
docker compose up -d --no-deps --force-recreate trading-app market-intelligence
```

The commands preserved named volumes and avoided recreating unrelated services.

### MCP/OpenCode impact

The bridge process must be restarted or reconnected after approval so that the
new `inspect_historical_research_readiness` definition is loaded. The bridge
holds its credential only in memory; restarting it will lose the pairing.
OpenCode itself should not be restarted. The documented operator procedure is:

1. Use the existing OpenCode MCP reconnect/restart control for the configured
   local `trading-os` server.
2. Open the Trading OS Web Agents page in the allowed browser origin.
3. Pair the displayed bridge challenge using the existing Agent Management
   flow, without copying credentials into files or environment configuration.
4. Confirm the bridge status and tool list before attempting readiness.

If that reconnect control is unavailable, stop and request human assistance;
do not kill OpenCode or invent a credential.

### Rollback plan

If an approved startup fails, stop further actions, preserve logs and report the
failure. Rollback would require explicit approval to recreate the affected
service containers from the previously recorded image IDs. Do not remove the
new images or touch persistent database volumes. If a migration has already
been applied, database rollback is not assumed safe and requires a separate
operator decision.

### Approval and execution status

```text
APPLICATION DEPLOYMENT APPROVED AND COMPLETED
MCP BRIDGE STOPPED; RECONNECT BLOCKED PENDING HUMAN ACTION
```


## 7. OpenCode And MCP Pairing Preflight

| Check | Result | Classification |
|---|---|---|
| OpenCode process | PID `744072` running since 2026-11-07 23:26:30 | OBSERVED |
| Bridge process | PID `744733` stopped after deployment | OBSERVED |
| Bridge listener | `127.0.0.1:17891` is down | OBSERVED |
| Bridge status before reload | `connected: true` with allowed Origin header | OBSERVED |
| Current pairing preservation | Lost when bridge process stopped | OBSERVED |
| Readiness tool in loaded process | Not verified; pre-merge process was stopped | OBSERVED |
| Human re-pair required after bridge restart | Required by in-memory credential design | OBSERVED/INFERRED |

The prior authenticated sequence remains valid evidence for the current paired
bridge: token exchange `200`, markets `200`, and opportunities `403`. No new
token or credential was retrieved by this preflight.

## 8. Research Readiness Invocation

Not executed. The bridge is currently unavailable after the approved reload,
and this validation did not invent a ResearchScope, market ID,
interval, cutoff or grant. No readiness result, authorization result, coverage
diagnostic or correlation ID can be claimed.

Required operator prerequisites after approved synchronization:

- active agent identity and credential through the existing pairing flow;
- exact active `research:inspect` grant;
- owner-scoped active ResearchScope;
- canonical market belonging to that scope;
- allowed interval and cutoff range;
- bounded dataset profile and observation limit.

If any prerequisite is missing, classify the branch as authorization-blocked
and request the exact legitimate operator action. Do not create or broaden a
grant automatically.

## 9. Remaining Blockers

- The application deployment is complete.
- The bridge must be respawned/reconnected to expose the merged readiness tool.
- A human must complete legitimate bridge re-pairing after the bridge reload.
- An authorized research scope, market and `research:inspect` grant are not
  available to this preflight.
- Root filesystem free space is only 6.6G; targeted build capacity is plausible
  but not guaranteed.

## 10. Fresh Runtime Recheck

This section records a fresh read-only recheck at `2026-11-08T19:06:51+01:00`.
It reconciles the earlier sections without claiming that this validation run
performed deployment actions.

| Check | Result | Classification |
|---|---|---|
| Git branch and HEAD | `main` at `c7954b40a07e5e74da2b2176c2ac99f792061537` | OBSERVED |
| Worktree | No tracked changes; five pre-existing untracked reports preserved | OBSERVED |
| Trading Core | Running; image `sha256:856fe1e7617c83b93ee102a94c956b775b310af700db8e236f51ef0d4898b0b4`; image created `2026-11-08T19:00:07+01:00`; Flyway at `v60` | OBSERVED |
| Market Intelligence | Running; image `sha256:4247dfe2d58d1f05a03b112326f22cb2b2b74ebc176b06c64e1d3e3079181960`; image created `2026-11-08T18:59:40+01:00`; Flyway at `v25` | OBSERVED |
| Gateway | Running; image revision remains UNKNOWN; actuator returned `401` | OBSERVED |
| Persistent volumes | Targeted application containers have no mounts; named database volumes remain present and attached to PostgreSQL containers | OBSERVED |
| Docker healthchecks | Not configured for the application containers | OBSERVED |
| Root filesystem | 6,532,513,792 bytes available; 26,796,359 inodes free; 99% byte utilization and 11% inode utilization | OBSERVED |
| Docker storage | Images `16.55GB`, build cache `18.22GB`, volumes `97.37GB`; no cleanup performed | OBSERVED |
| OpenCode | Process running; no restart performed | OBSERVED |
| MCP bridge | Process running and listening on `127.0.0.1:17891`; `/v1/status` returned `{"connected": false}` | OBSERVED |
| Readiness invocation | Not attempted: no authorized scope, market, interval, cutoff or `research:inspect` grant was available | NOT_EXECUTED |

The targeted images are newer than the merge commit and the migration logs
confirm the Story 0222 schema state. The images do not carry a reliable source
revision label, so exact source-to-image identity remains **UNVERIFIED** even
though the required migrations are observed.

No deployment approval checkpoint is being requested by this execution: the
targeted deployment is already observed as completed in the runtime and in the
pre-existing report above. Any further rebuild, recreation, restart, grant
change or runtime configuration change still requires separate explicit human
approval.

## 11. Final Verdict

```text
VALIDATION_INCOMPLETE
```

The next action is a human-supported MCP reconnect/re-pair operation. No
additional application deployment, grant change, migration, export or readiness
invocation should occur until the bridge is connected again.

## 12. Validation Boundary

`git diff --check` passes. The report itself is a new untracked report and the
existing untracked reports were preserved. No commit or push was performed.

## 13. Current Operational Recheck

This addendum records a fresh read-only recheck at
`2026-11-08T20:40:44+01:00`. It supersedes the earlier capacity and pairing
observations above for the current runtime state.

| Check | Result | Classification |
|---|---|---|
| Git branch | `main` | OBSERVED |
| Git HEAD | `c7954b40a07e5e74da2b2176c2ac99f792061537` | OBSERVED |
| Worktree | Multiple tracked modifications and five untracked reports; preserved | OBSERVED |
| Gateway | Running, image `sha256:594cbcc074a5...`, created `2026-11-08T19:29:57Z`; source revision UNKNOWN | OBSERVED / UNVERIFIED |
| Trading Core | Running, image `sha256:e760eb8ffb5...`, created `2026-11-08T19:10:04Z`; source revision UNKNOWN | OBSERVED / UNVERIFIED |
| Market Intelligence | Running, image `sha256:4247dfe2d58d...`, created `2026-11-08T18:00:21Z`, before the merge | OBSERVED |
| MCP Adapter | PID `2655245`, local `mcp-adapter` process running; bridge status `{"connected": false}` | OBSERVED |
| OpenCode | Process `opencode` running; no restart performed | OBSERVED |
| Gateway actuator | HTTP `401` | OBSERVED; reachability only |
| Trading Core actuator | HTTP `403` | OBSERVED; reachability only |
| Eureka | HTTP `200` | OBSERVED |
| Root filesystem | `449G` total, `2.2G` available, `100%` used | OBSERVED |
| Root inodes | `26,572,073` free, `12%` used | OBSERVED |
| Docker images | `16.98GB` total; `5.372GB` reclaimable | OBSERVED |
| Docker build cache | `22.01GB` total; `10.59GB` reclaimable | OBSERVED |
| Docker volumes | `97.39GB` total; `442.2MB` reclaimable | OBSERVED |
| Readiness MCP invocation | Not executed | NOT_EXECUTED |

Story 0222 affects Trading Core, Market Intelligence and the MCP adapter. The
Gateway merge change is test-only according to the prior static review. Exact
source-to-image identity remains UNKNOWN because the images do not carry a
reliable repository revision label. The current dirty worktree also means a
Docker build from the repository directory would not be a verified build of
the requested merge commit.

## 14. Current Stop Decision

No rebuild, container recreation, migration, grant modification, export or MCP
restart was performed during this recheck.

The current verdict is:

```text
INSUFFICIENT_DISK_CAPACITY
```

The available `2.2GiB` is not a reasonable safety margin for even a targeted
Maven image rebuild with Docker layer growth. The safe next step requires
separate human approval for one of:

1. A non-destructive Docker build-cache review/removal, without touching named
   volumes or historical order-book data.
2. Additional host filesystem capacity.

After capacity is remediated, a clean source context for the verified target
commit must be selected, then a new deployment approval checkpoint is required.
The MCP bridge also requires legitimate human re-pairing through the existing
Agent Management flow because its current in-memory pairing state is
`connected: false`.

## 15. Approved Cache Remediation

The requested non-destructive Docker build-cache remediation was explicitly
approved and executed:

```text
docker builder prune --force
```

Observed result:

| Check | Before | After | Classification |
|---|---:|---:|---|
| Root filesystem available | `2.2GiB` | `15GiB` | OBSERVED |
| Root filesystem utilization | `100%` | `97%` | OBSERVED |
| Docker build cache | `22.01GB`, `10.59GB` reclaimable | `11.42GB`, `0B` reclaimable | OBSERVED |
| Docker volumes | `97.39GB` | Preserved | OBSERVED |
| Running containers | 25 | 25 | OBSERVED |

No image, container, named volume, database record or order-book data was
deleted.

## 16. Migration Source Contradiction

Current Trading Core startup logs show:

- `Successfully validated 63 migrations`;
- schema current version `62` at startup;
- `V63 - extend agent research permissions` applied successfully;
- schema now at `v63`.

The `V61`, `V62` and `V63` migration files are present only as uncommitted
worktree changes and are not part of merge commit `c7954b40...`. Rebuilding
strictly from that merge would therefore not provide a source set that matches
the current database migration history and could fail Flyway validation.

The deployment is blocked pending an explicit choice between:

1. Building from the current worktree, accepting that the runtime source is not
   the exact Story 0222 merge revision; or
2. Preparing a clean, committed source revision that includes the migrations
   already applied to the database, which is outside this operational task.

No rebuild or container recreation was performed after the cache remediation.

## 17. Approved Runtime Synchronization

The selected source was the current worktree, including the already applied
`V61`-`V63` migrations. The targeted synchronization was then executed
sequentially:

```text
docker compose build trading-app
docker compose build market-intelligence
docker compose up -d --no-deps --force-recreate trading-app
docker compose up -d --no-deps --force-recreate market-intelligence
```

Both builds completed successfully using the existing Docker layers. The two
target containers were recreated without touching their PostgreSQL volumes or
other services.

Runtime evidence after recreation:

- `trading-app`: running;
- `market-intelligence`: running;
- Trading Core validated all 63 migrations and reported schema `v63` up to date;
- Market Intelligence validated all 25 migrations and reported the schema up to
  date;
- all other compose services remained running;
- disk availability remained `15GiB` (`97%` used);
- `git diff --check` passed.

## 18. Post-Recreate Findings

Market Intelligence emitted two warning classes during its scheduled lifecycle
after startup:

1. replay artifact population returned `Forbidden` for market
   `e23ba62a-995f-4755-8deb-11a71ab3788b`;
2. research feature projection reported
   `Feature snapshot identity has conflicting content` for two executions.

These warnings did not stop the service, but they prevent claiming a clean
historical-research runtime validation and require a separate investigation.

The OpenCode bridge remains running as PID `2655245`, but the UI connection
attempt failed because the deployed UI origin is `http://localhost:17085` while
the bridge's configured allowed origins remain the development origins on port
`4200`. No credential was displayed or persisted by the agent.

The direct MCP readiness invocation therefore remains `NOT_EXECUTED`. No
credential, internal endpoint, database query, grant mutation, or invented
market/scope selection was used to bypass this blocker.

## 19. Bridge Origin Configuration

The project OpenCode configuration was updated to allow the deployed UI origin
while preserving the development origins:

```text
TRADING_OS_MCP_BRIDGE_ALLOWED_ORIGINS=http://localhost:17085,http://127.0.0.1:17085,http://localhost:4200,http://127.0.0.1:4200
```

The old bridge child process was stopped. The running OpenCode process keeps
its already loaded configuration, so a full OpenCode restart is required before
the bridge is relaunched with the corrected origin configuration. The bridge
port is currently not listening until that restart.

## 20. Post-Restart MCP Recheck

This addendum records the read-only recheck after the runtime was rebuilt from
the current worktree and the OpenCode bridge was reconnected through the Agents
interface. No grant, volume, migration or market data was modified.

### Bridge and application state

| Check | Result | Classification |
|---|---|---|
| Bridge listener | `127.0.0.1:17891` listening | PASS |
| Bridge status | `connected: true` with origin `http://localhost:17085` | PASS |
| Trading Core | running; source/runtime state reported at Flyway `v63` | OBSERVED |
| Market Intelligence | running; source/runtime state reported at 25 migrations | OBSERVED |
| Persistent volumes | preserved; no volume operation performed in this recheck | PASS |
| Agent grants | existing `research:inspect` and `research:export` grants observed for the authorized scope | OBSERVED |

The existing OpenCode agent was reconnected from the Agents page. No
credential was displayed, copied or persisted by this validation.

### Resolved application state

The active scope with an active `research:inspect` grant is:

```text
scope: 87a98c16-2db5-4f0c-af41-da7539b7d817
status: ACTIVE
authorized markets:
  - 8b336ec3-1557-4432-b052-ae569e041dac
  - f345a888-031d-48e6-a5fb-0e49d499a2eb
  - ba1576b7-7bd6-4260-a309-7e5a156cbce8
  - f3974ba8-c74a-4517-b9ba-001423ce028e
historical interval: PT1H
cutoff: 2026-10-16T22:00:00Z to 2026-11-08T20:00:00Z
dataset profile: feature-projection-v1
```

The scope's `historicalConstraints` and allowed dataset profiles are returned
as `null` by the application. The current configured research market is
`e23ba62a-995f-4755-8deb-11a71ab3788b`, but it belongs to a different active
scope whose `research:inspect` grant is revoked. It was therefore not used and
no scope or grant was broadened.

### MCP readiness attempt

One call was attempted through MCP only:

```text
inspect_historical_research_readiness(
  scope_id=87a98c16-2db5-4f0c-af41-da7539b7d817,
  market_ids=[8b336ec3-1557-4432-b052-ae569e041dac],
  cutoff_start=2026-10-16T22:00:00Z,
  cutoff_end=2026-11-08T20:00:00Z,
  dataset_profile=feature-projection-v1,
  max_observations=10000
)
```

Result: `BLOCKED`. The MCP tool execution returned a refusal/error without a
structured response payload, so no readiness verdict, counts, correlation ID
or current dataset eligibility result can be claimed. The missing historical
constraints on the granted scope are a confirmed application-state blocker.
No direct internal endpoint, database query, export request or retry with an
invented parameter was used.

### Runtime warnings retained

The post-recreate Market Intelligence warnings remain unresolved:

- replay artifact population returned `Forbidden`;
- `Feature snapshot identity has conflicting content` was reported for research
  feature projection.

These warnings prevent a clean historical-research validation and were not
repaired or suppressed during this recheck.

## 21. Current Verdict

```text
BLOCKED
```

The bridge and OpenCode pairing are operational, but the authorized scope does
not expose the historical interval/profile constraints required for a valid
readiness result, and the MCP inspection was refused without structured
diagnostics. No export is authorized by this validation, and no persistent
runtime or market state was changed.
