# ADR-027 — Notes d’implémentation

- **Statut :** Implémenté
- **Module :** `market-intelligence`
- **Plan :** `ADR-027-implementation-plan.md`

## Architecture

```text
TradingOpportunity + TradingContext snapshot
                    │
                    ▼
           TradePlanningEngine
          ┌─────────┴─────────┐
          ▼                   ▼
 deterministic policies   optional AI proposal
          │                   │
          │             validation boundary
          └─────────┬─────────┘
                    ▼
              TradePlanDraft
                    │
          conflict detection
                    │
                    ▼
             TradePlanBuilder
                    │
                    ▼
       immutable TradePlan version
                    │
          ┌─────────┴──────────┐
          ▼                    ▼
 Risk validation boundary   Execution boundary
```

## Domaine

`TradePlan` sépare explicitement :

- `ExecutionParameters` : instrument, direction, entrée, stop, objectifs,
  position sizing, risk/reward, expiration et règles de gestion ;
- `TradingRationale` : Opportunities exactes, Observations, analyses IA,
  thèse, confirmations et invalidations.

L’agrégat ne contient ni ordre broker, ni rapport d’exécution, ni position, ni
PnL. Chaque plan référence une version exacte d’un snapshot `TradingContext`.

## Construction

`TradePlanningEngine` est l’unique frontière publique de création. Il :

1. résout les identifiants d’Opportunity ;
2. charge et autorise le snapshot de TradingContext ;
3. vérifie instrument, direction et activité des Opportunities ;
4. exécute les politiques selon un ordre explicite ;
5. rejette les contributions contradictoires ;
6. accepte éventuellement une proposition IA structurée après validation ;
7. finalise le draft via le builder interne.

L’adaptateur IA par défaut est désactivé. Une défaillance IA produit un warning
observable et n’empêche pas le planning déterministe.

## Politiques initiales

Les politiques déterministes couvrent l’entrée, le stop, l’objectif, le sizing,
l’expiration, les confirmations, les invalidations, la gestion et la thèse.
Elles sont stateless, testables séparément et ne persistent rien.

`OpportunityScore`, confiance d’Observation et calcul du risque monétaire restent
des concepts distincts.

## Lifecycle et versions

Transitions prises en charge :

```text
DRAFT -> PROPOSED
PROPOSED -> ACCEPTED | REJECTED | EXPIRED
ACCEPTED -> RISK_VALIDATED | EXPIRED
RISK_VALIDATED -> READY_TO_EXECUTE | EXPIRED
READY_TO_EXECUTE -> EXECUTED | EXPIRED
```

Chaque transition et chaque replanning ajoute une version. Le repository est
append-only, conserve l’historique et permet la navigation précédent/suivant.
Le replanning charge le dernier contexte mais ne modifie jamais l’ancien snapshot.

## Frontières ADR-028 et ADR-029

`TradePlanRiskValidationBoundary` fournit uniquement une version `ACCEPTED`
exacte et enregistre le résultat sur la dernière version attendue.

`TradePlanExecutionBoundary` fournit uniquement une version
`READY_TO_EXECUTE` exacte. Aucun composant Trade Planning ne dépend d’un broker.

## API

| Méthode | Endpoint | Fonction |
|---|---|---|
| `POST` | `/trade-plans` | créer une proposition |
| `GET` | `/trade-plans/{id}` | lire la dernière version |
| `GET` | `/trade-plans/{id}/versions` | lire l’historique |
| `POST` | `/trade-plans/{id}/replan` | créer une nouvelle version |

Les contrôleurs valident des DTO et n’exposent jamais les agrégats directement.
Les échecs de planning utilisent un résultat structuré indépendant de HTTP.

## Événements et observabilité

Les événements couvrent création, nouvelle version, acceptation, rejet,
expiration et disponibilité pour validation du risque. L’adaptateur par défaut
les journalise sous forme structurée. Les métriques couvrent créations,
replanning, acceptations, rejets, expirations, conflits, échecs IA et durée de
planning. Un snapshot exploitable par un dashboard opérationnel est exposé sur
`GET /internal/trade-planning/metrics`.

## Vérification

Les tests couvrent value objects, invariants, snapshots, politiques, registre,
conflits, validation IA, moteur, lifecycle, replanning, persistence, mapping,
REST, événements, métriques et frontières ArchUnit.
