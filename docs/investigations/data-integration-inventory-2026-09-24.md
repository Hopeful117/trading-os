# Inventaire des données et intégrations — 2026-09-24

## Objet et méthode

Ce document rend visibles les dépendances de données avant toute décision d'outillage.
Il s'appuie uniquement sur le dépôt et sa documentation courante. Aucun secret n'a
été lu ou reproduit, aucun système source n'a été modifié et aucune intégration de
production n'a été lancée.

Les responsabilités décrites ci-dessous suivent `AGENTS.md`, les ADR acceptés et
les contrats présents dans le code. L'absence d'un contrat explicite est signalée
comme une lacune, et non comblée par une hypothèse.

## Flux et sources

| Source / flux | Données et format observés | Propriétaire dans Trading OS | Accès / contrat | Qualité et fraîcheur | Dépendances et limites |
| --- | --- | --- | --- | --- | --- |
| Kraken public REST | Marchés `AssetPairs`, ticker, OHLC; réponses JSON fournisseur transformées en modèles Java | Market Data Service | `GET /0/public/AssetPairs`, `GET /0/public/Ticker?pair=...`, `GET /0/public/OHLC?pair=...&interval=...`; adaptateur Feign interne | OHLC normalisé; doublons et trous traités par le normalizer; snapshot marqué `FRESH` ou `STALE`; fenêtre par défaut des snapshots: 30 s | URL configurable; limite, disponibilité et sémantique des symboles Kraken à surveiller; pas de contrat OpenAPI fournisseur versionné dans le dépôt |
| Kraken public WebSocket | Ticker, OHLC, carnet, transactions récentes, événements horodatés | Market Data Service | WebSocket Kraken v2 configuré; abonnements dynamiques via `/api/v1/markets/{marketId}/subscriptions` | État en mémoire avec timestamps; la fraîcheur dépend de la connexion et de la reconnexion; pas de mécanisme distribué entre instances | Une instance peut perdre son état; reconnect et comportement lors d'un trou réseau doivent être validés en environnement contrôlé |
| Catalogue Market Data | Marchés, contraintes, identifiants internes, symboles normalisés | Market Data Service | `GET /api/v1/markets`, `GET /api/v1/markets/{marketId}`, `POST /api/v1/markets/synchronize` | Source persistée dans la base `market_data`; exactitude liée à la synchronisation Kraken; `marketId` interne stable attendu par les consommateurs | Les consommateurs ne doivent pas utiliser directement les symboles Kraken comme identifiants métier |
| Snapshots de prix | Bid/ask/prix, source, version, statut de fraîcheur | Market Data Service | `POST /internal/markets/prices/snapshot`; contrat de requête par liste de `marketIds` | Retour explicite `FRESH`/`STALE`; valorisation utilise bid pour BUY et ask pour SELL selon la documentation | Contrat interne protégé par confiance réseau/JWT de service; la couverture de sécurité locale de Market Data doit rester vérifiée |
| Valuation snapshots | Faits de valorisation, timestamp, provenance, âge, conversion et statut | Market Data Service | `POST /internal/v1/valuation-snapshots/batch` | Provenance conservée (`provider`, symbole, observation, `effectiveAt`, `capturedAt`); statut peut être `STALE` ou indisponible | Requiert prix et conversion disponibles; ne doit pas être interprété comme une autorité broker pour les comptes LIVE |
| Compte et risque broker | Balances, equity, positions, ordres, capacités, marge | Broker Service, consommé par Trading Core | Contrats broker-neutres internes sous `/internal/v1/broker-accounts/...`; opérations d'exécution sous `/internal/v1/executions`; appels privés Kraken dans l'adaptateur | Snapshot temporel; permissions requises documentées `read` + `trade`, sans withdrawal; risque non revalidé juste avant soumission | Credentials chiffrés et confinés au Broker Service; ownership et protection des endpoints internes doivent être prouvés avant exposition élargie |
| Exécution broker | Requête d'ordre, résultat, annulation, réconciliation; identifiants et statuts normalisés | Trading Core pour l'autorité métier; Broker Service pour transport technique | `POST /api/v1/executions/validate`, `POST /api/v1/executions/{id}/execute`; Broker Service `/internal/v1/executions` | Idempotency key, résultat inconnu et réconciliation prévus; pas de retry aveugle; tests sandbox/E2E non validés | Ne pas lancer en production; la réponse ambiguë doit rester `UNKNOWN` jusqu'à réconciliation |
| Trading Core | Utilisateurs, comptes, plans, risque, trades, exécutions, statistiques | Trading Core | REST public `/api/v1/...`, appels Feign internes vers Market Data, Broker Service et Market Intelligence | PostgreSQL `trading_os`; cohérence métier et ownership à contrôler par compte/utilisateur | Le chemin complet opportunité → risque → validation → ordre n'est pas démontré de bout en bout |
| Market Intelligence | Contexte marché, observations, opportunités, Trade Plans, exécutions d'analyse | Market Intelligence | REST `/api/v1/intelligence/...`, `/api/v1/opportunities/...`; clients internes Market Data et Trading Core | Fraîcheur et provenance font partie du modèle; artefacts et certains repositories restent en mémoire; AI Engine réel désactivé | Pas de News Service ni AI Engine; durabilité et multi-instance incomplètes |
| PostgreSQL | Données relationnelles séparées: `trading_os`, `market_data`, `broker_service`, `market_intelligence` | Service propriétaire correspondant | Réseau Compose interne; accès applicatif via Spring/JPA | Persistance locale par volumes Compose; migrations, sauvegardes et HA non démontrées | Identifiants de démonstration présents dans Compose; ne pas réutiliser cette configuration telle quelle en production |
| Eureka | Découverte de services | Eureka Server / plateforme | Service discovery interne; Feign par nom de service | Disponibilité nécessaire aux appels inter-services; pas une source métier | Une panne de discovery dégrade les flux même si les bases sont disponibles |
| News / calendrier économique | Non disponible | Aucun service implémenté | Aucun contrat exploitable | Inconnue | Dépendance fonctionnelle explicitement différée; ne pas substituer une source non approuvée |
| AI Engine | Non disponible, adaptateur désactivé | Aucun moteur implémenté | Aucun fournisseur ni contrat d'intégration actif | Inconnue | Aucun appel LLM/RAG/agent ne doit être supposé pour le socle minimal |

## Contrats et dépendances principales

```text
Kraken REST/WebSocket
        |
        v
Market Data -- catalogue, OHLC, ticker, snapshots, fraîcheur
        |                         |
        v                         v
Market Intelligence         Trading Core
 contexte/opportunités       comptes/risque/exécution
        |                         |
        +------------+------------+
                     v
              validation humaine
                     |
                     v
              Broker Service
                     |
                     v
              Kraken privé
```

Contrats à conserver comme frontières stables:

- les DTO du fournisseur Kraken restent dans les adaptateurs Kraken;
- Market Data expose des faits normalisés et des statuts de fraîcheur, pas des décisions de stratégie;
- Broker Service expose des commandes, réponses et snapshots broker-neutres;
- Trading Core reste propriétaire de l'autorisation, du risque, de l'idempotence et de l'état d'exécution;
- Market Intelligence produit des observations/opportunités/plans, mais ne soumet jamais directement un ordre;
- les appels `/internal/...` sont des contrats de service et ne doivent pas être traités comme des APIs publiques.

## Socle minimal intégrable recommandé

Le plus petit socle permettant de démontrer une valeur sans risque d'exécution est:

1. Market Data + une source Kraken **publique** (AssetPairs, ticker, OHLC), sans clé privée.
2. PostgreSQL pour le catalogue, l'historique OHLC et les données nécessaires au parcours local.
3. Market Intelligence déterministe consommant catalogue, snapshots et OHLC frais.
4. Trading Core en mode PAPER pour comptes, plans, règles de risque et positions locales.
5. Gateway + Eureka uniquement pour le routage et la découverte nécessaires au parcours local.
6. Tests contractuels simulés pour les frontières inter-services; aucun ordre externe.

Ce socle doit refuser ou rendre explicites `STALE`, incomplet, indisponible et
contradictoire. Il ne nécessite ni News Service, ni AI Engine, ni credentials
Kraken privés, ni nouvelle plateforme d'événements distribués.

## Préconditions d'accès et d'exploitation

- Définir les propriétaires opérationnels de Kraken, Market Data, Trading Core, Broker Service, bases et discovery.
- Obtenir uniquement des accès de lecture pour l'inventaire et la validation publique; ne jamais demander de secret dans un ticket ou un rapport.
- Pour un futur sandbox broker, créer des credentials dédiés et à permissions minimales `read` + `trade`; interdire withdrawal.
- Stocker les secrets via le mécanisme chiffré existant; ne pas les placer dans le dépôt, les logs, les traces ou les métriques.
- Fixer des seuils de fraîcheur par usage et conserver `effectiveAt`, `capturedAt`, provider et identifiant de source.
- Versionner les contrats inter-services et ajouter des tests de compatibilité pour payloads valides, champs absents, précision décimale, timestamps et erreurs.
- Tester les limites Kraken: rate limits, coupure WebSocket, retard OHLC, symbole inconnu, réponse partielle et changement de schéma.
- Avant toute écriture externe, prouver ownership, idempotence, issue inconnue et réconciliation sur sandbox.

## Qualité, fraîcheur et observabilité à exiger

Chaque fait consommé par une analyse ou une valorisation devrait pouvoir répondre à:

- quelle est la source et quel provider l'a produit;
- quand le fait est devenu effectif et quand il a été capturé;
- quel est son âge au moment de l'utilisation;
- est-il frais, obsolète, incomplet ou indisponible;
- quelle normalisation a été appliquée;
- quelle version de contrat/politique l'a interprété.

Les contrôles minimum sont: complétude des chandelles et intervalles, monotonie
des timestamps, bid/ask cohérents, prix positifs, marché tradable, symboles
résolus, conversion de devise disponible et propagation déterministe des statuts
dégradés. Une donnée manquante ou stale ne doit pas être transformée en donnée
fraîche par défaut.

## Risques et décisions à escalader

| Niveau | Risque | Conséquence | Action avant intégration |
| --- | --- | --- | --- |
| Critique | Exposition ou fuite de credentials broker | Ordres ou compromission de comptes | Revue sécurité, coffre/chiffrement, rotation, preuve d'absence dans logs; ne pas exécuter |
| Élevé | Endpoints internes insuffisamment protégés ou ownership incomplet | Accès inter-comptes ou exécution non autorisée | Restreindre réseau et JWT de service, tests d'acceptation cross-service, revue indépendante |
| Élevé | Risque et marge non revalidés avant soumission | Ordre autorisé sur un état devenu obsolète | Décision produit/risque avant LIVE; sandbox seulement tant que non traité |
| Élevé | Fraîcheur WebSocket ou OHLC non garantie en multi-instance | Décision sur prix ou contexte périmé | SLA de fraîcheur, reconnexion, stockage/stream distribué ou limitation à une instance |
| Moyen | Contrat Kraken non versionné dans le dépôt | Régression lors d'un changement fournisseur | Fixtures contractuelles et surveillance de schéma sans secret |
| Moyen | News Service et AI Engine absents | Contexte macro et interprétation indisponibles | Marquer explicitement l'absence; ne pas créer de fallback implicite |
| Moyen | Artefacts/observations Market Intelligence partiellement en mémoire | Perte au redémarrage, divergence multi-instance | Décider la persistance durable avant usage opérationnel |
| Moyen | Compose utilise des valeurs de base de données adaptées au local | Déploiement non conforme ou exposition de données | Séparer profils local/sandbox/production, gestionnaire de secrets, sauvegardes et TLS |

## Hors périmètre de cette évaluation

- aucun appel authentifié Kraken;
- aucune création, modification, annulation ou réconciliation d'ordre;
- aucune modification de système source ou de configuration d'environnement;
- aucune sélection de fournisseur News/AI;
- aucune décision d'architecture remplaçant les ADR existants.

## Conclusion

Le dépôt fournit déjà un chemin d'intégration minimal et testable autour de la
donnée de marché publique et du mode PAPER. La prochaine étape sûre est une
validation contractuelle hors production, avec fixtures et données synthétiques,
puis une preuve sandbox séparée et approuvée. L'accès privé broker et toute
intégration de production restent bloqués par les contrôles de sécurité,
d'ownership, de fraîcheur et de réconciliation listés ci-dessus.
