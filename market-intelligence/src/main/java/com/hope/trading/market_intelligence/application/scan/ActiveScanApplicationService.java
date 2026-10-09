package com.hope.trading.market_intelligence.application.scan;

import com.hope.trading.market_intelligence.adapter.web.ActiveScanSummary;
import com.hope.trading.market_intelligence.application.port.ActiveScanRepository;
import com.hope.trading.market_intelligence.domain.scan.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ActiveScanApplicationService {
    private final ActiveScanRepository scans;
    private final ActiveScanFingerprintFactory fingerprints;
    private final ActiveScanCreationTransaction creation;
    private final ActiveScanReconciliationService reconciliation;

    public ActiveScanApplicationService(
            ActiveScanRepository scans,
            ActiveScanFingerprintFactory fingerprints,
            ActiveScanCreationTransaction creation,
            ActiveScanReconciliationService reconciliation
    ) {
        this.scans = scans;
        this.fingerprints = fingerprints;
        this.creation = creation;
        this.reconciliation = reconciliation;
    }

    public ActiveScan create(CreateActiveScanCommand command) {
        String fingerprint = fingerprints.fingerprint(
                command.actorId(),
                command.accountId(),
                command.objective(),
                command.requestedMarketIds(),
                command.scopeMode()
        );
        ActiveScan existing = scans.findByActorIdAndIdempotencyKey(
                command.actorId(),
                command.idempotencyKey()
        ).orElse(null);
        if (existing != null) {
            if (!existing.requestFingerprint().equals(fingerprint)) {
                throw new ActiveScanException(
                        "IDEMPOTENCY_CONFLICT",
                        "Idempotency-Key is already bound to another active scan request",
                        409
                );
            }
            return creation.replay(existing);
        }
        try {
            return creation.create(command, fingerprint);
        } catch (DataIntegrityViolationException race) {
            ActiveScan winner = scans.findByActorIdAndIdempotencyKey(
                    command.actorId(), command.idempotencyKey()
            ).orElseThrow(() -> race);
            if (!winner.requestFingerprint().equals(fingerprint)) {
                throw idempotencyConflict();
            }
            return creation.replay(winner);
        }
    }

    @Transactional(readOnly = true)
    public ActiveScanView findOwned(UUID actorId, UUID scanId) {
        ActiveScan scan = scans.findByActorIdAndScanId(actorId, scanId)
                .orElseThrow(() -> new ActiveScanException(
                        "ACTIVE_SCAN_NOT_FOUND",
                        "Active scan not found: " + scanId,
                        404
                ));
        return new ActiveScanView(scan, scans.findMarketsByScanId(scanId));
    }

    public ActiveScanResultProjection findOwnedProjection(UUID actorId, UUID scanId) {
        return reconciliation.reconcileOwned(actorId, scanId);
    }

    @Transactional(readOnly = true)
    public List<ActiveScanSummary> findRecentSummary(UUID actorId, int limit) {
        return scans.findRecentByActorId(actorId, limit).stream()
                .map(ActiveScanSummary::from)
                .toList();
    }

    private ActiveScanException idempotencyConflict() {
        return new ActiveScanException(
                "IDEMPOTENCY_CONFLICT",
                "Idempotency-Key is already bound to another active scan request",
                409
        );
    }

    public record ActiveScanView(
            ActiveScan scan,
            List<ActiveScanMarket> markets
    ) {
        public ActiveScanView {
            markets = List.copyOf(markets);
        }
    }
}
