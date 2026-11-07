# Implementation Plan - Story 0198

## Operating Mode

This Story is validation-first. The first execution stage is a runtime preflight
and account-identity proof. No code change, credential rotation, OAuth restart,
provider mutation or order request is part of the default path.

The existing worktree is clean on `main`. Any local correction discovered later
must remain isolated, minimal and separately visible in the implementation diff.

## 1. Repository and Runtime Preflight

Record without secrets:

- current branch, commit and worktree state;
- Story and ADR versions;
- service availability and configured endpoints;
- cTrader environment configuration (`demo`/`live`), without printing secrets;
- `CTRADER_SAFETY_EXECUTION_ENABLED` value;
- broker and Trading Core service health;
- Angular application availability if an interactive run is possible.

Inspect the normal account APIs and UI, not database credentials or token
material. Do not decode, print or copy stored secrets.

If services are not running or the authenticated application context is not
available, classify runtime checks as `BLOCKED` or `NOT_TESTED`; do not invent a
successful E2E result.

## 2. Existing Account Identity Proof

Resolve the current authenticated user's broker-account list through the normal
Trading OS mechanism. Select an account only when all of these facts agree:

- local broker account identity;
- provider is cTrader;
- display/name or documented account metadata identifies the FTMO Free Trial;
- execution mode and provider environment match the expected account;
- durable `externalAccountId` is present;
- connection status is usable;
- permissions are available for the intended read-only checks.

The value `49289512` must not be used merely because it occurs in fixtures. The
actual external account ID must come from the existing configured connection and
be confirmed by the provider response.

If more than one candidate matches, the identity is ambiguous and all real
provider calls stop. If no candidate can be identified, record the exact blocker
and do not create or reauthorize an account.

## 3. Controlled Read-Only Provider Validation

Use the existing credential/token and adapter paths only. Respect safety leases,
cooldowns and request budgets. Do not bypass persisted safety state to repeat a
call.

Execute, in the smallest number of controlled calls:

1. Existing connection status and authorization state.
2. One account-scoped authoritative read-only snapshot.
3. One or more symbol capability/metadata/quote reads only for a symbol already
   present on the confirmed account or explicitly selected in the test plan.
4. Account synchronization through the existing Trading Core path when the
   account identity proof and safety guard permit it.

For each check, record only safe metadata:

- result: `PASS`, `FAIL`, `BLOCKED` or `NOT_TESTED`;
- endpoint/capability name;
- local broker account ID and redacted external-account reference if needed;
- provider environment;
- response identity match result;
- completeness/freshness and missing-fact reasons;
- timestamp and correlation identifier when safe;
- whether the result came from a real provider call or an application layer.

Never record access tokens, refresh tokens, authorization codes, client secrets,
database passwords or raw provider payloads.

Expected read-only result codes include:

```text
CTRADER_ACCOUNT_IDENTIFIED
CTRADER_AUTHENTICATION_VALID
CTRADER_ACCOUNT_AUTHORIZED
CTRADER_ENVIRONMENT_MATCHED
CTRADER_READ_ONLY_CONNECTION_VALID
```

Each code must have an observable proof or be marked `FAIL`, `BLOCKED` or
`NOT_TESTED`.

## 4. Market and Account Data Matrix

For the same confirmed account, classify:

- account identity and environment;
- balance and deposit currency;
- account model, permissions and provider metadata;
- supported symbols and symbol metadata;
- open positions;
- pending orders;
- position-level unrealized PnL;
- bounded recent deals;
- observation timestamps, completeness and freshness;
- Trading Core synchronization and reconciliation state.

Trace each fact through the existing boundary:

```text
cTrader Open API -> cTrader adapter -> Broker Service -> DTO -> Trading Core -> Angular
```

Do not derive equity, free margin or account-level margin. Preserve explicit
unavailability and the existing `EQUITY_PROVIDER_SOURCE_UNVALIDATED` behavior.

## 5. Trade Intent and Risk Validation

Use the existing Angular or API path to create a representative Trade Intent or
Trade Plan without submitting an order. Use a symbol proven tradable by the
confirmed account and respect its provider constraints.

Verify:

- account and symbol selection;
- side, quantity and price constraints;
- stop-loss and optional take-profit;
- idempotency key propagation;
- persistence and state transitions;
- diagnostics visible at the API and Angular layers.

Run T0 and T1 using the actual available account evidence. Verify that:

- independent invariants with complete evidence produce their normal result;
- equity-dependent invariants remain `INCOMPLETE` or otherwise fail-closed;
- aggregate approval is not executable when mandatory evidence is absent;
- T1 repeats the required evidence checks;
- no experimental Story 0197 value enters risk evidence;
- no approval is fabricated to make the scenario pass.

## 6. Isolated Simulated Execution

Use existing provider-neutral execution ports, test profiles and simulated
adapter/test doubles. Do not connect this phase to the confirmed real account.

The scenario must pass through real validation and orchestration services and
cover:

- valid order request construction;
- symbol and volume mapping;
- stop-loss/take-profit mapping;
- accepted response;
- rejected response;
- timeout;
- unknown outcome requiring reconciliation;
- idempotency duplicate prevention;
- controlled retry behavior;
- final reconciliation and Trade Intent state.

The simulation must supply valid test facts and actual T0/T1 approvals produced
by the services. It must never inject an approval directly into persistence.

## 7. Real Order Preparation Without Submission

For the confirmed real account, verify read-only:

- order type support;
- symbol tradability;
- volume constraints;
- protection mapping;
- trading permissions;
- execution configuration;
- correlation and reconciliation readiness.

Do not call `ProtoOANewOrderReq` or any equivalent write operation. The real
order row in the evidence matrix is always `NOT_TESTED` with an explicit safety
reason.

## 8. Angular Validation

Use existing components and services only. Verify, when the authenticated
runtime is available:

- FTMO account visibility and connection status;
- financial facts and explicit unavailable fields;
- symbols and market state;
- Trade Intent/Trade Plan creation;
- T0/T1 diagnostics;
- understandable execution blocking;
- simulated execution history and reconciliation states.

Do not redesign the UI. A functional defect may receive a local correction only
after it is reproduced, traced to an existing contract, covered by a focused
test, and shown not to weaken safety or financial admissibility.

## 9. Evidence Artifacts

Create Story-local artifacts only:

- `e2e-validation-matrix.md`: every component/check, result class, proof source,
  real-vs-simulated classification and blocker;
- `implementation-report.md`: account identity outcome without secrets, real
  connectivity, data path, simulated execution, Angular validation, defects,
  blockers, readiness verdict and one next action.

The report must distinguish:

```text
REAL_PROVIDER_VALIDATION
SIMULATED_VALIDATION
AUTOMATED_TEST
BLOCKED
NOT_TESTED
```

No fixture, mock or unit test may be presented as real provider validation.

## 10. Allowed Corrections

Only local corrections directly discovered by the validation may be implemented.
Any correction touching FTMO policy, risk admissibility, T0/T1 invariants,
execution authorization, equity provenance, provider contracts or architecture
requires a new decision and stops implementation.

No correction may enable execution, bypass safety controls, weaken identity
matching, substitute an account, or turn an unavailable fact into a value.

### Approved Market-Closure Evidence Derivation

When cTrader does not provide a usable symbol schedule, the market-data adapter
may derive a conservative weekend closure only when all of these facts hold:

- the provider symbol is an FX pair composed of two known fiat currency codes;
- the current UTC day is Saturday or Sunday;
- the last provider observation occurred before the current UTC date;
- no usable cTrader schedule or holiday evidence is available.

The derived state is `CLOSED` with `MarketClosureReason.WEEKEND`. This rule does
not apply to crypto, metals, indices, equities or other non-FX symbols. A provider
observation received locally on the current day must not be treated as evidence
from an earlier provider timestamp. When the evidence is insufficient, the state
remains `UNKNOWN` and the market remains non-tradable.

## 11. Validation Commands

Run the exact applicable checks and report each result:

```text
git diff --check
mvn test                         # broker-service, when changed/validated
mvn test                         # trading-core, when changed/validated
npm run test:ci                  # trading-os-web, when frontend is validated
npm run build                    # trading-os-web production build
```

The Maven commands run from their respective service directories because the
repository has independent Maven builds. Add focused integration commands only
when the repository exposes them and the required runtime is available.

## Stop Conditions

Stop real validation immediately when:

- the target FTMO account cannot be identified with certainty;
- provider response identity or environment mismatches;
- token is expired or connection permissions are insufficient;
- safety cooldown, lease or budget blocks the request;
- a requested action would mutate provider state;
- a result would require invented or derived financial facts;
- T0/T1 would need to be bypassed;
- an issue requires a policy, architecture or contract decision.

## Expected Verdict

`READY_FOR_CONTROLLED_EXECUTION` is allowed only when all mandatory risk,
identity, authorization and execution prerequisites are actually satisfied. A
successful read-only connection alone yields at most `PARTIALLY_READY`.
Unavailable equity or another mandatory FTMO fact must keep the final verdict
`NOT_READY`.
