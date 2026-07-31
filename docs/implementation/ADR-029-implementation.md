# ADR-029 — Notes d’implémentation

- **Statut :** Implémenté
- **Module :** `trading-core`
- **ADR :** `docs/adr/ADR-029.md`
- **Plan :** `docs/implementation/ADR-029-implementation-plan.md`
- **Runtime :** Java 21

## Périmètre

Cette implémentation introduit un domaine Execution explicite dans Trading
Core. Il transforme un TradePlan approuvé par le domaine Risk en une
soumission broker traçable, idempotente et récupérable.

Le domaine Execution prend en charge :

- l’autorisation logique d’exécuter un TradePlan approuvé ;
- le cycle de vie d’une exécution ;
- les tentatives de soumission ;
- l’idempotence ;
- la communication avec le broker via un port ;
- le traitement des réponses connues et incertaines ;
- la réconciliation avant retry ;
- l’annulation ;
- l’audit et les événements d’exécution.

Il ne réalise aucune analyse de marché, sélection de stratégie, optimisation
de portefeuille ou évaluation du risque.

## Architecture

```text
TradePlanReference + RiskApprovalReference
                    │
                    ▼
         CreateExecutionIntentService
                    │
                    ▼
             ExecutionIntent
                    │
                    ▼
             ExecuteTradeService
                    │
                    ▼
        ExecutionValidationStep
                    │
                    ▼
      IdempotencyVerificationStep
                    │
                    ▼
      ExecutionAttemptCreationStep
                    │
                    ▼
          BrokerSubmissionStep
                    │
                    ▼
    BrokerResponseProcessingStep
                    │
                    ▼
       ExecutionFinalizationStep
                    │
                    ▼
      ExecutionAttempt + BrokerOrder
                    │
                    ▼
       immutable execution events
```

Les règles métier et les agrégats se trouvent dans `execution.domain`.
L’orchestration se trouve dans `execution.application`. Spring, JPA, Feign,
les contrôleurs HTTP et l’observabilité restent dans les adaptateurs
d’infrastructure ou la couche API.

## Agrégats

### ExecutionIntent

`ExecutionIntent` représente l’autorisation métier d’exécuter une version
précise d’un TradePlan.

Il possède :

- une identité interne stable ;
- une référence de TradePlan avec version ;
- une référence d’approbation Risk ;
- une clé d’idempotence immuable ;
- l’initiateur et le compte broker ;
- les paramètres d’exécution broker-neutres ;
- son état courant ;
- au plus une tentative active ;
- une date d’expiration ;
- une version destinée au verrouillage optimiste ;
- ses événements métier non encore publiés.

Les transitions sont explicites. Un état terminal ne peut pas être réactivé.
Une tentative active doit être terminée ou réconciliée avant d’être
remplacée.

### ExecutionAttempt

`ExecutionAttempt` représente une soumission au broker.

Une tentative conserve :

- son numéro monotone pour l’intention concernée ;
- son statut indépendant ;
- son identifiant de corrélation broker éventuel ;
- le résultat de soumission ;
- ses timestamps ;
- sa version.

La tentative est créée et persistée avant l’appel broker. Ce séquencement est
essentiel : une interruption du processus ne doit jamais produire un appel
externe sans trace locale préalable.

### BrokerOrder

`BrokerOrder` représente l’ordre connu du côté broker sans exposer les types
d’un fournisseur particulier.

Son cycle de vie couvre notamment :

- l’acquittement ;
- le rejet ;
- l’exécution partielle ;
- l’exécution complète ;
- l’annulation.

Les fills sont modélisés comme des valeurs métier immuables contenant une
identité, une quantité, un prix, des frais et un timestamp.

## Value objects et statuts

Les identités suivantes sont typées et immuables :

- `ExecutionIntentId` ;
- `ExecutionAttemptId` ;
- `BrokerOrderId` ;
- `IdempotencyKey`.

Les cycles de vie restent indépendants :

- `ExecutionStatus` décrit l’intention logique ;
- `AttemptStatus` décrit une tentative de communication ;
- `BrokerOrderStatus` décrit l’ordre externe.

Cette séparation évite de déduire implicitement l’état d’un agrégat depuis
celui d’un autre.

## Validation et approbation Risk

Une intention référence obligatoirement :

- un TradePlan identifié et versionné ;
- une évaluation Risk identifiée ;
- une décision `APPROVED` ou `APPROVED_WITH_WARNINGS` ;
- la date de cette approbation.

`ExecutionValidationService` vérifie avant soumission :

- que l’intention n’est pas expirée ;
- que son état permet une exécution ;
- qu’aucune tentative n’est déjà active ;
- que l’approbation Risk autorise l’exécution.

Le domaine Execution consomme cette approbation comme une autorisation. Il ne
recalcule et ne réinterprète aucune métrique de risque.

## Idempotence

Une clé d’idempotence stable appartient à chaque `ExecutionIntent`.

La protection est appliquée à plusieurs niveaux :

1. `IdempotencyService` refuse une création logique dupliquée ;
2. le pipeline revérifie la cohérence avant soumission ;
3. la base impose une contrainte unique sur la clé ;
4. la combinaison TradePlan/version est également unique ;
5. une intention ne peut avoir qu’une tentative active.

Un doublon rejeté incrémente la métrique opérationnelle correspondante.

## Pipeline d’exécution

`ExecuteTradeService` matérialise le pipeline défini par ADR-029.

### 1. Validation

`ExecutionValidationStep` valide les invariants et fait passer une intention
nouvelle à l’état `VALIDATED`.

### 2. Vérification d’idempotence

`IdempotencyVerificationStep` confirme que l’intention chargée correspond
toujours à la clé métier persistée.

### 3. Création de la tentative

`ExecutionAttemptCreationStep` :

- calcule le prochain numéro de tentative ;
- crée l’agrégat ;
- le persiste avant tout effet externe ;
- l’associe comme tentative active.

### 4. Soumission broker

`BrokerSubmissionStep` construit une requête broker-neutre et appelle
`BrokerExecutionPort`.

### 5. Traitement de la réponse

`BrokerResponseProcessingStep` distingue explicitement :

- `Acknowledged` ;
- `Rejected` ;
- `Unknown`.

### 6. Finalisation

`ExecutionFinalizationStep` met à jour les agrégats, persiste un éventuel
`BrokerOrder`, libère la tentative active lorsqu’il est sûr de le faire et
produit les événements et métriques appropriés.

## Résultat de soumission inconnu

Un timeout ou une erreur de transport ne prouve pas que le broker a refusé la
requête. L’implémentation ne transforme donc jamais automatiquement une
incertitude technique en échec retryable.

```text
broker outcome unknown
          │
          ▼
SUBMISSION_OUTCOME_UNKNOWN
          │
          ▼
retry interdit
          │
          ▼
réconciliation obligatoire
```

Cette règle empêche la création d’ordres dupliqués après une réponse perdue.

## Pipeline de récupération

`RecoverExecutionService` utilise un pipeline distinct :

```text
RecoverableExecutionDiscoveryStep
                │
                ▼
      ExecutionInspectionStep
                │
                ▼
        RecoveryStrategyStep
                │
                ▼
      BrokerReconciliationStep
                │
                ▼
       RecoveryFinalizationStep
```

Le pipeline :

1. recherche les intentions récupérables ;
2. inspecte leur dernière tentative ;
3. choisit une stratégie déterministe ;
4. interroge le broker ;
5. finalise selon le résultat.

Résultats possibles :

| Résultat broker | Conséquence |
|---|---|
| Ordre retrouvé | création du `BrokerOrder`, intention terminée |
| Absence confirmée | tentative réconciliée, intention de nouveau exécutable |
| État incohérent | intention placée en `RECOVERY_BLOCKED` |

Une absence confirmée est la seule situation incertaine permettant ensuite
une nouvelle tentative sûre.

## Retry et annulation

`RetryExecutionService` :

- refuse les états terminaux ;
- refuse tout retry tant qu’une réconciliation est requise ;
- conserve une numérotation monotone des tentatives ;
- publie un événement de retry planifié ou abandonné.

`CancelExecutionService` annule l’ordre externe lorsqu’il existe, puis met à
jour l’intention et publie l’événement correspondant. L’appel broker reste
derrière `BrokerExecutionPort`.

## Port broker

`BrokerExecutionPort` définit les opérations nécessaires :

- soumission ;
- annulation ;
- réconciliation.

Ses requêtes et réponses utilisent exclusivement des types propres au domaine
Execution. `BrokerExecutionAdapter` et ses mappers traduisent ces contrats
vers le client Feign du Broker Service.

Les erreurs de transport retryables sont traduites en résultat inconnu. Elles
ne déclenchent aucun retry automatique au niveau de l’adaptateur.

## Persistance

La couche infrastructure fournit :

- `ExecutionIntentEntity` ;
- `ExecutionAttemptEntity` ;
- `BrokerOrderEntity` ;
- `ExecutionEventEntity` ;
- les repositories Spring Data ;
- les mappers domaine/JPA ;
- les adaptateurs des repository ports.

Les contraintes structurelles comprennent :

- unicité de la clé d’idempotence ;
- unicité du couple TradePlan/version ;
- unicité du numéro de tentative par intention ;
- unicité de l’ordre broker par intention ;
- unicité de l’identifiant d’ordre externe ;
- verrouillage optimiste via `@Version`.

Les événements d’audit utilisent une table séparée append-only au niveau de
l’application. Ils ne sont pas mélangés aux agrégats métier.

## Événements et audit

`ExecutionEvent` est un contrat scellé composé de valeurs immuables.

Il couvre notamment :

- création et validation ;
- création, démarrage et résultat d’une tentative ;
- soumission et réponse broker ;
- association d’un ordre ;
- planification ou abandon d’un retry ;
- début, réussite ou blocage d’une récupération ;
- annulation.

`PersistentExecutionEventPublisher` persiste chaque événement avec son type,
son instant, l’intention concernée et une représentation de son payload. Il
produit également un log structuré.

## API REST

`ExecutionController` expose :

| Méthode | Endpoint | Responsabilité |
|---|---|---|
| `POST` | `/executions` | créer une intention |
| `POST` | `/executions/{id}/execute` | lancer sa soumission |
| `GET` | `/executions/{id}` | consulter une intention |
| `GET` | `/executions` | lister les intentions de l’utilisateur |
| `POST` | `/executions/{id}/retry` | relancer une intention éligible |
| `POST` | `/executions/{id}/cancel` | annuler |
| `POST` | `/executions/recovery` | lancer la récupération |

Les DTO API sont séparés des agrégats. Jakarta Validation contrôle les
entrées. Les accès unitaires et les listes sont filtrés par l’identité de
l’utilisateur authentifié.

`ExecutionExceptionHandler` traduit les exceptions métier en réponses HTTP
sans introduire de dépendance HTTP dans le domaine.

## Observabilité

`ExecutionOperationsMetrics` expose des compteurs pour :

- créations ;
- succès ;
- échecs ;
- annulations ;
- doublons empêchés ;
- retries planifiés ;
- récupérations ;
- résultats de soumission inconnus.

Un contrôleur interne fournit un snapshot de ces compteurs. Les métriques ne
participent à aucune décision métier.

## Déterminisme

Le comportement métier dépend uniquement :

- de l’état persisté des agrégats ;
- des commandes reçues ;
- de la réponse explicite du port broker ;
- du `Clock` injecté ;
- du générateur d’identifiants injecté.

Les tests remplacent horloge, repositories, broker et générateur
d’identifiants par des implémentations déterministes.

Le domaine n’utilise directement ni l’heure système, ni UUID aléatoire, ni
appel réseau.

## Tests

Les tests ADR-029 couvrent :

- l’impossibilité de quitter un état terminal ;
- l’unicité de la tentative active ;
- l’obligation de réconciliation après un résultat inconnu ;
- une exécution acquittée avec tentative persistée avant soumission ;
- le blocage d’un retry incertain ;
- la récupération après confirmation d’absence d’ordre ;
- l’absence de dépendances Spring, JPA, Feign ou infrastructure dans le
  domaine.

Commande ciblée :

```bash
cd trading-core
mvn -q -Dtest='com.hope.trading.trading_core.execution.*Test' test
```

Résultat constaté :

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
```

Le packaging est également validé :

```bash
mvn -q -DskipTests package
```

La suite globale contient des tests préexistants utilisant le mock maker
inline de Mockito. Dans l’environnement isolé de validation, sept de ces tests
ne peuvent pas démarrer car Byte Buddy ne peut pas attacher son agent à la
JVM. Cette limitation ne produit aucun échec d’assertion ADR-029.

## Conformité à ADR-029

| Décision | Implémentation |
|---|---|
| Business first | agrégats et contrats broker-neutres |
| Broker independence | `BrokerExecutionPort` et adaptateur isolé |
| Deterministic execution | pipelines explicites, `Clock` et IDs injectés |
| Explicit lifecycle | statuts et transitions contrôlés |
| Idempotency by design | service, invariants et contraintes uniques |
| Recovery before retry | retry interdit pour tout résultat inconnu |
| Immutable audit history | événements immuables et stockage séparé |
| Hexagonal architecture | domaine sans dépendance framework |
| Single responsibility | Risk, Execution et connectivité broker séparés |

## Éléments d’intégration différés

Le domaine et les pipelines ADR-029 sont implémentés. Les validations
nécessitant plusieurs services restent dépendantes de leur environnement :

- tests contractuels contre une instance réelle du Broker Service ;
- scénario end-to-end TradePlan → Risk → Execution → Broker ;
- alimentation de l’API par une source persistée d’approbations Risk ;
- réception asynchrone ultérieure des fills et événements broker.

Ces éléments ne modifient pas l’architecture du domaine. Ils devront être
réalisés dans les adaptateurs ou dans les tests interservices lorsque les
contrats correspondants seront disponibles.

