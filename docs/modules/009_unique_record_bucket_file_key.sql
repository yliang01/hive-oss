-- 009_unique_record_bucket_file_key.sql
-- Enforce one logical file record per bucket and file key.
-- Clean existing duplicate (bucket_name, file_key) rows before running this migration.

ALTER TABLE hive_record
  ADD UNIQUE KEY uk_hive_record_bucket_file_key (bucket_name, file_key);
