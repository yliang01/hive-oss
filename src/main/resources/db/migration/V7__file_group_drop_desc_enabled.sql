DROP INDEX IF EXISTS idx_group_enabled;
ALTER TABLE file_group DROP COLUMN group_desc;
ALTER TABLE file_group DROP COLUMN enabled;
