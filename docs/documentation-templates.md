# Documentation Templates

## Purpose

This document defines the standard templates used throughout the Trading OS
documentation.

Templates ensure that similar documents follow a consistent structure,
making them easier to read, maintain and generate.

Every template defines the minimum recommended sections for a document type.

Projects may extend these templates when necessary, but should avoid removing
their core structure.

---

# General Principles

All templates follow the same principles.

- One document answers one primary question.
- Sections appear in a predictable order.
- Similar document types share a common structure.
- Navigation is preferred over duplication.
- Every document should reference authoritative sources when appropriate.

---

# README Template

Purpose

Documentation Structure

Reading Guide

Relationship with Other Documentation

Contributing

README files are entry points.

They should not duplicate the contents of child documents.

---

# Architecture Diagram Template

Purpose

Question Answered

Diagram

Key Components

Related Documents

Related ADRs

Maintenance Notes

Architecture diagrams explain one architectural concern.

---

# Domain Documentation Template

Purpose

Responsibilities

Out of Scope

Owned Concepts

Public Interfaces

Dependencies

Related Domains

Related ADRs

Related Stories

Implementation Status

References

---

# Concept Documentation Template

Purpose

Definition

Owner

Lifecycle

Relationships

Invariants

Related ADRs

Related Stories

References

Concept documentation explains business meaning rather than implementation.

---

# ADR Template

Metadata

Status

Context

Problem Statement

Decision

Architecture

Alternatives Considered

Consequences

Implementation Impact

Migration Strategy

Related Decisions

References

---

# Story Template

Metadata

Goal

Context

Problem

Scope

Out of Scope

Acceptance Criteria

Constraints

Relevant ADRs

Relevant Modules

Validation

Definition of Done

Stories define implementation scope.

---

# Repository Analysis Template

Purpose

Repository Overview

Current Implementation

Relevant Components

Dependencies

Architecture Alignment

Risks

Recommendations

Open Questions

Repository Analysis documents the current repository state.

---

# Implementation Plan Template

Executive Summary

Objectives

Architecture Impact

Implementation Strategy

Technical Tasks

Validation Plan

Risks

Rollback Strategy

Expected Deliverables

Implementation Plans describe how a Story will be implemented.

---

# Implementation Report Template

Summary

Implemented Changes

Modified Components

Validation Executed

Known Issues

Follow-up Work

Implementation Reports describe completed work.

---

# Code Review Template

Scope

Review Summary

Architecture Compliance

Code Quality

Validation

Findings

Recommendations

Approval Status

Code Reviews evaluate implementation quality.

---

# Engineering Report Template

Summary

Completed Workflow

Implemented Features

Validation Summary

Lessons Learned

Remaining Work

Engineering Reports summarize the complete engineering outcome.

---

# Cross References

Templates should reference:

- ADRs
- Stories
- Domain documentation
- Concept documentation
- Engineering Reports

Avoid copying information already maintained elsewhere.

---

# Optional Sections

Some document types may include additional sections when appropriate.

Examples:

Assumptions

Security Considerations

Performance Considerations

Examples

Glossary

Decision Matrix

Open Questions

These sections should only be added when they improve understanding.

---

# Template Evolution

Templates are engineering standards.

When a template changes:

- update the standard;
- communicate the change;
- apply it only to future documents unless migration is required.

Avoid frequent structural changes.

Consistency is generally more valuable than perfection.