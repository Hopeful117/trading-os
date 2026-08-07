# Trading OS

Trading OS est un assistant de trading intelligent destiné aux traders discrétionnaires. Il centralise les données de marché, les comptes broker, les règles de risque, la préparation, l'exécution contrôlée et l'analyse des trades afin de réduire la charge cognitive et d'améliorer la discipline.

Le produit n'est ni un bot autonome, ni une plateforme HFT, ni un outil limité aux challenges de prop firms. Les règles déterministes ont toujours priorité sur les recommandations de l'IA et toute exécution reste soumise à une validation humaine explicite.

Le projet est en développement actif. L'architecture microservices, l'authentification, la synchronisation Kraken, le moteur de risque, le Dashboard, l'interface Angular, les fondations de Market Intelligence et le pipeline d'exécution broker sont présents. Aucun AI Engine réel ni mécanisme de décision ou d'exécution autonome n'est actuellement intégré : une exécution doit provenir d'une intention explicitement autorisée.

## Principes directeurs

- Le produit et le workflow du trader déterminent l'architecture, pas les API des fournisseurs.
- Le domaine Trading OS reste indépendant de Kraken et des futurs brokers ou fournisseurs.
- Les décisions critiques de risque et d'éligibilité restent déterministes, explicables et reproductibles.
- L'IA interprète et explique des informations structurées ; elle ne contourne jamais les règles et n'exécute aucun ordre.
- Le trader demeure le décideur final pour ouvrir, modifier ou fermer une position.
- Seules les données ayant une valeur métier mesurable sont collectées et conservées.

## Architecture

| Service | Port | Responsabilité |
| --- | ---: | --- |
| `gateway` | 8080 | Point d'entrée HTTP, sécurité JWT et routage |
| `trading-core` | 8081 | Utilisateurs, comptes, règles, trades, dashboard, statistiques et domaine d'exécution |
| `broker-service` | 8082 | Connexions broker, credentials chiffrés, capacités broker-neutres et exécution Kraken |
| `market-data` | 8083 | Référentiel de marchés, OHLC, snapshots de prix et flux Kraken |
| `market-intelligence` | 8084 | Analyses, observations, opportunités, trade plans, orchestration et artefacts d'intelligence |
| `eureka-server` | 8761 | Découverte des services |
| `trading-web` | 4200 | Application Angular servie par Nginx |
| PostgreSQL | interne | Bases séparées `trading_os`, `market_data` et `broker_service` |

Services prévus par l'architecture cible, mais pas encore implémentés :

| Service ou composant | Responsabilité cible |
| --- | --- |
| `news-service` | Calendrier économique, actualités et contexte macroéconomique normalisés |
| `ai-engine` | Interprétation, scénarios, explications et classement d'opportunités |
| Passive Scanner | Planification continue de la stratégie passive Market Intelligence |
| Active Scanner UI | Déclenchement et présentation de l'analyse approfondie |
| Position Monitoring | Surveillance des positions et recommandations sans exécution automatique |

La stack utilise Java 21, Spring Boot 4, Spring Cloud, PostgreSQL 16, Angular 21 et Docker Compose.

## État fonctionnel

- Authentification et autorisation JWT : disponible.
- Comptes broker et synchronisation Kraken : disponible.
- Référentiel et affichage des marchés Kraken : disponible.
- Règles et moteur de risque : première version implémentée ; les profils configurables complets restent à construire.
- Cycle de vie local des trades et statistiques : API implémentée, intégration UI incomplète.
- Exécution broker : intentions, tentatives, idempotence, soumission Kraken, annulation, récupération et réconciliation implémentées. La validation contractuelle contre le sandbox Kraken et le parcours déployé de bout en bout restent à exécuter.
- Flux temps réel : ticker, OHLC, carnet d'ordres et transactions récentes avec abonnements dynamiques.
- Dashboard : orchestration dans Trading Core, valorisation des positions, risque, fraîcheur et états dégradés.
- Fondation Market Intelligence ADR-020 : contexte modulaire, modes passif/actif, analyses déterministes, provenance et consolidation partielle.
- Gouvernance ADR-021 : `AnalysisExecution` asynchrone, idempotence, politique d'exécution, annulation, classification du contexte et contrat AI Engine désactivé.
- Artefacts ADR-022 : identités et scopes fortement typés, fraîcheur métier, résolution de réutilisation, dépendances et stockage V1 en mémoire.
- Capability Engine ADR-023 : contrat atomique, planner DAG immuable, moteur local parallèle, lifecycle, propagation ciblée, retries et annulation coopérative.
- Credentials ADR-024 : stockage chiffré et versionné, validation, rotation et statut technique par compte broker.
- Observations ADR-025 : modèle normalisé, construction, requêtes et repository local en mémoire.
- Opportunités ADR-026 : détection, ranking, projections utilisateur et API de consultation.
- Trade Planning ADR-027 : génération déterministe, versionnement et replanification ; adaptateur IA désactivé.
- Risk Domain ADR-028 : moteur déterministe autonome et testé ; autorisation des Trade Plans via le pipeline Trading Core, rendue accessible à travers le Gateway (Story 0003).
- Execution Domain ADR-029 : cycle de vie, idempotence, audit, retry contrôlé, annulation et récupération dans Trading Core.
- Broker Architecture ADR-030 : contrats broker-neutres, capacités, registre de providers, adaptateur Kraken, résilience et observabilité.
- News Service, scheduling passif, interface Scanner et AI Engine réel : non commencés.

### Market Intelligence

Le service distingue explicitement :

- l'exécution technique `AnalysisExecution` ;
- le résultat consolidé `ConsolidatedIntelligence` ;
- la connaissance métier durable `IntelligenceObservation` ;
- les artefacts dérivés réutilisables.

Les analyses déterministes et IA sont des capacités de premier rang travaillant en parallèle sur les sections de contexte qui leur sont autorisées. L'adaptateur AI Engine est volontairement indisponible : aucun résultat IA fictif n'est produit.

Contrat REST asynchrone :

```text
POST /api/v1/intelligence/analyses
GET  /api/v1/intelligence/analyses/{executionId}
GET  /api/v1/intelligence/analyses/{executionId}/result
POST /api/v1/intelligence/analyses/{executionId}/cancel
```

La création exige un en-tête `Idempotency-Key` et retourne `202 Accepted`.

La gestion des artefacts reste indépendante de Redis, Caffeine ou SQL. L'implémentation actuelle utilise uniquement un store en mémoire pour valider les règles de clé, scope, provenance, fraîcheur, réutilisation et invalidation ciblée. Elle n'est ni durable ni distribuée.

## Pipeline de décision

```text
Market Data et contexte
    ├── Analyse déterministe ──┐
    └── Interprétation IA ─────┤
                              ↓
                   Market Intelligence consolidée
    → Validation des règles et du risque
    → Validation humaine
    → Exécution par le Broker Service
```

Chaque couche enrichit les informations reçues sans remplacer les responsabilités des couches précédentes. Une recommandation doit rester explicable et un refus du moteur de risque doit être traçable.

Les briques Market Intelligence, Risk, Execution et Broker existent. L'autorisation
déterministe des Trade Plans par le Risk Domain est connectée et exposée via le
Gateway. L'enchaînement complet depuis une opportunité jusqu'à une exécution
validée par l'utilisateur n'est pas encore intégré de bout en bout : l'exécution
post-approbation reste une Story future.

## Configuration

Créer un fichier `.env` à la racine. Il n'est jamais versionné.

```dotenv
JWT_SECRET=change-me-with-at-least-32-random-bytes
JWT_EXPIRATION=3600000
JWT_ISSUER=trading-os
KRAKEN_BASE_URL=https://api.kraken.com
KRAKEN_WEBSOCKET=wss://ws.kraken.com/v2
KRAKEN_API_KEY=
KRAKEN_API_SECRET=
BROKER_MASTER_KEY=
BROKER_MASTER_KEY_VERSION=v1
SPRING_PROFILES_ACTIVE=prod
```

`BROKER_MASTER_KEY` doit contenir exactement 32 octets aléatoires encodés en
Base64. Elle peut être générée hors du dépôt avec `openssl rand -base64 32`.
Elle ne doit jamais être commitée. Le profil production utilise
`BROKER_CREDENTIAL_SOURCE=stored`; le mode `environment` est réservé au
développement et aux démonstrations explicitement configurées.

Ne jamais journaliser ni committer les clés Kraken, le JWT, les mots de passe ou les en-têtes `Authorization`.

## Démarrage

```bash
docker compose up --build
```

Points d'accès principaux :

- application : <http://localhost:4200>
- API Gateway : <http://localhost:8080>
- Eureka : <http://localhost:8761>

Le fichier Compose est adapté au développement et à la validation locale. Pour une production exposée, les secrets, TLS, sauvegardes PostgreSQL, healthchecks et limites de ressources doivent être fournis par la plateforme de déploiement.

## Politique de journalisation

Les applications écrivent uniquement sur la sortie standard afin que Docker ou la plateforme d'orchestration assure la collecte et la rétention.

- `ERROR` : panne ou opération abandonnée nécessitant une action.
- `WARN` : événement anormal géré, refus de sécurité ou dépendance dégradée.
- `INFO` : démarrage, arrêt et événements métier importants à faible volume.
- `DEBUG` : détails de diagnostic, désactivés en production.
- `TRACE` : investigation locale temporaire uniquement.

Le profil `prod` active le JSON Logstash, une ligne par événement. Les niveaux se règlent sans reconstruire les images :

```dotenv
LOG_LEVEL_ROOT=INFO
LOG_LEVEL_APP=INFO
LOG_LEVEL_SPRING=WARN
LOG_LEVEL_HIBERNATE=WARN
LOG_LEVEL_EUREKA=WARN
```

Une requête réussie génère une ligne Gateway avec méthode, chemin, statut et durée. Les query strings, corps, tokens, données personnelles et secrets ne doivent pas apparaître dans les logs. Le SQL Hibernate est désactivé par défaut.

## Politique de tests

Toute correction doit être accompagnée d'un test qui échoue avant la correction. Toute nouvelle règle métier doit couvrir au minimum le cas nominal, les limites et le refus attendu.

La pyramide visée est :

1. tests unitaires rapides pour services, mappers, validateurs, guards et composants ;
2. tests d'intégration ciblés pour JPA, sécurité, contrôleurs et routes ;
3. tests de contrat pour les échanges entre microservices et Kraken ;
4. quelques parcours de bout en bout sur la stack Docker.

Les tests unitaires et d'intégration standards doivent être déterministes, sans réseau, sans clés Kraken et sans Docker. Les services persistants utilisent H2 en mémoire avec le profil `test`; Eureka et la découverte de services y sont désactivés.

Exécuter toute la validation :

```bash
./scripts/test-all.sh
```

Le script couvre les six applications Maven et le frontend. Le module
autonome `risk-domain` se valide séparément :

```bash
cd risk-domain && mvn test
```

Ou par application :

```bash
cd trading-core && ./mvnw test
cd market-intelligence && ../trading-core/mvnw test
cd trading-os-web && npm run check
```

Une branche n'est intégrable que si les six suites applicatives Maven, la suite
`risk-domain`, les tests Angular et le build Angular passent. Les tests
`contextLoads` constituent seulement un smoke test : la couverture métier doit
progresser avec chaque fonctionnalité.

## Roadmap

- [x] Structure microservices et découverte Eureka
- [x] Authentification JWT et interface de connexion
- [x] Première intégration Kraken et synchronisation des comptes
- [x] Référentiel de marchés et affichage Angular
- [ ] Stabilisation Docker, healthchecks et migrations de base
- [x] Domaine de risque déterministe et tests métier associés
- [ ] Intégration complète du domaine Risk aux parcours de trades et d'exécution
- [ ] Routage Gateway de toutes les API publiques
- [ ] Profils de règles configurables, versionnables et indépendants des prop firms
- [x] Abonnements temps réel dynamiques
- [ ] Événements de marché distribuables entre instances
- [ ] Parcours complet préparation → risque → validation humaine → ordre broker idempotent
- [x] Market Detail avec OHLC, ticker, carnet d'ordres et transactions récentes
- [x] Première version du dashboard de compte
- [ ] Journal, historique d'equity et pages Positions/Analytics complets
- [ ] News Service et calendrier économique
- [x] Fondation d'orchestration Market Intelligence et première analyse déterministe
- [x] Gouvernance asynchrone et idempotente des `AnalysisExecution`
- [x] Première fondation de gestion et de réutilisation des artefacts en mémoire
- [ ] Intégration de l'`ArtifactResolver` dans l'orchestrateur et les capacités
- [x] Fondation locale du Capability Engine et planification par DAG
- [ ] Migration des capacités ADR-020 vers le contrat atomique ADR-023
- [ ] Intégration Planner → Engine dans le cycle `AnalysisExecution`
- [ ] Stockage durable et cohérence multi-instance des exécutions et artefacts
- [x] Modèle, repository local et requêtes d'observations Market Intelligence
- [ ] Persistance durable et réutilisation distribuée des observations Market Intelligence
- [x] Modèle d'opportunités, ranking et projections utilisateur
- [x] Trade Planning déterministe, versionnement et replanification
- [x] Risk Domain ADR-028
- [x] Autorisation déterministe des Trade Plans via le Risk Domain, exposée à travers le Gateway (Story 0003)
- [x] Execution Domain ADR-029
- [x] Architecture broker-neutre et provider Kraken ADR-030
- [ ] Tests contractuels sandbox et E2E déployé Trading Core → Broker Service → Kraken
- [ ] Passive Scanner, Active Scanner et surveillance des positions
- [ ] AI Engine explicable consommant les données normalisées

## Décisions d'architecture

Les décisions structurantes et la vision produit sont consignées dans les [ADR](docs/architecture/adr/ADR-001.md). Les ADR décrivent l'architecture cible ; leur statut `Accepted` signifie que la décision est adoptée, pas que son implémentation est terminée.

Les principaux axes couverts sont :

- vision produit, architecture microservices et approche AI-first ;
- responsabilités de Trading Core, Market Data, Broker et News Service ;
- profils de règles et Risk Engine déterministe ;
- modèle `MarketState` et abonnements temps réel dynamiques ;
- modélisation métier indépendante des fournisseurs ;
- architecture Angular réactive ;
- pipeline de décision avec validation humaine ;
- architecture pilotée par la valeur produit.
- orchestration Market Intelligence, gouvernance des exécutions et gestion des artefacts dérivés.

Les documents d'implémentation actuels sont :

- [ADR-020 — fondation Market Intelligence](docs/implementation/ADR-020-implementation.md) ;
- [ADR-021 — gouvernance des exécutions](docs/implementation/ADR-021-implementation.md) ;
- [ADR-022 — gestion des artefacts](docs/implementation/ADR-022-implementation.md) ;
- [ADR-023 — Capability Engine local](docs/implementation/ADR-023-implementation.md) ;
- [ADR-024 — gestion des credentials broker](docs/implementation/ADR-024-implementation.md) ;
- [ADR-025 — modèle d'observation](docs/implementation/ADR-025-implementation.md) ;
- [ADR-026 — opportunités de trading](docs/implementation/ADR-026-implementation.md) ;
- [ADR-027 — Trade Planning](docs/implementation/ADR-027-implementation.md) ;
- [ADR-028 — Risk Domain](docs/implementation/ADR-028-implementation.md) ;
- [ADR-029 — Execution Domain](docs/implementation/ADR-029-implementation.md) ;
- [ADR-030 — Broker Service Architecture](docs/implementation/ADR-030-implementation.md).

## Avertissement

Ce projet est un outil de simulation et d'assistance au trading. Il ne constitue pas un conseil financier.
