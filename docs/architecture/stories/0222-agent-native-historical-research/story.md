# Story 0222 - Agent-Native Historical Research Inspection & Controlled Dataset Export

## Metadata

**ID:** `0222`
**Title:** Agent-Native Historical Research Inspection & Controlled Dataset Export
**Status:** Repository Analysis pending human approval
**Priority:** P1 - Agent Platform and Research Trustworthiness
**Predecessors:** Stories `0219`, `0220`, `0221`
**Related ADRs:** `ADR-057`, `ADR-060`, `ADR-061`, `ADR-062`, `ADR-088`, `ADR-089`, `ADR-090`

## Objective

Enable an explicitly authorized technical agent to inspect historical research
readiness and request a bounded, deterministic dataset export through the
existing agent credential, Gateway, Trading Core authorization and Market
Intelligence boundaries.

The agent is a first-class authenticated actor, not a proxy for a human
session. It receives diagnostics and controlled artifact metadata only. It
cannot reconstruct historical data, change canonical evidence, make trading
decisions or access broker authority.

## Business Value

An agent can determine whether a declared historical population is measurable
and temporally valid before requesting offline research data. Authorized export
then becomes repeatable, auditable and safe for the existing Python validation
and experiment workflow without exposing databases, credentials or arbitrary
filesystem operations.

## Current Limitations

- Only `research:run` exists for agent research and it is tied to the existing
  two-market research workflow.
- Readiness is currently a global/configuration-based user-facing endpoint.
- The existing exporter is an application-runner command with a filesystem
  input/output boundary, not an agent-facing job lifecycle.
- Scope constraints do not currently represent historical interval, cutoff,
  population, output or compute limits.
- Runtime historical population and agent authorization are not currently
  verified in this environment.

## Architectural Boundaries

```text
Agent credential
    -> Gateway token exchange
    -> short-lived agent JWT
    -> Gateway public route
    -> Trading Core exact grant/scope authorization
    -> Market Intelligence deterministic readiness/export lifecycle
    -> controlled artifact metadata
    -> MCP response
```

- Trading Core owns agent identity, grants, scope ownership and authorization.
- Market Data owns canonical historical evidence.
- Market Intelligence owns deterministic readiness, dataset construction,
  export lifecycle and artifact provenance.
- Gateway owns external authentication and routing.
- MCP is a thin, bounded Gateway-backed anti-corruption layer.
- Python `research/ml` consumes validated offline bundles and is not a runtime
  acquisition or authorization service.

## In Scope

- Add distinct `research:inspect` and `research:export` permissions.
- Extend research scope constraints additively without breaking the exactly-two
  market invariant for `research:run`.
- Add scoped historical readiness diagnostics with explicit `READY`,
  `INSUFFICIENT`, `BLOCKED` and `UNKNOWN` semantics.
- Add bounded, idempotent export request/status lifecycle using the existing
  deterministic dataset exporter.
- Revalidate authorization before queued execution and status/artifact reads.
- Preserve agent, owner, credential, grant, scope, cutoff and dataset
  provenance where relevant.
- Add Gateway-backed MCP tools for inspection and export status.
- Add focused Trading Core, Gateway, Market Intelligence and MCP tests.
- Add operator documentation for legitimate grant provisioning.

## Out of Scope

- Historical reconstruction or provider historical backfill.
- Historical provenance repair or immutable snapshot/outcome modification.
- Broker synchronization, orders, positions, risk or execution authority.
- New authentication, authorization server, generic policy language, generic
  workflow engine or generic approval engine.
- Direct MCP/internal service/database/provider access.
- Arbitrary filesystem paths or full dataset responses through MCP.
- Python subprocess execution from HTTP requests.
- ML training, model serving, profitability claims or autonomous planning.

## Authorization Requirements

### Inspection

`research:inspect` is required for read-only, bounded readiness diagnostics.
It is independent from `research:run` and `research:export`.

### Export

`research:export` is required for a bounded offline export. It does not imply
reconstruction, provider ingestion or access to arbitrary output paths.

Both permissions require:

- active dedicated agent and credential;
- active, unexpired, non-revoked exact grant;
- owner-owned research scope;
- scope-compatible markets, intervals, cutoffs, dataset profile and limits;
- server-side authorization on submission, execution and retrieval.

Existing agents do not receive these permissions automatically. Human-facing
authentication and ownership behavior remains unchanged.

## Functional Acceptance Criteria

- [ ] Agent authentication reuses the existing credential exchange and JWT.
- [ ] Inspection requires an explicit `research:inspect` grant.
- [ ] Export requires a distinct `research:export` grant.
- [ ] Owner, market, interval, cutoff, profile and resource limits are checked
      server-side.
- [ ] Existing two-market `research:run` scopes and behavior remain compatible.
- [ ] Readiness returns deterministic structured diagnostics and an explicit
      `READY`, `INSUFFICIENT`, `BLOCKED` or `UNKNOWN` verdict.
- [ ] Unknown provenance is never promoted to eligible evidence.
- [ ] Export invokes the existing deterministic dataset export implementation.
- [ ] Export requests are bounded, idempotent and expose only job/artifact
      metadata through MCP.
- [ ] Queued jobs revalidate authorization before execution.
- [ ] Status and artifact retrieval revalidate authorization and ownership.
- [ ] Export fingerprints, validation status, cutoff and provenance metadata
      are retained.
- [ ] Java export completion, Java contract validity, Python validation and
      experiment eligibility remain distinct states.

## Security Acceptance Criteria

- [ ] Human JWTs cannot impersonate agents.
- [ ] Client-supplied identity/delegation headers cannot establish authority.
- [ ] Missing, invalid, expired, revoked or stale-version credentials/grants
      fail closed.
- [ ] Cross-owner, cross-scope, out-of-market and out-of-interval requests are
      rejected without protected resource enumeration.
- [ ] Conflicting idempotency reuse fails deterministically.
- [ ] Server-controlled output roots and limits prevent arbitrary filesystem
      writes and unbounded resource use.
- [ ] Tokens, credentials, absolute filesystem paths and unbounded exceptions
      are absent from tool responses and artifacts.
- [ ] No new capability can access brokers, accounts, positions, orders, risk
      or live execution.

## Testing Strategy

- Trading Core unit/integration tests for permission mapping, scope constraints,
  expiry, revocation, credential versions and backward compatibility.
- Gateway route/authentication tests for agent and human principal separation.
- Market Intelligence tests for readiness verdicts, temporal provenance,
  export lifecycle, limits, idempotency, revalidation and artifact metadata.
- MCP tests for typed schemas, credential reuse, Gateway-only access, bounded
  responses, error mapping and no secret exposure.
- Isolated end-to-end fixtures for agent credential -> Gateway -> grant ->
  readiness and export -> status retrieval.
- Existing Python validator tests remain the offline bundle contract checks;
  no Python runtime invocation is added to HTTP handling.

## Runtime Validation Expectations

If an already authorized agent credential and isolated test data are available:

1. Inspect one explicitly granted historical scope.
2. Verify a scope, market or interval outside the grant is rejected.
3. Verify missing export permission is rejected.
4. Exercise export against isolated fixtures.
5. Verify authorized status and artifact metadata retrieval.

If credentials or isolated runtime authorization are unavailable, report
`RUNTIME_AGENT_AUTHORIZATION_NOT_VERIFIED`. Fixture tests must not be presented
as runtime proof.

## Documentation Deliverables

- `repository-analysis.md` after repository analysis;
- `implementation-plan.md` only after analysis approval;
- `implementation-report.md` after implementation and validation;
- operator runbook content for legitimate grant provisioning, without secrets
  or manual database permission edits.

## Workflow Gate

Repository Analysis is complete. Implementation Planning and implementation are
blocked until the human explicitly approves the current Repository Analysis.
