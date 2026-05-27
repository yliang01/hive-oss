ALTER TABLE hive_record
    ALTER COLUMN category_id SET NOT NULL;

ALTER TABLE hive_record
    DROP COLUMN bucket_name;
