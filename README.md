# Trading OS

Trading OS est un assistant de trading intelligent destiné aux traders discrétionnaires. Il centralise les données de marché, les comptes broker, les règles de risque, la préparation, l'exécution contrôlée et l'analyse des trades afin de réduire la charge cognitive et d'améliorer la discipline.

Le produit n'est ni un bot autonome, ni une plateforme HFT, ni un outil limité aux challenges de prop firms. Les règles déterministes ont toujours priorité sur les recommandations de l'IA et toute exécution reste soumise à une validation humaine explicite.

Le projet est en développement actif. L'architecture microservices, l'authentification, la synchronisation Kraken, un premier moteur de risque et une interface Angular sont présents. Le prochain objectif est de stabiliser un parcours déterministe complet et reproductible avant d'introduire les couches Market Intelligence et IA.

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
| `trading-core` | 8081 | Utilisateurs, comptes, règles, trades et statistiques |
| `broker-service` | 8082 | Adaptateur Kraken privé : compte, positions et prix |
| `market-data` | 8083 | Référentiel de marchés, synchronisation et flux Kraken |
| `eureka-server` | 8761 | Découverte des services |
| `trading-web` | 4200 | Application Angular servie par Nginx |
| PostgreSQL | interne | Bases séparées `trading_os` et `market_data` |

Services prévus par l'architecture cible, mais pas encore implémentés :

| Service ou composant | Responsabilité cible |
| --- | --- |
| `news-service` | Calendrier économique, actualités et contexte macroéconomique normalisés |
| `ai-engine` | Interprétation, scénarios, explications et classement d'opportunités |
| Passive Scanner | Veille continue et détection d'événements sur un périmètre limité |
| Active Scanner | Analyse approfondie déclenchée par l'utilisateur |
| Position Monitoring | Surveillance des positions et recommandations sans exécution automatique |

La stack utilise Java 21, Spring Boot 4, Spring Cloud, PostgreSQL 16, Angular 21 et Docker Compose.

## État fonctionnel

- Authentification et autorisation JWT : disponible.
- Comptes broker et synchronisation Kraken : disponible, à durcir.
- Référentiel et affichage des marchés Kraken : disponible.
- Règles et moteur de risque : première version implémentée ; les profils configurables complets restent à construire.
- Cycle de vie local des trades et statistiques : API implémentée, intégration UI incomplète.
- Exécution réelle d'ordres : non implémentée ; le code enregistre actuellement les trades sans soumettre d'ordre au broker.
- Flux temps réel : prototype Kraken abonné en dur à `BTC/USD` ; gestion dynamique non implémentée.
- Dashboard : squelette.
- News Service, scanners, Market Intelligence et AI Engine : non commencés.

## Pipeline de décision cible

```text
Market Data
    → Analyse déterministe
    → Market Intelligence
    → Interprétation IA
    → Validation des règles et du risque
    → Validation humaine
    → Exécution par le Broker Service
```

Chaque couche enrichit les informations reçues sans remplacer les responsabilités des couches précédentes. Une recommandation doit rester explicable et un refus du moteur de risque doit être traçable.

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
SPRING_PROFILES_ACTIVE=prod
```

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

Ou par application :

```bash
cd trading-core && ./mvnw test
cd trading-os-web && npm run check
```

Une branche n'est intégrable que si les cinq suites Maven, les tests Angular et le build Angular passent. Les tests `contextLoads` constituent seulement un smoke test : la couverture métier doit progresser avec chaque fonctionnalité.

## Roadmap

- [x] Structure microservices et découverte Eureka
- [x] Authentification JWT et interface de connexion
- [x] Première intégration Kraken et synchronisation des comptes
- [x] Référentiel de marchés et affichage Angular
- [ ] Stabilisation Docker, healthchecks et migrations de base
- [ ] Couverture métier du moteur de risque et des trades
- [ ] Routage Gateway de toutes les API publiques
- [ ] Profils de règles configurables, versionnables et indépendants des prop firms
- [ ] Abonnements temps réel dynamiques et événements de marché distribuables
- [ ] Parcours complet préparation → risque → validation humaine → ordre broker idempotent
- [ ] Journal, Market Detail et dashboard complets
- [ ] News Service et calendrier économique
- [ ] Analyse déterministe et dépôt partagé de Market Intelligence
- [ ] Passive Scanner, Active Scanner et surveillance des positions
- [ ] AI Engine explicable consommant les données normalisées

## Décisions d'architecture

Les décisions structurantes et la vision produit sont consignées dans les [ADR](docs/ADR-001.md). Les ADR décrivent l'architecture cible ; leur statut `Accepted` signifie que la décision est adoptée, pas que son implémentation est terminée.

Les principaux axes couverts sont :

- vision produit, architecture microservices et approche AI-first ;
- responsabilités de Trading Core, Market Data, Broker et News Service ;
- profils de règles et Risk Engine déterministe ;
- modèle `MarketState` et abonnements temps réel dynamiques ;
- modélisation métier indépendante des fournisseurs ;
- architecture Angular réactive ;
- pipeline de décision avec validation humaine ;
- architecture pilotée par la valeur produit.

## Avertissement

Ce projet est un outil de simulation et d'assistance au trading. Il ne constitue pas un conseil financier.
