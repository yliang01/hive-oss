ALTER TABLE file_group_record DROP CONSTRAINT IF EXISTS uk_group_record;

ALTER TABLE file_group_record
    ADD CONSTRAINT uk_fgr_record UNIQUE (hive_record_id);
