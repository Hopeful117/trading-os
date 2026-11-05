# Story 0198 - Implementation Report

## Existing Account

The normal authenticated Trading OS path resolved the existing account named
`FTMO Free cTrader`. It is a `LIVE` cTrader connection with status `CONNECTED`.
The durable local broker account and provider registration were matched through
the application path. The provider confirmed external account ID `49411967`.
No credentials or tokens were inspected or recorded.

## Real cTrader Connectivity

The read-only status action completed successfully against the existing account.
The provider returned a fresh snapshot with USD balance `50000.00`, no open
positions, and no orders. The provider did not return equity, used margin, free
margin, or deal history. The application therefore reported
`CONNECTED_BUT_FACTS_INCOMPLETE`, `INCOMPLETE`, and `FRESH`.

No derived equity or margin value was introduced. `executionAllowed` and
`riskEvaluationAllowed` remained false for the incomplete snapshot.

## End-to-End Integration

The real adapter-to-Broker-Service snapshot path is proven for identity, balance,
currency, positions and orders. Trading Core synchronization was attempted only
through the authenticated application route. It did not produce a bounded
response during this run, and no incomplete snapshot was treated as synchronized.
The missing provider facts remain an explicit blocker rather than being filled
from local or experimental data.

## Simulated Execution

The existing provider-neutral paper execution and validation boundaries passed
their targeted tests, including execution guards, paper lifecycle, finalization,
mode boundaries and synchronization persistence. No simulated scenario was
connected to the real FTMO account.

## Angular Validation

The authenticated Accounts view displayed the FTMO connection, local account
state, USD balance, and the read-only diagnostic status. Angular validation passed
with 56 test files and 562 tests. The production build completed successfully.
The build still emits existing optional-chain and bundle/style budget warnings;
these were not changed because they are unrelated to the validation Story.

## Discovered Defects

- The provider snapshot for the configured FTMO account is incomplete because
  equity, used margin, free margin and deals are unavailable.
- The authenticated synchronization request did not return a bounded result in
  this runtime session. This needs separate diagnosis before claiming full
  Trading Core propagation.

## Remaining Blockers

### Connectivity

- No connectivity failure was observed. The read-only snapshot call succeeded.

### Functional

- Trading Core synchronization evidence is incomplete because the application
  request did not return a bounded result during this run.

### Financial

- Provider-authoritative equity is unavailable.
- Provider-authoritative used and free margin are unavailable.
- Deal history is unavailable or absent.
- T0/T1 and executable risk admission must remain fail-closed.

### Security

- No security blocker was observed. Execution safety remained disabled and no
  credentials were exposed.

### Environment

- Real order submission was not tested by design and must remain untested for
  this Story.

## Readiness

`PARTIALLY_READY` for read-only connectivity and account observation, but
`NOT_READY` for controlled execution. The mandatory missing financial facts make
the account ineligible for a first controlled trade under the existing policy.

## Prioritized Next Action

Determine why the cTrader provider response omits equity and margin facts for
external account `49411967`, then rerun the same read-only validation and Trading
Core synchronization path. Do not derive or admit replacement financial values.

## Validation Commands

- `mvn test` in `broker-service`: 355 tests passed.
- Targeted Trading Core execution and synchronization tests: all selected tests passed.
- `npm run test:ci` in `trading-os-web`: 56 files, 562 tests passed.
- `npm run build` in `trading-os-web`: passed with existing warnings.
- `git diff --check`: passed before this report was added.
