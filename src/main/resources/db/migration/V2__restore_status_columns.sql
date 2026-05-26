ALTER TABLE hive_record ADD COLUMN IF NOT EXISTS restore_status VARCHAR(32);
ALTER TABLE hive_record ADD COLUMN IF NOT EXISTS restore_requested_at TIMESTAMP;
ALTER TABLE hive_record ADD COLUMN IF NOT EXISTS restore_status_updated_at TIMESTAMP;
