# Implementation Plan - Story 0040

## Overview

Correct the T0/T1 identity flow and centralize mode-aware Risk Facts acquisition
in Trading Core. Keep the Risk Domain neutral and reuse the existing LIVE,
Market Data, profile, persistence, and execution boundaries.

## Work Packages

1. Define a neutral application Risk Facts contract representing the complete
   input required by `RiskEvaluationContextBuilder`, including source versions,
   timestamps, completeness, and provenance.
2. Implement canonical identity resolution for T0 from `Account.accountId` and
   `Account.brokerAccountId`.
3. Implement T1 resolution from `TradePlan.tradingAccountId` and verify that
   the resolved canonical relation matches `ExecutionIntent.brokerAccountId`.
4. Resolve `ExecutionMode` only from the linked BrokerAccount.
5. Move existing LIVE BrokerRiskFacts and Market Data mapping behind the neutral
   provider without changing LIVE authority.
6. Add local PAPER facts construction from AccountBalance, Account, Trade, and
   local execution/settlement state.
7. Keep unsupported required PAPER facts, especially margin or protection, as
   explicit fail-closed outcomes rather than inventing values.
8. Make both T0 and T1 use the same provider and context assembly boundary.
9. Persist T1 financial Account ID and preserve existing T0/T1 provenance and
   idempotency behavior.

## Identity Rules

```text
Account.accountId = financial identity
BrokerAccount.id = routing identity
Account.brokerAccountId = canonical relation
```

The implementation must not use UUID equality, owner/provider lookup,
displayName, or legacy `Account.broker` as identity.

## Transaction Rules

- Do not introduce distributed transactions.
- Preserve existing T0/T1 transaction templates unless a local consistency
  defect requires a focused correction.
- A post-settlement T1 path is not part of this Story.
- If local PAPER facts are evaluated after settlement in a future flow, the
  snapshot must be captured after settlement commit.

## Validation Plan

### Unit

- Neutral source mapping for LIVE and PAPER.
- Mode routing and profile propagation.
- Completeness, timestamps, freshness, provenance, and currency validation.
- Missing required fact failure behavior.

### Application

- T0 canonical Account and BrokerAccount resolution.
- T1 TradePlan financial Account resolution.
- T1 relation mismatch rejection and financial-ID persistence.
- Zero BrokerRiskFacts calls for PAPER.
- Existing BrokerRiskFacts calls for LIVE.
- Exact profile ID/version reuse.

### Integration

- Distinct Account/BrokerAccount IDs through T0 and T1.
- Persisted PAPER local balances and positions.
- LIVE provider fact path.
- Market Data valuation provenance and incomplete-data failure.
- Missing relation/profile/margin and inconsistent local PAPER state.

## Explicit Non-Goals

Do not modify risk rules, RiskEngine semantics, Broker Service, frontend, PAPER
close behavior, margin policy, or full idempotency architecture.

## Completion Result

The Story is implementation-ready after human review. No production code,
migration, tests, or commit are part of this planning step.
