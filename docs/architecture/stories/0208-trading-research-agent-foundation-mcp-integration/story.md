# Story 0208 - Trading Research Agent Foundation & MCP Integration

## Metadata

**ID:** `0208`
**Title:** Trading Research Agent Foundation & MCP Integration
**Status:** Implementation complete - review pending
**Priority:** P1 - Trading Intelligence
**Related ADRs:** `ADR-020`, `ADR-021`, `ADR-028`, `ADR-034`, `ADR-076`, `ADR-088`, `ADR-089`
**Predecessor Stories:** `0205`, `0206`, `0207`

## Mission

Implement the first Trading OS-owned specialized research workflow. The bounded
use case compares two explicitly selected canonical markets, such as BTC and
ETH, using existing deterministic Market Intelligence evidence and produces a
structured, auditable research report.

OpenCode remains an interaction and control client. Trading OS owns research
authorization, context assembly, orchestration, lifecycle, persistence and
retrieval. The Python AI Engine provides optional specialized interpretation
behind the existing Java `AiEnginePort` boundary.

## Scope

### Research Workflow

Implement an authenticated application workflow that:

1. accepts an explicit objective, bounded market IDs and research depth;
2. validates canonical market identity, provider and product type;
3. assembles existing deterministic Market Thesis, observations and approved
   opportunity/context projections;
4. preserves freshness, provenance, contradictions and missing evidence;
5. invokes the AI Engine only through the existing `AiEnginePort` when enabled;
6. validates a structured advisory result;
7. persists a durable research lifecycle and report/reference;
8. exposes status and report retrieval through public Gateway contracts;
9. exposes the approved application capability through the local MCP adapter.

The first workflow compares two selected markets. It must not infer markets from
ambiguous symbols or silently select an account.

### Proposed Capability Shape

Verify and finalize the public contract during implementation; the conceptual
operations are:

```text
start_market_research(market_ids, objective, depth)
get_research_status(research_id)
get_research_report(research_id)
```

The contract must be client-independent. MCP maps to it and never calls the AI
Engine directly. REST controllers must follow repository conventions and return
`ResponseEntity`.

### Research Report Semantics

The report must distinguish:

- authoritative market facts;
- deterministic analysis and Market Thesis evidence;
- AI-generated interpretation;
- hypotheses;
- supporting and contradicting evidence;
- stale, unavailable and missing information;
- unverified assumptions;
- invalidation conditions;
- limitations and model provenance;
- research conclusions.

An AI score is not a calibrated probability without calibration evidence. A
research conclusion is not a TradePlan, Risk verdict, execution authorization or
profitability guarantee.

### Lifecycle and Persistence

Reuse `AnalysisExecution` for technical analysis child work where semantics fit.
Introduce a small Java-owned `ResearchExecution`/`ResearchReport` model only if
the repository confirms that existing aggregates cannot represent a durable
multi-market research request and report.

The lifecycle must define:

- actor and account ownership where account context is used;
- request fingerprint and actor-scoped idempotency;
- bounded market count, research depth, context size, AI request count and
  duration;
- `REQUESTED`, `RUNNING`, `PARTIALLY_COMPLETED`, `COMPLETED`, `FAILED`,
  `CANCELLED` and `EXPIRED` behavior as applicable;
- timestamps, failure diagnostics and result references;
- bounded cooperative cancellation;
- retention without duplicating large raw market data, prompts or model payloads.

### MCP Integration

Preserve the four existing direct tools from Stories 0206-0207:

- `list_markets`;
- `get_active_scan`;
- `get_market_thesis`;
- `get_opportunities`.

Add only the approved research lifecycle tools. Keep MCP local stdio and Gateway
based. Do not expose internal AI Engine routes, repositories, databases,
providers, broker credentials or execution operations.

## Architecture Boundaries

### Market Intelligence Java

Owns user authorization, market scope, deterministic evidence assembly, research
orchestration, child executions, AI context classification, persistence,
idempotency, budgets, report provenance and public application contracts.

### AI Engine Python

Owns specialized reasoning, bounded hypothesis generation, scenario comparison,
missing/contradicting evidence identification and structured advisory output.
It remains disabled by default where the existing configuration requires it and
must not mutate canonical Trading OS state.

### MCP Adapter

Owns only protocol mapping, typed schemas, bounded inputs/outputs, authentication
propagation and sanitized errors. It contains no prompts, research algorithms,
workflow state or persistence.

### Trading Core, Risk and Broker Service

Remain unchanged and authoritative for account rules, financial risk, trade plans,
execution intents, credentials, orders, positions and reconciliation.

## Explicit Exclusions

- autonomous trading or order placement;
- Risk overrides or TradePlan approval;
- broker credential management or provider calls;
- external cTrader MCP integration;
- remote MCP HTTP/OAuth deployment;
- custom Angular chat interface;
- LangGraph, multi-agent orchestration or generic agent platform;
- full strategy discovery or backtesting;
- automatic StrategyDefinition creation, validation or promotion;
- independent Python market-data pipeline;
- direct MCP-to-AI Engine calls;
- browser credential extraction or refresh-token persistence.

## Acceptance Criteria

- A bounded authenticated research request accepts explicit canonical market IDs
  and rejects invalid, ambiguous or over-limit scope.
- User/account ownership and actor-scoped idempotency are enforced by the Java
  application boundary.
- Existing deterministic Market Intelligence evidence is reused without
  duplicating market-data normalization, thesis composition, opportunity scoring
  or scan orchestration.
- Research lifecycle state, diagnostics and report references are durable and
  retrievable independently of the initiating client session.
- The report preserves deterministic evidence, AI interpretation, hypotheses,
  contradictions, missing/stale data, invalidation conditions, limitations and
  provenance as separate concepts.
- AI-disabled, AI-timeout, malformed-output and partial-result behavior is
  explicit and does not interrupt deterministic authority.
- AI output cannot mutate Market Thesis, Observation, Opportunity, Strategy,
  TradePlan, Risk or Execution state.
- Public REST contracts are routed through Gateway and enforce authentication and
  ownership; no internal AI Engine route is public.
- MCP exposes only the approved research capabilities in addition to the four
  existing direct tools.
- No MCP capability can call broker, provider, repository, database, Risk or
  execution operations.
- Automated tests cover validation, ownership, idempotency, lifecycle, evidence
  assembly, missing/contradictory evidence, AI disabled/failure/partial output,
  report retrieval and MCP mappings.
- Automated tests use offline fixtures and no real broker, provider or LLM.
- The acceptance scenario “Analyze BTC and ETH...” is documented with truthful
  live-validation status and no profitability or authorization claim.

## Required Documentation

- implementation plan;
- implementation report;
- code review report;
- MCP adapter README and capability mapping;
- public API and security documentation where contracts change;
- ADR-089/ADR-088 reconciliation if the controlled research boundary changes.

## Human Decisions Still Required

- approval of this Story and implementation plan;
- confirmation of the final `ResearchExecution`/`ResearchReport` persistence
  shape;
- approval of the controlled research-command authorization boundary;
- explicit environment, JWT, account and budget approval for any live test;
- separate approval before enabling a real LLM provider or spending external
  provider quota.

## Delivery Constraint

Do not commit, push or merge without explicit authorization.
