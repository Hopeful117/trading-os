package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinitionKey;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengePhaseDefinition;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Service
public class ChallengeProgressService {
    private final AccountRepository accounts;
    private final ChallengeInstanceRepository instances;
    private final ChallengeDefinitionRepository definitions;
    private final RiskPersistence riskPersistence;
    private final Clock clock;
    private final ChallengeProgressionValueResolver progressionValues = new ChallengeProgressionValueResolver();

    public ChallengeProgressService(AccountRepository accounts, ChallengeInstanceRepository instances,
                                    ChallengeDefinitionRepository definitions) {
        this(accounts, instances, definitions, null);
    }

    public ChallengeProgressService(AccountRepository accounts, ChallengeInstanceRepository instances,
                                    ChallengeDefinitionRepository definitions, RiskPersistence riskPersistence) {
        this(accounts, instances, definitions, riskPersistence, Clock.systemUTC());
    }

    @Autowired
    public ChallengeProgressService(AccountRepository accounts, ChallengeInstanceRepository instances,
                                    ChallengeDefinitionRepository definitions, RiskPersistence riskPersistence,
                                    Clock clock) {
        this.accounts = accounts;
        this.instances = instances;
        this.definitions = definitions;
        this.riskPersistence = riskPersistence;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ChallengeProgress get(UUID accountId, UUID ownerId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ChallengeNotFoundException("Account does not exist"));
        if (account.getUser() == null || !ownerId.equals(account.getUser().getUserId())) {
            throw new ChallengeOwnershipException("Account is not owned by the caller");
        }
        ChallengeInstance challenge = instances.findTopByAccountIdOrderByStartedAtDesc(accountId)
                .orElseThrow(() -> new ChallengeNotStartedException("Account has no Challenge"));
        ChallengeDefinition definition = definitions.findById(
                        new ChallengeDefinitionKey(challenge.definitionId(), challenge.definitionVersion()))
                .orElseThrow(() -> new ChallengeConfigurationException("Challenge definition does not exist"));
        ChallengePhaseDefinition phase = definition.effectivePhases().stream()
                .filter(candidate -> challenge.currentPhaseKey() == null
                        ? candidate.phaseOrder() == 1
                        : candidate.phaseKey().equals(challenge.currentPhaseKey()))
                .findFirst()
                .orElseThrow(() -> new ChallengeConfigurationException("Challenge current phase does not exist"));
        BigDecimal current = phase.profitTargetRatio() == null ? null
                : progressionValues.resolve(account, definition, phase.progressionValueSource()).orElse(null);
        BigDecimal target = phase.profitTargetRatio() == null ? null : definition.startingCapital()
                .add(definition.startingCapital().multiply(phase.profitTargetRatio()));
        BigDecimal profit = current == null ? null : current.subtract(challenge.startingCapital());
        BigDecimal progress = current == null || target == null ? null : target.compareTo(challenge.startingCapital()) == 0
                ? BigDecimal.ONE : profit.divide(target.subtract(challenge.startingCapital()), 12,
                java.math.RoundingMode.HALF_UP).max(BigDecimal.ZERO).min(BigDecimal.ONE);
        RiskPersistence.AccountMonitoringEvaluation evaluation = riskPersistence == null ? null
                : riskPersistence.latestAccountMonitoringEvaluation(accountId, challenge.id(),
                challenge.startedAt(), clock.instant().minus(Duration.ofHours(24)), clock.instant()).orElse(null);
        RiskHeadroom dailyHeadroom = headroom(challenge, evaluation, true);
        RiskHeadroom totalHeadroom = headroom(challenge, evaluation, false);
        BigDecimal dailyReferenceBalance = riskPersistence == null
                ? null : evaluation == null ? null : evaluation.dailyReferenceBalance();
        return new ChallengeProgress(challenge.id(), accountId, challenge.status(), challenge.startingCapital(),
                current, target, profit, progress, dailyReferenceBalance,
                dailyHeadroom.remaining(), totalHeadroom.remaining(),
                dailyHeadroom, totalHeadroom,
                challenge.status() == com.hope.trading.trading_core.challenge.domain.ChallengeStatus.PASSED
                        ? challenge.passedAt() : challenge.breachedAt(),
                challenge.passedAt(), challenge.breachedAt(), challenge.terminalReason(),
                challenge.terminalRiskEvaluationId());
    }

    private RiskHeadroom headroom(ChallengeInstance challenge,
                                  RiskPersistence.AccountMonitoringEvaluation evaluation,
                                  boolean daily) {
        if (evaluation == null) return RiskHeadroom.unavailable("RISK_EVALUATION_UNAVAILABLE", null);
        if (!"COMPLETED".equals(evaluation.status()) || evaluation.result() == null
                || evaluation.result().evaluationStatus()
                != com.hope.trading.risk.domain.RiskTypes.EvaluationStatus.COMPLETED) {
            return RiskHeadroom.unavailable("RISK_EVALUATION_INCOMPLETE", evaluation.evaluatedAt());
        }
        if (!challenge.accountId().equals(evaluation.accountId())
                || !evaluation.id().equals(evaluation.result().trace().evaluationId())) {
            return RiskHeadroom.unavailable("RISK_EVALUATION_CONTEXT_MISMATCH", evaluation.evaluatedAt());
        }
        if (!challenge.activeRiskPolicyVersion().equals(
                evaluation.result().trace().policyVersions().get(challenge.activeRiskPolicyId().toString()))) {
            return RiskHeadroom.unavailable("RISK_POLICY_VERSION_MISMATCH", evaluation.evaluatedAt());
        }
        BigDecimal limit = daily ? evaluation.dailyLossLimit() : evaluation.totalDrawdownLimit();
        BigDecimal used = daily ? evaluation.dailyLossUsed() : evaluation.totalDrawdownUsed();
        BigDecimal remaining = daily ? evaluation.dailyLossRemaining() : evaluation.totalDrawdownRemaining();
        if (limit == null || used == null || remaining == null) {
            return RiskHeadroom.unavailable("RISK_HEADROOM_FACTS_INCOMPLETE", evaluation.evaluatedAt());
        }
        return new RiskHeadroom(RiskHeadroom.Status.AVAILABLE, limit, used, remaining,
                evaluation.evaluatedAt(), "");
    }
}
