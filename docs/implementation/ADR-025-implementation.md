# ADR-025 — Notes d'implémentation

- **Statut :** Implémenté
- **Module :** `market-intelligence`
- **ADR source :** ADR-025 — Observation Model

## Modèle

Le package `domain.observation` contient l'agrégat immutable `Observation`, ses Evidence,
sa confiance déterministe, ses types, son lifecycle et les objets de traçabilité.
Les collections et maps sont copiées défensivement.

`ObservationConfidence` utilise la moyenne arithmétique des contributions des Evidence,
arrondie à quatre décimales selon la méthode versionnée
`EVIDENCE_ARITHMETIC_MEAN_V1`. Ce score exprime la force des Evidence et jamais une
probabilité de gain.

## Construction et versioning

`ObservationBuilder` est l'unique service de production autorisé à piloter
`ObservationFactory`. Il :

1. charge les exécutions de Capability terminées ;
2. exclut les résultats partiels ou dégradés ;
3. exécute une `ObservationConsolidationRule` versionnée ;
4. construit et valide les Evidence et leur provenance ;
5. calcule la confiance à partir des seules Evidence ;
6. persiste une V1 ou remplace atomiquement la version active par une V2.

Une nouvelle version conserve un `lineageId`, référence l'ancienne version avec
`supersedes`, et l'ancienne référence la nouvelle avec `supersededBy`.

## Traçabilité

La chaîne persistée est :

```text
Observation
└── ObservationEvidence
    └── CapabilityResultTrace (executionId, capabilityId, version)
        └── ArtifactTrace (identité et empreintes)
            └── RawMarketDataReference (source, instrument, timeframe, empreinte, date)
```

Le builder refuse une Evidence sans CapabilityResult terminé, sans Artifact, sans
donnée marché ou avec un Artifact dont l'exécution productrice ne correspond pas.

## Ports et lecture

`ObservationRepository` est un port applicatif. L'adaptateur
`InMemoryObservationRepository` fournit une persistance thread-safe destinée au
runtime actuel et aux tests. Il pourra être remplacé sans modifier le domaine.

`ObservationQueryService` expose les recherches par instrument, état actif, type,
statut, horizon, catégorie, intervalle de confiance et période.

Les consommateurs, y compris l'AI Engine, ne reçoivent que le service de lecture.
Ils ne disposent d'aucune opération de création ou de mutation.

## Vérification

La couverture comprend :

- tests unitaires du builder, de la confiance, des Evidence, du lifecycle et du versioning ;
- test d'intégration de persistance, lecture, relations et traçabilité ;
- règles ArchUnit sur l'indépendance des Capabilities, la frontière de construction
  et l'immutabilité.
