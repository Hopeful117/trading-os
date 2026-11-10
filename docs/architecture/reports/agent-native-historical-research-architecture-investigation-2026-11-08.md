# Agent-Native Historical Research Architecture Investigation

**Date:** 2026-11-08  
**Decision status:** investigation only; no architecture or Story created  
**Scope:** agent-native historical research inspection, readiness diagnostics, controlled export and future reconstruction

## Executive Verdict

Trading OS can expose historical research safely through the existing Gateway, agent identity and bounded Market Intelligence research boundary. The next implementation should not create a second agent authorization system, direct database access, or a generic autonomous operation framework.

The recommended next step is a bounded research-inspection capability:

1. read-only readiness and coverage diagnostics for an explicitly authorized research scope;
2. controlled asynchronous dataset export using an explicit export permission and limits;
3. defer historical reconstruction until provenance, cutoff and write semantics are specified and separately approved.

Readiness inspection does not require human approval per request. Export may use a pre-authorized, bounded grant because it is non-trading and produces an offline artifact. Reconstruction should require an explicit human-approved operation or an equivalent stronger grant because it can materialize derived historical evidence and consume materially more resources.

## Current Evidence

### Agent and MCP boundary

- The MCP adapter exchanges `TRADING_OS_AGENT_CREDENTIAL` for a short-lived agent JWT and calls only Gateway-backed public APIs.
- It does not receive broker credentials, call internal service endpoints, access databases, or place orders.
- Existing research tools require two distinct canonical market IDs, a bounded objective, a bounded depth and an idempotency key.
- Gateway and downstream services already have agent-principal support for research execution, status and report reads. `MarketIntelligenceController` requires an explicit scope for agent requests and forwards agent, credential, grant and version provenance.

### Authorization model

- `AgentPermission.RESEARCH_RUN` is mapped to `RESEARCH_SCOPE` and is enforced through Trading Core authorization.
- `ResearchScope` currently represents an owner, exactly two markets and the `research:run` permission. It is sufficient for the existing bounded research workflow but not for interval limits, export limits or reconstruction policy.
- Authorization decisions include grant identity/version and agent credential version. These values should be retained in new research audit/provenance records.
- Authorization is default-deny and must be evaluated server-side. Client-provided delegated actor headers must not establish authority.

### Research and historical data

- Market Intelligence already has persisted research lifecycle state, idempotent execution, ownership checks, readiness/integrity services, historical reconstruction policy/validators and a controlled dataset export path.
- The research architecture is deterministic-first. Historical data, cutoff, freshness, provenance, missingness and contradiction states are business evidence, not merely transport metadata.
- Current historical evidence is not generally ML-ready: the prior investigation found 471 snapshots, 470 created after the target cutoff and no eligible samples. Provenance such as `fetchedAt` is incomplete or unavailable for the affected evidence.
- Therefore the first agent capability must expose readiness and insufficiency explicitly, not imply that a dataset is usable or silently reconstruct data.

### Existing operation approval

The Trading Core agent operation workflow is concrete but currently specific to account synchronization:

- operation type: `ACCOUNT_SYNCHRONIZATION`;
- resource type: `BROKER_ACCOUNT`;
- states: pending approval, approved, executing, succeeded, failed, outcome unknown, rejected and expired;
- grant and agent versions are captured at request time;
- execution is claimed idempotently and uncertain broker outcomes are represented as unknown;
- audit events are persisted around lifecycle transitions.

This is a useful precedent, not a generic operation engine. Extending it for research reconstruction should be a later, explicit decision rather than a prerequisite for read-only diagnostics.

## Recommended Capability Boundary

```text
MCP adapter
  -> Gateway
  -> authenticated agent principal
  -> Trading Core authorization / scope resolution
  -> Market Intelligence research-inspection API
  -> deterministic readiness, coverage and export services
```

The MCP adapter should remain a thin anti-corruption layer. It should translate bounded tool arguments to Gateway contracts, preserve correlation and idempotency keys, enforce response-size/time limits and never contain authorization logic beyond input validation.

### Capability 1: readiness and coverage inspection

Expose a read-only endpoint/tool for an authorized scope with:

- canonical markets and provider identities;
- requested interval and effective cutoff;
- available sample counts by market/timeframe;
- provenance completeness and freshness status;
- missing, stale, contradictory and post-cutoff evidence counts;
- supported capability/reconstruction policy status;
- deterministic verdict such as `READY`, `INSUFFICIENT`, `BLOCKED` or `UNKNOWN`;
- stable diagnostic identifier and correlation ID.

It must not return arbitrary database rows or imply suitability for production training. Exact owner/agent scope must be checked before revealing whether the requested research resource exists.

### Capability 2: controlled dataset export

Export should be an asynchronous, idempotent job when the output can exceed a bounded response. The request should contain:

- authorized research scope ID;
- explicit market/time interval and cutoff, constrained by the scope;
- dataset profile/version;
- maximum rows/bytes and execution deadline;
- idempotency key.

The job result should expose status, deterministic validation summary, artifact metadata, checksum and provenance. Raw provider payloads and credentials must remain behind existing service boundaries. Export must reject post-cutoff or provenance-invalid data according to the existing dataset policy rather than silently repairing it.

### Capability 3: historical reconstruction, deferred

Reconstruction should remain a separate capability. It may create derived artifacts, consume substantial resources and alter the evidence set visible to later research. It therefore needs:

- an explicit reconstruction policy and version;
- immutable source cutoff and source provenance;
- a deterministic allowlist of reconstructable capabilities;
- bounded compute, rows, bytes and duration;
- separate audit events and artifact lineage;
- explicit human approval or a stronger pre-authorized grant;
- cancellation, expiry and unknown/failure handling.

It must never rewrite canonical market facts or turn reconstructed evidence into proof of real historical availability.

## Authorization Recommendation

Do not overload `research:run` with all historical operations.

Recommended permission vocabulary for the next Story:

| Permission | Resource | Initial use |
|---|---|---|
| `research:inspect` | `RESEARCH_SCOPE` | Readiness, coverage and provenance diagnostics |
| `research:export` | `RESEARCH_SCOPE` | Bounded offline dataset export |
| `research:reconstruct` | `RESEARCH_SCOPE` | Deferred; reconstruction only |

The exact enum names remain an implementation detail for the approved Story, but the separation is important. A grant should also carry bounded market IDs, interval/cutoff constraints and output/compute limits. Agent access must be checked for every request, not only when the scope is first resolved.

Research reads should use a separate read permission rather than treating `research:run` as implicit read access. This follows least privilege and prevents an agent authorized to launch one narrow research job from enumerating all historical artifacts.

## Approval Decision

### No approval required

Read-only readiness and coverage inspection is safe to allow through a valid, bounded `research:inspect` grant. It must not mutate canonical evidence or broker state.

### Approval can be deferred

Bounded export can be implemented without the existing human approval workflow if it is explicitly grant-authorized, resource-limited, idempotent, auditable and produces only validated offline artifacts. This is preferable to coupling the first Story to the account-synchronization-specific operation model.

### Approval required later

Historical reconstruction should not be included in the first implementation unless the human approval semantics are made explicit. The existing operation lifecycle is a possible foundation, but it currently hard-codes account synchronization and broker-account resources. A later Story should either extend it deliberately or introduce a research-specific operation lifecycle; it should not silently generalize the current code.

## API and MCP Shape

Suggested public API shape, subject to contract review:

- `POST /api/v1/intelligence/research/scopes/{scopeId}/readiness`
- `POST /api/v1/intelligence/research/scopes/{scopeId}/exports`
- `GET /api/v1/intelligence/research/exports/{exportId}`
- future: `POST /api/v1/intelligence/research/scopes/{scopeId}/reconstructions`

Suggested MCP tools:

- `inspect_historical_research_readiness`
- `request_historical_dataset_export`
- `get_historical_dataset_export_status`

The adapter should not expose reconstruction until its authorization and approval contract exists. Tool responses should preserve explicit `INSUFFICIENT`, `BLOCKED`, `UNKNOWN`, expired-grant and authorization-unavailable states.

## Threats and Mitigations

| Threat | Mitigation |
|---|---|
| Human JWT reuse or impersonation | Dedicated agent credential exchange; reject human principals on agent routes |
| Scope widening | Server-side exact grant/resource/market/interval checks on every request |
| Historical data leakage | Do not reveal resource existence before authorization; bounded aggregate diagnostics |
| Unbounded export or denial of service | Grant-level row/byte/time limits, async jobs, quotas and idempotency |
| Cutoff contamination | Immutable cutoff in request and artifact; reject post-cutoff evidence |
| Provenance laundering | Preserve source identity, acquisition time, provider and reconstruction lineage; expose missing provenance |
| Stale/revoked authorization | Reauthorize on request and job execution; capture grant and credential versions |
| Artifact substitution | Checksums, immutable artifact metadata and owner/agent-scoped retrieval |
| AI authority expansion | Keep AI advisory; deterministic Java services own authorization, validation and lifecycle |
| Audit failure hiding execution | Persist lifecycle as authoritative; record audit failure and do not claim successful completion without state evidence |

## Alternatives

### A. Direct MCP-to-Market-Intelligence extension

Rejected. It would bypass the established Trading Core grant/resource authorization boundary and make scope ownership harder to audit.

### B. Generic agent operation framework first

Deferred. It could unify approval and asynchronous operations, but it expands scope and risks generalizing an account-synchronization-specific implementation before research semantics are known.

### C. Gateway-authorized research inspection and export

Recommended. It reuses existing identity, grants, scope resolution, research lifecycle, export validation, provenance and MCP patterns while keeping reconstruction and approval semantics explicit future work.

## Proposed Story 0222 Scope

### Goal

Allow an explicitly authorized agent to inspect historical research readiness and request bounded, validated offline dataset exports through the Gateway and MCP adapter without gaining broker, database, execution or autonomous reconstruction authority.

### In scope

- Add separate read/export research permissions and grant compatibility.
- Extend research scope constraints with bounded interval/cutoff and export limits.
- Add deterministic readiness/coverage/provenance diagnostic contract.
- Add bounded asynchronous export request/status lifecycle with idempotency.
- Record agent, owner, grant, credential, cutoff, scope and artifact provenance.
- Add Gateway routes and MCP adapter tools.
- Add authorization, leakage, cutoff, limit, revocation, idempotency and integration tests.

### Out of scope

- Historical reconstruction or backfilling provider data.
- Generic operation approval framework.
- Broker synchronization, order execution, positions or risk decisions.
- Direct database access from MCP or Python.
- AI-generated readiness decisions or autonomous dataset planning.
- ML training, model serving, profitability claims or production prediction APIs.

### Acceptance criteria

1. An agent without the exact scope grant receives a non-leaking authorization failure.
2. A valid agent can inspect readiness only for the granted markets, interval, cutoff and owner scope.
3. Readiness distinguishes ready, insufficient, blocked and unknown evidence states.
4. Export requests are idempotent, bounded and asynchronous where required.
5. Export artifacts include checksum, dataset/profile version, cutoff and complete source provenance.
6. Post-cutoff, stale, contradictory or provenance-incomplete evidence is not silently promoted to valid training data.
7. Revoked or expired grants prevent new requests and prevent unauthorized job retrieval.
8. No endpoint or MCP tool reaches an internal service or broker directly.
9. No operation creates, amends, cancels or closes a broker order or position.
10. The implementation does not introduce reconstruction or a generic approval engine.

## Validation Expectations

- Trading Core authorization and scope tests.
- Market Intelligence readiness, export, cutoff and provenance tests.
- Gateway authentication/routing tests.
- MCP adapter contract and bounded-response tests.
- Integration tests for grant revocation, idempotency, owner isolation and resource non-disclosure.
- `./scripts/test-all.sh` and `./scripts/quality-verify.sh` where the implementation changes the covered modules.
- Runtime validation with an explicitly authorized agent credential should be a human-approved follow-up; no credential was generated or accessed during this investigation.

## Constraints and Open Decisions

- Confirm the canonical resource identifier for historical research scopes before implementation; do not use market IDs alone as the authorization resource.
- Decide whether export artifacts are persisted by Market Intelligence or delegated to an existing artifact store; preserve current service ownership.
- Define the exact maximum interval, rows, bytes and job duration as product/security constraints rather than agent defaults.
- Decide whether export permission is pre-authorized or requires a future human approval request; this is not needed for read-only readiness.
- Define whether incomplete provenance makes readiness `INSUFFICIENT` or `BLOCKED` for each dataset profile.

## Conclusion

The narrowest safe next step is Gateway-backed, grant-scoped historical readiness inspection plus bounded export. The platform already contains most required primitives. The missing work is contract integration and explicit authorization vocabulary, not a new agent architecture. Reconstruction and human approval should remain separate until their persistence, lineage, resource and review semantics are approved.
