export type TrendContextOperationalStatus =
  'AVAILABLE' | 'STALE' | 'UNAVAILABLE' | 'FAILED' | 'IN_PROGRESS' | 'MISSING' | string;

export type TrendContextAssessmentValidity = 'VALID' | 'HISTORICAL' | 'EXPIRED' | 'NONE' | string;
export type TrendContextRole = 'BIAS' | 'SETUP' | 'TRIGGER';
export type TrendDirection = 'UP' | 'DOWN' | 'NEUTRAL' | 'UNKNOWN';
export type TrendRegime = 'TRENDING' | 'TRANSITIONING' | 'NON_DIRECTIONAL' | 'UNKNOWN';
export type TrendPhase = 'DIRECTIONAL' | 'PULLBACK' | 'EXTENDED' | 'TRANSITION' | 'UNDETERMINED';
export type TrendAttention =
  'UNKNOWN' | 'NO_SETUP' | 'WATCH' | 'CONTEXTUALLY_ATTRACTIVE' | 'CONTEXTUALLY_DANGEROUS';
export type TrendTimeframeAlignment =
  | 'ALIGNED_UP'
  | 'ALIGNED_DOWN'
  | 'PULLBACK_WITHIN_UP_BIAS'
  | 'PULLBACK_WITHIN_DOWN_BIAS'
  | 'BIAS_TRANSITION'
  | 'CONFLICTING'
  | 'INSUFFICIENT_DIRECTION'
  | 'INSUFFICIENT_BIAS'
  | 'TRIGGER_CONTRADICTION'
  | 'TRIGGER_UNAVAILABLE';

export type BreakStatus =
  | 'NONE'
  | 'CONFIRMED_BEARISH_BREAK'
  | 'CONFIRMED_BULLISH_BREAK'
  | 'UNCONFIRMED_BEARISH_BREAK'
  | 'UNCONFIRMED_BULLISH_BREAK';
export type SwingType = 'HIGH' | 'LOW';
export type SwingRelation = 'HH' | 'LH' | 'EQ_HIGH' | 'HL' | 'LL' | 'EQ_LOW';

export interface TrendContextEvidenceReference {
  role: TrendContextRole;
  interval: string;
  ruleId: string;
  ruleVersion: string;
  profileVersion: string;
  inputFingerprint: string;
  cutOffAt: string;
  sourceIds: string[];
  from: string | null;
  to: string | null;
  key: string;
}

export interface TrendContextCandle {
  provider: string;
  symbol: string;
  interval: string;
  openTime: string;
  closeTime: string;
  open: number | null;
  high: number | null;
  low: number | null;
  close: number | null;
  volume: number | null;
  closed: boolean;
  synthetic: boolean;
  sourceId: string;
  sourceOccurredAt: string | null;
  fetchedAt: string;
}

export interface ConfirmedSwing {
  role: TrendContextRole;
  type: SwingType;
  index: number;
  pivotTime: string;
  price: number;
  confirmationTime: string;
  pivotSourceId: string;
  confirmationSourceId: string;
  suppressed: boolean;
  suppressionReason: string | null;
  evidence: TrendContextEvidenceReference;
}

export interface ProtectedLevel {
  type: SwingType;
  price: number;
  source: ConfirmedSwing;
}

export interface StructuralBreak {
  status: BreakStatus;
  level: number | null;
  candle: TrendContextCandle | null;
  protectedLevel: ProtectedLevel | null;
  barsAfterBreak: number;
  reclaimCandle: TrendContextCandle | null;
  evidence: TrendContextEvidenceReference | null;
}

export interface TrendInvalidation {
  ruleId: string;
  thesisDirection: TrendDirection;
  protectedLevel: ProtectedLevel | null;
  candle: TrendContextCandle | null;
  level: number | null;
  evidence: TrendContextEvidenceReference | null;
}

export interface TrendEmaEvidence {
  period: number;
  current: number | null;
  slope: number | null;
  slopeClassification: string;
  available: boolean;
  evidence: TrendContextEvidenceReference | null;
}

export interface TrendAtrEvidence {
  period: number;
  current: number | null;
  baseline: number | null;
  ratio: number | null;
  abnormal: boolean;
  available: boolean;
  evidence: TrendContextEvidenceReference | null;
}

export interface TrendPullbackAssessment {
  direction: TrendDirection;
  qualifyingSwing: ConfirmedSwing | null;
  postPivotCandles: TrendContextCandle[];
  qualified: boolean;
  evidence: TrendContextEvidenceReference | null;
}

export interface TrendExtensionAssessment {
  available: boolean;
  direction: TrendDirection;
  distance: number | null;
  atrMultiple: number | null;
  extended: boolean;
  evidence: TrendContextEvidenceReference | null;
}

export interface StructuralLevel {
  type: SwingType;
  price: number;
  pivotTime: string;
  confirmationTime: string;
  protectedLevel: boolean;
  swing: ConfirmedSwing;
}

export interface TrendContextFinding {
  code: string;
  ruleId: string;
  role: TrendContextRole;
  message: string;
  evidence: TrendContextEvidenceReference[];
}

export interface TrendContextContradiction {
  code: string;
  message: string;
  evidence: TrendContextEvidenceReference[];
}

export interface TrendContextExclusion {
  code: string;
  role: TrendContextRole;
  detail: string;
  evidence: TrendContextEvidenceReference | null;
}

export interface TrendContextTimeframeAssessment {
  role: TrendContextRole;
  interval: string;
  direction: TrendDirection;
  regime: TrendRegime;
  phase: TrendPhase;
  swings: ConfirmedSwing[];
  suppressedSwings: ConfirmedSwing[];
  highRelation: SwingRelation | null;
  lowRelation: SwingRelation | null;
  protectedLevel: ProtectedLevel | null;
  structuralBreak: StructuralBreak;
  pullback: TrendPullbackAssessment;
  extension: TrendExtensionAssessment;
  ema: TrendEmaEvidence;
  atr: TrendAtrEvidence;
  levels: StructuralLevel[];
  invalidation: TrendInvalidation | null;
  fresh: boolean;
  findings: TrendContextFinding[];
  evidence: TrendContextEvidenceReference[];
}

export interface TrendContextAssessment {
  marketId: string;
  provider: string;
  symbol: string;
  assessmentAt: string;
  cutOffAt: string;
  inputFingerprint: string;
  profileId: string;
  profileVersion: string;
  ruleVersion: string;
  timeframes: Partial<Record<TrendContextRole, TrendContextTimeframeAssessment>>;
  alignment: TrendTimeframeAlignment;
  direction: TrendDirection;
  regime: TrendRegime;
  phase: TrendPhase;
  attention: TrendAttention;
  findings: TrendContextFinding[];
  contradictions: TrendContextContradiction[];
  exclusions: TrendContextExclusion[];
  invalidations: TrendInvalidation[];
  fingerprint: string;
}

export interface TrendContextReadModel {
  marketId: string;
  operationalStatus: TrendContextOperationalStatus;
  assessmentPresent: boolean;
  assessmentValidity: TrendContextAssessmentValidity;
  observationId: string | null;
  lineageId: string | null;
  observationVersion: number | null;
  observationStatus: string | null;
  validFrom: string | null;
  validUntil: string | null;
  assessment: TrendContextAssessment | null;
  lastSuccessfulAssessment: TrendContextAssessment | null;
}
