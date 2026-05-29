CREATE UNIQUE INDEX IF NOT EXISTS uk_hive_record_category_file_key
    ON hive_record (category_id, file_key);