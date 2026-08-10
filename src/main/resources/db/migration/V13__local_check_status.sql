ALTER TABLE hive_record ADD COLUMN IF NOT EXISTS local_check_status VARCHAR(32);
ALTER TABLE hive_record ADD COLUMN IF NOT EXISTS local_check_time TIMESTAMP;
ALTER TABLE hive_record ADD COLUMN IF NOT EXISTS local_check_observed_size BIGINT;

ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_local_check_time TIMESTAMP;
ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_local_check_status VARCHAR(32);
ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_local_check_record_total INT;
ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_local_check_size_matched INT;
ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_local_check_size_changed INT;
ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_local_check_missing INT;
ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_local_check_unverifiable INT;
ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_local_check_message VARCHAR(512);
