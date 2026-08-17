ALTER TABLE hive_record DROP COLUMN IF EXISTS local_check_status;
ALTER TABLE hive_record DROP COLUMN IF EXISTS local_check_time;
ALTER TABLE hive_record DROP COLUMN IF EXISTS local_check_observed_size;

ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_check_time;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_check_status;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_check_record_total;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_check_size_matched;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_check_size_changed;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_check_missing;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_check_unverifiable;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_check_baseline_missing;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_check_message;

ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_scan_time;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_scan_status;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_scan_local_total;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_scan_matched;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_scan_size_mismatched;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_scan_local_only;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_scan_db_only;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_scan_skipped;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_scan_message;
ALTER TABLE file_category DROP COLUMN IF EXISTS last_local_scan_group_mismatched;
