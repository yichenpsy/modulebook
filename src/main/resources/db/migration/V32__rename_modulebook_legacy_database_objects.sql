ALTER TABLE IF EXISTS study_program_change_sets
RENAME TO modulebook_change_sets;

ALTER SEQUENCE IF EXISTS study_program_change_sets_id_seq
RENAME TO modulebook_change_sets_id_seq;

ALTER TABLE modulebook_change_sets
ALTER COLUMN id SET DEFAULT nextval('modulebook_change_sets_id_seq'::regclass);

ALTER SEQUENCE IF EXISTS modulebook_change_sets_id_seq
OWNED BY modulebook_change_sets.id;

ALTER SEQUENCE IF EXISTS modulebook_tags_id_seq
RENAME TO modulebook_versions_id_seq;

ALTER TABLE modulebook_versions
ALTER COLUMN id SET DEFAULT nextval('modulebook_versions_id_seq'::regclass);

ALTER SEQUENCE IF EXISTS modulebook_versions_id_seq
OWNED BY modulebook_versions.id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'study_program_change_sets_pkey'
    ) THEN
        ALTER TABLE modulebook_change_sets
        RENAME CONSTRAINT study_program_change_sets_pkey TO modulebook_change_sets_pkey;
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'study_program_change_sets_discarded_by_change_set_id_fkey'
    ) THEN
        ALTER TABLE modulebook_change_sets
        RENAME CONSTRAINT study_program_change_sets_discarded_by_change_set_id_fkey
        TO modulebook_change_sets_discarded_by_change_set_id_fkey;
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'study_program_change_sets_modulebook_id_fkey'
    ) THEN
        ALTER TABLE modulebook_change_sets
        RENAME CONSTRAINT study_program_change_sets_modulebook_id_fkey
        TO modulebook_change_sets_modulebook_id_fkey;
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'modulebook_tags_pkey'
    ) THEN
        ALTER TABLE modulebook_versions
        RENAME CONSTRAINT modulebook_tags_pkey TO modulebook_versions_pkey;
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'modulebook_tags_source_change_set_id_fkey'
    ) THEN
        ALTER TABLE modulebook_versions
        RENAME CONSTRAINT modulebook_tags_source_change_set_id_fkey
        TO modulebook_versions_source_change_set_id_fkey;
    END IF;
END $$;
