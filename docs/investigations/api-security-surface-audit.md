# Trading OS - Rapport d'investigation API et securite

Date: 2026-09-15
Branche auditee: `main`
Revision: `8eb75431749c3ca5c8b6dd9384b3fe1d9f61b3be`
Statut: investigation terminee, aucune correction appliquee

## 1. Objectif

Auditer la surface HTTP/WebSocket actuelle de Trading OS avant la prochaine Story de hardening. L'audit couvre:

- les controleurs et mappings exposes par service;
- les routes du Gateway;
- les consommateurs Angular et Feign;
- les controles d'authentification et d'ownership;
- les endpoints internes et management;
- les tests de routage et de securite existants.

Le rapport ne modifie ni les contrats, ni le code applicatif, ni les ADR. Les recommandations doivent etre implementees dans une Story approuvee.

## 2. Etat de reference

- Worktree propre au debut et a la fin de l'investigation.
- `origin/main` pointe sur la meme revision que `HEAD`.
- Story0043 est fusionnee; Story0044 n'est pas creee.
- Les autorites metier a preserver sont `BROKER` pour les positions LIVE et `TRADING_CORE` pour les positions PAPER.
- Les decisions deterministes de risque, l'attribution `profileId + semanticVersion` et la validation humaine restent hors de portee d'un contournement API.

## 3. Inventaire synthetique

L'inventaire des controleurs represente environ 99 mappings de methodes, hors declarations Feign et appels provider:

| Service | Surface | Volume indicatif | Observation |
|---|---|---:|---|
| Gateway | routes publiques | 10 familles | Les endpoints `/internal/**` ne sont pas routes volontairement. |
| Trading Core | public + internal | 47 | Auth locale active, ownership inegal selon les anciens controleurs. |
| Broker Service | public + internal | 21 | Auth JWT locale active; endpoints broker sensibles. |
| Market Data | public + internal + WebSocket | 8 + 1 WS | Pas de chaine Spring Security locale visible. |
| Market Intelligence | public + internal | 23 | Pas de chaine Spring Security locale visible; plusieurs acteurs viennent des donnees de requete. |

Les principaux prefixes sont:

- Gateway: `/api/v1/users/**`, `/api/v1/accounts/**`, `/api/v1/broker-accounts/**`, `/api/v1/risk-profiles/**`, `/api/v1/trade-plans/**`, `/api/v1/opportunities/**`, `/api/v1/markets/**`, `/api/v1/intelligence/**`, `/api/v1/executions/**` et `/ws/market-data`.
- Trading Core: `/api/v1/...`, mais aussi `/executions/**` et `/internal/**`.
- Market Intelligence: `/api/v1/intelligence/**`, `/api/v1/opportunities/**`, mais aussi `/trade-plans/**`.
- Broker Service: `/api/v1/broker/**`, `/api/v1/broker-accounts/**` et `/internal/v1/**`.
- Market Data: `/api/v1/markets/**` et `/internal/**`.

## 4. Findings prioritaires

### F-01 - Operations Trade sans ownership explicite - critique

`TradeController` demande seulement une authentification pour plusieurs operations, puis appelle des services qui ne recoivent pas l'acteur:

- `GET /api/v1/trades/{tradeId}`;
- `GET /api/v1/trades?accountId=...`;
- `PATCH /api/v1/trades/{tradeId}/stop-loss`;
- `PATCH /api/v1/trades/{tradeId}/take-profit`.

`TradingServiceImpl` charge les trades par identifiant ou compte sans verifier l'utilisateur. Les deux endpoints PATCH modifient directement le trade trouve. Un utilisateur authentifie pourrait donc lire ou modifier les donnees d'un autre compte si un identifiant est connu.

Preuves:

- `trading-core/src/main/java/com/hope/trading/trading_core/controller/TradeController.java:48-68,107-128`;
- `trading-core/src/main/java/com/hope/trading/trading_core/service/TradingService.java:18-21`;
- `trading-core/src/main/java/com/hope/trading/trading_core/service/TradingServiceImpl.java:162-200`.

Classification: `DELEGATE`, apres definition humaine de la matrice d'ownership et ajout de tests multi-utilisateurs.

### F-02 - Secret JWT de repli faible et previsible - critique

Trading Core accepte `default-secret-must-change` lorsque `JWT_SECRET` n'est pas defini. Ce comportement permettrait de forger des tokens dans un environnement mal configure.

Preuve: `trading-core/src/main/resources/application.properties:22-24`.

Recommandation: rendre le secret obligatoire en production et faire echouer le demarrage si la valeur est absente ou connue comme valeur de developpement.

Classification: `DELEGATE`.

### F-03 - Entite User retournee directement avec le mot de passe - critique

`GET /api/v1/users/{id}` retourne directement `User`. L'entite contient le champ `password` et ne porte aucune exclusion de serialisation. Le mot de passe hash peut donc etre expose par l'API.

Preuves:

- `trading-core/src/main/java/com/hope/trading/trading_core/controller/UserController.java:51-55`;
- `trading-core/src/main/java/com/hope/trading/trading_core/model/User.java:25-36`.

Recommandation: retourner un DTO public sans secret et ajouter un test de contrat garantissant l'absence de `password`.

Classification: `DELEGATE`.

### F-04 - Desalignement des routes Execution - eleve

Le controleur Trading Core expose `/executions/**`, alors que:

- le Gateway route `/api/v1/executions/**`;
- Angular appelle `/api/v1/executions/**`.

Les tests de Gateway valident la route abstraite, mais pas le mapping reel du controleur downstream. Le parcours d'execution peut donc echouer en integration.

Preuves:

- `trading-core/src/main/java/com/hope/trading/trading_core/execution/api/ExecutionController.java:17-19`;
- `gateway/src/main/java/com/hope/trading/gateway/config/GatewayRouteConfig.java:95-98`;
- `trading-os-web/src/app/core/services/execution.service.ts:15-48`.

Classification: `DELEGATE`, avec test Gateway vers un downstream reel ou stub conforme.

### F-05 - APIs publiques Trading Core non routees - eleve

Le Gateway ne route pas plusieurs controleurs exposes sous des prefixes publics:

- `/api/v1/trades/**`;
- `/api/v1/rules/**`;
- `/api/v1/trade-planning-profiles/**`;
- `/api/v1/accounts/{accountId}/positions/close/**`;
- `/api/analytics/**`.

Les routes `/api/v1/accounts/**` ne couvrent pas `/api/analytics/**`. Il existe donc une divergence entre la surface declaree des services et la surface accessible par le point d'entree officiel.

Preuves:

- `gateway/src/main/java/com/hope/trading/gateway/config/GatewayRouteConfig.java:46-98`;
- controleurs correspondants dans `trading-core/src/main/java/com/hope/trading/trading_core/`.

Classification: `PAIR` pour decider quels anciens endpoints restent publics, puis `DELEGATE` pour l'implementation.

### F-06 - Surface legacy Market Intelligence non protegee et acteur fourni par requete - critique

Market Intelligence ne contient pas de configuration Spring Security locale visible dans `src/main`. La surface legacy `/trade-plans/**` accepte `actorId` dans le body pour creation/replanification. Les endpoints internes de decision acceptent aussi `actorId` dans le body ou en query parameter.

Les endpoints `/api/v1/intelligence/**` utilisent `X-Actor-Id`. Le Gateway reecrit ce header pour ce prefix, mais une requete directe au service ou un appel interne non authentifie peut fournir une autre identite.

Preuves:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/TradePlanController.java:12-57`;
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/InternalTradePlanDecisionController.java:33-47`;
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/MarketIntelligenceController.java:22,86-133,164-180`;
- `gateway/src/main/java/com/hope/trading/gateway/security/AuthenticatedActorHeaderFilter.java:14-32`.

Recommandation: definir explicitement un modele de confiance inter-services, authentifier localement les appels, et ne jamais prendre l'acteur metier d'un payload lorsque le contexte JWT est disponible.

Classification: `PAIR`, car cela touche la frontiere Gateway/services et peut necessiter un ADR si une nouvelle authentification inter-services est choisie.

### F-07 - Endpoints internes Market Data sans protection locale visible - eleve

Market Data ne contient pas de chaine Spring Security locale visible. Ses endpoints `/internal/markets/prices/snapshot` et `/internal/v1/valuation-snapshots/batch` sont donc dependants de la topologie reseau plutot que d'une authentification applicative. Le WebSocket accepte toutes les origines avec `setAllowedOriginPatterns("*")`.

Preuves:

- `market-data/src/main/java/com/hope/trading/market_data/controller/InternalMarketController.java`;
- `market-data/src/main/java/com/hope/trading/market_data/controller/InternalValuationController.java`;
- `market-data/src/main/java/com/hope/trading/market_data/config/MarketDataWebSocketConfiguration.java:25-30`.

Classification: `DELEGATE`, apres decision sur le modele public market-data et la protection des flux internes.

### F-08 - Ports de services exposes hors Gateway - moyen a eleve

Docker Compose publie directement Trading Core, Broker Service, Eureka et Market Data:

- `17081:8081`;
- `17082:8082`;
- `17084:8761`;
- `17083:8083`.

Cela permet de contourner les politiques de routage du Gateway. Trading Core et Broker Service ont une securite locale, mais les services sans securite locale et les endpoints management doivent etre examines dans ce contexte.

Preuve: `docker-compose.yml:77-78,110-112,121-122,141-142,166-167`.

Classification: `PAIR` pour distinguer exposition de developpement, reseau interne et exposition de production.

## 5. Points rassurants

- Gateway exige l'authentification pour toute route non explicitement publique.
- Trading Core est stateless et exige un JWT hors login/register/documentation.
- Broker Service exige un JWT hors `/actuator/health`.
- Les commandes d'execution et de fermeture utilisent des cles d'idempotence dans les contrats recents.
- Les endpoints Execution recents appliquent `requireOwned` pour les operations par identifiant.
- Les positions et le dashboard verifient la relation compte/utilisateur et la coherence du BrokerAccount.
- Les endpoints internes ne sont pas routes par la table de routes publique du Gateway.

Ces protections ne compensent pas les controleurs legacy et les services sans protection locale identifies ci-dessus.

## 6. Validation executee

Commandes executees avec succes:

```text
gateway: mvn test -Dtest=GatewayExecutionRouteTest,GatewayMarketIntelligenceRouteTest,GatewayDownstreamRoutingIntegrationTest
Resultat: 15 tests, 0 echec

trading-core: mvn test -Dtest=JwtSecurityBoundaryTest
Resultat: 8 tests, 0 echec

broker-service: mvn test -Dtest=BrokerApiSecurityIntegrationTest
Resultat: 3 tests, 0 echec

git diff --check
Resultat: succes
```

Limite: ces tests ne prouvent pas le parcours complet Gateway -> controleur Execution reel et ne couvrent pas une matrice d'ownership entre deux utilisateurs.

## 7. Story recommandee

### Story: API Surface Hardening and Ownership Enforcement

Objectif: rendre la surface API coherente, authentifiee et owner-scoped avant tout nouveau parcours trader ou Research Lab.

Scope propose:

- corriger et tester les prefixes publics, en particulier Execution;
- decider et documenter les APIs legacy a supprimer, router ou rendre internes;
- remplacer les entites exposees par des DTOs sans secrets;
- appliquer l'ownership a toutes les lectures et mutations Trade;
- supprimer les secrets JWT de repli en production;
- definir l'authentification des services Market Intelligence et Market Data;
- remplacer les acteurs fournis par payload/header par un contexte authentifie fiable;
- restreindre les origines WebSocket;
- ajouter des tests contractuels Gateway/downstream et des tests IDOR multi-utilisateurs;
- verifier la politique d'exposition des ports et de l'Actuator.

Out of scope:

- nouvelle strategie de trading;
- moteur IA;
- Research Lab;
- changement de l'autorite des positions LIVE/PAPER;
- modification des regles deterministes de risque;
- ajout de retries automatiques sur les ecritures broker.

## 8. Decisions a obtenir avant implementation

1. Le Gateway est-il le seul point d'entree autorise en production, ou les services doivent-ils rester directement accessibles?
2. Quel mecanisme d'authentification inter-services doit proteger Market Intelligence et Market Data?
3. Les APIs `/api/v1/trades`, `/api/v1/rules`, `/api/v1/trade-planning-profiles` et `/api/analytics` sont-elles encore des contrats publics supportes?
4. La surface legacy `/trade-plans/**` doit-elle etre supprimee ou migree vers Trading Core?
5. Faut-il ajouter un ADR pour la confiance inter-services et la propagation d'acteur?

## 9. Conclusion

Le socle recent d'authentification et d'idempotence est present, mais la surface API reste heterogene. Les risques les plus importants sont l'absence d'ownership sur plusieurs operations Trade, l'exposition possible du hash de mot de passe, le secret JWT de repli et les surfaces Market Intelligence/Market Data basees sur la confiance reseau ou des identifiants fournis par la requete.

La prochaine action recommandee est de faire approuver une Story de hardening API avant d'etendre le parcours PAPER ou de commencer le Research Lab.
