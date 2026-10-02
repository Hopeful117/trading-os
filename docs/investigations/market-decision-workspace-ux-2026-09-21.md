# Investigation - convergence UX Market, Opportunity et Trade Planning

**Date :** 21 septembre 2026
**Application :** Trading OS Web
**Perimetre :** parcours MANUAL, Market Decision Workspace, Opportunity et execution
**Statut :** investigation complete, premier raccordement frontend implemente

## 1. Conclusion

La capacite backend MANUAL est reutilisable et respecte le pipeline attendu :

```text
Manual Trade Plan
  -> validation humaine
  -> Risk
  -> Execution
  -> Position
```

Le principal probleme est frontend. Une page MANUAL autonome a ete ajoutee alors que `DecisionWorkspace` contient deja le contexte account-scoped necessaire : compte, marche eligible, ticker, chart, order book et trades recents.

La convergence recommandee est donc :

```text
DecisionWorkspace
  -> contexte compte
  -> contexte marche
  -> contexte Opportunity optionnel
  -> ticket MANUAL ou preparation Opportunity
  -> TradePlan
  -> Risk
  -> Execution
  -> Position
```

Le premier raccordement implemente transmet maintenant `accountId` et `marketId` depuis `DecisionWorkspace` vers la page MANUAL et pre-remplit les champs correspondants. Le pipeline backend et les routes existantes n'ont pas ete supprimes.

## 2. Regles Preservees

- Le compte reste selectionne par l'utilisateur.
- Le marche doit rester eligible et tradable.
- Le `PlanningContext` reste derive et interne cote backend.
- L'origine `MANUAL` reste distincte de l'origine `OPPORTUNITY`.
- Risk reste une decision deterministe.
- Execution reste une action humaine explicite.
- Les validations runtime restent limitees aux comptes PAPER.
- Aucun marche LIVE ne doit etre introduit par cette convergence UX.

## 3. Etat Des Changements MANUAL

### 3.1 A conserver

- `ManualTradePlanOrchestrationService`.
- `POST /api/v1/trade-plans/manual`.
- Validation de ownership du compte.
- Validation du profil de planification.
- Validation du marche tradable.
- Calcul serveur du contexte de planification.
- Provenance `MANUAL`.
- Flow partage `TradePlan -> ACCEPT -> Risk -> Execution`.
- Tests backend et frontend existants.

### 3.2 A reutiliser dans le workspace

- Le formulaire de `ManualTradePage` comme ticket de decision.
- Le compte deja selectionne dans `DecisionWorkspace`.
- Le marche deja selectionne dans `DecisionWorkspace`.
- Les flux live du marche deja ouverts dans `DecisionWorkspace`.
- Les contraintes du marche deja chargees dans le contexte marche.

### 3.3 A deplacer ou refactorer

- Extraire le formulaire de `ManualTradePage` en composant `ManualTradeTicket`.
- Permettre au ticket de recevoir `accountId` et `marketId` depuis le contexte parent.
- Afficher le ticket dans le contexte marche, plutot que dans une page sans donnees live.
- Conserver la page standalone uniquement comme fallback pendant la transition.

### 3.4 A retirer a terme

- La selection independante de tous les comptes depuis le ticket contextuel.
- La selection independante de tous les marches depuis le ticket contextuel.
- Le lien qui quitte le contexte marche pour recommencer la selection.
- La route standalone lorsque le composant integre sera couvert par les tests.

## 4. Market UX Actuelle

### 4.1 `/markets/:marketId`

Cette page est une page de donnees marche. Elle affiche notamment :

- identite du marche ;
- provider ;
- etat tradable ;
- ticker ;
- last price ;
- bid et ask ;
- spread ;
- volume ;
- historique OHLC ;
- chart ;
- order book ;
- trades recents ;
- contraintes du marche.

Elle ne possede pas encore :

- compte selectionne ;
- `PlanningContext` ;
- Opportunity ;
- action BUY/SELL ;
- action Prepare Trade ;
- contexte Risk ;
- position associee.

### 4.2 `/decision-workspace`

Cette page est deja la surface la plus proche de la cible produit. Elle gere :

- selection du compte ;
- resolution du `DecisionContext` ;
- marches eligibles pour le compte ;
- selection du marche ;
- ticker live ;
- chart OHLC ;
- order book ;
- trades recents ;
- contraintes du marche ;
- fraicheur des flux ;
- query params `accountId` et `marketId`.

Avant le raccordement de cette investigation, elle ouvrait la page MANUAL sans transmettre le contexte selectionne.

## 5. Parcours Opportunity Observe

Le parcours frontend actuel est :

```text
Scanner
  -> Opportunities
  -> OpportunityDetail
  -> PreparePlanPage
  -> PlanPage
  -> Risk
  -> Execution
  -> Positions
```

### 5.1 Scanner vers Opportunities

- Action : scan officiel termine.
- Contexte conserve : liste des opportunites.
- Contexte perdu : aucun marche n'est ouvert dans le workspace.

### 5.2 Opportunities vers OpportunityDetail

- Action : clic sur une opportunite.
- Contexte conserve : `opportunityId`.
- Contexte visible : instrument, direction, score, timeframe, rationale, setup, triggers et provenance.
- Donnees absentes : ticker live, chart, bid/ask, order book et compte.

### 5.3 OpportunityDetail vers PreparePlanPage

- Action : `Create Trade Plan`.
- Contexte conserve : `opportunityId`.
- Nouvelle selection imposee : compte.
- Contexte marche visible : absent.
- Contexte marche live : absent.

### 5.4 PreparePlanPage vers PlanPage

- Action : creation du plan.
- Contexte backend conserve : Opportunity-derived Trade Plan.
- Contexte frontend visible : principalement les donnees du plan.
- Relation visuelle avec l'Opportunity : faible apres la creation.

### 5.5 PlanPage vers Risk et Execution

- Action : `Accept Plan`, puis `Evaluate Risk`, puis `Execute Trade`.
- Validation humaine : explicite.
- Decision Risk : raisons et warnings visibles.
- Contexte marche : non visible comme surface interactive.
- Etat du compte : conserve pour la navigation vers les positions.

### 5.6 Execution vers Positions

- Action : `View account positions`.
- Contexte conserve : `accountId` par query param.
- Position observable : oui.
- Retour naturel vers le marche ou l'Opportunity : non.

## 6. Pertes De Contexte

### Compte

Le compte est selectionne dans `DecisionWorkspace`, puis la page MANUAL demandait a nouveau un compte. Cette duplication est maintenant supprimee lorsque le ticket est ouvert depuis le workspace.

### Marche

Le marche est selectionne et charge dans `DecisionWorkspace`, mais la page MANUAL demandait a nouveau un marche. Le `marketId` est maintenant transmis par query param et pre-rempli.

### Donnees live

Le ticket MANUAL n'affichait pas le ticker, le chart, l'order book ou les trades recents. Ces donnees restent dans le workspace et ne sont pas encore integrees visuellement dans le ticket.

### Opportunity

L'Opportunity est conservee par identifiant cote backend, mais elle n'est pas encore affichee dans le meme espace que les donnees marche et le ticket de preparation.

### PlanningContext

La separation est correcte : l'utilisateur ne selectionne pas directement de `PlanningContext`. Le backend le derive a partir du compte et de son profil.

## 7. Parcours Dupliques

Les parcours suivants coexistent encore :

```text
Markets -> MarketDetail
```

Page de donnees sans action de trading.

```text
DecisionWorkspace -> marche selectionne
```

Page account-scoped avec donnees live, mais sans ticket integre.

```text
Opportunity -> OpportunityDetail -> PreparePlanPage
```

Parcours Opportunity sans passage par une Market Decision Workspace.

```text
DecisionWorkspace -> ManualTradePage
```

Parcours MANUAL standalone. Le compte et le marche sont maintenant transmis, mais le ticket reste visuellement separe.

## 8. Classification Des Fichiers

### KEEP

- `trading-core/src/main/java/com/hope/trading/trading_core/tradeplanning/application/ManualTradePlanOrchestrationService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/tradeplanning/api/ManualTradePlanController.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/InternalManualTradePlanController.java`
- `trading-os-web/src/app/core/services/trade-plan.service.ts`
- `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.ts`

### MOVE / REFACTOR

- `trading-os-web/src/app/features/trade-planning/manual-trade-page/manual-trade-page.ts`
- `trading-os-web/src/app/features/trade-planning/manual-trade-page/manual-trade-page.html`
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.ts`
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.html`

### REMOVE A TERME

- Le lien standalone sans contexte.
- La selection independante du marche dans le ticket integre.
- La route standalone apres extraction du composant et migration des parcours.

### REQUIRES INVESTIGATION

- Le contrat `OpportunityResponse` expose actuellement l'instrument, mais pas de `marketId` frontend fiable.
- La navigation Opportunity vers le workspace ne doit pas resoudre un marche uniquement a partir d'un symbole libre.
- Le `marketId` doit etre expose explicitement si l'Opportunity doit ouvrir le workspace de maniere autoritaire.

## 9. Changement Implemente

### DecisionWorkspace

Le lien `New manual trade` transmet maintenant :

```text
/trade-planning/manual?accountId=<selected-account>&marketId=<selected-market>
```

Les valeurs restent nulles si aucun contexte n'est selectionne, ce qui preserve le fallback standalone.

### ManualTradePage

La page lit les query params `accountId` et `marketId` et pre-remplit les controles correspondants.

Le backend recoit toujours le meme contrat MANUAL. Aucun identifiant de planning context n'est expose au frontend.

## 10. Validation

Commandes executees dans `trading-os-web` :

```text
npm run test:ci
npm run build
```

Resultats :

- 44 fichiers de test passes.
- 334 tests passes.
- Build Angular reussi.
- `git diff --check` reussi pour les fichiers modifies par ce raccordement.

Le build conserve des warnings de budgets existants sur le bundle initial et plusieurs feuilles de style. Aucun de ces warnings n'est introduit par le raccordement query params.

## 11. Prochaines Etapes

1. Ajouter une action `Prepare manual trade` directement dans le bloc marche charge du `DecisionWorkspace`.
2. Extraire le formulaire MANUAL en composant `ManualTradeTicket` reutilisable.
3. Afficher dans le ticket le contexte marche deja charge : symbole, prix de reference et etat tradable.
4. Exposer explicitement `marketId` dans le contrat Opportunity.
5. Faire naviguer `OpportunityDetail` vers le workspace avec `opportunityId`, `marketId` et le compte choisi si disponible.
6. Ajouter les actions distinctes `Prepare from opportunity` et `Prepare manual`.
7. Afficher la provenance et le contexte Opportunity dans `PlanPage`.
8. Supprimer la route standalone uniquement apres migration et couverture des parcours.

## 12. Fichiers De Reference

- `trading-os-web/src/app/features/decision-workspace/decision-workspace.ts`
- `trading-os-web/src/app/features/decision-workspace/decision-workspace.html`
- `trading-os-web/src/app/features/trade-planning/manual-trade-page/manual-trade-page.ts`
- `trading-os-web/src/app/features/trade-planning/manual-trade-page/manual-trade-page.html`
- `trading-os-web/src/app/features/trade-planning/manual-trade-page/manual-trade-page.spec.ts`
- `trading-os-web/src/app/features/opportunities/opportunity-details/opportunity-details.ts`
- `trading-os-web/src/app/features/trade-planning/prepare-plan-page/prepare-plan-page.ts`
- `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.html`
- `trading-os-web/src/app/app.routes.ts`
