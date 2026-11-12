package com.hope.trading.market_intelligence.application.research.histdata;

public record Ml01DukascopyH1AdmissibilityProfile(
        String name,
        String version,
        int minimumValidM1Count,
        int expectedM1Count,
        boolean firstMinuteRequired,
        boolean lastMinuteRequired
) {
    public static final Ml01DukascopyH1AdmissibilityProfile V1 =
            new Ml01DukascopyH1AdmissibilityProfile(
                    "ML01_DUKASCOPY_EXPLORATORY_V1", "v1", 55, 60, true, true);
    public static final Ml01DukascopyH1AdmissibilityProfile V1_59 =
            new Ml01DukascopyH1AdmissibilityProfile(
                    "ML01_DUKASCOPY_SENSITIVITY_59", "v1", 59, 60, true, true);
    public static final Ml01DukascopyH1AdmissibilityProfile V1_60 =
            new Ml01DukascopyH1AdmissibilityProfile(
                    "ML01_DUKASCOPY_SENSITIVITY_60", "v1", 60, 60, true, true);

    public Ml01DukascopyH1AdmissibilityProfile {
        if (name == null || name.isBlank() || version == null || version.isBlank()
                || minimumValidM1Count < 0 || expectedM1Count <= 0
                || minimumValidM1Count > expectedM1Count) {
            throw new IllegalArgumentException("invalid Dukascopy ML-01 admissibility profile");
        }
    }
}
