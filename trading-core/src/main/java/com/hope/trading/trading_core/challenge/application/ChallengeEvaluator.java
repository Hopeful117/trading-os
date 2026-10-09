package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.risk.domain.RiskValidationResult;
import com.hope.trading.risk.domain.RiskTypes.RuleSeverity;
import com.hope.trading.risk.domain.RiskTypes.RuleStatus;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.model.Account;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Applies one authoritative Risk and progression evaluation to a Challenge. */
@Service
public final class ChallengeEvaluator {
    public ChallengeStatus evaluate(ChallengeInstance challenge, ChallengeDefinition definition,
                                    Account account, RiskValidationResult risk, UUID riskEvaluationId,
                                    Instant evaluatedAt) {
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(risk, "risk");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");

        if (challenge.status() != ChallengeStatus.ACTIVE) return challenge.status();
        if (terminalRiskViolation(challenge, risk)) {
            challenge.breach(evaluatedAt, "Blocking Challenge Risk constraint breached", riskEvaluationId);
            return ChallengeStatus.BREACHED;
        }
        if (risk.evaluationStatus() != com.hope.trading.risk.domain.RiskTypes.EvaluationStatus.COMPLETED) {
            return ChallengeStatus.ACTIVE;
        }
        if (definition.progressionValueSource() == ProgressionValueSource.BALANCE) {
            BigDecimal balance = account.getBalances().stream()
                    .filter(value -> definition.capitalCurrency().equalsIgnoreCase(value.getAsset()))
                    .map(value -> value.getAmount())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Challenge balance is unavailable"));
            BigDecimal target = definition.startingCapital()
                    .add(definition.startingCapital().multiply(definition.profitTargetRatio()));
            if (balance.compareTo(target) >= 0) {
                challenge.pass(evaluatedAt, "Challenge balance target reached", riskEvaluationId);
                return ChallengeStatus.PASSED;
            }
        }
        return ChallengeStatus.ACTIVE;
    }

    private boolean terminalRiskViolation(ChallengeInstance challenge, RiskValidationResult risk) {
        return risk.ruleResults().stream().anyMatch(result ->
                result.status() == RuleStatus.FAILURE
                        && result.severity() == RuleSeverity.BLOCKING
                        && (result.ruleId().equals("DAILY_DRAWDOWN")
                        || result.ruleId().equals("MAX_TOTAL_DRAWDOWN"))
                        && challenge.riskPolicyVersion().equals(
                        risk.trace().policyVersions().get(challenge.riskPolicyId().toString())));
    }
}
