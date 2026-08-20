# Story 0005 — Account-Aware Active Scan Scope Resolution

## Metadata

**ID:** `0005`
**Title:** Account-Aware Active Scan Scope Resolution
**Status:** Draft

## Goal

Resolve the smallest deterministic slice required to validate the ADR-033 Active Scanner boundary before introducing `ActiveScan` orchestration.

This Story makes Active Scan scope resolution account-aware:

`User objective`
+ `selected account`
+ `optional requested market scope`
↓
`candidate markets`
↓
`deterministic hard eligibility`
↓
`effective scan scope`

## Context

ADR-033 establishes that Active Scanner is intention-driven and that market eligibility must be resolved before expensive targeted analysis.

The repository already contains:

- a single-market `AnalysisExecution` flow;
- active and passive analysis strategies;
- market-data contributors;
- deterministic capabilities;
- observations;
- opportunities;
- trade-planning and risk handoff.

What the repository does not yet contain is an account-aware scope-resolution step that can decide which markets are eligible to be scanned for the selected trading context.

This Story validates that boundary without introducing:

- a persisted `ActiveScan` aggregate;
- multi-market `AnalysisExecution` orchestration;
- cross-market ranking;
- AI relevance;
- risk evaluation duplication.

## Problem

Trading OS can already analyze one market at a time, but it cannot yet deterministically answer:

> Given this selected account and this requested scope, which markets are eligible to be scanned right now?

Without that step, ADR-033 cannot be validated incrementally.

The next slice should not jump directly to multi-market orchestration.
It should first prove the scan boundary:

`Intent` → `Trading Context` → `Eligibility` → `Effective Scope`

## Scope

This Story introduces account-aware scan scope resolution for Active Scanner.

Included:

- resolve a candidate market universe from existing market data;
- verify selected account ownership against authoritative trading-core data;
- incorporate broker-account ownership / usability where current repository facts support it;
- apply deterministic hard eligibility checks before deep analysis;
- emit an explicit effective scan scope with exclusion diagnostics;
- keep passive scanning independent from the selected account;
- expose the resolution result through a small dedicated API or equivalent application-service entry point.

## Out of Scope

- `ActiveScan` aggregate persistence;
- orchestration of `N AnalysisExecution` instances;
- Passive Scanner implementation;
- scheduler/background scanner jobs;
- AI relevance or AI recommendations;
- global cross-market ranking;
- `OpportunityScore` normalization;
- trade planning changes;
- risk engine changes;
- broker execution changes;
- frontend scanner UI;
- watchlists;
- news integration;
- order-flow analysis;
- Quant/V2 research features.

## Acceptance Criteria

- [ ] An authenticated user can resolve an active-scan scope for an owned account context.
- [ ] The resolver uses authoritative account/broker facts, not caller-provided account state.
- [ ] The resolver uses the Trading OS market catalog as the candidate universe source.
- [ ] Requested markets that do not exist are excluded with a deterministic reason.
- [ ] Markets that are not currently tradable are excluded with a deterministic reason.
- [ ] The resolver does not invoke the Risk Domain.
- [ ] The resolver does not create `AnalysisExecution` records.
- [ ] The resolver does not create `PipelineRun` records.
- [ ] Duplicate requested markets do not create duplicate scope entries.
- [ ] The output preserves deterministic exclusion diagnostics.
- [ ] The output does not claim any cross-market ranking semantics.
- [ ] Passive analysis behavior remains unchanged.
- [ ] Relevant targeted tests pass.

## Constraints

- Preserve single-market `AnalysisExecution` semantics.
- Preserve existing `PipelineRun` semantics.
- Preserve deterministic authority for account, market and risk rules.
- Do not duplicate final Risk Domain authority inside scanner scope resolution.
- Do not introduce an AI authority layer.
- Do not persist scan state unless it is strictly required for this slice.
- Keep the slice focused on eligibility and effective scope only.

## Relevant ADRs

- `ADR-020` Market Intelligence Architecture
- `ADR-023` Capability Execution Model
- `ADR-025` Observation Model
- `ADR-026` Trading Opportunity Model
- `ADR-028` Risk Engine Architecture
- `ADR-030` Broker Service Architecture
- `ADR-032` Represent Trade Plan Entry Intent Explicitly
- `ADR-033` Active and Passive Market Intelligence Orchestration

## Relevant Modules

- `market-intelligence`
- `market-data`
- `trading-core`
- `gateway`

## Validation

Expected validation for this Story:

- targeted `market-intelligence` tests for scope resolution and API handling;
- targeted `market-data` tests covering market catalog and tradability;
- targeted `trading-core` tests covering account and broker-account ownership checks;
- verification that the Risk Domain is not invoked;
- verification that passive analysis behavior is unchanged;
- manual review of the diff before approval.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved.
- [ ] Implementation completed.
- [ ] Relevant tests pass.
- [ ] Code review approved.
- [ ] Human commit created.
