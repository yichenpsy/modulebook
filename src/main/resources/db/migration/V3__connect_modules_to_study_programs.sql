-- Add the column that links each module to one study_programs row.
ALTER TABLE modules
ADD COLUMN study_program_id BIGINT;

-- This foreign key guarantees that modules.study_program_id points to a real study program.
ALTER TABLE modules
ADD CONSTRAINT fk_modules_study_program
FOREIGN KEY (study_program_id)
REFERENCES study_programs(id);

-- code was globally unique before. That is too strict because many programs can have B01.
ALTER TABLE modules
DROP CONSTRAINT modules_code_key;

-- A module code must now be unique only inside the same study program.
ALTER TABLE modules
ADD CONSTRAINT uq_modules_study_program_code
UNIQUE (study_program_id, code);
