# Implementation Report - Story 0208

## Delivered

- Added the Java-owned durable `ResearchExecution` lifecycle and report model.
- Added Flyway migration `V19__trading_research_execution.sql` with actor-scoped
  idempotency.
- Added authenticated Market Intelligence endpoints:
  - `POST /api/v1/intelligence/research`;
  - `GET /api/v1/intelligence/research/{researchId}`;
  - `GET /api/v1/intelligence/research/{researchId}/report`.
- Bounded the first workflow to exactly two distinct canonical market IDs,
  `STANDARD` or `DEEP` depth, and a 500-character objective.
- Reused `MarketDataClient`, `MarketThesisRepository` and the existing
  `CapabilityAnalysisCoordinator` for deterministic market analysis.
- Reused the existing `HttpAiAnalysisCapability` and `AiEnginePort` when the AI
  Engine bean is enabled and a Market Thesis is available.
- Added MCP mappings for `start_market_research`, `get_research_status` and
  `get_research_report` while preserving the four existing direct tools.
- Consolidated the MCP identity and documentation: the server is now named
  `trading-os-research`, and the README distinguishes direct reads from the
  controlled research lifecycle, including authentication and idempotency.
- Added explicit Gateway route coverage for the three public research endpoints.
- Classified AI responses with warnings and no findings as `FAILED`, so timeout
  and unavailable-provider outcomes are not reported as completed interpretation.
- Kept the Gateway route boundary unchanged because the existing
  `/api/v1/intelligence/**` public route already covers these endpoints and does
  not expose `/internal/**` routes.

## Validation

- `mvn -Dtest=ResearchApplicationServiceTest test`: passed, 4 tests.
- `mvn test` in `market-intelligence`: passed, 848 tests.
- `mvn test` in `gateway`: passed, 51 tests.
- `mvn -Dtest=GatewayMarketIntelligenceRouteTest test` in `gateway`: passed, 12 tests.
- Flyway validation in the integration suite: 19 migrations applied successfully.
- `python3 -m py_compile app/*.py tests/*.py` in `mcp-adapter`: passed.
- `mcp-adapter/.venv/bin/pytest`: passed, 29 tests.
- `git diff --check`: passed.
- No live Gateway, Market Data, AI Engine, broker or provider validation was run.

## Deliberate Limitations

- The bounded workflow remains synchronously orchestrated, but lifecycle
  creation is persisted before execution and the execution path is not held in
  one long request transaction. A worker-backed asynchronous dispatcher remains
  a follow-up hardening step.
- The current research report stores bounded JSON references/payloads in the
  research row. Large raw market payloads are not copied into the report.
- The AI Engine receives the existing authorized thesis/context contract; no new
  Python endpoint or direct MCP-to-AI Engine path was introduced.
- The MCP start command requires a caller-supplied idempotency key so retries
  can replay the same request safely. The Java persistence layer also handles a
  concurrent unique-key collision by re-reading the existing execution.

## Known Follow-Up Work

- Add an asynchronous worker and explicit cancellation/expiry transitions.
- Add an end-to-end authenticated REST/Gateway test for research ownership and
  persisted report retrieval.
- Replace synchronous request execution with a durable worker-backed lifecycle
  before adaptive research can issue multiple analysis rounds.
- Add AI-enabled integration fixtures covering completed, partial, timeout and
  malformed AI results.
- Decide whether the final report should use a normalized child table instead of
  the bounded JSON payload.

## Safety

The workflow does not create TradePlans, call Risk, create ExecutionIntents,
place broker orders, mutate Market Thesis or authorize a trade.
