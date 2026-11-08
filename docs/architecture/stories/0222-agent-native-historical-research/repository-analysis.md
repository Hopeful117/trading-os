# Repository Analysis - Story 0222

## Status

Completed - human approval required before Implementation Planning.

No production code, migration, runtime database, credential, broker, provider,
commit, push or merge operation was performed during this analysis. No live
agent grant was created or expanded.

## Repository Baseline

- Repository: `/home/ludo/workspace/trading-os`
- Branch: `main`
- HEAD: `ed9284ac` (`Merge pull request #276 from Hopeful117/story/0221-historical-research-dataset-readiness`)
- Branch state: synchronized with `origin/main`.
- Pre-existing untracked files were preserved:
  - `docs/architecture/reports/historical-data-next-milestone-investigation-2026-11-08.md`
  - `docs/architecture/reports/agent-native-historical-research-architecture-investigation-2026-11-08.md`
- Story 0220 and Story 0221 are present and merged into the current `main` history.
- Story 0219 documentation contains historical status text that is inconsistent
  with the current implementation and Story 0220 baseline. Current source and
  current `main` are authoritative for physical state.

## Context Sources

Read and reconciled:

- repository `AGENTS.md` and global engineering workflow instructions;
- Story 0219 - Agent Research Authorization;
- Story 0220 - Research Lifecycle Observability;
- Story 0221 - Historical Research Dataset Readiness;
- `historical-data-next-milestone-investigation-2026-11-08.md`;
- `agent-native-historical-research-architecture-investigation-2026-11-08.md`;
- Research Intelligence roadmap and gap matrix;
- ADR-057, ADR-060, ADR-061, ADR-062;
- ADR-088, ADR-089 and ADR-090;
- current Trading Core, Gateway, Market Intelligence and MCP adapter source/tests;
- current `research/ml` validator, dataset and experiment contracts.

No usable DevLog MCP capability or repository mapping is available in this
execution context. The repository and accepted ADRs are therefore the
authoritative sources for current implementation. Obsidian was not consulted;
the required context is repository-local.

## Current End-to-End Flow

```text
TRADING_OS_AGENT_CREDENTIAL
    -> POST /api/v1/agents/token through Gateway
    -> short-lived dedicated agent JWT
    -> Gateway agent authentication and route forwarding
    -> Market Intelligence agent principal
    -> Trading Core research-scope authorization
    -> ResearchApplicationService
    -> persisted ResearchExecution / ResearchReport
```

The MCP adapter is a thin Gateway client. `AgentTokenProvider` exchanges the
dedicated credential, keeps the access token in process memory, invalidates it
on unauthorized responses and never exposes the credential or token as tool
output. `TradingOsClient` only calls configured Gateway paths, applies HTTPS
outside localhost, bounds response bytes and does not retry POST requests.

Existing research MCP tools are:

- `start_market_research`;
- `get_research_status`;
- `get_research_report`.

They use the public `/api/v1/intelligence/research` contract and preserve
idempotency for starts. No MCP tool calls a repository, internal endpoint,
provider, broker or database.

## Existing Agent Authorization

Trading Core currently contains:

- dedicated agent identity, credential and JWT validation;
- `AgentPermission.RESEARCH_RUN` (`research:run`);
- `AgentResourceType.RESEARCH_SCOPE`;
- persistent owner-owned `ResearchScope`;
- exact grant lookup with active agent, grant and scope checks;
- credential ID/version validation for research authorization;
- authorization audit events with correlation IDs.

`ResearchScope` currently requires exactly two canonical market IDs and supports
status, owner, creation, expiry, revocation and optimistic versioning. The
existing `AgentAuthorizationService.authorizeResearch` also requires exactly two
requested markets contained by the scope.

The current model has no distinct permissions for historical inspection or
export and no scope fields for interval, cutoff, row, byte, duration or dataset
profile constraints. Existing `research:run` must remain unchanged and must not
implicitly authorize either new capability.

## Existing Readiness and Export

Market Intelligence already owns deterministic historical research behavior:

- `ResearchReadinessService` computes current configured-market readiness,
  feature snapshot counts, outcome status counts, evidence timestamps and
  approximate population with bounded remote checks and caching;
- `ResearchReadinessController` exposes the current readiness through
  `GET /api/v1/intelligence/research/readiness`, authenticated for human reads;
- historical reconstruction has strict cutoff and provenance policy;
- `ResearchDatasetBuilder` pairs immutable feature snapshots and explicitly
  selected immutable outcomes;
- `ResearchDatasetExportService` delegates to the existing deterministic
  builder and bundle exporter;
- `ResearchDatasetBundleExporter` writes a manifest, JSONL samples and README
  through controlled temporary output handling and fingerprints the result;
- the current export command is an `ApplicationRunner` enabled by a property,
  reads a population specification from a filesystem path and writes to a
  caller-supplied `Path`.

The current readiness response is global/configuration-based rather than
request-scoped. It does not provide the full requested interval/cutoff,
coverage-gap, provenance completeness or explicit `READY`/`INSUFFICIENT`/
`BLOCKED`/`UNKNOWN` contract. The current export command is not a public agent
workflow: it has no agent authorization, async job identity, idempotency,
revocation recheck, artifact retrieval boundary or server-controlled output
root. It must not be exposed directly.

## Temporal and Dataset Contracts

The accepted historical evidence and dataset decisions establish these
non-negotiable rules:

- Market Data owns canonical historical evidence.
- `knownAt <= evaluationCutoff` is the strict evidence eligibility condition.
- `createdAt` is not by itself proof of look-ahead leakage.
- Feature snapshots are immutable, versioned projections and exclude future
  outcomes.
- Outcomes are selected by complete immutable identity, not latest row or
  database order.
- The dataset boundary is Market Intelligence-owned and provider-neutral.
- Java remains factual deterministic research authority; Python remains an
  offline consumer for validation, diagnostics and experiments.
- Existing provenance defects must be reported, not repaired in this Story.

The Python validator already checks manifest/sample schema, fingerprints,
strict provenance, missingness, outcome identity and maturity. It should not be
invoked as an arbitrary subprocess from an HTTP request. Java export completion,
Java contract validity, Python validation completion and experiment eligibility
must remain separate states.

## Reusable Components and Affected Modules

### Trading Core

- `AgentPermission` and `AgentGrantCompatibility` for new distinct permission
  values and `RESEARCH_SCOPE` compatibility;
- `ResearchScope` and its repository/service/API for bounded historical scope
  constraints while preserving exactly-two-market `research:run` behavior;
- `AgentAuthorizationService` for exact permission/resource/owner/grant/
  credential-version checks and authorization audit;
- additive migration and focused tests if scope constraints require persistence.

### Gateway

- existing `/api/v1/intelligence/**` route and agent authentication filters;
- route tests and downstream forwarding tests;
- no new authentication system or client-controlled actor authority.

### Market Intelligence

- existing readiness services and evidence repositories as deterministic inputs;
- existing `ResearchDatasetExportService`, population specification, manifest,
  fingerprint and bundle exporter;
- a focused application boundary for scoped readiness and export job lifecycle;
- public controllers/DTOs with `ResponseEntity`, structured errors and
  correlation propagation;
- additive persistence only if an export job/artifact lifecycle cannot be
  represented by existing persistence. No generic workflow engine.

### MCP Adapter

- existing `TradingOsClient` and agent token exchange;
- three bounded tools for readiness, export request and export status;
- no local authorization decisions, direct internal calls or full dataset
  response.

### Python research/ml

- existing validator/diagnostic contract remains the offline verification
  boundary;
- no production HTTP coupling or second validator.

## Proposed Minimal Scope

The smallest coherent implementation is:

1. Add `research:inspect` and `research:export` as distinct default-deny
   permissions over `RESEARCH_SCOPE`.
2. Extend the existing scope additively with bounded historical constraints,
   without changing the exactly-two-market invariant for `research:run`.
3. Add a scoped, read-only historical readiness application contract that
   returns deterministic diagnostics and explicit verdicts.
4. Add an idempotent, bounded export request/status lifecycle that invokes the
   existing deterministic exporter and stores only controlled artifact metadata.
5. Revalidate grant, scope, credential version and owner before queued export
   execution and on status/artifact retrieval.
6. Add Gateway-backed MCP tools and focused security/integration tests.
7. Document operator provisioning through existing legitimate grant mechanisms.

Historical reconstruction, provider backfill, provenance repair, generic
approval/workflow infrastructure, Python subprocess execution and trading
authority remain out of scope.

## Authorization and Approval Findings

Readiness inspection is read-only and can use an explicit `research:inspect`
grant without per-request human approval.

Bounded export is non-trading but creates an offline artifact and consumes
server resources. It should be authorized by an explicit `research:export`
grant with server-enforced limits. The existing human approval workflow is
currently specific to `ACCOUNT_SYNCHRONIZATION` and `BROKER_ACCOUNT`; it should
not be generalized in this Story merely to export a research bundle.

Historical reconstruction is explicitly excluded. If later implemented, it
should use a separate stronger grant and an explicit human approval or
pre-authorization decision because it can materialize derived historical
evidence.

## Security Requirements

- Dedicated agent credential and agent JWT only; no human JWT reuse.
- Gateway remains the only external ingress.
- Server-side exact owner, scope, permission, market, interval and cutoff
  enforcement on every operation.
- Existing grants are not widened silently.
- Grant expiry/revocation and credential-version mismatch fail closed.
- Unauthorized scope/resource existence is not disclosed.
- Export limits, safe server-controlled output root, artifact names, bytes,
  rows, duration and concurrency are server-controlled.
- Idempotency binds owner, agent, scope, permission, canonical request
  fingerprint and key; conflicting reuse fails deterministically.
- No broker, account synchronization, risk, order, position or credential
  authority is introduced.
- Artifacts expose metadata/checksums, not absolute filesystem paths, tokens,
  credentials or unbounded logs.

## Risks and Open Decisions

1. The exact scope extension must be chosen before implementation: additive
   fields on `ResearchScope` versus a capability-specific historical scope
   specification. The smallest likely option is additive optional constraints,
   provided existing `research:run` records and validation remain unchanged.
2. The repository currently has no public export job/artifact lifecycle. A
   focused research-specific lifecycle may require a migration; creating a
   generic job framework would exceed scope.
3. Existing exporter output handling must be adapted to server-controlled
   storage without weakening deterministic manifest/fingerprint behavior.
4. Readiness diagnostics must distinguish measurable insufficiency from
   unknown provenance and confirmed policy blockers; no unavailable metric may
   be guessed.
5. Runtime agent authorization is not verified. No credential is available in
   the current process and no grant may be created automatically.

## Required Approval Decision

Human approval is required for this Repository Analysis before producing an
Implementation Plan or changing production code. The material decisions are
the additive scope contract, permission separation and focused export lifecycle
boundary.

```text
Story 0222                 CURRENT
Repository Analysis        COMPLETED
Repository Analysis Gate   HUMAN APPROVAL REQUIRED
Implementation Plan         NOT STARTED
Production Implementation   NOT AUTHORIZED
```
