ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_sync_time TIMESTAMP;
ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_sync_status VARCHAR(32);
ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_sync_oss_total INT;
ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_sync_oss_only INT;
ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_sync_oss_to_db_matched INT;
ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_sync_oss_to_db_mismatched INT;
ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_sync_db_to_oss_mismatched INT;
ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_sync_message VARCHAR(512);
