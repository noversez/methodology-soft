ALTER TABLE attachments ADD COLUMN captured_at timestamptz;
ALTER TABLE attachments ADD COLUMN latitude double precision;
ALTER TABLE attachments ADD COLUMN longitude double precision;
ALTER TABLE attachments ADD CONSTRAINT attachments_latitude_range CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90);
ALTER TABLE attachments ADD CONSTRAINT attachments_longitude_range CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180);
