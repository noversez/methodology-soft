CREATE TABLE interviews (
    id uuid PRIMARY KEY,
    case_file_id uuid NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    interviewee varchar(255) NOT NULL,
    occurred_at timestamptz NOT NULL,
    protocol_text varchar(12000) NOT NULL,
    created_by_id uuid NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL
);
CREATE INDEX interviews_case_occurred_idx ON interviews(case_file_id, occurred_at);
