package com.hope.trading.trading_core.opportunitydecision.application;

import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.opportunitydecision.domain.OpportunityDecision;
import com.hope.trading.trading_core.opportunitydecision.infrastructure.OpportunityDecisionEntity;
import com.hope.trading.trading_core.opportunitydecision.infrastructure.OpportunityDecisionRepository;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OpportunityDecisionService {
    private final AccountRepository accounts;
    private final OpportunityDecisionRepository decisions;
    private final Clock clock;
    private final MarketIntelligenceTradePlanningClient marketIntelligence;

    public OpportunityDecisionService(AccountRepository accounts,
                                      OpportunityDecisionRepository decisions, Clock clock,
                                      MarketIntelligenceTradePlanningClient marketIntelligence) {
        this.accounts = accounts;
        this.decisions = decisions;
        this.clock = clock;
        this.marketIntelligence = marketIntelligence;
    }

    @Transactional
    public OpportunityDecision noTrade(UUID actorId, UUID accountId, UUID opportunityId,
                                       long opportunityVersion, UUID thesisId, long thesisVersion,
                                       String rationale, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw failure(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        }
        if (idempotencyKey.length() > 200) {
            throw failure(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID");
        }
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
        if (account.getUser() == null || !actorId.equals(account.getUser().getUserId())) {
            throw failure(HttpStatus.FORBIDDEN, "ACCOUNT_FORBIDDEN");
        }
        MarketIntelligenceTradePlanningClient.OpportunityAuthorityResponse authority;
        try {
            authority = marketIntelligence.authority(opportunityId, opportunityVersion);
        } catch (feign.FeignException.NotFound missing) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "OPPORTUNITY_NOT_FOUND");
        } catch (feign.FeignException unavailable) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "OPPORTUNITY_AUTHORITY_UNAVAILABLE");
        }
        if (!accountId.equals(authority.accountId())) {
            throw failure(HttpStatus.FORBIDDEN, "OPPORTUNITY_ACCOUNT_MISMATCH");
        }
        if (!thesisId.equals(authority.thesisId()) || thesisVersion != authority.thesisVersion()) {
            throw failure(HttpStatus.CONFLICT, "MARKET_THESIS_LINEAGE_MISMATCH");
        }
        var existing = decisions.findByActorIdAndIdempotencyKey(actorId, idempotencyKey);
        if (existing.isPresent()) {
            OpportunityDecision value = existing.get().toDomain();
            if (!value.accountId().equals(accountId) || !value.opportunityId().equals(opportunityId)
                    || value.opportunityVersion() != opportunityVersion
                    || !value.thesisId().equals(thesisId) || value.thesisVersion() != thesisVersion) {
                throw failure(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
            }
            return value;
        }
        var duplicate = decisions.findByAccountIdAndOpportunityIdAndOpportunityVersionAndDecision(
                accountId, opportunityId, opportunityVersion, OpportunityDecision.Decision.NO_TRADE.name());
        if (duplicate.isPresent()) return duplicate.get().toDomain();
        var value = new OpportunityDecision(UUID.randomUUID(), actorId, accountId, opportunityId,
                opportunityVersion, thesisId, thesisVersion, OpportunityDecision.Decision.NO_TRADE,
                rationale == null || rationale.isBlank() ? null : rationale.trim(), idempotencyKey,
                clock.instant());
        try {
            return decisions.saveAndFlush(OpportunityDecisionEntity.from(value)).toDomain();
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            return decisions.findByAccountIdAndOpportunityIdAndOpportunityVersionAndDecision(
                            accountId, opportunityId, opportunityVersion,
                            OpportunityDecision.Decision.NO_TRADE.name())
                    .map(OpportunityDecisionEntity::toDomain)
                    .orElseGet(() -> decisions.findByActorIdAndIdempotencyKey(actorId, idempotencyKey)
                            .map(OpportunityDecisionEntity::toDomain)
                            .orElseThrow(() -> race));
        }
    }

    private ResponseStatusException failure(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
