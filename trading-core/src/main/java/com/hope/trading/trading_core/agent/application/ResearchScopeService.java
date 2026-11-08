package com.hope.trading.trading_core.agent.application;

import com.hope.trading.trading_core.agent.api.ResearchScopeRequest;
import com.hope.trading.trading_core.agent.model.ResearchScope;
import com.hope.trading.trading_core.agent.model.HistoricalResearchConstraints;
import com.hope.trading.trading_core.agent.repository.ResearchScopeRepository;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResearchScopeService {
    private final ResearchScopeRepository scopes;
    private final MarketDataClient markets;
    private final Clock clock;

    @Transactional
    public ResearchScope create(UUID ownerUserId, ResearchScopeRequest request) {
        if (ownerUserId == null || request == null || request.marketIds() == null
                || request.marketIds().size() != 2) {
            throw new AgentGrantException(HttpStatus.BAD_REQUEST.value(), "INVALID_RESEARCH_SCOPE",
                    "Exactly two distinct market IDs are required");
        }
        Instant now = clock.instant();
        if (request.expiresAt() != null && !request.expiresAt().isAfter(now)) {
            throw new AgentGrantException(HttpStatus.BAD_REQUEST.value(), "INVALID_EXPIRATION",
                    "expiresAt must be after the current time");
        }
        request.marketIds().forEach(id -> {
            if (markets.findById(id) == null) {
                throw new AgentNotFoundException();
            }
        });
        HistoricalResearchConstraints historical = request.historicalConstraints() == null ? null
                : new HistoricalResearchConstraints(
                request.historicalConstraints().earliestCutoff(), request.historicalConstraints().latestCutoff(),
                request.historicalConstraints().maxObservations(), request.historicalConstraints().maxExportRows(),
                request.historicalConstraints().maxExportBytes(),
                request.historicalConstraints().maxExecutionDurationSeconds(),
                request.historicalConstraints().allowedDatasetProfiles());
        return scopes.save(ResearchScope.create(ownerUserId, request.marketIds(), now, request.expiresAt(), historical));
    }

    @Transactional(readOnly = true)
    public List<ResearchScope> list(UUID ownerUserId) {
        return scopes.findAllByOwnerUserIdOrderByCreatedAtDesc(ownerUserId);
    }

    @Transactional
    public void revoke(UUID ownerUserId, UUID scopeId) {
        ResearchScope scope = scopes.findByIdAndOwnerUserId(scopeId, ownerUserId)
                .orElseThrow(AgentNotFoundException::new);
        scope.revoke(clock.instant());
    }

    @Transactional
    public void expireElapsed() {
        Instant now = clock.instant();
        scopes.findAllByStatusAndExpiresAtIsNotNull(com.hope.trading.trading_core.agent.model.ResearchScopeStatus.ACTIVE)
                .stream().filter(scope -> !scope.expiresAt().isAfter(now))
                .forEach(scope -> scope.expire(now));
    }
}
