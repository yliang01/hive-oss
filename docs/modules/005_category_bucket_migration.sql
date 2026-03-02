-- 005_category_bucket_migration.sql
-- Migrate to category -> bucket mapping and record storage class cache.

ALTER TABLE file_category
  ADD COLUMN bucket_name VARCHAR(128) NULL COMMENT 'category to bucket binding (1:1)' AFTER description;

ALTER TABLE file_category
  ADD UNIQUE KEY uk_file_category_bucket (bucket_name);

ALTER TABLE hive_record
  ADD COLUMN bucket_name VARCHAR(128) NULL COMMENT 'resolved bucket for this object' AFTER source,
  ADD COLUMN storage_class_cache VARCHAR(32) NULL COMMENT 'last synced storage class from OSS metadata' AFTER bucket_name,
  ADD COLUMN provider VARCHAR(16) NULL COMMENT 'storage vendor provider: ALIBABA|TENCENT' AFTER source;

-- Backfill category bucket with existing deployment buckets before enabling NOT NULL.
-- Replace placeholders with real bucket names.
UPDATE file_category SET bucket_name = 'hive-image-preview' WHERE code = 'IMAGE_PREVIEW' AND bucket_name IS NULL;
UPDATE file_category SET bucket_name = 'hive-hot-file' WHERE code = 'HOT_FILE' AND bucket_name IS NULL;
UPDATE file_category SET bucket_name = 'hive-cold-archive' WHERE code = 'COLD_ARCHIVE' AND bucket_name IS NULL;

-- Backfill record cache from legacy source semantics.
UPDATE hive_record
SET storage_class_cache = CASE
    WHEN source = 'ALIBABA_ACHIEVE' THEN 'ARCHIVE'
    ELSE 'STANDARD'
END
WHERE storage_class_cache IS NULL;

-- Backfill provider from legacy source.
UPDATE hive_record
SET provider = CASE
    WHEN source LIKE 'ALIBABA_%' THEN 'ALIBABA'
    ELSE 'ALIBABA'
END
WHERE provider IS NULL;

ALTER TABLE hive_record
  MODIFY COLUMN provider VARCHAR(16) NOT NULL DEFAULT 'ALIBABA';

-- Config switch order:
-- 1) deploy code that reads category.bucket_name and hive.oss.alibaba.backupBucket
-- 2) apply this migration and ensure every enabled category has bucket_name
-- 3) update external-secret.yml to remove standardBucket/archiveBucket and set backupBucket
--
-- Rollback:
-- 1) restore external-secret.yml to previous standardBucket/archiveBucket values
-- 2) keep new columns as-is (backward compatible)
