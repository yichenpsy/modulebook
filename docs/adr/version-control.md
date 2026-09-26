# Version Control

The main version control model is based on change sets.

```text
User edits modules or imports a PDF
        ↓
create modulebook_change_sets row
        ↓
save module_changes rows
        ↓
current module data changes
```

`modulebook_change_sets` stores one high-level operation, for example a manual edit, PDF import, restore, or version restore.

`module_changes` stores the concrete module changes that belong to one change set. Each row stores:

```text
change_set_id
module_id
change_path
old_value
new_value
```

Restore uses `old_value` from `module_changes` to write fields back to the state before a selected change set.

Named modulebook tags still use a snapshot:

```text
User creates a modulebook version
        ↓
take current modulebook + modules
        ↓
store JSON in modulebook_versions.snapshot_json
```

The version snapshot is used to freeze and restore a named modulebook state.
