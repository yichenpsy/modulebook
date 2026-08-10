-- A study program is the parent object, for example "Architektur" in year 2021.
CREATE TABLE IF NOT EXISTS study_programs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    year INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- The same program/year should exist only once.
    CONSTRAINT uq_study_program_name_year UNIQUE (name, year)
);
