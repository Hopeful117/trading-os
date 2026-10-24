# Story 0154 - First Executable FTMO 2-Step 50k Challenge Definition

## Metadata

- **ID:** `0154`
- **Status:** `IMPLEMENTATION IN PROGRESS`
- **Related ADRs:** `ADR-075`, `ADR-028`, `ADR-031`, `ADR-074`
- **Related Stories:** `0150`, `0151`, `0152`, `0153`
- **Repository Analysis:** approved by human engineer
- **Implementation Plan:** `implementation-plan.md`

## Goal

Use the existing provider-neutral Challenge and deterministic Risk architecture
to represent and evaluate one versioned FTMO 2-Step Challenge definition:

```text
provider: FTMO
program: FTMO 2-Step
reference account: 50,000 USD
execution platform: cTrader
integration target: cTrader Open API
```

The implementation must support local deterministic progress and explicit
unavailability states without claiming cTrader runtime validation or provider
confirmation that has not been observed.

## Context

Engineering V1 is closed and the current product phase is
`PRE_CHALLENGE_READINESS`.

ADR-075 provides immutable, versioned, provider-neutral multi-phase Challenge
definitions with phase objectives, Risk-policy references, executed-trading
evidence, and explicit local versus provider-confirmed progression.

The Risk Domain remains the deterministic authority for financial constraints.
Story 0153 added explicit daily-drawdown boundary semantics. Real cTrader
runtime validation remains externally blocked until the Spotware application is
activated, but deterministic FTMO definition work must not wait for that event.

## Verified External Sources

The following official FTMO source was consulted on 2026-10-24:

- `https://ftmo.com/en/trading-objectives/`
- `https://ftmo.com/en/challenge/`

The current source describes the paid FTMO Challenge: 2-Step as a two-phase
evaluation with the following rules:

- FTMO Challenge profit target: 10% of Initial Simulated Capital.
- Verification profit target: 5% of Initial Simulated Capital.
- Maximum Daily Loss: 5% of Initial Simulated Capital, recalculated at 00:00
  CE(S)T from the balance recorded at that reset boundary.
- Maximum Loss: 10% of Initial Simulated Capital and static for the 2-Step
  evaluation phases.
- Minimum Trading Days: 4 days, with a Trading Day defined as 00:00:00 to
  23:59:59 CE(S)T and at least one position opened.
- No maximum trading-period time limit.
- Equity for loss limits includes balance, open-position P/L, swaps and
  commissions according to the official wording.

For a 50,000 USD definition, the readable absolute values are:

- Challenge target: 5,000 USD; target balance: 55,000 USD.
- Verification target: 2,500 USD; target balance: 52,500 USD.
- Maximum Daily Loss amount: 2,500 USD.
- Maximum Loss amount: 5,000 USD; static floor: 45,000 USD.

The source wording states that a loss rule is violated when equity drops below
the applicable limit. Boundary behavior must therefore be represented and tested
explicitly; the implementation must not silently assume the existing inclusive
total-drawdown behavior is FTMO-compatible.

## Scope

- Add one immutable, versioned FTMO 2-Step 50k Challenge definition using the
  existing generic Challenge model.
- Add two ordered phases: `CHALLENGE` and `VERIFICATION`.
- Configure phase-specific profit targets of 10% and 5%.
- Configure four minimum trading days for each evaluation phase.
- Reference versioned provider-neutral Risk policies from both phases.
- Configure the verified FTMO daily and total loss limits using generic Risk
  capabilities.
- Add the smallest provider-neutral Risk capability required if static Maximum
  Loss boundary semantics cannot be represented safely by the current model.
- Preserve immutable definition identity and historical reproducibility.
- Evaluate local phase satisfaction from available authoritative account facts and
  complete executed-trading evidence.
- Preserve explicit provider-confirmation requirements between phases.
- Expose unavailable or incomplete progress/headroom explicitly and fail closed.
- Add deterministic unit, persistence, migration and integration coverage.
- Reconcile the canonical Story and relevant implementation documentation.

## Out Of Scope

- FTMO-specific classes, enums or branches inside generic Challenge or Risk code.
- cTrader OAuth, credentials, provider adapter, execution or order mutation.
- Spotware runtime validation or fabricated provider evidence.
- Provider-confirmed progression inferred from local balances, Risk results or
  trading-day counts.
- Frontend work.
- S1, AI Engine, ML, passive scanner, active scanner or News Service changes.
- Free Trial implementation unless the current repository proves it is required
  by the definition itself.
- News restrictions, overnight/weekend restrictions, payouts, funded-account
  lifecycle or provider terminal actions not established by the verified source.
- Duplicating Maximum Daily Loss, Maximum Loss or headroom formulas in Challenge.
- Changing legacy single-phase, PAPER or Kraken behavior.
- A new ADR unless this work proves that the Challenge-effective Risk-day
  authority cannot be represented safely by existing generic configuration.

## Acceptance Criteria

### Definition identity and provisioning

- A canonical FTMO 2-Step 50k definition has an explicit immutable identity and
  version.
- The definition records FTMO, the 2-Step program, USD currency and 50,000 USD
  starting capital without hardcoding 50,000 inside generic rules.
- Provenance records the verified source URLs, retrieval date and interpreted
  rules.
- Starting a Challenge preserves the exact definition identity and Risk-policy
  versions.
- A later FTMO rule change requires a new definition version and cannot mutate an
  existing Challenge definition or instance.

### Phases and progression

- Phase 1 is represented by a generic ordered phase with a 10% profit target and
  four minimum trading days.
- Verification is represented by a generic ordered phase with a 5% profit target
  and four minimum trading days.
- A phase cannot be skipped or activated out of order.
- Profit target and minimum trading-day objectives are required concurrently.
- Local objective satisfaction is distinct from provider confirmation.
- Missing provider confirmation cannot activate Verification.
- Provider confirmation cannot be inferred from balance, equity, Risk approval,
  local trading-day evidence or account state.
- A final phase can only complete under its declared progression policy.

### Risk policy

- The FTMO policy references generic deterministic Risk rules only.
- Maximum Daily Loss is configured at 5% of initial capital.
- Maximum Loss is configured at 10% of initial capital with static semantics.
- Daily and total boundary semantics are explicit and tested against the verified
  FTMO wording.
- Equality at a configured boundary is not silently classified without explicit
  evidence and a named configuration.
- Risk continues to own financial calculations and breach authority.
- Currency mismatch, missing policy, missing baseline, stale facts and incomplete
  required costs fail closed.

### Risk day and authoritative facts

- The daily Risk interval is calculated deterministically from a named IANA
  timezone and 00:00 reset, including CET/CEST transitions.
- The selected Risk-day configuration cannot silently fall back to machine,
  Docker, database, browser or user timezone.
- The daily baseline is authoritative, reproducible and restart-safe.
- Current equity is consumed once as the authoritative provider/account value;
  floating P/L, commission or swap is not double-counted.
- Required commission and swap facts are unavailable rather than estimated when
  the authority chain cannot establish them.
- The implementation does not claim real cTrader facts until runtime validation
  is complete.

### Progress and headroom

- Local progress can expose current phase, targets, trading-day counts, local
  satisfaction and provider-confirmation state.
- Risk headroom is consumed from authoritative persisted/evaluable Risk facts.
- Challenge does not independently recompute Risk formulas.
- Daily-loss or total-loss headroom remains explicitly unavailable when the
  authoritative Risk evaluation or required facts are unavailable.
- A partial state cannot be presented as Challenge-safe or execution-eligible.

### Compatibility and safety

- Existing legacy single-phase Challenges remain readable and semantically
  unchanged.
- Existing PAPER and Kraken definitions remain compatible and covered by
  regression tests.
- `CTRADER_RUNTIME_VALIDATION` remains `BLOCKED_EXTERNAL` until Spotware
  activation and controlled validation.
- `CTRADER_EXECUTION_ENABLED` remains `NO`.
- No FTMO logic is added to Broker Service.
- No cTrader types, credentials, OAuth tokens or provider payloads enter the
  Challenge definition or generic Risk domain.

## Required Tests

- Definition identity, version, currency and 50,000 USD starting capital.
- Phase ordering and independent 10% / 5% targets.
- Four-day minimum objective and distinct qualifying days.
- Local satisfaction with incomplete profit or incomplete trading days.
- Provider confirmation required before Verification activation.
- Final phase completion and provider-confirmation semantics.
- Maximum Daily Loss safe, exact boundary, breach, reset and DST cases.
- Maximum Loss safe, exact boundary and breach cases.
- Missing baseline, stale facts, missing costs and currency mismatch.
- Cross-reset open-position behavior.
- Historical definition immutability and new-version behavior.
- Headroom projection from authoritative Risk facts and explicit unavailable
  components.
- Legacy single-phase, PAPER, Kraken and migration regressions.
- `git diff --check` and affected module validation.

## Expected Validation

- Focused Challenge domain/application tests.
- Risk-domain tests for boundary and Risk-day behavior.
- Trading Core persistence and migration tests.
- Challenge progress/headroom tests.
- Existing Kraken benchmark and PAPER regression tests.
- Full `trading-core` tests when practical.
- Independent code review before delivery.

## Stop Conditions

Stop implementation and isolate a follow-up Story if:

- authoritative executed-trading evidence cannot be obtained from the existing
  lifecycle without inventing a source;
- Challenge-effective Risk-day authority requires an unapproved architectural
  redesign;
- required cTrader facts cannot be represented without changing the provider-
  neutral Broker contract;
- headroom requires duplicating Risk formulas or an unsafe second authority;
- FTMO official rules materially conflict with the values recorded here;
- a required rule boundary cannot be established authoritatively;
- implementation would require cTrader execution or Spotware workarounds.

## Human Boundary

This Story is a proposed canonical artifact. Repository Analysis approval is
required before Implementation Planning. Implementation requires explicit
approval of the Implementation Plan before code, seed migrations or production
configuration changes begin.

No commit, push, pull request or merge is authorized by this Story artifact.
