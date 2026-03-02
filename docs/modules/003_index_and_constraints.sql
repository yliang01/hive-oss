-- 003_index_and_constraints.sql
-- Add unique constraints and performance indexes.

ALTER TABLE file_category
  ADD UNIQUE KEY uk_file_category_code (code),
  ADD UNIQUE KEY uk_file_category_bucket (bucket_name);

ALTER TABLE file_group
  ADD UNIQUE KEY uk_group_cat_code (category_id, group_code),
  ADD KEY idx_group_cat_sort (category_id, sort_order),
  ADD KEY idx_group_enabled (enabled);

ALTER TABLE file_group_record
  ADD UNIQUE KEY uk_group_record (group_id, hive_record_id),
  ADD KEY idx_record_group (hive_record_id, group_id);

-- Validation queries
SELECT code, COUNT(*) AS cnt
FROM file_category
GROUP BY code
HAVING COUNT(*) > 1;

SELECT bucket_name, COUNT(*) AS cnt
FROM file_category
GROUP BY bucket_name
HAVING COUNT(*) > 1;

SELECT category_id, group_code, COUNT(*) AS cnt
FROM file_group
GROUP BY category_id, group_code
HAVING COUNT(*) > 1;
