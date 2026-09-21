DROP INDEX IF EXISTS uq_visible_study_program_name_degree_type;

ALTER TABLE study_programs
DROP CONSTRAINT IF EXISTS study_programs_hidden_by_change_set_id_fkey;

ALTER TABLE study_programs
DROP COLUMN IF EXISTS year,
DROP COLUMN IF EXISTS code,
DROP COLUMN IF EXISTS name_de,
DROP COLUMN IF EXISTS name_en,
DROP COLUMN IF EXISTS standard_semesters,
DROP COLUMN IF EXISTS total_credits,
DROP COLUMN IF EXISTS is_active,
DROP COLUMN IF EXISTS modulebook_release_date,
DROP COLUMN IF EXISTS study_examination_regulations_date,
DROP COLUMN IF EXISTS hidden_at,
DROP COLUMN IF EXISTS hidden_by_change_set_id,
DROP COLUMN IF EXISTS updated_at;

CREATE UNIQUE INDEX IF NOT EXISTS uq_study_program_name_degree_type
ON study_programs (name, degree_type);
