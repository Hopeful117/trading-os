# Implementation Plan - Story 0222

## Status

Implementation plan completed - human approval required before implementation.

The plan is based on the approved Repository Analysis. It does not authorize
production code changes until the human explicitly approves this plan.

## Objective

Add the smallest end-to-end, Gateway-backed agent capability for scoped
historical readiness inspection and bounded dataset export while binding
research scopes to an owner-owned broker account. The account's provider is the
universe identity and its catalogue markets are resolved server-side. Preserve
the deterministic dataset exporter, agent identity model and trading isolation.

## Approved Scope Boundary

### In scope

- New default-deny `research:inspect` and `research:export` permissions.
- Additive historical constraints on the existing owner-owned `ResearchScope`.
- Trading Core authorization methods that evaluate the exact permission and
  scope constraints using the validated agent credential/version.
- Market Intelligence request-scoped readiness diagnostics.
- Market Intelligence export job persistence, bounded execution and status
  retrieval.
- Reuse of `ResearchDatasetExportService`, `ResearchPopulationSpec`, manifest,
  fingerprint and existing bundle exporter.
- Gateway route coverage for the existing intelligence wildcard route.
- MCP tools for readiness, export request and export status.
- Focused security, idempotency, lifecycle and regression tests.
- Story implementation report and operator runbook/documentation outcome.

### Out of scope

- Historical reconstruction, provider backfill or provenance repair.
- Changes to immutable `FeatureSnapshot` or outcome facts.
- New authentication, authorization server, generic policy language, generic
  job/workflow engine or generic approval engine.
- Python subprocess execution from HTTP requests.
- Broker synchronization, orders, positions, Risk or execution.
- Full dataset transfer through MCP or arbitrary filesystem paths.
- New artifact storage platform.

## Design Decisions

### 1. Broker-account universe and scope compatibility

Use the existing broker account provider registration as the universe identity.
Add `brokerAccountId` to `ResearchScope`; scope creation resolves all current
catalogue markets belonging to that provider through Market Data. The request
contains no market list and the frontend only selects the broker account.

Existing legacy scopes are retained and remain readable during migration. New
scope creation requires a broker account and no longer imposes an exactly-two
market limit.

The persisted constraint contains only bounded scalar/list values needed by this
Story:

- optional earliest and latest permitted evaluation cutoff;
- maximum requested observations;
- maximum export rows and bytes;
- maximum export duration seconds;
- allowed dataset profile/version values.

Existing scopes remain valid with no historical constraint. New
`research:run` and historical authorization requests validate requested markets
against the scope's selected subset and the scope's broker-account ownership.

The human-facing scope creation contract keeps historical constraints optional.
The operator must explicitly provide the broker account; no defaults widen
existing grants.

### 2. Permission authorization

Add `RESEARCH_INSPECT("research:inspect")` and
`RESEARCH_EXPORT("research:export")` to `AgentPermission`. Map both only to
`RESEARCH_SCOPE` in `AgentGrantCompatibility`.

Add a capability-aware authorization method to Trading Core rather than
duplicating checks in Market Intelligence. It must verify:

- active agent and owner;
- active credential ID and exact credential version;
- exact scope ownership and active status;
- effective grant for the requested permission/resource;
- requested markets contained in the scope;
- interval/cutoff/profile/limit constraints;
- correlation ID and authorization audit event.

Return the existing decision/provenance shape extended only as necessary for
the accepted grant and scope versions. Do not expose denial reasons that reveal
another owner's resource existence.

### 3. Readiness contract

Add a Market Intelligence application service and DTOs for a request-scoped
readiness calculation. The service will reuse existing repositories and
deterministic readiness/eligibility logic but will not call the global cached
readiness endpoint as an authorization substitute.

The request will be bounded by:

- scope ID;
- one or both markets from the authorized scope, while preserving the scope's
  two-market definition;
- historical interval/profile;
- cutoff start/end;
- requested observation limit.

The response will include canonical market IDs, effective bounds, evidence and
eligibility counts, earliest/latest eligible cutoff, coverage/gap information
only where determinable, provenance/freshness/cutoff status, feature projection
and outcome status, blockers, diagnostic timestamp and correlation ID.

Use a small verdict enum:

- `READY`: declared export prerequisites are satisfied;
- `INSUFFICIENT`: measurable population or coverage requirements are unmet;
- `BLOCKED`: confirmed integrity/policy prerequisite prevents export;
- `UNKNOWN`: required evidence cannot be established reliably.

`createdAt` must not be used as a standalone look-ahead decision. Strict
eligibility remains based on existing `knownAt` semantics. Unknown provenance
remains unknown or blocked according to the checked dataset policy; it is never
converted into eligible evidence.

### 4. Export lifecycle

Introduce a focused Market Intelligence `HistoricalDatasetExportJob` lifecycle,
not a generic job framework. The persisted states are:

```text
PENDING -> RUNNING -> SUCCEEDED
                    -> FAILED
                    -> EXPIRED
```

`CANCELLED` is not implemented unless the existing executor boundary supports
actual cancellation; a request must never claim cancellation without stopping
the work.

The job record stores:

- job ID, owner, agent, scope, grant and credential versions;
- canonical request fingerprint and idempotency key;
- dataset profile and cutoff/population request;
- created/started/completed/expiry timestamps;
- status and bounded failure category;
- artifact server-side identifier, checksum, byte/row counts and validation
  summary;
- correlation ID.

Submission performs authorization and conflict-safe idempotency persistence.
Execution atomically claims a pending job, revalidates current authorization,
enforces limits and invokes the existing exporter with a server-controlled
output directory. Retrieval revalidates authorization and returns metadata only.

If grant revocation or expiry is detected before claim, the job becomes
`EXPIRED` or `FAILED` with a deterministic authorization outcome and does not
start. Revocation during an already-running export does not falsely claim
cancellation; the final lifecycle records the actual bounded execution result,
and subsequent retrieval is denied if policy requires current authorization.

Idempotency uniqueness is scoped by owner, agent and idempotency key. The
canonical request fingerprint distinguishes same-key/same-request replay from
same-key/conflicting-request failure. Concurrent submissions rely on a unique
database constraint and return the existing job for an identical request.

### 5. Artifact boundary

Use a configured server-controlled export root owned by Market Intelligence.
Generate the job directory and filenames from the server-side job UUID. Reject
path input from the agent. The public response includes an opaque artifact ID,
checksum, size, profile/version, validation status and lifecycle timestamps;
never an absolute path.

The existing bundle exporter remains the canonical writer and fingerprint
producer. Java export validation is reported separately from Python validator
execution, which remains an explicit offline operation.

## Planned File and Module Changes

### Trading Core

- `agent/model/AgentPermission.java`: add inspection/export values.
- `agent/application/AgentGrantCompatibility.java`: map new permissions only
  to `RESEARCH_SCOPE`.
- `agent/model/ResearchScope.java`: add additive historical constraint state
  and server-side validation/accessors.
- `agent/api/ResearchScopeRequest.java` and response: optional constrained
  historical specification.
- `agent/application/AgentAuthorizationService.java`: add permission-aware
  historical authorization and provenance decision data.
- `agent/application/ResearchScopeService.java`: persist and validate the
  optional specification without changing existing two-market behavior.
- `src/main/resources/db/migration/common/V60__historical_research_scope_constraints.sql`:
  additive nullable columns/tables as required by the final mapping.
- focused model, grant, authorization, controller and migration tests.

### Market Intelligence

- new historical readiness request/response/verdict DTOs;
- new scoped readiness application service reusing existing repositories and
  deterministic eligibility logic;
- new export job entity/status/repository/application service and DTOs;
- new additive migration after `V23` for export jobs and artifact metadata;
- new public controller endpoints for scoped readiness, export submission and
  export status;
- configuration for safe export root, limits and bounded executor behavior;
- tests for temporal policy, verdicts, idempotency, lifecycle, revalidation,
  artifact metadata and failure handling.

### Gateway

- verify the existing `/api/v1/intelligence/**` route covers the new paths;
- add route/downstream tests only if path matching or header behavior requires
  coverage;
- do not add a parallel route or authentication mechanism.

### MCP Adapter

- `app/server.py`: add typed `inspect_historical_research_readiness`,
  `request_historical_dataset_export` and
  `get_historical_dataset_export_status` tools.
- `app/client.py`: reuse existing GET/POST Gateway methods, idempotency and
  bounded response handling; add no authorization logic.
- tests for schemas, Gateway paths, error mapping, limits and response shape.

### Documentation

- `mcp-adapter/README.md`: document new bounded Gateway-backed tools and
  credential requirements.
- relevant Market Intelligence API/operator documentation only if a canonical
  document exists for these contracts.
- Story `implementation-report.md` with documentation outcome, validation and
  runtime status.

## API Contracts

Use `ResponseEntity` and structured domain errors. Proposed endpoints:

```http
POST /api/v1/intelligence/research/scopes/{scopeId}/readiness
POST /api/v1/intelligence/research/scopes/{scopeId}/exports
GET  /api/v1/intelligence/research/exports/{exportId}
```

All requests carry a correlation ID through the established request context or
header convention. Export submission requires an idempotency key. Status and
artifact responses are owner/agent scoped and do not reveal unauthorized job
existence.

MCP responses are JSON strings produced by the Gateway client, bounded by the
existing response-byte limit. MCP never returns dataset rows or absolute paths.

## Test Plan

### Trading Core

- permission JSON conversion and grant compatibility;
- optional constraint persistence and backward-compatible scope creation;
- exact owner/resource/permission checks;
- market, interval, cutoff, profile and limit rejection;
- expired/revoked scope/grant and credential-version mismatch;
- existing `research:run` two-market regression.

### Market Intelligence

- all four readiness verdicts;
- missing provenance, post-cutoff evidence and incomplete outcomes;
- deterministic repeated diagnostics;
- scope-bound market and interval validation;
- export request authorization and request fingerprinting;
- same-key replay, conflicting key and concurrent duplicate submission;
- pending claim, pre-execution revalidation, success, failure and expiry;
- artifact checksum/metadata and safe output root;
- unauthorized status retrieval and bounded failure projection.

### Gateway and MCP

- existing intelligence route matching for new paths;
- agent/human principal separation and missing/invalid credential behavior;
- MCP typed arguments, bounded values, Gateway-only paths, POST no-retry,
  sanitized errors and no secret/path leakage.

### Existing regression suites

- relevant Trading Core tests;
- relevant Market Intelligence research/readiness/export tests;
- Gateway route/security tests;
- MCP adapter tests;
- `research/ml` tests to confirm the offline contract remains unchanged.

## Validation Commands

Run after implementation:

```bash
./scripts/test-all.sh
./scripts/quality-verify.sh
git diff --check
```

Also run targeted module commands where failures need isolation:

```bash
cd trading-core && ./mvnw -q -Dtest='*Agent*Test,*ResearchScope*Test' test
cd market-intelligence && ../trading-core/mvnw -q -Dtest='*Research*Test,*Dataset*Test' test
cd gateway && ../trading-core/mvnw -q -Dtest='*Gateway*RouteTest,*Security*Test' test
cd mcp-adapter && .venv/bin/pytest
cd research/ml && .venv/bin/pytest
```

The exact Maven test selector may be adjusted to the repository's actual test
names before execution. Do not report runtime agent validation unless an
already authorized credential and isolated environment are available.

## Runtime Validation

Attempt only with an existing authorized agent credential and safe isolated
fixtures. Do not create grants, inspect secrets, mutate shared databases or
run provider operations.

Expected status when unavailable:

```text
RUNTIME_AGENT_AUTHORIZATION_NOT_VERIFIED
```

## Stop Conditions

- Scope constraints require breaking exactly-two-market `research:run` behavior.
- Existing dataset exporter cannot be safely invoked under a server-controlled
  output root without changing canonical dataset semantics.
- A required API requires a new authentication or generic workflow framework.
- Readiness metrics would require inventing cadence, provenance or population
  facts not present in existing evidence.
- Export authorization cannot be revalidated before asynchronous execution.
- Tests reveal temporal eligibility or immutable identity must be weakened.
- Runtime validation requires credentials or shared-state mutation not already
  authorized.

## Documentation Reconciliation

Update only the MCP README and canonical API/operator documentation if actual
contracts change. Do not update ADRs unless implementation exposes a genuine
architectural conflict with accepted decisions. Record the final decision and
any documentation update in `implementation-report.md`.

The Story implementation report must include a `Vault Outcome` section. The
Obsidian vault was not consulted during Repository Analysis, so the default
outcome is expected to be `no vault action` unless implementation evidence
creates a clear proposal-only transverse-memory candidate.

## Approval Gate

Implementation must not begin until the human explicitly approves this
Implementation Plan.
