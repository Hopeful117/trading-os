export type OpportunityStatus = 'DETECTED' | 'ANALYZED' | 'ACTIVE' | 'CONSUMED' | 'EXPIRED';

export type OpportunityDirection = 'LONG' | 'SHORT' | 'NEUTRAL';

export type OpportunityType = 'SCALPING' | 'INTRADAY' | 'SWING' | 'POSITIONAL';

export type OpportunityOrigin =
  'PASSIVE_SCAN' | 'ACTIVE_SCAN' | 'USER_REQUEST' | 'SYSTEM_REEVALUATION';

export interface OpportunityTrigger {
  condition: string;
  observedValue: string | null;
}

export interface OpportunitySetup {
  referencePrice: number | null;
  referencePriceAt: string | null;
  description: string;
  triggers: OpportunityTrigger[];
  detectedAt: string;
}

export interface OpportunityResponse {
  id: string;
  version: number;
  status: OpportunityStatus;
  instrument: string;
  direction: OpportunityDirection;
  scenario: string;
  timeframe: string;
  type: OpportunityType;
  origin: OpportunityOrigin;
  score: number;
  scoreSemantics: 'BUSINESS_PRIORITY_NOT_PROBABILITY';
  explanation: string;
  observationIds: string[];
  aiAnalysisIds: string[];
  evaluatedAt: string;
  validFrom: string;
  validUntil: string | null;
  createdAt: string;
  strategyMatchId: string | null;
  marketId?: string | null;
  accountId?: string | null;
  sourceScanId?: string | null;
  sourceScanMarketId?: string | null;
  analysisExecutionId?: string | null;
  marketThesis?: MarketThesisReference | null;
  thesis?: MarketThesisResponse | null;
  setup?: OpportunitySetup | null;
  ranking?: OpportunityRankingResponse | null;
}

export interface OpportunityPageResponse {
  items: OpportunityResponse[];
  page: number;
  size: number;
  total: number;
}
import {
  EvidenceQualityDimension,
  EvidenceQualityDimensionState,
  EvidenceQualityReason,
  EvidenceQualityStatus,
  MarketThesisReference,
  MarketThesisResponse,
} from './market-thesis.model';

export type OpportunityRankingOutcome = 'RANKED' | 'EXCLUDED';

export interface OpportunityRankingResponse {
  policyVersion: string;
  outcome: OpportunityRankingOutcome;
  position: number | null;
  rationale: OpportunityRankingRationale | null;
  exclusionCode: string | null;
  exclusionExplanation: string | null;
}

export interface OpportunityRankingRationale {
  policyVersion: string;
  evidenceQualityPolicyVersion: string;
  qualityStatus: EvidenceQualityStatus;
  dimensions: OpportunityRankingDimensionDecision[];
  reasons: EvidenceQualityReason[];
  evidenceQualityFingerprint: string;
  thesisInputFingerprint: string;
  orderingExplanation: string;
}

export interface OpportunityRankingDimensionDecision {
  dimension: EvidenceQualityDimension;
  state: EvidenceQualityDimensionState;
  precedence: number;
}
