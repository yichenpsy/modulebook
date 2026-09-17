CREATE TABLE modulebook_tags (
    id BIGSERIAL PRIMARY KEY,
    study_program_id BIGINT NOT NULL REFERENCES study_programs(id) ON DELETE CASCADE,
    tag_message TEXT NOT NULL,
    snapshot_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

