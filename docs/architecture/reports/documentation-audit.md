# Documentation Audit — Trading OS

- **Date:** 2026-08-06
- **Scope:** ADR set (ADR-001..031), implementation notes, Stories 0001–0003, AGENTS.md, README, stories/README, diagrams.
- **Constraint:** Verification only; no files were modified. Findings report evidence as found in the documents.

---

# Executive Summary

The documentation repository is coherent overall. ADR-001..031 form a consistent
sequence, and the AGENTS.md engineering workflow, story artifacts and
implementation notes align with the accepted ADRs. Story 0001..0003 are recorded
as Complete, and implementation notes report passing deterministic test suites.

Three broken cross-references were found, all located in the front-matter or
"Related ADRs"/"Related Decisions" sections of three ADRs:

1. **ADR-030** labels **ADR-001** as "Hexagonal Architecture", but ADR-001 is
   "Trading OS Vision and Product Philosophy". The rest of the ADR set references
   ADR-001 with its correct title. No ADR in the set is titled "Hexagonal
   Architecture" (the concept is described in prose, e.g. ADR-029 and ADR-030
   itself).
2. **ADR-030** labels **ADR-006** as "Domain Events", but ADR-006 is
   "Market Data Service Responsibilities". No ADR is titled "Domain Events".
3. **ADR-026** and **ADR-027** both list **ADR-024 — Observation Model** and
   **ADR-025 — AI Analysis Model** as related ADRs. The actual titles are
   **ADR-024 — Broker Credential Management and Account Connection** and
   **ADR-025 — Observation Model**. The pairing is therefore shifted/mislabeled;
   "AI Analysis Model" does not exist as an ADR.

These are localized reference/terminology defects and do not indicate an
architectural inconsistency. Correcting the references will make the ADR set
self-consistent and navigationally correct.

One naming inconsistency was found in the Story 0003 directory name, which does
not follow the documented kebab-case convention.

---

# Findings

## F-001 — ADR-030 mislabels ADR-001 as "Hexagonal Architecture"

- **Category:** Broken reference
- **Severity:** High
- **Confidence:** HIGH
- **Evidence:**
  - `docs/adr/ADR-030.md:8` — `| **Related ADRs** | ADR-001 (Hexagonal Architecture), ADR-006 (Domain Events), ADR-029 (Execution Domain) |`
  - `docs/adr/ADR-030.md:3334` — `- **ADR-001 — Hexagonal Architecture**`
  - `docs/adr/ADR-001.md:1` — `# ADR-001 — Trading OS Vision and Product Philosophy`
  - Other ADRs reference ADR-001 with its correct title (e.g. `ADR-002.md:183`,
    `ADR-004.md:188`, `ADR-005.md:201`, `ADR-009.md:196`, `ADR-014.md:241`,
    `ADR-015.md:178`).
  - `grep -l "Hexagonal" docs/adr/` returns only `ADR-029.md` and `ADR-030.md`;
    no ADR document is titled "Hexagonal Architecture".
- **Impact:** Readers following the reference expect a dedicated Hexagonal
  Architecture ADR. The reference points to an unrelated decision (product
  vision/philosophy), breaking navigation and misattributing the architectural
  basis of ADR-030.
- **Recommendation:** Correct the ADR-001 label in `ADR-030.md:8` and
  `ADR-030.md:3334` to "ADR-001 — Trading OS Vision and Product Philosophy",
  or cite the ADR/document that actually establishes Hexagonal Architecture
  (currently only described in prose within ADR-029 and ADR-030).

## F-002 — ADR-030 mislabels ADR-006 as "Domain Events"

- **Category:** Broken reference
- **Severity:** High
- **Confidence:** HIGH
- **Evidence:**
  - `docs/adr/ADR-030.md:8` — `ADR-006 (Domain Events)`.
  - `docs/adr/ADR-006.md:1` — `# ADR-006 — Market Data Service Responsibilities`.
  - `grep -l "Domain Events" docs/adr/` returns no ADR file; "Domain Events"
    is not an ADR title.
- **Impact:** The reference misidentifies the related decision, misleading
  readers about which ADR governs domain events.
- **Recommendation:** Change `ADR-006 (Domain Events)` to the correct title
  "ADR-006 — Market Data Service Responsibilities", or point to the actual ADR
  that governs domain/event modeling if one exists.

## F-003 — ADR-026 and ADR-027 mislabel ADR-024 and ADR-025 in "Related ADRs"

- **Category:** Terminology inconsistency
- **Severity:** High
- **Confidence:** HIGH
- **Evidence:**
  - `docs/adr/ADR-026.md:9` — `- ADR-024 — Observation Model`
  - `docs/adr/ADR-026.md:10` — `- ADR-025 — AI Analysis Model`
  - `docs/adr/ADR-026.md:1202` — `- ADR-024 — Observation Model`
  - `docs/adr/ADR-026.md:1203` — `- ADR-025 — AI Analysis Model`
  - `docs/adr/ADR-027.md:9` — `- ADR-024 — Observation Model`
  - `docs/adr/ADR-027.md:10` — `- ADR-025 — AI Analysis Model`
  - `docs/adr/ADR-027.md:1099` — `- ADR-024 — Observation Model`
  - `docs/adr/ADR-027.md:1100` — `- ADR-025 — AI Analysis Model`
  - Actual titles: `docs/adr/ADR-024.md:1` — "Broker Credential Management and
    Account Connection"; `docs/adr/ADR-025.md:1` — "Observation Model".
  - `grep -rln "AI Analysis Model" .` returns only `ADR-026.md` and `ADR-027.md`.
- **Impact:** The Observation Model is attributed to the wrong ADR number
  (ADR-024 instead of ADR-025), and ADR-025 is given a title ("AI Analysis Model")
  that does not exist anywhere. This misleads readers about which decisions
  support the Trading Opportunity and Trade Planning models.
- **Recommendation:** In both ADR-026 and ADR-027, relabel to
  "ADR-025 — Observation Model" and remove the non-existent "AI Analysis Model"
  reference (or replace with the actual related ADR, e.g. ADR-023 Capability
  Execution Model, if that is the intended link).

## F-004 — Story 0003 directory name violates the documented naming convention

- **Category:** Workflow inconsistency
- **Severity:** Low
- **Confidence:** MEDIUM
- **Evidence:**
  - `docs/stories/README.md` — `NNNN-short-kebab-case-title`, example
    `0001-connect-trade-plan-to-risk`.
  - Actual directory: `docs/stories/0003 - Authorize Trade Plans through the Risk Domain/` (renamed to `0003-authorize-trade-plans-through-risk-domain` as part of the applied corrections).
    (contains spaces, a hyphen, and title-case words instead of kebab-case).
  - Compare with conforming siblings `0001-connect-trade-plan-to-risk` and
    `0002-connect-market-intelligence-to-trade-plan-generation`.
- **Impact:** The folder does not match the documented convention, which makes
  automated tooling and consistent navigation harder. It is cosmetic and does
  not affect content or approval state.
- **Recommendation:** Rename the directory to kebab-case, e.g.
  `0003-authorize-trade-plans-through-risk-domain`, and update any references.

---

# Missing Evidence

These items could not be verified during this audit and are noted for
completeness; they are not reported as defects.

- **Diagram content cross-check (HIGH relevance, not verified):** The 9 PNGs in
  `docs/diagrammes/` (architecture, data-lifecycle, deployment-view,
  domain-ownership, event-flow, runtime-sequence, story-progression,
  system-context, trading-decision-pipeline) were not compared against the ADR
  and Story text, because they are binary images and were not opened. Their
  filenames are consistent with ADR topics, but visual content alignment with
  the written decisions was not confirmed.
- **Story 0002 / 0003 in-depth artifacts:** Story 0002 and 0003
  `implementation-report.md`, `code-review.md`, and `repository-analysis.md`
  were not fully read in this audit. Engineering reports for both were inspected
  and indicate Complete status; deeper cross-check of their acceptance-criteria
  mapping remains open.
- **Implementation plans for ADR-027/028/029/030:** The full plan documents were
  not exhaustively diffed against the delivered implementation notes.
- **Code-level verification:** No source tests were executed as part of this
  documentation-only audit; all test results cited are as reported in the
  documentation.
- **Post-audit hardening status:** The ADR-030 implementation notes describe a
  "Durcissement post-audit" integrated into `main`; no runtime verification was
  performed here.

---

# Audit Statistics

| Metric | Value |
| --- | --- |
| ADRs reviewed | 31 (ADR-001 .. ADR-031) |
| Stories reviewed | 3 (0001, 0002, 0003) |
| ADR implementation notes reviewed | 11 (ADR-020 .. ADR-030) |
| ADR implementation plans present | 4 (ADR-027, ADR-028, ADR-029, ADR-030) |
| Diagrams present | 9 |
| Total findings | 4 |
| — High severity | 3 (F-001, F-002, F-003) |
| — Low severity | 1 (F-004) |
| Findings with HIGH confidence | 3 |
| Findings with MEDIUM confidence | 1 |
| Files modified during audit | 0 |
