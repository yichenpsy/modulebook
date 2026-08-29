# Version Control

This document explains how module version control works in Modulebook.

The current implementation uses a snapshot-first model. This means the app saves a full copy of the module state before an update happens. Later, the app can list old snapshots, restore one snapshot, or compare two snapshots.

The app also groups related module snapshots into a study program change set. A change set is one user operation, for example one manual edit or one PDF import. It is not a full study program snapshot.

It is not a Git-style system that stores only patches. The database stores complete snapshots.

## Main Idea

When a user updates a module, the frontend first asks the user to confirm a change message.
The backend then saves the current module state before writing the new module data:

```text
User saves module
        |
        v
Frontend generates default change message
        |
        v
User confirms or edits change message
        |
        v
ModuleService.update(...)
        |
        v
StudyProgramChangeSetService.create(...)
        |
        v
ModuleVersionService.saveSnapshot(...)
        |
        v
INSERT INTO module_versions
        |
        v
Update the real module data
```

This order is important. The saved version is the state before the change. If the user makes a mistake, that old state can be restored.

The change message is required for normal module updates. The frontend sends it as `versionReason`.
The backend stores it in `module_versions.reason`.

The names are different because they describe different layers:

```text
Change message   User-facing label in the edit and history pages
versionReason    JSON field sent from frontend to backend
reason           Database column in module_versions
```

## Database Table

Module versions are stored in:

```text
module_versions
```

Important columns:

```text
id              Primary key of the version row
module_id       The module this version belongs to
version_number  Version number inside one module, for example 1, 2, 3
snapshot_json   Full JSON snapshot of the old module state
reason          Why the snapshot was created
created_at      Time when the snapshot was saved
change_set_id   Optional link to the operation that created this snapshot
```

`module_id` comes from `modules.id`.

`version_number` is calculated per module. The repository checks the highest existing version for this module and adds 1.

`snapshot_json` is JSONB in PostgreSQL. It stores a serialized `ModuleSnapshot`.

`reason` stores the exact change message confirmed by the user for module updates.
For restore actions, the backend creates an automatic reason like `before restore to version 2`.

## Study Program Change Sets

Change sets are stored in:

```text
study_program_change_sets
```

Important columns:

```text
id                Primary key of the change set
study_program_id  The study program affected by this operation
source_type       Where the operation came from
reason            Human-readable operation message
created_at        Time when the operation started
```

Current source types:

```text
MANUAL_EDIT
PDF_IMPORT
RESTORE
DELETE
INITIAL_MODULE
```

`change_set_id` in `module_versions` points to `study_program_change_sets.id`.
This is what connects many module snapshots to one operation.

Example:

```text
study_program_change_sets.id = 15
source_type = PDF_IMPORT
reason = before PDF re-import

module_versions:
B01 version 4 -> change_set_id 15
B02 version 3 -> change_set_id 15
B05 version 2 -> change_set_id 15
```

This means one PDF import changed B01, B02, and B05.
The module histories still remain separate, but the study program history page can group them as one operation.

Change sets are different from future study program versions:

```text
StudyProgramChangeSet
= one operation that changed one or more modules

StudyProgramVersion
= a complete snapshot of the whole study program
```

The current code implements `StudyProgramChangeSet`, not `StudyProgramVersion`.

## Snapshot Structure

The snapshot object is:

```java
public class ModuleSnapshot {
    private Module module;
    private ModuleWorkload workload;
    private List<Long> teachingFormIds;
}
```

It has three data blocks because a module's editable state is stored in more than one table.

```text
module
```

Comes from the `modules` table. It contains fields like `moduleNumber`, `name`, `credits`, `description`, `contents`, `learningObjectives`, and `examinationForm`.

```text
workload
```

Comes from `module_workloads` and `presence_hour_components`. It contains presence hours, self-study hours, raw workload text, and SWS components.

```text
teachingFormIds
```

Comes from the `module_teaching_forms` join table. It stores which teaching forms are connected to the module.

The snapshot does not store study program data, users, or import reports because restore currently targets the module edit state only.

## Backend Classes

Main classes:

```text
ModuleService
```

Calls `versionService.saveSnapshot(...)` before updating a module.

```text
ModuleVersionService
```

Creates snapshots, lists history, and restores old versions.

```text
ModuleVersionRepository
```

Reads and writes rows in `module_versions`.

```text
ModuleSnapshot
```

Defines what data is saved in `snapshot_json`.

```text
ModuleDiffService
```

Compares two snapshots and returns changed fields.

```text
ModuleVersionController
```

Exposes the version-control HTTP API.

## API Endpoints

Update a module and save a version snapshot:

```text
PUT /api/modules/{moduleId}
```

The request body must include:

```text
versionReason
```

`versionReason` comes from the commit message textarea shown after the user clicks `Save`.
The textarea is prefilled by the frontend with an automatically generated message, for example:

```text
changed module number from "B01" to "B02"
```

The user can keep this default message or edit it.
After the user clicks `Confirm save`, the frontend sends the final textarea value as `versionReason`.

If `versionReason` is empty, the backend returns:

```text
400 Commit message is required
```

This prevents history rows from being saved without a user-confirmed message.

List versions for one module:

```text
GET /api/modules/{moduleId}/versions
```

`moduleId` comes from the module page URL, for example:

```text
/module-edit.html?id=12
```

Here `12` is the module id.

Restore one version:

```text
POST /api/modules/{moduleId}/versions/{versionNumber}/restore
```

`versionNumber` comes from the `version_number` column in `module_versions`.

Before restore, the backend saves the current state as a new snapshot. This makes restore reversible.

Compare two versions:

```text
GET /api/modules/{moduleId}/versions/compare?from=1&to=2
```

`from` and `to` are version numbers. They are not database ids. They mean "compare version 1 with version 2 for this module".

The compare response contains:

```text
fromVersionNumber
toVersionNumber
changes
```

Each change has:

```text
path      Field path, for example module.credits
oldValue  Value from the from-version
newValue  Value from the to-version
```

## Restore Flow

Restore works like this:

```text
POST restore endpoint
        |
        v
Find selected ModuleVersion
        |
        v
Save current state as "before restore ..."
        |
        v
Parse snapshot_json into ModuleSnapshot
        |
        v
Update modules table
        |
        v
Restore workload
        |
        v
Restore teaching form links
```

If the snapshot has no workload, the current workload is deleted.

If the snapshot has workload data, it is upserted.

Teaching forms are restored by replacing the current module teaching form links with `teachingFormIds` from the snapshot.

## Compare Flow

Compare works on snapshot JSON trees.

```text
Load snapshot_json for from-version
Load snapshot_json for to-version
Parse both as JSON
Walk both JSON trees
Collect changed leaf values
Return list of changed fields
```

The backend normalizes values for display comparison. This prevents noise from invisible differences.

Currently ignored differences:

```text
null vs ""
multiple spaces vs one space
leading spaces
trailing spaces
line break format differences
```

The normalization is:

```java
value.replaceAll("\\s+", " ").trim()
```

This means a field is not shown as changed when only whitespace formatting changed.

If real text changes, the field is still shown.

## Frontend Pages

Edit one module:

```text
/module-edit.html?id={moduleId}
```

The first `Save` click does not immediately call the update API.
Instead, the page builds a pending request from the current form fields and opens a commit message panel near the Save button.

The default message is generated by comparing:

```text
currentModule          Old module data loaded from GET /api/modules/{moduleId}
pendingModuleRequest   New module data collected from the edit form
```

Example:

```text
currentModule.moduleNumber = "B01"
pendingModuleRequest.moduleNumber = "B02"
```

This creates:

```text
changed module number from "B01" to "B02"
```

The user can change this text.
When the user clicks `Confirm save`, the page sends the final text as `versionReason`.

After a successful save, the page redirects to:

```text
/module-versions.html?id={moduleId}
```

Single module history:

```text
/module-versions.html?id={moduleId}
```

Shows all versions for one module. It also links to restore and compare actions.
The `Change message` table column displays `version.reason` from the history API response.
This is the value stored in `module_versions.reason`.

Compare two module versions:

```text
/module-version-compare.html?id={moduleId}
```

The page loads all versions for the module and lets the user choose a from-version and to-version.

It can also be opened directly:

```text
/module-version-compare.html?id=12&from=1&to=2
```

Study program history:

```text
/study-program-history.html?id={studyProgramId}
```

This page gets all modules in one study program, loads each module's versions, and shows one combined history table.

`studyProgramId` comes from `study_programs.id`.

## Frontend Diff Highlighting

The compare page receives `oldValue` and `newValue` from the backend.

Then the frontend splits each value into tokens:

```text
words
spaces
punctuation
```

It uses an LCS-based comparison to find equal parts and changed parts.

UI behavior:

```text
removed text  red highlight and strikethrough
added text    green highlight
```

The backend decides which fields are different.

The frontend only highlights where the text differs inside those fields.

## Design Tradeoff

The current design favors simplicity:

```text
Store complete snapshots
Compare snapshots when needed
Restore by writing snapshot data back
```

Advantages:

```text
Easy to understand
Easy to restore
Works even if many fields change at once
Does not need a custom patch format
```

Tradeoffs:

```text
snapshot_json can become large
field-level history is computed later, not stored directly
schema changes may require snapshot compatibility handling later
```

For the current modulebook workflow, this is acceptable because module snapshots are not huge and restore correctness is more important than storage efficiency.
