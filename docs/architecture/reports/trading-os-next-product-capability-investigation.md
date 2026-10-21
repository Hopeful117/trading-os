# Trading OS Next Product Capability Investigation

**Date:** 2026-10-20  
**Scope:** Read-only product-capability selection after the completed
account-scoped Decision Workspace  
**Repository state:** `main` at `436d2e438f74f0e28b2cdadaf67ff18d7ae74a10`  
**Intended repository change:** This report only

## Executive Summary

The next highest-value Trading OS product capability should be:

> **Post-trade feedback and decision history V1:** make each human-authorized
> PAPER or broker execution inspectable through a durable outcome/history view
> that connects the original market evidence, Opportunity or MANUAL TradePlan,
> Risk decision, execution result, position lifecycle, and realized result when
> available.

This is not a new execution path, an AI decision-maker, or an autonomous
monitor. It completes the product's existing human decision loop and creates the
evidence needed for trustworthy review, analytics, later position intelligence,
and eventual AI assistance.

The recommendation is deliberately narrower than a generic "trading journal":
the first Story should use existing durable identifiers and authoritative
backend facts. Free-text journaling, strategy optimization, LLM synthesis,
automatic monitoring, and live execution expansion remain out of scope.

## Applicability And Evidence

DevLog applicability was checked for prior Stories, decisions, and validation
context. The Story Agent was unavailable because the configured provider
returned HTTP 429 for exhausted quota. This report therefore relies on the
repository, accepted ADRs, canonical Stories, and existing investigation
reports.

The repository is authoritative for current physical state. Historical reports
are used for intent and known gaps, with discrepancies called out below.

Relevant evidence:

- `README.md` states that the platform has PAPER provisioning, simulated entry,
  local positions, and valuation, while PAPER closure, realized settlement, and
  active position tracking remain incomplete in that summary.
- Story `0048` is closed and runtime-validated for the authenticated PAPER
  journey, including full local exposure close and the final empty-position
  state. The README statement is therefore stale or describes the broader
  settlement/history capability rather than the validated close command.
- Story `0036` establishes durable entry-side `TradeOutcome` provenance, but
  explicitly defers close association, realized PnL, close fees, and frontend
  outcome display.
- The S1 accumulation investigation identifies the missing coherent decision
  journal and incomplete trading analytics as product gaps, and recommends
  completing the PAPER position lifecycle and post-trade feedback loop before
  position monitoring recommendations or AI synthesis.
- ADR-014 keeps the decision pipeline deterministic and human-authorized.
- ADR-033 states that Active Scanner is substantially implemented while Passive
  Scanner remains architectural direction; it also lists deferred scanner
  recovery, cancellation, contextual relevance, reuse, and zero-to-many result
  concerns.
- Market Intelligence configures disabled AI adapters. No real AI Engine is
  present.
- Financial News is user-facing but providers are disabled by default. Story
  `0103B` remains proposed and gated on provider authorization and data-use
  clarification.

## Current Product Loop

The repository currently supports most of this path:

```text
market data
  -> deterministic analysis / active scan
  -> TradingOpportunity or MANUAL TradePlan
  -> deterministic Risk evaluation
  -> explicit human authorization
  -> execution attempt and broker/PAPER result
  -> open-position projection
  -> explicit close command where supported
```

The missing product-level loop is:

```text
what was proposed?
what did the human accept or reject?
what risk facts authorized or refused it?
what actually happened?
what was the resulting position and realized outcome?
what evidence should be reviewed later?
```

Existing backend identities already provide the likely join points: execution
intent, trade plan and version, opportunity and provenance, risk evaluation,
execution outcome, broker/PAPER position authority, and trade outcome.

## Candidate Comparison

Scores are relative, from 1 (low) to 5 (high). Risk means implementation or
semantic risk, not financial trade risk.

| Candidate | Trader value | Readiness | Time to value | Risk | Decision |
|---|---:|---:|---:|---:|---|
| Post-trade feedback and decision history V1 | 5 | 4 | 4 | 3 | **Select** |
| Complete deterministic Decision Workspace/provenance presentation | 5 | 4 | 4 | 2 | Already substantially addressed by Stories 0062, 0081, 0134 |
| Active Scanner hardening and contextual relevance | 4 | 3 | 3 | 3 | Important follow-up; current scan is usable but has known deferred semantics |
| Passive Market Scanner V1 | 4 | 2 | 2 | 4 | Too much orchestration and durability work for the next product slice |
| Position Monitoring Intelligence V1 | 5 | 2 | 2 | 4 | Depends on durable thesis/outcome history and must remain recommendation-only |
| Real AI Engine / AI synthesis | 4 | 1 | 1 | 5 | Defer until deterministic context, provenance, news, and outcomes mature |
| Financial News provider activation | 3 | 2 | 3 | 3 | Explicit provider authorization and selection gate unresolved |
| cTrader / prop-firm integration | 4 | 1 | 1 | 5 | Multi-broker and prop semantics are explicitly deferred |
| Research Experiment Detail | 3 | 4 | 2 | 3 | Valuable for research governance, not the highest direct trader capability |
| Generic analytics dashboard | 4 | 3 | 3 | 3 | Should follow reliable outcome and settlement association |

The selected capability has the best combination of direct trader value,
existing domain continuity, testability, and enabling value for later analytics
and intelligence. It also avoids adding a second decision or execution path.

## Why Not The Obvious Alternatives

### Real AI Engine

The current AI ports and disabled adapters are governance boundaries, not a
working capability. The S1 investigation records that deterministic context,
news/macro context, historical research, and user-facing provenance are not yet
mature enough for a first AI milestone. Adding an LLM now would risk producing
interpretation without a reliable outcome record and would make it harder to
measure whether the assistance improves decisions.

### Passive Scanner

ADR-033 explicitly leaves recurring batch orchestration, configured universe,
persisted market awareness, and recovery semantics unimplemented. This is a
substantial platform capability, not a small product increment. It should follow
clearer evidence about what market awareness the trader needs and how that
evidence will be reviewed.

### Active Scanner Expansion

Active scanning is already user-triggered and runtime-proven. The runtime report
identified a blocking POST defect and incomplete child write-back; Story 0025
closed the asynchronous dispatch issue, while ADR-033 still records deferred
restart recovery, cancellation, relevance policies, passive reuse, and
zero-to-many result semantics. Hardening is valuable, but it improves discovery
before the product can explain what happened to discovered opportunities and
executed plans.

### Position Monitoring Intelligence

Read-only position monitoring exists, including deterministic valuation and
explicit close behavior. Recommendation-only thesis or risk monitoring does
not. A monitor needs an original thesis, current evidence, durable lifecycle,
and an auditable decision history. Building it before the selected capability
would require inventing or duplicating that history.

### Financial News Provider

The V1 audit recommends Story 0103B as the next context capability only after
written provider authorization, retention/data-use approval, and single-provider
selection. That is a valid gated follow-up, but it is not currently unblocked.
Economic context is already represented separately and provider absence must
remain truthful.

## Recommended Capability Boundary

### Name

**Post-trade feedback and decision history V1**

### User outcome

After a PAPER or broker execution reaches a terminal or safely uncertain state,
the trader can open a read-only history item and understand the complete
authoritative lifecycle without reconstructing it from separate screens.

### In scope for the next Story

- Define one read-only account-scoped outcome/history contract.
- Correlate an execution outcome with its TradePlan and version.
- Preserve Opportunity/Strategy provenance when it exists and explicitly
  represent MANUAL origin when it does not.
- Show the recorded Risk decision and the execution/broker outcome without
  re-evaluating either in the frontend.
- Show position-open and position-close evidence according to the existing
  authority rules: Trading Core for PAPER and the broker projection for LIVE.
- Persist or expose realized PnL and close-fee facts only when an authoritative
  close association exists; otherwise show an explicit unavailable state.
- Provide a bounded read-only history/detail surface from the existing
  authenticated account context.
- Add tests for missing provenance, rejected Risk, failed execution, unknown
  execution, open position, closed position, and unavailable settlement facts.
- Capture runtime evidence using PAPER without weakening Risk or human
  authorization.

### Out of scope

- Automatic execution, retry, reconciliation, or position mutation.
- AI summaries, embeddings, RAG, historical similarity, or recommendations.
- Free-text journal notes or a second journal aggregate disconnected from
  existing identities.
- Strategy optimization, backtesting, win-rate claims, or profitability
  conclusions without validated outcome data.
- Changing Risk rules, broker semantics, execution-state transitions, or
  position authority.
- Passive scanning, active-scan redesign, cTrader, prop-firm rules, or LIVE
  sandbox claims.

### Required architectural decision

An ADR is likely required before implementation if the Story introduces a new
durable close-association or outcome projection model. The ADR must establish:

- the canonical outcome identity and lifecycle;
- how one provider-level close may map to multiple entry outcomes;
- the difference between observed exposure absence and proven command causality;
- PAPER versus LIVE source authority;
- unavailable versus zero realized result semantics;
- immutable provenance and versioning rules.

No ADR is required merely for a read-only composition of already authoritative
contracts if the implementation does not introduce new persistence semantics.

## Dependency Readiness

| Dependency | Current state | Consequence |
|---|---|---|
| TradePlan and version lineage | Present | Reuse existing identity and lifecycle |
| Risk decision | Present and deterministic | Render persisted result; do not recompute |
| Execution lifecycle | Present with unknown/reconciliation safety | Preserve all terminal distinctions |
| PAPER entry/position/close | Runtime-validated in Story 0048 | Use as acceptance path; do not infer from stale README text |
| LIVE position authority | Broker-authoritative | No local substitute or optimistic state |
| TradeOutcome entry provenance | Story 0036 establishes it | Extend only with approved close association |
| Realized PnL/close fees | Explicitly incomplete/deferred | Must expose unavailable until authoritative |
| Frontend account context | Story 0134 completed | Reuse canonical workspace/account scope |
| AI Engine | Disabled adapters only | Not a dependency for this Story |
| News provider | Disabled/gated | Not a dependency for this Story |

## Validation Strategy

The future Story should validate, at minimum:

1. A MANUAL PAPER plan and an OPPORTUNITY PAPER plan both preserve their
   distinct origins and converge on one history contract.
2. A rejected or unavailable Risk result produces no execution or outcome that
   claims a trade occurred.
3. An acknowledged execution is not displayed as a guaranteed fill when the
   broker state does not prove a fill.
4. An unknown execution remains unknown and exposes reconciliation state rather
   than a false failure or success.
5. A PAPER position can be opened, displayed, closed, and reviewed after reload.
6. A LIVE position and close command retain broker-authoritative semantics.
7. Missing provenance, missing close association, provider outage, and stale
   data are explicit states rather than empty success states.
8. Existing Angular tests, affected Maven tests, production build, and
   `git diff --check` pass.

## Final Recommendation

Select **Post-trade feedback and decision history V1** as the next product
capability. Create a canonical Story and, if new close-association persistence
is required, an ADR before implementation approval.

The sequencing should be:

```text
post-trade feedback/history
    -> read-only trading analytics
    -> recommendation-only position monitoring
    -> validated deterministic context enrichment
    -> AI synthesis with measurable provenance
```

This sequence improves the current human trading workflow, makes the product
learnable from its own outcomes, and preserves the project's deterministic,
human-authorized architecture.
