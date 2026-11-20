# Epic 0248 - AI Market Intelligence V1

**Status:** `ACTIVE - Story implementation gated`
**Date:** 2026-11-20
**Priority:** P1 - Trading Intelligence
**Predecessor:** Epic work package `ES-01 - AI and Market Intelligence Architecture Audit`
**Authorization:** ADR-103 accepted; each implementation Story requires its own analysis and plan approval

## 1. Goal

Provide bounded, manually triggered AI interpretation of one existing Trading Opportunity while
preserving deterministic Market Intelligence authority, explicit human review, deterministic risk,
and Trading Core execution boundaries.

## 2. Scope

### In scope

- Immutable, provider-neutral ContextPack construction.
- Java-owned `OPPORTUNITY_ASSESSMENT` and `DIAGNOSTIC` modes.
- Evidence-grounded structured AI interpretation.
- Durable association between interpretation, opportunity version, ContextPack and technical execution.
- Explicit cross-service recovery and idempotency semantics.
- Human-facing review of advisory analysis results.

### Out of scope

- Autonomous trading or automatic passive-to-active escalation.
- AI authority over Market Thesis, opportunity status, Risk, TradePlan or execution.
- Direct AI-to-broker or AI-to-Trading-Core execution.
- Provider activation or live model-quality claims without separate approval.
- A second AI runtime, orchestration service or eligibility engine.

## 3. Architecture Boundaries

- Market Intelligence owns opportunity authorization, deterministic eligibility, mode selection,
  ContextPack construction, result acceptance and durable business-analysis history.
- AI Engine owns provider invocation, prompt execution, technical schema validation and temporary
  provider execution state.
- Risk Domain remains authoritative for risk decisions.
- Trading Core remains authoritative for human authorization and execution state.
- Broker Service remains the only broker/provider execution boundary.

## 4. Dependency-Ordered Work

| Work package | Scope | Status |
|---|---|---|
| ES-01 | AI and Market Intelligence architecture audit | Complete |
| ES-02 | ContextPack and AI analysis domain contracts | Complete and merged |
| ES-03 | Deterministic AI interpretation orchestration | Next |
| ES-04 | AI boundary wire contract compatibility | Planned |
| ES-05 | Durable AI analysis history | Planned |
| ES-06 | Cross-service reconciliation and recovery | Planned |
| ES-07 | Trader-facing AI review workflow | Planned |

## 5. Open Decisions

- **OD-01:** exact deterministic blocker-to-`DIAGNOSTIC` mapping.
- **OD-02:** durable business-analysis history shape.
- **OD-03:** cross-service reconciliation and retry policy.

These decisions must be explicitly approved in the applicable Story workflow before implementation
requires them. ADR-103 provides recommendations but does not silently resolve them.

## 6. Current State

ES-02 introduced the immutable domain boundary and `RECOVERY_REQUIRED` technical state. The next
increment must connect those contracts to Java-owned opportunity loading, authorization, deterministic
eligibility and mode selection without yet adding provider activation, frontend behavior or broker
operations.
