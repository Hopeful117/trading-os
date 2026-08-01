package com.hope.trading.trading_core.risk.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.hope.trading.trading_core.risk.application.RiskEvaluationModels.Response;
import com.hope.trading.trading_core.risk.application.RiskEvaluationModels.Trace;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class RiskAcknowledgmentOutboxPersistenceTest {
    @Autowired RiskPersistence persistence;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private final UUID actorId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID evaluationId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-08-01T12:00:00Z");

    @BeforeEach
    void createOwnerAndAccount() {
        jdbc.update("insert into users(user_id,username,password,email,role) values(?,?,?,?,?)",
                actorId, "outbox-" + actorId, "x", actorId + "@test.local", "ROLE_USER");
        jdbc.update("insert into accounts(account_id,name,base_currency,peak_equity,equity,user_id) values(?,?,?,?,?,?)",
                accountId, "main", "USD", 0, 0, actorId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from risk_acknowledgment_outbox where evaluation_id=?", evaluationId);
        jdbc.update("delete from risk_evaluation where id=?", evaluationId);
        jdbc.update("delete from accounts where account_id=?", accountId);
        jdbc.update("delete from users where user_id=?", actorId);
    }

    @Test
    void officialEvaluationAndAcknowledgmentStateRollbackAtomically() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            persistApprovedEvaluationAndOutbox();
            status.setRollbackOnly();
        });

        assertThat(count("risk_evaluation")).isZero();
        assertThat(count("risk_acknowledgment_outbox")).isZero();
    }

    @Test
    void claimLeasePreservesExactIdentityAndSupportsDurableExplicitRetry() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> persistApprovedEvaluationAndOutbox());

        var first = persistence.claimAcknowledgment(evaluationId, now, true).orElseThrow();

        assertThat(first.tradePlanId()).isEqualTo(planId);
        assertThat(first.tradePlanVersion()).isEqualTo(7);
        assertThat(first.evaluationId()).isEqualTo(evaluationId);
        assertThat(persistence.claimAcknowledgment(evaluationId, now, true)).isEmpty();

        persistence.acknowledgmentFailed(evaluationId, first.claimToken(), now, "remote unavailable");
        var retry = persistence.claimAcknowledgment(evaluationId, now, true).orElseThrow();
        persistence.acknowledgmentDelivered(evaluationId, retry.claimToken(), now);

        assertThat(persistence.claimAcknowledgment(evaluationId, now, true)).isEmpty();
        assertThat(jdbc.queryForObject("select status from risk_acknowledgment_outbox where evaluation_id=?",
                String.class, evaluationId)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("select attempt_count from risk_acknowledgment_outbox where evaluation_id=?",
                Integer.class, evaluationId)).isEqualTo(2);
    }

    private void persistApprovedEvaluationAndOutbox() {
        Response response = new Response(evaluationId, planId, 7, accountId, "COMPLETED", "APPROVED", true,
                List.of(), List.of(), Map.of(), now,
                new Trace(UUID.randomUUID(), "test", Map.of(), Map.of(), Map.of()));
        persistence.evaluation(evaluationId, actorId, "outbox-key", planId, 7, accountId, now,
                "COMPLETED", "APPROVED", null, response, response);
        persistence.acknowledgment(evaluationId, planId, 7, "APPROVED", now, now);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where "
                + ("risk_evaluation".equals(table) ? "id" : "evaluation_id") + "=?", Integer.class, evaluationId);
    }
}
