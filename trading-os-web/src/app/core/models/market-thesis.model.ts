export type MarketThesisState = 'NO_TRADE' | 'WATCH' | 'CANDIDATE';

export type EvidenceQualityStatus = 'SUPPORTED' | 'DEGRADED' | 'BLOCKED' | 'UNAVAILABLE';

export type EvidenceQualityDimension =
  | 'COMPLETENESS'
  | 'FRESHNESS'
  | 'PROVENANCE'
  | 'CONTRADICTION'
  | 'APPLICABILITY'
  | 'MISSING_POLICY';

export type EvidenceQualityDimensionState =
  | 'SATISFIED'
  | 'PARTIAL'
  | 'MISSING'
  | 'STALE'
  | 'CONTRADICTED'
  | 'UNAVAILABLE'
  | 'NOT_IMPLEMENTED'
  | 'NOT_APPLICABLE';

export interface MarketThesisReference {
  thesisId: string;
  thesisVersion: number;
}

export interface MarketThesisCondition {
  code: string;
  status: string;
  sourceReference: string;
  explanation: string;
}

export interface MarketThesisLevel {
  reference: string;
  role: string;
  timeframe: string;
  value: number;
  source: string;
  sourceReference: string;
  observedAt: string | null;
  confirmationAt: string | null;
  sourceCutoff: string | null;
}

export interface MarketThesisEvidence {
  evidenceId: string;
  role: string;
  availability: string;
  severity: string | null;
  source: string;
  reference: string;
  timeframe: string | null;
  explanation: string;
  observedAt: string;
  validUntil: string | null;
  provider: string | null;
  instrument: string | null;
  sourceFingerprint: string | null;
  ruleVersion: string | null;
  sourceCutoff: string | null;
  required: boolean;
}

export interface MarketThesisScenario {
  direction: 'LONG' | 'SHORT' | 'NONE';
  statement: string;
  confirmationConditions: MarketThesisCondition[];
  invalidationConditions: MarketThesisCondition[];
  relevantLevels: MarketThesisLevel[];
}

export interface MarketThesisResponse {
  id: string;
  lineageId: string;
  marketId: string;
  instrument: string;
  version: number;
  state: MarketThesisState;
  scenario: MarketThesisScenario | null;
  evidence: MarketThesisEvidence[];
  supportingEvidence: MarketThesisEvidence[];
  contradictingEvidence: MarketThesisEvidence[];
  missingEvidence: MarketThesisEvidence[];
  reasons: string[];
  evaluatedAt: string;
  sourceCutoff: string;
  validFrom: string;
  validUntil: string | null;
  analysisExecutionId: string | null;
  composerVersion: string;
  inputFingerprint: string;
  supersedes: string | null;
  evidenceQuality?: EvidenceQualityResponse | null;
}

export interface EvidenceQualityResponse {
  policyVersion: string;
  evaluatedAt: string;
  sourceCutoff: string;
  status: EvidenceQualityStatus;
  dimensions: EvidenceQualityDimensionResponse[];
  reasons: EvidenceQualityReason[];
  provenance: EvidenceQualityProvenance[];
  inputFingerprint: string;
  resultFingerprint: string;
}

export interface EvidenceQualityDimensionResponse {
  dimension: EvidenceQualityDimension;
  state: EvidenceQualityDimensionState;
  evidenceReferences: string[];
}

export interface EvidenceQualityReason {
  code: string;
  explanation: string;
}

export interface EvidenceQualityProvenance {
  evidenceId: string;
  source: string;
  provider: string | null;
  instrument: string | null;
  timeframe: string | null;
  observedAt: string;
  validUntil: string | null;
  sourceCutoff: string | null;
  sourceFingerprint: string | null;
  ruleVersion: string | null;
}
