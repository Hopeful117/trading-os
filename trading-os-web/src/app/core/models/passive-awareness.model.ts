export interface PassiveAwarenessResponse {
  opportunityId: string;
  opportunityVersion: number;
  current: boolean;
  diagnostics: string[];
  lifecycleState: string;
  presentationState: 'PASSIVE_CANDIDATE' | 'NON_POSITIVE';
  instrument: string;
  direction: string;
  scenario: string;
  timeframe: string;
  origin: string;
  marketId: string;
  provider: string | null;
  productType: string | null;
  strategyMatchId: string;
  observationIds: string[];
  observationTimes: string[];
  thesisId: string;
  thesisVersion: number | null;
  sourceScanId: string;
  sourceScanMarketId: string;
  analysisExecutionId: string;
  evaluatedAt: string;
  sourceCutoff: string | null;
  validFrom: string;
  validUntil: string | null;
  createdAt: string;
}

export interface PassiveAwarenessPageResponse {
  items: PassiveAwarenessResponse[];
  page: number;
  size: number;
  total: number;
  truncated: boolean;
}

export interface PassiveAwarenessQueryParams {
  instrument?: string;
  provider?: string;
  productType?: string;
  currentOnly?: boolean;
  page?: number;
  size?: number;
}

export interface PaginationState {
  page: number;
  size: number;
  total: number;
  truncated: boolean;
}

export interface PassiveAwarenessItem extends PassiveAwarenessResponse {
  isCandidate: boolean;
  isExpired: boolean;
  isSuperseded: boolean;
  freshnessSummary: string;
  diagnosticCategory: string | null;
  diagnosticLabels: string[];
}

export type AwarenessFeedView =
  | { status: 'loading' }
  | { status: 'error'; httpStatus: number; code?: string }
  | { status: 'empty' }
  | { status: 'filtered-empty' }
  | { status: 'loaded'; items: PassiveAwarenessItem[]; pagination: PaginationState };

const SUPERSESSION_CODES = new Set(['OPPORTUNITY_SUPERSEDED', 'THESIS_SUPERSEDED']);

const DIAGNOSTIC_CATEGORIES: Array<{ codes: Set<string>; label: string }> = [
  {
    codes: new Set(['OPPORTUNITY_SUPERSEDED', 'THESIS_SUPERSEDED']),
    label: 'Superseded',
  },
  {
    codes: new Set(['THESIS_BLOCKING_CONTRADICTION']),
    label: 'Contradictory',
  },
  {
    codes: new Set([
      'OPPORTUNITY_OUTSIDE_VALIDITY',
      'THESIS_OUTSIDE_VALIDITY',
      'REQUIRED_EVIDENCE_EXPIRED',
    ]),
    label: 'Outside validity',
  },
  {
    codes: new Set([
      'REQUIRED_EVIDENCE_NON_POSITIVE',
      'REQUIRED_EVIDENCE_FRESHNESS_INVALID',
      'THESIS_FRESHNESS_INVALID',
      'STRATEGY_MATCH_FRESHNESS_INVALID',
    ]),
    label: 'Stale evidence',
  },
  {
    codes: new Set([
      'MARKET_IDENTITY_UNAVAILABLE',
      'MARKET_IDENTITY_MISSING',
      'MARKET_IDENTITY_MISMATCH',
    ]),
    label: 'Market unavailable',
  },
  {
    codes: new Set([
      'STRATEGY_MATCH_MISSING',
      'MARKET_THESIS_REFERENCE_UNAVAILABLE',
      'OBSERVATION_REFERENCE_UNAVAILABLE',
      'MARKET_THESIS_OPPORTUNITY_MISMATCH',
    ]),
    label: 'Reference missing',
  },
  {
    codes: new Set([
      'OPPORTUNITY_NOT_ACTIVE',
      'THESIS_NOT_CANDIDATE',
      'OBSERVATION_NOT_CURRENT',
    ]),
    label: 'No longer active',
  },
  {
    codes: new Set([
      'STRATEGY_MATCH_MARKET_MISMATCH',
      'STRATEGY_MATCH_DIRECTION_MISMATCH',
      'STRATEGY_MATCH_OBSERVATION_MISMATCH',
      'STRATEGY_MATCH_EXECUTION_MISMATCH',
      'PASSIVE_SCAN_MARKET_MISMATCH',
      'PASSIVE_PROVENANCE_INCOMPLETE',
    ]),
    label: 'Validation mismatch',
  },
];

export function mapDiagnosticCategory(diagnostics: string[]): string | null {
  if (diagnostics.length === 0) return null;
  for (const { codes, label } of DIAGNOSTIC_CATEGORIES) {
    if (diagnostics.some((d) => codes.has(d))) return label;
  }
  return 'Invalid';
}

export function mapDiagnosticLabels(diagnostics: string[]): string[] {
  return diagnostics.map((code) => {
    for (const { codes, label } of DIAGNOSTIC_CATEGORIES) {
      if (codes.has(code)) return label;
    }
    return code;
  });
}

export function formatFreshness(evaluatedAt: string, now = new Date()): string {
  const diffMs = now.getTime() - new Date(evaluatedAt).getTime();
  const diffMin = Math.floor(diffMs / 60_000);
  if (diffMin < 1) return 'just now';
  if (diffMin < 60) return `${diffMin} min ago`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return `${diffH} h ago`;
  return `${Math.floor(diffH / 24)} d ago`;
}

export function mapToPassiveAwarenessItem(
  r: PassiveAwarenessResponse,
  now = new Date(),
): PassiveAwarenessItem {
  return {
    ...r,
    isCandidate: r.presentationState === 'PASSIVE_CANDIDATE',
    isExpired: r.validUntil !== null && now > new Date(r.validUntil),
    isSuperseded: r.diagnostics.some((d) => SUPERSESSION_CODES.has(d)),
    freshnessSummary: formatFreshness(r.evaluatedAt, now),
    diagnosticCategory: mapDiagnosticCategory(r.diagnostics),
    diagnosticLabels: mapDiagnosticLabels(r.diagnostics),
  };
}

export function isFiltered(params: PassiveAwarenessQueryParams): boolean {
  return !!(params.currentOnly || params.instrument?.trim());
}

export function mapToFeedView(
  r: PassiveAwarenessPageResponse,
  params: PassiveAwarenessQueryParams,
  now = new Date(),
): AwarenessFeedView {
  if (r.total === 0) {
    return isFiltered(params) ? { status: 'filtered-empty' } : { status: 'empty' };
  }
  return {
    status: 'loaded',
    items: r.items.map((item) => mapToPassiveAwarenessItem(item, now)),
    pagination: { page: r.page, size: r.size, total: r.total, truncated: r.truncated },
  };
}
