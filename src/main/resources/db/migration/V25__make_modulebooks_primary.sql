ALTER TABLE modulebooks
ADD COLUMN IF NOT EXISTS study_program_name TEXT,
ADD COLUMN IF NOT EXISTS degree_type TEXT;

UPDATE modulebooks
SET study_program_name = COALESCE(NULLIF(modulebooks.study_program_name, ''), study_programs.name),
    degree_type = COALESCE(NULLIF(modulebooks.degree_type, ''), study_programs.degree_type)
FROM study_programs
WHERE modulebooks.study_program_id = study_programs.id;

UPDATE modulebooks
SET study_program_name = COALESCE(NULLIF(study_program_name, ''), NULLIF(title, ''), 'Modulebook ' || id),
    degree_type = COALESCE(NULLIF(degree_type, ''), 'BACHELOR');

ALTER TABLE modulebooks
ALTER COLUMN study_program_name SET NOT NULL,
ALTER COLUMN degree_type SET NOT NULL;

UPDATE modules
SET modulebook_id = modulebooks.id
FROM modulebooks
WHERE modules.modulebook_id IS NULL
  AND modules.study_program_id = modulebooks.study_program_id;

ALTER TABLE study_program_change_sets
ADD COLUMN IF NOT EXISTS modulebook_id BIGINT;

UPDATE study_program_change_sets
SET modulebook_id = modulebooks.id
FROM modulebooks
WHERE study_program_change_sets.modulebook_id IS NULL
  AND study_program_change_sets.study_program_id = modulebooks.study_program_id;

ALTER TABLE study_program_change_sets
ALTER COLUMN modulebook_id SET NOT NULL;

ALTER TABLE study_program_change_sets
DROP CONSTRAINT IF EXISTS study_program_change_sets_study_program_id_fkey;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'study_program_change_sets_modulebook_id_fkey'
    ) THEN
        ALTER TABLE study_program_change_sets
        ADD CONSTRAINT study_program_change_sets_modulebook_id_fkey
        FOREIGN KEY (modulebook_id) REFERENCES modulebooks(id) ON DELETE CASCADE;
    END IF;
END $$;

ALTER TABLE modulebook_versions
DROP CONSTRAINT IF EXISTS modulebook_tags_study_program_id_fkey;

ALTER TABLE modulebook_versions
DROP COLUMN IF EXISTS study_program_id;

ALTER TABLE modules
DROP CONSTRAINT IF EXISTS fk_modules_study_program;

ALTER TABLE modules
DROP COLUMN IF EXISTS study_program_id;

ALTER TABLE modules
DROP CONSTRAINT IF EXISTS modules_modulebook_id_fkey;

ALTER TABLE modules
ADD CONSTRAINT modules_modulebook_id_fkey
FOREIGN KEY (modulebook_id) REFERENCES modulebooks(id) ON DELETE CASCADE;

ALTER TABLE module_changes
DROP CONSTRAINT IF EXISTS module_changes_modulebook_id_fkey;

ALTER TABLE module_changes
ADD CONSTRAINT module_changes_modulebook_id_fkey
FOREIGN KEY (modulebook_id) REFERENCES modulebooks(id) ON DELETE CASCADE;

DROP INDEX IF EXISTS uq_modulebooks_study_program_id;

ALTER TABLE modulebooks
DROP CONSTRAINT IF EXISTS modulebooks_study_program_id_fkey,
DROP CONSTRAINT IF EXISTS modulebooks_study_examination_regulation_id_fkey,
DROP CONSTRAINT IF EXISTS uq_modulebook_regulation_version;

ALTER TABLE modulebooks
DROP COLUMN IF EXISTS study_program_id,
DROP COLUMN IF EXISTS study_examination_regulation_id,
DROP COLUMN IF EXISTS version,
DROP COLUMN IF EXISTS status_date,
DROP COLUMN IF EXISTS status,
DROP COLUMN IF EXISTS source_pdf,
DROP COLUMN IF EXISTS generated_pdf,
DROP COLUMN IF EXISTS title,
DROP COLUMN IF EXISTS modulebook_release_date,
DROP COLUMN IF EXISTS study_examination_regulations_date;

ALTER TABLE study_program_change_sets
DROP COLUMN IF EXISTS study_program_id;

DROP TABLE IF EXISTS study_examination_regulations;
DROP TABLE IF EXISTS study_programs;

CREATE UNIQUE INDEX IF NOT EXISTS uq_modulebooks_study_program_name_degree_type
ON modulebooks (study_program_name, degree_type);
