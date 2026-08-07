# Domain Documentation

This directory documents the business domains that compose Trading OS.

Each domain owns a well-defined business responsibility and exposes explicit
interfaces to the rest of the system.

The purpose of these documents is to describe **ownership**, **responsibilities**
and **boundaries**.

Implementation details belong in the source code.

Architectural decisions belong in ADRs.

Business concepts belong in the `concepts/` documentation.

---

# Available Domains

| Domain | Primary Responsibility |
|----------|------------------------|
| Trading Core | Business orchestration |
| Market Intelligence | Market analysis and Trade Plan generation |
| Risk Domain | Deterministic risk evaluation |
| Broker Service | Broker integration |
| Market Data Service | Public market information |
| News Service *(planned)* | Economic calendar and financial news |
| AI Engine *(planned)* | Decision support |

---

# Choosing a Domain

| I want to understand... | Read... |
|--------------------------|----------|
| Application orchestration | Trading Core |
| Market analysis | Market Intelligence |
| Risk evaluation | Risk Domain |
| Broker connectivity | Broker Service |
| Market information | Market Data Service |
| Economic events | News Service |
| AI assistance | AI Engine |

---

# Domain Documentation Structure

Every domain document should answer the same questions.

- What is the purpose of the domain?
- Which responsibilities does it own?
- Which responsibilities does it explicitly not own?
- Which concepts does it own?
- Which public interfaces does it expose?
- Which other domains does it collaborate with?
- Which ADRs define its architecture?
- What is its current implementation status?

This common structure makes every domain easier to navigate.

---

# Domain Ownership

Trading OS follows explicit domain ownership.

Every business responsibility should have exactly one primary owner.

When introducing a new feature, the first architectural question should always
be:

> Which domain owns this responsibility?

If ownership is unclear, the architecture should be reviewed before
implementation begins.

---

# Relationship with Other Documentation

Domain documentation complements the rest of the architecture documentation.

| Information | Authoritative Source |
|-------------|----------------------|
| Business concepts | `concepts/` |
| Architecture decisions | ADRs |
| Features | Stories |
| Implementation | Source code |
| Engineering history | Engineering Reports |

Domain documents should reference these sources rather than duplicate their
content.