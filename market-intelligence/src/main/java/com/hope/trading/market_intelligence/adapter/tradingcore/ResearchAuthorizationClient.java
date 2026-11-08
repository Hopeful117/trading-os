package com.hope.trading.market_intelligence.adapter.tradingcore;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Set;
import java.util.UUID;

@FeignClient(name = "trading-core", contextId = "researchAuthorizationClient",
        configuration = ResearchAuthorizationFeignConfiguration.class)
public interface ResearchAuthorizationClient {
    @PostMapping("/internal/v1/agent/research/authorize")
    AuthorizationResponse authorize(@RequestBody AuthorizationRequest request);

    record AuthorizationRequest(UUID agentId, UUID credentialId, Integer credentialVersion,
                                UUID scopeId, Set<UUID> marketIds, String correlationId) { }
    record AuthorizationResponse(boolean allowed, String denialReason, UUID grantId,
                                 Long grantVersion, Long agentVersion, UUID ownerUserId,
                                 UUID credentialId, Integer credentialVersion, UUID agentId) { }
}
