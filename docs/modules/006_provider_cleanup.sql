-- 006_provider_cleanup.sql
-- Remove legacy source column after provider rollout is fully validated.

ALTER TABLE hive_record
  DROP COLUMN source;
