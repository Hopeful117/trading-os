# Implementation Report - Story 0219

## Outcome

Implemented the explicit `ResearchScope` contract for agent-owned bounded
research.

## Changes

- Added durable owner-managed research scopes containing exactly two canonical
  market IDs, with expiration and revocation.
- Enabled `RESEARCH_SCOPE` grants through the existing `research:run`
  authorization path.
- Added Trading Core internal authorization for Market Intelligence.
- Added Market Intelligence validation of agent JWTs and fail-closed research
  authorization for start, status and report operations.
- Restricted authenticated agent principals to the research endpoint family in
  Market Intelligence; human-only intelligence endpoints remain unavailable.
- Persisted research scope provenance on research executions.
- Added `scopeId` to the MCP research-start request and documented the setup
  prerequisite.
- Added Trading Core migration `V59` and Market Intelligence migration `V21`.

## Validation

- Trading Core agent authorization and grant tests: passed.
- Trading Core compilation: passed.
- Market Intelligence research and migration-focused tests: passed.
- Market Intelligence full suite: started successfully and exercised migration
  21, but the complete run exceeded the command timeout during the broader
  integration suite.
- MCP adapter Python compilation: passed.
- `git diff --check`: passed.

## Documentation Update

Updated `mcp-adapter/README.md` because the public research tool now requires an
explicit scope ID.

## Vault Outcome

The Obsidian vault was not consulted. No vault action is proposed because the
implementation is fully determined by the Story, repository code, and accepted
architecture/security constraints.

## Remaining Validation

- Run the repository's full quality script in an environment with stable
  PostgreSQL/Testcontainers availability.
- Complete deployed research execution validation after resolving the existing
  research data consistency gap described below.

## Runtime Gap

The deployed authorization path was exercised successfully through
Gateway → Market Intelligence → Trading Core:

- an agent credential was exchanged for an agent JWT;
- an active exact `ResearchScope` and `research:run` grant were accepted;
- `POST /api/v1/intelligence/research` returned `202 REQUESTED`;
- the Trading Core authorization audit recorded an `ALLOWED` decision.

The research execution then failed independently of agent authorization with:

```text
RESEARCH_EXECUTION_FAILED
Evidence source cutoff cannot exceed thesis source cutoff
```

Runtime evidence:

- research ID: `e6c2cc5d-c0b5-492d-87af-e3b4b67dd51d`;
- failure timestamp: `2026-11-08T09:41:31.262752Z`;
- Market Intelligence log: `InvalidDataAccessApiUsageException` from the
  research lifecycle.

This is a pre-existing research fixture/data consistency gap. The Story's
authorization boundary is validated, but successful completion and report
content remain unvalidated until the conflicting thesis/evidence source
cutoffs are corrected or the affected fixture is isolated.
