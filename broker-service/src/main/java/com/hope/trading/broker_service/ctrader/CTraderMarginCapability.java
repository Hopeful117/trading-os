package com.hope.trading.broker_service.ctrader;

import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.MarginCapability;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.MarginPreview;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.MarginPreviewRequest;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.ctrader.authentication.CTraderAuthenticationException;
import com.hope.trading.broker_service.ctrader.authentication.CTraderOpenApiClient;
import com.hope.trading.broker_service.ctrader.authentication.CTraderRequestSafetyGuard;
import com.hope.trading.broker_service.ctrader.authentication.CTraderToken;
import com.hope.trading.broker_service.ctrader.authentication.CTraderTokenCodec;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public final class CTraderMarginCapability implements MarginCapability {
    private final BrokerConnectionRepository connections;
    private final SecretReader secrets;
    private final CTraderOpenApiClient client;
    private final CTraderProperties properties;
    private final Clock clock;
    private final CTraderRequestSafetyGuard safety;

    public CTraderMarginCapability(BrokerConnectionRepository connections, SecretReader secrets,
                                   CTraderOpenApiClient client, CTraderProperties properties,
                                   Clock clock, CTraderRequestSafetyGuard safety) {
        this.connections = connections;
        this.secrets = secrets;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
        this.safety = safety;
    }

    @Override
    public MarginPreview preview(MarginPreviewRequest request) {
        if (request == null || request.brokerAccountId() == null) {
            throw new CTraderAuthenticationException("CTRADER_EXPECTED_MARGIN_REQUEST_INVALID");
        }
        var connection = connections.findByBrokerAccountId(request.brokerAccountId())
                .orElseThrow(() -> new CTraderAuthenticationException("cTrader connection is not available"));
        if (connection.provider() != BrokerProviderId.CTRADER
                || connection.externalAccountId() == null
                || connection.activeCredentialReference() == null) {
            throw new CTraderAuthenticationException("cTrader account is not authenticated and bound");
        }
        if (connection.providerEnvironment() == null
                || !connection.providerEnvironment().equalsIgnoreCase(properties.environment())) {
            throw new CTraderAuthenticationException("cTrader account environment does not match configuration");
        }
        if (connection.technicalStatus() == BrokerConnectionStatus.DISCONNECTED
                || connection.technicalStatus() == BrokerConnectionStatus.REVOKED
                || connection.technicalStatus() == BrokerConnectionStatus.REAUTHENTICATION_REQUIRED
                || connection.technicalStatus() == BrokerConnectionStatus.INVALID_CREDENTIALS
                || connection.technicalStatus() == BrokerConnectionStatus.INSUFFICIENT_PERMISSIONS
                || connection.technicalStatus() == BrokerConnectionStatus.TEMPORARILY_UNAVAILABLE) {
            throw new CTraderAuthenticationException("cTrader connection is not usable");
        }
        Instant now = clock.instant();
        try (CTraderRequestSafetyGuard.SnapshotLease ignored = safety.acquireExpectedMargin(connection.externalAccountId());
             PlainSecret secret = secrets.read(new CredentialReference(connection.activeCredentialReference()))) {
            CTraderToken token = CTraderTokenCodec.decode(secret.copyValue());
            if (!token.expiresAt().isAfter(now)) {
                throw new CTraderAuthenticationException("cTrader authentication has expired");
            }
            var result = client.expectedMargin(token, connection.externalAccountId(), request.instrument(),
                    request.side().name(), request.quantity());
            if (!request.instrument().equalsIgnoreCase(result.instrument())
                    || !request.side().name().equalsIgnoreCase(result.side())) {
                throw new CTraderAuthenticationException("CTRADER_EXPECTED_MARGIN_IDENTITY_MISMATCH");
            }
            return new MarginPreview(request.brokerAccountId(), result.instrument(), result.amount(),
                    result.currency(), "CTRADER:EXPECTED_MARGIN", result.sourceVersion(), result.observedAt());
        }
    }
}
