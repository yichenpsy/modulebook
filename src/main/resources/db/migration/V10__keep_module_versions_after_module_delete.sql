ALTER TABLE module_versions
    ADD COLUMN study_program_id BIGINT;

UPDATE module_versions
SET study_program_id = modules.study_program_id
FROM modules
WHERE module_versions.module_id = modules.id;

ALTER TABLE module_versions
    ALTER COLUMN study_program_id SET NOT NULL;

ALTER TABLE module_versions
    DROP CONSTRAINT module_versions_module_id_fkey;

ALTER TABLE module_versions
    ALTER COLUMN module_id DROP NOT NULL;

ALTER TABLE module_versions
    ADD CONSTRAINT module_versions_module_id_fkey
    FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE SET NULL;

ALTER TABLE module_versions
    ADD CONSTRAINT module_versions_study_program_id_fkey
    FOREIGN KEY (study_program_id) REFERENCES study_programs(id) ON DELETE CASCADE;
