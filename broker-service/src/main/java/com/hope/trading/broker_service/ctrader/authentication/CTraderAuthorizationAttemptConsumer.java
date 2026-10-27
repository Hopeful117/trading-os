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
        if (attempt.status() != CTraderAuthorizationAttemptStatus.INITIATED) {
            return attempt.id();
        }
        if (attempt.consumedAt() != null) {
            throw new CTraderAuthenticationException("Authorization attempt has already been consumed");
        }
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID attemptId, String code) {
        attempts.findById(attemptId).ifPresent(attempt -> {
            if (attempt.status() == CTraderAuthorizationAttemptStatus.INITIATED) {
                attempt.fail(code);
                attempts.saveAndFlush(attempt);
            }
        });
    }
}
