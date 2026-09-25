CREATE UNIQUE INDEX uq_modules_modulebook_module_number
ON modules (modulebook_id, lower(btrim(module_number)))
WHERE modulebook_id IS NOT NULL
  AND module_number IS NOT NULL
  AND btrim(module_number) <> '';
