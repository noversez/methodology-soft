CREATE TABLE users (
    id uuid PRIMARY KEY,
    login varchar(255) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    role varchar(255) NOT NULL CHECK (role IN ('DETECTIVE', 'ASSISTANT', 'INSPECTOR', 'AGENT', 'LAB_ANALYST', 'ADMIN')),
    display_name varchar(255) NOT NULL,
    active boolean NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE cases (
    id uuid PRIMARY KEY,
    registration_number varchar(255) NOT NULL UNIQUE,
    title varchar(255) NOT NULL,
    opened_at timestamptz NOT NULL,
    priority varchar(255) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status varchar(255) NOT NULL CHECK (status IN ('NEW', 'IN_PROGRESS', 'CLOSED')),
    description varchar(4000) NOT NULL,
    created_by_id uuid NOT NULL REFERENCES users(id),
    version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE evidence (
    id uuid PRIMARY KEY,
    case_file_id uuid NOT NULL REFERENCES cases(id),
    registration_number varchar(255) NOT NULL UNIQUE,
    name varchar(255) NOT NULL,
    type varchar(255) NOT NULL,
    importance varchar(255) NOT NULL CHECK (importance IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    description varchar(8000) NOT NULL,
    discovery_date_time timestamptz NOT NULL,
    latitude double precision,
    longitude double precision,
    location_title varchar(255),
    responsible_user_id uuid NOT NULL REFERENCES users(id),
    status varchar(255) NOT NULL CHECK (status IN ('REGISTERED', 'UNDER_EXAMINATION', 'EXAMINATION_COMPLETED')),
    version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT evidence_latitude_range CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT evidence_longitude_range CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE TABLE evidence_versions (
    id uuid PRIMARY KEY,
    evidence_id uuid NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,
    description_snapshot varchar(8000) NOT NULL,
    changed_by_id uuid NOT NULL REFERENCES users(id),
    changed_at timestamptz NOT NULL,
    version_number bigint NOT NULL,
    CONSTRAINT evidence_versions_number_unique UNIQUE (evidence_id, version_number)
);

CREATE TABLE tasks (
    id uuid PRIMARY KEY,
    case_file_id uuid NOT NULL REFERENCES cases(id),
    title varchar(255) NOT NULL,
    description varchar(4000) NOT NULL,
    assignee_id uuid NOT NULL REFERENCES users(id),
    created_by_id uuid NOT NULL REFERENCES users(id),
    priority varchar(255) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    deadline timestamptz NOT NULL,
    status varchar(255) NOT NULL CHECK (status IN ('ASSIGNED', 'IN_PROGRESS', 'DONE')),
    result_text varchar(8000),
    result_evidence_id uuid REFERENCES evidence(id),
    version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE lab_requests (
    id uuid PRIMARY KEY,
    case_file_id uuid NOT NULL REFERENCES cases(id),
    evidence_id uuid NOT NULL REFERENCES evidence(id),
    profile varchar(255) NOT NULL,
    questions varchar(8000) NOT NULL,
    desired_due_date timestamptz NOT NULL,
    status varchar(255) NOT NULL CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED')),
    requester_id uuid NOT NULL REFERENCES users(id),
    lab_assignee_id uuid REFERENCES users(id),
    result_text varchar(20000),
    version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE hypotheses (
    id uuid PRIMARY KEY,
    case_file_id uuid NOT NULL REFERENCES cases(id),
    title varchar(255) NOT NULL,
    text varchar(12000) NOT NULL,
    confidence varchar(255) NOT NULL CHECK (confidence IN ('LOW', 'MEDIUM', 'HIGH', 'CONFIRMED')),
    author_id uuid NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL
);

CREATE TABLE graph_edges (
    id uuid PRIMARY KEY,
    case_file_id uuid NOT NULL REFERENCES cases(id),
    source_type varchar(255) NOT NULL CHECK (source_type IN ('CASE', 'EVIDENCE', 'PERSON', 'LOCATION', 'TASK', 'LAB_REQUEST', 'REPORT', 'HYPOTHESIS')),
    source_id uuid NOT NULL,
    target_type varchar(255) NOT NULL CHECK (target_type IN ('CASE', 'EVIDENCE', 'PERSON', 'LOCATION', 'TASK', 'LAB_REQUEST', 'REPORT', 'HYPOTHESIS')),
    target_id uuid NOT NULL,
    semantic_type varchar(255) NOT NULL,
    confidence varchar(255) NOT NULL CHECK (confidence IN ('LOW', 'MEDIUM', 'HIGH', 'CONFIRMED')),
    hypothesis_id uuid REFERENCES hypotheses(id),
    created_by_id uuid NOT NULL REFERENCES users(id),
    version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT graph_edges_not_self CHECK (source_type <> target_type OR source_id <> target_id),
    CONSTRAINT graph_edges_identity_unique UNIQUE (case_file_id, source_type, source_id, target_type, target_id, semantic_type)
);

CREATE TABLE reports (
    id uuid PRIMARY KEY,
    case_file_id uuid NOT NULL REFERENCES cases(id),
    registration_number varchar(255) NOT NULL UNIQUE,
    format varchar(255) NOT NULL,
    status varchar(255) NOT NULL CHECK (status IN ('DRAFT', 'APPROVED')),
    storage_path varchar(255) NOT NULL,
    sha256 varchar(255) NOT NULL,
    approved_by_id uuid NOT NULL REFERENCES users(id),
    approved_at timestamptz NOT NULL,
    content varchar(40000) NOT NULL
);

CREATE TABLE attachments (
    id uuid PRIMARY KEY,
    owner_type varchar(255) NOT NULL,
    owner_id uuid NOT NULL,
    file_name varchar(255) NOT NULL,
    mime_type varchar(255) NOT NULL,
    size_bytes bigint NOT NULL CHECK (size_bytes > 0),
    storage_path varchar(255) NOT NULL,
    sha256 varchar(255) NOT NULL,
    uploaded_by_id uuid NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL
);

CREATE TABLE audit_logs (
    id uuid PRIMARY KEY,
    actor_id uuid NOT NULL REFERENCES users(id),
    action varchar(255) NOT NULL,
    entity_type varchar(255) NOT NULL,
    entity_id uuid NOT NULL,
    timestamp timestamptz NOT NULL,
    metadata_json varchar(4000) NOT NULL
);

CREATE TABLE notifications (
    id uuid PRIMARY KEY,
    recipient_id uuid NOT NULL REFERENCES users(id),
    type varchar(255) NOT NULL,
    payload_json varchar(4000) NOT NULL,
    read_at timestamptz,
    created_at timestamptz NOT NULL
);

CREATE INDEX cases_created_by_idx ON cases(created_by_id);
CREATE INDEX evidence_case_idx ON evidence(case_file_id);
CREATE INDEX evidence_responsible_idx ON evidence(responsible_user_id);
CREATE INDEX tasks_case_idx ON tasks(case_file_id);
CREATE INDEX tasks_assignee_idx ON tasks(assignee_id);
CREATE INDEX lab_requests_case_idx ON lab_requests(case_file_id);
CREATE INDEX lab_requests_evidence_idx ON lab_requests(evidence_id);
CREATE INDEX lab_requests_assignee_due_idx ON lab_requests(lab_assignee_id, desired_due_date);
CREATE UNIQUE INDEX lab_requests_one_active_per_evidence_idx ON lab_requests(evidence_id) WHERE status <> 'COMPLETED';
CREATE INDEX hypotheses_case_idx ON hypotheses(case_file_id);
CREATE INDEX graph_edges_case_idx ON graph_edges(case_file_id);
CREATE INDEX graph_edges_source_idx ON graph_edges(source_type, source_id);
CREATE INDEX graph_edges_target_idx ON graph_edges(target_type, target_id);
CREATE INDEX reports_case_idx ON reports(case_file_id);
CREATE INDEX attachments_owner_idx ON attachments(owner_type, owner_id);
CREATE INDEX audit_logs_timestamp_idx ON audit_logs(timestamp DESC);
CREATE INDEX audit_logs_entity_idx ON audit_logs(entity_type, entity_id);
CREATE INDEX notifications_recipient_created_idx ON notifications(recipient_id, created_at DESC);
