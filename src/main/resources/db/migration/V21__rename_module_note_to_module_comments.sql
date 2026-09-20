DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'modules'
          AND column_name = 'module_note'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'modules'
          AND column_name = 'module_comments'
    ) THEN
        ALTER TABLE modules RENAME COLUMN module_note TO module_comments;
    END IF;
END $$;

ALTER TABLE modules
ADD COLUMN IF NOT EXISTS module_comments TEXT;

UPDATE module_field_changes
SET field_path = 'module.moduleComments'
WHERE field_path = 'module.moduleNote';

UPDATE modulebook_tags
SET snapshot_json = replace(snapshot_json::text, '"moduleNote"', '"moduleComments"')::jsonb
WHERE snapshot_json::text LIKE '%"moduleNote"%';
