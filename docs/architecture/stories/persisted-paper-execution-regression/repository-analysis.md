# Repository Analysis - Persisted PAPER Execution Regression

## Checkpoint

| Field | Value |
|---|---|
| Baseline | `main` after PAPER merge |
| Main | `03e3a6fbcea8d07402408bc1206dd09af72af5fd` |
| Origin main | `03e3a6fbcea8d07402408bc1206dd09af72af5fd` |
| Production module | `trading-core` |
| External network required | No |
| Existing persisted PAPER regression | Missing |

## Existing PAPER Coverage

`PaperExecutionVerticalRegressionTest` already verifies the in-memory
application pipeline for BUY and SELL scenarios. It proves routing,
deterministic prices, fill propagation, settlement, Trade creation, and
terminal execution behavior, but uses mocked repositories and adapters.

Existing Spring-backed tests use `@SpringBootTest`, `@DataJpaTest`, Flyway, and
the test H2 database. No second testing framework or external Testcontainers
setup is required for this Story.

## Production Path to Exercise

```text
persisted User
    -> persisted Account
    -> persisted PAPER BrokerAccount
    -> ExecuteTradeService
    -> T1 risk revalidation
    -> RoutingBrokerExecutionAdapter
    -> SimulatedExecutionAdapter
    -> BrokerOrder and Fill persistence
    -> PaperSettlementService
    -> Account and Trade persistence
    -> COMPLETED execution
```

The market-data client may be mocked at the external service boundary with
deterministic snapshots. The live `BrokerExecutionClient` must be mocked and
verified to have no interactions. Core repositories and persistence should be
real wherever the current wiring supports it.

## Known Boundaries

- PAPER is an execution mode, not a BrokerProvider.
- Market Data remains the source for simulated bid/ask prices.
- The legacy `Trade` entity is the current PAPER position projection.
- No new persistence model or financial semantics are required.
- Account uniqueness and broker status drift remain outside this Story.

## Readiness

`READY_FOR_IMPLEMENTATION`
