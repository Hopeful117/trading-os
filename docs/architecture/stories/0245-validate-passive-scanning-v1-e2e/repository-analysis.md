# Story 0245 — Repository Analysis

**Date:** 2026-11-14
**Branch:** `story/0245-validate-passive-scanning-v1-e2e`

---

## 1. Baseline

| Item | Value |
|---|---|
| Branch | `story/0245-validate-passive-scanning-v1-e2e` |
| Base | `main` at `70ca1f53` |
| Latest Flyway migration | `V31__allow_passive_skipped_without_execution.sql` |

---

## 2. Existing Infrastructure Reused

### market-intelligence test infrastructure
- `@SpringBootTest` + `@ActiveProfiles("test")` — H2 in-memory database with full Flyway migration
- `@Transactional` — per-test rollback for isolation
- `PassiveScanPersistenceTest` — pattern for seeding `AnalysisExecution` rows via `JpaAnalysisExecutionRepository`
- `PassiveAwarenessProjectionService` — instantiated directly in `@BeforeEach` with injected repositories
- `MutableClock` inner class — controlled time for freshness and validity assertions

### Gateway test infrastructure
- `GatewayDownstreamRoutingIntegrationTest` — existing Spring Cloud Gateway test using `@MockBean` downstream services; extended with passive-awareness route coverage

---

## 3. Domain Invariant Discovered

`PassiveScanMarket` requires `analysisExecutionId != null` for all non-SKIPPED statuses (line 49 of `PassiveScanMarket.java`). The execution ID is pre-assigned at `REGISTERED` creation. Any test that saves a market in a dispatchable state must first persist a valid `AnalysisExecution` row to satisfy the FK constraint `fk_passive_scan_market_execution`.

---

## 4. Files Identified for Creation

| File | Purpose |
|---|---|
| `market-intelligence/src/test/java/.../PassiveScanV1ProjectionIntegrationTest.java` | 6 integration tests covering Scenarios A, B, C, D, G, H |
| `gateway/.../GatewayDownstreamRoutingIntegrationTest.java` (modified) | Added passive-awareness route assertions (Scenario F-01) |
