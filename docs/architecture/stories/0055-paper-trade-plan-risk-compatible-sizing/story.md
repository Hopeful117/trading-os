# Story 0055 - Make PAPER Trade Plan sizing compatible with effective risk

## Metadata

**ID:** `0055`
**Title:** Make PAPER Trade Plan sizing compatible with effective risk
**Status:** Draft

---

## Goal

Allow a valid PAPER Trade Plan generated from an active opportunity to reach an
approved deterministic risk evaluation when the market valuation is available,
without changing the configured risk thresholds or bypassing risk controls.

---

## Context

Story 0054 made current PAPER market valuation available to Trading Core. The
authenticated runtime journey now reaches deterministic risk evaluation with a
current compatible `ADA/USD` valuation.

The current PAPER planning flow generates a Trade Plan with a `10,000 USD`
notional for a `10,000 USD` account. The effective PAPER risk profile limits
maximum exposure to `3%`, so deterministic risk correctly rejects the plan with
`MAX_EXPOSURE`.

This is a planning and sizing compatibility gap, not a market-data or risk-rule
failure.

---

## Problem

The supported PAPER journey cannot continue from an otherwise valid opportunity
to an approved risk decision because the generated plan is incompatible with
the account's effective risk profile. Users receive a deterministic rejection,
but the planning flow does not prevent or explain the incompatible sizing
before risk evaluation.

---

## Scope

* Trace the effective PAPER Trade Planning Profile and risk profile inputs used
  to calculate Trade Plan notional and quantity.
* Make generated PAPER sizing compatible with the account's effective risk
  constraints.
* Preserve deterministic risk evaluation as the final authority.
* Keep the risk profile thresholds and rule semantics unchanged.
* Provide explicit validation or user feedback when requested sizing cannot
  satisfy the effective risk profile.
* Validate the corrected flow through the existing authenticated web journey.
* Add focused backend and frontend regression coverage for the discovered
  sizing incompatibility.

---

## Out of Scope

* Changing `MAX_EXPOSURE`, `MAX_POSITION_RISK`, or `DAILY_DRAWDOWN` thresholds.
* Weakening, bypassing, or duplicating deterministic risk enforcement.
* Synthetic prices or valuation fallbacks.
* LIVE broker or Kraken sandbox acceptance.
* Autonomous execution or unattended trading.
* User-selected risk-policy changes as part of Trade Plan creation.
* Redesigning opportunity ranking or Market Intelligence strategies.

---

## Acceptance Criteria

* [ ] A valid PAPER opportunity produces a Trade Plan whose requested exposure
      is compatible with the account's effective risk profile, or the user
      receives an explicit actionable sizing rejection before execution.
* [ ] With a recent compatible valuation, the corrected PAPER Trade Plan reaches
      deterministic risk evaluation without `MAX_EXPOSURE` caused by the
      default planning inputs.
* [ ] The risk evaluation remains authoritative and unchanged thresholds still
      reject genuinely excessive exposure.
* [ ] Trade Plan quantity, notional, and displayed sizing remain internally
      consistent after correction.
* [ ] Account currency and instrument currency compatibility remain enforced.
* [ ] LIVE account behavior is unchanged.
* [ ] The authenticated web journey exposes the resulting sizing or rejection
      state without manually constructing an API request.
* [ ] Focused Trading Core, Risk Domain, and frontend tests pass.
* [ ] The Angular production build passes.
* [ ] Runtime evidence records the account balance, effective risk limits,
      generated notional, valuation timestamp, and observed risk result.

---

## Constraints

* Preserve deterministic risk and human authorization boundaries.
* Do not modify risk thresholds to make the scenario pass.
* Use an isolated PAPER account and non-production credentials.
* Use existing Trade Planning Profile, Risk Profile, and Trade Plan contracts
  where they remain compatible with the approved architecture.
* Any change to risk semantics, ownership boundaries, or profile architecture
  requires a separate approved architectural decision.

---

## Relevant ADRs

* `docs/architecture/adr/ADR-001.md` - Trading OS Vision and Human Authority
* `docs/architecture/adr/ADR-014.md` - Trading Decision Pipeline
* `docs/architecture/adr/ADR-028.md` - Deterministic Risk
* `docs/architecture/adr/ADR-029.md` - Execution Domain Architecture

---

## Related Stories

* `0048-paper-trading-journey-runtime-acceptance`
* `0053-effective-paper-planning-profile`
* `0054-paper-valuation-runtime-readiness`

---

## Relevant Modules

* `trading-core`
* `risk-domain`
* `market-intelligence`
* `trading-os-web`

---

## Validation

* Trading Core Trade Plan and risk evaluation tests.
* Risk Domain rule and sizing tests.
* Angular `npm run test:ci`.
* Angular `npm run build`.
* Authenticated PAPER runtime walkthrough using the configured local
  environment.
* Negative validation with deliberately excessive requested exposure.
* `git diff --check` and review of runtime evidence.

---

## Definition of Done

* [ ] Repository Analysis approved.
* [ ] Implementation Plan approved when required.
* [ ] Story scope and sizing behavior approved.
* [ ] Implementation completed within this Story's scope.
* [ ] Acceptance criteria validated with evidence.
* [ ] Human code review completed.
* [ ] Human commit created.
