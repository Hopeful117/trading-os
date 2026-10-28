# Story 0171 - Canonicalize PAPER and LIVE Execution Semantics

## Metadata

**ID:** `0171`
**Title:** Canonicalize PAPER and LIVE Execution Semantics
**Status:** Implemented Locally - Awaiting Review
**Related ADRs:** `ADR-029`, `ADR-042`, `ADR-043`, `ADR-071`
**Related Stories:** `0030`, `0037`, `0038`, `0040`, `0041`, `0048`, `0052`, `0154`, `0169`, `0170`

---

## Goal

Establish one canonical Trading OS meaning for `ExecutionMode.PAPER` and
`ExecutionMode.LIVE`, then reconcile domain code, execution routing, tests,
architecture documentation, and user-facing terminology with that meaning.

The distinction is an execution-boundary distinction, not a real-money or
provider-environment distinction.

---

## Context

Previous PAPER and FTMO/cTrader rehearsals exposed repeated ambiguity between:

```text
Trading OS execution mode
provider technical environment
provider-reported isLive flag
provider account classification
economic environment
```

The existing repository already establishes that PAPER execution and settlement
remain local to Trading Core, while LIVE facts and execution use the external
Broker Service boundary. The meaning must be made explicit so that a provider
hosted demo account is not incorrectly converted into a local PAPER account.

For FTMO/cTrader, the business provider is FTMO and the execution platform is
cTrader. A connected cTrader demo, free-trial, Challenge, Verification, or
funded account crosses the external broker boundary and is therefore
`ExecutionMode.LIVE` in Trading OS, even when its economic environment is
DEMO/SIMULATED or cTrader reports `isLive=false`.

`LIVE` remains independent from execution permission. A LIVE account may keep
external execution disabled until human authorization, deterministic Risk,
Challenge, provider capability, freshness, and safety gates all pass.

---

## Problem

The enum values `PAPER` and `LIVE` are widely used but their business meaning
is not documented directly at the domain boundary. Existing wording and tests
can encourage one of two invalid inferences:

```text
PAPER = every economically simulated or demo account
LIVE = real-money account only
```

Those inferences can misroute authoritative provider facts, incorrectly select
local simulated execution, or make a cTrader FTMO account appear PAPER merely
because the provider reports a non-live environment.

The repository needs a focused consistency audit and the smallest necessary
documentation, invariant, test, and user-facing wording changes.

---

## Canonical Decision

### `ExecutionMode.PAPER`

```text
Execution is simulated entirely inside Trading OS.
Trading instructions never leave Trading OS toward a broker or provider.
Orders, fills, settlement, and position lifecycle remain inside the local
Trading OS simulated execution boundary.
```

### `ExecutionMode.LIVE`

```text
The account participates in the external broker/provider execution boundary.
Authoritative broker facts may come from the external provider.
When explicit execution permission and all Risk, Challenge, capability,
safety, and human-authorization gates pass, instructions may leave Trading OS.
```

The canonical invariant is:

```text
Does the trade/order remain entirely inside Trading OS?

YES -> PAPER
NO, an external broker/provider boundary is involved -> LIVE
```

Therefore:

```text
LIVE != REAL_MONEY
PAPER != ALL_DEMO_ACCOUNTS
```

Provider technical environment, provider account classification, economic
environment, Challenge phase, and execution permission remain separate facts.

---

## Scope

- Audit all `ExecutionMode` references and current PAPER/LIVE assumptions.
- Audit `BrokerAccount`, account creation/update flows, execution routing,
  `RoutingBrokerExecutionAdapter`, `SimulatedExecutionAdapter`, and
  `ModeAwareRiskFactsProvider`.
- Verify that PAPER entry, exit, close, cancellation, modification, and
  provider-specific capability paths cannot reach an external provider.
- Verify that LIVE does not route to the simulated execution adapter.
- Document the canonical invariant directly on the `ExecutionMode` enum.
- Create or update the appropriate canonical architecture ADR with the context,
  decision, invariants, FTMO/cTrader example, consequences, and rejected
  alternatives.
- Reconcile current architecture and engineering-agent documentation without
  rewriting historical Story records.
- Correct current misleading user-facing PAPER/LIVE wording with minimal UI
  changes where required.
- Add focused PAPER boundary, LIVE boundary, and FTMO/cTrader semantic
  regression tests using mocks or fakes only.
- Report remaining active, historical, documentation, and UI ambiguities.

---

## Out of Scope

- Adding another `ExecutionMode` value such as `DEMO`, `SIMULATED`,
  `CHALLENGE`, `FUNDED`, `REAL`, or `BROKER`.
- Renaming persisted `PAPER` or `LIVE` values.
- Changing cTrader `isLive` or redefining provider facts.
- Creating or provisioning a cTrader connection or changing FTMO runtime data.
- Fixing account binding or Challenge projection unless the audit proves the
  defect is directly caused by the PAPER/LIVE semantic confusion.
- Changing Risk formulas, Challenge rules, FTMO targets, Market State, Market
  Data, Active Scanner, Market Thesis, Opportunity, or TradePlan semantics.
- Changing cTrader request budgets, snapshot cooldowns, or execution safety
  configuration.
- Enabling cTrader, Kraken, or any other external order execution.
- Runtime broker orders, real-money trading, or external provider mutation.
- Generic architecture redesign, multi-broker expansion, AI, or UI redesign.

---

## Acceptance Criteria

- [ ] `ExecutionMode.PAPER` is documented as entirely local Trading OS
      simulation and never as a generic provider-demo classification.
- [ ] `ExecutionMode.LIVE` is documented as the external broker/provider
      boundary and never as a real-money-only classification.
- [ ] Provider technical environment, provider `isLive`, economic environment,
      account classification, and execution permission remain separate concepts.
- [ ] PAPER routes to local simulated execution and cannot reach cTrader,
      Kraken, or any future external broker mutation path.
- [ ] PAPER exit and close behavior remain local and covered by focused tests.
- [ ] `SimulatedExecutionAdapter` rejects LIVE requests.
- [ ] LIVE does not route to `SimulatedExecutionAdapter`.
- [ ] A connected economically simulated FTMO/cTrader account remains
      `ExecutionMode.LIVE`.
- [ ] No production logic infers `ExecutionMode` from provider `isLive`.
- [ ] LIVE remains insufficient by itself to authorize execution.
- [ ] LIVE provider-backed facts remain authoritative where the external
      integration owns them; PAPER facts remain locally authoritative.
- [ ] Current architecture documentation and coding-agent context state the
      canonical rule and FTMO/cTrader example.
- [ ] Current misleading UI wording, if present, is corrected minimally;
      persisted enum values and historical Story records are preserved.
- [ ] Focused Trading Core execution routing and PAPER regression tests pass.
- [ ] Relevant Broker Service tests pass if provider-boundary code is changed.
- [ ] Relevant Angular tests pass if user-facing wording or logic is changed.
- [ ] `git diff --check` passes.
- [ ] No external broker order is submitted by tests, builds, or validation.

---

## Constraints

- Preserve the responsibility boundaries in ADR-029, ADR-042, ADR-043, and
  ADR-071.
- Keep Trading Core provider-neutral; provider-specific `isLive` handling stays
  inside the provider adapter and its broker-neutral translation boundary.
- Keep deterministic Risk, Challenge, authorization, and execution-state rules
  authoritative.
- Preserve human authority and fail-closed execution safety.
- Do not silently reinterpret existing persisted account modes.
- Do not modify unrelated pre-existing worktree files.
- Do not expose or create credentials, tokens, or provider secrets.

---

## Relevant Architecture

- `docs/architecture/adr/ADR-029.md` - execution domain and authorization.
- `docs/architecture/adr/ADR-042.md` - local PAPER authority and execution
  boundary.
- `docs/architecture/adr/ADR-043.md` - mode-aware neutral Risk Facts.
- `docs/architecture/adr/ADR-071.md` - FTMO/cTrader boundary and provider
  environment semantic separation.

---

## Relevant Modules

- `trading-core`
- `risk-domain`
- `broker-service`
- `gateway`
- `trading-os-web`
- `docs/architecture`

---

## Validation

- Repository-wide semantic search before and after implementation.
- Focused Trading Core execution routing and PAPER boundary tests.
- Focused Broker Service tests when provider-boundary behavior is changed.
- Focused Angular tests when current user-facing terminology is changed.
- `git diff --check`.
- Confirm no runtime broker order or provider mutation is attempted.

---

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Canonical PAPER/LIVE semantics documented in the appropriate ADR and
      domain code.
- [ ] PAPER external-mutation boundary proven by focused tests.
- [ ] LIVE external-boundary semantics and FTMO/cTrader regression covered.
- [ ] Current misleading assumptions reconciled without rewriting history.
- [ ] Required validation passes.
- [ ] Implementation and documentation review completed.
- [ ] Human engineering approval completed.
