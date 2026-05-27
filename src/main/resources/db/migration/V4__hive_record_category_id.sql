ALTER TABLE hive_record
    ADD COLUMN category_id BIGINT;

UPDATE hive_record r
SET category_id = (
    SELECT c.id
    FROM file_category c
    WHERE c.bucket_name = r.bucket_name
)
WHERE category_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM file_category c
      WHERE c.bucket_name = r.bucket_name
  );

ALTER TABLE hive_record
    ADD CONSTRAINT fk_hive_record_category
    FOREIGN KEY (category_id) REFERENCES file_category(id);

CREATE INDEX IF NOT EXISTS idx_hive_record_category_deleted
    ON hive_record (category_id, deleted);

CREATE INDEX IF NOT EXISTS idx_hive_record_category_file_key
    ON hive_record (category_id, file_key);
