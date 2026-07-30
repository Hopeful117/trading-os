# ADR-026 — Notes d'implémentation

- **Statut :** Implémenté
- **Module :** `market-intelligence`
- **Plan :** `IMPLEMENTATION-PLAN-ADR-026.md`

## Architecture livrée

```text
Observations ───────┐
                   ├─> OpportunityEngine ─> TradingOpportunity (append-only)
AI Analysis refs ──┘            │                    │
                                │                    ├─> OpportunityRegistry
                                │                    ├─> Ranking
                                │                    └─> REST queries
                                └─> Fusion + Deduplication + Lifecycle

TradingOpportunity ── 1:N ── UserOpportunity
        global                    projection utilisateur
```

## Domaine

`TradingOpportunity` est un agrégat immutable et global. Il contient une identité
logique, une version, un lifecycle, un score de priorité métier, un scénario, une
direction, une origine, au moins une référence d'Observation et des références IA
optionnelles. Il ne contient aucune préférence utilisateur, donnée broker ou donnée
d'exécution de trade.

`OpportunityScore` est borné entre 0 et 100. Il représente une priorité métier et
jamais une probabilité de succès.

## Création et versioning

`OpportunityEngine` est l'unique service de création. Son builder est package-private.
Le moteur charge les Observations actives, valide les références IA via un port de
lecture, applique la fusion, recherche une équivalence logique, puis ajoute :

- une version 1 avec un nouvel `OpportunityId` ;
- ou la version suivante de la même Opportunity logique.

La déduplication compare instrument, direction, scénario, timeframe, ensemble
d'Observations et fenêtre temporelle configurable.

## Lifecycle et expiration

Les transitions autorisées sont celles de l'ADR :

```text
DETECTED -> ANALYZED -> ACTIVE -> CONSUMED
    |           |          |
    +-----------+----------+-> EXPIRED
```

Chaque transition ajoute une version immutable. La politique d'expiration par fenêtre
de validité est déterministe et remplaçable. L'IA n'a aucune opération de transition.

## Persistance et projections

`TradingOpportunityRepository` expose la version exacte, la dernière version,
l'historique, les Opportunities actives et les candidats de déduplication.
L'adaptateur actuel utilise une entité séparée, un mapper sans logique métier et un
stockage append-only thread-safe.

`UserOpportunity` et son repository isolent favoris, masquage, notifications, lecture,
priorité personnalisée et notes. Plusieurs utilisateurs partagent la même Opportunity
globale.

## Registry, ranking et API

Le Registry résout versions courantes et historiques et délègue toute transition au
moteur. Le Ranking Engine trie sans mutation avec stratégie, filtre et ordre stable.

API disponible :

| Méthode | Endpoint | Fonction |
|---|---|---|
| `GET` | `/opportunities` | recherche paginée, filtrée et triée |
| `GET` | `/opportunities/{id}` | dernière version |
| `GET` | `/opportunities/active` | Opportunities actives |
| `GET` | `/opportunities/history/{id}` | historique complet |

Les filtres couvrent instrument, timeframe, statut, type et activité. Les tris
supportés sont `createdAt`, `evaluatedAt`, `score` et `instrument`.

## Vérification

Les tests couvrent agrégat, value objects, commande, fusion, déduplication, moteur,
versioning, lifecycle, expiration, ranking, registry indirectement via le moteur,
persistance, mapping, projections utilisateur, REST et frontières ArchUnit.
