CREATE TABLE incident_scenes (
    id uuid PRIMARY KEY,
    case_file_id uuid NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    title varchar(255) NOT NULL,
    description varchar(4000) NOT NULL,
    address varchar(255) NOT NULL,
    latitude double precision,
    longitude double precision,
    created_by_id uuid NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL,
    CONSTRAINT incident_scene_latitude_range CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT incident_scene_longitude_range CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE INDEX incident_scenes_case_idx ON incident_scenes(case_file_id);
