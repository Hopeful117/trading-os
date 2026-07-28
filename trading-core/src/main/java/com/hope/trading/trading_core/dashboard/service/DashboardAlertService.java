package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.dashboard.model.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardAlertService {
    public List<DashboardAlert> build(
            List<OpenPositionDashboardView> positions,
            DashboardFreshness freshness,
            RiskDashboardSummary risk,
            boolean equityDivergent
    ) {
        List<DashboardAlert> alerts = new ArrayList<>();
        Instant now = freshness.calculatedAt();

        for (OpenPositionDashboardView position : positions) {
            if (position.protectionStatus() == PositionProtectionStatus.MISSING_STOP_LOSS) {
                alerts.add(alert(
                        "MISSING_STOP_LOSS", DashboardAlertSeverity.WARNING,
                        "Position non protégée", "Aucun stop loss n’est défini.",
                        position, now
                ));
            }
            if (position.currentPrice() == null) {
                alerts.add(alert(
                        "MARKET_PRICE_UNAVAILABLE", DashboardAlertSeverity.WARNING,
                        "Prix indisponible", "La position ne peut pas être valorisée.",
                        position, now
                ));
            } else if (isCloseToStopLoss(position)) {
                alerts.add(alert(
                        "NEAR_STOP_LOSS", DashboardAlertSeverity.CRITICAL,
                        "Stop loss proche", "Le prix courant est à moins de 1 % du stop loss.",
                        position, now
                ));
            }
            if (!position.marketTradable()) {
                alerts.add(alert(
                        "MARKET_NOT_TRADABLE", DashboardAlertSeverity.WARNING,
                        "Marché non négociable", "Le marché est actuellement fermé ou indisponible.",
                        position, now
                ));
            }
        }

        if (freshness.brokerDataStale()) {
            alerts.add(global(
                    "BROKER_DATA_STALE", DashboardAlertSeverity.CRITICAL,
                    "Données broker obsolètes", "Les données du compte dépassent le seuil de fraîcheur.", now
            ));
        }
        if (freshness.marketDataStale()) {
            alerts.add(global(
                    "MARKET_DATA_STALE", DashboardAlertSeverity.WARNING,
                    "Prix trop anciens", "Au moins un prix dépasse le seuil de fraîcheur.", now
            ));
        }
        if (risk.status() == RiskStatus.BREACHED) {
            alerts.add(global(
                    "RISK_LIMIT_REACHED", DashboardAlertSeverity.CRITICAL,
                    "Limite de risque atteinte", "Une ou plusieurs règles de risque sont dépassées.", now
            ));
        } else if (risk.status() == RiskStatus.WARNING) {
            alerts.add(global(
                    "RISK_LIMIT_NEAR", DashboardAlertSeverity.WARNING,
                    "Limite de risque proche", "Une ou plusieurs règles dépassent 80 % de leur limite.", now
            ));
        }
        if (equityDivergent) {
            alerts.add(global(
                    "EQUITY_DIVERGENCE", DashboardAlertSeverity.WARNING,
                    "Divergence d’equity", "L’equity broker diffère de l’equity calculée.", now
            ));
        }
        return List.copyOf(alerts);
    }

    private boolean isCloseToStopLoss(OpenPositionDashboardView position) {
        if (position.stopLoss() == null || position.currentPrice().signum() == 0) {
            return false;
        }
        return position.currentPrice().subtract(position.stopLoss()).abs()
                .divide(position.currentPrice().abs(), 8, java.math.RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("0.01")) <= 0;
    }

    private DashboardAlert alert(
            String code, DashboardAlertSeverity severity, String title, String message,
            OpenPositionDashboardView position, Instant occurredAt
    ) {
        return new DashboardAlert(
                code, severity, title, message,
                position.marketId(), position.positionId(), occurredAt
        );
    }

    private DashboardAlert global(
            String code, DashboardAlertSeverity severity, String title,
            String message, Instant occurredAt
    ) {
        return new DashboardAlert(code, severity, title, message, null, null, occurredAt);
    }
}
