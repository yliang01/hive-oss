-- 002_category_seed.sql
-- Seed default categories and example groups.

INSERT INTO file_category (code, name, description, bucket_name, storage_class, preview_policy, ui_variant, is_system, sort_order, enabled)
VALUES
  ('IMAGE_PREVIEW', '图片与小文件', '优先展示预览能力', 'hive-image-preview', 'STANDARD', 'IMAGE_FIRST', 'image', 1, 10, 1),
  ('HOT_FILE', '高频普通文件', '高频访问与检索效率优先', 'hive-hot-file', 'STANDARD', 'DEFAULT', 'hot', 1, 20, 1),
  ('COLD_ARCHIVE', '低频归档大文件', '低频访问，强调解冻流程', 'hive-cold-archive', 'ARCHIVE', 'NO_PREVIEW', 'archive', 1, 30, 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  description = VALUES(description),
  bucket_name = VALUES(bucket_name),
  storage_class = VALUES(storage_class),
  preview_policy = VALUES(preview_policy),
  ui_variant = VALUES(ui_variant),
  is_system = VALUES(is_system),
  sort_order = VALUES(sort_order),
  enabled = VALUES(enabled);

-- Optional seed groups (idempotent by category + group_code)
INSERT INTO file_group (category_id, group_code, group_name, group_desc, sort_order, enabled)
SELECT c.id, 'DEFAULT', '默认分组', '自动创建默认分组', 10, 1
FROM file_category c
WHERE c.code = 'IMAGE_PREVIEW'
  AND NOT EXISTS (
    SELECT 1 FROM file_group g WHERE g.category_id = c.id AND g.group_code = 'DEFAULT'
  );
