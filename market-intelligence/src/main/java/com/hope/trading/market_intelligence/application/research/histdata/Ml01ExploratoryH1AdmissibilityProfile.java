package com.hope.trading.market_intelligence.application.research.histdata;

public record Ml01ExploratoryH1AdmissibilityProfile(
        String name,
        String version,
        int minimumValidM1Count,
        int expectedM1Count,
        boolean firstMinuteRequired,
        boolean lastMinuteRequired
) {
    public static final Ml01ExploratoryH1AdmissibilityProfile V1 =
            new Ml01ExploratoryH1AdmissibilityProfile(
                    "ML01_HISTDATA_EXPLORATORY_V1", "v1", 55, 60, true, true);

    public Ml01ExploratoryH1AdmissibilityProfile {
        if (name == null || name.isBlank() || version == null || version.isBlank()
                || minimumValidM1Count < 0 || expectedM1Count <= 0
                || minimumValidM1Count > expectedM1Count) {
            throw new IllegalArgumentException("invalid ML-01 exploratory admissibility profile");
        }
    }
}
