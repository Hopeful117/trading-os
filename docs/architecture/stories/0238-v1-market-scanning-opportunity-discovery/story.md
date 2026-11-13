# Epic 0238 - V1 Market Scanning and Opportunity Discovery

## Metadata

**ID:** `0238`

**Title:** V1 Market Scanning and Opportunity Discovery

**Status:** APPROVED - architecture and planning baseline; implementation not authorized

**Priority:** P1 - Trading Intelligence

**Type:** Engineering Epic Group

**Related ADR:** `ADR-102` - Passive Market Discovery and Active Opportunity Investigation Architecture

**Related Stories:** `0239`, `0240`, `0241`, `0242`, `0243`, `0244`

**Predecessors:** `0005`, `0006`, `0007`, `0021`, `0022`, `0235`, `0236`

## Goal

Deliver bounded global passive market monitoring that produces explainable,
evidence-backed awareness and lets a trader explicitly launch the existing
account-aware Active Scanner for deeper investigation.

## Product Value

The trader can see which configured markets have meaningful deterministic
changes without manually launching a scan across every account. The trader then
chooses whether to investigate a market using the existing active workflow.

## Architectural Outcome

```text
Configured global universe
    -> durable bounded PassiveScanRun
    -> AnalysisExecution(PASSIVE)
    -> Observation / MarketThesis / optional PASSIVE_SCAN opportunity
    -> dashboard awareness projection
    -> existing ActiveScan with account revalidation
```

## Story Sequence

1. `0239` - Define the bounded passive market universe.
2. `0240` - Persist the PassiveScanRun and per-market lifecycle.
3. `0241` - Add bounded recurring dispatch, claims and reconciliation.
4. `0242` - Expose passive awareness with freshness and provenance.
5. `0243` - Connect passive candidates to the existing ActiveScan workflow.
6. `0244` - Add the trader-facing passive awareness dashboard.

## Cross-Epic Constraints

- Preserve ADR-033 and accepted ADR-102.
- Reuse existing deterministic analysis and persistence boundaries.
- Do not create a new service or generic workflow framework.
- Do not require AI, historical backfill or account synchronization.
- Do not create trading authority or broker operations.
- Do not interpret passive opportunities as account-authorized trades.
- Do not introduce global cross-market ranking semantics.
- Keep all Stories proposed until separately approved.

## End-to-End Definition of Value

With a deterministic fixture universe containing EUR/USD CFD and BTC/USD with
their exact provider/product identities, the system can create a bounded
passive run, persist explicit evidence outcomes, project a fresh passive
candidate when the existing strategy pipeline produces one, and let a trader
launch the existing ActiveScan for an authorized account. The demonstration
must work without AI and must not mutate broker or trading state.

## V1 Acceptance Metrics

Engineering reliability metrics:

- no duplicate logical passive execution per run and market;
- no overlapping execution beyond configured concurrency;
- bounded retry count and provider request consumption;
- restart recovery rate for unfinished runs;
- truthful terminal and partial-result rate;
- passive-to-active provenance completion rate.

Product usefulness metrics:

- deterministic fixture event detection correctness;
- zero candidate flood from repeated equivalent detections;
- candidate freshness and expiration correctness;
- explicit stale or insufficient-evidence escalation rate;
- active investigations that preserve the selected canonical market;
- active investigations that truthfully produce either an opportunity or no opportunity.

These metrics evaluate correctness and usefulness. They do not claim
profitability, win probability or strategy quality.

## Dependencies

The Stories are intentionally sequenced. `0239` establishes configuration and
identity. `0240` depends on it for immutable run snapshots. `0241` depends on
the persisted lifecycle. `0242` depends on durable results. `0243` depends on
the candidate projection and existing ActiveScan contract. `0244` depends on
the backend read and command contracts.

## Non-Goals

- autonomous execution;
- user-customizable watchlists;
- per-account passive monitoring;
- notifications;
- AI escalation;
- research hypothesis generation;
- historical data preparation;
- strategy discovery or promotion.

## Approval Record

The project owner approved Epic 0238 as the architectural and planning baseline
on 2026-11-13. Approval covers the documented scope and sequence of Stories
0239-0244. It does not approve or authorize implementation of any individual
Story, scheduler activation, schema migration, runtime scan, broker operation,
AI execution, branch creation, commit or push.

Each Story retains its own lifecycle, Repository Analysis, Implementation Plan
and human approval gates.

## Approval Boundary

This epic group is an approved planning baseline. No implementation, schema
migration, configuration activation, runtime scan, broker operation, branch
creation, commit or push is authorized by this document.
