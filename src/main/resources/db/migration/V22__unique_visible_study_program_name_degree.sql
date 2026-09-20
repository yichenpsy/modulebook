ALTER TABLE study_programs
DROP CONSTRAINT IF EXISTS uq_study_program_name_degree_type;

CREATE UNIQUE INDEX IF NOT EXISTS uq_visible_study_program_name_degree_type
ON study_programs (name, degree_type)
WHERE hidden_at IS NULL;
