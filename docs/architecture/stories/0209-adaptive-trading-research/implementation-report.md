# Implementation Report - Story 0209

## Status

Implementation complete for the approved vertical slice. Story closure, code
review and human delivery approval remain pending.

## Implemented

- Added a typed adaptive request contract across Java and the AI Engine.
- Added deterministic mock AI output for one requested follow-up analysis.
- Added a Java-owned capability allowlist with explicit capability versions.
- Added market-scope validation and separate proposed, executed and rejected
  capability records in the research report.
- Added asynchronous in-process research dispatch with persisted `REQUESTED`
  and `RUNNING` lifecycle states.
- Added atomic JPA claiming for requested executions and stale-running expiry.
- Enforced one adaptive follow-up budget across the complete two-market
  research execution.
- Added tests for adaptive budget enforcement, exclusive claiming, explicit
  expiry, unknown evidence references and strict adaptive serialization.
- Preserved the existing seven MCP tools and Gateway public research routes.
- Preserved actor ownership, idempotency and the no-trade-authority boundary.

## Validation

- `market-intelligence`: `mvn test`, 851 tests passed.
- `ai-engine`: `.venv/bin/pytest`, 9 tests passed.
- `mcp-adapter`: `.venv/bin/pytest`, 29 tests passed.
- `gateway`: `mvn -Dtest=GatewayMarketIntelligenceRouteTest test`, 12 tests passed.
- `git diff --check` passed.

## Remaining Governance

- Complete independent code review.
- Update Story acceptance checkboxes after human review.
- Obtain human commit and delivery approval.

## Limitations

- The implementation uses the existing bounded report JSON rather than a
  normalized child execution table.
- Recovery expires stale `RUNNING` executions; it does not replay potentially
  unsafe deterministic follow-up work automatically.
- No live LLM, provider, broker or authenticated deployment validation was run.
