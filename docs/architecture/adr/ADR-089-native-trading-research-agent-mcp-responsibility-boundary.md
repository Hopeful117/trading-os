# ADR-089 - Native Trading Research Agent & MCP Responsibility Boundary

## Status

Accepted - human architecture approval recorded

## Date

2026-11-05

## Decision Type

Native research orchestration, specialized AI reasoning and MCP capability
boundary

## Related ADRs

- ADR-020 - Market Intelligence Architecture
- ADR-021 - Governance of Market Intelligence Analysis Executions
- ADR-028 - Deterministic Risk Engine Architecture
- ADR-030 - Broker Architecture
- ADR-034 - Strategy, StrategyMatch and Trading Opportunity Boundaries
- ADR-076 - Market Thesis Authority and Evidence Composition
- ADR-088 - MCP-First Agent Interaction Boundary

## Context

Stories 0205-0207 established three adjacent foundations:

- the AI Engine is a standalone Python/FastAPI service behind the Java
  `AiEnginePort`, disabled by default and advisory only;
- the MCP adapter is a local stdio anti-corruption layer over public Gateway
  contracts;
- the adapter can read canonical markets, Market Thesis, opportunities and
  existing actor-owned active-scan projections, but cannot start scans or invoke
  the AI Engine directly.

OpenCode is useful as an interaction and control client, but it must not become
the durable owner of market research orchestration, evidence lineage, research
state or specialized trading interpretation. Conversely, Trading OS must not
turn the MCP adapter into a second business service or embed prompts and market
analysis algorithms in the protocol boundary.

The proposed capability is a bounded Trading Research Agent that compares
explicitly selected canonical markets, consumes deterministic evidence, forms
competing hypotheses, identifies contradictions and missing information, and
returns an auditable structured research report. It is not an autonomous trader,
a strategy promotion mechanism, a risk engine or a broker interface.

## Decision

Trading OS SHALL own specialized trading research as a Java-controlled Market
Intelligence workflow. The first implementation SHALL use the existing Market
Intelligence orchestration and persistence boundaries and the existing Python AI
Engine as a specialized reasoning provider.

The responsibility chain is:

```text
OpenCode / Angular / future client
            |
            | public authenticated MCP or REST request
            v
MCP adapter / public Gateway boundary
            |
            v
Market Intelligence research application boundary
            |
            +--> canonical Market Data and deterministic evidence
            +--> Market Thesis and opportunity projections
            +--> AnalysisExecution child work where appropriate
            +--> AiEnginePort for bounded specialized reasoning
            v
Persisted ResearchExecution / ResearchReport
            |
            v
MCP or REST retrieval
```

The first research workflow SHALL compare two or more explicitly selected
canonical market IDs, preserve provider/product identity, and produce structured
facts, hypotheses, supporting evidence, contradicting evidence, missingness,
freshness, invalidation conditions, limitations and provenance. It SHALL NOT
authorize a TradePlan, Risk decision, order, position change or execution intent.

## Responsibility Boundaries

### External Clients

OpenCode, Angular and future clients own:

- user interaction and conversation;
- selection of a research objective and explicit market scope;
- presentation and exploration of results;
- follow-up questions;
- human approval and confirmation flows.

They MAY call approved direct application capabilities and specialized research
capabilities. They MUST NOT own durable research orchestration, canonical
evidence composition, strategy validation or trading authorization.

### Trading OS MCP Adapter

The adapter owns:

- stable MCP tool exposure;
- mapping to approved public Gateway contracts;
- typed input and bounded output limits;
- protocol-level error handling;
- authentication propagation without token persistence;
- preservation of source identifiers, provenance, freshness and missingness.

The adapter MUST NOT contain:

- trading business logic;
- market-analysis algorithms;
- LLM prompts or provider clients;
- research orchestration or persistence;
- direct access to the AI Engine, repositories, databases, brokers or providers.

### Market Intelligence - Java

Market Intelligence owns:

- research request authorization and ownership;
- canonical market scope validation;
- deterministic context assembly;
- Market Thesis and opportunity authority;
- child `AnalysisExecution` coordination where applicable;
- research lifecycle, idempotency, timeouts and cancellation policy;
- AI context classification and authorized transmission;
- report persistence, provenance and retrieval;
- separation between deterministic evidence and AI-generated proposals.

### AI Engine - Python

The AI Engine owns specialized reasoning only:

- research planning and bounded hypothesis generation;
- comparison of supplied scenarios;
- identification of contradictions, assumptions and missing evidence;
- structured synthesis over the authorized context;
- model/provider execution behind existing Pydantic contracts.

It MUST NOT access Trading OS services autonomously, mutate canonical state,
promote strategies, authorize risk, create Trade Plans or execute orders. It
receives only the context authorized by Market Intelligence and never receives
the user JWT, broker credentials or unrestricted account data.

### Trading Core, Risk and Broker Service

Trading Core remains authoritative for account-scoped workflows and execution
intent. Risk remains authoritative for deterministic financial authorization.
Broker Service remains authoritative for credentials, provider connectivity,
orders, positions and reconciliation. The Research Agent cannot bypass any of
these authorities.

## Capability Model

MCP SHALL expose two conceptual capability families.

### Direct Application Tools

These map directly to stable application reads or explicitly authorized
commands, for example:

- `list_markets`;
- `get_market_thesis`;
- `get_opportunities`;
- `get_active_scan`;
- future separately authorized scan or account-state operations.

They do not require specialized research reasoning.

### Specialized Research Capabilities

These expose a Trading OS-owned research lifecycle, for example:

- `start_market_research`;
- `get_research_status`;
- `get_research_report`.

The exact names and contracts are implementation concerns for Story 0208. MCP
MUST expose the research application boundary rather than individual internal
agent steps, prompts, provider calls or database operations.

Controlled research commands are an explicit extension of the read-only scope
accepted by ADR-088. This ADR proposes that extension but does not authorize it
until human approval is recorded. ADR-088 remains operationally read-only until
then.

## Research Lifecycle and Persistence

The existing `AnalysisExecution` aggregate is reusable for technical analysis
child work. It already provides asynchronous states, idempotency, policy,
provenance, result quality, cancellation and immutable accepted results.

It is not sufficient as the sole research aggregate because it is a technical
analysis execution scoped around one market/context and its result is
`ConsolidatedIntelligence`. A research request compares multiple markets, owns a
user objective, combines child evidence and produces a distinct report that must
remain retrievable independently of the initiating MCP session.

The first research implementation SHALL therefore introduce a small,
Java-owned `ResearchExecution`/`ResearchReport` bounded model only if the
existing code confirms that no compatible report aggregate exists. It SHALL:

- reference, rather than duplicate, deterministic evidence and child execution
  IDs;
- preserve actor and account ownership when account context is involved;
- use an actor-scoped idempotency key and request fingerprint;
- have explicit `REQUESTED`, `RUNNING`, `PARTIALLY_COMPLETED`, `COMPLETED`,
  `FAILED`, `CANCELLED` and `EXPIRED` semantics as appropriate;
- retain created, updated, completed and expiration timestamps;
- enforce research depth, market count, context size, AI request count and time
  budgets;
- support bounded cancellation without retry loops;
- persist a structured report or immutable report reference without duplicating
  large OHLC, order-book, prompt or raw model payloads.

`ActiveScan` SHALL not be reused as the research aggregate: it is an
account-scoped market scanner with scan-specific child projections. It may be a
source of deterministic evidence or a referenced prerequisite.

## Agent Placement

The selected placement is option C: Java-controlled workflow with specialized
reasoning in the existing Python AI Engine.

Java owns durable orchestration, authorization, persistence, idempotency,
timeouts, context classification and public contracts. Python owns adaptive
interpretation and validated structured output. The MCP adapter consumes the Java
application contract and never calls Python directly.

No separate Python research service is justified for the first increment. A
separate service may be reconsidered only if independent scaling, security,
deployment or ownership requirements become concrete.

## Research Output Contract

The first report contract SHOULD be an explicit application projection with
fields equivalent to:

```text
ResearchReport
  researchId
  objective
  marketScope[]
    marketId
    provider
    productType
    symbol
  generatedAt
  sourceCutoff
  sourceFreshness
  deterministicEvidence[]
  hypotheses[]
  supportingEvidence[]
  contradictingEvidence[]
  missingEvidence[]
  invalidationConditions[]
  researchConclusions[]
  limitations[]
  modelProvenance
```

This is a candidate contract, not permission to create a second Market Thesis
model. Existing Market Thesis, observations, opportunities and AI result
contracts must be reused where semantically compatible. The report MUST
distinguish market facts, deterministic analysis, AI interpretation, hypotheses,
unverified assumptions and missing information. An AI score MUST NOT be called a
calibrated probability without calibration evidence.

Research conclusions are advisory and do not authorize trading.

## Strategy Research Boundary

The Research Agent MAY propose candidate strategy hypotheses, experiments and
questions. It MUST NOT create or promote an authoritative StrategyDefinition,
StrategyMatch or enabled strategy merely from a plausible explanation.

Strategy evaluation and backtesting remain deterministic, reproducible services.
Any promotion requires the explicit validation workflow and human approvals
defined by ADR-034. Full strategy discovery and backtesting are deferred.

## Security and Trust Model

Research is not read-only: it consumes compute, persistence and potentially
external LLM quota. Every research command therefore requires:

- authenticated user identity;
- account ownership checks when account context is requested;
- bounded market scope and research depth;
- actor-scoped idempotency;
- concurrency and rate limits;
- execution and context timeouts;
- cancellation semantics;
- auditable request, child execution and report provenance;
- sensitive-data minimization and classification;
- prompt-injection resistance by treating external market text as untrusted data;
- explicit separation of deterministic evidence and AI conclusions.

MCP is not an authorization bypass. The Gateway and Java application boundary
remain authoritative. Internal endpoints, service credentials, JWT forwarding to
the AI Engine, broker credentials, provider APIs and database access remain
forbidden from MCP and the Research Agent.

## Sequence

```text
User
  |
  v
OpenCode / MCP client
  |  start_market_research(marketIds, objective, depth)
  v
MCP adapter -- public authenticated Gateway --> Market Intelligence
                                                  |
                                                  +-- validate actor, scope,
                                                  |   idempotency and budgets
                                                  +-- assemble deterministic
                                                  |   Market Thesis/evidence
                                                  +-- optional AnalysisExecution
                                                  +-- AiEnginePort --> AI Engine
                                                  |                    |
                                                  |                    +-- validate
                                                  |                        structured
                                                  |                        advisory result
                                                  +-- persist ResearchReport
  ^
  |  get_research_status / get_research_report
  +------------------------------------------------
```

## Alternatives

### OpenCode as Primary Research Orchestrator

Rejected. It would make lifecycle, evidence assembly, retries, persistence and
security dependent on a client session, and would duplicate domain logic across
clients.

### Direct MCP Tools Only

Rejected as the long-term model. Direct tools remain valuable primitives, but
they cannot own multi-market research lifecycle, structured synthesis or durable
report provenance.

### Trading OS-Owned Specialized Agent with MCP Pilot

Selected. Trading OS owns the research contract and lifecycle while MCP remains
an interaction surface. The first implementation is a single specialized agent,
not a generic agent platform.

### Full Multi-Agent Architecture

Deferred. It would introduce unnecessary orchestration, routing, observability
and governance complexity before one bounded research workflow is validated.

## Consequences

Positive:

- research remains available independently of OpenCode session state;
- deterministic evidence and AI interpretation retain separate authority;
- Java owns durable lifecycle and security boundaries;
- Python reasoning can evolve without moving business persistence;
- MCP exposes stable application capabilities rather than implementation steps;
- future Angular and other clients can reuse the same research contract.

Costs and risks:

- a research lifecycle/report model adds persistence and contract complexity;
- controlled MCP commands require authorization, quotas and auditability;
- AI output remains non-authoritative and requires explicit provenance handling;
- context budgets and prompt-injection defenses need continuous maintenance.

## Migration Strategy

1. Preserve the four existing direct MCP tools and ADR-088 read-only behavior.
2. Define the Java research application contract and ownership model.
3. Add the smallest durable research execution/report boundary, reusing
   `AnalysisExecution` for child technical work where appropriate.
4. Add a bounded deterministic two-market research workflow with AI disabled
   support and offline fixtures.
5. Add the AI Engine research interpretation contract behind `AiEnginePort`.
6. Expose only the approved research lifecycle through MCP and public REST.
7. Validate one end-to-end local workflow with explicit credentials and budgets;
   do not enable autonomous trading or strategy promotion.

## Deferred Decisions

- final `ResearchExecution` versus `ResearchJob` naming and physical schema;
- whether report content is stored inline or as versioned artifacts;
- AI provider/model selection and production enablement;
- remote MCP transport, OAuth and resource-server authorization;
- research quotas, commercial cost budgets and tenant policy;
- strategy research and backtesting integration;
- multi-agent decomposition;
- long-term report retention and search/indexing.

## Approval Record

Human architectural approval was recorded before creating Story 0208.

This ADR does not by itself authorize implementation of controlled research
commands, remote MCP, autonomous trading, strategy promotion or execution.
Those capabilities remain governed by Story 0208 scope and subsequent approval
gates. ADR-088 remains operationally read-only until an explicit implementation
and security decision extends that boundary.

## Recommendation

Approve the responsibility boundary and the Java-controlled, Python-reasoned
single-agent direction for a bounded two-market research workflow. After approval,
create Story 0208 with exact public contracts, persistence decisions, migration
steps, tests and a separately approved controlled-command boundary.
