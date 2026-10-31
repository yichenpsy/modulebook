# Requirements and MVP scope

Modulebook replaces repeated editing of Word/PDF handbooks with structured module data, traceable changes, and generated publication views. The primary workflow is: create a modulebook, import or enter modules, review/edit them, inspect history, save a named version, and print the current or saved state.

This document preserves the thesis requirement IDs for handover. “Implemented” describes an available code path, not proof that all acceptance checks have passed. Current code takes precedence over thesis descriptions; verification procedures are in [Testing and evaluation](../development/testing.md).

## Users and boundaries

The thesis identified module coordinators, program directors, administrative reviewers, approval bodies, students, and administrators. These are domain stakeholders, not implemented permission roles. The MVP has no authentication or authorization; it does not enforce the institutional review and approval process.

A modulebook is the workspace for one study program and degree. There is no separate study-program management layer. Structured data is maintained independently of the printed document layout.

## Figma prototypes

The thesis (§2.2 and Appendix A.1) links these design artifacts used for requirements elicitation and prototype refinement:

| Design artifact | Purpose |
| --- | --- |
| [Interactive Module Editing Prototype](https://www.figma.com/make/RbVI7VFUM6jBFEylTizEjf/Interactive-Module-Editing-Page?t=gwycRc3H81xCqFWv-1) | Initial clickable prototype for exploring structured module editing with stakeholders |
| [Modulebook Core Function Design](https://www.figma.com/design/oXByJWO6d7CBeiTeUBwiN0/Modulebook?node-id=0-1&t=3J4Xl2OouvT62gjk-1) | Refined designs for core functions, including workload input and version control |

Prototype ideas may extend beyond the implemented MVP. The requirements and boundaries below describe the delivered scope.

## Functional requirements

All requirements below have implementation paths, with the qualifications shown.

| ID | Requirement | Current behavior / boundary |
| --- | --- | --- |
| FR1.1 | Structured module data | Module fields plus related workload and teaching-form records |
| FR1.2 | Modulebook management | List, create, view, and permanently delete modulebooks |
| FR1.3 | Module management | Create, read, edit, and permanently delete modules within a modulebook |
| FR1.4 | Input assistance and validation | Form constraints, predefined choices, contextual help, workload hints, DTO and service checks; validation differs by entry path |
| FR1.5 | Module lifecycle status | `ACTIVE` or `DEACTIVE`; current publication output includes active modules |
| FR2.1 | Change traceability | Reasons, timestamps, and old/new values; no authenticated author attribution |
| FR2.2 | Change sets | Group changes belonging to one operation |
| FR2.3 | Save a modulebook version | Named JSON snapshot of modulebook metadata and active modules, with publication dates |
| FR2.4 | Version history | Module and modulebook history pages |
| FR2.5 | Version comparison | Browser-side comparison of selected version points / change ranges |
| FR2.6 | Restore a previous state | Restore-before, named-version restore, and initial-import reset have different restrictions and effects |
| FR3.1 | PDF import | Upload, normalize, review, and separately save extracted data |
| FR3.2 | Module and field detection | Rule-based extraction with configurable labels; human review remains necessary |
| FR3.3 | Initial imported version | First PDF-import save path creates protected `Version 1` even if all rows fail; inspect the save result before relying on it |
| FR3.4 | Re-import and update | Match modules by supplied ID or module number; preserve existing status and mark missing modules |
| FR4.1 | Generate handbook view | Browser renders structured data as print-ready HTML |
| FR4.2 | Export current handbook | Browser print / Save as PDF for active current modules |
| FR4.3 | Export a saved version | Browser renders the stored snapshot independently of subsequent module edits/deletion |

Implementation details: [Architecture](../architecture/overview.md), [Versioning](../architecture/versioning.md), and [PDF import](../architecture/pdf-import.md).

## Non-functional requirements

| ID | Goal | Evidence and limit |
| --- | --- | --- |
| NFR1 | Usability for occasional users | Structured input, warnings, contextual help, and prototype feedback; no formal usability evaluation of the final implementation in the thesis |
| NFR2 | Maintainability | Layered Java code, explicit JDBC, Flyway migrations, and separate extraction service; architectural evidence rather than a measured maintenance outcome |
| NFR3 | Deployability and portability | Dockerfiles and GitLab/Kubernetes configuration; BHT deployment reported in the thesis, with environment-specific paths, credentials, and infrastructure still requiring adaptation |
| NFR4 | Technical observability | Java agent and Python FastAPI tracing configured for an external collector; live collection and correlation must be verified in the destination environment |

## Outside the MVP

Authentication/login, role-based permissions, a complete approval workflow, and collaborative editing were deferred. Prototype discussions also included field administration, bulk editing, module references, and automatic study-plan generation; these requests must not be treated as delivered features or a committed roadmap. Future implementation requires its own scope and acceptance criteria.

## Source and implementation differences

Source: Yichen Zhong, *User-Centered Design and Development of a System for Management of Module Handbooks*, submitted 1 October 2026; Chapters 2–5 and Appendix A. The thesis is the source for requirement IDs and historical evaluation, not a substitute for current code.

| Topic | Current implementation used by these docs | Difference from the thesis |
| --- | --- | --- |
| SWS guidance | Editor calculates `sum(SWS) × 17`; no `0.75` conversion | §2.2.2 describes `sum(SWS) × 0.75 × 17`. This is an unresolved domain-rule discrepancy; documenting code does not validate the formula |
| Missing modules on re-import | Backend adds `MISSING_FROM_IMPORT` and preserves status | §3.4.2 also describes deactivation; the backend does not automatically do this |
| Comparison and printing | Java supplies data; JavaScript performs comparison and renders printable HTML | Parts of §4.5.3 and §4.7 attribute this work to the backend |
| PDF evaluation counts | Thesis counts are historical results, not current configured expectations | Table 4.3 and `import_manifest.yaml` differ; match source PDF versions and rerun before drawing current conclusions |

The SWS behavior is defined in `module-edit.html`; missing-module handling in `PdfImportService`; comparison in the two version-comparison pages; publication rendering in `modulebook-view.html`. These files are under the locations listed in the architecture guide. No application behavior was changed to reconcile these differences.
