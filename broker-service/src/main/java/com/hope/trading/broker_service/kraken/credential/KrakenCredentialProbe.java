package com.hope.trading.broker_service.kraken.credential;

import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;

import java.util.Set;

public interface KrakenCredentialProbe {
    ProbeResult probe(CredentialMaterial credentials);

    record ProbeResult(Set<BrokerPermission> granted, ProbeOutcome outcome) {
        public ProbeResult {
            granted = Set.copyOf(granted);
        }
    }

    enum ProbeOutcome {
        SUCCESS,
        INVALID_CREDENTIALS,
        PERMISSION_DENIED,
        RATE_LIMITED,
        UNAVAILABLE,
        UNEXPECTED_RESPONSE
    }
}
