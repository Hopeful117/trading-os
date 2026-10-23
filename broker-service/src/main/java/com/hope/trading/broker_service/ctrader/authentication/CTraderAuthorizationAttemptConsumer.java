package com.hope.trading.broker_service.ctrader.authentication;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class CTraderAuthorizationAttemptConsumer {
    private final CTraderAuthorizationAttemptRepository attempts;

    public CTraderAuthorizationAttemptConsumer(CTraderAuthorizationAttemptRepository attempts) {
        this.attempts = attempts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID consume(String stateHash, Instant now) {
        CTraderAuthorizationAttempt attempt = attempts.findByStateHashForUpdate(stateHash)
                .orElseThrow(() -> new CTraderAuthenticationException("Authorization state is invalid"));
        try {
            attempt.consume(now);
        } catch (CTraderAuthenticationException exception) {
            if (attempt.status() == CTraderAuthorizationAttemptStatus.EXPIRED) {
                attempts.saveAndFlush(attempt);
            }
            throw exception;
        }
        attempts.saveAndFlush(attempt);
        return attempt.id();
    }
}
