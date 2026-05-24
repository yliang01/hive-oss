CREATE TABLE IF NOT EXISTS file_category (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    code          VARCHAR(32)  NOT NULL,
    name          VARCHAR(64)  NOT NULL,
    storage_class VARCHAR(16)  NOT NULL DEFAULT 'STANDARD',
    bucket_name   VARCHAR(128),
    ui_variant    VARCHAR(32)  NOT NULL DEFAULT 'hot',
    sort_order    INT          NOT NULL DEFAULT 0,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_file_category_code   UNIQUE (code),
    CONSTRAINT uk_file_category_bucket UNIQUE (bucket_name)
);

CREATE TABLE IF NOT EXISTS hive_record (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    file_name       VARCHAR(255),
    file_key        VARCHAR(512),
    zipped          BOOLEAN,
    provider        VARCHAR(32),
    bucket_name     VARCHAR(128),
    size            BIGINT,
    update_time     TIMESTAMP,
    status          VARCHAR(32),
    last_sync_time  TIMESTAMP,
    deletable       BOOLEAN,
    deleted         BOOLEAN DEFAULT FALSE,
    restore_time    TIMESTAMP,
    download_status VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS hive_record_image_meta (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    hive_record_id INT NOT NULL,
    thumb_key      VARCHAR(512),
    thumb_status   VARCHAR(32),
    image_width    INT,
    image_height   INT,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    CONSTRAINT uq_hive_record_image_meta_record UNIQUE (hive_record_id),
    CONSTRAINT fk_image_meta_record FOREIGN KEY (hive_record_id) REFERENCES hive_record(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS file_group (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT       NOT NULL,
    group_code  VARCHAR(64)  NOT NULL,
    group_name  VARCHAR(128) NOT NULL,
    group_desc  VARCHAR(255),
    sort_order  INT          NOT NULL DEFAULT 0,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_group_cat_code  UNIQUE (category_id, group_code),
    CONSTRAINT fk_group_category  FOREIGN KEY (category_id) REFERENCES file_category(id)
);

CREATE INDEX IF NOT EXISTS idx_group_cat_sort ON file_group (category_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_group_enabled   ON file_group (enabled);

CREATE TABLE IF NOT EXISTS file_group_record (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id       BIGINT     NOT NULL,
    hive_record_id INT        NOT NULL,
    assigned_by    VARCHAR(64),
    assigned_at    TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_group_record UNIQUE (group_id, hive_record_id),
    CONSTRAINT fk_fgr_group    FOREIGN KEY (group_id)       REFERENCES file_group(id),
    CONSTRAINT fk_fgr_record   FOREIGN KEY (hive_record_id) REFERENCES hive_record(id)
);

CREATE INDEX IF NOT EXISTS idx_record_group ON file_group_record (hive_record_id, group_id);
