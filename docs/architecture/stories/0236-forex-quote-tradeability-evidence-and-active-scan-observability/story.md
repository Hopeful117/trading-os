# Story 0236 - Forex Quote Tradeability Evidence and Active Scan Observability

## Metadata

**ID:** `0236`
**Title:** Forex Quote Tradeability Evidence and Active Scan Observability
**Status:** Validated with limitations; not fully closed
**Priority:** P0 - deterministic LIVE market evidence
**Related ADRs:** `ADR-079`, `ADR-099`, `ADR-100`
**Related Stories:** `0232`, `0234`, `0235`

## User Problem

The first post-authentication-recovery EUR/USD active scan accepted
`FOREX_QUOTE_BASIC` and completed, but the persisted Market Thesis still
reported `TRADEABILITY_UNAVAILABLE`. Independent cTrader quote availability did
not prove that the Trading OS pipeline consumed the quote. The authorized active
scan projection also exposed neither the effective analysis provenance nor the
capability-level diagnostic needed to establish where evidence was lost.

## Goal

Make the Forex quote-based `LIVE_CURRENT` path correct, deterministic,
traceable and operationally verifiable without increasing trading authority or
generating opportunities by weakening safeguards.

## Scope

- Correct the demonstrated active-pipeline incompatibility with
  `FOREX_QUOTE_BASIC` and `LIVE_CURRENT`.
- Preserve valid quote identity, provider provenance, cutoff and freshness
  semantics.
- Ensure quote/spread observations and precise diagnostics reach Market Thesis.
- Preserve the distinction between observed spread and liquidity suitability.
- Expose bounded effective analysis configuration, capability execution state,
  provenance and diagnostics through an existing account-scoped read projection
  or a narrowly scoped detail contract.
- Preserve `LEVEL_II_REQUIRED` fail-closed behavior.
- Add focused unit, orchestration, projection and authorization tests.
- Revalidate one controlled EUR/USD scan through the existing authorized MCP
  connection after implementation.
- Append the root cause, correction and runtime evidence to the existing
  validation report.

## Out Of Scope

- Broker orders, position changes, account synchronization or reconciliation.
- TradePlan creation, Risk evaluation mutations, approval or execution
  authorization.
- Credential rotation, grant changes or permission changes.
- New ADRs, providers, ingestion subsystems or parallel tradeability models.
- Universal Level II requirements for basic Forex quote analysis.
- Changing Market Thesis safeguards, strategy direction, scoring semantics or
  Candidate eligibility to produce more signals.
- Treating a zero spread as proof of liquidity or execution quality.

## Confirmed Operational Failure

The scan `94f39117-5928-41a1-be1c-f2feac8df9f1` accepted the explicit profile
and completed with `PARTIAL` quality and `COMPLETED_NO_OPPORTUNITY`. Its
analysis execution `e506db66-604d-4f4c-9183-8a90b8450743` produced a Thesis with
`TRADEABILITY_UNAVAILABLE`. The exact code-level boundary is recorded in the
Repository Analysis for this Story.

## Acceptance Criteria

- An active `LIVE_CURRENT` request with `FOREX_QUOTE_BASIC` persists both the
  effective policy and profile on the analysis execution.
- The active execution path does not reject an otherwise valid current quote
  solely because its evidence policy is `LIVE_CURRENT`.
- A fresh, valid, market-consistent two-sided Forex quote produces deterministic
  quote and spread evidence, including provider, timestamps, price spread and
  supported pip representation.
- Bid equal to ask follows the existing deterministic calculator contract and
  remains explicitly distinguishable from liquidity suitability.
- Missing, one-sided, invalid, negative-spread, stale, future or
  identity-inconsistent quotes produce precise non-positive diagnostics without
  synthetic replacement values.
- Basic Forex quote analysis does not require Level II evidence; a
  `LEVEL_II_REQUIRED` request remains fail-closed without its contract.
- Valid or rejected tradeability evidence reaches Market Thesis with its
  provenance and limitations; missing evidence remains explicit and cannot
  become positive evidence.
- Blocking Thesis contradictions still produce `NO_TRADE` and cannot be
  promoted to `CANDIDATE`.
- The authorized active-scan projection exposes the effective policy/profile,
  analysis cutoff, execution status and result quality, capability status and
  diagnostics, bounded provenance and Market Thesis evidence needed for audit.
- The projection remains account/actor scoped; cross-account access is denied
  and no credential, token, raw secret or provider secret is exposed.
- Tests cover profile propagation, quote validation, zero spread, freshness,
  provider and market identity, capability execution, Thesis integration,
  projection authorization and no-secret behavior.
- A real read-only EUR/USD scan confirms either provider-backed quote evidence
  or a precise deterministic rejection. A missing-evidence result is not
  reported as positive validation.
- No LIVE broker mutation or execution-authority transition occurs.

## Architectural Constraints

- ADR-100 permits `LIVE_CURRENT` only for an explicitly selected,
  capability-aware Forex quote profile with fresh, valid, identity-consistent
  quote evidence.
- ADR-079 remains authoritative for Level II, liquidity and suitability
  semantics.
- ADR-099/Story 0232 restored the MCP authentication boundary; this Story must
  not rotate credentials or broaden grants.
- Market Intelligence remains the evidence and Thesis authority. Risk and
  Trading Core remain execution authorities.
- `ExecutionMode.LIVE` remains correct for the external cTrader account.
- Existing working-tree changes must be preserved. No commit, push or PR is
  part of this Story.

## Validation Expectations

- Targeted Market Intelligence capability, calculator, orchestration, active
  scan and projection tests.
- Account-scoped authorization regression tests for detailed scan evidence.
- Applicable Java quality checks and `git diff --check`.
- One controlled read-only operational scan through the Gateway and existing
  authorized MCP connection.
- No synchronization, TradePlan, Risk mutation, approval, execution or broker
  operation.
