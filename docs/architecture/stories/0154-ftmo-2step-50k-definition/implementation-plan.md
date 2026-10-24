# Story 0154 - Implementation Plan

## Plan Status

`APPROVED - IMPLEMENTATION IN PROGRESS`

The Repository Analysis and Implementation Plan for Story 0154 were approved by
the human engineer. Implementation is proceeding within the approved scope.

## 1. Preserve The Authority Chain

Keep the existing boundaries:

```text
cTrader/provider facts
        -> provider-neutral account and execution evidence
        -> Risk Domain deterministic evaluation
        -> persisted/evaluable Risk facts and headroom
        -> Challenge phase progress projection
```

FTMO will be represented by immutable persisted configuration. No FTMO class,
enum or runtime branch will be added to generic Challenge, Risk or Broker code.

## 2. Risk Boundary Compatibility

Reuse the `breach_boundary` column introduced by V36 for both daily and total
drawdown rules.

Changes:

- Extend generic Risk profile validation so `STRICT` is valid for
  `MAX_TOTAL_DRAWDOWN` as well as `DAILY_DRAWDOWN`.
- Make `MaximumTotalDrawdownRule` select `maximum` or
  `maximumInclusiveBreach` from the persisted boundary.
- Preserve `INCLUSIVE` as the default for all historical profiles.
- Add exact-boundary and beyond-boundary tests for both rules.
- Do not add an FTMO-specific formula or migration column.

The FTMO seed will use `STRICT` only after the official wording is retained as
the provenance interpretation. If the official contract or human review
rejects that interpretation, implementation stops before the seed is added.

## 3. FTMO Risk Profile And Definition Seed

Add one additive Trading Core migration after the current migration baseline:

```text
V37__seed_ftmo_2step_50k_definition.sql
```

The migration will insert:

- one immutable Risk profile with a stable UUID and version `1.0.0`;
- `DAILY_DRAWDOWN`, ratio `0.05`, boundary `STRICT`;
- `MAX_TOTAL_DRAWDOWN`, ratio `0.10`, boundary `STRICT`;
- one immutable Challenge definition with stable UUID and version `1.0.0`;
- provider `FTMO`, product `2_STEP`, plan `STANDARD`, currency `USD`, capital
  `50000`;
- provenance containing both official FTMO URLs, retrieval date, rule
  interpretation and the cTrader runtime validation limitation;
- two ordered phases:
  - `CHALLENGE`, order 1, balance target `0.10`, minimum days `4`;
  - `VERIFICATION`, order 2, balance target `0.05`, minimum days `4`;
- the same versioned Risk policy reference for both phases;
- provider confirmation required between phases and for final progression only if
  the current generic progression contract requires it.

No cTrader account ID, OAuth identifier, token, provider payload or execution
configuration will be persisted by this migration.

## 4. Risk-day Authority

First verify whether the existing account-level `riskTimeZone` and
`riskDayResetTime` are immutable and authoritative for an active Challenge.

If they are sufficient:

- require a complete Risk configuration before Challenge start;
- require the FTMO-compatible named IANA timezone and `00:00` reset through a
  generic validation path;
- reuse `RiskDay` and persisted `DailyRiskBaseline` unchanged;
- add deterministic `Europe/Prague`-style CET/CEST boundary tests only if that
  named zone is the reviewed interpretation of FTMO CE(S)T.

The human engineer approved extending the generic authority model when the
existing account-level configuration is not sufficient. The implementation
therefore versions the Risk-day timezone/reset on the Challenge definition and
copies it to the immutable Challenge instance. Challenge start and monitoring
fail closed when the account configuration does not match the Challenge value.

This is a mandatory implementation checkpoint, not an implicit permission to
change Risk-day ownership.

## 5. Challenge Progression And Evidence

Reuse the existing ADR-075 implementation:

- `ChallengeDefinition` and `ChallengePhaseDefinition` for the seed;
- `ChallengeInstance` definition/version and active phase identity;
- `ChallengeEvaluator` for concurrent profit/day objectives;
- `ExecutedTradingEvidence` for distinct qualifying days;
- `providerConfirmationRequired` and opaque confirmation references for external
  progression.

Before production integration, identify the existing authoritative execution or
deal lifecycle source that can create complete `ExecutedTradingEvidence` with
Risk-day-resolved dates.

Allowed sources must represent executed/opened activity. Plans, intents,
submitted-but-unexecuted orders, balance changes and local timestamps alone are
not sufficient.

If no authoritative source exists without inventing a new provider contract,
stop and isolate that work as a follow-up Story. Tests may use explicit synthetic
evidence, but production progress must remain unavailable rather than inferred.

## 6. Risk Headroom Projection

Do not calculate daily or total drawdown independently from Challenge.

Add the smallest provider-neutral Risk read capability needed to expose the
latest complete account-monitoring evaluation, including:

- evaluation timestamp and freshness;
- Risk decision/status;
- resolved Risk policy version;
- authoritative account balance/equity;
- daily baseline and Risk-day interval;
- daily and total rule result metrics;
- unavailable/incomplete state.

Challenge progress will consume that Risk projection and expose:

- current phase and target;
- achieved/remaining profit;
- completed/required/remaining trading days when evidence is complete;
- daily-loss limit, usage and remaining headroom when Risk facts are complete;
- maximum-loss limit, usage and remaining headroom when Risk facts are complete;
- explicit unavailable components otherwise.

The implementation adds a generic persisted latest account-monitoring projection
with the daily baseline and Risk rule result payload. Challenge progress derives
headroom only from those persisted Risk outputs; absent, incomplete or policy-
mismatched evaluations produce explicit `UNAVAILABLE` headroom states.

## 7. Fail-closed Start And Evaluation

Ensure Challenge start/evaluation rejects or remains unavailable for:

- missing definition or definition version;
- missing Risk profile or version;
- currency mismatch;
- missing Risk configuration or incompatible Risk-day configuration;
- missing/stale/incomplete account facts;
- missing daily baseline;
- missing required commission/swap evidence where the authoritative account
  contract requires it;
- unknown phase or invalid progression evidence;
- absent required provider confirmation.

No failure path may convert unknown facts into zero, empty exposure, safe
headroom or provider confirmation.

## 8. Tests And Validation

### Risk domain

- strict and inclusive boundary behavior for daily and total drawdown;
- exact 50k absolute values through named fixture values;
- DST transitions and cross-reset open positions;
- missing baseline and invalid facts.

### Trading Core Challenge

- migration seed identity/version and phase ordering;
- 10% and 5% targets and four-day objectives;
- local satisfaction versus provider confirmation;
- invalid, stale, contradictory and future evidence;
- legacy single-phase behavior;
- headroom complete and unavailable projections.

### Persistence and regression

- Flyway fresh-install and migration history tests;
- existing Kraken benchmark tests;
- existing PAPER Challenge and Risk tests;
- targeted affected-module suites;
- full `trading-core` tests when practical;
- `git diff --check`.

## 9. Documentation Reconciliation

Update only the Story implementation report and any canonical documentation that
changes because of the implementation. Record explicitly:

- official FTMO source provenance;
- implementation result and validation limits;
- cTrader runtime status remains `BLOCKED_EXTERNAL`;
- no cTrader execution was enabled;
- any capability split into a follow-up Story;
- `Documentation update: ...` outcome.

No new ADR is created unless the Risk-day checkpoint proves a durable authority
decision is required.

## 10. Expected Files

Potential files, subject to the checkpoint results:

- `risk-domain/.../MaximumTotalDrawdownRule.java`;
- `risk-domain/.../RiskProfile` boundary tests;
- `trading-core/src/main/resources/db/migration/common/V37__seed_ftmo_2step_50k_definition.sql`;
- Challenge seed, evaluator and progress tests;
- generic Risk read/projection files only if required by the existing contracts;
- Story 0154 implementation report and code review report.

No cTrader adapter, Broker Service provider implementation, frontend file,
credential file or Spotware workaround is expected.

## 11. Approval Boundary

After this plan is approved, implementation may begin. If any checkpoint above
hits a stop condition, implementation must pause and report the evidence instead
of broadening the Story.
