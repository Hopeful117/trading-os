package com.hope.trading.market_intelligence.application.ai;

import com.hope.trading.market_intelligence.adapter.tradingcore.TradingCoreAccountClient;
import com.hope.trading.market_intelligence.application.context.IntelligenceContextAssembler;
import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.ai.*;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public final class OpportunityInterpretationOrchestrationService {
    private static final List<ContextRequirement> CONTEXT_REQUIREMENTS = List.of(
            ContextRequirement.requiredPublic(ContextSectionType.MARKET_IDENTITY),
            ContextRequirement.requiredPublic(ContextSectionType.MARKET_SNAPSHOT),
            ContextRequirement.optionalPublic(ContextSectionType.HISTORICAL_OHLC)
    );

    private final TradingOpportunityRepository opportunities;
    private final MarketThesisRepository theses;
    private final TradingCoreAccountClient accounts;
    private final IntelligenceContextAssembler contextAssembler;
    private final OpportunityInterpretationModeSelector modeSelector;
    private final AnalysisExecutionService executions;

    public OpportunityInterpretationOrchestrationService(
            TradingOpportunityRepository opportunities,
            MarketThesisRepository theses,
            TradingCoreAccountClient accounts,
            IntelligenceContextAssembler contextAssembler,
            OpportunityInterpretationModeSelector modeSelector,
            AnalysisExecutionService executions) {
        this.opportunities = opportunities;
        this.theses = theses;
        this.accounts = accounts;
        this.contextAssembler = contextAssembler;
        this.modeSelector = modeSelector;
        this.executions = executions;
    }

    public OpportunityInterpretationResult request(
            UUID actorId,
            UUID opportunityId,
            long opportunityVersion,
            String idempotencyKey,
            String requestId,
            String traceId,
            Instant evaluationCutoff) {
        TradingOpportunity opportunity = opportunities.find(
                        new OpportunityId(opportunityId), new OpportunityVersion(opportunityVersion))
                .orElseThrow(() -> new OpportunityInterpretationRejectedException("OPPORTUNITY_VERSION_NOT_FOUND"));
        UUID accountId = opportunity.accountId().orElseThrow(
                () -> new OpportunityInterpretationRejectedException("OPPORTUNITY_ACCOUNT_MISSING"));
        if (evaluationCutoff != null) {
            throw new OpportunityInterpretationRejectedException("EVALUATION_CUTOFF_UNSUPPORTED", 400);
        }
        TradingCoreAccountClient.TradingCoreAccountResponse account = accounts.findOwnedAccount(accountId);
        if (account == null || !actorId.equals(account.userId())) {
            throw new OpportunityInterpretationRejectedException("OPPORTUNITY_NOT_AUTHORIZED");
        }

        MarketThesis thesis = opportunity.marketThesis()
                .flatMap(reference -> theses.findByIdAndVersion(reference.thesisId(), reference.thesisVersion()))
                .orElseThrow(() -> new OpportunityInterpretationRejectedException("MARKET_THESIS_NOT_FOUND"));
        UUID executionId = UUID.nameUUIDFromBytes(
                ("opportunity-interpretation:" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
        Instant cutoff = opportunity.evaluatedAt();
        IntelligenceAnalysisRequest contextRequest = new IntelligenceAnalysisRequest(
                executionId,
                opportunity.marketId().orElseThrow(() -> new OpportunityInterpretationRejectedException(
                        "MARKET_IDENTITY_MISSING")),
                AnalysisExecutionMode.PASSIVE,
                "OPPORTUNITY_INTERPRETATION",
                accountId,
                opportunity.sourceScanId().orElse(null),
                opportunity.sourceScanMarketId().orElse(null),
                cutoff,
                EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                thesis,
                null,
                TradeabilityEvidenceProfile.UNSUPPORTED);
        IntelligenceContext context = contextAssembler.assemble(contextRequest, CONTEXT_REQUIREMENTS);
        OpportunityVersionReference opportunityReference = new OpportunityVersionReference(
                opportunity.id().value(), opportunity.version().value());
        OpportunityInterpretationDecision selected = modeSelector.select(opportunityReference, cutoff);
        ContextPack pack = ContextPack.from(
                new ContextPackId(executionId), selected.mode(), ContextPackAuthorization.publicAi(),
                context, opportunityReference,
                opportunity.marketThesis().map(reference -> new com.hope.trading.market_intelligence.domain.opportunity.MarketThesisReference(
                        reference.thesisId(), reference.thesisVersion())).orElse(null));

        IntelligenceAnalysisRequest executionRequest = contextRequest.withInterpretationMetadata(
                new OpportunityInterpretationMetadata(
                        opportunityReference, selected.mode(), pack.id(), pack.fingerprint()));
        AnalysisExecution execution = executions.register(
                executionRequest,
                new IdempotencyKey(idempotencyKey),
                requestId == null ? executionId.toString() : requestId,
                traceId == null ? executionId.toString() : traceId);
        return new OpportunityInterpretationResult(execution, selected.mode(), selected.eligibility(), pack);
    }

    public record OpportunityInterpretationResult(
            AnalysisExecution execution,
            AiInterpretationMode mode,
            AiAnalysisEligibility eligibility,
            ContextPack contextPack
    ) {
    }
}
