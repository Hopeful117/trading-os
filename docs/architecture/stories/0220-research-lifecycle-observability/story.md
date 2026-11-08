# Story 0220 - Research Lifecycle Observability & End-to-End Validation

## Metadata

**ID:** `0220`
**Title:** Research Lifecycle Observability & End-to-End Validation
**Status:** Repository Analysis pending human approval
**Priority:** P1 - Research Trustworthiness
**Predecessors:** Story `0219`, PR `#274`
**Related ADRs:** `ADR-089`, `ADR-090`

## Goal

Prove that an authorized external agent can execute the existing bounded
research lifecycle after the temporal precision fix, understand its progress and
failure state, and retrieve a persisted evidence-grounded report through the
secure MCP integration.

## Context

Market Intelligence already owns asynchronous research orchestration,
deterministic capabilities, optional advisory AI branches, persistence and
bounded MCP contracts. Story 0219 added agent authorization for research start,
status and report access. PR #274 corrected PostgreSQL microsecond precision
handling for thesis and evidence cutoffs.

## Problem

The complete authorized research lifecycle has not yet been proven after PR
#274. Existing lifecycle state, worker behavior, retries, deterministic and AI
branch outcomes, correlation and failure diagnostics must be traced against the
current implementation before any observability gap is changed.

## Scope

- Validate the isolated authorized MCP-to-research-to-report workflow.
- Inventory the actual ResearchExecution state machine, persistence and worker
  transitions.
- Verify status and report contracts, correlation and ownership boundaries.
- Add only focused lifecycle diagnostics, status fields, metrics, logs or tests
  where a demonstrable gap prevents diagnosis without database access.
- Preserve existing MCP tool names, Gateway authorization and bounded research
  behavior.
- Document validation evidence, confirmed gaps, lifecycle behavior and limits.

## Out of Scope

- New orchestration frameworks, event brokers or monitoring platforms.
- Broker synchronization, trade execution, risk changes or production writes.
- Paid AI calls, deployment, shared-service restarts or live runtime mutation.
- Weakening temporal evidence validation or agent authorization.
- Autonomous polling loops or a generic agent framework.
- ML model implementation or historical dataset population.

## Acceptance Criteria

- [ ] PR #274 is validated in a complete isolated research execution.
- [ ] The actual lifecycle state machine and transitions are documented.
- [ ] Authorized callers can distinguish queued, running, progressing, retrying,
      terminal and report-available states where supported by persisted evidence.
- [ ] Failure diagnostics are stable, actionable and do not expose sensitive
      exception details.
- [ ] Worker retries, capability outcomes and AI branch outcomes remain
      traceable to the original research execution.
- [ ] MCP start, status and report compatibility and security are preserved.
- [ ] At least one complete report retrieval and one controlled failure scenario
      are validated in isolated infrastructure.
- [ ] Relevant lifecycle, correlation, API, security and observability tests
      pass.
- [ ] No unnecessary infrastructure or architecture is introduced.
- [ ] Documentation and validation evidence are complete.

## Constraints

- Preserve Java Market Intelligence ownership of research orchestration.
- Preserve Trading Core ownership of agent grants and authorization.
- Keep deterministic market facts authoritative and AI advisory only.
- Do not rely on thread-local request context across asynchronous boundaries.
- Do not invent progress percentages or unsupported lifecycle states.
- Do not commit, push or merge automatically.

## Relevant ADRs

- `docs/architecture/adr/ADR-089-native-trading-research-agent-mcp-responsibility-boundary.md`
- `docs/architecture/adr/ADR-090-agent-authorization-and-delegated-service-calls.md`

## Relevant Modules

- `market-intelligence`
- `trading-core`
- `gateway`
- `mcp-adapter`
- `ai-engine`

## Validation

- Targeted Market Intelligence lifecycle, persistence, web and authorization
  tests.
- Targeted Trading Core authorization regression tests.
- MCP adapter test suite and Python compilation.
- Isolated authorized start, bounded status polling, terminal report retrieval
  and controlled failure evidence.
- `git diff --check` and repository quality validation for affected modules.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Implementation completed.
- [ ] Relevant validation executed.
- [ ] Documentation reconciled.
- [ ] Code Review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created separately.
