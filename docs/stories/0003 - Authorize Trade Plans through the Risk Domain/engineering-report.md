# Engineering Report

## Story
Story 0003 — Authorize Trade Plans through the Risk Domain

## Status
Complete — Engineering Report Stage

---

## Executive Summary

Story 0003 establishes the deterministic authorization boundary between Trade
Planning and Broker Execution and makes the authorization endpoint reachable
through the Gateway. The production risk machinery delivered by Story 0001/0002
was verified and preserved; the genuine delta was exposing the public
`POST /api/v1/trade-plans/**` endpoint through the Gateway and adding focused
route coverage. No execution, order, frontend, or AI capability was introduced,
and no Risk Domain or Trading Core production logic was modified.

---

## Architecture Delivered

The decision pipeline remains:

```text
Market Intelligence -> Trade Plan
    -> Trading Core RiskEvaluationContext assembly
    -> Risk Domain deterministic evaluation
    -> immutable persisted RiskEvaluation
    -> REST response (now exposed through the Gateway)
    -> Execution (future Story)
```

### Responsibility Preservation (ADR-028 / ADR-031)

| Component | Owns | Never does |
|-----------|------|------------|
| Market Intelligence | analysis, observations, opportunities, Trade Plans | financial validation, risk decisions |
| Trading Core | orchestration, context assembly, persistence, API exposure | deterministic risk evaluation |
| Risk Domain | evaluation, rules, authorization decision | orchestration, persistence, external I/O |
| Broker Service | external financial facts | authorization, business decisions |
| Market Data Service | market prices and market reference data | financial validation |

---

## Changes Delivered

### Production Change
- `gateway/src/main/java/com/hope/trading/gateway/config/GatewayRouteConfig.java`
  — added route `trade-plan-risk-evaluations` forwarding `/api/v1/trade-plans/**`
  to `lb://trading-core`. Additive only; no existing route modified.

### Test Added
- `gateway/src/test/java/com/hope/trading/gateway/GatewayRiskEvaluationRouteTest.java`
  — verifies the authorization path matches, unrelated paths do not match, and
  all existing routes remain registered.

### Not Changed
- Trading Core risk application/API/ports/persistence/execution: unchanged.
- Risk Domain: unchanged.
- Angular, Broker Service, Market Data, Market Intelligence: unchanged.
- Migrations: none.

---

## Validation Results

| Module | Command | Result |
|--------|---------|--------|
| risk-domain | `mvn -o test` | PASS — 21 tests |
| trading-core (focused risk) | `./mvnw -o -Dtest=... test` | PASS — 29 tests |
| trading-core (full) | `./mvnw -o test` | 73/74 PASS; 1 pre-existing timing-sensitive outbox failure |
| gateway | `mvn -o test` | PASS — 4 tests |
| git diff --check | — | PASS (clean) |

The single full-suite trading-core failure
(`RiskAcknowledgmentOutboxPersistenceTest.claimLeasePreservesExactIdentityAndSupportsDurableExplicitRetry`)
is pre-existing and timing-sensitive, documented in Story 0001/0002 reports, and
outside Story 0003 scope. No Trading Core production code changed here.

---

## Acceptance-Criteria Trace

| Story criterion | Status | Evidence |
|-----------------|--------|----------|
| Trade Plans evaluated through the Risk Domain | Pass | existing orchestration + focused risk suite |
| Trading Core assembles RiskEvaluationContext | Pass | context-assembly/fail-closed tests |
| Evaluations immutable and persisted | Pass | RiskPersistence + replay tests |
| Authorization deterministic | Pass | Risk Domain invocation tests |
| APPROVED / APPROVED_WITH_WARNINGS / REJECTED | Pass | decision-mapping tests |
| Unavailable/unknown outcomes conservative | Pass | `CONTEXT_UNAVAILABLE` fail-closed tests |
| Evaluation idempotent | Pass | replay + conflict tests |
| Traceability preserved | Pass | provenance/snapshot-version assertions |
| Endpoint exposed via Gateway | Pass | `GatewayRiskEvaluationRouteTest` |
| No execution coupling | Pass | `RiskEvaluationArchitectureTest` green |
| No unrelated behavior changed | Pass | module-scope diff + `git diff --check` |

---

## Remaining Technical Debt

1. Pre-existing timing-sensitive outbox lease test in trading-core
   (`RiskAcknowledgmentOutboxPersistenceTest`) — stabilize independently.
2. Deployed PostgreSQL migration rehearsal and deployed Gateway -> Trading Core
   HTTP flow pending.
3. Remote Sonar scan / Quality Gate pending credentials.
4. Human Code Review approval and human commit pending.

---

## Recommendation

**ENGINEERING REPORT APPROVED**

Story 0003 is complete and satisfies its acceptance criteria. The authorization
workflow is deterministic, immutable, idempotent, traced, and now reachable
through the Gateway, while preserving strict separation from the Execution
Domain.

**Next steps:** human review of the diff in IntelliJ, remote Sonar scan when
configured, deployed E2E validation, then human commit.

---

*Engineering Report generated per engineering-story workflow*
*Story 0003 — Authorize Trade Plans through the Risk Domain*
*Workflow Stage: Complete (all artifacts present)*