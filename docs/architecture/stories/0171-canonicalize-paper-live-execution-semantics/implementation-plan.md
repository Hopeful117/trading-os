# Implementation Plan - Story 0171

## Plan Status

**Story:** `0171 - Canonicalize PAPER and LIVE Execution Semantics`
**Status:** Approved - Implemented Locally

## Objective

Prevent the specific operational error observed during the cTrader dry run:

```text
cTrader provider account is economically DEMO/SIMULATED
-> incorrectly select the Trading OS PAPER journey
```

The implementation will make the execution-boundary rule explicit and prove it
without changing existing persisted modes, provider facts, runtime account data,
or execution routing architecture.

## Design Decision

Use the existing two-value enum without adding a third mode:

```text
PAPER = execution remains entirely inside Trading OS
LIVE  = execution or authoritative facts cross an external broker/provider boundary
```

The following remain separate and must not determine `ExecutionMode`:

- provider transport environment;
- provider-reported `isLive`;
- provider account classification;
- economic DEMO/SIMULATED status;
- FTMO Challenge phase;
- execution permission.

## Implementation Steps

### 1. Domain Documentation

Update `ExecutionMode.java` with concise Javadoc on the enum and both values.
The documentation must state that LIVE is an external-boundary mode, not a
real-money label, and that PAPER is local simulation only.

No enum values, persistence mapping, constructors, or API contracts change.

### 2. Canonical Architecture ADR

Create the next available ADR:

```text
docs/architecture/adr/ADR-078-paper-live-execution-boundary.md
```

The ADR will document:

- context and the observed cTrader dry-run confusion;
- canonical PAPER and LIVE definitions;
- the local PAPER mutation invariant;
- LIVE authority and authorization consequences;
- provider/economic-environment separation;
- the FTMO/cTrader DEMO -> Trading OS LIVE example;
- consequences for Risk Facts and provider authority;
- rejected alternatives `PAPER = all demo` and `LIVE = real money only`.

It will reference ADR-029, ADR-042, ADR-043, and ADR-071 and will not redefine
provider-specific cTrader facts.

### 3. Agent and Project Context Guardrail

Update `AGENTS.md` in the architecture/semantic guidance section with the same
short rule used by the ADR:

```text
Does execution remain entirely inside Trading OS?
YES -> PAPER
NO, an external broker/provider boundary is involved -> LIVE
```

Add the explicit FTMO/cTrader example and warn agents not to infer PAPER from a
provider demo label or `isLive=false`.

### 4. Preserve and Strengthen Execution Invariants

Do not redesign `RoutingBrokerExecutionAdapter`, `SimulatedExecutionAdapter`,
or `ModeAwareRiskFactsProvider`. Add only the smallest focused test coverage
needed to make the invariant executable:

- direct `SimulatedExecutionAdapter` submission with a LIVE BrokerAccount is
  rejected before Market Data access;
- routing a PAPER request invokes the simulator and not the external adapter;
- routing a LIVE request invokes the external adapter and not the simulator;
- PAPER exit remains local and does not call the provider close client;
- an economically simulated/provider-demo cTrader fixture remains represented
  as `ExecutionMode.LIVE` at the connected-account/provisioning boundary;
- provider `isLive` remains provider data and is not converted into the enum.

Reuse existing `PaperExecutionVerticalRegressionTest`,
`ModeSpecificExecutionBoundaryTest`, and mode-aware Risk Facts tests instead of
duplicating their full scenarios.

### 5. Minimal Current UI Terminology

Update the Accounts page only where raw mode labels can be interpreted as
economic environment labels:

```text
PAPER - Simulated inside Trading OS
LIVE - Connected to external broker
```

Preserve the persisted values, form values, API payloads, cTrader binding flow,
and all freshness/status values that independently use `LIVE`.

Add or update focused Angular assertions for the explanatory wording only.

### 6. Historical Documentation Reconciliation

Do not rewrite closed Stories or historical reports. Add the current ADR and
agent guidance as the authoritative interpretation. Classify older phrases such
as “PAPER subset versus real-provider” as historical readiness context during
the implementation report.

Do not change the existing `ActiveChallengeMonitoringService` PAPER-only filter
in this Story. It is a separate product-scope decision about monitoring LIVE
Challenges, not necessary to establish the mode meaning. If validation shows it
blocks a required acceptance criterion, stop and request a separate scope
decision rather than expanding this plan silently.

## Files Expected To Change

- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/domain/ExecutionMode.java`
- focused existing or new Trading Core execution boundary test files
- focused cTrader/account semantic test file in the smallest appropriate module
- `trading-os-web/src/app/features/accounts/pages/accounts/accounts.html`
- related Accounts Angular spec if wording changes
- `AGENTS.md`
- `docs/architecture/adr/ADR-078-paper-live-execution-boundary.md`
- Story implementation artifacts under this directory

No runtime capture files, credentials, provider configuration, account data,
challenge data, cTrader safety settings, or unrelated worktree files will be
modified.

## Validation Plan

1. Run focused Trading Core execution routing and PAPER boundary tests.
2. Run focused Broker Service tests only if the cTrader semantic fixture is
   placed in Broker Service.
3. Run focused Angular Accounts tests if wording changes.
4. Run relevant module test/build commands according to repository conventions.
5. Search the repository for active assumptions equating `PAPER` with provider
   demo or `LIVE` with real money.
6. Run `git diff --check`.
7. Confirm no test or runtime validation submits an external broker order.

## Risks and Mitigations

- **Risk:** Freshness/status labels named LIVE are accidentally changed.
  **Mitigation:** Limit UI changes to account execution-mode wording and retain
  separate freshness types.
- **Risk:** FTMO/cTrader provider logic leaks into Trading Core.
  **Mitigation:** Keep the semantic regression at an existing provider-neutral
  account/provisioning boundary and treat `isLive` as provider-owned data.
- **Risk:** The Story silently broadens LIVE Challenge monitoring.
  **Mitigation:** Leave `ActiveChallengeMonitoringService` unchanged and report
  the open product decision.
- **Risk:** Existing PAPER behavior regresses.
  **Mitigation:** Reuse the full vertical PAPER regression and add only focused
  negative/route assertions.

## Out of Scope Confirmation

This plan does not change:

- Risk formulas or Challenge rules;
- Market Data, Market State, Scanner, Thesis, Opportunity, or TradePlan
  semantics;
- cTrader `isLive` values or provider environment configuration;
- account binding or runtime FTMO data;
- cTrader request budgets, snapshot cooldowns, or execution enablement;
- external broker order execution.
