ALTER TABLE study_program_change_sets
    ADD COLUMN discarded_at TIMESTAMPTZ;

ALTER TABLE study_program_change_sets
    ADD COLUMN discarded_by_change_set_id BIGINT;

ALTER TABLE study_program_change_sets
    ADD CONSTRAINT study_program_change_sets_discarded_by_change_set_id_fkey
    FOREIGN KEY (discarded_by_change_set_id) REFERENCES study_program_change_sets(id) ON DELETE SET NULL;

ALTER TABLE modules
    ADD COLUMN created_change_set_id BIGINT;

ALTER TABLE modules
    ADD CONSTRAINT modules_created_change_set_id_fkey
    FOREIGN KEY (created_change_set_id) REFERENCES study_program_change_sets(id) ON DELETE SET NULL;

ALTER TABLE module_field_changes
    DROP CONSTRAINT module_field_changes_module_id_fkey;

ALTER TABLE module_field_changes
    ALTER COLUMN module_id DROP NOT NULL;

ALTER TABLE module_field_changes
    ADD CONSTRAINT module_field_changes_module_id_fkey
    FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE SET NULL;
