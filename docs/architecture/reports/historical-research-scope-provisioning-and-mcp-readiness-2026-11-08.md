# Trading OS - Historical ResearchScope Provisioning and MCP Readiness

**Date:** 2026-11-08  
**Story:** 0222 - Agent-Native Historical Research Inspection and Controlled Dataset Export  
**Final verdict:** `VALIDATION_INCOMPLETE`  
**Readiness integration verdict:** `AGENT_RESEARCH_READINESS_VALIDATED`

## 1. Executive Summary

The legitimate agent authentication and MCP readiness path was exercised once
after correcting the active agent authorization boundary.

The existing OpenCode agent already had active `research:inspect` and
`research:export` permissions, but both historical grants were attached to the
legacy `RESEARCH_SCOPE`. The new readiness request uses the broker-account
authorization path. A new active `research:inspect` grant was therefore created
through the human-controlled Agent Management workflow for the owner-owned
broker account.

One authorized readiness inspection was then executed through the existing
OpenCode MCP bridge. It returned a structured `READY` response with HTTP 200.
Authentication, grant acceptance, Gateway routing and Market Intelligence
readiness execution are operationally validated.

No new ResearchScope was created during this run. The existing ResearchScope
was not modified. Consequently, the original bounded historical ResearchScope
provisioning objective is not fully complete and remains a separate governance
and contract issue.

## 2. Reports and Baseline Reviewed

The following reports were read before the final validation:

- `historical-research-scope-mcp-readiness-blocker-investigation-2026-11-08.md`
- `story-0222-runtime-synchronization-and-agent-research-validation-2026-11-08.md`
- `agent-authentication-401-root-cause-investigation-2026-11-08.md`

Relevant prior findings were preserved:

- authentication succeeds through the dedicated agent credential flow;
- unauthorized capabilities remain default-deny;
- the legacy scope had null historical constraints;
- replay population reports `Forbidden` for an independent path;
- feature snapshot identity conflicts remain present;
- MCP error propagation previously obscured authorization failures.

## 3. Verified Runtime Baseline

| Item | Result | Classification |
|---|---|---|
| Gateway | Running and reachable | `OBSERVED` |
| Trading Core | Rebuilt/recreated; Flyway v63 | `OBSERVED` |
| Market Intelligence | Rebuilt/recreated; Flyway v25 | `OBSERVED` |
| Trading Web | Rebuilt/recreated with provisioning correction | `OBSERVED` |
| MCP bridge | Paired and connected on `127.0.0.1:17891` | `OBSERVED` |
| MCP readiness tool | Present in loaded tool list | `OBSERVED` |
| Agent token exchange | HTTP 200 | `OBSERVED` |
| Human JWT used by MCP | No | `OBSERVED` |
| Broker operations | None | `OBSERVED` |

The bridge credential remained process-local. No token or credential value was
copied into a file or exposed in this report.

## 4. Agent and Existing Permissions

### Agent

| Property | Value | Classification |
|---|---|---|
| Agent | `OpenCode` | `OBSERVED` |
| Agent ID | `ac700592-cae9-43b5-b799-c5aaa565e3a1` | `OBSERVED` |
| Status | `ACTIVE` | `OBSERVED` |
| Owner | Existing authenticated Trading OS owner | `OBSERVED` |

### Broker account

| Property | Value | Classification |
|---|---|---|
| Display name | `FTMO Free cTrader` | `OBSERVED` |
| Broker account ID | `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456` | `OBSERVED` |
| Execution mode | `LIVE` because an external broker boundary exists | `OBSERVED` |

### Grant state before correction

The existing active grants included:

- `accounts:read` on `BROKER_ACCOUNT` `7913f82b-...`;
- `positions:read` on `BROKER_ACCOUNT` `7913f82b-...`;
- `research:inspect` on `RESEARCH_SCOPE` `87a98c16-...`;
- `research:export` on `RESEARCH_SCOPE` `87a98c16-...`.

The active research permissions were therefore present, but the inspect grant
did not match the broker-account resource requested by the new readiness path.

## 5. Provisioning Workflow Assessment

The public ResearchScope creation API supports historical constraints including:

- earliest and latest permitted cutoffs;
- maximum observations;
- maximum export rows, bytes and execution duration;
- allowed dataset profiles.

The Angular Agent Management UI does not expose those historical constraint
fields. It can create an ordinary provider-universe scope and grants, but it
cannot create a fully bounded historical scope from the UI alone.

The existing scope has no supported update endpoint and was not modified.
Direct SQL provisioning was not used.

Classification: `CODE_CONFIRMED`.

## 6. Proposed Scope Configuration

No new scope was created. The following is the bounded configuration that would
be required for a separate approved scope-provisioning operation:

| Property | Proposed value | Classification |
|---|---|---|
| Owner | Existing authorized owner | `OBSERVED` |
| Agent | `ac700592-cae9-43b5-b799-c5aaa565e3a1` | `OBSERVED` |
| Scope purpose | Historical research inspection | `INFERRED` |
| Broker account | `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456` | `OBSERVED` |
| Canonical market | `8b336ec3-1557-4432-b052-ae569e041dac` | `OBSERVED` |
| Cutoff start | `2026-11-01T00:00:00Z` | `OBSERVED` request value |
| Cutoff end | `2026-11-08T20:00:00Z` | `OBSERVED` request value |
| Dataset profile | `feature-projection-v1` | `OBSERVED` request value |
| Maximum observations | `1000` | `OBSERVED` request value |
| Grant | `research:inspect` only | `OBSERVED` |

The actual readiness request used the broker-account authorization path and did
not create or mutate this proposed scope.

## 7. Human Approval and Actions

Explicit human approval was received to correct the existing agent permission:

> `ok corrige ca`

This approval authorized the corrective grant operation. It did not authorize
creation or mutation of a ResearchScope, and no ResearchScope provisioning was
performed.

The official Agent Management workflow was used after rebuilding the web
service. The resulting persistent change was:

```text
research:inspect
resourceType: BROKER_ACCOUNT
resourceId: 7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456
status: ACTIVE
```

The existing legacy scope grants were not revoked or reactivated. The existing
`research:export` grant was not broadened to the broker account; export was not
executed.

## 8. Grant Verification

The official grant read API returned an active broker-account inspect grant:

```text
grantId: 1f964bd4-cf73-4896-8df2-1a8b774407a8
agentId: ac700592-cae9-43b5-b799-c5aaa565e3a1
permission: research:inspect
resourceType: BROKER_ACCOUNT
resourceId: 7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456
status: ACTIVE
grantVersion: 0
```

Classification: `OBSERVED`.

## 9. MCP Readiness Invocation

Exactly one readiness inspection was executed after the corrected grant was
persisted.

Tool:

```text
inspect_historical_research_readiness
```

Request parameters:

```json
{
  "broker_account_id": "7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456",
  "market_ids": ["8b336ec3-1557-4432-b052-ae569e041dac"],
  "cutoff_start": "2026-11-01T00:00:00Z",
  "cutoff_end": "2026-11-08T20:00:00Z",
  "dataset_profile": "feature-projection-v1",
  "max_observations": 1000
}
```

The request used the dedicated agent credential through MCP, Gateway and
Trading Core authorization. No export, reconstruction, backfill, broker read or
write operation was triggered by this validation.

## 10. Structured Readiness Result

Result: HTTP `200 OK`, verdict `READY`.

```json
{
  "verdict": "READY",
  "marketIds": ["8b336ec3-1557-4432-b052-ae569e041dac"],
  "requestedCutoffStart": "2026-11-01T00:00:00Z",
  "requestedCutoffEnd": "2026-11-08T20:00:00Z",
  "effectiveCutoffStart": "2026-11-01T00:00:00Z",
  "effectiveCutoffEnd": "2026-11-08T20:00:00Z",
  "availableEvidenceCount": 1,
  "featureSnapshotCount": 1,
  "eligibleOutcomeCount": 1,
  "postCutoffEvidenceCount": 0,
  "earliestEligibleEvidence": "2026-11-06T16:45:00Z",
  "latestEligibleEvidence": "2026-11-06T16:45:00Z",
  "evidenceByMarket": {
    "8b336ec3-1557-4432-b052-ae569e041dac": 1
  },
  "outcomesByHorizonAndStatus": {
    "PT1H:AVAILABLE": 1
  },
  "featureProjectionVersion": "feature-projection-v1",
  "featureProvenanceStatus": "VALID",
  "outcomeMaturityStatus": "AVAILABLE",
  "blockers": []
}
```

The response did not include a correlation ID (`null` in the actual response).

Classification: `OBSERVED`.

## 11. Historical Population Findings

The bounded request had:

- one authorized canonical market;
- one available evidence record;
- one feature snapshot;
- one eligible outcome;
- one available one-hour outcome horizon;
- no post-cutoff evidence;
- valid feature provenance;
- no readiness blockers.

This demonstrates technical readiness for the requested population. It does not
establish that the population is sufficient for a meaningful ML experiment.
Technical workflow readiness, dataset contract validity and ML experiment
eligibility remain separate states.

## 12. Secondary Issues Preserved

The following issues were observed but were not repaired as part of scope or
grant provisioning:

- replay artifact population continues to report `Forbidden`;
- feature snapshot identity conflicts remain in scheduled research projection;
- the MCP adapter still maps non-success Gateway responses to a generic tool
  error rather than preserving all structured error bodies;
- the readiness persistence path required a JPA enum-to-string correction during
  validation and was rebuilt before the successful call;
- host disk capacity remains a known operational concern from the prior report.

The replay and feature-snapshot warnings were not causal blockers for the
successful one-market readiness response.

## 13. Remaining Blockers and Risks

1. A new bounded historical ResearchScope was not provisioned. The current
   broker-account inspect path accepted the request without proving persisted
   historical scope constraints. This contract difference requires an explicit
   architectural/product decision before claiming full bounded-scope compliance.
2. The active legacy `research:inspect` scope grant remains present. It was not
   revoked because that was outside the approved corrective action.
3. The result contains only one eligible observation. It is not a sufficient
   basis for model training or performance claims.
4. Export was not requested and remains unvalidated.

## 14. Recommended Next Action

Create a separate human-approved operational task to provision a new
ResearchScope through the public API with explicit historical constraints, then
decide whether inspect authorization should require that scope or the current
broker-account path. Do not mutate the legacy scope or broaden export access as
an implicit follow-up.

## 15. Final Classification

```text
AGENT_RESEARCH_READINESS_VALIDATED
VALIDATION_INCOMPLETE
```

The end-to-end agent-native readiness integration is operationally validated,
but the original bounded ResearchScope provisioning objective is not complete.
