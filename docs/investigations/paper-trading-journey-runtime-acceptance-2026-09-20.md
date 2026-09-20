# Rapport d'investigation - parcours PAPER de bout en bout

**Date :** 20 septembre 2026
**Application :** Trading OS Web
**Utilisateur authentifie :** `story48_user_20260920`
**Objectif :** verifier le parcours utilisateur officiel depuis la recherche d'opportunites jusqu'a l'execution PAPER, sans contourner l'interface, l'authentification, le domaine de risque ou les transitions d'etat.

## 1. Conclusion

Le parcours a ete execute avec succes jusqu'a l'evaluation deterministe du risque.

Le parcours n'a pas abouti a une execution PAPER, pour deux raisons fonctionnelles distinctes :

1. Le compte `Story 0048 PAPER account` ne possede aucun `Trade Planning Profile` effectif. La creation du plan est donc refusee par Trading Core.
2. Le compte `Story 0053 PAPER account`, qui possede un profil exploitable, permet de creer et d'accepter le plan, mais l'evaluation de risque rejette la position pour `MAX_EXPOSURE`.

Le comportement observe est coherent avec les regles de responsabilite du systeme : aucun plan ne doit etre execute sans profil de planification valide et sans decision de risque approuvee. Aucun `ExecutionIntent` n'a ete cree a la suite du rejet de risque.

## 2. Perimetre et regles de validation

L'investigation devait couvrir le flux suivant :

```text
Authentification
  -> selection du compte PAPER
  -> scan officiel des marches
  -> selection d'une opportunite
  -> creation du Trade Plan
  -> validation humaine du plan
  -> evaluation deterministe du risque
  -> creation de l'intention d'execution
  -> execution PAPER MARKET
  -> verification de l'etat final et de la position
```

Les regles suivantes ont ete appliquees :

- utilisation exclusive de Trading OS Web et de ses boutons/actions officiels ;
- aucune creation manuelle d'intention d'execution ;
- aucune reutilisation forcee d'un ancien `evaluationId`, plan ou intent terminal ;
- aucune desactivation de JWT, d'autorisation, d'idempotence ou du controle de risque ;
- aucun ajustement direct du profil ou des donnees de trading en base ;
- arret du parcours lorsqu'une decision de risque bloquante est retournee.

## 3. Preparation de l'environnement

Les actions de preparation constatees avant l'investigation fonctionnelle sont :

- reconstruction de `trading-os-web` ;
- redemarrage de `broker-postgres` sans suppression du volume ;
- demarrage des services applicatifs necessaires ;
- utilisation de l'interface web sur `http://localhost:17085`.

Le depot etait deja dans un etat de travail non propre avant cette investigation. Les modifications preexistantes n'ont pas ete annulees ni modifiees par ce rapport. La creation de ce document est la seule modification apportee pendant la redaction du rapport.

## 4. Parcours execute

### 4.1 Authentification et selection initiale

L'utilisateur authentifie etait `story48_user_20260920`, avec le role utilisateur attendu. Le compte selectionne initialement dans l'interface etait :

| Element | Valeur |
|---|---|
| Nom | `Story 0048 PAPER account` |
| Identifiant | `ca8fd57c-edca-429a-bdb4-7c99bfb76cad` |
| Devise | USD |
| Solde observe | 10 000,00 USD |

La session etait bien authentifiee. Le refus ulterieur n'etait donc pas un echec de connexion ou de validation JWT.

### 4.2 Scan officiel des opportunites

Depuis la page `/opportunities`, le scan a ete lance via l'interface avec le scope `ALL_ELIGIBLE`.

Le scan a produit des opportunites. L'opportunite retenue pour le parcours etait :

| Element | Valeur |
|---|---|
| Identifiant | `8d4f652f-35eb-3c73-8d71-48a120222f80` |
| Marche | `ADA/USD` |
| Direction | `LONG` |
| Origine | scan officiel depuis l'interface |

La page de preparation utilisee ensuite etait :

```text
/trade-planning/prepare/8d4f652f-35eb-3c73-8d71-48a120222f80
```

### 4.3 Premiere tentative de creation du plan

La creation du Trade Plan a ete declenchee depuis le bouton officiel `Create Trade Plan` avec le compte `Story 0048 PAPER account`.

La requete portait sur le compte :

```json
{"accountId":"ca8fd57c-edca-429a-bdb4-7c99bfb76cad"}
```

Le Gateway a retourne `403 Forbidden` a l'interface. L'analyse des logs `trading-app` a permis d'identifier la cause applicative exacte :

```text
TradePlanningProfileException: Account has no effective Trade Planning Profile
```

La trace confirme le chemin suivant :

```text
OpportunityTradePlanController.create
  -> OpportunityTradePlanOrchestrationService.createFromOpportunity
  -> TradePlanningProfileService.effective
```

La verification des donnees d'assignation a montre que ce compte ne possede aucune assignation de profil de planification effective. Cette erreur est donc un rejet metier attendu, et non un probleme de transport ou d'authentification.

### 4.4 Selection d'un compte disposant d'un profil

Sans modifier la base et sans contourner l'application, le compte a ete change depuis le selecteur officiel de la page de preparation.

Compte utilise pour la seconde tentative :

| Element | Valeur |
|---|---|
| Nom | `Story 0053 PAPER account` |
| Identifiant | `4583b444-1737-4f7f-9f17-1bee95b88a4b` |
| Devise | USD |
| Solde observe | 10 000,00 USD |
| Profil de planification | assignation existante |

Le changement de compte est reste dans le perimetre du parcours utilisateur normal.

### 4.5 Creation du Trade Plan

La creation a reussi avec le compte `Story 0053 PAPER account`.

Trade Plan cree :

| Element | Valeur |
|---|---|
| Plan ID | `e0438fd6-8885-4bdf-9a4f-6fe0c95175f5` |
| Version | `1` |
| URL | `/trade-planning/plans/e0438fd6-8885-4bdf-9a4f-6fe0c95175f5/versions/1` |
| Etat initial | `PROPOSED` |
| Marche | `ADA/USD` |
| Direction | `LONG` |
| Type | `LIMIT` |
| Prix d'entree | `0.22` |
| Stop loss | `0.22` |
| Take profit | `0.23` |
| Quantite | `45 128,7976` |
| Notionnel | `10 000,00` |
| Risque affiche | `100,00` |
| Ratio risque/rendement | `2.0R` |
| Expiration affichee | 20 septembre 2026, 16:58 |

La these affichee etait :

```text
Legacy OHLC Trend: Legacy OHLC Trend
```

Les conditions de confirmation et d'invalidation etaient visibles dans l'interface.

### 4.6 Validation humaine du plan

Le bouton officiel `Accept Plan` a ete utilise.

Transition observee :

```text
PROPOSED -> ACCEPTED
```

Cette etape confirme que la validation humaine du plan fonctionne pour ce compte et cette opportunite.

### 4.7 Evaluation deterministe du risque

Le bouton officiel `Evaluate Risk` a ete utilise depuis le plan accepte.

L'evaluation a produit :

| Element | Valeur |
|---|---|
| Etat du plan apres evaluation | `REJECTED` |
| Evaluation ID | `98ced56d-eac0-4e2f-b6f8-6e78e61a073f` |
| Evaluation | `APPROVED: No` |
| Motif | `MAX_EXPOSURE` |
| Code/message affiche | `MAX_EXPOSURE — maximum-exposure` |
| Date affichee | 20 septembre 2026, 15:58:38 |

Le rejet est coherant avec les valeurs observees : le Trade Plan porte sur un notionnel de `10 000,00 USD`, soit la totalite du solde observe du compte. Le moteur de risque a bloque l'exposition avant toute demande d'execution.

## 5. Etapes non executees et raisons

Les etapes suivantes n'ont pas ete executees :

- creation d'un `ExecutionIntent` ;
- validation d'une intention d'execution ;
- creation d'un `ExecutionAttempt` ;
- appel de l'adaptateur d'execution PAPER ;
- verification d'un remplissage `MARKET` ;
- verification de la position ouverte et du feedback d'execution.

La raison est deterministe et bloquante : l'evaluation de risque est `APPROVED: No`. Continuer vers l'execution aurait viole la chaine de responsabilite du produit et aurait necessite un contournement du controle de risque.

## 6. Historique connexe observe

Un parcours PAPER precedent avait atteint l'execution avec une requete `ADA/USD` `BUY LIMIT`, mais avait ete rejete avec :

```text
LIMIT_PRICE_NOT_REACHED
```

Ce parcours precedent utilisait un plan deja consomme et une intent terminale `FAILED`. Il n'a donc pas ete reutilise pour la presente investigation. Cette decision garantit que la tentative documentee ici part bien d'un nouveau scan, d'une nouvelle opportunite et d'un nouveau Trade Plan.

## 7. Resultats par etape

| Etape | Resultat | Commentaire |
|---|---|---|
| Authentification | Reussie | Session utilisateur validee |
| Selection du compte 0048 | Reussie | Compte visible dans l'interface |
| Scan officiel | Reussi | Opportunites produites avec `ALL_ELIGIBLE` |
| Selection d'ADA/USD | Reussie | Opportunite `8d4f652f-35eb-3c73-8d71-48a120222f80` |
| Creation avec compte 0048 | Bloquee | Aucun profil de planification effectif |
| Selection du compte 0053 | Reussie | Profil existant |
| Creation du Trade Plan | Reussie | Plan `e0438fd6-8885-4bdf-9a4f-6fe0c95175f5` |
| Acceptation humaine | Reussie | `PROPOSED -> ACCEPTED` |
| Evaluation de risque | Rejetee | `MAX_EXPOSURE` |
| Creation d'intent | Non executee | Risque non approuve |
| Execution PAPER | Non executee | Risque non approuve |
| Verification de position | Non executee | Aucune execution |

## 8. Diagnostic

### Faits confirmes

- L'authentification utilisateur fonctionne.
- Le scan officiel et la projection d'opportunites fonctionnent.
- La creation d'un plan depend bien d'un profil de planification effectif.
- Le compte 0048 ne satisfait pas ce pre-requis.
- Le compte 0053 permet de creer un plan et de le faire accepter humainement.
- L'evaluation deterministe du risque est appelee depuis l'interface.
- Le controle `MAX_EXPOSURE` bloque correctement le plan.
- Aucun intent ni ordre n'est emis apres un rejet de risque.

### Ce qui n'est pas un bug demontre

- Le `403` de la premiere tentative n'est pas une preuve d'un probleme JWT : le log applicatif donne une exception metier precise.
- Le rejet `MAX_EXPOSURE` n'est pas une preuve d'un probleme d'execution PAPER : l'execution n'a jamais ete autorisee.
- L'absence d'une position finale n'est pas une regression d'execution : aucun intent n'a ete cree.

### Limitation restante

Le parcours complet jusqu'a l'ouverture d'une position PAPER n'a pas ete valide dans cette session. Il faut une combinaison valide de :

- compte avec profil de planification effectif ;
- profil de risque autorisant l'exposition demandee ;
- opportunite et plan dont le notionnel reste dans les limites du compte ;
- evaluation retournee `APPROVED: Yes`.

## 9. Prochaine action recommandee

La prochaine tentative doit rester dans le flux officiel et choisir l'une des options suivantes, apres decision humaine :

1. utiliser une opportunite dont le notionnel est inferieur a la limite `MAX_EXPOSURE` du compte ;
2. utiliser un compte PAPER deja configure avec une limite d'exposition compatible ;
3. faire configurer officiellement un profil de risque adapte au compte, sans modifier directement les tables ni desactiver le controle.

Une fois une evaluation approuvee obtenue, le parcours pourra reprendre avec la validation de l'intention, l'execution PAPER `MARKET` et la verification de la position.

## 10. Fichiers et composants pertinents

- `trading-os-web/src/app/features/opportunities/` : scan et selection des opportunites.
- `trading-os-web/src/app/features/trade-planning/` : creation et cycle de vie du Trade Plan.
- `trading-os-web/src/app/features/trade-planning/plan-page/plan-page.ts` : acceptation, evaluation du risque et execution.
- `trading-core/src/main/java/com/hope/trading/trading_core/tradeplanning/application/TradePlanningProfileService.java` : resolution du profil de planification effectif.
- `trading-core/src/main/java/com/hope/trading/trading_core/tradeplanning/application/OpportunityTradePlanOrchestrationService.java` : creation du plan depuis l'opportunite.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/InternalTradePlanRiskController.java` : handoff interne du risque et de l'execution.
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/tradeplan/TradePlanRiskHandoffService.java` : orchestration du handoff risque/execution.
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/api/ExecutionController.java` : surface d'execution.
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/ValidateAndCreateService.java` : validation et creation de l'intention.
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/ExecutionTimeRiskRevalidationService.java` : revalidation du risque au moment de l'execution.
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/PaperSettlementService.java` : settlement PAPER.

## 11. Validation du rapport

Ce rapport documente uniquement les observations faites pendant le parcours et les verifications techniques associees. Il ne modifie ni les profils de risque, ni les comptes, ni les plans, ni les intents, ni les positions.

## 12. Reprise du parcours apres correction du sizing

Une seconde tentative a ete executee avec un nouveau Trade Plan depuis le meme
flux officiel, apres que le sizing PAPER a ete rendu compatible avec le profil
de risque.

| Element | Valeur |
|---|---|
| Compte | `Story 0048 Runtime Final` |
| Account ID | `74d0a7b5-c126-476b-9e2b-f32685f8f190` |
| Opportunite | `ADA/USD` `LONG` |
| Trade Plan | `71371618-4e52-4a1e-b4f0-97a583264958` |
| Evaluation | `55608e0e-cb4c-459e-8521-b0a88dbc6958` |
| Decision risque | `APPROVED: Yes` |
| Execution ID | `c89ffab1-a12b-4056-896d-b9aae5b2b568` |
| Broker order | `SIM-8a050028-7248-4a8e-8081-6d945faf2316` |
| Broker status | `Filled` |
| Quantite remplie | `1330.6484` |
| Prix moyen | `0.23` |

La position `ADA/USD` a ete affichee dans `/positions` avec le compte transmis
par query parameter. Elle est restee visible apres rechargement de la page.
L'action officielle `Fermer l'exposition` a ensuite ete confirmee. L'interface
a affiche `ADA/USD Fermée`, puis `Aucune position ouverte.`; cet etat vide a
ete confirme apres un nouveau rechargement.

La premiere tentative precedente (`4b23f1db-effb-479e-9411-c046b1316a3a`)
avait ete rejetee avec `LIMIT_PRICE_NOT_REACHED`. Elle reste documentee comme
preuve negative et n'est pas incluse dans le succes du second parcours.
