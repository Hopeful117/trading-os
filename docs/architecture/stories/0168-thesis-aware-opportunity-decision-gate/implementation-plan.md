# Implementation Plan

## Overview

Story 0168 will connect the existing immutable Market Thesis to the existing
Opportunity investigation and enforce Thesis-state eligibility before
TradePlan preparation.

The implementation preserves the following authority boundaries:

```text
Market Intelligence -> Market Thesis and Opportunity authority
Trading Core         -> account ownership and human workflow authority
Risk                 -> financial validation authority
Execution            -> explicit human authorization boundary
```

Implementation is not authorized until this plan is explicitly approved.

## Phase 1 - Exact Thesis Lineage

### Domain

Add a small provider-neutral `MarketThesisReference` value containing Thesis id
and version. Add it to `TradingOpportunity` and preserve it across:

- initial creation;
- Opportunity regeneration;
- status transitions;
- persistence reload;
- API serialization.

### Pipeline

Change `ProductionIntelligencePipeline.persistThesis` to return the exact
persisted Thesis. Pass its id/version into the Opportunity creation command.

Do not derive lineage from symbol, timestamp, latest Thesis, or analysis
execution alone.

### Persistence

Store the reference in the existing Opportunity version payload. Add a schema
column only if required by repository conventions; do not create a competing
Thesis table or duplicate Thesis content.

Legacy Opportunity rows with no reference remain readable but are not eligible
for preparation.

## Phase 2 - Opportunity Thesis Contract

Expose the exact Thesis reference and structured Thesis content through the
Market Intelligence Opportunity read contract.

The implementation may use either:

- an exact nested Thesis projection on Opportunity detail; or
- a separate exact Thesis read performed by the cockpit using the reference.

The list endpoint should avoid unbounded N+1 Thesis reads. The detail path must
never call the latest Thesis endpoint for an existing Opportunity.

Expose:

- state;
- scenario/direction/statement;
- confirmation conditions and statuses;
- invalidation conditions and statuses;
- relevant levels with roles;
- supporting evidence;
- contradicting evidence and severity;
- missing evidence and availability;
- evaluatedAt, sourceCutoff, validFrom, validUntil;
- compact provenance and fingerprint fields.

No confidence score or Angular reinterpretation is introduced.

## Phase 3 - Backend Preparation Gate

### Public Request

Extend Opportunity TradePlan preparation to carry the expected Opportunity
version. Update Angular and Trading Core clients together.

### Market Intelligence Enforcement

In `OpportunityTradePlanGenerationService`:

1. Load the requested Opportunity id/version.
2. Require `ACTIVE` status.
3. Verify account ownership and planning context as today.
4. Require an exact Thesis reference.
5. Load Thesis by id/version.
6. Verify identity and validity/freshness according to existing Thesis
   contracts.
7. Require state `CANDIDATE`.
8. Continue existing deterministic TradePlan generation only after the gate.

Return stable rejection codes for `NO_TRADE`, `WATCH`, missing lineage, stale
Thesis, expired Thesis, and version mismatch.

No discretionary override is added.

## Phase 4 - TradePlan Provenance

Extend `TradingRationale` with the exact Thesis reference. Preserve it through
TradePlan versions and expose it through:

- `TradePlanResponse`;
- `TradePlanRiskSnapshot`;
- relevant internal handoff contracts.

Do not copy the full Thesis into the TradePlan. The persisted Thesis remains
the immutable analytical source and the reference preserves exact identity.

## Phase 5 - Durable NO TRADE

### Trading Core Domain

Add a bounded account-scoped `OpportunityDecision` record with:

- actor id;
- account id;
- Opportunity id/version;
- Thesis id/version;
- decision `NO_TRADE`;
- optional rationale;
- createdAt.

### API and Persistence

Add an authenticated endpoint under the account-scoped Trading Core API.

Enforce:

- actor/account ownership;
- exact Opportunity and Thesis identity supplied by the cockpit;
- idempotent repeated submissions;
- no creation of TradePlan or Risk evaluation;
- no ability to record a decision for another account.

Expose the decision only where required by the cockpit/history workflow. Do not
merge it into execution outcome records or change existing TradePlan decision
semantics.

## Phase 6 - DecisionWorkspace

Add a reactive Thesis read model to the existing Opportunity investigation.

Visual order:

```text
Market + Thesis State
Scenario / Direction
Confirmation
Invalidation
Objective
Obstacles
Supporting Evidence
Contradictions
Missing Evidence
Freshness / Provenance
Trade / No Trade actions
```

Behavior:

- `NO_TRADE`: prepare button disabled, NO TRADE action enabled;
- `WATCH`: prepare button disabled, missing/pending conditions visible;
- `CANDIDATE`: prepare button enabled;
- blocking contradictions appear before supporting evidence;
- unavailable evidence is explicitly unavailable;
- invalidation is not mapped to Stop;
- objective is not mapped to Target;
- challenge projection remains provided by existing Risk/TradePlan flow.

Use existing Angular Observable and async-pipe patterns.

## Phase 7 - Tests

### Market Intelligence

- exact Thesis reference is created and preserved;
- Opportunity transitions preserve lineage;
- exact Opportunity/Thesis reads do not use latest substitution;
- structured Thesis response exposes all evidence groups;
- legacy/no-reference Opportunity fails closed;
- `NO_TRADE` and `WATCH` preparation fail;
- `CANDIDATE` preparation continues;
- stale/expired Thesis fails;
- TradePlan rationale retains Thesis reference.

### Trading Core

- exact Opportunity version is required;
- ownership and version mismatch are rejected;
- durable NO TRADE is created without TradePlan;
- duplicate NO TRADE is idempotent;
- wrong actor/account is rejected;
- existing Risk preflight behavior is unchanged.

### Angular

- state rendering for all three Thesis states;
- supporting, contradicting, and missing evidence;
- severity and availability rendering;
- confirmation, invalidation, objective, obstacles, freshness;
- prepare disabled for `NO_TRADE` and `WATCH`;
- prepare invoked for `CANDIDATE` only;
- NO TRADE request and success state;
- context reset when Opportunity/account/market changes;
- desktop and narrow layout behavior.

## Validation Commands

```text
mvn -q test                         # affected Market Intelligence module
mvn -q test                         # affected Trading Core module
npm test -- --runInBand             # trading-os-web, repository-equivalent command
npm run build                       # trading-os-web, repository-equivalent command
git diff --check
```

The exact frontend commands will be confirmed from the module package scripts
before execution.

## Manual Runtime Validation

When authenticated cTrader data is available:

```text
cTrader market
  -> active Opportunity
  -> exact Thesis visible
  -> state and evidence understandable
  -> NO_TRADE or WATCH preparation blocked
  -> NO TRADE can be recorded without TradePlan
```

No real order or live execution is permitted.

## Documentation Reconciliation

Update the Story implementation report and any affected canonical API or
architecture documentation. Do not modify ADR-076 unless implementation changes
authority boundaries. No Obsidian vault writes are permitted.

## Residual Risks

- Legacy Opportunities without Thesis lineage will become non-preparable until
  regenerated.
- Runtime validation may not produce a naturally occurring CANDIDATE; a valid
  NO_TRADE or WATCH result remains successful evidence.
- DevLog context is unavailable due provider failure and must be reported in the
  implementation report.
