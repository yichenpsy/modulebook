ALTER TABLE study_programs
ALTER COLUMN code TYPE TEXT,
ALTER COLUMN degree_type TYPE TEXT;

ALTER TABLE study_examination_regulations
ALTER COLUMN version TYPE TEXT;

ALTER TABLE modulebooks
ALTER COLUMN version TYPE TEXT;

ALTER TABLE modules
ALTER COLUMN module_number TYPE TEXT,
ALTER COLUMN module_type TYPE TEXT,
ALTER COLUMN level TYPE TEXT,
ALTER COLUMN status TYPE TEXT;

ALTER TABLE teaching_forms
ALTER COLUMN code TYPE TEXT;

ALTER TABLE examination_forms
ALTER COLUMN code TYPE TEXT;

ALTER TABLE module_examinations
ALTER COLUMN selection_rule TYPE TEXT;

ALTER TABLE app_users
ALTER COLUMN role TYPE TEXT;
