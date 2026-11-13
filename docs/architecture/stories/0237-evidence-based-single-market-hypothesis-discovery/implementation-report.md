# Implementation Report - Story 0237

## Status

Implementation completed for the bounded additive slice; independent review
findings were addressed in the current working tree. Code review and final
human validation remain pending.

## Implemented

- Added typed `COMPARATIVE_MARKET_RESEARCH` and
  `SINGLE_MARKET_HYPOTHESIS_DISCOVERY` objectives.
- Preserved the existing comparative two-market default and validation.
- Added conditional one-market validation with bounded timeframe, horizon and
  cutoff fields.
- Included objective and temporal scope in the research fingerprint and durable
  execution persistence.
- Added Flyway migrations `V27` and `V28` for execution scope fields and
  immutable research hypothesis persistence.
- Added `ResearchHypothesis` domain/persistence projection and report-level
  proposal summaries.
- Added explicit discovery outcomes and proposal validation status.
- Added strict `HYPOTHESIS_DISCOVERY` phase/mode support in Java and Python AI
  contracts while preserving thesis-required behavior for existing modes.
- Added a typed AI hypothesis-proposal contract and rejected generic findings as
  implicit hypotheses.
- Added no-thesis discovery handling to the Python service and providers.
- Passed the deterministic assembled context, including its fingerprint and
  public evidence sections, to discovery AI.
- Routed single-market deterministic analysis through `STRICT_OBSERVED` evidence
  and propagated the requested historical OHLC interval and complete bounded
  cutoff range.
- Added canonical provider/product constraints to REST, MCP and fingerprint
  validation.
- Changed hypothesis persistence to a composite `(hypothesis_id, version)` key
  and reject duplicate immutable versions.
- Extended the existing REST response/request and MCP tool additively.
- Updated the MCP research documentation.
- Added a Java regression test proving bounded single-market discovery behavior.

## Documentation Reconciliation

- Updated `mcp-adapter/README.md` because the public research tool now supports
  conditional one-market/two-market semantics.
- No unrelated architecture document or broad README was changed.

## Validation

Passed:

- `market-intelligence`: package compilation.
- Targeted Market Intelligence research, response, lifecycle and evaluation
  tests.
- `ai-engine/.venv/bin/python -m pytest`: 19 passed.
- `mcp-adapter/.venv/bin/python -m pytest`: 48 passed.
- Targeted PostgreSQL migration integration test after updating the expected
  current schema version from 26 to 28.
- Independent review regression set: 23 Market Intelligence tests passed.
- PostgreSQL persistence/migration integration: 8 tests passed; all 28 Flyway
  migrations applied successfully.
- `git diff --check` for tracked implementation files.

The complete Market Intelligence suite was started earlier but exceeded the
120-second execution limit. Its visible output showed one stale schema-version
assertion, which was corrected; targeted and PostgreSQL integration validation
completed successfully afterward.

## Residual Risks

- Proposal evidence references are validated against the thesis/context evidence
  identifiers available to the AI request. Proposals with missing or
  unauthorized evidence are rejected rather than persisted.
- The current historical range endpoint exposes `fromExclusive` and
  `toInclusive`; the research boundary applies the requested `[cutoffStart,
  cutoffEnd)` interval after retrieval and rejects observations outside it.
- No LIVE, broker, account, synchronization, Risk, TradePlan, Strategy or
  Market Thesis mutation was performed.

## Vault Outcome

The Obsidian vault was not consulted and no vault action is proposed. The
repository Story, ADR and source code provided the relevant context. No
`vault-outcome.json` was created because there is no candidate curated-memory
action.
