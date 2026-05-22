-- 008_image_meta_split.sql
-- Split thumbnail/image metadata from hive_record into 1:1 extension table.
-- Use this instead of 007_thumbnail_metadata.sql (do not run 007).
-- OSS object content remains app-layer encrypted; metadata in DB is plaintext.

CREATE TABLE hive_record_image_meta (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  hive_record_id INT NOT NULL COMMENT 'FK to hive_record.id',
  thumb_key VARCHAR(512) NULL COMMENT 'OSS object key of thumbnail (e.g. thumb/{fileKey}_w320.jpg)',
  thumb_status VARCHAR(32) NULL COMMENT 'PENDING, READY, FAILED',
  image_width INT NULL COMMENT 'original image width in pixels',
  image_height INT NULL COMMENT 'original image height in pixels',
  created_at DATETIME NULL,
  updated_at DATETIME NULL,
  UNIQUE KEY uk_image_meta_record (hive_record_id),
  CONSTRAINT fk_image_meta_record FOREIGN KEY (hive_record_id) REFERENCES hive_record (id) ON DELETE CASCADE
) COMMENT '1:1 image/thumbnail metadata for records that have thumbnails';
