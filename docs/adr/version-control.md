# Version Control

The main version control model is based on change sets.

```text
User edits modules or imports a PDF
        ↓
create study_program_change_sets row
        ↓
save module_field_changes rows
        ↓
current module data changes
```

`study_program_change_sets` stores one high-level operation, for example a manual edit, PDF import, restore, or tag restore.

`module_field_changes` stores the concrete field-level changes that belong to one change set. Each row stores:

```text
change_set_id
module_id
field_path
old_value
new_value
```

Restore uses `old_value` from `module_field_changes` to write fields back to the state before a selected change set.

Named modulebook tags still use a snapshot:

```text
User creates a modulebook tag
        ↓
take current study program + modules
        ↓
store JSON in modulebook_tags.snapshot_json
```

The tag snapshot is not the old module version system. It is only used to freeze and restore a named modulebook state.
