# ADR-092 - Proposed MCP Research Operational Control Boundary

## Status

Accepted - human architecture approval recorded

## Date

2026-11-11

## Decision Type

MCP operational control, research delegation and bounded offline export

## Related ADRs

- ADR-020 - Market Intelligence Architecture
- ADR-021 - Governance of Market Intelligence Analysis Executions
- ADR-023 - Capability Execution Model
- ADR-088 - MCP-First Agent Interaction Boundary
- ADR-089 - Native Trading Research Agent and MCP Responsibility Boundary
- ADR-090 - Agent Identity, Permissions and Delegated Authorization

## Context

OpenCode has two roles in Trading OS:

1. A development and maintenance agent.
2. An operational control interface for authorized Trading OS and cTrader MCP
   capabilities.

In the operational role, OpenCode may use permitted trading capabilities,
subject to the existing broker, risk, authorization and human-confirmation
controls. The Research Agent is not a mandatory intermediary for ordinary
operational MCP capabilities such as authorized market, account or cTrader
operations.

Scientific research follows a distinct path:

```text
User -> OpenCode -> MCP -> Java-controlled Research Agent role -> research services
```

The Research Agent in this path is a Trading OS responsibility, not a second
MCP server or an OpenCode-owned workflow. ADR-089 establishes the Java-
controlled research boundary. In the current repository, that role is
implemented by the Market Intelligence research application and lifecycle
services, notably `ResearchApplicationService`, `ResearchExecution`,
`ResearchReport`, readiness services and historical export services. The
MCP adapter maps to their public Gateway contracts.

The current implementation also contains the existing `AiEnginePort` and its
enabled/disabled HTTP adapters. When AI interpretation is enabled for a
supported research flow, Java invokes it through the existing capability and
port boundary. The Research Agent must not call the Python AI Engine directly,
and the MCP adapter must not expose the AI Engine as a direct capability.

## Decision Proposed

MCP SHALL expose research operations only as bounded commands and reads over
public Gateway contracts. Market Intelligence Java remains authoritative for
validation, authorization, lifecycle, provenance, persistence and delegation
to existing research capabilities.

The Research Agent is a logical role within this existing Java-controlled
research architecture. This ADR does not create a new export subsystem,
orchestration service, MCP layer, AI service or persistence boundary.

The operational capability families are:

1. Research lifecycle:
   - `start_market_research` creates an idempotent, actor-scoped research
     execution.
   - `get_research_status` reads lifecycle state and bounded diagnostics.
   - `get_research_report` reads the persisted advisory result.
2. Historical readiness:
   - `inspect_historical_research_readiness` performs a bounded deterministic
     readiness inspection and never starts a scan or export.
3. Historical export:
   - `request_historical_dataset_export` creates a bounded export request using
     the existing Java historical research/export services.
   - `get_historical_dataset_export_status` reads export lifecycle and artifact
     metadata; it never returns unrestricted filesystem paths or dataset rows.

The MCP adapter SHALL:

- validate input shape and enforce protocol-level size and timeout limits;
- propagate authenticated agent identity without persisting bearer tokens;
- preserve operation, scope, dataset and provenance identifiers;
- return bounded status and error projections;
- treat external text and model output as untrusted data;
- avoid retries for unsafe or ambiguous write-like commands;
- expose no database, filesystem, broker, provider or AI Engine endpoint;
- remain a thin protocol adapter rather than implementing research logic.

Market Intelligence SHALL:

- validate the exact `AgentGrant`, permission, resource and research scope;
- bind idempotency to the authenticated actor and request fingerprint;
- validate population versions, market identities, feature requirements and
  strict-observed evidence policy;
- enforce row, byte, duration, concurrency and expiration limits;
- persist lifecycle state before scheduling work;
- distinguish rejected, failed and ambiguous outcomes;
- make source provenance and historical limitations visible to clients;
- delegate scientific work through existing Java-controlled capabilities and
  ports rather than client-controlled provider calls.

An MCP client MAY propose a population and objective. It MUST NOT grant itself
access by naming a scope, market, account, dataset or export location. Agent
authorization is established only by the server-side `AgentGrant` and the
validated research scope.

## Capability Status

The following distinctions are part of this proposed decision:

### Already Implemented

- MCP stdio transport and public Gateway client boundary.
- Agent authentication and grant-scoped research/export contracts.
- Research start, status and report retrieval.
- Java `ResearchExecution` lifecycle and persisted report projection.
- Historical readiness inspection and bounded export request/status flow.
- Java `AiEnginePort` with enabled and disabled adapters for supported AI
  interpretation paths.

### Partially Implemented or Limited

- Research Agent scientific delegation exists for the current bounded research
  workflow, but ML-01-specific dataset population, future-outcome construction,
  KMeans execution and diagnostics are not yet implemented.
- AI interpretation is optional and disabled by default; it is not a required
  authority for deterministic research or offline dataset validation.
- Export completion provides an artifact and manifest boundary, but it does not
  by itself prove scientific readiness or validity.

### Approved Architectural Intent

- ADR-089's Java-controlled Research Agent responsibility boundary.
- ADR-090's agent identity, grant and delegated-authorization model.
- Bounded, auditable research commands over the public Gateway boundary.

### Future Implementation Requirements

- The incremental ML-01 research workflow described by ADR-093.
- Any additional grant, quota, lifecycle or artifact fields required by the
  approved ML-01 Story.
- Explicit operational approval before production exports or new controlled
  commands are enabled.

## Explicit Non-Authority

Neither MCP nor the Research Agent may:

- become a mandatory intermediary for ordinary operational MCP capabilities;
- select or mutate broker accounts outside existing authorized capabilities;
- authorize a TradePlan, risk decision, execution intent or order;
- call the Python AI Engine directly;
- alter canonical Market Thesis, FeatureSnapshot or Outcome records;
- publish a dataset as scientifically valid merely because export succeeded;
- infer that an insufficient or unavailable historical population is usable;
- expose raw credentials, unrestricted prompt context or local filesystem data.

## Lifecycle and Failure Semantics

Every command that starts work is asynchronous and returns an operation identity.
The server must durably record the request before dispatching work. Repeating
the same idempotency key with the same fingerprint returns the existing
operation. Reusing it with changed scope, population, objective or limits is an
idempotency conflict.

The following distinctions are mandatory:

- `REJECTED`: validation or authorization failed before work was accepted.
- `FAILED`: the operation reached execution and produced a definitive failure.
- `AMBIGUOUS`: the commit or external boundary result is unknown and requires
  reconciliation; it must not be retried blindly.
- `COMPLETED`: the requested operation and its authoritative artifact metadata
  are durable.
- `EXPIRED` or `CANCELLED`: no further work may be scheduled for the operation.

Export completion is not equivalent to research validity. The export must carry
the exact population, manifest fingerprint, feature projection version,
outcome-definition version and readiness result used to produce it.

## Consequences

Positive:

- OpenCode can retain its operational control role without forcing research
  through the Research Agent.
- Scientific research has one auditable Java authority and one MCP path.
- Existing research, AI-port and export infrastructure is reused.
- Offline exports remain bounded and reproducible without exposing storage.
- Clients can distinguish framework availability from scientific data readiness.

Costs and risks:

- ML-01 must extend existing contracts without confusing export completion with
  scientific success.
- Permissions and quotas must be maintained for each controlled capability.
- AI output remains optional, advisory and bounded by Java authority.
- Human review remains necessary before changing grants or enabling new writes.

## Alternatives Rejected

### OpenCode-Owned Research Orchestration

Rejected because lifecycle, authorization, retries and provenance would depend on
the client session and would be duplicated by future clients.

### Direct Python or Filesystem MCP Access

Rejected because it bypasses Gateway authentication, Java authorization,
dataset provenance and bounded resource controls.

### Research Agent as a Universal MCP Intermediary

Rejected because ordinary operational MCP capabilities must remain directly
available to authorized OpenCode control flows under their existing controls.

### New Research or Export Subsystem

Deferred and not justified by the current evidence. Existing Java research,
AI-port, readiness, lifecycle and export services are the implementation
boundary for ML-01 unless a later approved decision proves them insufficient.

## Implementation Boundary

This ADR does not authorize new permissions, production exports, database
changes, model training or broker operations. Any implementation must be
approved by a Story and must preserve ADR-088, ADR-089 and ADR-090. Acceptance
of this ADR would authorize the contract and governance direction only;
separate operational grants and human approval remain required for controlled
commands.

## Open Questions

- Which agent grants and research scopes are approved for ML-01?
- What are the production row, byte, duration and concurrency limits?
- Which existing artifact store and retention policy are authoritative for the
  ML-01 export?
- Which operations require a fresh human approval challenge rather than an
  existing grant?
