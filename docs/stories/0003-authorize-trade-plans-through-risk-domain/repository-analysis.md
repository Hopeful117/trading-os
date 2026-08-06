# Repository Analysis

## Story Overview

- **Story ID**: 0003
- **Title**: Authorize Trade Plans through the Risk Domain
- **Status**: Draft
- **Location**: `/home/ludo/Bureau/workspace/trading-os/docs/stories/0003 - Authorize Trade Plans through the Risk Domain/story.md`
- **Author**: Trading OS Team
- **Last Modified**: 2026-08-05 08:36 GMT+2

## Context Summary

Story 003 addresses the missing deterministic authorization step between Trade Planning and Broker Execution. It requires evaluating immutable Trade Plans through the Risk Domain before broker execution, ensuring:

- Risk evaluation of immutable Trade Plans
- Integration with Trading Core's Risk Evaluation Context
- Persistence of authorization decisions
- Deterministic and idempotent evaluation
- Complete audit traceability

## Affected Modules & Services

| Module | Responsibility | Key Components |
|--------|----------------|---------------|
| **trading-core** | Orchestration & deterministic business validation | Account management, Risk rules, ADR-005 compliance |
| **market-intelligence** | Trade Plan source | Immutable Trade Plans, TradePlan repository |
| **risk-domain** | Deterministic decision authority | Risk Evaluation Request, Proposed Trade, RiskEngine |
| **risk-domain** (ADR-028) | Risk evaluation engine | RiskEvaluationContextBuilder, DeterministicRiskEngine |
| **risk-domain** (ADR-031) | Clarified context boundaries | TradePlanningContext, RiskBudget |

## Repository Structure Investigation

The Trading OS repository is a multi-service Maven project without a root aggregator. Key directories:

```
trading-core
risk-domain
market-intelligence
broker-service
gateway
eureka-server
...
docs/
  stories/
    0001-connect-trade-plan-to-risk/
    0002-connect-market-intelligence-to-trade-plan-generation/
    0003 - Authorize Trade Plans through the Risk Domain/   <-- Current Story
```

The story directory currently contains only `story.md`. No repository analysis, implementation plan, or other workflow artifacts exist.

## Required Integration Points

Based on ADR-028 and ADR-031:

1. **RiskEvaluationContext Assembly** - Trading Core must construct immutable snapshots from:
   - `AccountSnapshot`
   - `PortfolioSnapshot`
   - `MarketSnapshot`
   - `RuleSetSnapshot`
   These snapshots must come from authoritative sources (see below).

2. **Persistent Audit Trail** - Create `RiskEvaluationRecord` artifacts that are immutable and traceable to specific Trade Plans.

3. **REST API Endpoint** - Expose an authorization endpoint in Trading Core for Market Intelligence to submit Trade Plans.

4. **Idempotent Evaluation** - Multiple evaluations of identical Trade Plans should return consistent results without side effects.

5. **Snapshot Authority** - Determine authoritative sources:
   - Account ownership & balances
   - Portfolio positions
   - Market prices
   - Rule versions
   - Effective rule resolution

## Cross-Module Dependencies

| Dependent Service | Current State | Integration Gap |
|-----------------|---------------|-----------------|
| Market Intelligence | Provides immutable Trade Plans via repository | No mechanism to submit Trade Plans to Trading Core for evaluation |
| Trading Core | Designed for orchestration (ADR-005) | No API to receive Trade Plans for evaluation |
| Risk Domain | Stateless deterministic engine (ADR-028) | No integration mechanism with Trading Core |
| Broker Service | Facts provider only | Must not participate in evaluation |

## Required Artifacts

To satisfy the workflow:

1. **Repository Analysis Document** - Already generated (this file)
2. **Implementation Plan** - To outline technical approach, endpoints, and data flow
3. **Risk Evaluation Context Builder** - Or adapté code to assemble snapshots
4. **API Specification** - OpenAPI/Swagger for Trade Plan evaluation endpoint
5. **Audit Record Schema** - For immutable persistence of evaluation results
6. **Test Cases** - For validation in trading-core and risk-domain

## Open Questions & Risks

| Question | Impact | Possible Solutions |
|----------|--------|-------------------|
| Should identical Trade Plan evaluations reuse existing results? | Affects idempotency strategy | Cache valid results or always generate new evaluation artifacts |
| Must RiskEvaluationRecord be persisted durably? | Determines storage requirement | Implement write-through to audit DB or use in-memory with persistence hooks |
| Which service owns snapshot authority? | Risk of inconsistent data | Establish clear ownership: Trading Core assembles context from validated sources |
| How to derive marginRequired from TradePlan? | Critical for accurate evaluation | Extend TradePlan schema or compute via RiskMetrics calculator |
| What authentication mechanism for evaluation endpoint? | Security concern | Leverage existing JWT validation in Trading Core |

## Recommendations

1. **Proceed with Implementation Plan** to define technical approach and API design.
2. **Establish clear authority boundaries** between Market Intelligence and Trading Core per ADR-031.
3. **Design immutable audit trail** to satisfy ADR-028 auditability requirements.
4. **Implement idempotency** by generating unique evaluation IDs tied to Trade Plan version + context hash.
5. **Adopt existing ADR-028 components** rather than creating new logic.

## Next Steps

1. **Await Human Approval** of Repository Analysis (current step)
2. Upon approval:
   - Proceed to **Implementation Planning**
   - Define technical approach, API design, and integration boundaries
   - Create Implementation Plan document
3. **Await Human Approval** of Implementation Plan