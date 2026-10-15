# Story 0098 Runtime Validation and PAPER Challenge Continuation Report

Date: 2026-10-14
Commit tested: `3feea00401fc76ed0777d0df6c068406ffbbad1b`
(`main`, merge of Story 0098)

## Scope

This report validates Story 0098 against a fresh runtime and resumes the real
PAPER Challenge workflow until the first meaningful product blocker. Previous
runtime reports were preserved. No production code, database row, Risk rule, or
Challenge rule was modified during this mission.

## Repository State

- Remote fetch through the configured SSH URL initially failed because the local
  SSH agent refused the `id_ed25519_sk` signature.
- The same remote repository was fetched through its HTTPS URL without changing
  Git configuration.
- `origin/main` was verified at `3feea00401fc76ed0777d0df6c068406ffbbad1b`.
- No commit newer than `3feea004` was present.
- Story 0098 is present through merge commit `3feea004` and implementation commit
  `fcd7cc56`.
- Branch tested: `main`.
- Working tree: clean before runtime validation.

## Runtime Environment

- Isolated Compose project: `trading-e2e-0098`.
- Fresh PostgreSQL volumes were created for this project.
- PostgreSQL: `16.15`.
- Java: `21.0.12`.
- Maven: `3.8.7`.
- Node: `v26.10.0`.
- npm: `11.19.1`.
- Docker Engine: `29.1.3`.
- Docker Compose: `2.40.3`.
- Spring Boot runtime: `4.1.0` for Market Intelligence; `0.0.1-SNAPSHOT`
  services elsewhere.
- Flyway schemas after startup:
  - Trading Core: V31;
  - Market Data: V4;
  - Broker Service: V2;
  - Market Intelligence: V13.
- Rebuilt all Compose images with `docker compose -p trading-e2e-0098 build
  --no-cache`.
- Started Eureka, Gateway, Trading Core, Broker Service, Market Data, Market
  Intelligence, News Service, Angular Web, and their PostgreSQL databases.
- Angular production image build succeeded with existing bundle/style budget
  warnings and npm audit warnings. Those warnings are not related to this run.

## Gate A - Story 0098 Runtime

### Market Discovery

Authenticated `GET /api/v1/markets` through Gateway returned `200 OK`:

- total Kraken markets: `1664`;
- SPOT: `1460`;
- LINEAR_PERPETUAL: `204`;
- tradable LINEAR_PERPETUAL: `204`.

The selected market was discovered dynamically as:

```text
marketId:       8ec9fa8a-e279-4ea5-a1df-eee3f8de99ca
provider:       KRAKEN
symbol:         BTC/USD
productType:    LINEAR_PERPETUAL
tradingStatus:  OPEN
tradable:       true
providerSymbol: PF_XBTUSD (persistence/adapter boundary only)
```

The explicit public synchronization endpoint returned `403` for the user
token, but the normal runtime catalogue was populated from the real Kraken
source at startup and exposed the current product families through the public
catalogue. No fixture was used as runtime proof.

### Futures Current Price

The protected Market Data snapshot path returned `200 OK` for the selected
market:

```text
bid:          81590
ask:          81591
lastPrice:    81592
occurredAt:   2026-10-14T16:04:38.848661622Z
capturedAt:   2026-10-14T16:04:38.949342771Z
status:       FRESH
tradable:     true
```

The response was obtained through the Market Data service path and used the
Futures provider identity `PF_XBTUSD`; no Spot ticker was substituted.

### Futures OHLC Semantics

Authenticated `GET /api/v1/markets/{marketId}/ohlc?interval=ONE_HOUR&limit=24`
returned `200 OK` with 24 live Futures candles.

- all 24 normalized candle `vwap` values were `null`;
- completed candles had `closed=true`;
- representative candles had `synthetic=false`;
- the current open candle was not included in completed activity;
- no mapper substitution of `close` into `vwap` was observed.

Representative completed evidence:

```text
openTime:  2026-10-14T15:00:00Z
closeTime: 2026-10-14T16:00:00Z
close:     81560
volume:    235.93000000000
vwap:      null
closed:    true
synthetic: false
```

### Futures Market Facts

The protected internal Market Facts path returned `200 OK`:

```text
activity.status:                 AVAILABLE
activity.calculationBasis:      CLOSE_PRICE_FALLBACK
activity.calculationVersion:    market-facts-v2
activity.quoteNotional:         66492220.972000000000
activity.expectedCandleCount:   2
activity.observedCandleCount:   2
activity.eligibleCandleCount:   2
activity.incompleteCandleCount: 0
readiness.status:                AVAILABLE
readiness.observedCompleted:    23
readiness.syntheticCandles:     0
readiness.missingIntervals:     0
```

The repeated Market Facts request produced the same basis, version, quote
notional, eligibility count, and readiness result. The runtime therefore
confirmed deterministic evaluation for the observed evidence. The activity
value is close-basis activity, not provider VWAP.

### Spot Regression

The dynamically selected Spot market was:

```text
marketId:    c9f31b19-e2eb-427b-b490-6d190e6c5211
symbol:      ETH/USD
productType: SPOT
```

Live Spot OHLC returned 24 non-null VWAP values. Market Facts returned:

```text
activity.status:              AVAILABLE
activity.calculationBasis:   VWAP
activity.calculationVersion: market-facts-v2
activity.quoteNotional:      16469733.0627079673
eligibleCandleCount:          2
readiness.status:             AVAILABLE
syntheticCandleCount:         0
missingIntervalCount:        0
```

Spot retained its provider-VWAP behavior.

### Market Intelligence Eligibility

The normal authenticated Active Scan scope path was exercised with the
selected Futures market and the fresh CASH PAPER account. It returned `200
OK`:

```text
eligible:                       true
status:                         ELIGIBLE
marketFactsStatus:              AVAILABLE
marketFactsCalculationVersion: market-facts-v2
activityCalculationBasis:       CLOSE_PRICE_FALLBACK
readinessStatus:                AVAILABLE
reasons:                        []
```

No Kraken-specific payload or fallback logic was required in Market
Intelligence.

## Gate A Verdict

```text
STORY 0098 RUNTIME: PASS
```

## Gate B - PAPER Challenge E2E

### Workflow Matrix

| Stage | Result | Evidence |
|---|---|---|
| Remote main verification | PASS | `origin/main` at `3feea004`; Story 0098 present |
| Clean runtime | PASS | Fresh `trading-e2e-0098` Compose project and PostgreSQL volumes |
| Authentication | PASS | Registration `200`; login token used through Gateway |
| PAPER account | PASS | Fresh KRAKEN account, `PAPER`, `CASH`, USD 50000 |
| Challenge catalog | PASS | Kraken Prop Advanced 50k definition returned through Gateway |
| Challenge start | PASS | Challenge created with status `ACTIVE` |
| Initial Challenge progress | PASS with observation | Current value 50000; public Risk detail fields remain null |
| Active Challenge monitoring | PASS | Repeated cycles: discovered=1, evaluated=1, skipped=0, failed=0 |
| LINEAR_PERPETUAL selection | PASS | Reused validated BTC/USD Futures market |
| Manual TradePlan creation | PASS | Manual plan created, ID `5902630d-f5dc-3a0c-9bd4-fa00999bc52c` |
| Human acceptance | PASS | Plan version 2 transitioned to `ACCEPTED` |
| PRE_TRADE Risk | PASS | Evaluation `b310322d-019a-4ccf-bf7c-952caead48ff`, `APPROVED` |
| Execution-time/T1 Risk | NOT REACHED | Execution intent validation failed before intent creation |
| PAPER entry | NOT REACHED | Stop-at-first-blocker rule |
| OPEN Trade persistence | NOT REACHED | Stop-at-first-blocker rule |
| Position projection | NOT REACHED | Stop-at-first-blocker rule |
| MTM valuation | NOT REACHED | Stop-at-first-blocker rule |
| Open-position Challenge monitoring | NOT REACHED | Stop-at-first-blocker rule |
| Position close | NOT REACHED | Stop-at-first-blocker rule |
| Realized P&L reconciliation | NOT REACHED | Stop-at-first-blocker rule |
| Post-close Challenge monitoring | NOT REACHED | Stop-at-first-blocker rule |
| Angular verification | NOT EXECUTED | Browser validation was not needed after the backend blocker |

### Challenge Definition

Authenticated `GET /api/v1/challenges` returned the required benchmark:

- definition ID: `00890000-0000-4000-8000-000000000050`;
- version: `1.0.0`;
- provider: `KRAKEN_PROP`;
- program: `EVALUATION`;
- tier: `ADVANCED`;
- starting capital: `50000 USD`;
- profit target: `9%`;
- progression source: `EQUITY`;
- settlement: `CASH`;
- required product: `LINEAR_PERPETUAL`.

### PAPER Context

- user ID: `fec1ce5c-8959-4241-8152-f2a41e4db963`;
- BrokerAccount ID: `4ea52b11-ab18-426c-964f-deb6f6bb8c3c`;
- Account ID: `6968cc96-6dc4-4514-bfa0-b0650a5254e8`;
- Challenge ID: `c7355bf0-106e-4d8c-8b6d-29f6103f24b8`;
- risk profile ID: `0a10c7e2-9d1e-4f5a-b6c8-123456789089`, version `1.0.0`.

### Snapshot A - Initial Challenge

Challenge progress through Gateway:

| Field | Value |
|---|---:|
| Status | `ACTIVE` |
| Starting capital | `50000.000000000000` |
| Current value | `50000.00` |
| Target value | `54500.000000000000000000000000` |
| Current profit | `0E-12` |
| Target progress | `0E-12` |
| Daily reference balance | `null` |
| Daily loss remaining | `null` |
| Total drawdown remaining | `null` |
| Evaluated at | `null` |

Authoritative account state:

| Field | Value |
|---|---:|
| Settlement model | `CASH` |
| Realized balance | `50000.00` |
| Equity | `50000.00` |
| Peak equity | `50000.00` |
| Valuation status | `FRESH` |

The monitoring scheduler successfully discovered and evaluated the Challenge,
but the public progress projection still returned the monitoring detail fields
as `null`, consistent with the existing response mapping limitation.

### TradePlan and PRE_TRADE Risk

The deliberately small manual plan used:

```text
direction:      LONG
entryType:      MARKET
referencePrice: 81592
quantity:       0.001
stopLoss:       80000
monetaryRisk:   10
productType:    LINEAR_PERPETUAL
```

Human acceptance was exercised through the public decision endpoint. PRE_TRADE
Risk returned:

```text
evaluationId: b310322d-019a-4ccf-bf7c-952caead48ff
status:       COMPLETED
decision:     APPROVED
approved:     true
warnings:     []
```

### Snapshot B-F

No entry was submitted because execution-intent validation failed. Therefore:

| Snapshot | Result |
|---|---|
| A - before trade | PASS: 50000 USD account, no position |
| B - immediately after entry | NOT REACHED |
| C - fresh mark-to-market | NOT REACHED |
| D - open-position monitoring | NOT REACHED |
| E - after close | NOT REACHED |
| F - post-close Challenge reevaluation | NOT REACHED |

## First Blocker

### Classification

`BLOCKER` for the requested PAPER derivative execution workflow.

### Symptom

The normal public request:

```text
POST /api/v1/executions/validate
```

returned `403` through Gateway with an empty response body. Trading Core logs
show the underlying failure as:

```text
InvalidDataAccessApiUsageException: No active transaction
TransactionRequiredException: No active transaction
```

No ExecutionIntent was created, so no order or Trade was submitted.

### Root Cause Evidence

The stack trace identifies:

```text
ChallengeInstrumentCompatibilityService.validate:22
ChallengeInstanceRepository.findByAccountIdAndStatus
ValidateAndCreateService.validateAndCreate:175
```

`ValidateAndCreateService.validateAndCreate()` invokes the active Challenge
instrument compatibility check before creating the ExecutionIntent. The
compatibility service calls a repository query requiring an active JPA
transaction, but this validation application path has no transaction boundary.
The failure occurs before T1 Risk revalidation and before broker submission.

This is an execution-boundary transaction defect, not a Story 0098 Market Data
or product-identity failure. The Challenge compatibility check itself is
correctly present and was not bypassed.

### Stop Decision

The run stopped at execution-intent validation as required. No direct database
mutation, execution shortcut, Risk bypass, or production-code change was made.

## Persistence Trace Reached

The following public workflow chain was created and verified before the stop:

```text
User
→ BrokerAccount
→ Account
→ ChallengeInstance
→ Manual TradePlan
→ ACCEPTED TradePlan version
→ APPROVED PRE_TRADE RiskEvaluation
```

The chain did not reach `ExecutionIntent`, `ExecutionAttempt`, or `Trade`.

## Known Limitations and Observations

- **Known limitation:** public Challenge progress returned `null` for daily
  reference, daily loss remaining, total drawdown remaining, and evaluated-at,
  although monitoring cycles completed successfully.
- **Known limitation:** exact Risk Day reset-boundary reconstruction was not
  tested, as instructed.
- **Observation:** the first account creation omitted `settlementModel` and
  correctly defaulted to `INVENTORY`; a second account was created with the
  explicit `CASH` field and was used for Gate B.
- **Observation:** the runtime emitted existing Angular bundle/style budget and
  npm audit warnings during the clean image build.
- **Angular validation:** not executed after the backend blocker; this is not a
  UI failure.

## Recommendation

The next narrow Story should repair the transaction boundary and HTTP error
mapping around execution-time Challenge instrument compatibility validation.
It should add a runtime or integration regression covering:

```text
ACTIVE CASH Challenge
→ accepted LINEAR_PERPETUAL TradePlan
→ approved PRE_TRADE Risk
→ execution validation
```

The fix must preserve the Challenge compatibility gate, T1 Risk revalidation,
Gateway authentication, and the existing fail-closed behavior. No Story 0099
was created.

## Final Verdict

```text
STORY 0098 RUNTIME: PASS
E2E PAPER CHALLENGE: FAIL
FIRST BLOCKER: No active transaction in ChallengeInstrumentCompatibilityService during execution validation
RECOMMENDED NEXT STORY: Repair execution validation transaction boundary and error mapping for active Challenge compatibility checks
```
