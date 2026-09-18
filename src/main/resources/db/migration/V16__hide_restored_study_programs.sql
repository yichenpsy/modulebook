ALTER TABLE study_programs
    ADD COLUMN hidden_at TIMESTAMPTZ;

ALTER TABLE study_programs
    ADD COLUMN hidden_by_change_set_id BIGINT;

ALTER TABLE study_programs
    ADD CONSTRAINT study_programs_hidden_by_change_set_id_fkey
    FOREIGN KEY (hidden_by_change_set_id) REFERENCES study_program_change_sets(id) ON DELETE SET NULL;
