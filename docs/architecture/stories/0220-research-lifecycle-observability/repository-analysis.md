# Repository Analysis - Story 0220

## Repository Baseline

- Branch: `main`
- HEAD: `807ab16a` (`Merge pull request #274 from Hopeful117/fix/market-thesis-cutoff-precision`)
- Working tree: clean before this Story artifact was created.
- Story 0219: merged and documented at
  `docs/architecture/stories/0219-authorize-agent-research-end-to-end/`.
- PR #274: merged; temporal precision fix is present in `main`.
- Next available Story ID: `0220`.
- Story-local `story.md` was created from the requested scope because no
  canonical Story 0220 artifact existed.

## Context Sources

Read and reconciled:

- Research Intelligence roadmap, capability inventory and gap matrix dated
  `2026-11-08`.
- MCP research integration assessment dated `2026-11-08`.
- Story 0219 repository analysis, implementation plan and implementation report.
- ADR-089 and ADR-090.
- Current Market Intelligence, Trading Core and MCP source/tests.

The audit reports were treated as hypotheses. The current repository is the
authority for the findings below.

## Stage A - PR #274 Validation

### Repository and isolated evidence

- `ResearchApplicationServiceTest.completesDeterministicResearchAndReplaysIdempotentRequest`
  passes and proves a deterministic two-market execution produces a persisted
  in-memory report and supports idempotent replay.
- `ResearchExecutionLifecycleTest` passes for worker claim exclusivity,
  checkpoint retention, stale requeue protection and explicit expiration code.
- `MarketThesisJpaPersistenceTest` passes against isolated H2 with all 22
  migrations, including the PostgreSQL microsecond precision regression path.
- The targeted Market Intelligence command executed successfully:
  `../trading-core/mvnw -q -Dtest=ResearchApplicationServiceTest,ResearchExecutionLifecycleTest,MarketThesisDomainTest,MarketThesisJpaPersistenceTest test`.

### Runtime evidence and limitation

The local Docker stack is running, but it is a shared stateful development
runtime rather than an isolated Story fixture. No
`TRADING_OS_AGENT_CREDENTIAL` is available in the current process or local
`.env`, and no new scope/grant was provisioned. Starting research there would
mutate shared local persistence without a dedicated isolated authorization
fixture, so it was not executed.

The runtime logs also contain pre-existing background lifecycle failures such
as `Feature snapshot identity has conflicting content` and replay population
`Forbidden` events. These are not evidence that the PR #274 temporal error
persists, because no fresh authorized agent research was started against this
runtime.

### Outcome

`RUNTIME_BLOCKED` for the complete external MCP-to-Gateway-to-report workflow.

The temporal fix is validated in isolated domain/JPA tests and the existing
research application path is validated with an in-memory report, but a complete
authorized MCP execution and report retrieval is not currently proven.

The MCP adapter test suite also exposed one existing contract-test defect:
`tests/test_server.py::test_tools_call_only_approved_gateway_reads` omits the
mandatory `scope_id` introduced by Story 0219. Result: 35 passed, 1 failed.
This is a test fixture mismatch, not evidence of a production authorization
bypass.

## Actual Research Lifecycle

The lifecycle is owned by `ResearchApplicationService` and persisted through
`ResearchExecutionRepository` / `JpaResearchExecutionRepository`.

```text
POST /api/v1/intelligence/research
  -> authorize agent in Trading Core when principal is MiAgentPrincipal
  -> validate request and actor-scoped idempotency
  -> persist ResearchExecution REQUESTED
  -> enqueue on bounded researchExecutor
  -> atomic claim REQUESTED -> RUNNING
  -> build deterministic result per market
  -> optional initial AI interpretation
  -> optional one Java-authorized adaptive follow-up
  -> optional final AI synthesis
  -> persist checkpoint report while RUNNING after each market
  -> persist COMPLETED when every market is COMPLETE
     or PARTIALLY_COMPLETED otherwise
  -> GET status through ownership and agent scope checks
  -> GET report through the same checks, 202 until report exists
```

### State and transition inventory

| State/transition | Owner | Persisted evidence | Trigger/retry behavior | Public visibility |
|---|---|---|---|---|
| `REQUESTED` | ResearchApplicationService/repository | status, requestedAt, updatedAt, request data | Initial queue entry; also requeue target after stale running execution | Status API |
| Claim `REQUESTED -> RUNNING` | JpaResearchExecutionRepository | status and updatedAt | Pessimistic/conditional database update; one worker wins | Status API |
| `RUNNING` | ResearchApplicationService | status, updatedAt, optional report checkpoint | Worker builds report; no explicit lease owner | Status API |
| Checkpoint | ResearchApplicationService | partial JSON in `report`, status remains RUNNING, updatedAt | Saved after each market; protected by expected RUNNING status | Status API/report endpoint returns 202 with response |
| Requeue | ResearchApplicationService.dispatchPending | status REQUESTED, prior report retained, updatedAt | RUNNING older than five minutes is requeued and enqueued | Indistinguishable from initial REQUESTED today |
| `COMPLETED` | ResearchApplicationService | completedAt, report, status | All market results have `COMPLETE` status | Status API; report 200 |
| `PARTIALLY_COMPLETED` | ResearchApplicationService | completedAt, report, status | One or more market results are incomplete/failed | Status API; report 200 |
| `FAILED` | ResearchApplicationService | completedAt, failureCode, null report | Stable `ResearchException.code()` or generic runtime fallback | Status API; report 202 response, no safe failure projection |
| `EXPIRED` | ResearchExecution domain | completedAt and `RESEARCH_EXECUTION_EXPIRED` | Domain transition exists; no active worker timeout path invokes it | Not demonstrated through production endpoint |
| `CANCELLED` | ResearchExecutionStatus enum | No current transition in ResearchApplicationService | No production cancellation endpoint for research | Not observable |
| Waiting for AI | Report market fields | `aiStatus`, diagnostics inside report only | AI is synchronous inside worker; no persisted waiting state | Only after checkpoint/report exists |

## Confirmed Observability Gaps

1. The public status response has no attempt count, current stage, last
   progress timestamp distinct from generic `updatedAt`, retry indicator or
   terminal reason category. An external agent cannot distinguish initial queue
   wait from a requeued execution.
2. Worker claims have no persisted attempt number or lease/claim timestamp. The
   original research ID remains in logs, but retries cannot be counted from the
   status contract.
3. Checkpoint persistence records a partial report but not the stage that
   produced it. Deterministic, initial-AI, adaptive follow-up and final-AI
   outcomes are represented inside completed report market fields, not while a
   research is running.
4. Failure codes are partially stable, but the status/report endpoint does not
   expose a safe terminal category separately from the internal failure code.
   The report endpoint returns the whole status projection with HTTP 202 while
   no report exists, including for failed executions.
5. No Micrometer/Actuator dependency or research-specific bounded metrics were
   found in Market Intelligence. Existing structured logs include research IDs
   for worker failures and queue rejection, but no request correlation model is
   persisted across the async boundary.
6. MCP tool names and paths are compatible, but the adapter test suite still
   contains a pre-0219 start request without `scope_id` and therefore does not
   currently verify the authorized start payload consistently.

## Security Assessment

- Agent start calls Trading Core authorization through the internal Feign
  client before persistence.
- Agent status/report reads resolve ownership by agent ID and re-run scope
  authorization against the execution scope and market IDs.
- Human reads retain the owner path.
- `ResearchExecution` stores owner, initiating agent, credential, grant and
  version provenance.
- The MCP adapter calls only Gateway public paths and does not access internal
  services or databases.
- New observability fields must remain derived from the execution itself and
  must not expose owner, credential, grant or exception stack details to an
  unauthorized caller.

## Proposed Minimal Change Surface

Subject to approval, the smallest coherent implementation is:

- add persisted bounded lifecycle evidence for attempt count, current stage and
  retry/progress timestamps, derived only from existing worker transitions;
- expose those fields additively in `ResearchExecutionResponse`;
- classify terminal failure safely while preserving existing failure codes and
  HTTP/security behavior;
- add bounded structured research lifecycle logs and metrics only if the
  repository's existing dependency boundary supports them without introducing
  a monitoring platform;
- update the stale MCP contract test and add status/report security and
  lifecycle regression tests;
- document bounded external polling and the isolated validation limitation.

No implementation plan is produced yet. The proposed fields and metric choice
require human approval because they change a persisted/API contract, even
though they do not change the architecture.

## Required Approval Decision

Approve or reject this Repository Analysis before implementation planning.
Implementation must not begin until the current analysis is explicitly
approved.
