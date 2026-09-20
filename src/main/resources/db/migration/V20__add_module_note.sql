ALTER TABLE modules
ADD COLUMN IF NOT EXISTS module_note TEXT;

UPDATE modules
SET module_note = 'MISSING_FROM_IMPORT',
    module_status = 'ACTIVE'
WHERE module_status = 'MISSING_FROM_IMPORT';

UPDATE modules
SET module_status = 'DEACTIVE'
WHERE module_status = 'DELETED';
