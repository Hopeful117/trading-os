# Implementation Report

## Story

Story 0003 — Authorize Trade Plans through the Risk Domain.

## Status

Complete — Implementation Report Stage.

## Executive Summary

Story 0003 establishes and exposes the deterministic authorization boundary
between Trade Planning and Broker Execution. A Trade Plan produced by Market
Intelligence is evaluated through the deterministic Risk Domain and transformed
into an immutable, idempotent and fully traced `RiskEvaluation` before any
execution is allowed.

The production authorization machinery already existed in Trading Core as the
result of Story 0001/0002 (`TradePlanRiskEvaluationService`,
`RiskEvaluationModels`, `RiskPersistence`, the public REST endpoint and its
tests). Story 0003 verified and hardened that orchestration and closed the
remaining architectural gap: the public authorization endpoint was not reachable
through the Gateway.

The genuine delta delivered by this Story is therefore:

- a Gateway route forwarding `POST /api/v1/trade-plans/**` to `lb://trading-core`;
- a focused Gateway route test proving the authorization path is routed, that
  unrelated paths are not caught, and that existing routes remain registered;
- confirmation that the Trading Core orchestration preserves idempotency,
  immutability, fail-closed context assembly and strict isolation from the
  Execution Domain.

No broker execution, order submission, Execution Intent, frontend flow, or AI
decision was introduced. No Risk Domain production code was modified.

## Repository State

- Repository: `/home/ludo/Bureau/workspace/trading-os`
- Branch: `main`, tracking `origin/main`
- Pre-existing working-tree state preserved. The following were present before
  implementation and remain untouched:
  - `docs/stories/0003 - Authorize Trade Plans through the Risk Domain/story.md`
    (added-then-modified, staged by the human before this task);
  - untracked
    `docs/stories/0003 - Authorize Trade Plans through the Risk Domain/implementation-plan.md`
    and `repository-analysis.md`.

No commit, push, merge, reset, checkout, clean, or discard operation was
performed.

## Authoritative Inputs Reviewed

- `AGENTS.md`;
- the approved Story 0003 Story, Repository Analysis, and Implementation Plan;
- accepted ADR-028 (Risk Domain), ADR-029 (Execution Domain), ADR-031
  (Trade Planning Context and Risk Context responsibilities);
- Story 0001 implementation report, code review, and engineering report;
- Story 0002 implementation report, engineering report, and implementation plan;
- current Trading Core risk application, API, ports, persistence and tests;
- current Gateway route configuration and test setup.

## Blocker Consideration

No implementation blocker was reached. The mandatory stop condition in the
Story and plan (assembling authoritative snapshots without inventing financial
facts) was already satisfied by the Story 0001/0002 baseline. The authorization
workflow fails closed on missing configuration, profiles, snapshots, required
margin and prices without fabricating values, and it never infers balances,
leverage, buying power or rule versions.

## Genuine Delta vs Existing Baseline

| Required behavior | Before Story 0003 | After Story 0003 |
| --- | --- | --- |
| Authorization orchestration | Present (`TradePlanRiskEvaluationService`) | Preserved and verified |
| Idempotent, immutable evaluation | Present | Preserved and verified |
| Fail-closed context assembly | Present | Preserved and verified |
| Persistence and provenance | Present (`RiskPersistence`) | Preserved and verified |
| Public endpoint | Present but not routed | Now reachable through the Gateway |
| Gateway route coverage | None | Added and tested |
| Execution isolation | `RiskEvaluationArchitectureTest` | Confirmed green |

## Modified and Created Files

### Modified

- `gateway/src/main/java/com/hope/trading/gateway/config/GatewayRouteConfig.java`
  — added the `trade-plan-risk-evaluations` route forwarding
  `/api/v1/trade-plans/**` to `lb://trading-core`. No existing route was altered.

### Created

- `gateway/src/test/java/com/hope/trading/gateway/GatewayRiskEvaluationRouteTest.java`
  — focused route test with three cases (authorization path matches,
  unrelated path does not match, existing routes remain registered).
- `docs/stories/0003 - Authorize Trade Plans through the Risk Domain/implementation-report.md`
  (this file).

### Not Modified

- `trading-core` risk, API, ports, persistence and execution packages: unchanged.
- `risk-domain`: unchanged.
- Angular, Broker Service, Market Data, Market Intelligence: unchanged.
- Migrations: none added or changed. No schema change was required.

## Architecture Delivered

- The authorization pipeline remains: Trade Plan -> Trading Core context assembly
  -> Risk Domain deterministic evaluation -> immutable persisted `RiskEvaluation`
  -> REST response.
- Trading Core orchestrates and assembles the `RiskEvaluationContext`; the Risk
  Domain is the sole deterministic decision authority; Broker Service provides
  facts only; Market Intelligence produces Trade Plans only.
- The authorization modules retain no coupling to the Execution Domain
  (ADR-029). `RiskEvaluationArchitectureTest` enforces that the `risk` package
  never references `trading_core.execution`.
- The public endpoint is now reachable through the Gateway for authenticated
  Trading Core clients without relaxing JWT validation.

## Controlled Outcomes

The workflow continues to distinguish completed authorizations
(`COMPLETED` with `APPROVED`, `APPROVED_WITH_WARNINGS` or `REJECTED`), rejected
authorizations with structured reasons, and fail-closed unavailable outcomes
(`CONTEXT_UNAVAILABLE`) for missing configuration, profile, snapshots, required
margin or prices. No authorization is granted when required information is
incomplete; the workflow never fails open.

## Tests and Validation

### risk-domain

Command: `mvn -o test` (module directory `risk-domain`).

Result: 21 tests, 0 failures/errors/skips — PASS. No Risk Domain production
code was changed.

### trading-core

Command: `./mvnw -o test` (module directory `trading-core`).

Result: 74 tests executed, 73 pass, 1 failure — the pre-existing,
timing-sensitive
`RiskAcknowledgmentOutboxPersistenceTest.claimLeasePreservesExactIdentityAndSupportsDurableExplicitRetry`.
This test is documented in Story 0001/0002 reports as failing on the baseline
under scheduler contention and is outside Story 0003 scope. No Trading Core
production code was changed by this Story.

Focused risk suite
(`TradePlanRiskEvaluationServiceTest`, `RiskEvaluationArchitectureTest`,
`RiskPersistenceTest`, `RiskAcknowledgmentDeliveryServiceTest`,
`RiskDayAndReconstructionTest`): 29 tests, 0 failures/errors/skips — PASS.

### gateway

Command: `mvn -o test` (module directory `gateway`).

Result: 4 tests (1 existing `GatewayApplicationTests` + 3 new
`GatewayRiskEvaluationRouteTest`), 0 failures/errors/skips — PASS.

### Repository validation

Command: `git diff --check`.

Result: PASS with no output.

## SonarQube

No configured Sonar server/token was available in this local run, so no remote
scan or server-side Quality Gate check was executed. Local test and coverage
generation were executed. Static analysis is supplementary to tests,
ADR/architecture validation, and human Code Review; no remote success is
claimed.

## Remaining Issues

- The pre-existing Trading Core outbox lease test remains timing-sensitive under
  scheduler contention and should be stabilized independently of Story 0003.
- Deployed PostgreSQL migration rehearsal and a deployed Gateway -> Trading Core
  HTTP flow were not available in this local run.
- Remote Sonar scan, human Code Review approval, and human commit remain
  pending.

## Final Git Status

- Branch: `main`, tracking `origin/main`; no commit was created.
- Implementation changes remain unstaged and visible for human inspection in
  IntelliJ under `gateway/` and the Story 0003 directory.
- Pre-existing user changes at `docs/stories/0003 .../story.md` were not
  modified, reverted, staged, or discarded.
- No file was staged, committed, pushed, merged, reset, checked out, cleaned,
  or used to rewrite history.
