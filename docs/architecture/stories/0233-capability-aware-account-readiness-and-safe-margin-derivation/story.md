# Story 0233 - Capability-Aware Account Readiness and Safe Margin Derivation

## Metadata

**ID:** `0233`
**Title:** Capability-Aware Account Readiness and Safe Margin Derivation
**Status:** Repository Analysis pending human approval
**Priority:** P0 - capability-aware LIVE account readiness
**Related ADRs:** `ADR-074`, `ADR-084`, `ADR-085`, `ADR-086`
**Related Stories:** `0193`, `0194`, `0195`, `0198`, `0199`, `0200`, `0232`

## User Problem

The FTMO Free cTrader account is connected and partially observable, but the
provider does not expose account-level `usedMargin` or `freeMargin`. The current
account snapshot is marked `INCOMPLETE`, and the public readiness response keeps
both risk and execution flags false. That conservative state is correct for
controls that require unavailable evidence, but it is too coarse for unrelated
read-only account capabilities and independently evaluable risk invariants.

## Goal

Make account readiness capability-aware while preserving deterministic Risk
authority and fail-closed LIVE execution. The implementation must distinguish
provider-observed facts, explicitly derived facts and unavailable facts, then
evaluate only the facts required by the selected operation or risk invariant.

## Scope

- Reconcile the account observation, fact provenance and operation-specific
  readiness semantics with the existing provider-neutral snapshot boundary.
- Preserve the existing conditional empty-account equity derivation only when
  all explicit preconditions are satisfied and the provenance remains derived.
- Evaluate whether zero used margin can be proven for this provider/account
  contract; implement it only if a documented domain contract and complete
  evidence establish the implication.
- Derive free margin only when equity and used margin are reliable, coherent and
  the account semantics explicitly support `freeMargin = equity - usedMargin`.
- Keep unsupported or unproven facts unavailable rather than substituting zero,
  balance or stale values.
- Separate account observation readiness, per-invariant T0 Risk readiness,
  provider preflight readiness and LIVE execution readiness.
- Preserve partial deterministic diagnostics while keeping aggregate policy and
  execution decisions fail-closed.
- Extend existing account-status/Risk contracts minimally and preserve existing
  MCP and Angular compatibility where practical.
- Validate the existing FTMO Free cTrader account through read-only operations.

## Out Of Scope

- Searching for additional FTMO or cTrader margin endpoints.
- New broker integrations, microservices or parallel readiness engines.
- Changes to broker authentication, agent authorization or grants.
- Changes to FTMO thresholds, policy versions or Risk formulas without an
  explicit accepted decision.
- Treating missing provider fields as zero or globally optional.
- Enabling LIVE execution or creating execution authorizations.
- Account synchronization, broker orders or position mutations during validation.
- Synthetic PAPER infrastructure.
- Changes to unrelated providers or unrelated trading-domain behavior.

## Constraints

- Preserve account, owner, provider-registration and environment identity checks.
- Preserve `ExecutionMode.LIVE` for the FTMO account.
- Preserve deterministic RiskEngine authority and T0/T1 requirement parity.
- Missing mandatory evidence must result in `INCOMPLETE`, `BLOCKED` or an
  equivalent explicit non-PASS outcome.
- Partial evaluation is diagnostic only and never execution authorization.
- Derived facts must retain source observations, derivation rule, timestamps,
  identity and admissibility conditions.
- Do not initiate LIVE synchronization during initial implementation validation.
- Keep broker execution safety disabled.
- Preserve existing uncommitted work and do not commit, push or create a PR.

## Acceptance Criteria

- Account observation readiness remains usable for account, position and
  portfolio display when unsupported margin fields are absent.
- The account-status contract exposes enough bounded diagnostics to distinguish
  readable account state, missing facts, T0 availability and LIVE execution
  blocking without exposing provider secrets.
- Provider facts and derived facts remain distinguishable end to end.
- Empty-account equity derivation requires authoritative balance, complete empty
  position and order collections, coherent fresh observations and no unresolved
  exposure or broker operation; otherwise equity remains unavailable.
- Used-margin derivation is implemented only if its provider/account preconditions
  are proven. Otherwise it remains unavailable and is documented as such.
- Free-margin derivation is implemented only when its inputs and relationship
  are contractually valid. Otherwise it remains unavailable.
- T0 invariants evaluate independently from their actual `RiskFactRequirements`.
  A missing unrelated margin fact does not block an invariant that does not
  consume it.
- Missing evidence for a mandatory invariant produces `INCOMPLETE` or an
  equivalent explicit blocking result and never PASS or NOT_APPLICABLE.
- Aggregate T0 policy readiness remains incomplete when any mandatory invariant
  is incomplete or failed.
- LIVE preflight and execution retain stricter margin, identity, market,
  freshness, T1 and human-authorization requirements.
- Valid derived facts are accepted only by controls whose contracts explicitly
  admit their provenance.
- Tests cover derivation preconditions, stale/conflicting observations,
  incomplete collections, unresolved operations, per-invariant readiness,
  aggregate fail-closed behavior and LIVE execution blocking.
- Read-only validation of the existing FTMO account records the observed facts,
  provenance, capability readiness and remaining blockers without synchronization
  or broker mutation.

## Relevant Existing Decisions

- ADR-074 defines the provider-authoritative read-only observation boundary.
- ADR-084 defines capability-based observation, provider-authoritative LIVE
  margin preflight and operation-specific readiness.
- ADR-085 defines equity provenance and currently rejects unapproved balance
  substitution; the narrow empty-account derivation requires reconciliation with
  the new product decision.
- ADR-086 defines per-invariant evidence requirements, partial diagnostics and
  fail-closed aggregate execution.
- Story 0200 defines capability-scoped partial synchronization and currently
  leaves account-level margin unavailable when the provider does not supply it.

## Validation Expectations

- Targeted Broker Service cTrader mapping and snapshot tests.
- Targeted Trading Core account-readiness and Risk evidence tests.
- T0/T1 execution guard regression tests.
- Relevant integration tests and repository quality checks for changed modules.
- Read-only MCP validation for account, positions, status and markets.
- No synchronization, approval challenge, execution authorization or broker
  mutation.
