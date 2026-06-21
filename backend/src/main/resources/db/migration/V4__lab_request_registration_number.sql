ALTER TABLE lab_requests ADD COLUMN registration_number varchar(255);
UPDATE lab_requests
SET registration_number = 'LAB-MIGRATED-' || upper(substr(replace(CAST(id AS varchar), '-', ''), 1, 12));
ALTER TABLE lab_requests ALTER COLUMN registration_number SET NOT NULL;
ALTER TABLE lab_requests ADD CONSTRAINT lab_requests_registration_number_unique UNIQUE (registration_number);
