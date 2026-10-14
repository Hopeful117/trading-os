# Story 0099 - Implementation Plan

## Changes

1. Remove `final` from `ValidateAndCreateService` so Spring can create a
   class-based proxy.
2. Add Spring `@Transactional` to `validateAndCreate`.
3. Add a unit regression asserting the transaction boundary annotation.
4. Add a Spring context regression asserting the service is AOP-proxied.

## Validation

- Run `mvn -Dtest=ValidateAndCreateServiceTest test`.
- Run `mvn -Dtest=TradingCoreApplicationTests test`.
- Run `mvn -Dtest='com.hope.trading.trading_core.execution.**' test`.
- Run `git diff --check`.
- Report PostgreSQL/live runtime validation separately.

## Constraints

- Keep the change limited to the execution validation boundary and tests.
- Preserve the existing pessimistic lock.
- Do not change validation ordering, Challenge compatibility, Risk, T1, or
  broker execution behavior.
- Do not commit or push automatically.
