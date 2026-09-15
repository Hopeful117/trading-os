export interface RiskProfileRuleSummary {
  ruleId: string;
  ruleVersion: string;
  category: string;
  severity: string;
  priority: number;
  maximumRatio: number;
}

export interface RiskProfileCatalogEntry {
  profileId: string;
  semanticVersion: string;
  policyId: string;
  policyVersion: string;
  authority: string;
  createdAt: string;
  provenance: string;
  rules: RiskProfileRuleSummary[];
}
