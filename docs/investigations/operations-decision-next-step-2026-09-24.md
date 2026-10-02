# Décision documentée — Prochaine étape de structuration des opérations

Statut : **PROJECTION GOUVERNANCE / DÉCISION CONDITIONNELLE** — revue indépendante
de fiabilité et de gouvernance (Garde), pour validation du responsable de
HopeCode Labs.

## 1. Périmètre de la revue

- Travaux relus : Flux HOP-3 (inventaire des données et intégrations, livré),
  fichiers de plan et d'ADR du dépôt, contrats internes.
- Travaux Atlas attendus : HOP-2 (cartographie des workflows/opérations) et
  HOP-4 (modèle opératoire cible) — **ABSENTS** (issues `todo`, aucun livrable).
- Méthode : lecture indépendante, classification des constats par gravité,
  exigence de preuves avant validation, aucune modification de système, aucun
  déploiement.

## 2. Constats par gravité

### BLOCKER B1 — Dépendance Atlas non livrée (HOP-2, HOP-4)
- Constat : la synthèse des workflows/acteurs/décisions (Atlas) et le modèle
  opératoire cible avec points de contrôle humain (Atlas) n'existent pas. La
  présente décision ne peut donc pas être pleinement validée côté gouvernance.
- Condition de sortie : Atlas livre HOP-2 et HOP-4 (cartographie + modèle
  opératoire) ; les livrables sont relus par Garde ; la décision finale est
  ré-émise et validée par le responsable.

### MAJEUR M1 — Décision partiellement étayée
- Constat : seulement le volet « données et intégrations » (Flux) est étayé.
  La recommandation ne concerne que la validation contractuelle hors
  production du socle minimal ; aucune décision sur le modèle opératoire, les
  responsabilités ou les points de contrôle ne peut être prise avec preuves.
- Condition de sortie (acceptation) : l'engagement se limite à une validation
  contractuelle et technique HORS PRODUCTION, réversible, sans credentials
  privés, sans accès broker privé, sans ordre réel.

### MOYEN M2 — Points de vigilance issus de la revue (Flux)
Repris et classés depuis le livrable Flux (relecture indépendante) :
- contrats Kraken non versionnés dans le dépôt → fixtures contractuelles requises ;
- fraîcheur WebSocket/OHLC multi-instance non garantie → SLA + reconnexion +
  limitation à une instance ;
- artefacts Market Intelligence partiellement en mémoire → persistance à décider ;
- valeurs de démonstration dans Compose → séparer profils local/sandbox/production ;
- réconciliation/ownership non prouvés pour toute écriture externe.

### INFO I1 — Conformité du processus observé
- La chaîne adaptateur (openai/big-pickle) est saine ; les adversaires sont
  configurés ; aucune fuite de secret observée ; les livrables Flux sont
  factuels et datés. (Note : les livrables Atlas étant absents, la traçabilité
  complète du flux « Atlas → Garde » ne peut pas encore être certifiée.)

## 3. Options comparées pour la prochaine étape

| Option | Description | Avantages | Risques / coûts |
| --- | --- | --- | --- |
| A | Valider contractuellement, HORS PRODUCTION, le socle minimal (Market Data publique Kraken + PostgreSQL + Market Intelligence déterministe + Trading Core PAPER + Transit + Eureka), avec données synthétiques | Réversible, pas de credentials privés, pas de risque réel, étayé par Flux | Ne couvre pas le modèle opératoire (dépend d'Atlas) |
| B | Attendre Atlas (HOP-2, HOP-4) avant toute validation | Décision complète dès le départ | Temps mort ; le volet données (Flux) reste non exercé |
| C | Ouvrir dès maintenant une intégration production/sandbox Kraken privé | Validerait le contour réel | **BLOQUÉ** : nécessite ownership, réconciliation, contrôles sécurité, validation responsable |

## 4. Décision recommandée (conditionnelle — à valider par le responsable)

**Recommander l'option A** : lancer la validation contractuelle et technique du
socle minimalé hors production, fondée sur les preuves Flux, avec conditions de
sortie explicites (fixtures contractuelles, fraîcheur, persistance, séparation
des profils). Cette validation est sûre et réversible.

**Spécifiquement différé, non validé ici** (car non étayé par des preuves) :
- modèle opératoire cible et points de contrôle humain (Atlas HOP-4) ;
- attributions de responsabilités et flux de workflow (Atlas HOP-2) ;
- toute intégration production / broker privé / ordres réels.

## 5. Critères de réussite observables (pour la prochaine étape)

1. Le socle minimal tourne en PAPER avec données synthétiques/fixtures, sans
   credentials privés ni secrets de production.
2. Chaque contrat d'intégration est versionné et couvert par des tests
   contractuels (fixtures, schémas, erreurs).
3. Les statuts `FRESH`/`STALE`/`UNKNOWN` sont explicites et propagés ; aucune
   donnée manquante n'est traitée comme fraîche.
4. Les points de contrôle humain et d'escalade (une fois Atlas livré) sont
   documentés et re-validés.
5. La décision révisée incluant Atlas est re-validée par le responsable sans
   incertitude résiduelle.

## 6. Éléments explicitement différés

- Cartographie des workflows et des acteurs (Atlas HOP-2).
- Modèle opératoire cible et contrôles/escalades (Atlas HOP-4).
- Brokers privés / sandbox réel / production : exclus tant que les
  préconditions sécurité et la validation responsable ne sont pas obtenues.
- Toute externalisation de données sensibles ou de secrets.

## 7. Validation requise

Cette décision doit être **validée par le responsable de HopeCode Labs**
(escalade obligatoire avant engagement). Garde ne valide pas ce document dans
ses conditions actuelles (B1 / M1) : il est soumis à confirmation du
responsable et sera finalisé dès la levée des blocages Atlas.

— Garde (revue indépendante de fiabilité et gouvernance) — 2026-09-24
