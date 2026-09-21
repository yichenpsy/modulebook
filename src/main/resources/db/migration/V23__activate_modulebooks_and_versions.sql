ALTER TABLE modulebooks
ALTER COLUMN study_examination_regulation_id DROP NOT NULL;

ALTER TABLE modulebooks
ALTER COLUMN version DROP NOT NULL;

ALTER TABLE modulebooks
ADD COLUMN IF NOT EXISTS study_program_id BIGINT,
ADD COLUMN IF NOT EXISTS title TEXT,
ADD COLUMN IF NOT EXISTS modulebook_release_date DATE,
ADD COLUMN IF NOT EXISTS study_examination_regulations_date DATE;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'modulebooks_study_program_id_fkey'
    ) THEN
        ALTER TABLE modulebooks
        ADD CONSTRAINT modulebooks_study_program_id_fkey
        FOREIGN KEY (study_program_id) REFERENCES study_programs(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_modulebooks_study_program_id
ON modulebooks (study_program_id)
WHERE study_program_id IS NOT NULL;

INSERT INTO modulebooks (
    study_program_id,
    title,
    status,
    modulebook_release_date,
    study_examination_regulations_date
)
SELECT
    study_programs.id,
    trim(study_programs.name || ' ' || study_programs.degree_type || ' Modulebook'),
    'DRAFT',
    study_programs.modulebook_release_date,
    study_programs.study_examination_regulations_date
FROM study_programs
WHERE NOT EXISTS (
    SELECT 1
    FROM modulebooks
    WHERE modulebooks.study_program_id = study_programs.id
);

UPDATE modules
SET modulebook_id = modulebooks.id
FROM modulebooks
WHERE modules.modulebook_id IS NULL
  AND modules.study_program_id = modulebooks.study_program_id;

DO $$
BEGIN
    IF to_regclass('public.modulebook_versions') IS NULL
       AND to_regclass('public.modulebook_tags') IS NOT NULL THEN
        ALTER TABLE modulebook_tags RENAME TO modulebook_versions;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'modulebook_versions'
          AND column_name = 'tag_message'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'modulebook_versions'
          AND column_name = 'version_message'
    ) THEN
        ALTER TABLE modulebook_versions RENAME COLUMN tag_message TO version_message;
    END IF;
END $$;

ALTER TABLE modulebook_versions
ADD COLUMN IF NOT EXISTS modulebook_id BIGINT,
ADD COLUMN IF NOT EXISTS version_number INTEGER;

UPDATE modulebook_versions
SET modulebook_id = modulebooks.id
FROM modulebooks
WHERE modulebook_versions.modulebook_id IS NULL
  AND modulebook_versions.study_program_id = modulebooks.study_program_id;

WITH numbered_versions AS (
    SELECT
        id,
        row_number() OVER (
            PARTITION BY modulebook_id
            ORDER BY created_at, id
        ) AS generated_version_number
    FROM modulebook_versions
    WHERE modulebook_id IS NOT NULL
)
UPDATE modulebook_versions
SET version_number = numbered_versions.generated_version_number
FROM numbered_versions
WHERE modulebook_versions.id = numbered_versions.id
  AND modulebook_versions.version_number IS NULL;

ALTER TABLE modulebook_versions
ALTER COLUMN modulebook_id SET NOT NULL,
ALTER COLUMN version_number SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'modulebook_versions_modulebook_id_fkey'
    ) THEN
        ALTER TABLE modulebook_versions
        ADD CONSTRAINT modulebook_versions_modulebook_id_fkey
        FOREIGN KEY (modulebook_id) REFERENCES modulebooks(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_modulebook_versions_number
ON modulebook_versions (modulebook_id, version_number);
