# TOOLS.md

Guide operationnel des outils du projet Trading OS. Ce document complete
`AGENTS.md` : `AGENTS.md` definit les regles d'architecture et de travail,
`TOOLS.md` indique comment utiliser les outils locaux sans contourner ces
regles.

## Prerequis

- Java 21 et Maven Wrapper (`./mvnw`) pour les services Java.
- Node.js et npm pour `trading-os-web`.
- Python 3.11 ou plus recent pour `ai-engine`, `mcp-adapter` et `research/ml`.
- Docker et Docker Compose pour la stack locale.
- OpenSSL pour la generation des secrets locaux.
- `jq` est utile pour inspecter les reponses JSON, mais n'est pas requis par
  les scripts du depot.

Verifier les versions avant une validation complete :

```bash
java -version
node --version
npm --version
python3 --version
docker compose version
```

## Configuration locale

Initialiser `.env` sans afficher ni committer de secret :

```bash
./scripts/bootstrap-local-env.sh
```

Le script cree `.env` si necessaire, genere les secrets JWT manquants et
applique les permissions `600`. Les cles Kraken, cTrader, Alpha Vantage,
SonarQube et les JWT ne doivent jamais etre ajoutees au depot.

## Stack Docker

Demarrer la stack complete :

```bash
docker compose up --build
```

Arreter la stack :

```bash
docker compose down
```

Points d'acces locaux :

| Composant | URL ou port |
| --- | --- |
| Trading Web | `http://localhost:17085` |
| Gateway | `http://localhost:17080` |
| Trading Core | `localhost:17081` |
| Broker Service | `localhost:17082` |
| Market Data | `localhost:17083` |
| Eureka | `http://localhost:17084` |
| News Service | `localhost:17086` |
| Market Intelligence | interne, port conteneur `8084` |

La stack comprend PostgreSQL pour Trading Core, Market Data, Broker Service,
Market Intelligence et News Service. Les appels frontend doivent passer par le
Gateway ; ne pas utiliser directement les endpoints internes pour contourner
l'authentification ou l'autorisation.

## Validation

Validation fonctionnelle complete des services Java et du frontend :

```bash
./scripts/test-all.sh
```

Validation qualite, couverture JaCoCo/LCOV, formatage frontend et build :

```bash
./scripts/quality-verify.sh
```

Validation ciblee :

```bash
(cd trading-core && ./mvnw test)
(cd market-intelligence && ../trading-core/mvnw test)
(cd risk-domain && ../trading-core/mvnw test)
(cd trading-os-web && npm run check)
```

Le script `test-all.sh` couvre les huit modules Maven valides par le projet,
ainsi que les tests et le build Angular. Il ne couvre pas automatiquement les
projets Python ni `ctrader-openapi-technical`.

## Outils Python

### AI Engine

Installer et tester le service local :

```bash
cd ai-engine
python3 -m venv .venv
.venv/bin/pip install -e '.[test]'
.venv/bin/pytest
```

Lancer l'API hors ligne :

```bash
cd ai-engine
.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8086
```

L'AI Engine est desactive par defaut et ne doit pas recevoir directement les
donnees metier ni autoriser une execution.

### MCP Adapter

Installer et tester l'adaptateur :

```bash
cd mcp-adapter
python3 -m venv .venv
.venv/bin/pip install -e '.[test]'
.venv/bin/pytest
```

Lancer l'adaptateur avec une credential agent opaque fournie par
l'environnement :

```bash
TRADING_OS_GATEWAY_URL=http://127.0.0.1:17080 \
TRADING_OS_AGENT_CREDENTIAL="$TRADING_OS_AGENT_CREDENTIAL" \
.venv/bin/trading-os-mcp
```

La credential est echangee contre un token agent court via le Gateway. Le token
est partage par les outils pendant la duree du processus MCP, reste en memoire
et est renouvele une seule fois apres un `401` pour les lectures
GET uniquement. Un `POST` en erreur `401` n'est jamais rejoue. Aucun JWT humain
n'est accepte. Les lectures de comptes, statut read-only et positions passent
par `/api/v1/agent/broker-accounts/**` et exigent des `AgentGrant` exacts.
L'adaptateur utilise stdio, ne persiste aucune credential et ne peut ni creer de
Trade Plan, ni evaluer le risque, ni modifier une position, ni placer un ordre,
ni synchroniser un compte, ni administrer un agent ou ses grants.

### Research ML

Installer le package de recherche :

```bash
python3 -m venv research/ml/.venv
research/ml/.venv/bin/pip install -e research/ml
```

Valider un bundle :

```bash
PYTHONPATH=research/ml/src \
  research/ml/.venv/bin/python -m research_dataset validate <bundle-directory>
```

Le code Python de recherche consomme des exports valides et ne remplace pas la
verite deterministe canonique de Market Intelligence en Java.

## Inspection et hygiene

Commandes utiles avant et apres une modification :

```bash
git status --short --branch
git diff --check
git diff -- AGENTS.md TOOLS.md
```

### Reconstruction historique controlee

La reconstruction historique est desactivee par defaut et ne doit etre activee
que pour une execution operateur explicitement approuvee. Elle n'est pas exposee
par le Gateway.

```bash
java -jar market-intelligence.jar \
  --research.historical-reconstruction.enabled=true \
  --market-id=<market-uuid> \
  --cutoff=<evaluation-cutoff> \
  --run-id=<new-run-id> \
  --approval-reference=<human-approval-reference> \
  --confirm-historical-reconstruction
```

La commande utilise uniquement les preuves OHLC historiques strictes, produit au
maximum un echantillon et refuse un repertoire de sortie existant ou hors de la
racine configuree. Elle ne lance ni replay Order Book, ni appel LLM, ni ordre.

Ne pas utiliser de commande destructive pour nettoyer le worktree. Les
modifications existantes doivent rester intactes et visibles pour la revue
humaine.

## Outils hors depot

- OpenCode peut enregistrer le MCP local avec `opencode mcp add trading-os`.
- La configuration OpenCode et les JWT restent hors du depot.
- SonarQube se lance via `./scripts/quality-scan.sh` avec `SONAR_HOST_URL` et
  `SONAR_TOKEN` fournis par l'environnement ; le token ne doit jamais etre
  ecrit dans un fichier versionne.
