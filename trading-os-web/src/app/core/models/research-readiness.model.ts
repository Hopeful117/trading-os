export interface S1Readiness {
  totalSignals: number;
  longSignals: number;
  shortSignals: number;
  aggregateRegimes: string[];
  replayChecks: number;
  replayAvailable: number;
  replayCoverage: number;
  feeChecks: number;
  feeAvailable: number;
  feeCoverage: number;
  ready: boolean;
  blockingReasons: string[];
}

export interface ResearchReadiness {
  enabled: boolean;
  configuredMarkets: string[];
  observationCadence: string;
  outcomeHorizons: string[];
  featureEvidencePolicy: string;
  latestSuccessfulEvidenceKnownAt: Record<string, string>;
  featureSnapshotCount: number;
  featureSnapshotsByMarket: Record<string, number>;
  earliestFeatureSnapshotCutoff: string | null;
  latestFeatureSnapshotCutoff: string | null;
  outcomeCountsByHorizonAndStatus: Record<string, number>;
  eligibleOutcomeCount: number;
  pendingOutcomeCount: number;
  approximateAvailableResearchPopulation: number;
  s1: S1Readiness;
}
