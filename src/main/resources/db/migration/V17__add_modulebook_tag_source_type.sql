ALTER TABLE modulebook_tags
    ADD COLUMN source_type VARCHAR(50) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE modulebook_tags
    ADD COLUMN source_change_set_id BIGINT;

ALTER TABLE modulebook_tags
    ADD CONSTRAINT modulebook_tags_source_change_set_id_fkey
    FOREIGN KEY (source_change_set_id) REFERENCES study_program_change_sets(id) ON DELETE SET NULL;
