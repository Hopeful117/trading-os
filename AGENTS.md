# AGENTS.md

# Trading OS

## Mission

Trading OS is an AI-assisted discretionary trading platform.

Its purpose is to help the user understand markets, identify relevant trading opportunities, manage risk, prepare trade decisions, and execute validated orders through supported brokers.

The system should remain useful for normal trading and configurable prop-firm challenges.

It is not an autonomous trading bot.

The human trader retains final authority over trading decisions and execution.

---

## Product Priorities

Engineering work should prioritize:

1. Building an effective trading tool.
2. Completing the end-to-end decision pipeline.
3. Preserving deterministic risk controls.
4. Improving usability for daily trading.
5. Validating behavior with real market and sandbox environments.
6. Introducing AI only where interpretation adds measurable value.

Commercialization and SaaS concerns are secondary until the platform proves useful in real trading.

---

## Current Architecture

Trading OS currently contains:

```text
Gateway
Trading Core
Broker Service
Market Data Service
Market Intelligence
Risk Domain
Eureka Server
Trading OS Web
```

### Gateway

Responsibilities:

* external entry point;
* route forwarding;
* JWT validation;
* service discovery integration;
* market-data WebSocket routing.

### Trading Core

Responsibilities:

* users and authentication;
* trading accounts;
* broker-account references;
* trades and trading statistics;
* dashboard aggregation;
* execution intentions;
* execution attempts and history;
* execution reconciliation;
* deterministic business orchestration.

Trading Core owns user-specific trading workflows.

### Broker Service

Responsibilities:

* broker credentials;
* broker authentication;
* account and position access;
* order placement and cancellation;
* execution reconciliation;
* broker error translation;
* broker-specific infrastructure.

Broker Service must expose broker-neutral contracts.

Kraken-specific payloads, signatures, mappings, transport details, and errors must remain inside the Kraken infrastructure adapter.

### Market Data Service

Responsibilities:

* market catalogue;
* market metadata and constraints;
* market state;
* ticker data;
* OHLC data;
* order books;
* recent trades;
* normalized price snapshots;
* dynamic market subscriptions;
* market-data freshness.

Market Data Service exposes public market information and does not own trading decisions.

### Market Intelligence

Responsibilities:

* analysis executions;
* deterministic analysis capabilities;
* context assembly;
* artifact production and reuse;
* observations;
* opportunity detection and ranking;
* trade-plan generation;
* analysis orchestration.

Market Intelligence interprets market context but must not override deterministic risk decisions.

### Risk Domain

Responsibilities:

* deterministic risk models;
* risk evaluation;
* risk-rule enforcement;
* capital-protection decisions;
* reusable domain logic independent of infrastructure.

The same inputs must produce the same risk result.

### Trading OS Web

Responsibilities:

* user interaction;
* dashboard visualization;
* account management;
* market exploration;
* opportunity and plan presentation;
* human validation workflows.

The Angular frontend should use reactive data flows with Observables and the async pipe when practical.

---

## Core Architectural Principles

### Deterministic Core

The following responsibilities must remain deterministic:

* risk rules;
* financial calculations;
* position sizing;
* validation;
* execution-state transitions;
* authorization;
* persistence decisions;
* market-data normalization;
* order-book reconstruction;
* broker-error classification.

AI must not override these rules.

### AI Assistance

AI may:

* interpret structured market context;
* summarize complex information;
* rank or explain opportunities;
* propose trade plans;
* identify patterns;
* produce recommendations;
* assist with technical and macroeconomic interpretation.

AI outputs are recommendations or proposals.

They are not authoritative business decisions.

### Human Authority

The human trader remains responsible for:

* approving trade plans;
* accepting risk;
* authorizing execution;
* managing open positions;
* approving architectural decisions;
* approving merges and deployments.

---

## Target Decision Pipeline

The intended product workflow is:

```text
Market Data
    ↓
Market Intelligence
    ↓
Observation
    ↓
Opportunity
    ↓
Trade Plan
    ↓
Risk Evaluation
    ↓
Human Validation
    ↓
Execution Intent
    ↓
Trading Core
    ↓
Broker Service
    ↓
Broker
```

Agents must preserve this responsibility chain.

Market Intelligence must not place broker orders directly.

Broker Service must not decide whether a trade is strategically appropriate.

Trading Core remains responsible for deterministic orchestration and execution state.

---

## Broker Rules

Broker integrations must:

* implement broker-neutral ports;
* isolate provider-specific code in infrastructure adapters;
* preserve idempotency;
* distinguish rejected, acknowledged, and unknown outcomes;
* avoid automatic retry of unsafe write operations;
* reconcile ambiguous execution results;
* protect credentials;
* avoid exposing provider payloads through public APIs.

An ambiguous broker response after an order request must never be treated as a definitive rejection.

It must produce an unknown result and trigger reconciliation.

---

## Market Data Rules

Market-data modeling must start from trading-domain requirements, not provider APIs.

Only collect and persist data that supports:

* deterministic analysis;
* risk management;
* AI interpretation;
* opportunity detection;
* execution;
* user-facing visualization.

Broker-specific field names must not leak into the domain model.

A market is considered open only when an order can be executed in real time under acceptable conditions.

---

## Security

Services handling user-specific or sensitive operations must validate JWTs and use Spring Security.

This applies particularly to:

* Trading Core;
* Broker Service;
* Gateway.

Infrastructure services exposing public market data should remain unauthenticated internally unless their responsibilities change.

Agents must never:

* commit secrets;
* expose broker credentials;
* log private keys or API secrets;
* weaken authorization;
* make internal endpoints public without approval;
* replace encrypted credential storage with plaintext storage.

---

## API Conventions

REST controllers should return responses using `ResponseEntity`.

Public APIs should use consistent versioned paths when practical:

```text
/api/v1/...
```

Internal service APIs should use explicit internal paths:

```text
/internal/v1/...
```

Existing incompatible contracts must not be changed casually.

Gateway routes must be updated when a public API is intended to be accessible from the frontend.

---

## Frontend Conventions

The Angular frontend should favor:

* standalone components where consistent with the existing application;
* reactive services;
* Observables;
* async pipe;
* explicit loading and error states;
* typed API contracts;
* reusable dashboard components;
* restrained and professional visual design.

The visual style should remain:

* dark slate;
* dashboard-oriented;
* clean and modern;
* subtly accented in blue;
* responsive;
* consistent across pages.

Do not introduce manual subscriptions and `ChangeDetectorRef` when a reactive flow is practical.

---

## Engineering Workflow

Meaningful changes should begin with a Story.

The expected workflow is:

```text
Story
    ↓
Repository Analysis
    ↓
Human Approval
    ↓
Implementation
    ↓
Implementation Report
    ↓
Code Review
    ↓
Human Approval
    ↓
Engineering Report
    ↓
Human Commit
```

An Implementation Plan is required only when the Story cannot be implemented safely from:

* the approved Story;
* the Repository Analysis;
* accepted ADRs;
* existing repository conventions.

Architectural decisions require an ADR.

Small implementation tasks should not generate unnecessary documentation.

---

## Story Requirements

A Story should define:

* goal;
* context;
* problem;
* scope;
* out-of-scope items;
* acceptance criteria;
* constraints;
* relevant ADRs;
* validation expectations.

Stories describe the expected result.

They should not prescribe implementation details unless a constraint requires them.

---

## AI Agent Roles

### OpenClaw

OpenClaw acts as the workflow coordinator.

It may:

* inspect repository context;
* identify relevant ADRs;
* produce Repository Analysis;
* ask the human engineer for clarification;
* delegate approved implementation work;
* coordinate review and reporting.

OpenClaw must not bypass human approval gates.

### OpenCode or Another Coding Agent

The coding agent acts as the Implementation Engineer.

It may:

* inspect approved context;
* modify authorized files;
* add or update tests;
* execute validation;
* report implementation results.

It must not:

* expand Story scope;
* make unapproved architectural decisions;
* commit;
* push;
* merge;
* discard user changes;
* approve its own implementation.

The coding agent may be replaced by another local or remote model without changing the engineering workflow.

### Human Engineer

The human engineer:

* defines priorities;
* approves Stories;
* approves architectural decisions;
* reviews repository analysis;
* reviews diffs in the IDE;
* corrects implementation when needed;
* approves integration;
* performs commits and pushes.

---

## OpenCode Delegation

OpenClaw delegates implementation using the configured OpenCode server.

Expected command pattern:

```text
opencode run \
  --attach "$OPENCODE_SERVER_URL" \
  --username "$OPENCODE_SERVER_USERNAME" \
  --password "$OPENCODE_SERVER_PASSWORD" \
  --dir "<repository-path>" \
  "<implementation-prompt>"
```

OpenCode credentials and endpoints must come from the execution environment.

They must never be hardcoded in:

* Stories;
* prompts;
* skills;
* repository files;
* implementation reports.

Before delegation, OpenClaw must verify:

* required environment variables exist;
* the OpenCode server is reachable;
* the repository path is correct;
* the current branch is known;
* required artifacts are approved;
* implementation scope is explicit.

---

## Git Safety

Agents must never:

* commit automatically;
* push automatically;
* merge automatically;
* force-push;
* rewrite shared history;
* delete branches;
* discard pre-existing modifications;
* include IDE configuration accidentally.

Before implementation, agents should report the existing working-tree state.

After implementation, changes must remain visible for human review in IntelliJ.

---

## Testing

Agents must execute the validation relevant to the modified modules.

Trading OS does not currently have one root Maven build.

Services must therefore be tested independently.

Typical validation includes:

```text
Trading Core tests
Broker Service tests
Market Data tests
Market Intelligence tests
Risk Domain tests
Gateway tests
Angular tests
Angular production build
git diff --check
```

Agents must never report a test as passing unless it was actually executed successfully.

When validation cannot be executed, the report must state why.

---

## Documentation and ADRs

Accepted ADRs are authoritative for architectural decisions.

Before changing a responsibility boundary, agents must inspect relevant ADRs.

Important current areas include:

* product vision;
* microservice boundaries;
* AI-first architecture;
* Market Intelligence;
* Market Data;
* Broker Service;
* deterministic risk;
* decision pipeline;
* artifact memory;
* capability orchestration;
* credentials;
* observations;
* opportunities;
* trade planning;
* execution;
* broker architecture.

Agents must report conflicts between current code, Stories, and ADRs rather than silently choosing one interpretation.

---

## Current Product Priorities

The highest-value remaining work includes:

1. Connect the end-to-end decision pipeline.
2. Validate execution against Kraken sandbox.
3. Complete Gateway routing.
4. Expose Market Intelligence in the frontend.
5. Make critical Market Intelligence storage durable.
6. Add configurable and versioned risk profiles.
7. Add News and economic-calendar capabilities.
8. Introduce a real explainable AI engine.
9. Add passive and active scanners.
10. Add active position monitoring.

Agents should avoid starting lower-priority infrastructure work unless it directly enables one of these outcomes.

---

## Known Constraints

The repository currently includes:

* multiple independent Maven services;
* synchronous HTTP and OpenFeign communication;
* Eureka service discovery;
* local virtual-thread execution in Market Intelligence;
* some in-memory state;
* incomplete Gateway routing;
* incomplete frontend routes;
* one broker provider;
* no real AI Engine;
* no News Service;
* local-only resilience limits in Broker Service;
* pending sandbox and deployed E2E validation.

Agents must account for these constraints without treating every limitation as part of the current Story.

---

## Working Principle

Understand before modifying.

Use the Story as the source of scope.

Use accepted ADRs as the source of architecture.

Use deterministic services as the source of business authority.

Use AI agents for analysis and implementation assistance.

Keep important decisions explicit.

Keep changes focused.

Keep the human engineer in control.
