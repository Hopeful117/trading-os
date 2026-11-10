# Story 0222 Post-Merge Operational Validation

**Date:** 2026-11-08  
**Baseline:** `main` at `c7954b40a07e5e74da2b2176c2ac99f792061537`  
**PR:** #277  
**Scope:** read-only operational validation; no production code, configuration,
database, credential, grant, service, broker or deployment changes were made.

## 1. Executive Summary

Primary verdict:

```text
AGENT_RUNTIME_AUTHORIZATION_BLOCKED
```

Story 0222 is structurally present in the merged baseline and the relevant
automated tests pass. The local Trading OS stack is running, but this validation
process has no legitimate `TRADING_OS_AGENT_CREDENTIAL`; no human JWT was used
as a substitute. Consequently, the real agent authentication, grant resolution,
readiness inspection, export authorization and shared-runtime export lifecycle
could not be exercised.

The current historical population is not established as ML-ready. The latest
authorized runtime measurement is absent. Existing repository evidence records
zero eligible samples in the previous audited environment, and the current
Market Intelligence logs show repeated feature-snapshot identity conflicts and
forbidden replay population attempts. These are evidence of an operational data
problem, but they do not by themselves establish the complete current root
cause or current row counts.

The provenance issue is historically evidenced but not remeasured in the current
runtime. In particular, the prior report recorded 470 of 471 snapshots created
after their evaluation cutoff and zero eligible samples; this validation does
not promote those historical counts to current measurements.

No real export was run. The repository fixture validates successfully, but it is
not runtime population evidence. The first required operational action is to
provide an explicitly scoped, legitimate agent credential and grants in an
isolated environment, then perform bounded readiness inspection before any
export approval.

## 2. Git Baseline And Repository Status

Observed:

| Item | Result |
|---|---|
| Branch | `main` |
| HEAD | `c7954b40a07e5e74da2b2176c2ac99f792061537` |
| HEAD subject | Merge pull request #277 from `Hopeful117/story/0222-agent-native-historical-research` |
| `main` tracking | Synchronized with `origin/main` |
| Tracked worktree changes | None |
| Untracked pre-existing files | Two architecture investigation reports; preserved unchanged |
| Story 0222 branch | Deleted locally and remotely before this validation |

The Story file still says `Repository Analysis pending human approval` and its
acceptance checkboxes remain unchecked, while the implementation is merged.
This is a documentation/workflow-state discrepancy, not a runtime code defect.
The implementation report also still says that changes are uncommitted; that
statement is stale after PR #277 was merged.

## 3. Implementation Verification

### 3.1 Authorization and scope

Static inspection confirms:

- `RESEARCH_INSPECT("research:inspect")` and
  `RESEARCH_EXPORT("research:export")` are separate permissions.
- Both permissions map only to `RESEARCH_SCOPE`.
- Historical authorization validates active agent, credential identity and
  version, owner-owned scope, active scope, market membership, cutoff interval,
  profile and resource limits.
- Existing `research:run` remains a separate path and retains the two-market
  workflow.
- Authorization occurs before readiness data is returned.
- Export status is filtered by agent identity and re-authorized against the
  stored request.
- Queued export execution revalidates credential and grant state.

Relevant implementation locations:

- `trading-core/.../AgentAuthorizationService.java`
- `trading-core/.../AgentGrantCompatibility.java`
- `market-intelligence/.../HistoricalReadinessApplicationService.java`
- `market-intelligence/.../HistoricalDatasetExportApplicationService.java`

### 3.2 Export controls

Static inspection confirms server-side limits of 100,000 rows, 100 MiB and
3,600 seconds, in addition to request validation and bounded population JSON.
Export output is resolved below the configured server-controlled root. Jobs use
an agent/idempotency-key uniqueness constraint and canonical request fingerprints.
Dispatch is registered after transaction commit. Published bundles are validated,
checksummed and failed output directories are removed when cleanup succeeds.

The implementation exposes job and artifact metadata only. It does not return
dataset rows, absolute output paths or provider payloads through MCP.

### 3.3 Gateway and MCP boundary

Static inspection confirms:

- Gateway routes cover readiness, export submission and export status.
- `MiAgentAccessPolicyFilter` allow-lists only the expected agent research
  routes and rejects other Market Intelligence routes for agent principals.
- MCP calls public Gateway paths only.
- MCP exchanges `TRADING_OS_AGENT_CREDENTIAL` for a short-lived token and keeps
  it in process memory.
- MCP rejects non-local HTTP Gateway URLs unless HTTPS is used.
- Human JWT configuration is not accepted by the adapter path.
- No broker, account mutation, position mutation, risk or order capability was
  introduced by Story 0222.

### 3.4 Migrations

The merged source contains:

- Trading Core `V59__research_scopes.sql`.
- Trading Core `V60__historical_research_scope_constraints.sql`.
- Market Intelligence `V24__agent_historical_dataset_export_jobs.sql`.
- Market Intelligence `V25__historical_dataset_export_validation_state.sql`.

The files are coherent with the job and scope models. Runtime migration state
was not directly inspected because direct database inspection is forbidden by
this validation scope. No database mutation was performed.

## 4. Test Verification

| Check | Status | Evidence |
|---|---|---|
| Trading Core focused authorization tests | PASS | `AgentGrantCompatibilityTest`, `HistoricalResearchConstraintsTest` |
| Market Intelligence focused readiness/export tests | PASS | `HistoricalReadinessServiceTest`, `ResearchDatasetExportCommandTest` |
| Gateway route test | PASS | `GatewayMarketIntelligenceRouteTest` |
| MCP adapter tests | PASS | Local `mcp-adapter/.venv/bin/pytest -q`; 36 tests passed |
| `./scripts/test-all.sh` | PASS | All configured Java tests, 60 frontend test files / 575 tests, frontend build |
| `./scripts/quality-verify.sh` | FAIL | Backend coverage gate failed only for `broker-service`: 75.51% < 80%; other reported modules passed |
| `git diff --check` | PASS | No whitespace errors |
| `research/ml` pytest suite | BLOCKED | No project venv and system Python has no `pytest`; no global installation performed |
| Research ML fixture `validate` | PASS | `PYTHONPATH=research/ml/src python3 -m research_dataset validate research/ml/tests/fixtures/java-generated` |
| Research ML fixture `diagnose` | PASS | One valid fixture sample, one `AVAILABLE` outcome, zero exclusions |
| SonarQube | NOT_RUN | Not required to establish the runtime blockers; no scan was launched |

The quality gate failure is not attributable to Story 0222 based on the
available evidence: it is a pre-existing module-wide `broker-service` coverage
shortfall and not a Story 0222 changed surface. It nevertheless means the
repository-wide quality script did not pass.

The fixture result is deliberately classified separately from runtime export:
it proves Java/Python bundle compatibility only.

## 5. Runtime Service Inventory

`docker compose ps --all` showed all relevant containers running:

- Gateway
- Trading Core
- Market Data
- Market Intelligence
- News Service
- Broker Service
- Eureka
- Their PostgreSQL containers
- AI Engine and Trading Web

The application containers report `running` but have no Docker healthcheck.
Unauthenticated health probes were not treated as health proof:

| Probe | HTTP result | Interpretation |
|---|---:|---|
| Gateway `/actuator/health` | 401 | Reachable; protected; health not established |
| Trading Core `/actuator/health` | 403 | Reachable; protected; health not established |
| Market Data `/actuator/health` | 403 | Reachable; protected; health not established |
| News Service `/actuator/health` | 401 | Reachable; protected; health not established |

The runtime is therefore classified as **running and reachable**, but not
authenticated, authorized or business-operation validated.

### Runtime configuration evidence

Non-secret container configuration inspection showed:

- Historical OHLC capture enabled for five configured market UUIDs at
  `FIFTEEN_MINUTES`, `ONE_HOUR` and `FOUR_HOURS`.
- Research lifecycle enabled for one configured market UUID.
- Research artifact population enabled with a per-run maximum of `1` and depth
  `25`.
- Agent JWT configuration keys exist in Trading Core and Market Intelligence.
- No agent credential value was present in this validation process.

These flags show configured intent, not valid historical population or grant
authorization.

## 6. Storage Capacity

Observed storage:

| Measurement | Result |
|---|---:|
| Root filesystem | 449 GiB total, 419 GiB used, 6.7 GiB available, 99% used |
| `research/` working-tree directory | 671 MiB |
| Market Data Docker volume | 91.42 GiB |
| Trading Core Docker volume | 3.38 GiB |
| Market Intelligence Docker volume | 219.9 MiB |
| Docker build cache | 17.7 GiB |

The available root capacity is a material operational constraint. No export,
reconstruction, cleanup, Docker pruning or filesystem permission change was
performed. A real export requires a preflight estimate and explicit approval;
the configured 100 MiB per-job bound does not remove the need to account for
temporary files and existing filesystem pressure.

## 7. Agent Authentication And Grants

Observed process state:

- `TRADING_OS_AGENT_CREDENTIAL`: absent.
- `TRADING_OS_JWT`: absent.
- `TRADING_OS_GATEWAY_URL`: unset.

Safe negative probes:

- `GET /api/v1/markets` through Gateway: `401`.
- `GET /api/v1/intelligence/research/readiness` through Gateway: `401`.
- Empty-body `POST /api/v1/agents/token`: `403`.

No human JWT was used, no credential was generated or extracted, and no grant
was created. Agent principal recognition, grant lookup, scope resolution and
historical permission checks therefore remain unverified.

Classification:

```text
AGENT_RUNTIME_AUTHORIZATION_BLOCKED
```

Legitimate operator action required: provision or provide an already-approved
agent credential through the existing Agent Management flow, with active,
unexpired, non-revoked exact grants for `research:inspect` and, separately when
needed, `research:export` on an owner-scoped historical research scope. The
credential must be supplied through the intended runtime mechanism and must not
be placed in repository files or MCP configuration.

## 8. MCP Readiness Inspection

Not executed. The required agent credential and grant were unavailable, so no
scope, market or interval was invented and no internal Market Intelligence or
Market Data endpoint was called.

The intended bounded probe remains:

- one configured canonical market from an authorized scope;
- one declared interval and cutoff range within that scope;
- one `research:inspect` grant;
- MCP tool `inspect_historical_research_readiness` through the Gateway.

No current verdict, counts, coverage gaps, provenance counts, outcome maturity,
or correlation ID can be claimed from this validation.

## 9. Historical Population And Provenance

### Current runtime measurements

Current persisted counts are **UNKNOWN**. Direct database inspection is out of
scope, and the agent readiness endpoint could not be invoked.

Current runtime logs do provide operational symptoms. Market Intelligence
repeatedly logged:

- `Feature snapshot identity has conflicting content` from the research
  lifecycle for the configured market.
- `Replay artifact population failed ... errorType=Forbidden` for replay artifact
  population.

These messages demonstrate that the configured lifecycle is not producing a
clean, validated population in the observed runtime. They do not prove whether
the primary cause is conflicting persisted facts, service authorization,
configuration, or another downstream condition.

### Historical evidence retained from prior reports

The previous Story 0221 / investigation reports recorded, in an earlier
environment:

- approximately 471 FeatureSnapshots;
- approximately 470 snapshots created after the evaluation cutoff;
- zero snapshots created by the cutoff;
- incomplete or missing `fetchedAt` provenance;
- zero eligible samples;
- approximately 96 exclusions;
- approximately 13,218 OHLC rows in the audited environment.

Those values are not current measurements. They remain relevant historical risk
context only.

### Temporal audit conclusion

The code and bundle contract preserve the required distinction between:

- `sourceOccurredAt`;
- `fetchedAt`;
- `knownAt`;
- `evaluationCutoff`;
- `createdAt`.

Eligibility requires evidence `knownAt <= evaluationCutoff`; snapshot creation
after the cutoff is not automatically invalid if all source evidence was known
by the cutoff. However, the available historical report indicates that the
affected population could not demonstrate this condition. The current
validation cannot distinguish current post-cutoff evidence from missing
acquisition timestamps, incomplete capture, missing snapshots or immature
outcomes without an authorized readiness response or approved bundle.

Provenance status: **historically evidenced, currently unconfirmed**.

## 10. Controlled Export Feasibility

### Contract feasibility

The code supports a bounded asynchronous export with:

- exact scope and `research:export` authorization;
- declared market IDs, cutoff bounds, dataset profile and population selection;
- maximum rows, bytes and duration;
- idempotency and request fingerprinting;
- post-commit dispatch;
- authorization revalidation before execution and status retrieval;
- checksum, artifact metadata, Java contract status and experiment eligibility
  status.

### Operational feasibility

No real export is currently approved or feasible to execute safely because:

- no agent credential/grant is available;
- current runtime population is not measured;
- previous evidence had zero eligible samples;
- current runtime logs show population/projection failures;
- root filesystem free space is only 6.7 GiB at 99% utilization.

Required approval checkpoint before a shared-runtime export:

| Parameter | Required value before approval |
|---|---|
| Agent identity | Operator-provided non-secret reference |
| Scope | Exact owner-scoped research scope UUID |
| Markets/intervals | Selected from authorized scope and catalogue evidence |
| Cutoff | Explicit bounded UTC interval |
| Dataset profile | Exact profile/version |
| Maximum rows/bytes/duration | Explicit values within grant and server limits |
| Estimated storage | Calculated from readiness and output bounds |
| Destination | Server-controlled export category, no arbitrary path |
| Impact | Expected database, CPU, disk and lifecycle impact |
| Cleanup | Approved bounded cleanup of the job output if required |

No shared export was executed, and no rollback or destructive cleanup was
needed.

## 11. Dataset And Python Validation

### Existing fixture bundle

The committed Java-generated fixture passed the existing Python validator and
diagnostics:

- `valid: true`;
- one observation and one eligible sample;
- one `AVAILABLE` outcome;
- zero exclusions;
- zero known-at violations;
- zero provenance violations;
- dataset fingerprint:
  `8eefc33c7614134c56b8d8cb236197e54011826ef9ac9f83bfb258f48e0eb260`;
- physical cadence and effective independence reported as not fully
  determinable/approximate as designed.

This is an isolated fixture result and not evidence of production population.

### Runtime bundle

No approved runtime bundle was available, so manifest verification,
fingerprint verification, temporal eligibility and experiment eligibility could
not be run against real historical data.

No model was trained and no experiment result or profitability claim was made.

ML population readiness is **UNKNOWN for current runtime** and **INSUFFICIENT
based on the last audited real population**.

## 12. End-To-End Evidence Matrix

| Capability | Code | Tests | Runtime | Verdict |
|---|---|---|---|---|
| Agent authentication | Dedicated credential exchange and agent JWT code present | Negative/token contract coverage present | Credential absent; no valid exchange | BLOCKED |
| Agent scope authorization | Exact agent, credential, grant, owner, scope and constraint checks present | Focused authorization tests pass | No agent principal/grant available | BLOCKED |
| MCP readiness inspection | Gateway-backed typed tool present | MCP tool/schema tests pass | Not invoked | BLOCKED |
| Historical coverage | Deterministic readiness service present | Readiness tests pass | Current counts unavailable | UNKNOWN |
| Temporal provenance | `knownAt <= evaluationCutoff` and provenance checks present | Fixture and Java contract tests pass | Current evidence unavailable; prior defect observed historically | INSUFFICIENT / UNCONFIRMED CURRENT |
| Export authorization | Distinct export permission and revalidation present | Focused export/authorization tests pass | No authorized agent | BLOCKED |
| Export idempotency | Unique agent/key and canonical fingerprint present | Focused contract tests pass | Not exercised at runtime | CODE-VERIFIED ONLY |
| Export execution | Bounded async job, validation and cleanup present | Java export tests pass | Not approved or executed | PENDING |
| Artifact integrity | Manifest validation, checksum and metadata persistence present | Fixture validation passes | No runtime artifact | PENDING |
| Python validation | Offline validator and diagnostics present | Fixture CLI passes; pytest blocked | No runtime bundle | FIXTURE-VALIDATED ONLY |
| ML population readiness | Existing gates and diagnostics present | Fixture diagnostics pass | Real current population not measured | INSUFFICIENT / UNKNOWN CURRENT |

## 13. Confirmed Blockers And Root Causes

### Blocker A: Agent runtime access unavailable

- Classification: `AUTHENTICATION` / `AUTHORIZATION`.
- Symptom: no credential in the process; protected Gateway routes return `401`.
- Evidence: environment inspection and unauthenticated Gateway probes.
- Root cause: no legitimate agent credential/grant was supplied to this run.
- Severity: High for end-to-end validation; not a code defect.
- Minimum action: operator supplies an existing approved credential and exact
  grants through the supported management/runtime path.
- Code change required: No.
- Human authorization required: Yes.

### Blocker B: Current historical population cannot be measured

- Classification: `DATASET_ELIGIBILITY` / `CONFIGURATION`.
- Symptom: readiness cannot be called and database inspection is prohibited.
- Evidence: no agent access; prior report explicitly marks counts historical.
- Root cause: missing authorized operational read path for this validation.
- Severity: High for dataset decision; not enough evidence for a code defect.
- Minimum action: run bounded readiness inspection with an authorized agent.
- Code change required: Not established.
- Human authorization required: Yes.

### Blocker C: Runtime research lifecycle failures

- Classification: `DATA_POPULATION` with confirmed cause `UNKNOWN`.
- Symptom: repeated snapshot identity conflicts and replay population `Forbidden`.
- Evidence: Market Intelligence container logs during the running stack.
- Root cause: not established without authorized correlation and downstream
  diagnostic evidence; do not infer from log text alone.
- Severity: High because it can prevent population growth and valid export.
- Minimum action: authorized readiness inspection and service-level correlation
  of the failing execution, without rewriting persisted evidence.
- Code change required: Unknown; defer until cause is confirmed.
- Human authorization required: Yes for runtime investigation beyond read-only
  logs and for any repair or replay operation.

### Blocker D: Storage headroom

- Classification: `STORAGE_CAPACITY`.
- Symptom: root filesystem is 99% full with 6.7 GiB available; Market Data
  volume is 91.42 GiB.
- Evidence: `df -h` and `docker system df -v`.
- Root cause: confirmed capacity pressure; detailed historical allocation was
  not inspected directly.
- Severity: High for large exports/reconstruction.
- Minimum action: human-approved storage plan and export-size preflight.
- Code change required: No immediate code change.
- Human authorization required: Yes for cleanup or retention changes.

### Blocker E: Repository quality gate

- Classification: `TEST_INFRASTRUCTURE` / pre-existing coverage gap.
- Symptom: `quality-verify.sh` failed because `broker-service` coverage is
  75.51%, below the 80% gate.
- Evidence: script output; all other reported module gates passed.
- Root cause: not connected to Story 0222 changes based on changed scope.
- Severity: Medium for repository quality status, low for this workflow.
- Minimum action: address broker-service coverage separately.
- Code change required: Not for Story 0222 validation.
- Human authorization required: No, if separately prioritized.

### Blocker F: Research ML test environment

- Classification: `TEST_INFRASTRUCTURE`.
- Symptom: no `research/ml/.venv`; system Python lacks `pytest`.
- Evidence: documented environment inspection and command failure.
- Root cause: local dependency environment unavailable.
- Severity: Medium for complete test verification.
- Minimum action: create/use the documented isolated environment, subject to
  disk/network approval; do not install globally.
- Code change required: No.
- Human authorization required: No for an isolated local environment, but no
  environment was created in this validation because disk pressure is high.

## 14. Minimum Corrective Actions

1. Provision or expose one legitimate agent identity with exact active
   `research:inspect` grant for an isolated owner scope.
2. Invoke readiness through MCP/Gateway for one configured market and bounded
   cutoff interval; record the structured response and correlation ID.
3. Reconcile current counts, cutoff coverage, `knownAt` provenance and outcome
   maturity before considering export.
4. Investigate the observed snapshot conflict and replay `Forbidden` result
   using authorized service diagnostics; do not mutate historical facts.
5. Confirm storage headroom and prepare the required export approval checkpoint.
6. Only after readiness is sufficient and approval is explicit, run a bounded
   export and validate the resulting bundle with Java metadata and Python tools.

No new architecture or code Story is justified by this validation alone. A new
implementation Story should be considered only after the authorized runtime
inspection confirms a reproducible implementation defect rather than an
authorization, configuration, data-population or capacity issue.

## 15. Recommended Next Engineering Milestone

Operationally, the next milestone is **authorized isolated runtime validation**:

- one exact research scope;
- one configured canonical market;
- one bounded interval/cutoff;
- `research:inspect` grant;
- readiness response and provenance audit;
- export approval only if the readiness verdict and storage preflight permit it.

This is an operational validation step, not a reason to bypass the Gateway,
create a privileged credential, expose an internal endpoint or weaken temporal
eligibility.

## 16. Independent Status Summary

| Dimension | Status |
|---|---|
| Code verification | PASS, with documentation-state discrepancies noted |
| Automated tests | PASS for `test-all.sh` and focused Story tests; repository quality script FAIL due broker coverage |
| Runtime agent authentication | BLOCKED |
| Runtime authorization | BLOCKED |
| Historical population | UNKNOWN current; historically insufficient evidence |
| Export | NOT RUN / PENDING authorization, readiness and approval |
| Python validation | PASS for committed fixture; runtime bundle NOT AVAILABLE; pytest BLOCKED |
| ML readiness | UNKNOWN current; last audited real population insufficient |

## Final Verdict

```text
AGENT_RUNTIME_AUTHORIZATION_BLOCKED
```

The agent-native architecture is code- and test-supported but not operationally
validated in this environment. Current historical data is not demonstrated to
be sufficient for a first trustworthy research dataset. The provenance defect
is confirmed in historical audit evidence but not remeasured against current
runtime data. A real export is contractually supported but operationally
pending authorization, current readiness evidence, and storage approval.

The single most important next action is to provide an explicitly authorized,
isolated agent credential and `research:inspect` grant, then collect one real
Gateway-backed readiness response before any export is considered.
