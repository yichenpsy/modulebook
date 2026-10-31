# Versioning and restore

The application keeps the current module data, grouped change history, and named handbook snapshots. They serve different purposes.

## Change sets

`modulebook_change_sets` groups an operation such as a manual edit, PDF import, deletion, or restore. `module_changes` stores its individual changes using `change_set_id`, `module_id`, `change_path`, `old_value`, and `new_value`.

`ModuleChangeLogService` records changes to module fields, workload, teaching forms, and module lifecycle. A path such as `module.title` identifies the changed value. The history interface uses these records to explain how the current state was reached.

The database `audit_log` is separate: triggers capture row operations for database inspection. It is not the application's restore model or a user-attributed audit trail.

## Named versions

`ModulebookVersionService` serializes a `ModulebookSnapshot` containing modulebook metadata and module responses into `modulebook_versions.snapshot_json`. A version also carries its message, source type, and release/regulation dates.

`buildSnapshot` includes only modules whose status is `ACTIVE`, sorted by module number, with their workload and teaching forms. A saved version is a publication snapshot, not a full backup of active and deactivated workspace data. `modulebook-view.html?versionId=...` reads that snapshot for historical viewing and printing; the ID comes from the saved-version API.

The first PDF-import save creates the protected initial version, with message `Version 1` and source type `INITIAL_IMPORT`. This reflects the saved result, so inspect import errors before relying on its completeness. Manually saved versions use source type `MANUAL`. Initial-import versions cannot be deleted through the version API.

The browser compares selected history/version points by aggregating recorded changes. Saved versions establish points in that history; the comparison is not a direct diff of two complete snapshot JSON documents. There is no dedicated server-side comparison endpoint.

## Displayed version numbers

The history page calculates major/minor labels such as `1.0`, `1.1`, and `2.0` from chronological saved-version and change-set events. In the ordinary forward sequence, a saved version establishes a major point and later change sets increment the minor part. Restore events have special handling, so the full history is not simply an always-increasing counter.

These labels are calculated by `calculateHistoryVersions` in `modulebook-history.html`; the comparison page builds its own version points in `buildVersionPoints`. They are presentation values, separate from database IDs and the integer `modulebook_versions.version_number` assigned when a snapshot is saved. Use IDs in API requests. If changing numbering, review both pages together.

## Operation boundaries

| Operation | Reads / changes | Important limit |
| --- | --- | --- |
| View current handbook | Current active modules | Deactivated modules remain stored but are excluded |
| View / print saved version | Stored snapshot | Independent of later edits or deletion of its modules |
| Compare version points | Change records and saved-version metadata loaded into JavaScript | Aggregates recorded differences; does not change stored data |
| Delete saved version | Selected `modulebook_versions` row | Initial-import version is protected; current modules are not deleted |
| Restore named version | Snapshot reconciled with current modules | Records a new change set; may recreate records with different IDs |
| Restore before change set | Target and later active change history | Can delete later-created modules after force confirmation |
| Reset to initial import | Later changes and saved versions | Cleans history; differs from an ordinary restore |

## Restore operations

| Operation | Endpoint (`POST`) | Effect |
| --- | --- | --- |
| Restore before a change set | `/api/change-sets/{id}/restore-before?force=false` | Reverts the target and later active change sets for the same modulebook; records a restore operation and marks reverted change sets discarded |
| Restore a named version | `/api/modulebook-versions/{id}/restore` | Reconciles current modules with the snapshot and records a new change set |
| Reset to initial import | `/api/change-sets/{id}/reset-to-initial-import` | Reverts later changes and removes later change sets and saved versions, retaining the initial import version |

Use the relevant change-set or version ID returned by the API, not a module ID.

### Restore before

`ChangeSetRestoreService.restoreBefore` uses the earliest old value for each changed path in the selected range. It can physically delete modules created within that range. The default `force=false` returns HTTP 409 when such modules exist; `force=true` allows their deletion. Discarded, deletion, PDF-import, and restore-before change sets cannot themselves be targets.

Lifecycle records are skipped when restoring fields, and records for physically deleted modules no longer have a usable module reference. A deletion later in the selected range is therefore not reversed. Restore-before is not guaranteed to reconstruct the complete earlier state when modules have been physically deleted.

### Restore a named version

`ModulebookVersionService.restore` updates matching modules, recreates missing modules, and sets currently active modules absent from the snapshot to status `DEACTIVE`. It restores workload and teaching-form data as part of module state. Recreated modules may receive new database IDs.

The snapshot includes modulebook metadata, but the restore implementation reconciles modules; it does not rewrite the modulebook's identity or version dates. Restore is transactional and produces history rather than replacing the snapshot.

### Reset to initial import

`resetToInitialImport` accepts only the first PDF-import change set. Unlike an ordinary restore, it deletes later application history and non-initial saved versions. Treat it as a destructive history cleanup; use disposable data when testing it.

Reset uses recorded old values, not the initial snapshot. It removes later-created modules but cannot recreate initial modules that were physically deleted later. For recovery, use a saved-version restore containing that module instead of assuming reset can recover it. A subsequent reset can also remove modules recreated by that restore. Neither restore-before nor reset can be assumed to reproduce a snapshot exactly.

## Deletion boundaries

Direct module deletion is permanent and requires the exact confirmation string `DELETE`. It cannot be undone through restore-before; a saved snapshot containing that module can recreate it. Modulebook deletion also requires `DELETE` and removes related data through database relationships. These confirmation strings are implemented checks, not authentication.

Entry points: `ModuleChangeLogService`, `ChangeSetRestoreService`, `ModulebookVersionService`, and the browser history/comparison pages in `src/main/resources/static/`.
