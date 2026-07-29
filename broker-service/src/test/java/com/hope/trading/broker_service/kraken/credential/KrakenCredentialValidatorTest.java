package com.hope.trading.broker_service.kraken.credential;

import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.credential.application.DefaultRequiredBrokerPermissionsPolicy;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.credential.domain.CredentialValidationOutcome;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KrakenCredentialValidatorTest {
    private static final Set<BrokerPermission> ALL_READ = Set.of(
            BrokerPermission.READ_ACCOUNT, BrokerPermission.READ_BALANCES, BrokerPermission.READ_POSITIONS,
            BrokerPermission.READ_ORDERS, BrokerPermission.READ_TRADE_HISTORY);

    @Test
    void mapsValidAndMissingPermissionsWithoutWithdrawalRequirement() {
        KrakenCredentialValidator valid = validator(new KrakenCredentialProbe.ProbeResult(
                ALL_READ, KrakenCredentialProbe.ProbeOutcome.SUCCESS));
        try (CredentialMaterial material = material()) {
            assertEquals(CredentialValidationOutcome.VALID, valid.validate(material).outcome());
        }
        KrakenCredentialValidator insufficient = validator(new KrakenCredentialProbe.ProbeResult(
                Set.of(BrokerPermission.READ_ACCOUNT), KrakenCredentialProbe.ProbeOutcome.SUCCESS));
        try (CredentialMaterial material = material()) {
            var result = insufficient.validate(material);
            assertEquals(CredentialValidationOutcome.INSUFFICIENT_PERMISSIONS, result.outcome());
            assertFalse(result.missingPermissions().contains(BrokerPermission.WITHDRAW));
        }
    }

    @Test
    void mapsProviderFailuresToStableOutcomes() {
        assertOutcome(KrakenCredentialProbe.ProbeOutcome.INVALID_CREDENTIALS,
                CredentialValidationOutcome.INVALID_CREDENTIALS);
        assertOutcome(KrakenCredentialProbe.ProbeOutcome.RATE_LIMITED,
                CredentialValidationOutcome.RATE_LIMITED);
        assertOutcome(KrakenCredentialProbe.ProbeOutcome.UNAVAILABLE,
                CredentialValidationOutcome.BROKER_UNAVAILABLE);
        assertOutcome(KrakenCredentialProbe.ProbeOutcome.UNEXPECTED_RESPONSE,
                CredentialValidationOutcome.UNEXPECTED_PROVIDER_RESPONSE);
    }

    private void assertOutcome(KrakenCredentialProbe.ProbeOutcome probe, CredentialValidationOutcome expected) {
        try (CredentialMaterial material = material()) {
            assertEquals(expected, validator(new KrakenCredentialProbe.ProbeResult(Set.of(), probe))
                    .validate(material).outcome());
        }
    }

    private KrakenCredentialValidator validator(KrakenCredentialProbe.ProbeResult result) {
        return new KrakenCredentialValidator(credentials -> result,
                new DefaultRequiredBrokerPermissionsPolicy(),
                Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC));
    }

    private CredentialMaterial material() {
        return new CredentialMaterial("FAKE_API_KEY_1234".toCharArray(),
                "RkFLRV9TRU5USU5FTF9TRUNSRVQ=".toCharArray(), null);
    }
}
