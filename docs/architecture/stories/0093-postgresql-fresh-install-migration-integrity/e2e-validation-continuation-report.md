# E2E PAPER Challenge Validation Continuation Report

Date: 2026-10-13
Commit tested: `f0694da0` (`main`, Story 0094 merge)
Story 0094 implementation: `c9ca2c4`

## Scope

This is a continuation of the historical validation in
`e2e-validation-report.md`. The historical report was not modified. The run
resumed from the merged Gateway routing fix and stopped at the first new
meaningful product blocker, without implementing a fix or creating Story 0095.

## Environment

- Isolated Compose project: `trading-e2e-resume`.
- Fresh PostgreSQL volumes were created for Trading Core, Broker Service,
  Market Data, Market Intelligence, and News Service.
- PostgreSQL runtime: `16.15`.
- Flyway dependency/runtime source version: `12.4.0`.
- Fresh Trading Core schema reached migration `V31`.
- Java runtime: `21.0.12`.
- Docker Engine: `29.1.3`.
- Docker Compose: `2.40.3`.
- Gateway image: `sha256:8d7214c8e11eb630ce0658e95ec1df4522336daaa668757f5c61c6b310430619`.
- Trading Core image: `sha256:5d8406d751fd91943b190bb436c7c512d8e56ab74c92ed511f389468641bb334`.
- Market Data image: `sha256:100b607e9c8e8a22a792f5c1354c4bc529e022e008583553f6f81fbeab3deba2`.
- Broker Service image: `sha256:21da684d344d04b28c51948ab72d08934365fd977ba43a97243fd329a56e9b69`.
- Market Intelligence image: `sha256:9cae9e7195afad9582fcb0c04ed110f86cc45778176102453d6fd2e487245207`.
- News Service image: `sha256:24125c8cd38411d3328a03c6b3e57cee4be5d3d37df077d90eda257df79635a9`.
- Angular Web image: `sha256:f3b1436de8784a771fec20c34083dfccd6109206554bf2ee3126bd498c62ac`.

## Story 0094 Verification

The normal registration/login workflow completed through Gateway. The
authenticated request:

```text
GET http://localhost:27880/api/v1/challenges
```

returned `200 OK` and exposed the persisted Kraken Prop Advanced 50k
definition:

- definition ID: `00890000-0000-4000-8000-000000000050`;
- version: `1.0.0`;
- starting capital: `50000` USD;
- target ratio: `0.09`;
- progression source: `EQUITY`;
- settlement model: `CASH`;
- instrument product type: `LINEAR_PERPETUAL`.

## Workflow Matrix

| Stage | Result | Evidence |
|---|---|---|
| Environment bootstrap | PASS | Isolated stack started; fresh databases; V31 reached |
| Authentication | PASS | Registration `200`, login issued JWT through Gateway |
| Risk Profile discovery | PASS | `GET /api/v1/risk-profiles/eligible` returned `200` |
| PAPER account creation | PASS | `POST /api/v1/broker-accounts` returned `201`; public account state verified |
| Challenge discovery through Gateway | PASS | `GET /api/v1/challenges` returned `200` |
| Challenge start through Gateway | PASS | `POST /api/v1/accounts/{accountId}/challenge` returned `200`, `ACTIVE` |
| Initial Challenge progress | PASS | `GET /api/v1/accounts/{accountId}/challenge` returned `200`; core values coherent |
| Active Challenge monitoring | FAIL | Scheduler repeatedly fails before discovering the active Challenge |
| Derivative market discovery | NOT REACHED | Stop-at-first-blocker rule |
| TradePlan creation | NOT REACHED | Stop-at-first-blocker rule |
| Human acceptance | NOT REACHED | Stop-at-first-blocker rule |
| PRE_TRADE Risk | NOT REACHED | Stop-at-first-blocker rule |
| Execution validation | NOT REACHED | Stop-at-first-blocker rule |
| T1 Risk revalidation | NOT REACHED | Stop-at-first-blocker rule |
| PAPER ENTRY | NOT REACHED | Stop-at-first-blocker rule |
| Position projection | NOT REACHED | Stop-at-first-blocker rule |
| MTM | NOT REACHED | Stop-at-first-blocker rule |
| Challenge monitoring while open | NOT REACHED | Stop-at-first-blocker rule |
| Position close | NOT REACHED | Stop-at-first-blocker rule |
| Realized settlement | NOT REACHED | Stop-at-first-blocker rule |
| Post-close monitoring | NOT REACHED | Stop-at-first-blocker rule |
| Angular final state | NOT REACHED | Stop-at-first-blocker rule |

## Identifiers And Account State

- Authenticated user ID: `72ba12f4-7a01-4440-aa40-b15a05877b34`.
- BrokerAccount ID: `32629def-9ef6-4ca7-909a-af9381a96ad7`.
- Account ID: `929f5230-dcc0-4621-a756-470494d4f1bf`.
- Challenge ID: `10bf4ac4-608c-40d2-abc6-47a2ed55e4bc`.
- Broker provider: `KRAKEN`.
- Execution mode: `PAPER`.
- Initial capital: `50000.00` USD.
- Settlement model: `CASH`.
- Risk profile: `0a10c7e2-9d1e-4f5a-b6c8-123456789089`, version `1.0.0`.
- PostgreSQL verification confirmed the account owner, broker-account link,
  `CASH` settlement, and `ACTIVE` Challenge state.

## Financial Snapshot A

| Field | Value |
|---|---:|
| Starting capital | `50000.000000000000` |
| Current value | `50000.00` |
| Target value | `54500.000000000000000000000000` |
| Current profit | `0E-12` |
| Target progress | `0E-12` |
| Account realized balance | `50000.00` |
| Account equity | `50000.00` |
| Account peak equity | `50000.00` |
| Daily reference balance | `null` |
| Daily loss remaining | `null` |
| Total drawdown remaining | `null` |
| Evaluated at | `null` |

The capital, target, profit, and progress values are financially coherent. The
monitoring-derived fields remained null because the scheduler failed before
reevaluation.

## First Blocker

### Classification

`BLOCKER` for the requested end-to-end workflow; responsible component:
Trading Core Challenge monitoring scheduler/persistence query.

### Symptom

Once the Challenge was `ACTIVE`, the monitoring scheduler ran every five
seconds and failed before discovering it. The log repeatedly reported:

```text
ERROR: could not determine data type of parameter $2
SQLState: 42P18
```

The public progress endpoint continued to return `200`, but
`dailyReferenceBalance`, `dailyLossRemaining`, `totalDrawdownRemaining`, and
`evaluatedAt` remained null. No active Challenge reevaluation occurred.

### Reproduction

1. Start from fresh PostgreSQL and current `main` at `f0694da0`.
2. Create a compatible `PAPER`/`CASH` account with `50000` USD.
3. Start the persisted Kraken Prop Advanced 50k Challenge through Gateway.
4. Wait for the configured monitoring interval.
5. Observe the failure on every scheduler cycle.

### Root Cause

`ActiveChallengeMonitoringService.monitor()` calls
`ChallengeInstanceRepository.findNextByStatus()` with both cursor values null
on the first page (`ActiveChallengeMonitoringService.java:49-55`). The JPQL
query contains nullable cursor predicates:

```text
(:afterStartedAt is null or c.startedAt > :afterStartedAt
 or (c.startedAt = :afterStartedAt and c.id > :afterId))
```

Hibernate/PostgreSQL renders the first nullable parameter as an untyped `?` in
`? is null`. PostgreSQL cannot infer its type and aborts the query with
`42P18`, preventing the scheduler from reaching the active Challenge.

### Evidence

- Trading Core log: `ActiveChallengeMonitoringService`, scheduler thread,
  repeated SQLState `42P18`.
- Generated SQL includes `where ci1_0.status=? and (? is null or ...)`.
- Database row remained `ACTIVE` with no monitoring-derived progress fields
  updated.
- Fresh database contained the expected `challenge_instance` and `accounts`
  rows; no direct state mutation was performed.

## Financial Reconciliation

Only Snapshot A was reached. Snapshots B through F are `NOT REACHED` because
the monitoring scheduler blocker occurred before market, TradePlan, Risk, and
execution validation could safely proceed.

## Secondary Observations

- Story 0094 Gateway routing is confirmed fixed in the real rebuilt
  environment; no direct Trading Core call was used for the Challenge gate.
- The initial progress endpoint exposes a coherent starting state but leaves
  monitoring-derived fields null until scheduler reevaluation.
- The known daily baseline semantics limitation at exactly `00:30 UTC` was not
  evaluated because the scheduler failed earlier.
- Angular routes were not evaluated because the stop-at-first-blocker rule
  applied. This is `NOT REACHED`, not a UI failure.
- No product source code was changed during this validation. No Story 0095 was
  created or implemented.

## Verdict

```text
E2E PAPER CHALLENGE: FAIL
FIRST BLOCKER: Active Challenge monitoring fails with PostgreSQL 42P18 because the nullable cursor parameter in findNextByStatus is untyped.
RECOMMENDED NEXT STORY: Make Active Challenge monitoring pagination PostgreSQL-safe without changing Challenge or Risk business rules.
```
