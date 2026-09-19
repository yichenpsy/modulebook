ALTER TABLE study_programs
ADD COLUMN IF NOT EXISTS modulebook_release_date DATE,
ADD COLUMN IF NOT EXISTS study_examination_regulations_date DATE;

UPDATE study_programs
SET degree_type = 'BACHELOR'
WHERE degree_type IS NULL OR trim(degree_type) = '';

ALTER TABLE study_programs
ALTER COLUMN degree_type SET NOT NULL;

ALTER TABLE study_programs
ALTER COLUMN year DROP NOT NULL;

ALTER TABLE study_programs
DROP CONSTRAINT IF EXISTS uq_study_program_name_year;

ALTER TABLE study_programs
ADD CONSTRAINT uq_study_program_name_degree_type UNIQUE (name, degree_type);

ALTER TABLE modulebook_tags
ADD COLUMN IF NOT EXISTS modulebook_release_date DATE,
ADD COLUMN IF NOT EXISTS study_examination_regulations_date DATE;
