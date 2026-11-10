package com.hope.trading.trading_core.agent.application;

import com.hope.trading.trading_core.agent.api.ResearchScopeRequest;
import com.hope.trading.trading_core.agent.model.ResearchScope;
import com.hope.trading.trading_core.agent.model.HistoricalResearchConstraints;
import com.hope.trading.trading_core.agent.repository.ResearchScopeRepository;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.market_data.helper.MarketProvider;
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
    private final BrokerAccountRepository brokerAccounts;
    private final Clock clock;

    @Transactional
    public ResearchScope create(UUID ownerUserId, ResearchScopeRequest request) {
        if (ownerUserId == null || request == null || request.brokerAccountId() == null) {
            throw new AgentGrantException(HttpStatus.BAD_REQUEST.value(), "INVALID_RESEARCH_SCOPE",
                    "A broker account is required");
        }
        var brokerAccount = brokerAccounts.findByIdAndOwnerId(request.brokerAccountId(), ownerUserId)
                .orElseThrow(AgentNotFoundException::new);
        MarketProvider provider = providerFor(brokerAccount.providerRegistrationReference());
        var marketIds = markets.findAll().stream()
                .filter(market -> market.getProvider() == provider)
                .map(com.hope.trading.trading_core.market_data.dto.MarketResponse::getMarketId)
                .collect(java.util.stream.Collectors.toSet());
        if (marketIds.isEmpty()) {
            throw new AgentGrantException(HttpStatus.BAD_REQUEST.value(), "MARKET_OUTSIDE_BROKER_UNIVERSE",
                    "The selected broker has no markets in the market catalogue");
        }
        Instant now = clock.instant();
        if (request.expiresAt() != null && !request.expiresAt().isAfter(now)) {
            throw new AgentGrantException(HttpStatus.BAD_REQUEST.value(), "INVALID_EXPIRATION",
                    "expiresAt must be after the current time");
        }
        HistoricalResearchConstraints historical = request.historicalConstraints() == null ? null
                : new HistoricalResearchConstraints(
                request.historicalConstraints().earliestCutoff(), request.historicalConstraints().latestCutoff(),
                request.historicalConstraints().maxObservations(), request.historicalConstraints().maxExportRows(),
                request.historicalConstraints().maxExportBytes(),
                request.historicalConstraints().maxExecutionDurationSeconds(),
                request.historicalConstraints().allowedDatasetProfiles());
        return scopes.save(ResearchScope.create(ownerUserId, request.brokerAccountId(), marketIds,
                now, request.expiresAt(), historical));
    }

    private MarketProvider providerFor(UUID registrationReference) {
        if (UUID.fromString("7f6f5f8e-3d42-4a0d-9e11-4d8f2a7c6b01").equals(registrationReference)) {
            return MarketProvider.KRAKEN;
        }
        if (UUID.fromString("3d6c3f0a-8d2d-4f4d-9a4c-7a2e1b5c9012").equals(registrationReference)) {
            return MarketProvider.CTRADER;
        }
        throw new AgentGrantException(HttpStatus.BAD_REQUEST.value(), "UNKNOWN_BROKER_UNIVERSE",
                "The broker provider has no market universe mapping");
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
