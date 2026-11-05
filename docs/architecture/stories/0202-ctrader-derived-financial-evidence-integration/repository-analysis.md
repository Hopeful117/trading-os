# Repository Analysis - Story 0202

## Repository State

- Branch: `main`, aligned with `origin/main` before this Story artifact was
  created.
- The worktree is intentionally dirty with uncommitted implementation and
  documentation changes from Stories 0198-0201.
- Those changes include Broker Service, Trading Core, Angular, Flyway V52/V53
  and Story-local artifacts. They must not be reset, cleaned or rewritten.
- Story 0202 did not previously exist. This analysis is the first workflow
  artifact after creating its `story.md`.
- `TOOLS.md` was not present in the repository, so no repository-local DevLog
  mapping or lifecycle command could be resolved.

## Context Sources

Read and reconciled:

- ADR-082 - Provider-Independent CFD Financial Model and Mark-to-Market
  Accounting.
- ADR-084 - LIVE Financial Facts and Provider Preflight Readiness.
- ADR-085 - Broker Equity Resolution and Provenance Contract.
- ADR-086 - Evidence-Based Risk Evaluation.
- Story 0197 implementation report.
- Story 0200 implementation report.
- Story 0201 implementation report.
- Current Broker Service and Trading Core contracts.

DevLog was queried twice with the same idempotency key and targeted file list;
both attempts timed out. Repository evidence is therefore authoritative for
current physical state, with no usable DevLog context available.

The Obsidian vault was not consulted. This Story is governed by the repository
ADRs and current provider-neutral contracts; no transverse vault context was
needed for this analysis.

## Current Implementation

### Equity

`broker-service/.../CTraderDerivedEquityExperiment.java` already provides the
deterministic candidate:

```text
balance + sum(netUnrealizedPnl)
```

It validates account identity, valuation asset, balance, collection
availability, position/PnL identity coverage, timestamps, freshness, collection
window, duplicate identities and balance-version stability when comparing two
observations. It returns `CTRADER_DERIVED_EQUITY_V1`, hypothesis
`BALANCE_PLUS_NET_UNREALIZED_PNL_V1` and validation status `UNVERIFIED`.

The implementation explicitly records that swap, commission and mirroring
commission are not included. Its result is already mapped as a separate
`DerivedEquityFactDto` in `BrokerAccountFactsV2Dto` and persisted/projected by
Story 0201 without populating authoritative equity.

The empty-position case is covered by the existing calculator only when the
position and PnL collections are authoritative and complete. Arithmetic
equality with balance does not establish provider-authoritative equity.

### Margin

The existing cTrader margin capability implements provider-authoritative
proposed-order margin through `ProtoOAExpectedMarginReq/Res` and the existing
`MarginCapability` boundary. Trading Core consumes this through
`RequiredMarginPort` and preserves source, version, timestamp and evidence
metadata.

This is required margin for a proposed order. It is not current account used
margin, position margin, free margin or execution authorization.

Current cTrader observation mapping preserves account-level `usedMargin` and
`freeMargin` only when the provider response supplies them. The reference
account currently exposes neither as an authoritative current fact.

### Evidence and Admission

The provider-neutral contracts already distinguish:

- `BROKER_REPORTED`, `BROKER_DERIVED` and `UNAVAILABLE` equity provenance;
- availability, freshness, completeness and coherence;
- derivation version, hypothesis and bounded diagnostics;
- per-invariant risk evidence requirements;
- diagnostic partial evaluation versus fail-closed aggregate authorization.

`RiskFactRequirements.from(...)` currently defaults FTMO equity admission to
`BROKER_REPORTED` and requires an equity contract for equity-dependent rules.
`ModeAwareRiskFactsProvider` rejects missing, stale, incoherent or inadmissible
equity before Risk Domain evaluation. This is consistent with ADR-086 and must
not be weakened as part of the first implementation pass.

## Architectural Constraints

ADR-082 states that LIVE CFD margin is an authoritative required/used margin
fact and explicitly rejects deriving margin as `notional / leverage` without a
separate approved decision. ADR-084 further states that account-level used
margin must not be obtained by summing position margins or applying an
aggregation formula without a validated provider contract, and that
`freeMargin = equity - usedMargin` is not approved provider truth.

Therefore the following Story request conflicts with accepted architecture if
interpreted as a production/admissible account fact:

- implementing a validated current account used-margin derivation from
  positions, leverage, tiers or notional;
- deriving an admissible LIVE free margin from candidate equity and candidate
  used margin;
- changing FTMO admission to accept `BROKER_DERIVED` without a separately
  approved policy/contract decision.

This is a material contradiction, not a missing implementation detail. The
repository cannot safely choose an interpretation silently.

## Safe Implementation Boundary

The repository supports a safe implementation scope without changing ADRs or
policy:

1. Reuse and strengthen the existing derived-equity evidence mapping and tests.
2. Add provider-neutral evidence diagnostics for margin candidates only when
   the input contract is complete, while keeping the candidate explicitly
   non-authoritative and non-admissible.
3. Keep provider-authoritative cTrader Expected Margin as the only LIVE
   proposed-order margin path.
4. Represent used margin and free margin as unavailable unless an authoritative
   provider fact is present.
5. Propagate provenance and diagnostics through Trading Core without changing
   T0/T1 requirements or FTMO policy admission.
6. Re-run T0 only as a fail-closed validation after the cTrader cooldown; never
   bypass the safety guard.

If the desired outcome is to admit a derived equity or derived account margin
for FTMO risk, a separate accepted ADR/policy decision is required before
implementation. That decision must define the metric, derivation contract,
invariants, rejection conditions, validation evidence and T0/T1 impact.

## Affected Modules

- `broker-service`
  - cTrader observation and derived-equity experiment.
  - provider-neutral snapshot and margin capability contracts.
- `trading-core`
  - snapshot DTO mapping and persistence projection.
  - `RiskFactsProvider`, `RiskFactRequirements`, `RequiredMarginPort` and T0/T1
    orchestration.
- `trading-os-web`
  - only if evidence diagnostics require a user-facing account/readiness change.

## Validation Baseline

Existing evidence from Stories 0197-0201 includes:

- Broker Service derived-equity and cTrader snapshot tests passing.
- Trading Core synchronization, mapping, margin and valuation tests passing.
- Angular CI: 56 files and 563 tests passing.
- Angular production build passing.
- `git diff --check` passing.
- Full Trading Core Maven validation remains limited by PostgreSQL integration
  timeout behavior.
- Runtime FTMO synchronization and T0 remain fail-closed:
  `BROKER_SNAPSHOT_DEFERRED` and `CONTEXT_UNAVAILABLE / DEPENDENCY_UNAVAILABLE`.

## Risks

- Treating a mathematically consistent candidate as provider-authoritative would
  violate ADR-084/085 and could authorize an unsafe FTMO decision.
- Position-level margin aggregation may be wrong under provider account model,
  dynamic leverage, hedging/netting or instrument-specific rules.
- Free margin inherits every uncertainty in equity and used margin.
- Runtime validation may remain blocked by the provider cooldown; this does not
  block deterministic offline work.

## Workflow Status

Repository Analysis is complete and requires explicit human approval before an
Implementation Plan is created. The unresolved architectural decision is:

> Should Story 0202 remain limited to non-admissible financial evidence and
> provider-authoritative required margin, or should an ADR/policy change be
> approved to define admissibility for a specific derived metric?

No implementation files were changed for Story 0202.
