# Repository Analysis - Story 0237

## Status

Completed - waiting for explicit human approval before implementation planning.
This artifact is not an implementation approval or an implementation plan.

## Context Sources

- Story 0237 and accepted ADR-101.
- ADR-057, ADR-060, ADR-062, ADR-076, ADR-089, ADR-092 and ADR-100.
- Current Market Intelligence Java source and tests.
- Current AI Engine Python contracts.
- Current MCP Adapter research tool and tests.
- `TOOLS.md` and repository validation conventions.
- DevLog applicability check: the DevLog Story Agent capability was not
  available in this session, so no DevLog context was obtained. Current
  repository evidence remains authoritative.
- Obsidian vault: not consulted; no transverse note was required to establish
  the current implementation state.

## Worktree Finding

The worktree contains extensive pre-existing uncommitted changes across ADRs,
Story 0232, active-scan Java code, MCP credential/scan code, Angular agent UI,
validation artifacts and a Trading Core integration test. These changes were
not created for Story 0237 and must not be reverted or silently folded into its
implementation.

The future Story 0237 implementation will touch `mcp-adapter/app/server.py`,
which already has unrelated uncommitted changes in `start_market_scan`. That
function is separate from `start_market_research`, but the file must be edited
with a narrow patch and its existing changes preserved.

## Existing Research Architecture

### REST and authorization

- `MarketIntelligenceController` exposes `POST /api/v1/intelligence/research`.
- The controller authenticates the request, calls
  `ResearchAuthorizationClient`, then delegates to
  `ResearchApplicationService`.
- Authorization receives the explicit research scope and request market IDs.
- Status and report retrieval re-authorize the stored scope and market IDs.
- Trading Core already defines `AgentPermission.RESEARCH_RUN` as
  `research:run`; no separate hypothesis permission currently exists.

### Request and execution lifecycle

- `ResearchExecutionRequest` currently contains `scopeId`, exactly two
  `marketIds`, free-form `objective` and `depth`.
- `ResearchApplicationService.ResearchRequest` contains only market IDs,
  objective and depth.
- `ResearchApplicationService.validate` hard-rejects every cardinality other
  than exactly two distinct markets.
- `ResearchExecution` persists the actor/scope/provenance, request fingerprint,
  market IDs, objective, depth, lifecycle state, attempts, progress and JSON
  report.
- `JpaResearchExecutionEntity` maps these fields to `research_executions`; the
  existing migrations cover scope/provenance/observability but not objective
  type or discovery-specific temporal fields.
- The current fingerprint contains actor, scope, market IDs, objective and
  depth. It does not contain an objective type, timeframe, horizon or cutoff.
- The worker reconstructs a three-field `ResearchRequest` from the persisted
  execution, so any new request fields require domain/persistence propagation
  before they can affect execution.

### Current deterministic context

- `buildReport` resolves each market through `MarketDataClient`, reads the
  latest `MarketThesis`, runs `CapabilityAnalysisCoordinator` with
  `LIVE_CURRENT`, and reports deterministic context freshness and provenance.
- The current research worker is therefore a current/live evidence workflow,
  not a historical cutoff-consistent discovery workflow.
- The current report carries hypotheses as strings copied from Market Thesis
  scenarios and carries supporting/contradicting/missing evidence as JSON.
- There is no immutable `ResearchHypothesis` entity or proposal repository.
- Research lifecycle persistence is durable and idempotent, but the report is
  stored as a JSON snapshot on the execution row.

### AI boundary

- `HttpAiAnalysisCapability` uses the provider-independent `AiEnginePort` and
  supports the existing adaptive interpretation flow.
- `AiInterpretationPhase` currently has only `INITIAL` and `FINAL_SYNTHESIS`.
- The Java wire request sends `researchMode` values for thesis interpretation or
  adaptive research and always includes a `MarketThesisWire` when the current
  capability is invoked.
- The Python AI Engine uses strict models with `extra="forbid"` and currently
  requires `MarketThesisInput` in `InterpretationRequest`.
- The Python contract currently supports thesis interpretation and adaptive
  follow-up, not a standalone structured hypothesis-discovery proposal.
- Adding `HYPOTHESIS_DISCOVERY` therefore requires a coordinated Java/Python
  contract change, not only a new Java enum value.

### MCP and frontend surfaces

- `mcp-adapter/app/server.py::start_market_research` validates exactly two
  distinct UUIDs and posts to the existing public Gateway route.
- The MCP status/report tools already expose the existing research lifecycle and
  do not call internal services directly.
- The MCP server file has unrelated active-scan changes; only the research
  function and its tests should be changed for this Story.
- No Angular research-specific page was found in the inspected surface. A
  minimal read projection is optional; it must use existing reactive service
  conventions and must not own orchestration.

## Confirmed Gaps Relevant To Story 0237

1. The request DTO, application request, service validation and MCP tool all
   require two markets.
2. The persisted execution has no typed objective, timeframe, horizon or
   historical cutoff.
3. The current fingerprint cannot distinguish the new objective or temporal
   inputs.
4. The current worker uses `LIVE_CURRENT` evidence and the latest Market Thesis,
   which is incompatible with the Story's historical cutoff requirement.
5. The current `ResearchReport` uses free-form hypotheses and has no typed,
   immutable proposal contract.
6. No proposal persistence/read model exists beyond the execution JSON report.
7. The AI contract requires a Market Thesis and lacks a discovery mode and
   structured proposal schema.
8. The MCP contract has no objective type or conditional cardinality support.

## Proposed Implementation Shape

The Implementation Plan should keep the first slice bounded and additive:

- Introduce a typed objective enum with comparative default behavior.
- Propagate objective-specific request fields through REST, application,
  execution persistence and fingerprinting.
- Keep comparative requests at exactly two markets and allow one market only
  for discovery.
- Introduce a versioned historical context contract backed by existing
  authorized evidence APIs, with explicit insufficiency rather than current
  quote fallback.
- Add a typed `ResearchHypothesis` proposal model and durable persistence or a
  clearly bounded child projection, with immutable versions and provenance.
- Extend the AI Java/Python contracts with a discovery-specific mode and strict
  proposal DTO while preserving the current thesis/adaptive contracts.
- Validate proposal evidence and claims in Java before persistence.
- Extend the existing REST/MCP report surface; do not add direct AI or internal
  service MCP tools.
- Add focused Java, Python and MCP tests, then run the repository-targeted
  Market Intelligence and MCP validation commands.

The exact relational shape, historical data adapter and AI DTO decomposition
belong in the Implementation Plan. They are not silently decided by this
analysis.

## Architecture And Safety Constraints

- Preserve ADR-089 comparative research semantics and existing idempotency.
- Reuse `research:run`; do not create trading, broker, Risk, TradePlan or
  strategy permissions.
- Derive actor and provider identity server-side; client fields are constraints
  to verify, not authority.
- Do not pass raw broker credentials, internal endpoints or unbounded provider
  payloads through MCP or AI contracts.
- Do not use current/live quotes as historical evidence.
- Do not fabricate a hypothesis when evidence, AI or the historical dataset is
  unavailable.
- Do not mutate Market Thesis, Opportunity, StrategyDefinition, TradePlan,
  Risk, account, position, order, synchronization or reconciliation state.
- Preserve existing unrelated worktree changes and avoid broad refactors.
- No LIVE broker operation or cTrader evidence is required or permitted for
  this Story.

## Validation Surface

### Market Intelligence Java

- Request DTO and application validation tests for comparative/default and
  single-market objective modes.
- Fingerprint and idempotency conflict tests covering every material new field.
- Historical context cutoff, missingness, contradiction and insufficiency
  tests.
- Proposal schema, evidence-reference, provenance and immutable-version tests.
- Persistence/repository migration tests where applicable.
- Existing `ResearchApplicationServiceTest` comparative regressions.
- Controller authorization and report/status retrieval tests.

### AI Engine

- Strict discovery request/response validation and extra-field rejection.
- Required proposal fields and bounded collections/text.
- Temporal/evidence-reference validation.
- No-hypothesis, inconclusive, unavailable and malformed-output cases.
- Existing thesis/adaptive contract regressions.

### MCP Adapter

- Conditional one-market/two-market validation.
- Backward-compatible default objective mapping.
- Request payload and bounded error/report projection tests.
- Existing scan, credential and read-only boundary tests must remain intact.

### Repository checks

Relevant commands from `TOOLS.md` are expected after an approved plan:

```bash
(cd market-intelligence && ../trading-core/mvnw test)
(cd mcp-adapter && .venv/bin/python -m pytest)
git diff --check
```

The AI Engine test command is also applicable if its contract changes:

```bash
(cd ai-engine && .venv/bin/python -m pytest)
```

The complete root validation may be run if the implementation crosses shared
contracts, but it must not be reported as passed unless actually executed.

## Risks And Open Decisions For The Implementation Plan

- Historical evidence APIs and their exact cutoff semantics must be identified
  before implementation; the current research path is live/current.
- Adding fields to the execution row requires a migration and careful restore
  compatibility for existing comparative executions.
- A separate proposal table improves immutable versioning but expands the
  migration surface; embedding proposals in the JSON report is simpler but may
  not satisfy durable version/query requirements. The plan must resolve this
  explicitly.
- The AI Engine currently requires a thesis. The plan must define whether
  discovery uses a separate request variant or a safely optional thesis field;
  weakening the existing strict contract without preserving validation is not
  acceptable.
- The current MCP tool is a Python public surface and must preserve its
  existing default behavior for older callers.
- Existing worktree modifications in `mcp-adapter/app/server.py` and related
  files require narrow patches and targeted diff review.

No blocker prevents Implementation Planning, but implementation must not begin
until this Repository Analysis is explicitly approved and the resulting
Implementation Plan is separately approved.

## Gate 1 Decision Required

Human approval is required for this Repository Analysis. Upon approval, the
next stage is an Implementation Plan covering the request contract, historical
context source, persistence shape, AI contract evolution, authorization,
compatibility tests and validation sequence.
