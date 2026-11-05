# ADR-087 - Fresh Snapshot Reuse and Refresh Coordination

## Status

Accepted

## Date

2026-11-05

## Decision Type

Authoritative broker snapshot reuse, refresh coordination and T0 availability

## Related ADRs

- ADR-028 - Deterministic Risk Engine Architecture
- ADR-074 - cTrader Authoritative READ-ONLY Synchronization Boundary
- ADR-084 - LIVE Financial Facts and Provider Preflight Readiness
- ADR-086 - Evidence-Based Risk Evaluation

## 1. Context

Broker Service protects cTrader synchronization with a provider request cooldown.
The current T0 path requests a new broker snapshot as part of every evaluation.
At the same time, automatic or UI-triggered synchronization can acquire the
cooldown lease first. T0 then receives `BROKER_SNAPSHOT_DEFERRED`, even when a
recent authoritative snapshot already exists.

This creates a race between operational synchronization and risk evaluation. It
does not improve evidence quality, because T0 is still required to consume a
coherent, owner-bound and sufficiently fresh snapshot.

## 2. Problem

The system currently conflates two distinct operations:

```text
read the latest authoritative snapshot
refresh the provider snapshot
```

That conflation causes:

- T0 starvation when the automatic synchronizer owns the cTrader cooldown;
- duplicate provider reads for the same account and observation window;
- unnecessary dependency failures despite a usable fresh snapshot;
- unpredictable validation timing for FTMO cTrader accounts.

## 3. Proposed Decision

Trading Core SHALL evaluate T0 from the latest persisted provider-neutral
snapshot when that snapshot is authoritative, owner-bound, coherent and within
the configured T0 freshness limit.

T0 SHALL NOT force a new provider snapshot merely because an evaluation starts.

Broker Service SHALL remain responsible for provider synchronization and SHALL
continue to enforce provider cooldowns and request safety limits.

Automatic, manual and on-demand refreshes SHALL coordinate through a single
refresh ownership mechanism. At most one refresh may be active for an external
account, and concurrent callers SHALL either reuse the resulting snapshot or
receive an explicit in-progress/deferred outcome.

If no persisted snapshot satisfies the T0 freshness and evidence requirements,
T0 SHALL fail closed with an explicit stale or unavailable reason. It SHALL NOT
use stale facts, inferred values, numeric fallbacks or partial facts as current
risk evidence.

Expected margin for a proposed LIVE order remains a separate provider-authoritative
preflight capability. Snapshot reuse SHALL NOT bypass its own freshness,
identity, safety or provider validation requirements.

## 4. Safety Invariants

```text
T0_CURRENT_SNAPSHOT_MUST_BE_AUTHORITATIVE = true
T0_CURRENT_SNAPSHOT_MUST_BE_FRESH = true
T0_SNAPSHOT_OWNER_AND_ACCOUNT_IDENTITY_MUST_MATCH = true
T0_MUST_NOT_FORCE_PROVIDER_REFRESH = true
REFRESH_SINGLE_FLIGHT_PER_EXTERNAL_ACCOUNT = true
STALE_OR_UNAVAILABLE_SNAPSHOT = EXECUTION_BLOCKED
PARTIAL_SNAPSHOT = EXECUTION_BLOCKED
EXPECTED_MARGIN_REMAINS_PROVIDER_AUTHORITATIVE = true
T0_REQUIREMENTS == T1_REQUIREMENTS = true
```

## 5. Consequences

### Positive

- T0 no longer races the automatic synchronization cooldown.
- Provider request volume and duplicate reads are reduced.
- Fresh persisted evidence becomes reusable across dashboard, monitoring and
  risk evaluation consumers.
- Fail-closed behavior remains explicit when evidence is stale or incomplete.
- Runtime validation becomes deterministic with respect to snapshot freshness.

### Costs and Risks

- Snapshot persistence must expose freshness, completeness, identity and
  observation metadata reliably.
- Refresh coordination requires explicit ownership and in-progress semantics.
- T0 must distinguish `SNAPSHOT_STALE`, `SNAPSHOT_UNAVAILABLE` and
  `SNAPSHOT_REFRESH_IN_PROGRESS` rather than collapsing them into one error.
- Tests must cover scheduler/T0 concurrency and restart behavior.

## 6. Rejected Alternatives

### Bypass the cTrader cooldown for T0

Rejected because it weakens provider safety controls and can increase provider
request volume without improving evidence quality.

### Let T0 use the last snapshot regardless of age

Rejected because stale broker facts cannot authorize current risk evaluation or
execution.

### Disable automatic synchronization during manual T0 validation

Rejected as an operational workaround. It does not provide a durable product
behavior and makes availability depend on operator timing.

### Treat a deferred refresh as an immediately unavailable account

Rejected when a persisted snapshot is still fresh and authoritative. A refresh
defer should not invalidate usable evidence.

## 7. Scope Boundary

This ADR does not change FTMO thresholds, risk rules, execution authorization,
provider account identity, cTrader request limits or the separate Expected Margin
contract. It defines how existing authoritative snapshot evidence is selected
and how refresh consumers coordinate.
