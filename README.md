# Trading OS

Trading OS est un poste de travail d'assistance au trading pour traders
discrétionnaires. Il centralise les données de marché, le contexte de compte,
l'analyse déterministe, le risque, la préparation des décisions et l'exécution
explicitement autorisée.

Trading OS n'est ni un bot autonome, ni une plateforme HFT, ni un système qui
promet une rentabilité. Le trader conserve l'autorité finale. Les règles
déterministes de risque, d'éligibilité et d'exécution priment sur toute
recommandation d'IA.

## État actuel

- Architecture microservices, authentification JWT et Gateway disponibles.
- Comptes PAPER : provisioning, entrée simulée, positions locales, valorisation,
  clôture et historique décisionnel disponibles.
- Market Data : catalogue, état de marché, ticker, OHLC, carnet d'ordres et
  transactions récentes avec identité provider-aware.
- Market Intelligence : analyses déterministes, Active Scan, opportunités,
  Trade Plans et pipeline de validation du risque disponibles.
- Passive Scanning V1 : univers configuré, dispatch borné, awareness projection
  et parcours d'investigation explicite disponibles. La clôture de l'Epic 0238
  reste soumise à l'approbation humaine documentée dans le
  [rapport de clôture](docs/architecture/epics/0238-passive-market-scanning/closure-report.md).
- AI Engine : fondation shadow-mode disponible, désactivée par défaut, sans
  autorité de décision, de risque ou d'exécution.
- News Service : fondation provider-neutral disponible ; synchronisation avec
  un fournisseur réel non activée.
- Validation LIVE, sandbox broker et parcours déployés restent des validations
  opérationnelles distinctes du parcours PAPER.

## Architecture

| Composant | Responsabilité | État |
| --- | --- | --- |
| `gateway` | Entrée HTTP, JWT et routage | Disponible |
| `trading-core` | Comptes, Trade Plans, risque, exécution et état PAPER | Disponible |
| `broker-service` | Contrats broker-neutres et adaptateurs broker | Disponible, validation LIVE distincte |
| `market-data` | Marchés, prix, OHLC et données temps réel | Disponible |
| `market-intelligence` | Analyse, scans, observations, opportunités et plans | Disponible, capacités en évolution |
| `news-service` | Événements économiques et actualités normalisés | Fondation disponible |
| `eureka-server` | Découverte de services | Disponible |
| `trading-os-web` | Interface Angular du workflow humain | Disponible |
| `ai-engine` | Interprétation advisory shadow-mode | Désactivé par défaut |

Le domaine reste indépendant des API fournisseurs. Trading Core et Risk Domain
conservent l'autorité sur les comptes, le risque, l'autorisation et l'exécution ;
Market Intelligence interprète les preuves mais ne place pas directement de broker orders.

## Parcours de décision

```text
Market Data
    -> Market Intelligence
    -> Observation / MarketThesis / Opportunity
    -> TradePlan
    -> Risk déterministe
    -> Validation humaine
    -> Trading Core
    -> Broker Service ou exécution PAPER
```

Le Passive Scanner peut proposer une investigation Active Scan, mais ne crée
pas automatiquement de scan, de TradePlan ou d'opération broker.

## Démarrage rapide

Initialiser l'environnement local puis démarrer la stack :

```bash
./scripts/bootstrap-local-env.sh
docker compose up --build
```

Points d'accès locaux :

- Application : <http://localhost:17085>
- Gateway : <http://localhost:17080>
- Eureka : <http://localhost:17084>

Les prérequis, ports, tests, validations qualité, services Python et règles de sécurité sont dans [`TOOLS.md`](TOOLS.md).

## Documentation

- [Architecture](docs/architecture/README.md)
- [ADR](docs/architecture/adr/README.md)
- [Domaines](docs/architecture/domains/README.md)
- [Concepts](docs/architecture/concepts/README.md)
- [Stories](docs/architecture/stories/README.md)
- [Rapports d'ingénierie](docs/architecture/reports/)
- [Epic 0238 — clôture conditionnelle](docs/architecture/epics/0238-passive-market-scanning/closure-report.md)
- [Epic 0247 — qualité des preuves et des opportunités](docs/architecture/epics/0247-trading-intelligence-evidence-and-opportunity-quality-v1/epic.md)
- [MCP Adapter](mcp-adapter/README.md)
- [Interface Web](trading-os-web/README.md)
- [Risk Domain](risk-domain/README.md)

Les ADR décrivent les décisions, les Stories le périmètre, les rapports les résultats et leurs limites ;
le code reste la source de vérité pour le comportement courant.

## Prochaine direction

Le draft [Epic 0247](docs/architecture/epics/0247-trading-intelligence-evidence-and-opportunity-quality-v1/epic.md)
propose de mesurer la qualité des preuves, d'évaluer les résultats et d'améliorer le classement explicable.
Il est soumis à approbation humaine et n'autorise pas encore l'implémentation.

## Contributions et sécurité

Toute modification significative suit le workflow décrit dans [`AGENTS.md`](AGENTS.md).
Les commits, pushes, merges et opérations broker restent soumis au contrôle humain.

Ne jamais committer de secrets, credentials broker, clés API, JWT ou tokens.
Ne jamais utiliser un compte cTrader LIVE pour valider le comportement PAPER.

## Avertissement

Trading OS est un outil logiciel d'assistance et de simulation. Il ne constitue
pas un conseil financier et ne garantit aucun résultat de trading.
