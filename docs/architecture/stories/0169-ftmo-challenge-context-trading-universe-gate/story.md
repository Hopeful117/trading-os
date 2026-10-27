# Story 0169 - FTMO Challenge Context and Trading Universe Gate

## Metadata

**ID:** `0169`
**Title:** FTMO Challenge Context and Trading Universe Gate
**Status:** Draft - Implementation Plan Awaiting Approval

## Goal

Prevent an FTMO-oriented market scan from starting unless the Challenge
definition, trading account, Risk configuration, broker/provider context, and
effective market universe describe one coherent trading reality.

This is a Challenge-safety capability. It must fail closed before Active Scan
creation when the context is invalid or incomplete.

## Observed Runtime Failure

The first FTMO-oriented PAPER dry-run observed:

```text
FTMO Challenge definition: 50,000 USD
PAPER account equity: 10,000 USD
Challenge activation: rejected
Reason: Account Risk-day configuration does not match Challenge definition
Active Scan: still allowed
Reported universe: 1,476 eligible markets
Selected opportunity: 2Z/EUR SHORT KRAKEN Spot
Intended context: FTMO + cTrader + PAPER
```

No Challenge rule was violated, no gate was bypassed, no PAPER trade was
executed, and no production code was modified during the dry-run.

## Invariant

```text
Challenge definition
    -> compatible Trading Account
    -> compatible Challenge and Risk configuration
    -> compatible Broker/Provider context
    -> compatible Market Universe
    -> Active Scan
```

An invalid link in this chain must prevent the scan from starting. Kraken
remains valid for non-Challenge and Kraken-specific Trading OS workflows.

## Scope

- Trace and enforce the authoritative relationship between Challenge definition
  starting balance and the account used by the Challenge.
- Enforce compatibility between Challenge rules and the account Risk
  configuration without weakening deterministic Risk semantics.
- Represent the smallest provider-neutral Challenge trading context required to
  identify the Challenge, account, provider, and valid market universe.
- Enforce provider-compatible market scope before Active Scan creation.
- Ensure an FTMO/cTrader context cannot include Kraken Spot markets.
- Fail closed with a structured, deterministic, trader-facing result when the
  context is invalid.
- Return an explicit no-compatible-markets outcome without falling back to
  another provider.
- Expose Challenge readiness and the effective provider/universe in the existing
  Trader Cockpit and Market Navigator.
- Disable the scan action in the UI when backend readiness is blocked while
  retaining mandatory backend enforcement.
- Preserve generic and non-Challenge scanning and personal Kraken workflows.
- Establish a coherent PAPER runtime context for the next FTMO dry-run when the
  existing supported account flow permits it.
- Validate one bounded FTMO/cTrader-compatible scan without executing a PAPER
  trade.

## Out of Scope

- General session, portfolio, or orchestration framework redesign.
- New Risk calculations, Risk-rule tuning, or Risk overrides.
- Automatic account balance mutation or silent account conversion.
- Automatic selection of another Challenge definition.
- Disabling Kraken globally.
- New cTrader market-data or analytical capabilities.
- Changes to Market Thesis, strategy, Opportunity scoring, entry, Stop,
  Target, execution, or position management.
- Fixing scan latency or downstream missing Thesis evidence observed in the
  first dry-run before this context gate is validated.
- Live execution or real-money activity.

## Required Gate Semantics

Before an FTMO-oriented Active Scan starts, validate at minimum:

- Challenge exists and its phase permits preparation.
- Challenge account ownership is valid.
- Account balance/equity is compatible with the Challenge definition.
- Challenge Risk configuration is compatible with the authoritative definition.
- Broker/provider context is compatible with the Challenge.
- Requested and effective market scope is provider-compatible.
- At least one compatible market exists.

Use existing repository naming conventions for the structured outcomes. The
result must distinguish at least these semantic cases where applicable:

```text
READY
ACCOUNT_BALANCE_MISMATCH
RISK_POLICY_MISMATCH
PROVIDER_MISMATCH
NO_COMPATIBLE_MARKETS
CHALLENGE_NOT_ACTIVE
ACCOUNT_UNAVAILABLE
```

## FTMO/cTrader Context

For the first target context:

```text
FTMO 2-Step
    -> compatible PAPER account
    -> matching Challenge balance and Risk configuration
    -> cTrader provider context
    -> cTrader-compatible market universe
```

The implementation must not spread FTMO-specific conditionals across unrelated
services. Challenge definitions and provider-neutral compatibility metadata
should remain authoritative at the appropriate domain boundary.

## Acceptance Criteria

- [ ] A Challenge/account balance mismatch blocks Challenge readiness and scan
      creation with a structured actionable result.
- [ ] A Challenge/Risk policy mismatch blocks Challenge readiness and scan
      creation without weakening Risk validation.
- [ ] A provider mismatch blocks an FTMO/cTrader Challenge scan before market
      analysis begins.
- [ ] Kraken markets cannot enter an FTMO/cTrader Challenge scan.
- [ ] A compatible cTrader market can enter the Challenge scan scope.
- [ ] An empty compatible cTrader universe fails closed without Kraken fallback.
- [ ] A direct API scan request cannot bypass the backend pre-scan gate.
- [ ] The existing Challenge account ownership boundary remains enforced.
- [ ] Non-Challenge Kraken workflows remain valid.
- [ ] Generic/non-Challenge market scanning remains backward compatible.
- [ ] The existing Trader Cockpit/Market Navigator exposes Challenge identity,
      phase, size, account, provider, effective universe, and readiness.
- [ ] The UI disables scan when readiness is blocked and shows the reason.
- [ ] Challenge activation succeeds for a coherent supported FTMO PAPER context,
      or the runtime blocker is explicitly documented if supported configuration
      cannot be established without mutating historical truth.
- [ ] A bounded runtime validation proves the active Challenge, account,
      compatible Risk configuration, cTrader provider context, cTrader-only
      effective universe, and scan creation without PAPER execution.
- [ ] Focused tests cover all compatibility and fail-closed cases.
- [ ] Existing Risk, Challenge, Scanner, Market Intelligence, cTrader, and
      Kraken tests remain passing.

## Relevant Stories

- `0154` - FTMO 2-Step 50k Definition.
- `0167` - cTrader Market Data for FTMO Dry-Run.
- `0168` - Thesis-Aware Opportunity Decision Gate.
- `0078` - Challenge Definition and Instance Domain.
- `0092` - Challenge Dashboard Angular Workflow.
- `0035` - Scope Active Market Scans and Surface Per-Market Results.
- `0145` - Generalize Provider Registration and Account Routing.
- `0146` - Establish cTrader Provider Identity and Connection Account Foundation.

## Relevant Modules

- `trading-core`
- `market-intelligence`
- `market-data`
- `broker-service`
- `gateway`
- `trading-os-web`

## Validation

- Focused Challenge/account/Risk compatibility tests.
- Focused provider and market-universe gate tests.
- Direct API fail-closed tests.
- Non-Challenge Kraken regression tests.
- Angular Cockpit and Market Navigator tests.
- Affected Maven module tests.
- Angular test and production build checks.
- `git diff --check`.
- Bounded authenticated runtime validation using PAPER only and no trade
  execution.

## Workflow Status

- [x] Story artifact created.
- [x] Repository Analysis completed.
- [x] Repository Analysis approved by human.
- [x] Implementation Plan prepared.
- [ ] Implementation Plan approved.
- [ ] Implementation completed.
- [ ] Documentation reconciliation completed.
- [ ] Code Review completed.
- [ ] Human commit and integration completed.
