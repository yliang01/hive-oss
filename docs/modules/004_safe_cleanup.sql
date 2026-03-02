-- 004_safe_cleanup.sql
-- Optional cleanup and rollback helpers. Run manually with caution.

-- -------------------------
-- Forward cleanup (optional)
-- -------------------------
-- Example: disable deprecated category instead of hard delete.
-- UPDATE file_category SET enabled = 0 WHERE code = 'LEGACY_CATEGORY';

-- -------------------------
-- Rollback helpers
-- -------------------------
-- 1) Drop newly-added indexes first
-- ALTER TABLE file_group_record DROP INDEX uk_group_record;
-- ALTER TABLE file_group_record DROP INDEX idx_record_group;
-- ALTER TABLE file_group DROP INDEX uk_group_cat_code;
-- ALTER TABLE file_group DROP INDEX idx_group_cat_sort;
-- ALTER TABLE file_group DROP INDEX idx_group_enabled;
-- ALTER TABLE file_category DROP INDEX uk_file_category_code;

-- 2) Drop data tables in dependency order
-- DROP TABLE IF EXISTS file_group_record;
-- DROP TABLE IF EXISTS file_group;
-- DROP TABLE IF EXISTS file_category;

-- -------------------------
-- Execution order checklist
-- -------------------------
-- 1. Run 001_category_group_schema.sql
-- 2. Run 002_category_seed.sql
-- 3. Run 003_index_and_constraints.sql
-- 4. (Optional) Run this cleanup script sections as needed
