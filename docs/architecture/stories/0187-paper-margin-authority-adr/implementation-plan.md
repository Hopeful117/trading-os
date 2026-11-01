# Implementation Plan - Story 0187

## Objective

Produce an accepted-ready ADR-083 proposal defining the provider-neutral PAPER
margin authority and simulation policy required after Story 0186. This Story is
documentation and architecture only. It must not implement the policy or change
the current compatibility matrix.

## Planned ADR Decision

The ADR will propose a hybrid/reference-derived model:

```text
provider/reference/user configuration
        -> normalized provider-neutral MarginPolicy
        -> immutable versioned PAPER account assignment
        -> local deterministic T0/T1 simulation
```

The runtime authority will be the local versioned policy, not Broker Service or a
provider API. Provider/reference provenance may be preserved for explanation and
configuration, but provider calls will not be required for every PAPER decision.

## ADR Content

Create `docs/architecture/adr/ADR-083-paper-margin-authority-and-simulation-policy.md`
with:

- Context and the Story 0186 blocker.
- Decision and authority model for PAPER and LIVE.
- Separation between `InstrumentFinancialContract`, `MarginPolicy`, and FTMO
  challenge rules.
- Proposed-order required-margin definition as incremental collateral required
  for the proposed order at a decision boundary.
- Minimum policy inputs: canonical contract, signed quantity, reference price,
  account reporting currency, static margin rate, existing exposure, netting
  model, policy version, effective time, and explicit FX evidence where needed.
- V1 scope: static linear margin policy, netting only, no portfolio offsets,
  no cross-instrument collateral optimization, and fail-closed unsupported
  cases.
- Account/simulation-profile policy ownership, assignment, immutable versioning,
  effective-time behavior, and historical evidence retention.
- Canonical margin-rate representation, positive-value validation, precision,
  and explicit monetary rounding policy for the later implementation.
- Quote-currency calculation and directed Market Data FX conversion to account
  reporting currency.
- T0/T1 policy-version and fresh-fact semantics, including policy changes between
  boundaries.
- Required provenance and failure semantics.
- Explicit migration decision for the current PAPER provider preview: retain as
  configuration/diagnostic evidence only, remove it from authorization in the
  implementation Story.
- Consequences, rejected alternatives, acceptance examples, invariants, and
  implementation sequencing.
- Cross-reference to ADR-082 without modifying ADR-082.

## Story Artifacts

After the ADR proposal is drafted:

1. Update `implementation-report.md` to record documentation-only output, no
   production changes, and validation evidence.
2. Perform an independent architecture review in `code-review.md` covering
   provider coupling, financial-contract duplication, exposure semantics,
   provenance, T0/T1 races, Risk Domain leakage, and compatibility scope.
3. Resolve HIGH/MEDIUM review findings in the ADR or report them as unresolved
   architectural decisions requiring human input.
4. Produce `engineering-report.md` only after review and workflow approval.

## Validation

- Verify ADR and Story cross-references.
- Verify required ADR sections and invariant consistency.
- Verify examples are explicitly hypothetical and do not claim current margin
  truth.
- Run `git diff --check` for all new Markdown artifacts.
- Confirm no production Java, database, API, or compatibility files changed.
- Confirm `CASH + CFD` and `INVENTORY + CFD` remain rejected.

## Explicit Non-Changes

- Do not add `MarginPolicy.java` or a calculator.
- Do not evolve `RequiredMarginPort` in this Story.
- Do not alter `BrokerRequiredMarginClient` behavior in this Story.
- Do not modify Risk Domain, settlement, account persistence, or Broker Service.
- Do not call cTrader, submit orders, close positions, or enable safety execution.
- Do not retry ADR-082 Phase 8 compatibility authorization.

## Approval Boundary

This plan requires explicit human approval before the ADR proposal and remaining
Story artifacts are created.
