# Story 0079 - Implementation Report

## Result

Implemented the first provider-neutral PAPER Challenge progression loop.
Challenge lifecycle remains in Trading Core, Risk remains the authority for
drawdown constraints, and Account remains the source of PAPER financial state.

## Repository State

- Starting HEAD: `40db9eb9c249b282d2a14bcd0082e41dd822fb29`
- Branch: `main`
- Initial worktree: clean apart from the Story 0079 documentation created
  during the approved workflow.
- Final worktree: contains only Story 0079 documentation and implementation
  changes; no commit, push, merge, or reset was performed.

## Account Monitoring

`AccountRiskMonitoringService` builds an immutable observed context with:

- `ValidationMode.ACCOUNT_MONITORING`;
- no proposed TradePlan;
- PAPER facts from `ModeAwareRiskFactsProvider`;
- the exact Challenge Risk Policy and version;
- the Challenge profile UUID as the trace policy identity;
- the existing daily baseline persistence path;
- Account, Portfolio, Market, Rule Set, and Context snapshots;
- existing `DeterministicRiskEngine` evaluation.

The generic `risk_evaluation` audit model was minimally extended so
account-monitoring evaluations can omit TradePlan identity and persist an
explicit `evaluation_mode`. No Challenge-specific Risk table was introduced.

## Challenge Evaluation

`ChallengeEvaluator` evaluates Risk before progression:

1. Blocking `DAILY_DRAWDOWN` or `MAX_TOTAL_DRAWDOWN` failures with the exact
   Challenge policy ID and version produce `BREACHED`.
2. Incomplete or failed Risk evaluation does not produce `PASSED`.
3. `BALANCE` progression computes the target with `BigDecimal` and uses an
   inclusive comparison.
4. Terminal instances are unchanged on repeated evaluation.

If target and terminal breach coincide, `BREACHED` wins.

## PAPER Integration

After `PaperSettlementService` mutates and saves Account state, the optional
`ChallengeReevaluationService` looks up the single ACTIVE Challenge. Accounts
without a Challenge return through the existing settlement path. Reevaluation
runs in a new transaction using the already-mutated Account state, then
persists only an actual lifecycle transition. Challenge lookup uses a
pessimistic lock and the entity retains optimistic locking, so concurrent
terminal evaluation cannot silently overwrite the first committed state.
Monitoring failure is logged and does not roll back the completed PAPER
settlement.

## ChallengeProgress

`ChallengeProgress` is a read projection assembled from Account,
ChallengeDefinition, and ChallengeInstance. It exposes current BALANCE, target,
profit, and bounded progress. Daily and total remaining-loss fields are null in
this V1 projection because the current generic Risk output does not expose those
remaining monetary limits without reimplementing Risk formulas.

An ownership-checked read endpoint is available at:

```text
GET /api/v1/accounts/{accountId}/challenge
```

No endpoint mutates Challenge status.

## Tests

Passed:

```text
mvn -q verify                              # risk-domain
./mvnw -q verify                           # trading-core
git diff --check
```

Focused coverage includes:

- Risk `ACCOUNT_MONITORING` with no proposed TradePlan;
- daily and total observed drawdown semantics;
- Challenge active, pass, breach, warning, collision, and terminal idempotency;
- post-settlement reevaluation orchestration;
- no-Challenge settlement behavior;
- account-monitoring persistence replay and idempotency;
- rejection of account-monitoring evaluations at execution authorization;
- exact policy identity matching;
- existing Challenge persistence and PAPER settlement suites.
- end-to-end PAPER entry/exit settlement followed by Challenge progression and
  persisted `ACCOUNT_MONITORING` Risk evaluation.

## Remaining Gaps

- Continuous open-position Challenge monitoring is not implemented because
  local PAPER mark-to-market equity is not authoritative between settlements.
- Daily/total remaining-loss projection fields remain unavailable rather than
  duplicating Risk calculations.
- Real Kraken Prop synchronization and sandbox validation remain out of scope.
- Frontend display and historical Challenge simulation remain future work.

## Documentation Reconciliation

Story documentation, implementation plan, and implementation report were added
under the canonical Story directory. No broader architecture or operational
documentation required updating.

## Vault Outcome

The Obsidian vault was not consulted because the repository Story, ADRs, and
current implementation fully determined this change. No vault action is
proposed.
