# Engineering Report

## Story

Story 0006 — Persistent Active Scan Orchestration Foundation.

## Final Status

Implemented locally on branch
`story/0006-persistent-active-scan-orchestration-foundation`.

Story 0006 now persists actor-owned `ActiveScan` orchestration above
single-market `AnalysisExecution`, composes Story 0005 scope resolution
directly, preserves deterministic eligibility authority, and durably separates
child registration from dispatch.

Final security verification identified a trust-boundary defect in the local
runtime topology: `market-intelligence` accepted trusted actor identity from
Gateway correctly, but the service was also directly host-exposed on `8084`
without local Spring Security. The approved correction was applied by removing
normal host publication of `market-intelligence` from `docker-compose.yml`
while preserving Docker-network, Eureka, and Gateway connectivity.

No commit, push, merge, or pull request was performed.

## Delivered Architecture

```text
Authenticated external client
    -> Gateway :8080
    -> JWT validation
    -> AuthenticatedActorHeaderFilter overwrites X-Actor-Id from principal UUID
    -> market-intelligence on internal Docker network only
    -> ActiveScan persists actor-owned orchestration state
    -> eligible markets register or reuse exactly one logical AnalysisExecution(ACTIVE)
    -> durable linkage commits before any dispatch handoff
```

Story 0006 preserves:

- Story 0005 scope authority;
- single-market `AnalysisExecution` ownership;
- `PipelineRun` provenance ownership;
- no Risk Domain, broker, or Passive Scanner redesign;
- no cross-market ranking, AI authority, or result aggregation.

## Security Correction

### Root Cause

`market-intelligence` had become an actor-owned API surface through
`ActiveScan.actorId`, actor-scoped idempotency, and owned scan reads, but
remained published on host port `8084`. Because the service itself does not
perform JWT validation, an external caller could bypass Gateway and forge
`X-Actor-Id`.

### Selected Correction

Remove direct host exposure from the normal Docker Compose runtime:

- before: `0.0.0.0:8084->8084/tcp`
- after: internal-only `8084/tcp`

Rejected alternative:

- adding local Spring Security to `market-intelligence`

Reason for rejection:

- unnecessary duplication of authenticated entry responsibility already owned
  by Gateway;
- larger Story 0006 scope expansion than required to close the verified
  trust-boundary defect.

### Verified Runtime Boundary

After correction:

- direct host access to `localhost:8084` is refused;
- Gateway-authenticated traffic still reaches
  `/api/v1/intelligence/scans/{scanId}`;
- spoofed authenticated `X-Actor-Id` is overwritten by Gateway-derived actor
  UUID;
- `market-intelligence` remains registered in Eureka and reachable on the
  internal Docker network.

## Files

### Production Change for Final Security Correction

- `docker-compose.yml`

### Documentation Updated

- `docs/architecture/stories/0006-persistent-active-scan-orchestration-foundation/implementation-report.md`
- `docs/architecture/stories/0006-persistent-active-scan-orchestration-foundation/engineering-report.md`

## Validation Summary

Validated during final security correction:

- `docker compose config`
- Gateway security tests:
  `AuthenticatedActorHeaderFilterTest`,
  `GatewayApplicationTests`,
  `GatewayRiskEvaluationRouteTest`
- Focused Story 0006 tests in `market-intelligence`
- Full `market-intelligence` suite with `-Dserver.port=0`
- Runtime verification of:
  - blocked `localhost:8084`;
  - authenticated Gateway route to `/api/v1/intelligence/scans/...`;
  - spoofed actor-header overwrite;
  - Eureka registration and internal service reachability.

## Final Recommendation

Story 0006 is ready for human commit approval once the final runtime/test
evidence and diff review are accepted. The trust boundary now matches the
approved actor-ownership model: external access to actor-owned ActiveScan
endpoints flows through authenticated Gateway traffic only.
