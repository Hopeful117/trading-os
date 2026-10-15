import { NewsAvailability } from './news-availability.model';

export type FinancialNewsImpact = 'LOW' | 'MEDIUM' | 'HIGH' | 'UNKNOWN';

export interface FinancialNewsItem {
  id: string;
  sourceName: string;
  sourceItemId: string;
  title: string;
  summary: string | null;
  canonicalUrl: string | null;
  publishedAt: string;
  publisher: string | null;
  categories: string[];
  currencies: string[];
  marketIds: string[];
  impact: FinancialNewsImpact;
  sourceUpdatedAt: string | null;
  fetchedAt: string;
  normalizationVersion: string;
}

export interface FinancialNewsQuery {
  from?: string;
  to?: string;
  marketId?: string;
  currency?: string;
  impact?: FinancialNewsImpact;
  limit?: number;
}

export interface FinancialNewsResponse {
  status: NewsAvailability;
  items: FinancialNewsItem[];
  fetchedAt: string | null;
  message: string | null;
  attribution: string | null;
}
