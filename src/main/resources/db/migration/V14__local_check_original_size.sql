ALTER TABLE hive_record ADD COLUMN IF NOT EXISTS original_size BIGINT;
ALTER TABLE file_category ADD COLUMN IF NOT EXISTS last_local_check_baseline_missing INT;

UPDATE hive_record
SET local_check_status = NULL,
    local_check_time = NULL,
    local_check_observed_size = NULL;

UPDATE file_category
SET last_local_check_time = NULL,
    last_local_check_status = NULL,
    last_local_check_record_total = NULL,
    last_local_check_size_matched = NULL,
    last_local_check_size_changed = NULL,
    last_local_check_missing = NULL,
    last_local_check_unverifiable = NULL,
    last_local_check_baseline_missing = NULL,
    last_local_check_message = NULL;
