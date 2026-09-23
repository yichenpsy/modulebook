CREATE OR REPLACE FUNCTION audit_log_enable_for_existing_tables()
RETURNS void AS $$
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
END;
$$ LANGUAGE plpgsql;

SELECT audit_log_enable_for_existing_tables();
