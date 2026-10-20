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

export type ResearchIntegrityStatus = 'HEALTHY' | 'DEGRADED' | 'STALE' | 'NO_DATA_YET' | 'UNKNOWN';

export interface MarketAcquisitionIntegrity {
  status: ResearchIntegrityStatus;
  expectedCutoff: string | null;
  latestSnapshotCutoff: string | null;
  lagSeconds: number;
}

export interface OutcomeMaturationIntegrity {
  dueSnapshotCount: number;
  availableCount: number;
  pendingCount: number;
  incompleteCount: number;
  unavailableCount: number;
  overdueCount: number;
  latestComputedAt: string | null;
}

export interface ResearchIntegrity {
  evaluatedAt: string;
  enabled: boolean;
  configuredMarkets: string[];
  observationCadence: string;
  status: ResearchIntegrityStatus;
  acquisitionByMarket: Record<string, MarketAcquisitionIntegrity>;
  maturationByHorizon: Record<string, OutcomeMaturationIntegrity>;
  overdueOutcomeCount: number;
  latestSuccessfulOutcomeComputedAt: string | null;
  timestampAnomalyCount: number;
  warnings: string[];
}
