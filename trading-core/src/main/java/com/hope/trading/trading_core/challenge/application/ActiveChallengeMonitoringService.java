package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Triggers existing Challenge/Risk reevaluation for eligible PAPER Challenges. */
@Service
public class ActiveChallengeMonitoringService {
    private static final Logger log = LoggerFactory.getLogger(ActiveChallengeMonitoringService.class);

    private final ChallengeInstanceRepository challenges;
    private final AccountRepository accounts;
    private final BrokerAccountRepository brokerAccounts;
    private final ChallengeReevaluationService reevaluation;
    private final ChallengeMonitoringProperties properties;
    private final Clock clock;

    public ActiveChallengeMonitoringService(ChallengeInstanceRepository challenges,
                                            AccountRepository accounts,
                                            BrokerAccountRepository brokerAccounts,
                                            ChallengeReevaluationService reevaluation,
                                            ChallengeMonitoringProperties properties,
                                            Clock clock) {
        this.challenges = challenges;
        this.accounts = accounts;
        this.brokerAccounts = brokerAccounts;
        this.reevaluation = reevaluation;
        this.properties = properties;
        this.clock = clock;
    }

    public MonitoringCycle monitor() {
        Instant startedAt = clock.instant();
        int discovered = 0;
        int evaluated = 0;
        int skipped = 0;
        int failed = 0;
        Instant afterStartedAt = null;
        UUID afterId = null;

        log.info("active_challenge_monitoring_cycle_started");
        while (true) {
            var page = PageRequest.of(0, properties.batchSize());
            var batch = afterStartedAt == null
                    ? challenges.findFirstPageByStatus(ChallengeStatus.ACTIVE, page)
                    : challenges.findNextPageByStatus(ChallengeStatus.ACTIVE, afterStartedAt, afterId, page);
            if (batch.isEmpty()) break;
            afterStartedAt = batch.getLast().startedAt();
            afterId = batch.getLast().id();

            for (var challenge : batch) {
                discovered++;
                try {
                    Account account = accounts.findById(challenge.accountId()).orElse(null);
                    if (account == null || account.getBrokerAccountId() == null) {
                        skipped++;
                        log.warn("active_challenge_monitoring_skipped challengeId={} reason=ACCOUNT_UNAVAILABLE",
                                challenge.id());
                        continue;
                    }
                    var broker = brokerAccounts.findById(account.getBrokerAccountId()).orElse(null);
                    if (broker == null || broker.executionMode() != ExecutionMode.PAPER) {
                        skipped++;
                        log.info("active_challenge_monitoring_skipped challengeId={} reason=NOT_PAPER",
                                challenge.id());
                        continue;
                    }
                    ChallengeStatus status = reevaluation.reevaluate(challenge.id());
                    if (status == null) {
                        skipped++;
                        log.info("active_challenge_monitoring_skipped challengeId={} reason=NO_LONGER_PAPER",
                                challenge.id());
                        continue;
                    }
                    evaluated++;
                    log.info("active_challenge_evaluated challengeId={} status={}", challenge.id(), status);
                } catch (RuntimeException failure) {
                    failed++;
                    log.error("active_challenge_monitoring_failed challengeId={}", challenge.id(), failure);
                }
            }
        }

        MonitoringCycle result = new MonitoringCycle(discovered, evaluated, skipped, failed,
                startedAt, clock.instant());
        log.info("active_challenge_monitoring_cycle_completed discovered={} evaluated={} skipped={} failed={} durationMs={}",
                discovered, evaluated, skipped, failed, result.durationMillis());
        return result;
    }

    public record MonitoringCycle(int discovered, int evaluated, int skipped, int failed,
                                  Instant startedAt, Instant completedAt) {
        public long durationMillis() {
            return completedAt.toEpochMilli() - startedAt.toEpochMilli();
        }
    }
}
