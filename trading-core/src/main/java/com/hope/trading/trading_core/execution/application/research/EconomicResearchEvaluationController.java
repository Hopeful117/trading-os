package com.hope.trading.trading_core.execution.application.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.execution.domain.economic.ExplicitCost;
import com.hope.trading.trading_core.execution.domain.economic.TransactionCosts;
import com.hope.trading.trading_core.execution.domain.economic.research.EconomicResearchResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/economic-research")
public final class EconomicResearchEvaluationController {
    private final EconomicResearchCandidateJsonlImporter importer;
    private final HistoricalEconomicResearchEvaluationService evaluation;
    private final EconomicResearchExportService export;

    public EconomicResearchEvaluationController(ObjectMapper mapper, HistoricalEconomicResearchEvaluationService evaluation) {
        this.importer = new EconomicResearchCandidateJsonlImporter(mapper);
        this.evaluation = evaluation;
        this.export = new EconomicResearchExportService(
                new com.hope.trading.trading_core.execution.domain.economic.research.EconomicResearchCanonicalSerializer(mapper));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<EvaluationResponse> evaluate(@RequestBody EvaluationRequest request) {
        var candidates = importer.importLines(request.candidateJsonl());
        if (candidates.stream().anyMatch(candidate ->
                !candidate.transactionCostModelVersion().equals(request.transactionCostModelVersion()))) {
            throw new IllegalArgumentException("transactionCostModelVersion must match every candidate");
        }
        validateFeeContext(request.brokerAccountId(), request.provider(), request.productType());
        TransactionCosts costs = new TransactionCosts(java.math.BigDecimal.ZERO, ExplicitCost.unavailable(),
                ExplicitCost.unavailable(),
                ExplicitCost.notApplicable(), null, request.transactionCostModelVersion());
        List<EconomicResearchResult> results = candidates.stream()
                .map(candidate -> request.brokerAccountId() == null || request.provider() == null
                        || request.productType() == null
                        ? evaluation.evaluate(candidate, costs, Instant.parse(request.researchCutoff()))
                        : evaluation.evaluate(candidate, request.brokerAccountId(), request.provider(),
                        request.productType(), costs, Instant.parse(request.researchCutoff())))
                .toList();
        var bundle = export.render(candidates, results);
        return ResponseEntity.ok(new EvaluationResponse(bundle.candidateJsonl(), bundle.resultJsonl(),
                bundle.manifestJson(), bundle.readme(), bundle.manifest().fingerprint()));
    }

    private void validateFeeContext(UUID brokerAccountId, String provider, String productType) {
        boolean any = brokerAccountId != null || provider != null || productType != null;
        boolean complete = brokerAccountId != null && provider != null && !provider.isBlank()
                && productType != null && !productType.isBlank();
        if (any && !complete) throw new IllegalArgumentException("brokerAccountId, provider and productType must be supplied together");
    }

    public record EvaluationRequest(String candidateJsonl, String researchCutoff,
                                     String transactionCostModelVersion, UUID brokerAccountId,
                                     String provider, String productType) {
        public EvaluationRequest(String candidateJsonl, String researchCutoff, String transactionCostModelVersion) {
            this(candidateJsonl, researchCutoff, transactionCostModelVersion, null, null, null);
        }
        public EvaluationRequest {
            if (candidateJsonl == null || candidateJsonl.isBlank()) throw new IllegalArgumentException("candidateJsonl is required");
            if (researchCutoff == null || researchCutoff.isBlank()) throw new IllegalArgumentException("researchCutoff is required");
            if (transactionCostModelVersion == null || transactionCostModelVersion.isBlank()) {
                throw new IllegalArgumentException("transactionCostModelVersion is required");
            }
        }
    }

    public record EvaluationResponse(String candidateJsonl, String resultJsonl, String manifestJson,
                                     String readme, String manifestFingerprint) {
    }
}
