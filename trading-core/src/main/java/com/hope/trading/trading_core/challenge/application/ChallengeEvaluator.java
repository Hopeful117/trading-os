package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.risk.domain.RiskValidationResult;
import com.hope.trading.risk.domain.RiskTypes.RuleSeverity;
import com.hope.trading.risk.domain.RiskTypes.RuleStatus;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengePhaseDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengePhaseProgressionState;
import com.hope.trading.trading_core.challenge.domain.ChallengeProgressionEvidence;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.model.Account;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Applies one authoritative Risk and progression evaluation to a Challenge. */
@Service
public final class ChallengeEvaluator {
    private final ChallengeProgressionValueResolver progressionValues = new ChallengeProgressionValueResolver();

    public ChallengeStatus evaluate(ChallengeInstance challenge, ChallengeDefinition definition,
                                    Account account, RiskValidationResult risk, UUID riskEvaluationId,
                                    Instant evaluatedAt) {
        return evaluate(challenge, definition, account, risk, riskEvaluationId, evaluatedAt,
                ChallengeProgressionEvidence.unavailable(evaluatedAt));
    }

    public ChallengeStatus evaluate(ChallengeInstance challenge, ChallengeDefinition definition,
                                    Account account, RiskValidationResult risk, UUID riskEvaluationId,
                                    Instant evaluatedAt, ChallengeProgressionEvidence evidence) {
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(risk, "risk");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(evidence, "evidence");

        if (challenge.status() != ChallengeStatus.ACTIVE) return challenge.status();
        ChallengePhaseDefinition phase = currentPhase(challenge, definition);
        validatePhaseState(challenge, phase, evidence, evaluatedAt);
        if (terminalRiskViolation(challenge, phase, risk)) {
            challenge.breach(evaluatedAt, "Blocking Challenge Risk constraint breached", riskEvaluationId);
            return ChallengeStatus.BREACHED;
        }
        if (risk.evaluationStatus() != com.hope.trading.risk.domain.RiskTypes.EvaluationStatus.COMPLETED) {
            return ChallengeStatus.ACTIVE;
        }
        if (challenge.phaseProgressionState() == ChallengePhaseProgressionState.PROVIDER_CONFIRMED) {
            return advanceAfterConfirmation(challenge, definition, phase, evaluatedAt, riskEvaluationId);
        }
        if (challenge.phaseProgressionState() == ChallengePhaseProgressionState.LOCALLY_SATISFIED) {
            return progressAfterLocalSatisfaction(challenge, definition, phase, evidence, evaluatedAt,
                    riskEvaluationId);
        }
        if (!objectivesSatisfied(account, definition, phase, evidence)) return ChallengeStatus.ACTIVE;

        challenge.markLocallySatisfied(evaluatedAt);
        return progressAfterLocalSatisfaction(challenge, definition, phase, evidence, evaluatedAt,
                riskEvaluationId);
    }

    private ChallengeStatus progressAfterLocalSatisfaction(ChallengeInstance challenge,
                                                            ChallengeDefinition definition,
                                                            ChallengePhaseDefinition phase,
                                                            ChallengeProgressionEvidence evidence,
                                                            Instant evaluatedAt, UUID riskEvaluationId) {
        if (phase.providerConfirmationRequired()) {
            if (!evidence.providerConfirmed() || !evidence.fresh()) return ChallengeStatus.ACTIVE;
            challenge.confirmProviderProgression(evaluatedAt, evidence.providerConfirmationReference());
        }
        return advanceAfterConfirmation(challenge, definition, phase, evaluatedAt, riskEvaluationId);
    }

    private ChallengeStatus advanceAfterConfirmation(ChallengeInstance challenge,
                                                     ChallengeDefinition definition,
                                                     ChallengePhaseDefinition phase,
                                                     Instant evaluatedAt, UUID riskEvaluationId) {
        ChallengePhaseDefinition next = definition.effectivePhases().stream()
                .filter(candidate -> candidate.phaseOrder() == phase.phaseOrder() + 1)
                .findFirst().orElse(null);
        if (next == null) {
            String reason = definition.phases().isEmpty()
                    ? "Challenge " + phase.progressionValueSource().name().toLowerCase() + " target reached"
                    : "Challenge final phase completed";
            challenge.pass(evaluatedAt, reason, riskEvaluationId);
            return ChallengeStatus.PASSED;
        }
        challenge.advanceTo(next, evaluatedAt);
        return ChallengeStatus.ACTIVE;
    }

    private boolean objectivesSatisfied(Account account, ChallengeDefinition definition,
                                       ChallengePhaseDefinition phase,
                                       ChallengeProgressionEvidence evidence) {
        if (phase.profitTargetRatio() != null) {
            BigDecimal value = progressionValues.resolve(account, definition, phase.progressionValueSource())
                    .orElse(null);
            if (value == null) return false;
            BigDecimal target = definition.startingCapital()
                    .add(definition.startingCapital().multiply(phase.profitTargetRatio()));
            if (value.compareTo(target) < 0) return false;
        }
        return phase.minimumTradingDays() == null
                || (evidence.fresh() && evidence.executedTrading().complete()
                && evidence.executedTrading().qualifyingTradingDays().size() >= phase.minimumTradingDays());
    }

    private ChallengePhaseDefinition currentPhase(ChallengeInstance challenge, ChallengeDefinition definition) {
        return definition.effectivePhases().stream()
                .filter(phase -> challenge.currentPhaseKey() == null
                        ? phase.phaseOrder() == 1
                        : phase.phaseKey().equals(challenge.currentPhaseKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Challenge current phase is unknown"));
    }

    private void validatePhaseState(ChallengeInstance challenge, ChallengePhaseDefinition phase,
                                    ChallengeProgressionEvidence evidence, Instant evaluatedAt) {
        if (challenge.currentPhaseKey() == null && !challenge.definitionVersion().isBlank()
                && challenge.phaseProgressionState() != null) {
            throw new IllegalStateException("Legacy Challenge cannot contain phase state");
        }
        if (challenge.currentPhaseKey() != null && challenge.phaseProgressionState() == null) {
            throw new IllegalStateException("Challenge phase state is incomplete");
        }
        if (challenge.phaseProgressionState() != null
                && phase.providerConfirmationRequired()
                && challenge.phaseProgressionState() == ChallengePhaseProgressionState.PROVIDER_CONFIRMED) {
            return;
        }
        if (evidence.phaseKey() != null && !evidence.phaseKey().isEmpty()
                && !evidence.phaseKey().equals(phase.phaseKey())) {
            throw new IllegalStateException("Progression evidence belongs to another Challenge phase");
        }
        if (evidence.fresh() && (evidence.observedAt().isAfter(evaluatedAt)
                || evidence.executedTrading().observedAt().isAfter(evaluatedAt))) {
            throw new IllegalStateException("Progression evidence is from the future");
        }
        if (!evidence.fresh()) return;
    }

    private boolean terminalRiskViolation(ChallengeInstance challenge, ChallengePhaseDefinition phase,
                                          RiskValidationResult risk) {
        return risk.ruleResults().stream().anyMatch(result ->
                result.status() == RuleStatus.FAILURE
                        && result.severity() == RuleSeverity.BLOCKING
                        && (result.ruleId().equals("DAILY_DRAWDOWN")
                        || result.ruleId().equals("MAX_TOTAL_DRAWDOWN"))
                        && phase.riskPolicyVersion().equals(
                         risk.trace().policyVersions().get(phase.riskPolicyId().toString())));
    }
}
