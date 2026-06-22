CREATE TABLE case_participants (
    id uuid PRIMARY KEY,
    case_file_id uuid NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    user_account_id uuid NOT NULL REFERENCES users(id),
    added_at timestamptz NOT NULL,
    CONSTRAINT case_participants_unique UNIQUE(case_file_id,user_account_id)
);
CREATE INDEX case_participants_case_idx ON case_participants(case_file_id);

INSERT INTO case_participants(id,case_file_id,user_account_id,added_at)
SELECT gen_random_uuid(),c.id,u.id,CURRENT_TIMESTAMP
FROM cases c CROSS JOIN users u
WHERE u.active=true AND (u.role IN ('AGENT','ASSISTANT') OR u.id=c.created_by_id);

ALTER TABLE tasks ADD COLUMN registration_number varchar(255);
UPDATE tasks SET registration_number='TASK-MIGRATED-'||upper(substr(replace(CAST(id AS varchar),'-',''),1,12));
ALTER TABLE tasks ALTER COLUMN registration_number SET NOT NULL;
ALTER TABLE tasks ADD CONSTRAINT tasks_registration_number_unique UNIQUE(registration_number);
