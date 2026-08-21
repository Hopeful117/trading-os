# Story 0012 — TradingOpportunity from StrategyMatch

## Changement d'autorité

AVANT : Observation → OhlcTrendObservationRule → TradingOpportunity.
APRÈS : Observation (preuve) → StrategyEvaluation → StrategyMatch → TradingOpportunity.

La règle OHLC legacy reste uniquement de la construction d'Observation ; elle ne
décide plus de l'existence d'une opportunité. Un seul chemin causal de
production existe : `MATCH → StrategyMatch → TradingOpportunity`.

## Modèle transactionnel (correction obligatoire appliquée)

Une seule transaction atomique T1 (`ProductionIntelligencePipeline.process`,
`@Transactional` REQUIRED) :

```
T1: Observation → StrategyEvaluation → StrategyMatch persist/reuse (REQUIRED)
    → TradingOpportunity create/reuse depuis le match → PipelineRun.complete
    → COMMIT ALL
```

- `StrategyMatchPersister` : propagation par défaut (REQUIRED), plus aucun
  REQUIRES_NEW ni afterCommit.
- Tout échec (match, opportunité, run) → rollback complet T1 ; pas d'orphan.
- Le chemin shadow Story 0011 est supprimé : `StrategyMatchRecorder`,
  `PendingStrategyMatchRecord` et le hook after-commit ont été retirés ;
  `ShadowStrategyParityMonitor` conserve uniquement les diagnostics de parité.

## Invariant un-match-une-opportunité

V1 ADR-034 : une lignée TradingOpportunity dérive d'exactement un StrategyMatch.

- Identité de lignée déterministe : UUIDv3-style dérivé du matchId
  (namespace dédié), jamais égal au matchId. Même match ⇒ même lignée ;
  retry/concurrent ⇒ aucune duplication (PK `(opportunity_id, version)` +
  business key match).
- `strategy_match_id` porté par chaque version de
  `trading_opportunity_versions` (attribution stable à travers le versioning,
  préservée par les transitions) + FK vers `strategy_matches(match_id)`
  sans cascade + index. Lignes historiques pré-0012 : NULL autorisé.
- Pas de colonne `pipeline_runs.strategy_match_id` (reconstructible via
  PipelineRun → Opportunity → Match).

## Parité bootstrap (parity-first)

Direction vient du match ; scenario `OHLC_TREND`, timeframe `15m` mappés
déterminiquement sur l'identité exacte LEGACY_OHLC_TREND_V1 ; validité et
explication/score conservent le comportement legacy (fenêtre 30 min via le
contexte d'observation ; explication = valeur historique). Toute autre
stratégie exige une extension de mapping explicite (échec rapide).

NO_MATCH → no-signal truthful (aucun match, aucune opportunité).
NOT_EVALUABLE / FAILED → `run.fail("STRATEGY_…")` distincts, jamais traités en
no-signal.

## Truthfulness

Le bootstrap reste UNVALIDATED : une opportunité issue de son match signifie
uniquement « les conditions déterministes de cette stratégie bootstrap non
validée ont matché ». Aucune claim de validated edge / probabilité / expectancy.

## Compatibilité

Passive path : même pipeline → même autorité, sans redesign. Risk/Broker/AI :
inchangés. Backtest (0013) : `StrategyDefinition` + `StrategyEvaluator`
restent indépendants du live ; créer une TradingOpportunity reste une
préoccupation live/produit — Backtest n'en a pas besoin pour déterminer un
match. Trader Analytics : lineage complète préservée via strategy_match_id.

## Limitations restantes

- Explication toujours héritée du flux legacy (migration texte ultérieure).
- Score toujours basé sur les confidences d'Observation (le match ne porte pas
  de confiance en V1).
- Mapping opportunité limité au bootstrap ; extension requise par nouvelle
  stratégie.

## Acceptation runtime finale (Story 0012)

### Projection ActiveScan — provenance Strategy

La projection trader-facing `GET /api/v1/intelligence/scans/{scanId}` expose
désormais, par marché à opportunité, un bloc minimal et véridique :

```json
"strategy": {
  "strategyMatchId": "…",
  "strategyId": "…",
  "strategyVersion": 1
}
```

- Source de vérité : `TradingOpportunity.strategyMatchId` →
  `StrategyMatchRepository.findById` → `StrategyMatch`. Aucune reconstruction
  depuis scenario/OHLC/Observation.
- L'opportunité porte aussi `strategyMatchId` en racine.
- Aucune exposition de conditionResults, contextDigest, paramètres de stratégie
  ou payloads internes.
- Rangées historiques (pre-0012, `strategy_match_id = NULL`) : les trois champs
  valent null / bloc absent — aucune attribution legacy fabriquée.

### Migration V6 en runtime

Appliquée normalement par Flyway sur PostgreSQL (schéma market_intelligence) :
colonne `trading_opportunity_versions.strategy_match_id` (NULL autorisé),
FK `fk_opportunity_strategy_match` vers `strategy_matches(match_id)`,
index `idx_opportunity_strategy_match`.

### Preuve ETH/USD (bounded benchmark, scope explicite 1 marché)

- Utilisateur/account benchmark fraîchement authentifié via Gateway.
- Marché : ETH/USD (`41d626f1-b621-4627-9666-6ef8ac408a25`, OPEN).
- scanId : `382ba177-7734-4115-8df2-261e4a7e0358`
- AnalysisExecutionId : `3c43b477-2b80-426f-8ce0-4f40869d151b`
- ObservationId : `76b302a9-7fd0-427c-a258-de6cac2f156f`
- StrategyEvaluation : MATCH, direction LONG,
  contextDigest préfixe `c6446841920baa0c…`,
  stratégie bootstrap LEGACY_OHLC_TREND_V1
  (`0a10c7e2-9d1e-4f5a-b6c8-123456789001`, version 1), UNVALIDATED.
- StrategyMatchId : `101a0cd0-f428-44f1-9d99-3ab7cba886cf`
  (matchId, strategyId/version, marketId, analysisExecutionId, observationId,
  direction LONG, digest, matchedAt/createdAt persistés ; commité dans le
  chemin requis T1, pas d'afterCommit ni transaction séparée).
- TradingOpportunity `983fb5c4-cf4a-32dc-8ba8-cc8952745826`, versions 1→3
  (DETECTED → ANALYZED → ACTIVE), toutes avec
  `strategy_match_id = 101a0cd0-…` (attribution sur chaque version).
- PipelineRun `68f34bd0-ca61-4810-9f39-0a8d6d99379b` COMPLETED.
- Projection GET : strategyMatchId/strategyId/strategyVersion présents et
  identiques aux faits DB ; GET répétés strictement stables.
- Idempotence : rejeu du même `Idempotency-Key` → même scanId, un seul
  PipelineRun, aucun doublon de match/opportunité.

### Effets de bord

Aucun TradePlan, RiskEvaluation, ExecutionIntent, ordre broker ou Trade créé
par le benchmark. NO_MATCH → zéro match/opportunité : confirmé par tests
automatisés (TEST_CONFIRMED au runtime).

### Qualité

Suite market-intelligence complète : **230 tests, 0 failures, 0 errors,
0 skipped** (base 222 + 8 nouveaux tests projection/factory/truth).

### Handoff Story 0013 (Backtest)

`StrategyDefinition` + `StrategyEvaluator` restent découplés d'ActiveScan,
Gateway, HTTP, Broker et des adaptateurs Market Data live ; le backtest pourra
réutiliser le même couple sans dupliquer la règle OHLC.
