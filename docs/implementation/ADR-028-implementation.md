# ADR-028 — Notes d’implémentation

- **Statut :** Implémenté
- **Module :** `risk-domain`
- **ADR :** `docs/adr/ADR-028.md`
- **Plan :** `docs/implementation/ADR-028-implementation-plan.md`
- **Runtime :** Java 21

## Périmètre

Cette implémentation introduit un domaine Risk autonome chargé d’évaluer de
manière déterministe un état de compte, un portefeuille et une action de
trading proposée.

Le module ne contient aucune dépendance vers :

- Spring ;
- une base de données ou une API de persistence ;
- un broker ;
- un système de messaging ;
- Market Intelligence ou Trading Core ;
- un composant IA.

Les adaptateurs chargés de collecter les snapshots, de convertir un TradePlan,
de conserver l’audit ou d’exposer une API restent à l’extérieur du domaine.

## Architecture

```text
Configured RiskPolicy instances
              │
              ▼
     RiskPolicyResolver
              │
              ▼
   EffectiveRiskRuleSet
              │
              ▼
 RiskEvaluationRequest + immutable snapshots
              │
              ▼
 RiskEvaluationContextBuilder
              │
              ▼
   RiskEvaluationContext
              │
              ▼
   ObservedMetricsCalculator
              │
              ▼
       ObservedMetrics
              │
              ▼
       ProjectionEngine
              │
              ▼
 ProjectedPortfolioState + ProjectedMetrics
              │
              ▼
    DerivedMetricsCalculator
              │
              ▼
       DerivedMetrics
              │
              ▼
      RiskMetricsAssembler
              │
              ▼
 RiskRuleEvaluationContext
              │
              ▼
   complete RiskRule traversal
              │
              ▼
     RiskResultAggregator
              │
              ▼
    RiskValidationResult
```

Chaque étape produit une valeur immuable utilisée par l’étape suivante.
`DeterministicRiskEngine` orchestre ce pipeline sans effectuer de calcul
financier.

## Fondations du domaine

Le vocabulaire ADR-028 est représenté explicitement :

- `RiskDecision` : `APPROVED`, `APPROVED_WITH_WARNINGS`, `REJECTED` ;
- `EvaluationStatus` : `COMPLETED`, `INCOMPLETE`, `FAILED` ;
- `ValidationMode` : pré-trade, monitoring et simulation ;
- `RuleStatus` : `PASS`, `WARNING`, `FAILURE`, `NOT_APPLICABLE` ;
- `RuleSeverity` : `INFO`, `WARNING`, `BLOCKING` ;
- `RuleCategory` et `PolicyAuthority`.

`Money` et `Ratio` sont les principaux value objects. Les calculs monétaires
refusent les mélanges de devises. Un ratio utilise une représentation décimale :
`0.01` représente 1 %.

`ProposedTrade` constitue le contrat broker-neutre extrait d’une version exacte
de TradePlan. Il contient l’identité et la version du plan, l’instrument, la
direction, la quantité, le notional, la perte au stop et la marge requise.

## Snapshots et intégrité du contexte

Les entrées métier sont des snapshots immuables et versionnés :

- `AccountSnapshot` ;
- `PortfolioSnapshot` ;
- `PositionSnapshot` ;
- `MarketSnapshot` ;
- `TradingContext` ;
- `RuleSetSnapshot`.

`RiskEvaluationContext` n’a pas de constructeur public. Le chemin de
construction prévu passe obligatoirement par `RiskEvaluationContextBuilder`.

Le builder ne calcule aucune métrique et n’exécute aucune projection. Il :

1. vérifie la présence des snapshots ;
2. vérifie la cohérence entre compte et TradingContext ;
3. vérifie que les valeurs du portefeuille utilisent la devise du compte ;
4. vérifie les devises du trade proposé ;
5. exige un prix de marché pour l’instrument d’une évaluation pré-trade ;
6. assemble le contexte immuable.

Le Risk Engine ne charge aucune information supplémentaire après cette étape.

## Métriques

Les calculs financiers sont centralisés dans la couche `metric`.

### Métriques observées

`ObservedMetricsCalculator` produit :

- balance ;
- equity ;
- floating PnL ;
- closed PnL journalier ;
- marge utilisée ;
- marge disponible.

Formules V1 :

```text
floating PnL = equity - balance
free margin  = equity - used margin
```

### Métriques projetées

`ProjectionEngine` retourne :

- exposition projetée ;
- drawdown monétaire projeté ;
- marge projetée ;
- portfolio heat ;
- état complet du portefeuille projeté.

### Métriques dérivées

`DerivedMetricsCalculator` produit notamment :

- risque restant ;
- utilisation du risque ;
- ratio de risque de la position projetée ;
- ratio d’exposition projetée ;
- ratio de drawdown journalier projeté.

Les règles reçoivent uniquement ces métriques précalculées via
`RiskRuleEvaluationContext`. Elles n’ont pas accès aux snapshots financiers ou
au TradePlan complet.

## Projection du portefeuille

La projection utilise des quantités signées :

```text
LONG  -> quantité positive
SHORT -> quantité négative
```

Elle gère les opérations suivantes :

- ouverture d’une position longue ou courte ;
- augmentation d’une position dans la même direction ;
- réduction proportionnelle par un trade opposé ;
- fermeture lorsque les quantités s’annulent ;
- inversion lorsque le trade opposé traverse zéro ;
- ouverture du reliquat seulement dans la nouvelle direction.

Pour une réduction, exposition, perte au stop et marge de la position existante
sont diminuées proportionnellement. Pour une inversion, seule la partie du
trade dépassant la quantité clôturée contribue à la nouvelle position.

Les positions projetées sont triées par instrument afin de préserver un ordre
stable.

Formules d’agrégation V1 :

```text
projected exposure = somme des expositions des positions projetées
portfolio heat     = somme des pertes au stop projetées
projected drawdown = perte journalière observée + portfolio heat
projected margin   = marge hors positions actuelle
                     + marge des positions projetées
```

Le moteur de projection ne prend aucune décision d’autorisation et n’évalue
aucune règle.

## Infrastructure des règles

`RiskRule` définit :

- une identité stable ;
- une condition d’applicabilité ;
- une évaluation à partir des métriques précalculées ;
- un `RiskRuleResult` structuré.

`RiskRuleRegistry` indexe les implémentations, refuse les identifiants dupliqués
et reste immuable après construction.

Chaque résultat contient :

- rule ID et version ;
- statut et sévérité ;
- explication structurée ;
- métriques exposées ;
- timestamp ;
- metadata.

Une règle non applicable produit explicitement `NOT_APPLICABLE`. Une
implémentation configurée mais absente du registre produit un résultat
auditable et rend l’évaluation `INCOMPLETE`.

## Règles initiales

Trois règles sont disponibles :

| Règle | Métrique consommée | Paramètre |
|---|---|---|
| `MAX_POSITION_RISK` | `positionRiskRatio` | `maximumRatio` |
| `MAX_EXPOSURE` | `exposureRatio` | `maximumRatio` |
| `DAILY_DRAWDOWN` | `dailyDrawdownRatio` | `maximumRatio` |

Les règles comparent une métrique à leur configuration et construisent une
explication structurée. Elles ne calculent ni exposition, ni drawdown, ni
perte attendue, ni projection, ni ratio financier.

## Policies et résolution des conflits

Les policies sont résolues selon l’autorité suivante :

```text
PLATFORM
  ↓
BROKER
  ↓
PROP_FIRM
  ↓
ACCOUNT
  ↓
USER
  ↓
STRATEGY
```

Une autorité inférieure peut renforcer une contrainte mais ne peut pas
affaiblir une contrainte de sécurité supérieure.

La sémantique des paramètres n’est pas codée dans `RiskPolicyResolver`.
Chaque famille de règles fournit une `RuleConflictResolutionStrategy`.

Les trois règles initiales utilisent `UpperBoundRuleResolutionStrategy`, qui
conserve la borne numérique la plus stricte et la sévérité la plus forte. Une
future règle peut enregistrer une stratégie différente sans modifier le
resolver.

`RuleConfiguration` accepte des paramètres immuables numériques, textuels et
booléens. La stratégie associée est responsable de leur interprétation.

## Agrégation et fail-closed

Le moteur parcourt toujours toutes les règles dans l’ordre stable défini par
`EffectiveRiskRuleSet`. Il ne s’arrête pas à la première violation.

`RiskResultAggregator` sépare le statut d’exécution de la décision métier :

```text
missing rule implementation -> INCOMPLETE, aucune décision
rule/calculation failure     -> FAILED, aucune décision
blocking FAILURE             -> REJECTED
WARNING result               -> APPROVED_WITH_WARNINGS
INFO FAILURE only            -> APPROVED
all applicable rules pass    -> APPROVED
```

Une `FAILURE` n’est donc pas automatiquement bloquante. Seule une règle de
sévérité `BLOCKING` peut produire un rejet.

Les erreurs et informations indisponibles n’accordent jamais une autorisation :
les évaluations incomplètes ou échouées ne contiennent aucune décision métier.

## Déterminisme

Le moteur est stateless. Toutes ses dépendances sont immuables ou sans état
mutable d’évaluation.

Le timestamp est obtenu via un `Clock` injecté. La même horloge, le même
contexte, les mêmes versions de policies, les mêmes versions de règles et la
même version du moteur produisent le même résultat.

L’ordre logique ne dépend pas de la structure interne des maps ou de l’ordre
d’insertion des positions. Aucune parallélisation n’est introduite.

## Audit, traçabilité et replay

`RiskValidationResult` contient :

- evaluation ID et correlation ID ;
- statut d’exécution et décision éventuelle ;
- résultats complets des règles ;
- violations et warnings ;
- métriques globales ;
- mode et timestamp ;
- version du moteur ;
- versions des policies et des règles ;
- identifiants, versions et timestamps des snapshots.

`RiskEvaluationRecord` associe le contexte complet et le résultat officiel.
`RiskReplayService` réutilise le timestamp enregistré et compare le nouvel
artefact au résultat historique.

`RiskEvaluationAuditPort` est la frontière append-only destinée à la
conservation durable. Aucun adaptateur de persistence n’est fourni dans le
module afin de préserver l’indépendance du domaine.

## Explainability

Chaque règle produit un code d’explication et des valeurs structurées telles
que :

- valeur courante ;
- maximum autorisé ;
- dépassement.

`DecisionExplainer` expose les violations et warnings expliquant un rejet ou
une approbation avec avertissements. Pour une approbation sans anomalie, il
conserve les explications PASS afin que la décision ne soit pas opaque.

Aucune explication ne repose sur un modèle IA ou sur du texte non structuré.

## Vérification

La suite Maven couvre :

- indépendance du domaine vis-à-vis des frameworks et autres modules ;
- absence de calcul financier dans les règles concrètes ;
- intégrité et construction contrôlée du contexte ;
- métriques observées et dérivées ;
- ouverture et augmentation long/short ;
- réduction, fermeture et inversion de position ;
- exposition, marge, heat et drawdown projetés ;
- résolution hiérarchique des policies ;
- stratégies de résolution numériques et textuelles ;
- séparation statut/sévérité ;
- évaluation complète ;
- fail-closed ;
- ordre et résultat déterministes ;
- audit et replay.

Commande :

```shell
cd risk-domain
mvn clean test
```

État de la suite au terme de l’implémentation :

```text
Tests run: 21
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

## Éléments volontairement différés

Les éléments suivants appartiennent à de futures couches applicatives ou
infrastructurelles :

- adaptateur durable de `RiskEvaluationAuditPort` ;
- collecte de durée opérationnelle wall-clock ;
- gouvernance et autorisation des overrides temporaires ;
- intégration concrète avec Trade Planning, monitoring et exécution ;
- exposition REST, événements et dashboards.

Ces éléments ne modifient pas le comportement déterministe du Risk Domain et
ne doivent pas introduire de dépendance infrastructurelle dans ce module.
