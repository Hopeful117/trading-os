export type MarketThesisState = 'NO_TRADE' | 'WATCH' | 'CANDIDATE';

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
}
