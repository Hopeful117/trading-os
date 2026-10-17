package com.hope.trading.trading_core.execution.application.research;

import com.hope.trading.trading_core.execution.domain.economic.EconomicResult;
import com.hope.trading.trading_core.execution.domain.economic.research.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/** Writes a bounded economic bundle using semantic ordering and byte checksums. */
public final class EconomicResearchExportService {
    public static final String EXPORT_SCHEMA_VERSION = "economic-research-bundle-v1";
    public static final String CANDIDATES_FILE = "economic-intents.jsonl";
    public static final String RESULTS_FILE = "economic-results.jsonl";
    public static final String MANIFEST_FILE = "economic-dataset-manifest.json";
    public static final String README_FILE = "README.md";
    private final EconomicResearchCanonicalSerializer serializer;

    public EconomicResearchExportService(EconomicResearchCanonicalSerializer serializer) {
        this.serializer = Objects.requireNonNull(serializer, "serializer");
    }

    public EconomicResearchDatasetManifest export(List<EconomicResearchCandidate> candidates,
                                                   List<EconomicResearchResult> results, Path directory) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(results, "results");
        if (candidates.isEmpty()) throw new IllegalArgumentException("economic export cannot be empty");
        if (candidates.size() != results.size()) throw new IllegalArgumentException("every candidate needs one result disposition");
        if (Files.exists(directory)) throw new IllegalArgumentException("export directory already exists");
        Bundle bundle = render(candidates, results);
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(CANDIDATES_FILE), bundle.candidateJsonl(), StandardCharsets.UTF_8);
            Files.writeString(directory.resolve(RESULTS_FILE), bundle.resultJsonl(), StandardCharsets.UTF_8);
            Files.writeString(directory.resolve(MANIFEST_FILE), bundle.manifestJson() + "\n", StandardCharsets.UTF_8);
            Files.writeString(directory.resolve(README_FILE), bundle.readme(), StandardCharsets.UTF_8);
            return bundle.manifest();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot write economic research bundle", exception);
        }
    }

    public Bundle render(List<EconomicResearchCandidate> candidates, List<EconomicResearchResult> results) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(results, "results");
        if (candidates.isEmpty()) throw new IllegalArgumentException("economic export cannot be empty");
        if (candidates.size() != results.size()) throw new IllegalArgumentException("every candidate needs one result disposition");
        String datasetFingerprint = candidates.getFirst().datasetDefinition().fingerprint();
        if (candidates.stream().anyMatch(candidate ->
                !datasetFingerprint.equals(candidate.datasetDefinition().fingerprint()))) {
            throw new IllegalArgumentException("all candidates must belong to one dataset definition");
        }
        List<EconomicResearchCandidate> orderedCandidates = candidates.stream().sorted(candidateOrder()).toList();
        Map<String, EconomicResearchResult> byCandidate = results.stream().collect(Collectors.toUnmodifiableMap(
                EconomicResearchResult::candidateFingerprint, value -> value));
        List<EconomicResearchResult> orderedResults = orderedCandidates.stream().map(candidate -> {
            EconomicResearchResult result = byCandidate.get(candidate.fingerprint());
            if (result == null) throw new IllegalArgumentException("missing result for candidate " + candidate.fingerprint());
            return result;
        }).toList();
        String candidateJsonl = lines(orderedCandidates.stream().map(serializer::candidate).toList());
        String resultJsonl = lines(orderedResults.stream().map(serializer::result).toList());
        String candidatesSha = sha256(candidateJsonl);
        String resultsSha = sha256(resultJsonl);
        EconomicResearchDatasetManifest manifest = manifest(orderedCandidates, orderedResults, candidatesSha, resultsSha);
        return new Bundle(candidateJsonl, resultJsonl, serializer.manifest(manifest), readme(manifest), manifest);
    }

    private EconomicResearchDatasetManifest manifest(List<EconomicResearchCandidate> candidates,
                                                     List<EconomicResearchResult> results, String candidatesSha,
                                                     String resultsSha) {
        Map<String, Integer> execution = new TreeMap<>();
        Map<String, Integer> completeness = new TreeMap<>();
        Map<EconomicResearchResultDisposition, Integer> dispositions = new EnumMap<>(EconomicResearchResultDisposition.class);
        BigDecimal residual = BigDecimal.ZERO;
        int residualCount = 0;
        for (EconomicResearchResult result : results) {
            dispositions.merge(result.disposition(), 1, Integer::sum);
            EconomicResult economic = result.economicResult();
            if (economic != null) {
                if (economic.executionStatus() != null) execution.merge(economic.executionStatus().name(), 1, Integer::sum);
                if (economic.completeness() != null) completeness.merge(economic.completeness().name(), 1, Integer::sum);
                if (economic.residualExposure() != null && economic.residualExposure().signum() > 0) {
                    residual = residual.add(economic.residualExposure());
                    residualCount++;
                }
            }
        }
        List<String> populationMaterial = new ArrayList<>();
        populationMaterial.add(candidates.getFirst().datasetDefinition().fingerprint());
        populationMaterial.addAll(candidates.stream().map(EconomicResearchCandidate::fingerprint).sorted().toList());
        StringBuilder populationBytes = new StringBuilder();
        populationMaterial.forEach(value -> populationBytes.append(value.length()).append(':').append(value));
        String sourceBundleFingerprint = sha256(populationBytes.toString());
        return new EconomicResearchDatasetManifest(EXPORT_SCHEMA_VERSION, candidates.getFirst().datasetDefinition().fingerprint(),
                candidates.size(), results.size(), execution, completeness, dispositions, residual, residualCount,
                candidates.stream().map(EconomicResearchCandidate::fingerprint).toList(),
                results.stream().map(EconomicResearchResult::resultFingerprint).toList(), candidatesSha, resultsSha,
                sourceBundleFingerprint);
    }

    private Comparator<EconomicResearchCandidate> candidateOrder() {
        return Comparator.comparing((EconomicResearchCandidate value) -> value.featureSnapshot().marketId())
                .thenComparing(value -> value.featureSnapshot().evaluationCutoff())
                .thenComparing(value -> value.hypothesis().hypothesisId())
                .thenComparing(value -> value.hypothesis().hypothesisVersion())
                .thenComparing(EconomicResearchCandidate::fingerprint);
    }

    private String lines(List<String> values) { return values.isEmpty() ? "" : String.join("\n", values) + "\n"; }

    private String sha256(String value) {
        return HexFormat.of().formatHex(digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] digest(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is required", exception); }
    }

    private String readme(EconomicResearchDatasetManifest manifest) {
        return "# Economic Research Bundle\n\n"
                + "Trading Core is authoritative for validation, historical replay, costs, and EconomicResult.\n"
                + "Python may consume these artifacts read-only and must not recalculate accounting.\n\n"
                + "Manifest fingerprint: `" + manifest.fingerprint() + "`\n";
    }

    public record Bundle(String candidateJsonl, String resultJsonl, String manifestJson, String readme,
                         EconomicResearchDatasetManifest manifest) {
    }
}
