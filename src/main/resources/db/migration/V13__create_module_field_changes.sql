CREATE TABLE module_field_changes (
    id BIGSERIAL PRIMARY KEY,
    change_set_id BIGINT NOT NULL REFERENCES study_program_change_sets(id) ON DELETE CASCADE,
    module_id BIGINT NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    field_path VARCHAR(255) NOT NULL,
    old_value JSONB,
    new_value JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);