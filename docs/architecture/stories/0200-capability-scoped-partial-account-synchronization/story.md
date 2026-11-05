# Story 0200 - Capability-Scoped Partial Account Synchronization

## Metadata

**ID:** `0200`
**Title:** Capability-Scoped Partial Account Synchronization
**Status:** Repository Analysis pending human approval
**Priority:** P0 - usable FTMO read-only account state
**Related Stories:** `0197`, `0198`, `0199`

## Goal

Allow the existing FTMO Free cTrader account synchronization path to persist
provider-neutral account facts that are independently available and coherent,
even when unrelated provider capabilities such as equity, margin or deal
history are unavailable. The resulting state must remain explicit about
availability, freshness and provenance, and must never become risk or execution
authorization merely because part of the observation was synchronized.

## Context

The current cTrader observation for external account `49411967` contains a
fresh provider-authoritative balance and confirmed empty positions/orders, but
does not provide current equity, account-level used margin, free margin or deal
history. Trading Core currently rejects the whole synchronization before local
persistence when any required collection is unavailable or when the snapshot is
not globally `COMPLETE`.

ADR-084 and ADR-086 define capability-scoped observation and evidence-scoped
risk evaluation. ADR-085 requires unavailable or non-admissible equity to remain
unavailable and fail closed for equity-dependent FTMO risk. This Story applies
those decisions to the account synchronization boundary without changing FTMO
policy semantics or authorizing provider execution.

## Problem

Global synchronization rejection conflates:

- facts required to describe the current account;
- optional or unsupported provider capabilities;
- facts required by a selected risk policy;
- facts required to authorize a proposed order.

As a result, a safe read-only balance and an authoritative empty position set
cannot be persisted when independent equity, margin or deal capabilities are
unavailable. Conversely, relaxing the rejection without explicit semantics could
turn unavailable collections into empty collections, delete valid local state,
or expose incomplete facts as risk-ready.

## Scope

- Define and implement capability-scoped synchronization semantics for the
  provider-neutral broker snapshot used by Trading Core.
- Persist account identity and independently available, identity-validated,
  fresh account facts such as balance and confirmed current positions/orders.
- Preserve explicit collection availability and bounded unavailability reasons.
- Distinguish `AVAILABLE_EMPTY`, `AVAILABLE_NON_EMPTY`, `UNAVAILABLE_OR_ABSENT`
  and `TRUNCATED` without converting unavailable or truncated data into empty
  data.
- Prevent unavailable or truncated collections from clearing or overwriting
  previously synchronized facts that depend on those collections.
- Keep account-level equity, used margin, free margin and deal history
  unavailable unless their existing provider-authoritative contracts are
  satisfied.
- Preserve provider-neutral provenance, identity, observation timestamps,
  freshness and completeness information through the synchronization boundary.
- Keep risk evaluation evidence-scoped and fail-closed for every mandatory
  invariant whose facts are missing, stale, incoherent or inadmissible.
- Add deterministic unit and integration coverage for complete, partial,
  unavailable, truncated, identity-mismatch and repeated synchronization cases.
- Validate the real FTMO account in READ-ONLY mode after implementation, with no
  provider mutation and no execution authorization.

## Out Of Scope

- Deriving current equity from balance, PnL, positions, deals or any local
  approximation.
- Deriving account-level used margin or free margin from position margin,
  equity, leverage or generic formulas.
- Making `BROKER_DERIVED` equity admissible for `FTMO_2STEP_50K_RISK`.
- Changing FTMO thresholds, policy versions, T0/T1 requirements or human
  authorization rules.
- Implementing cTrader ExpectedMargin or a new provider-specific financial
  capability.
- Adding cTrader protocol types, provider semantics or FTMO rules to Trading
  Core or the Risk Domain.
- Submitting, cancelling, amending or closing any provider order.
- Enabling `CTRADER_SAFETY_EXECUTION_ENABLED`.
- Replacing the existing provider-neutral snapshot boundary with a broad
  architectural refactor.
- Changing unrelated broker integrations or PAPER accounting behavior.

## Constraints

- Preserve all uncommitted Story 0198 and Story 0199 artifacts and unrelated
  working-tree changes.
- Use only the existing FTMO Free cTrader account `49411967` for controlled
  runtime validation.
- Preserve exact owner, local broker-account and provider-registration identity
  checks.
- Preserve transactionality and idempotence for every fact that is persisted.
- An unavailable collection must never be interpreted as an authoritative empty
  collection.
- A truncated collection must never be treated as a complete collection.
- Missing facts must remain missing; no zero, stale, balance or inferred
  substitute is permitted.
- Partial synchronization is diagnostic/read-only state only and must never
  authorize risk approval or provider execution.
- Keep `CTRADER_SAFETY_EXECUTION_ENABLED=false`.
- Do not commit, push, merge or delete branches without explicit human
  authorization.

## Acceptance Criteria

- The current snapshot contract and synchronization boundary clearly distinguish
  observation completeness from capability availability and risk readiness.
- A fresh, identity-valid snapshot with available balance and confirmed empty
  positions/orders can persist those facts even when equity, margin or deals are
  unavailable.
- An unavailable or truncated positions/orders/deals collection does not become
  an empty collection and does not clear or overwrite the corresponding local
  synchronized state.
- Account identity, provider registration, external account identity, currency,
  observation timestamps, freshness and collection availability remain
  validated and traceable after synchronization.
- A provider snapshot with invalid identity, stale data, incoherent data,
  duplicate position references or invalid financial facts remains rejected
  without partial mutation.
- Equity remains unavailable and non-admissible for the FTMO equity-dependent
  policy when no authoritative provider equity is present.
- Used margin, free margin and deal history remain unavailable when the provider
  does not supply a contractually valid fact; no local fallback is emitted.
- Risk evaluation can expose independently provable diagnostics, but any
  mandatory missing or inadmissible evidence keeps aggregate execution
  authorization blocked.
- Repeated synchronization of the same unchanged snapshot is idempotent and
  does not duplicate positions, trades, balances or provenance state.
- The API exposes a bounded, explicit result for partial synchronization without
  exposing secrets or provider-specific payloads.
- Unit and integration tests cover the supported availability matrix, mutation
  boundaries, identity checks, idempotence and fail-closed risk behavior.
- The real FTMO READ-ONLY validation records the request identity, bounded
  duration, persisted facts, unavailable capabilities and safety status; no
  provider mutation occurs.
- A Story-local implementation report records the final contract, changed files,
  tests, runtime result, unresolved financial capabilities and one next action.

## Relevant ADRs

- ADR-071 - FTMO Challenge Target and cTrader Broker Boundary
- ADR-074 - cTrader Authoritative READ-ONLY Account Synchronization Boundary
- ADR-078 - PAPER/LIVE Execution Boundary
- ADR-082 - Provider-Independent CFD Financial Model and Mark-to-Market
  Accounting
- ADR-084 - LIVE Financial Facts and Provider Preflight Readiness
- ADR-085 - Broker Equity Resolution and Provenance Contract
- ADR-086 - Evidence-Based Risk Evaluation

## Validation Expectations

- Trading Core targeted unit and integration tests.
- Broker Service cTrader snapshot contract tests where the provider-neutral
  availability contract is affected.
- Relevant Broker Service and Trading Core regression suites.
- `git diff --check`.
- Controlled FTMO READ-ONLY runtime synchronization with the safety execution
  flag disabled.
- No claim of LIVE risk or execution readiness from partial synchronization.
