# ADR-030 — Notes d’implémentation

- **Statut :** Implémenté
- **Module :** `broker-service`
- **ADR :** `docs/adr/ADR-030.md`
- **Plan :** `docs/implementation/ADR-030-implementation-plan.md`
- **Runtime :** Java 21

## Périmètre

Cette implémentation transforme Broker Service en frontière technique
broker-neutre entre Trading Core et les fournisseurs externes. Elle complète
l’intégration Kraken existante avec une architecture par capacités, un registre
de providers et le contrat d’exécution attendu par ADR-029.

Broker Service possède exclusivement les responsabilités techniques :

- résolution du provider ;
- chargement sécurisé des credentials ;
- signature et transport HTTP ;
- mapping des requêtes et réponses ;
- soumission, annulation et recherche d’ordres ;
- traduction des erreurs ;
- résilience et observabilité techniques.

Les autorisations d’exécution, stratégies de retry, décisions de récupération,
événements métier et évaluations Risk restent dans Trading Core.

## Architecture

```text
Trading Core / BrokerExecutionPort
                │
                ▼
       Internal REST API v1
                │
                ▼
       Application Services
                │
                ▼
      BrokerProviderResolver
                │
                ▼
      BrokerProviderRegistry
                │
                ▼
        KrakenBrokerProvider
                │
                ▼
   specialized provider capabilities
                │
                ▼
 credentials → resilience → REST → Kraken
```

Les contrats neutres et interfaces de capacités se trouvent dans
`broker.domain`. Ils ne dépendent ni de Spring, ni de JPA, ni de Jackson, ni de
Kraken. L’orchestration appartient à `broker.application`. Les détails Kraken,
HTTP, secrets, sécurité et monitoring restent dans `broker.infrastructure`.

## Contrats broker-neutres

`BrokerModels` définit des contrats immuables pour :

- `ExecutionRequest` et ses résultats `Acknowledged`, `Rejected`, `Unknown` ;
- `ReconciliationRequest` et ses résultats `ReconciledOrder`,
  `ConfirmedAbsent`, `Inconsistent` ;
- `AccountSnapshot` ;
- `PositionSnapshot` ;
- `OrderSnapshot` ;
- `FillSnapshot`.

Les commandes valident leurs invariants à la construction. Une quantité doit
être positive et un ordre limite exige un prix strictement positif. Aucun type
Kraken ou HTTP ne traverse la frontière publique.

## Architecture par capacités

Les responsabilités provider sont séparées dans les interfaces suivantes :

- `AuthenticationCapability` ;
- `AccountCapability` ;
- `PositionCapability` ;
- `OrderCapability` ;
- `ExecutionCapability` ;
- `ReconciliationCapability`.

`BrokerProvider` expose une résolution générique et optionnelle de capacité.
Un futur provider peut donc ne fournir qu’un sous-ensemble des capacités sans
implémenter des opérations factices.

`KrakenBrokerProvider` assemble les capacités Kraken. Il ne contient aucune
orchestration métier.

## Registre et sélection du provider

`BrokerProviderRegistry` :

- découvre les providers injectés par Spring ;
- les indexe par `BrokerProviderId` ;
- refuse deux providers portant la même identité ;
- retourne une erreur neutre pour un provider absent ;
- publie la liste immuable des providers disponibles.

`BrokerProviderResolver` sélectionne le provider depuis le
`brokerAccountId`. Il charge la `BrokerConnection`, exige l’état `CONNECTED`
et vérifie la présence d’une référence de credential active avant de résoudre
le provider.

Trading Core ne transmet et ne connaît jamais un type de provider concret.

## Credentials par compte

Toutes les opérations privées utilisent exclusivement les credentials chiffrés
associés au compte broker demandé.

```text
brokerAccountId
      │
      ▼
BrokerConnection.activeCredentialReference
      │
      ▼
BrokerCredentialSource
      │
      ▼
temporary CredentialMaterial
      │
      ▼
signed Kraken request
      │
      ▼
CredentialMaterial.close()
```

`KrakenCredentialSession` ouvre le secret seulement pendant l’opération. Le
matériel sensible est détruit via `AutoCloseable` dès la fin de l’appel, y
compris lorsqu’une exception survient.

Le nouveau pipeline ne possède aucun fallback silencieux vers les propriétés
globales `KRAKEN_API_KEY` et `KRAKEN_API_SECRET`. Les composants historiques
restent disponibles pour compatibilité, mais ne participent pas aux nouvelles
capacités ADR-030.

## Authentification Kraken

`KrakenRequestSigner` produit les en-têtes `API-Key` et `API-Sign` à partir des
credentials du compte courant. Les copies temporaires des clés sont effacées
après signature.

`KrakenRestProviderClient` :

- génère des nonces monotones et thread-safe ;
- encode les corps privés en
  `application/x-www-form-urlencoded` ;
- applique la signature ;
- exécute la requête avec le `RestClient` configuré ;
- limite les payloads Kraken à l’infrastructure provider ;
- traduit les erreurs avant leur remontée.

## Capacités Kraken

`KrakenCapabilities` fournit :

- vérification technique des credentials ;
- récupération des balances ;
- récupération et normalisation des positions ouvertes ;
- récupération des ordres ouverts et clôturés ;
- soumission d’un ordre ;
- annulation d’un ordre ;
- réconciliation d’une exécution.

Les transformations sont déterministes et ne prennent aucune décision de
trading.

## Idempotence technique

L’idempotence métier reste la responsabilité d’ADR-029. Broker Service
transporte néanmoins un identifiant provider stable lorsque Kraken le permet.

La clé d’idempotence reçue est transformée de manière déterministe en UUID et
transmise comme `cl_ord_id`. Une même clé produit donc toujours le même
identifiant client Kraken.

Broker Service ne considère pas cette propriété comme une autorisation de
retry automatique.

## Soumission d’ordre

Une `ExecutionRequest` neutre est traduite en requête Kraken `AddOrder` :

| Contrat neutre | Kraken |
|---|---|
| `instrument` | `pair` |
| `side` | `type` |
| `orderType` | `ordertype` |
| `quantity` | `volume` |
| `limitPrice` | `price` |
| clé d’idempotence | `cl_ord_id` |

Un identifiant `txid` explicite produit `Acknowledged`. Une indisponibilité de
transport produit `Unknown`, car Broker Service ne peut pas déterminer si
l’ordre a été accepté. Une erreur provider explicite produit un résultat
standardisé sans exposer le payload Kraken.

## Réconciliation

La réconciliation recherche le `cl_ord_id` stable dans les ordres ouverts puis
clôturés.

| Observation technique | Résultat neutre |
|---|---|
| un ordre correspondant | `ReconciledOrder` |
| aucun ordre correspondant | `ConfirmedAbsent` |
| plusieurs ordres | `Inconsistent` |
| communication incertaine | `Inconsistent` |

Broker Service ne décide jamais si une nouvelle tentative doit être créée.
Cette interprétation appartient au pipeline de récupération ADR-029.

## Traduction des erreurs

Les erreurs infrastructure sont normalisées par `BrokerExceptions` :

- `BrokerAuthenticationException` ;
- `BrokerRateLimitException` ;
- `BrokerUnavailableException` ;
- `BrokerProtocolException` ;
- `UnsupportedBrokerProviderException`.

Les erreurs Kraken, statuts HTTP et exceptions `RestClient` ne quittent pas
l’infrastructure provider. `BrokerExceptionHandler` les convertit en réponses
HTTP neutres et structurées.

## Résilience

`KrakenResilientClient` protège chaque appel provider avec :

- un bulkhead borné à vingt appels concurrents ;
- un circuit breaker ouvert trente secondes après cinq indisponibilités
  consécutives ;
- une remise à zéro après une communication réussie ;
- un état technique `AVAILABLE`, `DEGRADED` ou `UNAVAILABLE`.

Aucun retry automatique n’est appliqué aux opérations modifiant l’état broker,
notamment `AddOrder` et `CancelOrder`. Cette garantie empêche une
infrastructure de masquer une issue de soumission incertaine.

Les timeouts de connexion et de lecture restent configurés par
`KrakenProperties` et le `RestClient` existant.

## Services d’application

Les services suivants orchestrent la résolution et l’appel d’une capacité :

- `GetAccountService` ;
- `GetPositionsService` ;
- `GetOrdersService` ;
- `ExecuteOrderService` ;
- `CancelOrderService` ;
- `ReconcileExecutionService`.

Ils ne contiennent ni signature, ni HTTP, ni mapping Kraken, ni décision
métier.

## API REST interne

Les endpoints d’exécution correspondent exactement au client Feign ADR-029 :

| Méthode | Endpoint | Responsabilité |
|---|---|---|
| `POST` | `/internal/v1/executions` | soumettre un ordre |
| `POST` | `/internal/v1/executions/reconcile` | rechercher une exécution |
| `POST` | `/internal/v1/executions/{externalOrderId}/cancel` | annuler un ordre |

Les endpoints de consultation sont :

| Méthode | Endpoint |
|---|---|
| `GET` | `/internal/v1/broker-accounts/{id}` |
| `GET` | `/internal/v1/broker-accounts/{id}/positions` |
| `GET` | `/internal/v1/broker-accounts/{id}/orders` |

Les contrôleurs valident les DTO, délèguent aux services d’application et
retournent toujours un `ResponseEntity`. Ils n’accèdent jamais directement à
Kraken.

L’API reste protégée par la configuration JWT stateless existante. Les secrets
provider et les tokens de service demeurent deux mécanismes indépendants.

## Observabilité et santé

`BrokerOperationsMetrics` collecte par type d’opération :

- le nombre de requêtes ;
- le nombre d’échecs techniques ;
- la latence totale en nanosecondes.

Les soumissions et réconciliations produisent des logs structurés contenant
les identifiants techniques, jamais les credentials.

Spring Boot Actuator est activé pour `health` et `info`. Le health indicator
`brokerProviders` publie les providers enregistrés et l’état du circuit Kraken.
Les probes de liveness et readiness sont activées.

## Déterminisme

Les transformations dépendent uniquement :

- de la requête reçue ;
- de la connexion et des credentials du compte ;
- de la réponse provider ;
- du `Clock` injecté ;
- du générateur de nonce monotone.

Le provider ne consulte aucune donnée de marché supplémentaire et n’assigne
aucune signification métier aux faits techniques.

## Tests

Les tests ADR-030 couvrent notamment :

- enregistrement et résolution d’un provider ;
- rejet des providers dupliqués ou absents ;
- immutabilité et validation des contrats ;
- isolation du domaine vis-à-vis de Spring, JPA, Jackson et Kraken ;
- stabilité du `cl_ord_id` ;
- soumission Kraken acquittée ;
- résultat inconnu lors d’une indisponibilité ;
- réconciliation par clé d’idempotence.

Commande de la suite complète :

```bash
cd broker-service
mvn test
```

Résultat de la suite complète vérifié le 1er août 2026 :

```text
Tests run: 41
Failures: 0
Errors: 0
Skipped: 0
```

Le packaging est validé avec :

```bash
mvn -q -DskipTests package
```

La suite complète passe. La JVM affiche encore un avertissement concernant
l'auto-attachement du mock maker inline Mockito/Byte Buddy, sans échec de test.

## Conformité à ADR-030

| Décision ADR-030 | Implémentation |
|---|---|
| Responsabilité technique uniquement | capacités et services sans logique Trading Core |
| Contrats broker-neutres | records et résultats scellés |
| Architecture par capacités | capacités optionnelles résolues par type |
| Authentification isolée | session de credentials par compte et signer Kraken |
| Mapping provider isolé | package Kraken dédié |
| Traduction des erreurs | exceptions neutres et handler HTTP |
| Résilience | timeouts, bulkhead, circuit breaker, aucun retry unsafe |
| Observabilité | logs, métriques et health indicator |
| Adaptateur synchrone | API REST interne request/response |
| Communication Trading Core | endpoints compatibles avec le client ADR-029 |
| Extensibilité | registre dynamique et capacités optionnelles |

## Éléments différés

Les éléments suivants nécessitent un environnement externe et ne sont pas
simulés comme des fonctionnalités locales :

- test contractuel contre le sandbox Kraken officiel ;
- test end-to-end déployé Trading Core → Broker Service → Kraken ;
- providers supplémentaires ;
- WebSocket, FIX et gRPC ;
- enrichissement des fills à partir des flux asynchrones Kraken.

Ces éléments ne changent pas les frontières architecturales mises en place.

## Durcissement post-audit

Le durcissement intégré à `main` complète l'implémentation initiale sur les
points suivants :

- une réponse Kraken ambiguë après `AddOrder` produit désormais `Unknown` et
  jamais un faux rejet définitif ;
- les rejets explicites distinguent authentification, autorisation, ordre
  invalide, fonds insuffisants, ordre absent, quota, indisponibilité et erreur
  de protocole ;
- les messages provider ne sont plus exposés par l'API d'erreur ;
- les timeouts configurés sont effectivement appliqués au client HTTP ;
- seules les lectures sûres disposent d'un retry technique borné ;
- les écritures `AddOrder` et `CancelOrder` ne sont jamais retentées ;
- un quota local Kraken, un bulkhead et un circuit breaker protègent les appels ;
- le health indicator agrège des contributeurs provider-neutres et passe à
  `DOWN` lorsqu'un provider est indisponible ;
- les métriques Micrometer/Prometheus, observations et traces OpenTelemetry
  remplacent les seuls compteurs locaux ;
- `X-Correlation-ID` est propagé de Trading Core jusqu'au client provider ;
- les interactions produisent un audit technique structuré sans secrets ;
- des tests couvrent le contrat API, la sécurité JWT, les réponses HTTP Kraken,
  la traduction d'erreurs, les retries sûrs, l'absence de retry des écritures,
  le circuit breaker, les métriques, le health et la corrélation.

Le test contre le sandbox officiel et l'E2E déployé restent des validations
d'environnement : ils exigent des services démarrés et des credentials Kraken
de test et ne font pas partie de la suite locale déterministe.
