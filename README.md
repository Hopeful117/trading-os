# Trading OS

Trading OS est une plateforme personnelle de trading conçue pour centraliser l’exécution des ordres, la gestion du risque et le suivi des performances dans un environnement unifié.

Le projet est pensé comme un **système d’exploitation de trading**, permettant de préparer et passer des challenges de prop firms (FTMO, Kraken, etc.) tout en construisant un outil évolutif orienté architecture logicielle.

---

## 🧠 Objectif

- Gérer des opérations de trading depuis une seule interface
- Appliquer des règles de risque strictes automatiquement
- Suivre et analyser ses performances de trading
- Simuler ou exécuter des trades en conditions réelles

---

## 🏗️ Architecture

Le projet est basé sur une architecture microservices :

- **API Gateway** : point d’entrée unique
- **Trading Core (Java / Spring Boot)** : logique métier, risk management, journal, challenges
- **Broker Service (Java / Spring Boot)** : communication avec les exchanges (Kraken, etc.)
- **AI & Analytics (Python)** : backtesting et analyse (phase future)

---

## 🚀 Stack technique

- Java 21 / Spring Boot 3
- Spring Cloud Gateway
- PostgreSQL
- Angular (frontend, à venir)
- Python (analytics, à venir)
- Docker

---

## 📌 Statut du projet

🚧 En cours de développement

- Sprint 0 : initialisation du projet

---

## 🧭 Roadmap

- [ ] Sprint 0 — Setup architecture
- [ ] Sprint 1 — Connexion broker (Kraken)
- [ ] Sprint 2 — Passage d’ordres
- [ ] Sprint 3 — Risk management
- [ ] Sprint 4 — Journal de trading
- [ ] Sprint 5 — Dashboard
- [ ] Sprint 6 — Analytics & IA

---

## ⚠️ Disclaimer

Ce projet est un outil de simulation et d’assistance au trading. Il ne constitue pas un conseil financier.

---
