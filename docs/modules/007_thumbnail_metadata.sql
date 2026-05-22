-- 007_thumbnail_metadata.sql
-- DEPRECATED: superseded by 008_image_meta_split.sql. Do not run this script.
-- Add thumbnail and image dimension fields for IMAGE_PREVIEW gallery.
-- Metadata stored in DB as plaintext; object content in OSS remains app-layer encrypted.

-- ALTER TABLE hive_record
--   ADD COLUMN thumb_key VARCHAR(512) NULL COMMENT 'OSS object key of thumbnail (e.g. thumb/{fileKey}_w320.jpg)' AFTER download_status,
--   ADD COLUMN thumb_status VARCHAR(32) NULL COMMENT 'PENDING, READY, FAILED' AFTER thumb_key,
--   ADD COLUMN image_width INT NULL COMMENT 'original image width in pixels' AFTER thumb_status,
--   ADD COLUMN image_height INT NULL COMMENT 'original image height in pixels' AFTER image_width;
