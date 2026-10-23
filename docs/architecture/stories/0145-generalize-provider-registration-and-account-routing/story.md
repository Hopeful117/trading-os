# Story 0145 - Generalize Provider Registration and Account Routing

## Metadata

**ID:** `0145`
**Title:** Generalize Provider Registration and Account Routing
**Status:** Completed
**Related ADRs:** `ADR-002`, `ADR-030`, `ADR-041`, `ADR-043`, `ADR-071`

---

## Goal

Remove the current physical Kraken-only provider registration and account-routing
assumptions while preserving the provider-neutral Broker Service boundary and the
distinct financial Account versus BrokerAccount identities.

This is the first prerequisite for evaluating a second execution provider. It is
not a cTrader or FTMO implementation Story.

## Scope

- Generalize provider registration and resolution behind existing broker-neutral
  contracts.
- Preserve independent provider capability composition.
- Resolve financial Account identity and BrokerAccount routing identity according
  to ADR-043.
- Reject ambiguous, missing, cross-owner, or provider-inconsistent mappings.
- Preserve existing Kraken behavior and tests.
- Add deterministic test fixtures for at least two provider identities without
  adding a real second-provider adapter.

## Out Of Scope

- cTrader or FTMO classes, enums, SDKs, OAuth, credentials, or endpoints.
- FTMO Challenge definitions, phase progression, loss rules, or minimum days.
- cTrader account synchronization, events, execution, close, or reconciliation.
- Market Data provider integration or instrument catalogue changes.
- Changes to Risk rule semantics or human authorization.
- Automatic migration of ambiguous legacy rows.

## Acceptance Criteria

- [x] Trading Core does not branch on Kraken-specific provider behavior.
- [x] Broker Service provider resolution remains capability-oriented and
      provider-neutral.
- [x] Financial Account identity is not resolved from BrokerAccount UUID equality
      or ambiguous `(owner, provider)` lookup.
- [x] BrokerAccount remains the execution-routing identity.
- [x] Missing or inconsistent Account-to-BrokerAccount mapping fails closed before
      Risk authorization or execution.
- [x] Cross-owner mappings are rejected deterministically.
- [x] Existing Kraken behavior remains validated by the affected test suite.
- [x] Registry composition, duplicate detection, unsupported-provider handling,
      and provider-neutral routing behavior are covered without adding a second
      production provider identity.
- [x] No FTMO or cTrader dependency, payload, or business rule enters Trading
      Core.

## Safety Constraints

- Preserve explicit human authorization and unknown-outcome reconciliation.
- Do not weaken authentication, ownership, Risk, or fail-closed behavior.
- Do not create or inspect real broker credentials.
- Do not modify historical ADRs or reports.
- Do not commit, push, merge, or discard unrelated work.

## Validation

- Run focused Trading Core and Broker Service tests covering provider resolution,
  account routing, ownership, and missing-mapping failures.
- Run `git diff --check`.
- Verify no provider-specific dependency crosses the Broker Service boundary.
- Record any schema/backfill requirement as a separate implementation decision if
  it cannot be completed safely within this Story.
