package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hope.trading.market_intelligence.application.port.ActiveScanRepository;
import com.hope.trading.market_intelligence.domain.scan.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 0243: passive provenance lives in the existing payload JSON; no migration. */
@SpringBootTest
@ActiveProfiles("test")
class PassiveCandidateProvenancePersistenceTest {
    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    @Autowired ActiveScanRepository scans;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void provenanceRoundTripsThroughPayload() {
        PassiveCandidateProvenance provenance = provenance();
        ActiveScan scan = scans.save(completedNoWork(provenance));

        ActiveScan reloaded = scans.findById(scan.scanId()).orElseThrow();

        assertThat(reloaded.passiveCandidate()).contains(provenance);
    }

    @Test
    void legacyPayloadWithoutProvenanceStillLoads() throws Exception {
        ActiveScan scan = scans.save(completedNoWork(provenance()));
        String payload = jdbc.queryForObject(
                "select payload from active_scans where scan_id = ?", String.class, scan.scanId());
        ObjectNode legacy = (ObjectNode) mapper.readTree(payload);
        legacy.remove("passiveCandidate");
        jdbc.update("update active_scans set payload = ? where scan_id = ?",
                mapper.writeValueAsString(legacy), scan.scanId());

        ActiveScan reloaded = scans.findById(scan.scanId()).orElseThrow();

        assertThat(reloaded.passiveCandidate()).isEmpty();
        assertThat(reloaded.scopeSnapshot()).isEqualTo(scan.scopeSnapshot());
    }

    private ActiveScan completedNoWork(PassiveCandidateProvenance provenance) {
        return ActiveScan.completedNoWork(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "investigate",
                "key-" + UUID.randomUUID(), "fingerprint",
                new ActiveScanScopeSnapshot(List.of(provenance.marketId()), List.of(provenance.marketId()),
                        List.of(), List.of(), NOW),
                NOW, provenance);
    }

    private PassiveCandidateProvenance provenance() {
        UUID marketId = UUID.randomUUID();
        return new PassiveCandidateProvenance(UUID.randomUUID(), 2, UUID.randomUUID(), List.of(UUID.randomUUID()),
                UUID.randomUUID(), 1, UUID.randomUUID(), marketId, UUID.randomUUID(), marketId, "KRAKEN", "SPOT",
                NOW.minusSeconds(60), NOW.minusSeconds(70), NOW.plusSeconds(240), NOW,
                List.of(new PassiveEvidenceAssessment(PassiveEvidenceAssessment.Dimension.FRESHNESS,
                        PassiveEvidenceAssessment.Status.INCOMPATIBLE_REACQUIRED, "OLDER_THAN_PT30S")));
    }
}
