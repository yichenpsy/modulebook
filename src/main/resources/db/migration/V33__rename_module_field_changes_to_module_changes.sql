ALTER TABLE module_field_changes
    RENAME TO module_changes;

ALTER TABLE module_changes
    RENAME COLUMN field_path TO change_path;

ALTER TABLE module_changes
    RENAME CONSTRAINT module_field_changes_pkey TO module_changes_pkey;

ALTER TABLE module_changes
    RENAME CONSTRAINT module_field_changes_change_set_id_fkey TO module_changes_change_set_id_fkey;

ALTER TABLE module_changes
    RENAME CONSTRAINT module_field_changes_module_id_fkey TO module_changes_module_id_fkey;

ALTER SEQUENCE IF EXISTS module_field_changes_id_seq
    RENAME TO module_changes_id_seq;
