# Story 0194 - Implement Broker Equity Provenance and Admissibility Contract

## Metadata

**ID:** `0194`
**Title:** Implement Broker Equity Provenance and Admissibility Contract
**Status:** Draft
**Related ADRs:** `ADR-028`, `ADR-043`, `ADR-071`, `ADR-074`, `ADR-082`, `ADR-084`, `ADR-085`
**Related Stories:** `0148`, `0183`, `0193`

---

## Goal

Implement the provider-neutral equity provenance, quality and admissibility
contract accepted by ADR-085 without introducing an unvalidated cTrader equity
calculation and without weakening FTMO fail-closed risk controls.

The implementation must allow Trading OS to distinguish:

```text
equity available and admissible
equity available but not admissible
equity unavailable
```

The Story is a contract and propagation Story. It does not make T1 LIVE-ready.

---

## Context

ADR-084 requires policy-scoped LIVE financial readiness and fail-closed handling
of missing required facts. ADR-085 accepts a general provenance contract with
three outcomes:

```text
BROKER_REPORTED
BROKER_DERIVED
UNAVAILABLE
```

The reference cTrader account currently has no validated provider-reported
current equity source. The equivalence between a cTrader-derived equity and the
equity used by FTMO is not demonstrated.

The current policy `FTMO_2STEP_50K_RISK`, version `1.0.0`, therefore continues
to reject missing or non-admissible equity. This Story must preserve that
behavior.

---

## Problem

The current risk boundary must distinguish a numeric value that is available for
observation from a value that is authorized for a particular deterministic risk
policy. Without explicit provenance, quality and admissibility, a future
provider adapter could accidentally make a derived or stale value appear to be
authoritative equity.

The contract must support generic provider evolution while preserving the
current FTMO refusal and the existing fail-closed behavior.

---

## Scope

- Represent equity provenance explicitly.
- Represent equity availability explicitly.
- Represent quality metadata required for freshness, coherence, completeness,
  identity and source guarantees.
- Preserve bounded reasons for unavailable or non-admissible equity.
- Propagate provenance and quality from Broker Service to the provider-neutral
  Trading Core boundary.
- Allow policies or capabilities to declare admissible provenance and required
  quality guarantees.
- Validate admissibility in Trading Core before risk evaluation.
- Preserve deterministic Risk Engine behavior.
- Preserve fail-closed behavior for required but unavailable or non-admissible
  equity.
- Preserve compatibility with existing providers, including providers that
  already expose authoritative equity.
- Add focused offline regression and contract tests.
- Preserve ADR-084 and ADR-085 authority boundaries.

---

## Constraints

- Preserve ADR-084, ADR-074, ADR-082 and ADR-085.
- Keep FTMO-specific rules in policies and out of Broker Service.
- Keep Trading Core provider-neutral.
- Keep Risk Domain deterministic.
- Do not introduce an unvalidated cTrader equity calculation.
- Do not modify `FTMO_2STEP_50K_RISK` version `1.0.0`.
- Do not call cTrader or any authenticated LIVE endpoint.
- Do not create positions or mutate broker state.
- Preserve existing providers and their supported contracts.
- Do not commit, push or merge automatically.

---

## Out Of Scope

- Implementing or enabling a cTrader equity derivation formula.
- Authorizing `BROKER_DERIVED` for `FTMO_2STEP_50K_RISK`.
- Modifying FTMO thresholds, rules or policy version `1.0.0`.
- Treating `ProtoOADepositWithdraw.equity` as current account equity.
- Adding `equity = balance` or any zero-value fallback.
- Reusing a previous observation as current equity authority.
- Declaring any derived equity FTMO-authoritative.
- cTrader Account MetriX integration.
- Provider mutation or order execution.
- LIVE configuration changes or authenticated LIVE validation.
- Database migrations unless a separately approved contract requires one.
- Rewriting the Risk Engine or adding FTMO-specific logic to Broker Service.
- Frontend, Market Intelligence or unrelated provider work.

---

## Acceptance Criteria

- [ ] A documented broker-reported equity with valid quality guarantees and
      policy-admissible provenance is accepted by the risk boundary.
- [ ] A synthetic broker-derived equity is accepted only for a test policy or
      capability that explicitly permits `BROKER_DERIVED` and whose quality
      requirements pass.
- [ ] A broker-derived equity rejected by policy is not passed to risk
      evaluation, even when its numeric value is present.
- [ ] An unavailable equity required by a policy produces a blocking fail-closed
      outcome.
- [ ] An unavailable equity that is not required does not block unrelated risk
      evaluation or read-only capability behavior.
- [ ] Missing, blank or unknown provenance produces a fail-closed outcome when
      equity is required.
- [ ] Stale, incomplete, incoherent or identity-invalid equity fails the quality
      requirements declared by the policy or capability.
- [ ] `FTMO_2STEP_50K_RISK` version `1.0.0` rejects `BROKER_DERIVED` equity.
- [ ] `FTMO_2STEP_50K_RISK` version `1.0.0` rejects `UNAVAILABLE` equity when
      equity is required.
- [ ] No cTrader broker-reported equity source is assumed or synthesized by the
      tests or production contract.
- [ ] No implicit `equity = balance`, zero or stale-observation fallback exists.
- [ ] No broker-reported value is exposed as FTMO-authoritative without an
      explicit accepted policy contract.
- [ ] Broker Service exposes provider-neutral provenance and quality metadata
      without exposing cTrader DTOs to Trading Core or Risk Domain.
- [ ] Trading Core determines policy admissibility and Broker Service does not
      contain FTMO rule logic.
- [ ] Risk Engine behavior remains deterministic and provider-neutral.
- [ ] Existing providers retain their current supported equity behavior and no
      unrelated provider is made unavailable.
- [ ] Focused tests cover both accepted and rejected provenance paths without
      calling a provider.
- [ ] `git diff --check` passes.

---

## Safety Invariants

```text
EQUITY_PROVENANCE_REQUIRED = true
EQUITY_QUALITY_REQUIRED = true
BROKER_REPORTED != FTMO_AUTHORITATIVE_BY_DEFAULT
BROKER_DERIVED != BROKER_REPORTED
FTMO_2STEP_50K_RISK_ACCEPTS_BROKER_DERIVED = false
MISSING_EQUITY_FOR_EQUITY_POLICY = FAIL_CLOSED
NON_ADMISSIBLE_EQUITY_FOR_EQUITY_POLICY = FAIL_CLOSED
EQUITY_BALANCE_FALLBACK = false
EQUITY_ZERO_FALLBACK = false
STALE_EQUITY_FALLBACK = false
BROKER_SERVICE_KNOWS_FTMO_RULES = false
TRADING_CORE_RETAINS_POLICY_ADMISSIBILITY = true
RISK_ENGINE_REMAINS_DETERMINISTIC = true
CTRADER_MUTATION = false
LIVE_VALIDATION = false
T1_LIVE_READY = false
HUMAN_AUTHORIZATION_REQUIRED = true
```

---

## Compatibility Requirements

- Preserve ADR-084 capability-scoped completeness and readiness semantics.
- Preserve ADR-085 provenance, quality and admissibility separation.
- Preserve ADR-074 Broker Service ownership of cTrader synchronization and
  provider mapping.
- Preserve ADR-082 provider authority for LIVE CFD facts and deterministic
  accounting boundaries.
- Preserve the current fail-closed `ModeAwareRiskFactsProvider` behavior.
- Preserve PAPER authority and existing provider-neutral contracts.
- Do not add cTrader types, payloads or money-scaling rules to Trading Core or
  Risk Domain.

---

## Future Comparative Validation Boundary

Equivalence between cTrader-derived equity and FTMO reference equity is a
separate future investigation and is not part of this Story.

That investigation must define, before any FTMO admission decision:

- authorized reference sources;
- explicit human authorization and READ-ONLY collection procedure;
- synchronization and timestamp boundaries;
- observations with and without open positions;
- commissions, swaps, mirroring commission and potential closing commission;
- rounding and precision behavior;
- observable discrepancies and tolerances;
- decision criteria and evidence limitations.

No positions may be created artificially for that investigation. No LIVE
validation may occur without a separate human-authorized procedure.

---

## Relevant Modules

- `broker-service`
- `trading-core`
- `risk-domain`

---

## Validation

- Focused Broker Service contract and normalization tests.
- Focused Trading Core admissibility and fail-closed tests.
- Risk Domain regression tests proving provider neutrality and determinism.
- Synthetic fixtures for reported, derived and unavailable equity.
- Regression tests for existing providers.
- No provider calls or LIVE validation.
- `git diff --check`.

Full Maven suites are not required for documentation-only preparation of this
Story. They become applicable when the Story is implemented.

---

## Definition Of Done

- The provenance, quality and admissibility contract is implemented without
  provider leakage.
- `BROKER_DERIVED` remains rejected for the current FTMO policy.
- Missing or non-admissible required equity remains fail-closed.
- Existing providers retain their supported behavior.
- The future comparative FTMO validation is explicitly separate.
- No LIVE configuration, provider mutation or order capability is changed.
- Focused tests and `git diff --check` pass.
- The implementation report records all unresolved cTrader/FTMO equivalence
  questions without claiming T1 LIVE readiness.
