# Forex LIVE_CURRENT and Real T0 Validation

## Verdict

`REAL_T0_AND_FOREX_FOCUSED_VALIDATED`

The focused Forex `LIVE_CURRENT` behavior and the Trading Core TradePlan to T0
application path now have passing deterministic test evidence. No LIVE broker
operation occurred. No human authorization or execution continuation was
invoked.

## Changes

- Added `RealTradePlanT0IntegrationTest`.
- The test invokes the public `OpportunityTradePlanOrchestrationService`.
- The real `TradePlanRiskEvaluationService`, `RiskEngine`, risk persistence and
  H2/Flyway schema are used.
- External Market Intelligence, TradePlan loading, broker facts, valuation and
  margin ports remain mocked at their infrastructure/application boundaries.
- The test verifies a persisted completed PREFLIGHT evaluation and context
  snapshot for the returned TradePlan and account.
- Added `LEVEL_II_REQUIRED` fail-closed coverage when no cutoff-compatible
  order-book evidence exists.
- Strengthened spread evidence assertions to preserve provider, instrument,
  cutoff, fingerprint and observation timestamp provenance.

## Validation

All commands completed successfully:

```text
market-intelligence:
TradeabilityAnalysisCapabilityTest, TradeabilityCalculatorTest,
ActiveAnalysisStrategyTest, AnalysisExecutionServiceTest

trading-core:
RealTradePlanT0IntegrationTest, OpportunityTradePlanPreparationIntegrationTest,
OpportunityTradePlanOrchestrationServiceTest, TradePlanRiskEvaluationServiceTest
```

Relevant Surefire results:

- `RealTradePlanT0IntegrationTest`: 1 test, 0 failures.
- `OpportunityTradePlanPreparationIntegrationTest`: 2 tests, 0 failures.
- `TradePlanRiskEvaluationServiceTest`: 33 tests, 0 failures.
- `TradeabilityAnalysisCapabilityTest`: 13 tests, 0 failures.
- `TradeabilityCalculatorTest`: 12 tests, 0 failures.

`git diff --check` passes.

## Boundary

This validates the real Trading Core T0 evaluation path from the public
orchestration entry point with a controlled upstream TradePlan fixture. It does
not prove the complete live Market Intelligence generation, durable upstream
TradePlan creation, deployed database migration state, human approval or
broker execution path in one test. Those remain separate validation concerns.

## Git State

Changes remain uncommitted and unpushed for human review.
