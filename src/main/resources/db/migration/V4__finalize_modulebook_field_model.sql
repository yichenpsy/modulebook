ALTER TABLE study_programs
ADD COLUMN IF NOT EXISTS code VARCHAR(50),
ADD COLUMN IF NOT EXISTS name_de VARCHAR(255),
ADD COLUMN IF NOT EXISTS name_en VARCHAR(255),
ADD COLUMN IF NOT EXISTS degree_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS standard_semesters INTEGER,
ADD COLUMN IF NOT EXISTS total_credits NUMERIC(5,1),
ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE study_examination_regulations (
    id BIGSERIAL PRIMARY KEY,
    study_program_id BIGINT NOT NULL REFERENCES study_programs(id),
    version VARCHAR(50) NOT NULL,
    title TEXT NOT NULL,
    effective_from DATE,
    effective_until DATE,
    official_notice_number VARCHAR(100),
    source_pdf TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_regulation_program_version UNIQUE (study_program_id, version)
);

CREATE TABLE modulebooks (
    id BIGSERIAL PRIMARY KEY,
    study_examination_regulation_id BIGINT NOT NULL REFERENCES study_examination_regulations(id),
    version VARCHAR(50) NOT NULL,
    status_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    source_pdf TEXT,
    generated_pdf TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_modulebook_regulation_version UNIQUE (study_examination_regulation_id, version)
);

ALTER TABLE modules
RENAME COLUMN code TO module_number;

ALTER TABLE modules
RENAME CONSTRAINT uq_modules_study_program_code TO uq_modules_study_program_module_number;

ALTER TABLE modules
ADD COLUMN IF NOT EXISTS modulebook_id BIGINT REFERENCES modulebooks(id),
ADD COLUMN IF NOT EXISTS module_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS title_de TEXT,
ADD COLUMN IF NOT EXISTS title_en TEXT,
ADD COLUMN IF NOT EXISTS coordinator TEXT,
ADD COLUMN IF NOT EXISTS applicability TEXT,
ADD COLUMN IF NOT EXISTS learning_area TEXT,
ADD COLUMN IF NOT EXISTS learning_objectives TEXT,
ADD COLUMN IF NOT EXISTS prerequisites TEXT,
ADD COLUMN IF NOT EXISTS level VARCHAR(50),
ADD COLUMN IF NOT EXISTS status VARCHAR(50),
ADD COLUMN IF NOT EXISTS frequency TEXT,
ADD COLUMN IF NOT EXISTS contents TEXT,
ADD COLUMN IF NOT EXISTS literature TEXT,
ADD COLUMN IF NOT EXISTS additional_notes TEXT,
ADD COLUMN IF NOT EXISTS module_weight NUMERIC(6,2);

CREATE TABLE teaching_forms (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    label_de VARCHAR(255) NOT NULL,
    label_en VARCHAR(255)
);

CREATE TABLE module_teaching_forms (
    id BIGSERIAL PRIMARY KEY,
    module_id BIGINT NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    teaching_form_id BIGINT NOT NULL REFERENCES teaching_forms(id),
    details TEXT,
    CONSTRAINT uq_module_teaching_form UNIQUE (module_id, teaching_form_id)
);

CREATE TABLE module_workloads (
    id BIGSERIAL PRIMARY KEY,
    module_id BIGINT NOT NULL UNIQUE REFERENCES modules(id) ON DELETE CASCADE,
    presence_hours NUMERIC(6,2),
    self_study_hours NUMERIC(6,2),
    raw_text TEXT
);

CREATE TABLE presence_hour_components (
    id BIGSERIAL PRIMARY KEY,
    module_workload_id BIGINT NOT NULL REFERENCES module_workloads(id) ON DELETE CASCADE,
    teaching_form_id BIGINT REFERENCES teaching_forms(id),
    sws_amount NUMERIC(5,2),
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE examination_forms (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    label_de VARCHAR(255) NOT NULL,
    label_en VARCHAR(255)
);

CREATE TABLE module_examinations (
    id BIGSERIAL PRIMARY KEY,
    module_id BIGINT NOT NULL UNIQUE REFERENCES modules(id) ON DELETE CASCADE,
    selection_rule VARCHAR(50) NOT NULL DEFAULT 'FIXED',
    raw_text TEXT
);

CREATE TABLE examination_groups (
    id BIGSERIAL PRIMARY KEY,
    module_examination_id BIGINT NOT NULL REFERENCES module_examinations(id) ON DELETE CASCADE,
    parent_group_id BIGINT REFERENCES examination_groups(id) ON DELETE CASCADE,
    operator VARCHAR(20) NOT NULL DEFAULT 'AND',
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_examination_group_operator CHECK (operator IN ('AND', 'OR'))
);

CREATE TABLE examination_components (
    id BIGSERIAL PRIMARY KEY,
    examination_group_id BIGINT NOT NULL REFERENCES examination_groups(id) ON DELETE CASCADE,
    examination_form_id BIGINT NOT NULL REFERENCES examination_forms(id),
    weight_percent NUMERIC(5,2),
    duration_minutes INTEGER,
    details TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    graded BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_weight_percent CHECK (weight_percent IS NULL OR weight_percent BETWEEN 0 AND 100)
);

CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    role VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE module_changes (
    id BIGSERIAL PRIMARY KEY,
    module_id BIGINT NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    modulebook_id BIGINT REFERENCES modulebooks(id),
    field_name VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    reason TEXT,
    changed_by BIGINT REFERENCES app_users(id),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
);
