# Story 0193 - LIVE Financial Readiness and cTrader Required Margin Capability

## Metadata

**ID:** `0193`

**Title:** LIVE Financial Readiness and cTrader Required Margin Capability

**Status:** Draft

**Related ADRs:** `ADR-028`, `ADR-043`, `ADR-071`, `ADR-074`, `ADR-078`, `ADR-082`, `ADR-083`, `ADR-084`

**Related Stories:** `0148`, `0150`, `0183`, `0187`

---

## Goal

Implement the provider-neutral LIVE financial readiness architecture approved by
ADR-084:

```text
capability-based current account observation
+ separate provider-authoritative proposed-order margin
```

The Story must allow Trading Core to distinguish observation completeness, risk
readiness, preflight readiness and execution readiness without inventing cTrader
equity, free margin or generic account-margin semantics.

For cTrader, the Story must establish the Broker Service path required to map a
supported `ProtoOAExpectedMarginReq/Res` capability into the existing neutral
`RequiredMarginPort` boundary. It must not authorize provider execution.

---

## Context

ADR-084 formalizes the approved LIVE financial facts contract. `BrokerAccountSnapshotV2`
is a provider-neutral current-account observation, not a universal guarantee that
every financial scalar exists for every broker.

The current cTrader read-only foundation from Story 0148 and subsequent LIVE CFD
work exposes account and position facts, but current snapshot completeness and
risk-facts admission still treat `equity`, account `usedMargin` and `freeMargin`
as universal account requirements. Official cTrader Open API research does not
establish those three general current-account fields as a complete account
snapshot contract.

The current repository already contains `RequiredMarginPort`, and ADR-083
defines it as the application-facing boundary for proposed-order margin. PAPER
uses a local deterministic `MarginPolicy`; LIVE must use provider-authoritative
semantics.

Story 0183 and its implementation artifacts must be reconciled with ADR-084
where they assume that cTrader direct `equity`, account `usedMargin` or
`freeMargin` fields are universally available. Historical Story artifacts must
not be rewritten merely to hide that evolution.

---

## Problem

The current universal snapshot contract creates two safety and architecture
problems:

- missing optional or separately scoped facts make unrelated read-only and risk
  capabilities unavailable;
- the proposed-order margin requirement is mixed conceptually with current
  account margin and free-margin fields.

At the same time, LIVE risk must remain fail-closed when the selected policy
requires authoritative equity, current exposure, historical deals or another
missing safety-critical fact.

Trading Core must also obtain required margin before any provider mutation. A
provider order rejection is not an acceptable substitute for deterministic
pre-submission risk validation.

---

## Scope

- Implement capability-scoped availability and readiness semantics for current
  account observations.
- Separate observation completeness from policy-dependent risk readiness,
  preflight readiness and execution readiness.
- Preserve provider-neutral account, position, pending-order and PnL facts.
- Propagate independent deal-history availability semantics:
  `AVAILABLE_EMPTY`, `AVAILABLE_NON_EMPTY`, `UNAVAILABLE_OR_ABSENT` and
  `TRUNCATED`.
- Preserve authoritative position-level margin without automatically aggregating
  it into account-level used margin.
- Reuse `RequiredMarginPort` as the provider-neutral LIVE proposed-order margin
  boundary.
- Implement the Broker Service cTrader ExpectedMargin adapter for the explicitly
  supported capability subset.
- Preserve cTrader-specific protocol, scaling, enum and capability semantics
  inside Broker Service.
- Make missing, stale or unsupported required-margin facts fail closed before
  provider submission.
- Integrate the readiness contract at LIVE T0 and T1 without changing PAPER
  authority.
- Add deterministic tests for policy-dependent readiness, incomplete facts,
  deal availability and required-margin failures.
- Produce a controlled READ-ONLY provider-validation procedure or evidence
  boundary for the cTrader ExpectedMargin capability without placing orders.

---

## Out of Scope

- Local derivation of cTrader equity.
- `freeMargin = equity - usedMargin` as cTrader provider truth.
- Generic `sum(position.usedMargin)` account-margin semantics.
- Treating `ProtoOADepositWithdraw.equity` as current account equity.
- Reproducing a cTrader/provider-specific margin engine in Trading Core or Risk
  Domain.
- Changing PAPER `MarginPolicy` authority from ADR-083.
- FTMO Challenge rule implementation or challenge lifecycle changes.
- cTrader order placement, cancellation, amendment, close, SL/TP or any other
  provider mutation.
- Using provider order rejection as preflight validation.
- Automatic trading authorization or removal of human authorization.
- Rewriting historical ADRs or Story 0183 artifacts.
- Uncontrolled provider calls, credentials or live-account execution.
- Frontend, database migration, Market Intelligence or unrelated broker work.

---

## Acceptance Criteria

- [ ] `BrokerAccountSnapshotV2` and its consumers represent a provider-neutral
      current observation whose completeness is scoped to a declared capability.
- [ ] Missing facts do not become zero, empty, false or inferred values without
      an explicitly approved semantic contract.
- [ ] Observation completeness, risk readiness, preflight readiness and execution
      readiness are represented and evaluated as distinct concepts.
- [ ] A risk policy requiring equity fails closed when authoritative equity is
      unavailable; no balance/PnL/equity approximation is introduced.
- [ ] Missing free margin does not universally make cTrader unusable, while a
      policy explicitly requiring free margin remains fail-closed.
- [ ] Account-level used margin is never generically derived from position
      margins or leverage.
- [ ] Position-level provider margin remains distinct from account used margin
      and proposed-order required margin.
- [ ] Deal collection availability preserves `AVAILABLE_EMPTY`,
      `AVAILABLE_NON_EMPTY`, `UNAVAILABLE_OR_ABSENT` and `TRUNCATED`.
- [ ] Omitted deal collections are not treated as authoritative empty unless the
      provider contract proves that semantic.
- [ ] `RequiredMarginPort` remains the provider-neutral proposed-order margin
      abstraction for LIVE and PAPER.
- [ ] PAPER continues to resolve margin through the local deterministic
      versioned `MarginPolicy` authority.
- [ ] LIVE cTrader ExpectedMargin is adapted inside Broker Service and exposed
      only as a provider-neutral, monetary, fresh and provenance-bearing fact.
- [ ] Unsupported cTrader ExpectedMargin semantics fail closed and do not fall
      back to leverage, notional, current margin, position margin or free margin.
- [ ] Missing, stale or invalid LIVE required-margin facts block T0/T1 preflight
      and prevent provider submission.
- [ ] T0 and T1 preserve required-margin provenance and independently revalidate
      the required fact at the applicable decision boundary.
- [ ] Trading Core and Risk Domain contain no cTrader DTOs, protocol messages,
      cTrader enum handling or `moneyDigits` semantics.
- [ ] No cTrader provider mutation capability is added or enabled.
- [ ] Existing PAPER authority and cTrader read-only safety invariants remain
      unchanged.
- [ ] Targeted Broker Service and Trading Core tests pass, with no production
      test or behavior unrelated to this Story changed.
- [ ] The implementation report records any unresolved cTrader capability or
      provider-validation limitation without claiming execution readiness.

---

## Constraints

- Preserve ADR-084 as the governing LIVE financial readiness decision.
- Preserve ADR-071, ADR-074, ADR-082 and ADR-083 without silently weakening their
  fail-closed or authority invariants.
- Preserve Broker Service ownership of provider connectivity, decoding, scaling,
  capability detection and provider-authoritative facts.
- Keep Trading Core provider-neutral and responsible for readiness orchestration,
  T0/T1 and execution gates.
- Keep Risk Domain deterministic and limited to normalized business facts.
- Do not invent cTrader account-level equity, used margin or free margin.
- Do not aggregate provider position margins without a separately validated
  semantic contract.
- Do not call cTrader during deterministic tests.
- Any controlled provider validation must be READ-ONLY, explicitly authorized,
  owner-bound and isolated from order operations.
- Preserve `CTRADER_SAFETY_EXECUTION_ENABLED=false` unless a separate approved
  procedure changes that state.
- Do not commit, push or merge automatically.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-028.md`
- `docs/architecture/adr/ADR-043.md`
- `docs/architecture/adr/ADR-071.md`
- `docs/architecture/adr/ADR-074-ctrader-authoritative-read-only-synchronization.md`
- `docs/architecture/adr/ADR-078-paper-live-execution-boundary.md`
- `docs/architecture/adr/ADR-082-cfd-financial-model-and-mark-to-market-accounting.md`
- `docs/architecture/adr/ADR-083-paper-margin-authority-and-simulation-policy.md`
- `docs/architecture/adr/ADR-084-live-financial-facts-and-provider-preflight-readiness.md`

---

## Relevant Modules

- `broker-service`
- `trading-core`
- `risk-domain`

---

## Validation

- Inspect provider-neutral snapshot and readiness contracts for cTrader leakage.
- Run focused Broker Service tests for capability mapping, money precision,
  account identity, unsupported ExpectedMargin semantics and deal availability.
- Run focused Trading Core tests for policy-dependent readiness, missing equity,
  missing free margin, missing deal history, missing required margin and T0/T1
  revalidation.
- Run relevant Risk Domain regression tests without changing Risk Domain
  provider neutrality.
- Run Broker Service and Trading Core module tests when production behavior in
  those modules changes.
- Run `git diff --check`.
- Perform controlled cTrader READ-ONLY validation only under a separately
  authorized procedure; no order or other mutation is permitted.
- Report provider validation as unavailable or incomplete rather than claiming
  execution readiness when evidence is insufficient.

---

## Safety Invariants

```text
LIVE_ACCOUNT_STATE = CAPABILITY_BASED
LIVE_MARGIN_AUTHORITY = PROVIDER
PAPER_MARGIN_AUTHORITY = LOCAL_VERSIONED_MARGIN_POLICY
REQUIRED_MARGIN_PORT = SHARED_PROVIDER_NEUTRAL_BOUNDARY
MISSING_EQUITY_FOR_EQUITY_POLICY = FAIL_CLOSED
MISSING_FREE_MARGIN = NOT_UNIVERSALLY_BLOCKING
MISSING_REQUIRED_MARGIN = ALWAYS_BLOCKING_FOR_LIVE_PREFLIGHT
POSITION_MARGIN != ACCOUNT_USED_MARGIN
POSITION_MARGIN != PROPOSED_REQUIRED_MARGIN
PROVIDER_REJECTION != PREFLIGHT_VALIDATION
NO_PROVIDER_ORDER_BEFORE_PREFLIGHT_READY = true
NO_CTRADER_MUTATION_IN_THIS_STORY = true
HUMAN_AUTHORIZATION_REQUIRED = true
AUTOMATIC_TRADING_AUTHORIZED = false
```

---

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Capability-based LIVE observation and readiness behavior implemented.
- [ ] cTrader ExpectedMargin adapter implemented only for the validated support
      subset.
- [ ] RequiredMarginPort remains provider-neutral and PAPER-compatible.
- [ ] Deal availability semantics are preserved through the neutral boundary.
- [ ] Missing safety-critical facts fail closed at the correct readiness level.
- [ ] T0/T1 required-margin behavior is independently tested.
- [ ] Relevant validation executed and reported.
- [ ] Documentation reconciliation completed.
- [ ] Code Review completed.
- [ ] Engineering Report completed.
- [ ] Human commit created.

---

## Workflow Boundary

This Story is created after human approval of ADR-084. Only this Story is created
initially. Repository Analysis is the next workflow artifact and requires its own
explicit human approval before Implementation Planning. Implementation requires
explicit human approval of the Implementation Plan.

This Story does not authorize cTrader credentials, provider calls, preflight,
provider execution or automatic trading.
