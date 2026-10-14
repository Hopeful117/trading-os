# E2E PAPER Challenge Validation Continuation Report After Story 0095

Date: 2026-10-13
Commit tested: `a134f891b77e0768d1fca1c561581eed31ed9677`
(`main`, merge of Story 0095)

## Scope

This validation resumed the real PAPER Challenge workflow from current `main`
after Stories 0094 and 0095 were merged. Previous continuation reports were
not modified. The run stopped at the first new meaningful product blocker and
did not create or implement Story 0096.

## Baseline

- Git branch: `main`.
- HEAD: `a134f891b77e0768d1fca1c561581eed31ed9677`.
- Story 0094 Gateway routing is present through merge commit `c9ca2c4`.
- Story 0095 PostgreSQL pagination is present through merge commit `d6e2938`.
- GitHub fetch/pull was attempted; the local `main` already contained the
  merged commit. The configured SSH key agent refused the remote operation, so
  no remote update was required or applied.
- Isolated Compose project: `trading-e2e-0095`.
- PostgreSQL runtime: `16.15`.
- Flyway dependency: `12.4.0`.
- Java runtime: `21.0.12`.
- Docker Engine: `29.1.3`.
- Docker Compose: `2.40.3`.
- Trading Core schema: V31, `enforce immutable risk evaluation t1`.
- Market Data schema: V3, `market product identity`.
- Broker Service schema: V2, `broker risk snapshots`.
- Market Intelligence schema: V13, `account scoped opportunity provenance`.
- News Service schema: V3, `economic event source`.
- Started services: Eureka, Trading Core, Gateway, Broker Service, Market Data,
  Market Intelligence, News Service, Angular Web, and all service PostgreSQL
  databases.
- Relevant runtime configuration:
  - `SPRING_PROFILES_ACTIVE=prod`;
  - `TRADING_CHALLENGE_MONITORING_ENABLED=true`;
  - `TRADING_CHALLENGE_MONITORING_INTERVAL_MS=5000`;
  - Trading Core batch size default `50`;
  - public Gateway exposed on `http://localhost:28880`.
- Trading Core and Gateway were rebuilt from the current source before startup.
- Fresh isolated PostgreSQL volumes were used for all services.

## Regression Gates

### Story 0094 Gateway Routing

Authenticated:

```text
GET /api/v1/challenges
```

returned `200 OK` through Gateway and exposed the Kraken Prop Advanced 50k
definition:

- definition ID `00890000-0000-4000-8000-000000000050`;
- version `1.0.0`;
- starting capital `50000` USD;
- target ratio `0.09`;
- settlement model `CASH`;
- required product type `LINEAR_PERPETUAL`.

**Result: PASS.**

### Story 0095 PostgreSQL Monitoring Pagination

The normal public workflow completed through Gateway:

- user registration: `200`;
- login: `200`;
- PAPER Kraken account creation: `201`;
- account state: `CASH`, `50000.00`, `realizedBalance=50000.00`;
- Challenge start: `200`, status `ACTIVE`;
- scheduler discovery: `discovered=1`;
- scheduler reevaluation: `evaluated=1`;
- cycle result: `skipped=0`, `failed=0`;
- no `SQLState 42P18` or `could not determine data type of parameter` after
  startup.

**Result: PASS.**

## Workflow Matrix

| Stage | Result | Evidence |
|---|---|---|
| Environment bootstrap | PASS | Fresh isolated Compose project and PostgreSQL volumes; all services started |
| Authentication | PASS | Registration `200`, login `200` through Gateway |
| PAPER account | PASS | `POST /api/v1/broker-accounts` returned `201`; CASH account with `50000.00` |
| Challenge discovery | PASS | Authenticated `GET /api/v1/challenges` returned `200` |
| Challenge start | PASS | `POST /api/v1/accounts/{accountId}/challenge` returned `200`, `ACTIVE` |
| First monitoring cycle | PASS | Scheduler discovered and reevaluated the Challenge without `42P18` |
| Initial monitored progress | PASS with observation | Gateway returned coherent financial values; existing public mapping leaves Risk detail fields null |
| Derivative market discovery | FAIL | Gateway returned 1460 Kraken markets, all `SPOT`; zero `LINEAR_PERPETUAL` |
| TradePlan creation | NOT REACHED | Stop-at-first-blocker rule |
| Human acceptance | NOT REACHED | Stop-at-first-blocker rule |
| PRE_TRADE Risk | NOT REACHED | Stop-at-first-blocker rule |
| Execution validation | NOT REACHED | Stop-at-first-blocker rule |
| T1 revalidation | NOT REACHED | Stop-at-first-blocker rule |
| PAPER ENTRY | NOT REACHED | Stop-at-first-blocker rule |
| Position projection | NOT REACHED | Stop-at-first-blocker rule |
| MTM | NOT REACHED | Stop-at-first-blocker rule |
| Open-position Challenge monitoring | NOT REACHED | Stop-at-first-blocker rule |
| Position close | NOT REACHED | Stop-at-first-blocker rule |
| Realized settlement | NOT REACHED | Stop-at-first-blocker rule |
| Post-close Challenge monitoring | NOT REACHED | Stop-at-first-blocker rule |
| Angular verification | NOT REACHED | Stop-at-first-blocker rule |

## Snapshot A - Initial Monitored Challenge

Identifiers:

- user ID: `2a21d0f1-0b73-4772-9bd1-cb18a9f3020a`;
- BrokerAccount ID: `a4b3bb97-f023-45a0-af8e-180c8ea92115`;
- Account ID: `3e1cf2bf-7af9-47a0-9ad1-804898360b00`;
- Challenge ID: `3e47f11a-b20a-4f0c-a126-b5916a28e778`.

Gateway Challenge progress:

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

Authoritative Account state through Gateway/PostgreSQL:

| Field | Value |
|---|---:|
| Realized balance | `50000.00` |
| Equity | `50000.00` |
| Peak equity | `50000.00` |
| Valuation status | `FRESH` |
| Valued at | populated, latest observed `2026-10-13 21:58:49+00` |
| Risk-day baseline | `50000.000000000000 USD` |
| Risk evaluations | `19` persisted by the monitoring cycles |

The financial values are coherent for an untouched `50000` USD account. The
public `ChallengeProgressService` currently constructs the four monitoring
detail fields as `null`; this is an existing response-mapping limitation and
not a regression in Story 0095. The monitoring cycles persisted the Risk-day
baseline and account monitoring evaluations, and refreshed authoritative
valuation state.

## Market Discovery Evidence

Authenticated:

```text
GET /api/v1/markets
```

returned `200 OK` with:

- total markets: `1460`;
- provider: `KRAKEN` for all returned markets;
- product type: `SPOT` for all returned markets;
- tradable markets: `1365`;
- `LINEAR_PERPETUAL` markets: `0`.

The Market Data PostgreSQL database independently confirmed:

```text
product_type | count
-------------+------
SPOT         | 1460
```

No compatible derivative market could be selected. Bid, ask, midpoint, and
derivative freshness validation were therefore not reached.

## First Blocker

### Classification

`BLOCKER` for the requested PAPER derivative workflow.

### Symptom

The Challenge definition requires:

```text
InstrumentProductType.LINEAR_PERPETUAL
```

The supported Gateway market catalogue exposes only `SPOT` instruments. A
currently tradable compatible derivative cannot be obtained through the normal
application boundary, so no valid CASH derivative TradePlan can be created.

### Root Cause Evidence

The current Market Data Kraken adapter obtains markets through the Kraken
`/0/public/AssetPairs` endpoint:

- `KrakenMarketData.getMarkets()` calls `client.getAssetPairs()`;
- `KrakenMarketMapper.toDomain(...)` maps every asset pair to
  `InstrumentProductType.SPOT`;
- no Kraken derivative market source or mapper exists in the current runtime;
- `InstrumentProductType.LINEAR_PERPETUAL` exists in the domain enum but is not
  produced by the provider adapter.

The runtime result is therefore a real provider-to-domain product identity gap,
not an absence of spot market data or a temporary scheduler failure.

### Stop Decision

The run stopped before TradePlan creation as required. No state was inserted or
mutated directly in PostgreSQL, and no application code was changed during the
validation.

## Financial Reconciliation

Only Snapshot A was reached. Snapshots B through F are not applicable because
the required derivative market was unavailable.

| Snapshot | Realized balance | Unrealized P&L | Equity | Challenge current value | Current profit | Target progress |
|---|---:|---:|---:|---:|---:|---:|
| A - monitored before trade | `50000.00` | `0` / no open trade | `50000.00` | `50000.00` | `0E-12` | `0E-12` |
| B - after entry | NOT REACHED | NOT REACHED | NOT REACHED | NOT REACHED | NOT REACHED | NOT REACHED |
| C - later MTM | NOT REACHED | NOT REACHED | NOT REACHED | NOT REACHED | NOT REACHED | NOT REACHED |
| D - open-position monitoring | NOT REACHED | NOT REACHED | NOT REACHED | NOT REACHED | NOT REACHED | NOT REACHED |
| E - after close | NOT REACHED | NOT REACHED | NOT REACHED | NOT REACHED | NOT REACHED | NOT REACHED |
| F - post-close monitoring | NOT REACHED | NOT REACHED | NOT REACHED | NOT REACHED | NOT REACHED | NOT REACHED |

## Secondary Findings

- **Known limitation:** the public Challenge progress DTO currently returns
  monitoring Risk detail fields as `null`, although the monitoring path
  persists a Risk-day baseline and account monitoring evaluations.
- **Known fidelity limitation:** provider fee, funding, leverage, margin, and
  liquidation fidelity remain untested because the derivative market gate was
  not reached.
- **Environment observation:** Compose emitted warnings for unset Kraken API
  credentials. This did not block public market catalogue synchronization,
  which used the public Kraken asset-pair endpoint.
- Angular validation is `NOT REACHED`, not a UI product failure.

## Recommendation

The smallest corrective Story should make the Market Data provider boundary
produce and expose provider-neutral Kraken `LINEAR_PERPETUAL` markets with
correct product identity, tradability, constraints, and fresh ticker data
through the existing Gateway market catalogue and snapshot workflow. It should
not alter Challenge, Risk, or execution semantics.

No successor Story artifact was created.

## Verdict

```text
E2E PAPER CHALLENGE: FAIL

FIRST BLOCKER: Gateway Market Data exposes only KRAKEN SPOT markets; no
LINEAR_PERPETUAL instrument is produced by the current Kraken adapter, so the
required CASH derivative TradePlan workflow cannot begin.

RECOMMENDED NEXT STORY: Expose provider-neutral Kraken LINEAR_PERPETUAL market
identity and fresh derivative market data through the supported application
boundary.
```
