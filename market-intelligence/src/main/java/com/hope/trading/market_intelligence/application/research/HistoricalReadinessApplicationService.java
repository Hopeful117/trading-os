package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.adapter.tradingcore.ResearchAuthorizationClient;
import com.hope.trading.market_intelligence.security.MiAgentPrincipal;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HistoricalReadinessApplicationService {
    private final ResearchAuthorizationClient authorization;
    private final HistoricalReadinessService readiness;

    public HistoricalReadinessApplicationService(ResearchAuthorizationClient authorization,
                                                 HistoricalReadinessService readiness) {
        this.authorization = authorization;
        this.readiness = readiness;
    }

    public HistoricalReadinessResponse inspect(MiAgentPrincipal agent, UUID scopeId,
                                               HistoricalReadinessRequest request, String correlationId) {
        var decision = authorization.authorize(new ResearchAuthorizationClient.AuthorizationRequest(
                agent.agentId(), agent.credentialId(), agent.credentialVersion(), scopeId, request.marketIds(),
                correlationId, "research:inspect", request.cutoffStart(), request.cutoffEnd(),
                request.datasetProfile(), request.maxObservations(), 0, 0, 0));
        if (decision == null || !decision.allowed()) {
            throw new HistoricalResearchException(403, "RESEARCH_NOT_AUTHORIZED", "research readiness is not authorized");
        }
        return readiness.inspect(request, correlationId);
    }
}
