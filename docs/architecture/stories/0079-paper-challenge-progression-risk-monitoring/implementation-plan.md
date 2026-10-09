# Story 0079 - Implementation Plan

## Design

Keep Challenge lifecycle ownership in `trading-core`, Risk calculations in
`risk-domain`, and PAPER financial state in `Account`.

The runtime path will be:

```text
PAPER settlement
    -> Account mutation
    -> account-monitoring Risk context
    -> deterministic Risk engine
    -> persisted ACCOUNT_MONITORING result
    -> ChallengeEvaluator
    -> ACTIVE / PASSED / BREACHED
```

Risk breach evaluation precedes target evaluation. This is safety-first: when
one authoritative state satisfies both conditions, the Challenge becomes
`BREACHED`.

## Account Monitoring Risk

Add an application service beside the existing
`TradePlanRiskEvaluationService`, reusing the existing context and engine
components rather than creating a second Risk pipeline.

The service will:

- load the Account and its technical BrokerAccount mapping;
- use `ModeAwareRiskFactsProvider` for PAPER facts;
- resolve the exact Challenge Risk Policy reference/version;
- obtain the daily baseline through existing Risk persistence;
- construct `RiskEvaluationRequest` with `ACCOUNT_MONITORING` and no
  `ProposedTrade`;
- construct observed `AccountSnapshot`, `PortfolioSnapshot`, `MarketSnapshot`,
  `TradingContext`, and `RuleSetSnapshot` through the existing immutable path;
- evaluate with `DeterministicRiskEngine`;
- persist generic component/context/evaluation provenance with an explicit
  evaluation mode;
- return the domain `RiskValidationResult` and evaluation reference.

The account-monitoring path will not fabricate TradePlan values. Existing Risk
metrics will remain authoritative for daily and total drawdown. The projection
will use only values available from the generic Risk result; unavailable
remaining-loss metrics will be omitted rather than recalculated in Challenge.

## Risk Persistence Evolution

Extend the existing generic Risk evaluation audit model minimally so it can
represent both TradePlan and account-monitoring evaluations:

- make TradePlan identity nullable only for account-monitoring records;
- persist evaluation mode and account-monitoring provenance;
- preserve existing TradePlan idempotency and replay behavior;
- avoid creating a Challenge-specific evaluation table or audit mechanism;
- add the required Flyway migration and persistence tests.

## Challenge Evaluation

Add a small deterministic `ChallengeEvaluator` that consumes an ACTIVE
`ChallengeInstance`, its exact definition, the authoritative Account balance,
the account-monitoring `RiskValidationResult`, and an evaluation timestamp.

Rules:

1. A terminal blocking Risk violation applicable to the Challenge policy causes
   `BREACHED` and records the Risk evaluation reference.
2. Otherwise, for `BALANCE`, calculate the target using the definition's
   `startingCapital` and `profitTargetRatio` with `BigDecimal`.
3. Inclusive balance target attainment causes `PASSED`.
4. Otherwise the Challenge remains `ACTIVE`.
5. Terminal instances are returned unchanged; lifecycle methods remain the only
   transition mechanism.

Challenge-terminal violations will be identified from exact policy rule
   identity, blocking severity, and failure status. A generic rejected result
   alone will not be treated as a Challenge breach.

## PAPER Integration

Add an application-level reevaluation collaborator to the existing PAPER
settlement boundary. Settlement will update Account first, save it, and then
invoke Challenge reevaluation using the managed post-mutation Account state.

The collaborator will:

- find at most one ACTIVE Challenge for the Account;
- return normally when no Challenge exists;
- load the exact Challenge definition;
- invoke account-monitoring Risk and `ChallengeEvaluator`;
- save only an actual lifecycle transition.

The smallest safe transaction design will be verified against current execution
finalization boundaries. If the current transaction cannot guarantee a fresh
post-settlement read, reevaluation will use a transaction-completion-safe
application boundary rather than evaluating stale state. No messaging system
or new event framework will be introduced.

## ChallengeProgress

Add a read-only projection assembled from Account, ChallengeDefinition,
ChallengeInstance, and the latest account-monitoring result where available.
It will include only derivable values:

- Challenge identity and status;
- starting capital and current BALANCE value;
- target value and current profit;
- target progress when balance and target are available;
- Risk-derived daily/total metrics only where generic output exposes reliable
  remaining values;
- evaluation timestamp and relevant provenance references.

No continuously changing projection fields will be persisted.

## Read API

Inspect existing Trading Core ownership and DTO conventions. If a minimal API
is justified, add an account-scoped read endpoint returning a DTO through
`ResponseEntity`, enforcing account ownership and exposing no JPA entity or
mutation operation. No broad Challenge CRUD API will be added.

## Tests

Add focused tests for:

- account monitoring with no TradePlan and `ACCOUNT_MONITORING` mode;
- daily and total threshold behavior using authoritative baselines;
- exact Risk Policy/version and context provenance;
- terminal and non-terminal Risk result interpretation;
- Challenge active/pass/breach ordering and inclusive target semantics;
- terminal idempotency and optimistic-lock race behavior;
- PAPER settlement with active, pass, breach, no Challenge, and repeated
  evaluation paths;
- projection omission of unavailable derived values.

## Validation

Run:

```text
./mvnw -q -f ../risk-domain/pom.xml test
./mvnw -q verify                         # trading-core
git diff --check
```

Review the complete diff for duplicated Risk formulas, provider-specific
branches, fake TradePlans, stale Account reads, mandatory Challenge behavior,
and accidental mark-to-market scope expansion.

## Explicit Limitation

Story 0079 provides reliable post-settlement PAPER evaluation only. It does not
claim continuous open-position monitoring because current local PAPER
mark-to-market equity is not authoritative between settlements.
