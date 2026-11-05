# Story 0201 - Operational Account Synchronization and Financial Derivation Integration

## Metadata

**ID:** `0201`
**Title:** Operational Account Synchronization and Financial Derivation Integration
**Status:** Implementation complete; runtime validation completed through Market Data and scan, Trade Plan/T0/T1 blocked by HTTP 403
**Priority:** P0 - first FTMO trade preparation
**Related Stories:** `0197`, `0198`, `0199`, `0200`
**Reference Account:** cTrader FTMO Free account `49411967`

## Goal

Make the existing FTMO account usable in the normal Trading OS account and
decision workflow while preserving explicit partial-data semantics, deterministic
financial provenance and fail-closed risk/execution behavior.

## Context

Story 0200 validated the real READ-ONLY cTrader path and operationally persisted
a fresh partial snapshot:

- balance: `50000 USD`;
- positions: `AVAILABLE_EMPTY`;
- orders: `AVAILABLE_EMPTY`;
- PnL: `AVAILABLE_EMPTY`;
- deals: `UNAVAILABLE_OR_ABSENT`;
- equity, used margin and free margin: unavailable;
- synchronization result: `SYNCED_PARTIAL`;
- diagnostic-only: `true`.

The local Trading Core account is broker account
`7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456`. The provider execution safety flag must
remain disabled and no provider mutation is allowed in this Story.

## Scope

- Expose the synchronized account state through the normal account APIs and
  user-facing account workflow, without presenting partial synchronization as
  trading readiness.
- Preserve explicit snapshot completeness, freshness, availability, provenance
  and unavailability reasons in operational projections and DTOs.
- Reuse the Story 0197 derived-equity experiment only as an explicitly
  non-authoritative diagnostic candidate when its existing completeness,
  identity, temporal and precision guarantees pass.
- Keep any derived equity provenance as `BROKER_DERIVED` and preserve its
  inadmissibility for the current FTMO risk policy.
- Integrate only deterministic financial derivations whose source contract is
  complete and already supported by the provider-neutral model.
- Preserve explicit unavailability for unsupported equity, used margin, free
  margin and deal history; do not invent zeros or synthetic deals.
- Execute a representative Trade Intent through existing T0/T1 and execution
  readiness checks without submitting an order.
- Add focused backend, derivation, risk and Angular coverage for the changed
  operational path.
- Validate the existing FTMO account in controlled READ-ONLY runtime mode.

## Out Of Scope

- Making `BROKER_DERIVED` equity admissible for `FTMO_2STEP_50K_RISK`.
- Treating a candidate derived equity as `BROKER_REPORTED` or FTMO-authoritative.
- Deriving account used margin from notional, leverage, position margin or a
  generic formula without an approved authoritative contract.
- Treating an empty position set as proof that used margin is zero.
- Creating fictitious equity, margin, free margin or deal history.
- Changing FTMO thresholds, policy versions, T0/T1 requirements or human
  authorization rules.
- Implementing provider-specific cTrader models in Trading Core or Risk Domain.
- Adding a new microservice or refactoring unrelated UI architecture.
- Submitting, cancelling, amending or closing any provider order.
- Enabling `CTRADER_SAFETY_EXECUTION_ENABLED`.
- Committing, pushing, merging or deleting branches without explicit approval.

## Constraints

- Preserve all existing uncommitted changes from Stories 0198-0200.
- Use only external account `49411967` for controlled runtime validation.
- Preserve account owner, broker-account, provider-registration and external
  identity checks.
- Preserve transactionality and idempotence of synchronization.
- Keep unavailable data distinct from confirmed empty collections.
- Keep partial synchronization diagnostic-only and execution fail-closed.
- Keep T0 and T1 evidence requirements aligned.
- Do not weaken ADR-082, ADR-084, ADR-085 or ADR-086 invariants.

## Acceptance Criteria

- The existing FTMO account is visible through the normal account API and
  account UI with provider, currency, balance, synchronization status and
  explicit missing financial capabilities.
- The normal operational synchronization persists valid partial facts and
  exposes a bounded structured result without exposing provider payloads or
  secrets.
- `UNAVAILABLE_OR_ABSENT` is never rendered or serialized as numeric zero or an
  authoritative empty collection.
- Repeated operational synchronization remains idempotent and does not clear
  facts whose source collection is unavailable or truncated.
- Story 0197's candidate calculation is reused rather than duplicated, and is
  surfaced only with explicit `BROKER_DERIVED`, version, diagnostics, freshness
  and validation state when its preconditions pass.
- Derived equity remains excluded from the current FTMO risk admission unless an
  existing policy explicitly permits it; no policy is changed by this Story.
- Used margin and free margin remain unavailable unless their source facts and
  approved deterministic dependencies are complete and valid.
- Deal history remains explicitly classified as complete, partial, empty or
  unavailable; no fictitious deals are created.
- A representative Trade Intent reaches the existing T0/T1/readiness path and
  returns a deterministic result. Any refusal identifies its exact rule and
  missing or inadmissible evidence.
- No real provider mutation is observed during implementation or validation.
- Targeted synchronization, derivation, risk and Angular tests pass, and the
  full-suite limitations are reported precisely if the PostgreSQL timeout
  remains.
- Story-local implementation, E2E, safety and blocker reports are produced.

## Relevant ADRs

- ADR-082 - Provider-Independent CFD Financial Model and Mark-to-Market Accounting
- ADR-084 - LIVE Financial Facts and Provider Preflight Readiness
- ADR-085 - Broker Equity Resolution and Provenance Contract
- ADR-086 - Evidence-Based Risk Evaluation

## Validation Expectations

- Targeted Trading Core synchronization/account/risk tests.
- Story 0197 derivation and affected Broker Service tests.
- Angular account and trade-flow tests plus production build where applicable.
- Controlled authenticated V2 synchronization for external account `49411967`.
- Representative Trade Intent through T0/T1 without submission.
- PostgreSQL integration suite retry where the environment permits.
- `git diff --check`.
