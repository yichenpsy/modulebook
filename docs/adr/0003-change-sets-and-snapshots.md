# ADR 0003: Combine change sets with named snapshots

Status: Accepted — implemented. Recorded retrospectively on 2026-10-31.

## Context

Developers and users need to understand individual edits, group related changes such as a PDF import, and preserve named handbook states. Explaining a change and restoring a saved handbook require different information.

## Decision

Keep editable module data in relational tables. Group operations in `modulebook_change_sets` and store their individual paths and old/new values in `module_changes`. Store named handbook versions as JSON snapshots in `modulebook_versions.snapshot_json`.

Use change records for history and restore-before operations. Use snapshots to reconcile modules with a named saved state. Keep database-trigger `audit_log` records separate from this application versioning model.

## Rationale and alternatives

The thesis (§4.3.4, pp. 32–33) describes an earlier approach that saved a complete module snapshot after every change. It was replaced because small edits duplicated substantial unchanged content. It also explains that change records alone can be insufficient for recovery after deletion or incomplete history, motivating deliberately saved handbook snapshots. This is thesis-reported design history; the current implementation remains the source for restore behavior.

- Change sets connect many field changes to one operation and reason. Snapshots alone would not directly explain which fields a particular operation changed.
- Snapshots capture a named state without requiring that state to be rebuilt from every earlier edit. Change records alone would require more reconstruction logic for this use case.
- Current relational data supports ordinary reads and edits directly. A full event-sourcing design would require rebuilding state from events or maintaining projections; the current implementation does neither as its primary persistence model.
- Database audit rows record low-level writes. Using them alone would not supply the application's operation grouping, reasons, or named versions.

## Consequences

The system maintains both change history and snapshots, increasing storage and consistency responsibilities. New editable fields must be considered in change logging, snapshot serialization, comparisons, and both restore paths. Stored JSON also creates compatibility work when DTOs or field names change.

Restore-before can delete modules created in the reverted range and requires explicit force when this occurs. Snapshot restore can recreate missing modules with new IDs and sets active modules absent from the snapshot to `DEACTIVE`; it does not restore the modulebook identity or version dates. Reset-to-initial-import deletes later application history and saved versions. These operations are not interchangeable, and none replaces a database backup.

Restore-before and initial-import reset skip lifecycle records when restoring fields, so neither can recreate physically deleted modules. Snapshot restore is the implemented recovery path for a deleted module preserved in a saved version.

## Implementation references

- [Change logging](../../src/main/java/de/bht/modulebook/module/version/ModuleChangeLogService.java) and [change-set restore](../../src/main/java/de/bht/modulebook/modulebook/changeset/ChangeSetRestoreService.java).
- [Snapshot version service](../../src/main/java/de/bht/modulebook/modulebook/ModulebookVersionService.java) and [snapshot structure](../../src/main/java/de/bht/modulebook/modulebook/ModulebookSnapshot.java).
- [Versioning guide](../architecture/versioning.md) for endpoints, restrictions, and deletion behavior.
- [Thesis source and implementation differences](../product/requirements.md#source-and-implementation-differences).
