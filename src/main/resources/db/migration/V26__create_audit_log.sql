CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    table_name TEXT NOT NULL,
    operation TEXT NOT NULL,
    row_id BIGINT NOT NULL,
    old_data JSONB,
    new_data JSONB,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_audit_log_operation CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE'))
);

CREATE INDEX IF NOT EXISTS idx_audit_log_table_row
ON audit_log (table_name, row_id);

CREATE INDEX IF NOT EXISTS idx_audit_log_changed_at
ON audit_log (changed_at);

CREATE OR REPLACE FUNCTION audit_log_record_change()
RETURNS TRIGGER AS $$
DECLARE
    old_row JSONB;
    new_row JSONB;
BEGIN
    IF TG_OP = 'UPDATE' AND OLD IS NOT DISTINCT FROM NEW THEN
        RETURN NEW;
    END IF;

    old_row := CASE
        WHEN TG_OP IN ('UPDATE', 'DELETE') THEN to_jsonb(OLD) - 'password_hash'
        ELSE NULL
    END;

    new_row := CASE
        WHEN TG_OP IN ('INSERT', 'UPDATE') THEN to_jsonb(NEW) - 'password_hash'
        ELSE NULL
    END;

    INSERT INTO audit_log (
        table_name,
        operation,
        row_id,
        old_data,
        new_data
    )
    VALUES (
        TG_TABLE_NAME,
        TG_OP,
        CASE
            WHEN TG_OP = 'DELETE' THEN OLD.id
            ELSE NEW.id
        END,
        old_row,
        new_row
    );

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    audited_table RECORD;
BEGIN
    FOR audited_table IN
        SELECT tables.table_schema, tables.table_name
        FROM information_schema.tables tables
        JOIN information_schema.columns columns
          ON columns.table_schema = tables.table_schema
         AND columns.table_name = tables.table_name
         AND columns.column_name = 'id'
        WHERE tables.table_schema = 'public'
          AND tables.table_type = 'BASE TABLE'
          AND tables.table_name <> 'audit_log'
    LOOP
        EXECUTE format(
            'DROP TRIGGER IF EXISTS audit_log_changes ON %I.%I',
            audited_table.table_schema,
            audited_table.table_name
        );

        EXECUTE format(
            'CREATE TRIGGER audit_log_changes
             AFTER INSERT OR UPDATE OR DELETE ON %I.%I
             FOR EACH ROW
             EXECUTE FUNCTION audit_log_record_change()',
            audited_table.table_schema,
            audited_table.table_name
        );
    END LOOP;
END $$;
