CREATE TABLE study_program_change_sets (
    id BIGSERIAL PRIMARY KEY,
    study_program_id BIGINT NOT NULL REFERENCES study_programs(id) ON DELETE CASCADE,
    source_type VARCHAR(50) NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE module_versions
    ADD COLUMN change_set_id BIGINT;

ALTER TABLE module_versions
    ADD CONSTRAINT module_versions_change_set_id_fkey
    FOREIGN KEY (change_set_id) REFERENCES study_program_change_sets(id) ON DELETE SET NULL;
